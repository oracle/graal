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

import static com.oracle.truffle.api.bytecode.test.basic_interpreter.AbstractBasicInterpreterTest.ExpectedSourceTree.expectedSourceTree;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeEncodingException;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ExceptionHandler;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instruction.Argument;
import com.oracle.truffle.api.bytecode.Instruction.Argument.Kind;
import com.oracle.truffle.api.bytecode.SourceInformation;
import com.oracle.truffle.api.bytecode.SourceInformationTree;
import com.oracle.truffle.api.bytecode.test.AbstractInstructionTest;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * Tests basic features of the Bytecode DSL. Serves as a catch-all for functionality we just need a
 * few tests (and not a separate test class) for.
 */
@RunWith(Parameterized.class)
public class BasicInterpreterTest extends AbstractBasicInterpreterTest {

    public BasicInterpreterTest(TestRun run) {
        super(run);
    }

    private record ExpectedArgument(String name, Argument.Kind kind, Object value) {
    }

    private record ExpectedInstruction(String name, Integer bci, Boolean instrumented, ExpectedArgument[] arguments, Set<String> activeSpecializations) {

        private ExpectedInstruction withBci(Integer newBci) {
            return new ExpectedInstruction(name, newBci, instrumented, arguments, activeSpecializations);
        }

        static final class Builder {
            String name;
            Integer bci;
            Boolean instrumented;
            List<ExpectedArgument> arguments;
            Set<String> activeSpecializations;

            private Builder(String name) {
                this.name = name;
                this.arguments = new ArrayList<>();
                this.activeSpecializations = null;
            }

            Builder instrumented(Boolean newInstrumented) {
                this.instrumented = newInstrumented;
                return this;
            }

            Builder arg(String argName, Argument.Kind kind, Object value) {
                this.arguments.add(new ExpectedArgument(argName, kind, value));
                return this;
            }

            Builder specializations(String... newActiveSpecializations) {
                this.activeSpecializations = Set.of(newActiveSpecializations);
                return this;
            }

            ExpectedInstruction build() {
                return new ExpectedInstruction(name, bci, instrumented, arguments.toArray(new ExpectedArgument[0]), activeSpecializations);
            }
        }

    }

    private static ExpectedInstruction.Builder instr(String name) {
        return new ExpectedInstruction.Builder(name);
    }

    private static void assertInstructionsEqual(List<Instruction> actualInstructions, ExpectedInstruction... expectedInstructions) {
        if (actualInstructions.size() != expectedInstructions.length) {
            fail(String.format("Expected %d instructions, but %d found.\nExpected: %s.\nActual: %s", expectedInstructions.length, actualInstructions.size(), expectedInstructions, actualInstructions));
        }
        int bci = 0;
        for (int i = 0; i < expectedInstructions.length; i++) {
            assertInstructionEquals(actualInstructions.get(i), expectedInstructions[i].withBci(bci));
            bci = actualInstructions.get(i).getNextBytecodeIndex();
        }
    }

    private static void assertInstructionEquals(Instruction actual, ExpectedInstruction expected) {
        assertTrue(actual.getName().startsWith(expected.name));
        if (expected.bci != null) {
            assertEquals(expected.bci.intValue(), actual.getBytecodeIndex());
        }
        if (expected.instrumented != null) {
            assertEquals(expected.instrumented.booleanValue(), actual.isInstrumentation());
        }
        if (expected.arguments.length > 0) {
            Map<String, Argument> args = actual.getArguments().stream().collect(Collectors.toMap(Argument::getName, arg -> arg));
            for (ExpectedArgument expectedArgument : expected.arguments) {
                Argument actualArgument = args.get(expectedArgument.name);
                if (actualArgument == null) {
                    fail(String.format("Argument %s missing from instruction %s", expectedArgument.name, actual.getName()));
                }
                assertEquals(expectedArgument.kind, actualArgument.getKind());
                switch (expectedArgument.kind) {
                    case CONSTANT -> assertEquals(expectedArgument.value, actualArgument.asConstant());
                    case INTEGER -> assertEquals(expectedArgument.value, actualArgument.asInteger());
                    case BRANCH_PROFILE -> assertEquals((double) expectedArgument.value, actualArgument.asBranchProfile().getFrequency(), 0.0001d);
                    default -> throw new AssertionError(String.format("Testing arguments of kind %s not yet implemented", expectedArgument.kind));
                }
            }
        }

        if (expected.activeSpecializations != null) {
            List<Argument> nodeArgs = actual.getArguments().stream().filter(arg -> arg.getKind() == Kind.NODE_PROFILE).toList();
            assertEquals(1, nodeArgs.size());
            List<SpecializationInfo> specializations = nodeArgs.get(0).getSpecializationInfo();
            Set<String> activeSpecializations = specializations.stream() //
                            .filter(SpecializationInfo::isActive) //
                            .map(SpecializationInfo::getMethodName) //
                            .collect(Collectors.toSet());
            assertEquals(expected.activeSpecializations, activeSpecializations);
        }
    }

    @Test
    public void testAdd() {
        // return arg0 + arg1;

        RootCallTarget root = parse("add", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAdd();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, root.call(20L, 22L));
        assertEquals("foobar", root.call("foo", "bar"));
        assertEquals(100L, root.call(120L, -20L));
    }

    @Test
    public void testMax() {
        // if (arg0 < arg1) {
        // return arg1;
        // } else {
        // return arg0;
        // }

        RootCallTarget root = parse("max", b -> {
            b.beginRoot();
            b.beginIfThenElse();

            b.beginLess();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endLess();

            b.beginReturn();
            b.emitLoadArgument(1);
            b.endReturn();

            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();

            b.endIfThenElse();

            b.endRoot();
        });

        assertEquals(42L, root.call(42L, 13L));
        assertEquals(42L, root.call(42L, 13L));
        assertEquals(42L, root.call(42L, 13L));
        assertEquals(42L, root.call(13L, 42L));
    }

