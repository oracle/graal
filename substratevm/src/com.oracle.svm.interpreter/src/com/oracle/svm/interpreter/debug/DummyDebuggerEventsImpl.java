/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.interpreter.debug;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.Interpreter;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Allows the {@link Interpreter} to run without any debugger hooks.
 */
final class DummyDebuggerEventsImpl implements DebuggerEvents {
    @Override
    public void setEventEnabled(Thread thread, EventKind eventKind, boolean enable) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean isEventEnabled(Thread thread, EventKind eventKind) {
        return false;
    }

    @Override
    public void setEventHandler(EventHandler eventHandler) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public EventHandler getEventHandler() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public void toggleBreakpoint(ResolvedJavaMethod method, int bci, boolean enable) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public void toggleMethodEnterEvent(ResolvedJavaType clazz, boolean enable) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public void toggleMethodExitEvent(ResolvedJavaType clazz, boolean enable) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public SteppingControl getSteppingControl(Thread thread) {
        return null;
    }

    @Override
    public void setSteppingFromLocation(Thread thread, int depth, int size, Location location) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public void clearStepping(Thread thread) {
        throw VMError.intentionallyUnimplemented();
    }
}
