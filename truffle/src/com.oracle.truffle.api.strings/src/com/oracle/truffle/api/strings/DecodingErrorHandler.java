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
package com.oracle.truffle.api.strings;

/**
 * An error handler for handling byte sequences that cannot be decoded in operations such as
 * {@link TruffleStringIterator.NextNode}.
 */
@FunctionalInterface
interface DecodingErrorHandler {

    /**
     * The default decoding error handler. In UTF encodings, it decodes invalid sequences to
     * {@code 0xfffd}, in all other encodings it uses {@code '?'} instead. Exceptions: in UTF-16 and
     * UTF-32 it keeps {@link Character#isSurrogate(char) UTF-16 surrogate values}.
     */
    DecodingErrorHandler DEFAULT = new Encodings.BuiltinDecodingErrorHandler();
    /**
     * Like {@link #DEFAULT}, but views incomplete UTF-8 sequences as single erroneous codepoints.
     */
    DecodingErrorHandler DEFAULT_UTF8_INCOMPLETE_SEQUENCES = new Encodings.BuiltinDecodingErrorHandler();
    /**
     * Like {@link #DEFAULT_UTF8_INCOMPLETE_SEQUENCES}, but also keeps
     * {@link Character#isSurrogate(char) UTF-16 surrogate values} encoded in UTF-8 strings.
     */
    DecodingErrorHandler DEFAULT_KEEP_SURROGATES_IN_UTF8 = new Encodings.BuiltinDecodingErrorHandler();
    /**
     * Returns {@code -1} when an invalid sequence is encountered.
     */
    DecodingErrorHandler RETURN_NEGATIVE = new Encodings.BuiltinDecodingErrorHandler();
    /**
     * Like {@link #DEFAULT}, but views incomplete UTF-8 sequences as single erroneous codepoints.
     */
    DecodingErrorHandler RETURN_NEGATIVE_UTF8_INCOMPLETE_SEQUENCES = new Encodings.BuiltinDecodingErrorHandler();

    /**
     * Error handler implementation. This method is called once for every byte position in the
     * string that could not be decoded.
     *
     * @param string the string currently being decoded.
     * @param bytePosition the starting byte index of the sequence that could not be decoded.
     * @param estimatedByteLength estimated byte length of erroneous region.
     * @return a codepoint to use instead of the next {@code n} bytes, starting at
     *         {@code bytePosition}.
     */
    Result apply(AbstractTruffleString string, int bytePosition, int estimatedByteLength);

    /**
     * Result type for {@link #apply(AbstractTruffleString, int, int)}.
     *
     * @param codepoint a {@link TruffleString} the current {@code nBytes} bytes with.
     * @param byteLength the number of bytes to skip, starting from the current byte position. Must
     *            be greater than zero.
     */
    record Result(int codepoint, int byteLength) {
        // suppress Redundant 'public' modifier warning
        // Checkstyle: stop
        public Result(int codepoint, int byteLength) {
            // Checkstyle: resume
            this.codepoint = codepoint;
            this.byteLength = byteLength;
            if (byteLength <= 0) {
                throw InternalErrors.illegalState("byteLength must be greater than zero");
            }
        }
    }
}
