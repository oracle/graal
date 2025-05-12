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

import java.io.Serial;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.RuntimeCodeCacheCleaner;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.DuplicatedInNativeCode;

import jdk.graal.compiler.word.Word;

/**
 * Analyzes if run-time compiled code has any references to otherwise unreachable objects. Throws an
 * {@link UnreachableObjectsException} if a reference to an otherwise unreachable object is
 * detected.
 */
@DuplicatedInNativeCode
final class RuntimeCodeCacheReachabilityAnalyzer implements ObjectReferenceVisitor {
    private static final UnreachableObjectsException UNREACHABLE_OBJECTS_EXCEPTION = new UnreachableObjectsException();

    @Platforms(Platform.HOSTED_ONLY.class)
    RuntimeCodeCacheReachabilityAnalyzer() {
    }

    @Override
    public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
        Pointer pos = firstObjRef;
        Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
        while (pos.belowThan(end)) {
            visitObjectReference(pos, compressed);
            pos = pos.add(referenceSize);
        }
    }

    private static void visitObjectReference(Pointer ptrPtrToObject, boolean compressed) {
        Pointer ptrToObj = ReferenceAccess.singleton().readObjectAsUntrackedPointer(ptrPtrToObject, compressed);
        if (ptrToObj.isNonNull() && !isReachable(ptrToObj)) {
            throw UNREACHABLE_OBJECTS_EXCEPTION;
        }
    }

    public static boolean isReachable(Pointer ptrToObj) {
        assert ptrToObj.isNonNull();
        if (HeapImpl.getHeapImpl().isInImageHeap(ptrToObj)) {
            return true;
        }

        ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
        Word header = ohi.readHeaderFromPointer(ptrToObj);
        if (ObjectHeaderImpl.isForwardedHeader(header)) {
            return true;
        }
        if (SerialGCOptions.useCompactingOldGen() && ObjectHeaderImpl.isMarkedHeader(header)) {
            return true;
        }
        Space space = HeapChunk.getSpace(HeapChunk.getEnclosingHeapChunk(ptrToObj, header));
        if (space.isToSpace()) {
            return true;
        }
        if (space.isCompactingOldSpace() && !GCImpl.getGCImpl().isCompleteCollection()) {
            return true;
        }
        Class<?> clazz = DynamicHub.toClass(ohi.dynamicHubFromObjectHeader(header));
        return isAssumedReachable(clazz);
    }

    private static boolean isAssumedReachable(Class<?> clazz) {
        Class<?>[] classesAssumedReachable = RuntimeCodeCacheCleaner.CLASSES_ASSUMED_REACHABLE;
        for (Class<?> aClass : classesAssumedReachable) {
            if (aClass.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }

    static final class UnreachableObjectsException extends RuntimeException {
        @Serial private static final long serialVersionUID = 1L;

        @Override
        @SuppressWarnings("sync-override")
        public Throwable fillInStackTrace() {
            /* No stacktrace needed. */
            return this;
        }
    }
}
