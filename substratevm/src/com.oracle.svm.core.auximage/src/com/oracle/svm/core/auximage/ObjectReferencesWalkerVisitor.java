/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import org.graalvm.word.Pointer;

import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubIntrinsics;
import com.oracle.svm.shared.util.UnsignedUtils;

import jdk.graal.compiler.core.common.NumUtil;
import org.graalvm.word.impl.Word;

abstract class ObjectReferencesWalkerVisitor implements ObjectReferenceVisitor {

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while inspecting an object.")
    public final void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
        DynamicHub holderHub = DynamicHubIntrinsics.readHub(holderObject);
        Pointer objAddr = Word.objectToUntrackedPointer(holderObject);
        int firstOffset = UnsignedUtils.safeToInt(firstObjRef.subtract(objAddr));
        int monitorOffset = holderHub.getMonitorOffset();
        int endOffset = firstOffset + referenceSize * count;
        if (monitorOffset != 0 && monitorOffset >= firstOffset && monitorOffset < endOffset) {
            int leadingCount = (monitorOffset - firstOffset) / referenceSize;
            visitNonMonitorReferences(holderObject, firstOffset, compressed, referenceSize, leadingCount, holderHub);
            visitObjectMonitor(holderObject, monitorOffset, compressed);
            int trailingCount = count - 1 - leadingCount;
            visitNonMonitorReferences(holderObject, monitorOffset + referenceSize, compressed, referenceSize, trailingCount, holderHub);
        } else {
            visitNonMonitorReferences(holderObject, firstOffset, compressed, referenceSize, count, holderHub);
        }
    }

    private void visitNonMonitorReferences(Object obj, int firstOffset, boolean compressed, int referenceSize, int count, DynamicHub objHub) {
        assert count >= 0;
        if (count == 0) {
            return;
        }
        if (objHub.isReferenceInstanceClass()) {
            int nextFieldOffset = NumUtil.safeToInt(ReferenceInternals.getNextDiscoveredFieldOffset());
            int endOffset = firstOffset + referenceSize * count;
            if (nextFieldOffset >= firstOffset && nextFieldOffset < endOffset) {
                int leadingCount = (nextFieldOffset - firstOffset) / referenceSize;
                invokeVisitRegularReferences(obj, firstOffset, compressed, referenceSize, leadingCount);
                visitReferenceObjectDiscoveredLink(obj, nextFieldOffset, compressed);
                int trailingCount = count - 1 - leadingCount;
                invokeVisitRegularReferences(obj, nextFieldOffset + referenceSize, compressed, referenceSize, trailingCount);
                return;
            }
        }
        invokeVisitRegularReferences(obj, firstOffset, compressed, referenceSize, count);
    }

    private void invokeVisitRegularReferences(Object obj, int firstOffset, boolean compressed, int referenceSize, int count) {
        assert count >= 0;
        if (count != 0) {
            visitRegularReferences(obj, firstOffset, compressed, referenceSize, count);
        }
    }

    protected abstract void visitObjectMonitor(Object obj, int offset, boolean compressed);

    protected abstract void visitReferenceObjectDiscoveredLink(Object obj, int offset, boolean compressed);

    protected abstract void visitReferenceObjectReferent(Object obj, int offset, boolean compressed, Object target);

    protected abstract void visitRegularReferences(Object obj, int firstOffset, boolean compressed, int referenceSize, int count);

    protected abstract void visitReferenceInCode(Object obj, int offset, boolean compressed, Object target);
}
