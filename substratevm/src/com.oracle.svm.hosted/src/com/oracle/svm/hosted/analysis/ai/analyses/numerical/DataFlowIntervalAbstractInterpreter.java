package com.oracle.svm.hosted.analysis.ai.analyses.numerical;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbsMemory;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.vm.ci.meta.ResolvedJavaField;

import java.util.Objects;

public class DataFlowIntervalAbstractInterpreter implements AbstractInterpreter<AbsMemory> {

    private static final String NODE_PREFIX = "n";

    private static String nodeId(Node n) {
        return NODE_PREFIX + Integer.toHexString(System.identityHashCode(n));
    }

    @Override
    public void execEdge(Node source, Node target, AbstractState<AbsMemory> abstractState) {
        // simple flow: propagate post(source) into pre(target)
        abstractState.getPreCondition(target).joinWith(abstractState.getPostCondition(source));
    }

    @Override
    public void execNode(Node node, AbstractState<AbsMemory> abstractState, InvokeCallBack<AbsMemory> invokeCallBack) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Interpreting node: " + node, LoggerVerbosity.DEBUG);

        AbsMemory pre = abstractState.getPreCondition(node);
        AbsMemory post = pre.copyOf();

        try {
            if (node instanceof ConstantNode cn) {
                if (cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                    int v = cn.asJavaConstant().asInt();
                    IntInterval iv = new IntInterval(v, v);
                    bindNodeResult(node, iv, post);
                } else {
                    IntInterval top = new IntInterval();
                    top.setToTop();
                    bindNodeResult(node, top, post);
                }
            } else if (node instanceof BinaryArithmeticNode<?> bin) {
                Node x = bin.getX();
                Node y = bin.getY();
                execAndGet(x, abstractState, invokeCallBack);
                execAndGet(y, abstractState, invokeCallBack);
                IntInterval ix = getNodeResultInterval(x, abstractState);
                IntInterval iy = getNodeResultInterval(y, abstractState);
                IntInterval res;
                if (bin instanceof AddNode) res = ix.add(iy);
                else if (bin instanceof SubNode) res = ix.sub(iy);
                else if (bin instanceof MulNode) res = ix.mul(iy);
                else if (bin instanceof FloatDivNode) res = ix.div(iy);
                else {
                    res = new IntInterval();
                    res.setToTop();
                }
                bindNodeResult(node, res, post);
            } else if (node instanceof IntegerLessThanNode itn) {
                // Conservative: produce an interval representing possible booleans as 0/1
                execAndGet(itn.getX(), abstractState, invokeCallBack);
                execAndGet(itn.getY(), abstractState, invokeCallBack);
                IntInterval res = new IntInterval(0, 1);
                bindNodeResult(node, res, post);
            } else if (node instanceof AllocatedObjectNode alloc) {
                String aid = "alloc" + Integer.toHexString(System.identityHashCode(alloc));
                AccessPath root = AccessPath.forAllocSite(aid);
                // bind node temp to allocation site root
                post.bindTempByName(nodeId(node), root);
            } else if (node instanceof ParameterNode pn) {
                // bind parameter to a local param root
                String pname = "param" + pn.index();
                AccessPath root = AccessPath.forLocal(pname);
                post.bindParamByName(pname, root);
            } else if (node instanceof LoadFieldNode lfn) {
                // object() returns the base reference node, field() returns ResolvedJavaField
                Node obj = lfn.object();
                ResolvedJavaField field = lfn.field();
                AccessPath base = accessBaseForNode(obj, abstractState);
                AccessPath key = (base == null) ? AccessPath.forLocal("unknown") : base.appendField(field.getName());
                IntInterval val = post.readStore(key);
                bindNodeResult(node, val, post);
            } else if (node instanceof StoreFieldNode sfn) {
                Node obj = sfn.object();
                ResolvedJavaField field = sfn.field();
                execAndGet(sfn.value(), abstractState, invokeCallBack);
                IntInterval val = getNodeResultInterval(sfn.value(), abstractState);
                AccessPath base = accessBaseForNode(obj, abstractState);
                AccessPath key = (base == null) ? AccessPath.forLocal("unknown") : base.appendField(field.getName());
                // conservative weak update
                post.writeStore(key, val);
            } else if (node instanceof PhiNode phi) {
                IntInterval acc = new IntInterval();
                acc.setToBot();
                for (Node input : phi.inputs()) {
                    execAndGet(input, abstractState, invokeCallBack);
                    acc.joinWith(getNodeResultInterval(input, abstractState));
                }
                bindNodeResult(node, acc, post);
            } else if (node instanceof ReturnNode rn) {
                if (rn.result() != null) {
                    execAndGet(rn.result(), abstractState, invokeCallBack);
                    IntInterval v = getNodeResultInterval(rn.result(), abstractState);
                    post.bindLocalByName("ret", AccessPath.forLocal("ret"));
                    post.writeStore(AccessPath.forLocal("ret"), v);
                }
            } else if (node instanceof Invoke inv) {
                // conservative: try to get call summary via callback
                if (invokeCallBack != null) {
                    var outcome = invokeCallBack.handleInvoke(inv, node, abstractState);
                    if (outcome != null && outcome.isError()) {
                        post.setToTop();
                    } else if (outcome != null && outcome.summary() != null) {
                        // try to apply summary: domain-typed applySummary not universally available; skip for now
                        // conservative: set result to top
                        AccessPath.forPlaceholder("ret");
                        // if primitive return, bind top
                        IntInterval tt = new IntInterval(); tt.setToTop();
                        post.writeStore(AccessPath.forLocal(nodeId(node)), tt);
                    }
                } else {
                    // no callback: conservative
                    IntInterval t = new IntInterval(); t.setToTop();
                    bindNodeResult(node, t, post);
                }
            } else if (node instanceof IfNode) {
                // nothing to do here intraprocedurally; branch handling done in execEdge
            } else {
                // default: unknown node -> produce top
                IntInterval t = new IntInterval();
                t.setToTop();
                bindNodeResult(node, t, post);
            }
        } catch (Exception e) {
            logger.log("Interpreter error on node " + node + ": " + e, LoggerVerbosity.INFO);
            post.setToTop();
        }

