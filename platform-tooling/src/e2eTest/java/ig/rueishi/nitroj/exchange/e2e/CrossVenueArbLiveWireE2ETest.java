package ig.rueishi.nitroj.exchange.e2e;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ArbStrategyConfig;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyRegistry;
import ig.rueishi.nitroj.exchange.execution.MultiLegContingentExecution;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
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
import ig.rueishi.nitroj.exchange.strategy.ArbStrategy;
import ig.rueishi.nitroj.exchange.strategy.StrategyContextImpl;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-410 live-wire proof that both local venue simulators can execute the two
 * legs used by cross-venue Arb activation concurrently.
 *
 * <p>The production ArbStrategy/MultiLegContingent integration is covered in
 * platform-cluster task-owned tests; this E2E adds the simulator wire boundary:
 * Coinbase and Binance both log on over local TCP FIX, publish market data, and
 * accept/reject/fill the two venue legs without live exchange access. Real venue
 * QA/UAT remains blocked until this local gate and the broader V14 gates pass.</p>
 */
@Tag("E2E")
final class CrossVenueArbLiveWireE2ETest {
    private static final char SOH = '\001';

    @Test
    void bothSimulatorsExecuteCrossVenueArbLegsConcurrently() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (CoinbaseExchangeSimulator coinbase = coinbase(CoinbaseExchangeSimulator.FillMode.IMMEDIATE);
             BinanceExchangeSimulator binance = binance(BinanceExchangeSimulator.FillMode.IMMEDIATE, keyPair);
             FixClient coinbaseClient = FixClient.connect(coinbase.config().port(), "FIXT.1.1");
             FixClient binanceClient = FixClient.connect(binance.config().port(), "FIX.4.4")) {

            coinbaseClient.coinbaseLogon();
            binanceClient.binanceLogon(keyPair);
            coinbaseClient.send("V", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "1022", "L2", "55", "BTC-USD"));
            binanceClient.send("V", Map.of("49", "NITROJEX", "56", "SPOT", "1022", "L2", "55", "BTCUSDT"));
            assertThat(coinbaseClient.readMessageContaining("269=0")).contains("BTC-USD");
            assertThat(binanceClient.readMessageContaining("269=0")).contains("BTCUSDT");

