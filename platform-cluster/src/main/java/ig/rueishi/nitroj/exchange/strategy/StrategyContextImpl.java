package ig.rueishi.nitroj.exchange.strategy;

import ig.rueishi.nitroj.exchange.cluster.InternalMarketView;
import ig.rueishi.nitroj.exchange.cluster.PortfolioEngine;
import ig.rueishi.nitroj.exchange.cluster.RecoveryCoordinator;
import ig.rueishi.nitroj.exchange.cluster.RiskEngine;
import ig.rueishi.nitroj.exchange.execution.ExecutionStrategyEngine;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.messages.MessageHeaderEncoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandEncoder;
import ig.rueishi.nitroj.exchange.order.OrderManager;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import io.aeron.cluster.service.Cluster;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;

/**
 * Immutable strategy dependency bundle created during cluster startup.
 *
 * <p>ClusterMain constructs one context once all cluster-side collaborators
 * exist. Concrete strategies receive this record in {@link Strategy#init} and
 * keep references to the components they need. The record deliberately has no
 * behavior: deterministic ordering remains in TradingClusteredService,
 * MessageRouter, and StrategyEngine, while this type simply makes wiring
 * auditable and compile-time checked.</p>
 *
 * @param marketView cluster-local gross level-2 market data view; its
 * external-liquidity accessor subtracts NitroJEx-owned visible orders for
 * arbitrage and hedging decisions
 * @param riskEngine pre-trade and post-fill risk owner
 * @param orderManager live order lifecycle owner
 * @param portfolioEngine position and PnL owner
 * @param recoveryCoordinator venue recovery state owner
 * @param executionEngine optional execution-strategy engine; V14 startup must
 * validate strategy/execution compatibility before this context is handed to
 * strategies, while tests may still pass {@code null} for isolated strategy
 * construction
 * @param cluster Aeron Cluster facade, initially {@code null} before service
 * start
 * @param egressBuffer reusable strategy command buffer
 * @param headerEncoder reusable SBE header encoder
 * @param newOrderEncoder reusable SBE new-order encoder
 * @param cancelOrderEncoder reusable SBE cancel-order encoder
 * @param idRegistry deterministic venue and instrument id registry
 * @param counters optional Agrona counters owner for strategy metrics
 */
public record StrategyContextImpl(
    InternalMarketView marketView,
    RiskEngine riskEngine,
    OrderManager orderManager,
    PortfolioEngine portfolioEngine,
    RecoveryCoordinator recoveryCoordinator,
    ExecutionStrategyEngine executionEngine,
    Cluster cluster,
    UnsafeBuffer egressBuffer,
    MessageHeaderEncoder headerEncoder,
    NewOrderCommandEncoder newOrderEncoder,
    CancelOrderCommandEncoder cancelOrderEncoder,
    IdRegistry idRegistry,
    CountersManager counters
) implements StrategyContext {
    public StrategyContextImpl(
        final InternalMarketView marketView,
        final RiskEngine riskEngine,
        final OrderManager orderManager,
        final PortfolioEngine portfolioEngine,
        final RecoveryCoordinator recoveryCoordinator,
        final Cluster cluster,
        final UnsafeBuffer egressBuffer,
        final MessageHeaderEncoder headerEncoder,
        final NewOrderCommandEncoder newOrderEncoder,
        final CancelOrderCommandEncoder cancelOrderEncoder,
        final IdRegistry idRegistry,
        final CountersManager counters) {
        this(
            marketView,
            riskEngine,
            orderManager,
            portfolioEngine,
            recoveryCoordinator,
            null,
            cluster,
            egressBuffer,
            headerEncoder,
            newOrderEncoder,
            cancelOrderEncoder,
            idRegistry,
            counters);
    }
}
