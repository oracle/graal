/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

/**
 * A class that overrides most of the public methods of Log with noop implementations.
 *
 * The usage is somewhere to have a
 *
 * <pre>
 * public static final int verbosity = ....;
 * </pre>
 *
 * and then in methods that want to conditionally log output use
 *
 * <pre>
 * final Log myLog = (verbosity > 17 ? log() : noopLog());
 * myLog.string("Some opening message").newline();
 * ....
 * myLog.string("Some closing message").newline();
 * </pre>
 *
 * and expect the runtime compiler to evaluate the predicate and inline the effectively-empty bodies
 * of the methods from NoopLog into noops. It can do that except if the evaluation of the arguments
 * to the methods have side-effects, including possibly causing exceptions, e.g.,
 * NullPointerException. So be careful with the arguments.
 */
public final class NoopLog implements Log {
    public NoopLog() {
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Log string(String value) {
        return this;
    }

    @Override
    public Log string(String str, int fill, int align) {
        return this;
    }

    @Override
    public Log string(String value, int maxLen) {
        return this;
    }

    @Override
    public Log string(char[] value) {
        return this;
    }

    @Override
    public Log string(byte[] value) {
        return this;
    }

    @Override
    public Log string(byte[] value, int offset, int length) {
        return this;
    }

    @Override
    public Log string(CCharPointer value) {
        return this;
    }

    @Override
    public Log string(CCharPointer bytes, int length) {
        return this;
    }

    @Override
    public Log character(char value) {
        return this;
    }

    @Override
    public Log newline() {
        return this;
    }

    @Override
    public Log number(long value, int radix, boolean signed) {
        return this;
    }

    @Override
    public Log signed(WordBase value) {
        return this;
    }

    @Override
    public Log signed(int value) {
        return this;
    }

    @Override
    public Log signed(long value) {
        return this;
    }

    @Override
    public Log signed(long value, int fill, int align) {
        return this;
    }

    @Override
    public Log unsigned(WordBase value) {
        return this;
    }

    @Override
    public Log unsigned(WordBase value, int fill, int align) {
        return this;
    }

    @Override
    public Log unsigned(int value) {
        return this;
    }

    @Override
    public Log unsigned(long value) {
        return this;
    }

    @Override
    public Log unsigned(long value, int fill, int align) {
        return this;
    }

    @Override
    public Log rational(long numerator, long denominator, long decimals) {
        return this;
    }

    @Override
    public Log rational(UnsignedWord numerator, long denominator, long decimals) {
        return this;
    }

    @Override
    public Log hex(WordBase value) {
        return this;
    }

    @Override
    public Log hex(int value) {
        return this;
    }

    @Override
    public Log hex(long value) {
        return this;
    }

    @Override
    public Log bool(boolean value) {
        return this;
    }

    @Override
    public Log object(Object value) {
        return this;
    }

    @Override
    public Log spaces(int value) {
        return this;
    }

    @Override
    public Log zhex(WordBase value) {
        return this;
    }

    @Override
    public Log zhex(long value) {
        return this;
    }

    @Override
    public Log zhex(int value) {
        return this;
    }

    @Override
    public Log zhex(short value) {
        return this;
    }

    @Override
    public Log zhex(byte value) {
        return this;
    }

    @Override
    public Log hexdump(PointerBase from, int wordSize, int numWords) {
        return this;
    }

    @Override
    public Log hexdump(PointerBase from, int wordSize, int numWords, int bytesPerLine) {
        return this;
    }

    @Override
    public Log exception(Throwable t) {
        return this;
    }

    @Override
    public Log exception(Throwable t, int maxFrames) {
        return this;
    }

    @Override
    public Log redent(boolean addOrRemove) {
        return this;
    }

    @Override
    public Log indent(boolean addOrRemove) {
        return this;
    }

    @Override
    public Log resetIndentation() {
        return this;
    }

    @Override
    public int getIndentation() {
        return 0;
    }

    @Override
    public Log flush() {
        return this;
    }
}
