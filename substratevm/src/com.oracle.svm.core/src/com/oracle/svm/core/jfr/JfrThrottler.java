package com.oracle.svm.core.jfr;

import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.JfrEvent;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import com.oracle.svm.core.jfr.JfrTicks;


/**
 * Similar to Hotspot, each event that allows throttling, shall have its own throttler instance.
 * An alternative approach could be to use event specific throttling parameters in each event's event settings.
 *
 * Another alternative could be to maintain a set of parameters for each event type within the JfrThrottler singleton.
 * But maps cannot be used because allocation cannot be done while on the allocation slow path.
 * Maybe the map can be created in hosted mode before any allocation happens
 */
public class JfrThrottler {
    private long eventSampleSize;
    private long periodNs;
    private UninterruptibleUtils.AtomicInteger measuredPopSize; // already volatile
    private UninterruptibleUtils.AtomicLong endTicks; // already volatile
    private UninterruptibleUtils.AtomicBoolean lock; // already volatile
    JfrThrottler() {
        this.lock = new UninterruptibleUtils.AtomicBoolean(false);
        periodNs = 0;
        eventSampleSize = 0;
        measuredPopSize = new UninterruptibleUtils.AtomicInteger(0);
        this.endTicks = new UninterruptibleUtils.AtomicLong(JfrTicks.currentTimeNanos() + periodNs);
    }

    public boolean setThrottle(long eventSampleSize, long periodMs) {
        //TODO  may need to protect with mutex

        this.eventSampleSize = eventSampleSize;
        this.periodNs = periodMs * 1000000; //convert to ns from ms
        return true;
    }

    public boolean sample() {
        boolean expired = isExpired();
        if (expired) {
            //If fail to acquire lock, it means another thread is already handling it and this one can move on.
            if (lock.compareAndSet(false,true)) {
                rotateWindow();
                lock.set(false);
            }
            return false; // if expired, hotspot returns false
        }

        long prevMeasuredPopSize = measuredPopSize.incrementAndGet(); //guarantees only one thread can record the last event of the window
        if ( prevMeasuredPopSize > eventSampleSize) {
            return false;
        }
        return true;
    }

    private boolean isExpired() {
        if (JfrTicks.currentTimeNanos() > endTicks.get()) {
            return true;
        }
        return false;
    }

    private void rotateWindow() {
        measuredPopSize.set(0);
        endTicks.set(JfrTicks.currentTimeNanos() + periodNs);
    }
}
