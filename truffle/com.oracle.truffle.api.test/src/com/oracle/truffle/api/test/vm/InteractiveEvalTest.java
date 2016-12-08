/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

public class InteractiveEvalTest {

    private static final String DEFAULT_INTERACT_MT = "application/x-test-definteract";
    private static final String SPECIAL_INTERACT_MT = "application/x-test-specinteract";

    @Test
    public void testDefaultInteractiveLanguage() throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(out).build();
        PolyglotEngine.Language language = engine.getLanguages().get(DEFAULT_INTERACT_MT);
        Assert.assertFalse(language.isInteractive());
        Source s = Source.newBuilder("").mimeType(DEFAULT_INTERACT_MT).name("definteract").interactive().build();
        PolyglotEngine.Value value = engine.eval(s);
        Assert.assertEquals("42", value.get());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertTrue(strOutput.isEmpty());
    }

    @Test
    public void testSpecialInteractiveLanguage() throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(out).build();
        Source s = Source.newBuilder("").mimeType(SPECIAL_INTERACT_MT).name("specinteract").interactive().build();
        PolyglotEngine.Language language = engine.getLanguages().get(SPECIAL_INTERACT_MT);
        Assert.assertTrue(language.isInteractive());
        PolyglotEngine.Value value = engine.eval(s);
        Assert.assertEquals("42", value.get());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertEquals("\"42\"", strOutput);
    }

    @Test
    public void testDefaultNoninteractiveLanguage() throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(out).build();
        Source s = Source.newBuilder("").mimeType(DEFAULT_INTERACT_MT).name("defnoninteract").build();
        PolyglotEngine.Value value = engine.eval(s);
        Assert.assertEquals("42", value.get());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertTrue(strOutput.isEmpty());
    }

    @Test
    public void testSpecialNoninteractiveLanguage() throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(out).build();
        Source s = Source.newBuilder("").mimeType(SPECIAL_INTERACT_MT).name("specnoninteract").build();
        PolyglotEngine.Value value = engine.eval(s);
        Assert.assertEquals("42", value.get());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertTrue(strOutput.isEmpty());
    }

    private static class InteractiveContext {

        final Env env;

        InteractiveContext(Env env) {
            this.env = env;
        }

        String getValue() {
            return "42";
        }
    }

    @TruffleLanguage.Registration(name = "DefaultInteractive", mimeType = DEFAULT_INTERACT_MT, version = "1.0")
    public static class DefaultInteractiveLanguage extends TruffleLanguage<InteractiveContext> {
        public static final DefaultInteractiveLanguage INSTANCE = new DefaultInteractiveLanguage();

        @Override
        protected InteractiveContext createContext(Env env) {
            return new InteractiveContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new RootNode(DefaultInteractiveLanguage.class, null, null) {

                @Child Node findContext = createFindContextNode();

                @Override
                public Object execute(VirtualFrame frame) {
                    return findContext(findContext).getValue();
                }
            });
        }

        @Override
        protected Object findExportedSymbol(InteractiveContext context, String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal(InteractiveContext context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }
    }

    @TruffleLanguage.Registration(name = "SpecialInteractive", mimeType = SPECIAL_INTERACT_MT, version = "1.0", interactive = true)
    public static class SpecialInteractiveLanguage extends TruffleLanguage<InteractiveContext> {
        public static final SpecialInteractiveLanguage INSTANCE = new SpecialInteractiveLanguage();

        @Override
        protected InteractiveContext createContext(Env env) {
            return new InteractiveContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            Source code = request.getSource();
            return Truffle.getRuntime().createCallTarget(new RootNode(DefaultInteractiveLanguage.class, null, null) {

                @Child Node findContext = createFindContextNode();

                @Override
                public Object execute(VirtualFrame frame) {
                    InteractiveContext ic = findContext(findContext);
                    Object value = ic.getValue();
                    if (code.isInteractive()) {
                        try {
                            ic.env.out().write(("\"" + value + "\"").getBytes(StandardCharsets.UTF_8));
                        } catch (IOException ioex) {
                            return ioex;
                        }
                    }
                    return value;
                }
            });
        }

        @Override
        protected Object findExportedSymbol(InteractiveContext context, String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal(InteractiveContext context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }
    }

}
