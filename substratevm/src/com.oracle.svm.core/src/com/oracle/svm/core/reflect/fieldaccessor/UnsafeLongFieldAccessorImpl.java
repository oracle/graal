/*
 * Copyright (c) 2001, 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.reflect.fieldaccessor;

import java.lang.reflect.Field;

class UnsafeLongFieldAccessorImpl extends UnsafeFieldAccessorImpl {
    UnsafeLongFieldAccessorImpl(Field field) {
        super(field);
    }

    @Override
    public Object get(Object obj) throws IllegalArgumentException {
        return Long.valueOf(getLong(obj));
    }

    @Override
    public boolean getBoolean(Object obj) throws IllegalArgumentException {
        throw newGetBooleanIllegalArgumentException();
    }

    @Override
    public byte getByte(Object obj) throws IllegalArgumentException {
        throw newGetByteIllegalArgumentException();
    }

    @Override
    public char getChar(Object obj) throws IllegalArgumentException {
        throw newGetCharIllegalArgumentException();
    }

    @Override
    public short getShort(Object obj) throws IllegalArgumentException {
        throw newGetShortIllegalArgumentException();
    }

    @Override
    public int getInt(Object obj) throws IllegalArgumentException {
        throw newGetIntIllegalArgumentException();
    }

    @Override
    public long getLong(Object obj) throws IllegalArgumentException {
        ensureObj(obj);
        return unsafe.getLong(obj, fieldOffset);
    }

    @Override
    public float getFloat(Object obj) throws IllegalArgumentException {
        return getLong(obj);
    }

    @Override
    public double getDouble(Object obj) throws IllegalArgumentException {
        return getLong(obj);
    }

    @Override
    public void set(Object obj, Object value)
                    throws IllegalArgumentException, IllegalAccessException {
        ensureObj(obj);
        if (isFinal) {
            throwFinalFieldIllegalAccessException(value);
        }
        if (value == null) {
            throwSetIllegalArgumentException(value);
        }
        if (value instanceof Byte) {
            unsafe.putLong(obj, fieldOffset, ((Byte) value).byteValue());
            return;
        }
        if (value instanceof Short) {
            unsafe.putLong(obj, fieldOffset, ((Short) value).shortValue());
            return;
        }
        if (value instanceof Character) {
            unsafe.putLong(obj, fieldOffset, ((Character) value).charValue());
            return;
        }
        if (value instanceof Integer) {
            unsafe.putLong(obj, fieldOffset, ((Integer) value).intValue());
            return;
        }
        if (value instanceof Long) {
            unsafe.putLong(obj, fieldOffset, ((Long) value).longValue());
            return;
        }
        throwSetIllegalArgumentException(value);
    }

    @Override
    public void setBoolean(Object obj, boolean z)
                    throws IllegalArgumentException, IllegalAccessException {
        throwSetIllegalArgumentException(z);
    }

    @Override
    public void setByte(Object obj, byte b)
                    throws IllegalArgumentException, IllegalAccessException {
        setLong(obj, b);
    }

    @Override
    public void setChar(Object obj, char c)
                    throws IllegalArgumentException, IllegalAccessException {
        setLong(obj, c);
    }

    @Override
    public void setShort(Object obj, short s)
                    throws IllegalArgumentException, IllegalAccessException {
        setLong(obj, s);
    }

    @Override
    public void setInt(Object obj, int i)
                    throws IllegalArgumentException, IllegalAccessException {
        setLong(obj, i);
    }

    @Override
    public void setLong(Object obj, long l)
                    throws IllegalArgumentException, IllegalAccessException {
        ensureObj(obj);
        if (isFinal) {
            throwFinalFieldIllegalAccessException(l);
        }
        unsafe.putLong(obj, fieldOffset, l);
    }

    @Override
    public void setFloat(Object obj, float f)
                    throws IllegalArgumentException, IllegalAccessException {
        throwSetIllegalArgumentException(f);
    }

    @Override
    public void setDouble(Object obj, double d)
                    throws IllegalArgumentException, IllegalAccessException {
        throwSetIllegalArgumentException(d);
    }
}
