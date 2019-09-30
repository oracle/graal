/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.descriptors.Symbol;

public final class Utf8Constant implements PoolConstant {

    private static final int VALID_CLASS_NAME = 0x01;
    private static final int VALID_METHOD_NAME = 0x02;
    private static final int VALID_FIELD_NAME = 0x04;
    private static final int VALID_SIGNATURE = 0x04;
    private static final int VALID_UTF8 = 0x08;
    private static final int VALID_TYPE = 0x1;

    private byte validationCache;

    @Override
    public final Tag tag() {
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
            // TODO(peterssen): Validate well-formed modified UTF8 constant, throws guest ClassFormatError otherwise.

            validationCache |= VALID_UTF8;
        }
    }

    public void validateClassName() {
        validateUTF8();
        if ((validationCache & VALID_CLASS_NAME) == 0) {
            // TODO(peterssen): Validate class name, throws guest ClassFormatError otherwise.

            validationCache |= VALID_CLASS_NAME;
        }
    }

    public void validateType() {
        validateUTF8();
        if ((validationCache & VALID_TYPE) == 0) {
            // TODO(peterssen): Validate type in internal form, throws guest ClassFormatError otherwise.

            validationCache |= VALID_TYPE;
        }
    }

    public void validateMethodName() {
        validateUTF8();
        if ((validationCache & VALID_METHOD_NAME) == 0) {
            // TODO(peterssen): Validate method name, throws guest ClassFormatError otherwise.

            validationCache |= VALID_METHOD_NAME;
        }
    }

    public void validateFieldName() {
        validateUTF8();
        if ((validationCache & VALID_FIELD_NAME) == 0) {
            // TODO(peterssen): Validate field name, throws guest ClassFormatError otherwise.

            validationCache |= VALID_FIELD_NAME;
        }
    }

    public void validateSignature() {
        validateUTF8();
        if ((validationCache & VALID_SIGNATURE) == 0) {
            // TODO(peterssen): Validate signature, throws guest ClassFormatError otherwise.

            validationCache |= VALID_SIGNATURE;
        }
    }

    @Override
    public final String toString() {
        return value.toString();
    }

    @Override
    public String toString(ConstantPool pool) {
        return value.toString();
    }
}
