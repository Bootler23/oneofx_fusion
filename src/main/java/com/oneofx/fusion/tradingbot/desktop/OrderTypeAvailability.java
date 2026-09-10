package com.oneofx.fusion.tradingbot.desktop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.oneofx.fusion.client.model.OrderType;
import com.oneofx.fusion.tradingbot.Database.dbUrl;

/** Liest und kombiniert die von Fusion gemeldeten Ordertypen je Handelspaar. */
public final class OrderTypeAvailability {
    private static final List<OrderType> BUY_TYPES = List.of(
            OrderType.LIMIT, OrderType.MARKET, OrderType.STOP_LIMIT);
    private static final List<OrderType> SELL_TYPES = List.of(
            OrderType.MARKET, OrderType.LIMIT, OrderType.STOP_MARKET);

    public Availability forPair(String currency) throws SQLException {
        String normalized = CurrencySettings.normalizeCurrency(currency);
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(
                "SELECT supportedOrderTypes FROM tradingRules WHERE currency=?")) {
            ps.setString(1, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new Availability(EnumSet.allOf(OrderType.class),
                        List.of(normalized));
                return new Availability(parse(rs.getString(1)), List.of(normalized));
            }
        }
    }

    /**
     * Liefert die Schnittmenge der Ordertypen aller Paare, auf die eine
     * Baseconfig beziehungsweise ein Config Pool tatsächlich angewendet wird.
     */
    public Availability forConfiguration(long botId, Long poolId) throws SQLException {
        String sql = "SELECT b.currency,r.supportedOrderTypes FROM botPairSettings b "
                + "LEFT JOIN tradingRules r ON r.currency=b.currency "
                + "WHERE b.bot_id=? AND b.archived=0 AND "
                + (poolId == null ? "b.config_pool_id IS NULL" : "b.config_pool_id=?")
                + " ORDER BY b.currency";
        EnumSet<OrderType> intersection = EnumSet.allOf(OrderType.class);
        List<String> currencies = new ArrayList<>();
        try (Connection con = open(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, botId);
            if (poolId != null) ps.setLong(2, poolId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    currencies.add(rs.getString(1));
                    intersection.retainAll(parse(rs.getString(2)));
                }
            }
        }
        return new Availability(intersection, currencies);
    }

    public void validatePair(String currency, BotBaseConfig config) throws SQLException {
        Availability availability = forPair(currency);
        validate(availability, config, "Handelspaar " + CurrencySettings.normalizeCurrency(currency));
    }

    public void validateConfiguration(long botId, Long poolId, BotBaseConfig config)
            throws SQLException {
        Availability availability = forConfiguration(botId, poolId);
        if (availability.currencies().isEmpty()) return;
        validate(availability, config, String.join(", ", availability.currencies()));
    }

    public void validateBot(long botId, BaseConfigRepository configs) throws SQLException {
        for (CurrencySettings pair : new CurrencySettingsRepository().loadAll(botId)) {
            validatePair(pair.currency(), configs.loadEffective(botId, pair.currency()));
        }
    }

    private static void validate(Availability availability, BotBaseConfig config, String scope) {
        OrderType buy = type(config.buyOrderType());
        OrderType sell = type(config.sellOrderType());
        List<String> invalid = new ArrayList<>();
        if (!availability.supported().contains(buy)) invalid.add("Kauf: " + label(buy));
        if (!availability.supported().contains(sell)) invalid.add("Verkauf: " + label(sell));
        if (!invalid.isEmpty()) throw new IllegalArgumentException(
                scope + " unterstützt " + String.join(" und ", invalid) + " nicht. Erlaubt: "
                        + availability.display());
    }

    public static List<String> buyChoices(Availability availability) {
        return choices(BUY_TYPES, availability.supported());
    }

    public static List<String> sellChoices(Availability availability) {
        return choices(SELL_TYPES, availability.supported());
    }

    public static List<OrderType> terminalChoices(Availability availability) {
        return List.of(OrderType.LIMIT, OrderType.MARKET).stream()
                .filter(availability.supported()::contains).toList();
    }

    public static String label(OrderType type) {
        return switch (type) {
            case LIMIT -> "Limit";
            case MARKET -> "Market";
            case STOP_LIMIT -> "Stop-Limit";
            case STOP_MARKET -> "Stop-Market";
        };
    }

    private static List<String> choices(List<OrderType> candidates, Set<OrderType> supported) {
        return candidates.stream().filter(supported::contains).map(Enum::name).toList();
    }

    private static OrderType type(String value) {
        try { return OrderType.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Unbekannter Ordertyp: " + value); }
    }

    private static EnumSet<OrderType> parse(String value) {
        EnumSet<OrderType> result = EnumSet.noneOf(OrderType.class);
        if (value != null) for (String token : value.split(",")) {
            try { result.add(OrderType.valueOf(token.trim().toUpperCase(Locale.ROOT))); }
            catch (RuntimeException ignored) { }
        }
        // Alte Installationen besitzen keine Katalogangabe. Dort bleibt das
        // bisherige Verhalten erhalten, bis das Paar erneut geprüft wird.
        return result.isEmpty() ? EnumSet.allOf(OrderType.class) : result;
    }

    private static Connection open() throws SQLException {
        return com.oneofx.fusion.tradingbot.Database.SQLiteConnectionFactory.open(dbUrl.getoneOfX());
    }

    public record Availability(Set<OrderType> supported, List<String> currencies) {
        public Availability {
            supported = supported == null ? Set.of() : Set.copyOf(supported);
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
        }

        public String display() {
            if (supported.isEmpty()) return "keine gemeinsamen Ordertypen";
            return supported.stream().sorted().map(OrderTypeAvailability::label)
                    .collect(Collectors.joining(", "));
        }
    }
}
