/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * Tests the {@code engine.TraceBytecodeTransition} engine option which logs bytecode interpreter
 * transition events (uncached-to-cached, bytecode updates, deoptimization).
 */
public class TransitionTracingTest {

    static final TransitionTracingRootNodeGen.Bytecode BYTECODE = TransitionTracingRootNodeGen.BYTECODE;

    private Context context;

    @After
    public void tearDownContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    /**
     * With {@code engine.TraceBytecodeTransition=true}, executing a root node triggers an
     * uncached-to-cached tier transition. Since the bytecode array identity does not change in this
     * transition, no bytecode/tier transition kind is logged.
     */
    @Test
    public void testEngineTransitionTracingAll() {
        Context.Builder cb = Context.newBuilder(BytecodeDSLTestLanguage.ID).option("engine.TraceBytecodeTransition", "true");
        List<String> messages = captureLog(cb);

        BytecodeDSLTestLanguage language = setupLanguage(cb);
        TransitionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, TransitionTracingTest::emitSimpleInc).getNode(0);

        // Force immediate transition to cached.
        node.getBytecodeNode().setUncachedThreshold(0);
        node.getCallTarget().call(Boolean.FALSE, 42);

        assertFalse("Expected no [bc-transition] bytecode log line when bytecode array identity is unchanged", hasTransitionLog(messages, "bytecode"));
        assertFalse("Expected no [bc-transition] tier log line when bytecode array identity is unchanged", hasTransitionLog(messages, "tier"));
    }

    /**
     * With {@code engine.TraceBytecodeTransition=bytecode}, only bytecodeUpdate events are logged.
     * For uncached-to-cached transitions with identical bytecode-array identity, no log is emitted.
     */
    @Test
    public void testFilterBytecodeUpdate() {
        Context.Builder cb = Context.newBuilder(BytecodeDSLTestLanguage.ID).option("engine.TraceBytecodeTransition", "bytecode");
        List<String> messages = captureLog(cb);

        BytecodeDSLTestLanguage language = setupLanguage(cb);
        TransitionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, TransitionTracingTest::emitSimpleInc).getNode(0);

        // Force immediate transition to cached and execute to trigger uncached->cached.
        node.getBytecodeNode().setUncachedThreshold(0);
        node.getCallTarget().call(Boolean.FALSE, 42);

        assertFalse("Expected no bytecode log line when bytecode array identity is unchanged", hasTransitionLog(messages, "bytecode"));
    }

    /**
     * With {@code engine.TraceBytecodeTransition=tier}, only tier updates derived from bytecode
     * updates are logged. Uncached-to-cached transitions with identical bytecode-array identity do
     * not produce tier logs.
     */
    @Test
    public void testFilterTierUpdate() {
        Context.Builder cb = Context.newBuilder(BytecodeDSLTestLanguage.ID).option("engine.TraceBytecodeTransition", "tier");
        List<String> messages = captureLog(cb);

        BytecodeDSLTestLanguage language = setupLanguage(cb);
        TransitionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, TransitionTracingTest::emitSimpleInc).getNode(0);

        // Force immediate transition to cached and execute to trigger uncached->cached.
        node.getBytecodeNode().setUncachedThreshold(0);
        node.getCallTarget().call(Boolean.FALSE, 42);

        assertFalse("Expected no tier log line when bytecode array identity is unchanged", hasTransitionLog(messages, "tier"));
    }

    /**
     * With {@code engine.TraceBytecodeTransition=true}, enabling instrumentation on-stack in a
     * bytecode-updatable interpreter reports both {@code bytecode} and {@code instrumentation}
     * kinds.
     */
    @Test
    public void testEngineTransitionTracingInstrumentationUpdate() {
        Context.Builder cb = Context.newBuilder(BytecodeDSLTestLanguage.ID).option("engine.TraceBytecodeTransition", "true");
        List<String> messages = captureLog(cb);

        BytecodeDSLTestLanguage language = setupLanguage(cb);
        TransitionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, TransitionTracingTest::emitInstrumentationUpdateInc).getNode(0);

        node.getBytecodeNode().setUncachedThreshold(0);
        node.getCallTarget().call(Boolean.FALSE, 42);

        assertTrue("Expected instrumentation log line for on-stack instrumentation enablement", hasTransitionLog(messages, "instrumentation"));
        assertTrue("Expected bytecode log line for on-stack instrumentation enablement", hasTransitionLog(messages, "bytecode"));
    }

    @Test
    public void testFilterTagUpdate() {
        Context.Builder cb = Context.newBuilder(BytecodeDSLTestLanguage.ID).option("engine.TraceBytecodeTransition", "tag");
        List<String> messages = captureLog(cb);

        BytecodeDSLTestLanguage language = setupLanguage(cb);
        TransitionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, TransitionTracingTest::emitSimpleInc).getNode(0);

        node.getBytecodeNode().setUncachedThreshold(0);
        node.getCallTarget().call(Boolean.FALSE, 42);
        assertFalse("Expected no tag log line before tag materialization", hasTransitionLog(messages, "tag"));

        BytecodeConfig.Builder configBuilder = BYTECODE.newConfigBuilder();
        configBuilder.addTag(StatementTag.class);
        node.getRootNodes().update(configBuilder.build());
        node.getCallTarget().call(Boolean.FALSE, 42);

        // Config changes applied between calls do not currently trigger a transition callback.
        assertFalse("Expected no tag log line for between-call tag updates", hasTransitionLog(messages, "tag"));
    }

    @Test
    public void testFilterInstrumentationUpdate() {
        Context.Builder cb = Context.newBuilder(BytecodeDSLTestLanguage.ID).option("engine.TraceBytecodeTransition", "instrumentation");
        List<String> messages = captureLog(cb);

        BytecodeDSLTestLanguage language = setupLanguage(cb);
        TransitionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, TransitionTracingTest::emitSimpleInc).getNode(0);

        node.getBytecodeNode().setUncachedThreshold(0);
        node.getCallTarget().call(Boolean.FALSE, 42);
        assertFalse("Expected no instrumentation log line before instrumentation materialization", hasTransitionLog(messages, "instrumentation"));

        BytecodeConfig.Builder configBuilder = BYTECODE.newConfigBuilder();
        configBuilder.addInstrumentation(TransitionTracingRootNode.TraceValue.class);
        node.getRootNodes().update(configBuilder.build());
        node.getCallTarget().call(Boolean.FALSE, 42);

        // Config changes applied between calls do not currently trigger a transition callback.
        assertFalse("Expected no instrumentation log line for between-call instrumentation updates", hasTransitionLog(messages, "instrumentation"));
    }

    /**
     * With {@code engine.BytecodeMethodFilter} set, matching transitions would be logged if the
     * root's qualified name matched. For uncached-to-cached transitions with unchanged
     * bytecode-array identity, no transition kinds are emitted.
     */
    @Test
    public void testMethodFilterInclude() {
        Context.Builder cb = Context.newBuilder(BytecodeDSLTestLanguage.ID).//
                        option("engine.TraceBytecodeTransition", "true").//
                        option("engine.BytecodeMethodFilter", "TransitionTracing");
        List<String> messages = captureLog(cb);

        BytecodeDSLTestLanguage language = setupLanguage(cb);
        TransitionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, TransitionTracingTest::emitSimpleInc).getNode(0);

        node.getBytecodeNode().setUncachedThreshold(0);
        node.getCallTarget().call(Boolean.FALSE, 42);

        assertFalse("Expected no [bc-transition] bytecode log line when bytecode array identity is unchanged", hasTransitionLog(messages, "bytecode"));
        assertFalse("Expected no [bc-transition] tier log line when bytecode array identity is unchanged", hasTransitionLog(messages, "tier"));
    }

    @Test
    public void testMethodTransferToInterpreter() {
        TruffleTestAssumptions.assumeOptimizingRuntime();

        Context.Builder cb = Context.newBuilder(BytecodeDSLTestLanguage.ID).//
                        allowExperimentalOptions(true).//
                        option("engine.TraceBytecodeTransition", "transferToInterpreter").//
                        option("engine.MultiTier", "false").//
                        option("engine.BackgroundCompilation", "false");

        List<String> messages = captureLog(cb);
        BytecodeDSLTestLanguage language = setupLanguage(cb);
        TransitionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, TransitionTracingTest::emitSimpleInc).getNode(0);

        node.getBytecodeNode().setUncachedThreshold(0);

        CallTarget target = node.getCallTarget();
        for (int i = 0; i < 100000; i++) {
            target.call(Boolean.FALSE, 42);
        }

        assertFalse(hasTransitionLog(messages, "transferToInterpreter"));

        target.call(Boolean.TRUE, 42);

        assertTrue(hasTransitionLog(messages, "transferToInterpreter"));
    }

    /**
     * With {@code engine.BytecodeMethodFilter} set to exclude the root's qualified name, no
     * transition events are logged.
     */
    @Test
    public void testMethodFilterExclude() {
        Context.Builder cb = Context.newBuilder(BytecodeDSLTestLanguage.ID).//
                        option("engine.TraceBytecodeTransition", "true").//
                        option("engine.BytecodeMethodFilter", "~TransitionTracing");
        List<String> messages = captureLog(cb);

        BytecodeDSLTestLanguage language = setupLanguage(cb);
        TransitionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, TransitionTracingTest::emitSimpleInc).getNode(0);

        node.getBytecodeNode().setUncachedThreshold(0);
        node.getCallTarget().call(Boolean.FALSE, 42);

        assertFalse("Expected no [bc-transition] log line with exclude filter", hasTransitionLog(messages, "bytecode"));
    }

    /**
     * When the option is not set (empty string default), no {@code [bc-transition]} log lines
     * appear.
     */
    @Test
    public void testDisabled() {
        Context.Builder cb = Context.newBuilder(BytecodeDSLTestLanguage.ID);
        List<String> messages = captureLog(cb);

        BytecodeDSLTestLanguage language = setupLanguage(cb);
        TransitionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, TransitionTracingTest::emitSimpleInc).getNode(0);

        node.getBytecodeNode().setUncachedThreshold(0);
        node.getCallTarget().call(Boolean.FALSE, 42);

        assertFalse("Expected no [bc-transition] log line when option is disabled", hasTransitionLog(messages, "bytecode"));
    }

    private BytecodeDSLTestLanguage setupLanguage(Context.Builder cb) {
        context = cb.build();
        context.initialize(BytecodeDSLTestLanguage.ID);
        context.enter();
        return BytecodeDSLTestLanguage.REF.get(null);
    }

    private static List<String> captureLog(Context.Builder cb) {
        List<String> messages = new ArrayList<>();
        cb.logHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                synchronized (messages) {
                    messages.add(record.getMessage());
                }
            }

            @Override
            public void close() {
            }

            @Override
            public void flush() {
            }
        });
        return messages;
    }

    private static void emitSimpleInc(TransitionTracingRootNodeGen.Builder b) {
        b.beginRoot();

        /*
         * var i = arg[1] i = i + 1; deoptIf(arg[0]); return i;
         */
        var local = b.createLocal();
        b.beginTag(StatementTag.class);
        b.beginStoreLocal(local);
        b.beginTraceValue();
        b.beginInc();
        b.emitLoadArgument(1);
        b.endInc();
        b.endTraceValue();
        b.endStoreLocal();
        b.endTag(StatementTag.class);

        b.beginDeoptIf();
        b.emitLoadArgument(0);
        b.endDeoptIf();

        b.beginReturn();
        b.emitLoadLocal(local);
        b.endReturn();

        b.endRoot();
    }

    private static void emitInstrumentationUpdateInc(TransitionTracingRootNodeGen.Builder b) {
        b.beginRoot();

        var local = b.createLocal();
        b.beginStoreLocal(local);
        b.beginInc();
        b.emitLoadArgument(1);
        b.endInc();
        b.endStoreLocal();

        b.emitEnableTraceValueInstrumentation();

        b.beginStoreLocal(local);
        b.beginTraceValue();
        b.beginInc();
        b.emitLoadLocal(local);
        b.endInc();
        b.endTraceValue();
        b.endStoreLocal();

        b.beginReturn();
        b.emitLoadLocal(local);
        b.endReturn();

        b.endRoot();
    }

    private static boolean hasTransitionLog(List<String> messages, String kind) {
        synchronized (messages) {
            for (String msg : messages) {
                String prefix = "[bc-transition] kinds=";
                if (!msg.startsWith(prefix)) {
                    continue;
                }
                int langIndex = msg.indexOf(" lang=");
                String kinds = langIndex >= 0 ? msg.substring(prefix.length(), langIndex) : msg.substring(prefix.length());
                for (String token : kinds.split(",")) {
                    if (token.equals(kind)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, enableTagInstrumentation = true)
    public abstract static class TransitionTracingRootNode extends RootNode implements BytecodeRootNode {

        protected TransitionTracingRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation(storeBytecodeIndex = false)
        public static final class EnableTraceValueInstrumentation {
            @Specialization
            static void doDefault(@Bind TransitionTracingRootNode root,
                            @Cached(value = "getConfig()", allowUncached = true, neverDefault = true) BytecodeConfig config) {
                root.getRootNodes().update(config);
            }

            @TruffleBoundary
            protected static BytecodeConfig getConfig() {
                BytecodeConfig.Builder configBuilder = TransitionTracingTest.BYTECODE.newConfigBuilder();
                configBuilder.addInstrumentation(TraceValue.class);
                return configBuilder.build();
            }
        }

        @Operation
        public static final class DeoptIf {
            @Specialization
            static void doDefault(boolean condition) {
                if (condition) {
                    CompilerDirectives.transferToInterpreter();
                    foo();
                }
            }

        }

        @Operation
        public static final class Inc {
            @Specialization
            static int doDefault(int v) {
                return v + 1;
            }

        }

        @Instrumentation
        public static final class TraceValue {
            @Specialization
            static int doDefault(int value) {
                return value;
            }
        }

        @TruffleBoundary
        public static void foo() {
            System.out.println("foo");
        }

    }

}
