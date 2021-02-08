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
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Validation;

public final class Utf8Constant implements PoolConstant {

    private static final int VALID_CLASS_NAME = 0x01;
    private static final int VALID_METHOD_NAME = 0x02;
    private static final int VALID_METHOD_NAME_OR_CLINIT = 0x4;
    private static final int VALID_FIELD_NAME = 0x08;
    private static final int VALID_SIGNATURE = 0x10;

    private static final int VALID_UTF8 = 0x20;
    private static final int VALID_TYPE = 0x40;

    private static final int VALID_INIT_SIGNATURE = 0x80;

    private byte validationCache;

    @Override
    public Tag tag() {
        return Tag.UTF8;
    }

    private final Symbol<?> value;

    public Utf8Constant(Symbol<?> value) {
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public <T> Symbol<T> value() {
        // TODO(peterssen): Maybe assert signature/type is valid.
        return (Symbol<T>) value;
    }

    @Override
    public void validate(ConstantPool pool) {
        validateUTF8();
    }

    public void validateUTF8() {
        if ((validationCache & VALID_UTF8) == 0) {
            if (!Validation.validModifiedUTF8(value())) {
                throw ConstantPool.classFormatError("Ill-formed modified-UTF8 entry");
            }
            validationCache |= VALID_UTF8;
        }
    }

    public void validateClassName() {
        validateUTF8();
        if ((validationCache & VALID_CLASS_NAME) == 0) {
            if (!Validation.validClassNameEntry(value)) {
                throw ConstantPool.classFormatError("Invalid class name entry: " + value);
            }
            validationCache |= VALID_CLASS_NAME;
        }
    }

    public void validateType(boolean allowVoid) {
        validateUTF8();
        if ((validationCache & VALID_TYPE) == 0) {
            if (!Validation.validTypeDescriptor(value, allowVoid)) {
                throw ConstantPool.classFormatError("Invalid type descriptor: " + value);
            }
            validationCache |= VALID_TYPE;
        }
    }

    public void validateMethodName(boolean allowClinit) {
        validateUTF8();
        int mask = allowClinit ? VALID_METHOD_NAME_OR_CLINIT : VALID_METHOD_NAME;
        if ((validationCache & mask) == 0) {
            if (!Validation.validMethodName(value, allowClinit)) {
                throw ConstantPool.classFormatError("Invalid method name: " + value);
            }
            validationCache |= mask;
        }
    }

    public void validateFieldName() {
        validateUTF8();
        if ((validationCache & VALID_FIELD_NAME) == 0) {
            if (!Validation.validUnqualifiedName(value)) {
                throw ConstantPool.classFormatError("Invalid field name: " + value);
            }
            validationCache |= VALID_FIELD_NAME;
        }
    }

    public void validateSignature() {
        validateSignature(false);
    }

    public void validateSignature(boolean isInitOrClinit) {
        validateUTF8();
        int mask;
        if (isInitOrClinit) {
            mask = VALID_INIT_SIGNATURE;
        } else {
            mask = VALID_SIGNATURE;
        }
        if ((validationCache & mask) == 0) {
            if (!Validation.validSignatureDescriptor(value, isInitOrClinit)) {
                throw ConstantPool.classFormatError("Invalid signature descriptor: " + value);
            }
            validationCache |= (mask | VALID_SIGNATURE);
        }
    }

    public int validateSignatureGetSlots(boolean isInitOrClinit) {
        validateUTF8();
        int mask;
        if (isInitOrClinit) {
            mask = VALID_INIT_SIGNATURE;
        } else {
            mask = VALID_SIGNATURE;
        }
        int slots = Validation.validSignatureDescriptorGetSlots(value, isInitOrClinit);
        if (slots < 0) {
            throw ConstantPool.classFormatError("Invalid signature descriptor: " + value);
        }
        validationCache |= (mask | VALID_SIGNATURE);
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
        buf.putChar((char) value().length());
        for (int i = 0; i < value().length(); i++) {
            buf.put(value().byteAt(i));
        }
    }
}
