package top.dynesshely.chargesentinel.history;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SampleGateTest {

    private static final long MINUTE = 60_000L;

    @Test
    public void firstSampleIsAlwaysRecorded() {
        assertTrue(SampleGate.shouldRecord(0, 0, 80, -1));
    }

    @Test
    public void recordsEveryFiveMinutesEvenWithoutChanges() {
        assertTrue(SampleGate.shouldRecord(5 * MINUTE, 0, 80, 80));
    }

    @Test
    public void ignoresLevelChangesInsideTheMinimumGap() {
        assertFalse(SampleGate.shouldRecord(10_000, 0, 79, 80));
    }

    @Test
    public void recordsLevelChangeAfterTheMinimumGap() {
        assertTrue(SampleGate.shouldRecord(31_000, 0, 79, 80));
    }

    @Test
    public void doesNotRecordUnchangedLevelBeforeTheInterval() {
        assertFalse(SampleGate.shouldRecord(4 * MINUTE, 0, 80, 80));
    }
}
