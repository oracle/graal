/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.attributes;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

/**
 * The ConstantValue attribute is a fixed-length attribute in the attributes table of a field_info
 * structure (&sect;4.5). A ConstantValue attribute represents the value of a constant expression
 * (JLS &sect;15.28), and is used as follows:
 * <ul>
 * <li>If the ACC_STATIC flag in the access_flags item of the field_info structure is set, then the
 * field represented by the field_info structure is assigned the value represented by its
 * ConstantValue attribute as part of the initialization of the class or interface declaring the
 * field (&sect;5.5). This occurs prior to the invocation of the class or interface initialization
 * method of that class or interface (&sect;2.9).
 * <li>Otherwise, the Java Virtual Machine must silently ignore the attribute.
 * </ul>
 * There may be at most one ConstantValue attribute in the attributes table of a field_info
 * structure.
 */
public final class ConstantValueAttribute extends Attribute {
    public static final Symbol<Name> NAME = Name.ConstantValue;
    private final int constantValueIndex;

    public ConstantValueAttribute(int constantValueIndex) {
        super(NAME, null);
        this.constantValueIndex = constantValueIndex;
    }

    public int getConstantValueIndex() {
        return constantValueIndex;
    }

    @Override
    public boolean sameAs(Attribute other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        if (!super.sameAs(other)) {
            return false;
        }
        ConstantValueAttribute that = (ConstantValueAttribute) other;
        return constantValueIndex == that.constantValueIndex;
    }
}
