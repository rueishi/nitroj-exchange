package ig.rueishi.nitroj.exchange.simulator;

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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-406 coverage for the Binance local FIX simulator.
 *
 * <p>Coverage notes: positive, negative, edge, malformed, failure/counter,
 * replay, and wire-integration categories are applicable and covered here.
 * Capacity, allocation benchmark, latency percentile, snapshot persistence, and
 * V13 behavior-equivalence categories are non-applicable to TASK-406 because the
 * changed code is bounded test tooling, not a declared runtime hot path or
 * persistent strategy/execution state owner.</p>
 */
final class BinanceExchangeSimulatorTest {
    private static final char SOH = '\001';

    @Test
    void wireLogonHeartbeatMarketDataAndOrders_useEd25519AndFix44() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (BinanceExchangeSimulator simulator = simulator(BinanceExchangeSimulator.FillMode.NO_FILL, keyPair);
             FixClient client = FixClient.connect(simulator.config().port())) {

            client.logon(keyPair);
            assertThat(client.readUntil("35=A"))
                .contains("8=FIX.4.4")
                .contains("35=A")
                .contains("25035=2");
            client.send("0", Map.of("49", "NITROJEX", "56", "SPOT"));
            assertThat(client.readUntil("35=0")).contains("35=0");

            client.send("V", Map.of("49", "NITROJEX", "56", "SPOT", "1022", "L2", "55", "BTCUSDT"));
            assertThat(client.readUntil("35=X")).contains("55=BTCUSDT").contains("269=0");
            assertThat(simulator.l2Events()).hasSize(2);

            client.send("D", Map.of(
                "49", "NITROJEX", "56", "SPOT", "11", "BN-1", "55", "BTCUSDT",
                "54", "1", "44", "65000.0", "38", "0.1"));
            assertThat(client.readUntil("150=0")).contains("35=8").contains("11=BN-1");
            assertThat(simulator.receivedOrders()).hasSize(1);

            client.send("F", Map.of("49", "NITROJEX", "56", "SPOT", "11", "CXL-1", "41", "BN-1"));
            assertThat(client.readUntil("150=4")).contains("35=8").contains("11=CXL-1");
            assertThat(simulator.receivedCancels()).hasSize(1);
            assertThat(simulator.getWireLogonCount()).isEqualTo(1);
        }
    }

    @Test
    void directScenarios_coverSnapshotIncrementalDepthRejectPartialAndTimeControl() throws Exception {
        final AtomicLong clock = new AtomicLong(123_000L);
        try (BinanceExchangeSimulator simulator = BinanceExchangeSimulator.builder()
            .config(BinanceSimulatorScenarios.defaultConfig().withPort(freePort()))
            .clock(clock::get)
            .fillMode(BinanceExchangeSimulator.FillMode.PARTIAL_THEN_FULL)
            .build()) {
            simulator.start();

            simulator.subscribeMarketData("L2");
            simulator.emitL2Snapshot();
            simulator.emitL2Update("BUY", "BTCUSDT", 64_999.50, 2.0, "CHANGE");
            simulator.emitL2Update("SELL", "BTCUSDT", 65_002.00, 0.0, "DELETE");
            assertThat(simulator.l2Events()).extracting(BinanceExchangeSimulator.L2PriceLevelEvent::seqNum)
                .containsExactly(1, 2, 3, 4);
            assertThat(simulator.l2Events()).extracting(BinanceExchangeSimulator.L2PriceLevelEvent::timestampMs)
                .containsOnly(123_000L);

            simulator.submitNewOrder("PARTIAL", "BUY", "BTCUSDT", 65_000.00, 0.20);
            waitUntil(() -> simulator.getFillCount() == 2);
            assertThat(simulator.executionReports()).extracting(BinanceExchangeSimulator.ExecutionReport::execType)
                .contains("0", "F", "F");

            simulator.setFillMode(BinanceExchangeSimulator.FillMode.REJECT_ALL);
            simulator.submitNewOrder("REJECT", "SELL", "BTCUSDT", 65_001.00, 0.10);
            assertThat(simulator.getRejectCount()).isEqualTo(1);
            assertThat(simulator.executionReports().get(simulator.executionReports().size() - 1).execType()).isEqualTo("8");
        }
    }

    @Test
    void sequenceResetDisconnectReconnectAndMalformedInputs_areDeterministicAndCounted() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (BinanceExchangeSimulator simulator = simulator(BinanceExchangeSimulator.FillMode.NO_FILL, keyPair);
             FixClient client = FixClient.connect(simulator.config().port())) {

            client.raw("8=FIX.4.4" + SOH + "9=0" + SOH + "49=NITROJEX" + SOH + "10=000" + SOH);
            assertThat(client.readUntil("missing MsgType")).contains("35=j");
            assertThat(simulator.getMalformedInboundFixCount()).isGreaterThanOrEqualTo(1);

            client.logon(keyPair);
            simulator.emitL3SequenceGap(5);
            assertThat(simulator.getL3SequenceGapCount()).isEqualTo(1);
            simulator.emitL2Snapshot();
            assertThat(simulator.l2FixMessages().get(0)).contains("34=1");
            simulator.reset();
            simulator.emitL2Snapshot();
            assertThat(simulator.l2FixMessages().get(0)).contains("34=1");

            simulator.scheduleDisconnect(0, true);
            waitUntil(() -> !simulator.isConnected());
            simulator.reconnect();
            assertThat(simulator.isConnected()).isTrue();
            assertThat(simulator.getLogonCount()).isEqualTo(2);
        }
    }

    @Test
    void invalidLogonUnknownSymbolAndInvalidScenarioInputs_areRejected() throws Exception {
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (BinanceExchangeSimulator simulator = simulator(BinanceExchangeSimulator.FillMode.NO_FILL, keyPair);
             FixClient client = FixClient.connect(simulator.config().port())) {

            client.send("A", Map.of(
                "49", "NITROJEX", "56", "SPOT", "34", "1", "52", "20260508-12:00:00.000",
                "553", "binance-api-key", "96", "bad-signature", "25035", "2"));
            assertThat(client.readUntil("logon rejected")).contains("35=5");
            assertThat(simulator.getRejectedWireSessionCount()).isEqualTo(1);

            client.logon(keyPair);
            client.send("V", Map.of("49", "NITROJEX", "56", "SPOT", "1022", "L2", "55", "DOGEUSDT"));
            assertThat(client.readUntil("unknown symbol")).contains("35=Y").contains("DOGEUSDT");

            assertThatThrownBy(() -> simulator.emitL2Update("BUY", "DOGEUSDT", 1.0, 1.0, "ADD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown L2 symbol");
            assertThatThrownBy(() -> simulator.subscribeMarketData("L3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported simulator market-data model");
        }
    }

    private static BinanceExchangeSimulator simulator(
        final BinanceExchangeSimulator.FillMode fillMode,
        final KeyPair keyPair) throws IOException {

        final BinanceExchangeSimulator simulator = BinanceExchangeSimulator.builder()
            .config(BinanceSimulatorScenarios.defaultConfig().withPort(freePort()))
            .apiKey("binance-api-key")
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

    private static void waitUntil(final Condition condition) throws InterruptedException {
        final long deadline = System.nanoTime() + 2_000_000_000L;
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

    private static final class FixClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;

        private FixClient(final Socket socket) throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        private static FixClient connect(final int port) throws IOException {
            return new FixClient(new Socket("127.0.0.1", port));
        }

        private void logon(final KeyPair keyPair) throws Exception {
            final String sendingTime = "20260508-12:00:00.000";
            final String payload = BinanceExchangeSimulator.buildLogonPayload(
                "A", "NITROJEX", "SPOT", "1", sendingTime);
            final Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(keyPair.getPrivate());
            signer.update(payload.getBytes(StandardCharsets.US_ASCII));
            send("A", Map.of(
                "49", "NITROJEX",
                "56", "SPOT",
                "34", "1",
                "52", sendingTime,
                "553", "binance-api-key",
                "96", Base64.getEncoder().encodeToString(signer.sign()),
                "25035", "2"));
        }

        private void send(final String msgType, final Map<String, String> fields) throws IOException {
            final StringBuilder builder = new StringBuilder()
                .append("8=FIX.4.4").append(SOH)
                .append("9=0").append(SOH)
                .append("35=").append(msgType).append(SOH);
            fields.forEach((tag, value) -> builder.append(tag).append('=').append(value).append(SOH));
            builder.append("10=000").append(SOH);
            raw(builder.toString());
        }

        private void raw(final String message) throws IOException {
            output.write(message.getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }

        private String readUntil(final String expected) throws IOException {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final long deadline = System.nanoTime() + 2_000_000_000L;
            final byte[] buffer = new byte[256];
            while (System.nanoTime() < deadline) {
                while (input.available() > 0) {
                    final int read = input.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    bytes.write(buffer, 0, read);
                    final String text = bytes.toString(StandardCharsets.US_ASCII);
                    if (text.contains(expected)) {
                        return text;
                    }
                }
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return bytes.toString(StandardCharsets.US_ASCII);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
