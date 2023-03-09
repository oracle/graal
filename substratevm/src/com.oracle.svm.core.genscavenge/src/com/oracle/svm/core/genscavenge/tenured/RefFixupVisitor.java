/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.tenured;

import org.graalvm.word.Pointer;

import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;

import java.lang.ref.Reference;

public class RefFixupVisitor implements ObjectReferenceVisitor {

    @Override
    public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
        return visitObjectReferenceInline(objRef, 0, compressed, holderObject);
    }

    @Override
    public boolean visitObjectReferenceInline(Pointer objRef, int innerOffset, boolean compressed, Object holderObject) {
        assert innerOffset == 0; // Will always be 0.

        Pointer p = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
        if (p.isNull()) {
            return true;
        }

        if (HeapImpl.getHeapImpl().isInImageHeap(p)) {
            return true;
        }

        Object obj = p.toObject();
        if (ObjectHeaderImpl.isUnalignedObject(obj)) {
            if (HeapImpl.getHeapImpl().isInImageHeap(holderObject)) {
                RememberedSet.get().dirtyCardIfNecessary(holderObject, obj);
            }
            return true;
        }

        Pointer newLocation = RelocationInfo.getRelocatedObjectPointer(p);
        assert newLocation.isNonNull() || holderObject == null || holderObject instanceof Reference<?>;

        Object relocatedObj = newLocation.toObject();
        ReferenceAccess.singleton().writeObjectAt(objRef, relocatedObj, compressed);

        if (HeapImpl.getHeapImpl().isInImageHeap(holderObject)) {
            RememberedSet.get().dirtyCardIfNecessary(holderObject, relocatedObj);
        }

        return true;
    }
}
