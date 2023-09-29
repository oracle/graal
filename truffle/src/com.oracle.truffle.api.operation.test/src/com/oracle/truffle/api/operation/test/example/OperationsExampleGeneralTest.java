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
package com.oracle.truffle.api.operation.test.example;

import static com.oracle.truffle.api.operation.test.example.OperationsExampleCommon.parseNode;
import static com.oracle.truffle.api.operation.test.example.OperationsExampleCommon.hasBE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.introspection.Instruction;
import com.oracle.truffle.api.operation.introspection.OperationIntrospection;

@RunWith(Parameterized.class)
public class OperationsExampleGeneralTest extends AbstractOperationsExampleTest {
    // @formatter:off

    private static void assertInstructionEquals(Instruction instr, int bci, String name) {
        assertEquals(bci, instr.getBci());
        assertEquals(name, instr.getName());
    }

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
    public void testSumLoop() {
        // i = 0; j = 0;
        // while (i < arg0) { j = j + i; i = i + 1; }
        // return j;

        RootCallTarget root = parse("sumLoop", b -> {
            b.beginRoot(LANGUAGE);
            OperationLocal locI = b.createLocal();
            OperationLocal locJ = b.createLocal();

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

            OperationLocal local = b.createLocal();
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

            OperationLocal local0 = b.createLocal();
            OperationLocal local1 = b.createLocal();

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

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation Root ended without emitting one or more declared labels. This likely indicates a bug in the parser.");
        parse("undeclaredLabel", b -> {
            b.beginRoot(LANGUAGE);
            OperationLabel lbl = b.createLabel();
            b.emitBranch(lbl);
            b.endRoot();
        });
    }

    @Test
    public void testUnusedLabel() {
        // lbl:
        // return 42;

        RootCallTarget root = parse("unusedLabel", b -> {
            b.beginRoot(LANGUAGE);
            OperationLabel lbl = b.createLabel();
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

            OperationLocal local = b.createLocal();

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

            OperationLocal local1 = b.createLocal();
            OperationLocal local2 = b.createLocal();

            b.beginTeeLocalRange(new OperationLocal[] {local1, local2});
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
    public void testNestedFunctions() {
        // return (() -> return 1)();

        RootCallTarget root = parse("nestedFunctions", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();

            b.beginInvoke();

                b.beginRoot(LANGUAGE);

                emitReturn(b, 1);

                OperationsExample innerRoot = b.endRoot();

                b.emitLoadConstant(innerRoot);

            b.endInvoke();

            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call());
    }

    @Test
    @Ignore
    public void testLocalsNonlocalRead() {
        // todo: this test fails when boxing elimination is enabled
        // locals accessed non-locally must have boxing elimination disabled
        // since non-local reads do not do boxing elimination

        // this can be done automatically, or by
        // having `createLocal(boolean accessedFromClosure)` or similar
        RootCallTarget root = parse("localsNonlocalRead", b -> {
            // x = 1
            // return (lambda: x)()
            b.beginRoot(LANGUAGE);

            OperationLocal xLoc = b.createLocal();

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
                OperationsExample inner = b.endRoot();

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

            OperationLocal xLoc = b.createLocal();

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

                OperationsExample inner = b.endRoot();

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
    public void testIntrospectionData() {
        OperationsExample node = parseNode(interpreterClass, "introspectionData", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        OperationIntrospection data = node.getIntrospectionData();

        assertEquals(5, data.getInstructions().size());
        assertInstructionEquals(data.getInstructions().get(0), 0, "load.argument");
        assertInstructionEquals(data.getInstructions().get(1), 2, "load.argument");
        assertInstructionEquals(data.getInstructions().get(2), 4, "c.AddOperation");
        // With BE, the add instruction's encoding includes its child indices.
        int beOffset = hasBE(interpreterClass) ? 2 : 0;
        assertInstructionEquals(data.getInstructions().get(3), 6 + beOffset, "return");
        assertInstructionEquals(data.getInstructions().get(4), 7 + beOffset, "pop");

    }

    @Test
    public void testCloneUninitializedAdd() {
        // return arg0 + arg1;

        OperationsExample node = parseNode(interpreterClass, "cloneUninitializedAdd", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
        node.setUncachedInterpreterThreshold(16);
        RootCallTarget root = node.getCallTarget();

        // Run enough times to trigger cached execution.
        for (int i = 0; i < 16; i++) {
            assertEquals(42L, root.call(20L, 22L));
            assertEquals("foobar", root.call("foo", "bar"));
            assertEquals(100L, root.call(120L, -20L));
        }

        OperationsExample cloned = node.doCloneUninitialized();
        assertNotEquals(node.getCallTarget(), cloned.getCallTarget());
        root = cloned.getCallTarget();

        // Run enough times to trigger cached execution again. The transition should work without crashing.
        for (int i = 0; i < 16; i++) {
            assertEquals(42L, root.call(20L, 22L));
            assertEquals("foobar", root.call("foo", "bar"));
            assertEquals(100L, root.call(120L, -20L));
        }
    }

    @Test
    public void testCloneUninitializedFields() {
        OperationsExample node = parseNode(interpreterClass, "cloneUninitializedFields", b -> {
            b.beginRoot(LANGUAGE);
            emitReturn(b, 0);
            b.endRoot();
        });

        OperationsExample cloned = node.doCloneUninitialized();
        assertEquals("User field was not copied to the uninitialized clone.", node.name, cloned.name);
    }

    @Test
    @Ignore
    public void testDecisionQuicken() {
        OperationsExample node = parseNode(interpreterClass, "decisionQuicken", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        // todo: these tests do not pass, since quickening is not implemented yet properly

        assertInstructionEquals(node.getIntrospectionData().getInstructions().get(2), 2, "c.AddOperation");

        assertEquals(3L, node.getCallTarget().call(1L, 2L));

        assertInstructionEquals(node.getIntrospectionData().getInstructions().get(2), 2, "c.AddOperation.q.AddLongs");

        assertEquals("foobar", node.getCallTarget().call("foo", "bar"));

        assertInstructionEquals(node.getIntrospectionData().getInstructions().get(2), 2, "c.AddOperation");
    }

    @Test
    @Ignore
    public void testDecisionSuperInstruction() {
        OperationsExample node = parseNode(interpreterClass, "decisionSuperInstruction", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endLessThanOperation();
            b.endReturn();

            b.endRoot();
        });

        // todo: these tests do not pass, since quickening is not implemented yet properly

        assertInstructionEquals(node.getIntrospectionData().getInstructions().get(1), 1, "si.load.argument.c.LessThanOperation");
    }
}
