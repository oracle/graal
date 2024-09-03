/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.tregex.parser.ast.InnerLiteral;
import com.oracle.truffle.regex.tregex.string.Encodings;

public final class InputOps {

    public static int length(TruffleString input, Encodings.Encoding encoding) {
        TruffleString.Encoding tsEncoding = encoding.getTStringEncoding();
        int stride = encoding.getStride();
        CompilerAsserts.partialEvaluationConstant(tsEncoding);
        CompilerAsserts.partialEvaluationConstant(stride);
        return input.byteLength(tsEncoding) >> stride;
    }

    public static int indexOf(TruffleString input, int fromIndex, int maxIndex, TruffleString.CodePointSet codePointSet, Encodings.Encoding encoding,
                    TruffleString.ByteIndexOfCodePointSetNode indexOfNode) {
        int stride = encoding.getStride();
        CompilerAsserts.partialEvaluationConstant(codePointSet);
        CompilerAsserts.partialEvaluationConstant(encoding);
        CompilerAsserts.partialEvaluationConstant(stride);
        return indexOfNode.execute(input, fromIndex << stride, maxIndex << stride, codePointSet, false) >> stride;
    }

    public static int indexOf(TruffleString input, int fromIndex, int maxIndex, InnerLiteral literal, Encodings.Encoding encoding,
                    TruffleString.ByteIndexOfStringNode indexOfStringNode) {
        TruffleString.Encoding tsEncoding = encoding.getTStringEncoding();
        boolean hasMask = literal.hasMask();
        int stride = encoding.getStride();
        CompilerAsserts.partialEvaluationConstant(literal);
        CompilerAsserts.partialEvaluationConstant(hasMask);
        CompilerAsserts.partialEvaluationConstant(tsEncoding);
        CompilerAsserts.partialEvaluationConstant(stride);
        int fromByteIndex = fromIndex << stride;
        int maxByteIndex = maxIndex << stride;
        if (fromByteIndex >= maxByteIndex) {
            return -1;
        }
        if (hasMask) {
            TruffleString.WithMask mask = literal.getMaskContent();
            CompilerAsserts.partialEvaluationConstant(mask);
            return indexOfStringNode.execute(input, mask, fromByteIndex, maxByteIndex, tsEncoding) >> stride;
        } else {
            TruffleString string = literal.getLiteralContent();
            CompilerAsserts.partialEvaluationConstant(string);
            return indexOfStringNode.execute(input, string, fromByteIndex, maxByteIndex, tsEncoding) >> stride;
        }
    }

    public static boolean regionEquals(TruffleString input, InnerLiteral literal, int literalLength, Encodings.Encoding encoding, int fromIndex, int toIndex,
                    TruffleString.RegionEqualByteIndexNode regionEqualsNode) {
        int fromByteIndex = fromIndex << encoding.getStride();
        int byteLength = literalLength << encoding.getStride();
        return toIndex - fromIndex >= literalLength &&
                        regionEqualsInner(input, literal, encoding, fromByteIndex, byteLength, regionEqualsNode);
    }

    private static boolean regionEqualsInner(TruffleString input, InnerLiteral literal, Encodings.Encoding encoding, int fromByteIndexInput, int byteLength,
                    TruffleString.RegionEqualByteIndexNode regionEqualsNode) {
        TruffleString.Encoding tsEncoding = encoding.getTStringEncoding();
        boolean hasMask = literal.hasMask();
        CompilerAsserts.partialEvaluationConstant(literal);
        CompilerAsserts.partialEvaluationConstant(hasMask);
        CompilerAsserts.partialEvaluationConstant(tsEncoding);
        if (hasMask) {
            TruffleString.WithMask mask = literal.getMaskContent();
            CompilerAsserts.partialEvaluationConstant(mask);
            return regionEqualsNode.execute(input, fromByteIndexInput, mask, 0, byteLength, tsEncoding);
        } else {
            TruffleString string = literal.getLiteralContent();
            CompilerAsserts.partialEvaluationConstant(string);
            return regionEqualsNode.execute(input, fromByteIndexInput, string, 0, byteLength, tsEncoding);
        }
    }
}
