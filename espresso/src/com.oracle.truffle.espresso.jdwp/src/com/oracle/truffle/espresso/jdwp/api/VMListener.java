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

    void vmStarted(boolean suspend);

    /**
     * Fire a class prepare event on the listener.
     *
     * @param klass the class that has just been prepared by the VM
     * @param prepareThread the thread used when preparing the class
     */
    void classPrepared(KlassRef klass, Object prepareThread);

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
     * This method will be called when a field value is about to be modified. The method will
     * determine if there is an active field modification breakpoint that will be triggered.
     *
     * @param field the field
     * @param receiver owner of the field
     * @param value the new field value
     * @return true if a breakpoint should be hit due to the modification
     */
    boolean onFieldModification(FieldRef field, Object receiver, Object value);

    /**
     * This method will be called when a field is about to be accessed. The method will determine if
     * there is an active field access breakpoint that will be triggered.
     *
     * @param field the field
     * @param receiver owner of the field
     * @return true if a breakpoint should be hit due to the modification
     */
    boolean onFieldAccess(FieldRef field, Object receiver);

    /**
     * This method will be called when a method is entered iff there is an active
     * {@link MethodHook}. Returns true if the method has an active method exit breakpoint that
     * should be triggered.
     *
     * @param method the method
     * @param scope the {@link com.oracle.truffle.api.interop.InteropLibrary} object representing
     *            local variables in scope
     * @return true a breakpoint should be hit on method entry
     */
    boolean onMethodEntry(MethodRef method, Object scope);

    /**
     * This method will be called when a method is about to return iff there is an active
     * {@link MethodHook}. Returns true if the method has an active method exit breakpoint that
     * should be triggered.
     *
     * @param method the method
     * @param returnValue the return value
     * @return true if a breakpoint should be hit on method exit
     */
    boolean onMethodReturn(MethodRef method, Object returnValue);

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
     * Marks a monitor object as a current contending object on the current thread.
     *
     * @param monitor the monitor object
     */
    void onContendedMonitorEnter(Object monitor);

    /**
     * Removes a monitor object as a current contending object on the current thread.
     *
     * @param monitor the monitor object
     */
    void onContendedMonitorEntered(Object monitor);

    /**
     * Returns the current contended monitor object, or <code>null</code> if the thread is not in
     * contention on any monitor.
     *
     * @param guestThread
     * @return the current contended monitor object
     */
    Object getCurrentContendedMonitor(Object guestThread);
}
