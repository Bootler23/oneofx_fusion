package com.oneofx.fusion.tradingbot.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.oneofx.fusion.client.FusionApiClient;
import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.client.model.CandlestickInterval;

/**
 * Ermittelt die Marktfreigabe aus MACD und StochRSI auf dem 1D-Timeframe.
 *
 * <p>Die laufende Tageskerze wird bewusst einbezogen und die Berechnung wird
 * einmal pro Minute erneuert. Dadurch können sich die Signale innerhalb eines
 * Tages ändern.</p>
 */
public final class MarketRegimeService {

    static final int FAST_PERIOD = 12;
    static final int SLOW_PERIOD = 26;
    static final int SIGNAL_PERIOD = 9;
    static final int RSI_PERIOD = 14;
    static final int STOCH_PERIOD = 14;
    static final int K_PERIOD = 3;
    static final int D_PERIOD = 3;
    static final int DAILY_CANDLE_LIMIT = 300;
    static final int MIN_DAILY_BARS = 60;

    private static final Duration CACHE_TTL = Duration.ofMinutes(1);
    private static final MarketRegimeService INSTANCE = new MarketRegimeService(Clock.systemUTC());

    private final Clock clock;
    private final Map<String, CachedSnapshot> cache = new ConcurrentHashMap<>();

    MarketRegimeService(Clock clock) {
        this.clock = clock;
    }

    public static MarketRegimeService getInstance() {
        return INSTANCE;
    }

    public Snapshot getSnapshot(String currency, FusionApiClient client) {
        Instant now = clock.instant();
        CachedSnapshot cached = cache.get(currency);
        if (cached != null && Duration.between(cached.loadedAt(), now).compareTo(CACHE_TTL) < 0) {
            return cached.snapshot();
        }

        Snapshot snapshot;
        try {
            List<Candlestick> daily = client.getCandlestickBars(
                    currency, CandlestickInterval.DAILY, DAILY_CANDLE_LIMIT, null, null);
            List<Double> dailyCloses = extractCurrentDailyCloses(daily, now);
            snapshot = calculateSnapshot(dailyCloses);
        } catch (Exception ex) {
            System.err.println("1D-Marktfilter konnte fuer " + currency
                    + " nicht berechnet werden: " + ex.getMessage());
            snapshot = Snapshot.unknown();
        }

        cache.put(currency, new CachedSnapshot(snapshot, now));
        // Bei jeder minütlichen Neuberechnung die Werte sichtbar protokollieren.
        System.out.println("1D-Marktfilter " + currency + ": " + snapshot);
        return snapshot;
    }

