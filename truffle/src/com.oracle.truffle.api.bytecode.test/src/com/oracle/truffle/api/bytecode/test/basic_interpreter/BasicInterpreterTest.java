/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ExceptionHandler;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instruction.Argument;
import com.oracle.truffle.api.bytecode.SourceInformation;
import com.oracle.truffle.api.bytecode.SourceInformationTree;
import com.oracle.truffle.api.bytecode.test.AbstractInstructionTest;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.source.Source;

/**
 * Tests basic features of the Bytecode DSL. Serves as a catch-all for functionality we just need a
 * few tests (and not a separate test class) for.
 */
@RunWith(Parameterized.class)
public class BasicInterpreterTest extends AbstractBasicInterpreterTest {
    private record ExpectedArgument(String name, Argument.Kind kind, Object value) {
    }

    private record ExpectedInstruction(String name, Integer bci, Boolean instrumented, ExpectedArgument[] arguments) {

        private ExpectedInstruction withBci(Integer newBci) {
            return new ExpectedInstruction(name, newBci, instrumented, arguments);
        }

        static final class Builder {
            String name;
            Integer bci;
            Boolean instrumented;
            List<ExpectedArgument> arguments;

            private Builder(String name) {
                this.name = name;
                this.arguments = new ArrayList<>();
            }

            private Builder bci(Integer newBci) {
                this.bci = newBci;
                return this;
            }

            private Builder instrumented(Boolean newInstrumented) {
                this.instrumented = newInstrumented;
                return this;
            }

            private Builder arg(String argName, Argument.Kind kind, Object value) {
                this.arguments.add(new ExpectedArgument(argName, kind, value));
                return this;
            }

            private ExpectedInstruction build() {
                return new ExpectedInstruction(name, bci, instrumented, arguments.toArray(new ExpectedArgument[0]));
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
        assertEquals(expected.name, actual.getName());
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
                Object actualValue = switch (expectedArgument.kind) {
                    case CONSTANT -> actualArgument.asConstant();
                    case INTEGER -> actualArgument.asInteger();
                    default -> throw new AssertionError(String.format("Testing arguments of kind %s not yet implemented", expectedArgument.kind));
                };
                assertEquals(expectedArgument.value, actualValue);
            }
        }
    }

