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
package com.oracle.svm.core.genscavenge;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.heap.DerivedReferenceSupport;
import com.oracle.svm.shared.Uninterruptible;

public final class DerivedReferenceUpdater {
    private Pointer baseBefore;
    private Pointer baseAfter;

    @Platforms(Platform.HOSTED_ONLY.class)
    public DerivedReferenceUpdater() {
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void captureBaseBeforeUpdate(Pointer baseObjRef, boolean compressed) {
        baseBefore = DerivedReferenceSupport.readReferenceAsPointer(baseObjRef, compressed);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void captureBaseAfterUpdate(Pointer baseObjRef, boolean compressed) {
        baseAfter = DerivedReferenceSupport.readReferenceAsPointer(baseObjRef, compressed);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void updateDerivedReference(Pointer derivedObjRef, boolean compressed, Pointer derivedBefore) {
        updateDerivedReference(derivedObjRef, compressed, baseBefore, baseAfter, derivedBefore);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void updateDerivedReference(Pointer derivedObjRef, boolean compressed, Pointer baseBefore, Pointer baseAfter, Pointer derivedBefore) {
        if (baseBefore.isNull() || derivedBefore.isNull()) {
            return;
        }

        if (baseAfter.isNull()) {
            DerivedReferenceSupport.writeReference(derivedObjRef, Word.nullPointer(), compressed);
            return;
        }

        long offset = derivedBefore.rawValue() - baseBefore.rawValue();
        Pointer derivedAfter = Word.pointer(baseAfter.rawValue() + offset);
        DerivedReferenceSupport.writeReference(derivedObjRef, derivedAfter, compressed);
    }
}
