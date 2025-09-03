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

import com.oracle.svm.interpreter.DebuggerSupport;
import com.oracle.svm.interpreter.InterpreterUtil;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.interpreter.InterpreterDirectives;
import com.oracle.svm.interpreter.metadata.InterpreterUniverse;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class DebuggerEventsImpl implements DebuggerEvents {

    private static int globalEnabledEventsMask;

    private static final FastThreadLocalInt perThreadEnabledEventsMask = FastThreadLocalFactory.createInt("Debugger.perThreadEnabledEventsMask");

    private static final FastThreadLocalObject<SteppingControl> perThreadSteppingControl = FastThreadLocalFactory.createObject(SteppingControl.class, "Debugger.perThreadSteppingControl");

    private static EventHandler eventHandler;

    @Override
    public void setEventEnabled(Thread thread, EventKind eventKind, boolean enable) {
        if (thread == null) {
            globalEnabledEventsMask = Bits.setBit(globalEnabledEventsMask, eventKind.ordinal(), enable);
        } else {
            IsolateThread isolateThread = PlatformThreads.getIsolateThreadUnsafe(thread);
            int oldMask;
            int newMask;
            do {
                oldMask = perThreadEnabledEventsMask.getVolatile(isolateThread);
                newMask = Bits.setBit(oldMask, eventKind.ordinal(), enable);
            } while (!perThreadEnabledEventsMask.compareAndSet(isolateThread, oldMask, newMask));
        }
    }

    /**
     * Returns events enabled for the specified thread, including the globally enabled events. If
     * the thread is null, then returns events enabled globally.
     */
    private static int enabledEventsMask(Thread thread) {
        if (thread == null) {
            return globalEnabledEventsMask;
        } else {
            IsolateThread isolateThread = PlatformThreads.getIsolateThreadUnsafe(thread);
            return (globalEnabledEventsMask | perThreadEnabledEventsMask.get(isolateThread));
        }
    }

    @Override
    public boolean isEventEnabled(Thread thread, EventKind eventKind) {
        return Bits.testBit(enabledEventsMask(thread), eventKind.ordinal());
    }

    @Override
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "Intentional.")
    public void setEventHandler(EventHandler eventHandler) {
        DebuggerEventsImpl.eventHandler = eventHandler;
    }

    @Override
    public EventHandler getEventHandler() {
        return eventHandler;
    }

    @Override
    public void toggleBreakpoint(ResolvedJavaMethod method, int bci, boolean enable) {
        InterpreterResolvedJavaMethod interpreterMethod = (InterpreterResolvedJavaMethod) method;
        interpreterMethod.ensureCanSetBreakpointAt(bci);
        interpreterMethod.toggleBreakpoint(bci, enable);
        InterpreterUtil.traceInterpreter(enable ? "Setting" : "Unsetting")
                        .string(" breakpoint for method=")
                        .string(interpreterMethod.toString())
                        .string(" at bci=").signed(bci)
                        .newline();
        if (enable) {
            // GR-54095: Make method and all the callers that inline it run in the
            // interpreter.
            // This operation cannot be nested, methods need to keep counters.
            // Ignore token for now.
            InterpreterDirectives.ensureInterpreterExecution(interpreterMethod);
        }
    }

    @Override
    public void toggleMethodEnterEvent(ResolvedJavaType clazz, boolean enable) {
        toggleDeclaredMethodsInterpreterExecution(clazz, enable);
        ((InterpreterResolvedJavaType) clazz).toggleMethodEnterEvent(enable);
    }

    @Override
    public void toggleMethodExitEvent(ResolvedJavaType clazz, boolean enable) {
        toggleDeclaredMethodsInterpreterExecution(clazz, enable);
        ((InterpreterResolvedJavaType) clazz).toggleMethodExitEvent(enable);
    }

    private static void toggleDeclaredMethodsInterpreterExecution(ResolvedJavaType clazz, boolean enable) {
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        for (ResolvedJavaMethod rMethod : universe.getAllDeclaredMethods(clazz)) {
            InterpreterResolvedJavaMethod method = (InterpreterResolvedJavaMethod) rMethod;
            Object token = method.getInterpreterExecToken();
            if (enable) {
                if (token == null) {
                    token = InterpreterDirectives.ensureInterpreterExecution(method);
                    method.setInterpreterExecToken(token);
                }
            } else if (token != null) {
                // GR-54095: The undoExecutionOperation() can not be nested
                // InterpreterDirectives.undoExecutionOperation(token);
                method.setInterpreterExecToken(null);
            }
        }
    }

    @Override
    public SteppingControl getSteppingControl(Thread thread) {
        VMError.guarantee(thread != null);

        IsolateThread isolateThread = PlatformThreads.getIsolateThreadUnsafe(thread);
        return perThreadSteppingControl.get(isolateThread);
    }

    @Override
    public void setSteppingFromLocation(Thread thread, int depth, int size, Location location) {
        VMError.guarantee(thread != null);

        IsolateThread isolateThread = PlatformThreads.getIsolateThreadUnsafe(thread);
        SteppingControl value = new SteppingControl(thread, depth, size);
        perThreadSteppingControl.set(isolateThread, value);

        if (location != null) {
            value.setStartingLocation(location);
        }
    }

    @Override
    public void clearStepping(Thread thread) {
        VMError.guarantee(thread != null);
        IsolateThread isolateThread = PlatformThreads.getIsolateThreadUnsafe(thread);
        perThreadSteppingControl.set(isolateThread, null);
    }
}
