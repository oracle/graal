package com.oracle.truffle.espresso.impl;

/**
 * Used to access native configuration details.
 */
class EspressoVMConfig {

    public int jvmAccFieldStable;
    public int jvmAccSynthetic;
    public int jvmAccFieldInternal;
    public int jvmAccAnnotation;
    public int jvmAccVarargs;
    public int jvmAccBridge;
    public int jvmAccEnum;
    public int jvmAccIsCloneable;

    private EspressoVMConfig() {
    }

    static EspressoVMConfig instance = new EspressoVMConfig();

    static EspressoVMConfig config() {
        return instance;
    }
}