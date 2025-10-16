/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.test.GenerateInstructionRewriter.Instruction;
import com.oracle.truffle.api.bytecode.test.GenerateInstructionRewriter.InstructionPattern;
import com.oracle.truffle.api.bytecode.test.GenerateInstructionRewriter.InstructionRewriteRule;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;

/**
 * Tests rewriter rule validation and code generation, including end-to-end rewriting tests.
 */
public class InstructionRewriterTest {

    @GenerateInstructionRewriter(instructionSet = {
                    @Instruction(name = "foo"),
                    @Instruction(name = "bar"),
                    @Instruction(name = "baz"),
                    @Instruction(name = "foobar"),
                    @Instruction(name = "foobaz"),
                    @Instruction(name = "foofoofoo")
    }, rules = {
                    @InstructionRewriteRule(//
                                    lhs = {@InstructionPattern("foo"), @InstructionPattern("bar")}, //
                                    rhs = {@InstructionPattern("foobar")} //
                    ), //
                    @InstructionRewriteRule(//
                                    lhs = {@InstructionPattern("foo"), @InstructionPattern("baz")}, //
                                    rhs = {@InstructionPattern("foobaz")} //
                    ), //
                    @InstructionRewriteRule(//
                                    lhs = {@InstructionPattern("foo"), @InstructionPattern("foo"), @InstructionPattern("foo")}, //
                                    rhs = {@InstructionPattern("foofoofoo")} //
                    ), //
    })
    public class SimpleInstructionRewriter {
        private static RewriteFrom assertRewrites(String... lhs) {
            return new RewriteFrom(lhs);
        }

        private record RewriteFrom(String... lhs) {
            private void to(String... rhs) {
                assertArrayEquals(rhs, SimpleInstructionRewriterGen.rewrite(lhs));
            }
        }
    }

    @Test
    public void testSimpleRewrites() {
        SimpleInstructionRewriter.assertRewrites("foo", "bar").to("foobar");
        SimpleInstructionRewriter.assertRewrites("foo", "baz").to("foobaz");
        SimpleInstructionRewriter.assertRewrites("foo", "foo", "foo").to("foofoofoo");
    }

    @Test
    public void testRewriteIgnorePrefix() {
        SimpleInstructionRewriter.assertRewrites("foo", "foo", "bar").to("foobar");
        SimpleInstructionRewriter.assertRewrites("foo", "foo", "baz").to("foobaz");
        SimpleInstructionRewriter.assertRewrites("bar", "foo", "baz").to("foobaz");
    }

    @GenerateInstructionRewriter(instructionSet = {
                    @Instruction(name = "foo"),
                    @Instruction(name = "bar", immediates = {"barimm"}),
                    @Instruction(name = "baz", immediates = {"bazimm"}),
                    @Instruction(name = "foobar"),
                    @Instruction(name = "foobaz"),
    }, rules = {
                    @InstructionRewriteRule(//
                                    lhs = {@InstructionPattern("foo"), @InstructionPattern("bar")}, //
                                    rhs = {@InstructionPattern("foobar")} //
                    ), //
                    @InstructionRewriteRule(//
                                    lhs = {@InstructionPattern("foo"), @InstructionPattern("baz")}, //
                                    rhs = {@InstructionPattern("foobaz")} //
                    ), //
    })
    public class InstructionRewriterDifferentEncodings {
        private static RewriteFrom assertRewrites(String... lhs) {
            return new RewriteFrom(lhs);
        }

        private record RewriteFrom(String... lhs) {
            private void to(String... rhs) {
                assertArrayEquals(rhs, InstructionRewriterDifferentEncodingsGen.rewrite(lhs));
            }
        }
    }

    @Test
    public void testRewritesDifferentEncodings() {
        InstructionRewriterDifferentEncodings.assertRewrites("foo", "bar").to("foobar");
        InstructionRewriterDifferentEncodings.assertRewrites("foo", "baz").to("foobaz");
    }

    @GenerateInstructionRewriter(instructionSet = {
                    @Instruction(name = "foo", immediates = {"fooimm"}),
                    @Instruction(name = "bar", immediates = {"barimm"}),
                    @Instruction(name = "baz"),
                    @Instruction(name = "foobar", immediates = {"foobarimm"}),
                    @Instruction(name = "barbaz"),
                    @Instruction(name = "bazfoo"),
    }, rules = {
                    @InstructionRewriteRule(//
                                    lhs = {@InstructionPattern(value = "foo", immediates = "X"), @InstructionPattern(value = "bar", immediates = "X")}, //
                                    rhs = {@InstructionPattern(value = "foobar", immediates = "X")} //
                    ), //
                    @InstructionRewriteRule(//
                                    lhs = {@InstructionPattern("bar"), @InstructionPattern("baz")}, //
                                    rhs = {@InstructionPattern("barbaz")} //
                    ), //
                    @InstructionRewriteRule(//
                                    lhs = {@InstructionPattern("baz"), @InstructionPattern("foo")}, //
                                    rhs = {@InstructionPattern("bazfoo")} //
                    ), //
    })
    public class InstructionRewriterImmediateConstraint {
        private static RewriteFrom assertRewrites(String... lhs) {
            return new RewriteFrom(lhs);
        }

