/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.test.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.api.vm.PolyglotRuntime;
import static org.junit.Assert.assertNull;

public class ContextLookupTest {

    protected PolyglotEngine.Builder createBuilder() {
        return PolyglotEngine.newBuilder();
    }

    @Test
    public void basicContextLookup() throws Exception {
        LanguageLookupContext context = new LanguageLookupContext(null);
        PolyglotEngine vm = createBuilder().config(LanguageLookup.MIME_TYPE, "channel", context).build();
        vm.getLanguages().get(LanguageLookup.MIME_TYPE).getGlobalObject();
        LanguageLookup language = context.language;
        assertExpectedContext(vm, language, context);
        vm.dispose();
    }

    private static void assertExpectedContext(PolyglotEngine vm, LanguageLookup language, LanguageLookupContext expectedContext) {
        Source s1 = Source.newBuilder("assertContext").name("").mimeType(LanguageLookup.MIME_TYPE).build();
        Value result = vm.getLanguages().get(LanguageLookup.MIME_TYPE).eval(s1);
        LanguageLookupContext prevContext = language.expectedContext;
        language.expectedContext = expectedContext;

        // trying to exercise all TruffleLanguage API

        result.execute();

        vm.eval(s1);

        result.getMetaObject();

        result.getSourceLocation();

        assertEquals("something", result.as(String.class));

        Source s2 = Source.newBuilder("").name("").mimeType(LanguageLookup.MIME_TYPE).interactive().build();
        vm.eval(s2);

        vm.findGlobalSymbol("");

        vm.getLanguages().get(LanguageLookup.MIME_TYPE).getGlobalObject();

        language.expectedContext = prevContext;
    }

    @Test
    public void forkedLookupTest() throws Exception {
        LanguageLookupContext context = new LanguageLookupContext(null);
        final PolyglotEngine.Builder builder = createBuilder().config(LanguageLookup.MIME_TYPE, "channel", context);
        builder.runtime(PolyglotRuntime.newBuilder().build());
        PolyglotEngine vm = builder.build();
        vm.getLanguages().get(LanguageLookup.MIME_TYPE).getGlobalObject();
        LanguageLookup language = context.language;
        assertExpectedContext(vm, language, context);

        for (int i = 0; i < 5; i++) {
            context.toFork = context;
            PolyglotEngine ie = fork(context, context, builder);
            language.expectedFinal = false;
            final LanguageLookupContext subContext = context.forks.get(0);
            for (int j = 0; j < 5; j++) {
                PolyglotEngine je = fork(context, subContext, builder);
                assertExpectedContext(je, language, context.forks.get(0).forks.get(0));
                je.dispose();
            }
            assertExpectedContext(ie, language, context.forks.get(0));
            ie.dispose();
        }
        vm.dispose();
    }

    private PolyglotEngine fork(LanguageLookupContext original, LanguageLookupContext context, PolyglotEngine.Builder builder) {
        original.toFork = context;
        PolyglotEngine engine = builder.build();
        engine.getLanguages().get(LanguageLookup.MIME_TYPE).getGlobalObject();
        assertNull("Fork used", original.toFork);
        return engine;
    }

    @Test
    public void invalidLookup() throws Exception {
        LanguageLookupContext context = new LanguageLookupContext(null);
        PolyglotEngine vm = createBuilder().config(LanguageLookup.MIME_TYPE, "channel", context).build();
        vm.getLanguages().get(LanguageLookup.MIME_TYPE).getGlobalObject();

        try {
            // using an exposed context reference outside of PE does not work
            context.language.sharedChannelRef.get();
            fail();
        } catch (IllegalStateException e) {
        }

        try {
            // but you can create context references outside
            context.language.getContextReference();
        } catch (IllegalStateException e) {
        }

        try {
            // creating a context reference in the constructor does not work
            context.language.getContextReference().get();
            fail();
        } catch (IllegalStateException e) {
            // illegal state expected. context not yet initialized
        }

    }

    private static class LanguageLookupContext {

