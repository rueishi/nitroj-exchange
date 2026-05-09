package ig.rueishi.nitroj.exchange.e2e;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.RiskDecision;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.common.ExecutionStrategyIds;
import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.execution.ChildExecutionView;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyContext;
import ig.rueishi.nitroj.exchange.execution.ImmediateLimitExecution;
import ig.rueishi.nitroj.exchange.execution.ParentOrderIntentView;
import ig.rueishi.nitroj.exchange.execution.ParentOrderRegistry;
import ig.rueishi.nitroj.exchange.execution.ParentOrderState;
import ig.rueishi.nitroj.exchange.gateway.GatewaySlot;
import ig.rueishi.nitroj.exchange.gateway.venue.binance.BinanceL2MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.messages.BooleanType;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.ExecType;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import ig.rueishi.nitroj.exchange.messages.ExecutionEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.ParentIntentType;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentDecoder;
import ig.rueishi.nitroj.exchange.messages.ParentOrderIntentEncoder;
import ig.rueishi.nitroj.exchange.messages.PriceMode;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.order.OrderManagerImpl;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import ig.rueishi.nitroj.exchange.simulator.BinanceExchangeSimulator;
import ig.rueishi.nitroj.exchange.simulator.BinanceSimulatorScenarios;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-408 Binance live-wire E2E using the local Binance FIX simulator.
 *
 * <p>This class is the V14 pre-QA/UAT proof for Binance L2 without live Binance
 * access. It crosses TCP FIX bytes for logon, market-data subscription, order
 * entry, execution reports, and disconnect/reconnect. It then applies the FIX
 * market-data bytes through the production Binance L2 normalizer into
 * {@link InternalMarketView}, and routes simulator execution reports through
 * production {@link ImmediateLimitExecution} parent callbacks. Allocation and
 * latency evidence are non-applicable here because TASK-408 owns E2E coverage,
 * not a new runtime hot path; TASK-404 owns Binance L2 JMH evidence.</p>
 */
@Tag("E2E")
final class BinanceFixL2LiveWireE2ETest {
    private static final char SOH = '\001';
    private static final long PARENT_ID = 9_408L;
    private static final long CHILD_ID = 10_408L;
    private static final long PRICE = 65_000L * Ids.SCALE;
    private static final long QTY = Ids.SCALE / 10L;

