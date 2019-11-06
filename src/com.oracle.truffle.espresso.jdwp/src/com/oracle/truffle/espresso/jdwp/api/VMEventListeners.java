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

import com.oracle.truffle.espresso.jdwp.impl.JDWPCallFrame;
import com.oracle.truffle.espresso.jdwp.impl.VMEventListener;

/**
 * A class that keeps track of VM event listeners for which
 * events are fired as they occur in the underlying VM.
 */
public class VMEventListeners {

    /**
     * The default instance
     */
    private static final VMEventListeners DEFAULT = new VMEventListeners();

    /**
     * The single listener instance that must be registered through
     * registerListener() on the default instance.
     */
    private VMEventListener listener;


    public static VMEventListeners getDefault() {
        return DEFAULT;
    }

    /**
     * Register a VM event vmEventListener.
     * @param vmEventListener
     */
    public void registerListener(VMEventListener vmEventListener) {
        this.listener = vmEventListener;
    }

    /**
     * Fire a class prepare event on the listener.
     * @param klass the class that has just been prepared by the VM
     * @param currentThread the thread used when preparing the class
     */
    public void classPrepared(KlassRef klass, Object currentThread) {
        if (listener != null) {
            listener.classPrepared(klass, currentThread);
        }
    }

    /**
     * Fire a class unload event on the listener.
     * @param klass that was just unloaded
     */
    public void classUnloaded(KlassRef klass) {
        if (listener != null) {
            listener.classUnloaded(klass);
        }
    }

    /**
     * Fire a thread started event on the listener.
     * @param thread that has just been started
     */
    public void threadStarted(Object thread) {
        if (listener != null) {
            listener.threadStarted(thread);
        }
    }

    /**
     * Fire a thread stopped event on the listener.
     * @param thread that was just stopped
     */
    public void threadDied(Object thread) {
        if (listener != null) {
            listener.threadDied(thread);
        }
    }

    /**
     * Fire a breakpoint hit event on the listener.
     * @param info information about the breakpoint that was hit
     * @param currentThread the thread in which the breakpoint was hit
     */
    public void breakpointHit(BreakpointInfo info, Object currentThread) {
        if (listener != null) {
            listener.breakpointHIt(info, currentThread);
        }
    }

    /**
     * Fire a step completed event on the listener. Example of a step is when the
     * VM performed e.g. a STEP_OVER command.
     * @param commandRequestId the ID that requested the step to be performed
     * @param currentFrame the frame for the current code location after the step
     */
    public void stepCompleted(int commandRequestId, JDWPCallFrame currentFrame) {
        if (listener != null) {
            listener.stepCompleted(commandRequestId, currentFrame);
        }
    }

    public void exceptionThrown(BreakpointInfo info, Object currentThread, Object exception, JDWPCallFrame callFrame) {
        if (listener != null) {
            listener.exceptionThrown(info, currentThread, exception, callFrame);
        }
    }
}
