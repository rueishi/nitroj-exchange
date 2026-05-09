package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.GatewaySlot;
import ig.rueishi.nitroj.exchange.messages.EntryType;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.UpdateAction;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

final class BinanceL2MarketDataNormalizerTest {
    private static final long SESSION_ID = 9002L;
    private static final int VENUE_ID = 2;
    private static final int BTC_USDT_INSTRUMENT_ID = 101;
    private static int currentLength;

    @Test
    void snapshotPublishesBidAndAskMarketDataEvents() throws Exception {
        try (Harness harness = Harness.started(2)) {
            harness.normalizer.onFixMessage(SESSION_ID, fix("35=W", "34=21", "55=BTCUSDT", "268=2",
                "269=0", "270=65000.25", "271=0.75", "1023=1",
                "269=1", "270=65001.25", "271=0.50", "1023=1"), 0, currentLength, 99L);

            final List<MarketDataEventDecoder> events = harness.events();
            assertThat(events).hasSize(2);
            assertThat(events.get(0).venueId()).isEqualTo(VENUE_ID);
            assertThat(events.get(0).instrumentId()).isEqualTo(BTC_USDT_INSTRUMENT_ID);
            assertThat(events.get(0).entryType()).isEqualTo(EntryType.BID);
            assertThat(events.get(0).updateAction()).isEqualTo(UpdateAction.NEW);
            assertThat(events.get(0).priceScaled()).isEqualTo(6_500_025_000_000L);
            assertThat(events.get(0).sizeScaled()).isEqualTo(75_000_000L);
            assertThat(events.get(1).entryType()).isEqualTo(EntryType.ASK);
        }
    }

    @Test
    void incrementalDeleteMapsUpdateActionAndSequenceGapCounter() throws Exception {
        try (Harness harness = Harness.started(2)) {
            harness.normalizer.onFixMessage(SESSION_ID, fix("35=X", "34=1", "55=BTCUSDT", "268=1",
                "279=0", "269=0", "270=65000", "271=1"), 0, currentLength, 1L);
            harness.normalizer.onFixMessage(SESSION_ID, fix("35=X", "34=3", "55=BTCUSDT", "268=1",
                "279=2", "269=0", "270=65000", "271=0"), 0, currentLength, 2L);

            final List<MarketDataEventDecoder> events = harness.events();
            assertThat(events).hasSize(2);
            assertThat(events.get(1).updateAction()).isEqualTo(UpdateAction.DELETE);
            assertThat(harness.normalizer.sequenceGapCount()).isEqualTo(1);
        }
    }

    @Test
    void malformedOrUnknownSymbolDropsWithoutPublishing() {
        try (Harness harness = Harness.notStarted(4)) {
            harness.normalizer.onFixMessage(SESSION_ID, fix("35=W", "34=22", "55=DOGEUSDT", "268=1",
                "269=1", "270=1", "271=1"), 0, currentLength, 100L);
            harness.normalizer.onFixMessage(SESSION_ID, fix("35=W", "34=BAD", "55=BTCUSDT", "268=1",
                "269=1", "270=1", "271=1"), 0, currentLength, 101L);

            assertThat(harness.disruptor.remainingCapacity()).isEqualTo(4);
            assertThat(harness.normalizer.unknownSymbolDropCount()).isEqualTo(1);
            assertThat(harness.normalizer.malformedMessageDropCount()).isEqualTo(1);
        }
    }

    @Test
    void hotPathPublishesWithoutThreadAllocationAfterWarmup() {
        final BenchmarkPublisher publisher = new BenchmarkPublisher();
        final BinanceL2MarketDataNormalizer normalizer =
            new BinanceL2MarketDataNormalizer(new TestRegistry(), publisher);
        final UnsafeBuffer message = fix("35=X", "34=1", "55=BTCUSDT", "268=1",
            "279=1", "269=0", "270=65000.00", "271=1.25", "1023=1");
        final int length = currentLength;
        for (int i = 0; i < 2_000_000; i++) {
            normalizer.onFixMessage(SESSION_ID, message, 0, length, 1L);
        }
        assertThat(normalizer.malformedMessageDropCount()).isZero();
        assertThat(normalizer.unknownSymbolDropCount()).isZero();
        assertThat(publisher.publishCount).isEqualTo(2_000_000L);

        final com.sun.management.ThreadMXBean bean =
            (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        assertThat(bean.isThreadAllocatedMemorySupported()).isTrue();
        bean.setThreadAllocatedMemoryEnabled(true);
        final long threadId = Thread.currentThread().threadId();
        measureAllocatedBytes(bean, threadId, normalizer, message, length);
        final long before = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 1_000_000; i++) {
            normalizer.onFixMessage(SESSION_ID, message, 0, length, 1L);
        }
        final long after = bean.getThreadAllocatedBytes(threadId);

        assertThat(after - before).isZero();
        assertThat(publisher.publishCount).isEqualTo(4_000_000L);
    }

