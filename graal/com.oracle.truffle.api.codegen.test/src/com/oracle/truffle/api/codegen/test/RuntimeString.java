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

@NodeClass(RuntimeString.BuiltinNode.class)
public class RuntimeString {

    abstract static class BuiltinNode extends ValueNode {

        @Children ValueNode[] parameters;

        BuiltinNode(ValueNode[] parameters) {
            this.parameters = adoptChildren(parameters);
        }

        BuiltinNode(BuiltinNode prev) {
            this(prev.parameters);
        }
    }

    private final String internal;

    public RuntimeString(String internal) {
        this.internal = internal;
    }

    @Specialization
    RuntimeString substr(int beginIndex, int endIndex) {
        return new RuntimeString(internal.substring(beginIndex, endIndex));
    }

    @Generic
    static RuntimeString substr(Object s, Object beginIndex, Object endIndex) {
        return ((RuntimeString) s).substr(convertInt(beginIndex), convertInt(endIndex));
    }

    @Specialization
    static RuntimeString concat(RuntimeString s1, RuntimeString s2) {
        return new RuntimeString(s1.internal + s2.internal);
    }

    @Generic
    static RuntimeString concat(Object s1, Object s2) {
        return concat(((RuntimeString) s1), (RuntimeString) s2);
    }

    static int convertInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        throw new RuntimeException("Invalid datatype");
    }

}