        abstractState.setPostCondition(node, post);
        logger.log("Post condition for node " + node + " = " + post, LoggerVerbosity.DEBUG);
    }

    // Bind a node's result into the given AbsMemory as a temp
    private static void bindNodeResult(Node node, IntInterval val, AbsMemory mem) {
        String id = nodeId(node);
        AccessPath p = AccessPath.forLocal(id);
        mem.bindTempByName(id, p);
        mem.writeStore(p, val.copyOf());
    }

    // Recursively execute a node to ensure its postcondition is computed, then return domain for that node
    private AbstractState<AbsMemory> execAndGet(Node node, AbstractState<AbsMemory> abstractState, InvokeCallBack<AbsMemory> invokeCallBack) {
        execNode(node, abstractState, invokeCallBack);
        return abstractState;
    }

    // Read the interval associated with a node's temp
    private static IntInterval getNodeResultInterval(Node node, AbstractState<AbsMemory> abstractState) {
        AbsMemory callerPost = abstractState.getPostCondition(node);
        String id = nodeId(node);
        AccessPath p = callerPost.lookupTempByName(id);
        if (p == null) {
            IntInterval t = new IntInterval(); t.setToTop();
            return t;
        }
        return callerPost.readStore(p);
    }

    // Helper: compute an access-path base for an object node using the postcondition's env
    private static AccessPath accessBaseForNode(Node objNode, AbstractState<AbsMemory> abstractState) {
        if (objNode == null) return null;
        if (objNode instanceof ParameterNode pn) {
            return AccessPath.forLocal("param" + pn.index());
        }
        if (objNode instanceof AllocatedObjectNode alloc) {
            String aid = "alloc" + Integer.toHexString(System.identityHashCode(alloc));
            return AccessPath.forAllocSite(aid);
        }
        // fallback: check if the node has a temp AccessPath bound in its postcondition
        AbsMemory post = abstractState.getPostCondition(objNode);
        AccessPath p = post.lookupTempByName(nodeId(objNode));
        if (p != null) return p;
        return null;
    }
}
