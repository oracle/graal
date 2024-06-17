/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test.parser;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.Source;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ErrorParseSignatureTest extends ParseSignatureTest {

    protected static void tryParseSignature(String signature) {
        Source source = Source.newBuilder("nfi", signature, "signature").build();
        runWithPolyglot.getTruffleTestEnv().parseInternal(source);
    }

    @Rule public ExpectedException exception = ExpectedException.none();

    static class ParserExceptionMatcher extends TypeSafeDiagnosingMatcher<AbstractTruffleException> {

        private final ExceptionType expectedExceptionType;
        private final boolean expectedIncomplete;

        static final ParserExceptionMatcher PARSER = new ParserExceptionMatcher(ExceptionType.PARSE_ERROR, false);
        static final ParserExceptionMatcher INCOMPLETE = new ParserExceptionMatcher(ExceptionType.PARSE_ERROR, true);

        ParserExceptionMatcher(ExceptionType expectedExceptionType, boolean expectedIncomplete) {
            super(AbstractTruffleException.class);
            this.expectedExceptionType = expectedExceptionType;
            this.expectedIncomplete = expectedIncomplete;
        }

        @Override
        protected boolean matchesSafely(AbstractTruffleException item, Description mismatchDescription) {
            try {
                ExceptionType actualExceptionType = InteropLibrary.getUncached().getExceptionType(item);
                boolean actualIncomplete = InteropLibrary.getUncached().isExceptionIncompleteSource(item);
                if (actualExceptionType == expectedExceptionType && actualIncomplete == expectedIncomplete) {
                    return true;
                } else {
                    mismatchDescription.appendValue(actualExceptionType);
                    if (actualIncomplete) {
                        mismatchDescription.appendText(" (incomplete)");
                    }
                    return false;
                }
            } catch (UnsupportedMessageException ex) {
                throw new AssertionError(ex);
            }
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(expectedExceptionType);
            if (expectedIncomplete) {
                description.appendText(" (incomplete)");
            }
        }
    }

    @Test
    public void parseEmpty() {
        exception.expect(ParserExceptionMatcher.INCOMPLETE);
        tryParseSignature("");
    }

    @Test
    public void parseUnknownToken() {
        exception.expect(ParserExceptionMatcher.PARSER);
        tryParseSignature("..");
    }

    @Test
    public void parseMissingParen() {
        exception.expect(ParserExceptionMatcher.PARSER);
        tryParseSignature("(sint32 : void");
    }

    @Test
    public void parseMissingComma() {
        exception.expect(ParserExceptionMatcher.PARSER);
        tryParseSignature("(sint32 float) : void");
    }

    @Test
    public void parseMissingColon() {
        exception.expect(ParserExceptionMatcher.PARSER);
        tryParseSignature("(sint32) void");
    }

    @Test
    public void parseMissingRetType() {
        exception.expect(ParserExceptionMatcher.INCOMPLETE);
        tryParseSignature("() : ");
    }

    @Test
    public void parseMissingVararg() {
        exception.expect(ParserExceptionMatcher.PARSER);
        tryParseSignature("(float, ...) : void");
    }

    @Test
    public void parseMissingVararg2() {
        exception.expect(ParserExceptionMatcher.PARSER);
        tryParseSignature("(...) : void");
    }
}
