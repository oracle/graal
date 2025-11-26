package com.oracle.svm.hosted.analysis.ai.checker.checkers;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.core.NodeUtil;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.SafeBoundsAccessFact;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;

import com.oracle.svm.hosted.analysis.ai.util.AnalysisServices;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.java.AccessIndexedNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.spi.ArrayLengthProvider;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import com.oracle.svm.hosted.analysis.ai.fixpoint.state.NodeState;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;

/**
 * Produces facts for array loads/stores proven to be always within bounds.
 */
public final class BoundsSafetyChecker implements Checker<AbstractMemory> {

    private static final int MAX_TRACE_DEPTH = 64;

    @Override
    public String getDescription() {
        return "Array bounds safety checker";
    }

    @Override
    public List<Fact> produceFacts(AnalysisMethod method, AbstractState<AbstractMemory> abstractState) {
        List<Fact> facts = new ArrayList<>();
        for (Node n : abstractState.getStateMap().keySet()) {
            if (n instanceof AccessIndexedNode ain) {

                IfNode guardingIf = NodeUtil.findGuardingIf(ain);
                if (guardingIf == null) {
                    continue;
                }

                if (!NodeUtil.leadsToByteCodeException(guardingIf)) {
                    continue;
                }

                var mem = pickMem(abstractState, ain);
                if (mem == null) continue;
                IntInterval idx = intervalOf(ain.index(), mem);
                int len = constantArrayLenFromState(abstractState, ain.array());
                if (len < 0) {
                    len = deriveLengthFromGuard(abstractState, ain.getBoundsCheck());
                }

                if (isSafe(idx, len)) {
                    facts.add(new SafeBoundsAccessFact(ain, true, idx, len));
                }
            }
        }
        return facts;
    }

    @Override
    public boolean isCompatibleWith(AbstractState<?> abstractState) {
        return abstractState.getInitialDomain() instanceof AbstractMemory;
    }

    private static AbstractMemory pickMem(AbstractState<AbstractMemory> state, Node node) {
        var post = state.getPostCondition(node);
        if (post != null) return post;
        return state.getPreCondition(node);
    }

    private static boolean isSafe(IntInterval idx, int len) {
        if (idx == null || idx.isTop() || idx.isBot() || idx.isLowerInfinite() || idx.isUpperInfinite()) return false;
        if (len < 0) return false;
        long lo = idx.getLower();
        long hi = idx.getUpper();
        return lo >= 0 && hi <= (long) len - 1;
    }

    private static IntInterval intervalOf(Node n, AbstractMemory mem) {
        if (n == null) return null;
        if (n instanceof ConstantNode cn && cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
            long v = cn.asJavaConstant().asLong();
            return new IntInterval(v, v);
        }

        if (n instanceof ParameterNode pn) {
            String id = "param" + pn.index();
            return mem.readStore(AccessPath.forLocal(id));
        }

        String id = "n" + Integer.toHexString(System.identityHashCode(n));
        return mem.readStore(AccessPath.forLocal(id));
    }

    private static int constantArrayLenFromState(AbstractState<AbstractMemory> st, Node arrayNode) {
        NewArrayNode newArr = resolveNewArray(arrayNode);
        if (newArr != null) {
            Node lenNode = newArr.length();
            if (lenNode instanceof ConstantNode cn && cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                long v = cn.asJavaConstant().asLong();
                if (v >= 0 && v <= Integer.MAX_VALUE) return (int) v;
            }
        }
        // Search for ArrayLengthNode in the analyzed nodes that refers to the same array value
        for (Map.Entry<Node, NodeState<AbstractMemory>> e : st.getStateMap().entrySet()) {
            Node n = e.getKey();
            if (n instanceof ArrayLengthNode aln) {
                if (sameNode(aln.array(), arrayNode)) {
                    AbstractMemory mem = pickMem(st, aln);
                    IntInterval iv = intervalOf(aln, mem);
                    if (iv != null && !iv.isTop() && !iv.isBot() && !iv.isLowerInfinite() && !iv.isUpperInfinite() && iv.getLower() == iv.getUpper()) {
                        long v = iv.getLower();
                        if (v >= 0 && v <= Integer.MAX_VALUE) return (int) v;
                    }
                }
            }
        }
        return -1;
    }

