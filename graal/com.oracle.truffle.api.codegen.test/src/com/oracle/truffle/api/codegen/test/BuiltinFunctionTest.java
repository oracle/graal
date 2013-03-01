/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.codegen.test;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Generated code at {@link BuiltinFunctionTestFactory}.
 */
public class BuiltinFunctionTest {

    @TypeSystemReference(SimpleTypes.class)
    abstract static class ValueNode extends Node {

        abstract int executeInt() throws UnexpectedResultException;

        abstract String executeString() throws UnexpectedResultException;

        abstract Object execute();

    }

    abstract static class FunctionNode extends ValueNode {

        @Children ValueNode[] parameters;

        FunctionNode(ValueNode[] parameters) {
            this.parameters = adoptChildren(parameters);
        }

        FunctionNode(FunctionNode prev) {
            this(prev.parameters);
        }
    }

    static int convertInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        throw new RuntimeException("Invalid datatype");
    }

    abstract static class MathAbsNode extends FunctionNode {

        MathAbsNode(ValueNode[] children) {
            super(children);
        }

        MathAbsNode(MathAbsNode prev) {
            super(prev);
        }

        @Specialization
        int doInt(int value1) {
            return Math.abs(value1);
        }

        @Generic
        int doGeneric(Object value0) {
            return doInt(convertInt(value0));
        }
    }

    abstract static class StringThisNode extends ValueNode {

        @Override
        final String executeString() {
            return (String) execute();
        }

    }

    @ExecuteChildren({"thisNode", "parameters"})
    abstract static class InstanceFunctionNode extends FunctionNode {

        @Child StringThisNode thisNode;

        InstanceFunctionNode(StringThisNode thisNode, ValueNode[] parameters) {
            super(parameters);
            this.thisNode = thisNode;
        }

        InstanceFunctionNode(InstanceFunctionNode prev) {
            this(prev.thisNode, prev.parameters);
        }
    }

    abstract static class StringSubstrNode extends InstanceFunctionNode {

        StringSubstrNode(StringThisNode thisNode, ValueNode[] parameters) {
            super(thisNode, parameters);
        }

        StringSubstrNode(StringSubstrNode prev) {
            super(prev);
        }

        @Specialization
        String doInt(String thisValue, int beginIndex, int endIndex) {
            return thisValue.substring(beginIndex, endIndex);
        }

        @Generic
        String doGeneric(String thisValue, Object beginIndex, Object endIndex) {
            return thisValue.substring(convertInt(beginIndex), convertInt(endIndex));
        }
    }

}
