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
import com.oracle.truffle.espresso.impl.MethodInfo;

public interface OperandStack {

    List<FrameSlotKind> KIND_VALUES = Collections.unmodifiableList(Arrays.asList(FrameSlotKind.values()));

    int stackIndex();

    void popVoid(int slots);

    void pushObject(Object value);

    void pushInt(int value);

    void pushLong(long value);

    void pushFloat(float value);

    void pushDouble(double value);

    Object popObject();

    int popInt();

    float popFloat();

    long popLong();

    double popDouble();

    void dup1();

    void swapSingle();

    void dupx1();

    void dupx2();

    void dup2();

    void dup2x1();

    void dup2x2();

    void clear();

    Object peekReceiver(MethodInfo method);
}
