package com.oneofx.fusion.tradingbot.desktop;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.FusionApiException;
import com.oneofx.fusion.client.model.NewOrder;
import com.oneofx.fusion.client.model.NewOrderResponse;
import com.oneofx.fusion.client.model.Order;
import com.oneofx.fusion.client.model.OrderSide;
import com.oneofx.fusion.client.model.OrderStatus;
import com.oneofx.fusion.client.model.OrderType;
import com.oneofx.fusion.client.model.TimeInForce;
import com.oneofx.fusion.tradingbot.HelperFunctions.Time;
import com.oneofx.fusion.tradingbot.HelperFunctions.TradingRulesFormatter;
import com.oneofx.fusion.tradingbot.SQL_Database.BuyOrderPersistence;
import com.oneofx.fusion.tradingbot.SQL_Database.BuyOrderPersistence.Attempt;
import com.oneofx.fusion.tradingbot.SQL_Database.SellOrderPersistence;
import com.oneofx.fusion.tradingbot.SQL_Database.SellOrderPersistence.Reservation;
import com.oneofx.fusion.tradingbot.constants.TradingConstants;
import com.oneofx.fusion.tradingbot.desktop.OperationsRepository.OpenOrderRow;
import com.oneofx.fusion.tradingbot.desktop.OperationsRepository.OrderOrigin;
import com.oneofx.fusion.tradingbot.desktop.PaperTradingRepository.PaperPosition;
import com.oneofx.fusion.tradingbot.domain.Position;

/** Safe application service for manual terminal orders and cancel/replace. */
public final class ManualOrderService {
    private final PaperTradingRepository paper;
    private final OperationsRepository operations;
    private final BuyOrderPersistence buys;
    private final SellOrderPersistence sells;

    public ManualOrderService() {
        this(new PaperTradingRepository(), new OperationsRepository(),
                new BuyOrderPersistence(), new SellOrderPersistence());
    }

    ManualOrderService(PaperTradingRepository paper, OperationsRepository operations,
            BuyOrderPersistence buys, SellOrderPersistence sells) {
        this.paper=paper; this.operations=operations; this.buys=buys; this.sells=sells;
    }

    public PreparedOrder prepare(BotProfile bot, Request input) throws SQLException {
        return prepare(bot,input,null);
    }

    public PreparedOrder prepareReplacement(BotProfile bot, OpenOrderRow oldOrder, Request input)
            throws SQLException {
        Objects.requireNonNull(oldOrder,"oldOrder");
        return prepare(bot,input,oldOrder);
    }

    private PreparedOrder prepare(BotProfile bot, Request input, OpenOrderRow replaced)
            throws SQLException {
        Objects.requireNonNull(bot, "bot");
        Objects.requireNonNull(input, "input");
        String currency=CurrencySettings.normalizeCurrency(input.currency());
        if (input.side()==null || input.type()==null)
            throw new IllegalArgumentException("Orderseite und Ordertyp fehlen.");
        if (input.type()!=OrderType.LIMIT && input.type()!=OrderType.MARKET)
            throw new IllegalArgumentException("Manuelle Orders unterstützen derzeit LIMIT und MARKET.");
        if (!TradingRulesFormatter.supportsOrderType(currency,input.type()))
            throw new IllegalArgumentException("Der gespeicherte Fusion-Paarkatalog erlaubt "
                    +input.type()+" für dieses Handelspaar nicht.");
        double reference=input.type()==OrderType.MARKET ? input.marketPrice() : input.limitPrice();
        if (!positive(reference)) throw new IllegalArgumentException(
                input.type()==OrderType.MARKET ? "Aktueller Marktpreis fehlt." : "Limitpreis muss größer als 0 sein.");

        String priceText=TradingRulesFormatter.formatOrderPrice(currency, reference);
        double price=new BigDecimal(priceText).doubleValue();
        double requestedQuantity=input.quantity();
        String positionId=input.positionId();
        if (replaced!=null) positionId=replaced.referenceId();
        if (input.side()==OrderSide.SELL) {
            if (positionId==null || positionId.isBlank())
                throw new IllegalArgumentException("Für einen Verkauf muss eine Position gewählt werden.");
            requestedQuantity=replaced==null
                    ? positionQuantity(bot,currency,positionId) : replaced.quantity();
        }
        String quantityText=TradingRulesFormatter.formatOrderQuantity(currency, requestedQuantity);
        double quantity=new BigDecimal(quantityText).doubleValue();
        if (!positive(quantity)) throw new IllegalArgumentException("Ordermenge muss größer als 0 sein.");
        if (!TradingRulesFormatter.isOrderValid(currency, BigDecimal.valueOf(price),
                BigDecimal.valueOf(quantity)))
            throw new IllegalArgumentException("Preis oder Menge verletzt die gespeicherten Fusion-Handelsregeln.");
        double notional=price*quantity;
        if (input.side()==OrderSide.BUY && replaced==null) validateBuyRisk(bot, notional);
        return new PreparedOrder(new Request(currency,input.side(),input.type(),quantity,price,
                input.marketPrice(),positionId),priceText,quantityText,notional);
    }

