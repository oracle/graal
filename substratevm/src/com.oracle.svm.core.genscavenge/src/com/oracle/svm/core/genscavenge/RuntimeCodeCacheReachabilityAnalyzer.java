/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.DuplicatedInNativeCode;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.RuntimeCodeCacheCleaner;
import com.oracle.svm.core.hub.DynamicHub;

@DuplicatedInNativeCode
final class RuntimeCodeCacheReachabilityAnalyzer implements ObjectReferenceVisitor {
    private boolean unreachableObjects;

    @Platforms(Platform.HOSTED_ONLY.class)
    RuntimeCodeCacheReachabilityAnalyzer() {
    }

    public void initialize() {
        this.unreachableObjects = false;
    }

    public boolean hasUnreachableObjects() {
        return unreachableObjects;
    }

    @Override
    public boolean visitObjectReference(Pointer ptrPtrToObject, boolean compressed, Object holderObject) {
        assert !unreachableObjects;
        Pointer ptrToObj = ReferenceAccess.singleton().readObjectAsUntrackedPointer(ptrPtrToObject, compressed);
        if (ptrToObj.isNonNull() && !isReachable(ptrToObj)) {
            unreachableObjects = true;
            return false;
        }
        return true;
    }

    public static boolean isReachable(Pointer ptrToObj) {
        assert ptrToObj.isNonNull();
        if (HeapImpl.getHeapImpl().isInImageHeap(ptrToObj)) {
            return true;
        }

        UnsignedWord header = ObjectHeaderImpl.readHeaderFromPointer(ptrToObj);
        if (ObjectHeaderImpl.isForwardedHeader(header)) {
            return true;
        }

        Space space = HeapChunk.getSpace(HeapChunk.getEnclosingHeapChunk(ptrToObj, header));
        if (!space.isFromSpace()) {
            return true;
        }

        ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
        Class<?> clazz = DynamicHub.toClass(ohi.dynamicHubFromObjectHeader(header));
        return isAssumedReachable(clazz);
    }

    private static boolean isAssumedReachable(Class<?> clazz) {
        Class<?>[] classesAssumedReachable = RuntimeCodeCacheCleaner.CLASSES_ASSUMED_REACHABLE;
        for (int i = 0; i < classesAssumedReachable.length; i++) {
            if (classesAssumedReachable[i].isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }
}
