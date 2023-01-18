package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrThrottler;

/**
 * Each event that supports throttling has its own throttler that can be accessed through this class.
 * TODO: Consider making this a proper singleton later
 * When more events support throttling, some sort of allocation free hashmap should be used.
 */
public class JfrThrottlerSupport {
    JfrThrottler objectAllocationSampleThrottler;
    @Platforms(Platform.HOSTED_ONLY.class)
    JfrThrottlerSupport() {
        objectAllocationSampleThrottler = new JfrThrottler();
    }

    private JfrThrottler getThrottler(long eventId) {
        if (eventId == JfrEvent.ObjectAllocationSample.getId()) {
            return objectAllocationSampleThrottler;
        }
        return null;
    }

    public boolean setThrottle(long eventTypeId, long eventSampleSize, long periodMs) {
        JfrThrottler throttler = getThrottler(eventTypeId);
        if (throttler == null) {
            //event doesn't support throttling
            return false;
        }
        throttler.setThrottle(eventSampleSize, periodMs);
        return true;
    }

    public boolean shouldCommit(long eventTypeId) {
        JfrThrottler throttler = getThrottler(eventTypeId);
        if (throttler == null) {
            //event doesn't support throttling
            return true;
        }
        return throttler.sample();
    }
}