    public Submission submit(BotProfile bot, PreparedOrder prepared, FusionApiClient client)
            throws SQLException {
        Objects.requireNonNull(bot, "bot"); Objects.requireNonNull(prepared, "prepared");
        Request request=prepared.request();
        if (bot.mode()==BotProfile.Mode.PAPER) return submitPaper(bot,prepared);
        if (client==null) throw new IllegalArgumentException("Für Live-Orders fehlt der Fusion API-Zugang.");
        return request.side()==OrderSide.BUY
                ? submitLiveBuy(prepared,client) : submitLiveSell(prepared,client);
    }

    public CancelResult cancel(BotProfile bot, OpenOrderRow row, FusionApiClient client)
            throws SQLException {
        Objects.requireNonNull(bot,"bot"); Objects.requireNonNull(row,"row");
        if (row.origin()==OrderOrigin.PAPER) {
            if (!paper.cancelOpenOrder(bot.id(),row.orderId(),"MANUAL_CANCEL"))
                throw new IllegalStateException("Die Paper-Order ist nicht mehr offen.");
            return new CancelResult(true,"Paper-Order wurde storniert.");
        }
        if (client==null) throw new IllegalArgumentException("Für Live-Stornos fehlt der Fusion API-Zugang.");
        Order result=client.cancelOrder(row.orderId());
        if (result==null || result.getStatus()==null)
            throw new IllegalStateException("Fusion hat keinen eindeutigen Stornostatus geliefert. Lokal wurde nichts geändert.");
        double executed=number(result.getExecutedQty());
        if (!Double.isFinite(executed) || executed>0 || !terminalCancellation(result.getStatus()))
            return new CancelResult(false,"Fusion meldet "+result.getStatus()+" mit ausgeführter Menge "
                    +result.getExecutedQty()+". Die Order bleibt für den regulären Abgleich erhalten.");
        if (row.orderSide()==OrderSide.BUY) buys.recordCancelled(row.orderId());
        else sells.recordCancelled(row.orderId());
        return new CancelResult(true,"Live-Order wurde von Fusion bestätigt storniert.");
    }

    public Submission replace(BotProfile bot, OpenOrderRow oldOrder, PreparedOrder replacement,
            FusionApiClient client) throws SQLException {
        OrderOrigin expected=bot.mode()==BotProfile.Mode.PAPER?OrderOrigin.PAPER:OrderOrigin.LIVE;
        if(oldOrder.origin()!=expected)
            throw new IllegalArgumentException("Eine Order aus einem anderen Bot-Modus kann nicht ersetzt werden.");
        if (oldOrder.orderSide()!=replacement.request().side())
            throw new IllegalArgumentException("Beim Ersetzen darf die Orderseite nicht geändert werden.");
        if (!oldOrder.currency().equalsIgnoreCase(replacement.request().currency()))
            throw new IllegalArgumentException("Beim Ersetzen darf das Handelspaar nicht geändert werden.");
        CancelResult cancelled=cancel(bot,oldOrder,client);
        if (!cancelled.clean()) throw new IllegalStateException(cancelled.message());
        return submit(bot,replacement,client);
    }

