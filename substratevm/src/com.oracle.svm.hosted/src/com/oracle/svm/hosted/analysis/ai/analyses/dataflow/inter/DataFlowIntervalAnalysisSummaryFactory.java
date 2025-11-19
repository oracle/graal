package com.oracle.svm.hosted.analysis.ai.analyses.dataflow.inter;

import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

/**
 * Factory creating {@link DataFlowIntervalAnalysisSummary} instances from a call site context.
 * Maps actual argument intervals onto formal parameter slots (param0, param1, ...).
 */
public final class DataFlowIntervalAnalysisSummaryFactory implements SummaryFactory<AbstractMemory> {

    private static final String NODE_PREFIX = "n";

    private static String nodeId(Node n) {
        return NODE_PREFIX + Integer.toHexString(System.identityHashCode(n));
    }

    @Override
    public Summary<AbstractMemory> createSummary(Invoke invoke,
                                                 AbstractMemory callerPreCondition,
                                                 List<AbstractMemory> argumentMemories) {
        AbstractMemory pre = new AbstractMemory();
        if (invoke.callTarget() == null) {
            return new DataFlowIntervalAnalysisSummary(pre);
        }
        var argNodes = invoke.callTarget().arguments();
        int count = Math.min(argNodes.size(), argumentMemories.size());
        for (int i = 0; i < count; i++) {
            Node argNode = argNodes.get(i);
            AbstractMemory argMem = argumentMemories.get(i);
            String tempId = nodeId(argNode);
            IntInterval iv = argMem.readStore(AccessPath.forLocal(tempId));
            String paramName = "param" + i;
            AccessPath paramPath = AccessPath.forLocal(paramName);
            pre.bindParamByName(paramName, paramPath);
            pre.writeStoreStrong(paramPath, iv);
        }
        return new DataFlowIntervalAnalysisSummary(pre);
    }

    @Override
    public Summary<AbstractMemory> tryCreateEarlySummary(Invoke invoke, AbstractMemory callerPreCondition, List<AbstractMemory> argumentMemories) {
        if (invoke.callTarget() == null || argumentMemories.isEmpty()) return null;
        var argNodes = invoke.callTarget().arguments();
        if (argNodes.size() == 0) return null;

        Node firstArg = argNodes.get(0);
        AbstractMemory firstArgMem = argumentMemories.get(0);
        String nid = nodeId(firstArg);
        IntInterval iv = firstArgMem.readStore(AccessPath.forLocal(nid));
        if (!iv.isTop() && !iv.isBot() && !iv.isLowerInfinite() && iv.getUpper() < 2) {
            // Return the argument itself for base case
            AbstractMemory pre = new AbstractMemory();
            pre.bindParamByName("param0", AccessPath.forLocal("param0"));
            pre.writeStoreStrong(AccessPath.forLocal("param0"), iv);
            DataFlowIntervalAnalysisSummary summary = new DataFlowIntervalAnalysisSummary(pre);
            AbstractMemory post = new AbstractMemory();
            post.bindLocalByName("ret", AccessPath.forLocal("ret"));
            post.writeStoreStrong(AccessPath.forLocal("ret"), iv.copyOf());
            summary.setPostCondition(post);
            return summary;
        }
        return null;
    }
}
