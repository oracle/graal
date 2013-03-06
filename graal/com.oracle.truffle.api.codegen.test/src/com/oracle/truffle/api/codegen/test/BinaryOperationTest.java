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

public class BinaryOperationTest {

    static int convertInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        throw new RuntimeException("Invalid datatype");
    }

    @NodeClass(BinaryNode.class)
    abstract static class BinaryNode extends ValueNode {

        @Child protected ValueNode leftNode;
        @Child protected ValueNode rightNode;

        public BinaryNode(ValueNode left, ValueNode right) {
            this.leftNode = left;
            this.rightNode = right;
        }

        public BinaryNode(BinaryNode prev) {
            this(prev.leftNode, prev.rightNode);
        }

        @Specialization
        int add(int left, int right) {
            return left + right;
        }

        @Generic
        int add(Object left, Object right) {
            return convertInt(left) + convertInt(right);
        }

        @Specialization
        int sub(int left, int right) {
            return left + right;
        }

        @Generic
        int sub(Object left, Object right) {
            return convertInt(left) + convertInt(right);
        }

    }

}
