/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.interpreter;

import java.util.*;

import com.oracle.graal.api.meta.*;

public class InterpreterFrame extends Frame {

    public static final int BASE_LENGTH = 3;

    private static final int METHOD_FRAME_SLOT = 1;
    private static final int BCI_FRAME_SLOT = 2;

    private static final int DOUBLE = 2;
    private static final int SINGLE = 1;

    /** Pointer to the top-most stack frame element. */
    private int tos;

    public InterpreterFrame(ResolvedJavaMethod method, int additionalStackSpace) {
        this(method, null, additionalStackSpace);
    }

    private InterpreterFrame(ResolvedJavaMethod method, InterpreterFrame parent, int additionalStackSpace) {
        super(method.maxLocals() + method.maxStackSize() + BASE_LENGTH + additionalStackSpace, parent);
        setMethod(method);
        setBCI(0);
        this.tos = BASE_LENGTH;
    }

    public InterpreterFrame create(ResolvedJavaMethod method, boolean hasReceiver) {
        InterpreterFrame frame = new InterpreterFrame(method, this, 0);
        int length = method.signature().argumentSlots(hasReceiver);

        frame.pushVoid(method.maxLocals());
        if (length > 0) {
            copyArguments(frame, length);
            popVoid(length);
        }

        return frame;
    }

    public int resolveLocalIndex(int index) {
        return BASE_LENGTH + index;
    }

    private int stackTos() {
        return BASE_LENGTH + getMethod().maxLocals();
    }

    private void copyArguments(InterpreterFrame dest, int length) {
        System.arraycopy(locals, tosSingle(length - 1), dest.locals,
                        BASE_LENGTH, length);
        System.arraycopy(primitiveLocals, tosSingle(length - 1), dest.primitiveLocals,
                        BASE_LENGTH, length);
    }


    public Object peekReceiver(ResolvedJavaMethod method) {
        return getObject(tosSingle(method.signature().argumentSlots(false)));
    }

    public void pushBoth(Object oValue, int intValue) {
        incrementTos(SINGLE);
        setObject(tosSingle(0), oValue);
        setInt(tosSingle(0), intValue);
    }

    public void pushBoth(Object oValue, long longValue) {
        incrementTos(SINGLE);
        setObject(tosSingle(0), oValue);
        setLong(tosSingle(0), longValue);
    }

    public void pushObject(Object value) {
        incrementTos(SINGLE);
        setObject(tosSingle(0), value);
    }

    public void pushInt(int value) {
        incrementTos(SINGLE);
        setInt(tosSingle(0), value);
    }

    public void pushDouble(double value) {
        incrementTos(DOUBLE);
        setDouble(tosDouble(0), value);
    }

    public void pushFloat(float value) {
        incrementTos(SINGLE);
        setFloat(tosSingle(0), value);
    }

    public void pushLong(long value) {
        incrementTos(DOUBLE);
        setLong(tosDouble(0), value);
    }

    public int popInt() {
        int value = getInt(tosSingle(0));
        decrementTos(SINGLE);
        return value;
    }

    public double popDouble() {
        double value = getDouble(tosDouble(0));
        decrementTos(DOUBLE);
        return value;
    }

    public float popFloat() {
        float value = getFloat(tosSingle(0));
        decrementTos(SINGLE);
        return value;
    }

    public long popLong() {
        long value = getLong(tosDouble(0));
        decrementTos(DOUBLE);
        return value;
    }

    public Object popObject() {
        Object value = getObject(tosSingle(0));
        decrementTos(SINGLE);
        return value;
    }

    public void swapSingle() {
        int tmpInt = getInt(tosSingle(1));
        Object tmpObject = getObject(tosSingle(1));

        setInt(tosSingle(1), getInt(tosSingle(0)));
        setObject(tosSingle(1), getObject(tosSingle(0)));

        setInt(tosSingle(0), tmpInt);
        setObject(tosSingle(0), tmpObject);
    }

    public void dupx1() {
        long tosLong = getLong(tosSingle(0));
        Object tosObject = getObject(tosSingle(0));

        swapSingle();

        pushBoth(tosObject, tosLong);
    }

    public void dup2x1() {
        long tosLong2 = getLong(tosSingle(2));
        Object tosObject2 = getObject(tosSingle(2));
        long tosLong1 = getLong(tosSingle(1));
        Object tosObject1 = getObject(tosSingle(1));
        long tosLong0 = getLong(tosSingle(0));
        Object tosObject0 = getObject(tosSingle(0));

        popVoid(3);

        pushBoth(tosObject1, tosLong1);
        pushBoth(tosObject0, tosLong0);

        pushBoth(tosObject2, tosLong2);

        pushBoth(tosObject1, tosLong1);
        pushBoth(tosObject0, tosLong0);
    }

