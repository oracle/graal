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
package com.oracle.truffle.tools.agentscript;

import com.oracle.truffle.api.source.Source;
import java.util.function.Function;
import org.graalvm.polyglot.Engine;

/**
 * Programatic access to the scripting agent. Obtain an instance of this interface via its
 * {@link #ID}:
 * <p>
 * {@codesnippet Embedding#createAgentObject}
 * 
 * and then {@link Function#apply(java.lang.Object) evaluate} {@link org.graalvm.polyglot.Source}
 * scripts written in any language accessing the {@code agent} variable exposed to them. Use
 * {@link #VERSION current version API} when dealing with the {@code agent} variable.
 */
public interface AgentScript {
    /**
     * The ID of the agent script instrument is {@code "agentscript"}. Use it to obtain access to an
     * {@link AgentScript} instances inside of your {@link Engine}:
     * <p>
     * {@codesnippet Embedding#createAgentObject}
     */
    String ID = "agentscript";

    /**
     * Version of the agent script instrument. The current version understands following Java-like
     * polyglot <em>API</em> made available to the
     * {@link #registerAgentScript(com.oracle.truffle.api.source.Source) registered agent scripts}
     * via {@code agent} reference:
     * <p>
     * {@codesnippet AgentScriptAPI}
     */
    String VERSION = "0.3";

    /**
     * Loads an agent script file into the system. The script file may be written in any GraalVM
     * supported language and shall access {@code agent} variable which provides following
     * {@link #VERSION polyglot-ready API}.
     * 
     * @param file the file with the code for the agent
     */
    default void registerAgentScript(Source file) {
        throw new UnsupportedOperationException();
    }
}
