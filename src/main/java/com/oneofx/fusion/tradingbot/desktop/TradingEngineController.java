package com.oneofx.fusion.tradingbot.desktop;

import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import com.oneofx.fusion.tradingbot.TradingMain.oneofx;

/** Startet und stoppt die vorhandene Trading-Schleife außerhalb des UI-Threads. */
public final class TradingEngineController {

    public enum State {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING,
        FAILED
    }

    private volatile State state = State.STOPPED;
    private volatile Thread engineThread;
    private volatile TradingExecution execution;

    public synchronized void start(Consumer<State> listener) {
        try {
            start(new BotRepository().loadSelected(), listener);
        } catch (Exception ex) {
            throw new IllegalStateException("Aktiver Bot konnte nicht geladen werden.", ex);
        }
    }

    public synchronized void start(BotProfile bot, Consumer<State> listener) {
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(bot, "bot");
        if (state != State.STOPPED) return;
        updateState(State.STARTING, listener);

        execution = bot.mode() == BotProfile.Mode.PAPER
                ? new PaperTradingEngine(bot)
                : new TradingExecution() {
                    @Override public void run() { oneofx.main(new String[0]); }
                    @Override public void stop() { oneofx.stop(); }
                };

        engineThread = new Thread(() -> {
            updateState(State.RUNNING, listener);
            try {
                execution.run();
                updateState(State.STOPPED, listener);
            } catch (Throwable error) {
                error.printStackTrace(System.err);
                updateState(State.FAILED, listener);
            } finally {
                execution = null;
            }
        }, bot.mode() == BotProfile.Mode.PAPER
                ? "oneofx-paper-engine" : "oneofx-live-engine");
        engineThread.setDaemon(true);
        engineThread.start();
    }

    public synchronized void stop(Consumer<State> listener) {
        Objects.requireNonNull(listener, "listener");
        if (state != State.RUNNING && state != State.STARTING) return;
        updateState(State.STOPPING, listener);
        TradingExecution active = execution;
        if (active != null) active.stop();
    }

    public State getState() {
        return state;
    }

    public boolean canStart() {
        return state == State.STOPPED;
    }

    private void updateState(State next, Consumer<State> listener) {
        state = next;
        SwingUtilities.invokeLater(() -> listener.accept(next));
    }
}
