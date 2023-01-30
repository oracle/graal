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
    public UninterruptibleUtils.AtomicBoolean disabled; // Already volatile
    private JfrThrottlerWindow window0; // Race allowed
    private JfrThrottlerWindow window1; // Race allowed
    private volatile JfrThrottlerWindow activeWindow;
    public volatile long eventSampleSize;  // Race allowed
    public volatile long periodNs; // Race allowed
    // Only accessed in critical section
    private double ewmaPopulationSizeAlpha = 0;
    // Only accessed in critical section
    private double avgPopulationSize = 0;
    // Copied from hotspot
    private final int windowDivisor = 5;
    // does it have to be volatile? The same thread will set and check this.
    private volatile boolean reconfigure;
    // Can't use reentrant lock because it allocates
    private final UninterruptibleUtils.AtomicPointer<IsolateThread> lock;

    private static final long SECOND_IN_NS = 1000000000;
    private static final long SECOND_IN_MS = 1000;
    private static final long MINUTE_IN_NS = SECOND_IN_NS * 60;
    private static final long MINUTE_IN_MS = SECOND_IN_MS *60;
    private static final long HOUR_IN_MS = MINUTE_IN_MS * 60;
    private static final long HOUR_IN_NS = MINUTE_IN_NS * 60;
    private static final long DAY_IN_MS = HOUR_IN_MS * 24;
    private static final long DAY_IN_NS = HOUR_IN_NS * 24;
    private static final long TEN_PER_S_IN_MINUTES = 600;
    private static final long TEN_PER_S_IN_HOURS = 36000;
    private static final long TEN_PER_S_IN_DAYS = 864000;
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

    /**
     * Convert rate to samples/second if possible.
     * Want to avoid long period with large windows with a large number of samples per window
     * in favor of many smaller windows.
     * This is in the critical section because setting the sample size and period must be done together atomically.
     * Otherwise, we risk a window's params being set with only one of the two updated.
     */
    private void normalize1(long eventSampleSize, long periodMs){
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        // Do we want more than 10samples/s ? If so convert to samples/s
        if (periodMs <= SECOND_IN_MS){
            //nothing
        } else if (periodMs <= MINUTE_IN_MS) {
            if (eventSampleSize >= TEN_PER_S_IN_MINUTES) {
                eventSampleSize /= 60;
                periodMs /= 60;
            }
        } else if (periodMs <= HOUR_IN_MS) {
            if (eventSampleSize >=TEN_PER_S_IN_HOURS) {
                eventSampleSize /= 3600;
                periodMs /= 3600;
            }
        } else if (periodMs <= DAY_IN_MS) {
            if (eventSampleSize >=TEN_PER_S_IN_DAYS) {
                eventSampleSize /= 86400;
                periodMs /= 86400;
            }
        }
        this.eventSampleSize = eventSampleSize;
        this.periodNs = periodMs * 1000000;
    }
    private void normalize(double samplesPerPeriod, double periodMs){
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        // Do we want more than 10samples/s ? If so convert to samples/s
        double periodsPerSecond = 1000.0/ periodMs;
        double samplesPerSecond = samplesPerPeriod * periodsPerSecond;
        if (samplesPerSecond >= 10) {
            this.periodNs = SECOND_IN_NS;
            this.eventSampleSize = (long) samplesPerSecond;
            return;
        }

        this.eventSampleSize = eventSampleSize;
        this.periodNs = (long) periodMs * 1000000;
    }

    public boolean setThrottle(long eventSampleSize, long periodMs) {
        if (eventSampleSize == 0) {
            disabled.set(true);
        }

        // Blocking lock because new settings MUST be applied.
        lock();
        normalize(eventSampleSize, periodMs);
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
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        if (periodNs == 0 || windowDurationNs > MINUTE_IN_NS) {
            return 1;
        }
        return windowDivisor; // this var isn't available to the class in Hotspot.
    }

    private long amortizeDebt(JfrThrottlerWindow lastWindow) {
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
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

    /**
     * Handles the case where the sampling rate is very low.
     */
    private void setSamplePointsAndWindowDuration1(){
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        VMError.guarantee(reconfigure, "Should only modify sample size and window duration during reconfigure.");
        JfrThrottlerWindow next = getNextWindow();
        long samplesPerWindow = eventSampleSize / windowDivisor;
        long windowDurationNs = periodNs / windowDivisor;
        if (eventSampleSize < 10
        || periodNs >= MINUTE_IN_NS && eventSampleSize < TEN_PER_S_IN_MINUTES
        || periodNs >= HOUR_IN_NS && eventSampleSize < TEN_PER_S_IN_HOURS
        || periodNs >= DAY_IN_NS && eventSampleSize < TEN_PER_S_IN_DAYS){
            samplesPerWindow = eventSampleSize;
            windowDurationNs = periodNs;
        }
        activeWindow.samplesPerWindow = samplesPerWindow;
        activeWindow.windowDurationNs = windowDurationNs;
        next.samplesPerWindow = samplesPerWindow;
        next.windowDurationNs = windowDurationNs;
    }
    private void setSamplePointsAndWindowDuration(){
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        VMError.guarantee(reconfigure, "Should only modify sample size and window duration during reconfigure.");
        JfrThrottlerWindow next = getNextWindow();
        long samplesPerWindow = eventSampleSize / windowDivisor;
        long windowDurationNs = periodNs / windowDivisor;
        // If period isn't 1s, then we're effectively taking under 10 samples/s
        // because the values have already undergone normalization.
        if (eventSampleSize < 10 || periodNs > SECOND_IN_NS){
            samplesPerWindow = eventSampleSize;
            windowDurationNs = periodNs;
        }
        activeWindow.samplesPerWindow = samplesPerWindow;
        activeWindow.windowDurationNs = windowDurationNs;
        next.samplesPerWindow = samplesPerWindow;
        next.windowDurationNs = windowDurationNs;
    }

    public void configure() {
        VMError.guarantee(lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        JfrThrottlerWindow next = getNextWindow();

        // Store updated params to both windows.
        if (reconfigure) {
            setSamplePointsAndWindowDuration();
            accumulatedDebtCarryLimit = computeAccumulatedDebtCarryLimit(next.windowDurationNs);
            accumulatedDebtCarryCount = accumulatedDebtCarryLimit;
            // compute alpha and debt
            avgPopulationSize = 0;
            ewmaPopulationSizeAlpha = (double) 1 / windowLookback(next); // lookback count;
            reconfigure = false;
        }

        next.configure(amortizeDebt(activeWindow), projectPopulationSize(activeWindow.measuredPopSize.get()));
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
    public long getPeriodNs() {
        return periodNs;
    }
    /** Visible for testing. */
    public long getEventSampleSize() {
        return eventSampleSize;
    }
    /** Visible for testing. */
    public void expireActiveWindow() {
        if (eventSampleSize < 10 || periodNs > SECOND_IN_NS){
            window0.testCurrentNanos += periodNs;
            window1.testCurrentNanos += periodNs;
        }
        window0.testCurrentNanos += periodNs / windowDivisor;
        window1.testCurrentNanos += periodNs / windowDivisor;
    }
}
