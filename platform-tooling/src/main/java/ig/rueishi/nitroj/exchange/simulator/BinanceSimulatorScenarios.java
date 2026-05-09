package ig.rueishi.nitroj.exchange.simulator;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Binance simulator scenario catalog and deterministic defaults.
 *
 * <p>Responsibility: centralizes TASK-406 scenario behavior for local Binance
 * testing: accepted orders, rejects, partial fills, delayed fills, disconnects,
 * and deterministic default market configuration. Role in system: unit tests
 * and future live-wire tests configure {@link BinanceExchangeSimulator} through
 * these defaults instead of depending on live Binance. Relationships:
 * {@link BinanceExchangeSimulator} records observable orders, market data, and
 * execution reports while this class owns the branching from fill mode to state
 * transition. Lifecycle: one instance is owned by a simulator; static defaults
 * are immutable test fixtures. Design intent: make simulator behavior repeatable
 * and explicit, while keeping all scenario code out of gateway production paths.</p>
 */
public final class BinanceSimulatorScenarios {
    public static final String DEFAULT_API_KEY = "binance-api-key";
    public static final String DEFAULT_PUBLIC_KEY_BASE64 =
        "MCowBQYDK2VwAyEAjAyvxdIHShFlizcNu2g6xhGLgFA31V/HRfdb8oKyBTU=";

    private final BinanceExchangeSimulator simulator;
    private final ScheduledExecutorService scheduler;

    BinanceSimulatorScenarios(
        final BinanceExchangeSimulator simulator,
        final ScheduledExecutorService scheduler) {

        this.simulator = simulator;
        this.scheduler = scheduler;
    }

    public static SimulatorConfig defaultConfig() {
        return SimulatorConfig.builder()
            .port(19902)
            .logDir("./build/test-binance-simulator-fix")
            .marketDataIntervalMs(100)
            .fillDelayMs(50)
            .senderCompId("SPOT")
            .targetCompId("NITROJEX")
            .requiredPassword("")
            .instrument("BTCUSDT", 65_000.00, 65_001.00)
            .fillMode(CoinbaseExchangeSimulator.FillMode.IMMEDIATE)
            .build();
    }

    /**
     * Executes the configured scenario for a newly acknowledged order.
     *
     * @param order accepted simulator order
     */
    public void applyNewOrderScenario(final SimulatorOrderBook.SimOrder order) {
        switch (simulator.fillMode()) {
            case IMMEDIATE -> simulator.recordFill(order, order.limitPrice(), order.qty(), true);
            case PARTIAL_THEN_FULL -> {
                final double halfQty = order.qty() / 2.0;
                simulator.recordFill(order, order.limitPrice(), halfQty, false);
                scheduler.schedule(
                    () -> simulator.recordFill(order, order.limitPrice(), order.qty() - halfQty, true),
                    100,
                    TimeUnit.MILLISECONDS);
            }
            case REJECT_ALL -> simulator.recordReject(order, 0, "Binance simulator reject");
            case NO_FILL -> {
                // Order remains pending for cancel, reconnect, and recovery tests.
            }
            case DELAYED_FILL -> scheduler.schedule(
                () -> simulator.recordFill(order, order.limitPrice(), order.qty(), true),
                simulator.config().fillDelayMs(),
                TimeUnit.MILLISECONDS);
            case DISCONNECT_ON_FILL -> scheduleDisconnect(0);
        }
    }

    public void scheduleDisconnect(final long delayMs) {
        scheduleDisconnect(delayMs, false);
    }

    public void scheduleDisconnect(final long delayMs, final boolean cancelAllOnLogout) {
        scheduler.schedule(() -> simulator.markDisconnected(cancelAllOnLogout), delayMs, TimeUnit.MILLISECONDS);
    }
}
