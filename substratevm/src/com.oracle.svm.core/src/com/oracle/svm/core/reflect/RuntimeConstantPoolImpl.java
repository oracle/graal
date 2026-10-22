/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.reflect;

import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.guest.staging.reflect.RuntimeConstantPool;

/** Core-side checked adapter for the runtime class-file constant pool. */
public final class RuntimeConstantPoolImpl implements RuntimeConstantPool {
    private final ConstantPool constantPool;

    public RuntimeConstantPoolImpl(ConstantPool constantPool) {
        this.constantPool = constantPool;
    }

    private void checkTag(ConstantPool.Tag expected, int index) {
        if (index < 0 || index >= constantPool.length()) {
            throw new IllegalArgumentException("Constant pool index out of bounds");
        }
        if (constantPool.tagAt(index) != expected) {
            throw new IllegalArgumentException("Wrong type at constant pool index");
        }
    }

    @Override
    public String getUTF8At(int index) {
        checkTag(ConstantPool.Tag.UTF8, index);
        return constantPool.utf8At(index).toString();
    }

    @Override
    public int getIntAt(int index) {
        checkTag(ConstantPool.Tag.INTEGER, index);
        return constantPool.intAt(index);
    }

    @Override
    public long getLongAt(int index) {
        checkTag(ConstantPool.Tag.LONG, index);
        return constantPool.longAt(index);
    }

    @Override
    public float getFloatAt(int index) {
        checkTag(ConstantPool.Tag.FLOAT, index);
        return constantPool.floatAt(index);
    }

    @Override
    public double getDoubleAt(int index) {
        checkTag(ConstantPool.Tag.DOUBLE, index);
        return constantPool.doubleAt(index);
    }
}
