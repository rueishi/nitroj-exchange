package ig.rueishi.nitroj.exchange.order;

import ig.rueishi.nitroj.exchange.messages.ExecutionEventDecoder;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.service.Cluster;

/**
 * Cluster-side owner of order lifecycle state.
 *
 * <p>The message router calls this interface after receiving normalized
 * {@link ExecutionEventDecoder} messages from the gateway. Implementations own
 * the live-order map, state-machine transitions, duplicate execution detection,
 * cancel fan-out, and archive snapshot integration. The boolean returned from
 * {@link #onExecution(ExecutionEventDecoder)} tells the router whether downstream
 * portfolio and risk fill handling must run.</p>
 */
public interface OrderManager {
    void createPendingOrder(
        long clOrdId,
        int venueId,
        int instrumentId,
        byte side,
        byte ordType,
        byte timeInForce,
        long priceScaled,
        long qtyScaled,
        int strategyId
    );

    default void createPendingOrder(
        long clOrdId,
        int venueId,
        int instrumentId,
        byte side,
        byte ordType,
        byte timeInForce,
        long priceScaled,
        long qtyScaled,
        int strategyId,
        long parentOrderId
    ) {
        createPendingOrder(clOrdId, venueId, instrumentId, side, ordType, timeInForce,
            priceScaled, qtyScaled, strategyId);
    }

    boolean onExecution(ExecutionEventDecoder decoder);

    void cancelAllOrders();

    long[] getLiveOrderIds(int venueId);

    default void forEachLiveOrder(final int venueId, final LiveOrderConsumer consumer) {
        final long[] ids = getLiveOrderIds(venueId);
        for (int i = 0; i < ids.length; i++) {
            final OrderState order = getOrder(ids[i]);
            if (order != null) {
                consumer.onLiveOrder(order);
            }
        }
    }

    OrderState getOrder(long clOrdId);

    void forceTransitionToCanceled(long clOrdId);

    default void markCancelSent(long clOrdId) {
    }

    void writeSnapshot(ExclusivePublication pub);

    void loadSnapshot(Image image);

    void setCluster(Cluster cluster);

    void resetAll();

    interface LiveOrderConsumer {
        void onLiveOrder(OrderState order);
    }
}
