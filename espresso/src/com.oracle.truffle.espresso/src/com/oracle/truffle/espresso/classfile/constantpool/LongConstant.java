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

public final class LongConstant implements PoolConstant {

    public static LongConstant create(long value) {
        // TODO: cache ?
        return new LongConstant(value);
    }

    @Override
    public Tag tag() {
        return Tag.LONG;
    }

    private final long value;

    LongConstant(long value) {
        this.value = value;
    }

    public long value() {
        return value;
    }

    @Override
    public String toString(ConstantPool pool) {
        return String.valueOf(value);
    }

    @Override
    public void dump(ByteBuffer buf) {
        buf.putLong(value());
    }
}
