/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.constantpool;

import java.nio.ByteBuffer;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.Validation;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;

public final class Utf8Constant implements ImmutablePoolConstant {
    private static final short VALID_CLASS_NAME = 0x01;
    private static final short VALID_METHOD_NAME = 0x02;
    private static final short VALID_METHOD_NAME_OR_CLINIT = 0x4;
    private static final short VALID_FIELD_NAME = 0x08;
    private static final short VALID_SIGNATURE = 0x10;
    private static final short VALID_UTF8 = 0x20;
    private static final short VALID_TYPE = 0x40;
    private static final short VALID_INIT_SIGNATURE = 0x80;
    private static final short VALID_TYPE_NO_VOID = 0x100;

    private final Symbol<?> value;
    private short validationCache;

    @Override
    public Tag tag() {
        return Tag.UTF8;
    }

    public Utf8Constant(Symbol<?> value) {
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public <T> Symbol<T> unsafeSymbolValue() {
        return (Symbol<T>) value;
    }

    @Override
    public boolean isSame(ImmutablePoolConstant other, ConstantPool thisPool, ConstantPool otherPool) {
        if (!(other instanceof Utf8Constant otherConstant)) {
            return false;
        }
        return value == otherConstant.value;
    }

    @Override
    public void validate(ConstantPool pool) throws ValidationException {
        validateUTF8();
    }

    public Symbol<? extends ModifiedUTF8> validateUTF8() throws ValidationException {
        if ((validationCache & VALID_UTF8) == 0) {
            if (!Validation.validModifiedUTF8(unsafeSymbolValue())) {
                throw ValidationException.raise("Ill-formed modified-UTF8 entry");
            }
            validationCache |= VALID_UTF8;
        }
        return unsafeSymbolValue();
    }

    public Symbol<Name> validateClassName() throws ValidationException {
        validateUTF8();
        if ((validationCache & VALID_CLASS_NAME) == 0) {
            if (!Validation.validClassNameEntry(value)) {
                throw ValidationException.raise("Invalid class name entry: " + value);
            }
            validationCache |= VALID_CLASS_NAME;
        }
        return unsafeSymbolValue();
    }

    public Symbol<Type> validateType(boolean allowVoid) throws ValidationException {
        validateUTF8();
        short mask = allowVoid ? VALID_TYPE : VALID_TYPE_NO_VOID;
        if ((validationCache & mask) == 0) {
            if (!Validation.validTypeDescriptor(value, allowVoid)) {
                throw ValidationException.raise("Invalid type descriptor: " + value);
            }
            validationCache |= mask;
        }
        return unsafeSymbolValue();
    }

    public Symbol<Name> validateMethodName(boolean allowClinit) throws ValidationException {
        validateUTF8();
        short mask = allowClinit ? VALID_METHOD_NAME_OR_CLINIT : VALID_METHOD_NAME;
        if ((validationCache & mask) == 0) {
            if (!Validation.validMethodName(value, allowClinit)) {
                throw ValidationException.raise("Invalid method name: " + value);
            }
            validationCache |= mask;
        }
        return unsafeSymbolValue();
    }

    public Symbol<Name> validateFieldName() throws ValidationException {
        validateUTF8();
        if ((validationCache & VALID_FIELD_NAME) == 0) {
            if (!Validation.validUnqualifiedName(value)) {
                throw ValidationException.raise("Invalid field name: " + value);
            }
            validationCache |= VALID_FIELD_NAME;
        }
        return unsafeSymbolValue();
    }

    public void validateSignature() throws ValidationException {
        validateSignature(false);
    }

    public Symbol<Signature> validateSignature(boolean isInitOrClinit) throws ValidationException {
        validateUTF8();
        short mask;
        if (isInitOrClinit) {
            mask = VALID_INIT_SIGNATURE;
        } else {
            mask = VALID_SIGNATURE;
        }
        if ((validationCache & mask) == 0) {
            if (!Validation.validSignatureDescriptor(value, isInitOrClinit)) {
                throw ValidationException.raise("Invalid signature descriptor: " + value);
            }
            validationCache |= (short) (mask | VALID_SIGNATURE);
        }
        return unsafeSymbolValue();
    }

    public int validateSignatureGetSlots(boolean isInitOrClinit) throws ValidationException {
        validateUTF8();
        short mask;
        if (isInitOrClinit) {
            mask = VALID_INIT_SIGNATURE;
        } else {
            mask = VALID_SIGNATURE;
        }
        int slots = Validation.validSignatureDescriptorGetSlots(value, isInitOrClinit);
        if (slots < 0) {
            throw ValidationException.raise("Invalid signature descriptor: " + value);
        }
        validationCache |= (short) (mask | VALID_SIGNATURE);
        return slots;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public String toString(ConstantPool pool) {
        return value.toString();
    }

    @Override
    public void dump(ByteBuffer buf) {
        buf.putChar((char) value.length());
        for (int i = 0; i < value.length(); i++) {
            buf.put(value.byteAt(i));
        }
    }
}