    // @formatter:off
    @Test
    public void testAdd() {
        // return arg0 + arg1;

        RootCallTarget root = parse("add", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
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
        //   return arg1;
        // } else {
        //   return arg0;
        // }

        RootCallTarget root = parse("max", b -> {
            b.beginRoot(LANGUAGE);
            b.beginIfThenElse();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endLessThanOperation();

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
        //   return 0;
        // }
        // return arg0;

        RootCallTarget root = parse("ifThen", b -> {
            b.beginRoot(LANGUAGE);
            b.beginIfThen();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();

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
            b.beginRoot(LANGUAGE);

            b.beginReturn();

            b.beginConditional();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();

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
            b.beginRoot(LANGUAGE);
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
            b.beginRoot(LANGUAGE);
            BytecodeLocal locI = b.createLocal();
            BytecodeLocal locJ = b.createLocal();

            b.beginStoreLocal(locI);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginStoreLocal(locJ);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();
                b.beginLessThanOperation();
                b.emitLoadLocal(locI);
                b.emitLoadArgument(0);
                b.endLessThanOperation();

                b.beginBlock();
                    b.beginStoreLocal(locJ);
                        b.beginAddOperation();
                        b.emitLoadLocal(locJ);
                        b.emitLoadLocal(locI);
                        b.endAddOperation();
                    b.endStoreLocal();

                    b.beginStoreLocal(locI);
                        b.beginAddOperation();
                        b.emitLoadLocal(locI);
                        b.emitLoadConstant(1L);
                        b.endAddOperation();
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
    public void testTryCatch() {
        // try {
        //   if (arg0 < 0) throw arg0+1
        // } catch ex {
        //   return ex.value;
        // }
        // return 0;

        RootCallTarget root = parse("tryCatch", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal local = b.createLocal();
            b.beginTryCatch(local);

            b.beginIfThen();
            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();

            b.beginThrowOperation();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endThrowOperation();

            b.endIfThen();

            b.beginReturn();
            b.beginReadExceptionOperation();
            b.emitLoadLocal(local);
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
    public void testTryCatchNestedInTry() {
        // try {
        //   try {
        //     if (arg0 < 1) throw arg0
        //   } catch ex2 {
        //     if (arg0 < 0) throw arg0 - 100
        //     return 42;
        //   }
        //   throw arg0;
        // } catch ex1 {
        //   return ex1.value
        // }
        RootCallTarget root = parse("tryCatch", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal ex1 = b.createLocal();
            b.beginTryCatch(ex1);

            b.beginBlock(); // begin outer try
            BytecodeLocal ex2 = b.createLocal();
            b.beginTryCatch(ex2);

            b.beginIfThen(); // begin inner try
            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(1L);
            b.endLessThanOperation();
            b.beginThrowOperation();
            b.emitLoadArgument(0);
            b.endThrowOperation();
            b.endIfThen(); // end inner try

            b.beginBlock(); // begin inner catch

            b.beginIfThen();
            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();
            b.beginThrowOperation();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(-100L);
            b.endAddOperation();
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
            b.emitLoadLocal(ex1);
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
        //   throw arg0
        // } catch ex1 {
        //   try {
        //     if (arg0 < 0) throw -1
        //     return 42;
        //   } catch ex2 {
        //     return 123;
        //   }
        // }
        RootCallTarget root = parse("tryCatch", b -> {
            b.beginRoot(LANGUAGE);

            b.beginTryCatch(b.createLocal());

            b.beginThrowOperation(); // begin outer try
            b.emitLoadArgument(0);
            b.endThrowOperation(); // end outer try

            BytecodeLocal ex2 = b.createLocal();
            b.beginTryCatch(ex2); // begin outer catch

            b.beginBlock(); // begin inner try
            b.beginIfThen();
            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();
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
    public void testVariableBoxingElim() {
        // local0 = 0;
        // local1 = 0;
        // while (local0 < 100) {
        //   local1 = box(local1) + local0;
        //   local0 = local0 + 1;
        // }
        // return local1;

        RootCallTarget root = parse("variableBoxingElim", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal local0 = b.createLocal();
            BytecodeLocal local1 = b.createLocal();

            b.beginStoreLocal(local0);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginStoreLocal(local1);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();

            b.beginLessThanOperation();
            b.emitLoadLocal(local0);
            b.emitLoadConstant(100L);
            b.endLessThanOperation();

            b.beginBlock();

            b.beginStoreLocal(local1);
            b.beginAddOperation();
            b.beginAlwaysBoxOperation();
            b.emitLoadLocal(local1);
            b.endAlwaysBoxOperation();
            b.emitLoadLocal(local0);
            b.endAddOperation();
            b.endStoreLocal();

            b.beginStoreLocal(local0);
            b.beginAddOperation();
            b.emitLoadLocal(local0);
            b.emitLoadConstant(1L);
            b.endAddOperation();
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
                b.beginRoot(LANGUAGE);
                BytecodeLabel lbl = b.createLabel();
                b.emitBranch(lbl);
                b.endRoot();
            });
        }, IllegalStateException.class, (e)-> {
            assertTrue(e.getMessage(), e.getMessage().endsWith("ended without emitting one or more declared labels. This likely indicates a bug in the parser."));
        });
    }

    @Test
    public void testUnusedLabel() {
        // lbl:
        // return 42;

        RootCallTarget root = parse("unusedLabel", b -> {
            b.beginRoot(LANGUAGE);
            BytecodeLabel lbl = b.createLabel();
            b.emitLabel(lbl);
            emitReturn(b, 42);
            b.endRoot();
        });

        assertEquals(42L, root.call());
    }

    @Test
    public void testTeeLocal() {
        // tee(local, 1);
        // return local;

        RootCallTarget root = parse("teeLocal", b -> {
            b.beginRoot(LANGUAGE);

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
    public void testTeeLocalRange() {
        // teeRange([local1, local2], [1, 2]));
        // return local2;

        RootCallTarget root = parse("teeLocalRange", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal local1 = b.createLocal();
            BytecodeLocal local2 = b.createLocal();

            b.beginTeeLocalRange(new BytecodeLocal[] {local1, local2});
            b.emitLoadConstant(new long[] {1L, 2L});
            b.endTeeLocalRange();

            b.beginReturn();
            b.emitLoadLocal(local2);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(2L, root.call());
    }

    @Test
    public void testTeeLocalRangeEmptyRange() {
        // teeRange([], []));
        // return 42;

        RootCallTarget root = parse("teeLocalRangeEmptyRange", b -> {
            b.beginRoot(LANGUAGE);

            b.beginTeeLocalRange(new BytecodeLocal[] {});
            b.emitLoadConstant(new long[] {});
            b.endTeeLocalRange();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, root.call());
    }

    @Test
    public void testAddConstant() {
        // return 40 + arg0
        RootCallTarget root = parse("addConstant", b -> {
            b.beginRoot(LANGUAGE);
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
            b.beginRoot(LANGUAGE);
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
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginInvoke();
                b.beginRoot(LANGUAGE);
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
        //   x = () -> return 1
        //   y = () -> return 2
        //   arg0 ? x : y
        // })();

        RootCallTarget root = parse("multipleNestedFunctions", b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginInvoke();
                b.beginRoot(LANGUAGE);
                emitReturn(b, 1);
                BasicInterpreter x = b.endRoot();

                b.beginRoot(LANGUAGE);
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
            b.beginRoot(LANGUAGE);

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

            b.beginAddOperation();
            b.beginLoadLocalMaterialized(x);
            b.emitLoadLocal(f);
            b.endLoadLocalMaterialized();
            b.emitLoadConstant(1L);
            b.endAddOperation();

            b.endStoreLocalMaterialized();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, root.call());
    }

    @Test
    @Ignore
    public void testLocalsNonlocalRead() {
        // TODO this test fails when boxing elimination is enabled
        // locals accessed non-locally must have boxing elimination disabled
        // since non-local reads do not do boxing elimination

        // this can be done automatically, or by
        // having `createLocal(boolean accessedFromClosure)` or similar
        RootCallTarget root = parse("localsNonlocalRead", b -> {
            // x = 1
            // return (lambda: x)()
            b.beginRoot(LANGUAGE);

            BytecodeLocal xLoc = b.createLocal();

            b.beginStoreLocal(xLoc);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginReturn();

            b.beginInvoke();

                b.beginRoot(LANGUAGE);
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

        assertEquals(1L, root.call());
    }

    @Test
    public void testLocalsNonlocalWrite() {
        // x = 1;
        // ((x) -> x = 2)();
        // return x;

        RootCallTarget root = parse("localsNonlocalWrite", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal xLoc = b.createLocal();

            b.beginStoreLocal(xLoc);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginInvoke();

                b.beginRoot(LANGUAGE);

                b.beginStoreLocalMaterialized(xLoc);
                b.emitLoadArgument(0);
                b.emitLoadConstant(2L);
                b.endStoreLocalMaterialized();

                b.beginReturn();
                b.emitLoadConstant(null);
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
    public void testVariadicZeroVarargs()  {
        // return veryComplex(7);

        RootCallTarget root = parse("variadicZeroVarargs", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.emitLoadConstant(7L);
            b.endVeryComplexOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(7L, root.call());
    }

    @Test
    public void testVariadicOneVarargs()  {
        // return veryComplex(7, "foo");

        RootCallTarget root = parse("variadicOneVarargs", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.emitLoadConstant(7L);
            b.emitLoadConstant("foo");
            b.endVeryComplexOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(8L, root.call());
    }

    @Test
    public void testVariadicFewVarargs()  {
        // return veryComplex(7, "foo", "bar", "baz");

        RootCallTarget root = parse("variadicFewVarargs", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.emitLoadConstant(7L);
            b.emitLoadConstant("foo");
            b.emitLoadConstant("bar");
            b.emitLoadConstant("baz");
            b.endVeryComplexOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(10L, root.call());
    }

    @Test
    public void testVariadicManyVarargs()  {
        // return veryComplex(7, [1330 args]);

        RootCallTarget root = parse("variadicManyVarArgs", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.emitLoadConstant(7L);
            for (int i = 0; i < 1330; i++) {
                b.emitLoadConstant("test");
            }
            b.endVeryComplexOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1337L, root.call());
    }

    @Test
    public void testVariadicTooFewArguments()  {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation VeryComplexOperation expected at least 1 child, but 0 provided. This is probably a bug in the parser.");

        parse("variadicTooFewArguments", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.endVeryComplexOperation();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testValidationTooFewArguments() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation AddOperation expected exactly 2 children, but 1 provided. This is probably a bug in the parser.");

        parse("validationTooFewArguments", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testValidationTooManyArguments() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation AddOperation expected exactly 2 children, but 3 provided. This is probably a bug in the parser.");

        parse("validationTooManyArguments", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(1L);
            b.emitLoadConstant(2L);
            b.emitLoadConstant(3L);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testValidationNotValueArgument() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation AddOperation expected a value-producing child at position 0, but a void one was provided. This likely indicates a bug in the parser.");

        parse("validationNotValueArgument", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitVoidOperation();
            b.emitLoadConstant(2L);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
    }


    @Test
    public void testShortCircuitingAllPass() {
        // return 1 && true && "test";

        RootCallTarget root = parse("shortCircuitingAllPass", b -> {
            b.beginRoot(LANGUAGE);

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
            b.beginRoot(LANGUAGE);

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
            b.beginRoot(LANGUAGE);

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
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation ScAnd expected at least 1 child, but 0 provided. This is probably a bug in the parser.");
        parse("shortCircuitingNoChildren", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginScAnd();
            b.endScAnd();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testShortCircuitingNonValueChild() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation ScAnd expected a value-producing child at position 1, but a void one was provided. This likely indicates a bug in the parser.");
        parse("shortCircuitingNonValueChild", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginScAnd();
            b.emitLoadConstant("test");
            b.emitVoidOperation();
            b.emitLoadConstant("tost");
            b.endScAnd();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testEmptyBlock() {
        RootCallTarget root = parse("emptyBlock", b -> {
            b.beginRoot(LANGUAGE);

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
            b.beginRoot(LANGUAGE);

            b.beginBlock();
            b.endBlock();

            b.endRoot();
        });

        assertNull(root.call());
    }

    @Test
    public void testNoReturnInABranch() {
        RootCallTarget root = parse("noReturn", b -> {
            b.beginRoot(LANGUAGE);

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
            b.beginRoot(LANGUAGE);

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
    public void testIntrospectionDataInstructions() {
        BasicInterpreter node = parseNode("introspectionDataInstructions", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                            instr("load.argument").arg("index", Argument.Kind.INTEGER, 0).build(),
                            instr("load.argument").arg("index", Argument.Kind.INTEGER, 1).build(),
                            instr("c.AddOperation").build(),
                            instr("return").build()
                        );
    }

    @Test
    public void testIntrospectionDataInstructionsWithConstant() {
        BasicInterpreter node = parseNode("introspectionDataInstructions", b -> {
            b.beginRoot(LANGUAGE);

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
                        instr("return").build()
                    );
    }

    @Test
    public void testIntrospectionDataExceptionHandlers() {
        BasicInterpreter node = parseNode("introspectionDataExceptionHandlers", b -> {
            // @formatter:off
            b.beginRoot(LANGUAGE);
            b.beginBlock();
            BytecodeLocal exceptionLocal = b.createLocal();

                b.beginTryCatch(exceptionLocal); // h1
                    b.beginBlock();
                        b.emitVoidOperation();
                        // shares the local, and it should be entirely within the range of the outer handler
                        b.beginTryCatch(exceptionLocal); // h2
                            b.emitVoidOperation();
                            b.emitVoidOperation();
                        b.endTryCatch();
                    b.endBlock();

                    b.emitVoidOperation();
                b.endTryCatch();

                b.beginTryCatch(b.createLocal()); // h3
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
        assertNotEquals(h1.getHandlerIndex(), h2.getHandlerIndex());
        assertNotEquals(h2.getHandlerIndex(), h3.getHandlerIndex());
        assertNotEquals(h1.getHandlerIndex(), h3.getHandlerIndex());

        // h2's guarded range and handler are both contained within h1's guarded range
        assertTrue(h1.getStartIndex() < h2.getStartIndex());
        assertTrue(h2.getEndIndex() < h1.getEndIndex());
        assertTrue(h1.getStartIndex() < h2.getHandlerIndex());
        assertTrue(h2.getHandlerIndex() < h1.getEndIndex());

        // h1 and h3 are independent
        assertTrue(h1.getEndIndex() < h3.getStartIndex());
    }

    @Test
    public void testIntrospectionDataFinallyEarlyExitExceptionHandlers() {
        BasicInterpreter node = parseNode("introspectionDataFinallyEarlyExitExceptionHandlers", b -> {
            // @formatter:off
            b.beginRoot(LANGUAGE);
            BytecodeLabel lbl = b.createLabel();
                b.beginBlock();
                    b.beginFinallyTry(() -> b.emitVoidOperation());
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
                    b.endFinallyTry();
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

        assertEquals(h1.getHandlerIndex(), h2.getHandlerIndex());
        assertEquals(h1.getHandlerIndex(), h3.getHandlerIndex());
        assertTrue(h1.getEndIndex() < h2.getStartIndex());
        assertTrue(h2.getEndIndex() < h3.getStartIndex());
    }

    @Test
    public void testIntrospectionDataEmptyHandlers() {
        /**
         * When the guarded ranges are split into multiple ranges, some may be empty. We should skip
         * exception table entries for empty bytecode ranges.
         */

        // test case: early return
        BasicInterpreter node = parseNode("introspectionDataEmptyHandlers", b -> {
            // @formatter:off
            b.beginRoot(LANGUAGE);
                b.beginBlock();
                    b.beginFinallyTry(() -> b.emitVoidOperation());
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
                    b.endFinallyTry();
                b.endBlock();
            b.endRoot();
            // @formatter:on
        });
        // one range (everything up until the return)
        assertEquals(1, node.getBytecodeNode().getExceptionHandlers().size());

        // test case: branch out
        node = parseNode("introspectionDataEmptyHandlers", b -> {
            // @formatter:off
            b.beginRoot(LANGUAGE);
            BytecodeLabel lbl = b.createLabel();
                b.beginBlock();
                    b.beginFinallyTry(() -> b.emitVoidOperation());
                        b.beginBlock();
                            b.emitVoidOperation();
                            b.beginIfThen();
                                b.emitLoadArgument(0);
                                b.emitBranch(lbl);
                            b.endIfThen();
                            // nothing
                        b.endBlock();
                    b.endFinallyTry();
                b.endBlock();
                b.emitLabel(lbl);
            b.endRoot();
            // @formatter:on
        });
        // one range (everything up until the branch)
        assertEquals(1, node.getBytecodeNode().getExceptionHandlers().size());

        // test case: early return with branch, and instrumentation in the middle.
        node = parseNode("introspectionDataEmptyHandlers", b -> {
            // @formatter:off
            b.beginRoot(LANGUAGE);
            BytecodeLabel lbl = b.createLabel();
                b.beginBlock();
                    b.beginFinallyTry(() -> b.emitVoidOperation());
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
                    b.endFinallyTry();
                b.endBlock();
                b.emitLabel(lbl);
            b.endRoot();
            // @formatter:on
        });
        // without instrumentation, one range (everything up until the return)
        assertEquals(1, node.getBytecodeNode().getExceptionHandlers().size());

        // with instrumentation, two ranges (everything up until the return, and the instrumentation
        // instruction)
        node.getRootNodes().update(createBytecodeConfigBuilder().addInstrumentation(BasicInterpreter.PrintHere.class).build());
        List<ExceptionHandler> handlers = node.getBytecodeNode().getExceptionHandlers();
        assertEquals(2, handlers.size());
        assertEquals(handlers.get(0).getHandlerIndex(), handlers.get(1).getHandlerIndex());
        assertTrue(handlers.get(0).getEndIndex() < handlers.get(1).getStartIndex());
    }

    @Test
    public void testIntrospectionDataSourceInformation() {
        Source source = Source.newBuilder("test", "return 1 + 2", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("introspectionDataSourceInformation", b -> {
            b.beginSource(source);
            b.beginSourceSection(0, 12);

            b.beginRoot(LANGUAGE);
            b.beginReturn();

            b.beginSourceSection(7, 5);
            b.beginAddOperation();

            // intentional duplicate source section
            b.beginSourceSection(7, 1);
            b.beginSourceSection(7, 1);
            b.emitLoadConstant(1L);
            b.endSourceSection();
            b.endSourceSection();

            b.beginSourceSection(11, 1);
            b.emitLoadConstant(2L);
            b.endSourceSection();

            b.endAddOperation();
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

        assertEquals(0, s1.getStartIndex());
        assertEquals(instructions.get(1).getBytecodeIndex(), s1.getEndIndex());

        assertEquals(2, instructions.get(1).getBytecodeIndex());
        assertEquals(instructions.get(2).getBytecodeIndex(), s2.getEndIndex());

        assertEquals(0, s3.getStartIndex());
        assertEquals(instructions.get(3).getBytecodeIndex(), s3.getEndIndex());

        assertEquals(0, s4.getStartIndex());
        assertEquals(instructions.get(3).getBytecodeIndex() + 1, s4.getEndIndex());
    }

    @Test
    public void testIntrospectionDataSourceInformationTree() {
        Source source = Source.newBuilder("test", "return (a + b) + 2", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("introspectionDataSourceInformationTree", b -> {
            b.beginSource(source);
            b.beginSourceSection(0, 18);

            b.beginRoot(LANGUAGE);
            b.beginReturn();

            b.beginSourceSection(7, 11);
            b.beginAddOperation();

            // intentional duplicate source section
            b.beginSourceSection(7, 7);
            b.beginSourceSection(7, 7);
            b.beginAddOperation();

            b.beginSourceSection(8, 1);
            b.emitLoadArgument(0);
            b.endSourceSection();

            b.beginSourceSection(12, 1);
            b.emitLoadArgument(1);
            b.endSourceSection();

            b.endAddOperation();
            b.endSourceSection();
            b.endSourceSection();

            b.beginSourceSection(17, 1);
            b.emitLoadConstant(2L);
            b.endSourceSection();

            b.endAddOperation();
            b.endSourceSection();

            b.endReturn();
            b.endRoot();

            b.endSourceSection();
            b.endSource();
        });

        BytecodeNode bytecode = node.getBytecodeNode();
        SourceInformationTree root = bytecode.getSourceInformationTree();

        record ExpectedSourceTree(String contents, ExpectedSourceTree... children) {
            public void assertTreeEquals(SourceInformationTree actual) {
                assertEquals(contents, actual.getSourceSection().getCharacters().toString());
                assertEquals(children.length, actual.getChildren().size());
                for (int i = 0; i < children.length; i++) {
                    children[i].assertTreeEquals(actual.getChildren().get(i));
                }
            }
        }

        // @formatter:off
        ExpectedSourceTree expected = new ExpectedSourceTree("return (a + b) + 2",
            new ExpectedSourceTree("(a + b) + 2",
                new ExpectedSourceTree("(a + b)",
                    new ExpectedSourceTree("a"),
                    new ExpectedSourceTree("b")
                ),
                new ExpectedSourceTree("2")
            )
        );
        // @formatter:on

        expected.assertTreeEquals(root);
    }

    @Test
    public void testIntrospectionDataInstrumentationInstructions() {
        BasicInterpreter node = parseNode("introspectionDataInstrumentationInstructions", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginTag(ExpressionTag.class);
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.beginIncrementValue();
            b.emitLoadArgument(1);
            b.endIncrementValue();
            b.endAddOperation();
            b.endTag(ExpressionTag.class);
            b.endReturn();

            b.endRoot();
        });

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").instrumented(false).build(),
                        instr("load.argument").instrumented(false).build(),
                        instr("c.AddOperation").instrumented(false).build(),
                        instr("return").instrumented(false).build());

        node.getRootNodes().update(createBytecodeConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build());

        assertInstructionsEqual(node.getBytecodeNode().getInstructionsAsList(),
                        instr("load.argument").instrumented(false).build(),
                        instr("load.argument").instrumented(false).build(),
                        instr("c.IncrementValue").instrumented(true).build(),
                        instr("c.AddOperation").instrumented(false).build(),
                        instr("return").instrumented(false).build());
    }

    @Test
    public void testTags() {
        RootCallTarget root = parse("tags", b -> {
            b.beginRoot(null);

            b.beginReturn();
            b.beginAddOperation();

            b.beginTag(ExpressionTag.class, StatementTag.class);
            b.emitLoadConstant(1L);
            b.endTag(ExpressionTag.class, StatementTag.class);

            b.beginTag(ExpressionTag.class);
            b.emitLoadConstant(2L);
            b.endTag(ExpressionTag.class);

            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(3L, root.call());
    }

    @Test
    public void testCloneUninitializedAdd() {
        // return arg0 + arg1;

        BasicInterpreter node = parseNode("cloneUninitializedAdd", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
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
            b.beginRoot(LANGUAGE);
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
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(40L);
            b.emitLoadArgument(0);
            b.endAddOperation();
            b.endReturn();
            b.endRoot();
        });

        AbstractInstructionTest.assertInstructions(node,
                        "load.constant",
                        "load.argument",
                        "c.AddOperation",
                        "return");

        node.getBytecodeNode().setUncachedThreshold(0); // ensure we use cached
        assertEquals(42L, node.getCallTarget().call(2L));

        AbstractInstructionTest.assertInstructions(node,
                        "load.constant$Long",
                        "load.argument$Long",
                        "c.AddOperation$AddLongs",
                        "return");

        BasicInterpreter cloned = node.doCloneUninitialized();
        // clone should be unquickened
        AbstractInstructionTest.assertInstructions(cloned,
                        "load.constant",
                        "load.argument",
                        "c.AddOperation",
                        "return");
        // original should be unchanged
        AbstractInstructionTest.assertInstructions(node,
                        "load.constant$Long",
                        "load.argument$Long",
                        "c.AddOperation$AddLongs",
                        "return");
        // clone call should work like usual
        assertEquals(42L, cloned.getCallTarget().call(2L));
    }

    @Test
    public void testConstantOperandBoxingElimination() {
        assumeTrue(run.hasBoxingElimination());

        BasicInterpreter node = parseNode("constantOperandBoxingElimination", b -> {
            b.beginRoot(LANGUAGE);
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
            b.beginRoot(LANGUAGE);
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
    @Ignore
    public void testDecisionQuicken() {
        BasicInterpreter node = parseNode("decisionQuicken", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        // todo these tests do not pass, since quickening is not implemented yet properly

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        int bci = instructions.get(1).getNextBytecodeIndex();

        assertInstructionEquals(instructions.get(2), instr("c.AddOperation").bci(bci).build());

        assertEquals(3L, node.getCallTarget().call(1L, 2L));

        assertInstructionEquals(instructions.get(2), instr("c.AddOperation.q.AddLongs").bci(bci).build());

        assertEquals("foobar", node.getCallTarget().call("foo", "bar"));

        assertInstructionEquals(instructions.get(2), instr("c.AddOperation").bci(bci).build());
    }

    @Test
    @Ignore
    public void testDecisionSuperInstruction() {
        BasicInterpreter node = parseNode("decisionSuperInstruction", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endLessThanOperation();
            b.endReturn();

            b.endRoot();
        });

        // todo these tests do not pass, since quickening is not implemented yet properly

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        int bci = instructions.get(0).getNextBytecodeIndex();

        assertInstructionEquals(instructions.get(1), instr("si.load.argument.c.LessThanOperation").bci(bci).build());
    }
}
