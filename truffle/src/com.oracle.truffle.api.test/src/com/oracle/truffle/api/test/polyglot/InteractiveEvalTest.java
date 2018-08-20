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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class InteractiveEvalTest {

    static final String DEFAULT_INTERACTIVE = "DefaultInteractive";
    static final String SPECIAL_INTERACTIVE = "SpecialInteractive";

    private ByteArrayOutputStream out;
    private Context context;

    @Before
    public void setup() {
        out = new ByteArrayOutputStream();
        context = Context.newBuilder().out(out).build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testDefaultInteractiveLanguage() throws UnsupportedEncodingException {
        Source s = Source.newBuilder(DEFAULT_INTERACTIVE, "", "").interactive(true).buildLiteral();
        Value value = context.eval(s);
        Assert.assertEquals("42", value.asString());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertEquals("42" + System.getProperty("line.separator"), strOutput);
    }

    @Test
    public void testSpecialInteractiveLanguage() throws UnsupportedEncodingException {
        Source s = Source.newBuilder(SPECIAL_INTERACTIVE, "", "").interactive(true).buildLiteral();
        Value value = context.eval(s);
        Assert.assertEquals("42", value.asString());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertEquals("\"42\"", strOutput);
    }

    @Test
    public void testDefaultNoninteractiveLanguage() throws UnsupportedEncodingException {
        Source s = Source.newBuilder(DEFAULT_INTERACTIVE, "", "defnoninteract").interactive(false).buildLiteral();
        Value value = context.eval(s);
        Assert.assertEquals("42", value.asString());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertTrue(strOutput.isEmpty());
    }

    @Test
    public void testSpecialNoninteractiveLanguage() throws UnsupportedEncodingException {
        Source s = Source.newBuilder(SPECIAL_INTERACTIVE, "", "").interactive(false).buildLiteral();
        Value value = context.eval(s);
        Assert.assertEquals("42", value.asString());
        String strOutput = out.toString(StandardCharsets.UTF_8.name());
        Assert.assertTrue(strOutput.isEmpty());
    }

    @Test
    public void isInteractive1() {
        assertFalse(context.getEngine().getLanguages().get(SPECIAL_INTERACTIVE).isInteractive());
        assertTrue(context.getEngine().getLanguages().get(DEFAULT_INTERACTIVE).isInteractive());
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

    @TruffleLanguage.Registration(id = DEFAULT_INTERACTIVE, name = "DefaultInteractive", characterMimeTypes = "application/x-test-definteract", version = "1.0")
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
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }
    }

    @TruffleLanguage.Registration(id = SPECIAL_INTERACTIVE, name = "SpecialInteractive", characterMimeTypes = "application/x-test-specinteract", version = "1.0", interactive = false)
    public static class SpecialInteractiveLanguage extends TruffleLanguage<InteractiveContext> {

        @Override
        protected InteractiveContext createContext(Env env) {
            return new InteractiveContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            boolean interactive = request.getSource().isInteractive();
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Override
                public Object execute(VirtualFrame frame) {
                    InteractiveContext ic = getContextReference().get();
                    Object value = ic.getValue();
                    if (interactive) {
                        try {
                            write(ic, value);
                        } catch (IOException ioex) {
                            return ioex;
                        }
                    }
                    return value;
                }

                @TruffleBoundary
                private void write(InteractiveContext ic, Object value) throws IOException {
                    ic.env.out().write(("\"" + value + "\"").getBytes(StandardCharsets.UTF_8));
                }
            });
        }

        @Override
        protected boolean isVisible(InteractiveContext context, Object value) {
            return false;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }
    }

}
