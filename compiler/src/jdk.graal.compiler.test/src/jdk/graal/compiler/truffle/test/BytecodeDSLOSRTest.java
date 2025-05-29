/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.DebugBytecodeRootNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.runtime.BytecodeOSRMetadata;

import jdk.graal.compiler.test.GraalTest;

public class BytecodeDSLOSRTest extends TestWithSynchronousCompiling {
    private static final BytecodeDSLOSRTestLanguage LANGUAGE = null;

    private static BytecodeDSLOSRTestRootNode parseNode(BytecodeParser<BytecodeDSLOSRTestRootNodeGen.Builder> builder) {
        BytecodeRootNodes<BytecodeDSLOSRTestRootNode> nodes = BytecodeDSLOSRTestRootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    private static BytecodeDSLOSRTestRootNode parseNodeWithSources(BytecodeParser<BytecodeDSLOSRTestRootNodeGen.Builder> builder) {
        BytecodeRootNodes<BytecodeDSLOSRTestRootNode> nodes = BytecodeDSLOSRTestRootNodeGen.create(LANGUAGE, BytecodeConfig.WITH_SOURCE, builder);
        return nodes.getNode(0);
    }

    @Rule public TestRule timeout = GraalTest.createTimeout(30, TimeUnit.SECONDS);

    static final int OSR_THRESHOLD = 10 * BytecodeOSRMetadata.OSR_POLL_INTERVAL;

    @Before
    @Override
    public void before() {
        setupContext("engine.MultiTier", "false",
                        "engine.OSR", "true",
                        "engine.OSRCompilationThreshold", String.valueOf(OSR_THRESHOLD),
                        "engine.OSRMaxCompilationReAttempts", String.valueOf(1),
                        "engine.ThrowOnMaxOSRCompilationReAttemptsReached", "true");
    }

    @Test
    public void testInfiniteInterpreterLoop() {
        BytecodeDSLOSRTestRootNode root = parseNode(b -> {
            b.beginRoot();
            b.beginBlock();
            b.beginWhile();
            b.emitLoadConstant(true);
            b.emitThrowsInCompiledCode();
            b.endWhile();
            b.endBlock();
            b.endRoot();
        });

        try {
            root.getCallTarget().call();
            Assert.fail("Should not reach here.");
        } catch (BytecodeDSLOSRTestRootNode.InCompiledCodeException ex) {
            // expected
        }
    }

    @Test
    public void testReturnValueFromLoop() {
        /**
         * @formatter:off
         * result = 0
         * for (int i = 0; i < OSR_THRESHOLD*2; i++) {
         *   result++;
         *   if (inCompiledCode) result++;
         * }
         * @formatter:on
         */
        BytecodeDSLOSRTestRootNode root = parseNode(b -> {
            b.beginRoot();
            b.beginBlock();

            BytecodeLocal result = b.createLocal();
            b.beginStoreLocal(result);
            b.emitLoadConstant(0);
            b.endStoreLocal();

            BytecodeLocal i = b.createLocal();
            b.beginStoreLocal(i);
            b.emitLoadConstant(0);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLt();
            b.emitLoadLocal(i);
            b.emitLoadConstant(OSR_THRESHOLD * 2);
            b.endLt();

            b.beginBlock();
            b.beginIncrement(result);
            b.emitLoadLocal(result);
            b.endIncrement();

            b.beginIncrementIfCompiled(result);
            b.emitLoadLocal(result);
            b.endIncrementIfCompiled();

            b.beginIncrement(i);
            b.emitLoadLocal(i);
            b.endIncrement();
            b.endBlock();
            b.endWhile();

            b.emitLoadLocal(result);
            b.endBlock();
            b.endRoot();
        });

        // 1*(# interpreter iterations) + 2*(# compiled iterations)
        assertEquals(OSR_THRESHOLD * 3, root.getCallTarget().call());
    }

    @Test
    public void testInstrumented() {
        /**
         * @formatter:off
         * result = 0
         * for (int i = 0; i < OSR_THRESHOLD*2; i++) {
         *   result++;
         *   if (inCompiledCode) enableInstrumentation;
         *   result = plusOne(result) // no-op if instrumentation disabled
         * }
         * @formatter:on
         */
        BytecodeDSLOSRTestRootNode root = parseNodeWithSources(b -> {
            b.beginRoot();
            b.beginBlock();

            BytecodeLocal result = b.createLocal();
            b.beginStoreLocal(result);
            b.emitLoadConstant(0);
            b.endStoreLocal();

            BytecodeLocal i = b.createLocal();
            b.beginStoreLocal(i);
            b.emitLoadConstant(0);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLt();
            b.emitLoadLocal(i);
            b.emitLoadConstant(OSR_THRESHOLD * 2);
            b.endLt();

            b.beginBlock();
            b.beginIncrement(result);
            b.emitLoadLocal(result);
            b.endIncrement();

            b.emitInstrumentIfCompiled();

            b.beginStoreLocal(result);
            b.beginPlusOne();
            b.emitLoadLocal(result);
            b.endPlusOne();
            b.endStoreLocal();

            b.beginIncrement(i);
            b.emitLoadLocal(i);
            b.endIncrement();

            b.endBlock();
            b.endWhile();

            b.emitLoadLocal(result);
            b.endBlock();
            b.endRoot();
        });

        // 1*(# interpreter iterations) + 2*(# instrumented iterations)
        assertEquals(OSR_THRESHOLD * 3, root.getCallTarget().call());
    }

    @Test
    public void testNoImmediateDeoptAfterOSRLoop() {
        /**
         * When OSR is performed for a loop, the loop exit branch often has never been taken. Bytecode
         * DSL interpreters should force the branch profile to a non-zero value so that OSR code does
         * not immediately deopt when exiting the loop.
         *
         * @formatter:off
         * i = 0;
         * while (i < arg0) {
         *   i += 1;
         * }
         * return inCompiledCode;
         * @formatter:on
         */
        BytecodeDSLOSRTestRootNode root = parseNode(b -> {
            b.beginRoot();
            BytecodeLocal i = b.createLocal();
            b.beginStoreLocal(i);
            b.emitLoadConstant(0);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLt();
            b.emitLoadLocal(i);
            b.emitLoadArgument(0);
            b.endLt();
            b.beginIncrement(i);
            b.emitLoadLocal(i);
            b.endIncrement();
            b.endWhile();

            b.beginReturn();
            b.emitInCompiledCode();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(true, root.getCallTarget().call(OSR_THRESHOLD + 1));
    }

    private static BytecodeParser<BytecodeDSLOSRTestRootNodeWithYieldGen.Builder> getParserForYieldTest(boolean emitYield) {
        return b -> {
            b.beginRoot();
            b.beginBlock();

            if (emitYield) {
                b.beginYield();
                b.emitLoadConstant(0);
                b.endYield();
            }

            BytecodeLocal result = b.createLocal();
            b.beginStoreLocal(result);
            b.emitLoadConstant(0);
            b.endStoreLocal();

            BytecodeLocal i = b.createLocal();
            b.beginStoreLocal(i);
            b.emitLoadConstant(0);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLt();
            b.emitLoadLocal(i);
            b.emitLoadConstant(OSR_THRESHOLD * 2);
            b.endLt();

            b.beginBlock();
            b.beginIncrement(result);
            b.emitLoadLocal(result);
            b.endIncrement();

            b.beginIncrementIfCompiled(result);
            b.emitLoadLocal(result);
            b.endIncrementIfCompiled();

            b.beginIncrement(i);
            b.emitLoadLocal(i);
            b.endIncrement();
            b.endBlock();
            b.endWhile();

            b.emitLoadLocal(result);
            b.endBlock();
            b.endRoot();
        };
    }

    /**
     * The following tests are identical to #testReturnValueFromLoop but on an interpreter with
     * continuations. We test with and without a yield at the beginning to test that locals are
     * correctly forwarded in either case.
     */

    @Test
    public void testReturnValueFromLoopYieldingWithYield() {
        BytecodeDSLOSRTestRootNodeWithYield rootWithYield = BytecodeDSLOSRTestRootNodeWithYieldGen.create(LANGUAGE, BytecodeConfig.DEFAULT, getParserForYieldTest(true)).getNode(0);
        ContinuationResult cont = (ContinuationResult) rootWithYield.getCallTarget().call();
        // 1*(# interpreter iterations) + 2*(# compiled iterations)
        assertEquals(OSR_THRESHOLD * 3, cont.continueWith(null));
    }

    @Test
    public void testReturnValueFromLoopYieldingNoYield() {
        BytecodeDSLOSRTestRootNodeWithYield rootNoYield = BytecodeDSLOSRTestRootNodeWithYieldGen.create(LANGUAGE, BytecodeConfig.DEFAULT, getParserForYieldTest(false)).getNode(0);
        // 1*(# interpreter iterations) + 2*(# compiled iterations)
        assertEquals(OSR_THRESHOLD * 3, rootNoYield.getCallTarget().call());
    }

    private static final BytecodeParser<BytecodeDSLOSRTestRootNodeWithYieldGen.Builder> badFrameParser = b -> {
        /*
         * This is a regression test. An earlier implementation erroneously passed a materialized
         * continuation frame to OSR using the interpreter state parameter, which is for constant
         * data. The OSR target was subsequently reused, and it used the old materialized frame for
         * its locals.
         *
         * @formatter:off
         * if (arg0) {
         *   yield 0
         * }
         * int result = 0;
         * for (int i = 0; i < arg1; i++) {
         *  result++;
         *  if (inCompiledCode) result++;
         * }
         * return result;
         * @formatter:on
         */
        b.beginRoot();

        b.beginIfThen();
        b.emitLoadArgument(0);
        b.beginYield();
        b.emitLoadConstant(0);
        b.endYield();
        b.endIfThen();

        BytecodeLocal result = b.createLocal();
        b.beginStoreLocal(result);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        BytecodeLocal i = b.createLocal();
        b.beginStoreLocal(i);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        b.beginWhile();
        b.beginLt();
        b.emitLoadLocal(i);
        b.emitLoadArgument(1);
        b.endLt();
        b.beginBlock();

        b.beginIncrement(i);
        b.emitLoadLocal(i);
        b.endIncrement();

        b.beginIncrement(result);
        b.emitLoadLocal(result);
        b.endIncrement();

        b.beginIncrementIfCompiled(result);
        b.emitLoadLocal(result);
        b.endIncrementIfCompiled();

        b.endBlock();
        b.endWhile();

        b.beginReturn();
        b.emitLoadLocal(result);
        b.endReturn();

        b.endRoot();
    };

    @Test
    public void testBadFrameReuse() {
        BytecodeDSLOSRTestRootNodeWithYield root = BytecodeDSLOSRTestRootNodeWithYieldGen.create(LANGUAGE, BytecodeConfig.DEFAULT, badFrameParser).getNode(0);
        // First, call it with yield, so the OSR uses the continuation frame.
        ContinuationResult cont = (ContinuationResult) root.getCallTarget().call(true, OSR_THRESHOLD * 2);
        // OSR_THRESHOLD (interpreter) + 2*OSR_THRESHOLD (compiled)
        assertEquals(OSR_THRESHOLD * 3, cont.continueWith(null));
        // Then, call it again with yield. OSR should not reuse the old frame.
        cont = (ContinuationResult) root.getCallTarget().call(true, BytecodeOSRMetadata.OSR_POLL_INTERVAL * 2);
        // OSR_POLL_INTERVAL (interpreter) + 2*OSR_POLL_INTERVAL (compiled)
        assertEquals(BytecodeOSRMetadata.OSR_POLL_INTERVAL * 3, cont.continueWith(null));
    }

    @Test
    public void testContinuationThenRegularFrame() {
        BytecodeDSLOSRTestRootNodeWithYield root = BytecodeDSLOSRTestRootNodeWithYieldGen.create(LANGUAGE, BytecodeConfig.DEFAULT, badFrameParser).getNode(0);
        // First, call it with yield, so OSR uses the continuation frame.
        ContinuationResult cont = (ContinuationResult) root.getCallTarget().call(true, OSR_THRESHOLD * 2);
        // OSR_THRESHOLD (interpreter) + 2*OSR_THRESHOLD (compiled)
        assertEquals(OSR_THRESHOLD * 3, cont.continueWith(null));
        // Then, call it regularly. OSR should compile separately for the regular frame.
        // OSR_THRESHOLD (interpreter) + 2*(OSR_THRESHOLD + 2) (compiled)
        assertEquals(OSR_THRESHOLD * 3 + 2, root.getCallTarget().call(false, OSR_THRESHOLD * 2 + 1));
    }

    @Test
    public void testRegularThenContinuationFrame() {
        BytecodeDSLOSRTestRootNodeWithYield root = BytecodeDSLOSRTestRootNodeWithYieldGen.create(LANGUAGE, BytecodeConfig.DEFAULT, badFrameParser).getNode(0);
        // First, call it regularly, so OSR uses the regular frame.
        // OSR_THRESHOLD (interpreter) + 2*OSR_THRESHOLD (compiled)
        assertEquals(OSR_THRESHOLD * 3, root.getCallTarget().call(false, OSR_THRESHOLD * 2));
        // Then, call it with yield. OSR should compile separately for the continuation frame.
        ContinuationResult cont = (ContinuationResult) root.getCallTarget().call(true, OSR_THRESHOLD * 2 + 1);
        // OSR_THRESHOLD (interpreter) + 2*(OSR_THRESHOLD + 2) (compiled)
        assertEquals(OSR_THRESHOLD * 3 + 2, cont.continueWith(null));
    }

    @TruffleLanguage.Registration(id = "BytecodeDSLOSRTestLanguage")
    static class BytecodeDSLOSRTestLanguage extends TruffleLanguage<Object> {
        @Override
        protected Object createContext(Env env) {
            return new Object();
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLOSRTestLanguage.class)
    public abstract static class BytecodeDSLOSRTestRootNode extends DebugBytecodeRootNode {

        static class InCompiledCodeException extends AbstractTruffleException {
            private static final long serialVersionUID = 1L;
        }

        protected BytecodeDSLOSRTestRootNode(BytecodeDSLOSRTestLanguage language, FrameDescriptor fd) {
            super(language, fd);
        }

        @Operation
        static final class ThrowsInCompiledCode {
            @Specialization
            public static void perform() {
                if (CompilerDirectives.inCompiledCode()) {
                    throw new InCompiledCodeException();
                }
            }
        }

        @Operation
        static final class InCompiledCode {
            @Specialization
            public static boolean perform() {
                return CompilerDirectives.inCompiledCode();
            }
        }

        @Operation
        static final class Lt {
            @Specialization
            public static boolean perform(int left, int right) {
                return left < right;
            }
        }

        @Operation
        @ConstantOperand(type = LocalAccessor.class)
        static final class Increment {
            @Specialization
            public static void perform(VirtualFrame frame, LocalAccessor variable, int currentValue,
                            @Bind BytecodeNode bytecodeNode) {
                variable.setInt(bytecodeNode, frame, currentValue + 1);
            }
        }

        @Operation
        @ConstantOperand(type = LocalAccessor.class)
        static final class IncrementIfCompiled {
            @Specialization
            public static void perform(VirtualFrame frame, LocalAccessor variable, int currentValue,
                            @Bind BytecodeNode bytecodeNode) {
                /**
                 * NB: this is implemented as one operation rather than a built-in IfThen operation
                 * because the IfThen branch profile would mark the "in compiled code" branch as
                 * dead and we'd deopt on OSR entry.
                 */
                if (CompilerDirectives.inCompiledCode()) {
                    variable.setInt(bytecodeNode, frame, currentValue + 1);
                }
            }
        }

        @Instrumentation
        static final class PlusOne {
            @Specialization
            public static int perform(int value) {
                return value + 1;
            }
        }

        @Operation
        static final class InstrumentIfCompiled {
            @Specialization

            public static void perform(@Bind BytecodeDSLOSRTestRootNode root) {
                if (CompilerDirectives.inCompiledCode()) {
                    enableInstrumentation(root);
                }
            }

            @TruffleBoundary
            private static void enableInstrumentation(BytecodeDSLOSRTestRootNode root) {
                root.getRootNodes().update(BytecodeDSLOSRTestRootNodeGen.newConfigBuilder().addInstrumentation(PlusOne.class).build());
            }
        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLOSRTestLanguage.class, enableYield = true)
    public abstract static class BytecodeDSLOSRTestRootNodeWithYield extends DebugBytecodeRootNode {

        protected BytecodeDSLOSRTestRootNodeWithYield(BytecodeDSLOSRTestLanguage language, FrameDescriptor fd) {
            super(language, fd);
        }

        @Operation
        static final class Lt {
            @Specialization
            public static boolean perform(int left, int right) {
                return left < right;
            }
        }

        @Operation
        @ConstantOperand(type = LocalAccessor.class)
        static final class Increment {
            @Specialization
            public static void perform(VirtualFrame frame, LocalAccessor variable, int currentValue,
                            @Bind BytecodeNode bytecodeNode) {
                variable.setInt(bytecodeNode, frame, currentValue + 1);
            }
        }

        @Operation
        @ConstantOperand(type = LocalAccessor.class)
        static final class IncrementIfCompiled {
            @Specialization
            public static void perform(VirtualFrame frame, LocalAccessor variable, int currentValue,
                            @Bind BytecodeNode bytecodeNode) {
                /**
                 * NB: this is implemented as one operation rather than a built-in IfThen operation
                 * because the IfThen branch profile would mark the "in compiled code" branch as
                 * dead and we'd deopt on OSR entry.
                 */
                if (CompilerDirectives.inCompiledCode()) {
                    variable.setInt(bytecodeNode, frame, currentValue + 1);
                }
            }
        }
    }

}
