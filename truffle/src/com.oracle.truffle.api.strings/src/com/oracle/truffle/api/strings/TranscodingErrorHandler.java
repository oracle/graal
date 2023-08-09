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
 * An error handler for transcoding operations such as {@link TruffleString.SwitchEncodingNode}.
 *
 * @since 23.1
 */
@FunctionalInterface
public interface TranscodingErrorHandler {

    /**
     * The default transcoding error handler. In UTF encodings, it replaces invalid codepoints with
     * {@code 0xfffd}, in all other encodings it uses {@code '?'} instead. Exceptions: in UTF-16 and
     * UTF-32, it keeps {@link Character#isSurrogate(char) UTF-16 surrogate values}.
     * 
     * @since 23.1
     */
    TranscodingErrorHandler DEFAULT = new Encodings.BuiltinTranscodingErrorHandler();

    /**
     * Same as {@link #DEFAULT}, but also keeps {@link Character#isSurrogate(char) UTF-16 surrogate
     * values} in UTF-8.
     *
     * @since 23.1
     */
    TranscodingErrorHandler DEFAULT_KEEP_SURROGATES_IN_UTF8 = new Encodings.BuiltinTranscodingErrorHandler();

    /**
     * Transcoding error handler implementation. This method is called once for every byte region
     * that could not be transcoded from {@code sourceEncoding} to {@code targetEncoding}.
     *
     * @param sourceString the string currently being transcoded.
     * @param byteIndex starting index of region that could not be transcoded.
     * @param estimatedByteLength estimated byte length of erroneous region.
     * @param sourceEncoding the source string's encoding.
     * @param targetEncoding the target encoding.
     * @return a string to use instead of the invalid region.
     * @since 23.1
     */
    ReplacementString apply(AbstractTruffleString sourceString, int byteIndex, int estimatedByteLength, TruffleString.Encoding sourceEncoding, TruffleString.Encoding targetEncoding);

    /**
     * Result type of
     * {@link #apply(AbstractTruffleString, int, int, TruffleString.Encoding, TruffleString.Encoding)}.
     * 
     * @param replacement the string to use as replacement for the erroneous region in the source
     *            string.
     * @param byteLength the byte length of the region to replace. If this value is negative, let
     *            the transcoder determine the region length instead.
     * 
     * @since 23.1
     */
    record ReplacementString(TruffleString replacement, int byteLength) {
    }
}
