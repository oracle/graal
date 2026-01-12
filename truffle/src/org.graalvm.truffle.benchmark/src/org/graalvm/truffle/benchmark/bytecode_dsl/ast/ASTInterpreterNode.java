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
package org.graalvm.truffle.benchmark.bytecode_dsl.ast;

import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.ConstNodeGen;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.CountingConditionProfile;

public abstract class ASTInterpreterNode extends Node {

    protected static final Object VOID = new Object();

    public abstract Object execute(VirtualFrame frame);

    @SuppressWarnings("unused")
    public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        throw new AssertionError();
    }

    @SuppressWarnings("unused")
    public boolean executeBool(VirtualFrame frame) throws UnexpectedResultException {
        throw new AssertionError();
    }

    @SuppressWarnings("serial")
    public static class ReturnException extends ControlFlowException {
        private final Object value;

        ReturnException(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }
    }

    @NodeChild(type = ASTInterpreterNode.class)
    @NodeChild(type = ASTInterpreterNode.class)
    public abstract static class AddNode extends ASTInterpreterNode {
        @Specialization
        public int addInts(int lhs, int rhs) {
            return lhs + rhs;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object fallback(Object lhs, Object rhs) {
            throw new AssertionError();
        }
    }

    @NodeChild(type = ASTInterpreterNode.class)
    @NodeChild(type = ASTInterpreterNode.class)
    public abstract static class MultNode extends ASTInterpreterNode {
        @Specialization
        public int multInts(int lhs, int rhs) {
            return lhs * rhs;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object fallback(Object lhs, Object rhs) {
            throw new AssertionError();
        }
    }

    @NodeChild(type = ASTInterpreterNode.class)
    @NodeChild(type = ASTInterpreterNode.class)
    public abstract static class DivNode extends ASTInterpreterNode {
        @Specialization
        public int divInts(int lhs, int rhs) {
            return lhs / rhs;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object fallback(Object lhs, Object rhs) {
            throw new AssertionError();
        }
    }

    @NodeChild(type = ASTInterpreterNode.class)
    @NodeChild(type = ASTInterpreterNode.class)
    public abstract static class ModNode extends ASTInterpreterNode {
        @Specialization
        public int modInts(int lhs, int rhs) {
            return lhs % rhs;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object fallback(Object lhs, Object rhs) {
            throw new AssertionError();
        }
    }

    @NodeChild(type = ASTInterpreterNode.class)
    @NodeChild(type = ASTInterpreterNode.class)
    public abstract static class LessNode extends ASTInterpreterNode {
        @Specialization
        public boolean compareInts(int lhs, int rhs) {
            return lhs < rhs;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object fallback(Object lhs, Object rhs) {
            throw new AssertionError();
        }
    }

    @NodeChild(type = ASTInterpreterNode.class)
    public abstract static class StoreLocalNode extends ASTInterpreterNode {

        private final int local;

        StoreLocalNode(int local) {
            this.local = local;
        }

        @Specialization
        public Object storeValue(VirtualFrame frame, int value) {
            frame.setInt(local, value);
            return VOID;
        }
    }

    @NodeChild(type = ASTInterpreterNode.class)
    public abstract static class ReturnNode extends ASTInterpreterNode {
        @Specialization
        public Object doReturn(Object value) {
            throw new ReturnException(value);
        }
    }

    public abstract static class LoadLocalNode extends ASTInterpreterNode {
        private final int local;

        LoadLocalNode(int local) {
            this.local = local;
        }

        @Specialization
        public int loadValue(VirtualFrame frame) {
            return frame.getInt(local);
        }
    }

    public static class IfNode extends ASTInterpreterNode {
        @Child ASTInterpreterNode condition;
        @Child ASTInterpreterNode thenBranch;
        @Child ASTInterpreterNode elseBranch;
        private final CountingConditionProfile profile;

        public static IfNode create(ASTInterpreterNode condition, ASTInterpreterNode thenBranch, ASTInterpreterNode elseBranch) {
            return new IfNode(condition, thenBranch, elseBranch);
        }

        IfNode(ASTInterpreterNode condition, ASTInterpreterNode thenBranch, ASTInterpreterNode elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
            this.profile = CountingConditionProfile.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (profile.profile(condition.execute(frame) == Boolean.TRUE)) {
                thenBranch.execute(frame);
            } else {
                elseBranch.execute(frame);
            }
            return VOID;
        }
    }

    public static class WhileNode extends ASTInterpreterNode {

        @Child private ASTInterpreterNode condition;
        @Child private ASTInterpreterNode body;
        private final CountingConditionProfile profile;

        public static WhileNode create(ASTInterpreterNode condition, ASTInterpreterNode body) {
            return new WhileNode(condition, body);
        }

        WhileNode(ASTInterpreterNode condition, ASTInterpreterNode body) {
            this.condition = condition;
            this.body = body;
            this.profile = CountingConditionProfile.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int count = 0;
            while (profile.profile(condition.execute(frame) == Boolean.TRUE)) {
                body.execute(frame);
                count++;
            }
            LoopNode.reportLoopCount(this, count);
            return VOID;
        }
    }

    public static class BlockNode extends ASTInterpreterNode {
        @Children final ASTInterpreterNode[] nodes;

        public static final BlockNode create(ASTInterpreterNode... nodes) {
            return new BlockNode(nodes);
        }

        BlockNode(ASTInterpreterNode[] nodes) {
            this.nodes = nodes;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            for (ASTInterpreterNode node : nodes) {
                node.execute(frame);
            }
            return VOID;
        }
    }

    public static class SwitchNode extends ASTInterpreterNode {
        @Child ASTInterpreterNode valueNode;
        @Children final ASTInterpreterNode[] caseValues;
        @Children final ASTInterpreterNode[] caseNodes;

        public static final SwitchNode create(ASTInterpreterNode valueNode, Object... cases) {
            if (cases.length % 2 != 0) {
                throw new AssertionError("cases should be value-node pairs");
            }
            ASTInterpreterNode[] caseValues = new ASTInterpreterNode[cases.length / 2];
            ASTInterpreterNode[] caseNodes = new ASTInterpreterNode[cases.length / 2];
            for (int i = 0; i < caseValues.length; i++) {
                if (!(cases[i * 2] instanceof Integer num)) {
                    throw new AssertionError("invalid case value at index " + (i * 2) + ": " + cases[i * 2]);
                }
                if (!(cases[i * 2 + 1] instanceof ASTInterpreterNode node)) {
                    throw new AssertionError("invalid case node at index " + (i * 2 + 1) + ": " + cases[i * 2 + 1]);
                }
                caseValues[i] = ConstNodeGen.create(num);
                caseNodes[i] = node;
            }
            return new SwitchNode(valueNode, caseValues, caseNodes);
        }

        SwitchNode(ASTInterpreterNode value, ASTInterpreterNode[] caseValues, ASTInterpreterNode[] caseNodes) {
            if (caseValues.length != caseNodes.length) {
                throw new AssertionError("case size mismatch: " + caseValues.length + " versus " + caseNodes.length);
            }
            this.valueNode = value;
            this.caseValues = caseValues;
            this.caseNodes = caseNodes;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            Object value = this.valueNode.execute(frame);
            for (int i = 0; i < caseValues.length; i++) {
                if (value == caseValues[i].execute(frame)) {
                    return caseNodes[i].execute(frame);
                }
            }
            return CompilerDirectives.shouldNotReachHere(unhandledValue(value));
        }

        @CompilerDirectives.TruffleBoundary
        private String unhandledValue(Object value) {
            return "unhandled value " + value;
        }
    }

    public abstract static class ConstNode extends ASTInterpreterNode {

        private final int value;

        ConstNode(int value) {
            this.value = value;
        }

        @Specialization
        public int doIt() {
            return value;
        }
    }

    @SuppressWarnings("truffle-inlining")
    public abstract static class LoadArgumentNode extends ASTInterpreterNode {
        @Specialization
        public static Object perform(VirtualFrame frame) {
            return frame.getArguments()[0];
        }
    }

    @NodeChild(type = ASTInterpreterNode.class)
    public abstract static class ArrayLengthNode extends ASTInterpreterNode {
        @Specialization
        public static int doArray(int[] array) {
            return array.length;
        }
    }

    @NodeChild(value = "array", type = ASTInterpreterNode.class)
    @NodeChild(value = "i", type = ASTInterpreterNode.class)
    public abstract static class ArrayIndexNode extends ASTInterpreterNode {
        @Specialization
        public static int doArray(int[] array, int i) {
            return array[i];
        }
    }
}
