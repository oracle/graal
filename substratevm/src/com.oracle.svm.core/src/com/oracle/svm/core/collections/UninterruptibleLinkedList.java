/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.collections;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.VMError;

/** An uninterruptible singly linked list. */
public final class UninterruptibleLinkedList {
    private Element head;
    private Element tail;

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Element getHead() {
        return head;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isEmpty() {
        return head == null;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void append(Element sample) {
        assert sample != null;
        assert sample.getNext() == null;

        if (tail == null || head == null) {
            assert tail == head;
            tail = sample;
            head = sample;
        } else {
            tail.setNext(sample);
            tail = sample;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void remove(Element sample) {
        assert sample != null;

        Element prev = findPrevious(sample);
        if (prev == null) {
            head = sample.getNext();
        } else {
            prev.setNext(sample.getNext());
        }

        if (tail == sample) {
            tail = prev;
        }
        sample.setNext(null);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private Element findPrevious(Element sample) {
        Element prev = null;
        Element cur = head;
        while (cur != null) {
            if (cur == sample) {
                break;
            }
            prev = cur;
            cur = cur.getNext();
        }
        VMError.guarantee(cur != null, "obj must be in the list");
        return prev;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Element pop() {
        Element result = head;
        remove(result);
        return result;
    }

    public interface Element {
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        Element getNext();

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        void setNext(Element element);
    }
}
