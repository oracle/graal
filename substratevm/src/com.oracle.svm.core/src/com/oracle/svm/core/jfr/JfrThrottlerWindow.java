package com.oracle.svm.core.jfr;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.JfrTicks;
public class JfrThrottlerWindow {
    // reset every rotation
    public UninterruptibleUtils.AtomicLong measuredPopSize; // already volatile
    public UninterruptibleUtils.AtomicLong endTicks; // already volatile

    // Calculated every rotation based on params set by user and results from previous windows
    public long samplingInterval;

    //params set by user
    public long eventSampleSize;
    public long periodNs;

    public JfrThrottlerWindow(){
        periodNs = 0;
        eventSampleSize = 0;
        measuredPopSize = new UninterruptibleUtils.AtomicLong(0);
        endTicks = new UninterruptibleUtils.AtomicLong(JfrTicks.currentTimeNanos() + periodNs);
        samplingInterval = 1;
    }
    public boolean isExpired() {
        if (JfrTicks.currentTimeNanos() > endTicks.get()) {
            return true;
        }
        return false;
    }

    public boolean sample(){
        // Guarantees only one thread can record the last event of the window
        long prevMeasuredPopSize = measuredPopSize.incrementAndGet();
        if (prevMeasuredPopSize % samplingInterval != 0 || prevMeasuredPopSize > eventSampleSize) {
            return false;
        }
        return true;
    }


}
