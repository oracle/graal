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
package com.oracle.svm.core.gc.shenandoah.graal;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.gc.shared.graal.NativeGCAllocationSupport;
import com.oracle.svm.core.gc.shenandoah.ShenandoahConstants;
import com.oracle.svm.core.gc.shenandoah.ShenandoahHeap;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahLibrary;
import com.oracle.svm.core.hub.DynamicHub;

import jdk.graal.compiler.word.Word;

public class ShenandoahAllocationSupport extends NativeGCAllocationSupport {
    @Override
    public Word getTLABInfo() {
        return ShenandoahHeap.javaThreadTL.getAddress();
    }

    @Override
    public int tlabTopOffset() {
        return ShenandoahConstants.tlabTopOffset();
    }

    @Override
    public int tlabEndOffset() {
        return ShenandoahConstants.tlabEndOffset();
    }

    @Override
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected Object allocateInstance0(DynamicHub hub) {
        Word result = ShenandoahLibrary.allocateInstance(Word.objectToUntrackedPointer(hub));
        return result.toObject();
    }

    @Override
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected Object allocateArray0(int length, DynamicHub hub) {
        Word result = ShenandoahLibrary.allocateArray(Word.objectToUntrackedPointer(hub), length);
        return result.toObject();
    }

    @Override
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected Object allocateStoredContinuation0(int length, DynamicHub hub) {
        Word result = ShenandoahLibrary.allocateStoredContinuation(Word.objectToUntrackedPointer(hub), length);
        return result.toObject();
    }

    @Override
    @Uninterruptible(reason = "The newly allocated object must be young or all its covered cards must be dirty.", callerMustBe = true, calleeMustBe = false)
    protected Object allocatePod0(int length, DynamicHub hub) {
        Word result = ShenandoahLibrary.allocatePod(Word.objectToUntrackedPointer(hub), length);
        return result.toObject();
    }
}
