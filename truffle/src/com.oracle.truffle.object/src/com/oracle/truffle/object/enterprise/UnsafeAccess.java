/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.enterprise;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Legacy class for compatibility with JDK 21 native image builds. Unused.
 */
@SuppressWarnings("unused")
final class UnsafeAccess {

    private UnsafeAccess() {
    }

    static int intCast(boolean value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static boolean booleanCast(int value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @SuppressWarnings("unchecked")
    static <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static void arraycopy(Object from, int fromIndex, Object to, int toIndex, int length) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static boolean unsafeGetBoolean(Object receiver, long offset, boolean condition, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static byte unsafeGetByte(Object receiver, long offset, boolean condition, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static short unsafeGetShort(Object receiver, long offset, boolean condition, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static int unsafeGetInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static long unsafeGetLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static float unsafeGetFloat(Object receiver, long offset, boolean condition, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static double unsafeGetDouble(Object receiver, long offset, boolean condition, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static Object unsafeGetObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static void unsafePutBoolean(Object receiver, long offset, boolean value, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static void unsafePutByte(Object receiver, long offset, byte value, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static void unsafePutShort(Object receiver, long offset, short value, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static void unsafePutInt(Object receiver, long offset, int value, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static void unsafePutLong(Object receiver, long offset, long value, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static void unsafePutFloat(Object receiver, long offset, float value, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static void unsafePutDouble(Object receiver, long offset, double value, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static void unsafePutObject(Object receiver, long offset, Object value, Object locationIdentity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static final Object ANY_LOCATION = new Object();
}
