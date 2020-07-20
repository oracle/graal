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

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.Source;
import java.io.IOException;
import java.util.function.Function;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

// @formatter:off
@SuppressWarnings("deprecation")
@TruffleInstrument.Registration(
    id = com.oracle.truffle.tools.agentscript.AgentScript.ID,
    name = "Agent Script",
    version = com.oracle.truffle.tools.agentscript.AgentScript.VERSION,
    services = { Function.class, com.oracle.truffle.tools.agentscript.AgentScript.class }
)
// @formatter:on
public final class AgentScriptInstrument extends InsightInstrument implements com.oracle.truffle.tools.agentscript.AgentScript {
    @Option(stability = OptionStability.EXPERIMENTAL, name = "", help = "Deprecated. Use --insight!", category = OptionCategory.USER) //
    static final OptionKey<String> DEPRECATED = new OptionKey<>("");

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new AgentScriptInstrumentOptionDescriptors();
    }

    @Override
    OptionKey<String> option() {
        return DEPRECATED;
    }

    @Override
    protected void onCreate(TruffleInstrument.Env env) {
        super.onCreate(env);
        try {
            env.err().write("Warning: Option --agentscript is deprecated. Use --insight option.\n".getBytes());
        } catch (IOException ex) {
            // ignore
        }
        com.oracle.truffle.tools.agentscript.AgentScript as = maybeProxy(com.oracle.truffle.tools.agentscript.AgentScript.class, this);
        env.registerService(as);
    }

    @Override
    public void registerAgentScript(final Source script) {
        registerAgentScript(() -> script);
    }
}
