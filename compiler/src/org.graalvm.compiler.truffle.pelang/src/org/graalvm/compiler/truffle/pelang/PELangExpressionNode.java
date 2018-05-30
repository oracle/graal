/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;

public abstract class PELangExpressionNode extends PELangStatementNode {

    @Override
    public void executeVoid(VirtualFrame frame) {
        executeGeneric(frame);
    }

    public abstract Object executeGeneric(VirtualFrame frame);

    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        return expectLong(executeGeneric(frame));
    }

    public long[] executeLongArray(VirtualFrame frame) throws UnexpectedResultException {
        return expectLongArray(executeGeneric(frame));
    }

    public String executeString(VirtualFrame frame) throws UnexpectedResultException {
        return expectString(executeGeneric(frame));
    }

    public String[] executeStringArray(VirtualFrame frame) throws UnexpectedResultException {
        return expectStringArray(executeGeneric(frame));
    }

    public PELangFunction executeFunction(VirtualFrame frame) throws UnexpectedResultException {
        return expectFunction(executeGeneric(frame));
    }

    public DynamicObject executeObject(VirtualFrame frame) throws UnexpectedResultException {
        return expectObject(executeGeneric(frame));
    }

    public Object executeArray(VirtualFrame frame) throws UnexpectedResultException {
        return expectArray(executeGeneric(frame));
    }

    public long evaluateLong(VirtualFrame frame) {
        try {
            return executeLong(frame);
        } catch (UnexpectedResultException ex) {
            throw new PELangException("expected value of type long", this);
        }
    }

    public long[] evaluateLongArray(VirtualFrame frame) {
        try {
            return executeLongArray(frame);
        } catch (UnexpectedResultException ex) {
            throw new PELangException("expected value of type long[]", this);
        }
    }

    public String evaluateString(VirtualFrame frame) {
        try {
            return executeString(frame);
        } catch (UnexpectedResultException ex) {
            throw new PELangException("expected value of type String", this);
        }
    }

    public String[] evaluateStringArray(VirtualFrame frame) {
        try {
            return executeStringArray(frame);
        } catch (UnexpectedResultException ex) {
            throw new PELangException("expected value of type String[]", this);
        }
    }

    public PELangFunction evaluateFunction(VirtualFrame frame) {
        try {
            return executeFunction(frame);
        } catch (UnexpectedResultException ex) {
            throw new PELangException("expected value of type PELangFunction", this);
        }
    }

    public DynamicObject evaluateObject(VirtualFrame frame) {
        try {
            return executeObject(frame);
        } catch (UnexpectedResultException ex) {
            throw new PELangException("expected value of type DynamicObject", this);
        }
    }

    public Object evaluateArray(VirtualFrame frame) {
        try {
            return executeArray(frame);
        } catch (UnexpectedResultException ex) {
            throw new PELangException("expected value to be an array", this);
        }
    }

    private static long expectLong(Object value) throws UnexpectedResultException {
        if (value instanceof Long) {
            return (long) value;
        }
        throw new UnexpectedResultException(value);
    }

    private static long[] expectLongArray(Object value) throws UnexpectedResultException {
        if (value instanceof long[]) {
            return (long[]) value;
        }
        throw new UnexpectedResultException(value);
    }

    private static String expectString(Object value) throws UnexpectedResultException {
        if (value instanceof String) {
            return (String) value;
        }
        throw new UnexpectedResultException(value);
    }

    private static String[] expectStringArray(Object value) throws UnexpectedResultException {
        if (value instanceof String[]) {
            return (String[]) value;
        }
        throw new UnexpectedResultException(value);
    }

    private static PELangFunction expectFunction(Object value) throws UnexpectedResultException {
        if (value instanceof PELangFunction) {
            return (PELangFunction) value;
        }
        throw new UnexpectedResultException(value);
    }

    private static DynamicObject expectObject(Object value) throws UnexpectedResultException {
        if (value instanceof DynamicObject) {
            return (DynamicObject) value;
        }
        throw new UnexpectedResultException(value);
    }

    private static Object expectArray(Object value) throws UnexpectedResultException {
        if (value.getClass().isArray()) {
            return value;
        }
        throw new UnexpectedResultException(value);
    }

}
