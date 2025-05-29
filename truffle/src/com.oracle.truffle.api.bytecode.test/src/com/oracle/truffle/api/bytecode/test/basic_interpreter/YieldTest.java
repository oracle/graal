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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeTier;

public class YieldTest extends AbstractBasicInterpreterTest {

    public YieldTest(TestRun run) {
        super(run);
    }

    @Test
    public void testYield() {
        // yield 1;
        // yield 2;
        // return 3;

        RootCallTarget root = parse("yield", b -> {
            b.beginRoot();

            b.beginYield();
            b.emitLoadConstant(1L);
            b.endYield();

            b.beginYield();
            b.emitLoadConstant(2L);
            b.endYield();

            emitReturn(b, 3);

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call();
        assertEquals(1L, r1.getResult());

        ContinuationResult r2 = (ContinuationResult) r1.continueWith(42L);
        assertEquals(2L, r2.getResult());

        assertEquals(3L, r2.continueWith(null));
    }

    @Test
    public void testYieldLocal() {
        // local = 0;
        // yield local;
        // local = local + 1;
        // yield local;
        // local = local + 1;
        // return local;

        RootCallTarget root = parse("yieldLocal", b -> {
            b.beginRoot();
            BytecodeLocal local = b.createLocal();

            b.beginStoreLocal(local);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginYield();
            b.emitLoadLocal(local);
            b.endYield();

            b.beginStoreLocal(local);
            b.beginAdd();
            b.emitLoadLocal(local);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            b.beginYield();
            b.emitLoadLocal(local);
            b.endYield();

            b.beginStoreLocal(local);
            b.beginAdd();
            b.emitLoadLocal(local);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call();
        assertEquals(0L, r1.getResult());

        ContinuationResult r2 = (ContinuationResult) r1.continueWith(null);
        assertEquals(1L, r2.getResult());

        assertEquals(2L, r2.continueWith(null));
    }

    @Test
    public void testYieldTee() {
        // yield tee(local, 1);
        // yield tee(local, local + 1);
        // return local + 1;

        // Unlike with testYieldLocal, the local here is set using a LocalAccessor in a custom
        // operation. The localFrame should be passed to the custom operation (as opposed to the
        // frame containing the stack locals).

        RootCallTarget root = parse("yieldTee", b -> {
            b.beginRoot();
            BytecodeLocal local = b.createLocal();

            b.beginYield();
            b.beginTeeLocal(local);
            b.emitLoadConstant(1L);
            b.endTeeLocal();
            b.endYield();

            b.beginYield();
            b.beginTeeLocal(local);
            b.beginAdd();
            b.emitLoadLocal(local);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endTeeLocal();
            b.endYield();

            b.beginReturn();
            b.beginAdd();
            b.emitLoadLocal(local);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endReturn();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call();
        assertEquals(1L, r1.getResult());

        ContinuationResult r2 = (ContinuationResult) r1.continueWith(null);
        assertEquals(2L, r2.getResult());

        assertEquals(3L, r2.continueWith(null));
    }

    @Test
    public void testYieldStack() {
        // return (yield 1) + (yield 2);

        RootCallTarget root = parse("yieldStack", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAdd();

            b.beginYield();
            b.emitLoadConstant(1L);
            b.endYield();

            b.beginYield();
            b.emitLoadConstant(2L);
            b.endYield();

            b.endAdd();
            b.endReturn();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call();
        assertEquals(1L, r1.getResult());

        ContinuationResult r2 = (ContinuationResult) r1.continueWith(3L);
        assertEquals(2L, r2.getResult());

        assertEquals(7L, r2.continueWith(4L));
    }

    @Test
    public void testYieldFromFinally() {
        // @formatter:off
        // try {
        //   yield 1;
        //   if (false) {
        //     return 2;
        //   } else {
        //     return 3;
        //   }
        // } finally {
        //   yield 4;
        // }
        // @formatter:on

        RootCallTarget root = parse("yieldFromFinally", b -> {
            b.beginRoot();

            b.beginTryFinally(() -> {
                b.beginYield();
                b.emitLoadConstant(4L);
                b.endYield();
            });
            b.beginBlock();

            b.beginYield();
            b.emitLoadConstant(1L);
            b.endYield();

            b.beginIfThenElse();
            b.emitLoadConstant(false);
            emitReturn(b, 2);
            emitReturn(b, 3);
            b.endIfThenElse();

            b.endBlock();
            b.endTryFinally();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call();
        assertEquals(1L, r1.getResult());

        ContinuationResult r2 = (ContinuationResult) r1.continueWith(3L);
        assertEquals(4L, r2.getResult());

        assertEquals(3L, r2.continueWith(4L));
    }

    @Test
    public void testYieldUpdateArguments() {
        // yield arg0
        // return arg0

        // If we update arguments, the resumed code should see the updated value.
        RootCallTarget root = parse("yieldUpdateArguments", b -> {
            b.beginRoot();

            b.beginYield();
            b.emitLoadArgument(0);
            b.endYield();

            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call(42L);
        assertEquals(42L, r1.getResult());
        r1.getFrame().getArguments()[0] = 123L;
        assertEquals(123L, r1.continueWith(null));
    }

    @Test
    public void testYieldGetSourceRootNode() {
        BasicInterpreter rootNode = parseNode("yieldGetSourceRootNode", b -> {
            b.beginRoot();

            b.beginYield();
            b.emitLoadArgument(0);
            b.endYield();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) rootNode.getCallTarget().call(42L);
        if (r1.getContinuationCallTarget().getRootNode() instanceof ContinuationRootNode continuationRootNode) {
            BasicInterpreter sourceRootNode = (BasicInterpreter) continuationRootNode.getSourceRootNode();
            assertEquals(rootNode, sourceRootNode);
        } else {
            fail("yield did not return a continuation");
        }
    }

    @Test
    public void testYieldGetLocation() {
        BasicInterpreter rootNode = parseNode("yieldGetLocation", b -> {
            b.beginRoot();

            b.beginYield();
            b.emitCurrentLocation();
            b.endYield();

            b.beginReturn();
            b.emitCurrentLocation();
            b.endReturn();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) rootNode.getCallTarget().call();
        BytecodeLocation before = (BytecodeLocation) r1.getResult();

        if (!run.hasUncachedInterpreter()) {
            /**
             * Tricky behaviour: interpreters that don't have an uncached interpreter start with
             * uninitialized bytecode. Though rootNode will transition to cached on first execution,
             * the continuation's location will not be cached until after *its* first execution.
             */
            BytecodeLocation locationBeforeResume = r1.getBytecodeLocation();
            assertEquals(BytecodeTier.UNCACHED /* actually uninit */, locationBeforeResume.getBytecodeNode().getTier());
        }

        BytecodeLocation after = (BytecodeLocation) r1.continueWith(null);
        BytecodeLocation location = r1.getBytecodeLocation();

        assertEquals(before.getBytecodeNode(), location.getBytecodeNode());
        assertEquals(location.getBytecodeNode(), after.getBytecodeNode());
        assertTrue(before.getBytecodeIndex() < location.getBytecodeIndex());
        assertTrue(location.getBytecodeIndex() < after.getBytecodeIndex());
    }

    @Test
    public void testYieldReparseSources() {
        Source source = Source.newBuilder("test", "x = yield; return x ? 42 : position", "file").build();

        BasicInterpreter rootNode = parseNode("yieldReparseSources", b -> {
            b.beginSource(source);
            b.beginRoot();

            BytecodeLocal result = b.createLocal();
            b.beginStoreLocal(result);
            b.beginYield();
            b.emitLoadArgument(0);
            b.endYield();
            b.endStoreLocal();

            b.beginReturn();
            b.beginConditional();

            b.emitLoadLocal(result);

            b.emitLoadConstant(42L);

            b.beginSourceSection(27, 8);
            b.emitGetSourcePositions();
            b.endSourceSection();

            b.endConditional();
            b.endReturn();

            b.endRoot();
            b.endSource();
        });

        // Invoke the continuation once to transition to cached.
        ContinuationResult cont = (ContinuationResult) rootNode.getCallTarget().call(123L);
        assertEquals(42L, cont.continueWith(true));

        // A suspended invocation should transition.
        cont = (ContinuationResult) rootNode.getCallTarget().call(123L);
        rootNode.getRootNodes().ensureSourceInformation();
        SourceSection[] result = (SourceSection[]) cont.continueWith(false);
        assertEquals(1, result.length);
        assertEquals(source, result[0].getSource());
        assertEquals("position", result[0].getCharacters());

        // Subsequent invocations work as expected.
        cont = (ContinuationResult) rootNode.getCallTarget().call(123L);
        result = (SourceSection[]) cont.continueWith(false);
        assertEquals(1, result.length);
        assertEquals(source, result[0].getSource());
        assertEquals("position", result[0].getCharacters());
    }

    @Test
    public void testYieldTransitionToInstrumented() {
        BasicInterpreter rootNode = parseNode("yieldTransitionToInstrumented", b -> {
            b.beginRoot();

            BytecodeLocal result = b.createLocal();
            b.beginStoreLocal(result);
            b.beginYield();
            b.emitLoadArgument(0);
            b.endYield();
            b.endStoreLocal();

            b.beginReturn();
            b.beginIncrementValue();
            b.emitLoadLocal(result);
            b.endIncrementValue();
            b.endReturn();

            b.endRoot();
        });

        // Regular invocation succeeds.
        ContinuationResult cont = (ContinuationResult) rootNode.getCallTarget().call(123L);
        assertEquals(42L, cont.continueWith(42L));

        // A suspended invocation should transition.
        cont = (ContinuationResult) rootNode.getCallTarget().call(123L);
        rootNode.getRootNodes().update(createBytecodeConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build());
        assertEquals(43L, cont.continueWith(42L));

        // Subsequent invocations work as expected.
        cont = (ContinuationResult) rootNode.getCallTarget().call(123L);
        assertEquals(43L, cont.continueWith(42L));
    }

    @Test
    public void testYieldInstrumentBeforeTransitionToCached() {
        BasicInterpreter rootNode = parseNode("yieldInstrumentBeforeTransitionToCached", b -> {
            b.beginRoot();

            BytecodeLocal result = b.createLocal();
            b.beginStoreLocal(result);
            b.beginYield();
            b.emitLoadArgument(0);
            b.endYield();
            b.endStoreLocal();

            b.beginReturn();
            b.beginIncrementValue();
            b.emitLoadLocal(result);
            b.endIncrementValue();
            b.endReturn();

            b.endRoot();
        });

        // Instrument immediately, before transitioning to cached.
        rootNode.getRootNodes().update(createBytecodeConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build());

        ContinuationResult cont = (ContinuationResult) rootNode.getCallTarget().call(123L);
        assertEquals(43L, cont.continueWith(42L));
    }

    @Test
    public void testYieldTransitionToInstrumentedInsideContinuation() {
        BasicInterpreter rootNode = parseNode("yieldTransitionToInstrumentedInsideContinuation", b -> {
            b.beginRoot();

            BytecodeLocal result = b.createLocal();
            b.beginStoreLocal(result);
            b.beginYield();
            b.beginIncrementValue();
            b.emitLoadArgument(0);
            b.endIncrementValue();
            b.endYield();
            b.endStoreLocal();

            b.emitEnableIncrementValueInstrumentation();

            b.beginReturn();
            b.beginIncrementValue();
            b.emitLoadLocal(result);
            b.endIncrementValue();
            b.endReturn();

            b.endRoot();
        });

        ContinuationResult cont = (ContinuationResult) rootNode.getCallTarget().call(123L);
        assertEquals(123L, cont.getResult());
        assertEquals(43L, cont.continueWith(42L));

        // After the first iteration, the first IncrementValue instrumentation should change the
        // yielded value.
        cont = (ContinuationResult) rootNode.getCallTarget().call(123L);
        assertEquals(124L, cont.getResult());
        assertEquals(43L, cont.continueWith(42L));
    }

    @Test
    public void testYieldTransitionToInstrumentedInsideContinuationTwice() {
        BasicInterpreter rootNode = parseNode("yieldTransitionToInstrumentedInsideContinuationTwice", b -> {
            b.beginRoot();

            BytecodeLocal result = b.createLocal();
            b.beginStoreLocal(result);
            b.beginYield();
            b.beginIncrementValue();
            b.beginDoubleValue();
            b.emitLoadArgument(0);
            b.endDoubleValue();
            b.endIncrementValue();
            b.endYield();
            b.endStoreLocal();

            b.emitEnableIncrementValueInstrumentation();
            b.beginStoreLocal(result);
            b.beginIncrementValue();
            b.emitLoadLocal(result);
            b.endIncrementValue();
            b.endStoreLocal();

            b.emitEnableDoubleValueInstrumentation();
            b.beginReturn();
            b.beginDoubleValue();
            b.emitLoadLocal(result);
            b.endDoubleValue();
            b.endReturn();

            b.endRoot();
        });

        ContinuationResult cont = (ContinuationResult) rootNode.getCallTarget().call(123L);
        assertEquals(123L, cont.getResult());
        assertEquals(42L, cont.continueWith(20L));

        // After the first iteration, the instrumentations should change the yielded value.
        cont = (ContinuationResult) rootNode.getCallTarget().call(10L);
        assertEquals(21L, cont.getResult());
        assertEquals(42L, cont.continueWith(20L));
    }

    @Test
    public void testYieldFromFinallyInstrumented() {
        // @formatter:off
        // try {
        //   yield 1;
        //   if (arg0) {
        //     return 2;
        //   } else {
        //     return 3;
        //   }
        // } finally {
        //   yield 4;
        // }
        // @formatter:on

        BasicInterpreter rootNode = parseNode("yieldFromFinally", b -> {
            b.beginRoot();

            b.beginTryFinally(() -> {
                b.beginYield();
                b.beginIncrementValue();
                b.emitLoadConstant(4L);
                b.endIncrementValue();
                b.endYield();
            });

            b.beginBlock();

            b.beginYield();
            b.beginIncrementValue();
            b.emitLoadConstant(1L);
            b.endIncrementValue();
            b.endYield();

            b.beginIfThenElse();
            b.emitLoadArgument(0);
            emitReturn(b, 2);
            emitReturn(b, 3);
            b.endIfThenElse();

            b.endBlock();
            b.endTryFinally();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) rootNode.getCallTarget().call(true);
        assertEquals(1L, r1.getResult());
        ContinuationResult r2 = (ContinuationResult) r1.continueWith(3L);
        assertEquals(4L, r2.getResult());
        assertEquals(2L, r2.continueWith(4L));

        r1 = (ContinuationResult) rootNode.getCallTarget().call(false);
        assertEquals(1L, r1.getResult());
        r2 = (ContinuationResult) r1.continueWith(3L);
        assertEquals(4L, r2.getResult());
        assertEquals(3L, r2.continueWith(4L));

        rootNode.getRootNodes().update(createBytecodeConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build());

        r1 = (ContinuationResult) rootNode.getCallTarget().call(true);
        assertEquals(2L, r1.getResult());
        r2 = (ContinuationResult) r1.continueWith(3L);
        assertEquals(5L, r2.getResult());
        assertEquals(2L, r2.continueWith(4L));

        r1 = (ContinuationResult) rootNode.getCallTarget().call(false);
        assertEquals(2L, r1.getResult());
        r2 = (ContinuationResult) r1.continueWith(3L);
        assertEquals(5L, r2.getResult());
        assertEquals(3L, r2.continueWith(4L));
    }
}
