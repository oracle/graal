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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeEncodingException;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction.Argument.Kind;
import com.oracle.truffle.api.bytecode.InstructionDescriptor;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests that use large numbers of instructions, locals, etc. to validate encoding limits.
 * These tests run with fewer parameterizations since they can consume a lot of execution time.
 */
public class BytecodeEncodingLimitsTest extends AbstractBasicInterpreterTest {

    public BytecodeEncodingLimitsTest(TestRun run) {
        super(run);
    }

    @Parameters(name = "{0}")
    public static List<TestRun> getParameters() {
        List<TestRun> result = new ArrayList<>();
        // These tests run for a while. Just run them on "complete" configs, which are the most interesting to us.
        result.add(new TestRun(BasicInterpreterProductionBlockScoping.BYTECODE, true, false, true));
        result.add(new TestRun(BasicInterpreterProductionRootScopingTailCall.BYTECODE, true, false, true));
        return result;
    }

    @Test
    public void testManyBytecodes() {
        BasicInterpreter node = parseNode("manyBytecodes", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLocal x = b.createLocal();
            for (int i = 0; i < Short.MAX_VALUE + 1; i++) {
                b.beginStoreLocal(x);
                b.emitLoadConstant(0L);
                b.endStoreLocal();
            }
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call());
    }

    /**
     * Helper to emit IfThen(arg0, Block(...)).
     * Allows test to exert builder without actually running a large amount of code.
     */
    private static void emitIfArgTrue(BasicInterpreterBuilder b, Runnable gen) {
        b.beginIfThen();
        b.emitLoadArgument(0);
        b.beginBlock();
        gen.run();
        b.endBlock();
        b.endIfThen();
    }

    @Test
    public void testManyConstants() {
        BasicInterpreter node = parseNode("manyConstants", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLocal x = b.createLocal();
            emitIfArgTrue(b, () -> {
                for (int i = 0; i < Short.MAX_VALUE + 1; i++) {
                    b.beginStoreLocal(x);
                    b.emitLoadConstant((long) i);
                    b.endStoreLocal();
                }
            });
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call(false));
    }

