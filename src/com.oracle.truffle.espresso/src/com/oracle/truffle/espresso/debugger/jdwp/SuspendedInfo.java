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
package com.oracle.truffle.espresso.debugger.jdwp;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class SuspendedInfo {

    private SuspendedEvent event;
    private int suspendStrategy;
    private JDWPCallFrame[] stackFrames;
    private StaticObject thread;
    private DebuggerCommand.Kind stepKind;

    SuspendedInfo(SuspendedEvent event, int strategy, JDWPCallFrame[] stackFrames, StaticObject thread) {
        this.event = event;
        this.suspendStrategy = strategy;
        this.stackFrames = stackFrames;
        this.thread = thread;
    }

    public SuspendedEvent getEvent() {
        return event;
    }

    public int getSuspendStrategy() {
        return suspendStrategy;
    }

    public JDWPCallFrame[] getStackFrames() {
        return stackFrames;
    }

    public StaticObject getThread() {
        return thread;
    }

    public void recordStep(DebuggerCommand.Kind kind) {
        stepKind = kind;
    }

    public DebuggerCommand.Kind getStepKind() {
        return stepKind;
    }
}
