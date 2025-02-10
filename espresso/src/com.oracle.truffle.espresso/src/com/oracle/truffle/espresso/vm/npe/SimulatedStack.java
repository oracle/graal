/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vm.npe;

final class SimulatedStack {

    final StackObject[] stack;
    int top = 0;

    /**
     * Optimized bytecode can reuse local variable slots for several local variables. If there is no
     * variable name information, we print 'parameter<i>' if a parameter maps to a local slot. Once
     * a local slot has been written, we don't know any more whether it was written as the
     * corresponding parameter, or whether another local has been mapped to the slot. So we don't
     * want to print 'parameter<i>' any more, but 'local<i>'. Similarly for 'this'. Therefore,
     * during the analysis, we mark a bit for local slots that get written and propagate this
     * information. We only run the analysis for 64 slots. If a method has more parameters, we print
     * 'local<i>' in all cases
     */
    long writtenLocalSlots = 0L;

    SimulatedStack(int maxStack) {
        this.stack = new StackObject[maxStack];
    }

    SimulatedStack(SimulatedStack copy) {
        this(copy, copy.size());
    }

    SimulatedStack(SimulatedStack copy, int requestedLength) {
        assert requestedLength >= copy.size();
        StackObject[] newStack = new StackObject[requestedLength];
        System.arraycopy(copy.stack, 0, newStack, 0, copy.size());
        this.stack = newStack;
        this.top = copy.top;
        this.writtenLocalSlots = copy.writtenLocalSlots;
    }

    static SimulatedStack merge(SimulatedStack s1, SimulatedStack s2) {
        if (s1 == null) {
            return new SimulatedStack(s2);
        }
        if (s2 == null) {
            return new SimulatedStack(s1);
        }
        SimulatedStack merge = new SimulatedStack(s1.size());
        // Verifier guarantees same stack size.
        assert s1.size() == s2.size() : "Verifier guarantee failed for stack sizes: " + s1.size() + " != " + s2.size();
        for (int i = 0; i < s1.size(); i++) {
            merge.put(i, StackObject.merge(s1.get(i), s2.get(i)));
        }
        merge.top = s1.size();
        merge.writtenLocalSlots = s1.writtenLocalSlots | s2.writtenLocalSlots;
        return merge;
    }

    SimulatedStack push(int bci, StackType type) {
        if (type == StackType.VOID) {
            return this;
        }
        return push(StackObject.create(bci, type));
    }

    // Push object to the stack
    SimulatedStack pushRaw(StackObject obj) {
        stack[top++] = obj;
        return this;
    }

    // Push object to the stack, push it again if it is a long or double.
    private SimulatedStack push(StackObject obj) {
        pushRaw(obj);
        if (obj.type().hasTwoSlots()) {
            pushRaw(obj);
        }
        return this;
    }

    StackObject pop() {
        return stack[--top];
    }

    void pop(int n) {
        for (int i = 0; i < n; i++) {
            pop();
        }
    }

    int size() {
        return top;
    }

    void setLocalSlotWritten(int at) {
        if (at < 64) {
            writtenLocalSlots = writtenLocalSlots | (1L << at);
        }
    }

    boolean isLocalSlotWritten(int at) {
        if (at < 64) {
            return (writtenLocalSlots & (1L << at)) != 0;
        }
        return true;
    }

    StackObject top(int at) {
        return stack[top - 1 - at];
    }

    private StackObject get(int at) {
        return stack[at];
    }

    private void put(int at, StackObject obj) {
        stack[at] = obj;
    }
}
