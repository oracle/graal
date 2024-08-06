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
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;

/**
 * Updates each reference after marking and before compaction to point to the referenced object's
 * future location.
 */
public final class ObjectRefFixupVisitor implements ObjectReferenceVisitor {
    @Override
    public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
        return visitObjectReferenceInline(objRef, 0, compressed, holderObject);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean visitObjectReferenceInline(Pointer objRef, int innerOffset, boolean compressed, Object holderObject) {
        assert innerOffset == 0;
        Pointer p = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
        if (p.isNull() || HeapImpl.getHeapImpl().isInImageHeap(p)) {
            return true;
        }

        Object obj;
        Object original = p.toObject();
        if (ObjectHeaderImpl.isAlignedObject(original)) {
            Pointer newLocation = ObjectMoveInfo.getNewObjectAddress(p);
            assert newLocation.isNonNull() //
                            || holderObject == null // references from CodeInfo, invalidated or weak
                            || holderObject instanceof Reference<?>; // cleared referent

            obj = newLocation.toObject();
            ReferenceAccess.singleton().writeObjectAt(objRef, obj, compressed);
        } else {
            obj = original;
        }
        if (HeapImpl.usesImageHeapCardMarking() && HeapImpl.getHeapImpl().isInImageHeap(holderObject)) {
            RememberedSet.get().dirtyCardIfNecessary(holderObject, obj);
        }
        return true;
    }
}
