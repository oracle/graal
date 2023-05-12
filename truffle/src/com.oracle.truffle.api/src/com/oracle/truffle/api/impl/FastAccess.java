/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.frame.Frame;

import sun.misc.Unsafe;

public abstract class FastAccess {
    public static final FastAccess UNSAFE = new UnsafeImpl();
    public static final FastAccess SAFE = new SafeImpl();

    public abstract short shortArrayRead(short[] arr, int index);

    public abstract void shortArrayWrite(short[] arr, int index, short value);

    public abstract byte byteArrayRead(byte[] arr, int index);

    public abstract void byteArrayWrite(byte[] arr, int index, byte value);

    public abstract int intArrayRead(int[] arr, int index);

    public abstract void intArrayWrite(int[] arr, int index, int value);

    public abstract <T> T objectArrayRead(T[] arr, int index);

    public abstract <T> T cast(Object arr, Class<T> clazz);

    public abstract byte getTag(Frame frame, int slot);

    public abstract Object getObject(Frame frame, int slot);

    public abstract boolean getBoolean(Frame frame, int slot);

    public abstract int getInt(Frame frame, int slot);

    public abstract long getLong(Frame frame, int slot);

    public abstract double getDouble(Frame frame, int slot);

    public abstract Object uncheckedGetObject(Frame frame, int slot);

    public abstract boolean uncheckedGetBoolean(Frame frame, int slot);

    public abstract int uncheckedGetInt(Frame frame, int slot);

    public abstract long uncheckedGetLong(Frame frame, int slot);

    public abstract double uncheckedGetDouble(Frame frame, int slot);

    public abstract void setObject(Frame frame, int slot, Object value);

    public abstract void setBoolean(Frame frame, int slot, boolean value);

    public abstract void setInt(Frame frame, int slot, int value);

    public abstract void setLong(Frame frame, int slot, long value);

    public abstract void setDouble(Frame frame, int slot, double value);

    public abstract boolean isObject(Frame frame, int slot);

    public abstract boolean isBoolean(Frame frame, int slot);

    public abstract boolean isInt(Frame frame, int slot);

    public abstract boolean isLong(Frame frame, int slot);

    public abstract boolean isDouble(Frame frame, int slot);

    public abstract void copy(Frame frame, int srcSlot, int dstSlot);

