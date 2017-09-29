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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

public final class Snippet {
    private final String id;
    private final Value executableValue;
    private final TypeDescriptor type;
    private final List<? extends TypeDescriptor> parameterTypes;
    private final Consumer<? super SnippetRun> verifier;

    private Snippet(
                    final String id,
                    final Value executableValue,
                    final TypeDescriptor type,
                    final List<? extends TypeDescriptor> parameterTypes,
                    final Consumer<? super SnippetRun> verifier) {
        if (!executableValue.canExecute()) {
            throw new IllegalArgumentException("The executableValue has to be executable.");
        }
        this.id = id;
        this.executableValue = executableValue;
        this.type = type;
        this.parameterTypes = parameterTypes;
        this.verifier = verifier == null ? new DefaultResultVerifier() : verifier;
    }

    public String getId() {
        return id;
    }

    public Value getExecutableValue() {
        return executableValue;
    }

    public TypeDescriptor getReturnType() {
        return type;
    }

    public List<? extends TypeDescriptor> getParameterTypes() {
        return parameterTypes;
    }

    public Consumer<? super SnippetRun> getResultVerifier() {
        return verifier;
    }

    @Override
    public String toString() {
        return new StringBuilder(id).append(parameterTypes.stream().map(Object::toString).collect(Collectors.joining(", ", "(", ")"))).append(':').append(type).toString();
    }

    public static Builder newBuilder(
                    final String id,
                    final Value executableValue,
                    final TypeDescriptor executableReturnType) {
        return new Builder(id, executableValue, executableReturnType);
    }

    public static final class Builder {
        private final String id;
        private final Value executableValue;
        private TypeDescriptor executableReturnType;
        private List<TypeDescriptor> parameterTypes;
        private Consumer<? super SnippetRun> verifier;

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

        public Builder parameterTypes(@SuppressWarnings("hiding") final TypeDescriptor... parameterTypes) {
            Objects.requireNonNull(parameterTypes);
            this.parameterTypes = Arrays.asList(parameterTypes);
            return this;
        }

        public Builder resultVerifier(final Consumer<? super SnippetRun> resultVerifier) {
            this.verifier = resultVerifier;
            return this;
        }

        public Snippet build() {
            return new Snippet(id, executableValue, executableReturnType, Collections.unmodifiableList(parameterTypes), verifier);
        }
    }

    private final class DefaultResultVerifier implements Consumer<SnippetRun> {

        @Override
        public void accept(final SnippetRun snippetRun) {
            final PolyglotException exception = snippetRun.getException();
            if (exception != null) {
                throw new AssertionError(null, exception);
            }
            final TypeDescriptor resultType = TypeDescriptor.forValue(snippetRun.getResult());
            if (!getReturnType().isAssignable(resultType)) {
                throw new AssertionError(String.format(
                                "Result is out of type bounds. Expected: %s, Got: %s.",
                                getReturnType(),
                                resultType));
            }
        }
    }
}
