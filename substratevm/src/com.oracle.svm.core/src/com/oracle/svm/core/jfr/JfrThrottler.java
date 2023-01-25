package com.oracle.svm.core.jfr;

import com.oracle.svm.core.jdk.UninterruptibleUtils;

import com.oracle.svm.core.jfr.JfrTicks;

import com.oracle.svm.core.jfr.JfrThrottlerWindow;
import org.graalvm.word.WordFactory;
import org.graalvm.compiler.nodes.PauseNode;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import com.oracle.svm.core.util.VMError;

/**
 * Similar to Hotspot, each event that allows throttling, shall have its own throttler instance. An
 * alternative approach could be to use event specific throttling parameters in each event's event
 * settings.
 *
 * Another alternative could be to maintain a set of parameters for each event type within the
 * JfrThrottler singleton. But maps cannot be used because allocation cannot be done while on the
 * allocation slow path. Maybe the map can be created in hosted mode before any allocation happens
 */
public class JfrThrottler {
    public UninterruptibleUtils.AtomicBoolean disabled; // already volatile
    private JfrThrottlerWindow window0; // Race allowed
    private JfrThrottlerWindow window1; // Race allowed
    private volatile JfrThrottlerWindow activeWindow;
    public volatile long eventSampleSize;  // Race allowed
    public volatile long periodNs; // Race allowed
    private double ewmaPopulationSizeAlpha = 0; // only accessed in critical section
    private double avgPopulationSize = 0; // only accessed in critical section

    private final int windowDivisor = 5; // Copied from hotspot
    private volatile boolean reconfigure; // does it have to be volatile? The same thread will set
                                          // and check this.
    private final UninterruptibleUtils.AtomicPointer<IsolateThread> lock; // Can't use reentrant
                                                                          // lock because it could
                                                                          // allocate

    private static final long SECOND_IN_NS = 1000000000;
    private static final long MINUTE_IN_NS = SECOND_IN_NS * 60;
    private long accumulatedDebtCarryLimit;
    private long accumulatedDebtCarryCount;

    public JfrThrottler() {
        accumulatedDebtCarryLimit = 0;
        accumulatedDebtCarryCount = 0;
        reconfigure = false;
        disabled = new UninterruptibleUtils.AtomicBoolean(true);
        lock = new UninterruptibleUtils.AtomicPointer<>(); // I assume this is initially
                                                           // WordFactorynullPointer()
        window0 = new JfrThrottlerWindow();
        window1 = new JfrThrottlerWindow();
        activeWindow = window0; // same as hotspot. Unguarded is ok because all throttler instances
                                // are created before program execution.
    }

    public boolean setThrottle(long eventSampleSize, long periodMs) {
        if (eventSampleSize == 0) {
            disabled.set(true);
        }

        this.eventSampleSize = eventSampleSize;
        this.periodNs = periodMs * 1000000;
        // Blocking lock because new settings MUST be applied.
        lock();
        reconfigure = true;
        rotateWindow(); // could omit this and choose to wait until next rotation.
        unlock();
        disabled.set(false); // should be after the above are set
        return true;
    }

    /**
     * The real active window may change while we're doing the sampling. That's ok as long as we
     * perform operations wrt a consistent window during this method. That's why we declare a stack
     * variable: "window". If the active window does change after we've read it from main memory,
     * there's no harm done because now we'll be writing to the next window (which gets reset before
     * becoming active again).
     */
    public boolean sample() {
        if (disabled.get()) {
            return true;
        }
        JfrThrottlerWindow window = activeWindow;
        boolean expired = window.isExpired();
        if (expired) {
            // Check lock to see if someone is already rotating. If so, move on.
            if (tryLock()) {
                // Once in critical section ensure active window is still expired.
                // Another thread may have already handled the expired window, or new settings may
                // have already triggered a rotation.
                if (activeWindow.isExpired()) {
                    rotateWindow();
                }
                unlock();
            }
            return false; // if expired, hotspot returns false
        }
        return window.sample();
    }

    /*
     * Locked. Only one thread should be rotating at once. If due to an expired window, then other
     * threads that try to rotate due to expiry, will just return false. If race between two threads
     * updating settings, then one will just have to wait for the other to finish. Order doesn't
     * matter as long as they are not interrupted.
     */
    private void rotateWindow() {
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        configure();
        installNextWindow();
    }

