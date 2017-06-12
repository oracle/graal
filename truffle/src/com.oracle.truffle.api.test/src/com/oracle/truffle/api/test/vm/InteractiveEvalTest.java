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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

public class InteractiveEvalTest {

    @Test
    public void testDefaultInteractiveLanguage() throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(out).build();
        Source s = Source.newBuilder("").mimeType("application/x-test-definteract").name("definteract").interactive().build();
        PolyglotEngine.Value value = engine.eval(s);
        Assert.assertEquals("42", value.get());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertEquals("42" + System.getProperty("line.separator"), strOutput);
    }

    @Test
    public void testDefaultInteractiveLanguageDirectly() throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(out).build();
        Source s = Source.newBuilder("").mimeType("application/x-test-definteract").name("definteract").interactive().build();
        PolyglotEngine.Value value = engine.getLanguages().get("application/x-test-definteract").eval(s);
        Assert.assertEquals("42", value.get());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertEquals("42" + System.getProperty("line.separator"), strOutput);
    }

    @Test
    public void testSpecialInteractiveLanguage() throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(out).build();
        Source s = Source.newBuilder("").mimeType("application/x-test-specinteract").name("specinteract").interactive().build();
        PolyglotEngine.Value value = engine.eval(s);
        Assert.assertEquals("42", value.get());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertEquals("\"42\"", strOutput);
    }

    @Test
    public void testSpecialInteractiveLanguageDirectly() throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(out).build();
        Source s = Source.newBuilder("").mimeType("application/x-test-specinteract").name("specinteract").interactive().build();
        PolyglotEngine.Value value = engine.getLanguages().get("application/x-test-specinteract").eval(s);
        Assert.assertEquals("42", value.get());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertEquals("\"42\"", strOutput);
    }

    @Test
    public void testDefaultNoninteractiveLanguage() throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(out).build();
        Source s = Source.newBuilder("").mimeType("application/x-test-definteract").name("defnoninteract").build();
        PolyglotEngine.Value value = engine.eval(s);
        Assert.assertEquals("42", value.get());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertTrue(strOutput.isEmpty());
    }

    @Test
    public void testSpecialNoninteractiveLanguage() throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(out).build();
        Source s = Source.newBuilder("").mimeType("application/x-test-specinteract").name("specnoninteract").build();
        PolyglotEngine.Value value = engine.eval(s);
        Assert.assertEquals("42", value.get());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertTrue(strOutput.isEmpty());
    }

    @Test
    public void isInteractive1() {
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();
        PolyglotEngine.Language language = engine.getLanguages().get("application/x-test-specinteract");
        assertFalse("SpecialInteractive language isn't interactive", language.isInteractive());
    }

    @Test
    public void isInteractive2() {
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();
        PolyglotEngine.Language language = engine.getLanguages().get("application/x-test-definteract");
        assertTrue("DefaultInteractive language is interactive", language.isInteractive());
    }

    @Test
    public void isInteractive3() {
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();
        PolyglotEngine.Language language = engine.getLanguages().get("application/x-test-async");
        assertTrue("By default a language is interactive", language.isInteractive());
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

    @TruffleLanguage.Registration(name = "DefaultInteractive", mimeType = "application/x-test-definteract", version = "1.0")
    public static class DefaultInteractiveLanguage extends TruffleLanguage<InteractiveContext> {

        @Override
        protected InteractiveContext createContext(Env env) {
            return new InteractiveContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return getContextReference().get().getValue();
                }
            });
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

    @TruffleLanguage.Registration(name = "SpecialInteractive", mimeType = "application/x-test-specinteract", version = "1.0", interactive = false)
    public static class SpecialInteractiveLanguage extends TruffleLanguage<InteractiveContext> {

        @Override
        protected InteractiveContext createContext(Env env) {
            return new InteractiveContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            Source code = request.getSource();
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    InteractiveContext ic = getContextReference().get();
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
        protected boolean isVisible(InteractiveContext context, Object value) {
            return false;
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