    private Submission submitPaper(BotProfile bot, PreparedOrder prepared) throws SQLException {
        Request request=prepared.request(); boolean accepted;
        if (request.side()==OrderSide.BUY) {
            accepted=request.type()==OrderType.MARKET
                    ? paper.executeMarketBuy(bot.id(),request.currency(),request.marketPrice(),
                            request.quantity(),bot.paperSlippagePercent(),bot.paperFeePercent())
                    : paper.placeBuy(bot.id(),request.currency(),request.limitPrice(),
                            request.quantity(),bot.paperFeePercent(),"LIMIT");
        } else {
            PaperPosition position=findPaperPosition(bot.id(),request.currency(),request.positionId());
            accepted=request.type()==OrderType.MARKET
                    ? paper.closePosition(bot.id(),position,request.marketPrice(),
                            bot.paperSlippagePercent(),bot.paperFeePercent(),"MANUAL","MARKET")
                    : paper.placeLimitSell(bot.id(),position,request.limitPrice());
        }
        if (!accepted) throw new IllegalStateException(
                "Paper-Order wurde wegen Kontostand, Doppelbelegung oder Positionsstatus abgelehnt.");
        return new Submission(null,bot.mode(),request.side(),request.type(),
                request.type()==OrderType.MARKET ? "Sofort ausgeführt" : "Offen");
    }

    private Submission submitLiveBuy(PreparedOrder prepared, FusionApiClient client)
            throws SQLException {
        Request request=prepared.request();
        String journalPrice=prepared.priceText();
        Attempt attempt=buys.start(request.currency(),prepared.quantityText(),journalPrice,journalPrice);
        String exchangeOrderId=null;
        try {
            NewOrderResponse response=client.newOrder(toNewOrder(prepared));
            exchangeOrderId=response==null?null:response.getOrderId();
            if (exchangeOrderId==null || exchangeOrderId.isBlank()) {
                buys.requireReconciliation(attempt,null,"Fusion lieferte keine Order-ID");
                throw new IllegalStateException("Fusion hat keine Order-ID geliefert; manueller Abgleich ist erforderlich.");
            }
            buys.recordSubmitted(attempt,exchangeOrderId,new Position.Builder(request.currency(),exchangeOrderId)
                    .orderPrice(Double.valueOf(journalPrice)).quantity(request.quantity())
                    .buyAmount(prepared.notional()).status(0).statusCode(TradingConstants.STATUS_NEW)
                    .orderOrigin("MANUAL").build());
            return new Submission(exchangeOrderId,BotProfile.Mode.LIVE,request.side(),request.type(),"Übermittelt");
        } catch (FusionApiException ex) {
            if (definiteRejection(ex)) buys.recordRejected(attempt,ex.getMessage());
            else buys.requireReconciliation(attempt,exchangeOrderId,ex.getMessage());
            throw ex;
        } catch (SQLException ex) {
            try { buys.requireReconciliation(attempt,exchangeOrderId,
                    "Lokale Verbuchung fehlgeschlagen: "+ex.getMessage()); }
            catch (SQLException marker) { ex.addSuppressed(marker); }
            throw ex;
        } catch (RuntimeException ex) {
            try { buys.requireReconciliation(attempt,exchangeOrderId,
                    "Unerwarteter Übermittlungsfehler: "+ex.getMessage()); }
            catch (SQLException marker) { ex.addSuppressed(marker); }
            throw ex;
        }
    }

    private Submission submitLiveSell(PreparedOrder prepared, FusionApiClient client)
            throws SQLException {
        Request request=prepared.request();
        Reservation reservation=sells.reserve(request.positionId(),request.currency(),
                prepared.quantityText()).orElseThrow(() -> new IllegalStateException(
                        "Die Position ist nicht mehr verkaufbar oder bereits reserviert."));
        String exchangeOrderId=null;
        try {
            NewOrderResponse response=client.newOrder(toNewOrder(prepared));
            exchangeOrderId=response==null?null:response.getOrderId();
            if (exchangeOrderId==null || exchangeOrderId.isBlank()) {
                sells.requireReconciliation(reservation,null,"Fusion lieferte keine Order-ID");
                throw new IllegalStateException("Fusion hat keine Order-ID geliefert; die Position bleibt gesperrt.");
            }
            sells.recordSubmitted(reservation,exchangeOrderId,Time.getCurrentDate(),Time.getCurrentTime_HHmmss());
            return new Submission(exchangeOrderId,BotProfile.Mode.LIVE,request.side(),request.type(),"Übermittelt");
        } catch (FusionApiException ex) {
            if (definiteRejection(ex)) sells.recordRejected(reservation,ex.getMessage());
            else sells.requireReconciliation(reservation,exchangeOrderId,ex.getMessage());
            throw ex;
        } catch (SQLException ex) {
            try { sells.requireReconciliation(reservation,exchangeOrderId,
                    "Lokale Verbuchung fehlgeschlagen: "+ex.getMessage()); }
            catch (SQLException marker) { ex.addSuppressed(marker); }
            throw ex;
        } catch (RuntimeException ex) {
            try { sells.requireReconciliation(reservation,exchangeOrderId,
                    "Unerwarteter Übermittlungsfehler: "+ex.getMessage()); }
            catch (SQLException marker) { ex.addSuppressed(marker); }
            throw ex;
        }
    }

