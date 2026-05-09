package ig.rueishi.nitroj.exchange.benchmark;

import ig.rueishi.nitroj.exchange.common.Ids;
import ig.rueishi.nitroj.exchange.common.VenueCapabilities;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.NewOrderSingleEncoder;
import ig.rueishi.nitroj.exchange.gateway.venue.binance.BinanceOrderEntryPolicy;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderDecoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.UnsafeBuffer;
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

/**
 * V14 benchmark owner for Binance order-entry policy enrichment.
 *
 * <p>The measured path is the venue-specific hot step that enriches a reusable
 * generated FIX NewOrderSingle encoder with Binance STP, order type, and TIF
 * values from a pre-decoded SBE command. Adapter construction, FIX session IO,
 * and credential handling are intentionally outside this benchmark because
 * they are cold-path or separately owned surfaces.</p>
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class BinanceOrderEntryPolicyBenchmark {
    private static final int OPS = 65_536;

    private BinanceOrderEntryPolicy policy;
    private NewOrderCommandDecoder command;
    private NewOrderSingleEncoder encoder;

    @Setup
    public void setup() {
        policy = new BinanceOrderEntryPolicy(
            new VenueCapabilities(true, true, false),
            BinanceOrderEntryPolicy.SelfTradePreventionMode.EXPIRE_TAKER);
        command = command();
        encoder = new NewOrderSingleEncoder();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int enrichNewOrderPolicy() {
        int result = 0;
        for (int i = 0; i < OPS; i++) {
            command.sbeRewind();
            policy.enrichNewOrder(command, encoder);
            result += encoder.selfTradePreventionMode();
            result += encoder.ordType();
            result += encoder.timeInForce();
        }
        return result;
    }

    private static NewOrderCommandDecoder command() {
        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[256]);
        new NewOrderCommandEncoder()
            .wrapAndApplyHeader(commandBuffer, 0, new MessageHeaderEncoder())
            .clOrdId(1001L)
            .venueId(Ids.VENUE_BINANCE)
            .instrumentId(Ids.INSTRUMENT_BTC_USDT)
            .strategyId((short)Ids.STRATEGY_INVENTORY_HEDGE)
            .side(Side.BUY)
            .ordType(OrdType.LIMIT)
            .timeInForce(TimeInForce.IOC)
            .priceScaled(65_000L * Ids.SCALE)
            .qtyScaled(Ids.SCALE);
        final NewOrderCommandDecoder decoder = new NewOrderCommandDecoder();
        decoder.wrapAndApplyHeader(commandBuffer, 0, new MessageHeaderDecoder());
        return decoder;
    }
}
