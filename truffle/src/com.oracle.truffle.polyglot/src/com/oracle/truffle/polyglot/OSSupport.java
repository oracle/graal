package com.oracle.truffle.polyglot;

class OSSupport {
    private static boolean available = false;

    static {
        String supportLib = System.getProperty("truffle.ossupport.library");
        try {
            if (supportLib == null) {
                System.loadLibrary("ossupport");
                available = true;
            } else {
                System.load(supportLib);
                available = true;
            }
        } catch (UnsatisfiedLinkError ule) {
            // native OS support not available
            available = false;
        }
    }

    public static boolean isAvailable() {
        return available;
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
