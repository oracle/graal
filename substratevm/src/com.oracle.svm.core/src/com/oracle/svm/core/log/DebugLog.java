/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;

/**
 * Similar to {@link Log} but can be used from {@link Uninterruptible} code. Unlike {@link Log},
 * there is no output redirection mechanism, so this always logs to {@code stderr}. Therefore, this
 * class should only be used for debugging.
 * <p>
 * Note that this functionality is not necessarily available on all platforms.
 */
public class DebugLog extends AbstractLog {
    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isEnabled() {
        return true;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog string(String value) {
        string0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog string(String str, int fill, int align) {
        string0(str, fill, align);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog string(String value, int maxLen) {
        string0(value, maxLen);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog string(char[] value) {
        string0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog string(byte[] value) {
        string0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog string(byte[] value, int offset, int length) {
        string0(value, offset, length);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog string(CCharPointer value) {
        string0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog string(CCharPointer value, int length) {
        string0(value, length);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog character(char value) {
        character0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog newline() {
        newline0();
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog number(long value, int radix, boolean signed) {
        number0(value, radix, signed);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog signed(WordBase value) {
        signed0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog signed(int value) {
        signed0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog signed(long value) {
        signed0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog signed(long value, int fill, int align) {
        signed0(value, fill, align);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog unsigned(WordBase value) {
        unsigned0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog unsigned(WordBase value, int fill, int align) {
        unsigned0(value, fill, align);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog unsigned(int value) {
        unsigned0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog unsigned(long value) {
        unsigned0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog unsigned(long value, int fill, int align) {
        unsigned0(value, fill, align);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog rational(long numerator, long denominator, long decimals) {
        rational0(numerator, denominator, decimals);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog rational(UnsignedWord numerator, long denominator, long decimals) {
        rational0(numerator, denominator, decimals);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog hex(WordBase value) {
        hex0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog hex(int value) {
        hex0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog hex(long value) {
        hex0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog zhex(WordBase value) {
        zhex0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog zhex(long value) {
        zhex0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog zhex(int value) {
        zhex0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog zhex(short value) {
        zhex0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog zhex(byte value) {
        zhex0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog hexdump(PointerBase from, int wordSize, int numWords) {
        hexdump0(from, wordSize, numWords);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog hexdump(PointerBase from, int wordSize, int numWords, int bytesPerLine) {
        hexdump0(from, wordSize, numWords);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog redent(boolean addOrRemove) {
        redent0(addOrRemove);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog indent(boolean addOrRemove) {
        indent0(addOrRemove);
        return this;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public DebugLog resetIndentation() {
        resetIndentation0();
        return this;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getIndentation() {
        return getIndentation0();
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog bool(boolean value) {
        bool0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog object(Object value) {
        object0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog spaces(int value) {
        spaces0(value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog exception(Throwable t) {
        exception0(t);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog exception(Throwable t, int maxFrames) {
        exception0(t, maxFrames);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    public DebugLog flush() {
        ImageSingletons.lookup(StdErrWriter.class).flush();
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    protected DebugLog rawBytes(CCharPointer bytes, UnsignedWord length) {
        ImageSingletons.lookup(StdErrWriter.class).log(bytes, length);
        return this;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected int printBacktrace(Throwable t, int maxFrames) {
        /*
         * If we ever want to support that, we would need a way to query the necessary information
         * uninterruptibly. This would need a larger refactoring of CodeInfoDecoder and
         * BacktraceDecoder.
         */
        return 0;
    }
}
