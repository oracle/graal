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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tools.agentscript.AgentScriptInstrument;
import java.lang.reflect.Constructor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Value;
import static org.junit.Assert.assertNotNull;
import com.oracle.truffle.tools.agentscript.AgentScript;

final class AgentObjectFactory extends ProxyLanguage {

    public TruffleObject createAgentObject() {
        try {
            Constructor<?> cnstr = Class.forName("com.oracle.truffle.tools.agentscript.AgentObject").getDeclaredConstructor(Instrumenter.class, Source.class, LanguageInfo.class);
            cnstr.setAccessible(true);
            return (TruffleObject) cnstr.newInstance(null, null, null);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return Truffle.getRuntime().createCallTarget(new RootNode(ProxyLanguage.getCurrentLanguage()) {
            @Override
            public Object execute(VirtualFrame frame) {
                final Env env = ProxyLanguage.getCurrentContext().getEnv();
                InstrumentInfo instrument = env.getInstruments().get(AgentScriptInstrument.ID);
                return createAgentObject();
            }
        });
    }

    public static Value createAgentObject(Context c) {
        ProxyLanguage.setDelegate(new AgentObjectFactory());
        Instrument instrument = c.getEngine().getInstruments().get(AgentScriptInstrument.ID);
        AgentScript access = instrument.lookup(AgentScript.class);
        assertNotNull("Accessor found", access);
        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.create(ProxyLanguage.ID, "");
        return c.eval(source);
    }

}
