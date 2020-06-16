/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.tools.insight.Insight;

// @formatter:off
@TruffleInstrument.Registration(
    id = Insight.ID,
    name = InsightInstrument.NAME,
    version = Insight.VERSION,
    services = { Function.class }
)
// @formatter:on
public class InsightInstrument extends TruffleInstrument {
    static final String NAME = "Insight";

    @Option(stability = OptionStability.EXPERIMENTAL, name = "", help = "Use provided file as an insight script", category = OptionCategory.USER) //
    static final OptionKey<String> SCRIPT = new OptionKey<>("");

    private Env env;
    private final IgnoreSources ignoreSources = new IgnoreSources();

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new InsightInstrumentOptionDescriptors();
    }

    @Override
    protected void onCreate(Env tmp) {
        this.env = tmp;
        final Function<?, ?> api = functionApi(this);
        env.registerService(api);
        final String path = env.getOptions().get(option());
        if (path != null && path.length() > 0) {
            registerAgentScript(() -> {
                try {
                    TruffleFile file = env.getTruffleFile(path);
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

    final AutoCloseable registerAgentScript(final Supplier<Source> src) {
        final Instrumenter instrumenter = env.getInstrumenter();
        class InitializeAgent implements ContextsListener, AutoCloseable {
            private AgentObject insight;
            private AgentObject agent;
            private EventBinding<?> agentBinding;

            @CompilerDirectives.TruffleBoundary
            synchronized boolean initializeAgentObject() {
                if (agent == null) {
                    AgentObject.Data sharedData = new AgentObject.Data();
                    insight = new AgentObject(null, env, ignoreSources, sharedData);
                    agent = new AgentObject("Warning: 'agent' is deprecated. Use 'insight'.\n", env, ignoreSources, sharedData);
                    return true;
                }
                return false;
            }

            @CompilerDirectives.TruffleBoundary
            void initializeAgent() {
                if (initializeAgentObject()) {
                    Source script = src.get();
                    ignoreSources.ignoreSource(script);
                    CallTarget target;
                    try {
                        target = env.parse(script, "insight", "agent");
                    } catch (Exception ex) {
                        throw InsightException.raise(ex);
                    }
                    target.call(insight, agent);
                }
            }

            @Override
            public void onContextCreated(TruffleContext context) {
            }

            @Override
            public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                if (agentBinding != null || language.isInternal()) {
                    return;
                }
                if (context.isEntered()) {
                    initializeAgent();
                } else {
                    class InitializeLater implements ExecutionEventListener {

                        @Override
                        public void onEnter(EventContext ctx, VirtualFrame frame) {
                            CompilerDirectives.transferToInterpreter();
                            agentBinding.dispose();
                            initializeAgent();
                        }

                        @Override
                        public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
                        }

                        @Override
                        public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {
                        }
                    }
                    final SourceSectionFilter anyRoot = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build();
                    agentBinding = instrumenter.attachExecutionEventListener(anyRoot, new InitializeLater());
                }
            }

            @Override
            public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
                if (agent != null) {
                    agent.onClosed();
                }
            }

            @Override
            public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onContextClosed(TruffleContext context) {
            }

            @Override
            public void close() {
                if (agent != null) {
                    agent.onClosed();
                }
                if (agentBinding != null) {
                    agentBinding.dispose();
                }
            }
        }
        final InitializeAgent initializeAgent = new InitializeAgent();
        instrumenter.attachContextsListener(initializeAgent, true);
        return initializeAgent;
    }

    @Override
    protected void onDispose(Env tmp) {
    }

    private static Function<?, ?> functionApi(InsightInstrument agentScript) {
        Function<org.graalvm.polyglot.Source, AutoCloseable> f = (text) -> {
            final Source.LiteralBuilder b = Source.newBuilder(text.getLanguage(), text.getCharacters(), text.getName());
            b.uri(text.getURI());
            b.mimeType(text.getMimeType());
            b.internal(text.isInternal());
            b.interactive(text.isInteractive());
            Source src = b.build();
            return agentScript.registerAgentScript(() -> src);
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
            return method.invoke(delegate, args);
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
