/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.word;

import org.graalvm.word.impl.Word;
import org.graalvm.word.impl.WordFactoryOpcode;
import org.graalvm.word.impl.WordFactoryOperation;

/**
 * Provides factory method to create machine-word-sized values.
 * <p>
 * In an execution environment where value returned by this factory words are boxed values (e.g. not
 * in Native Image runtime), a {@link Pointer} value will throw {@link UnsatisfiedLinkError} if any
 * of its memory access operations (i.e., read, write, compare-and-swap etc.) or
 * conversion-to-Object operations (i.e., toObject) are invoked.
 * <p>
 * In a Native Image runtime, {@linkplain WordBase word} values are distinct from Object values. To
 * avoid problems related to this, word values must never be used as Objects, even when
 * {@code javac} would allow it (e.g., {@code Map<Long, Pointer>}). The Native Image builder will
 * detect such usages and raise an error.
 *
 * @since 19.0
 */
public final class WordFactory {

    private WordFactory() {
    }

    /**
     * The constant 0, i.e., the word with no bits set. There is no difference between a signed and
     * unsigned zero.
     *
     * @return the constant 0.
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.ZERO)
    public static <T extends WordBase> T zero() {
        return Word.zero();
    }

    /**
     * The null pointer, i.e., a word with all bits set to 0. There is no difference between a
     * signed or unsigned {@link #zero}.
     *
     * @return a word value representing the null pointer
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.ZERO)
    public static <T extends PointerBase> T nullPointer() {
        return Word.nullPointer();
    }

    /**
     * Creates a word whose value is supplied by {@code val} which is (lossily) narrowed on a 32-bit
     * platform.
     *
     * @param val a 64-bit unsigned value
     * @return the value cast to Word
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends UnsignedWord> T unsigned(long val) {
        return Word.unsigned(val);
    }

    /**
     * Unsafe conversion from a Java long value to a {@link PointerBase pointer}. The parameter is
     * treated as an unsigned 64-bit value (in contrast to the semantics of a Java long).
     *
     * @param val a 64 bit unsigned value
     * @return the value cast to PointerBase
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends PointerBase> T pointer(long val) {
        return Word.pointer(val);
    }

    /**
     * Creates a word whose value is supplied by {@code val} which is zero-extended on a 64-bit
     * platform.
     *
     * @param val a 32 bit unsigned value
     * @return the value cast to Word
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends UnsignedWord> T unsigned(int val) {
        return Word.unsigned(val);
    }

    /**
     * Creates a word whose value is supplied by {@code val}.
     *
     * @param val a 64-bit signed value
     * @return the value cast to Word
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_SIGNED)
    public static <T extends SignedWord> T signed(long val) {
        return Word.signed(val);
    }

    /**
     * Creates a word whose value is supplied by a {@code val} which is sign-extended on a 64-bit
     * platform.
     *
     * @param val a 32-bit signed value
     * @return the value cast to Word
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_SIGNED)
    public static <T extends SignedWord> T signed(int val) {
        return Word.signed(val);
    }
}
