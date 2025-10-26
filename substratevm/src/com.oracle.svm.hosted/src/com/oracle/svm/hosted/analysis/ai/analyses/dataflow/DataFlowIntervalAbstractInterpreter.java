package com.oracle.svm.hosted.analysis.ai.analyses.dataflow;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbsMemory;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
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

import java.util.HashSet;
import java.util.Set;

public class DataFlowIntervalAbstractInterpreter implements AbstractInterpreter<AbsMemory> {

    private static final String NODE_PREFIX = "n";

    private static String nodeId(Node n) {
        return NODE_PREFIX + Integer.toHexString(System.identityHashCode(n));
    }

    @Override
    public void execEdge(Node source, Node target, AbstractState<AbsMemory> abstractState) {
        // If this is a branch node, try to narrow intervals along true/false edges
        if (source instanceof IfNode ifNode) {
            AbsMemory post = abstractState.getPostCondition(ifNode);
            Node cond = null;
            for (Node in : ifNode.inputs()) {
                if (in instanceof IntegerLessThanNode) {
                    cond = in;
                    break;
                }
                if (cond == null) cond = in;
            }

            if (cond instanceof IntegerLessThanNode itn) {
                if (target.equals(ifNode.trueSuccessor())) {
                    AbsMemory narrowed = narrowOnLessThan(post, itn, true);
                    abstractState.getPreCondition(target).joinWith(narrowed);
                    return;
                } else if (target.equals(ifNode.falseSuccessor())) {
                    AbsMemory narrowed = narrowOnLessThan(post, itn, false);
                    abstractState.getPreCondition(target).joinWith(narrowed);
                    return;
                }
            }

            abstractState.getPreCondition(target).joinWith(post);
            return;
        }

        abstractState.getPreCondition(target).joinWith(abstractState.getPostCondition(source));
    }

    // Narrow intervals for a condition `x < y`. If isTrue==true we apply the true-branch narrowing, otherwise the false-branch.
    private AbsMemory narrowOnLessThan(AbsMemory base, IntegerLessThanNode itn, boolean isTrue) {
        AbsMemory out = base.copyOf();
        Node x = itn.getX();
        Node y = itn.getY();
        IntInterval ix = getNodeResultInterval(x, out);
        IntInterval iy = getNodeResultInterval(y, out);
        if (ix == null || iy == null) return out;

        IntInterval newX = ix.copyOf();
        IntInterval newY = iy.copyOf();

        if (isTrue) {
            if (!Double.isInfinite(iy.getUpper())) {
                long uy = iy.getUpper();
                newX.setUpper(Math.min(newX.getUpper(), uy - 1));
            }
            if (!Double.isInfinite(ix.getLower())) {
                long lx = ix.getLower();
                newY.setLower(Math.max(newY.getLower(), lx + 1));
            }
        } else {
            // !(x < y) => x >= y  => x.lower >= y.lower  and y.upper <= x.upper
            if (!Double.isInfinite(iy.getLower())) {
                long ly = iy.getLower();
                newX.setLower(Math.max(newX.getLower(), ly));
            }
            if (!Double.isInfinite(ix.getUpper())) {
                long ux = ix.getUpper();
                newY.setUpper(Math.min(newY.getUpper(), ux));
            }
        }
        AccessPath px = out.lookupTempByName(nodeId(x));
        if (px != null) out.writeStore(px, newX);
        AccessPath py = out.lookupTempByName(nodeId(y));
        if (py != null) out.writeStore(py, newY);
        return out;
    }

    @Override
    public void execNode(Node node, AbstractState<AbsMemory> abstractState, InvokeCallBack<AbsMemory> invokeCallBack) {
        AbsMemory pre = abstractState.getPreCondition(node);

        /* Evaluate dataflow inputs */
        AbsMemory threaded = pre.copyOf();
        Set<Node> evalStack = new HashSet<>();
        for (Node input : node.inputs()) {
            threaded = evalNode(input, threaded, abstractState, invokeCallBack, evalStack);
        }

        // start from the threaded memory
        AbsMemory post = threaded.copyOf();

        /* Handle control-level nodes that have side-effects (stores, returns, etc.) here */
        if (node instanceof StoreFieldNode sfn) {
            // Evaluate the RHS value under the threaded memory so any side-effects are visible
            AbsMemory afterVal = evalNode(sfn.value(), threaded, abstractState, invokeCallBack, new HashSet<>());
            IntInterval val = getNodeResultInterval(sfn.value(), afterVal);
            // resolve base from the afterVal memory
            AccessPath base = resolveFieldBase(sfn.object(), sfn.field(), afterVal);
            AccessPath key = (base == null) ? AccessPath.forLocal("unknown") : base.appendField(sfn.field().getName());
            post = afterVal.copyOf();
            post.writeStore(key, val);
        } else if (node instanceof ReturnNode rn) {
            if (rn.result() != null) {
                AbsMemory after = evalNode(rn.result(), post, abstractState, invokeCallBack, new HashSet<>());
                IntInterval v = getNodeResultInterval(rn.result(), after);
                post = after.copyOf();
                post.bindLocalByName("ret", AccessPath.forLocal("ret"));
                post.writeStore(AccessPath.forLocal("ret"), v);
            }
        }

        abstractState.setPostCondition(node, post);
    }

