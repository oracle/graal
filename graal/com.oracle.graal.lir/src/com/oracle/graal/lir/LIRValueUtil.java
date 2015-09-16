/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir;

import jdk.internal.jvmci.meta.Constant;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.Value;

public final class LIRValueUtil {

    public static boolean isVariable(Value value) {
        assert value != null;
        return value instanceof Variable;
    }

    public static Variable asVariable(Value value) {
        assert value != null;
        return (Variable) value;
    }

    public static boolean isConstantValue(Value value) {
        assert value != null;
        return value instanceof ConstantValue;
    }

    public static ConstantValue asConstantValue(Value value) {
        assert value != null;
        return (ConstantValue) value;
    }

    public static Constant asConstant(Value value) {
        return asConstantValue(value).getConstant();
    }

    public static boolean isJavaConstant(Value value) {
        return isConstantValue(value) && asConstantValue(value).isJavaConstant();
    }

    public static JavaConstant asJavaConstant(Value value) {
        return asConstantValue(value).getJavaConstant();
    }
}
