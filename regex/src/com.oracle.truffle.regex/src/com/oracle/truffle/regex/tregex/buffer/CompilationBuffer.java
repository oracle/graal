/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.buffer;

import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.tregex.TRegexCompiler;

/**
 * This class is instantiated once per compilation of a regular expression in
 * {@link TRegexCompiler#compile(RegexSource)} and is supposed to reduce the amount of allocations
 * during automaton generation. It provides various "scratch-pad" buffers for the creation of arrays
 * of unknown size. When using these buffers, take extra care not to use them in two places
 * simultaneously! {@link TRegexCompiler#compile(RegexSource)} is designed to be run
 * single-threaded, but nested functions may still lead to "simultaneous" use of these buffers.
 *
 * @see ObjectArrayBuffer
 * @see ByteArrayBuffer
 * @see ShortArrayBuffer
 * @see CharRangesBuffer
 */
public class CompilationBuffer {

    private ObjectArrayBuffer objectBuffer1;
    private ObjectArrayBuffer objectBuffer2;
    private ByteArrayBuffer byteArrayBuffer;
    private ShortArrayBuffer shortArrayBuffer;
    private CharRangesBuffer charRangesBuffer1;
    private CharRangesBuffer charRangesBuffer2;
    private CharRangesBuffer charRangesBuffer3;
    private IntRangesBuffer intRangesBuffer1;
    private IntRangesBuffer intRangesBuffer2;
    private IntRangesBuffer intRangesBuffer3;

    public ObjectArrayBuffer getObjectBuffer1() {
        if (objectBuffer1 == null) {
            objectBuffer1 = new ObjectArrayBuffer();
        }
        objectBuffer1.clear();
        return objectBuffer1;
    }

    public ObjectArrayBuffer getObjectBuffer2() {
        if (objectBuffer2 == null) {
            objectBuffer2 = new ObjectArrayBuffer();
        }
        objectBuffer2.clear();
        return objectBuffer2;
    }

    public ByteArrayBuffer getByteArrayBuffer() {
        if (byteArrayBuffer == null) {
            byteArrayBuffer = new ByteArrayBuffer();
        }
        byteArrayBuffer.clear();
        return byteArrayBuffer;
    }

    public ShortArrayBuffer getShortArrayBuffer() {
        if (shortArrayBuffer == null) {
            shortArrayBuffer = new ShortArrayBuffer();
        }
        shortArrayBuffer.clear();
        return shortArrayBuffer;
    }

    public CharRangesBuffer getCharRangesBuffer1() {
        if (charRangesBuffer1 == null) {
            charRangesBuffer1 = new CharRangesBuffer(64);
        }
        charRangesBuffer1.clear();
        return charRangesBuffer1;
    }

    public CharRangesBuffer getCharRangesBuffer2() {
        if (charRangesBuffer2 == null) {
            charRangesBuffer2 = new CharRangesBuffer(64);
        }
        charRangesBuffer2.clear();
        return charRangesBuffer2;
    }

    public CharRangesBuffer getCharRangesBuffer3() {
        if (charRangesBuffer3 == null) {
            charRangesBuffer3 = new CharRangesBuffer(64);
        }
        charRangesBuffer3.clear();
        return charRangesBuffer3;
    }

    public IntRangesBuffer getIntRangesBuffer1() {
        if (intRangesBuffer1 == null) {
            intRangesBuffer1 = new IntRangesBuffer(64);
        }
        intRangesBuffer1.clear();
        return intRangesBuffer1;
    }

    public IntRangesBuffer getIntRangesBuffer2() {
        if (intRangesBuffer2 == null) {
            intRangesBuffer2 = new IntRangesBuffer(64);
        }
        intRangesBuffer2.clear();
        return intRangesBuffer2;
    }

    public IntRangesBuffer getIntRangesBuffer3() {
        if (intRangesBuffer3 == null) {
            intRangesBuffer3 = new IntRangesBuffer(64);
        }
        intRangesBuffer3.clear();
        return intRangesBuffer3;
    }
}
