/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.jdwp.api;

public interface VMListener {

    void vmStarted(Object mainThread);

    /**
     * Fire a class prepare event on the listener.
     *
     * @param klass the class that has just been prepared by the VM
     * @param prepareThread the thread used when preparing the class
     */
    void classPrepared(KlassRef klass, Object prepareThread, boolean alreadyPrepared);

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

    /**
     * Determines if the method has a method breakpoint set. If true, the caller of this method is
     * expected to enter a probe node to allow for the Truffle Debug API to suspend the execution.
     *
     * @param method the method
     * @param returnValue the object to be returned from the method if any
     * @return true only if the method has a method breakpoint, false otherwise
     */
    boolean hasMethodBreakpoint(MethodRef method, Object returnValue);

    /**
     * This method prepares for a monitor contended enter event, but only if there was a request for
     * such event on the current thread.
     *
     * @param monitor the monitor object
     * @return {@code true} if an event for a monitor contended was prepared, {@code false}
     *         otherwise.
     */
    boolean prepareMonitorContended(Object monitor);

    /**
     * This method prepares for a monitor contended entered event, but only if there was a request
     * for such event on the current thread.
     *
     * @param monitor the monitor object
     * @return {@code true} if an event for a monitor contended entered was prepared, {@code false}
     *         otherwise.
     */
    boolean prepareMonitorContendedEntered(Object monitor);

    /**
     * This method should be called when when the monitor wait(timeout) method is invoked in the
     * guest VM. A monitor wait event will then be sent through JDWP, if there was a request for the
     * current thread.
     * 
     * @param monitor the monitor object
     * @param timeout the timeout in ms before the wait will time out
     */
    void monitorWait(Object monitor, long timeout);

    /**
     * This method should be called just after the monitor wait(timeout) method is invoked in the
     * guest VM. A monitor waited event will then be sent through JDWP, if there was a request for
     * the current thread.
     *
     * @param monitor the monitor object
     * @param timedOut if the wait timed out or not
     */
    void monitorWaited(Object monitor, boolean timedOut);

    /**
     * Adds the monitor object to the list of currently owned monitors for the thread having called
     * this method.
     *
     * @param monitor the monitor object
     */
    void addOwnedMonitor(Object monitor);

    /**
     * Removes the monitor object from the list of owned monitors for the thread having called this
     * method.
     *
     * @param monitor the monitor object
     */
    void removeOwnedMonitor(Object monitor);

    /**
     * Returns the current owned monitors for the input guest thread.
     *
     * @param thread the guest thread
     * @return all currently held monitors for the thread
     */
    Object[] getOwnedMonitors(Object thread);

    /**
     * Marks a monitor object as a current contending object on the current thread.
     *
     * @param monitor the monitor object
     */
    void addCurrentContendedMonitor(Object monitor);

    /**
     * Returns the current contended monitor object, or <code>null</code> if the thread is not in
     * contention on any monitor.
     *
     * @param guestThread
     * @return the current contended monitor object
     */
    Object getCurrentContendedMonitor(Object guestThread);

    /**
     * Returns the value of a force early return value or <code>null</code> if not set.
     *
     * @return the return value for a force early return
     */
    Object getAndRemoveEarlyReturnValue();

    /**
     * Sets the force early return value.
     *
     * @param returnValue the value
     */
    void forceEarlyReturn(Object returnValue);
}
