/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;

/**
 * A singly linked list view of the queue. An item's previous is the item that was added to the
 * queue after the item itself. The list is iterated in FIFO order, starting with the element added
 * first and following previous links to find elements added after. The traversal can also be used
 * to discover entries that need removing, e.g. if references have been garbage collected. All of
 * its methods are marked as uninterruptible because they're accessed from the old object profiler
 * sampling and emitting methods, which are protected by a lock.
 */
public final class OldObjectList {
    /*
     * Points to the oldest entry added to the list. This would be the first FIFO iterated element.
     * Only gets updated when an entry is removed.
     */
    private OldObject head;

    /*
     * Points to the youngest entry added to the list. This would be last FIFO iterated element.
     * Prepending merely updates this pointer.
     */
    private OldObject tail;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    OldObject head() {
        return head;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void prepend(OldObject sample) {
        assert sample.previous == null;
        if (tail == null || head == null) {
            tail = sample;
            head = sample;
            return;
        }

        tail.previous = sample;
        tail = sample;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void remove(OldObject sample) {
        if (head == sample) {
            head = sample.previous;
            if (tail == sample) {
                // Removing last remaining item.
                tail = null;
            }
            return;
        }

        // Else, find an element whose previous is item; iow, find item's next element.
        OldObject next = findNext(sample);

        assert next != null;

        // Then set that next's previous to item's previous
        next.previous = sample.previous;
        sample.previous = null;

        // If the element removed is tail, update it to item's next.
        if (tail == sample) {
            tail = next;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private OldObject findNext(OldObject target) {
        OldObject current = head;
        while (current != null) {
            if (current.previous == target) {
                return current;
            }

            current = current.previous;
        }

        return null;
    }
}
