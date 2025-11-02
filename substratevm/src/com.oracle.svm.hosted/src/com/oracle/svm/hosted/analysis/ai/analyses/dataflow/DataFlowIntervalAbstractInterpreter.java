package com.oracle.svm.hosted.analysis.ai.analyses.dataflow;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.calc.RemNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerDivNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.vm.ci.meta.ResolvedJavaField;

import java.util.HashSet;
import java.util.Set;

public class DataFlowIntervalAbstractInterpreter implements AbstractInterpreter<AbstractMemory> {
    private static final String NODE_PREFIX = "n";

    private static String nodeId(Node n) {
        return NODE_PREFIX + Integer.toHexString(System.identityHashCode(n));
    }

    @Override
    public void execEdge(Node source, Node target, AbstractState<AbstractMemory> abstractState, IteratorContext iteratorContext) {
        AbstractMemory post = abstractState.getPostCondition(source);

        if (source instanceof IfNode ifNode) {
            Node cond = ifNode.condition();
            if (cond instanceof IntegerLessThanNode itn) {
                if (target.equals(ifNode.trueSuccessor())) {
                    AbstractMemory narrowed = narrowOnLessThan(post, itn, true);
                    abstractState.getPreCondition(target).joinWith(narrowed);
                    return;
                } else if (target.equals(ifNode.falseSuccessor())) {
                    AbstractMemory narrowed = narrowOnLessThan(post, itn, false);
                    abstractState.getPreCondition(target).joinWith(narrowed);
                    return;
                }
            }
        }

        abstractState.getPreCondition(target).joinWith(post);
    }

