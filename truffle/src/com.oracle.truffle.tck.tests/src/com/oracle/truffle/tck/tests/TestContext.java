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
package com.oracle.truffle.tck.tests;

import com.oracle.truffle.tck.common.inline.InlineVerifier;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.InlineSnippet;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.graalvm.polyglot.tck.LanguageProvider;

final class TestContext implements Closeable {
    private Map<String, LanguageProvider> providers;
    private final Map<String, Collection<? extends Snippet>> valueConstructors;
    private final Map<String, Collection<? extends Snippet>> expressions;
    private final Map<String, Collection<? extends Snippet>> statements;
    private final Map<String, Collection<? extends Snippet>> scripts;
    private final Map<String, Collection<? extends InlineSnippet>> inlineScripts;
    private final boolean printOutput;
    private Context context;
    private InlineVerifier inlineVerifier;
    private State state;

    TestContext(final Class<?> testClass) {
        state = State.NEW;
        this.valueConstructors = new HashMap<>();
        this.expressions = new HashMap<>();
        this.statements = new HashMap<>();
        this.scripts = new HashMap<>();
        this.inlineScripts = new HashMap<>();
        boolean verbose = Boolean.getBoolean("tck.verbose");
        final String propValue = System.getProperty(String.format("tck.%s.verbose", testClass.getSimpleName()));
        if (propValue != null) {
            verbose = Boolean.parseBoolean(propValue);
        }
        printOutput = verbose;
    }

    Map<String, ? extends LanguageProvider> getInstalledProviders() {
        checkState(State.NEW, State.INITIALIZED);
        if (providers == null) {
            state = State.INITIALIZING;
            try {
                final Map<String, LanguageProvider> tmpProviders = new HashMap<>();
                final Set<String> languages = getContext().getEngine().getLanguages().keySet();
                for (LanguageProvider provider : ServiceLoader.load(LanguageProvider.class)) {
                    final String id = provider.getId();
                    if (languages.contains(id) || isHost(provider)) {
                        tmpProviders.put(id, provider);
                    }
                }
                providers = Collections.unmodifiableMap(tmpProviders);
            } finally {
                state = State.INITIALIZED;
            }
        }
        return providers;
    }

    @Override
    public void close() throws IOException {
        checkState(State.NEW, State.INITIALIZED);
        state = State.CLOSED;
        if (context != null) {
            context.close();
        }
    }

    Context getContext() {
        checkState(State.NEW, State.INITIALIZING, State.INITIALIZED);
        if (context == null) {
            final Context.Builder builder = Context.newBuilder().allowAllAccess(true);
            if (!printOutput) {
                builder.out(NullOutputStream.INSTANCE).err(NullOutputStream.INSTANCE);
            }
            this.context = builder.build();
            if (!isTruffleCompileImmediately()) {
                this.inlineVerifier = context.getEngine().getInstruments().get(InlineVerifier.ID).lookup(InlineVerifier.class);
                Assert.assertNotNull(this.inlineVerifier);
            }
        }
        return context;
    }

    Collection<? extends Snippet> getValueConstructors(TypeDescriptor type, String... ids) {
        Objects.requireNonNull(ids);
        checkState(State.NEW, State.INITIALIZED);
        return filter(
                        valueConstructors,
                        LanguageIdPredicate.create(ids),
                        TypePredicate.create(type, null),
                        new Function<LanguageProvider, Collection<? extends Snippet>>() {
                            @Override
                            public Collection<? extends Snippet> apply(LanguageProvider tli) {
                                final Collection<? extends Snippet> result = tli.createValueConstructors(context);
                                for (Snippet snippet : result) {
                                    if (!snippet.getParameterTypes().isEmpty()) {
                                        Assert.fail("Value constructors cannot have parameters, invalid Snippet: " + snippet);
                                    }
                                    if (snippet.getReturnType().isUnion()) {
                                        Assert.fail("Value constructors cannot return union types (use intersection type), invalid Snippet: " + snippet);
                                    }
                                }
                                return result;
                            }
                        });
    }

    Collection<? extends Snippet> getExpressions(TypeDescriptor type, List<? extends TypeDescriptor> parameterTypes, String... ids) {
        Objects.requireNonNull(ids);
        checkState(State.NEW, State.INITIALIZED);
        return filter(
                        expressions,
                        LanguageIdPredicate.create(ids),
                        TypePredicate.create(type, parameterTypes),
                        new Function<LanguageProvider, Collection<? extends Snippet>>() {
                            @Override
                            public Collection<? extends Snippet> apply(LanguageProvider tli) {
                                return tli.createExpressions(context);
                            }
                        });
    }

    Collection<? extends Snippet> getScripts(TypeDescriptor type, String... ids) {
        Objects.requireNonNull(ids);
        checkState(State.NEW, State.INITIALIZED);
        return filter(
                        scripts,
                        LanguageIdPredicate.create(ids),
                        TypePredicate.create(type, Collections.emptyList()),
                        new Function<LanguageProvider, Collection<? extends Snippet>>() {
                            @Override
                            public Collection<? extends Snippet> apply(LanguageProvider tli) {
                                return tli.createScripts(context);
                            }
                        });
    }