    private static NewOrder toNewOrder(PreparedOrder prepared) {
        Request request=prepared.request();
        if (request.type()==OrderType.MARKET)
            return request.side()==OrderSide.BUY
                    ? NewOrder.marketBuy(request.currency(),prepared.quantityText())
                    : NewOrder.marketSell(request.currency(),prepared.quantityText());
        return new NewOrder(request.currency(),request.side(),OrderType.LIMIT,TimeInForce.GTC,
                prepared.quantityText(),prepared.priceText());
    }

    private double positionQuantity(BotProfile bot,String currency,String positionId)
            throws SQLException {
        if (bot.mode()==BotProfile.Mode.PAPER)
            return findPaperPosition(bot.id(),currency,positionId).quantity();
        return operations.loadPositions(bot.id()).stream()
                .filter(row->row.orderId().equals(positionId)
                        && row.currency().equalsIgnoreCase(currency)
                        && (row.status()==1 || row.status()==7))
                .mapToDouble(OperationsRepository.PositionRow::quantity).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Die Live-Position ist nicht mehr verkaufbar."));
    }

    private PaperPosition findPaperPosition(long botId,String currency,String positionId)
            throws SQLException {
        return paper.loadOpenPositions(botId,currency).stream()
                .filter(position->position.positionId().equals(positionId)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Die Paper-Position ist nicht mehr offen."));
    }

    private void validateBuyRisk(BotProfile bot,double notional) throws SQLException {
        if (bot.mode()==BotProfile.Mode.PAPER) {
            PaperTradingRepository.PaperRisk risk=paper.loadRisk(bot.id());
            if (risk.openOrders()>=bot.maxOpenOrders())
                throw new IllegalArgumentException("Maximale Zahl offener Orders ist erreicht.");
            if (risk.openPositions()>=bot.maxOpenPositions())
                throw new IllegalArgumentException("Maximale Zahl offener Positionen ist erreicht.");
            if (risk.exposure()+notional>Math.min(bot.budget(),bot.maxExposure())+0.000001)
                throw new IllegalArgumentException("Bot-Budget oder Exposure-Limit würde überschritten.");
            return;
        }
        List<OpenOrderRow> open=operations.loadOpenOrders(bot.id());
        List<OperationsRepository.PositionRow> positions=operations.loadPositions(bot.id());
        if (open.size()>=bot.maxOpenOrders())
            throw new IllegalArgumentException("Maximale Zahl offener Orders ist erreicht.");
        if (positions.size()>=bot.maxOpenPositions())
            throw new IllegalArgumentException("Maximale Zahl offener Positionen ist erreicht.");
        double exposure=open.stream().filter(row->row.orderSide()==OrderSide.BUY)
                .mapToDouble(row->row.price()*row.quantity()).sum()
                +positions.stream().mapToDouble(OperationsRepository.PositionRow::buyAmount).sum();
        if (exposure+notional>Math.min(bot.budget(),bot.maxExposure())+0.000001)
            throw new IllegalArgumentException("Bot-Budget oder Exposure-Limit würde überschritten.");
    }

    private static boolean terminalCancellation(OrderStatus status) {
        return status==OrderStatus.CANCELED || status==OrderStatus.REJECTED
                || status==OrderStatus.DONE_FOR_DAY || status==OrderStatus.FILLED_AND_CANCELED;
    }
    private static boolean definiteRejection(FusionApiException ex) {
        return ex.getStatusCode()>=400 && ex.getStatusCode()<500 && ex.getStatusCode()!=408;
    }
    private static double number(String value) {
        try { double parsed=Double.parseDouble(value); return Double.isFinite(parsed)?parsed:Double.NaN; }
        catch (RuntimeException ex) { return Double.NaN; }
    }
    private static boolean positive(double value) { return Double.isFinite(value)&&value>0; }

    public record Request(String currency,OrderSide side,OrderType type,double quantity,
            double limitPrice,double marketPrice,String positionId) { }
    public record PreparedOrder(Request request,String priceText,String quantityText,double notional) { }
    public record Submission(String orderId,BotProfile.Mode mode,OrderSide side,OrderType type,
            String status) { }
    public record CancelResult(boolean clean,String message) { }
}
