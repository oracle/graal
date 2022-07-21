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

import java.util.function.Predicate;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;

/**
 * Manages per-thread lists of pinned objects for safe direct memory access from native code.
 */
public class JNIThreadLocalPinnedObjects {
    private static class PinnedObjectListNode {
        final PinnedObject object;
        PinnedObjectListNode next;

        PinnedObjectListNode(PinnedObject object, PinnedObjectListNode next) {
            this.object = object;
            this.next = next;
        }
    }

    private static final FastThreadLocalObject<PinnedObjectListNode> pinnedObjectsListHead = FastThreadLocalFactory.createObject(PinnedObjectListNode.class,
                    "JNIThreadLocalPinnedObjects.pinnedObjectsListHead");

    public static <T extends PointerBase> T pinArrayAndGetAddress(Object array) {
        PinnedObject pin = PinnedObject.create(array);
        pinnedObjectsListHead.set(new PinnedObjectListNode(pin, pinnedObjectsListHead.get()));
        return pin.addressOfArrayElement(0);
    }

    /**
     * Unpins the first object in the pinned objects list matching a predicate.
     * 
     * @param p Predicate determining whether to unpin an object.
     * @return {@code true} if an object was unpinned, {@code false} if no object in the pinned
     *         objects list matched the predicate.
     */
    private static boolean unpinFirst(Predicate<PinnedObjectListNode> p) {
        PinnedObjectListNode previous = null;
        PinnedObjectListNode current = pinnedObjectsListHead.get();
        while (current != null) {
            if (p.test(current)) {
                if (previous != null) {
                    previous.next = current.next;
                } else {
                    pinnedObjectsListHead.set(current.next);
                }
                current.object.close();
                return true;
            }
            previous = current;
            current = current.next;
        }
        return false;
    }

    public static boolean unpinObject(Object object) {
        return unpinFirst(n -> n.object.getObject() == object);
    }

    public static boolean unpinArrayByAddress(PointerBase address) {
        return unpinFirst(n -> LayoutEncoding.isArrayLike(n.object.getObject()) && n.object.addressOfArrayElement(0) == address);
    }

    static int pinnedObjectCount() {
        int count = 0;
        PinnedObjectListNode node = pinnedObjectsListHead.get();
        while (node != null) {
            count++;
            node = node.next;
        }
        return count;
    }

}
