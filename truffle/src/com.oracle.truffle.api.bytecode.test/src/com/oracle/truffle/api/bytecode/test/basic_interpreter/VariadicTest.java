/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instruction.Argument;

public class VariadicTest extends AbstractBasicInterpreterTest {

    public VariadicTest(TestRun run) {
        super(run);
    }

    @Test
    public void testVariadic0Arguments() {
        for (int i = 0; i < 32; i++) {
            final int variadicCount = i;

            var root = parseNode("testVariadic0Arguments", (b) -> {
                b.beginRoot();
                b.beginReturn();
                b.beginVariadic0Operation();
                for (int j = 0; j < variadicCount; j++) {
                    b.emitLoadArgument(j);
                }
                b.endVariadic0Operation();
                b.endReturn();
                b.endRoot();
            });

            int expectedSlots;
            if (i == 0) {
                expectedSlots = 1;
            } else if (i <= run.getVariadicsLimit()) {
                expectedSlots = i;
            } else if (i < run.getVariadicsLimit() * 2) {
                expectedSlots = run.getVariadicsLimit();
            } else {
                expectedSlots = run.getVariadicsLimit() + 1;
            }

            assertEquals(expectedSlots + run.getFrameBaseSlots(), root.getFrameDescriptor().getNumberOfSlots());

            Object[] args = new Object[variadicCount];
            for (int j = 0; j < variadicCount; j++) {
                args[j] = j;
            }

            Object[] result = (Object[]) root.getCallTarget().call(args);
            assertArrayEquals(args, result);
        }
    }

    @Test
    public void testVariadic1Arguments() {
        for (int i = 0; i < run.getVariadicsLimit() * 4; i++) {
            final int variadicCount = i;

            var root = parseNode("testVariadic1Arguments", (b) -> {
                b.beginRoot();
                b.beginReturn();
                b.beginVariadic1Operation();
                b.emitLoadConstant(-1L);
                for (int j = 0; j < variadicCount; j++) {
                    b.emitLoadArgument(j);
                }
                b.endVariadic1Operation();
                b.endReturn();
                b.endRoot();
            });

            Object[] args = new Object[variadicCount];
            for (int j = 0; j < variadicCount; j++) {
                args[j] = (long) j;
            }
            Object[] result = (Object[]) root.getCallTarget().call(args);
            assertArrayEquals(args, result);
        }
    }

    @Test
    public void testVariadicOffsetArguments() {
        for (int i = 0; i < run.getVariadicsLimit() * 4; i++) {
            final int variadicCount = i;

            Object[] args = new Object[variadicCount];
            for (int j = 0; j < variadicCount; j++) {
                args[j] = (long) j;
            }

            var root = parseNode("testVariadicOffsetArguments", (b) -> {
                b.beginRoot();
                b.beginReturn();
                b.beginVariadicOffsetOperation();
                for (int j = 0; j < variadicCount; j++) {
                    b.emitLoadArgument(j);
                }
                b.endVariadicOffsetOperation();
                b.endReturn();
                b.endRoot();
            });

            Object[] result = (Object[]) root.getCallTarget().call(args);
            Object[] expectedArgs = new Object[variadicCount + 4];
            System.arraycopy(args, 0, expectedArgs, 4, args.length);

            assertArrayEquals(expectedArgs, result);
        }
    }

