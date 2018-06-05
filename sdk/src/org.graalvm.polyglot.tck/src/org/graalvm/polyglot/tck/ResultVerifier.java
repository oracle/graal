/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
     * @since 1.0
     */
    static ResultVerifier getDefaultResultVerifier() {
        return DefaultResultVerifier.INSTANCE;
    }
}
