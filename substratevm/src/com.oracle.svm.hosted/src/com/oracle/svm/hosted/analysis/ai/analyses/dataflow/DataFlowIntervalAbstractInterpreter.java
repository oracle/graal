package com.oracle.svm.hosted.analysis.ai.analyses.dataflow;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AliasSet;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.RemNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerDivNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.vm.ci.meta.ResolvedJavaField;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class DataFlowIntervalAbstractInterpreter implements AbstractInterpreter<AbstractMemory> {

    private static final String NODE_PREFIX = "n";
    private static final int MAX_INDEX_EXPANSION = 16;
    private static final int MAX_TRACE_DEPTH = 64;

    private static String nodeId(Node n) {
        return NODE_PREFIX + Integer.toHexString(System.identityHashCode(n));
    }

    @Override
    public void execEdge(Node source, Node target, AbstractState<AbstractMemory> abstractState, IteratorContext iteratorContext) {
        AbstractMemory post = abstractState.getPostCondition(source);
        if (target instanceof LoopExitNode exit) {
            LoopBeginNode lb = exit.loopBegin();
            AbstractMemory acc = post.copyOf();
            for (LoopEndNode le : lb.loopEnds()) {
                AbstractMemory lePost = abstractState.getPostCondition(le);
                if (lePost != null) {
                    acc.joinWith(lePost);
                }
            }
            abstractState.getPreCondition(target).joinWith(acc);
            return;
        }

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
            } else if (cond instanceof IntegerEqualsNode eq) {
                if (target.equals(ifNode.trueSuccessor())) {
                    AbstractMemory narrowed = narrowOnEquals(post, eq, true);
                    abstractState.getPreCondition(target).joinWith(narrowed);
                    return;
                } else if (target.equals(ifNode.falseSuccessor())) {
                    AbstractMemory narrowed = narrowOnEquals(post, eq, false);
                    abstractState.getPreCondition(target).joinWith(narrowed);
                    return;
                }
            } else if (cond instanceof IntegerBelowNode ib) {
                if (target.equals(ifNode.trueSuccessor())) {
                    AbstractMemory narrowed = narrowOnBelow(post, ib, true);
                    abstractState.getPreCondition(target).joinWith(narrowed);
                    return;
                } else if (target.equals(ifNode.falseSuccessor())) {
                    AbstractMemory narrowed = narrowOnBelow(post, ib, false);
                    abstractState.getPreCondition(target).joinWith(narrowed);
                    return;
                }
            } else if (cond instanceof IsNullNode isNull) {
                AbstractMemory narrowed = post.copyOf();
                IntInterval bool = target.equals(ifNode.trueSuccessor()) ? new IntInterval(1, 1) : new IntInterval(0, 0);
                bindNodeResult(isNull, bool, narrowed);
                abstractState.getPreCondition(target).joinWith(narrowed);
                return;
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

    private AbstractMemory narrowOnEquals(AbstractMemory base, IntegerEqualsNode eq, boolean isTrue) {
        AbstractMemory out = base.copyOf();
        Node x = eq.getX();
        Node y = eq.getY();
        IntInterval ix = getNodeResultInterval(x, out);
        IntInterval iy = getNodeResultInterval(y, out);
        if (ix == null || iy == null) return out;

        if (isTrue) {
            IntInterval nx = ix.copyOf();
            nx.meetWith(iy);
            IntInterval ny = iy.copyOf();
            ny.meetWith(ix);
            AccessPath px = out.lookupTempByName(nodeId(x));
            if (px != null) out.writeStore(px, nx);
            AccessPath py = out.lookupTempByName(nodeId(y));
            if (py != null) out.writeStore(py, ny);
        } else {
            if (isPoint(ix)) {
                IntInterval ny = iy.copyOf();
                AccessPath py = out.lookupTempByName(nodeId(y));
                if (py != null) out.writeStore(py, ny);
            }
            if (isPoint(iy)) {
                IntInterval nx = ix.copyOf();
                AccessPath px = out.lookupTempByName(nodeId(x));
                if (px != null) out.writeStore(px, nx);
            }
        }
        return out;
    }

    private static boolean isNonNegative(IntInterval v) {
        return v != null && !v.isBot() && !v.isTop() && !v.isLowerInfinite() && v.getLower() >= 0;
    }

    private AbstractMemory narrowOnBelow(AbstractMemory base, IntegerBelowNode ib, boolean isTrue) {
        AbstractMemory out = base.copyOf();
        Node x = ib.getX();
        Node y = ib.getY();
        IntInterval ix = getNodeResultInterval(x, out);
        IntInterval iy = getNodeResultInterval(y, out);
        if (ix == null || iy == null) return out;
        if (!isNonNegative(ix) || !isNonNegative(iy)) return out;

        IntInterval nx = ix.copyOf();
        IntInterval ny = iy.copyOf();
        if (isTrue) {
            if (!iy.isUpperInfinite()) {
                nx.setUpper(Math.min(nx.getUpper(), iy.getUpper() - 1));
            }
            if (!ix.isLowerInfinite()) {
                ny.setLower(Math.max(ny.getLower(), ix.getLower() + 1));
            }
        } else {
            if (!iy.isLowerInfinite()) {
                nx.setLower(Math.max(nx.getLower(), iy.getLower()));
            }
            if (!ix.isUpperInfinite()) {
                ny.setUpper(Math.min(ny.getUpper(), ix.getUpper()));
            }
        }
        AccessPath px = out.lookupTempByName(nodeId(x));
        if (px != null) out.writeStore(px, nx);
        AccessPath py = out.lookupTempByName(nodeId(y));
        if (py != null) out.writeStore(py, ny);
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
            AliasSet bases = resolveFieldBaseSet(sfn.object(), sfn.field(), afterVal);
            post = afterVal.copyOf();
            post.writeTo(bases, p -> p.appendField(sfn.field().getName()), val);
        } else if (node instanceof StoreIndexedNode sin) {
            AbstractMemory afterArr = evalNode(sin.array(), post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            AbstractMemory afterIdx = evalNode(sin.index(), afterArr, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            AbstractMemory afterVal = evalNode(sin.value(), afterIdx, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            IntInterval val = getNodeResultInterval(sin.value(), afterVal);
            AliasSet bases = accessBaseSetForNodeEval(sin.array(), afterVal);
            Function<AccessPath, AccessPath> idxTransform = indexTransform(sin.index(), afterVal);
            post = afterVal.copyOf();
            post.writeTo(bases, idxTransform, val);

            IntInterval idxIv = getNodeResultInterval(sin.index(), afterVal);
            boolean finiteBounds = idxIv != null && !idxIv.isTop() && !idxIv.isBot() && !idxIv.isLowerInfinite() && !idxIv.isUpperInfinite();
            if (finiteBounds) {
                long lo = Math.max(0, idxIv.getLower());
                long hi = idxIv.getUpper();
                long span = hi - lo;
                if (span >= 0 && span <= MAX_INDEX_EXPANSION && hi <= Integer.MAX_VALUE) {
                    for (long k = lo; k <= hi; k++) {
                        AbstractMemory memK = afterIdx.copyOf();
                        String idxId = nodeId(sin.index());
                        AccessPath ip = AccessPath.forLocal(idxId);
                        memK.bindTempByName(idxId, ip);
                        memK.writeStoreStrong(ip, new IntInterval(k, k));
                        memK = evalNode(sin.value(), memK, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
                        IntInterval vk = getNodeResultInterval(sin.value(), memK);
                        long finalK = k;
                        post.writeTo(bases, p -> p.appendArrayIndex((int) finalK), vk);
                    }
                }
            }
        } else if (node instanceof LoadFieldNode lfn) {
            ResolvedJavaField field = lfn.field();
            if (lfn.object() == null || field.isStatic()) {
                // Static field access: no object to evaluate
                AliasSet bases = resolveFieldBaseSet(null, field, post);
                IntInterval val = post.readFrom(bases, p -> p.appendField(field.getName()));
                if (val.isTop() && field.getType().getJavaKind().isNumericInteger()) {
                    val = new IntInterval(0, 0);
                    logger.log("LoadField of uninitialized static field " + field.getName() +
                            ", using default value [0, 0]", LoggerVerbosity.DEBUG);
                }
                bindNodeResult(node, val, post);
            } else {
                // Instance field: ensure base evaluated
                AbstractMemory afterObj = evalNode(lfn.object(), post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
                AliasSet bases = resolveFieldBaseSet(lfn.object(), field, afterObj);
                IntInterval val = afterObj.readFrom(bases, p -> p.appendField(field.getName()));
                bindNodeResult(node, val, afterObj);
                post = afterObj;
            }
        } else if (node instanceof LoadIndexedNode lin) {
            // Evaluate array and index first to get the most precise interval information
            AbstractMemory afterArr = evalNode(lin.array(), post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            AbstractMemory afterIdx = evalNode(lin.index(), afterArr, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            AliasSet bases = accessBaseSetForNodeEval(lin.array(), afterIdx);
            Function<AccessPath, AccessPath> idxTransform = indexTransform(lin.index(), afterIdx);

            IntInterval idxIv = getNodeResultInterval(lin.index(), afterIdx);
            boolean preciseIndex = isPoint(idxIv);

            IntInterval val;
            if (preciseIndex) {
                // Index is a singleton -> use only the precise cell value (do NOT pollute with wildcard)
                val = afterIdx.readFrom(bases, idxTransform).copyOf();
            } else {
                // Fallback: combine precise/wildcard (may still be wildcard if transform is wildcard)
                IntInterval precisePart = afterIdx.readFrom(bases, idxTransform);
                IntInterval summaryPart = afterIdx.readFrom(bases, AccessPath::appendArrayWildcard);
                val = precisePart.copyOf();
                val.joinWith(summaryPart);
            }

            bindNodeResult(node, val, afterIdx);
            post = afterIdx;
        } else if (node instanceof ArrayLengthNode aln) {
            Node arr = aln.array();
            NewArrayNode newArr = resolveNewArray(arr);
            if (newArr != null) {
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
                    var logger2 = AbstractInterpretationLogger.getInstance();
                    logger2.log("Printing inputs of phiNode: " + phi, LoggerVerbosity.INFO);
                    for (Node input : phi.inputs()) {
                        logger2.log("  Phi input: " + input, LoggerVerbosity.INFO);
                    }
                    post.joinWith(evalNode(phi, post, abstractState, invokeCallBack, evalStack, iteratorContext));
                }
            }
        } else if (node instanceof IfNode ifNode) {
            Node cond = ifNode.condition();
            post = evalNode(cond, post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
        }
        logger.log("Computed post-condition: " + post + " for node: " + node, LoggerVerbosity.INFO);
        abstractState.setPostCondition(node, post);
    }

    private AbstractMemory evalNode(Node node, AbstractMemory in, AbstractState<AbstractMemory> abstractState,
                                    InvokeCallBack<AbstractMemory> invokeCallBack, Set<Node> evalStack, IteratorContext iteratorContext) {
        if (node == null) {
            return in;
        }
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
            case IntegerBelowNode integerBelowNode -> {
                return evalIntegerBelowNode(integerBelowNode, mem);
            }
            case IsNullNode isNullNode -> {
                // Boolean result in {0,1}; we do not track nullness precisely in interval domain
                IntInterval res = new IntInterval(0, 1);
                bindNodeResult(isNullNode, res, mem);
                return mem;
            }
            case PiNode piNode -> {
                return evalPiNode(piNode, mem);
            }
            case ValueProxy valueProxy -> {
                Node original = valueProxy.getOriginalNode();
                IntInterval v = getNodeResultInterval(original, mem);
                bindNodeResult(node, v, mem);
                return mem;
            }
            default -> {
                // Fallback: bind TOP for unsupported node kinds to avoid endless recursion or NPEs
                logger.log("Unknown node type for evaluation: " + node.getClass().getSimpleName(), LoggerVerbosity.DEBUG);
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
                // Prefer the merge post-condition when available as previous iteration state
                AbstractMemory mergePost = abstractState.getPostCondition(merge);
                if (mergePost != null) {
                    v = evalBackEdgeValue(input, mergePost);
                } else {
                    v = evalBackEdgeValue(input, originalIn);
                }
                logger.log("  Input[" + i + "]: " + input + " = " + v + " (computed from previous iteration)", LoggerVerbosity.DEBUG);
            } else {
                v = getNodeResultInterval(input, originalIn);
                logger.log("  Input[" + i + "]: " + input + " = " + v, LoggerVerbosity.DEBUG);
            }

            acc.joinWith(v);
        }

        // Induction + unknown bound detection and immediate widening when appropriate
        if (isLoopHeader) {
            try {
                Node backEdge = (numInputs > 0) ? phi.valueAt(numInputs - 1) : null;
                Node initNode = (numInputs > 0) ? phi.valueAt(0) : null;
                boolean isInduction = false;
                long incr = 0L;

                if (backEdge instanceof jdk.graal.compiler.nodes.calc.BinaryArithmeticNode<?> bin && bin instanceof AddNode) {
                    Node x = bin.getX();
                    Node y = bin.getY();
                    boolean xIsPhi = (x == phi) || (x instanceof ValueProxy vpX && vpX.getOriginalNode() == phi);
                    boolean yIsPhi = (y == phi) || (y instanceof ValueProxy vpY && vpY.getOriginalNode() == phi);
                    Node other = xIsPhi ? y : (yIsPhi ? x : null);
                    if (other instanceof ConstantNode cn && cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                        incr = cn.asJavaConstant().asLong();
                        if (incr > 0) {
                            isInduction = true;
                        }
                    }
                }

                boolean boundedByFinite = false;
                long constantUpper = Long.MAX_VALUE;
                Node potentialBoundNode = null;
                if (isInduction) {
                    for (Node u : phi.usages()) {
                        if (u instanceof IntegerLessThanNode itn) {
                            Node a = itn.getX();
                            Node b = itn.getY();
                            Node otherSide = (a == phi || (a instanceof ValueProxy vp && vp.getOriginalNode() == phi)) ? b : ((b == phi || (b instanceof ValueProxy vp2 && vp2.getOriginalNode() == phi)) ? a : null);
                            if (otherSide instanceof ConstantNode cn2 && cn2.asJavaConstant() != null && cn2.asJavaConstant().getJavaKind().isNumericInteger()) {
                                long bound = cn2.asJavaConstant().asLong();
                                constantUpper = Math.min(constantUpper, bound - 1);
                                boundedByFinite = true;
                            } else if (otherSide != null) {
                                potentialBoundNode = otherSide;
                                IntInterval otherIv = getNodeResultInterval(otherSide, originalIn);
                                if (!otherIv.isTop() && !otherIv.isUpperInfinite()) {
                                    constantUpper = Math.min(constantUpper, otherIv.getUpper());
                                    boundedByFinite = true;
                                }
                            }
                        }
                    }

                    // If the only bound is array length of a parameter (or Pi(parameter)) => unknown finite bound
                    boolean boundIsArrayLenOfParam = isArrayLengthOfParamOrPiParam(potentialBoundNode);

                    if (!boundedByFinite && boundIsArrayLenOfParam) {
                        // Immediately widen phi to [init, +inf] to force convergence
                        long newLower;
                        IntInterval initIv = getNodeResultInterval(initNode, originalIn);
                        if (initIv != null && !initIv.isBot() && !initIv.isLowerInfinite()) {
                            newLower = initIv.getLower();
                        } else {
                            newLower = 0L;
                        }
                        acc.setLower(Math.min(acc.isLowerInfinite() ? newLower : acc.getLower(), newLower));
                        acc.setUpper(IntInterval.POS_INF);
                        logger.log("Applied immediate widening for induction over unknown array length: " + acc, LoggerVerbosity.DEBUG);
                    } else if (!boundedByFinite) {
                        // Fallback to previous iteration based widening if we detect growth
                        AbstractMemory mergePost = abstractState.getPostCondition(merge);
                        IntInterval prevPhi = null;
                        if (mergePost != null) {
                            AccessPath prevP = mergePost.lookupTempByName(nodeId(phi));
                            if (prevP != null) prevPhi = mergePost.readStore(prevP);
                        }
                        if (prevPhi != null) {
                            boolean growingUpper = prevPhi.isTop() || prevPhi.isUpperInfinite() || (!acc.isUpperInfinite() && acc.getUpper() > prevPhi.getUpper());
                            if (growingUpper) {
                                long newLower;
                                IntInterval initIv = getNodeResultInterval(initNode, originalIn);
                                if (initIv != null && !initIv.isBot() && !initIv.isLowerInfinite()) {
                                    newLower = Math.min(initIv.getLower(), acc.isLowerInfinite() ? 0 : acc.getLower());
                                } else {
                                    newLower = acc.isLowerInfinite() ? 0 : acc.getLower();
                                }
                                IntInterval widened = new IntInterval(newLower, IntInterval.POS_INF);
                                acc.joinWith(widened);
                                logger.log("Applied widening to induction phi " + phi + " => " + acc, LoggerVerbosity.DEBUG);
                            }
                        }
                    } else if (boundedByFinite && constantUpper != Long.MAX_VALUE) {
                        long newUpper = Math.min(acc.isUpperInfinite() ? constantUpper : Math.min(acc.getUpper(), constantUpper), constantUpper);
                        acc.setUpper(newUpper);
                    }
                }
            } catch (Throwable t) {
                AbstractInterpretationLogger.getInstance().log("Induction/widening step failed: " + t.getMessage(), LoggerVerbosity.DEBUG);
            }
        }

        bindNodeResult(phi, acc, mem);
        logger.log("  PhiNode result: " + acc, LoggerVerbosity.INFO);
        return mem;
    }

    private boolean isArrayLengthOfParamOrPiParam(Node node) {
        if (!(node instanceof ArrayLengthNode aln)) return false;
        Node arr = aln.array();
        if (arr instanceof ParameterNode) return true;
        if (arr instanceof PiNode pi) {
            Node obj = pi.object();
            return obj instanceof ParameterNode;
        }
        return false;
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
        IntInterval ix = getNodeResultInterval(node.getX(), mem);
        IntInterval iy = getNodeResultInterval(node.getY(), mem);
        IntInterval res = new IntInterval();
        if (ix.isBot() || iy.isBot()) {
            res.setToBot();
        } else if (ix.isTop() || iy.isTop()) {
            res.setToTop();
        } else if (!ix.isUpperInfinite() && !iy.isLowerInfinite() && ix.getUpper() < iy.getLower()) {
            // definitely true: max(x) < min(y)
            res = new IntInterval(1, 1);
        } else if (!ix.isLowerInfinite() && !iy.isUpperInfinite() && ix.getLower() >= iy.getUpper()) {
            // definitely false: min(x) >= max(y)
            res = new IntInterval(0, 0);
        } else {
            res = new IntInterval(0, 1);
        }
        bindNodeResult(node, res, mem);
        return mem;
    }

    private AbstractMemory evalIntegerBelowNode(IntegerBelowNode node, AbstractMemory mem) {
        IntInterval ix = getNodeResultInterval(node.getX(), mem);
        IntInterval iy = getNodeResultInterval(node.getY(), mem);
        IntInterval res = new IntInterval();
        if (ix.isBot() || iy.isBot()) {
            res.setToBot();
        } else if (ix.isTop() || iy.isTop()) {
            res.setToTop();
        } else if (isNonNegative(ix) && isNonNegative(iy) && !ix.isUpperInfinite() && !iy.isLowerInfinite() && ix.getUpper() < iy.getLower()) {
            res = new IntInterval(1, 1);
        } else if (isNonNegative(ix) && isNonNegative(iy) && !ix.isLowerInfinite() && !iy.isUpperInfinite() && ix.getLower() >= iy.getUpper()) {
            res = new IntInterval(0, 0);
        } else {
            res = new IntInterval(0, 1);
        }
        bindNodeResult(node, res, mem);
        return mem;
    }

    private AbstractMemory evalLeftShiftNode(LeftShiftNode node, AbstractMemory mem) {
        IntInterval x = getNodeResultInterval(node.getX(), mem);
        IntInterval s = getNodeResultInterval(node.getY(), mem);
        IntInterval res = new IntInterval();
        if (x.isBot() || s.isBot()) {
            res.setToBot();
        } else if (x.isTop() || s.isTop()) {
            res.setToTop();
        } else if (s.isSingleton() && s.getLower() >= 0 && s.getLower() <= 63) {
            long sh = s.getLower();
            long factor = 1L << sh;
            IntInterval f = new IntInterval(factor, factor);
            res = x.mul(f);
        } else {
            res.setToTop();
        }
        bindNodeResult(node, res, mem);
        return mem;
    }

    private AbstractMemory evalRightShiftNode(RightShiftNode node, AbstractMemory mem) {
        IntInterval x = getNodeResultInterval(node.getX(), mem);
        IntInterval s = getNodeResultInterval(node.getY(), mem);
        IntInterval res = new IntInterval();
        if (x.isBot() || s.isBot()) {
            res.setToBot();
        } else if (x.isTop() || s.isTop()) {
            res.setToTop();
        } else if (s.isSingleton() && s.getLower() >= 0 && s.getLower() <= 63) {
            long sh = s.getLower();
            long div = 1L << sh;
            if (!x.isLowerInfinite() && x.getLower() >= 0) {
                long lo = x.getLower() / div;
                long hi = x.getUpper() / div;
                res = new IntInterval(lo, hi);
            } else {
                res.setToTop();
            }
        } else {
            res.setToTop();
        }
        bindNodeResult(node, res, mem);
        return mem;
    }

    private AbstractMemory evalUnsignedRightShiftNode(UnsignedRightShiftNode node, AbstractMemory mem) {
        IntInterval x = getNodeResultInterval(node.getX(), mem);
        IntInterval s = getNodeResultInterval(node.getY(), mem);
        IntInterval res = new IntInterval();
        if (x.isBot() || s.isBot()) {
            res.setToBot();
        } else if (x.isTop() || s.isTop()) {
            res.setToTop();
        } else if (s.isSingleton() && s.getLower() >= 0 && s.getLower() <= 63) {
            long sh = s.getLower();
            long div = 1L << sh;
            // For non-negative x, >>> equals arithmetic >>
            if (!x.isLowerInfinite() && x.getLower() >= 0) {
                long lo = x.getLower() / div;
                long hi = x.getUpper() / div;
                res = new IntInterval(lo, hi);
            } else {
                // Negative inputs to >>> produce large positives; over-approximate with Top
                res.setToTop();
            }
        } else {
            res.setToTop();
        }
        bindNodeResult(node, res, mem);
        return mem;
    }

    private AbstractMemory evalVirtualArrayNode(VirtualArrayNode node, AbstractMemory mem) {
        // Treat as an allocation root for aliasing purposes
        String aid = "allocVArr" + Integer.toHexString(System.identityHashCode(node));
        AccessPath root = AccessPath.forAllocSite(aid);
        mem.bindTempByName(nodeId(node), root);
        return mem;
    }

    private AbstractMemory evalAllocatedObjectNode(AllocatedObjectNode node, AbstractMemory mem) {
        // Treat as an allocation root for aliasing purposes
        String aid = "allocObj" + Integer.toHexString(System.identityHashCode(node));
        AccessPath root = AccessPath.forAllocSite(aid);
        mem.bindTempByName(nodeId(node), root);
        return mem;
    }

    private AbstractMemory evalIntegerEqualsNode(IntegerEqualsNode node, AbstractMemory mem) {
        IntInterval res = new IntInterval(0, 1);
        bindNodeResult(node, res, mem);
        return mem;
    }

    private AbstractMemory evalPiNode(PiNode piNode, AbstractMemory mem) {
        IntInterval base = getNodeResultInterval(piNode.object(), mem);
        IntInterval refined = base.copyOf();
        Stamp s = piNode.piStamp();
        if (s instanceof IntegerStamp integerStamp) {
            if (integerStamp.isStrictlyPositive()) {
                if (refined.isLowerInfinite() || refined.getLower() < 0) {
                    refined.setLower(0);
                }
            }
            if (integerStamp.isStrictlyPositive() || integerStamp.isStrictlyNegative()) {
                if (refined.getLower() == 0 && refined.getUpper() == 0) {
                    refined.setToTop();
                } else if (refined.getLower() == 0) {
                    refined.setLower(1);
                } else if (refined.getUpper() == 0) {
                    refined.setUpper(-1);
                    refined.setToTop();
                }
            }
            // We could add more cases (non-null object stamps ignored for numeric domain)
        }
        bindNodeResult(piNode, refined, mem);
        return mem;
    }

    private static void bindNodeResult(Node node, IntInterval val, AbstractMemory mem) {
        if (node == null) {
            AbstractInterpretationLogger.getInstance().log("bindNodeResult called with null node, skipping", LoggerVerbosity.DEBUG);
            return;
        }
        String id = nodeId(node);
        AccessPath p = AccessPath.forLocal(id);
        mem.bindTempByName(id, p);
        mem.writeStoreStrong(p, val.copyOf());
    }

    private static IntInterval getNodeResultInterval(Node node, AbstractMemory mem) {
        var logger = AbstractInterpretationLogger.getInstance();

        if (node == null) {
            logger.log("getNodeResultInterval called with null node, returning TOP", LoggerVerbosity.DEBUG);
            IntInterval top = new IntInterval();
            top.setToTop();
            return top;
        }

        String nid = nodeId(node);

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

    private static AliasSet accessBaseSetForNodeEval(Node objNode, AbstractMemory mem) {
        switch (objNode) {
            case null -> {
                return AliasSet.of();
            }
            case ParameterNode pn -> {
                String pname = "param" + pn.index();
                return mem.lookupParamSetByName(pname);
            }
            case NewInstanceNode nii -> {
                String aid = "alloc" + Integer.toHexString(System.identityHashCode(nii));
                return AliasSet.of(AccessPath.forAllocSite(aid));
            }
            case NewArrayNode nan -> {
                String aid = "alloc" + Integer.toHexString(System.identityHashCode(nan));
                return AliasSet.of(AccessPath.forAllocSite(aid));
            }
            default -> {
            }
        }
        AliasSet s = mem.lookupTempSetByName(nodeId(objNode));
        if (s != null) return s;
        return AliasSet.of();
    }

    private static AliasSet resolveFieldBaseSet(Node objNode, ResolvedJavaField field, AbstractMemory mem) {
        if (objNode == null) {
            String className = field.getDeclaringClass().getName();
            return AliasSet.of(AccessPath.forStaticClass(className));
        }
        return accessBaseSetForNodeEval(objNode, mem);
    }

    private static boolean isPoint(IntInterval v) {
        return v != null && !v.isTop() && !v.isBot() && !v.isLowerInfinite() && !v.isUpperInfinite() && v.getLower() == v.getUpper();
    }

    private static Function<AccessPath, AccessPath> indexTransform(Node indexNode, AbstractMemory mem) {
        IntInterval idx = getNodeResultInterval(indexNode, mem);
        if (isPoint(idx)) {
            long k = idx.getLower();
            if (k >= Integer.MIN_VALUE && k <= Integer.MAX_VALUE) {
                int ik = (int) k;
                return p -> p.appendArrayIndex(ik);
            }
        }
        return AccessPath::appendArrayWildcard;
    }

    private NewArrayNode resolveNewArray(Node n) {
        return resolveNewArray(n, new HashSet<>(), 0);
    }

    private NewArrayNode resolveNewArray(Node n, Set<Node> visited, int depth) {
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
}
