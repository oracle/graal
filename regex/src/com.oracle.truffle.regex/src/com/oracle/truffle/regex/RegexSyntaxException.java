/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(InteropLibrary.class)
public final class RegexSyntaxException extends AbstractTruffleException {

    public enum ErrorCode {
        InvalidBackReference,
        InvalidCharacterClass,
        InvalidEscape,
        InvalidFlag,
        InvalidGroup,
        InvalidInlineFlag,
        InvalidLookbehind,
        InvalidNamedGroup,
        InvalidOption,
        InvalidQuantifier,
        InvalidSubexpressionCall,
        UnfinishedSequence,
        UnmatchedBracket,
        UnmatchedParenthesis,
        TRegexBailout;

        public int intValue() {
            return -(ordinal() + 3);
        }
    }

    private final SourceSection sourceSection;
    private final ErrorCode errorCode;

    public static RegexSyntaxException createOptions(Source source, String msg, int position) {
        return new RegexSyntaxException(msg, source, position, ErrorCode.InvalidOption);
    }

    public static RegexSyntaxException createPattern(RegexSource source, String msg, int position, ErrorCode errorCode) {
        return new RegexSyntaxException(msg, patternSource(source), position, errorCode);
    }

    public static RegexSyntaxException createFlags(RegexSource source, String msg) {
        return new RegexSyntaxException(msg, flagsSource(source), 0, ErrorCode.InvalidFlag);
    }

    public static RegexSyntaxException createFlags(RegexSource source, String msg, int position) {
        return new RegexSyntaxException(msg, flagsSource(source), position, ErrorCode.InvalidFlag);
    }

    @TruffleBoundary
    private static Source patternSource(RegexSource regexSource) {
        String src = regexSource.getSource().getCharacters().toString();
        int firstPos = src.indexOf('/') + 1;
        int lastPos = src.lastIndexOf('/');
        assert firstPos > 0;
        assert lastPos > firstPos;
        return regexSource.getSource().subSource(firstPos, lastPos - firstPos);
    }

    @TruffleBoundary
    private static Source flagsSource(RegexSource regexSource) {
        String src = regexSource.getSource().getCharacters().toString();
        int lastPos = src.lastIndexOf('/') + 1;
        assert lastPos > 0;
        return regexSource.getSource().subSource(lastPos, src.length() - lastPos);
    }

    @TruffleBoundary
    private RegexSyntaxException(String reason, Source src, int position, ErrorCode errorCode) {
        super(reason);
        assert position <= src.getLength();
        this.sourceSection = src.createSection(position, src.getLength() - position);
        this.errorCode = errorCode;
    }

    @TruffleBoundary
    private RegexSyntaxException(String reason, SourceSection sourceSection, ErrorCode errorCode) {
        super(reason);
        this.sourceSection = sourceSection;
        this.errorCode = errorCode;
    }

    public RegexSyntaxException withErrorCodeInMessage() {
        return new RegexSyntaxException(errorCode.name() + ' ' + getMessage(), sourceSection, errorCode);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    ExceptionType getExceptionType() {
        return ExceptionType.PARSE_ERROR;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasSourceLocation() {
        return true;
    }

    @ExportMessage(name = "getSourceLocation")
    SourceSection getSourceSection() {
        return sourceSection;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    private static final long serialVersionUID = 1L;

}
