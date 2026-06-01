/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

@SuppressWarnings("deprecation")
public class MaxHeapMemoryShapeTransitionTest {

    private static final String LANGUAGE_ID = "shape-transition-max-heap-memory-test";
    private static final String PHASE_1 = "phase-1";
    private static final String PHASE_2 = "phase-2";
    private static final long MEMORY_LIMIT = 2L * 1024L * 1024L;
    private static final int LONG_KEY_COUNT = 4;
    private static final int LONG_KEY_SIZE = 1024 * 1024;
    private static final int SMALL_KEY_COUNT = 100;

    private static volatile Object keepPhase1ObjectAlive;

    @Before
    public void before() {
        TruffleTestAssumptions.assumeOptimizingRuntime();
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void testSharedEngineContextDoesNotRetainWeakShapeTransitions() {
        try (Engine engine = Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build()) {
            long phase1RetainedSize = runPhase(engine, PHASE_1);
            assertTrue("Phase 1 expected context to exceed the heap limit", phase1RetainedSize > MEMORY_LIMIT);

            long phase2RetainedSize = runPhase(engine, PHASE_2);
            assertTrue("Phase 2 should retain some heap", phase2RetainedSize > 0);
            assertFalse("Phase 2 should not count weakly reachable shape transitions from phase 1", phase2RetainedSize > MEMORY_LIMIT);
        } finally {
            Reference.reachabilityFence(keepPhase1ObjectAlive);
            keepPhase1ObjectAlive = null;
        }
    }

    private static long runPhase(Engine engine, String phase) {
        try (Context context = Context.newBuilder(LANGUAGE_ID).engine(engine).build()) {
            context.enter();
            try {
                context.eval(LANGUAGE_ID, phase);
                return calculateRetainedSize(engine);
            } finally {
                context.leave();
            }
        }
    }

    private static long calculateRetainedSize(Engine engine) {
        try {
            var instrumentEnv = ProxyInstrument.findEnv(engine);
            return instrumentEnv.calculateContextHeapSize(instrumentEnv.getEnteredContext(), MEMORY_LIMIT, new AtomicBoolean(false));
        } catch (UnsupportedOperationException e) {
            Assume.assumeNoException("Heap memory limit is not supported on the current Truffle runtime.", e);
            throw e;
        }
    }

    @TruffleLanguage.Registration(id = LANGUAGE_ID, name = LANGUAGE_ID, version = "1.0", contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    public static final class ShapeTransitionLanguage extends TruffleLanguage<ShapeTransitionContext> {
        private final AtomicInteger keyCounter = new AtomicInteger();
        private static final Shape ROOT_SHAPE = Shape.newBuilder().build();

        @Override
        protected ShapeTransitionContext createContext(Env env) {
            return new ShapeTransitionContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) {
            String phase = request.getSource().getCharacters().toString();
            return new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    ShapeTransitionContext context = ShapeTransitionContext.REFERENCE.get(this);
                    switch (phase) {
                        case PHASE_1:
                            context.retainedObject = createObjectWithLongConstantKeys();
                            break;
                        case PHASE_2:
                            context.retainedObject = createObjectWithSmallConstantKeys();
                            break;
                        default:
                            throw new AssertionError(phase);
                    }
                    return Boolean.TRUE;
                }
            }.getCallTarget();
        }

        @TruffleBoundary
        private TestDynamicObject createObjectWithLongConstantKeys() {
            TestDynamicObject object = new TestDynamicObject(ROOT_SHAPE);
            DynamicObjectLibrary objectLibrary = DynamicObjectLibrary.getUncached();
            for (int i = 0; i < LONG_KEY_COUNT; i++) {
                objectLibrary.putConstant(object, createLongKey(), Boolean.TRUE, 0);
            }
            keepPhase1ObjectAlive = object;
            return object;
        }

        @TruffleBoundary
        private static TestDynamicObject createObjectWithSmallConstantKeys() {
            TestDynamicObject object = new TestDynamicObject(ROOT_SHAPE);
            DynamicObjectLibrary objectLibrary = DynamicObjectLibrary.getUncached();
            for (int i = 0; i < SMALL_KEY_COUNT; i++) {
                objectLibrary.putConstant(object, "small-key-" + i, Boolean.TRUE, 0);
            }
            return object;
        }

        private String createLongKey() {
            return keyCounter.getAndIncrement() + ":" + "x".repeat(LONG_KEY_SIZE);
        }

    }

    static final class ShapeTransitionContext {
        static final TruffleLanguage.ContextReference<ShapeTransitionContext> REFERENCE = TruffleLanguage.ContextReference.create(ShapeTransitionLanguage.class);

        @SuppressWarnings("unused") final TruffleLanguage.Env env;
        Object retainedObject;

        ShapeTransitionContext(TruffleLanguage.Env env) {
            this.env = env;
        }
    }

    static final class TestDynamicObject extends DynamicObject {
        TestDynamicObject(Shape shape) {
            super(shape);
        }
    }
}