    @Test
    public void testManyNodes() {
        // This test expects the instruction to allocate a node profile. If this assertion fails, pick a different operation.
        InstructionDescriptor getSourcePosition = run.bytecode().getInstructionDescriptors().stream().filter(descriptor -> descriptor.getName().contains("ToString")).findFirst().orElseThrow();
        assertTrue(getSourcePosition.getArgumentDescriptors().stream().anyMatch(argument -> argument.getKind() == Kind.NODE_PROFILE && argument.getLength() > 0));

        BasicInterpreter node = parseNode("manyNodes", b -> {
            b.beginRoot();
            b.beginBlock();
            emitIfArgTrue(b, () -> {
                for (int i = 0; i < Short.MAX_VALUE + 1; i++) {
                    b.beginToString();
                    b.emitLoadNull();
                    b.endToString();
                }
            });
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call(false));
    }

    @Test
    public void testManyConditionalBranches() {
        BasicInterpreter node = parseNode("manyConditionalBranches", b -> {
            b.beginRoot();
            b.beginBlock();
            emitIfArgTrue(b, () -> {
                for (int i = 0; i < Short.MAX_VALUE + 1; i++) {
                    b.beginIfThen();
                    b.emitLoadArgument(0);
                    b.emitVoidOperation();
                    b.endIfThen();
                }
            });
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call(false));
    }

    @Test
    public void testManyLocals() {
        BasicInterpreter node = parseNode("manyLocals", b -> {
            b.beginRoot();
            b.beginBlock();

            for (int i = 0; i < Short.MAX_VALUE - 10; i++) {
                b.createLocal();
            }
            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testTooManyLocals() {
        assertThrows(BytecodeEncodingException.class, () -> {
            parseNode("tooManyLocals", b -> {
                b.beginRoot();
                b.beginBlock();

                for (int i = 0; i <= 0xffff; i++) {
                    b.createLocal();
                }
                BytecodeLocal x = b.createLocal();
                b.beginStoreLocal(x);
                b.emitLoadConstant(42L);
                b.endStoreLocal();

                b.beginReturn();
                b.emitLoadLocal(x);
                b.endReturn();
                b.endBlock();
                b.endRoot();
            });
        });
    }

    @Test
    public void testLargeStackPointer() {
        BasicInterpreter node = parseNode("largeStackPointer", b -> {
            b.beginRoot();
            b.beginBlock();

            BytecodeLocal x = b.createLocal();
            int localCount = 0x10000 - run.getFrameBaseSlots();
            for (int i = 1; i < localCount; i++) {
                b.createLocal();
            }
            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            // The operand stack begins above the maximum unsigned-short frame index. This
            // expression must not overwrite the low-index local.
            b.beginVariadicOperation();
            for (int i = 0; i < 4; i++) {
                b.emitLoadConstant(0L);
            }
            b.endVariadicOperation();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testManyRoots() {
        // Introspection takes a long time on this test.
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(run.withoutIntrospection(), LANGUAGE, BytecodeConfig.DEFAULT, b -> {
            for (long i = 0; i <= Short.MAX_VALUE + 1; i++) {
                b.beginRoot();
                BytecodeLocal local = b.createLocal();
                b.beginStoreLocal(local);
                b.emitLoadConstant(i);
                b.endStoreLocal();
                b.beginReturn();
                // Materialized accesses use root index. Ensure high root indices can be encoded.
                b.beginLoadLocalMaterialized(local);
                b.emitMaterializeFrame();
                b.endLoadLocalMaterialized();
                b.endReturn();
                b.endRoot();
            }
        });
        assertEquals(0L, nodes.getNode(0).getCallTarget().call());
        assertEquals(42L, nodes.getNode(42).getCallTarget().call());
        assertEquals((long) (Short.MAX_VALUE - 1), nodes.getNode(Short.MAX_VALUE - 1).getCallTarget().call());
        assertEquals((long) Short.MAX_VALUE + 1, nodes.getNode(Short.MAX_VALUE + 1).getCallTarget().call());
    }

    @Test
    public void testManyInstructionsInLoop() {
        BasicInterpreter node = parseNode("manyInstructionsInLoop", b -> {
            b.beginRoot();
            b.beginBlock();

            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            BytecodeLocal result = b.createLocal();

            b.beginStoreLocal(result);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLess();
            b.emitLoadLocal(x);
            b.emitLoadConstant(5L);
            b.endLess();

            b.beginBlock();
            for (int i = 0; i < Short.MAX_VALUE + 1; i++) {
                b.emitVoidOperation();
            }
            // x = x + 1
            b.beginStoreLocal(x);
            b.beginAdd();
            b.emitLoadLocal(x);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            // result += x
            b.beginStoreLocal(result);
            b.beginAdd();
            b.emitLoadLocal(result);
            b.emitLoadLocal(x);
            b.endAdd();
            b.endStoreLocal();

            b.endBlock();

            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(result);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        assertEquals(15L, node.getCallTarget().call());
    }

    @Test
    public void testManyStackValues() {
        BasicInterpreter node = parseNode("manyStackValues", b -> {
            b.beginRoot();
            b.beginReturn();
            for (int i = 0; i < Short.MAX_VALUE - 1; i++) {
                b.beginAdd();
                b.emitLoadConstant(1L);
            }
            b.emitLoadConstant(0L);

            for (int i = 0; i < Short.MAX_VALUE - 1; i++) {
                b.endAdd();
            }

            b.endReturn();
            b.endRoot();
        });

        assertEquals((long) Short.MAX_VALUE - 1, node.getCallTarget().call());
    }

    @Test
    public void testTooManyStackValues() {
        assertThrowsWithMessage("Maximum stack height exceeded", BytecodeEncodingException.class, () -> {
            parseNode("tooManyStackValues", b -> {
                b.beginRoot();
                b.beginReturn();
                for (int i = 0; i < Short.MAX_VALUE; i++) {
                    b.beginAdd();
                    b.emitLoadConstant(1L);
                }
                b.emitLoadConstant(0L);

                for (int i = 0; i < Short.MAX_VALUE; i++) {
                    b.endAdd();
                }

                b.endReturn();
                b.endRoot();
            });
        });

    }
}