        LanguageLookup language;

        final LanguageLookupContext parent;
        final List<LanguageLookupContext> forks = new ArrayList<>();
        LanguageLookupContext toFork;

        LanguageLookupContext(LanguageLookupContext parent) {
            this.parent = parent;
        }

    }

    @TruffleLanguage.Registration(mimeType = LanguageLookup.MIME_TYPE, version = "", name = "")
    public static final class LanguageLookup extends TruffleLanguage<LanguageLookupContext> {

        static final String MIME_TYPE = "application/x-test-language-lookup";

        public LanguageLookup() {
            try {
                // creating a context reference in the constructr does not work
                getContextReference();
                fail();
            } catch (IllegalStateException e) {
                // illegal state expected. context not yet initialized
            }
        }

        ContextReference<LanguageLookupContext> sharedChannelRef;
        LanguageLookupContext expectedContext;
        boolean expectedFinal = true;

        @Override
        protected LanguageLookupContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            LanguageLookupContext channel = (LanguageLookupContext) env.getConfig().get("channel");
            if (channel.toFork != null) {
                LanguageLookupContext forking = channel.toFork;
                channel.toFork = null;
                return forkContext(forking);
            }
            channel.language = this;

            try {
                getContextReference().get();
                fail();
            } catch (IllegalStateException e) {
                // illegal state expected. context not yet initialized
            }

            // create context reference alone should be fine.
            ContextReference<LanguageLookupContext> channelRef = getContextReference();

            try {
                // getting the reference without initalization is not.
                channelRef.get();
                fail();
            } catch (IllegalStateException e) {
                // illegal state expected. context not yet initialized
            }

            return channel;
        }

        @Override
        protected void initializeContext(LanguageLookupContext context) throws Exception {
            assertContext(context);
            sharedChannelRef = getContextReference();
            super.initializeContext(context);
        }

        private void assertContext(LanguageLookupContext context) {
            LanguageLookupContext expected = this.expectedContext;
            if (expected == null) {
                expected = context;
            }
            assertSame(expected, context);
            assertSame(expected, getContextReference().get());
            // assertEquals(expectedFinal, getContextReference().isFinal());
        }

        @Override
        protected Object findMetaObject(LanguageLookupContext context, Object value) {
            assertContext(context);
            return super.findMetaObject(context, value);
        }

        @Override
        protected SourceSection findSourceLocation(LanguageLookupContext context, Object value) {
            assertContext(context);
            return super.findSourceLocation(context, value);
        }

        protected LanguageLookupContext forkContext(LanguageLookupContext context) {
            LanguageLookupContext channel = new LanguageLookupContext(context);
            channel.language = this;
            context.forks.add(channel);
            return channel;
        }

        @Override
        protected void disposeContext(LanguageLookupContext context) {
            assertContext(context);
            if (context.parent != null) {
                context.parent.forks.remove(context);
            }
        }

        @Override
        protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
            assertContext(getContextReference().get());
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    assertContext(getContextReference().get());
                    TruffleObject o = (TruffleObject) JavaInterop.asTruffleValue(new Runnable() {
                        public void run() {
                            assertContext(expectedContext);
                        }
                    });
                    try {
                        return ForeignAccess.sendRead(Message.READ.createNode(), o, "run");
                    } catch (UnknownIdentifierException e) {
                        throw new AssertionError();
                    } catch (UnsupportedMessageException e) {
                        throw new AssertionError();
                    }
                }
            });
        }

        @Override
        protected Object findExportedSymbol(LanguageLookupContext context, String globalName, boolean onlyExplicit) {
            assertContext(context);
            return null;
        }

        @Override
        protected Object getLanguageGlobal(LanguageLookupContext context) {
            assertContext(context);
            return null;
        }

        @Override
        protected boolean isVisible(LanguageLookupContext context, Object value) {
            assertContext(context);
            return false;
        }

        @Override
        protected String toString(LanguageLookupContext context, Object value) {
            assertContext(context);
            return "something";
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }
    }

}