    private static int deriveLengthFromGuard(AbstractState<AbstractMemory> st, GuardingNode guardingNode) {
        if (guardingNode == null) return -1;

        Node node = guardingNode.asNode();
        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("Guarding node: " + node, LoggerVerbosity.CHECKER);

        IfNode ifNode = NodeUtil.findGuardingIf(node);
        logger.log("Deriving length from guard: ", LoggerVerbosity.CHECKER);
        logger.log("Guarding ifNode: " + ifNode, LoggerVerbosity.CHECKER);
        if (ifNode == null) return -1;

        Node condition = ifNode.condition();
        if (condition instanceof CompareNode compareNode) {
            logger.log("It is an compareNode", LoggerVerbosity.CHECKER);
            Node y = compareNode.getY();
            logger.log("The y is:" + y, LoggerVerbosity.CHECKER);
            if (y instanceof ConstantNode cn && cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                long v = cn.asJavaConstant().asLong();
                if (v >= 0 && v <= Integer.MAX_VALUE) return (int) v;
            }

            // FIXME: refactor this
            if (y instanceof ArrayLengthNode aln) {
                return getLengthFromArray(st, aln.array());
            }

        }

        return -1;
    }

    private static int getLengthFromArray(AbstractState<AbstractMemory> st, ValueNode array) {

        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Get Length from array: " + array, LoggerVerbosity.CHECKER);
        if (array == null) return -1;

        if (array instanceof VirtualArrayNode vArr) {
            logger.log("Virtual Array Node", LoggerVerbosity.CHECKER);
            Node node = vArr.findLength(ArrayLengthProvider.FindLengthMode.SEARCH_ONLY, AnalysisServices.getInstance().getInflation().getConstantReflectionProvider());
            if (node instanceof ConstantNode cn && cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                long v = cn.asJavaConstant().asLong();
                if (v >= 0 && v <= Integer.MAX_VALUE) return (int) v;
            }
            return -1;
        }

        if (array instanceof PiNode piNode) {
            logger.log("Pi Node", LoggerVerbosity.CHECKER);
            return getLengthFromArray(st, piNode.object());
        }

        if (array instanceof  ParameterNode pn) {
            // now the parameter has the length of the array
            logger.log("Parameter Node: " + pn, LoggerVerbosity.CHECKER);
            String id = "param" + pn.index();
            IntInterval value = st.getStartNodeState().getPreCondition().readStore(AccessPath.forLocal(id));
            if (value.isSingleton() && value.getLower() <= Integer.MAX_VALUE && value.getLower() >= Integer.MIN_VALUE) {
                return (int) value.getLower();
            }
        }
        return -1;
    }

    private static NewArrayNode resolveNewArray(Node n) {
        return resolveNewArray(n, new HashSet<>(), 0);
    }

    private static NewArrayNode resolveNewArray(Node n, Set<Node> visited, int depth) {
        if (n == null) return null;
        if (depth > MAX_TRACE_DEPTH) {
            AbstractInterpretationLogger.getInstance().log("resolveNewArray: max depth exceeded at " + n, LoggerVerbosity.DEBUG);
            return null;
        }
        if (!visited.add(n)) {
            // cycle detected
            return null;
        }
        if (n instanceof NewArrayNode na) return na;
        if (n instanceof ValueProxy vp) return resolveNewArray(vp.getOriginalNode(), visited, depth + 1);
        if (n instanceof PhiNode phi) {
            NewArrayNode candidate = null;
            for (int i = 0; i < phi.valueCount(); i++) {
                Node in = phi.valueAt(i);
                NewArrayNode r = resolveNewArray(in, visited, depth + 1);
                if (r == null) return null;
                if (candidate == null) {
                    candidate = r;
                } else if (candidate != r) {
                    return null;
                }
            }
            return candidate;
        }
        return null;
    }

    private static boolean sameNode(Node a, Node b) {
        if (a == b) return true;
        if (a instanceof ValueProxy vp) return sameNode(vp.getOriginalNode(), b);
        if (b instanceof ValueProxy vp2) return sameNode(a, vp2.getOriginalNode());
        return false;
    }
}
