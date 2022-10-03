package com.oracle.truffle.api.operation.test.bml;

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
