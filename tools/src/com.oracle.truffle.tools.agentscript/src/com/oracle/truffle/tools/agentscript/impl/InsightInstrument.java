/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.tools.insight.Insight;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import java.util.ArrayList;

// @formatter:off
@TruffleInstrument.Registration(
    id = Insight.ID,
    name = InsightInstrument.NAME,
    version = Insight.VERSION,
    services = { Function.class },
    website = "https://www.graalvm.org/tools/graalvm-insight/"
)
// @formatter:on
public class InsightInstrument extends TruffleInstrument {
    static final String NAME = "Insight";

    @Option(stability = OptionStability.STABLE, name = "", help = "Use provided file as an insight script (default: no script).", usageSyntax = "<path>", category = OptionCategory.USER) //
    static final OptionKey<String> SCRIPT = new OptionKey<>("");

    final IgnoreSources ignoreSources = new IgnoreSources();
    private final ContextLocal<InsightPerContext> perContextData;
    private Env env;
    /** @GuardedBy("keys" */
    private final BitSet keys = new BitSet();
    /** @GuardedBy("keys" */
    @CompilerDirectives.CompilationFinal private Assumption keysUnchanged;

    @SuppressWarnings("this-escape")
    public InsightInstrument() {
        this.perContextData = locals.createContextLocal((context) -> {
            return new InsightPerContext(this);
        });
        this.keysUnchanged = Truffle.getRuntime().createAssumption();
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new InsightInstrumentOptionDescriptors();
    }

    @Override
    protected void onCreate(Env tmp) {
        this.env = tmp;
        final Function<?, ?> registerScripts = registerScriptsAPI(this);
        env.registerService(registerScripts);
        final String path = env.getOptions().get(option());
        if (path != null && path.length() > 0) {
            registerAgentScript(() -> {
                try {
                    TruffleFile file = env.getTruffleFile(null, path);
                    if (file == null || !file.exists()) {
                        throw InsightException.notFound(file);
                    }
                    String mimeType = file.detectMimeType();
                    String lang = null;
                    for (Map.Entry<String, LanguageInfo> e : env.getLanguages().entrySet()) {
                        if (mimeType != null && e.getValue().getMimeTypes().contains(mimeType)) {
                            lang = e.getKey();
                            break;
                        }
                    }
                    if (lang == null) {
                        throw InsightException.notRecognized(file);
                    }
                    return Source.newBuilder(lang, file).uri(file.toUri()).name(file.getName()).build();
                } catch (IOException ex) {
                    throw InsightException.raise(ex);
                }
            });
        }
    }

    OptionKey<String> option() {
        return SCRIPT;
    }

    final Env env() {
        return env;
    }

    final AutoCloseable registerAgentScript(final Supplier<Source> src) {
        return new InsightPerSource(env.getInstrumenter(), this, src, ignoreSources);
    }

    @Override
    protected void onDispose(Env tmp) {
    }

    AgentObject createInsightObject(InsightPerSource source) {
        return new AgentObject(null, this, source);
    }

    @SuppressWarnings("unused")
    void collectGlobalSymbolsImpl(InsightPerSource source, List<String> argNames, List<Object> args) {
        for (InstrumentInfo item : env.getInstruments().values()) {
            if (NAME.equals(item.getName())) {
                continue;
            }
            Insight.SymbolProvider provider = env.lookup(item, Insight.SymbolProvider.class);
            if (provider == null) {
                continue;
            }
            try {
                for (Map.Entry<String, ?> e : provider.symbolsWithValues().entrySet()) {
                    if (e.getValue() == null) {
                        continue;
                    }
                    if (argNames.contains(e.getKey())) {
                        throw InsightException.unknownAttribute(e.getKey());
                    }
                    argNames.add(e.getKey());
                    args.add(e.getValue());

                }
            } catch (Exception ex) {
                throw InsightException.raise(ex);
            }
        }
    }

    private static Function<?, ?> registerScriptsAPI(InsightInstrument insight) {
        Function<org.graalvm.polyglot.Source, AutoCloseable> f = (text) -> {
            final Source.LiteralBuilder b = Source.newBuilder(text.getLanguage(), text.getCharacters(), text.getName());
            b.uri(text.getURI());
            b.mimeType(text.getMimeType());
            b.internal(text.isInternal());
            b.interactive(text.isInteractive());
            Source src = b.build();
            return insight.registerAgentScript(() -> src);
        };
        return maybeProxy(Function.class, f);
    }

    static <Interface> Interface maybeProxy(Class<Interface> type, Interface delegate) {
        if (TruffleOptions.AOT) {
            return delegate;
        } else {
            return proxy(type, delegate);
        }
    }

    private static <Interface> Interface proxy(Class<Interface> type, Interface delegate) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    final InsightPerContext find(TruffleContext ctx) {
        return this.perContextData.get(ctx);
    }

    final InsightPerContext findCtx() {
        return this.perContextData.get();
    }

    final Key newKey(AgentType type) {
        synchronized (keys) {
            int index = keys.nextClearBit(0);
            invalidateKeys(index, -1);
            return new Key(type, index);
        }
    }

    private void invalidateKeys(int set, int clear) {
        assert Thread.holdsLock(keys);

        if (set != -1) {
            keys.set(set);
        }
        if (clear != -1) {
            keys.clear(clear);
        }
        keysUnchanged.invalidate();
    }

    synchronized Assumption keysUnchangedAssumption() {
        if (!keysUnchanged.isValid()) {
            keysUnchanged = Truffle.getRuntime().createAssumption("Keys[" + keys + "]");
        }
        return keysUnchanged;
    }

    final int keysLength() {
        synchronized (keys) {
            return keys.length();
        }
    }

    final void closeKeys(Key... noLongerNeededKeys) {
        synchronized (keys) {
            for (Key k : noLongerNeededKeys) {
                k.close();
            }
        }
    }

    final class Key {
        @CompilerDirectives.CompilationFinal //
        private int index;
        @CompilerDirectives.CompilationFinal //
        private int functionsMaxLen;
        private final AgentType type;
        /* @GuardedBy(keys) */
        private final List<EventBinding<?>> bindings = new ArrayList<>(2);

        private Key(AgentType type, int index) {
            if (index < 0) {
                throw new IllegalArgumentException();
            }
            this.type = type;
            this.index = index;
        }

        Key assign(EventBinding<?> b) {
            CompilerAsserts.neverPartOfCompilation();
            synchronized (keys) {
                this.bindings.add(b);
                return this;
            }
        }

        int index() {
            return index;
        }

        int functionsMaxCount() {
            return functionsMaxLen;
        }

        @Override
        public String toString() {
            return "Key[" + index + "@" + type + "]";
        }

        private void close() {
            List<EventBinding<?>> bs;
            CompilerAsserts.neverPartOfCompilation();
            synchronized (keys) {
                bs = new ArrayList<>(bindings);
                bindings.clear();
                index = -1;
            }
            for (EventBinding<?> b : bs) {
                b.dispose();
            }
        }

        void adjustSize(int size) {
            if (size > this.functionsMaxLen) {
                this.functionsMaxLen = size;
                keysUnchanged.invalidate();
            }
        }

        boolean isClosed() {
            synchronized (keys) {
                return index == -1;
            }
        }
    }
}
