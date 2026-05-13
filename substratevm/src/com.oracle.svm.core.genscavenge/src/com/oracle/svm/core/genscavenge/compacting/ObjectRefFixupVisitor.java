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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.ref.Reference;

import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.UninterruptibleObjectReferenceVisitor;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.Uninterruptible;

/**
 * Updates each reference after marking and before compaction to point to the referenced object's
 * future location.
 */
public final class ObjectRefFixupVisitor implements UninterruptibleObjectReferenceVisitor {
    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
        Pointer referenceSlot = firstObjRef;
        Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
        while (referenceSlot.belowThan(end)) {
            visitObjectReference(referenceSlot, 0, compressed, holderObject, false);
            referenceSlot = referenceSlot.add(referenceSize);
        }
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void visitDerivedReference(Pointer derivedReferenceSlot, int innerOffset, boolean compressed, Object holderObject) {
        visitObjectReference(derivedReferenceSlot, innerOffset, compressed, holderObject, true);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void visitObjectReference(Pointer referenceSlot, int innerOffset, boolean compressed, Object holderObject, boolean derivedReference) {
        assert innerOffset >= 0;
        Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(referenceSlot, compressed);
        Pointer basePointer = referentPointer.subtract(innerOffset);
        assert !derivedReference || basePointer.isNonNull() : "Base object of derived reference must not be null.";
        if (basePointer.isNull()) {
            return;
        }
        if (HeapImpl.getHeapImpl().isInImageHeap(basePointer) || Metaspace.singleton().isInAddressSpace(basePointer)) {
            return;
        }

        Object original = basePointer.toObjectNonNull();
        if (ObjectHeaderImpl.isAlignedObject(original)) {
            Pointer newBasePointer = ObjectMoveInfo.getNewObjectAddress(basePointer);
            assert newBasePointer.isNonNull() //
                            || holderObject == null // references from CodeInfo, invalidated or weak
                            || holderObject instanceof Reference<?>; // cleared referent

            if (derivedReference) {
                assert newBasePointer.isNonNull() : "Base object of derived reference must not be cleared.";
                Pointer newDerivedPointer = newBasePointer.add(innerOffset);
                ReferenceAccess.singleton().writeDerivedReferenceAt(referenceSlot, newDerivedPointer, compressed);
            } else {
                Object newObject = newBasePointer.isNull() ? null : newBasePointer.toObject();
                ReferenceAccess.singleton().writeObjectAt(referenceSlot, newObject, compressed);
            }
        }
        // Note that image heap cards have already been cleaned and re-marked during the scan
    }
}