        private record RewriteFrom(String... lhs) {
            private void to(String... rhs) {
                assertArrayEquals(rhs, InstructionRewriterImmediateConstraintGen.rewrite(lhs));
            }
        }
    }

    /*
     * Immediate constraints are checked outside of the DFA. Here we just verify that the DFA
     * correctly detects the rewrite candidate.
     */
    @Test
    public void testRewriterWithImmediateConstraint() {
        // The DFA should report the conditional rewrite opportunity.
        InstructionRewriterImmediateConstraint.assertRewrites("foo", "bar").to("foobar");
        // Since the first rule is conditional (depending on the immediates), we should be able to
        // transition out of the accepting state and recognize bar as part of the second rule.
        InstructionRewriterImmediateConstraint.assertRewrites("foo", "bar", "baz").to("barbaz");
        // Conversely, since the second rule is unconditional, we should not transition out of the
        // accepting state to recognize the third rule.
        InstructionRewriterImmediateConstraint.assertRewrites("foo", "bar", "baz", "foo").to((String[]) null);
        // But we should still recognize the third rule independently.
        InstructionRewriterImmediateConstraint.assertRewrites("foo", "baz", "foo").to("bazfoo");
    }

    @ExpectError("Unknown instruction bar")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo")
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("foo")}, rhs = {@InstructionPattern("bar")})
                    })
    public class MissingRhs {
    }

    @ExpectError("Unknown instruction bar")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo")
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("bar")}, rhs = {@InstructionPattern("foo")})
                    })
    public class MissingLhs {
    }

    @ExpectError("Instruction bar in the rhs of rewrite rule foo() -> bar(_) is missing an immediate binding.%")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo"),
                                    @Instruction(name = "bar", immediates = {"imm1"})
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("foo")}, rhs = {@InstructionPattern("bar")})
                    })
    public class MissingImmediateBindingRhs {
    }

    @ExpectError("Instruction foo declares 0 immediate(s) but 1 immediate(s) specified in pattern: [imm1]")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo")
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern(value = "foo", immediates = {"imm1"})}, rhs = {})
                    })
    public class ImmediateBindingArityMismatch {
    }

    @ExpectError("Instruction foo declares 1 immediate(s) but 2 immediate(s) specified in pattern: [imm1, imm2]")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo", immediates = {"imm1"})
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern(value = "foo", immediates = {"imm1", "imm2"})}, rhs = {})
                    })
    public class ImmediateBindingArityMismatch2 {
    }

    @ExpectError("Found unbound immediate imm1 in the rhs of rewrite rule bar() -> foo(imm1).%")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo", immediates = {"imm1"}),
                                    @Instruction(name = "bar"),
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("bar")}, rhs = {@InstructionPattern(value = "foo", immediates = {"imm1"})})
                    })
    public class UnboundImmediateBinding {
    }

    @ExpectError("The instructions on the lhs and rhs of rewrite rule foo() -> bar() have different stack effects%")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo", stackEffect = 1),
                                    @Instruction(name = "bar", stackEffect = 0),
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("foo")}, rhs = {@InstructionPattern("bar")})
                    })
    public class UnbalancedRewriteRule {
    }

    @ExpectError("Multiple rewrite rules declared with the same lhs: foo() -> bar(), foo() -> baz()")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo"),
                                    @Instruction(name = "bar"),
                                    @Instruction(name = "baz"),
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("foo")}, rhs = {@InstructionPattern("bar")}),
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("foo")}, rhs = {@InstructionPattern("baz")})
                    })
    public class DuplicateRewriteRule {
    }

    @ExpectError("Rewrite rule foo() bar() -> baz() has a lhs containing the lhs of rewrite rule foo() -> baz()")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo"),
                                    @Instruction(name = "bar"),
                                    @Instruction(name = "baz")
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("foo"), @InstructionPattern("bar")}, rhs = {@InstructionPattern("baz")}),
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("foo")}, rhs = {@InstructionPattern("baz")})
                    })
    public class SubstringRewriteRule1 {
    }

    @ExpectError("Rewrite rule foo() bar() -> baz() has a lhs containing the lhs of rewrite rule bar() -> baz()")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo"),
                                    @Instruction(name = "bar"),
                                    @Instruction(name = "baz")
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("foo"), @InstructionPattern("bar")}, rhs = {@InstructionPattern("baz")}),
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("bar")}, rhs = {@InstructionPattern("baz")})
                    })
    public class SubstringRewriteRule2 {
    }

    @ExpectError("Rewrite rule foo() bar() baz() -> baz() has a lhs containing the lhs of rewrite rule bar() -> baz()")
    @GenerateInstructionRewriter(//
                    instructionSet = {
                                    @Instruction(name = "foo"),
                                    @Instruction(name = "bar"),
                                    @Instruction(name = "baz")
                    }, rules = {
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("foo"), @InstructionPattern("bar"), @InstructionPattern("baz")}, rhs = {@InstructionPattern("baz")}),
                                    @InstructionRewriteRule(lhs = {@InstructionPattern("bar")}, rhs = {@InstructionPattern("baz")})
                    })
    public class SubstringRewriteRule3 {
    }

}
