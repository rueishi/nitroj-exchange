package ig.rueishi.nitroj.exchange.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.MarketMakingConfig;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.PostOnlyQuoteExecution;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import ig.rueishi.nitroj.exchange.simulator.BinanceExchangeSimulator;
import ig.rueishi.nitroj.exchange.simulator.BinanceSimulatorScenarios;
import ig.rueishi.nitroj.exchange.simulator.CoinbaseExchangeSimulator;
import ig.rueishi.nitroj.exchange.simulator.SimulatorConfig;
import ig.rueishi.nitroj.exchange.strategy.MarketMakingStrategy;
import ig.rueishi.nitroj.exchange.strategy.StrategyContextImpl;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Publication;
import io.aeron.cluster.service.Cluster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * TASK-411 live-wire proof for parallel multi-venue market making activation.
 *
 * <p>The test starts local Coinbase and Binance simulators, runs two independent
 * {@link MarketMakingStrategy} instances through the shared V13
 * {@link PostOnlyQuoteExecution} plugin, and sends the strategy-generated child
 * orders to the matching simulator over local FIX sockets. The same fixture also
 * forces a per-venue quote refresh before routing, proving each MM instance can
 * refresh independently while sharing the execution plugin. No production code,
 * live exchange network, credentials, or real-venue QA/UAT path is used.</p>
 */
@Tag("E2E")
final class ParallelMarketMakingLiveWireE2ETest {
    private static final char SOH = '\001';
    private static final long QTY = Ids.SCALE / 10L;

