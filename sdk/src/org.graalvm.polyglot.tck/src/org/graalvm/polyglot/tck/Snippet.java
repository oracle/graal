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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Value;

/**
 * The unit of execution with assigned parameters and result types. The {@link Snippet} represents
 * an execution of value constructor, statement, expression and script. The {@link Snippet} provides
 * parameter(s) and return types used to compose the {@link Snippet}s.
 *
 * @since 0.30
 */
public final class Snippet {
    private final String id;
    private final Value executableValue;
    private final TypeDescriptor type;
    private final List<? extends TypeDescriptor> parameterTypes;
    private final ResultVerifier verifier;

    private Snippet(
                    final String id,
                    final Value executableValue,
                    final TypeDescriptor type,
                    final List<? extends TypeDescriptor> parameterTypes,
                    final ResultVerifier verifier) {
        if (!executableValue.canExecute()) {
            throw new IllegalArgumentException("The executableValue has to be executable.");
        }
        this.id = id;
        this.executableValue = executableValue;
        this.type = type;
        this.parameterTypes = parameterTypes;
        this.verifier = verifier;
    }

    /**
     * Returns the identifier of a snippet. The {@link Snippet}s from a single
     * {@link LanguageProvider} with the same identifier but different types are treated as
     * overloads.
     *
     * @return the {@link Snippet} identifier
     * @since 0.30
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the function executing the {@link Snippet}.
     *
     * @return the executable {@link Value}
     * @since 0.30
     */
    public Value getExecutableValue() {
        return executableValue;
    }

    /**
     * Returns the {@link Snippet} return type.
     *
     * @return the return type
     * @since 0.30
     */
    public TypeDescriptor getReturnType() {
        return type;
    }

    /**
     * Returns the types of {@link Snippet} formal parameters.
     *
     * @return the parameter types
     * @since 0.30
     */
    public List<? extends TypeDescriptor> getParameterTypes() {
        return parameterTypes;
    }

    /**
     * Returns the {@link ResultVerifier} to verify the execution result.
     *
     * @return either a custom or the default {@link ResultVerifier}.
     * @see ResultVerifier#accept(org.graalvm.polyglot.tck.ResultVerifier.SnippetRun).
     * @since 0.30
     */
    public ResultVerifier getResultVerifier() {
        return verifier;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.30
     */
    @Override
    public String toString() {
        return new StringBuilder(id).append(parameterTypes.stream().map(Object::toString).collect(Collectors.joining(", ", "(", ")"))).append(':').append(type).toString();
    }

    /**
     * Creates a new {@link Snippet} builder object.
     *
     * @param id the {@link Snippet} identifier The {@link Snippet}s from a single
     *            {@link LanguageProvider} with the same identifier but different types are treated
     *            as overloads.
     * @param executableValue the executable {@link Value} used to execute the {@link Snippet}
     * @param executableReturnType the {@link Snippet} return type
     * @return the new {@link Builder}
     * @since 0.30
     */
    public static Builder newBuilder(
                    final String id,
                    final Value executableValue,
                    final TypeDescriptor executableReturnType) {
        return new Builder(id, executableValue, executableReturnType);
    }

    /**
     * The builder of a {@link Snippet}.
     *
     * @since 0.30
     */
    public static final class Builder {
        private final String id;
        private final Value executableValue;
        private TypeDescriptor executableReturnType;
        private List<TypeDescriptor> parameterTypes;
        private ResultVerifier verifier;

        private Builder(
                        final String id,
                        final Value executableValue,
                        final TypeDescriptor executableReturnType) {
            Objects.requireNonNull(id);
            Objects.requireNonNull(executableValue);
            Objects.requireNonNull(executableReturnType);
            this.id = id;
            this.executableValue = executableValue;
            this.executableReturnType = executableReturnType;
            this.parameterTypes = new ArrayList<>();
        }

        /**
         * Sets the {@link Snippet} formal parameter types.
         *
         * @param parameterTypes the types of {@link Snippet}'s parameters
         * @return this {@link Builder}
         * @since 0.30
         */
        public Builder parameterTypes(@SuppressWarnings("hiding") final TypeDescriptor... parameterTypes) {
            Objects.requireNonNull(parameterTypes);
            this.parameterTypes = Arrays.asList(parameterTypes);
            return this;
        }

        /**
         * Sets a custom verifier of a result of the {@link Snippet} execution.
         *
         * @param resultVerifier the custom {@link ResultVerifier}
         * @return this {@link Builder}
         * @see ResultVerifier#accept(org.graalvm.polyglot.tck.ResultVerifier.SnippetRun).
         * @since 0.30
         */
        public Builder resultVerifier(final ResultVerifier resultVerifier) {
            this.verifier = resultVerifier;
            return this;
        }

        /**
         * Creates a new {@link Snippet} configured by this {@link Builder}.
         *
         * @return the {@link Snippet}
         * @since 0.30
         */
        public Snippet build() {
            return new Snippet(
                            id,
                            executableValue,
                            executableReturnType,
                            Collections.unmodifiableList(parameterTypes),
                            verifier != null ? verifier : ResultVerifier.getDefaultResultVerifier());
        }
    }
}
