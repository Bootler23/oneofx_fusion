package com.oneofx.fusion.tradingbot.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.oneofx.fusion.client.model.Candlestick;
import com.oneofx.fusion.tradingbot.service.MarketRegimeService.Regime;

public class MarketRegimeServiceTest {

    @Test
    public void verwendetAuchDieLaufendeTageskerzeUndSortiertDieDaten() {
        List<Candlestick> daily = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 7; i++) {
            daily.add(candle(start.plusDays(i), i + 1.0));
        }
        Collections.reverse(daily);

        List<Double> closes = MarketRegimeService.extractCurrentDailyCloses(
                daily, Instant.parse("2026-01-07T12:00:00Z"));

        assertEquals(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0), closes);
    }

    @Test
    public void sperrtBeiFehlenderAktuellerTageskerze() {
        List<Candlestick> daily = List.of(
                candle(LocalDate.of(2026, 1, 1), 1.0),
                candle(LocalDate.of(2026, 1, 2), 2.0));

        List<Double> closes = MarketRegimeService.extractCurrentDailyCloses(
                daily, Instant.parse("2026-01-04T12:00:00Z"));

        assertEquals(List.of(), closes);
    }

    @Test
    public void verwendetNachEinerDatenlueckeNurDenJuengstenAbschnitt() {
        List<Candlestick> daily = List.of(
                candle(LocalDate.of(2026, 1, 1), 1.0),
                candle(LocalDate.of(2026, 1, 3), 3.0),
                candle(LocalDate.of(2026, 1, 4), 4.0));

        List<Double> closes = MarketRegimeService.extractCurrentDailyCloses(
                daily, Instant.parse("2026-01-04T12:00:00Z"));

        assertEquals(List.of(3.0, 4.0), closes);
    }

    @Test
    public void klassifiziertDieKaufbedingungenEindeutig() {
        assertEquals(Regime.BUY_ALLOWED,
                MarketRegimeService.classify(2.0, 1.0, 0.7, 0.6));
        assertEquals(Regime.BUY_PAUSED,
                MarketRegimeService.classify(2.0, 2.0, 0.7, 0.6));
        assertEquals(Regime.BUY_PAUSED,
                MarketRegimeService.classify(2.0, 1.0, 0.6, 0.6));
        assertEquals(Regime.BUY_PAUSED,
                MarketRegimeService.classify(2.0, 1.0, 0.5, 0.6));
        assertEquals(Regime.EXIT,
                MarketRegimeService.classify(0.0, -1.0, 0.7, 0.6));
        assertEquals(Regime.UNKNOWN,
                MarketRegimeService.classify(Double.NaN, 1.0, 0.7, 0.6));
    }

    @Test
    public void berechnetStochRsiAufDerSkalaNullBisEins() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            closes.add(100.0 + i * 0.15 + Math.sin(i * 0.55) * 4.0);
        }

        MarketRegimeService.StochValues values =
                MarketRegimeService.calculateStochRsi(closes);

        assertTrue(Double.isFinite(values.k()));
        assertTrue(Double.isFinite(values.d()));
        assertTrue(values.k() >= 0.0 && values.k() <= 1.0);
        assertTrue(values.d() >= 0.0 && values.d() <= 1.0);
    }

    @Test
    public void fallendeKurseLoesenDenNulllinienAusstiegAus() {
        List<Double> falling = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            falling.add(200.0 - i);
        }

        assertEquals(Regime.EXIT,
                MarketRegimeService.calculateSnapshot(falling).regime());
    }

    private static Candlestick candle(LocalDate date, double close) {
        Candlestick candle = new Candlestick();
        candle.setTimestamp(date.atStartOfDay(ZoneOffset.UTC).toEpochSecond());
        candle.setOpen(Double.toString(close));
        candle.setHigh(Double.toString(close));
        candle.setLow(Double.toString(close));
        candle.setClose(Double.toString(close));
        candle.setVolume("1");
        return candle;
    }
}
