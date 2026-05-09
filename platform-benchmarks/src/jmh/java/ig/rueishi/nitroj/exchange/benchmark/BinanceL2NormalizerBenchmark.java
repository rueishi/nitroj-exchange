package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.gateway.GatewaySlot;
import ig.rueishi.nitroj.exchange.gateway.venue.binance.BinanceL2MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class BinanceL2NormalizerBenchmark {
    private static final long SESSION_ID = 9002L;
    private static final int OPERATIONS_PER_INVOCATION = 1_000_000;
    private static final int ALLOCATION_CHECK_WARMUP_INVOCATIONS = 8;

    private BinanceL2MarketDataNormalizer normalizer;
    private BenchmarkPublisher publisher;
    private com.sun.management.ThreadMXBean threadBean;
    private long threadId;
    private int allocationCheckWarmupInvocations;
    private UnsafeBuffer incremental;
    private int incrementalLength;

    @Setup
    public void setup() {
        publisher = new BenchmarkPublisher();
        normalizer = new BinanceL2MarketDataNormalizer(new BenchmarkRegistry(), publisher);
        incremental = fix("35=X", "34=1", "55=BTCUSDT", "268=1",
            "279=1", "269=0", "270=65000.00", "271=1.25", "1023=1");
        incrementalLength = incremental.capacity();
        threadBean = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if (!threadBean.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("Thread allocation measurement is not supported by this JVM");
        }
        threadBean.setThreadAllocatedMemoryEnabled(true);
        threadId = Thread.currentThread().threadId();
        allocationCheckWarmupInvocations = ALLOCATION_CHECK_WARMUP_INVOCATIONS;
    }

    @Benchmark
    @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
    public long binanceIncrementalPublishesSbeEvent(final AllocationCounters counters) {
        final long before = threadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < OPERATIONS_PER_INVOCATION; i++) {
            normalizer.onFixMessage(SESSION_ID, incremental, 0, incrementalLength, 1L);
        }
        final long allocatedBytes = threadBean.getThreadAllocatedBytes(threadId) - before;
        if (allocationCheckWarmupInvocations > 0) {
            allocationCheckWarmupInvocations--;
            return publisher.publishCount + normalizer.sequenceGapCount();
        }
        if (allocatedBytes != 0L) {
            throw new AssertionError("Binance L2 normalizer allocated " + allocatedBytes + " bytes");
        }
        counters.measuredThreadAllocatedBytes += allocatedBytes;
        return publisher.publishCount + normalizer.sequenceGapCount();
    }

    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class AllocationCounters {
        public long measuredThreadAllocatedBytes;
    }

    private static UnsafeBuffer fix(final String... fields) {
        return new UnsafeBuffer((String.join("\001", fields) + "\001").getBytes(StandardCharsets.US_ASCII));
    }

    private static final class BenchmarkRegistry implements IdRegistry {
        @Override
        public int venueId(final long sessionId) {
            return 2;
        }

        @Override
        public int instrumentId(final CharSequence symbol) {
            return "BTCUSDT".contentEquals(symbol) ? 101 : 0;
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
                && buffer.getByte(offset + 6) == 'T' ? 101 : 0;
        }

        @Override
        public String symbolOf(final int instrumentId) {
            return instrumentId == 101 ? "BTCUSDT" : null;
        }

        @Override
        public String venueNameOf(final int venueId) {
            return venueId == 2 ? "BINANCE" : null;
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
