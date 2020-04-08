/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;
import org.graalvm.polyglot.PolyglotException;

public class TagsTest {

    private static class MyTag extends Tag {

    }

    @Tag.Identifier("MyTagWithIdentifier")
    private static class MyTagWithIdentifier extends Tag {

    }

    @Test
    public void testGetIdentifier() {
        assertEquals("EXPRESSION", Tag.getIdentifier(StandardTags.ExpressionTag.class));
        assertEquals("STATEMENT", Tag.getIdentifier(StandardTags.StatementTag.class));
        assertEquals("CALL", Tag.getIdentifier(StandardTags.CallTag.class));
        assertEquals("ROOT_BODY", Tag.getIdentifier(StandardTags.RootBodyTag.class));
        assertEquals("ROOT", Tag.getIdentifier(StandardTags.RootTag.class));
        assertNull(Tag.getIdentifier(MyTag.class));
        assertEquals("MyTagWithIdentifier", Tag.getIdentifier(MyTagWithIdentifier.class));
    }

    @Test
    public void testFindProvidedTags() {
        assertFails(() -> Tag.findProvidedTag(null, null), NullPointerException.class);
        assertFails(() -> Tag.findProvidedTag(null, ""), NullPointerException.class);

        Context context = Context.create("tagLanguage");
        context.initialize("tagLanguage");
        context.close();
    }

    private static void assertFails(Runnable r, Class<?> hostExceptionType) {
        try {
            r.run();
            Assert.fail("No error but expected " + hostExceptionType);
        } catch (Exception e) {
            if (!hostExceptionType.isInstance(e)) {
                throw new AssertionError(e.getClass().getName() + ":" + e.getMessage(), e);
            }
        }
    }

    @TruffleLanguage.Registration(id = "tagLanguage", name = "")
    @ProvidedTags({ExpressionTag.class, StatementTag.class})
    public static class TagLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            assertSame(ExpressionTag.class, Tag.findProvidedTag(env.getInternalLanguages().get("tagLanguage"), "EXPRESSION"));
            assertSame(StatementTag.class, Tag.findProvidedTag(env.getInternalLanguages().get("tagLanguage"), "STATEMENT"));
            assertFails(() -> Tag.findProvidedTag(env.getInternalLanguages().get("tagLanguage"), null), NullPointerException.class);
            assertNull(Tag.findProvidedTag(env.getInternalLanguages().get("tagLanguage"), "UNKNOWN_TAG"));
            return env;
        }

    }

    @Test
    public void testUndeclaredTagsLanguage() {
        ProxyInstrument instrument = new ProxyInstrument();
        ProxyInstrument.setDelegate(instrument);
        instrument.setOnCreate((env) -> {
            env.getInstrumenter().attachExecutionEventFactory(SourceSectionFilter.ANY, new ExecutionEventNodeFactory() {
                @Override
                public ExecutionEventNode create(EventContext context) {
                    try {
                        context.hasTag(MyTagWithIdentifier.class);
                        Assert.fail();
                    } catch (AssertionError err) {
                        String message = err.getMessage();
                        assertUndeclaredTagMessage(message, MyTagWithIdentifier.class);
                    }
                    return null;
                }
            });
        });
        Context context = Context.create();
        context.getEngine().getInstruments().get(ProxyInstrument.ID).lookup(ProxyInstrument.Initialize.class);
        try {
            context.eval(UndeclaredTagsLanguage.ID, "E");
            Assert.fail();
        } catch (PolyglotException ex) {
            String message = ex.getMessage();
            assertUndeclaredTagMessage(message, StandardTags.ExpressionTag.class);
        }
        context.eval(UndeclaredTagsLanguage.ID, "M");
    }

    private static void assertUndeclaredTagMessage(String message, Class<?> tag) {
        Assert.assertTrue(message, message.indexOf(UndeclaredTagsLanguage.ID) > 0);
        Assert.assertTrue(message, message.indexOf(ProvidedTags.class.getSimpleName()) > 0);
        Assert.assertTrue(message, message.indexOf(tag.getName()) > 0);
    }

    @TruffleLanguage.Registration(id = UndeclaredTagsLanguage.ID, name = "")
    @ProvidedTags({StatementTag.class})
    public static class UndeclaredTagsLanguage extends TruffleLanguage<Env> {

        static final String ID = "undeclaredTagsLanguage";

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Child private TaggedNode child = new TaggedNode(request.getSource().getCharacters().toString());

                @Override
                protected boolean isInstrumentable() {
                    return true;
                }

                @Override
                public Object execute(VirtualFrame frame) {
                    return child.execute(frame);
                }
            });
        }

        @GenerateWrapper
        static class TaggedNode extends Node implements InstrumentableNode {

            private final String code;

            TaggedNode(String code) {
                this.code = code;
            }

            TaggedNode(TaggedNode copy) {
                this.code = copy.code;
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new TaggedNodeWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag.equals(StandardTags.StatementTag.class) ||
                                "E".equals(code) && tag.equals(StandardTags.ExpressionTag.class) ||
                                "M".equals(code) && tag.equals(MyTagWithIdentifier.class);
            }

            boolean execute(@SuppressWarnings("unused") VirtualFrame frame) {
                return true;
            }
        }
    }
}