    @Test
    void bothMarketMakingInstancesRefreshAndReachBothSimulatorsConcurrently() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (CoinbaseExchangeSimulator coinbase = coinbase(CoinbaseExchangeSimulator.FillMode.IMMEDIATE);
             BinanceExchangeSimulator binance = binance(BinanceExchangeSimulator.FillMode.IMMEDIATE, keyPair);
             FixClient coinbaseClient = FixClient.connect(coinbase.config().port(), "FIXT.1.1");
             FixClient binanceClient = FixClient.connect(binance.config().port(), "FIX.4.4")) {

            coinbaseClient.coinbaseLogon();
            binanceClient.binanceLogon(keyPair);
            final Fixture fixture = fixture();
            final MarketMakingStrategy coinbaseMm = new MarketMakingStrategy(config(Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD));
            final MarketMakingStrategy binanceMm = new MarketMakingStrategy(config(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT));
            coinbaseMm.init(strategyContext(fixture));
            binanceMm.init(strategyContext(fixture));

            quote(fixture, coinbaseMm, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
                65_000L * Ids.SCALE, 65_001L * Ids.SCALE);
            fixture.cluster.logPosition = 80_000L;
            quote(fixture, coinbaseMm, Ids.VENUE_COINBASE, Ids.INSTRUMENT_BTC_USD,
                65_010L * Ids.SCALE, 65_011L * Ids.SCALE);
            routeNewOrder(fixture.commandBuffer, coinbaseClient, fixture.idRegistry);

            fixture.cluster.logPosition = 90_000L;
            quote(fixture, binanceMm, Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT,
                65_020L * Ids.SCALE, 65_021L * Ids.SCALE);
            fixture.cluster.logPosition = 91_000L;
            quote(fixture, binanceMm, Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT,
                65_030L * Ids.SCALE, 65_031L * Ids.SCALE);
            routeNewOrder(fixture.commandBuffer, binanceClient, fixture.idRegistry);

            assertThat(coinbaseClient.readMessageContaining("150=F")).contains("35=8");
            assertThat(binanceClient.readMessageContaining("150=F")).contains("35=8");
            assertThat(coinbase.receivedOrders()).hasSize(1);
            assertThat(binance.receivedOrders()).hasSize(1);
            assertThat(coinbase.getFillCount()).isEqualTo(1);
            assertThat(binance.getFillCount()).isEqualTo(1);
            assertThat(fixture.postOnly.parentCancels()).isGreaterThanOrEqualTo(4L);
            assertThat(fixture.engine.parentIntentDispatches()).isGreaterThanOrEqualTo(8L);
        }
    }

    private static Fixture fixture() {
        final InternalMarketView marketView = new InternalMarketView();
        final ParentOrderRegistry parents = new ParentOrderRegistry(64, 128);
        final OrderManagerImpl orders = new OrderManagerImpl();
        final RiskStub risk = new RiskStub();
        final RecordingCluster cluster = new RecordingCluster();
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[1024]);
        final PostOnlyQuoteExecution postOnly = new PostOnlyQuoteExecution();
        final ExecutionStrategyRegistry registry = new ExecutionStrategyRegistry(8, 8);
        registry.register(postOnly);
        registry.allowCompatibility(Ids.STRATEGY_MARKET_MAKING, ExecutionStrategyIds.POST_ONLY_QUOTE);
        final DualVenueIdRegistry idRegistry = new DualVenueIdRegistry();
        final ExecutionStrategyEngine engine = new ExecutionStrategyEngine(
            registry,
            new ExecutionStrategyContext(
                marketView,
                risk,
                orders,
                parents,
                commandBuffer,
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                () -> cluster.time,
                (correlationId, deadlineClusterMicros) -> true,
                idRegistry,
                counters()));
        engine.initRegisteredStrategies();
        return new Fixture(marketView, parents, orders, risk, cluster, commandBuffer, postOnly, engine, idRegistry);
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

    private static void quote(
        final Fixture fixture,
        final MarketMakingStrategy strategy,
        final int venueId,
        final int instrumentId,
        final long bid,
        final long ask) {
        onMarketData(fixture, strategy, venueId, instrumentId, EntryType.BID, bid);
        onMarketData(fixture, strategy, venueId, instrumentId, EntryType.ASK, ask);
    }

    private static void onMarketData(
        final Fixture fixture,
        final MarketMakingStrategy strategy,
        final int venueId,
        final int instrumentId,
        final EntryType entryType,
        final long price) {
        final MarketDataEventDecoder decoder = marketData(venueId, instrumentId, entryType, price);
        fixture.marketView.apply(decoder, fixture.cluster.time);
        strategy.onMarketData(decoder);
    }

    private static MarketDataEventDecoder marketData(
        final int venueId,
        final int instrumentId,
        final EntryType entryType,
        final long price) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new MarketDataEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .venueId(venueId)
            .instrumentId(instrumentId)
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

    private static void routeNewOrder(
        final UnsafeBuffer commandBuffer,
        final FixClient client,
        final IdRegistry idRegistry) throws IOException {
        final NewOrderCommandDecoder command = new NewOrderCommandDecoder();
        command.wrapAndApplyHeader(commandBuffer, 0, new MessageHeaderDecoder());
        final boolean binance = command.venueId() == Ids.VENUE_BINANCE;
        client.send("D", Map.of(
            "49", binance ? "NITROJEX" : "TEST_SENDER",
            "56", binance ? "SPOT" : "TEST_TARGET",
            "11", Long.toString(command.clOrdId()),
            "55", idRegistry.symbolOf(command.instrumentId()),
            "54", command.side() == Side.BUY ? "1" : "2",
            "44", decimal(command.priceScaled()),
            "38", decimal(command.qtyScaled()),
            "40", "2",
            "59", command.timeInForce() == TimeInForce.IOC ? "3" : "1"));
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

    private static MarketMakingConfig config(final int venueId, final int instrumentId) {
        return new MarketMakingConfig(
            instrumentId,
            venueId,
            10,
            0,
            QTY,
            QTY,
            10L * Ids.SCALE,
            10L * Ids.SCALE,
            1,
            10_000_000L,
            10_000_000L,
            1_000,
            2_000,
            10L * Ids.SCALE,
            QTY,
            1_000);
    }

    private static CountersManager counters() {
        return new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String decimal(final long scaled) {
        return Double.toString((double) scaled / Ids.SCALE);
    }

    private record Fixture(
        InternalMarketView marketView,
        ParentOrderRegistry parents,
        OrderManagerImpl orders,
        RiskStub risk,
        RecordingCluster cluster,
        UnsafeBuffer commandBuffer,
        PostOnlyQuoteExecution postOnly,
        ExecutionStrategyEngine engine,
        DualVenueIdRegistry idRegistry) {
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
                    case "toString" -> "ParallelMarketMakingLiveWireCluster";
                    default -> null;
                });
        }
    }

    private static final class DualVenueIdRegistry implements IdRegistry {
        @Override public int venueId(final long sessionId) { return Ids.VENUE_COINBASE; }
        @Override public int instrumentId(final CharSequence symbol) {
            return "BTCUSDT".contentEquals(symbol) ? Ids.INSTRUMENT_BTC_USDT : Ids.INSTRUMENT_BTC_USD;
        }
        @Override public String symbolOf(final int instrumentId) {
            return instrumentId == Ids.INSTRUMENT_BTC_USDT ? "BTCUSDT" : "BTC-USD";
        }
        @Override public String venueNameOf(final int venueId) {
            return venueId == Ids.VENUE_BINANCE ? "binance" : "coinbase";
        }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }

    private static final class RiskStub implements RiskEngine {
        @Override public RiskDecision preTradeCheck(final int venueId, final int instrumentId, final byte side, final long priceScaled, final long qtyScaled, final int strategyId) { return RiskDecision.APPROVED; }
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
        @Override public void onVenueStatus(final ig.rueishi.nitroj.exchange.messages.VenueStatusEventDecoder decoder) { }
        @Override public void onBalanceResponse(final ig.rueishi.nitroj.exchange.messages.BalanceQueryResponseDecoder decoder) { }
        @Override public void onTimer(final long correlationId, final long timestamp) { }
        @Override public boolean isInRecovery(final int venueId) { return false; }
        @Override public void reconcileOrder(final ExecutionEventDecoder decoder) { }
        @Override public void writeSnapshot(final ExclusivePublication snapshotPublication) { }
        @Override public void loadSnapshot(final Image snapshotImage) { }
        @Override public void resetAll() { }
        @Override public void setCluster(final Cluster cluster) { }
    }

    private static final class FixClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private final String beginString;
        private String pending = "";

        private FixClient(final Socket socket, final String beginString) throws IOException {
            this.socket = socket;
            input = socket.getInputStream();
            output = socket.getOutputStream();
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
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
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