    private static long measureAllocatedBytes(
        final com.sun.management.ThreadMXBean bean,
        final long threadId,
        final BinanceL2MarketDataNormalizer normalizer,
        final UnsafeBuffer message,
        final int length) {

        final long before = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 1_000_000; i++) {
            normalizer.onFixMessage(SESSION_ID, message, 0, length, 1L);
        }
        return bean.getThreadAllocatedBytes(threadId) - before;
    }

    private static UnsafeBuffer fix(final String... fields) {
        final String joined = String.join("\001", fields) + "\001";
        final byte[] bytes = joined.getBytes(StandardCharsets.US_ASCII);
        currentLength = bytes.length;
        return new UnsafeBuffer(bytes);
    }

    private static CountersManager counters() {
        return new CountersManager(
            new UnsafeBuffer(new byte[1024 * 1024]),
            new UnsafeBuffer(new byte[64 * 1024]));
    }

    private static final class Harness implements AutoCloseable {
        private final GatewayDisruptor disruptor;
        private final BinanceL2MarketDataNormalizer normalizer;
        private final CountDownLatch latch;
        private final List<UnsafeBuffer> payloads = new ArrayList<>();

        private Harness(final int ringSize, final int expectedEvents, final boolean start) {
            latch = new CountDownLatch(expectedEvents);
            disruptor = new GatewayDisruptor(ringSize, 512, counters(), (slot, sequence, endOfBatch) -> {
                final byte[] copy = new byte[slot.length];
                slot.buffer.getBytes(0, copy);
                payloads.add(new UnsafeBuffer(copy));
                latch.countDown();
            });
            normalizer = new BinanceL2MarketDataNormalizer(new TestRegistry(), disruptor);
            if (start) {
                disruptor.start();
            }
        }

        private static Harness started(final int expectedEvents) {
            return new Harness(8, expectedEvents, true);
        }

        private static Harness notStarted(final int ringSize) {
            return new Harness(ringSize, 0, false);
        }

        private List<MarketDataEventDecoder> events() throws Exception {
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            final MessageHeaderDecoder header = new MessageHeaderDecoder();
            final List<MarketDataEventDecoder> decoded = new ArrayList<>();
            for (UnsafeBuffer payload : payloads) {
                final MarketDataEventDecoder event = new MarketDataEventDecoder();
                event.wrapAndApplyHeader(payload, 0, header);
                decoded.add(event);
            }
            return decoded;
        }

        @Override
        public void close() {
            disruptor.close();
        }
    }

    private static final class TestRegistry implements IdRegistry {
        @Override
        public int venueId(final long sessionId) {
            if (sessionId != SESSION_ID) {
                throw new AssertionError("unexpected session id: " + sessionId);
            }
            return VENUE_ID;
        }

        @Override
        public int instrumentId(final CharSequence symbol) {
            return "BTCUSDT".contentEquals(symbol) ? BTC_USDT_INSTRUMENT_ID : 0;
        }

        @Override
        public int instrumentId(final org.agrona.DirectBuffer buffer, final int offset, final int length) {
            return length == 7
                && buffer.getByte(offset) == 'B'
                && buffer.getByte(offset + 1) == 'T'
                && buffer.getByte(offset + 2) == 'C'
                && buffer.getByte(offset + 3) == 'U'
                && buffer.getByte(offset + 4) == 'S'
                && buffer.getByte(offset + 5) == 'D'
                && buffer.getByte(offset + 6) == 'T' ? BTC_USDT_INSTRUMENT_ID : 0;
        }

        @Override
        public String symbolOf(final int instrumentId) {
            return instrumentId == BTC_USDT_INSTRUMENT_ID ? "BTCUSDT" : null;
        }

        @Override
        public String venueNameOf(final int venueId) {
            return venueId == VENUE_ID ? "BINANCE" : null;
        }

        @Override
        public void registerSession(final int venueId, final long sessionId) {
        }
    }

    private static final class BenchmarkPublisher implements BinanceL2MarketDataNormalizer.SlotPublisher {
        private final GatewaySlot slot = new GatewaySlot(512);
        private long publishCount;

        @Override
        public GatewaySlot claimSlot() {
            slot.sequence = 1L;
            return slot;
        }

        @Override
        public void publishSlot(final GatewaySlot publishedSlot) {
            publishedSlot.reset();
            publishCount++;
        }
    }
}
