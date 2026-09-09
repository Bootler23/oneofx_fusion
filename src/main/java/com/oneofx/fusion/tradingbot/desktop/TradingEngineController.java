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

    public synchronized void start(Consumer<State> listener) {
        Objects.requireNonNull(listener, "listener");
        if (state != State.STOPPED) return;
        updateState(State.STARTING, listener);

        engineThread = new Thread(() -> {
            updateState(State.RUNNING, listener);
            try {
                oneofx.main(new String[0]);
                updateState(State.STOPPED, listener);
            } catch (Throwable error) {
                error.printStackTrace(System.err);
                updateState(State.FAILED, listener);
            }
        }, "oneofx-trading-engine");
        engineThread.setDaemon(true);
        engineThread.start();
    }

    public synchronized void stop(Consumer<State> listener) {
        Objects.requireNonNull(listener, "listener");
        if (state != State.RUNNING && state != State.STARTING) return;
        updateState(State.STOPPING, listener);
        oneofx.stop();
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
