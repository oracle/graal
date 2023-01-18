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
 * Similar to Hotspot, each event that allows throttling, shall have its own throttler instance.
 * An alternative approach could be to use event specific throttling parameters in each event's event settings.
 *
 * Another alternative could be to maintain a set of parameters for each event type within the JfrThrottler singleton.
 * But maps cannot be used because allocation cannot be done while on the allocation slow path.
 * Maybe the map can be created in hosted mode before any allocation happens
 */
public class JfrThrottler {
    public UninterruptibleUtils.AtomicBoolean disabled; // already volatile
    private JfrThrottlerWindow window0; // Race allowed
    private JfrThrottlerWindow window1; // Race allowed
    private volatile JfrThrottlerWindow activeWindow;
    public volatile long eventSampleSize;  // Race allowed
    public volatile long periodNs; // Race allowed
    private final UninterruptibleUtils.AtomicPointer<IsolateThread>  lock; // Can't use reentrant lock because it could allocate

    JfrThrottler() {
        disabled = new UninterruptibleUtils.AtomicBoolean(true);
        lock = new UninterruptibleUtils.AtomicPointer<>(); // I assume this is initially WordFactorynullPointer()
        window0 = new JfrThrottlerWindow();
        window1 = new JfrThrottlerWindow();
        activeWindow = window0; // same as hotspot. Unguarded is ok because all throttler instances are created before program execution.
    }

    public boolean setThrottle(long eventSampleSize, long periodMs) {
        if (eventSampleSize == 0) {
            disabled.set(true);
        }
        this.eventSampleSize = eventSampleSize;
        this.periodNs = periodMs * 1000000;
        lock();
        rotateWindow(); // could omit this and choose to wait until next rotation.
        unlock();
        disabled.set(false); // should be after the above are set
        return true;
    }

    /** The real active window may change while we're doing the sampling. That's ok as long as we perform operations wrt a consistent window during this method.
     That's why we declare a stack variable: window. If the active window does change after we've read it from main memory, there's no harm done because now we'll be
     writing to the next window (which gets reset before becoming active again).
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
                // Another thread may have already handled the expired window, or new settings may have already triggered a rotation.
                if (activeWindow.isExpired()) {
                    rotateWindow();
                }
            }
            return false; // if expired, hotspot returns false
        }
        return window.sample();
    }

    /*
        Locked. Only one thread should be rotating at once.
        If due to an expired window, then other threads that try to rotate due to expiry, will just return false.
        If race between two threads updating settings, then one will just have to wait for the other to finish. Order doesn't matter as long as they are not interrupted.
     */
    private void rotateWindow() {
        VMError.guarantee(!lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        configure();
        installNextWindow();
    }

    private void installNextWindow(){
        VMError.guarantee(!lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        activeWindow = getNextWindow();
    }

    JfrThrottlerWindow getNextWindow(){
        VMError.guarantee(!lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        if (window0 == activeWindow){
            return window1;
        }
        return window0;
    }

    public void configure(){
        VMError.guarantee(!lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        JfrThrottlerWindow next = getNextWindow();

        // Store (possibly) updated params to both windows.
        activeWindow.eventSampleSize = eventSampleSize;
        activeWindow.periodNs = periodNs;
        next.eventSampleSize = eventSampleSize;
        next.periodNs = periodNs;

        //use updated params to compute
        if (activeWindow.measuredPopSize.get() == 0) {
            // Avoid math errors. If population is 0, consider every event.
            // Also means for first window on program start, we consider every sample (up until the cap)
            next.samplingInterval = 1;
        } else {
            // *** result will be a long. No floating points.  long/long = long rounded
            next.samplingInterval = activeWindow.measuredPopSize.get() / next.eventSampleSize; // TODO: needs to be more advanced.
        }
        //reset
        next.measuredPopSize.set(0);
        next.endTicks.set(JfrTicks.currentTimeNanos() + next.periodNs);
    }

    /** Basic spin lock*/
    private void lock() {
        while(!tryLock()){
            org.graalvm.compiler.nodes.PauseNode.pause();
        }
    }
    private boolean tryLock() {
        return lock.compareAndSet(WordFactory.nullPointer(),CurrentIsolate.getCurrentThread());
    }
    private void unlock() {
        VMError.guarantee(!lock.get().equal(CurrentIsolate.getCurrentThread()), "Throttler lock must be acquired in critical section.");
        lock.set(WordFactory.nullPointer());
    }
}
