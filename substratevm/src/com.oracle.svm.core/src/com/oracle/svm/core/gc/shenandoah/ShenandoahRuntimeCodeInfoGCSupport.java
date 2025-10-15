/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shenandoah;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahLibrary;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.heap.RuntimeCodeCacheCleaner;
import com.oracle.svm.core.heap.RuntimeCodeInfoGCSupport;

public class ShenandoahRuntimeCodeInfoGCSupport extends RuntimeCodeInfoGCSupport {
    private static final RuntimeCodeCacheCleaner CODE_CACHE_CLEANER = new RuntimeCodeCacheCleaner();

    public final CEntryPointLiteral<CFunctionPointer> funcCleanCodeCache;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ShenandoahRuntimeCodeInfoGCSupport() {
        funcCleanCodeCache = RuntimeCompilation.isEnabled() ? CEntryPointLiteral.create(ShenandoahRuntimeCodeInfoGCSupport.class, "cleanCodeCache", PointerBase.class, IsolateThread.class) : null;
    }

    @Override
    @Uninterruptible(reason = "Called when installing code.", callerMustBe = true)
    public void registerObjectFields(CodeInfo codeInfo) {
        ShenandoahLibrary.registerObjectFields(codeInfo);
    }

    @Override
    @Uninterruptible(reason = "Called when installing code.", callerMustBe = true)
    public void registerCodeConstants(CodeInfo codeInfo) {
        ShenandoahLibrary.registerCodeConstants(codeInfo);
    }

    @Override
    @Uninterruptible(reason = "Called when installing code.", callerMustBe = true)
    public void registerFrameMetadata(CodeInfo codeInfo) {
        ShenandoahLibrary.registerFrameMetadata(codeInfo);
    }

    @Override
    @Uninterruptible(reason = "Called when installing code.", callerMustBe = true)
    public void registerDeoptMetadata(CodeInfo codeInfo) {
        ShenandoahLibrary.registerDeoptMetadata(codeInfo);
    }

    @CEntryPoint(include = UseShenandoahGC.class, publishAs = Publish.NotPublished)
    public static void cleanCodeCache(@SuppressWarnings("unused") PointerBase heapBase, @SuppressWarnings("unused") IsolateThread thread) {
        RuntimeCodeInfoMemory.singleton().walkRuntimeMethodsDuringGC(CODE_CACHE_CLEANER);
    }
}
