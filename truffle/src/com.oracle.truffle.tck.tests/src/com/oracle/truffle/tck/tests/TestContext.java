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
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.InlineSnippet;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.graalvm.polyglot.tck.LanguageProvider;

final class TestContext implements Closeable {
    private static final Object contextCacheLock = new Object();
    private static RefCountedContextReference contextCache;

    private Map<String, LanguageProvider> providers;
    private final Map<String, Collection<? extends Snippet>> valueConstructors;
    private final Map<String, Collection<? extends Snippet>> expressions;
    private final Map<String, Collection<? extends Snippet>> statements;
    private final Map<String, Collection<? extends Snippet>> scripts;
    private final Map<String, Collection<? extends InlineSnippet>> inlineScripts;
    private final boolean printOutput;
    private final boolean enableInlineVerifier;
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
        boolean verbose = true;
        String propValue = System.getProperty("tck.verbose");
        if (propValue != null) {
            verbose = Boolean.parseBoolean(propValue);
        }
        propValue = System.getProperty(String.format("tck.%s.verbose", testClass.getSimpleName()));
        if (propValue != null) {
            verbose = Boolean.parseBoolean(propValue);
        }
        printOutput = verbose;
        propValue = System.getProperty("tck.inlineVerifierInstrument");
        enableInlineVerifier = propValue == null ? true : Boolean.parseBoolean(propValue);
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
                    } else {
                        throw new IllegalStateException("Provider " + provider.getClass().getName() + " requires a non installed language " + id + "\n" +
                                        "Installed languages: " + String.join(", ", languages));
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
            synchronized (contextCacheLock) {
                contextCache.close();
                if (!contextCache.isValid()) {
                    context.close();
                    contextCache = null;
                }
            }
        }
    }

    Context getContext() {
        checkState(State.NEW, State.INITIALIZING, State.INITIALIZED);
        if (context == null) {
            synchronized (contextCacheLock) {
                if (contextCache != null) {
                    this.context = contextCache.retain();
                    try {
                        contextCache.out().setDelegate(printOutput ? System.out : NullOutputStream.INSTANCE);
                        contextCache.err().setDelegate(printOutput ? System.err : NullOutputStream.INSTANCE);
                    } catch (IOException ioe) {
                        throw new RuntimeException("Failed to flush stdout, stderr.", ioe);
                    }
                } else {
                    ProxyOutputStream out = new ProxyOutputStream(printOutput ? System.out : NullOutputStream.INSTANCE);
                    ProxyOutputStream err = new ProxyOutputStream(printOutput ? System.err : NullOutputStream.INSTANCE);
                    this.context = Context.newBuilder().allowAllAccess(true).out(out).err(err).build();
                    assert contextCache == null;
                    contextCache = new RefCountedContextReference(context, out, err);
                }
            }
            if (enableInlineVerifier) {
                Instrument instrument = context.getEngine().getInstruments().get(InlineVerifier.ID);
                this.inlineVerifier = instrument.lookup(InlineVerifier.class);
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

    private static final class ProxyOutputStream extends OutputStream {

        private OutputStream delegate;

        ProxyOutputStream(OutputStream delegate) {
            Objects.requireNonNull(delegate, "Delegate must be non null.");
            this.delegate = delegate;
        }

        void setDelegate(OutputStream newDelegate) throws IOException {
            this.delegate.flush();
            this.delegate = newDelegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static final class RefCountedContextReference implements Closeable {

        private final ProxyOutputStream out;
        private final ProxyOutputStream err;
        private Context context;
        private int refCount;

        RefCountedContextReference(Context context, ProxyOutputStream out, ProxyOutputStream err) {
            this.context = context;
            this.refCount = 1;
            this.out = out;
            this.err = err;
        }

        ProxyOutputStream out() {
            return out;
        }

        ProxyOutputStream err() {
            return err;
        }

        Context retain() {
            if (refCount == 0) {
                throw new IllegalStateException("Released reference");
            }
            refCount++;
            return context;
        }

        boolean isValid() {
            return refCount > 0;
        }

        @Override
        public void close() {
            if (refCount == 0) {
                throw new IllegalStateException("Released reference");
            }
            refCount--;
            if (refCount == 0) {
                context = null;
            }
        }
    }
}
