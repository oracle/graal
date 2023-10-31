/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.input;

import static com.oracle.truffle.regex.tregex.string.Encodings.Encoding;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateInline
public abstract class InputIndexOfStringNode extends Node {

    public abstract int execute(Node node, TruffleString input, int fromIndex, int maxIndex, TruffleString match, TruffleString.WithMask mask, Encoding encoding);

    @Specialization(guards = "mask == null")
    public int doTString(TruffleString input, int fromIndex, int maxIndex, TruffleString match, @SuppressWarnings("unused") TruffleString.WithMask mask, Encoding encoding,
                    @Cached(inline = false) @Shared TruffleString.ByteIndexOfStringNode indexOfStringNode) {
        int fromByteIndex = fromIndex << encoding.getStride();
        if (fromByteIndex >= input.byteLength(encoding.getTStringEncoding())) {
            return -1;
        }
        return indexOfStringNode.execute(input, match, fromByteIndex, maxIndex << encoding.getStride(), encoding.getTStringEncoding()) >> encoding.getStride();
    }

    @Fallback
    public int doTStringMask(TruffleString input, int fromIndex, int maxIndex, @SuppressWarnings("unused") TruffleString match, TruffleString.WithMask mask, Encoding encoding,
                    @Cached(inline = false) @Shared TruffleString.ByteIndexOfStringNode indexOfStringNode) {
        int fromByteIndex = fromIndex << encoding.getStride();
        if (fromByteIndex >= input.byteLength(encoding.getTStringEncoding())) {
            return -1;
        }
        return indexOfStringNode.execute(input, mask, fromByteIndex, maxIndex << encoding.getStride(), encoding.getTStringEncoding()) >> encoding.getStride();
    }

    @NeverDefault
    public static InputIndexOfStringNode create() {
        return InputIndexOfStringNodeGen.create();
    }
}
