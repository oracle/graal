/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.bytecode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.MethodInfo;

public interface FrameOperandStack {

    List<FrameSlotKind> KIND_VALUES = Collections.unmodifiableList(Arrays.asList(FrameSlotKind.values()));

    int stackIndex(final VirtualFrame frame);

    void popVoid(final VirtualFrame frame, int slots);

    void pushObject(final VirtualFrame frame, Object value);

    void pushInt(final VirtualFrame frame, int value);

    void pushLong(final VirtualFrame frame, long value);

    void pushFloat(final VirtualFrame frame, float value);

    void pushDouble(final VirtualFrame frame, double value);

    Object popObject(final VirtualFrame frame);

    int popInt(final VirtualFrame frame);

    float popFloat(final VirtualFrame frame);

    long popLong(final VirtualFrame frame);

    double popDouble(final VirtualFrame frame);

    void dup1(final VirtualFrame frame);

    void swapSingle(final VirtualFrame frame);

    void dupx1(final VirtualFrame frame);

    void dupx2(final VirtualFrame frame);

    void dup2(final VirtualFrame frame);

    void dup2x1(final VirtualFrame frame);

    void dup2x2(final VirtualFrame frame);

    void clear(final VirtualFrame frame);

    Object peekReceiver(final VirtualFrame frame, MethodInfo method);
}
