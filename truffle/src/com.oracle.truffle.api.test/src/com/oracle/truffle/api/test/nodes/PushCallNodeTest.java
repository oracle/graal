package com.oracle.truffle.api.test.nodes;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class PushCallNodeTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testCallNodePickedByCallTargetCall() {
        CallTarget iterateFrames = create(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return captureStack();
            }
        });
        Node callLocation = new Node() {
        };

        CallTarget root = create(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return boundary(callLocation, () -> iterateFrames.call());
            }
        });
        List<TruffleStackTraceElement> frames = (List<TruffleStackTraceElement>) root.call();
        assertEquals(2, frames.size());
        Iterator<TruffleStackTraceElement> iterator = frames.iterator();
        TruffleStackTraceElement frame;

        frame = iterator.next();
        assertSame(iterateFrames, frame.getTarget());
        assertNull(frame.getLocation());

        frame = iterator.next();
        assertSame(root, frame.getTarget());
        assertSame(callLocation, frame.getLocation());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCallNodePickedWithoutCallTarget() {
        Node callLocation = new Node() {
        };
        CallTarget root = create(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return boundary(callLocation, () -> captureStack());
            }
        });

        List<TruffleStackTraceElement> frames = (List<TruffleStackTraceElement>) root.call();
        assertEquals(1, frames.size());
        TruffleStackTraceElement frame = frames.iterator().next();
        assertSame(root, frame.getTarget());
        assertSame(callLocation, frame.getLocation());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDirectCallNodeClearedAssertion() {
        Node callLocation = new Node() {
        };
        Node prev = IndirectCallNode.pushCallLocation(callLocation);
        try {
            DirectCallNode callNode = DirectCallNode.create(create(RootNode.createConstantNode("")));
            assertAssertionError(() -> callNode.call(new Object[0]));
        } finally {
            IndirectCallNode.popCallLocation(prev);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIndirectCallNodeClearedAssertion() {
        Node callLocation = new Node() {
        };
        Node prev = IndirectCallNode.pushCallLocation(callLocation);
        try {
            IndirectCallNode callNode = IndirectCallNode.create();
            assertAssertionError(() -> callNode.call(create(RootNode.createConstantNode("")), new Object[0]));
        } finally {
            IndirectCallNode.popCallLocation(prev);
        }
    }

    private static void assertAssertionError(Runnable r) {
        try {
            r.run();
        } catch (AssertionError e) {
            return;
        }
        fail();
    }

    static CallTarget create(RootNode root) {
        return Truffle.getRuntime().createCallTarget(root);
    }

    @TruffleBoundary
    private static Object captureStack() {
        Exception e = new Exception();
        TruffleStackTraceElement.fillIn(e);
        return TruffleStackTraceElement.getStackTrace(e);
    }

    @TruffleBoundary
    public static Object boundary(Node callNode, Supplier<Object> run) {
        Node prev = IndirectCallNode.pushCallLocation(callNode);
        try {
            return run.get();
        } finally {
            IndirectCallNode.popCallLocation(prev);
        }
    }

}
