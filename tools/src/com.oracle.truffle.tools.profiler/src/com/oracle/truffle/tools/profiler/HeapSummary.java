package com.oracle.truffle.tools.profiler;

/**
 * Represents a summary of total and alive instances and object sizes.
 *
 * @since 1.0
 */
public final class HeapSummary {

    long totalInstances;
    long aliveInstances;
    long totalBytes;
    long aliveBytes;

    HeapSummary() {
    }

    /**
     * Returns the total number of allocated instances.
     *
     * @since 1.0
     */
    public long getTotalInstances() {
        return totalInstances;
    }

    /**
     * Returns the number of objects that are alive (i.e. not garbage collected).
     *
     * @since 1.0
     */
    public long getAliveInstances() {
        return aliveInstances;
    }

    /**
     * Returns the total number of bytes allocated.
     *
     * @since 1.0
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    /**
     * Returns the number of bytes used by alive object instances.
     *
     * @since 1.0
     */
    public long getAliveBytes() {
        return aliveBytes;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public String toString() {
        return "HeapSummary [totalInstances=" + totalInstances + ", aliveInstances=" + aliveInstances + ", totalBytes=" + totalBytes + ", aliveBytes=" + aliveBytes + "]";
    }

}
