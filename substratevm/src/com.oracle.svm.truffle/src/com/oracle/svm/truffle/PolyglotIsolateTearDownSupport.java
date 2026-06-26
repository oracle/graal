/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
final class PolyglotIsolateTearDownSupport {

    private final CGlobalData<DetachAllThreadsAndTearDownIsolate> detachAllThreadsAndTearDownIsolateFunction;

    /**
     * The {@code detach_all_threads_and_tear_down_isolate} symbol is duplicated in the
     * native-to-native with external Truffle isolate library mode. The symbol is both in the
     * executable and the Truffle isolate library. On Linux the symbol is resolved to the wrong
     * function, the function in the executable. On macOS and Windows it's resolved correctly. To
     * make the native-to-native with external Truffle isolate library work on all systems we need
     * to remove symbol name clashes by specifying the API function prefix for the Truffle isolate
     * library.
     *
     * @see PolyglotIsolateGuestFeature#afterRegistration(Feature.AfterRegistrationAccess)
     */
    PolyglotIsolateTearDownSupport(String apiPrefix) {
        String detachAllThreadsAndTearDownIsolateSymbol = apiPrefix + "detach_all_threads_and_tear_down_isolate";
        detachAllThreadsAndTearDownIsolateFunction = CGlobalDataFactory.forSymbol(detachAllThreadsAndTearDownIsolateSymbol);
    }

    /**
     * Detaches all threads that were attached to the isolate and tears down the specified isolate.
     * It requires a parameter of type {@link IsolateThread}, and a return type of {@code int} or
     * {@code void}. With an {@code int} return type, zero is returned when successful, or non-zero
     * in case of an error.
     * <p>
     * This method is intended for internal use only as it is unsafe.
     */
    @Uninterruptible(reason = "Heap base and thread register are not set up.")
    @CEntryPoint(name = "Java_com_oracle_truffle_polyglot_isolate_PolyglotNativeIsolateHandler_tearDownIsolate", include = OptionalTrufflePolyglotGuestFeatureEnabled.class)
    @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class)
    @SuppressWarnings("unused")
    private static int tearDownIsolate(JNIEnv jniEnv, JClass clazz, IsolateThread isolateThread) {
        return ImageSingletons.lookup(PolyglotIsolateTearDownSupport.class).detachAllThreadsAndTearDownIsolateFunction.get().call(isolateThread);
    }

    private interface DetachAllThreadsAndTearDownIsolate extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = NO_TRANSITION)
        int call(IsolateThread isolateThread);
    }
}
