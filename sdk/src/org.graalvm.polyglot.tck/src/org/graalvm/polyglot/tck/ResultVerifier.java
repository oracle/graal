/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.tck;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * Allows a custom verification of a result of a snippet execution.
 *
 * @since 0.30
 */
public interface ResultVerifier extends Consumer<ResultVerifier.SnippetRun> {

    /**
     * Performs a verification of a result of a snippet execution. The custom {@link ResultVerifier}
     * overrides the default snippet execution verification. The default verification tests that the
     * result type is in bounds specified by the {@link Snippet}. The custom {@link ResultVerifier}
     * can be used to do additional checking of a result or an execution exception throwing
     * {@link AssertionError} in case of failed assertion. The custom {@link ResultVerifier} can
     * hide an expected execution exception. For example the division operator snippet may provide a
     * custom {@link ResultVerifier} hiding the execution exception for division by zero. To
     * propagate the execution exception the {@link ResultVerifier} should re-throw it. The
     * {@link ResultVerifier} can be used to resolve problems in the {@code ErrorTypeTest} by making
     * the {@link Snippet}'s parameter(s) more generic and restrict the required value in the
     * verifier.
     *
     * @param snippetRun the snippet execution data. The {@link SnippetRun} provides the actual
     *            snippet parameters, the execution result or the {@link PolyglotException} thrown
     *            by the execution.
     * @throws PolyglotException may propagate the {@link PolyglotException} from the snippetRun
     * @throws AssertionError may throw an {@link AssertionError} as a result of a verification
     * @since 0.30
     */
    @Override
    void accept(SnippetRun snippetRun) throws PolyglotException;

    /**
     * Provides the test execution data.
     *
     * @since 0.30
     */
    final class SnippetRun {
        private final Snippet snippet;
        private final List<? extends Value> parameters;
        private final Value result;
        private final PolyglotException exception;

        private SnippetRun(final Snippet snippet, final List<? extends Value> parameters, Value result, PolyglotException exception) {
            this.snippet = snippet;
            this.parameters = parameters;
            this.result = result;
            this.exception = exception;
        }

        /**
         * Returns the actual parameters of a snippet execution.
         *
         * @return the parameters
         * @since 0.30
         */
        public List<? extends Value> getParameters() {
            return parameters;
        }

        /**
         * Returns the result of a snippet execution.
         *
         * @return the result of a snippet execution or null in case of execution failure.
         * @since 0.30
         */
        public Value getResult() {
            return result;
        }

        /**
         * Returns the {@link PolyglotException} thrown during snippet execution.
         *
         * @return the {@link PolyglotException} thrown during the execution or null in case of
         *         successful execution.
         * @since 0.30
         */
        public PolyglotException getException() {
            return exception;
        }

        Snippet getSnippet() {
            return snippet;
        }

        /**
         * Creates a new {@link SnippetRun} for successful execution.
         *
         * @param snippet the executed {@link Snippet}
         * @param parameters the actual parameters of snippet execution
         * @param result the result of snippet execution
         * @return the {@link SnippetRun}
         * @since 0.30
         */
        public static SnippetRun create(final Snippet snippet, final List<? extends Value> parameters, final Value result) {
            Objects.requireNonNull(snippet, "Snippet has to be given.");
            Objects.requireNonNull(parameters, "Parameters has to be given.");
            Objects.requireNonNull(result, "Result has to be given.");
            return new SnippetRun(snippet, parameters, result, null);
        }

        /**
         * Creates a new {@link SnippetRun} for failed execution.
         *
         * @param snippet the executed {@link Snippet}
         * @param parameters the actual parameters of snippet execution
         * @param exception the {@link PolyglotException} thrown during snippet execution
         * @return the {@link SnippetRun}
         * @since 0.30
         */
        public static SnippetRun create(final Snippet snippet, final List<? extends Value> parameters, final PolyglotException exception) {
            return new SnippetRun(
                            Objects.requireNonNull(snippet, "Snippet has to be given."),
                            Objects.requireNonNull(parameters, "Parameters has to be given."),
                            null,
                            Objects.requireNonNull(exception, "Exception has to be given."));
        }
    }

    /**
     * Creates a default {@link ResultVerifier}. The default {@link ResultVerifier} tests that the
     * result type is in bounds specified by the {@link Snippet}.
     *
     * @return the default {@link ResultVerifier}
     * @since 19.0
     */
    static ResultVerifier getDefaultResultVerifier() {
        return DefaultResultVerifier.INSTANCE;
    }

    /**
     * Creates a default {@link ResultVerifier} for the {@code IdentityFunctionTest}. The returned
     * {@link ResultVerifier} tests that the identity function does not change the parameter type.
     *
     * @return the default {@link ResultVerifier} for {@code IdentityFunctionTest}.
     * @since 19.1.0
     */
    static ResultVerifier getIdentityFunctionDefaultResultVerifier() {
        return IdentityFunctionResultVerifier.INSTANCE;
    }
}
