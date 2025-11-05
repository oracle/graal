package com.oracle.svm.hosted.analysis.ai.checker.checkers;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.core.CheckerResult;
import com.oracle.svm.hosted.analysis.ai.checker.core.CheckerStatus;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.IndexSafetyFact;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.NodeState;

/**
 * Produces IndexSafetyFact for array loads/stores proven to be within bounds.
 */
public final class IndexSafetyChecker implements Checker<AbstractMemory> {

    @Override
    public String getDescription() {
        return "Array index safety checker";
    }

    @Override
    public List<CheckerResult> check(AnalysisMethod method, AbstractState<AbstractMemory> abstractState) {
        List<CheckerResult> results = new ArrayList<>();
        var logger = AbstractInterpretationLogger.getInstance();
        for (Node n : abstractState.getStateMap().keySet()) {
            if (n instanceof LoadIndexedNode lin) {
                var mem = pickMem(abstractState, lin);
                if (mem == null) continue;
                IntInterval idx = intervalOf(lin.index(), mem);
                int len = constantArrayLenFromState(abstractState, lin.array());
                if (len < 0) len = deriveLengthFromGuard(abstractState, lin.index());
                if (isSafe(idx, len)) {
                    results.add(new CheckerResult(CheckerStatus.OK, "Load index in-bounds: " + n + " idx=" + idx + " len=" + len));
                } else {
                    logger.log("[IndexSafetyChecker] Not safe or unknown: " + n + ", idx=" + idx + ", len=" + len, LoggerVerbosity.CHECKER);
                }
            } else if (n instanceof StoreIndexedNode sin) {
                var mem = pickMem(abstractState, n);
                if (mem == null) continue;
                IntInterval idx = intervalOf(sin.index(), mem);
                int len = constantArrayLenFromState(abstractState, sin.array());
                if (len < 0) len = deriveLengthFromGuard(abstractState, sin.index());
                if (isSafe(idx, len)) {
                    results.add(new CheckerResult(CheckerStatus.OK, "Store index in-bounds: " + n + " idx=" + idx + " len=" + len));
                } else {
                    logger.log("[IndexSafetyChecker] Not safe or unknown: " + n + ", idx=" + idx + ", len=" + len, LoggerVerbosity.CHECKER);
                }
            }
        }
        return results;
    }

    @Override
    public List<Fact> produceFacts(AnalysisMethod method, AbstractState<AbstractMemory> abstractState) {
        List<Fact> facts = new ArrayList<>();
        var logger = AbstractInterpretationLogger.getInstance();
        for (Node n : abstractState.getStateMap().keySet()) {
            if (n instanceof LoadIndexedNode lin) {
                var mem = pickMem(abstractState, lin);
                if (mem == null) continue;
                IntInterval idx = intervalOf(lin.index(), mem);
                int len = constantArrayLenFromState(abstractState, lin.array());
                if (len < 0) len = deriveLengthFromGuard(abstractState, lin.index());
                if (isSafe(idx, len)) {
                    facts.add(new IndexSafetyFact(lin, true, idx, len));
                } else {
                    logger.log("[IndexSafetyChecker] No fact for load: idx=" + idx + ", len=" + len, LoggerVerbosity.CHECKER);
                }
            } else if (n instanceof StoreIndexedNode sin) {
                var mem = pickMem(abstractState, sin);
                if (mem == null) continue;
                IntInterval idx = intervalOf(sin.index(), mem);
                int len = constantArrayLenFromState(abstractState, sin.array());
                if (len < 0) len = deriveLengthFromGuard(abstractState, sin.index());
                if (isSafe(idx, len)) {
                    facts.add(new IndexSafetyFact(sin, true, idx, len));
                } else {
                    logger.log("[IndexSafetyChecker] No fact for store: idx=" + idx + ", len=" + len, LoggerVerbosity.CHECKER);
                }
            }
        }
        return facts;
    }

    @Override
    public boolean isCompatibleWith(AbstractState<?> abstractState) {
        return abstractState.getInitialDomain() instanceof AbstractMemory;
    }

    private static AbstractMemory pickMem(AbstractState<AbstractMemory> st, Node n) {
        var post = st.getPostCondition(n);
        if (post != null) return post;
        return st.getPreCondition(n);
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
                if (sameValue(aln.array(), arrayNode)) {
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

    private static int deriveLengthFromGuard(AbstractState<AbstractMemory> st, Node indexNode) {
        for (Node n : st.getStateMap().keySet()) {
            if (n instanceof IntegerLessThanNode itn) {
                Node x = itn.getX();
                Node y = itn.getY();
                if (sameValue(x, indexNode) && y instanceof ConstantNode cn && cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                    long v = cn.asJavaConstant().asLong();
                    if (v >= 0 && v <= Integer.MAX_VALUE) return (int) v;
                }
            }
        }
        return -1;
    }

    private static NewArrayNode resolveNewArray(Node n) {
        if (n == null) return null;
        if (n instanceof NewArrayNode na) return na;
        if (n instanceof ValueProxy vp) return resolveNewArray(vp.getOriginalNode());
        if (n instanceof PhiNode phi) {
            NewArrayNode candidate = null;
            for (int i = 0; i < phi.valueCount(); i++) {
                Node in = phi.valueAt(i);
                NewArrayNode r = resolveNewArray(in);
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

    private static boolean sameValue(Node a, Node b) {
        if (a == b) return true;
        if (a instanceof ValueProxy vp) return sameValue(vp.getOriginalNode(), b);
        if (b instanceof ValueProxy vp2) return sameValue(a, vp2.getOriginalNode());
        return false;
    }
}
