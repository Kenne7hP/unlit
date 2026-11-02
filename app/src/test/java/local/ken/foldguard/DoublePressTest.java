package local.ken.foldguard;

import org.junit.Test;
import static org.junit.Assert.*;

public class DoublePressTest {
    @Test public void twoCompletedShortPressesRecover() {
        DoublePress d = new DoublePress();
        assertFalse(d.event(1, 0)); assertFalse(d.event(0, 100));
        assertFalse(d.event(1, 300)); assertTrue(d.event(0, 400));
    }
    @Test public void boundaryIsInclusiveAndGestureResets() {
        DoublePress d = new DoublePress();
        d.event(1, 0); d.event(0, 10); d.event(1, 490);
        assertTrue(d.event(0, 510));
        d.event(1, 550); assertFalse(d.event(0, 570));
    }
    @Test public void singleOrSlowPressDoesNotRecover() {
        DoublePress d = new DoublePress();
        d.event(1, 0); assertFalse(d.event(0, 50));
        d.event(1, 600); assertFalse(d.event(0, 650));
    }
    @Test public void autorepeatAndLongPressInvalidateSequence() {
        DoublePress d = new DoublePress();
        d.event(1, 0); d.event(0, 20); d.event(1, 100); d.event(2, 150);
        assertFalse(d.event(0, 180));
        d.event(1, 200); assertFalse(d.event(0, 250));
        d.event(1, 300); assertFalse(d.event(0, 700));
    }
    @Test public void orphanReleaseDoesNotRecover() {
        DoublePress d = new DoublePress();
        assertFalse(d.event(0, 0)); assertFalse(d.event(0, 100));
    }
    @Test public void tripleDoesNotTriggerOnDoubleAndAcceptsBoundary() {
        DoublePress d = new DoublePress(3);
        d.event(1,0); assertFalse(d.event(0,20));
        d.event(1,500); assertFalse(d.event(0,520));
        d.event(1,1000); assertTrue(d.event(0,1020));
        d.event(1,1040); assertFalse(d.event(0,1060));
    }
    @Test public void tripleRejectsLongRepeatSlowAndOrphanSequences() {
        DoublePress d = new DoublePress(3);
        d.event(1,0); d.event(0,20); d.event(1,100); d.event(2,110); d.event(0,130);
        d.event(1,150); assertFalse(d.event(0,170));
        d.event(1,200); assertFalse(d.event(0,600));
        assertFalse(d.event(0,650));
        d.event(1,700); assertFalse(d.event(0,720));
        d.event(1,1300); assertFalse(d.event(0,1320));
        d.event(1,1400); assertFalse(d.event(0,1420));
    }
    @Test public void explicitResetClearsPartialPress() {
        DoublePress d = new DoublePress(3);
        d.event(1,0); d.event(0,20); d.event(1,30); d.reset();
        assertFalse(d.event(0,50)); d.event(1,60); assertFalse(d.event(0,80));
    }
}
