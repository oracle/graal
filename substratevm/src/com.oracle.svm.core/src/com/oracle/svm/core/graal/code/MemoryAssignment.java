/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code;

// The formatter doesn't enjoy records and then conflicts with checkstyle

// Checkstyle: stop

/**
 * Represent a register or a stack offset.
 *
 * The interpretation of index depends on the kind and architecture, e.g.
 * on AMD64, the designated register is either AMD64.cpuRegisters[index] or AMD64.xmmRegistersAVX512[index].
 */
public record MemoryAssignment(int registerIndex, int stackOffset){//
    private static final int NONE = -1;
    private static boolean check(int i) {
        return i >= 0 || i == NONE;
    }

    public MemoryAssignment {
        if (!check(registerIndex) || !check(stackOffset)) {
            throw new IllegalArgumentException("Register index and stack offset cannot be < 0 (and not NONE).");
        }
        if ((registerIndex == NONE) == (stackOffset == NONE)) {
            throw new IllegalArgumentException("Must assign to either a register or stack (but not both).");
        }
    }

    public static MemoryAssignment toRegister(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Register must be >= 0");
        }
        return new MemoryAssignment(index, NONE);
    }

    public static MemoryAssignment toStack(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Stack offset must be >= 0");
        }
        return new MemoryAssignment(NONE, offset);
    }

    public boolean assignsToRegister() {
        return registerIndex >= 0;
    }

    public boolean assignsToStack() {
        return stackOffset >= 0;
    }

    @Override
    public String toString() {
        if (assignsToRegister()) {
            return "r" + registerIndex;
        }
        else {
            assert assignsToStack();
            return "s" + stackOffset;
        }
    }
}
