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

public abstract class UnsafeFrameAccess {

    public static UnsafeFrameAccess lookup() {
        return INSTANCE;
    }

    private static final UnsafeFrameAccess INSTANCE = new Impl();

    public abstract short unsafeShortArrayRead(short[] arr, int index);

    public abstract void unsafeShortArrayWrite(short[] arr, int index, short value);

    public abstract byte unsafeByteArrayRead(byte[] arr, int index);

    public abstract void unsafeByteArrayWrite(byte[] arr, int index, byte value);

    public abstract int unsafeIntArrayRead(int[] arr, int index);

    public abstract void unsafeIntArrayWrite(int[] arr, int index, int value);

    public abstract <T> T unsafeObjectArrayRead(T[] arr, int index);

    public abstract <T> T unsafeCast(Object arr, Class<T> clazz);

    public abstract byte unsafeGetTag(Frame frame, int slot);

    public abstract Object unsafeGetObject(Frame frame, int slot);

    public abstract boolean unsafeGetBoolean(Frame frame, int slot);

    public abstract int unsafeGetInt(Frame frame, int slot);

    public abstract long unsafeGetLong(Frame frame, int slot);

    public abstract double unsafeGetDouble(Frame frame, int slot);

    public abstract Object unsafeUncheckedGetObject(Frame frame, int slot);

    public abstract boolean unsafeUncheckedGetBoolean(Frame frame, int slot);

    public abstract int unsafeUncheckedGetInt(Frame frame, int slot);

    public abstract long unsafeUncheckedGetLong(Frame frame, int slot);

    public abstract double unsafeUncheckedGetDouble(Frame frame, int slot);

    public abstract void unsafeSetObject(Frame frame, int slot, Object value);

    public abstract void unsafeSetBoolean(Frame frame, int slot, boolean value);

    public abstract void unsafeSetInt(Frame frame, int slot, int value);

    public abstract void unsafeSetLong(Frame frame, int slot, long value);

    public abstract void unsafeSetDouble(Frame frame, int slot, double value);

    public abstract boolean unsafeIsObject(Frame frame, int slot);

    public abstract boolean unsafeIsBoolean(Frame frame, int slot);

    public abstract boolean unsafeIsInt(Frame frame, int slot);

    public abstract boolean unsafeIsLong(Frame frame, int slot);

    public abstract boolean unsafeIsDouble(Frame frame, int slot);

    public abstract void unsafeCopy(Frame frame, int srcSlot, int dstSlot);

