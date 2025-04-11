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

package com.oracle.svm.webimage;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.log.RealLog;

/**
 * An implementation of {@link Log} to be used in a JS runtime.
 *
 * Because we are in a JS runtime, everything is already initialized and this won't run during GC,
 * so we are allowed to allocate objects and do basically everything normal Java code can do (unlike
 * other Log implementations).
 *
 * For now this is just a copy of NoopLog
 *
 * This is a subtype of {@link RealLog} because {@link Log#setLog(RealLog)} requires a RealLog
 * object.
 *
 * TODO support logging stuff
 */
public class WebImageJSLog extends RealLog {

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
    public Log string(char[] value) {
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
    public Log flush() {
        return this;
    }

    @Override
    public Log autoflush(boolean onOrOff) {
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
        return null;
    }

    @Override
    public Log exception(Throwable t, int maxFrames) {
        return this;
    }

    @Override
    public Log redent(boolean addOrRemove) {
        return this;
    }
}
