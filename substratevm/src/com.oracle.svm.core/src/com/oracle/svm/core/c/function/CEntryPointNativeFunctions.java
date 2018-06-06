/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c.function;

import java.util.function.Function;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CHeader;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;

@CHeader(value = GraalIsolateHeader.class)
public final class CEntryPointNativeFunctions {

    @CPointerTo(Isolate.class)
    interface IsolatePointer extends PointerBase {
        void write(Isolate isolate);
    }

    @CPointerTo(IsolateThread.class)
    interface IsolateThreadPointer extends PointerBase {
        void write(IsolateThread isolate);
    }

    private static final String UNINTERRUPTIBLE_REASON = "Unsafe state in case of failure";

    public static class NameTransformation implements Function<String, String> {
        @Override
        public String apply(String s) {
            return SubstrateOptions.APIFunctionPrefix.getValue() + s;
        }
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "create_isolate", documentation = {
                    "Create a new isolate, considering the passed parameters (which may be NULL).",
                    "Returns 0 on success, or a non-zero value on failure.",
                    "On success, the current thread is attached to the created isolate, and the",
                    "address of the isolate structure is written to the passed pointer."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = NameTransformation.class)
    public static int createIsolate(CEntryPointCreateIsolateParameters params, IsolatePointer isolate) {
        int result = CEntryPointActions.enterCreateIsolate(params);
        if (result == 0) {
            isolate.write(CEntryPointContext.getCurrentIsolate());
            result = CEntryPointActions.leave();
        }
        return result;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "attach_thread", documentation = {
                    "Attaches the current thread to the passed isolate.",
                    "On failure, returns a non-zero value. On success, writes the address of the",
                    "created isolate thread structure to the passed pointer and returns 0.",
                    "If the thread has already been attached, the call succeeds and also provides",
                    "the thread's isolate thread structure."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = NameTransformation.class)
    public static int attachThread(Isolate isolate, IsolateThreadPointer thread) {
        int result = CEntryPointActions.enterAttachThread(isolate);
        if (result == 0) {
            thread.write(CEntryPointContext.getCurrentIsolateThread());
            result = CEntryPointActions.leave();
        }
        return result;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "current_thread", documentation = {
                    "Given an isolate to which the current thread is attached, returns the address of",
                    "the thread's associated isolate thread structure.  If the current thread is not",
                    "attached to the passed isolate or if another error occurs, returns NULL."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = NameTransformation.class)
    public static IsolateThread getCurrentThread(Isolate isolate) {
        int result = CEntryPointActions.enterIsolate(isolate);
        if (result != 0) {
            return WordFactory.nullPointer();
        }
        IsolateThread thread = CEntryPointContext.getCurrentIsolateThread();
        if (CEntryPointActions.leave() != 0) {
            thread = WordFactory.nullPointer();
        }
        return thread;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "current_isolate", documentation = {
                    "Given an isolate thread structure for the current thread, determines to which",
                    "isolate it belongs and returns the address of its isolate structure.  If an",
                    "error occurs, returns NULL instead."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = NameTransformation.class)
    public static Isolate getCurrentIsolate(IsolateThread thread) {
        int result = CEntryPointActions.enter(thread);
        if (result != 0) {
            return WordFactory.nullPointer();
        }
        Isolate isolate = CEntryPointContext.getCurrentIsolate();
        if (CEntryPointActions.leave() != 0) {
            isolate = WordFactory.nullPointer();
        }
        return isolate;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "detach_thread", documentation = {
                    "Detaches the passed isolate thread from its isolate and discards any state or",
                    "context that is associated with it. At the time of the call, no code may still",
                    "be executing in the isolate thread's context.",
                    "Returns 0 on success, or a non-zero value on failure."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = NameTransformation.class)
    public static int detachThread(IsolateThread thread) {
        int result = CEntryPointActions.enter(thread);
        if (result != 0) {
            CEntryPointActions.leave();
            return result;
        }
        result = CEntryPointActions.leaveDetachThread();
        return result;
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "tear_down_isolate", documentation = {
                    "Tears down the passed isolate, waiting for any attached threads to detach from",
                    "it, then discards the isolate's objects, threads, and any other state or context",
                    "that is associated with it.",
                    "Returns 0 on success, or a non-zero value on failure."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = NameTransformation.class)
    public static int tearDownIsolate(Isolate isolate) {
        int result = CEntryPointActions.enterAttachThread(isolate);
        if (result != 0) {
            CEntryPointActions.leave();
            return result;
        }
        result = CEntryPointActions.leaveTearDownIsolate();
        return result;
    }

    private CEntryPointNativeFunctions() {
    }
}
