/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.nativeapi;

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotIsolate;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotIsolateParameters;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotIsolatePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotIsolateThread;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotIsolateThreadPointer;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CConst;
import com.oracle.svm.core.c.CHeader;
import com.oracle.svm.core.c.CTypedef;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.c.function.CEntryPointNativeFunctions.IsolatePointer;
import com.oracle.svm.core.c.function.CEntryPointNativeFunctions.IsolateThreadPointer;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.UnchangedNameTransformation;

@CHeader(PolyglotIsolateHeader.class)
public final class PolyglotNativeAPIEntryPoints {
    private static final String UNINTERRUPTIBLE_REASON = "Unsafe state in case of failure";

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "poly_create_isolate", documentation = {
                    "Create a new isolate, considering the passed parameters (which may be NULL).",
                    "Returns poly_ok on success, or a poly_generic_failure value on failure.",
                    "On success, the current thread is attached to the created isolate, and the",
                    "address of the isolate structure is written to the passed pointer.",
                    "Every thread starts with a default handle scope. This scope is released when",
                    "the thread is detached."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = UnchangedNameTransformation.class)
    public static @CTypedef(name = "poly_status") int polyCreateIsolate(@CConst PolyglotIsolateParameters params, PolyglotIsolatePointer isolate, PolyglotIsolateThreadPointer thread) {
        return createIsolate(params, isolate, thread) == 0 ? Poly.ok() : Poly.generic_failure();
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "poly_attach_thread", documentation = {
                    "Attaches the current thread to the passed isolate.",
                    "On failure, returns poly_generic_failure. On success, writes the address of the",
                    "created isolate thread structure to the passed pointer and returns poly_ok.",
                    "If the thread has already been attached, the call succeeds and also provides",
                    "the thread's isolate thread structure."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = UnchangedNameTransformation.class)
    public static @CTypedef(name = "poly_status") int polyAttachThread(PolyglotIsolate isolate, PolyglotIsolateThreadPointer thread) {
        return attachThread(isolate, thread) == 0 ? Poly.ok() : Poly.generic_failure();
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "poly_get_current_thread", documentation = {
                    "Given an isolate to which the current thread is attached, returns the address of",
                    "the thread's associated isolate thread structure.  If the current thread is not",
                    "attached to the passed isolate or if another error occurs, returns NULL."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = UnchangedNameTransformation.class)
    public static PolyglotIsolateThread polyGetCurrentThread(PolyglotIsolate isolate) {
        return (PolyglotIsolateThread) getCurrentThread(isolate);
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "poly_get_isolate", documentation = {
                    "Given an isolate thread structure, determines to which isolate it belongs and",
                    "returns the address of its isolate structure.  If an error occurs, returns NULL",
                    "instead."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = UnchangedNameTransformation.class)
    public static PolyglotIsolate polyGetIsolate(PolyglotIsolateThread thread) {
        return (PolyglotIsolate) getIsolate(thread);
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "poly_detach_thread", documentation = {
                    "Detaches the passed isolate thread from its isolate and discards any state or",
                    "context that is associated with it. At the time of the call, no code may still",
                    "be executing in the isolate thread's context.",
                    "Returns poly_ok on success, or poly_generic_failure on failure."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = UnchangedNameTransformation.class)
    public static @CTypedef(name = "poly_status") int polyDetachThread(PolyglotIsolateThread thread) {
        return detachThread(thread) == 0 ? Poly.ok() : Poly.generic_failure();
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "poly_detach_all_threads_and_tear_down_isolate", documentation = {
                    "In the isolate of the passed isolate thread, detach all those threads that were",
                    "externally started (not within Java, which includes the \"main thread\") and were",
                    "attached to the isolate afterwards. Afterwards, all threads that were started",
                    "within Java undergo a regular shutdown process, followed by the tear-down of the",
                    "entire isolate, which detaches the current thread and discards the objects,",
                    "threads, and any other state or context associated with the isolate.",
                    "None of the manually attached threads targeted by this function may be executing",
                    "Java code at the time when this function is called or at any point in the future",
                    "or this will cause entirely undefined (and likely fatal) behavior.",
                    "Returns poly_ok on success, or poly_generic_failure on failure."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = UnchangedNameTransformation.class)
    public static @CTypedef(name = "poly_status") int polyDetachAllThreadsAndTearDownIsolate(PolyglotIsolateThread thread) {
        return detachAllThreadsAndTearDownIsolate(thread) == 0 ? Poly.ok() : Poly.generic_failure();
    }

    @Uninterruptible(reason = UNINTERRUPTIBLE_REASON)
    @CEntryPoint(name = "poly_tear_down_isolate", documentation = {
                    "Tears down the passed isolate, waiting for any attached threads to detach from",
                    "it, then discards the isolate's objects, threads, and any other state or context",
                    "that is associated with it.",
                    "Returns poly_ok on success, or poly_generic_failure on failure."})
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, nameTransformation = UnchangedNameTransformation.class)
    public static @CTypedef(name = "poly_status") int polyTearDownIsolate(PolyglotIsolateThread thread) {
        return tearDownIsolate(thread) == 0 ? Poly.ok() : Poly.generic_failure();
    }

    @CFunction(value = "graal_create_isolate", transition = NO_TRANSITION)
    private static native int createIsolate(CEntryPointCreateIsolateParameters params, IsolatePointer isolate, IsolateThreadPointer thread);

    @CFunction(value = "graal_attach_thread", transition = NO_TRANSITION)
    private static native int attachThread(Isolate isolate, IsolateThreadPointer thread);

    @CFunction(value = "graal_get_current_thread", transition = NO_TRANSITION)
    private static native IsolateThread getCurrentThread(Isolate isolate);

    @CFunction(value = "graal_get_isolate", transition = NO_TRANSITION)
    private static native Isolate getIsolate(IsolateThread thread);

    @CFunction(value = "graal_detach_thread", transition = NO_TRANSITION)
    private static native int detachThread(IsolateThread thread);

    @CFunction(value = "graal_detach_all_threads_and_tear_down_isolate", transition = NO_TRANSITION)
    private static native int detachAllThreadsAndTearDownIsolate(IsolateThread thread);

    @CFunction(value = "graal_tear_down_isolate", transition = NO_TRANSITION)
    private static native int tearDownIsolate(IsolateThread thread);

    @CContext(PolyglotNativeAPICContext.class)
    private static class Poly {
        @CConstant("poly_ok")
        static native int ok();

        @CConstant("poly_generic_failure")
        static native int generic_failure();
    }
}
