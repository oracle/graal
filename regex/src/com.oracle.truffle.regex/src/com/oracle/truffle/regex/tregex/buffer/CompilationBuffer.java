/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @see RangesArrayBuffer
 */
public class CompilationBuffer {

    private ObjectArrayBuffer objectBuffer1;
    private ObjectArrayBuffer objectBuffer2;
    private ByteArrayBuffer byteArrayBuffer;
    private ShortArrayBuffer shortArrayBuffer;
    private RangesArrayBuffer rangesArrayBuffer1;
    private RangesArrayBuffer rangesArrayBuffer2;
    private RangesArrayBuffer rangesArrayBuffer3;

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

    public RangesArrayBuffer getRangesArrayBuffer1() {
        if (rangesArrayBuffer1 == null) {
            rangesArrayBuffer1 = new RangesArrayBuffer(64);
        }
        rangesArrayBuffer1.clear();
        return rangesArrayBuffer1;
    }

    public RangesArrayBuffer getRangesArrayBuffer2() {
        if (rangesArrayBuffer2 == null) {
            rangesArrayBuffer2 = new RangesArrayBuffer(64);
        }
        rangesArrayBuffer2.clear();
        return rangesArrayBuffer2;
    }

    public RangesArrayBuffer getRangesArrayBuffer3() {
        if (rangesArrayBuffer3 == null) {
            rangesArrayBuffer3 = new RangesArrayBuffer(64);
        }
        rangesArrayBuffer3.clear();
        return rangesArrayBuffer3;
    }
}
