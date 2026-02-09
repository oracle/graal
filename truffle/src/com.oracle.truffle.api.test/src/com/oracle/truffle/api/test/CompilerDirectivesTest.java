/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import com.oracle.truffle.api.CompilerDirectives.EarlyEscapeAnalysis;
import com.oracle.truffle.api.CompilerDirectives.EarlyInline;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class CompilerDirectivesTest {

    static class EarlyInlineErrors {

        record TestRecord() {

            @EarlyInline
            public void ok() {
            }
        }

        static final class FinalClass {

            @EarlyInline
            public void ok1() {
            }

            @EarlyInline
            protected void ok2() {
            }

            @EarlyInline
            void ok3() {
            }
        }

        interface TestInterface {

            @EarlyInline
            @ExpectError("Methods annotated with @EarlyInline cannot be overridable.%")
            void error1();

            @EarlyInline
            @ExpectError("Methods annotated with @EarlyInline cannot be overridable.%")
            default void error2() {
            }

            @EarlyInline
            static void ok1() {
            }

            @EarlyInline
            private static void ok2() {
            }

        }

        @EarlyInline
        @ExpectError("Methods annotated with @EarlyInline cannot be overridable.%")
        public void error1() {
        }

        @EarlyInline
        @ExpectError("Methods annotated with @EarlyInline cannot be overridable.%")
        protected void error2() {
        }

        @EarlyInline
        @ExpectError("Methods annotated with @EarlyInline cannot be overridable.%")
        void error3() {
        }

        @EarlyInline
        @TruffleBoundary
        @ExpectError("@EarlyInline and @TruffleBoundary are mutually exlusive and cannot be used on the same element.%")
        private void error4() {
        }

        @EarlyInline
        @ExplodeLoop
        @ExpectError("@EarlyInline and @ExplodeLoop are mutually exlusive and cannot be used on the same element.%")
        private void error5() {
        }

        @EarlyEscapeAnalysis
        @EarlyInline
        @ExpectError("@EarlyInline and @EarlyEscapeAnalysis are mutually exlusive and cannot be used on the same element.%")
        private void error6() {
        }

        @EarlyEscapeAnalysis
        @TruffleBoundary
        @ExpectError("@EarlyEscapeAnalysis and @TruffleBoundary are mutually exlusive and cannot be used on the same element.%")
        private void error7() {
        }

        @EarlyInline
        private void ok1() {
        }

        @EarlyInline
        static void ok2() {
        }

        @EarlyInline
        public static void ok3() {
        }

        @EarlyInline
        protected static void ok4() {
        }

        @EarlyInline
        private static void ok5() {
        }

    }

}
