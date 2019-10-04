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
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tools.agentscript.AgentScript;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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

    public static final String ID = "agentscript";
    static final String NAME = "Agent Script";
    static final String VERSION = "0.1";

    @Option(stability = OptionStability.EXPERIMENTAL, name = "", help = "Use provided agent script", category = OptionCategory.USER) //
    static final OptionKey<String> SCRIPT = new OptionKey<>("");

    private EventBinding<?> rootsBinding = null;
    private Env env;

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new AgentScriptInstrumentOptionDescriptors();
    }

    @Override
    protected void onCreate(Env env) {
        this.env = env;
        env.registerService(this);
        final String path = env.getOptions().get(SCRIPT);
        if (path != null && path.length() > 0) {
            try {
                TruffleFile file = env.getTruffleFile(path);
                String mimeType = file.getMimeType();
                String lang = null;
                for (Map.Entry<String, LanguageInfo> e : env.getLanguages().entrySet()) {
                    if (e.getValue().getMimeTypes().contains(mimeType)) {
                        lang = e.getKey();
                        break;
                    }
                }
                Source script = Source.newBuilder(lang, file).uri(file.toUri()).internal(true).name(file.getName()).build();
                registerAgentScript(script);
            } catch (IOException ex) {
                throw AgentObject.raise(RuntimeException.class, ex);
            }
        }
    }

    @Override
    public void registerAgentScript(final Source script) {
        final Instrumenter instrumenter = env.getInstrumenter();
        final AtomicReference<EventBinding<?>> ctxListenerBinding = new AtomicReference<>();
        ctxListenerBinding.set(instrumenter.attachContextsListener(new ContextsListener() {
            @Override
            public void onContextCreated(TruffleContext context) {
            }

            @Override
            public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                try {
                    AgentObject agent = new AgentObject(env, null, language);
                    CallTarget target = env.parse(script, "agent");
                    target.call(agent);
                    agent.initializationFinished();
                } catch (IOException ex) {
                    throw AgentObject.raise(RuntimeException.class, ex);
                } finally {
                    final EventBinding<?> onceIsEnough = ctxListenerBinding.getAndSet(null);
                    if (onceIsEnough != null) {
                        onceIsEnough.dispose();
                    }
                }
            }

            @Override
            public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onContextClosed(TruffleContext context) {
            }
        }, true));
    }

    @Override
    protected void onDispose(Env env) {
        if (rootsBinding != null && !rootsBinding.isDisposed()) {
            rootsBinding.dispose();
        }
    }
}
