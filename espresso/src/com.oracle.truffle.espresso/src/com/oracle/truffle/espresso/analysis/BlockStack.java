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

import java.util.Arrays;

import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;

public final class BlockStack {

    private static final int DEFAULT_SIZE = 4;

    private LinkedBlock[] stack = new LinkedBlock[DEFAULT_SIZE];
    private int pos = 0;

    public void push(LinkedBlock block) {
        if (pos == stack.length) {
            stack = Arrays.copyOf(stack, stack.length << 1);
        }
        stack[pos++] = block;
    }

    public LinkedBlock pop() {
        assert !isEmpty();
        return stack[--pos];
    }

    public LinkedBlock peek() {
        assert !isEmpty();
        return stack[pos - 1];
    }

    public boolean isEmpty() {
        return pos == 0;
    }
}
