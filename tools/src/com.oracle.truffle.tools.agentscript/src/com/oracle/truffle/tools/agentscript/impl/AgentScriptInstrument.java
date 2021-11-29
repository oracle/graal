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

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.tools.insight.Insight;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

// @formatter:off
@SuppressWarnings("deprecation")
@TruffleInstrument.Registration(
    id = "agentscript",
    name = "Agent Script",
    version = Insight.VERSION,
    services = {Function.class}
)
// @formatter:on
public final class AgentScriptInstrument extends InsightInstrument {
    @Option(stability = OptionStability.EXPERIMENTAL, name = "", help = "Deprecated. Use --insight!", category = OptionCategory.USER) //
    static final OptionKey<String> DEPRECATED = new OptionKey<>("");

    private AgentObject agent;

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new AgentScriptInstrumentOptionDescriptors();
    }

    @Override
    OptionKey<String> option() {
        return DEPRECATED;
    }

    @Override
    synchronized void collectGlobalSymbolsImpl(InsightPerSource src, List<String> argNames, List<Object> args) {
        if (agent == null) {
            agent = new AgentObject("Warning: 'agent' is deprecated. Use 'insight'.\n", this, src);
        }
        argNames.add("agent");
        args.add(agent);
    }

    @Override
    protected void onCreate(TruffleInstrument.Env env) {
        super.onCreate(env);
        try {
            env.err().write("Warning: Option --agentscript is deprecated. Use --insight option.\n".getBytes());
        } catch (IOException ex) {
            // ignore
        }
    }

}
