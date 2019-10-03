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
package com.oracle.truffle.tools.agentscript.test;

import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class AgentObjectTest {
    public interface API {
        public interface EventContext {
            String name();
        }

        @FunctionalInterface
        public interface OnEventHandler {
            void eventHasJustHappened(EventContext c);
        }

        void on(String event, OnEventHandler handler);
    }

    @Test
    public void onSourceCallback() throws Exception {
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Value agent = AgentObjectFactory.createAgentObject(c);
            API agentAPI = agent.as(API.class);
            Assert.assertNotNull("Agent API obtained", agentAPI);

            String[] loadedScript = {null};
            agentAPI.on("source", (ev) -> {
                loadedScript[0] = ev.name();
            });

            Source sampleScript = Source.newBuilder(ProxyLanguage.ID, "sample, code", "sample.px").build();
            c.eval(sampleScript);

            assertEquals(sampleScript.getName(), loadedScript[0]);
        }
    }
}