    @Test
    public void testPreferSplatOverMerge() {
        var root = parseNode("testPreferSplatOverMerge", (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginVariadic0Operation();
            int index = 0;
            for (int e = 0; e < run.getVariadicsLimit() + 1; e++) {
                b.beginDynamicVariadic();
                b.emitLoadArgument(index++);
                b.endDynamicVariadic();
            }
            b.endVariadic0Operation();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(1, countInstructions(root, "load.variadic"));
        for (Instruction instruction : root.getBytecodeNode().getInstructions()) {
            if (instruction.getName().equals("load.variadic")) {
                boolean argumentFound = false;
                for (Argument arg : instruction.getArguments()) {
                    if (arg.getName().equals("merge_count")) {
                        assertEquals(instruction.toString(), 0, arg.asInteger());
                        argumentFound = true;
                    }
                }
                if (!argumentFound) {
                    fail("argument not found");
                }
            }
        }

        Object[] expectedArgs = new Object[run.getVariadicsLimit() + 1];
        for (int j = 0; j < expectedArgs.length; j++) {
            expectedArgs[j] = j;
        }

        Object[] result = (Object[]) root.getCallTarget().call(expectedArgs);
        assertArrayEquals(expectedArgs, result);
    }

    @Test
    public void testMergeVariadics() {
        for (int i = 0; i < run.getVariadicsLimit() + 1; i++) {
            final int staticVariadicCount = i;
            for (int y = 0; y < 6; y++) {
                final int dynamicVariadicCount = y;
                for (int z = 0; z < run.getVariadicsLimit() + 1; z++) {
                    final int dynamicVariadics = z;
                    var root = parseNode("testMergeVariadics", (b) -> {
                        b.beginRoot();
                        b.beginReturn();
                        b.beginVariadic0Operation();
                        int index = 0;
                        for (int j = 0; j < staticVariadicCount; j++) {
                            b.emitLoadArgument(index++);
                        }
                        for (int e = 0; e < dynamicVariadics; e++) {
                            b.beginDynamicVariadic();
                            for (int j = 0; j < dynamicVariadicCount; j++) {
                                b.emitLoadArgument(index++);
                            }
                            b.endDynamicVariadic();
                        }
                        b.endVariadic0Operation();
                        b.endReturn();
                        b.endRoot().name = "testMergeVariadics(staticVariadicCount=" + staticVariadicCount + ",dynamicVariadicCount=" + dynamicVariadicCount + ",dynamicVariadics=" +
                                        dynamicVariadics + ")";
                    });

                    Object[] expectedArgs = new Object[staticVariadicCount + dynamicVariadics * dynamicVariadicCount];
                    for (int j = 0; j < expectedArgs.length; j++) {
                        expectedArgs[j] = j;
                    }

                    // we can merge a LIMIT number of dynamic arrays with create.variadic and
                    // load.variadic
                    int stackCount = (dynamicVariadics + staticVariadicCount) % run.getVariadicsLimit();
                    if (stackCount == 0) {
                        stackCount = run.getVariadicsLimit();
                    }
                    if (dynamicVariadics <= stackCount) {
                        assertEquals(0, countInstructions(root, "splat.variadic"));
                    } else {
                        assertTrue(countInstructions(root, "splat.variadic") > 0);
                    }

                    Object[] result = (Object[]) root.getCallTarget().call(expectedArgs);
                    assertArrayEquals(expectedArgs, result);
                }
            }
        }
    }

    @Test
    public void testMultiSplat() {
        for (int i = 1; i < run.getVariadicsLimit() + 1; i++) {
            final int repeat = i;
            for (int y = 0; y < run.getVariadicsLimit() + 1; y++) {
                final int staticCount = y;
                for (int z = 0; z < 6; z++) {
                    final int dynamicCount = z;
                    var root = parseNode("testMultiSplat", (b) -> {
                        b.beginRoot();
                        b.beginReturn();
                        b.beginVariadic0Operation();

                        int index = 0;
                        for (int j = 0; j < repeat; j++) {
                            for (int k = 0; k < staticCount; k++) {
                                b.emitLoadArgument(index++);
                            }
                            b.beginDynamicVariadic();
                            for (int f = 0; f < dynamicCount; f++) {
                                b.emitLoadArgument(index++);
                            }
                            b.endDynamicVariadic();
                        }

                        b.endVariadic0Operation();
                        b.endReturn();
                        b.endRoot();
                    });

                    Object[] expectedArgs = new Object[repeat * (staticCount + dynamicCount)];
                    for (int j = 0; j < expectedArgs.length; j++) {
                        expectedArgs[j] = j;
                    }

                    Object[] result = (Object[]) root.getCallTarget().call(expectedArgs);
                    assertArrayEquals(expectedArgs, result);
                }
            }
        }
    }

    @Test
    public void testPassReturn() {
        var root = parseNode("testPassReturn", (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginVariadic0Operation();
            b.beginDynamicVariadic();
            b.endDynamicVariadic();
            b.endVariadic0Operation();
            b.endReturn();
            b.endRoot();
        });

        assertArrayEquals(new Object[0], (Object[]) root.getCallTarget().call());

        // we should not create.variadic
        assertEquals(0, countInstructions(root, "create.variadic"));
    }

    @Test
    public void testNullReturn() {
        var root = parseNode("testNullReturn", (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginVariadic0Operation();
            b.emitDynamicVariadicNull();
            b.endVariadic0Operation();
            b.endReturn();
            b.endRoot();
        });

        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> root.getCallTarget().call());
        assertEquals("The operation DynamicVariadicNull must return a non-null value, but did return a null value.", e.getMessage());
    }

    @Test
    public void testDynamicVariadicOneScalarOneVariadic() {
        var root = parseNode("testDynamicVariadicOneScalarOneVariadic", (b) -> {
            b.beginRoot();
            b.beginReturn();

            b.beginVariadicAddInt();
            b.emitLoadConstant(5L);

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();
            b.endVariadicAddInt();

            b.endReturn();
            b.endRoot();
        });
        assertEquals(2L * 5L + 1L * 5L, root.getCallTarget().call());
    }

    @Test
    public void testDynamicVariadicOneArrayOneVariadic() {
        var root = parseNode("testDynamicVariadicOneArrayOneVariadic", (b) -> {
            b.beginRoot();
            b.beginReturn();

            b.beginVariadicAddLArr();
            b.emitLoadConstant(new long[]{1L, 2L, 3L});

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();
            b.endVariadicAddLArr();

            b.endReturn();
            b.endRoot();
        });
        assertEquals(2L + 1L, root.getCallTarget().call());
    }

    @Test
    public void testDynamicVariadicOneScalarOneArrayOneVariadic() {
        var root = parseNode("testDynamicVariadicOneScalarOneArrayOneVariadic", (b) -> {
            b.beginRoot();
            b.beginReturn();

            b.beginVariadicAddIntLArr();
            b.emitLoadConstant(7L);
            b.emitLoadConstant(new long[]{1L, 2L, 3L});

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();
            b.endVariadicAddIntLArr();

            b.endReturn();
            b.endRoot();
        });
        assertEquals(2L * 7L + 1L * 7L, root.getCallTarget().call());
    }

    @Test
    public void testDynamicVariadicOneScalarMultipleVariadic() {
        /**
         * Note regarding the test name: 'Multiple variadic' means one @Variadic argument, but
         * multiple calls to operations returning @Variadic. 'Multiple scalar' or 'multiple array'
         * really means that the operation has multiples of those arguments.
         */
        var root = parseNode("testDynamicVariadicOneScalarMultipleVariadic", (b) -> {
            b.beginRoot();
            b.beginReturn();

            b.beginVariadicAddInt();
            b.emitLoadConstant(5L);

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();
            b.endVariadicAddInt();

            b.endReturn();
            b.endRoot();
        });
        assertEquals(2L * 5L + 1L * 5L + 2L * 5L + 1L * 5L + 2L * 5L + 1L * 5L, root.getCallTarget().call());
    }

    @Test
    public void testDynamicVariadicOneArrayMultipleVariadic() {
        var root = parseNode("testDynamicVariadicOneArrayMultipleVariadic", (b) -> {
            b.beginRoot();
            b.beginReturn();

            b.beginVariadicAddLArr();
            b.emitLoadConstant(new long[]{1L, 2L, 3L});

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();
            b.endVariadicAddLArr();

            b.endReturn();
            b.endRoot();
        });
        assertEquals(2L + 1L + 2L + 1L + 2L + 1L, root.getCallTarget().call());
    }

    @Test
    public void testDynamicVariadicOneScalarOneArrayMultipleVariadic() {
        var root = parseNode("testDynamicVariadicOneScalarOneArrayMultipleVariadic", (b) -> {
            b.beginRoot();
            b.beginReturn();

            b.beginVariadicAddIntLArr();
            b.emitLoadConstant(7L);
            b.emitLoadConstant(new long[]{1L, 2L, 3L});

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();
            b.endVariadicAddIntLArr();

            b.endReturn();
            b.endRoot();
        });
        assertEquals(2L * 7L + 1L * 7L + 2L * 7L + 1L * 7L + 2L * 7L + 1L * 7L, root.getCallTarget().call());
    }

    @Test
    public void testDynamicVariadicMultipleScalarMultipleArrayOneVariadic() {
        var root = parseNode("testDynamicVariadicMultipleScalarMultipleArrayOneVariadic", (b) -> {
            b.beginRoot();
            b.beginReturn();

            b.beginVariadicAddIntIntLArrLArr();
            b.emitLoadConstant(7L);
            b.emitLoadConstant(2L);
            b.emitLoadConstant(new long[]{1L, 2L, 3L});
            b.emitLoadConstant(new long[]{4L, 5L, 6L});

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();
            b.endVariadicAddIntIntLArrLArr();

            b.endReturn();
            b.endRoot();
        });
        assertEquals(2L * 7L * 2L + 1L * 7L * 2L, root.getCallTarget().call());
    }

    @Test
    public void testDynamicVariadicMultipleScalarMultipleArrayMultipleVariadic() {
        var root = parseNode("testDynamicVariadicMultipleScalarMultipleArrayMultipleVariadic", (b) -> {
            b.beginRoot();
            b.beginReturn();

            b.beginVariadicAddIntIntLArrLArr();
            b.emitLoadConstant(7L);
            b.emitLoadConstant(2L);
            b.emitLoadConstant(new long[]{1L, 2L, 3L});
            b.emitLoadConstant(new long[]{4L, 5L, 6L});

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();

            b.beginDynamicVariadicNums();
            b.emitLoadConstant(3L);
            b.endDynamicVariadicNums();
            b.endVariadicAddIntIntLArrLArr();

            b.endReturn();
            b.endRoot();
        });
        assertEquals(2L * 7L * 2L + 1L * 7L * 2L + 2L * 7L * 2L + 1L * 7L * 2L + 2L * 7L * 2L + 1L * 7L * 2L, root.getCallTarget().call());
    }

    private static int countInstructions(BytecodeRootNode root, String name) throws AssertionError {
        int count = 0;
        for (Instruction instr : root.getBytecodeNode().getInstructions()) {
            if (instr.getName().equals(name)) {
                count++;
            }
        }
        return count;
    }

}
