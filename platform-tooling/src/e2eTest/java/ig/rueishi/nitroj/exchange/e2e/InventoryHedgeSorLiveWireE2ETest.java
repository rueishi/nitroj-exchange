package ig.rueishi.nitroj.exchange.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.OrderStatus;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyRegistry;
import ig.rueishi.nitroj.exchange.execution.FeeSchedule;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import ig.rueishi.nitroj.exchange.execution.SmartOrderRoutingExecution;
import ig.rueishi.nitroj.exchange.gateway.ExecutionRouter;
import ig.rueishi.nitroj.exchange.gateway.OrderCommandHandler;
import ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentTerminalReason;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.order.OrderState;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import ig.rueishi.nitroj.exchange.simulator.BinanceExchangeSimulator;
import ig.rueishi.nitroj.exchange.simulator.BinanceSimulatorScenarios;
import ig.rueishi.nitroj.exchange.simulator.CoinbaseExchangeSimulator;
import ig.rueishi.nitroj.exchange.simulator.SimulatorConfig;
import ig.rueishi.nitroj.exchange.strategy.InventoryHedgeStrategy;
import ig.rueishi.nitroj.exchange.strategy.StrategyContextImpl;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * TASK-422 live-wire proof for inventory hedging through SmartOrderRouting.
 *
 * <p>The fixture runs the production {@link InventoryHedgeStrategy} and
 * {@link SmartOrderRoutingExecution} with both local venue simulators connected.
 * It verifies fee-aware initial routing, simulator-routed child execution,
 * market-data-driven re-slice cancel-and-resubmit, minimum re-slice interval
 * enforcement, and deterministic re-slice failure terminal behavior. This is a
 * local simulator gate only; real venue QA/UAT, external network access, and
 * REST recovery remain outside this task.</p>
 */
@Tag("E2E")
final class InventoryHedgeSorLiveWireE2ETest {
    private static final long BTC = Ids.SCALE;
    private static final long PRICE = 65_000L * Ids.SCALE;

