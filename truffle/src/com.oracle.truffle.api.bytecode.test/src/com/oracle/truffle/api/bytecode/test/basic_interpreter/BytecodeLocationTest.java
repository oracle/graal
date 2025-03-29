/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class BytecodeLocationTest extends AbstractBasicInterpreterTest {

    public BytecodeLocationTest(TestRun run) {
        super(run);
    }

    @Test
    public void testGetBytecodeLocation() {
        Source source = Source.newBuilder("test", "getBytecodeLocation", "baz").build();
        BasicInterpreter root = parseNode("getBytecodeLocation", b -> {
            // @formatter:off
            // collectBcis
            b.beginSource(source);
            b.beginSourceSection(0, "getBytecodeLocation".length());
            b.beginRoot();
                b.beginReturn();
                b.emitGetBytecodeLocation();
                b.endReturn();
            b.endRoot();
            b.endSourceSection();
            b.endSource();
        });

        BytecodeLocation location = (BytecodeLocation) root.getCallTarget().call();
        SourceSection section = location.ensureSourceInformation().getSourceLocation();
        assertSame(source, section.getSource());
        assertEquals("getBytecodeLocation", section.getCharacters());
    }


    @Test
    public void testStacktrace() {
        /**
         * @formatter:off
         * def baz(arg0) {
         *   if (arg0) <trace1> else <trace2>  // directly returns a trace
         * }
         *
         * def bar(arg0) {
         *   baz(arg0)
         * }
         *
         * def foo(arg0) {
         *   c = bar(arg0);
         *   c
         * }
         * @formatter:on
         */

        Source bazSource = Source.newBuilder("test", "if (arg0) <trace1> else <trace2>", "baz").build();
        Source barSource = Source.newBuilder("test", "baz(arg0)", "bar").build();
        Source fooSource = Source.newBuilder("test", "c = bar(arg0); c", "foo").build();
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.WITH_SOURCE, b -> {
            // @formatter:off
            // collectBcis
            b.beginRoot();
                b.beginReturn();
                b.emitCollectBytecodeLocations();
                b.endReturn();
            BasicInterpreter collectBcis = b.endRoot();
            collectBcis.setName("collectBcis");

            // baz
            b.beginRoot();
                b.beginSource(bazSource);
                b.beginBlock();

                b.beginIfThenElse();

                b.emitLoadArgument(0);

                b.beginReturn();
                b.beginSourceSection(10, 8);
                b.beginInvoke();
                b.emitLoadConstant(collectBcis);
                b.endInvoke();
                b.endSourceSection();
                b.endReturn();

                b.beginReturn();
                b.beginSourceSection(24, 8);
                b.beginInvoke();
                b.emitLoadConstant(collectBcis);
                b.endInvoke();
                b.endSourceSection();
                b.endReturn();

                b.endIfThenElse();

                b.endBlock();
                b.endSource();
            BasicInterpreter baz = b.endRoot();
            baz.setName("baz");

            // bar
            b.beginRoot();
                b.beginSource(barSource);

                b.beginReturn();

                b.beginSourceSection(0, 9);
                b.beginInvoke();
                b.emitLoadConstant(baz);
                b.emitLoadArgument(0);
                b.endInvoke();
                b.endSourceSection();

                b.endReturn();

                b.endSource();
            BasicInterpreter bar = b.endRoot();
            bar.setName("bar");

            // foo
            b.beginRoot();
                b.beginSource(fooSource);
                b.beginBlock();
                BytecodeLocal c = b.createLocal();

                b.beginSourceSection(0, 13);
                b.beginStoreLocal(c);
                b.beginSourceSection(4, 9);
                b.beginInvoke();
                b.emitLoadConstant(bar);
                b.emitLoadArgument(0);
                b.endInvoke();
                b.endSourceSection();
                b.endStoreLocal();
                b.endSourceSection();

                b.beginReturn();
                b.beginSourceSection(15, 1);
                b.emitLoadLocal(c);
                b.endSourceSection();
                b.endReturn();

                b.endBlock();
                b.endSource();
            BasicInterpreter foo = b.endRoot();
            foo.setName("foo");
        });

        List<BasicInterpreter> nodeList = nodes.getNodes();
        assert nodeList.size() == 4;
        BasicInterpreter foo = nodeList.get(3);
        assert foo.getName().equals("foo");
        BasicInterpreter bar = nodeList.get(2);
        assert bar.getName().equals("bar");
        BasicInterpreter baz = nodeList.get(1);
        assert baz.getName().equals("baz");

        for (boolean fooArgument : List.of(true, false)) {
            Object result = foo.getCallTarget().call(fooArgument);
            assertTrue(result instanceof List<?>);

            @SuppressWarnings("unchecked")
            List<BytecodeLocation> bytecodeLocations = (List<BytecodeLocation>) result;

            assertEquals(4, bytecodeLocations.size());

            // skip the helper

            // baz
            BytecodeLocation bazLocation = bytecodeLocations.get(1);
            assertNotNull(bazLocation);
            SourceSection bazSourceSection = bazLocation.getSourceLocation();
            assertEquals(bazSource, bazSourceSection.getSource());
            if (fooArgument) {
                assertEquals("<trace1>", bazSourceSection.getCharacters());
            } else {
                assertEquals("<trace2>", bazSourceSection.getCharacters());
            }

            // bar
            BytecodeLocation barLocation = bytecodeLocations.get(2);
            assertNotNull(barLocation);
            SourceSection barSourceSection = barLocation.getSourceLocation();
            assertEquals(barSource, barSourceSection.getSource());
            assertEquals("baz(arg0)", barSourceSection.getCharacters());

            // foo
            BytecodeLocation fooLocation = bytecodeLocations.get(3);
            assertNotNull(fooLocation);
            SourceSection fooSourceSection = fooLocation.getSourceLocation();
            assertEquals(fooSource, fooSourceSection.getSource());
            assertEquals("bar(arg0)", fooSourceSection.getCharacters());
        }
    }

    @Test
    public void testStacktraceWithContinuation() {
        /**
         * @formatter:off
         * def baz(arg0) {
         *   if (arg0) <trace1> else <trace2>  // directly returns a trace
         * }
         *
         * def bar() {
         *   x = yield 1;
         *   baz(x)
         * }
         *
         * def foo(arg0) {
         *   c = bar();
         *   continue(c, arg0)
         * }
         * @formatter:on
         */
        Source bazSource = Source.newBuilder("test", "if (arg0) <trace1> else <trace2>", "baz").build();
        Source barSource = Source.newBuilder("test", "x = yield 1; baz(x)", "bar").build();
        Source fooSource = Source.newBuilder("test", "c = bar(); continue(c, arg0)", "foo").build();
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.WITH_SOURCE, b -> {
            // @formatter:off
            // collectBcis
            b.beginRoot();
                b.beginReturn();
                b.emitCollectBytecodeLocations();
                b.endReturn();
            BasicInterpreter collectBcis = b.endRoot();
            collectBcis.setName("collectBcis");

            // baz
            b.beginRoot();
                b.beginSource(bazSource);
                b.beginBlock();

                b.beginIfThenElse();

                b.emitLoadArgument(0);

                b.beginReturn();
                b.beginSourceSection(10, 8);
                b.beginInvoke();
                b.emitLoadConstant(collectBcis);
                b.endInvoke();
                b.endSourceSection();
                b.endReturn();

                b.beginReturn();
                b.beginSourceSection(24, 8);
                b.beginInvoke();
                b.emitLoadConstant(collectBcis);
                b.endInvoke();
                b.endSourceSection();
                b.endReturn();

                b.endIfThenElse();

                b.endBlock();
                b.endSource();
            BasicInterpreter baz = b.endRoot();
            baz.setName("baz");

            // bar
            b.beginRoot();
                b.beginSource(barSource);
                b.beginBlock();
                BytecodeLocal x = b.createLocal();

                b.beginStoreLocal(x);
                b.beginYield();
                b.emitLoadConstant(1L);
                b.endYield();
                b.endStoreLocal();

                b.beginReturn();
                b.beginSourceSection(13, 6);
                b.beginInvoke();
                b.emitLoadConstant(baz);
                b.emitLoadLocal(x);
                b.endInvoke();
                b.endSourceSection();
                b.endReturn();

                b.endBlock();
                b.endSource();
            BasicInterpreter bar = b.endRoot();
            bar.setName("bar");

            // foo
            b.beginRoot();
                b.beginSource(fooSource);
                b.beginBlock();

                BytecodeLocal c = b.createLocal();

                b.beginStoreLocal(c);
                b.beginInvoke();
                b.emitLoadConstant(bar);
                b.endInvoke();
                b.endStoreLocal();

                b.beginReturn();
                b.beginSourceSection(11, 17);
                b.beginContinue();
                b.emitLoadLocal(c);
                b.emitLoadArgument(0);
                b.endContinue();
                b.endSourceSection();
                b.endReturn();

                b.endBlock();
                b.endSource();
            BasicInterpreter foo = b.endRoot();
            foo.setName("foo");
            // @formatter:off
        });

        List<BasicInterpreter> nodeList = nodes.getNodes();
        assertEquals(4, nodeList.size());
        BasicInterpreter foo = nodeList.get(3);
        assertEquals("foo", foo.getName());
        BasicInterpreter bar = nodeList.get(2);
        assertEquals("bar", bar.getName());
        BasicInterpreter baz = nodeList.get(1);
        assertEquals("baz", baz.getName());

        for (boolean continuationArgument : List.of(true, false)) {
            Object result = foo.getCallTarget().call(continuationArgument);
            assertTrue(result instanceof List<?>);

            @SuppressWarnings("unchecked")
            List<BytecodeLocation> locations = (List<BytecodeLocation>) result;
            assertEquals(4, locations.size());

            // skip the helper

            // baz
            BytecodeLocation bazLocation = locations.get(1);
            assertNotNull(bazLocation);
            SourceSection bazSourceSection = bazLocation.getSourceLocation();
            assertEquals(bazSource, bazSourceSection.getSource());
            if (continuationArgument) {
                assertEquals("<trace1>", bazSourceSection.getCharacters());
            } else {
                assertEquals("<trace2>", bazSourceSection.getCharacters());
            }

            // bar
            BytecodeLocation barLocation = locations.get(2);
            assertNotNull(barLocation);
            SourceSection barSourceSection = barLocation.getSourceLocation();
            assertEquals(barSource, barSourceSection.getSource());
            assertEquals("baz(x)", barSourceSection.getCharacters());

            // foo
            BytecodeLocation fooLocation = locations.get(3);
            assertNotNull(fooLocation);
            SourceSection fooSourceSection = fooLocation.getSourceLocation();
            assertEquals(fooSource, fooSourceSection.getSource());
            assertEquals("continue(c, arg0)", fooSourceSection.getCharacters());
        }
    }
}
