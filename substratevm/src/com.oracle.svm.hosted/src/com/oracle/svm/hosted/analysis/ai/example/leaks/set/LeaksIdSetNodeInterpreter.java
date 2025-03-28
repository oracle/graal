package com.oracle.svm.hosted.analysis.ai.example.leaks.set;

import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.InvokeUtil;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;

public class LeaksIdSetNodeInterpreter implements NodeInterpreter<SetDomain<ResourceId>> {

    @Override
    public SetDomain<ResourceId> execEdge(Node source, Node destination, AbstractStateMap<SetDomain<ResourceId>> abstractStateMap) {
        abstractStateMap.getPreCondition(destination).joinWith(abstractStateMap.getPostCondition(source));
        return abstractStateMap.getPreCondition(destination);
    }

    @Override
    public SetDomain<ResourceId> execNode(Node node, AbstractStateMap<SetDomain<ResourceId>> abstractStateMap, InvokeCallBack<SetDomain<ResourceId>> invokeCallBack) {
        SetDomain<ResourceId> preCondition = abstractStateMap.getPreCondition(node);
        SetDomain<ResourceId> computedPost = preCondition.copyOf();

        System.out.println("Executing node : " + node);
        switch (node) {
            case Invoke invoke -> {
                if (InvokeUtil.opensResource(invoke)) {
                    computedPost.add(InvokeUtil.getInitResourceId(invoke));
                } else if (InvokeUtil.closesResource(invoke)) {
                    ResourceId id;
                    ValueNode receiver = invoke.getReceiver();
                    System.out.println(invoke.getReceiver());
                    /* if we have receiver, then we simply can get its creation position */
                    if (receiver instanceof AllocatedObjectNode allocatedObject) {
                        id = InvokeUtil.getAllocatedObjResourceId(allocatedObject);
                    } else {
                        /* this resource must have been created somewhere else -> check the domain of the input */
                        execNode(receiver, abstractStateMap, invokeCallBack);
                        SetDomain<ResourceId> state = abstractStateMap.getPostCondition(receiver);
                        id = state.getSet().iterator().next();
                    }
                    computedPost.remove(id);

                } else {
                    AnalysisOutcome<SetDomain<ResourceId>> outcome = invokeCallBack.handleCall(invoke, node, abstractStateMap);
                    if (outcome.isError()) {
                        throw new RuntimeException(outcome.toString());
                    }
                    Summary<SetDomain<ResourceId>> summary = outcome.summary();
                    computedPost = summary.applySummary(preCondition);
                }
            }

            case PiNode piNode -> {
                /* This is a dirty hack, but the logic is as follows:
                 * Essentially what we want to do in PiNode is to get the id of the resource that really is being passed
                 * To do this, we can abuse the fact that we will join with our predecessor, possibly getting new resources
                 * then performing difference with postCondition to have only the new resources in the new post condition.
                 * Also we need to reset the precondition, which is something that should not be generally done but hey, it works
                 */

                for (Node input : piNode.inputs()) {
                    abstractStateMap.getPreCondition(piNode).joinWith(abstractStateMap.getPostCondition(input));
                }
                
                SetDomain<ResourceId> piPre = abstractStateMap.getPreCondition(piNode);
                SetDomain<ResourceId> piPost = abstractStateMap.getPostCondition(piNode);
                for (ResourceId id : piPost.getSet()) {
                    if (piPre.getSet().contains(id)) {
                        continue;
                    }
                    computedPost.add(id);
                }
                piPre.clear();
            }

            case AllocatedObjectNode allocatedObject -> {
                ResourceId id = InvokeUtil.getAllocatedObjResourceId(allocatedObject);
                computedPost.add(id);
            }
            case ParameterNode parameterNode -> {
                int idx = parameterNode.index();
                for (ResourceId id : preCondition.getSet()) {
                    if (id.getId().startsWith("param" + idx)) {
                        computedPost.add(id);
                    }
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
            }

        }

        abstractStateMap.getState(node).setPostCondition(computedPost);
        return computedPost;
    }
}
