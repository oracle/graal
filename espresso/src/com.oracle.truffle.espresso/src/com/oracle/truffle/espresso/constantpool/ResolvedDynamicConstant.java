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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.constantpool.DynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Resolvable;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.classfile.JavaKind;

public interface ResolvedDynamicConstant extends DynamicConstant, Resolvable.ResolvedConstant {
    void putResolved(VirtualFrame frame, int top, BytecodeNode node);

    @Override
    default Symbol<Type> getTypeSymbol(ConstantPool pool) {
        throw EspressoError.shouldNotReachHere("Getting type symbol of a resolved dynamic constant");
    }

    @Override
    default int getBootstrapMethodAttrIndex() {
        throw EspressoError.shouldNotReachHere("Getting bootstrap method index of a resolved dynamic constant");
    }

    @Override
    default NameAndTypeConstant getNameAndType(ConstantPool pool) {
        throw EspressoError.shouldNotReachHere("Getting name and type of a resolved dynamic constant");
    }

    JavaKind getKind();

    default StaticObject guestBoxedValue(Meta meta) {
        Object value = value();
        if (value instanceof StaticObject) {
            return (StaticObject) value;
        }
        return Meta.box(meta, value);
    }

    default void checkFail() {
    }
}
