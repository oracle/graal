/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.analysis;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.espresso.analysis.graph.Block;

public final class WorkingQueue<T extends Block> {
    private Element<T> first;

    public static class Element<U> {

        private final U block;
        private Element<U> next;

        public Element(U block, Element<U> next) {
            this.block = block;
            this.next = next;
        }

    }

    public void push(T block) {
        first = new Element<>(block, first);
    }

    public T pop() {
        assert !isEmpty();
        T res = first.block;
        first = first.next;
        return res;
    }

    public T peek() {
        return first.block;
    }

    public boolean isEmpty() {
        return first == null;
    }

    public List<T> findLoop(int entry) {
        List<T> res = new ArrayList<>();
        Element<T> current = first;
        while (current.block.id() != entry) {
            res.add(current.block);
            current = current.next;
            assert current != null;
        }
        res.add(current.block);
        return res;
    }
}
