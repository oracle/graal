package org.graalvm.compiler.truffle.test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

public class SubmitLexicalSingleCallerTest extends TestWithPolyglotOptions {

    public static final int COMPILATION_THRESHOLD = 100;
    private ByteArrayOutputStream err;

    @Before
    public void before() {
        err = new ByteArrayOutputStream();
        setupContext(Context.newBuilder().//
                        option("engine.TraceCompilationDetails", Boolean.TRUE.toString()).//
                        option("engine.SubmitLexicalSingleCaller", Boolean.TRUE.toString()).//
                        option("engine.BackgroundCompilation", Boolean.FALSE.toString()).//
                        option("engine.BackgroundCompilation", Boolean.FALSE.toString()).//
                        option("engine.FirstTierCompilationThreshold", String.valueOf(COMPILATION_THRESHOLD / 10)).//
                        option("engine.LastTierCompilationThreshold", String.valueOf(COMPILATION_THRESHOLD)).//
                        err(err));
    }

    static class SimpleLoopNode extends Node implements RepeatingNode {
        private int loopCount = 0;
        static final int LOOP_LIMIT = 99;

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            return loopCount++ < LOOP_LIMIT;
        }
    }

    abstract static class NamedRootNode extends RootNode {
        private final String name;

        protected NamedRootNode(String name) {
            super(null);
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static class RootNodeWithParentFrameDescriptor extends NamedRootNode {

        private final FrameDescriptor parentFrameDescriptor;
        @Child LoopNode loopNode = GraalTruffleRuntime.getRuntime().createLoopNode(new SimpleLoopNode());

        protected RootNodeWithParentFrameDescriptor(FrameDescriptor parentFrameDescriptor, String name) {
            super(name);
            this.parentFrameDescriptor = parentFrameDescriptor;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            loopNode.execute(frame);
            return 42;
        }

        @Override
        public FrameDescriptor getParentFrameDescriptor() {
            return parentFrameDescriptor;
        }
    }

    abstract static class CallerRootNode extends NamedRootNode {

        private final String calleeName;
        @Child DirectCallNode callNode;

        protected CallerRootNode(String name, String calleeName) {
            super(name);
            this.calleeName = calleeName;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (callNode == null) {
                createCallNode(frame.getFrameDescriptor());
            }
            callNode.call();
            return 42;
        }

        @CompilerDirectives.TruffleBoundary
        private void createCallNode(FrameDescriptor frameDescriptor) {
            CallTarget callTarget = new RootNodeWithParentFrameDescriptor(getParentFrameDescriptor(frameDescriptor), calleeName).getCallTarget();
            callNode = insert(GraalTruffleRuntime.getRuntime().createDirectCallNode(callTarget));
        }

        protected abstract FrameDescriptor getParentFrameDescriptor(FrameDescriptor frameDescriptor);
    }

    static class OwnFrameDescriptorCallerRootNode extends CallerRootNode {

        protected OwnFrameDescriptorCallerRootNode(String name, String calleeName) {
            super(name, calleeName);
        }

        @Override
        protected FrameDescriptor getParentFrameDescriptor(FrameDescriptor frameDescriptor) {
            return frameDescriptor;
        }
    }

    static class NullFrameDescriptorCallerRootNode extends CallerRootNode {

        protected NullFrameDescriptorCallerRootNode(String name, String calleeName) {
            super(name, calleeName);
        }

        @Override
        protected FrameDescriptor getParentFrameDescriptor(FrameDescriptor frameDescriptor) {
            return null;
        }
    }
    @Test
    public void basicTest() {
        final String callerName = "Caller";
        final String calleeName = "Callee";
        compile(new OwnFrameDescriptorCallerRootNode(callerName, calleeName).getCallTarget());
        List<String> tierTwoCompilation = getTierTwoCompilationQueues();
        int callerIndex = getIndex(tierTwoCompilation, callerName);
        int calleeIndex = getIndex(tierTwoCompilation, calleeName);
        Assert.assertNotEquals("Caller not scheduled", -1, callerIndex);
        Assert.assertNotEquals("Callee not scheduled", -1, calleeIndex);
        Assert.assertTrue("Caller should be scheduled before callee", callerIndex < calleeIndex);
    }

    @Test
    public void basicNoReorderTest() {
        final String callerName = "Caller";
        final String calleeName = "Callee";
        compile(new NullFrameDescriptorCallerRootNode(callerName, calleeName).getCallTarget());
        List<String> tierTwoCompilation = getTierTwoCompilationQueues();
        int callerIndex = getIndex(tierTwoCompilation, callerName);
        int calleeIndex = getIndex(tierTwoCompilation, calleeName);
        Assert.assertNotEquals("Caller not scheduled", -1, callerIndex);
        Assert.assertNotEquals("Callee not scheduled", -1, calleeIndex);
        Assert.assertTrue("Callee should be scheduled before caller", callerIndex > calleeIndex);
    }

    private static int getIndex(List<String> tierTwoCompilation, String callerName) {
        int callerIndex = -1;
        for (int i = 0; i < tierTwoCompilation.size(); i++) {
            String s = tierTwoCompilation.get(i);
            if (s.contains(callerName)) {
                callerIndex = i;
            }
        }
        return callerIndex;
    }

    private static void compile(RootCallTarget callTarget) {
        for (int i = 0; i < COMPILATION_THRESHOLD; i++) {
            callTarget.call();
        }
    }

    private List<String> getTierTwoCompilationQueues() {
        return err.toString().lines().//
                        filter(s -> s.contains("Tier 2")).//
                        filter(s -> s.contains("opt queued")).//
                        collect(Collectors.toList());
    }
}