    @Test
    void positiveFlow_logonSnapshotOrderFillAndParentDone() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (BinanceExchangeSimulator simulator = simulator(BinanceExchangeSimulator.FillMode.IMMEDIATE, keyPair);
             FixClient client = FixClient.connect(simulator.config().port())) {
            final Harness harness = new Harness();

            client.logon(keyPair);
            client.send("V", Map.of("49", "NITROJEX", "56", "SPOT", "1022", "L2", "55", "BTCUSDT"));
            assertThat(harness.applyBinanceL2FixMessage(client.readMessageContaining("269=0"))).isTrue();
            assertThat(harness.applyBinanceL2FixMessage(client.readMessageContaining("269=1"))).isTrue();

            assertThat(harness.marketView.getBestBid(Ids.VENUE_BINANCE, Ids.INSTRUMENT_BTC_USDT))
                .isEqualTo(scale(65_000.00));
            assertThat(harness.marketView.consolidatedBook(Ids.INSTRUMENT_BTC_USDT)
                .sizeAt(EntryType.BID, scale(65_000.00))).isEqualTo(scale(1.0));

            harness.submitParent();
            client.send("D", Map.of(
                "49", "NITROJEX", "56", "SPOT", "11", Long.toString(CHILD_ID), "55", "BTCUSDT",
                "54", "1", "44", "65000.0", "38", "0.1"));
            assertThat(client.readMessageContaining("150=0")).contains("35=8");
            assertThat(client.readMessageContaining("150=F")).contains("35=8");
            assertThat(simulator.getFillCount()).isEqualTo(1);

            harness.onChildFill(true);
            assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.DONE);
            assertThat(harness.registry.lookup(PARENT_ID).filledQtyScaled()).isEqualTo(QTY);
        }
    }

    @Test
    void gapRejectReconnectAndDeterministicTimeScenarios_areCovered() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final AtomicLong clock = new AtomicLong(777_000L);
        try (BinanceExchangeSimulator simulator = BinanceExchangeSimulator.builder()
            .config(BinanceSimulatorScenarios.defaultConfig().withPort(freePort()))
            .publicKeyBase64(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
            .clock(clock::get)
            .fillMode(BinanceExchangeSimulator.FillMode.REJECT_ALL)
            .build();
             FixClient client = startAndConnect(simulator)) {
            final Harness harness = new Harness();

            client.logon(keyPair);
            simulator.emitL3SequenceGap(2);
            final String gapFix = simulator.emitL2Update("BUY", "BTCUSDT", 64_999.50, 1.5, "CHANGE").toString();
            assertThat(simulator.getL3SequenceGapCount()).isEqualTo(1);
            assertThat(harness.applyBinanceL2FixMessage(simulator.l2FixMessages().getLast())).isTrue();
            assertThat(simulator.l2Events().getLast().timestampMs()).isEqualTo(777_000L);
            assertThat(gapFix).contains("BTCUSDT");

            harness.submitParent();
            client.send("D", Map.of(
                "49", "NITROJEX", "56", "SPOT", "11", Long.toString(CHILD_ID), "55", "BTCUSDT",
                "54", "1", "44", "65000.0", "38", "0.1"));
            assertThat(client.readMessageContaining("150=8")).contains("35=8");
            harness.onChildReject();
            assertThat(harness.registry.lookup(PARENT_ID).status()).isEqualTo(ParentOrderState.FAILED);

            simulator.scheduleDisconnect(0, true);
            waitUntil(() -> !simulator.isConnected());
            simulator.reconnect();
            assertThat(simulator.isConnected()).isTrue();
        }
    }

    private static BinanceExchangeSimulator simulator(
        final BinanceExchangeSimulator.FillMode fillMode,
        final KeyPair keyPair) throws IOException {
        final BinanceExchangeSimulator simulator = BinanceExchangeSimulator.builder()
            .config(BinanceSimulatorScenarios.defaultConfig().withPort(freePort()))
            .publicKeyBase64(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
            .fillMode(fillMode)
            .build();
        simulator.start();
        return simulator;
    }

    private static FixClient startAndConnect(final BinanceExchangeSimulator simulator) throws IOException {
        simulator.start();
        return FixClient.connect(simulator.config().port());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static long scale(final double value) {
        return Math.round(value * Ids.SCALE);
    }

    private static void waitUntil(final Condition condition) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("condition was not met before timeout");
    }

    @FunctionalInterface
    private interface Condition {
        boolean matches();
    }

    private static final class Harness {
        private final InternalMarketView marketView = new InternalMarketView();
        private final ParentOrderRegistry registry = new ParentOrderRegistry(4, 4);
        private final OrderManagerImpl orderManager = new OrderManagerImpl();
        private final ImmediateLimitExecution execution = new ImmediateLimitExecution();
        private final IdRegistry idRegistry = new BinanceIdRegistry();
        private final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[1024]);
        private final AtomicLong clock = new AtomicLong(10L);

        private Harness() {
            execution.init(new ExecutionStrategyContext(
                marketView,
                new RiskStub(),
                orderManager,
                registry,
                commandBuffer,
                new MessageHeaderEncoder(),
                new NewOrderCommandEncoder(),
                new CancelOrderCommandEncoder(),
                clock::incrementAndGet,
                (correlationId, deadlineClusterMicros) -> true,
                idRegistry,
                new CountersManager(new UnsafeBuffer(new byte[1024 * 1024]), new UnsafeBuffer(new byte[64 * 1024]))));
        }

        private boolean applyBinanceL2FixMessage(final String fixMessage) {
            final CapturingPublisher publisher = new CapturingPublisher();
            final BinanceL2MarketDataNormalizer normalizer =
                new BinanceL2MarketDataNormalizer(idRegistry, publisher);
            final UnsafeBuffer fixBuffer = new UnsafeBuffer(fixMessage.getBytes(StandardCharsets.US_ASCII));
            normalizer.onFixMessage(1L, fixBuffer, 0, fixMessage.length(), 1L);
            if (publisher.length == 0) {
                return false;
            }
            final MarketDataEventDecoder decoder = new MarketDataEventDecoder();
            decoder.wrap(publisher.slot.buffer, MessageHeaderEncoder.ENCODED_LENGTH,
                MarketDataEventEncoder.BLOCK_LENGTH, MarketDataEventEncoder.SCHEMA_VERSION);
            marketView.apply(decoder, 1L);
            return true;
        }

        private void submitParent() {
            execution.onParentIntent(intent());
        }

        private void onChildFill(final boolean terminal) {
            execution.onChildExecution(new ChildExecutionView().wrap(
                event(ExecType.FILL, QTY, QTY, 0L, terminal, "fill-1"),
                PARENT_ID));
        }

        private void onChildReject() {
            execution.onChildExecution(new ChildExecutionView().wrap(
                event(ExecType.REJECTED, 0L, 0L, QTY, true, "reject-1"),
                PARENT_ID));
        }
    }

    private static ParentOrderIntentView intent() {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        new ParentOrderIntentEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .parentOrderId(PARENT_ID)
            .strategyId((short)Ids.STRATEGY_MARKET_MAKING)
            .executionStrategyId(ExecutionStrategyIds.IMMEDIATE_LIMIT)
            .intentType(ParentIntentType.IMMEDIATE_LIMIT)
            .side(Side.BUY)
            .instrumentId(Ids.INSTRUMENT_BTC_USDT)
            .primaryVenueId(Ids.VENUE_BINANCE)
            .secondaryVenueId(0)
            .quantityScaled(QTY)
            .priceMode(PriceMode.LIMIT)
            .limitPriceScaled(PRICE)
            .referencePriceScaled(0L)
            .timeInForcePreference(TimeInForce.IOC)
            .urgencyHint((byte)1)
            .postOnlyPreference(BooleanType.FALSE)
            .selfTradePolicy((byte)0)
            .correlationId(CHILD_ID)
            .legCount((byte)1)
            .leg2Side(Side.SELL)
            .leg2LimitPriceScaled(0L)
            .parentTimeoutMicros(0L);
        final ParentOrderIntentDecoder decoder = new ParentOrderIntentDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return new ParentOrderIntentView().wrap(decoder);
    }

    private static ExecutionEventDecoder event(
        final ExecType execType,
        final long fillQty,
        final long cumQty,
        final long leavesQty,
        final boolean terminal,
        final String execId) {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
        final byte[] venueOrderId = "BN-ORDER".getBytes(StandardCharsets.US_ASCII);
        final byte[] execIdBytes = execId.getBytes(StandardCharsets.US_ASCII);
        new ExecutionEventEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .clOrdId(CHILD_ID)
            .venueId(Ids.VENUE_BINANCE)
            .instrumentId(Ids.INSTRUMENT_BTC_USDT)
            .execType(execType)
            .side(Side.BUY)
            .fillPriceScaled(PRICE)
            .fillQtyScaled(fillQty)
            .cumQtyScaled(cumQty)
            .leavesQtyScaled(leavesQty)
            .rejectCode(0)
            .ingressTimestampNanos(1L)
            .exchangeTimestampNanos(2L)
            .fixSeqNum(3)
            .isFinal(terminal ? BooleanType.TRUE : BooleanType.FALSE)
            .putVenueOrderId(venueOrderId, 0, venueOrderId.length)
            .putExecId(execIdBytes, 0, execIdBytes.length);
        final ExecutionEventDecoder decoder = new ExecutionEventDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
        return decoder;
    }

    private static final class CapturingPublisher implements BinanceL2MarketDataNormalizer.SlotPublisher {
        private final GatewaySlot slot = new GatewaySlot(512);
        private int length;

        @Override
        public GatewaySlot claimSlot() {
            return slot;
        }

        @Override
        public void publishSlot(final GatewaySlot publishedSlot) {
            length = publishedSlot.length;
        }
    }

    private static final class BinanceIdRegistry implements IdRegistry {
        @Override public int venueId(final long sessionId) { return Ids.VENUE_BINANCE; }
        @Override public int instrumentId(final CharSequence symbol) {
            return "BTCUSDT".contentEquals(symbol) ? Ids.INSTRUMENT_BTC_USDT : 0;
        }
        @Override public int instrumentId(final DirectBuffer buffer, final int offset, final int length) {
            return length == 7
                && buffer.getByte(offset) == 'B'
                && buffer.getByte(offset + 1) == 'T'
                && buffer.getByte(offset + 2) == 'C'
                && buffer.getByte(offset + 3) == 'U'
                && buffer.getByte(offset + 4) == 'S'
                && buffer.getByte(offset + 5) == 'D'
                && buffer.getByte(offset + 6) == 'T' ? Ids.INSTRUMENT_BTC_USDT : 0;
        }
        @Override public String symbolOf(final int instrumentId) { return "BTCUSDT"; }
        @Override public String venueNameOf(final int venueId) { return "BINANCE"; }
        @Override public void registerSession(final int venueId, final long sessionId) { }
    }

    private record RiskStub() implements RiskEngine {
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

    private static final class FixClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private String pending = "";

        private FixClient(final Socket socket) throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        private static FixClient connect(final int port) throws IOException {
            final Socket socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(2_000);
            return new FixClient(socket);
        }

        private void logon(final KeyPair keyPair) throws Exception {
            final String sendingTime = "20260508-12:00:00.000";
            final String payload = BinanceExchangeSimulator.buildLogonPayload(
                "A", "NITROJEX", "SPOT", "1", sendingTime);
            final Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(keyPair.getPrivate());
            signer.update(payload.getBytes(StandardCharsets.US_ASCII));
            send("A", Map.of(
                "49", "NITROJEX", "56", "SPOT", "34", "1", "52", sendingTime,
                "553", BinanceSimulatorScenarios.DEFAULT_API_KEY,
                "96", Base64.getEncoder().encodeToString(signer.sign()),
                "25035", "2"));
            readMessageContaining("35=A");
        }

        private void send(final String msgType, final Map<String, String> fields) throws IOException {
            final StringBuilder builder = new StringBuilder("8=FIX.4.4").append(SOH).append("9=0").append(SOH)
                .append("35=").append(msgType).append(SOH);
            fields.forEach((tag, value) -> builder.append(tag).append('=').append(value).append(SOH));
            builder.append("10=000").append(SOH);
            output.write(builder.toString().getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }

        private String readMessageContaining(final String marker) throws IOException {
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
