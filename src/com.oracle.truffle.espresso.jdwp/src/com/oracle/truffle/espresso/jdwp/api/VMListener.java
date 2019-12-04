package com.oracle.truffle.espresso.jdwp.api;

public interface VMListener {

    void vmStarted(Object mainThread);

    /**
     * Fire a class prepare event on the listener.
     * 
     * @param klass the class that has just been prepared by the VM
     * @param currentThread the thread used when preparing the class
     */
    void classPrepared(KlassRef klass, Object currentThread);

    /**
     * Fire a thread started event on the listener.
     * 
     * @param thread that has just been started
     */
    void threadStarted(Object thread);

    /**
     * Fire a thread stopped event on the listener.
     * 
     * @param thread that was just stopped
     */
    void threadDied(Object thread);

    /**
     * Determines if the field has a field modification breakpoint set. If true, the caller of the
     * method is expected to enter a probe node to allow for the Truffle Debug API to suspend the
     * execution.
     * 
     * @param field the field
     * @param receiver the receiving object in the field instruction
     * @param value the value about to be set on the receiver for the field
     * @return true only if the field has a modification breakpoint, false otherwise
     */
    boolean hasFieldModificationBreakpoint(FieldRef field, Object receiver, Object value);

    /**
     * Determines if the field has a field access breakpoint set. If true, the caller of the method
     * is expected to enter a probe node to allow for the Truffle Debug API to suspend the
     * execution.
     * 
     * @param field the field
     * @param receiver the receiving object in the field instruction
     * @return true only if the field has a access breakpoint, false otherwise
     */
    boolean hasFieldAccessBreakpoint(FieldRef field, Object receiver);
}
