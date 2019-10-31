/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
