package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.gateway.GatewayDisruptor;
import ig.rueishi.nitroj.exchange.gateway.GatewaySlot;
import ig.rueishi.nitroj.exchange.gateway.marketdata.AbstractFixL2MarketDataNormalizer;
import ig.rueishi.nitroj.exchange.gateway.marketdata.L2MarketDataContext;
import ig.rueishi.nitroj.exchange.messages.MarketDataEventEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import org.agrona.DirectBuffer;

import java.util.Objects;

/**
 * Binance Spot FIX L2 market-data normalizer.
 *
 * <p>Responsibility: converts Binance FIX 4.4 snapshot (`W`) and incremental
 * (`X`) price-level entries into NitroJEx `MarketDataEvent` SBE payloads. Role
 * in system: selected by {@link BinanceVenuePlugin} for venues configured with
 * `marketDataModel = L2`. Relationships: inherits standard L2 tag parsing from
 * {@link AbstractFixL2MarketDataNormalizer}, resolves symbols through
 * {@link IdRegistry}, and publishes through {@link GatewayDisruptor}.</p>
 */
public final class BinanceL2MarketDataNormalizer extends AbstractFixL2MarketDataNormalizer {
    private final IdRegistry idRegistry;
    private final SlotPublisher publisher;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MarketDataEventEncoder marketDataEncoder = new MarketDataEventEncoder();
    private int lastFixSeqNum;
    private long sequenceGapCount;

    public BinanceL2MarketDataNormalizer(final IdRegistry idRegistry, final GatewayDisruptor disruptor) {
        this(idRegistry, new DisruptorSlotPublisher(disruptor));
    }

    public BinanceL2MarketDataNormalizer(final IdRegistry idRegistry, final SlotPublisher publisher) {
        this.idRegistry = Objects.requireNonNull(idRegistry, "idRegistry");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    protected int venueId(final long sessionId) {
        return idRegistry.venueId(sessionId);
    }

    @Override
    protected int instrumentId(final String symbol) {
        return idRegistry.instrumentId(symbol);
    }

    @Override
    protected int instrumentId(final DirectBuffer buffer, final int valueStart, final int valueEnd) {
        return idRegistry.instrumentId(buffer, valueStart, valueEnd - valueStart);
    }

    @Override
    protected void enrich(final L2MarketDataContext context) {
        if (lastFixSeqNum != 0 && context.fixSeqNum != 0 && context.fixSeqNum != lastFixSeqNum + 1) {
            sequenceGapCount++;
        }
        if (context.fixSeqNum != 0) {
            lastFixSeqNum = context.fixSeqNum;
        }
    }

    @Override
    protected boolean publish(final L2MarketDataContext context) {
        final GatewaySlot slot = publisher.claimSlot();
        if (slot == null) {
            return false;
        }

        marketDataEncoder.wrapAndApplyHeader(slot.buffer, 0, headerEncoder)
            .venueId(context.venueId)
            .instrumentId(context.instrumentId)
            .entryType(context.entryType)
            .updateAction(context.updateAction)
            .priceScaled(context.priceScaled)
            .sizeScaled(context.sizeScaled)
            .priceLevel(context.priceLevel)
            .ingressTimestampNanos(context.ingressNanos)
            .exchangeTimestampNanos(context.exchangeTimestampNanos)
            .fixSeqNum(context.fixSeqNum);
        slot.length = MessageHeaderEncoder.ENCODED_LENGTH + marketDataEncoder.encodedLength();
        publisher.publishSlot(slot);
        return true;
    }

    public long sequenceGapCount() {
        return sequenceGapCount;
    }

    public interface SlotPublisher {
        GatewaySlot claimSlot();

        void publishSlot(GatewaySlot slot);
    }

    private record DisruptorSlotPublisher(GatewayDisruptor disruptor) implements SlotPublisher {
        private DisruptorSlotPublisher {
            Objects.requireNonNull(disruptor, "disruptor");
        }

        @Override
        public GatewaySlot claimSlot() {
            return disruptor.claimSlot();
        }

        @Override
        public void publishSlot(final GatewaySlot slot) {
            disruptor.publishSlot(slot);
        }
    }
}
