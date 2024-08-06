/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graph;

import jdk.graal.compiler.debug.GraalError;

/**
 * A stack implementation that uses a singly linked list as its underlying storage strategy making
 * push/pop operation O(1) at the cost of a constant memory overhead per entry.
 */
public class LinkedStack<T> {

    private static class LinkedListNode<T> {
        LinkedListNode<T> next;
        T data;

        LinkedListNode(T data) {
            this.data = data;
        }
    }

    public LinkedStack() {
        dummyHead = new LinkedListNode<>(null);
    }

    final LinkedListNode<T> dummyHead;

    public void push(T data) {
        LinkedListNode<T> prevHead = dummyHead.next;
        LinkedListNode<T> newHead = new LinkedListNode<>(data);
        newHead.next = prevHead;
        dummyHead.next = newHead;
    }

    public T pop() {
        GraalError.guarantee(!isEmpty(), "Cannot pop on empty stack");
        LinkedListNode<T> prevHead = dummyHead.next;
        dummyHead.next = prevHead.next;
        return prevHead.data;
    }

    public boolean isEmpty() {
        return dummyHead.next == null;
    }

    public T peek() {
        GraalError.guarantee(!isEmpty(), "Cannot peek on empty stack");
        LinkedListNode<T> head = dummyHead.next;
        return head.data;
    }
}
