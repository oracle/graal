/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicReference;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.UnmanagedMemory;

@TargetClass(className = "com.oracle.truffle.nfi.backend.libffi.NativeAllocation", onlyWith = TruffleNFIFeature.IsEnabled.class)
final class Target_com_oracle_truffle_nfi_backend_libffi_NativeAllocation {

    @Substitute
    static void free(long pointer) {
        UnmanagedMemory.free(Word.pointer(pointer));
    }

    /**
     * If the NFI is already used during image build time while building a preinitialized context,
     * we need to reset this value to null, so the GC thread will be re-initialized during image
     * loading.
     */
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias) //
    static AtomicReference<Thread> gcThread = new AtomicReference<>(null);

    /**
     * If the NFI is already used during image build time, it might happen that even though no
     * objects are alive anymore, some of the destructors are still queued and haven't been run yet.
     * These destructors need to run in the image build process. Since none of these objects are
     * allowed to be stored in the image heap anyway, we can just reset to an empty queue there.
     */
    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClassName = "com.oracle.truffle.nfi.backend.libffi.NativeAllocation$Queue") //
    static Target_com_oracle_truffle_nfi_backend_libffi_NativeAllocation_Queue globalQueue;

    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ReferenceQueue.class) //
    static ReferenceQueue<Object> refQueue;
}
