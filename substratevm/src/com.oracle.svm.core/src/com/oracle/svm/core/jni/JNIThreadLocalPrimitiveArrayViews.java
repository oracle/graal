/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni;

import org.graalvm.word.PointerBase;

import com.oracle.svm.core.handles.PrimitiveArrayView;
import com.oracle.svm.core.jni.headers.JNIMode;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;

/**
 * Manages per-thread lists of referenced objects for safe direct memory access from native code.
 */
public class JNIThreadLocalPrimitiveArrayViews {
    private static class ReferencedObjectListNode {
        final PrimitiveArrayView object;
        ReferencedObjectListNode next;

        ReferencedObjectListNode(PrimitiveArrayView object, ReferencedObjectListNode next) {
            this.object = object;
            this.next = next;
        }
    }

    private static final FastThreadLocalObject<ReferencedObjectListNode> referencedObjectsListHead = FastThreadLocalFactory.createObject(ReferencedObjectListNode.class,
                    "JNIThreadLocalReferencedObjects.referencedObjectsListHead");

    public static PrimitiveArrayView createArrayView(Object array) {
        PrimitiveArrayView ref = PrimitiveArrayView.createForReading(array);
        referencedObjectsListHead.set(new ReferencedObjectListNode(ref, referencedObjectsListHead.get()));
        return ref;
    }

    public static <T extends PointerBase> T createArrayViewAndGetAddress(Object array) {
        return createArrayView(array).addressOfArrayElement(0);
    }

    public static void destroyNewestArrayViewByAddress(PointerBase address, int mode) {
        ReferencedObjectListNode previous = null;
        ReferencedObjectListNode current = referencedObjectsListHead.get();
        while (current != null) {
            if (current.object.addressOfArrayElement(0) == address) {
                if (previous != null) {
                    previous.next = current.next;
                } else {
                    referencedObjectsListHead.set(current.next);
                }

                if (mode == 0 || mode == JNIMode.JNI_COMMIT()) {
                    current.object.syncToHeap();
                }
                if (mode == 0 || mode == JNIMode.JNI_ABORT()) {
                    current.object.close();
                } else {
                    current.object.untrack();
                }
                return;
            }
            previous = current;
            current = current.next;
        }
    }

    static int getCount() {
        int count = 0;
        ReferencedObjectListNode node = referencedObjectsListHead.get();
        while (node != null) {
            count++;
            node = node.next;
        }
        return count;
    }

}
