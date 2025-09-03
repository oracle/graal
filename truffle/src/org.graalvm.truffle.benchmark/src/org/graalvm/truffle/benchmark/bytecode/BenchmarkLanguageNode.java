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
package org.graalvm.truffle.benchmark.bytecode;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.CountingConditionProfile;

public abstract class BenchmarkLanguageNode extends Node {

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

    public static class BenchmarkLanguageRootNode extends RootNode {

        @Child BenchmarkLanguageNode body;

        public BenchmarkLanguageRootNode(BenchmarkLanguage lang, int locals, BenchmarkLanguageNode body) {
            super(lang, createFrame(locals));
            this.body = body;
        }

        private static FrameDescriptor createFrame(int locals) {
            FrameDescriptor.Builder b = FrameDescriptor.newBuilder(locals);
            b.addSlots(locals, FrameSlotKind.Illegal);
            return b.build();
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            try {
                body.execute(frame);
            } catch (ReturnException ex) {
                return ex.getValue();
            }

            throw new AssertionError();
        }

    }

    @NodeChild(type = BenchmarkLanguageNode.class)
    @NodeChild(type = BenchmarkLanguageNode.class)
    public abstract static class AddNode extends BenchmarkLanguageNode {
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

    @NodeChild(type = BenchmarkLanguageNode.class)
    @NodeChild(type = BenchmarkLanguageNode.class)
    public abstract static class ModNode extends BenchmarkLanguageNode {
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

    @NodeChild(type = BenchmarkLanguageNode.class)
    @NodeChild(type = BenchmarkLanguageNode.class)
    public abstract static class LessNode extends BenchmarkLanguageNode {
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

    @NodeChild(type = BenchmarkLanguageNode.class)
    public abstract static class StoreLocalNode extends BenchmarkLanguageNode {

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

    @NodeChild(type = BenchmarkLanguageNode.class)
    public abstract static class ReturnNode extends BenchmarkLanguageNode {
        @Specialization
        public Object doReturn(Object value) {
            throw new ReturnException(value);
        }
    }

    public abstract static class LoadLocalNode extends BenchmarkLanguageNode {
        private final int local;

        LoadLocalNode(int local) {
            this.local = local;
        }

        @Specialization
        public int loadValue(VirtualFrame frame) {
            return frame.getInt(local);
        }
    }

    public static class IfNode extends BenchmarkLanguageNode {
        @Child BenchmarkLanguageNode condition;
        @Child BenchmarkLanguageNode thenBranch;
        @Child BenchmarkLanguageNode elseBranch;
        private final CountingConditionProfile profile;

        public static IfNode create(BenchmarkLanguageNode condition, BenchmarkLanguageNode thenBranch, BenchmarkLanguageNode elseBranch) {
            return new IfNode(condition, thenBranch, elseBranch);
        }

        IfNode(BenchmarkLanguageNode condition, BenchmarkLanguageNode thenBranch, BenchmarkLanguageNode elseBranch) {
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

    public static class WhileNode extends BenchmarkLanguageNode {

        @Child private BenchmarkLanguageNode condition;
        @Child private BenchmarkLanguageNode body;
        private final CountingConditionProfile profile;

        public static WhileNode create(BenchmarkLanguageNode condition, BenchmarkLanguageNode body) {
            return new WhileNode(condition, body);
        }

        WhileNode(BenchmarkLanguageNode condition, BenchmarkLanguageNode body) {
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

    public static class BlockNode extends BenchmarkLanguageNode {
        @Children final BenchmarkLanguageNode[] nodes;

        public static final BlockNode create(BenchmarkLanguageNode... nodes) {
            return new BlockNode(nodes);
        }

        BlockNode(BenchmarkLanguageNode[] nodes) {
            this.nodes = nodes;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            for (BenchmarkLanguageNode node : nodes) {
                node.execute(frame);
            }
            return VOID;
        }
    }

    public abstract static class ConstNode extends BenchmarkLanguageNode {

        private final int value;

        ConstNode(int value) {
            this.value = value;
        }

        @Specialization
        public int doIt() {
            return value;
        }
    }
}