    @Test
    void initialSliceUsesFeeScoringAndRoutesThroughBinanceSimulator() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (LiveWire live = liveWire(false, keyPair)) {
            live.logon();
            live.seedInitialFeeScoredBook();
            live.triggerLongExposure();
            final long parentOrderId = live.strategy.activeParentOrderId();

            assertThat(live.orders.getOrder(parentOrderId + 1L).venueId()).isEqualTo(Ids.VENUE_BINANCE);
            live.route(parentOrderId + 1L);
            live.binanceClient.readMessageContaining("150=F");
            live.driveBinance(parentOrderId + 1L, Side.SELL);

            assertThat(live.parents.lookup(parentOrderId).status()).isEqualTo(ParentOrderState.DONE);
            assertThat(live.sor.childSubmissions()).isEqualTo(1L);
            assertThat(live.terminalReasons).containsExactly(ParentTerminalReason.COMPLETED);
        }
    }

    @Test
    void simulatorDrivenDepthShiftReSliceCancelsThenResubmitsToCoinbase() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (LiveWire live = liveWire(false, keyPair)) {
            live.logon();
            live.seedInitialFeeScoredBook();
            live.triggerLongExposure();
            final long parentOrderId = live.strategy.activeParentOrderId();
            assertThat(live.orders.getOrder(parentOrderId + 1L).venueId()).isEqualTo(Ids.VENUE_BINANCE);

            live.cluster.time = 2_000L;
            live.applyBook(Ids.VENUE_COINBASE, EntryType.BID, PRICE + 2_000L * Ids.SCALE);
            live.engine.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, live.cluster.time);

            assertThat(live.orders.getOrder(parentOrderId + 1L).status()).isEqualTo(OrderStatus.PENDING_CANCEL);
            assertThat(live.orders.getOrder(parentOrderId + 101L).venueId()).isEqualTo(Ids.VENUE_COINBASE);
            assertThat(live.sor.resliceSuccesses()).isEqualTo(1L);
            live.route(parentOrderId + 101L);
            live.coinbaseClient.readMessageContaining("150=F");
        }
    }

    @Test
    void minimumReSliceIntervalSkipsSecondDepthShift() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (LiveWire live = liveWire(false, keyPair)) {
            live.logon();
            live.seedInitialFeeScoredBook();
            live.triggerLongExposure();
            final long parentOrderId = live.strategy.activeParentOrderId();

            live.cluster.time = 2_000L;
            live.applyBook(Ids.VENUE_COINBASE, EntryType.BID, PRICE + 2_000L * Ids.SCALE);
            live.engine.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, live.cluster.time);
            live.cluster.time = 2_500L;
            live.applyBook(Ids.VENUE_BINANCE, EntryType.BID, PRICE + 3_000L * Ids.SCALE);
            live.engine.onMarketDataTick(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USD, live.cluster.time);

            assertThat(live.sor.resliceSuccesses()).isEqualTo(1L);
            assertThat(live.sor.resliceIntervalSkips()).isEqualTo(1L);
            assertThat(live.orders.getOrder(parentOrderId + 201L)).isNull();
        }
    }

    @Test
    void reSliceFailureTerminatesParentAndAppliesFailureCooldown() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (LiveWire live = liveWire(false, keyPair)) {
            live.logon();
            live.seedInitialFeeScoredBook();
            live.triggerLongExposure();
            final long parentOrderId = live.strategy.activeParentOrderId();

            live.risk.reject = true;
            live.cluster.time = 2_000L;
            live.applyBook(Ids.VENUE_COINBASE, EntryType.BID, PRICE + 2_000L * Ids.SCALE);
            live.engine.onMarketDataTick(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, live.cluster.time);

            assertThat(live.parents.lookup(parentOrderId).status()).isEqualTo(ParentOrderState.FAILED);
            assertThat(live.parents.lookup(parentOrderId).terminalReasonCode())
                .isEqualTo(ParentOrderState.REASON_RESLICE_FAILED);
            assertThat(live.sor.resliceFailures()).isEqualTo(1L);
            assertThat(live.strategy.activeParentOrderId()).isZero();
            assertThat(live.strategy.cooldownUntilMicros()).isEqualTo(live.cluster.time + 5_000L);
        }
    }

    private static LiveWire liveWire(final boolean rejectRisk, final KeyPair keyPair) throws Exception {
        final Fixture fixture = fixture(rejectRisk);
        final InventoryHedgeStrategy strategy = new InventoryHedgeStrategy(config());
        strategy.init(strategyContext(fixture));
        fixture.engine.setParentCallbackSink(decoder -> {
            fixture.terminalReasons.add(decoder.terminalReason());
            strategy.onParentTerminal(decoder);
        });
        return new LiveWire(coinbase(CoinbaseExchangeSimulator.FillMode.IMMEDIATE),
            binance(BinanceExchangeSimulator.FillMode.IMMEDIATE, keyPair), fixture, strategy, keyPair);
    }

    private static Fixture fixture(final boolean rejectRisk) {
        final InternalMarketView marketView = new InternalMarketView(new int[] {1, 2}, new int[] {Ids.INSTRUMENT_BTC_USD});
        final ParentOrderRegistry parents = new ParentOrderRegistry(64, 128);
        final OrderManagerImpl orders = new OrderManagerImpl();
        final RiskStub risk = new RiskStub(rejectRisk);
        final PortfolioStub portfolio = new PortfolioStub();
        final RecordingCluster cluster = new RecordingCluster();
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[1024]);
        final SmartOrderRoutingExecution sor = new SmartOrderRoutingExecution(1L, 1_000L, 16, fees());
        final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry(16, 16);
        registry.register(sor);
        registry.allowCompatibility(Ids.STRATEGY_INVENTORY_HEDGE, ExecutionStrategyIds.SMART_ORDER_ROUTING);
        final RecordingTimerScheduler timerScheduler = new RecordingTimerScheduler();
        final DualVenueIdRegistry idRegistry = new DualVenueIdRegistry();
        final ExecutionStrategyEngine engine = new ExecutionStrategyEngine(registry, new ExecutionStrategyContext(
            marketView,
            risk,
            orders,
            parents,
            commandBuffer,
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            () -> cluster.time,
            timerScheduler,
            idRegistry,
            counters()));
        timerScheduler.engine = engine;
        engine.initRegisteredStrategies();
        return new Fixture(marketView, parents, orders, risk, portfolio, cluster, commandBuffer, sor, engine,
            idRegistry, new ArrayList<>());
    }

    private static FeeSchedule fees() {
        final FeeSchedule fees = new FeeSchedule();
        fees.set(Ids.VENUE_COINBASE, 0L, 100L);
        fees.set(Ids.VENUE_BINANCE, 0L, 0L);
        return fees;
    }

    private static StrategyContextImpl strategyContext(final Fixture fixture) {
        return new StrategyContextImpl(
            fixture.marketView,
            fixture.risk,
            fixture.orders,
            fixture.portfolio,
            new RecoveryStub(),
            fixture.engine,
            fixture.cluster.proxy,
            new UnsafeBuffer(new byte[1024]),
            new MessageHeaderEncoder(),
            new NewOrderCommandEncoder(),
            new CancelOrderCommandEncoder(),
            fixture.idRegistry,
            counters());
    }

    private static InventoryHedgeStrategy.Config config() {
        return new InventoryHedgeStrategy.Config(
            Ids.INSTRUMENT_BTC_USD,
            7,
            new int[] {Ids.VENUE_COINBASE, Ids.VENUE_BINANCE},
            InventoryHedgeStrategy.ThresholdMode.BASE_QUANTITY,
            BTC + BTC / 2L,
            InventoryHedgeStrategy.ExposureMode.FILLED_ONLY,
            BTC,
            1_000L,
            5_000L,
            ExecutionStrategyIds.SMART_ORDER_ROUTING,
            250L);
    }

    private static void encodeOrder(final Fixture fixture, final long clOrdId) {
        final OrderState order = fixture.orders.getOrder(clOrdId);
        assertThat(order).isNotNull();
        new NewOrderCommandEncoder()
            .wrapAndApplyHeader(fixture.commandBuffer, 0, new MessageHeaderEncoder())
            .clOrdId(order.clOrdId())
            .venueId(order.venueId())
            .instrumentId(order.instrumentId())
            .side(Side.get(order.side()))
            .ordType(ig.rueishi.nitroj.exchange.messages.OrdType.LIMIT)
            .timeInForce(TimeInForce.IOC)
            .priceScaled(order.priceScaled())
            .qtyScaled(order.qtyScaled())
            .strategyId((short)order.strategyId())
            .parentOrderId(order.parentOrderId());
    }

    private static void routeOrder(
        final Fixture fixture,
        final CrossVenueArbLiveWireE2ETest.FixClient coinbaseClient,
        final CrossVenueArbLiveWireE2ETest.FixClient binanceClient,
        final long childClOrdId) {
        encodeOrder(fixture, childClOrdId);
        final SocketExecutionRouter router = new SocketExecutionRouter(coinbaseClient, binanceClient);
        new OrderCommandHandler(router).onFragment(fixture.commandBuffer, 0,
            MessageHeaderEncoder.ENCODED_LENGTH + NewOrderCommandEncoder.BLOCK_LENGTH, null);
        assertThat(router.sentMessages).isEqualTo(1);
    }

    private static void driveReports(
        final Fixture fixture,
        final List<? extends ReportView> reports,
        final long childClOrdId,
        final int venueId,
        final Side side) {
        final List<? extends ReportView> matching = reports.stream()
            .filter(report -> Long.toString(childClOrdId).equals(report.clOrdId()))
            .toList();
        assertThat(matching).isNotEmpty();
        for (ReportView report : matching) {
            final ExecutionEventDecoder execution = execution(report, childClOrdId, venueId, side);
            final OrderState child = fixture.orders.getOrder(childClOrdId);
            final long parentOrderId = child == null ? 0L : child.parentOrderId();
            fixture.orders.onExecution(execution);
            fixture.engine.onChildExecution(execution, parentOrderId);
        }
    }

    private static ExecutionEventDecoder execution(
        final ReportView report,
        final long childClOrdId,
        final int venueId,
        final Side side) {
        final ExecType execType = switch (report.execType()) {
            case "0" -> ExecType.NEW;
            case "F" -> ExecType.FILL;
            case "8" -> ExecType.REJECTED;
            default -> ExecType.ORDER_STATUS;
        };
        final long fillQty = scale(report.lastQty());
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] venueOrderId = ("SIM-" + childClOrdId).getBytes(StandardCharsets.US_ASCII);
        final byte[] execId = ("sor-" + childClOrdId + '-' + venueId + '-' + report.execType())
            .getBytes(StandardCharsets.US_ASCII);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(childClOrdId)
            .venueId(venueId)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .execType(execType)
            .side(side)
            .fillPriceScaled(scale(report.lastPx()))
            .fillQtyScaled(fillQty)
            .cumQtyScaled(fillQty)
            .leavesQtyScaled(execType == ExecType.FILL ? 0L : BTC)
            .rejectCode(Math.max(report.rejectReason(), 0))
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1)
            .isFinal(execType == ExecType.FILL || execType == ExecType.REJECTED ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
            .putExecId(execId, 0, execId.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static MarketDataEventDecoder marketData(final int venueId, final EntryType entryType, final long price) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(entryType)
            .updateAction(UpdateAction.NEW)
            .priceScaled(price)
            .sizeScaled(10L * BTC)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        return decoder;
    }

    private static CoinbaseExchangeSimulator coinbase(final CoinbaseExchangeSimulator.FillMode fillMode)
        throws IOException {
        final CoinbaseExchangeSimulator simulator = CoinbaseExchangeSimulator.builder()
            .config(SimulatorConfig.builder()
                .port(freePort())
                .instrument("BTC-USD", 65_000.00, 65_001.00)
                .fillMode(fillMode)
                .build())
            .build();
        simulator.start();
        return simulator;
    }

    private static BinanceExchangeSimulator binance(final BinanceExchangeSimulator.FillMode fillMode, final KeyPair keyPair)
        throws IOException {
        final BinanceExchangeSimulator simulator = BinanceExchangeSimulator.builder()
            .config(BinanceSimulatorScenarios.defaultConfig().withPort(freePort()))
            .publicKeyBase64(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
            .fillMode(fillMode)
            .build();
        simulator.start();
        return simulator;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static long scale(final double value) {
        return Math.round(value * Ids.SCALE);
    }

    private static String decimal(final long scaled) {
        return Double.toString((double)scaled / Ids.SCALE);
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private record Fixture(
        InternalMarketView marketView,
        ParentOrderRegistry parents,
        OrderManagerImpl orders,
        RiskStub risk,
        PortfolioStub portfolio,
        RecordingCluster cluster,
        UnsafeBuffer commandBuffer,
        SmartOrderRoutingExecution sor,
        ExecutionStrategyEngine engine,
        DualVenueIdRegistry idRegistry,
        List<ParentTerminalReason> terminalReasons) {
    }

    private static final class LiveWire implements AutoCloseable {
        final CoinbaseExchangeSimulator coinbase;
        final BinanceExchangeSimulator binance;
        final CrossVenueArbLiveWireE2ETest.FixClient coinbaseClient;
        final CrossVenueArbLiveWireE2ETest.FixClient binanceClient;
        final Fixture fixture;
        final InventoryHedgeStrategy strategy;
        final KeyPair keyPair;
        final ParentOrderRegistry parents;
        final OrderManagerImpl orders;
        final RiskStub risk;
        final RecordingCluster cluster;
        final SmartOrderRoutingExecution sor;
        final ExecutionStrategyEngine engine;
        final List<ParentTerminalReason> terminalReasons;

        LiveWire(
            final CoinbaseExchangeSimulator coinbase,
            final BinanceExchangeSimulator binance,
            final Fixture fixture,
            final InventoryHedgeStrategy strategy,
            final KeyPair keyPair) throws IOException {
            this.coinbase = coinbase;
            this.binance = binance;
            this.fixture = fixture;
            this.strategy = strategy;
            this.keyPair = keyPair;
            coinbaseClient = CrossVenueArbLiveWireE2ETest.FixClient.connect(coinbase.config().port(), "FIXT.1.1");
            binanceClient = CrossVenueArbLiveWireE2ETest.FixClient.connect(binance.config().port(), "FIX.4.4");
            parents = fixture.parents;
            orders = fixture.orders;
            risk = fixture.risk;
            cluster = fixture.cluster;
            sor = fixture.sor;
            engine = fixture.engine;
            terminalReasons = fixture.terminalReasons;
        }

        void logon() throws Exception {
            coinbaseClient.coinbaseLogon();
            binanceClient.binanceLogon(keyPair);
        }

        void seedInitialFeeScoredBook() {
            applyBook(Ids.VENUE_COINBASE, EntryType.BID, PRICE + 200L * Ids.SCALE);
            applyBook(Ids.VENUE_COINBASE, EntryType.ASK, PRICE);
            applyBook(Ids.VENUE_BINANCE, EntryType.BID, PRICE + 100L * Ids.SCALE);
            applyBook(Ids.VENUE_BINANCE, EntryType.ASK, PRICE);
        }

        void applyBook(final int venueId, final EntryType entryType, final long price) {
            fixture.marketView.apply(marketData(venueId, entryType, price), cluster.time);
        }

        void triggerLongExposure() {
            fixture.portfolio.netQty[Ids.VENUE_COINBASE] = 2L * BTC;
            strategy.onPositionUpdate(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD, 2L * BTC, PRICE);
        }

        void route(final long childClOrdId) {
            routeOrder(fixture, coinbaseClient, binanceClient, childClOrdId);
        }

        void driveBinance(final long childClOrdId, final Side side) {
            driveReports(fixture, binance.executionReports().stream().map(BinanceReportView::new).toList(),
                childClOrdId, Ids.VENUE_BINANCE, side);
        }

        @Override
        public void close() throws Exception {
            try {
                coinbaseClient.close();
            } finally {
                try {
                    binanceClient.close();
                } finally {
                    try {
                        coinbase.close();
                    } finally {
                        binance.close();
                    }
                }
            }
        }
    }

    private static final class RecordingTimerScheduler implements ExecutionStrategyContext.TimerScheduler {
        ExecutionStrategyEngine engine;
        @Override public boolean scheduleTimer(final long correlationId, final long deadlineClusterMicros) {
            return scheduleTimer(correlationId, deadlineClusterMicros, ExecutionStrategyIds.SMART_ORDER_ROUTING);
        }
        @Override public boolean scheduleTimer(final long correlationId, final long deadlineClusterMicros, final int executionStrategyId) {
            return engine == null || engine.registerTimerOwner(correlationId, executionStrategyId);
        }
    }

    private static final class RecordingCluster {
        final Cluster proxy;
        long time = 1_000L;
        long logPosition = 90_000L;
        RecordingCluster() {
            proxy = (Cluster)Proxy.newProxyInstance(Cluster.class.getClassLoader(), new Class<?>[] {Cluster.class},
                (p, m, a) -> switch (m.getName()) {
                    case "time" -> time;
                    case "logPosition" -> logPosition++;
                    case "timeUnit" -> TimeUnit.MICROSECONDS;
                    case "offer" -> ((Number)a[2]).longValue();
                    case "scheduleTimer", "cancelTimer" -> true;
                    case "toString" -> "InventoryHedgeSorLiveWireCluster";
                    default -> null;
                });
        }
    }

    private static final class SocketExecutionRouter implements ExecutionRouter {
        private final CrossVenueArbLiveWireE2ETest.FixClient coinbaseClient;
        private final CrossVenueArbLiveWireE2ETest.FixClient binanceClient;
        private int sentMessages;
        SocketExecutionRouter(final CrossVenueArbLiveWireE2ETest.FixClient coinbaseClient, final CrossVenueArbLiveWireE2ETest.FixClient binanceClient) {
            this.coinbaseClient = coinbaseClient;
            this.binanceClient = binanceClient;
        }
        @Override public void routeNewOrder(final NewOrderCommandDecoder command) {
            sentMessages++;
            final boolean binance = command.venueId() == Ids.VENUE_BINANCE;
            final CrossVenueArbLiveWireE2ETest.FixClient client = binance ? binanceClient : coinbaseClient;
            try {
                client.send("D", Map.of(
                    "49", binance ? "NITROJEX" : "TEST_SENDER",
                    "56", binance ? "SPOT" : "TEST_TARGET",
                    "11", Long.toString(command.clOrdId()),
                    "55", binance ? "BTCUSDT" : "BTC-USD",
                    "54", command.side() == Side.BUY ? "1" : "2",
                    "44", decimal(command.priceScaled()),
                    "38", decimal(command.qtyScaled()),
                    "40", "2",
                    "59", command.timeInForce() == TimeInForce.IOC ? "3" : "1"));
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        }
        @Override public void routeCancel(final CancelOrderCommandDecoder command) { }
        @Override public void routeReplace(final ReplaceOrderCommandDecoder command) { }
        @Override public void routeStatusQuery(final OrderStatusQueryCommandDecoder command) { }
    }

    private interface ReportView {
        String clOrdId();
        String execType();
        double lastPx();
        double lastQty();
        int rejectReason();
    }
    private record BinanceReportView(BinanceExchangeSimulator.ExecutionReport report) implements ReportView {
        @Override public String clOrdId() { return report.clOrdId(); }
        @Override public String execType() { return report.execType(); }
        @Override public double lastPx() { return report.lastPx(); }
        @Override public double lastQty() { return report.lastQty(); }
        @Override public int rejectReason() { return report.rejectReason(); }
    }

    private static final class RiskStub implements RiskEngine {
        boolean reject;
        RiskStub(final boolean reject) { this.reject = reject; }
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) {
            return reject ? RiskDecision.REJECT_MAX_NOTIONAL : RiskDecision.APPROVED;
        }
        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0L; }
        @Override public void activateKillSwitch(final String reason) { }
        @Override public void deactivateKillSwitch() { }
        @Override public boolean isKillSwitchActive() { return false; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    private static final class PortfolioStub implements PortfolioEngine {
        final long[] netQty = new long[Ids.MAX_VENUES + 1];
        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) { return netQty[venueId]; }
        @Override public long getAvgEntryPriceScaled(final int venueId, final int instrumentId) { return 0L; }
        @Override public long unrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { return 0L; }
        @Override public void adjustPosition(final int venueId, final int instrumentId, final double balanceUnscaled) { }
        @Override public long getTotalRealizedPnlScaled() { return 0L; }
        @Override public long getTotalUnrealizedPnlScaled() { return 0L; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void archiveDailyPnl(final Publication egressPublication) { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void resetAll() { }
    }

    private static final class RecoveryStub implements RecoveryCoordinator {
        @Override public void onVenueStatus(final VenueStatusEventDecoder decoder) { }
        @Override public void onBalanceResponse(final BalanceQueryResponseDecoder decoder) { }
        @Override public void onTimer(final long correlationId, final long timestamp) { }
        @Override public boolean isInRecovery(final int venueId) { return false; }
        @Override public void reconcileOrder(final ExecutionEventDecoder decoder) { }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetAll() { }
        @Override public void setCluster(final Cluster cluster) { }
    }

    private static final class DualVenueIdRegistry implements IdRegistry {
        @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
        @Override public int instrumentId(final CharSequence symbol) { return Ids.INSTRUMENT_BTC_USD; }
        @Override public String symbolOf(final int instrumentId) { return "BTC-USD"; }
        @Override public String venueNameOf(final int venueId) { return venueId == Ids.VENUE_BINANCE ? "BINANCE" : "COINBASE"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }
}
