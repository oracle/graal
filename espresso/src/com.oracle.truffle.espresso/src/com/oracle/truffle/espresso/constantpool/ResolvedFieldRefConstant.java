/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.constantpool;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.classfile.constantpool.FieldRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Resolvable;

import java.util.Objects;

public final class ResolvedFieldRefConstant implements FieldRefConstant, Resolvable.ResolvedConstant {
    private final Field resolved;

    ResolvedFieldRefConstant(Field resolvedField) {
        Objects.requireNonNull(resolvedField);
        this.resolved = resolvedField;
    }

    @Override
    public Symbol<Type> getType(ConstantPool pool) {
        return resolved.getType();
    }

    @Override
    public Field value() {
        return resolved;
    }

    @Override
    public Symbol<Name> getHolderKlassName(ConstantPool pool) {
        throw EspressoError.shouldNotReachHere("Field already resolved");
    }

    @Override
    public Symbol<Name> getName(ConstantPool pool) {
        return resolved.getName();
    }

    @Override
    public Symbol<Type> getDescriptor(ConstantPool pool) {
        return getType(pool);
    }
}
