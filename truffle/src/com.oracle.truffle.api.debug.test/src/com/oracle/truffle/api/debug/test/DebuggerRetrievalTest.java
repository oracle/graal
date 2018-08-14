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

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

/**
 * Test that languages and other instruments are able to retrieve the Debugger instance.
 */
public class DebuggerRetrievalTest {

    @Test
    public void testFromLanguage() {
        Value debuggerValue = Context.create(LanguageThatNeedsDebugger.ID).getBindings(LanguageThatNeedsDebugger.ID).getMember("debugger");
        Assert.assertTrue(debuggerValue.asBoolean());
    }

    @Test
    public void testFromInstrument() {
        InstrumentThatNeedsDebugger.haveDebugger = false;
        Context.newBuilder().option(InstrumentThatNeedsDebugger.ID + ".dbg", "").build();
        Assert.assertTrue(InstrumentThatNeedsDebugger.haveDebugger);
    }

    @TruffleLanguage.Registration(id = LanguageThatNeedsDebugger.ID, name = "Language That Needs Debugger", version = "1.0")
    public static class LanguageThatNeedsDebugger extends TruffleLanguage<Debugger> {

        static final String ID = "language-that-needs-debugger";

        @Override
        protected Debugger createContext(TruffleLanguage.Env env) {
            InstrumentInfo debuggerInfo = env.getInstruments().get("debugger");
            Debugger debugger = env.lookup(debuggerInfo, Debugger.class);
            Assert.assertEquals(debugger, Debugger.find(env));
            return debugger;
        }

        @Override
        protected Iterable<Scope> findTopScopes(Debugger context) {
            return Collections.singleton(Scope.newBuilder("Debugger top scope", new TopScopeObject(context)).build());
        }

        static final class TopScopeObject implements TruffleObject {

            private final Debugger context;

            private TopScopeObject(Debugger context) {
                this.context = context;
            }

            @Override
            public ForeignAccess getForeignAccess() {
                return TopScopeObjectMessageResolutionForeign.ACCESS;
            }

            public static boolean isInstance(TruffleObject obj) {
                return obj instanceof TopScopeObject;
            }

            @MessageResolution(receiverType = TopScopeObject.class)
            static class TopScopeObjectMessageResolution {

                @Resolve(message = "KEY_INFO")
                abstract static class VarsMapInfoNode extends Node {

                    @SuppressWarnings("unused")
                    public Object access(TopScopeObject ts, String name) {
                        if ("debugger".equals(name)) {
                            return KeyInfo.READABLE;
                        } else {
                            return 0;
                        }
                    }
                }

                @Resolve(message = "HAS_KEYS")
                abstract static class HasKeysNode extends Node {

                    @SuppressWarnings("unused")
                    public Object access(TopScopeObject ts) {
                        return true;
                    }
                }

                @Resolve(message = "READ")
                abstract static class VarsMapReadNode extends Node {

                    @CompilerDirectives.TruffleBoundary
                    public Object access(TopScopeObject ts, String name) {
                        if ("debugger".equals(name)) {
                            return ts.context != null;
                        } else {
                            throw UnknownIdentifierException.raise(name);
                        }
                    }
                }
            }
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
        protected OptionDescriptors getOptionDescriptors() {
            return OptionDescriptors.create(Collections.singletonList(OptionDescriptor.newBuilder(debuggerKey, ID + ".dbg").build()));
        }

    }

}
