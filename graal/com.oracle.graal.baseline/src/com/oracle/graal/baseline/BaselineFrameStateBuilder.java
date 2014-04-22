/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.baseline;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.java.*;

public class BaselineFrameStateBuilder extends AbstractFrameStateBuilder<Value, BaselineFrameStateBuilder> {

    private static final Value[] EMPTY_ARRAY = new Value[0];

    public BaselineFrameStateBuilder(ResolvedJavaMethod method) {
        // we always need at least one stack slot (for exceptions)
        super(method, new Value[method.getMaxLocals()], new Value[Math.max(1, method.getMaxStackSize())], EMPTY_ARRAY);
    }

    protected BaselineFrameStateBuilder(BaselineFrameStateBuilder other) {
        super(other);
    }

    @Override
    protected Value[] getEmtpyArray() {
        return EMPTY_ARRAY;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[locals: [");
        for (int i = 0; i < locals.length; i++) {
            sb.append(i == 0 ? "" : ",").append(locals[i] == null ? "_" : locals[i].toString());
        }
        sb.append("] stack: [");
        for (int i = 0; i < stackSize; i++) {
            sb.append(i == 0 ? "" : ",").append(stack[i] == null ? "_" : stack[i].toString());
        }
        sb.append("] locks: [");
        for (int i = 0; i < lockedObjects.length; i++) {
            sb.append(i == 0 ? "" : ",").append(lockedObjects[i].toString());
        }
        sb.append("]");
        if (rethrowException) {
            sb.append(" rethrowException");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public BaselineFrameStateBuilder copy() {
        return new BaselineFrameStateBuilder(this);
    }

    private static boolean isCompatible(Value x, Value y) {
        if (x == null && y == null) {
            return true;
        }
        if ((x == null || y == null) || (x.getKind() != y.getKind())) {
            return false;
        }
        return true;

    }

    @Override
    public boolean isCompatibleWith(BaselineFrameStateBuilder other) {
        assert method.equals(other.method) && localsSize() == other.localsSize() : "Can only compare frame states of the same method";

        if (stackSize() != other.stackSize()) {
            return false;
        }
        for (int i = 0; i < stackSize(); i++) {
            if (!isCompatible(stackAt(i), other.stackAt(i))) {
                return false;
            }
        }
        if (lockedObjects.length != other.lockedObjects.length) {
            return false;
        }
        return true;
    }
}
