/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.compacting;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.ref.Reference;

import org.graalvm.word.Pointer;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.UninterruptibleObjectReferenceVisitor;
import com.oracle.svm.core.metaspace.Metaspace;

import jdk.graal.compiler.word.Word;

/**
 * Updates each reference after marking and before compaction to point to the referenced object's
 * future location.
 */
public final class ObjectRefFixupVisitor implements UninterruptibleObjectReferenceVisitor {
    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
        Pointer pos = firstObjRef;
        Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
        while (pos.belowThan(end)) {
            visitObjectReference(pos, compressed, holderObject);
            pos = pos.add(referenceSize);
        }
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
        Pointer p = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
        if (p.isNull() || HeapImpl.getHeapImpl().isInImageHeap(p) || Metaspace.singleton().isInAddressSpace(p)) {
            return;
        }

        Object original = p.toObjectNonNull();
        if (ObjectHeaderImpl.isAlignedObject(original)) {
            Pointer newLocation = ObjectMoveInfo.getNewObjectAddress(p);
            assert newLocation.isNonNull() //
                            || holderObject == null // references from CodeInfo, invalidated or weak
                            || holderObject instanceof Reference<?>; // cleared referent

            Object obj = newLocation.toObjectNonNull();
            ReferenceAccess.singleton().writeObjectAt(objRef, obj, compressed);
        }
        // Note that image heap cards have already been cleaned and re-marked during the scan
    }
}
