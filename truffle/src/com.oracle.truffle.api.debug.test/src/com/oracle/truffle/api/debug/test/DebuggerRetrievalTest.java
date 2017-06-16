/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;

/**
 * Test that languages and other instruments are able to retrieve the Debugger instance.
 */
public class DebuggerRetrievalTest {

    @Test
    public void testFromLanguage() {
        Value debuggerValue = Engine.create().getLanguage(LanguageThatNeedsDebugger.ID).createContext().lookup("debugger");
        Assert.assertTrue(debuggerValue.asBoolean());
    }

    @Test
    public void testFromInstrument() {
        InstrumentThatNeedsDebugger.haveDebugger = false;
        Engine.newBuilder().setOption(InstrumentThatNeedsDebugger.ID + ".dbg", "").build();
        Assert.assertTrue(InstrumentThatNeedsDebugger.haveDebugger);
    }

    @TruffleLanguage.Registration(id = LanguageThatNeedsDebugger.ID, mimeType = LanguageThatNeedsDebugger.MIME_TYPE, name = "Language That Needs Debugger", version = "1.0")
    public static class LanguageThatNeedsDebugger extends TruffleLanguage<Debugger> {

        static final String ID = "language-that-needs-debugger";
        static final String MIME_TYPE = "application/x-language-that-needs-debugger";

        @Override
        protected Debugger createContext(TruffleLanguage.Env env) {
            InstrumentInfo debuggerInfo = env.getInstruments().get("debugger");
            Debugger debugger = env.lookup(debuggerInfo, Debugger.class);
            Assert.assertEquals(debugger, Debugger.find(env));
            return debugger;
        }

        @Override
        protected Object getLanguageGlobal(Debugger context) {
            return null;
        }

        @Override
        protected Object lookupSymbol(Debugger context, String symbolName) {
            return "debugger".equals(symbolName) && context != null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

    }

    @TruffleInstrument.Registration(id = InstrumentThatNeedsDebugger.ID, name = "Instrument That Needs Debugger")
    public static class InstrumentThatNeedsDebugger extends TruffleInstrument {

        static final String ID = "instrument-that-needs-debugger";
        static boolean haveDebugger;

        private final OptionKey<String> debuggerKey = new OptionKey<>("");

        @Override
        protected void onCreate(TruffleInstrument.Env env) {
            Debugger debugger = env.lookup(env.getInstruments().get("debugger"), Debugger.class);
            env.getOptions().set(debuggerKey, debugger.toString());
            haveDebugger = true;
        }

        @Override
        protected List<OptionDescriptor> describeOptions() {
            return Collections.singletonList(OptionDescriptor.newBuilder(debuggerKey, ID + ".dbg").build());
        }

    }

}
