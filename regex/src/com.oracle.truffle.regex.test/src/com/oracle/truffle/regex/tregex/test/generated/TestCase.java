/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.test.generated;

import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.tregex.string.Encodings;

public record TestCase(String pattern, String flags, Encodings.Encoding encoding, SyntaxErrorOrInputs syntaxErrorOrInputs) {

    public abstract static sealed class SyntaxErrorOrInputs permits SyntaxError, Inputs {
    }

    public abstract static sealed class SyntaxError extends SyntaxErrorOrInputs permits SyntaxErrorCode, SyntaxErrorMessage {
    }

    public static final class SyntaxErrorCode extends SyntaxError {
        public final RegexSyntaxException.ErrorCode errorCode;

        public SyntaxErrorCode(RegexSyntaxException.ErrorCode errorCode) {
            this.errorCode = errorCode;
        }
    }

    public static final class SyntaxErrorMessage extends SyntaxError {
        public final String message;
        public final int position;

        public SyntaxErrorMessage(String message, int position) {
            this.message = message;
            this.position = position;
        }
    }

    public static final class Inputs extends SyntaxErrorOrInputs {
        public final Input[] inputs;

        public Inputs(Input[] inputs) {
            this.inputs = inputs;
        }
    }

    public record Input(String input, int fromIndex, int[] captureGroupBoundsAndLastGroup) {
    }

    public static TestCase testCase(String pattern, String flags, Encodings.Encoding encoding, SyntaxError syntaxError) {
        return new TestCase(pattern, flags, encoding, syntaxError);
    }

    public static TestCase testCase(String pattern, String flags, Encodings.Encoding encoding, Input... inputs) {
        return new TestCase(pattern, flags, encoding, new Inputs(inputs));
    }

    public static Input noMatch(String input, int fromIndex) {
        return new Input(input, fromIndex, null);
    }

    public static Input match(String input, int fromIndex, int... captureGroupBoundsAndLastGroup) {
        return new Input(input, fromIndex, captureGroupBoundsAndLastGroup);
    }

    public static SyntaxErrorCode syntaxError(RegexSyntaxException.ErrorCode errorCode) {
        return new SyntaxErrorCode(errorCode);
    }

    public static SyntaxErrorMessage syntaxError(String message) {
        return new SyntaxErrorMessage(message, -1);
    }

    public static SyntaxErrorMessage syntaxError(String message, int position) {
        return new SyntaxErrorMessage(message, position);
    }
}