    public abstract void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length);

    public abstract void copyObject(Frame frame, int srcSlot, int dstSlot);

    public abstract void copyPrimitive(Frame frame, int srcSlot, int dstSlot);

    public abstract void clear(Frame frame, int slot);

    private FastAccess() {
    }

    private static final class UnsafeImpl extends FastAccess {

        @Override
        public short shortArrayRead(short[] arr, int index) {
            return FrameWithoutBoxing.UNSAFE.getShort(arr, Unsafe.ARRAY_SHORT_BASE_OFFSET + index * Unsafe.ARRAY_SHORT_INDEX_SCALE);
        }

        @Override
        public void shortArrayWrite(short[] arr, int index, short value) {
            FrameWithoutBoxing.UNSAFE.putShort(arr, Unsafe.ARRAY_SHORT_BASE_OFFSET + index * Unsafe.ARRAY_SHORT_INDEX_SCALE, value);
        }

        @Override
        public byte byteArrayRead(byte[] arr, int index) {
            return FrameWithoutBoxing.UNSAFE.getByte(arr, Unsafe.ARRAY_BYTE_BASE_OFFSET + index * Unsafe.ARRAY_BYTE_INDEX_SCALE);
        }

        @Override
        public void byteArrayWrite(byte[] arr, int index, byte value) {
            FrameWithoutBoxing.UNSAFE.putByte(arr, Unsafe.ARRAY_BYTE_BASE_OFFSET + index * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
        }

        @Override
        public int intArrayRead(int[] arr, int index) {
            return FrameWithoutBoxing.UNSAFE.getInt(arr, Unsafe.ARRAY_INT_BASE_OFFSET + index * Unsafe.ARRAY_INT_INDEX_SCALE);
        }

        @Override
        public void intArrayWrite(int[] arr, int index, int value) {
            FrameWithoutBoxing.UNSAFE.putInt(arr, Unsafe.ARRAY_INT_BASE_OFFSET + index * Unsafe.ARRAY_INT_INDEX_SCALE, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T objectArrayRead(T[] arr, int index) {
            return (T) FrameWithoutBoxing.UNSAFE.getObject(arr, Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T cast(Object obj, Class<T> clazz) {
            // TODO: make this unsafer
            return (T) obj;
        }

        @Override
        public byte getTag(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetTag(slot);
        }

        @Override
        public Object getObject(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetObject(slot);
        }

        @Override
        public int getInt(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetInt(slot);
        }

        @Override
        public boolean getBoolean(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetBoolean(slot);
        }

        @Override
        public long getLong(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetLong(slot);
        }

        @Override
        public double getDouble(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetDouble(slot);
        }

        @Override
        public Object uncheckedGetObject(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetObject(slot);
        }

        @Override
        public int uncheckedGetInt(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetInt(slot);
        }

        @Override
        public boolean uncheckedGetBoolean(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetBoolean(slot);
        }

        @Override
        public long uncheckedGetLong(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetLong(slot);
        }

        @Override
        public double uncheckedGetDouble(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetDouble(slot);
        }

        @Override
        public void setObject(Frame frame, int slot, Object value) {
            ((FrameWithoutBoxing) frame).unsafeSetObject(slot, value);
        }

        @Override
        public void setInt(Frame frame, int slot, int value) {
            ((FrameWithoutBoxing) frame).unsafeSetInt(slot, value);
        }

        @Override
        public void setBoolean(Frame frame, int slot, boolean value) {
            ((FrameWithoutBoxing) frame).unsafeSetBoolean(slot, value);
        }

        @Override
        public void setLong(Frame frame, int slot, long value) {
            ((FrameWithoutBoxing) frame).unsafeSetLong(slot, value);
        }

        @Override
        public void setDouble(Frame frame, int slot, double value) {
            ((FrameWithoutBoxing) frame).unsafeSetDouble(slot, value);
        }

        @Override
        public boolean isObject(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeIsObject(slot);
        }

        @Override
        public boolean isBoolean(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeIsBoolean(slot);
        }

        @Override
        public boolean isInt(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeIsInt(slot);
        }

        @Override
        public boolean isLong(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeIsLong(slot);
        }

        @Override
        public boolean isDouble(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeIsDouble(slot);
        }

        @Override
        public void copy(Frame frame, int srcSlot, int dstSlot) {
            ((FrameWithoutBoxing) frame).unsafeCopy(srcSlot, dstSlot);
        }

        @Override
        public void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length) {
            ((FrameWithoutBoxing) srcFrame).unsafeCopyTo(srcOffset, ((FrameWithoutBoxing) dstFrame), dstOffset, length);
        }

        @Override
        public void copyObject(Frame frame, int srcSlot, int dstSlot) {
            ((FrameWithoutBoxing) frame).unsafeCopyObject(srcSlot, dstSlot);
        }

        @Override
        public void copyPrimitive(Frame frame, int srcSlot, int dstSlot) {
            ((FrameWithoutBoxing) frame).unsafeCopyPrimitive(srcSlot, dstSlot);
        }

        @Override
        public void clear(Frame frame, int slot) {
            ((FrameWithoutBoxing) frame).unsafeClear(slot);
        }
    }

    private static final class SafeImpl extends FastAccess {

        @Override
        public short shortArrayRead(short[] arr, int index) {
            return arr[index];
        }

        @Override
        public void shortArrayWrite(short[] arr, int index, short value) {
            arr[index] = value;
        }

        @Override
        public byte byteArrayRead(byte[] arr, int index) {
            return arr[index];
        }

        @Override
        public void byteArrayWrite(byte[] arr, int index, byte value) {
            arr[index] = value;
        }

        @Override
        public int intArrayRead(int[] arr, int index) {
            return arr[index];
        }

        @Override
        public void intArrayWrite(int[] arr, int index, int value) {
            arr[index] = value;
        }

        @Override
        public <T> T objectArrayRead(T[] arr, int index) {
            return arr[index];
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T cast(Object obj, Class<T> clazz) {
            return (T) obj;
        }

        @Override
        public byte getTag(Frame frame, int slot) {
            return frame.getTag(slot);
        }

        @Override
        public Object getObject(Frame frame, int slot) {
            return frame.getObject(slot);
        }

        @Override
        public boolean getBoolean(Frame frame, int slot) {
            return frame.getBoolean(slot);
        }

        @Override
        public int getInt(Frame frame, int slot) {
            return frame.getInt(slot);
        }

        @Override
        public long getLong(Frame frame, int slot) {
            return frame.getLong(slot);
        }

        @Override
        public double getDouble(Frame frame, int slot) {
            return frame.getDouble(slot);
        }

        @Override
        public Object uncheckedGetObject(Frame frame, int slot) {
            return frame.getObject(slot);
        }

        @Override
        public boolean uncheckedGetBoolean(Frame frame, int slot) {
            return frame.getBoolean(slot);
        }

        @Override
        public int uncheckedGetInt(Frame frame, int slot) {
            return frame.getInt(slot);
        }

        @Override
        public long uncheckedGetLong(Frame frame, int slot) {
            return frame.getLong(slot);
        }

        @Override
        public double uncheckedGetDouble(Frame frame, int slot) {
            return frame.getDouble(slot);
        }

        @Override
        public void setObject(Frame frame, int slot, Object value) {
            frame.setObject(slot, value);
        }

        @Override
        public void setBoolean(Frame frame, int slot, boolean value) {
            frame.setBoolean(slot, value);
        }

        @Override
        public void setInt(Frame frame, int slot, int value) {
            frame.setInt(slot, value);
        }

        @Override
        public void setLong(Frame frame, int slot, long value) {
            frame.setLong(slot, value);
        }

        @Override
        public void setDouble(Frame frame, int slot, double value) {
            frame.setDouble(slot, value);
        }

        @Override
        public boolean isObject(Frame frame, int slot) {
            return frame.isObject(slot);
        }

        @Override
        public boolean isBoolean(Frame frame, int slot) {
            return frame.isBoolean(slot);
        }

        @Override
        public boolean isInt(Frame frame, int slot) {
            return frame.isInt(slot);
        }

        @Override
        public boolean isLong(Frame frame, int slot) {
            return frame.isLong(slot);
        }

        @Override
        public boolean isDouble(Frame frame, int slot) {
            return frame.isDouble(slot);
        }

        @Override
        public void copy(Frame frame, int srcSlot, int dstSlot) {
            frame.copy(srcSlot, dstSlot);
        }

        @Override
        public void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length) {
            srcFrame.copyTo(srcOffset, dstFrame, dstOffset, length);
        }

        @Override
        public void copyObject(Frame frame, int srcSlot, int dstSlot) {
            frame.copyObject(srcSlot, dstSlot);
        }

        @Override
        public void copyPrimitive(Frame frame, int srcSlot, int dstSlot) {
            frame.copyPrimitive(srcSlot, dstSlot);
        }

        @Override
        public void clear(Frame frame, int slot) {
            frame.clear(slot);
        }

    }
}
