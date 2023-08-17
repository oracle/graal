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

import java.util.Objects;

import jdk.vm.ci.code.Register;

/**
 * Represent a register or a stack offset.
 */
public final class AssignedLocation {

    private static final int NONE = -1;
    private static final AssignedLocation PLACEHOLDER = new AssignedLocation();

    private static boolean isValidOffset(int i) {
        return i >= 0 || i == NONE;
    }

    private void checkClassInvariant() {
        if (!isValidOffset(this.stackOffset)) {
            throw new IllegalStateException("Stack offset cannot be < 0 (and not NONE).");
        }
        if (assignsToStack() == assignsToRegister()) {
            throw new IllegalStateException("Cannot assign to both register and stack.");
        }
    }

    private final Register register;
    private final int stackOffset;

    private AssignedLocation() {
        this.register = null;
        this.stackOffset = NONE;
    }

    private AssignedLocation(Register register, int stackOffset) {
        this.register = register;
        this.stackOffset = stackOffset;
        checkClassInvariant();
    }

    public static AssignedLocation placeholder() {
        return PLACEHOLDER;
    }

    public static AssignedLocation forRegister(Register register) {
        return new AssignedLocation(register, NONE);
    }

    public static AssignedLocation forStack(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Stack offset must be >= 0");
        }
        return new AssignedLocation(null, offset);
    }

    public boolean isPlaceholder() {
        return !assignsToRegister() && !assignsToStack();
    }

    public boolean assignsToRegister() {
        return register != null;
    }

    public boolean assignsToStack() {
        return stackOffset >= 0;
    }

    public Register register() {
        if (register == null) {
            throw new IllegalStateException("Not a register assignment.");
        }
        return register;
    }

    public int stackOffset() {
        if (stackOffset == NONE) {
            throw new IllegalStateException("Not a stack assignment.");
        }
        return stackOffset;
    }

    @Override
    public String toString() {
        if (assignsToRegister()) {
            return "r-" + register;
        } else {
            assert assignsToStack();
            return "s-" + stackOffset;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AssignedLocation that = (AssignedLocation) o;
        return stackOffset == that.stackOffset && Objects.equals(register, that.register);
    }

    @Override
    public int hashCode() {
        return Objects.hash(register, stackOffset);
    }
}
