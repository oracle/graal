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

public class LIRFrameStateBuilder extends AbstractFrameStateBuilder<Value> {

    private final Value[] locals;
    private final Value[] stack;
    private Value[] lockedObjects;

    public LIRFrameStateBuilder(ResolvedJavaMethod method) {
        super(method);

        this.locals = new Value[method.getMaxLocals()];
        // we always need at least one stack slot (for exceptions)
        this.stack = new Value[Math.max(1, method.getMaxStackSize())];
    }

    protected LIRFrameStateBuilder(LIRFrameStateBuilder other) {
        super(other);
        // TODO Auto-generated constructor stub
        locals = other.locals;
        stack = other.stack;
        lockedObjects = other.lockedObjects;
    }

    @Override
    public int localsSize() {
        return locals.length;
    }

    @Override
    public Value localAt(int i) {
        return locals[i];
    }

    @Override
    public Value stackAt(int i) {
        return stack[i];
    }

    @Override
    public Value loadLocal(int i) {
        Value x = locals[i];
        assert !isTwoSlot(x.getKind()) || locals[i + 1] == null;
        assert i == 0 || locals[i - 1] == null || !isTwoSlot(locals[i - 1].getKind());
        return x;
    }

    @Override
    public void storeLocal(int i, Value x) {
        assert x == null || x.getKind() != Kind.Void && x.getKind() != Kind.Illegal : "unexpected value: " + x;
        locals[i] = x;
        if (x != null && isTwoSlot(x.getKind())) {
            // if this is a double word, then kill i+1
            locals[i + 1] = null;
        }
        if (x != null && i > 0) {
            Value p = locals[i - 1];
            if (p != null && isTwoSlot(p.getKind())) {
                // if there was a double word at i - 1, then kill it
                locals[i - 1] = null;
            }
        }
    }

    @Override
    public void storeStack(int i, Value x) {
        assert x == null || (stack[i] == null || x.getKind() == stack[i].getKind()) : "Method does not handle changes from one-slot to two-slot values or non-alive values";
        stack[i] = x;
    }

    @Override
    public void push(Kind kind, Value x) {
        assert x.getKind() != Kind.Void && x.getKind() != Kind.Illegal;
        xpush(assertKind(kind, x));
        if (isTwoSlot(kind)) {
            xpush(null);
        }
    }

    @Override
    public void xpush(Value x) {
        assert x == null || (x.getKind() != Kind.Void && x.getKind() != Kind.Illegal);
        stack[stackSize++] = x;
    }

    @Override
    public void ipush(Value x) {
        xpush(assertInt(x));
    }

    @Override
    public void fpush(Value x) {
        xpush(assertFloat(x));
    }

    @Override
    public void apush(Value x) {
        xpush(assertObject(x));
    }

    @Override
    public void lpush(Value x) {
        xpush(assertLong(x));
    }

    @Override
    public void dpush(Value x) {
        xpush(assertDouble(x));

    }

    @Override
    public void pushReturn(Kind kind, Value x) {
        if (kind != Kind.Void) {
            push(kind.getStackKind(), x);
        }
    }

    @Override
    public Value pop(Kind kind) {
        assert kind != Kind.Void;
        if (isTwoSlot(kind)) {
            xpop();
        }
        return assertKind(kind, xpop());
    }

    @Override
    public Value xpop() {
        Value result = stack[--stackSize];
        return result;
    }

    @Override
    public Value ipop() {
        return assertInt(xpop());
    }

    @Override
    public Value fpop() {
        return assertFloat(xpop());
    }

    @Override
    public Value apop() {
        return assertObject(xpop());
    }

    @Override
    public Value lpop() {
        assertHigh(xpop());
        return assertLong(xpop());
    }

    @Override
    public Value dpop() {
        assertHigh(xpop());
        return assertDouble(xpop());
    }

    @Override
    public Value[] popArguments(int slotSize, int argSize) {
        int base = stackSize - slotSize;
        Value[] r = new Value[argSize];
        int argIndex = 0;
        int stackindex = 0;
        while (stackindex < slotSize) {
            Value element = stack[base + stackindex];
            assert element != null;
            r[argIndex++] = element;
            stackindex += stackSlots(element.getKind());
        }
        stackSize = base;
        return r;
    }

    @Override
    public Value peek(int argumentNumber) {
        int idx = stackSize() - 1;
        for (int i = 0; i < argumentNumber; i++) {
            if (stackAt(idx) == null) {
                idx--;
                assert isTwoSlot(stackAt(idx).getKind());
            }
            idx--;
        }
        return stackAt(idx);
    }

    private static Value assertKind(Kind kind, Value x) {
        assert x != null && x.getKind() == kind : "kind=" + kind + ", value=" + x + ((x == null) ? "" : ", value.kind=" + x.getKind());
        return x;
    }

    private static Value assertLong(Value x) {
        assert x != null && (x.getKind() == Kind.Long);
        return x;
    }

    private static Value assertInt(Value x) {
        assert x != null && (x.getKind() == Kind.Int);
        return x;
    }

    private static Value assertFloat(Value x) {
        assert x != null && (x.getKind() == Kind.Float);
        return x;
    }

    private static Value assertObject(Value x) {
        assert x != null && (x.getKind() == Kind.Object);
        return x;
    }

    private static Value assertDouble(Value x) {
        assert x != null && (x.getKind() == Kind.Double);
        return x;
    }

    private static void assertHigh(Value x) {
        assert x == null;
    }
}
