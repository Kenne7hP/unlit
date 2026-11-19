package local.ken.foldguard;

import org.junit.Test;
import java.util.ArrayDeque;
import static org.junit.Assert.*;

public class GuardControllerTest {
    private static final class FakeDevice implements GuardController.Device {
        final ArrayDeque<Long> events = new ArrayDeque<>();
        long time = 100;
        boolean grabbed, dark, changed;
        int offCount, restoreCount;
        String nextError = "";
        public String open(boolean keysOnly, boolean probe) {
            events.clear();
            String error = nextError; nextError = "";
            grabbed = !keysOnly && error.isEmpty();
            return error;
        }
        public long poll() { return events.isEmpty() ? 0 : events.remove(); }
        public void close() { grabbed = false; events.clear(); }
        public void beat() { }
        public boolean fingersDown() { return false; }
        public long now() { return time; }
        public void sleep(long millis) { time += millis; }
        public void screenOff() { dark = true; offCount++; }
        public boolean displayChanged() { return changed; }
        public void restoreDisplay() { dark = false; restoreCount++; }
        void event(GuardController c, int code) { time += 30; events.add((time << 8) | code); c.tick(); }
        void press(GuardController c) { event(c, 1); event(c, 2); }
    }
    private GuardController ready(FakeDevice d) {
        GuardController c = new GuardController(d); assertEquals("READY", c.initialize()); return c;
    }
    @Test public void idleObservesWithoutTouchGrabAndRequiresThreePresses() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d);
        assertFalse(d.grabbed); assertFalse(d.dark);
        d.press(c); d.press(c); assertNotEquals("KEY_START", c.heartbeat());
        d.press(c); assertEquals("KEY_START", c.heartbeat());
        assertNotEquals("KEY_START", c.heartbeat()); // each request delivered once
    }
    @Test public void freshControllerCanStartWithoutAnyProbe() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d);
        assertEquals("OK", c.begin(false));
        assertTrue(d.grabbed); assertTrue(d.dark);
    }
    @Test public void doubleRestoreThenTripleStartAcrossRepeatedSessions() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d);
        for (int i=0; i<20; i++) {
            d.press(c); d.press(c); d.press(c); assertEquals("KEY_START", c.heartbeat());
            assertEquals("OK", c.begin(false)); assertTrue(d.dark); assertTrue(d.grabbed);
            d.press(c); assertTrue(d.dark);
            d.press(c); assertFalse(d.dark); assertFalse(d.grabbed);
            assertEquals("双击音量下键恢复", c.heartbeat());
        }
        assertEquals(20, d.offCount); assertEquals(20, d.restoreCount);
    }
    @Test public void rapidStopStartHasNoOldWorkerThatRestoresNewSession() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d);
        for (int i=0; i<100; i++) {
            assertEquals("OK", c.begin(false)); c.stop();
            assertEquals("OK", c.begin(false));
            c.tick(); // the formerly outstanding poll/monitor iteration
            assertTrue(d.dark); assertTrue(d.grabbed);
            assertEquals("GUARD_RUNNING", c.heartbeat()); c.stop();
        }
    }
    @Test public void closedControllerCannotTouchTheReplacementController() {
        FakeDevice d = new FakeDevice(); GuardController old = ready(d);
        old.begin(false); old.close();
        GuardController next = ready(d); assertEquals("OK", next.begin(false));
        assertFalse(old.tick()); assertTrue(d.dark); assertTrue(d.grabbed);
        assertEquals("GUARD_RUNNING", next.heartbeat());
    }
    @Test public void failedAcquisitionCanBeRetriedWithoutReenablingService() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d);
        d.nextError = "手指未离屏";
        assertEquals("手指未离屏", c.begin(false)); assertFalse(d.dark); assertFalse(d.grabbed);
        assertEquals("OK", c.begin(false)); assertTrue(d.dark);
    }
    @Test public void keySequenceDoesNotLeakAcrossRestoreOrButtonStart() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d);
        d.press(c); d.press(c); c.begin(false);
        d.press(c); assertTrue(d.dark); // idle two presses must not count toward restore
        c.stop(); d.press(c); d.press(c); assertNotEquals("KEY_START", c.heartbeat());
        d.press(c); assertEquals("KEY_START", c.heartbeat());
    }
    @Test public void heartbeatLossRestoresReleasesAndStopsShortcutListener() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d); c.begin(false);
        d.time += 5001; assertFalse(c.tick());
        assertFalse(d.dark); assertFalse(d.grabbed);
        assertTrue(c.heartbeat().startsWith("CONTROLLER_EXITED:"));
        assertNotEquals("OK", c.begin(false));
    }
    @Test public void probeExpiryNeverBlacksScreenAndRearmsKeys() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d); c.begin(true);
        d.event(c, 6); assertEquals("PROBE_PASSED", c.heartbeat());
        assertEquals(0, d.offCount); assertFalse(d.grabbed);
        d.press(c); d.press(c); d.press(c); assertEquals("KEY_START", c.heartbeat());
    }
    @Test public void powerAndDisplayChangeAllowAnotherCleanSession() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d); c.begin(false);
        d.event(c, 4); assertFalse(d.dark); assertFalse(d.grabbed);
        c.begin(false); d.changed = true; c.tick(); assertFalse(d.dark);
        d.changed = false; assertEquals("OK", c.begin(false)); assertTrue(d.dark);
    }
    @Test public void unchangedPhysicalDisplayKeepsProtectionUntilDoubleRestore() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d); c.begin(false);
        for (int i = 0; i < 100; i++) {
            d.time += 20;
            assertTrue(c.tick());
            assertEquals("GUARD_RUNNING", c.heartbeat());
            assertTrue(d.dark); assertTrue(d.grabbed);
        }
        assertEquals(0, d.restoreCount);
        d.press(c); d.press(c);
        assertFalse(d.dark); assertFalse(d.grabbed);
        assertEquals(1, d.restoreCount);
    }
    @Test public void physicalDisplaySwitchStillReleasesTouchAndRearmsShortcut() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d); c.begin(false);
        d.changed = true;
        assertTrue(c.tick());
        assertEquals("显示状态变化，已退出", c.heartbeat());
        assertFalse(d.dark); assertFalse(d.grabbed);
        assertEquals(1, d.restoreCount);
        d.changed = false;
        d.press(c); d.press(c); d.press(c);
        assertEquals("KEY_START", c.heartbeat());
        assertEquals("OK", c.begin(false));
        assertTrue(d.dark); assertTrue(d.grabbed);
    }
    @Test public void repeatedStopIsSafeAndDoesNotLeavePartialGesture() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d);
        d.press(c); d.press(c); c.stop(); c.stop();
        d.press(c); assertNotEquals("KEY_START", c.heartbeat());
        assertEquals("OK", c.begin(false));
    }
    @Test public void restoreThenTripleBeforeNextAppHeartbeatPreservesNewRequest() {
        FakeDevice d = new FakeDevice(); GuardController c = ready(d); c.begin(false);
        d.press(c); d.press(c); // restore, app still believes the preceding session is active
        d.press(c); d.press(c); d.press(c);
        assertEquals("KEY_START", c.heartbeat());
        assertFalse(d.dark); assertEquals("OK", c.begin(false)); assertTrue(d.dark);
    }
}
