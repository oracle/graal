/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.word.impl.WordBoxFactory;
import org.graalvm.word.impl.WordFactoryOpcode;
import org.graalvm.word.impl.WordFactoryOperation;

/**
 * Provides factory method to create machine-word-sized values.
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
        return WordBoxFactory.box(0L);
    }

    /**
     * The null pointer, i.e., the pointer with no bits set. There is no difference to a signed or
     * unsigned {@link #zero}.
     *
     * @return the null pointer.
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.ZERO)
    public static <T extends PointerBase> T nullPointer() {
        return WordBoxFactory.box(0L);
    }

    /**
     * Unsafe conversion from a Java long value to a Word. The parameter is treated as an unsigned
     * 64-bit value (in contrast to the semantics of a Java long).
     *
     * @param val a 64 bit unsigned value
     * @return the value cast to Word
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends UnsignedWord> T unsigned(long val) {
        return WordBoxFactory.box(val);
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
        return WordBoxFactory.box(val);
    }

    /**
     * Unsafe conversion from a Java int value to a Word. The parameter is treated as an unsigned
     * 32-bit value (in contrast to the semantics of a Java int).
     *
     * @param val a 32 bit unsigned value
     * @return the value cast to Word
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends UnsignedWord> T unsigned(int val) {
        return WordBoxFactory.box(val & 0xffffffffL);
    }

    /**
     * Unsafe conversion from a Java long value to a Word. The parameter is treated as a signed
     * 64-bit value (unchanged semantics of a Java long).
     *
     * @param val a 64 bit signed value
     * @return the value cast to Word
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_SIGNED)
    public static <T extends SignedWord> T signed(long val) {
        return WordBoxFactory.box(val);
    }

    /**
     * Unsafe conversion from a Java int value to a Word. The parameter is treated as a signed
     * 32-bit value (unchanged semantics of a Java int).
     *
     * @param val a 32 bit signed value
     * @return the value cast to Word
     *
     * @since 19.0
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_SIGNED)
    public static <T extends SignedWord> T signed(int val) {
        return WordBoxFactory.box(val);
    }
}