    public void dup2x2() {
        long tosLong3 = getLong(tosSingle(3));
        Object tosObject3 = getObject(tosSingle(3));
        long tosLong2 = getLong(tosSingle(2));
        Object tosObject2 = getObject(tosSingle(2));
        long tosLong1 = getLong(tosSingle(1));
        Object tosObject1 = getObject(tosSingle(1));
        long tosLong0 = getLong(tosSingle(0));
        Object tosObject0 = getObject(tosSingle(0));

        popVoid(4);

        pushBoth(tosObject1, tosLong1);
        pushBoth(tosObject0, tosLong0);

        pushBoth(tosObject3, tosLong3);
        pushBoth(tosObject2, tosLong2);

        pushBoth(tosObject1, tosLong1);
        pushBoth(tosObject0, tosLong0);
    }

    public void dupx2() {
        long tosLong2 = getLong(tosSingle(2));
        Object tosObject2 = getObject(tosSingle(2));
        long tosLong1 = getLong(tosSingle(1));
        Object tosObject1 = getObject(tosSingle(1));
        long tosLong0 = getLong(tosSingle(0));
        Object tosObject0 = getObject(tosSingle(0));

        popVoid(3);

        pushBoth(tosObject0, tosLong0);
        pushBoth(tosObject2, tosLong2);
        pushBoth(tosObject1, tosLong1);
        pushBoth(tosObject0, tosLong0);
    }

    public void dup(int length) {
        assert length > 0;
        for (int i = 0; i < length; i++) {
            long valueN1 = getLong(tosSingle(length - i - 1));
            Object valueO1 = getObject(tosSingle(length - i - 1));

            pushVoid(1);

            setLong(tosSingle(0), valueN1);
            setObject(tosSingle(0), valueO1);
        }
    }

    private void incrementTos(int size) {
        tos += size;
    }

    private void decrementTos(int size) {
        tos -= size;
    }

    private int tosDouble(int offset) {
        assert offset >= 0;
        return tos - DOUBLE - (offset * DOUBLE);
    }

    private int tosSingle(int offset) {
        assert offset >= 0;
        return tos - SINGLE - offset;
    }

    public int getStackTop() {
        return tos;
    }

    public void pushVoid(int count) {
        incrementTos(count * SINGLE);
    }

    public void popVoid(int count) {
        decrementTos(count * SINGLE);
    }

    public ConstantPool getConstantPool() {
        return getMethod().getConstantPool();
    }

    private void setMethod(ResolvedJavaMethod method) {
        setObject(METHOD_FRAME_SLOT, method);
    }

    public ResolvedJavaMethod getMethod() {
        return (ResolvedJavaMethod) getObject(METHOD_FRAME_SLOT);
    }

    public void setBCI(int bci) {
        setInt(BCI_FRAME_SLOT, bci);
    }

    public int getBCI() {
        return getInt(BCI_FRAME_SLOT);
    }

    public void pushTo(InterpreterFrame childFrame, int argumentSlots) {
        System.arraycopy(locals, tos - argumentSlots, childFrame.locals,
                        InterpreterFrame.MIN_FRAME_SIZE, argumentSlots);

        System.arraycopy(primitiveLocals, tos - argumentSlots, childFrame.primitiveLocals,
                        InterpreterFrame.MIN_FRAME_SIZE, argumentSlots);
        popVoid(argumentSlots);
    }

    public InterpreterFrame getParentFrame() {
        return (InterpreterFrame) getObject(PARENT_FRAME_SLOT);
    }

    public void dispose() {
        // Clear out references in locals array.
        Arrays.fill(locals, null);
    }

    @Override
    public String toString() {
        ResolvedJavaMethod method = getMethod();
        StringBuilder b = new StringBuilder(getMethod().toStackTraceElement(getBCI()).toString());
        for (int i = 0; i < tos; i++) {
            Object object = getObject(tosSingle(i));
            long primitive = getLong(tosSingle(i));

            String objectString = null;
            if (object != null) {
                objectString = object.getClass().getSimpleName() + "@" + Integer.toHexString(object.hashCode());
            }
            String primitiveString = "0x" + Long.toHexString(primitive).toUpperCase();
            String typeString;

            int index = tosSingle(i);
            if (index == METHOD_FRAME_SLOT) {
                typeString = "method";
            } else if (index == BCI_FRAME_SLOT) {
                typeString = "bci";
            } else if (index == PARENT_FRAME_SLOT) {
                typeString = "parent";
            } else if (index < BASE_LENGTH + method.maxLocals()) {
                typeString = "var " + (index - BASE_LENGTH);
            } else {
                typeString = "local";
            }
            b.append(String.format("%n [%d] %7s Primitive: %10s Object: %s", index, typeString, primitiveString, objectString));
        }
        if (getParentFrame() != null) {
            b.append("\n").append(getParentFrame().toString());
        }
        return b.toString();
    }

    public void popStack() {
        // TODO(chumer): prevent popping local variables.
        popVoid(tos - stackTos());
    }

}
