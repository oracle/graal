/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instruction.Argument;
import com.oracle.truffle.api.bytecode.Instruction.Argument.Kind;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

public class SplittingTest extends AbstractBasicInterpreterTest {

    public SplittingTest(TestRun run) {
        super(run);
    }

    Context context;

    @Before
    public void before() {
        // we can only perform this test if the runtime enables splitting
        Assume.assumeNotNull(split(parseNode("dummy", b -> {
            b.beginRoot();
            b.endRoot();
        })));
        context = Context.create();
        context.enter();
    }

    @After
    public void leave() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void testBytecodeUpdateInSplits() {
        Source s = Source.newBuilder("", "", "test.name").build();
        BasicInterpreter original = parseNode("bytecodeUpdateInSplits", b -> {
            b.beginSource(s);
            b.beginSourceSection(0, 0);
            b.beginRoot();
            b.beginTag(StatementTag.class);
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endTag(StatementTag.class);
            b.endRoot();
            b.endSourceSection();
            b.endSource();
        });

        BasicInterpreter split0 = split(original);
        assertNotSame(original, split0);

        assertNull(split0.getBytecodeNode().getSourceInformation());
        original.getRootNodes().ensureSourceInformation();

        assertNotNull(split0.getBytecodeNode().getSourceInformation());
        assertNotNull(original.getBytecodeNode().getSourceInformation());

        BasicInterpreter split1 = split(original);
        assertNotSame(original, split1);
        assertNotNull(split1.getBytecodeNode().getSourceInformation());

        assertNull(original.getBytecodeNode().getTagTree());
        assertNull(split0.getBytecodeNode().getTagTree());
        assertNull(split1.getBytecodeNode().getTagTree());

        original.getRootNodes().ensureComplete();

        assertNotNull(original.getBytecodeNode().getTagTree());
        assertNotNull(split0.getBytecodeNode().getTagTree());
        assertNotNull(split1.getBytecodeNode().getTagTree());
    }

    @Test
    public void testIndepentProfile() {
        Source s = Source.newBuilder("", "", "test.name").build();
        BasicInterpreter original = parseNode("indepentProfile", b -> {
            b.beginSource(s);
            b.beginSourceSection(0, 0);
            b.beginRoot();
            b.beginTag(StatementTag.class);
            b.beginReturn();
            b.beginAdd();
            b.emitLoadConstant(21L);
            b.emitLoadConstant(21L);
            b.endAdd();
            b.endReturn();
            b.endTag(StatementTag.class);
            b.endRoot();
            b.endSourceSection();
            b.endSource();
        });

        BasicInterpreter split0 = split(original);
        assertNotSame(original, split0);

        assertEquals(42L, original.getCallTarget().call());
        assertEquals(42L, split0.getCallTarget().call());

        original.getRootNodes().update(BytecodeConfig.COMPLETE); // materialize tags

        // tag tree must not be shared between bytecode nodes
        assertNotSameOrNull(original.getBytecodeNode().getTagTree(), split0.getBytecodeNode().getTagTree());
        assertNotSameOrNull(findNode(original.getBytecodeNode(), "c.AddOperation"), findNode(split0.getBytecodeNode(), "c.AddOperation"));

        assertEquals(42L, original.getCallTarget().call());
        assertEquals(42L, split0.getCallTarget().call());

        // create an additional split after execution
        BasicInterpreter split1 = split(original);

        // tag tree must not be shared between bytecode nodes
        assertNotSameOrNull(original.getBytecodeNode().getTagTree(), split0.getBytecodeNode().getTagTree());
        assertNotSameOrNull(original.getBytecodeNode().getTagTree(), split1.getBytecodeNode().getTagTree());
        assertNotSameOrNull(split0.getBytecodeNode().getTagTree(), split1.getBytecodeNode().getTagTree());

        assertNotSameOrNull(findNode(original.getBytecodeNode(), "c.AddOperation"), findNode(split0.getBytecodeNode(), "c.AddOperation"));
        assertNotSameOrNull(findNode(original.getBytecodeNode(), "c.AddOperation"), findNode(split1.getBytecodeNode(), "c.AddOperation"));
        assertNotSameOrNull(findNode(split0.getBytecodeNode(), "c.AddOperation"), findNode(split1.getBytecodeNode(), "c.AddOperation"));

        assertEquals(42L, original.getCallTarget().call());
        assertEquals(42L, split0.getCallTarget().call());
    }

    private static void assertNotSameOrNull(Object expected, Object actual) {
        if (expected == null) {
            assertNull(actual);
        } else {
            assertNotSame(expected, actual);
        }
    }

    private static Node findNode(BytecodeNode node, String name) {
        Instruction instr = findInstruction(node, name);
        if (instr == null) {
            return null;
        }
        for (Argument arg : instr.getArguments()) {
            if (arg.getKind() == Kind.NODE_PROFILE) {
                return arg.asCachedNode();
            }
        }
        return null;

    }

    private static Instruction findInstruction(BytecodeNode node, String name) {
        for (Instruction instruction : node.getInstructions()) {
            if (instruction.getName().contains(name)) {
                return instruction;
            }
        }
        return null;
    }

    private static BasicInterpreter split(BasicInterpreter node) {
        DirectCallNode callNode1 = DirectCallNode.create(node.getCallTarget());
        boolean split = callNode1.cloneCallTarget();
        if (!split) {
            return null;
        }
        return (BasicInterpreter) ((RootCallTarget) callNode1.getClonedCallTarget()).getRootNode();
    }

}
