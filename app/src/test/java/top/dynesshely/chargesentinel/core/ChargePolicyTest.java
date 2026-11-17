package top.dynesshely.chargesentinel.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChargePolicyTest {

    @Test
    public void suspendsAtOrAboveStopThreshold() {
        assertEquals(ChargePolicy.Decision.SUSPEND, ChargePolicy.decide(80, 80, 50, false));
        assertEquals(ChargePolicy.Decision.SUSPEND, ChargePolicy.decide(100, 80, 50, false));
    }

    @Test
    public void resumesAtOrBelowResumeThreshold() {
        assertEquals(ChargePolicy.Decision.RESUME, ChargePolicy.decide(50, 80, 50, true));
        assertEquals(ChargePolicy.Decision.RESUME, ChargePolicy.decide(12, 80, 50, true));
    }

    @Test
    public void holdsInsideTheHysteresisBand() {
        assertEquals(ChargePolicy.Decision.HOLD, ChargePolicy.decide(79, 80, 50, false));
        assertEquals(ChargePolicy.Decision.HOLD, ChargePolicy.decide(51, 80, 50, true));
    }

    @Test
    public void holdKeepsTheCurrentState() {
        assertTrue(ChargePolicy.wantSuspended(ChargePolicy.Decision.HOLD, true));
        assertFalse(ChargePolicy.wantSuspended(ChargePolicy.Decision.HOLD, false));
        assertTrue(ChargePolicy.wantSuspended(ChargePolicy.Decision.SUSPEND, false));
        assertFalse(ChargePolicy.wantSuspended(ChargePolicy.Decision.RESUME, true));
    }

    @Test
    public void clampsStopIntoRange() {
        assertEquals(ChargePolicy.STOP_MIN, ChargePolicy.clampStop(10));
        assertEquals(ChargePolicy.STOP_MAX, ChargePolicy.clampStop(120));
        assertEquals(85, ChargePolicy.clampStop(85));
    }

    @Test
    public void clampsResumeBelowStopWithGap() {
        assertEquals(75, ChargePolicy.clampResume(90, 80));
        assertEquals(ChargePolicy.RESUME_MIN, ChargePolicy.clampResume(0, 80));
        assertEquals(50, ChargePolicy.clampResume(50, 80));
        assertEquals(45, ChargePolicy.clampResume(45, 50));
    }
}
