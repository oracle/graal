/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.core.log;

import com.oracle.svm.core.annotate.Uninterruptible;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

/**
 * This class is a wrapper for {@link RealLog}, so that it can be called by methods with @{@link Uninterruptible} annotation.
 * If the caller requires no objects should be instantiated, using the static method {@link UninterruptibleLog#nativeLogJJ}
 * which directly calls the standard C function printf.
 */
public class UninterruptibleLog extends RealLog{
    @CFunction(value = "printf", transition = CFunction.Transition.NO_TRANSITION)
    private static native int printf(CCharPointer format);

    @CFunction(value = "printf", transition = CFunction.Transition.NO_TRANSITION)
    private static native int printf(CCharPointer format, long arg0);

    @CFunction(value = "printf", transition = CFunction.Transition.NO_TRANSITION)
    private static native int printf(CCharPointer format, long arg0, long arg1);

    @Uninterruptible(reason = "Log for debugging", calleeMustBe = false)
    public static void nativeLog(String format) {
        CCharPointer buffer = toCString(format);
        printf(buffer);
        UnmanagedMemory.free(buffer);
    }

    @Uninterruptible(reason = "Log for debugging", calleeMustBe = false)
    public static void nativeLogJ(String format, long arg0) {
        CCharPointer buffer = toCString(format);
        printf(buffer, arg0);
        UnmanagedMemory.free(buffer);
    }

    @Uninterruptible(reason = "Log for debugging", calleeMustBe = false)
    public static void nativeLogJJ(String format, long arg0, long arg1) {
        CCharPointer buffer = toCString(format);
        printf(buffer, arg0, arg1);
        UnmanagedMemory.free(buffer);
    }

    private static CCharPointer toCString(String str) {
        int formatSize = str.length();
        UnsignedWord bufferSize = WordFactory.unsigned(formatSize);
        CCharPointer buffer = UnmanagedMemory.malloc(bufferSize);
        for (int i = 0; i < str.length(); i++) {
            buffer.write(i, (byte) str.charAt(i));
        }
        return buffer;
    }

    @Uninterruptible(reason = "",calleeMustBe = false)
    public Log threadName() {
        return string("Thread:").string(getThreadName());
    }

    @Uninterruptible(reason = "",calleeMustBe = false)
    public static String getThreadName(){
        return Thread.currentThread().getName();
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log string(String value) {
        return super.string(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log string(String str, int fill, int align) {
        return super.string(str, fill, align);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log string(char[] value) {
        return super.string(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log string(byte[] value, int offset, int length) {
        return super.string(value, offset, length);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log string(CCharPointer value) {
        return super.string(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log character(char value) {
        return super.character(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log newline() {
        return super.newline();
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log number(long value, int radix, boolean signed) {
        return super.number(value, radix, signed);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log signed(WordBase value) {
        return super.signed(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log signed(int value) {
        return super.signed(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log signed(long value) {
        return super.signed(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log unsigned(WordBase value) {
        return super.unsigned(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log unsigned(WordBase value, int fill, int align) {
        return super.unsigned(value, fill, align);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log unsigned(int value) {
        return super.unsigned(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log unsigned(long value) {
        return super.unsigned(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log unsigned(long value, int fill, int align) {
        return super.unsigned(value, fill, align);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log rational(long numerator, long denominator, long decimals) {
        return super.rational(numerator, denominator, decimals);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log hex(WordBase value) {
        return super.hex(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log hex(int value) {
        return super.hex(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log hex(long value) {
        return super.hex(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log bool(boolean value) {
        return super.bool(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log object(Object value) {
        return super.object(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log spaces(int value) {
        return super.spaces(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log zhex(WordBase value) {
        return super.zhex(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log zhex(long value) {
        return super.zhex(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log zhex(int value) {
        return super.zhex(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log zhex(short value) {
        return super.zhex(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log zhex(byte value) {
        return super.zhex(value);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log hexdump(PointerBase from, int wordSize, int numWords) {
        return super.hexdump(from, wordSize, numWords);
    }

    @Override
    @Uninterruptible(reason = "Called by Uninterruptible", calleeMustBe = false)
    public Log exception(Throwable t, int maxFrames) {
        return super.exception(t, maxFrames);
    }
}