    public abstract void unsafeCopyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length);

    public abstract void unsafeCopyObject(Frame frame, int srcSlot, int dstSlot);

    public abstract void unsafeCopyPrimitive(Frame frame, int srcSlot, int dstSlot);

    UnsafeFrameAccess() {
    }

    private static final class Impl extends UnsafeFrameAccess {

        @Override
        public short unsafeShortArrayRead(short[] arr, int index) {
            return FrameWithoutBoxing.UNSAFE.getShort(arr, Unsafe.ARRAY_SHORT_BASE_OFFSET + index * Unsafe.ARRAY_SHORT_INDEX_SCALE);
        }

        @Override
        public void unsafeShortArrayWrite(short[] arr, int index, short value) {
            FrameWithoutBoxing.UNSAFE.putShort(arr, Unsafe.ARRAY_SHORT_BASE_OFFSET + index * Unsafe.ARRAY_SHORT_INDEX_SCALE, value);
        }

        @Override
        public byte unsafeByteArrayRead(byte[] arr, int index) {
            return FrameWithoutBoxing.UNSAFE.getByte(arr, Unsafe.ARRAY_BYTE_BASE_OFFSET + index * Unsafe.ARRAY_BYTE_INDEX_SCALE);
        }

        @Override
        public void unsafeByteArrayWrite(byte[] arr, int index, byte value) {
            FrameWithoutBoxing.UNSAFE.putByte(arr, Unsafe.ARRAY_BYTE_BASE_OFFSET + index * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
        }

        @Override
        public int unsafeIntArrayRead(int[] arr, int index) {
            return FrameWithoutBoxing.UNSAFE.getInt(arr, Unsafe.ARRAY_INT_BASE_OFFSET + index * Unsafe.ARRAY_INT_INDEX_SCALE);
        }

        @Override
        public void unsafeIntArrayWrite(int[] arr, int index, int value) {
            FrameWithoutBoxing.UNSAFE.putInt(arr, Unsafe.ARRAY_INT_BASE_OFFSET + index * Unsafe.ARRAY_INT_INDEX_SCALE, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unsafeObjectArrayRead(T[] arr, int index) {
            return (T) FrameWithoutBoxing.UNSAFE.getObject(arr, Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unsafeCast(Object obj, Class<T> clazz) {
            // TODO: make this unsafer
            return (T) obj;
        }

        @Override
        public byte unsafeGetTag(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetTag(slot);
        }

        @Override
        public Object unsafeGetObject(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetObject(slot);
        }

        @Override
        public int unsafeGetInt(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetInt(slot);
        }

        @Override
        public boolean unsafeGetBoolean(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetBoolean(slot);
        }

        @Override
        public long unsafeGetLong(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetLong(slot);
        }

        @Override
        public double unsafeGetDouble(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetDouble(slot);
        }

        @Override
        public Object unsafeUncheckedGetObject(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetObject(slot);
        }

        @Override
        public int unsafeUncheckedGetInt(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetInt(slot);
        }

        @Override
        public boolean unsafeUncheckedGetBoolean(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetBoolean(slot);
        }

        @Override
        public long unsafeUncheckedGetLong(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetLong(slot);
        }

        @Override
        public double unsafeUncheckedGetDouble(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetDouble(slot);
        }

        @Override
        public void unsafeSetObject(Frame frame, int slot, Object value) {
            ((FrameWithoutBoxing) frame).unsafeSetObject(slot, value);
        }

        @Override
        public void unsafeSetInt(Frame frame, int slot, int value) {
            ((FrameWithoutBoxing) frame).unsafeSetInt(slot, value);
        }

        @Override
        public void unsafeSetBoolean(Frame frame, int slot, boolean value) {
            ((FrameWithoutBoxing) frame).unsafeSetBoolean(slot, value);
        }

        @Override
        public void unsafeSetLong(Frame frame, int slot, long value) {
            ((FrameWithoutBoxing) frame).unsafeSetLong(slot, value);
        }

        @Override
        public void unsafeSetDouble(Frame frame, int slot, double value) {
            ((FrameWithoutBoxing) frame).unsafeSetDouble(slot, value);
        }

        @Override
        public boolean unsafeIsObject(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeIsObject(slot);
        }

        @Override
        public boolean unsafeIsBoolean(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeIsBoolean(slot);
        }

        @Override
        public boolean unsafeIsInt(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeIsInt(slot);
        }

        @Override
        public boolean unsafeIsLong(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeIsLong(slot);
        }

        @Override
        public boolean unsafeIsDouble(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeIsDouble(slot);
        }

        @Override
        public void unsafeCopy(Frame frame, int srcSlot, int dstSlot) {
            ((FrameWithoutBoxing) frame).unsafeCopy(srcSlot, dstSlot);
        }

        @Override
        public void unsafeCopyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length) {
            ((FrameWithoutBoxing) srcFrame).unsafeCopyTo(srcOffset, ((FrameWithoutBoxing) dstFrame), dstOffset, length);
        }

        @Override
        public void unsafeCopyObject(Frame frame, int srcSlot, int dstSlot) {
            ((FrameWithoutBoxing) frame).unsafeCopyObject(srcSlot, dstSlot);
        }

        @Override
        public void unsafeCopyPrimitive(Frame frame, int srcSlot, int dstSlot) {
            ((FrameWithoutBoxing) frame).unsafeCopyPrimitive(srcSlot, dstSlot);
        }
    }

    private static final class ImplSafe extends UnsafeFrameAccess {

        @Override
        public short unsafeShortArrayRead(short[] arr, int index) {
            return arr[index];
        }

        @Override
        public void unsafeShortArrayWrite(short[] arr, int index, short value) {
            arr[index] = value;
        }

        @Override
        public byte unsafeByteArrayRead(byte[] arr, int index) {
            return arr[index];
        }

        @Override
        public void unsafeByteArrayWrite(byte[] arr, int index, byte value) {
            arr[index] = value;
        }

        @Override
        public int unsafeIntArrayRead(int[] arr, int index) {
            return arr[index];
        }

        @Override
        public void unsafeIntArrayWrite(int[] arr, int index, int value) {
            arr[index] = value;
        }

        @Override
        public <T> T unsafeObjectArrayRead(T[] arr, int index) {
            return arr[index];
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unsafeCast(Object obj, Class<T> clazz) {
            return (T) obj;
        }

        @Override
        public byte unsafeGetTag(Frame frame, int slot) {
            return frame.getTag(slot);
        }

        @Override
        public Object unsafeGetObject(Frame frame, int slot) {
            return frame.getObject(slot);
        }

        @Override
        public boolean unsafeGetBoolean(Frame frame, int slot) {
            return frame.getBoolean(slot);
        }

        @Override
        public int unsafeGetInt(Frame frame, int slot) {
            return frame.getInt(slot);
        }

        @Override
        public long unsafeGetLong(Frame frame, int slot) {
            return frame.getLong(slot);
        }

        @Override
        public double unsafeGetDouble(Frame frame, int slot) {
            return frame.getDouble(slot);
        }

        @Override
        public Object unsafeUncheckedGetObject(Frame frame, int slot) {
            return frame.getObject(slot);
        }

        @Override
        public boolean unsafeUncheckedGetBoolean(Frame frame, int slot) {
            return frame.getBoolean(slot);
        }

        @Override
        public int unsafeUncheckedGetInt(Frame frame, int slot) {
            return frame.getInt(slot);
        }

        @Override
        public long unsafeUncheckedGetLong(Frame frame, int slot) {
            return frame.getLong(slot);
        }

        @Override
        public double unsafeUncheckedGetDouble(Frame frame, int slot) {
            return frame.getDouble(slot);
        }

        @Override
        public void unsafeSetObject(Frame frame, int slot, Object value) {
            frame.setObject(slot, value);
        }

        @Override
        public void unsafeSetBoolean(Frame frame, int slot, boolean value) {
            frame.setBoolean(slot, value);
        }

        @Override
        public void unsafeSetInt(Frame frame, int slot, int value) {
            frame.setInt(slot, value);
        }

        @Override
        public void unsafeSetLong(Frame frame, int slot, long value) {
            frame.setLong(slot, value);
        }

        @Override
        public void unsafeSetDouble(Frame frame, int slot, double value) {
            frame.setDouble(slot, value);
        }

        @Override
        public boolean unsafeIsObject(Frame frame, int slot) {
            return frame.isObject(slot);
        }

        @Override
        public boolean unsafeIsBoolean(Frame frame, int slot) {
            return frame.isBoolean(slot);
        }

        @Override
        public boolean unsafeIsInt(Frame frame, int slot) {
            return frame.isInt(slot);
        }

        @Override
        public boolean unsafeIsLong(Frame frame, int slot) {
            return frame.isLong(slot);
        }

        @Override
        public boolean unsafeIsDouble(Frame frame, int slot) {
            return frame.isDouble(slot);
        }

        @Override
        public void unsafeCopy(Frame frame, int srcSlot, int dstSlot) {
            frame.copy(srcSlot, dstSlot);
        }

        @Override
        public void unsafeCopyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length) {
            srcFrame.copyTo(srcOffset, dstFrame, dstOffset, length);
        }

        @Override
        public void unsafeCopyObject(Frame frame, int srcSlot, int dstSlot) {
            frame.copyObject(srcSlot, dstSlot);
        }

        @Override
        public void unsafeCopyPrimitive(Frame frame, int srcSlot, int dstSlot) {
            frame.copyPrimitive(srcSlot, dstSlot);
        }

    }
}
