/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.frame;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/** @since 0.8 or earlier */
public enum FrameSlotKind {
    /** @since 0.8 or earlier */
    Object,
    /** @since 0.8 or earlier */
    Long,
    /** @since 0.8 or earlier */
    Int,
    /** @since 0.8 or earlier */
    Double,
    /** @since 0.8 or earlier */
    Float,
    /** @since 0.8 or earlier */
    Boolean,
    /** @since 0.8 or earlier */
    Byte,
    /** @since 0.8 or earlier */
    Illegal;

    /** @since 0.8 or earlier */
    public final byte tag;

    /** @since 0.8 or earlier */
    FrameSlotKind() {
        this.tag = (byte) ordinal();
    }

    @CompilationFinal(dimensions = 1) private static final FrameSlotKind[] VALUES = values();

    /**
     * Converts from the numeric representation used in the implementation of {@link Frame} to the
     * {@link FrameSlotKind}.
     *
     * @param tag the numeric (byte) representation of the kind
     * @return the FrameSlotKind
     * @since 22.0
     */
    public static FrameSlotKind fromTag(byte tag) {
        return VALUES[tag];
    }
}
