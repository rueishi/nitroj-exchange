package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.messages.ExecType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class BinanceExecutionReportPolicyTest {
    private final BinanceExecutionReportPolicy policy = new BinanceExecutionReportPolicy();

    @Test
    void mapExecType_coversLifecycleValues() {
        assertThat(policy.mapExecType('0')).isEqualTo(ExecType.NEW);
        assertThat(policy.mapExecType('1')).isEqualTo(ExecType.PARTIAL_FILL);
        assertThat(policy.mapExecType('F')).isEqualTo(ExecType.FILL);
        assertThat(policy.mapExecType('4')).isEqualTo(ExecType.CANCELED);
        assertThat(policy.mapExecType('8')).isEqualTo(ExecType.REJECTED);
        assertThat(policy.mapExecType('C')).isEqualTo(ExecType.EXPIRED);
        assertThat(policy.mapExecType('?')).isEqualTo(ExecType.NULL_VAL);
    }

    @Test
    void isTerminal_matchesNormalizedExecutionState() {
        assertThat(policy.isTerminal(ExecType.NEW, 100L)).isFalse();
        assertThat(policy.isTerminal(ExecType.PARTIAL_FILL, 50L)).isFalse();
        assertThat(policy.isTerminal(ExecType.FILL, 0L)).isTrue();
        assertThat(policy.isTerminal(ExecType.FILL, 1L)).isFalse();
        assertThat(policy.isTerminal(ExecType.CANCELED, 10L)).isTrue();
        assertThat(policy.isTerminal(ExecType.REJECTED, 10L)).isTrue();
        assertThat(policy.isTerminal(ExecType.EXPIRED, 10L)).isTrue();
    }

    @Test
    void mapRejectReason_coversPrimitiveReasonMapping() {
        assertThat(policy.mapRejectReason(0)).isEqualTo(BinanceExecutionReportPolicy.REJECT_UNKNOWN_SYMBOL);
        assertThat(policy.mapRejectReason(2)).isEqualTo(BinanceExecutionReportPolicy.REJECT_EXCHANGE_CLOSED);
        assertThat(policy.mapRejectReason(3)).isEqualTo(BinanceExecutionReportPolicy.REJECT_ORDER_EXCEEDS_LIMIT);
        assertThat(policy.mapRejectReason(5)).isEqualTo(BinanceExecutionReportPolicy.REJECT_UNKNOWN_ORDER);
        assertThat(policy.mapRejectReason(6)).isEqualTo(BinanceExecutionReportPolicy.REJECT_DUPLICATE_ORDER);
        assertThat(policy.mapRejectReason(-1)).isEqualTo(BinanceExecutionReportPolicy.REJECT_OTHER);
    }

    @Test
    void malformedExecutionReport_requiresPublicationFields() {
        assertThat(policy.malformedExecutionReport(true, true, true, true, ExecType.NEW)).isFalse();
        assertThat(policy.malformedExecutionReport(false, true, true, true, ExecType.NEW)).isTrue();
        assertThat(policy.malformedExecutionReport(true, false, true, true, ExecType.NEW)).isTrue();
        assertThat(policy.malformedExecutionReport(true, true, false, true, ExecType.NEW)).isTrue();
        assertThat(policy.malformedExecutionReport(true, true, true, false, ExecType.NEW)).isTrue();
        assertThat(policy.malformedExecutionReport(true, true, true, true, ExecType.NULL_VAL)).isTrue();
    }
}
