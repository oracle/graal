/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.meta.EspressoError;

final class EspressoFrame {

    private EspressoFrame() {
        throw EspressoError.shouldNotReachHere();
    }

    /**
     * Bytecode execution frames are built on
     * {@link com.oracle.truffle.api.frame.FrameDescriptor.Builder#addSlot(FrameSlotKind, Object, Object)
     * indexed frame slots}, and contain one slot for the BCI followed by the locals and the stack
     * ("values").
     */

    static final int BCI_SLOT = 0;
    static final int VALUES_START = 1;

    public static FrameDescriptor createFrameDescriptor(int locals, int stack) {
        int slotCount = locals + stack;
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder(slotCount + VALUES_START);
        int bciSlot = builder.addSlot(FrameSlotKind.Int, null, null); // BCI
        assert bciSlot == BCI_SLOT;
        int valuesStart = builder.addSlots(slotCount, FrameSlotKind.Illegal); // locals + stack
        assert valuesStart == VALUES_START;
        return builder.build();
    }

    public static void dup1(VirtualFrame frame, int top) {
        // value1 -> value1, value1
        frame.copy(top - 1, top);
    }

    public static void dupx1(VirtualFrame frame, int top) {
        // value2, value1 -> value1, value2, value1
        frame.copy(top - 1, top);
        frame.copy(top - 2, top - 1);
        frame.copy(top, top - 2);
    }

    public static void dupx2(VirtualFrame frame, int top) {
        // value3, value2, value1 -> value1, value3, value2, value1
        frame.copy(top - 1, top);
        frame.copy(top - 2, top - 1);
        frame.copy(top - 3, top - 2);
        frame.copy(top, top - 3);
    }

    public static void dup2(VirtualFrame frame, int top) {
        // {value2, value1} -> {value2, value1}, {value2, value1}
        frame.copy(top - 2, top);
        frame.copy(top - 1, top + 1);
    }

    public static void swapSingle(VirtualFrame frame, int top) {
        // value2, value1 -> value1, value2
        frame.swap(top - 1, top - 2);
    }

    public static void dup2x1(VirtualFrame frame, int top) {
        // value3, {value2, value1} -> {value2, value1}, value3, {value2, value1}
        frame.copy(top - 2, top);
        frame.copy(top - 1, top + 1);
        frame.copy(top - 3, top - 1);
        frame.copy(top, top - 3);
        frame.copy(top + 1, top - 2);
    }

    public static void dup2x2(VirtualFrame frame, int top) {
        // {value4, value3}, {value2, value1} -> {value2, value1}, {value4, value3}, {value2,
        // value1}
        frame.copy(top - 1, top + 1);
        frame.copy(top - 2, top);
        frame.copy(top - 3, top - 1);
        frame.copy(top - 4, top - 2);
        frame.copy(top, top - 4);
        frame.copy(top + 1, top - 3);
    }
}
