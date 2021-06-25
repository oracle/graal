/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.predefined.wasi;

public final class FlagUtils {

    public static short set(short value, Enum<?> e) {
        assert e.getClass().getEnumConstants().length < Short.SIZE;
        return (short) set((long) value, e);
    }

    public static long set(long value, Enum<?> e) {
        return value | (1L << e.ordinal());
    }

    public static boolean isSet(short value, Enum<?> e) {
        return isSet((long) value, e);
    }

    public static boolean isSet(long value, Enum<?> e) {
        return (value & (1L << e.ordinal())) != 0;
    }

    public static short remove(short value, Enum<?> e) {
        assert e.getClass().getEnumConstants().length < Short.SIZE;
        return (short) remove((long) value, e);
    }

    public static long remove(long value, Enum<?> e) {
        return value & ~(1L << e.ordinal());
    }

    public static boolean isSubsetOf(long a, long b) {
        return (a & b) == a;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E extends Enum<E>> short flagsShort(Enum<E>... flagsSet) {
        assert flagsSet.length == 0 || flagsSet[0].getClass().getEnumConstants().length < Short.SIZE;
        return (short) flags(flagsSet);
    }

    @SafeVarargs
    public static <E extends Enum<E>> long flags(Enum<E>... flagsSet) {
        long result = 0;
        for (final Enum<?> flag : flagsSet) {
            result = set(result, flag);
        }
        return result;
    }

    public static <E extends Enum<E>> long allFlagsSet(Class<E> flagsEnum) {
        return (1L << flagsEnum.getEnumConstants().length) - 1;
    }

}