    /**
     * Sortiert die Tageskerzen und liefert den jüngsten lückenlosen Abschnitt.
     * Die aktuelle, noch laufende UTC-Tageskerze muss vorhanden sein.
     */
    static List<Double> extractCurrentDailyCloses(List<Candlestick> candles, Instant now) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }

        LocalDate currentUtcDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        Map<LocalDate, Double> dailyCloses = new TreeMap<>();
        for (Candlestick candle : candles) {
            if (candle == null || candle.getOpenTime() == null || candle.getClose() == null) {
                continue;
            }
            LocalDate date = Instant.ofEpochMilli(candle.getOpenTime())
                    .atZone(ZoneOffset.UTC).toLocalDate();
            if (date.isAfter(currentUtcDate)) {
                continue;
            }
            double close = Double.parseDouble(candle.getClose());
            if (Double.isFinite(close) && close > 0.0) {
                dailyCloses.put(date, close);
            }
        }

        if (!dailyCloses.containsKey(currentUtcDate)) {
            return List.of();
        }

        // Fehlende Tageskerzen dürfen kein scheinbar gültiges Signal erzeugen.
        List<Double> reversed = new ArrayList<>();
        LocalDate expectedDate = currentUtcDate;
        while (dailyCloses.containsKey(expectedDate)) {
            reversed.add(dailyCloses.get(expectedDate));
            expectedDate = expectedDate.minusDays(1);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    static Snapshot calculateSnapshot(List<Double> closes) {
        if (closes == null || closes.size() < MIN_DAILY_BARS) {
            return Snapshot.unknown();
        }

        double firstClose = closes.get(0);
        if (!Double.isFinite(firstClose) || firstClose <= 0.0) {
            return Snapshot.unknown();
        }

        double fast = firstClose;
        double slow = firstClose;
        double signal = 0.0;
        double macd = 0.0;
        double fastAlpha = 2.0 / (FAST_PERIOD + 1.0);
        double slowAlpha = 2.0 / (SLOW_PERIOD + 1.0);
        double signalAlpha = 2.0 / (SIGNAL_PERIOD + 1.0);

        for (int i = 1; i < closes.size(); i++) {
            double close = closes.get(i);
            if (!Double.isFinite(close) || close <= 0.0) {
                return Snapshot.unknown();
            }
            fast += fastAlpha * (close - fast);
            slow += slowAlpha * (close - slow);
            macd = fast - slow;
            signal += signalAlpha * (macd - signal);
        }

        StochValues stoch = calculateStochRsi(closes);
        Regime regime = classify(macd, signal, stoch.k(), stoch.d());
        return new Snapshot(regime, macd, signal, stoch.k(), stoch.d(), closes.size());
    }

    /** Berechnet StochRSI(14,14,3,3) mit Wilders RSI-Glättung. */
    static StochValues calculateStochRsi(List<Double> closes) {
        int size = closes.size();
        double[] rsi = new double[size];
        double[] rawStoch = new double[size];
        double[] kValues = new double[size];
        Arrays.fill(rsi, Double.NaN);
        Arrays.fill(rawStoch, Double.NaN);
        Arrays.fill(kValues, Double.NaN);

        double averageGain = 0.0;
        double averageLoss = 0.0;
        for (int i = 1; i <= RSI_PERIOD; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            averageGain += Math.max(change, 0.0);
            averageLoss += Math.max(-change, 0.0);
        }
        averageGain /= RSI_PERIOD;
        averageLoss /= RSI_PERIOD;
        rsi[RSI_PERIOD] = rsiValue(averageGain, averageLoss);

        for (int i = RSI_PERIOD + 1; i < size; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            double gain = Math.max(change, 0.0);
            double loss = Math.max(-change, 0.0);
            averageGain = ((averageGain * (RSI_PERIOD - 1)) + gain) / RSI_PERIOD;
            averageLoss = ((averageLoss * (RSI_PERIOD - 1)) + loss) / RSI_PERIOD;
            rsi[i] = rsiValue(averageGain, averageLoss);
        }

        int stochStart = RSI_PERIOD + STOCH_PERIOD - 1;
        for (int i = stochStart; i < size; i++) {
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (int j = i - STOCH_PERIOD + 1; j <= i; j++) {
                minimum = Math.min(minimum, rsi[j]);
                maximum = Math.max(maximum, rsi[j]);
            }
            rawStoch[i] = maximum == minimum
                    ? 0.5
                    : (rsi[i] - minimum) / (maximum - minimum);
        }

        int kStart = stochStart + K_PERIOD - 1;
        for (int i = kStart; i < size; i++) {
            kValues[i] = average(rawStoch, i, K_PERIOD);
        }

        int dStart = kStart + D_PERIOD - 1;
        if (size <= dStart) {
            return StochValues.unknown();
        }
        int last = size - 1;
        return new StochValues(kValues[last], average(kValues, last, D_PERIOD));
    }

    private static double rsiValue(double averageGain, double averageLoss) {
        if (averageGain == 0.0 && averageLoss == 0.0) return 50.0;
        if (averageLoss == 0.0) return 100.0;
        if (averageGain == 0.0) return 0.0;
        double relativeStrength = averageGain / averageLoss;
        return 100.0 - (100.0 / (1.0 + relativeStrength));
    }

    private static double average(double[] values, int endInclusive, int period) {
        double sum = 0.0;
        for (int i = endInclusive - period + 1; i <= endInclusive; i++) {
            if (!Double.isFinite(values[i])) return Double.NaN;
            sum += values[i];
        }
        return sum / period;
    }

    static Regime classify(double macd, double signal, double stochK, double stochD) {
        if (!Double.isFinite(macd) || !Double.isFinite(signal)
                || !Double.isFinite(stochK) || !Double.isFinite(stochD)) {
            return Regime.UNKNOWN;
        }
        // Der bereits vereinbarte Nulllinien-Ausstieg bleibt erhalten.
        if (macd <= 0.0) {
            return Regime.EXIT;
        }
        if (macd > signal && stochK > stochD) {
            return Regime.BUY_ALLOWED;
        }
        return Regime.BUY_PAUSED;
    }

    public enum Regime {
        BUY_ALLOWED,
        BUY_PAUSED,
        EXIT,
        UNKNOWN
    }

    public record Snapshot(Regime regime, double macd, double signal,
            double stochK, double stochD, int barCount) {
        static Snapshot unknown() {
            return new Snapshot(Regime.UNKNOWN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, 0);
        }

        @Override
        public String toString() {
            if (regime == Regime.UNKNOWN) {
                return "UNKNOWN";
            }
            return String.format(
                    "%s (MACD=%.4f, Signal=%.4f, Histogramm=%.4f, K=%.4f, D=%.4f, Kerzen=%d)",
                    regime, macd, signal, macd - signal, stochK, stochD, barCount);
        }
    }

    record StochValues(double k, double d) {
        static StochValues unknown() {
            return new StochValues(Double.NaN, Double.NaN);
        }
    }

    private record CachedSnapshot(Snapshot snapshot, Instant loadedAt) {
    }
}
