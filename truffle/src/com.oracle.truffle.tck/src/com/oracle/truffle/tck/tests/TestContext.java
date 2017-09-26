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

import java.io.Closeable;
import java.io.IOException;
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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.graalvm.polyglot.tck.LanguageProvider;

final class TestContext implements Closeable {
    private Map<String, LanguageProvider> providers;
    private final Map<String, Collection<? extends Snippet>> valueConstructors;
    private final Map<String, Collection<? extends Snippet>> expressions;
    private final Map<String, Collection<? extends Snippet>> statements;
    private final Map<String, Collection<? extends Snippet>> scripts;
    private Context context;
    private State state;

    TestContext() {
        state = State.NEW;
        this.valueConstructors = new HashMap<>();
        this.expressions = new HashMap<>();
        this.statements = new HashMap<>();
        this.scripts = new HashMap<>();
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
            this.context = Context.create();
        }
        return context;
    }

    Collection<? extends Snippet> getValueConstructors(TypeDescriptor type, String... ids) {
        Objects.requireNonNull(ids);
        checkState(State.NEW, State.INITIALIZED);
        return filter(
                        valueConstructors,
                        newIdPredicate(ids),
                        newTypePredicate(type, null),
                        (tli) -> tli.createValueConstructors(context));
    }

    Collection<? extends Snippet> getExpressions(TypeDescriptor type, List<? extends TypeDescriptor> parameterTypes, String... ids) {
        Objects.requireNonNull(ids);
        checkState(State.NEW, State.INITIALIZED);
        return filter(
                        expressions,
                        newIdPredicate(ids),
                        newTypePredicate(type, parameterTypes),
                        (tli) -> tli.createExpressions(context));
    }

    Collection<? extends Snippet> getScripts(TypeDescriptor type, String... ids) {
        Objects.requireNonNull(ids);
        checkState(State.NEW, State.INITIALIZED);
        return filter(
                        scripts,
                        newIdPredicate(ids),
                        newTypePredicate(type, Collections.emptyList()),
                        (tli) -> tli.createScripts(context));
    }

    Collection<? extends Snippet> getStatements(TypeDescriptor type, List<? extends TypeDescriptor> parameterTypes, String... ids) {
        Objects.requireNonNull(ids);
        checkState(State.NEW, State.INITIALIZED);
        return filter(
                        statements,
                        newIdPredicate(ids),
                        newTypePredicate(type, parameterTypes),
                        (tli) -> tli.createStatements(context));
    }

    private Collection<? extends Snippet> filter(
                    final Map<String, Collection<? extends Snippet>> cache,
                    final Predicate<Map.Entry<String, ? extends LanguageProvider>> idPredicate,
                    final Predicate<Snippet> typePredicate,
                    final Function<LanguageProvider, Collection<? extends Snippet>> provider) {
        return getInstalledProviders().entrySet().stream().filter(idPredicate).flatMap((e) -> cache.computeIfAbsent(e.getKey(), (k) -> provider.apply(e.getValue())).stream()).filter(
                        typePredicate).collect(Collectors.toList());
    }

    private static Predicate<Map.Entry<String, ? extends LanguageProvider>> newIdPredicate(final String[] ids) {
        return ids.length == 0 ? (e) -> true : new Predicate<Map.Entry<String, ? extends LanguageProvider>>() {
            private final Set<String> requiredIds;
            {
                requiredIds = new HashSet<>();
                Collections.addAll(requiredIds, ids);
            }

            @Override
            public boolean test(Map.Entry<String, ? extends LanguageProvider> e) {
                return requiredIds.contains(e.getKey());
            }
        };
    }

    private static Predicate<Snippet> newTypePredicate(TypeDescriptor type, List<? extends TypeDescriptor> parameterTypes) {
        return (Snippet op) -> {
            if (type != null && !TestUtil.isAssignable(op.getReturnType(), type)) {
                return false;
            }
            if (parameterTypes != null) {
                List<? extends TypeDescriptor> opParameterTypes = op.getParameterTypes();
                if (parameterTypes.size() != opParameterTypes.size()) {
                    return false;
                }
                for (int i = 0; i < parameterTypes.size(); i++) {
                    if (!TestUtil.isAssignable(opParameterTypes.get(i), parameterTypes.get(i))) {
                        return false;
                    }
                }
            }
            return true;
        };
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

    private enum State {
        NEW,
        INITIALIZING,
        INITIALIZED,
        CLOSED
    }
}