    private void installNextWindow() {
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        activeWindow = getNextWindow();
    }

    JfrThrottlerWindow getNextWindow() {
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        if (window0 == activeWindow) {
            return window1;
        }
        return window0;
    }

    private long computeAccumulatedDebtCarryLimitHotspot(long windowDurationNs) {
        if (periodNs == 0 || windowDurationNs >= SECOND_IN_NS) {
            return 1;
        }
        return SECOND_IN_NS / windowDurationNs;
    }

    private long computeAccumulatedDebtCarryLimit(long windowDurationNs) {
        if (periodNs == 0 || windowDurationNs > MINUTE_IN_NS) {
            return 1;
        }
        return windowDivisor; // this var isn't available to the class in Hotspot.
    }

    private long amortizeDebt(JfrThrottlerWindow lastWindow) {
        if (accumulatedDebtCarryCount == accumulatedDebtCarryLimit) {
            accumulatedDebtCarryCount = 1;
            return 0; // reset because new settings have been applied
        }
        accumulatedDebtCarryCount++;
        // did we sample less than we were supposed to?
        return lastWindow.samplesExpected() - lastWindow.samplesTaken(); // (base samples + carried
                                                                         // over debt) - samples
                                                                         // taken
    }

    public void configure() {
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        JfrThrottlerWindow next = getNextWindow();

        // Store updated params to both windows.
        if (reconfigure) {
            long windowDurationNs = periodNs / windowDivisor;
            accumulatedDebtCarryLimit = computeAccumulatedDebtCarryLimit(windowDurationNs);
            accumulatedDebtCarryCount = accumulatedDebtCarryLimit;
            activeWindow.samplesPerWindow = eventSampleSize / windowDivisor;  // TODO: handle low
                                                                              // rate so we don't
                                                                              // always undersample
            activeWindow.windowDurationNs = windowDurationNs;
            next.samplesPerWindow = eventSampleSize / windowDivisor;
            next.windowDurationNs = windowDurationNs;
            // compute alpha and debt
            avgPopulationSize = 0;
            ewmaPopulationSizeAlpha = (double) 1 / windowLookback(next); // lookback count;
            reconfigure = false;
        }
        next.projectedPopSize = projectPopulationSize(activeWindow.measuredPopSize.get());

        next.configure(amortizeDebt(activeWindow));
    }

    private double windowLookback(JfrThrottlerWindow window) {

        if (window.windowDurationNs <= SECOND_IN_NS) {
            return 25.0;
        } else if (window.windowDurationNs <= MINUTE_IN_NS) {
            return 5.0;
        } else {
            return 1.0;
        }

    }

    private double projectPopulationSize(long lastWindowMeasuredPop) {
        avgPopulationSize = exponentiallyWeightedMovingAverage(lastWindowMeasuredPop, ewmaPopulationSizeAlpha, avgPopulationSize);
        return avgPopulationSize;
    }

    private static double exponentiallyWeightedMovingAverage(double Y, double alpha, double S) {
        return alpha * Y + (1 - alpha) * S;
    }

    /** Basic spin lock */
    private void lock() {
        while (!tryLock()) {
            PauseNode.pause();
        }
    }

    private boolean tryLock() {
        return lock.compareAndSet(WordFactory.nullPointer(), CurrentIsolate.getCurrentThread());
    }

    private void unlock() {
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        lock.set(WordFactory.nullPointer());
    }

    /** Visible for testing. */
    public void beginTest(long eventSampleSize, long periodMs) {
        window0.isTest = true;
        window1.isTest = true;
        window0.testCurrentNanos = 0;
        window1.testCurrentNanos = 0;
        setThrottle(eventSampleSize, periodMs);
    }

    /** Visible for testing. */
    public double getActiveWindowProjectedPopulationSize() {
        return activeWindow.projectedPopSize;
    }

    /** Visible for testing. */
    public long getActiveWindowSamplingInterval() {
        return activeWindow.samplingInterval;
    }

    /** Visible for testing. */
    public long getActiveWindowDebt() {
        return activeWindow.debt;
    }

    /** Visible for testing. */
    public boolean IsActiveWindowExpired() {
        return activeWindow.isExpired();
    }

    /** Visible for testing. */
    public void expireActiveWindow() {
        window0.testCurrentNanos += periodNs / windowDivisor;
        window1.testCurrentNanos += periodNs / windowDivisor;
    }

}
