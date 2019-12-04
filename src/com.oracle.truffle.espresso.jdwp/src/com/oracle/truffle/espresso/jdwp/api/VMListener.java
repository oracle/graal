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
