/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import sun.misc.*;

import com.oracle.truffle.api.unsafe.*;

final class UnsafeAccessFactoryImpl implements UnsafeAccessFactory {
    public UnsafeAccess createUnsafeAccess(final Unsafe unsafe) {
        return new UnsafeAccessImpl(unsafe);
    }

    private static final class UnsafeAccessImpl implements UnsafeAccess {
        private final Unsafe unsafe;

        private UnsafeAccessImpl(Unsafe unsafe) {
            this.unsafe = unsafe;
        }

        @SuppressWarnings("unchecked")
        public <T> T uncheckedCast(Object value, Class<T> type, boolean condition, boolean nonNull) {
            return (T) value;
        }

        public void putShort(Object receiver, long offset, short value, Object locationIdentity) {
            unsafe.putShort(receiver, offset, value);
        }

        public void putObject(Object receiver, long offset, Object value, Object locationIdentity) {
            unsafe.putObject(receiver, offset, value);
        }

        public void putLong(Object receiver, long offset, long value, Object locationIdentity) {
            unsafe.putLong(receiver, offset, value);
        }

        public void putInt(Object receiver, long offset, int value, Object locationIdentity) {
            unsafe.putInt(receiver, offset, value);
        }

        public void putFloat(Object receiver, long offset, float value, Object locationIdentity) {
            unsafe.putFloat(receiver, offset, value);
        }

        public void putDouble(Object receiver, long offset, double value, Object locationIdentity) {
            unsafe.putDouble(receiver, offset, value);
        }

        public void putByte(Object receiver, long offset, byte value, Object locationIdentity) {
            unsafe.putByte(receiver, offset, value);
        }

        public void putBoolean(Object receiver, long offset, boolean value, Object locationIdentity) {
            unsafe.putBoolean(receiver, offset, value);
        }

        public short getShort(Object receiver, long offset, boolean condition, Object locationIdentity) {
            return unsafe.getShort(receiver, offset);
        }

        public Object getObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
            return unsafe.getObject(receiver, offset);
        }

        public long getLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
            return unsafe.getLong(receiver, offset);
        }

        public int getInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
            return unsafe.getInt(receiver, offset);
        }

        public float getFloat(Object receiver, long offset, boolean condition, Object locationIdentity) {
            return unsafe.getFloat(receiver, offset);
        }

        public double getDouble(Object receiver, long offset, boolean condition, Object locationIdentity) {
            return unsafe.getDouble(receiver, offset);
        }

        public byte getByte(Object receiver, long offset, boolean condition, Object locationIdentity) {
            return unsafe.getByte(receiver, offset);
        }

        public boolean getBoolean(Object receiver, long offset, boolean condition, Object locationIdentity) {
            return unsafe.getBoolean(receiver, offset);
        }
    }
}
