/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.verifier;

import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Bogus;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Double;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Float;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_InitObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Integer;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Long;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_NewObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Null;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Object;

import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;

public abstract class VerificationTypeInfo {

    VerificationTypeInfo() {
    }

    public abstract int getTag();

    public int getNewOffset() {
        throw EspressoError.shouldNotReachHere("Asking for new offset of non Uninitialized variable verification_type_info");
    }

    public int getConstantPoolOffset() {
        throw EspressoError.shouldNotReachHere("Asking for CPI of non reference verification_type_info");
    }

    public String toString(Klass klass) {
        // Note: JSR/RET is mutually exclusive with stack maps.
        switch (getTag()) {
            case ITEM_Bogus:
                return "invalid";
            case ITEM_Integer:
                return "int";
            case ITEM_Float:
                return "float";
            case ITEM_Double:
                return "double";
            case ITEM_Long:
                return "long";
            case ITEM_Null:
                return "null";
            default:
                return fromCP(klass);
        }
    }

    public abstract Symbol<Type> getType(ConstantPool pool, ObjectKlass thisKlass, BytecodeStream bs);

    public boolean isNull() {
        return false;
    }

    public boolean isIllegal() {
        return false;
    }

    @SuppressWarnings("unused")
    protected String fromCP(Klass klass) {
        return "";
    }
}

final class PrimitiveTypeInfo extends VerificationTypeInfo {
    private static final PrimitiveTypeInfo Bogus = new PrimitiveTypeInfo(ITEM_Bogus);
    private static final PrimitiveTypeInfo Integer = new PrimitiveTypeInfo(ITEM_Integer);
    private static final PrimitiveTypeInfo Float = new PrimitiveTypeInfo(ITEM_Float);
    private static final PrimitiveTypeInfo Double = new PrimitiveTypeInfo(ITEM_Double);
    private static final PrimitiveTypeInfo Long = new PrimitiveTypeInfo(ITEM_Long);
    private static final PrimitiveTypeInfo Null = new PrimitiveTypeInfo(ITEM_Null);

    private final int tag;

    private PrimitiveTypeInfo(int tag) {
        this.tag = tag;
    }

    @Override
    public int getTag() {
        return tag;
    }

    static VerificationTypeInfo get(int tag) {
        switch (tag) {
            case ITEM_Bogus:
                return Bogus;
            case ITEM_Integer:
                return Integer;
            case ITEM_Float:
                return Float;
            case ITEM_Double:
                return Double;
            case ITEM_Long:
                return Long;
            case ITEM_Null:
                return Null;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    @Override
    public Symbol<Type> getType(ConstantPool pool, ObjectKlass thisKlass, BytecodeStream bs) {
        switch (tag) {
            case ITEM_Integer:
                return Type._int;
            case ITEM_Float:
                return Type._float;
            case ITEM_Double:
                return Type._double;
            case ITEM_Long:
                return Type._long;
            case ITEM_Null: // fall through
            case ITEM_Bogus: // fall through
            default:
                throw EspressoError.shouldNotReachHere();
        }

    }

    @Override
    public boolean isNull() {
        return tag == ITEM_Null;
    }

    @Override
    public boolean isIllegal() {
        return tag == ITEM_Bogus;
    }
}

final class UninitializedThis extends VerificationTypeInfo {
    private static final UninitializedThis UNINITIALIZED_THIS = new UninitializedThis();

    private UninitializedThis() {
    }

    @Override
    public int getTag() {
        return ITEM_InitObject;
    }

    static VerificationTypeInfo get() {
        return UNINITIALIZED_THIS;
    }

    @Override
    protected String fromCP(Klass klass) {
        return "newThis";
    }

    @Override
    public Symbol<Type> getType(ConstantPool pool, ObjectKlass thisKlass, BytecodeStream bs) {
        return thisKlass.getType();
    }
}

final class UninitializedVariable extends VerificationTypeInfo {
    private final int newOffset;

    UninitializedVariable(int newOffset) {
        this.newOffset = newOffset;
    }

    @Override
    public int getTag() {
        return ITEM_NewObject;
    }

    @Override
    public int getNewOffset() {
        return newOffset;
    }

    @Override
    protected String fromCP(Klass klass) {
        return "new " + klass.getConstantPool().classAt(newOffset).getName(klass.getConstantPool());
    }

    @Override
    public Symbol<Type> getType(ConstantPool pool, ObjectKlass thisKlass, BytecodeStream bs) {
        Types types = thisKlass.getMeta().getTypes();
        return types.fromName(pool.classAt(bs.readCPI(getNewOffset())).getName(pool));
    }
}

final class ReferenceVariable extends VerificationTypeInfo {

    private final int constantPoolOffset;

    ReferenceVariable(int constantPoolOffset) {
        this.constantPoolOffset = constantPoolOffset;
    }

    @Override
    public int getTag() {
        return ITEM_Object;
    }

    @Override
    public int getConstantPoolOffset() {
        return constantPoolOffset;
    }

    @Override
    protected String fromCP(Klass klass) {
        return "" + klass.getConstantPool().classAt(constantPoolOffset).getName(klass.getConstantPool());
    }

    @Override
    public Symbol<Type> getType(ConstantPool pool, ObjectKlass thisKlass, BytecodeStream bs) {
        Types types = thisKlass.getMeta().getTypes();
        return types.fromName(pool.classAt(getConstantPoolOffset()).getName(pool));
    }
}