            coinbaseClient.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "ARB-CB-BUY", "55", "BTC-USD",
                "54", "1", "44", "65000.0", "38", "0.1"));
            binanceClient.send("D", Map.of(
                "49", "NITROJEX", "56", "SPOT", "11", "ARB-BN-SELL", "55", "BTCUSDT",
                "54", "2", "44", "65010.0", "38", "0.1"));

            assertThat(coinbaseClient.readMessageContaining("150=F")).contains("35=8");
            assertThat(binanceClient.readMessageContaining("150=F")).contains("35=8");
            assertThat(coinbase.getFillCount()).isEqualTo(1);
            assertThat(binance.getFillCount()).isEqualTo(1);
        }
    }

    @Test
    void rejectAndReconnectScenariosRemainLocalAndDeterministic() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (CoinbaseExchangeSimulator coinbase = coinbase(CoinbaseExchangeSimulator.FillMode.REJECT_ALL);
             BinanceExchangeSimulator binance = binance(BinanceExchangeSimulator.FillMode.REJECT_ALL, keyPair);
             FixClient coinbaseClient = FixClient.connect(coinbase.config().port(), "FIXT.1.1");
             FixClient binanceClient = FixClient.connect(binance.config().port(), "FIX.4.4")) {

            coinbaseClient.coinbaseLogon();
            binanceClient.binanceLogon(keyPair);
            coinbaseClient.send("D", Map.of(
                "49", "TEST_SENDER", "56", "TEST_TARGET", "11", "REJ-CB", "55", "BTC-USD",
                "54", "1", "44", "65000.0", "38", "0.1"));
            binanceClient.send("D", Map.of(
                "49", "NITROJEX", "56", "SPOT", "11", "REJ-BN", "55", "BTCUSDT",
                "54", "2", "44", "65010.0", "38", "0.1"));

            assertThat(coinbaseClient.readMessageContaining("150=8")).contains("35=8");
            assertThat(binanceClient.readMessageContaining("150=8")).contains("35=8");
            assertThat(coinbase.getRejectCount()).isEqualTo(1);
            assertThat(binance.getRejectCount()).isEqualTo(1);

            binance.scheduleDisconnect(0, true);
            Thread.sleep(50L);
            binance.reconnect();
            assertThat(binance.isConnected()).isTrue();
        }
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

    static final class FixClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private final String beginString;
        private String pending = "";

        private FixClient(final Socket socket, final String beginString) throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
            this.beginString = beginString;
        }

        static FixClient connect(final int port, final String beginString) throws IOException {
            final Socket socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(2_000);
            return new FixClient(socket, beginString);
        }

        void coinbaseLogon() throws IOException {
            send("A", Map.of("49", "TEST_SENDER", "56", "TEST_TARGET", "554", "coinbase-passphrase"));
            readMessageContaining("35=A");
        }

        void binanceLogon(final KeyPair keyPair) throws Exception {
            final String sendingTime = "20260508-12:00:00.000";
            final Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(keyPair.getPrivate());
            signer.update(BinanceExchangeSimulator.buildLogonPayload(
                "A", "NITROJEX", "SPOT", "1", sendingTime).getBytes(StandardCharsets.US_ASCII));
            send("A", Map.of(
                "49", "NITROJEX", "56", "SPOT", "34", "1", "52", sendingTime,
                "553", BinanceSimulatorScenarios.DEFAULT_API_KEY,
                "96", Base64.getEncoder().encodeToString(signer.sign()),
                "25035", "2"));
            readMessageContaining("35=A");
        }

        void send(final String msgType, final Map<String, String> fields) throws IOException {
            final StringBuilder builder = new StringBuilder("8=").append(beginString).append(SOH)
                .append("9=0").append(SOH)
                .append("35=").append(msgType).append(SOH);
            fields.forEach((tag, value) -> builder.append(tag).append('=').append(value).append(SOH));
            builder.append("10=000").append(SOH);
            output.write(builder.toString().getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }

        String readMessageContaining(final String marker) throws IOException {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final byte[] buffer = new byte[256];
            final long deadline = System.nanoTime() + 2_000_000_000L;
            while (System.nanoTime() < deadline) {
                final String message = pollCompleteMessage();
                if (message != null) {
                    if (message.contains(marker)) {
                        return message;
                    }
                    continue;
                }
                final int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                bytes.write(buffer, 0, read);
                pending += bytes.toString(StandardCharsets.US_ASCII);
                bytes.reset();
            }
            throw new AssertionError("FIX marker not received: " + marker);
        }

        private String pollCompleteMessage() {
            final int checksum = pending.indexOf(SOH + "10=");
            if (checksum < 0) {
                return null;
            }
            final int end = pending.indexOf(SOH, checksum + 1);
            if (end < 0) {
                return null;
            }
            final String message = pending.substring(0, end + 1);
            pending = pending.substring(end + 1);
            return message;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}

/**
 * TASK-420 production-path companion to the simulator smoke test above.
 *
 * <p>This class keeps the whole required chain in one local E2E fixture:
 * {@link ArbStrategy} emits a V14-enabled parent intent, the V13
 * {@link MultiLegContingentExecution} plugin creates child state in
 * {@link OrderManagerImpl}, {@link OrderCommandHandler} routes decoded SBE
 * child commands to both simulator sockets, and simulator execution reports are
 * normalized back into the execution engine. Parent terminal callbacks are
 * delivered to the trading strategy so failure cooldown is verified at the same
 * boundary used by the clustered service.</p>
 *
 * <p>Non-goals are live exchange QA/UAT, credential validation outside the
 * local Binance logon signature, REST recovery, and external network access.
 * The live-wire value here is deterministic concurrent Coinbase/Binance
 * simulator evidence for edge detection, fills, leg rejects, imbalance hedging,
 * hedge rejection, terminal reasons, and cooldown.</p>
 */
@Tag("E2E")
final class CrossVenueArbPipelineLiveWireE2ETest {
    private static final long QTY = Ids.SCALE / 10L;
    private static final long COINBASE_ASK = 65_000L * Ids.SCALE;
    private static final long COINBASE_BID = 64_990L * Ids.SCALE;
    private static final long BINANCE_BID = 65_200L * Ids.SCALE;
    private static final long BINANCE_ASK = 65_210L * Ids.SCALE;

    @Test
    void edgeDetectionLegFillsParentCallbackAndNoCooldownOnComplete() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (LiveWire live = liveWire(CoinbaseExchangeSimulator.FillMode.IMMEDIATE,
            BinanceExchangeSimulator.FillMode.IMMEDIATE, false, keyPair)) {
            live.logon();
            live.triggerEdge();
            final long parentOrderId = live.cluster.logPosition;

            live.route(parentOrderId + 1L);
            live.route(parentOrderId + 2L);
            live.coinbaseClient.readMessageContaining("150=F");
            live.binanceClient.readMessageContaining("150=F");
            live.driveCoinbase(parentOrderId + 1L, Side.BUY);
            live.driveBinance(parentOrderId + 2L, Side.SELL);

            assertThat(live.parents.lookup(parentOrderId).status()).isEqualTo(ParentOrderState.DONE);
            assertThat(live.parents.lookup(parentOrderId).terminalReasonCode()).isEqualTo(ParentOrderState.REASON_COMPLETED);
            assertThat(live.multiLeg.bothLegsFilled()).isEqualTo(1L);
            assertThat(live.terminalReasons).containsExactly(ParentTerminalReason.COMPLETED);
            assertThat(cooldownUntil(live.strategy)).isZero();
            assertThat(live.coinbase.getFillCount()).isEqualTo(1);
            assertThat(live.binance.getFillCount()).isEqualTo(1);
            assertThat(live.engine.parentIntentDispatches()).isEqualTo(1L);
            assertThat(live.engine.childExecutionDispatches()).isGreaterThanOrEqualTo(2L);
        }
    }

    @Test
    void legRejectProducesChildRejectedTerminalAndCooldown() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (LiveWire live = liveWire(CoinbaseExchangeSimulator.FillMode.IMMEDIATE,
            BinanceExchangeSimulator.FillMode.REJECT_ALL, false, keyPair)) {
            live.logon();
            live.triggerEdge();
            final long parentOrderId = live.cluster.logPosition;

            live.route(parentOrderId + 1L);
            live.route(parentOrderId + 2L);
            live.binanceClient.readMessageContaining("150=8");
            live.driveBinance(parentOrderId + 2L, Side.SELL);

            assertThat(live.parents.lookup(parentOrderId).status()).isEqualTo(ParentOrderState.FAILED);
            assertThat(live.parents.lookup(parentOrderId).terminalReasonCode())
                .isEqualTo(ParentOrderState.REASON_CHILD_REJECTED);
            assertThat(live.multiLeg.legRejects()).isEqualTo(1L);
            assertThat(live.terminalReasons).containsExactly(ParentTerminalReason.CHILD_REJECTED);
            assertThat(cooldownUntil(live.strategy)).isGreaterThan(live.cluster.time);
            assertThat(live.binance.getRejectCount()).isEqualTo(1);
        }
    }

    @Test
    void oneLegFillTimerSubmitsImbalanceHedgeAndHedgeFillCompletesParent() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (LiveWire live = liveWire(CoinbaseExchangeSimulator.FillMode.IMMEDIATE,
            BinanceExchangeSimulator.FillMode.NO_FILL, false, keyPair)) {
            live.logon();
            live.triggerEdge();
            final long parentOrderId = live.cluster.logPosition;

            live.route(parentOrderId + 1L);
            live.route(parentOrderId + 2L);
            live.coinbaseClient.readMessageContaining("150=F");
            live.driveCoinbase(parentOrderId + 1L, Side.BUY);
            live.engine.onTimer(live.multiLeg.timerCorrelationId());
            live.binance.setFillMode(BinanceExchangeSimulator.FillMode.IMMEDIATE);
            live.route(parentOrderId + 3L);
            live.binanceClient.readMessageContaining("150=F");
            live.driveBinance(parentOrderId + 3L, Side.SELL);

            assertThat(live.multiLeg.timerFirings()).isEqualTo(1L);
            assertThat(live.multiLeg.imbalanceHedges()).isEqualTo(1L);
            assertThat(live.parents.lookup(parentOrderId).status()).isEqualTo(ParentOrderState.DONE);
            assertThat(live.terminalReasons).containsExactly(ParentTerminalReason.COMPLETED);
            assertThat(live.binance.receivedOrders()).extracting(BinanceExchangeSimulator.ReceivedOrder::clOrdId)
                .contains(Long.toString(parentOrderId + 3L));
        }
    }

    @Test
    void hedgeRiskRejectActivatesKillSwitchHedgeFailedTerminalAndCooldown() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (LiveWire live = liveWire(CoinbaseExchangeSimulator.FillMode.IMMEDIATE,
            BinanceExchangeSimulator.FillMode.NO_FILL, true, keyPair)) {
            live.logon();
            live.triggerEdge();
            final long parentOrderId = live.cluster.logPosition;

            live.route(parentOrderId + 1L);
            live.route(parentOrderId + 2L);
            live.coinbaseClient.readMessageContaining("150=F");
            live.driveCoinbase(parentOrderId + 1L, Side.BUY);
            live.engine.onTimer(live.multiLeg.timerCorrelationId());

            assertThat(live.parents.lookup(parentOrderId).status()).isEqualTo(ParentOrderState.FAILED);
            assertThat(live.parents.lookup(parentOrderId).terminalReasonCode())
                .isEqualTo(ParentOrderState.REASON_HEDGE_FAILED);
            assertThat(live.multiLeg.hedgeRiskRejects()).isEqualTo(1L);
            assertThat(live.risk.killSwitchReason).isEqualTo("hedge_risk_reject");
            assertThat(live.terminalReasons).containsExactly(ParentTerminalReason.HEDGE_FAILED);
            assertThat(cooldownUntil(live.strategy)).isGreaterThan(live.cluster.time);
        }
    }

    private static LiveWire liveWire(
        final CoinbaseExchangeSimulator.FillMode coinbaseMode,
        final BinanceExchangeSimulator.FillMode binanceMode,
        final boolean rejectHedgeRisk,
        final KeyPair keyPair) throws Exception {
        final Fixture fixture = fixture(rejectHedgeRisk);
        final ArbStrategy strategy = new ArbStrategy(arbConfig());
        strategy.init(strategyContext(fixture));
        fixture.engine.setParentCallbackSink(decoder -> {
            fixture.terminalReasons.add(decoder.terminalReason());
            strategy.onParentTerminal(decoder);
        });
        return new LiveWire(
            coinbase(coinbaseMode),
            binance(binanceMode, keyPair),
            fixture,
            strategy,
            keyPair);
    }

    private static Fixture fixture(final boolean rejectHedgeRisk) {
        final InternalMarketView marketView = new InternalMarketView();
        final ParentOrderRegistry parents = new ParentOrderRegistry(64, 128);
        final OrderManagerImpl orders = new OrderManagerImpl();
        final RiskStub risk = new RiskStub(rejectHedgeRisk);
        final RecordingCluster cluster = new RecordingCluster();
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[1024]);
        final MultiLegContingentExecution multiLeg = new MultiLegContingentExecution();
        final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry();
        registry.register(multiLeg);
        registry.allowCompatibility(Ids.STRATEGY_ARB, ExecutionStrategyIds.MULTI_LEG_CONTINGENT);
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
        return new Fixture(marketView, parents, orders, risk, cluster, commandBuffer, multiLeg, engine,
            idRegistry, new ArrayList<>());
    }

    private static StrategyContextImpl strategyContext(final Fixture fixture) {
        return new StrategyContextImpl(
            fixture.marketView,
            fixture.risk,
            fixture.orders,
            new PortfolioStub(),
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

    private static ArbStrategyConfig arbConfig() {
        final long[] zero = new long[Ids.MAX_VENUES + 1];
        return new ArbStrategyConfig(
            Ids.INSTRUMENT_BTC_USD,
            new int[] {Ids.VENUE_COINBASE, Ids.VENUE_BINANCE},
            1L,
            zero,
            zero,
            zero,
            QTY,
            QTY,
            100L,
            1_000L,
            25_000L);
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
            case "4" -> ExecType.CANCELED;
            default -> ExecType.ORDER_STATUS;
        };
        final long fillQty = scale(report.lastQty());
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] venueOrderId = ("SIM-" + childClOrdId).getBytes(StandardCharsets.US_ASCII);
        final byte[] execId = ("sim-" + childClOrdId + '-' + venueId + '-' + report.execType())
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
            .leavesQtyScaled(execType == ExecType.FILL ? 0L : QTY)
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

    private static MarketDataEventDecoder marketData(final int venueId, final EntryType entryType, final long price) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(Ids.INSTRUMENT_BTC_USD)
            .entryType(entryType)
            .updateAction(UpdateAction.NEW)
            .priceScaled(price)
            .sizeScaled(10L * Ids.SCALE)
            .priceLevel(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(1);
        final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
        decoder.wrap(buffer, MessageHeaderEncoder.ENCODED_LENGTH,
            MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
        return decoder;
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

    private static long cooldownUntil(final ArbStrategy strategy) throws ReflectiveOperationException {
        final Field field = ArbStrategy.class.getDeclaredField("cooldownUntilMicros");
        field.setAccessible(true);
        return field.getLong(strategy);
    }

    private record Fixture(
        InternalMarketView marketView,
        ParentOrderRegistry parents,
        OrderManagerImpl orders,
        RiskStub risk,
        RecordingCluster cluster,
        UnsafeBuffer commandBuffer,
        MultiLegContingentExecution multiLeg,
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
        final ArbStrategy strategy;
        final KeyPair keyPair;
        final ParentOrderRegistry parents;
        final RiskStub risk;
        final RecordingCluster cluster;
        final MultiLegContingentExecution multiLeg;
        final ExecutionStrategyEngine engine;
        final List<ParentTerminalReason> terminalReasons;

        LiveWire(
            final CoinbaseExchangeSimulator coinbase,
            final BinanceExchangeSimulator binance,
            final Fixture fixture,
            final ArbStrategy strategy,
            final KeyPair keyPair) throws IOException {
            this.coinbase = coinbase;
            this.binance = binance;
            this.fixture = fixture;
            this.strategy = strategy;
            this.keyPair = keyPair;
            coinbaseClient = CrossVenueArbLiveWireE2ETest.FixClient.connect(coinbase.config().port(), "FIXT.1.1");
            binanceClient = CrossVenueArbLiveWireE2ETest.FixClient.connect(binance.config().port(), "FIX.4.4");
            parents = fixture.parents;
            risk = fixture.risk;
            cluster = fixture.cluster;
            multiLeg = fixture.multiLeg;
            engine = fixture.engine;
            terminalReasons = fixture.terminalReasons;
        }

        void logon() throws Exception {
            coinbaseClient.coinbaseLogon();
            binanceClient.binanceLogon(keyPair);
        }

        void triggerEdge() {
            fixture.marketView.apply(marketData(Ids.VENUE_COINBASE, EntryType.ASK, COINBASE_ASK), cluster.time);
            fixture.marketView.apply(marketData(Ids.VENUE_COINBASE, EntryType.BID, COINBASE_BID), cluster.time);
            fixture.marketView.apply(marketData(Ids.VENUE_BINANCE, EntryType.BID, BINANCE_BID), cluster.time);
            fixture.marketView.apply(marketData(Ids.VENUE_BINANCE, EntryType.ASK, BINANCE_ASK), cluster.time);
            strategy.onMarketData(marketData(Ids.VENUE_BINANCE, EntryType.BID, BINANCE_BID));
        }

        void route(final long childClOrdId) {
            routeOrder(fixture, coinbaseClient, binanceClient, childClOrdId);
        }

        void driveCoinbase(final long childClOrdId, final Side side) {
            driveReports(fixture, coinbase.executionReports().stream().map(CoinbaseReportView::new).toList(),
                childClOrdId, Ids.VENUE_COINBASE, side);
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

        @Override
        public boolean scheduleTimer(final long correlationId, final long deadlineClusterMicros) {
            return scheduleTimer(correlationId, deadlineClusterMicros, ExecutionStrategyIds.MULTI_LEG_CONTINGENT);
        }

        @Override
        public boolean scheduleTimer(
            final long correlationId,
            final long deadlineClusterMicros,
            final int executionStrategyId) {
            return engine == null || engine.registerTimerOwner(correlationId, executionStrategyId);
        }
    }

    private static final class RecordingCluster {
        final Cluster proxy;
        long time = 1_000L;
        long logPosition = 70_000L;

        RecordingCluster() {
            proxy = (Cluster)Proxy.newProxyInstance(Cluster.class.getClassLoader(), new Class<?>[] {Cluster.class},
                (p, m, a) -> switch (m.getName()) {
                    case "time" -> time;
                    case "logPosition" -> logPosition;
                    case "timeUnit" -> TimeUnit.MICROSECONDS;
                    case "offer" -> ((Number)a[2]).longValue();
                    case "scheduleTimer", "cancelTimer" -> true;
                    case "toString" -> "CrossVenueArbLiveWireCluster";
                    default -> null;
                });
        }
    }

    private static final class SocketExecutionRouter implements ExecutionRouter {
        private final CrossVenueArbLiveWireE2ETest.FixClient coinbaseClient;
        private final CrossVenueArbLiveWireE2ETest.FixClient binanceClient;
        private int sentMessages;

        SocketExecutionRouter(
            final CrossVenueArbLiveWireE2ETest.FixClient coinbaseClient,
            final CrossVenueArbLiveWireE2ETest.FixClient binanceClient) {
            this.coinbaseClient = coinbaseClient;
            this.binanceClient = binanceClient;
        }

        @Override
        public void routeNewOrder(final NewOrderCommandDecoder command) {
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

    private record CoinbaseReportView(CoinbaseExchangeSimulator.ExecutionReport report) implements ReportView {
        @Override public String clOrdId() { return report.clOrdId(); }
        @Override public String execType() { return report.execType(); }
        @Override public double lastPx() { return report.lastPx(); }
        @Override public double lastQty() { return report.lastQty(); }
        @Override public int rejectReason() { return report.rejectReason(); }
    }

    private record BinanceReportView(BinanceExchangeSimulator.ExecutionReport report) implements ReportView {
        @Override public String clOrdId() { return report.clOrdId(); }
        @Override public String execType() { return report.execType(); }
        @Override public double lastPx() { return report.lastPx(); }
        @Override public double lastQty() { return report.lastQty(); }
        @Override public int rejectReason() { return report.rejectReason(); }
    }

    private static final class RiskStub implements RiskEngine {
        private final boolean rejectHedgeRisk;
        private String killSwitchReason;

        RiskStub(final boolean rejectHedgeRisk) {
            this.rejectHedgeRisk = rejectHedgeRisk;
        }

        @Override
        public RiskDecision preTradeCheck(
            final int venueId,
            final int instrumentId,
            final byte side,
            final long priceScaled,
            final long qtyScaled,
            final int strategyId) {
            return rejectHedgeRisk && strategyId == Ids.STRATEGY_ARB_HEDGE
                ? RiskDecision.REJECT_MAX_NOTIONAL
                : RiskDecision.APPROVED;
        }

        @Override public void updatePositionSnapshot(final int venueId, final int instrumentId, final long netQtyScaled) { }
        @Override public void updateDailyPnl(final long realizedPnlDeltaScaled) { }
        @Override public void setRecoveryLock(final int venueId, final boolean locked) { }
        @Override public long getDailyPnlScaled() { return 0L; }
        @Override public void activateKillSwitch(final String reason) { killSwitchReason = reason; }
        @Override public void deactivateKillSwitch() { killSwitchReason = null; }
        @Override public boolean isKillSwitchActive() { return killSwitchReason != null; }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetDailyCounters() { }
        @Override public void setCluster(final Cluster cluster) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void resetAll() { }
    }

    private static final class PortfolioStub implements PortfolioEngine {
        @Override public void initPosition(final int venueId, final int instrumentId) { }
        @Override public void onFill(final ExecutionEventDecoder decoder) { }
        @Override public void refreshUnrealizedPnl(final int venueId, final int instrumentId, final long markPriceScaled) { }
        @Override public long getNetQtyScaled(final int venueId, final int instrumentId) { return 0L; }
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
