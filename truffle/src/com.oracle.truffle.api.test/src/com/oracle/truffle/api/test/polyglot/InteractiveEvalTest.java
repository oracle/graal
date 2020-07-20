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
                    return lookupContextReference(DefaultInteractiveLanguage.class).get().getValue();
                }
            });
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
                    InteractiveContext ic = lookupContextReference(SpecialInteractiveLanguage.class).get();
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

    }

}