    /**
     * Evaluate a dataflow node (expression) starting from a given abstract memory 'in'.
     * This does NOT call execNode (CFG), it only evaluates expression semantics and threads memory.
     * It is recursive over dataflow inputs and guards against cycles using evalStack.
     */
    private AbsMemory evalNode(Node node, AbsMemory in, AbstractState<AbsMemory> abstractState, InvokeCallBack<AbsMemory> invokeCallBack, Set<Node> evalStack) {
        // If node is a CFG-only node, do not evaluate here; return input memory unchanged
        if (node instanceof IfNode || node instanceof ReturnNode) {
            return in;
        }

        if (evalStack.contains(node)) {
            return in;
        }
        evalStack.add(node);

        AbsMemory mem = in.copyOf();

        for (Node input : node.inputs()) {
            mem = evalNode(input, mem, abstractState, invokeCallBack, evalStack);
        }

        if (node instanceof ConstantNode cn) {
            if (cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                int v = cn.asJavaConstant().asInt();
                IntInterval iv = new IntInterval(v, v);
                bindNodeResult(node, iv, mem);
            } else {
                IntInterval top = new IntInterval();
                top.setToTop();
                bindNodeResult(node, top, mem);
            }
        } else if (node instanceof BinaryArithmeticNode<?> bin) {
            Node x = bin.getX();
            Node y = bin.getY();
            IntInterval ix = getNodeResultInterval(x, mem);
            IntInterval iy = getNodeResultInterval(y, mem);
            IntInterval res;
            if (bin instanceof AddNode) res = ix.add(iy);
            else if (bin instanceof SubNode) res = ix.sub(iy);
            else if (bin instanceof MulNode) res = ix.mul(iy);
            else if (bin instanceof FloatDivNode) res = ix.div(iy);
            else {
                res = new IntInterval();
                res.setToTop();
            }
            bindNodeResult(node, res, mem);
        } else if (node instanceof IntegerLessThanNode) {
            IntInterval res = new IntInterval(0, 1);
            bindNodeResult(node, res, mem);
            // do NOT perform edge-narrowing here; narrowing happens in execEdge when propagating to successors
        } else if (node instanceof AllocatedObjectNode alloc) {
            String aid = "alloc" + Integer.toHexString(System.identityHashCode(alloc));
            AccessPath root = AccessPath.forAllocSite(aid);
            mem.bindTempByName(nodeId(node), root);
        } else if (node instanceof ParameterNode pn) {
            String pname = "param" + pn.index();
            AccessPath root = AccessPath.forLocal(pname);
            mem.bindParamByName(pname, root);
        } else if (node instanceof LoadFieldNode lfn) {
            Node obj = lfn.object();
            ResolvedJavaField field = lfn.field();
            AccessPath base = resolveFieldBase(obj, field, mem);
            AccessPath key = (base == null) ? AccessPath.forLocal("unknown") : base.appendField(field.getName());
            IntInterval val = mem.readStore(key);
            bindNodeResult(node, val, mem);
        } else if (node instanceof PhiNode phi) {
            // Phi semantics: join of incoming values. We conservatively evaluate each operand in the SAME incoming memory
            IntInterval acc = new IntInterval();
            acc.setToBot();
            for (Node input : phi.inputs()) {
                AbsMemory tmp = evalNode(input, in.copyOf(), abstractState, invokeCallBack, evalStack);
                IntInterval v = getNodeResultInterval(input, tmp);
                acc.joinWith(v);
            }
            bindNodeResult(node, acc, mem);
        } else if (node instanceof Invoke inv) {
            if (invokeCallBack != null) {
                var outcome = invokeCallBack.handleInvoke(inv, node, abstractState);
                if (outcome != null && outcome.isError()) {
                    mem.setToTop();
                } else if (outcome != null && outcome.summary() != null) {
                    IntInterval tt = new IntInterval();
                    tt.setToTop();
                    bindNodeResult(node, tt, mem);
                } else {
                    IntInterval tt = new IntInterval();
                    tt.setToTop();
                    bindNodeResult(node, tt, mem);
                }
            } else {
                IntInterval tt = new IntInterval();
                tt.setToTop();
                bindNodeResult(node, tt, mem);
            }
        } else {
            IntInterval t = new IntInterval();
            t.setToTop();
            bindNodeResult(node, t, mem);
        }

        evalStack.remove(node);
        return mem;
    }

    private static void bindNodeResult(Node node, IntInterval val, AbsMemory mem) {
        String id = nodeId(node);
        AccessPath p = AccessPath.forLocal(id);
        mem.bindTempByName(id, p);
        mem.writeStore(p, val.copyOf());
    }

    /* Read the interval associated with a node's temp in a local memory */
    private static IntInterval getNodeResultInterval(Node node, AbsMemory mem) {
        AccessPath p = mem.lookupTempByName(nodeId(node));
        if (p == null) {
            IntInterval t = new IntInterval();
            t.setToTop();
            return t;
        }
        return mem.readStore(p);
    }

    private static AccessPath accessBaseForNodeEval(Node objNode, AbsMemory mem) {
        switch (objNode) {
            case null -> {
                return null;
            }
            case ParameterNode pn -> {
                return AccessPath.forLocal("param" + pn.index());
            }
            case AllocatedObjectNode alloc -> {
                String aid = "alloc" + Integer.toHexString(System.identityHashCode(alloc));
                return AccessPath.forAllocSite(aid);
            }
            default -> {
            }
        }
        AccessPath p = mem.lookupTempByName(nodeId(objNode));
        if (p != null) return p;
        return null;
    }

    private static AccessPath resolveFieldBase(Node objNode, ResolvedJavaField field, AbsMemory mem) {
        if (objNode == null) {
            String className = field.getDeclaringClass().getName();
            return AccessPath.forStaticClass(className);
        }
        return accessBaseForNodeEval(objNode, mem);
    }
}
