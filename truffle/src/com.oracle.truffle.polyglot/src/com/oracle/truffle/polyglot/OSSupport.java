package com.oracle.truffle.polyglot;

public class OSSupport {
    static {
        String supportLib = System.getProperty("truffle.ossupport.library");
        if (supportLib == null) {
            System.loadLibrary("ossupport");
        } else {
            System.load(supportLib);
        }
    }

    public static native boolean canLowerThreadPriority();

    public static native boolean canRaiseThreadPriority();

    /**
     * This is a wrapper around getpriority(), which returns the nice value of the calling thread.
     * Do note that nice values behave inverse to the common understanding of priorities: a lower
     * nice value means higher priority.
     * 
     * @return the nice value of the calling thread
     */
    public static native int getNativeThreadPriority();
}