    private AbstractMemory narrowOnLessThan(AbstractMemory base, IntegerLessThanNode itn, boolean isTrue) {
        AbstractMemory out = base.copyOf();
        Node x = itn.getX();
        Node y = itn.getY();
        IntInterval ix = getNodeResultInterval(x, out);
        IntInterval iy = getNodeResultInterval(y, out);
        if (ix == null || iy == null) return out;

        IntInterval newX = ix.copyOf();
        IntInterval newY = iy.copyOf();

        if (isTrue) {
            if (!iy.isUpperInfinite()) {
                long uy = iy.getUpper();
                newX.setUpper(Math.min(newX.getUpper(), uy - 1));
            }
            if (!ix.isLowerInfinite()) {
                long lx = ix.getLower();
                newY.setLower(Math.max(newY.getLower(), lx + 1));
            }
        } else {
            // !(x < y) => x >= y  => x.lower >= y.lower  and y.upper <= x.upper
            if (!iy.isLowerInfinite()) {
                long ly = iy.getLower();
                newX.setLower(Math.max(newX.getLower(), ly));
            }
            if (!ix.isUpperInfinite()) {
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
    public void execNode(Node node, AbstractState<AbstractMemory> abstractState, InvokeCallBack<AbstractMemory> invokeCallBack, IteratorContext iteratorContext) {
        AbstractMemory pre = abstractState.getPreCondition(node);
        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("Executing node: " + node + " with pre-condition: " + pre, LoggerVerbosity.INFO);

        AbstractMemory post = pre.copyOf();
        if (node instanceof StoreFieldNode sfn) {
            AbstractMemory afterVal = evalNode(sfn.value(), post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            IntInterval val = getNodeResultInterval(sfn.value(), afterVal);
            AccessPath base = resolveFieldBase(sfn.object(), sfn.field(), afterVal);
            AccessPath key = (base == null) ? AccessPath.forLocal("unknown") : base.appendField(sfn.field().getName());
            post = afterVal.copyOf();
            post.writeStore(key, val);
        } else if (node instanceof StoreIndexedNode sin) {
            AbstractMemory afterVal = evalNode(sin.value(), post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            IntInterval val = getNodeResultInterval(sin.value(), afterVal);

            Node arrayNode = sin.array();
            AccessPath arrayBase = accessBaseForNodeEval(arrayNode, afterVal);

            if (arrayBase != null) {
                AccessPath arrayPath = arrayBase.appendArrayWildcard();
                post = afterVal.copyOf();
                post.writeStore(arrayPath, val);
            } else {
                post = afterVal.copyOf();
            }
        } else if (node instanceof LoadFieldNode lfn) {
            Node obj = lfn.object();
            ResolvedJavaField field = lfn.field();
            AccessPath base = resolveFieldBase(obj, field, post);
            AccessPath key = (base == null) ? AccessPath.forLocal("unknown") : base.appendField(field.getName());
            IntInterval val = post.readStore(key);

            if (val.isTop() && field.isStatic() && field.getType().getJavaKind().isNumericInteger()) {
                val = new IntInterval(0, 0);
                logger.log("LoadField of uninitialized static field " + field.getName() +
                          ", using default value [0, 0]", LoggerVerbosity.DEBUG);
            }

            bindNodeResult(node, val, post);
        } else if (node instanceof LoadIndexedNode lin) {
            Node arrayNode = lin.array();
            AccessPath arrayBase = accessBaseForNodeEval(arrayNode, post);

            if (arrayBase != null) {
                AccessPath arrayPath = arrayBase.appendArrayWildcard();
                IntInterval val = post.readStore(arrayPath);
                bindNodeResult(node, val, post);
            } else {
                IntInterval top = new IntInterval();
                top.setToTop();
                bindNodeResult(node, top, post);
            }
        } else if (node instanceof ArrayLengthNode aln) {
            Node arr = aln.array();
            if (arr instanceof NewArrayNode newArr) {
                Node lenNode = newArr.length();
                if (lenNode instanceof ConstantNode cn && cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                    long v = cn.asJavaConstant().asLong();
                    IntInterval exact = new IntInterval(v, v);
                    bindNodeResult(node, exact, post);
                } else {
                    IntInterval len = new IntInterval(0, IntInterval.POS_INF);
                    bindNodeResult(node, len, post);
                }
            } else {
                IntInterval len = new IntInterval(0, IntInterval.POS_INF);
                bindNodeResult(node, len, post);
            }
        } else if (node instanceof NewInstanceNode nii) {
            String aid = "alloc" + Integer.toHexString(System.identityHashCode(nii));
            AccessPath root = AccessPath.forAllocSite(aid);
            post.bindTempByName(nodeId(nii), root);
        } else if (node instanceof NewArrayNode nan) {
            String aid = "alloc" + Integer.toHexString(System.identityHashCode(nan));
            AccessPath root = AccessPath.forAllocSite(aid);
            post.bindTempByName(nodeId(nan), root);
        } else if (node instanceof Invoke inv) {
            if (invokeCallBack != null) {
                var outcome = invokeCallBack.handleInvoke(inv, inv.asNode(), abstractState);
                if (outcome != null && outcome.isError()) {
                    post.setToTop();
                } else {
                    IntInterval top = new IntInterval();
                    top.setToTop();
                    bindNodeResult(inv.asNode(), top, post);
                }
            } else {
                IntInterval top = new IntInterval();
                top.setToTop();
                bindNodeResult(inv.asNode(), top, post);
            }
        } else if (node instanceof ReturnNode rn) {
            if (rn.result() != null) {
                AbstractMemory after = evalNode(rn.result(), post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
                IntInterval v = getNodeResultInterval(rn.result(), after);
                post = after.copyOf();
                post.bindLocalByName("ret", AccessPath.forLocal("ret"));
                post.writeStore(AccessPath.forLocal("ret"), v);
            }
        } else if (node instanceof AbstractMergeNode merge) {
            Set<Node> evalStack = new HashSet<>();
            for (Node usage : merge.usages()) {
                if (usage instanceof PhiNode phi) {
                    logger.log("Printing inputs of phiNode: " + phi, LoggerVerbosity.INFO);
                    for (Node input : phi.inputs()) {
                        logger.log("  Phi input: " + input, LoggerVerbosity.INFO);
                    }
                    post.joinWith(evalNode(phi, post, abstractState, invokeCallBack, evalStack, iteratorContext));
                }
            }
        }
        logger.log("Computed post-condition: " + post + " for node: " + node, LoggerVerbosity.INFO);
        abstractState.setPostCondition(node, post);
    }

    private AbstractMemory evalNode(Node node, AbstractMemory in, AbstractState<AbstractMemory> abstractState,
                                    InvokeCallBack<AbstractMemory> invokeCallBack, Set<Node> evalStack, IteratorContext iteratorContext) {
        if (node instanceof FixedNode || evalStack.contains(node)) {
            return in;
        }

        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("Evaluating node: " + node + " with input memory: " + in, LoggerVerbosity.INFO);
        evalStack.add(node);
        AbstractMemory mem = in.copyOf();

        if (!(node instanceof PhiNode)) {
            for (Node input : node.inputs()) {
                mem = evalNode(input, mem, abstractState, invokeCallBack, evalStack, iteratorContext);
            }
        }

        mem = evaluateNodeSemantics(node, mem, in, abstractState, invokeCallBack, evalStack, iteratorContext);
        evalStack.remove(node);
        return mem;
    }

    private AbstractMemory evaluateNodeSemantics(Node node, AbstractMemory mem, AbstractMemory originalIn,
                                                 AbstractState<AbstractMemory> abstractState,
                                                 InvokeCallBack<AbstractMemory> invokeCallBack,
                                                 Set<Node> evalStack, IteratorContext iteratorContext) {
        var logger = AbstractInterpretationLogger.getInstance();
        switch (node) {
            case ConstantNode constantNode -> {
                return evalConstantNode(constantNode, mem);
            }
            case ParameterNode parameterNode -> {
                return evalParameterNode(parameterNode, mem);
            }
            case PhiNode phiNode -> {
                return evalPhiNode(phiNode, mem, originalIn, abstractState, invokeCallBack, evalStack, iteratorContext);
            }
            case BinaryArithmeticNode<?> binaryArithmeticNode -> {
                return evalBinaryArithmeticNode(binaryArithmeticNode, mem);
            }
            case IntegerLessThanNode integerLessThanNode -> {
                return evalIntegerLessThanNode(integerLessThanNode, mem);
            }
            case ValueProxy valueProxy -> {
                Node original = valueProxy.getOriginalNode();
                IntInterval v = getNodeResultInterval(original, mem);
                bindNodeResult(node, v, mem);
                return mem;
            }
            default -> {
                logger.log("Unknown node type for evaluation: " + node.getClass().getSimpleName(), LoggerVerbosity.INFO);
                IntInterval top = new IntInterval();
                top.setToTop();
                bindNodeResult(node, top, mem);
                return mem;
            }
        }
    }

    private AbstractMemory evalConstantNode(ConstantNode cn, AbstractMemory mem) {
        var logger = AbstractInterpretationLogger.getInstance();
        if (cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
            long v = cn.asJavaConstant().asLong();
            IntInterval iv = new IntInterval(v, v);
            logger.log("  Constant node " + cn + " evaluated to interval: " + iv, LoggerVerbosity.INFO);
            bindNodeResult(cn, iv, mem);
        } else {
            IntInterval top = new IntInterval();
            top.setToTop();
            logger.log("  Constant node " + cn + " is non-integer, using top", LoggerVerbosity.INFO);
            bindNodeResult(cn, top, mem);
        }
        return mem;
    }

    private AbstractMemory evalParameterNode(ParameterNode pn, AbstractMemory mem) {
        String pname = "param" + pn.index();
        AccessPath root = AccessPath.forLocal(pname);
        mem.bindParamByName(pname, root);
        mem.bindTempByName(nodeId(pn), root);
        IntInterval top = new IntInterval();
        top.setToTop();
        mem.writeStore(root, top);
        return mem;
    }

    private AbstractMemory evalPhiNode(PhiNode phi, AbstractMemory mem, AbstractMemory originalIn,
                                       AbstractState<AbstractMemory> abstractState,
                                       InvokeCallBack<AbstractMemory> invokeCallBack,
                                       Set<Node> evalStack, IteratorContext iteratorContext) {
        var logger = AbstractInterpretationLogger.getInstance();

        AbstractMergeNode merge = phi.merge();
        HIRBlock phiBlock = (iteratorContext != null) ? iteratorContext.getBlockForNode(merge) : null;
        HIRBlock currentBlock = (iteratorContext != null) ? iteratorContext.getCurrentBlock() : null;

        boolean analyzingPhiBlock = (phiBlock != null && phiBlock.equals(currentBlock));

        if (!analyzingPhiBlock && phiBlock != null) {
            AccessPath existingPath = mem.lookupTempByName(nodeId(phi));
            if (existingPath != null) {
                IntInterval existingValue = mem.readStore(existingPath);
                logger.log("  PhiNode cached value: " + existingValue, LoggerVerbosity.DEBUG);
                return mem;
            }

            AbstractMemory mergePost = abstractState.getPostCondition(merge);
            AccessPath mergePath = mergePost.lookupTempByName(nodeId(phi));
            if (mergePath != null) {
                IntInterval mergeValue = mergePost.readStore(mergePath);
                bindNodeResult(phi, mergeValue, mem);
                logger.log("  PhiNode value from merge post-condition: " + mergeValue, LoggerVerbosity.DEBUG);
                return mem;
            }

            logger.log("  PhiNode not found in state, evaluating conservatively", LoggerVerbosity.DEBUG);
        }
        boolean isLoopHeader = (merge instanceof jdk.graal.compiler.nodes.LoopBeginNode);
        boolean isFirstVisit = false;

        if (isLoopHeader && iteratorContext != null) {
            isFirstVisit = iteratorContext.isFirstVisit(merge);
        }

        logger.log("Evaluating PhiNode: " + phi + " (loop header: " + isLoopHeader + ", first visit: " + isFirstVisit + ")", LoggerVerbosity.INFO);
        IntInterval acc = new IntInterval();
        acc.setToBot();

        int numInputs = phi.valueCount();
        for (int i = 0; i < numInputs; i++) {
            Node input = phi.valueAt(i);
            if (isLoopHeader && isFirstVisit && i == numInputs - 1) {
                logger.log("  Input[" + i + "]: " + input + " = SKIPPED (back-edge on first iteration)", LoggerVerbosity.DEBUG);
                continue;
            }

            IntInterval v;
            if (isLoopHeader && !isFirstVisit && i == numInputs - 1) {
                logger.log("    Evaluating back-edge with originalIn: " + originalIn, LoggerVerbosity.DEBUG);
                v = evalBackEdgeValue(input, originalIn);
                logger.log("  Input[" + i + "]: " + input + " = " + v + " (computed from previous iteration)", LoggerVerbosity.DEBUG);
            } else {
                v = getNodeResultInterval(input, originalIn);
                logger.log("  Input[" + i + "]: " + input + " = " + v, LoggerVerbosity.DEBUG);
            }

            acc.joinWith(v);
        }

        bindNodeResult(phi, acc, mem);
        logger.log("  PhiNode result: " + acc, LoggerVerbosity.INFO);
        return mem;
    }

    private IntInterval evalBackEdgeValue(Node node, AbstractMemory fromMem) {
        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("    evalBackEdgeValue for node: " + node, LoggerVerbosity.DEBUG);

        if (node instanceof jdk.graal.compiler.nodes.calc.BinaryArithmeticNode<?> bin) {
            Node x = bin.getX();
            Node y = bin.getY();

            logger.log("      X operand: " + x, LoggerVerbosity.DEBUG);
            logger.log("      Y operand: " + y, LoggerVerbosity.DEBUG);

            IntInterval ix = getNodeResultInterval(x, fromMem);
            IntInterval iy = getNodeResultInterval(y, fromMem);

            logger.log("      X interval: " + ix, LoggerVerbosity.DEBUG);
            logger.log("      Y interval: " + iy, LoggerVerbosity.DEBUG);

            if (bin instanceof AddNode) {
                IntInterval result = ix.add(iy);
                logger.log("      Add result: " + result, LoggerVerbosity.DEBUG);
                return result;
            } else if (bin instanceof SubNode) {
                return ix.sub(iy);
            } else if (bin instanceof MulNode) {
                return ix.mul(iy);
            } else if (bin instanceof SignedFloatingIntegerDivNode) {
                return ix.div(iy);
            } else if (bin instanceof RemNode) {
                return ix.rem(iy);
            } else if (bin instanceof FloatDivNode) {
                IntInterval top = new IntInterval();
                top.setToTop();
                return top;
            }
        }

        logger.log("      Returning top (unsupported node type)", LoggerVerbosity.DEBUG);
        IntInterval top = new IntInterval();
        top.setToTop();
        return top;
    }

    private AbstractMemory evalBinaryArithmeticNode(BinaryArithmeticNode<?> bin, AbstractMemory mem) {
        Node x = bin.getX();
        Node y = bin.getY();
        IntInterval ix = getNodeResultInterval(x, mem);
        IntInterval iy = getNodeResultInterval(y, mem);

        IntInterval res;
        if (bin instanceof AddNode) {
            res = ix.add(iy);
        } else if (bin instanceof SubNode) {
            res = ix.sub(iy);
        } else if (bin instanceof MulNode) {
            res = ix.mul(iy);
        } else if (bin instanceof SignedFloatingIntegerDivNode) {
            res = ix.div(iy);
        } else if (bin instanceof RemNode) {
            res = ix.rem(iy);
        } else if (bin instanceof FloatDivNode) {
            res = new IntInterval();
            res.setToTop();
        } else {
            res = new IntInterval();
            res.setToTop();
        }

        bindNodeResult(bin, res, mem);
        return mem;
    }

    private AbstractMemory evalIntegerLessThanNode(IntegerLessThanNode node, AbstractMemory mem) {
        IntInterval res = new IntInterval(0, 1);
        bindNodeResult(node, res, mem);
        return mem;
    }

    private static void bindNodeResult(Node node, IntInterval val, AbstractMemory mem) {
        String id = nodeId(node);
        AccessPath p = AccessPath.forLocal(id);
        mem.bindTempByName(id, p);
        mem.writeStore(p, val.copyOf());
    }

    private static IntInterval getNodeResultInterval(Node node, AbstractMemory mem) {
        String nid = nodeId(node);
        var logger = AbstractInterpretationLogger.getInstance();

        if (node instanceof ConstantNode cn) {
            if (cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                long v = cn.asJavaConstant().asLong();
                IntInterval result = new IntInterval(v, v);
                logger.log("        getNodeResultInterval: constant " + node + " = " + result, LoggerVerbosity.DEBUG);
                return result;
            }
        }

        AccessPath p = AccessPath.forLocal(nid);
        IntInterval result = mem.readStore(p);

        logger.log("        getNodeResultInterval: node " + node + " (id=" + nid + ") = " + result, LoggerVerbosity.DEBUG);
        return result;
    }

    private static AccessPath accessBaseForNodeEval(Node objNode, AbstractMemory mem) {
        switch (objNode) {
            case null -> {
                return null;
            }
            case ParameterNode pn -> {
                return AccessPath.forLocal("param" + pn.index());
            }
            case NewInstanceNode nii -> {
                String aid = "alloc" + Integer.toHexString(System.identityHashCode(nii));
                return AccessPath.forAllocSite(aid);
            }
            case NewArrayNode nan -> {
                String aid = "alloc" + Integer.toHexString(System.identityHashCode(nan));
                return AccessPath.forAllocSite(aid);
            }
            default -> {
            }
        }
        AccessPath p = mem.lookupTempByName(nodeId(objNode));
        if (p != null) return p;
        return null;
    }

    private static AccessPath resolveFieldBase(Node objNode, ResolvedJavaField field, AbstractMemory mem) {
        if (objNode == null) {
            String className = field.getDeclaringClass().getName();
            return AccessPath.forStaticClass(className);
        }
        return accessBaseForNodeEval(objNode, mem);
    }
}
