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

import java.io.Serial;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Function;

import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;

final class ObjectTraversal {
    interface Callback {
        void traverseReference(Object from, Object to);
    }

    private static final BufferOverflowException BUFFER_OVERFLOW_EXCEPTION = new BufferOverflowException();

    private final Function<Object, ObjectReferencesWalker> walkerProvider;

    private final ObjectReferenceCollector collector = new ObjectReferenceCollector();
    private final Queue<Object> queue = new ArrayDeque<>();

    private final Object[] buffer = new Object[512];
    private int totalCount;
    private int bufferCount;
    private int skipFirst;
    private int skipped;

    ObjectTraversal(Function<Object, ObjectReferencesWalker> walkerProvider) {
        this.walkerProvider = walkerProvider;
    }

    boolean isFinished() {
        return queue.isEmpty();
    }

    void addObject(Object to) {
        queue.add(to);
    }

    void traverse(Callback callback) {
        while (!queue.isEmpty()) {
            Object obj = queue.poll();
            totalCount = 0;
            boolean bufferOverflow;
            do {
                skipFirst = totalCount;
                skipped = 0;
                bufferCount = 0;

                ObjectReferencesWalker walker = walkerProvider.apply(obj);
                bufferOverflow = walkAllReferences(walker, obj);
                for (int i = 0; i < bufferCount; i++) {
                    callback.traverseReference(obj, buffer[i]);
                }
            } while (bufferOverflow);
        }
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while inspecting an object.")
    private boolean walkAllReferences(ObjectReferencesWalker walker, Object obj) {
        try {
            walker.walkReferencesOf(obj, collector);
            return false;
        } catch (BufferOverflowException e) {
            return true;
        }
    }

    private final class ObjectReferenceCollector extends ObjectReferencesWalkerVisitor {
        @Override
        public void visitObjectMonitor(Object obj, int offset, boolean compressed) {
            // not included in the image
        }

        @Override
        public void visitReferenceObjectDiscoveredLink(Object obj, int offset, boolean compressed) {
            // VM-internal list linkage
        }

        @Override
        public void visitReferenceObjectReferent(Object obj, int offset, boolean compressed, Object target) {
            visitSingleReference(target);
        }

        @Override
        public void visitReferenceInCode(Object obj, int offset, boolean compressed, Object target) {
            visitSingleReference(target);
        }

        @Override
        public void visitRegularReferences(Object obj, int firstOffset, boolean compressed, int referenceSize, int count) {
            int offset = firstOffset;
            int remaining = count;
            int skip = skipFirst - skipped;
            if (skip > 0) {
                if (skip > remaining) {
                    skipped += remaining;
                    return;
                }
                offset += skip * referenceSize;
                remaining -= skip;
                skipped += skip;
            }
            Pointer p = Word.objectToUntrackedPointer(obj).add(offset);
            Pointer end = p.add(Word.unsigned(referenceSize).multiply(remaining));
            while (p.belowThan(end)) {
                Object target = ReferenceAccess.singleton().readObjectAt(p, compressed);
                visitSingleReference(target);
                p = p.add(referenceSize);
            }
        }

        private void visitSingleReference(Object target) {
            /*
             * We must not allocate while inspecting an object to avoid garbage collection, so we
             * fill a buffer with as many references as we can. If there are more than fit in the
             * buffer, we visit again, which should only be the case for larger object arrays.
             */
            if (skipped < skipFirst) {
                skipped++;
                return;
            }
            if (target != null) {
                if (bufferCount < buffer.length) {
                    buffer[bufferCount] = target;
                    bufferCount++;
                } else {
                    throw BUFFER_OVERFLOW_EXCEPTION;
                }
            }
            totalCount++;
        }
    }

    private static final class BufferOverflowException extends RuntimeException {
        @Serial private static final long serialVersionUID = 1L;

        @Override
        @SuppressWarnings("sync-override")
        public Throwable fillInStackTrace() {
            /* No stacktrace needed. */
            return this;
        }
    }
}