    @Test
    public void testIfThen() {
        // if (arg0 < 0) {
        // return 0;
        // }
        // return arg0;

        RootCallTarget root = parse("ifThen", b -> {
            b.beginRoot();
            b.beginIfThen();

            b.beginLess();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLess();

            emitReturn(b, 0);

            b.endIfThen();

            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(0L, root.call(-2L));
        assertEquals(0L, root.call(-1L));
        assertEquals(0L, root.call(0L));
        assertEquals(1L, root.call(1L));
        assertEquals(2L, root.call(2L));
    }

    @Test
    public void testConditional() {
        // return arg0 < 0 ? 0 : arg0;

        RootCallTarget root = parse("conditional", b -> {
            b.beginRoot();

            b.beginReturn();

            b.beginConditional();

            b.beginLess();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLess();

            b.emitLoadConstant(0L);

            b.emitLoadArgument(0);

            b.endConditional();

            b.endReturn();

            b.endRoot();
        });

        assertEquals(0L, root.call(-2L));
        assertEquals(0L, root.call(-1L));
        assertEquals(0L, root.call(0L));
        assertEquals(1L, root.call(1L));
        assertEquals(2L, root.call(2L));
    }

    @Test
    public void testBadConditionValues() {
        RootCallTarget badIfThen = parse("badConditionIfThen", b -> {
            b.beginRoot();
            b.beginIfThen();
            b.emitLoadArgument(0);
            b.emitLoadConstant(42L);
            b.endIfThen();
            b.endRoot();
        });
        RootCallTarget badIfThenElse = parse("badConditionIfThenElse", b -> {
            b.beginRoot();
            b.beginIfThenElse();
            b.emitLoadArgument(0);
            b.emitLoadConstant(42L);
            b.emitLoadConstant(42L);
            b.endIfThenElse();
            b.endRoot();
        });
        RootCallTarget badWhile = parse("badConditionWhile", b -> {
            b.beginRoot();
            b.beginWhile();
            b.emitLoadArgument(0);
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endWhile();
            b.endRoot();
        });
        RootCallTarget badConditional = parse("badConditional", b -> {
            b.beginRoot();
            b.beginConditional();
            b.emitLoadArgument(0);
            b.emitLoadConstant(42L);
            b.emitLoadConstant(42L);
            b.endConditional();
            b.endRoot();
        });

        assertThrows(ClassCastException.class, () -> badIfThen.call(0));
        assertThrows(ClassCastException.class, () -> badIfThenElse.call("not a boolean"));
        assertThrows(ClassCastException.class, () -> badWhile.call(42L));
        assertThrows(ClassCastException.class, () -> badConditional.call(3.14f));

        assertThrows(NullPointerException.class, () -> badIfThen.call(new Object[]{null}));
        assertThrows(NullPointerException.class, () -> badIfThenElse.call(new Object[]{null}));
        assertThrows(NullPointerException.class, () -> badWhile.call(new Object[]{null}));
        assertThrows(NullPointerException.class, () -> badConditional.call(new Object[]{null}));
    }

    @Test
    public void testConditionalBranchSpBalancing() {
        // For conditionals, we use a special merge instruction for BE. The builder needs to
        // correctly update the stack height at the merge. Currently, we only validate that the sp
        // is balanced between branches and labels, so we use those to check that the sp is
        // correctly updated.

        // goto lbl;
        // arg0 ? 1 else 2
        // lbl:
        // return 0
        RootCallTarget root = parse("conditional", b -> {
            b.beginRoot();
            b.beginBlock();

            BytecodeLabel lbl = b.createLabel();

            b.emitBranch(lbl);

            b.beginConditional();
            b.emitLoadArgument(0);
            b.emitLoadConstant(1L);
            b.emitLoadConstant(2L);
            b.endConditional();

            b.emitLabel(lbl);

            emitReturn(b, 0L);

            b.endBlock();
            b.endRoot();
        });

        assertEquals(0L, root.call(true));
        assertEquals(0L, root.call(false));
    }

    @Test
    public void testSumLoop() {
        // i = 0; j = 0;
        // while (i < arg0) { j = j + i; i = i + 1; }
        // return j;

        RootCallTarget root = parse("sumLoop", b -> {
            b.beginRoot();
            BytecodeLocal locI = b.createLocal();
            BytecodeLocal locJ = b.createLocal();

            b.beginStoreLocal(locI);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginStoreLocal(locJ);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLess();
            b.emitLoadLocal(locI);
            b.emitLoadArgument(0);
            b.endLess();

            b.beginBlock();
            b.beginStoreLocal(locJ);
            b.beginAdd();
            b.emitLoadLocal(locJ);
            b.emitLoadLocal(locI);
            b.endAdd();
            b.endStoreLocal();

            b.beginStoreLocal(locI);
            b.beginAdd();
            b.emitLoadLocal(locI);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();
            b.endBlock();
            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(locJ);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(45L, root.call(10L));
    }

    @Test
    public void testBadLoadConstant() {
        assertThrowsWithMessage("Invalid builder operation argument: The constant parameter must not be null.",
                        IllegalArgumentException.class, () -> {
                            parse("badLoadConstant", b -> {
                                b.beginRoot();
                                b.beginReturn();
                                b.emitLoadConstant(null);
                                b.endReturn();
                                b.endRoot();
                            });
                        });
    }

    @Test
    public void testBadLoadConstant2() {
        assertThrowsWithMessage("Invalid builder operation argument: Nodes cannot be used as constants.",
                        IllegalArgumentException.class, () -> {
                            parse("badLoadConstant2", b -> {
                                b.beginRoot();
                                b.beginReturn();
                                b.emitLoadConstant(new Node() {
                                });
                                b.endReturn();
                                b.endRoot();
                            });
                        });
    }

    @Test
    public void testTryCatch() {
        // try {
        // if (arg0 < 0) throw arg0+1
        // } catch ex {
        // return ex.value;
        // }
        // return 0;

        RootCallTarget root = parse("tryCatch", b -> {
            b.beginRoot();

            b.beginTryCatch();

            b.beginIfThen();
            b.beginLess();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLess();

            b.beginThrowOperation();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endThrowOperation();

            b.endIfThen();

            b.beginReturn();
            b.beginReadExceptionOperation();
            b.emitLoadException();
            b.endReadExceptionOperation();
            b.endReturn();

            b.endTryCatch();

            emitReturn(b, 0);

            b.endRoot();
        });

        assertEquals(-42L, root.call(-43L));
        assertEquals(0L, root.call(1L));
    }

    @Test
    public void testTryCatchLoadExceptionUnevenStack() {
        // try {
        // throw arg0+1
        // } catch ex {
        // 1 + 2 + { return ex.value; 3 }
        // }

        RootCallTarget root = parse("tryCatch", b -> {
            b.beginRoot();

            b.beginTryCatch();

            b.beginThrowOperation();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endThrowOperation();

            b.beginAdd();
            b.emitLoadConstant(1L);
            b.beginAdd();
            b.emitLoadConstant(2L);
            b.beginBlock();
            b.beginReturn();
            b.beginReadExceptionOperation();
            b.emitLoadException();
            b.endReadExceptionOperation();
            b.endReturn();
            b.emitLoadConstant(3L);
            b.endBlock();
            b.endAdd();
            b.endAdd();

            b.endTryCatch();

            b.endRoot();
        });

        assertEquals(-42L, root.call(-43L));
    }

    @Test
    public void testTryCatchNestedInTry() {
        // try {
        // try {
        // if (arg0 < 1) throw arg0
        // } catch ex2 {
        // if (arg0 < 0) throw arg0 - 100
        // return 42;
        // }
        // throw arg0;
        // } catch ex1 {
        // return ex1.value
        // }
        RootCallTarget root = parse("tryCatch", b -> {
            b.beginRoot();

            b.beginTryCatch();

            b.beginBlock(); // begin outer try
            b.beginTryCatch();

            b.beginIfThen(); // begin inner try
            b.beginLess();
            b.emitLoadArgument(0);
            b.emitLoadConstant(1L);
            b.endLess();
            b.beginThrowOperation();
            b.emitLoadArgument(0);
            b.endThrowOperation();
            b.endIfThen(); // end inner try

            b.beginBlock(); // begin inner catch

            b.beginIfThen();
            b.beginLess();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLess();
            b.beginThrowOperation();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.emitLoadConstant(-100L);
            b.endAdd();
            b.endThrowOperation();
            b.endIfThen();

            emitReturn(b, 42L);
            b.endBlock(); // end inner catch

            b.endTryCatch();

            b.beginThrowOperation();
            b.emitLoadArgument(0);
            b.endThrowOperation();

            b.endBlock(); // end outer try

            b.beginReturn(); // begin outer catch
            b.beginReadExceptionOperation();
            b.emitLoadException();
            b.endReadExceptionOperation();
            b.endReturn(); // end outer catch

            b.endTryCatch();
            b.endRoot();
        });

        assertEquals(-101L, root.call(-1L));
        assertEquals(42L, root.call(0L));
        assertEquals(123L, root.call(123L));
    }

    @Test
    public void testTryCatchNestedInCatch() {
        // try {
        // throw arg0
        // } catch ex1 {
        // try {
        // if (arg0 < 0) throw -1
        // return 42;
        // } catch ex2 {
        // return 123;
        // }
        // }
        RootCallTarget root = parse("tryCatch", b -> {
            b.beginRoot();

            b.beginTryCatch();

            b.beginThrowOperation(); // begin outer try
            b.emitLoadArgument(0);
            b.endThrowOperation(); // end outer try

            b.beginTryCatch(); // begin outer catch

            b.beginBlock(); // begin inner try
            b.beginIfThen();
            b.beginLess();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLess();
            b.beginThrowOperation();
            b.emitLoadConstant(-1L);
            b.endThrowOperation();
            b.endIfThen();
            emitReturn(b, 42L);
            b.endBlock(); // end inner try

            b.beginBlock(); // begin inner catch
            emitReturn(b, 123L);
            b.endBlock(); // end inner catch

            b.endTryCatch(); // end outer catch

            b.endTryCatch();
            b.endRoot();
        });

        assertEquals(123L, root.call(-100L));
        assertEquals(42L, root.call(0L));
        assertEquals(42L, root.call(1L));
    }

    @Test
    public void testBadLoadExceptionUsage1() {
        assertThrowsWithMessage("LoadException can only be used in the catch operation of a TryCatch/TryCatchOtherwise operation in the current root.",
                        IllegalStateException.class, () -> {
                            parse("badLoadExceptionUsage1", b -> {
                                b.beginRoot();
                                b.beginReturn();
                                b.emitLoadException();
                                b.endReturn();
                                b.endRoot();
                            });
                        });
    }

    @Test
    public void testMissingEnd1() {
        assertThrowsWithMessage("Unexpected parser end - there are still operations on the stack. Did you forget to end them?", IllegalStateException.class, () -> {
            parse("missingEnd", b -> {
                b.beginRoot();
            });
        });
    }

    @Test
    public void testMissingEnd2() {
        assertThrowsWithMessage("Unexpected parser end - there are still operations on the stack. Did you forget to end them?", IllegalStateException.class, () -> {
            parse("missingEnd", b -> {
                b.beginRoot();
                b.beginBlock();
                b.beginIfThen();
            });
        });
    }

    @Test
    public void testBadLoadExceptionUsage2() {
        assertThrowsWithMessage("LoadException can only be used in the catch operation of a TryCatch/TryCatchOtherwise operation in the current root.", IllegalStateException.class, () -> {
            parse("badLoadExceptionUsage2", b -> {
                b.beginRoot();
                b.beginTryCatch();
                b.beginReturn();
                b.emitLoadException();
                b.endReturn();
                b.emitVoidOperation();
                b.endTryCatch();
                b.endRoot();
            });
        });
    }

    @Test
    public void testBadLoadExceptionUsage3() {
        assertThrowsWithMessage("LoadException can only be used in the catch operation of a TryCatch/TryCatchOtherwise operation in the current root.", IllegalStateException.class, () -> {
            parse("badLoadExceptionUsage3", b -> {
                b.beginRoot();
                b.beginTryCatchOtherwise(() -> b.emitVoidOperation());
                b.beginReturn();
                b.emitLoadException();
                b.endReturn();
                b.emitVoidOperation();
                b.endTryCatchOtherwise();
                b.endRoot();
            });
        });
    }

    @Test
    public void testBadLoadExceptionUsage4() {
        assertThrowsWithMessage("LoadException can only be used in the catch operation of a TryCatch/TryCatchOtherwise operation in the current root.", IllegalStateException.class, () -> {
            parse("badLoadExceptionUsage4", b -> {
                b.beginRoot();
                b.beginTryCatchOtherwise(() -> b.emitLoadException());
                b.emitVoidOperation();
                b.emitVoidOperation();
                b.endTryCatchOtherwise();
                b.endRoot();
            });
        });
    }

    @Test
    public void testBadLoadExceptionUsage5() {
        assertThrowsWithMessage("LoadException can only be used in the catch operation of a TryCatch/TryCatchOtherwise operation in the current root.", IllegalStateException.class, () -> {
            parse("badLoadExceptionUsage5", b -> {
                b.beginRoot();
                b.beginTryFinally(() -> b.emitLoadException());
                b.emitVoidOperation();
                b.endTryFinally();
                b.endRoot();
            });
        });
    }

    @Test
    public void testBadLoadExceptionUsage6() {
        assertThrowsWithMessage("LoadException can only be used in the catch operation of a TryCatch/TryCatchOtherwise operation in the current root.", IllegalStateException.class, () -> {
            parse("testBadLoadExceptionUsage6", b -> {
                b.beginRoot();
                b.beginTryCatch();

                b.emitVoidOperation();

                b.beginBlock();
                b.beginRoot();
                b.emitLoadException();
                b.endRoot();
                b.endBlock();

                b.endTryCatch();
                b.endRoot();
            });
        });
    }

    @Test
    public void testVariableBoxingElim() {
        // local0 = 0;
        // local1 = 0;
        // while (local0 < 100) {
        // local1 = box(local1) + local0;
        // local0 = local0 + 1;
        // }
        // return local1;

        RootCallTarget root = parse("variableBoxingElim", b -> {
            b.beginRoot();

            BytecodeLocal local0 = b.createLocal();
            BytecodeLocal local1 = b.createLocal();

            b.beginStoreLocal(local0);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginStoreLocal(local1);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();

            b.beginLess();
            b.emitLoadLocal(local0);
            b.emitLoadConstant(100L);
            b.endLess();

            b.beginBlock();

            b.beginStoreLocal(local1);
            b.beginAdd();
            b.beginAlwaysBoxOperation();
            b.emitLoadLocal(local1);
            b.endAlwaysBoxOperation();
            b.emitLoadLocal(local0);
            b.endAdd();
            b.endStoreLocal();

            b.beginStoreLocal(local0);
            b.beginAdd();
            b.emitLoadLocal(local0);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            b.endBlock();

            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(local1);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(4950L, root.call());
    }

    @Test
    public void testUndeclaredLabel() {
        // goto lbl;
        AbstractInstructionTest.assertFails(() -> {
            parse("undeclaredLabel", b -> {
                b.beginRoot();
                BytecodeLabel lbl = b.createLabel();
                b.emitBranch(lbl);
                b.endRoot();
            });
        }, IllegalStateException.class, (e) -> {
            assertTrue(e.getMessage(), e.getMessage().contains("ended without emitting one or more declared labels."));
        });
    }

    @Test
    public void testUnusedLabel() {
        // lbl:
        // return 42;

        RootCallTarget root = parse("unusedLabel", b -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();
            b.emitLabel(lbl);
            emitReturn(b, 42);
            b.endRoot();
        });

        assertEquals(42L, root.call());
    }

    @Test
    public void testTeeLocal() {
        // tee(local, 1L);
        // return local;

        RootCallTarget root = parse("teeLocal", b -> {
            b.beginRoot();

            BytecodeLocal local = b.createLocal();

            b.beginTeeLocal(local);
            b.emitLoadConstant(1L);
            b.endTeeLocal();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call());
    }

    @Test
    public void testTeeLocalDifferentTypes() {
        // tee(local, arg0);
        // return local;

        RootCallTarget root = parse("teeLocal", b -> {
            b.beginRoot();

            BytecodeLocal local = b.createLocal();

            b.beginTeeLocal(local);
            b.emitLoadArgument(0);
            b.endTeeLocal();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call(1L));
        assertEquals(42, root.call(42));
        assertEquals((short) 12, root.call((short) 12));
        assertEquals((byte) 2, root.call((byte) 2));
        assertEquals(true, root.call(true));
        assertEquals(3.14f, root.call(3.14f));
        assertEquals(4.0d, root.call(4.0d));
        assertEquals("hello", root.call("hello"));
    }

    @Test
    public void testTeeLargeLocal() {
        // local0; local1; local2; ...; local63;
        // tee(local64, 1);
        // return local;

        RootCallTarget root = parse("teeLocal", b -> {
            b.beginRoot();

            for (int i = 0; i < 64; i++) {
                b.createLocal();
            }
            BytecodeLocal local64 = b.createLocal();

            b.beginTeeLocal(local64);
            b.emitLoadConstant(1L);
            b.endTeeLocal();

            b.beginReturn();
            b.emitLoadLocal(local64);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call());
    }

    @Test
    public void testTeeLocalRange() {
        // teeRange([local1, local2], [1, 2]));
        // return local2;

        RootCallTarget root = parse("teeLocalRange", b -> {
            b.beginRoot();

            BytecodeLocal local1 = b.createLocal();
            BytecodeLocal local2 = b.createLocal();

            b.beginTeeLocalRange(new BytecodeLocal[]{local1, local2});
            b.emitLoadConstant(new long[]{1L, 2L});
            b.endTeeLocalRange();

            b.beginReturn();
            b.emitLoadLocal(local2);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(2L, root.call());
    }

    @Test
    public void testTeeLocalRangeDifferentTypes() {
        // teeRange([local1, local2, ..., local8], arg0)
        // return local8

        RootCallTarget root = parse("teeLocalRange", b -> {
            b.beginRoot();

            BytecodeLocal local1 = b.createLocal();
            BytecodeLocal local2 = b.createLocal();
            BytecodeLocal local3 = b.createLocal();
            BytecodeLocal local4 = b.createLocal();
            BytecodeLocal local5 = b.createLocal();
            BytecodeLocal local6 = b.createLocal();
            BytecodeLocal local7 = b.createLocal();
            BytecodeLocal local8 = b.createLocal();

            b.beginTeeLocalRange(new BytecodeLocal[]{local1, local2, local3, local4, local5, local6, local7, local8});
            b.emitLoadArgument(0);
            b.endTeeLocalRange();

            b.beginReturn();
            b.emitLoadLocal(local8);
            b.endReturn();

            b.endRoot();
        });
        Object[] arg0 = new Object[]{1L, 42, (short) 12, (byte) 2, true, 3.14f, 4.0d, "hello"};
        assertEquals("hello", root.call(new Object[]{arg0}));
    }

    @Test
    public void testTeeLocalRangeEmptyRange() {
        // teeRange([], []));
        // return 42;

        RootCallTarget root = parse("teeLocalRangeEmptyRange", b -> {
            b.beginRoot();

            b.beginTeeLocalRange(new BytecodeLocal[]{});
            b.emitLoadConstant(new long[]{});
            b.endTeeLocalRange();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, root.call());
    }

    @Test
    public void testTeeMaterializedLocalDifferentTypes() {
        // function inner(frame, arg0) {
        // tee(local, arg0);
        // }
        // inner(materialize(), arg0)
        // return local;

        RootCallTarget root = parse("teeLocal", b -> {
            b.beginRoot();

            BytecodeLocal local = b.createLocal();

            b.beginRoot();
            b.beginTeeMaterializedLocal(local);
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endTeeMaterializedLocal();
            BasicInterpreter inner = b.endRoot();

            b.beginCall(inner);
            b.emitMaterializeFrame();
            b.emitLoadArgument(0);
            b.endCall();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call(1L));
        assertEquals(42, root.call(42));
        assertEquals((short) 12, root.call((short) 12));
        assertEquals((byte) 2, root.call((byte) 2));
        assertEquals(true, root.call(true));
        assertEquals(3.14f, root.call(3.14f));
        assertEquals(4.0d, root.call(4.0d));
        assertEquals("hello", root.call("hello"));
    }

    @Test
    public void testTeeMaterializedLocalDifferentTypesSameRoot() {
        // tee(local, materialize(), arg0);
        // return local;

        RootCallTarget root = parse("teeLocal", b -> {
            b.beginRoot();

            BytecodeLocal local = b.createLocal();

            b.beginTeeMaterializedLocal(local);
            b.emitMaterializeFrame();
            b.emitLoadArgument(0);
            b.endTeeMaterializedLocal();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call(1L));
        assertEquals(42, root.call(42));
        assertEquals((short) 12, root.call((short) 12));
        assertEquals((byte) 2, root.call((byte) 2));
        assertEquals(true, root.call(true));
        assertEquals(3.14f, root.call(3.14f));
        assertEquals(4.0d, root.call(4.0d));
        assertEquals("hello", root.call("hello"));
    }

    @Test
    public void testAddConstant() {
        // return 40 + arg0
        RootCallTarget root = parse("addConstant", b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAddConstantOperation(40L);
            b.emitLoadArgument(0);
            b.endAddConstantOperation();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.call(2L));
    }

    @Test
    public void testAddConstantAtEnd() {
        // return arg0 + 40
        RootCallTarget root = parse("addConstantAtEnd", b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAddConstantOperationAtEnd();
            b.emitLoadArgument(0);
            b.endAddConstantOperationAtEnd(40L);
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.call(2L));
    }

    @Test
    public void testNestedFunctions() {
        // return (() -> return 1)();

        RootCallTarget root = parse("nestedFunctions", b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginInvoke();
            b.beginRoot();
            emitReturn(b, 1);
            BasicInterpreter innerRoot = b.endRoot();
            b.emitLoadConstant(innerRoot);
            b.endInvoke();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(1L, root.call());
    }

    @Test
    public void testMultipleNestedFunctions() {
        // return ({
        // x = () -> return 1
        // y = () -> return 2
        // arg0 ? x : y
        // })();

        RootCallTarget root = parse("multipleNestedFunctions", b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginInvoke();
            b.beginRoot();
            emitReturn(b, 1);
            BasicInterpreter x = b.endRoot();

            b.beginRoot();
            emitReturn(b, 2);
            BasicInterpreter y = b.endRoot();

            b.beginConditional();
            b.emitLoadArgument(0);
            b.emitLoadConstant(x);
            b.emitLoadConstant(y);
            b.endConditional();
            b.endInvoke();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(1L, root.call(true));
        assertEquals(2L, root.call(false));
    }

    @Test
    public void testMaterializedFrameAccesses() {
        // x = 41
        // f = materialize()
        // f.x = f.x + 1
        // return x

        RootCallTarget root = parse("materializedFrameAccesses", b -> {
            b.beginRoot();

            BytecodeLocal x = b.createLocal();
            BytecodeLocal f = b.createLocal();

            b.beginStoreLocal(x);
            b.emitLoadConstant(41L);
            b.endStoreLocal();

            b.beginStoreLocal(f);
            b.emitMaterializeFrame();
            b.endStoreLocal();

            b.beginStoreLocalMaterialized(x);

            b.emitLoadLocal(f);

            b.beginAdd();
            b.beginLoadLocalMaterialized(x);
            b.emitLoadLocal(f);
            b.endLoadLocalMaterialized();
            b.emitLoadConstant(1L);
            b.endAdd();

            b.endStoreLocalMaterialized();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, root.call());
    }

    /*
     * In this test we check that access to outer locals works and the liveness validation code is
     * triggered (if available).
     */
    @Test
    public void testMaterializedFrameAccesses2() {
        // z = 38
        // y = 39
        // x = 40
        // function f() {
        // padding
        // x = x + 1;
        // return x + 1;
        // }
        // f(materialize());

        BasicInterpreter node = parseNode("materializedFrameAccesses2", b -> {
            b.beginRoot();

            BytecodeLocal z = b.createLocal();
            BytecodeLocal y = b.createLocal();
            BytecodeLocal x = b.createLocal();

            // z = 38
            b.beginStoreLocal(z);
            b.emitLoadConstant(38L);
            b.endStoreLocal();

            // y = 39
            b.beginStoreLocal(y);
            b.emitLoadConstant(39L);
            b.endStoreLocal();

            // x = 40
            b.beginStoreLocal(x);
            b.emitLoadConstant(40L);
            b.endStoreLocal();

            b.beginRoot();

            // add some dummy operations to make the bci
            // of the inner method incompatible with the outer.
            for (int i = 0; i < 100; i++) {
                b.emitVoidOperation();
            }

            // x = x + 1;
            b.beginStoreLocalMaterialized(x);
            b.emitLoadArgument(0); // materializedFrame
            b.beginAdd();
            b.beginLoadLocalMaterialized(x);
            b.emitLoadArgument(0); // materializedFrame
            b.endLoadLocalMaterialized();
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocalMaterialized();

            // return x + 1;
            b.beginReturn();
            b.beginAdd();

            b.emitLoadConstant(1L);

            b.beginLoadLocalMaterialized(x);
            b.emitLoadArgument(0); // materializedFrame
            b.endLoadLocalMaterialized();

            b.endAdd();
            b.endReturn();

            BasicInterpreter callTarget = b.endRoot();

            b.beginReturn();
            b.beginCall(callTarget);
            b.emitMaterializeFrame();
            b.endCall();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call());
        // Force interpreter to cached and run again (in case it has uncached).
        for (BytecodeRootNode i : node.getRootNodes().getNodes()) {
            i.getBytecodeNode().setUncachedThreshold(0);
        }
        assertEquals(42L, node.getCallTarget().call());
        // Run again, in case the interpreter quickened to BE instructions.
        assertEquals(42L, node.getCallTarget().call());
    }

    /*
     * In this test we check that accessing a dead local throws an assertion.
     */
    @Test
    @SuppressWarnings("try")
    public void testMaterializedFrameAccessesDeadVariable() {
        // @formatter:off
        // {
        //   x = 41;
        //   function storeX() {
        //     x = 42;
        //   }
        //   function readX() {
        //     return x;
        //   }
        //   yield materialize(); // x is live here
        // }
        // {
        //   y = -1;
        //   yield materialize(); // x is dead here
        // }

        // @formatter:on

        // The interpreter can only check liveness if it stores the bci in the frame.
        assumeTrue(run.storesBciInFrame());

        // This test relies on an assertion. Explicitly open a context with compilation disabled.
        try (Context c = createContextWithCompilationDisabled()) {
            BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
                b.beginRoot();

                b.beginBlock();
                // x = 41
                BytecodeLocal x = b.createLocal();
                b.beginStoreLocal(x);
                b.emitLoadConstant(41L);
                b.endStoreLocal();

                // function storeX
                b.beginRoot();
                // x = 42L;
                b.beginStoreLocalMaterialized(x);
                b.emitLoadArgument(0); // materializedFrame
                b.emitLoadConstant(42L);
                b.endStoreLocalMaterialized();
                b.endRoot();

                // function readX
                b.beginRoot();
                // return x;
                b.beginReturn();
                b.beginLoadLocalMaterialized(x);
                b.emitLoadArgument(0); // materializedFrame
                b.endLoadLocalMaterialized();
                b.endReturn();
                b.endRoot();

                b.beginYield();
                b.emitMaterializeFrame();
                b.endYield();
                b.endBlock();

                b.beginBlock();
                // y = -1
                BytecodeLocal y = b.createLocal();
                b.beginStoreLocal(y);
                b.emitLoadConstant(-1L);
                b.endStoreLocal();
                b.beginYield();
                b.emitMaterializeFrame();
                b.endYield();
                b.endBlock();

                b.endRoot();
            });

            BasicInterpreter outer = nodes.getNode(0);
            BasicInterpreter storeX = nodes.getNode(1);
            BasicInterpreter readX = nodes.getNode(2);

            // Run in a loop three times: once uncached, once cached, and once quickened.
            for (int i = 0; i < 3; i++) {
                ContinuationResult cont = (ContinuationResult) outer.getCallTarget().call();
                MaterializedFrame materializedFrame = (MaterializedFrame) cont.getResult();
                storeX.getCallTarget().call(materializedFrame);
                assertEquals(42L, readX.getCallTarget().call(materializedFrame));

                cont = (ContinuationResult) cont.continueWith(null);
                MaterializedFrame materializedFrame2 = (MaterializedFrame) cont.getResult();
                assertThrows(IllegalArgumentException.class, () -> storeX.getCallTarget().call(materializedFrame2));
                assertThrows(IllegalArgumentException.class, () -> readX.getCallTarget().call(materializedFrame2));

                // Ensure next iteration is cached.
                outer.getBytecodeNode().setUncachedThreshold(0);
                storeX.getBytecodeNode().setUncachedThreshold(0);
                readX.getBytecodeNode().setUncachedThreshold(0);
            }
        }

    }

    private static Context createContextWithCompilationDisabled() {
        var builder = Context.newBuilder(BytecodeDSLTestLanguage.ID);
        if (TruffleTestAssumptions.isOptimizingRuntime()) {
            builder.option("engine.Compilation", "false");
        }
        Context result = builder.build();
        result.enter();
        return result;
    }

    /*
     * In this test we check that accessing a local from the wrong frame throws an assertion.
     */
    @Test
    public void testMaterializedFrameAccessesBadFrame() {
        // @formatter:off
        // function f() {
        //   x = 41;
        //   yield materialize();
        //   function storeX() {
        //     x = 42;
        //   }
        //   function readX() {
        //     return x;
        //   }
        // }
        // function g() {
        //   y = -1;
        //   yield materialize();
        // }
        // @formatter:on

        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
            // function f
            b.beginRoot();
            b.beginBlock();
            // x = 41
            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(41L);
            b.endStoreLocal();

            // function storeX
            b.beginRoot();
            // x = 42L;
            b.beginStoreLocalMaterialized(x);
            b.emitLoadArgument(0); // materializedFrame
            b.emitLoadConstant(42L);
            b.endStoreLocalMaterialized();
            b.endRoot();

            // function readX
            b.beginRoot();
            // return x;
            b.beginReturn();
            b.beginLoadLocalMaterialized(x);
            b.emitLoadArgument(0); // materializedFrame
            b.endLoadLocalMaterialized();
            b.endReturn();
            b.endRoot();

            b.beginYield();
            b.emitMaterializeFrame();
            b.endYield();
            b.endBlock();
            b.endRoot();

            // function g
            b.beginRoot();
            b.beginBlock();
            // y = -1
            BytecodeLocal y = b.createLocal();
            b.beginStoreLocal(y);
            b.emitLoadConstant(-1L);
            b.endStoreLocal();
            b.beginYield();
            b.emitMaterializeFrame();
            b.endYield();
            b.endBlock();
            b.endRoot();
        });

        BasicInterpreter f = nodes.getNode(0);
        BasicInterpreter storeX = nodes.getNode(1);
        BasicInterpreter readX = nodes.getNode(2);
        BasicInterpreter g = nodes.getNode(3);

        // Run in a loop three times: once uncached, once cached, and once quickened (if available).
        for (int i = 0; i < 3; i++) {
            // Using f's frame
            ContinuationResult cont = (ContinuationResult) f.getCallTarget().call();
            MaterializedFrame materializedFrame = (MaterializedFrame) cont.getResult();
            storeX.getCallTarget().call(materializedFrame);
            assertEquals(42L, readX.getCallTarget().call(materializedFrame));

            // Using g's frame
            cont = (ContinuationResult) g.getCallTarget().call();
            MaterializedFrame materializedFrame2 = (MaterializedFrame) cont.getResult();
            assertThrows(IllegalArgumentException.class, () -> storeX.getCallTarget().call(materializedFrame2));
            assertThrows(IllegalArgumentException.class, () -> readX.getCallTarget().call(materializedFrame2));

            // Ensure next iteration is cached.
            f.getBytecodeNode().setUncachedThreshold(0);
            storeX.getBytecodeNode().setUncachedThreshold(0);
            readX.getBytecodeNode().setUncachedThreshold(0);
            g.getBytecodeNode().setUncachedThreshold(0);
        }
    }

    @Test
    public void testLocalsNonlocalRead() {
        BasicInterpreter node = parseNode("localsNonlocalRead", b -> {
            // x = 1
            // return (lambda: x)()
            b.beginRoot();

            BytecodeLocal xLoc = b.createLocal();

            b.beginStoreLocal(xLoc);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginReturn();

            b.beginInvoke();

            b.beginRoot();
            b.beginReturn();
            b.beginLoadLocalMaterialized(xLoc);
            b.emitLoadArgument(0);
            b.endLoadLocalMaterialized();
            b.endReturn();
            BasicInterpreter inner = b.endRoot();

            b.beginCreateClosure();
            b.emitLoadConstant(inner);
            b.endCreateClosure();

            b.endInvoke();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, node.getCallTarget().call());
    }

    @Test
    public void testLocalsNonlocalReadBoxingElimination() {
        /*
         * With BE, cached load.mat uses metadata from the outer root (e.g., tags array) to resolve
         * the type. If the outer root is uncached, this info can be unavailable, in which case a
         * cached inner root should should gracefully fall back to object loads.
         */
        assumeTrue(run.hasBoxingElimination() && run.hasUncachedInterpreter());
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
            // x = 1
            // return (lambda: x)()
            b.beginRoot();

            BytecodeLocal xLoc = b.createLocal();

            b.beginStoreLocal(xLoc);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginReturn();

            b.beginInvoke();

            b.beginRoot();
            b.beginReturn();
            b.beginLoadLocalMaterialized(xLoc);
            b.emitLoadArgument(0);
            b.endLoadLocalMaterialized();
            b.endReturn();
            BasicInterpreter inner = b.endRoot();

            b.beginCreateClosure();
            b.emitLoadConstant(inner);
            b.endCreateClosure();

            b.endInvoke();
            b.endReturn();

            b.endRoot();
        });

        BasicInterpreter outer = nodes.getNode(0);
        BasicInterpreter inner = nodes.getNode(1);

        inner.getBytecodeNode().setUncachedThreshold(0);

        assertEquals(1L, outer.getCallTarget().call());
    }

    @Test
    public void testLocalsNonlocalWriteBoxingElimination() {
        /*
         * With BE, store.mat should not break BE when the same type as the cached type is stored.
         */
        assumeTrue(run.hasBoxingElimination());
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
            // x = 1
            // if (arg0) (lambda: x = arg1)()
            // return x + 1
            b.beginRoot();

            BytecodeLocal xLoc = b.createLocal();

            b.beginStoreLocal(xLoc);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginRoot();
            b.beginStoreLocalMaterialized(xLoc);
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endStoreLocalMaterialized();
            BasicInterpreter inner = b.endRoot();

            b.beginIfThen();
            b.emitLoadArgument(0);
            b.beginInvoke();
            b.beginCreateClosure();
            b.emitLoadConstant(inner);
            b.endCreateClosure();
            b.emitLoadArgument(1);
            b.endInvoke();
            b.endIfThen();

            b.beginReturn();
            b.beginAdd();
            b.emitLoadLocal(xLoc);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endReturn();

            b.endRoot();
        });
        BasicInterpreter outer = nodes.getNode(0);
        outer.getBytecodeNode().setUncachedThreshold(0);

        assertEquals(2L, outer.getCallTarget().call(false, null));

        AbstractInstructionTest.assertInstructions(outer,
                        "load.constant$Long",
                        "store.local$Long$Long",
                        "load.argument$Boolean",
                        "branch.false$Boolean",
                        "load.constant",
                        "c.CreateClosure",
                        "load.argument",
                        "create.variadic",
                        "c.Invoke",
                        "pop",
                        "load.local$Long$unboxed",
                        "load.constant$Long",
                        "c.Add$AddLongs",
                        "return");

        assertEquals(42L, outer.getCallTarget().call(true, 41L));

        AbstractInstructionTest.assertInstructions(outer,
                        "load.constant$Long",
                        "store.local$Long$Long",
                        "load.argument$Boolean",
                        "branch.false$Boolean",
                        "load.constant",
                        "c.CreateClosure",
                        "load.argument",
                        "create.variadic",
                        "c.Invoke",
                        "pop$generic",
                        "load.local$Long$unboxed", // load is not affected by long store.
                        "load.constant$Long",
                        "c.Add$AddLongs",
                        "return");

        assertEquals("411", outer.getCallTarget().call(true, "41"));

        AbstractInstructionTest.assertInstructions(outer,
                        "load.constant$Long",
                        "store.local$Long$Long",
                        "load.argument$Boolean",
                        "branch.false$Boolean",
                        "load.constant",
                        "c.CreateClosure",
                        "load.argument",
                        "create.variadic",
                        "c.Invoke",
                        "pop$generic",
                        "load.local$generic", // load unquickens.
                        "load.constant",
                        "c.Add",
                        "return");
    }

    @Test
    public void testLocalsNonlocalWrite() {
        // x = 1;
        // ((x) -> x = 2)();
        // return x;

        RootCallTarget root = parse("localsNonlocalWrite", b -> {
            b.beginRoot();

            BytecodeLocal xLoc = b.createLocal();

            b.beginStoreLocal(xLoc);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginInvoke();

            b.beginRoot();

            b.beginStoreLocalMaterialized(xLoc);
            b.emitLoadArgument(0);
            b.emitLoadConstant(2L);
            b.endStoreLocalMaterialized();

            b.beginReturn();
            b.emitLoadNull();
            b.endReturn();

            BasicInterpreter inner = b.endRoot();

            b.beginCreateClosure();
            b.emitLoadConstant(inner);
            b.endCreateClosure();

            b.endInvoke();

            b.beginReturn();
            b.emitLoadLocal(xLoc);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(2L, root.call());
    }

    @Test
    public void testLocalsNonlocalDifferentFrameSizes() {
        /*
         * When validating/executing a materialized local access, the frame/root of the materialized
         * local should be used, not the frame/root of the instruction.
         */
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
            // x = 1
            // (lambda: x = x + 41)()
            // return x
            b.beginRoot();

            for (int i = 0; i < 100; i++) {
                b.createLocal();
            }
            BytecodeLocal xLoc = b.createLocal();

            b.beginStoreLocal(xLoc);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginRoot();
            b.beginStoreLocalMaterialized(xLoc);
            b.emitLoadArgument(0);
            b.beginAddConstantOperation(41L);
            b.beginLoadLocalMaterialized(xLoc);
            b.emitLoadArgument(0);
            b.endLoadLocalMaterialized();
            b.endAddConstantOperation();
            b.endStoreLocalMaterialized();
            BasicInterpreter inner = b.endRoot();

            b.beginInvoke();
            b.beginCreateClosure();
            b.emitLoadConstant(inner);
            b.endCreateClosure();
            b.endInvoke();

            b.beginReturn();
            b.emitLoadLocal(xLoc);
            b.endReturn();

            b.endRoot();
        });
        BasicInterpreter outer = nodes.getNode(0);

        assertEquals(42L, outer.getCallTarget().call());
    }

    @Test
    public void testVariadicZeroVarargs() {
        // return variadicOperation(7);

        RootCallTarget root = parse("variadicZeroVarargs", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginVariadicOperation();
            b.emitLoadConstant(7L);
            b.endVariadicOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(7L, root.call());
    }

    @Test
    public void testVariadicOneVarargs() {
        // return variadicOperation(7, "foo");

        RootCallTarget root = parse("variadicOneVarargs", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginVariadicOperation();
            b.emitLoadConstant(7L);
            b.emitLoadConstant("foo");
            b.endVariadicOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(8L, root.call());
    }

    @Test
    public void testVariadicFewVarargs() {
        // return variadicOperation(7, "foo", "bar", "baz");

        RootCallTarget root = parse("variadicFewVarargs", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginVariadicOperation();
            b.emitLoadConstant(7L);
            b.emitLoadConstant("foo");
            b.emitLoadConstant("bar");
            b.emitLoadConstant("baz");
            b.endVariadicOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(10L, root.call());
    }

    @Test
    public void testVariadicManyVarargs() {
        // return variadicOperation(7, [1330 args]);

        RootCallTarget root = parse("variadicManyVarArgs", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginVariadicOperation();
            b.emitLoadConstant(7L);
            for (int i = 0; i < 1330; i++) {
                b.emitLoadConstant("test");
            }
            b.endVariadicOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1337L, root.call());
    }

    public void testVariadicFallback() {
        // return variadicOperation(arg0, arg1, arg2);

        RootCallTarget root = parse("variadicFallback", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginVariadicOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.emitLoadArgument(2);
            b.endVariadicOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, root.call(40L, "foo", "bar"));
        assertEquals(2L, root.call("foo", "bar", "baz"));
    }

    @Test
    public void testVariadicTooFewArguments() {
        assertThrowsWithMessage("Operation VariadicOperation expected at least 1 child, but 0 provided. This is probably a bug in the parser.", IllegalStateException.class, () -> {
            parse("variadicTooFewArguments", b -> {
                b.beginRoot();

                b.beginReturn();
                b.beginVariadicOperation();
                b.endVariadicOperation();
                b.endReturn();

                b.endRoot();
            });
        });

    }

    @Test
    public void testValidationTooFewArguments() {
        assertThrowsWithMessage("Operation Add expected exactly 2 children, but 1 provided. This is probably a bug in the parser.", IllegalStateException.class, () -> {
            parse("validationTooFewArguments", b -> {
                b.beginRoot();

                b.beginReturn();
                b.beginAdd();
                b.emitLoadConstant(1L);
                b.endAdd();
                b.endReturn();

                b.endRoot();
            });
        });
    }

    @Test
    public void testValidationTooManyArguments() {
        assertThrowsWithMessage("Operation Add expected exactly 2 children, but 3 provided. This is probably a bug in the parser.", IllegalStateException.class, () -> {
            parse("validationTooManyArguments", b -> {
                b.beginRoot();

                b.beginReturn();
                b.beginAdd();
                b.emitLoadConstant(1L);
                b.emitLoadConstant(2L);
                b.emitLoadConstant(3L);
                b.endAdd();
                b.endReturn();

                b.endRoot();
            });
        });
    }

    @Test
    public void testValidationNotValueArgument() {
        assertThrowsWithMessage("Operation Add expected a value-producing child at position 0, but a void one was provided. ", IllegalStateException.class, () -> {
            parse("validationNotValueArgument", b -> {
                b.beginRoot();

                b.beginReturn();
                b.beginAdd();
                b.emitVoidOperation();
                b.emitLoadConstant(2L);
                b.endAdd();
                b.endReturn();

                b.endRoot();
            });
        });
    }

    @Test
    public void testShortCircuitingAllPass() {
        // return 1 && true && "test";

        RootCallTarget root = parse("shortCircuitingAllPass", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginScAnd();
            b.emitLoadConstant(1L);
            b.emitLoadConstant(true);
            b.emitLoadConstant("test");
            b.endScAnd();
            b.endReturn();

            b.endRoot();
        });

        assertEquals("test", root.call());
    }

    @Test
    public void testShortCircuitingLastFail() {
        // return 1 && "test" && 0;

        RootCallTarget root = parse("shortCircuitingLastFail", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginScAnd();
            b.emitLoadConstant(1L);
            b.emitLoadConstant("test");
            b.emitLoadConstant(0L);
            b.endScAnd();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(0L, root.call());
    }

    @Test
    public void testShortCircuitingFirstFail() {
        // return 0 && "test" && 1;

        RootCallTarget root = parse("shortCircuitingFirstFail", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginScAnd();
            b.emitLoadConstant(0L);
            b.emitLoadConstant("test");
            b.emitLoadConstant(1L);
            b.endScAnd();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(0L, root.call());
    }

    @Test
    public void testShortCircuitingNoChildren() {
        assertThrowsWithMessage("Operation ScAnd expected at least 1 child, but 0 provided. This is probably a bug in the parser.", IllegalStateException.class, () -> {
            parse("shortCircuitingNoChildren", b -> {
                b.beginRoot();

                b.beginReturn();
                b.beginScAnd();
                b.endScAnd();
                b.endReturn();

                b.endRoot();
            });
        });
    }

    @Test
    public void testShortCircuitingNonValueChild() {
        assertThrowsWithMessage("Operation ScAnd expected a value-producing child at position 1, but a void one was provided.", IllegalStateException.class, () -> {
            parse("shortCircuitingNonValueChild", b -> {
                b.beginRoot();

                b.beginReturn();
                b.beginScAnd();
                b.emitLoadConstant("test");
                b.emitVoidOperation();
                b.emitLoadConstant("tost");
                b.endScAnd();
                b.endReturn();

                b.endRoot();
            });
        });
    }

    @Test
    public void testEmptyBlock() {
        RootCallTarget root = parse("emptyBlock", b -> {
            b.beginRoot();

            b.beginBlock();
            b.beginBlock();
            b.endBlock();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();

            b.endRoot();
        });

        assertEquals(42L, root.call());
    }

    @Test
    public void testNoReturn() {
        RootCallTarget root = parse("noReturn", b -> {
            b.beginRoot();

            b.beginBlock();
            b.endBlock();

            b.endRoot();
        });

        assertNull(root.call());
    }

    @Test
    public void testNoReturnInABranch() {
        RootCallTarget root = parse("noReturn", b -> {
            b.beginRoot();

            b.beginIfThenElse();
            b.emitLoadArgument(0);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.beginBlock();
            b.endBlock();

            b.endIfThenElse();

            b.endRoot();
        });

        assertEquals(42L, root.call(true));
        assertNull(root.call(false));
    }

    @Test
    public void testBranchPastEnd() {
        RootCallTarget root = parse("noReturn", b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLabel label = b.createLabel();
            b.emitBranch(label);

            // skipped
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.emitLabel(label);
            b.endBlock();

            b.endRoot();
        });

        assertNull(root.call(false));
    }

    @Test
    public void testBranchIntoOuterRoot() {
        assertThrowsWithMessage("Branch must be targeting a label that is declared in an enclosing operation of the current root.", IllegalStateException.class, () -> {
            parse("branchIntoOuterRoot", b -> {
                b.beginRoot();
                b.beginBlock();
                BytecodeLabel lbl = b.createLabel();

                b.beginRoot();
                b.emitBranch(lbl);
                b.endRoot();

                b.emitLabel(lbl);
                b.endBlock();
                b.endRoot();
            });
        });
    }

    @Test
    public void testManyBytecodes() {
        BasicInterpreter node = parseNode("manyBytecodes", b -> {
            b.beginRoot();
            b.beginBlock();
            for (int i = 0; i < Short.MAX_VALUE * 2; i++) {
                b.emitLoadConstant(123L);
            }
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testManyConstants() {
        BasicInterpreter node = parseNode("manyConstants", b -> {
            b.beginRoot();
            b.beginBlock();
            for (int i = 0; i < Short.MAX_VALUE * 2; i++) {
                b.emitLoadConstant((long) i);
            }
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testManyNodes() {
        BasicInterpreter node = parseNode("manyNodes", b -> {
            b.beginRoot();
            b.beginBlock();
            for (int i = 0; i < Short.MAX_VALUE * 2; i++) {
                b.emitVoidOperation();
            }
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testManyConditionalBranches() {
        BasicInterpreter node = parseNode("manyConditionalBranches", b -> {
            b.beginRoot();
            b.beginBlock();
            for (int i = 0; i < Short.MAX_VALUE * 2; i++) {
                b.beginConditional();
                b.emitLoadArgument(0);
                b.emitLoadConstant(123L);
                b.emitLoadConstant(321L);
                b.endConditional();
            }
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        assertEquals(42L, node.getCallTarget().call(true));
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

        // TODO(GR-59372): Without default values, every local slot gets cleared on entry, which
        // breaks compilation because the number of clears exceed PE's explode loop threshold. Using
        // illegal default slots will solve this problem because the clears will be unnecessary.
        if (run.getDefaultLocalValue() != null) {
            assertEquals(42L, node.getCallTarget().call());
        }
    }

    @Test
    public void testTooManyLocals() {
        assertThrows(BytecodeEncodingException.class, () -> {
            parseNode("tooManyLocals", b -> {
                b.beginRoot();
                b.beginBlock();

                for (int i = 0; i < Short.MAX_VALUE; i++) {
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
    public void testManyRoots() {
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
            for (int i = 0; i < Short.MAX_VALUE; i++) {
                b.beginRoot();
                b.beginReturn();
                b.emitLoadConstant((long) i);
                b.endReturn();
                b.endRoot();
            }
        });
        assertEquals(0L, nodes.getNode(0).getCallTarget().call());
        assertEquals(42L, nodes.getNode(42).getCallTarget().call());
        assertEquals((long) (Short.MAX_VALUE - 1), nodes.getNode(Short.MAX_VALUE - 1).getCallTarget().call());

    }

    @Test
    public void testTooManyRoots() {
        assertThrowsWithMessage("Root node count exceeded maximum value", BytecodeEncodingException.class, () -> {
            createNodes(BytecodeConfig.DEFAULT, b -> {
                for (int i = 0; i < Short.MAX_VALUE + 1; i++) {
                    b.beginRoot();
                    b.beginReturn();
                    b.emitLoadConstant((long) i);
                    b.endReturn();
                    b.endRoot();
                }
            });
        });
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
            for (int i = 0; i < Short.MAX_VALUE * 2; i++) {
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

    @Test
    public void testTransitionToCached() {
        assumeTrue(run.hasUncachedInterpreter());
        BasicInterpreter node = parseNode("transitionToCached", b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        node.getBytecodeNode().setUncachedThreshold(50);
        for (int i = 0; i < 50; i++) {
            assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
            assertEquals(42L, node.getCallTarget().call());
        }
        assertEquals(BytecodeTier.CACHED, node.getBytecodeNode().getTier());
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testTransitionToCachedImmediately() {
        assumeTrue(run.hasUncachedInterpreter());
        BasicInterpreter node = parseNode("transitionToCachedImmediately", b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        node.getBytecodeNode().setUncachedThreshold(0);
        // The bytecode node will transition to cached on the first call.
        assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
        assertEquals(42L, node.getCallTarget().call());
        assertEquals(BytecodeTier.CACHED, node.getBytecodeNode().getTier());
    }

    @Test
    public void testTransitionToCachedBadThreshold() {
        assumeTrue(run.hasUncachedInterpreter());
        BasicInterpreter node = parseNode("transitionToCachedBadThreshold", b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        assertThrows(IllegalArgumentException.class, () -> node.getBytecodeNode().setUncachedThreshold(-1));
    }

    @Test
    public void testTransitionToCachedLoop() {
        assumeTrue(run.hasUncachedInterpreter());
        BasicInterpreter node = parseNode("transitionToCachedLoop", b -> {
            b.beginRoot();
            BytecodeLocal i = b.createLocal();
            b.beginStoreLocal(i);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLess();
            b.emitLoadLocal(i);
            b.emitLoadArgument(0);
            b.endLess();

            b.beginStoreLocal(i);
            b.beginAddConstantOperation(1L);
            b.emitLoadLocal(i);
            b.endAddConstantOperation();
            b.endStoreLocal();
            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(i);
            b.endReturn();

            b.endRoot();
        });

        node.getBytecodeNode().setUncachedThreshold(50);
        assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
        assertEquals(24L, node.getCallTarget().call(24L)); // 24 back edges + 1 return
        assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
        assertEquals(24L, node.getCallTarget().call(24L)); // 24 back edges + 1 return
        assertEquals(BytecodeTier.CACHED, node.getBytecodeNode().getTier());
        assertEquals(24L, node.getCallTarget().call(24L));
    }

    @Test
    public void testTransitionToCachedRecursive() {
        assumeTrue(run.hasUncachedInterpreter());
        BasicInterpreter node = parseNode("transitionToCachedRecursive", b -> {
            // function f(x) { return 0 < x ? x + f(x-1) : 0 }
            b.beginRoot();
            b.beginIfThenElse();
            b.beginLess();
            b.emitLoadConstant(0L);
            b.emitLoadArgument(0);
            b.endLess();

            b.beginReturn();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.beginInvokeRecursive();
            b.beginAddConstantOperation(-1L);
            b.emitLoadArgument(0);
            b.endAddConstantOperation();
            b.endInvokeRecursive();
            b.endAdd();
            b.endReturn();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();

            b.endIfThenElse();
            b.endRoot();
        });

        node.getBytecodeNode().setUncachedThreshold(22);
        assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
        assertEquals(20 * 21 / 2L, node.getCallTarget().call(20L)); // 21 calls
        assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
        node.getBytecodeNode().setUncachedThreshold(21);
        assertEquals(20 * 21 / 2L, node.getCallTarget().call(20L)); // 21 calls
        assertEquals(BytecodeTier.CACHED, node.getBytecodeNode().getTier());
    }

    @Test
    public void testTransitionToCachedYield() {
        assumeTrue(run.hasUncachedInterpreter());
        BasicInterpreter node = parseNode("transitionToCachedYield", b -> {
            b.beginRoot();
            for (int i = 0; i < 20; i++) {
                b.beginYield();
                b.emitLoadNull();
                b.endYield();
            }
            b.endRoot();
        });

        node.getBytecodeNode().setUncachedThreshold(16);
        assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
        ContinuationResult cont = (ContinuationResult) node.getCallTarget().call();
        for (int i = 1; i < 16; i++) {
            assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
            cont = (ContinuationResult) cont.continueWith(null);
        }
        assertEquals(BytecodeTier.CACHED, node.getBytecodeNode().getTier());
    }

    @Test
    public void testInvalidDefaultUncachedThreshold() {
        assumeTrue(run.hasUncachedInterpreter());

        int oldDefault = BasicInterpreter.defaultUncachedThreshold;
        // Invalid default thresholds are validated at run time.
        BasicInterpreter.defaultUncachedThreshold = -1;
        try {
            assertThrows(IllegalArgumentException.class, () -> {
                parseNode("invalidDefaultUncachedThreshold", b -> {
                    b.beginRoot();
                    b.beginReturn();
                    b.emitLoadConstant(42L);
                    b.endReturn();
                    b.endRoot();
                });
            });
        } finally {
            BasicInterpreter.defaultUncachedThreshold = oldDefault;
        }
    }

    @Test
    public void testDisableTransitionToCached() {
        assumeTrue(run.hasUncachedInterpreter());
        BasicInterpreter node = parseNode("disableTransitionToCached", b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        node.getBytecodeNode().setUncachedThreshold(Integer.MIN_VALUE);
        for (int i = 0; i < 50; i++) {
            assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
            assertEquals(42L, node.getCallTarget().call());
        }
    }

    @Test
    public void testDisableTransitionToCachedLoop() {
        assumeTrue(run.hasUncachedInterpreter());
        BasicInterpreter node = parseNode("disableTransitionToCachedLoop", b -> {
            b.beginRoot();
            BytecodeLocal i = b.createLocal();
            b.beginStoreLocal(i);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLess();
            b.emitLoadLocal(i);
            b.emitLoadArgument(0);
            b.endLess();

            b.beginStoreLocal(i);
            b.beginAddConstantOperation(1L);
            b.emitLoadLocal(i);
            b.endAddConstantOperation();
            b.endStoreLocal();
            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(i);
            b.endReturn();

            b.endRoot();
        });

        node.getBytecodeNode().setUncachedThreshold(Integer.MIN_VALUE);
        assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
        assertEquals(50L, node.getCallTarget().call(50L));
        assertEquals(BytecodeTier.UNCACHED, node.getBytecodeNode().getTier());
    }

    @Test
    public void testIntrospectionDataInstructions() {
        BasicInterpreter node = parseNode("introspectionDataInstructions", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAdd();
            b.endReturn();

            b.endRoot();
        });

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").arg("index", Argument.Kind.INTEGER, 0).build(),
                        instr("load.argument").arg("index", Argument.Kind.INTEGER, 1).build(),
                        instr("c.Add").build(),
                        instr("return").build());

        node.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(42L, node.getCallTarget().call(40L, 2L));
        assertEquals("Hello world", node.getCallTarget().call("Hello ", "world"));

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").arg("index", Argument.Kind.INTEGER, 0).build(),
                        instr("load.argument").arg("index", Argument.Kind.INTEGER, 1).build(),
                        instr("c.Add").specializations("addLongs", "addStrings").build(),
                        instr("return").build());

        // Normally, this method is called on parse with uninitialized bytecode.
        // Explicitly test here after initialization.
        testIntrospectionInvariants(node.getBytecodeNode());
    }

    @Test
    public void testIntrospectionDataInstructionsWithConstant() {
        BasicInterpreter node = parseNode("introspectionDataInstructions", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAddConstantOperation(10L);
            b.beginAddConstantOperationAtEnd();
            b.emitLoadArgument(0);
            b.endAddConstantOperationAtEnd(30L);
            b.endAddConstantOperation();
            b.endReturn();

            b.endRoot();
        });

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").arg("index", Argument.Kind.INTEGER, 0).build(),
                        instr("c.AddConstantOperationAtEnd").arg("constantRhs", Argument.Kind.CONSTANT, 30L).build(),
                        instr("c.AddConstantOperation").arg("constantLhs", Argument.Kind.CONSTANT, 10L).build(),
                        instr("return").build());
    }

    @Test
    public void testIntrospectionDataBranchProfiles1() {
        BasicInterpreter node = parseNode("introspectionDataBranchProfiles1", b -> {
            b.beginRoot();
            b.beginIfThen();
            b.emitLoadArgument(0);
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endIfThen();
            b.beginReturn();
            b.emitLoadConstant(123L);
            b.endReturn();
            b.endRoot();
        });

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").build(),
                        instr("branch.false").arg("branch_profile", Argument.Kind.BRANCH_PROFILE, 0.0d).build(),
                        instr("load.constant").build(),
                        instr("return").build(),
                        instr("load.constant").build(),
                        instr("return").build());

        node.getBytecodeNode().setUncachedThreshold(0); // force caching

        assertEquals(42L, node.getCallTarget().call(true));

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").build(),
                        instr("branch.false").arg("branch_profile", Argument.Kind.BRANCH_PROFILE, 1.0d).build(),
                        instr("load.constant").build(),
                        instr("return").build(),
                        instr("load.constant").build(),
                        instr("return").build());

        assertEquals(123L, node.getCallTarget().call(false));

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").build(),
                        instr("branch.false").arg("branch_profile", Argument.Kind.BRANCH_PROFILE, 0.5d).build(),
                        instr("load.constant").build(),
                        instr("return").build(),
                        instr("load.constant").build(),
                        instr("return").build());
    }

    @Test
    public void testIntrospectionDataBranchProfiles2() {
        BasicInterpreter node = parseNode("introspectionDataBranchProfiles2", b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginScOr();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.emitLoadArgument(2);
            b.endScOr();
            b.endReturn();
            b.endRoot();
        });

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").arg("index", Kind.INTEGER, 0).build(),
                        instr("dup").build(),
                        instr("c.ToBoolean").build(),
                        instr("sc.ScOr").arg("branch_profile", Argument.Kind.BRANCH_PROFILE, 0.0d).build(),
                        instr("load.argument").arg("index", Kind.INTEGER, 1).build(),
                        instr("dup").build(),
                        instr("c.ToBoolean").build(),
                        instr("sc.ScOr").arg("branch_profile", Argument.Kind.BRANCH_PROFILE, 0.0d).build(),
                        instr("load.argument").arg("index", Kind.INTEGER, 2).build(),
                        instr("return").build());

        node.getBytecodeNode().setUncachedThreshold(0); // force caching

        assertEquals(42L, node.getCallTarget().call(42L, 0L, 0L));
        assertEquals(42L, node.getCallTarget().call(0L, 42L, 0L));
        assertEquals(42L, node.getCallTarget().call(0L, 0L, 42L));

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").arg("index", Kind.INTEGER, 0).build(),
                        instr("dup").build(),
                        instr("c.ToBoolean").build(),
                        // operand 1 evaluated 3 times and was truthy once
                        instr("sc.ScOr").arg("branch_profile", Argument.Kind.BRANCH_PROFILE, 1.0d / 3).build(),
                        instr("load.argument").arg("index", Kind.INTEGER, 1).build(),
                        instr("dup").build(),
                        instr("c.ToBoolean").build(),
                        // operand 2 evaluated 2 times and was truthy once
                        instr("sc.ScOr").arg("branch_profile", Argument.Kind.BRANCH_PROFILE, 1.0d / 2).build(),
                        instr("load.argument").arg("index", Kind.INTEGER, 2).build(),
                        instr("return").build());
    }

    private static String[] collectInstructions(BytecodeNode bytecode, int startBci, int endBci) {
        List<String> result = new ArrayList<>();
        for (Instruction instruction : bytecode.getInstructions()) {
            int bci = instruction.getBytecodeIndex();
            if (startBci <= bci && bci < endBci) {
                result.add(instruction.getName());
            }
        }
        return result.toArray(new String[0]);
    }

    private static void assertGuards(ExceptionHandler handler, BytecodeNode bytecode, String... expectedInstructions) {
        assertArrayEquals(expectedInstructions, collectInstructions(bytecode, handler.getStartBytecodeIndex(), handler.getEndBytecodeIndex()));
    }

    @Test
    public void testIntrospectionDataExceptionHandlers1() {
        BasicInterpreter node = parseNode("introspectionDataExceptionHandlers1", b -> {
            // @formatter:off
            b.beginRoot();
            b.beginBlock();

                b.beginTryCatch(); // h1
                    b.beginBlock();
                        b.emitVoidOperation();
                        b.beginTryCatch(); // h2
                            b.emitVoidOperation();
                            b.emitVoidOperation();
                        b.endTryCatch();
                    b.endBlock();

                    b.emitVoidOperation();
                b.endTryCatch();

                b.beginTryCatch(); // h3
                    b.emitVoidOperation();
                    b.emitVoidOperation();
                b.endTryCatch();

            b.endBlock();
            b.endRoot();
            // @formatter:on
        });

        BytecodeNode bytecode = node.getBytecodeNode();
        List<ExceptionHandler> handlers = bytecode.getExceptionHandlers();

        assertEquals(3, handlers.size());
        // note: handlers get emitted in order of endTryCatch()
        ExceptionHandler h1 = handlers.get(1);
        ExceptionHandler h2 = handlers.get(0);
        ExceptionHandler h3 = handlers.get(2);

        // they all have unique handler bci's
        assertNotEquals(h1.getHandlerBytecodeIndex(), h2.getHandlerBytecodeIndex());
        assertNotEquals(h2.getHandlerBytecodeIndex(), h3.getHandlerBytecodeIndex());
        assertNotEquals(h1.getHandlerBytecodeIndex(), h3.getHandlerBytecodeIndex());

        // h2's guarded range and handler are both contained within h1's guarded range
        assertTrue(h1.getStartBytecodeIndex() < h2.getStartBytecodeIndex());
        assertTrue(h2.getEndBytecodeIndex() < h1.getEndBytecodeIndex());
        assertTrue(h1.getStartBytecodeIndex() < h2.getHandlerBytecodeIndex());
        assertTrue(h2.getHandlerBytecodeIndex() < h1.getEndBytecodeIndex());

        // h1 and h3 are independent
        assertTrue(h1.getEndBytecodeIndex() < h3.getStartBytecodeIndex());

        assertGuards(h2, bytecode, "c.VoidOperation");
        assertGuards(h1, bytecode, "c.VoidOperation", "c.VoidOperation", "branch", "c.VoidOperation", "pop");
        assertGuards(h3, bytecode, "c.VoidOperation");
    }

    @Test
    public void testIntrospectionDataExceptionHandlers2() {
        BasicInterpreter node = parseNode("testIntrospectionDataExceptionHandlers2", b -> {
            // @formatter:off
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();
                b.beginBlock();
                    b.beginTryFinally(() -> b.emitVoidOperation());
                        b.beginBlock();
                            b.emitVoidOperation();
                            b.beginIfThen();
                                b.emitLoadArgument(0);
                                b.beginReturn();
                                    b.emitLoadConstant(42L);
                                b.endReturn();
                            b.endIfThen();
                            b.emitVoidOperation();
                            b.beginIfThen();
                                b.emitLoadArgument(1);
                                b.emitBranch(lbl);
                            b.endIfThen();
                            b.emitVoidOperation();
                        b.endBlock();
                    b.endTryFinally();
                b.endBlock();
                b.emitLabel(lbl);
            b.endRoot();
            // @formatter:on
        });

        List<ExceptionHandler> handlers = node.getBytecodeNode().getExceptionHandlers();

        /**
         * The Finally handler should guard three ranges: from the start to the early return, from
         * the early return to the branch, and from the branch to the end.
         */
        assertEquals(3, handlers.size());
        ExceptionHandler h1 = handlers.get(0);
        ExceptionHandler h2 = handlers.get(1);
        ExceptionHandler h3 = handlers.get(2);

        assertEquals(h1.getHandlerBytecodeIndex(), h2.getHandlerBytecodeIndex());
        assertEquals(h1.getHandlerBytecodeIndex(), h3.getHandlerBytecodeIndex());
        assertTrue(h1.getEndBytecodeIndex() < h2.getStartBytecodeIndex());
        assertTrue(h2.getEndBytecodeIndex() < h3.getStartBytecodeIndex());

        assertGuards(h1, node.getBytecodeNode(),
                        "c.VoidOperation", "load.argument", "branch.false", "load.constant");
        assertGuards(h2, node.getBytecodeNode(),
                        "return", "c.VoidOperation", "load.argument", "branch.false");
        assertGuards(h3, node.getBytecodeNode(),
                        "branch", "c.VoidOperation");
    }

    @Test
    public void testIntrospectionDataExceptionHandlers3() {
        // test case: early return
        BasicInterpreter node = parseNode("testIntrospectionDataExceptionHandlers3", b -> {
            // @formatter:off
            b.beginRoot();
                b.beginBlock();
                    b.beginTryFinally(() -> b.emitVoidOperation());
                        b.beginBlock();
                            b.emitVoidOperation();
                            b.beginIfThen();
                                b.emitLoadArgument(0);
                                b.beginReturn();
                                    b.emitLoadConstant(42L);
                                b.endReturn();
                            b.endIfThen();
                            // nothing
                        b.endBlock();
                    b.endTryFinally();
                b.endBlock();
            b.endRoot();
            // @formatter:on
        });
        List<ExceptionHandler> handlers = node.getBytecodeNode().getExceptionHandlers();
        assertEquals(2, handlers.size());
        assertGuards(handlers.get(0), node.getBytecodeNode(),
                        "c.VoidOperation", "load.argument", "branch.false", "load.constant");
        assertGuards(handlers.get(1), node.getBytecodeNode(), "return");

        // test case: branch out
        node = parseNode("testIntrospectionDataExceptionHandlers3", b -> {
            // @formatter:off
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();
                b.beginBlock();
                    b.beginTryFinally(() -> b.emitVoidOperation());
                        b.beginBlock();
                            b.emitVoidOperation();
                            b.beginIfThen();
                                b.emitLoadArgument(0);
                                b.emitBranch(lbl);
                            b.endIfThen();
                            // nothing
                        b.endBlock();
                    b.endTryFinally();
                b.endBlock();
                b.emitLabel(lbl);
            b.endRoot();
            // @formatter:on
        });
        handlers = node.getBytecodeNode().getExceptionHandlers();
        assertEquals(2, handlers.size());
        assertGuards(handlers.get(0), node.getBytecodeNode(),
                        "c.VoidOperation", "load.argument", "branch.false");
        assertGuards(handlers.get(1), node.getBytecodeNode(), "branch");

        // test case: early return with branch, and instrumentation in the middle.
        node = parseNode("testIntrospectionDataExceptionHandlers3", b -> {
            // @formatter:off
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();
                b.beginBlock();
                    b.beginTryFinally(() -> b.emitVoidOperation());
                        b.beginBlock();
                            b.emitVoidOperation();
                            b.beginIfThen();
                                b.emitLoadArgument(0);
                                b.beginReturn();
                                    b.emitLoadConstant(42L);
                                b.endReturn();
                            b.endIfThen();
                            b.emitPrintHere(); // instrumentation instruction
                            b.emitBranch(lbl);
                        b.endBlock();
                    b.endTryFinally();
                b.endBlock();
                b.emitLabel(lbl);
            b.endRoot();
            // @formatter:on
        });
        handlers = node.getBytecodeNode().getExceptionHandlers();
        assertEquals(3, handlers.size());
        assertGuards(handlers.get(0), node.getBytecodeNode(),
                        "c.VoidOperation", "load.argument", "branch.false", "load.constant");
        assertGuards(handlers.get(1), node.getBytecodeNode(), "return");
        assertGuards(handlers.get(2), node.getBytecodeNode(), "branch");

        node.getRootNodes().update(createBytecodeConfigBuilder().addInstrumentation(BasicInterpreter.PrintHere.class).build());
        handlers = node.getBytecodeNode().getExceptionHandlers();
        assertEquals(3, handlers.size());
        assertGuards(handlers.get(0), node.getBytecodeNode(),
                        "c.VoidOperation", "load.argument", "branch.false", "load.constant");
        assertGuards(handlers.get(1), node.getBytecodeNode(), "return", "c.PrintHere");
        assertGuards(handlers.get(2), node.getBytecodeNode(), "branch");
    }

    @Test
    public void testIntrospectionDataSourceInformation() {
        Source source = Source.newBuilder("test", "return 1 + 2", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("introspectionDataSourceInformation", b -> {
            b.beginSource(source);
            b.beginSourceSection(0, 12);

            b.beginRoot();
            b.beginReturn();

            b.beginSourceSection(7, 5);
            b.beginAdd();

            // intentional duplicate source section
            b.beginSourceSection(7, 1);
            b.beginSourceSection(7, 1);
            b.emitLoadConstant(1L);
            b.endSourceSection();
            b.endSourceSection();

            b.beginSourceSection(11, 1);
            b.emitLoadConstant(2L);
            b.endSourceSection();

            b.endAdd();
            b.endSourceSection();

            b.endReturn();
            b.endRoot();

            b.endSourceSection();
            b.endSource();
        });

        BytecodeNode bytecode = node.getBytecodeNode();
        List<SourceInformation> sourceInformation = bytecode.getSourceInformation();

        assertEquals(4, sourceInformation.size());
        SourceInformation s1 = sourceInformation.get(0); // 1
        SourceInformation s2 = sourceInformation.get(1); // 2
        SourceInformation s3 = sourceInformation.get(2); // 1 + 2
        SourceInformation s4 = sourceInformation.get(3); // return 1 + 2

        assertEquals("1", s1.getSourceSection().getCharacters().toString());
        assertEquals("2", s2.getSourceSection().getCharacters().toString());
        assertEquals("1 + 2", s3.getSourceSection().getCharacters().toString());
        assertEquals("return 1 + 2", s4.getSourceSection().getCharacters().toString());

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();

        assertEquals(0, s1.getStartBytecodeIndex());
        assertEquals(instructions.get(1).getBytecodeIndex(), s1.getEndBytecodeIndex());

        assertEquals(6, s2.getStartBytecodeIndex());
        assertEquals(instructions.get(2).getBytecodeIndex(), s2.getEndBytecodeIndex());

        assertEquals(0, s3.getStartBytecodeIndex());
        assertEquals(instructions.get(3).getBytecodeIndex(), s3.getEndBytecodeIndex());

        assertEquals(0, s4.getStartBytecodeIndex());
        assertEquals(instructions.get(3).getNextBytecodeIndex(), s4.getEndBytecodeIndex());
    }

    @Test
    public void testIntrospectionDataSourceInformationTree() {
        Source source = Source.newBuilder("test", "return (a + b) + 2", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("introspectionDataSourceInformationTree", b -> {
            b.beginSource(source);
            b.beginSourceSection(0, 18);

            b.beginRoot();
            b.beginReturn();

            b.beginSourceSection(7, 11);
            b.beginAdd();

            // intentional duplicate source section
            b.beginSourceSection(7, 7);
            b.beginSourceSection(7, 7);
            b.beginAdd();

            b.beginSourceSection(8, 1);
            b.emitLoadArgument(0);
            b.endSourceSection();

            b.beginSourceSection(12, 1);
            b.emitLoadArgument(1);
            b.endSourceSection();

            b.endAdd();
            b.endSourceSection();
            b.endSourceSection();

            b.beginSourceSection(17, 1);
            b.emitLoadConstant(2L);
            b.endSourceSection();

            b.endAdd();
            b.endSourceSection();

            b.endReturn();
            b.endRoot();

            b.endSourceSection();
            b.endSource();
        });
        BytecodeNode bytecode = node.getBytecodeNode();

        // @formatter:off
        ExpectedSourceTree expected = expectedSourceTree("return (a + b) + 2",
            expectedSourceTree("(a + b) + 2",
                expectedSourceTree("(a + b)",
                    expectedSourceTree("a"),
                    expectedSourceTree("b")
                ),
                expectedSourceTree("2")
            )
        );
        // @formatter:on
        SourceInformationTree tree = bytecode.getSourceInformationTree();
        expected.assertTreeEquals(tree);
        assertTrue(tree.toString().contains("return (a + b) + 2"));
    }

    @Test
    public void testIntrospectionDataInstrumentationInstructions() {
        BasicInterpreter node = parseNode("introspectionDataInstrumentationInstructions", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.beginAdd();
            b.emitLoadArgument(0);
            b.beginIncrementValue();
            b.emitLoadArgument(1);
            b.endIncrementValue();
            b.endAdd();
            b.endTag(ExpressionTag.class);
            b.endReturn();

            b.endRoot();
        });

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").instrumented(false).build(),
                        instr("load.argument").instrumented(false).build(),
                        instr("c.Add").instrumented(false).build(),
                        instr("return").instrumented(false).build());

        node.getRootNodes().update(createBytecodeConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build());

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").instrumented(false).build(),
                        instr("load.argument").instrumented(false).build(),
                        instr("c.IncrementValue").instrumented(true).build(),
                        instr("c.Add").instrumented(false).build(),
                        instr("return").instrumented(false).build());
    }

    @Test
    public void testTags() {
        RootCallTarget root = parse("tags", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAdd();

            b.beginTag(ExpressionTag.class, StatementTag.class);
            b.emitLoadConstant(1L);
            b.endTag(ExpressionTag.class, StatementTag.class);

            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(2L);
            b.endTag(ExpressionTag.class);

            b.endAdd();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(3L, root.call());
    }

    @Test
    public void testPrepareForCall() {
        assertThrows(IllegalStateException.class, () -> parse("getCallTargetDuringParse", b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            BasicInterpreter root = b.endRoot();
            root.getCallTarget();
        }));

        assertTrue(parse("getCallTargetAfterParse", b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        }) != null);
    }

    @Test
    public void testCloneUninitializedAdd() {
        // return arg0 + arg1;

        BasicInterpreter node = parseNode("cloneUninitializedAdd", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAdd();
            b.endReturn();

            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(16);
        RootCallTarget root = node.getCallTarget();

        // Run enough times to trigger cached execution.
        for (int i = 0; i < 16; i++) {
            assertEquals(42L, root.call(20L, 22L));
            assertEquals("foobar", root.call("foo", "bar"));
            assertEquals(100L, root.call(120L, -20L));
        }

        BasicInterpreter cloned = node.doCloneUninitialized();
        assertNotEquals(node.getCallTarget(), cloned.getCallTarget());
        root = cloned.getCallTarget();

        // Run enough times to trigger cached execution again. The transition should work without
        // crashing.
        for (int i = 0; i < 16; i++) {
            assertEquals(42L, root.call(20L, 22L));
            assertEquals("foobar", root.call("foo", "bar"));
            assertEquals(100L, root.call(120L, -20L));
        }
    }

    @Test
    public void testCloneUninitializedFields() {
        BasicInterpreter node = parseNode("cloneUninitializedFields", b -> {
            b.beginRoot();
            emitReturn(b, 0);
            b.endRoot();
        });

        BasicInterpreter cloned = node.doCloneUninitialized();
        assertEquals("User field was not copied to the uninitialized clone.", node.name, cloned.name);
    }

    @Test
    public void testCloneUninitializedUnquicken() {
        assumeTrue(run.hasBoxingElimination());

        BasicInterpreter node = parseNode("cloneUninitializedUnquicken", b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAdd();
            b.emitLoadConstant(40L);
            b.emitLoadArgument(0);
            b.endAdd();
            b.endReturn();
            b.endRoot();
        });

        AbstractInstructionTest.assertInstructions(node,
                        "load.constant",
                        "load.argument",
                        "c.Add",
                        "return");

        node.getBytecodeNode().setUncachedThreshold(0); // ensure we use cached
        assertEquals(42L, node.getCallTarget().call(2L));

        AbstractInstructionTest.assertInstructions(node,
                        "load.constant$Long",
                        "load.argument$Long",
                        "c.Add$AddLongs",
                        "return");

        BasicInterpreter cloned = node.doCloneUninitialized();
        // clone should be unquickened
        AbstractInstructionTest.assertInstructions(cloned,
                        "load.constant",
                        "load.argument",
                        "c.Add",
                        "return");
        // original should be unchanged
        AbstractInstructionTest.assertInstructions(node,
                        "load.constant$Long",
                        "load.argument$Long",
                        "c.Add$AddLongs",
                        "return");
        // clone call should work like usual
        assertEquals(42L, cloned.getCallTarget().call(2L));
    }

    @Test
    public void testConstantOperandBoxingElimination() {
        assumeTrue(run.hasBoxingElimination());

        BasicInterpreter node = parseNode("constantOperandBoxingElimination", b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAddConstantOperation(40L);
            b.emitLoadArgument(0);
            b.endAddConstantOperation();
            b.endReturn();
            b.endRoot();
        });

        AbstractInstructionTest.assertInstructions(node,
                        "load.argument",
                        "c.AddConstantOperation",
                        "return");

        node.getBytecodeNode().setUncachedThreshold(0); // ensure we use cached
        assertEquals(42L, node.getCallTarget().call(2L));

        AbstractInstructionTest.assertInstructions(node,
                        "load.argument$Long",
                        "c.AddConstantOperation$AddLongs",
                        "return");

        assertEquals("401", node.getCallTarget().call("1"));
        AbstractInstructionTest.assertInstructions(node,
                        "load.argument",
                        "c.AddConstantOperation",
                        "return");

        assertEquals(42L, node.getCallTarget().call(2L));
        AbstractInstructionTest.assertInstructions(node,
                        "load.argument",
                        "c.AddConstantOperation",
                        "return");
    }

    @Test
    public void testConstantOperandAtEndBoxingElimination() {
        assumeTrue(run.hasBoxingElimination());

        BasicInterpreter node = parseNode("constantOperandAtEndBoxingElimination", b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAddConstantOperationAtEnd();
            b.emitLoadArgument(0);
            b.endAddConstantOperationAtEnd(40L);
            b.endReturn();
            b.endRoot();
        });

        AbstractInstructionTest.assertInstructions(node,
                        "load.argument",
                        "c.AddConstantOperationAtEnd",
                        "return");

        node.getBytecodeNode().setUncachedThreshold(0); // ensure we use cached
        assertEquals(42L, node.getCallTarget().call(2L));

        AbstractInstructionTest.assertInstructions(node,
                        "load.argument$Long",
                        "c.AddConstantOperationAtEnd$AddLongs",
                        "return");

        assertEquals("140", node.getCallTarget().call("1"));
        AbstractInstructionTest.assertInstructions(node,
                        "load.argument",
                        "c.AddConstantOperationAtEnd",
                        "return");

        assertEquals(42L, node.getCallTarget().call(2L));
        AbstractInstructionTest.assertInstructions(node,
                        "load.argument",
                        "c.AddConstantOperationAtEnd",
                        "return");
    }

    @Test
    public void testInvalidBciArgument() {
        // Check that BytecodeNode methods taking a bytecode index sanitize bad inputs.
        Source s = Source.newBuilder("test", "x = 42; return x", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("invalidBciArgument", b -> {
            b.beginSource(s);
            b.beginSourceSection(0, 16);
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();
            b.beginYield();
            b.emitLoadConstant(5L);
            b.endYield();
            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();
            b.endRoot();
            b.endSourceSection();
            b.endSource();
        });

        BytecodeNode bytecode = node.getBytecodeNode();
        assertThrows(IllegalArgumentException.class, () -> bytecode.getBytecodeLocation(-1));
        assertThrows(IllegalArgumentException.class, () -> bytecode.getSourceLocation(-1));
        assertThrows(IllegalArgumentException.class, () -> bytecode.getSourceLocations(-1));
        assertThrows(IllegalArgumentException.class, () -> bytecode.getInstruction(-1));
        assertThrows(IllegalArgumentException.class, () -> bytecode.getLocalNames(-1));
        assertThrows(IllegalArgumentException.class, () -> bytecode.getLocalInfos(-1));
        assertThrows(IllegalArgumentException.class, () -> bytecode.getLocalCount(-1));
        int localOffset = bytecode.getLocals().get(0).getLocalOffset();
        assertThrows(IllegalArgumentException.class, () -> bytecode.getLocalName(-1, localOffset));
        assertThrows(IllegalArgumentException.class, () -> bytecode.getLocalInfo(-1, localOffset));

        ContinuationResult cont = (ContinuationResult) node.getCallTarget().call();
        Frame frame = cont.getFrame();
        BytecodeNode contBytecode = cont.getBytecodeLocation().getBytecodeNode();
        assertThrows(IllegalArgumentException.class, () -> contBytecode.getLocalValues(-1, frame));
        assertThrows(IllegalArgumentException.class, () -> contBytecode.getLocalValue(-1, frame, localOffset));

        assertThrows(IllegalArgumentException.class, () -> contBytecode.setLocalValues(-1, frame, new Object[]{"hello"}));
        assertThrows(IllegalArgumentException.class, () -> contBytecode.setLocalValue(-1, frame, localOffset, "hello"));

    }
}
