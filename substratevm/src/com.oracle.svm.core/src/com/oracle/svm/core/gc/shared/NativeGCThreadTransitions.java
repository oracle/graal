/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shared;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.InitializeReservedRegistersPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.thread.ThreadStatusTransition;

/**
 * The methods below are called if any GC-related C++ code needs to do a thread status transitions.
 * This ensures that the GC doesn't need to know how thread status transitions work in detail.
 */
public class NativeGCThreadTransitions {
    public final CEntryPointLiteral<CFunctionPointer> funcVMToNative;
    public final CEntryPointLiteral<CFunctionPointer> funcFastNativeToVM;
    public final CEntryPointLiteral<CFunctionPointer> funcSlowNativeToVM;

    @Platforms(Platform.HOSTED_ONLY.class)
    public NativeGCThreadTransitions() {
        funcVMToNative = CEntryPointLiteral.create(NativeGCThreadTransitions.class, "fromVMToNative", IsolateThread.class);
        funcFastNativeToVM = CEntryPointLiteral.create(NativeGCThreadTransitions.class, "fastFromNativeToVM", IsolateThread.class);
        funcSlowNativeToVM = CEntryPointLiteral.create(NativeGCThreadTransitions.class, "slowFromNativeToVM", IsolateThread.class);
    }

    @Uninterruptible(reason = "Must not do a safepoint check after the thread status transition.")
    @CEntryPoint(include = UseNativeGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersPrologue.class, epilogue = NoEpilogue.class)
    public static void fromVMToNative(@SuppressWarnings("unused") IsolateThread thread) {
        ThreadStatusTransition.fromVMToNative();
    }

    @Uninterruptible(reason = "Must not do a safepoint check after the thread status transition.")
    @CEntryPoint(include = UseNativeGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersPrologue.class, epilogue = NoEpilogue.class)
    public static boolean fastFromNativeToVM(@SuppressWarnings("unused") IsolateThread thread) {
        return ThreadStatusTransition.tryFromNativeToVM();
    }

    @Uninterruptible(reason = "Must not do a safepoint check after the thread status transition.")
    @CEntryPoint(include = UseNativeGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersPrologue.class, epilogue = NoEpilogue.class)
    public static void slowFromNativeToVM(@SuppressWarnings("unused") IsolateThread thread) {
        ThreadStatusTransition.fromNativeToVM();
    }
}