    Collection<? extends Snippet> getStatements(TypeDescriptor type, List<? extends TypeDescriptor> parameterTypes, String... ids) {
        Objects.requireNonNull(ids);
        checkState(State.NEW, State.INITIALIZED);
        return filter(
                        statements,
                        LanguageIdPredicate.create(ids),
                        TypePredicate.create(type, parameterTypes),
                        new Function<LanguageProvider, Collection<? extends Snippet>>() {
                            @Override
                            public Collection<? extends Snippet> apply(LanguageProvider tli) {
                                return tli.createStatements(context);
                            }
                        });
    }

    Collection<? extends InlineSnippet> getInlineScripts(String... ids) {
        return filter(
                        inlineScripts,
                        LanguageIdPredicate.create(ids),
                        new Predicate<InlineSnippet>() {
                            @Override
                            public boolean test(InlineSnippet s) {
                                return true;
                            }
                        },
                        new Function<LanguageProvider, Collection<? extends InlineSnippet>>() {
                            @Override
                            public Collection<? extends InlineSnippet> apply(LanguageProvider tli) {
                                return tli.createInlineScripts(context);
                            }
                        });
    }

    private <S> Collection<? extends S> filter(
                    final Map<String, Collection<? extends S>> cache,
                    final Predicate<Map.Entry<String, ? extends LanguageProvider>> idPredicate,
                    final Predicate<S> typePredicate,
                    final Function<LanguageProvider, Collection<? extends S>> provider) {
        return getInstalledProviders().entrySet().stream().filter(idPredicate).flatMap(new Function<Map.Entry<String, ? extends LanguageProvider>, Stream<? extends S>>() {
            @Override
            public Stream<? extends S> apply(Map.Entry<String, ? extends LanguageProvider> e) {
                return cache.computeIfAbsent(e.getKey(), new Function<String, Collection<? extends S>>() {
                    @Override
                    public Collection<? extends S> apply(String k) {
                        return provider.apply(e.getValue());
                    }
                }).stream();
            }
        }).filter(typePredicate).collect(Collectors.toList());
    }

    private void checkState(final State... allowedInStates) {
        boolean allowed = false;
        for (State allowedState : allowedInStates) {
            if (state == allowedState) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new IllegalStateException("Cannot be called in state: " + state);
        }
    }

    private static boolean isHost(LanguageProvider provider) {
        return provider.getClass() == JavaHostLanguageProvider.class;
    }

    Value getValue(Object object) {
        return context.asValue(object);
    }

    void setInlineSnippet(String languageId, InlineSnippet inlineSnippet, InlineVerifier.ResultVerifier verifier) {
        if (inlineVerifier != null) {
            inlineVerifier.setInlineSnippet(languageId, inlineSnippet, verifier);
        }
    }

    private static boolean isTruffleCompileImmediately() {
        return Boolean.getBoolean("graal.TruffleCompileImmediately");
    }

    private enum State {
        NEW,
        INITIALIZING,
        INITIALIZED,
        CLOSED
    }

    private static final class LanguageIdPredicate implements Predicate<Map.Entry<String, ? extends LanguageProvider>> {
        private static final Predicate<Map.Entry<String, ? extends LanguageProvider>> TRUE = new Predicate<Map.Entry<String, ? extends LanguageProvider>>() {
            @Override
            public boolean test(Map.Entry<String, ? extends LanguageProvider> e) {
                return true;
            }
        };
        private final Set<String> requiredIds;

        private LanguageIdPredicate(final String... ids) {
            requiredIds = new HashSet<>();
            Collections.addAll(requiredIds, ids);
        }

        @Override
        public boolean test(Map.Entry<String, ? extends LanguageProvider> e) {
            return requiredIds.contains(e.getKey());
        }

        static Predicate<Map.Entry<String, ? extends LanguageProvider>> create(String... ids) {
            return ids.length == 0 ? TRUE : new LanguageIdPredicate(ids);
        }
    }

    private static final class TypePredicate implements Predicate<Snippet> {
        private final TypeDescriptor type;
        private final List<? extends TypeDescriptor> parameterTypes;

        private TypePredicate(TypeDescriptor type, List<? extends TypeDescriptor> parameterTypes) {
            this.type = type;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public boolean test(final Snippet op) {
            if (type != null && !op.getReturnType().isAssignable(type)) {
                return false;
            }
            if (parameterTypes != null) {
                List<? extends TypeDescriptor> opParameterTypes = op.getParameterTypes();
                if (parameterTypes.size() != opParameterTypes.size()) {
                    return false;
                }
                for (int i = 0; i < parameterTypes.size(); i++) {
                    if (!opParameterTypes.get(i).isAssignable(parameterTypes.get(i))) {
                        return false;
                    }
                }
            }
            return true;
        }

        static Predicate<Snippet> create(TypeDescriptor type, List<? extends TypeDescriptor> parameterTypes) {
            return new TypePredicate(type, parameterTypes);
        }
    }

    private static final class NullOutputStream extends OutputStream {
        static OutputStream INSTANCE = new NullOutputStream();

        private NullOutputStream() {
        }

        @Override
        public void write(int b) throws IOException {
        }
    }
}
