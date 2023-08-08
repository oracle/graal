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
package com.oracle.truffle.api.benchmark.operation;

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

public abstract class BMLNode extends Node {

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
}

@SuppressWarnings("serial")
class ReturnException extends ControlFlowException {
    private final Object value;

    ReturnException(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }
}

class BMLRootNode extends RootNode {

    @Child BMLNode body;

    BMLRootNode(BenchmarkLanguage lang, int locals, BMLNode body) {
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

@NodeChild(type = BMLNode.class)
@NodeChild(type = BMLNode.class)
abstract class AddNode extends BMLNode {
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

@NodeChild(type = BMLNode.class)
@NodeChild(type = BMLNode.class)
abstract class ModNode extends BMLNode {
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

@NodeChild(type = BMLNode.class)
@NodeChild(type = BMLNode.class)
abstract class LessNode extends BMLNode {
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

@NodeChild(type = BMLNode.class)
abstract class StoreLocalNode extends BMLNode {

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

@NodeChild(type = BMLNode.class)
abstract class ReturnNode extends BMLNode {
    @Specialization
    public Object doReturn(Object value) {
        throw new ReturnException(value);
    }
}

abstract class LoadLocalNode extends BMLNode {
    private final int local;

    LoadLocalNode(int local) {
        this.local = local;
    }

    @Specialization
    public int loadValue(VirtualFrame frame) {
        return frame.getInt(local);
    }
}

class IfNode extends BMLNode {
    @Child BMLNode condition;
    @Child BMLNode thenBranch;
    @Child BMLNode elseBranch;

    public static IfNode create(BMLNode condition, BMLNode thenBranch, BMLNode elseBranch) {
        return new IfNode(condition, thenBranch, elseBranch);
    }

    IfNode(BMLNode condition, BMLNode thenBranch, BMLNode elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (condition.execute(frame) == Boolean.TRUE) {
            thenBranch.execute(frame);
        } else {
            thenBranch.execute(frame);
        }
        return VOID;
    }
}

class WhileNode extends BMLNode {

    @Child private BMLNode condition;
    @Child private BMLNode body;

    public static WhileNode create(BMLNode condition, BMLNode body) {
        return new WhileNode(condition, body);
    }

    WhileNode(BMLNode condition, BMLNode body) {
        this.condition = condition;
        this.body = body;

    }

    @Override
    public Object execute(VirtualFrame frame) {
        int count = 0;
        while (condition.execute(frame) == Boolean.TRUE) {
            body.execute(frame);
            count++;
        }
        LoopNode.reportLoopCount(this, count);
        return VOID;
    }
}

class BlockNode extends BMLNode {
    @Children final BMLNode[] nodes;

    public static final BlockNode create(BMLNode... nodes) {
        return new BlockNode(nodes);
    }

    BlockNode(BMLNode[] nodes) {
        this.nodes = nodes;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        for (BMLNode node : nodes) {
            node.execute(frame);
        }
        return VOID;
    }
}

abstract class ConstNode extends BMLNode {

    private final int value;

    ConstNode(int value) {
        this.value = value;
    }

    @Specialization
    public int doIt() {
        return value;
    }
}
