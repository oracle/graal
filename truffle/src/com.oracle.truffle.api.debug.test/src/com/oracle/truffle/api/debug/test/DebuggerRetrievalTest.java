/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

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
        Context.newBuilder().allowExperimentalOptions(true).option(InstrumentThatNeedsDebugger.ID + ".dbg", "").build();
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

        @ExportLibrary(InteropLibrary.class)
        @SuppressWarnings({"unused", "static-method"})
        static final class TopScopeObject implements TruffleObject {

            private final Debugger context;

            private TopScopeObject(Debugger context) {
                this.context = context;
            }

            @ExportMessage
            boolean hasMembers() {
                return true;
            }

            @ExportMessage
            boolean isMemberReadable(String member) {
                return "debugger".equals(member);
            }

            @ExportMessage
            Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
                throw UnsupportedMessageException.create();
            }

            @ExportMessage
            Object readMember(@SuppressWarnings("unused") String member) throws UnknownIdentifierException {
                if ("debugger".equals(member)) {
                    return context != null;
                } else {
                    throw UnknownIdentifierException.create(member);
                }
            }
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
            haveDebugger = debugger != null;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return OptionDescriptors.create(Collections.singletonList(OptionDescriptor.newBuilder(debuggerKey, ID + ".dbg").build()));
        }

    }

}
