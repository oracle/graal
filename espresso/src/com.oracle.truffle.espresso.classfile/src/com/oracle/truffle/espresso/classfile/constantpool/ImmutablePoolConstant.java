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
package com.oracle.truffle.espresso.classfile.constantpool;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;

public interface ImmutablePoolConstant extends PoolConstant {

    /**
     * Checks if this constant is symbolically the same as the {@code other} constant.
     */
    boolean isSame(ImmutablePoolConstant other, ConstantPool thisPool, ConstantPool otherPool);

    /**
     * Throws {@link ValidationException} if the constant is ill-formed (/ex: a StringConstant does
     * not refer to an UTF8Constant).
     * <p>
     * Resolved entries are not validated.
     *
     * @param pool The constant pool in which this constant appears.
     */
    @SuppressWarnings("unused")
    default void validate(ConstantPool pool) throws ValidationException {
        /* nop */
    }

    String toString(ConstantPool pool);
}
