package com.oracle.svm.hosted.analysis.ai.example.leaks.set;

import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.InvokeUtil;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;

public class LeaksIdSetAbstractInterpreter implements AbstractInterpreter<SetDomain<ResourceId>> {

    @Override
    public void execEdge(Node source,
                         Node destination,
                         AbstractState<SetDomain<ResourceId>> abstractState) {
        abstractState.getPreCondition(destination).joinWith(abstractState.getPostCondition(source));
    }

    @Override
    public void execNode(Node node,
                         AbstractState<SetDomain<ResourceId>> abstractState,
                         InvokeCallBack<SetDomain<ResourceId>> invokeCallBack) {

        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("Analyzing node: " + node, LoggerVerbosity.DEBUG);
        SetDomain<ResourceId> preCondition = abstractState.getPreCondition(node).copyOf();
        SetDomain<ResourceId> computedPost = preCondition.copyOf();

        switch (node) {
            case Invoke invoke -> {
                if (InvokeUtil.opensResource(invoke)) {
                    computedPost.add(getInitResourceId(invoke));
                } else if (InvokeUtil.closesResource(invoke)) {
                    SetDomain<ResourceId> deletedIds = new SetDomain<>();
                    getResourceIdsFromReceiver(invoke.getReceiver(), deletedIds, abstractState);
                    for (ResourceId id : deletedIds.getSet()) {
                        computedPost.remove(id);
                    }
                } else {
                    AnalysisOutcome<SetDomain<ResourceId>> outcome = invokeCallBack.handleInvoke(invoke, node, abstractState);
                    if (outcome.isError()) {
                        throw new RuntimeException(outcome.toString());
                    }
                    Summary<SetDomain<ResourceId>> summary = outcome.summary();
                    computedPost = summary.applySummary(preCondition);
                }
            }

            case AllocatedObjectNode allocatedObject -> {
                ResourceId id = getAllocatedObjResourceId(allocatedObject);
                computedPost.add(id);
            }
            case ParameterNode parameterNode -> {
                int idx = parameterNode.index();
                ResourceId paramResourceId = getParameterResourceId(idx, abstractState);
                if (paramResourceId != null) {
                    computedPost.add(paramResourceId);
                }
            }

            case ReturnNode returnNode -> {
                if (returnNode.result() == null) {
                    break;
                }

                if (returnNode.result().getStackKind().isObject()) {
                    if (!(returnNode.result() instanceof AllocatedObjectNode allocatedObject)) {
                        break;
                    }

                    /* Find the returning resource and prefix it */
                    ResourceId objId = new ResourceId(allocatedObject.getVirtualObject().getNodeSourcePosition());
                    for (ResourceId id : preCondition.getSet()) {
                        if (id.getId().contains(objId.toString())) {
                            ResourceId returnId = id.addPrefix("return#");
                            computedPost.add(returnId);
                            break;
                        }
                    }
                }
            }

            default -> {
                /* Leave the abstract context as-is */
            }
        }
        logger.log("Computed post: " + computedPost, LoggerVerbosity.DEBUG);
        abstractState.getState(node).setPostCondition(computedPost);
    }

    private void getResourceIdsFromReceiver(Node receiver,
                                            SetDomain<ResourceId> preCondition,
                                            AbstractState<SetDomain<ResourceId>> abstractState) {
        if (receiver instanceof AllocatedObjectNode allocatedObject) {
            preCondition.add(getAllocatedObjResourceId(allocatedObject));
        } else if (receiver instanceof ParameterNode parameterNode) {
            preCondition.add(getParameterResourceId(parameterNode.index(), abstractState));
        } else if (receiver instanceof PiNode piNode) {
            for (Node input : piNode.inputs()) {
                getResourceIdsFromReceiver(input, preCondition, abstractState);
            }
        } else {
            preCondition.joinWith(abstractState.getPostCondition(receiver));
        }
    }

    private ResourceId getInitResourceId(Invoke invoke) {
        return new ResourceId(invoke.callTarget().getNodeSourcePosition());
    }

    private ResourceId getAllocatedObjResourceId(AllocatedObjectNode allocatedObjectNode) {
        return new ResourceId(allocatedObjectNode.getVirtualObject().getNodeSourcePosition());
    }

    private ResourceId getParameterResourceId(int idx, AbstractState<SetDomain<ResourceId>> abstractState) {
        String resultId = "";
        var startPre = abstractState.getStartNodeState().getPreCondition();
        for (ResourceId id : startPre.getSet()) {
            if (id.getId().startsWith("param" + idx)) {
                resultId = id.toString();
                break;
            }
        }

        return new ResourceId(resultId);
    }
}
