/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;

public class BranchTest extends AbstractBasicInterpreterTest {
    // @formatter:off

    @Test
    public void testBranchForward() {
        // goto lbl;
        // return 0;
        // lbl:
        // return 1;

        RootCallTarget root = parse("branchForward", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLabel lbl = b.createLabel();

            b.emitBranch(lbl);
            emitReturn(b, 0);
            b.emitLabel(lbl);
            emitReturn(b, 1);
            b.endRoot();
        });

        assertEquals(1L, root.call());
    }

    @Test
    public void testBranchBackward() {
        // x = 0;
        // lbl:
        // if (5 < x) return x;
        // x = x + 1;
        // goto lbl;

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Backward branches are unsupported. Use a While operation to model backward control flow.");
        parse("branchBackward", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLabel lbl = b.createLabel();
            BytecodeLocal loc = b.createLocal();

            b.beginStoreLocal(loc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.emitLabel(lbl);

            b.beginIfThen();

                b.beginLessThanOperation();
                b.emitLoadConstant(5L);
                b.emitLoadLocal(loc);
                b.endLessThanOperation();

                b.beginReturn();
                b.emitLoadLocal(loc);
                b.endReturn();

            b.endIfThen();

            b.beginStoreLocal(loc);
            b.beginAddOperation();
            b.emitLoadLocal(loc);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endStoreLocal();

            b.emitBranch(lbl);

            b.endRoot();
        });
    }

    @Test
    public void testBranchOutwardValid() {
        // {
        //   if(arg0 < 0) goto lbl;
        //   return 123;
        // }
        // lbl:
        // return 42;

        RootCallTarget root = parse("branchOutwardValid", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLabel lbl = b.createLabel();

            b.beginBlock();
              b.beginIfThen();

              b.beginLessThanOperation();
              b.emitLoadArgument(0);
              b.emitLoadConstant(0L);
              b.endLessThanOperation();

              b.emitBranch(lbl);

              b.endIfThen();

              emitReturn(b, 123L);
            b.endBlock();

            b.emitLabel(lbl);

            emitReturn(b, 42);

            b.endRoot();
        });

        assertEquals(123L, root.call(1L));
        assertEquals(42L, root.call(-1L));
    }

    @Test
    public void testBranchOutwardInvalid() {
        // return 1 + { goto lbl; 2 }
        // lbl:
        // return 0;

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("BytecodeLabel was emitted at a position with a different stack height than a branch instruction that targets it. Expected stack height 1, but was 0. Branches must be balanced.");
        parse("branchOutwardInvalid", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLabel lbl = b.createLabel();

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(1L);
            b.beginBlock();
              b.emitBranch(lbl);
              b.emitLoadConstant(2L);
            b.endBlock();
            b.endAddOperation();
            b.endReturn();

            b.emitLabel(lbl);

            emitReturn(b, 0);

            b.endRoot();
        });

    }

    @Test
    public void testBranchInward() {
        // goto lbl;
        // return 1 + { lbl: 2 }

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("BytecodeLabel must be emitted inside the same operation it was created in.");
        parse("branchInward", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLabel lbl = b.createLabel();
            b.emitBranch(lbl);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(1L);
            b.beginBlock();
              b.emitLabel(lbl);
              b.emitLoadConstant(2L);
            b.endBlock();
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testBranchBalancedStack() {
        // return 40 + {
        //   local result;
        //   if arg0 < 0 branch x
        //   result = 3
        //   branch y
        //   x:
        //   result = 2
        //   y:
        //   result
        // };

        RootCallTarget root = parse("branchInvalidStack", b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginAddOperation();

                b.emitLoadConstant(40L);

                b.beginBlock();
                    BytecodeLocal result = b.createLocal();
                    BytecodeLabel x = b.createLabel();
                    BytecodeLabel y = b.createLabel();
                    b.beginIfThen();
                        b.beginLessThanOperation();
                            b.emitLoadArgument(0);
                            b.emitLoadConstant(0L);
                        b.endLessThanOperation();

                        b.emitBranch(x);
                    b.endIfThen();

                    b.beginStoreLocal(result);
                    b.emitLoadConstant(3L);
                    b.endStoreLocal();

                    b.emitBranch(y);

                    b.emitLabel(x);

                    b.beginStoreLocal(result);
                    b.emitLoadConstant(2L);
                    b.endStoreLocal();

                    b.emitLabel(y);

                    b.emitLoadLocal(result);
                b.endBlock();

            b.endAddOperation();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.call(-1L));
        assertEquals(43L, root.call(1L));
    }

    @Test
    public void testBranchIntoAnotherBlock() {
        // { lbl: return 0 }
        // { goto lbl; }

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Branch must be targeting a label that is declared in an enclosing operation. Jumps into other operations are not permitted.");
        parse("branchIntoAnotherBlock", b -> {
            b.beginRoot(LANGUAGE);

            b.beginBlock();
                BytecodeLabel lbl = b.createLabel();
                b.emitLabel(lbl);
                emitReturn(b, 0);
            b.endBlock();

            b.beginBlock();
                b.emitBranch(lbl);
            b.endBlock();

            b.endRoot();
        });
    }

    @Test
    public void testDanglingLabel() {
        // {
        //   x = 42
        //   goto lbl;
        //   x = 123;
        //   456      // this should get popped, otherwise the stack heights don't match
        //   lbl:
        // }
        // return x;

        RootCallTarget root = parse("branchForward", b -> {
            b.beginRoot(LANGUAGE);
            BytecodeLocal x = b.createLocal();

            b.beginBlock();
            BytecodeLabel lbl = b.createLabel();

            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.emitBranch(lbl);

            b.beginStoreLocal(x);
            b.emitLoadConstant(123L);
            b.endStoreLocal();

            b.emitLoadConstant(456L);

            b.emitLabel(lbl);

            b.endBlock();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, root.call());
    }

}
