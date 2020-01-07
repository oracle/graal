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
import com.oracle.truffle.tools.agentscript.AgentScript;
import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

// @formatter:off
@TruffleInstrument.Registration(
    id = AgentScriptInstrument.ID,
    name = AgentScriptInstrument.NAME,
    version = AgentScriptInstrument.VERSION,
    services = AgentScript.class
)
// @formatter:on
public final class AgentScriptInstrument extends TruffleInstrument implements AgentScript {

    static final String NAME = "Agent Script";

    @Option(stability = OptionStability.EXPERIMENTAL, name = "", help = "Use provided agent script", category = OptionCategory.USER) //
    static final OptionKey<String> SCRIPT = new OptionKey<>("");

    private Env env;

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new AgentScriptInstrumentOptionDescriptors();
    }

    @Override
    protected void onCreate(Env tmp) {
        this.env = tmp;
        env.registerService(this);
        final String path = env.getOptions().get(SCRIPT);
        if (path != null && path.length() > 0) {
            registerAgentScript(() -> {
                try {
                    TruffleFile file = env.getTruffleFile(path);
                    if (file == null || !file.exists()) {
                        throw AgentException.notFound(file);
                    }
                    String mimeType = file.getMimeType();
                    String lang = null;
                    for (Map.Entry<String, LanguageInfo> e : env.getLanguages().entrySet()) {
                        if (e.getValue().getMimeTypes().contains(mimeType)) {
                            lang = e.getKey();
                            break;
                        }
                    }
                    return Source.newBuilder(lang, file).uri(file.toUri()).internal(true).name(file.getName()).build();
                } catch (IOException ex) {
                    throw AgentException.raise(ex);
                }
            });
        }
    }

    @Override
    public void registerAgentScript(final Source script) {
        registerAgentScript(() -> script);
    }

    private void registerAgentScript(final Supplier<Source> src) {
        final Instrumenter instrumenter = env.getInstrumenter();
        instrumenter.attachContextsListener(new ContextsListener() {
            private AgentObject agent;
            private EventBinding<?> agentBinding;

            @CompilerDirectives.TruffleBoundary
            void initializeAgent() {
                try {
                    Source script = src.get();
                    agent = new AgentObject(env);
                    CallTarget target = env.parse(script, "agent");
                    target.call(agent);
                    agent.initializationFinished();
                } catch (IOException ex) {
                    throw AgentException.raise(ex);
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
                final SourceSectionFilter anyRoot = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build();
                agentBinding = instrumenter.attachExecutionEventListener(anyRoot, new ExecutionEventListener() {
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
                });
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
        }, true);
    }

    @Override
    protected void onDispose(Env tmp) {
    }
}
