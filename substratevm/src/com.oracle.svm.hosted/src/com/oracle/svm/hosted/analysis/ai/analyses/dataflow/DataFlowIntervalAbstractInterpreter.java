package com.oracle.svm.hosted.analysis.ai.analyses.dataflow;

import com.oracle.svm.hosted.analysis.ai.analyzer.invokehandle.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.analyzer.invokehandle.InvokeInput;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AliasSet;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.NodeState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationServices;
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
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.spi.ArrayLengthProvider;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Executing edge from: " + source + " -> " + target, LoggerVerbosity.DEBUG);
        AbstractMemory sourcePost = abstractState.getPostCondition(source);
        AbstractMemory destPre = abstractState.getPreCondition(target);

        logger.log("Source post: " + sourcePost, LoggerVerbosity.DEBUG);
        logger.log("Dest pre: " + destPre, LoggerVerbosity.DEBUG);
        if (target instanceof LoopExitNode exit) {
            LoopBeginNode lb = exit.loopBegin();
            AbstractMemory acc = sourcePost.copyOf();
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
            IntInterval res = getNodeResultInterval(ifNode, sourcePost);
            boolean defTrue = !res.isBot() && !res.isTop() && res.getLower() == 1 && res.getUpper() == 1;
            boolean defFalse = !res.isBot() && !res.isTop() && res.getLower() == 0 && res.getUpper() == 0;

            boolean toTrue = target.equals(ifNode.trueSuccessor());
            boolean toFalse = target.equals(ifNode.falseSuccessor());

            if ((toTrue && defFalse) || (toFalse && defTrue)) {
                abstractState.getNodeState(target).setMark(NodeState.NodeMark.UNREACHABLE);
                logger.log("[EdgePrune] Skipping " + (toTrue ? "true" : "false") + " edge (condition is definitely " + (defTrue ? "true" : "false") + "): " + source + " -> " + target,
                        LoggerVerbosity.DEBUG);
                return;
            }

            // Narrowing on some conditions
            AbstractMemory edgeState = sourcePost.copyOf();
            if (cond instanceof IntegerLessThanNode itn) {
                if (toTrue) {
                    narrowOnLessThan(edgeState, itn, true);
                } else if (toFalse) {
                    narrowOnLessThan(edgeState, itn, false);
                }
                destPre.joinWith(edgeState);
                return;
            }

            if (cond instanceof IntegerBelowNode ib) {
                if (toTrue) {
                    narrowOnBelow(edgeState, ib, true);
                } else if (toFalse) {
                    narrowOnBelow(edgeState, ib, false);
                }
                destPre.joinWith(edgeState);
                return;
            }

            if (cond instanceof IntegerEqualsNode eq) {
                if (toTrue) {
                    narrowOnEquals(edgeState, eq, true);
                } else if (toFalse) {
                    narrowOnEquals(edgeState, eq, false);
                }
                destPre.joinWith(edgeState);
                return;
            }

            if (cond instanceof IsNullNode isNull) {
                IntInterval bool = toTrue ? new IntInterval(1, 1) : new IntInterval(0, 0);
                bindNodeResult(isNull, bool, edgeState);
                destPre.joinWith(edgeState);
                return;
            }

            // Unknown condition: just propagate
            destPre.joinWith(sourcePost);
            return;
        }

        // Non-If edges: standard propagation
        destPre.joinWith(sourcePost);
    }

    private void narrowOnLessThan(AbstractMemory base, IntegerLessThanNode itn, boolean isTrue) {
        Node x = itn.getX();
        Node y = itn.getY();
        IntInterval ix = getNodeResultInterval(x, base);
        IntInterval iy = getNodeResultInterval(y, base);
        if (ix == null || iy == null) return;
        AccessPath px = base.lookupTempByName(nodeId(x));
        if (px == null) return;

        if (isTrue) {
            // (x < y) => x.upper < y.lower
            ix.meetWith(new IntInterval(IntInterval.NEG_INF, iy.getLower() - 1));
        } else {
            // !(x < y) => x >= y  => x.lower >= y.lower  and y.upper <= x.upper
            ix.meetWith(new IntInterval(iy.getUpper(), ix.getUpper()));
        }

        base.writeStoreStrong(px, ix);
    }

    private void narrowOnEquals(AbstractMemory base, IntegerEqualsNode eq, boolean isTrue) {
        Node x = eq.getX();
        Node y = eq.getY();
        IntInterval ix = getNodeResultInterval(x, base);
        IntInterval iy = getNodeResultInterval(y, base);
        if (ix == null || iy == null) return;
        AccessPath px = base.lookupTempByName(nodeId(x));
        if (px == null) return;

        if (isTrue && isPoint(iy)) {
            ix.meetWith(iy);
            base.writeStoreStrong(px, ix);
        }
    }

    private static boolean isNonNegative(IntInterval v) {
        return v != null && !v.isBot() && !v.isTop() && !v.isLowerInfinite() && v.getLower() >= 0;
    }

    private void narrowOnBelow(AbstractMemory base, IntegerBelowNode ib, boolean isTrue) {
        Node x = ib.getX();
        Node y = ib.getY();
        IntInterval ix = getNodeResultInterval(x, base);
        IntInterval iy = getNodeResultInterval(y, base);
        if (ix == null || iy == null) return;
        if (!isNonNegative(ix) || !isNonNegative(iy)) return;
        AccessPath px = base.lookupTempByName(nodeId(x));
        if (px == null) return;

        if (isTrue) {
            ix.meetWith(new IntInterval(0, iy.getLower() - 1));
        } else {
            ix.meetWith(new IntInterval(iy.getUpper(), ix.getUpper()));
        }

        base.writeStoreStrong(px, ix);
    }

    @Override
    public void execNode(Node node, AbstractState<AbstractMemory> abstractState, InvokeCallBack<AbstractMemory> invokeCallBack, IteratorContext iteratorContext) {
        AbstractMemory pre = abstractState.getPreCondition(node);
        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("Executing node: " + node + " with pre-condition: " + pre, LoggerVerbosity.DEBUG);

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
                AliasSet bases = resolveFieldBaseSet(null, field, post);
                IntInterval val = post.readFrom(bases, p -> p.appendField(field.getName()));
                if (val.isTop() && field.getType().getJavaKind().isNumericInteger()) {
                    val = new IntInterval(0, 0);
                    logger.log("LoadField of uninitialized static field " + field.getName() +
                            ", using default value [0, 0]", LoggerVerbosity.DEBUG);
                }
                bindNodeResult(node, val, post);
            } else {
                AbstractMemory afterObj = evalNode(lfn.object(), post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
                AliasSet bases = resolveFieldBaseSet(lfn.object(), field, afterObj);
                IntInterval val = afterObj.readFrom(bases, p -> p.appendField(field.getName()));
                bindNodeResult(node, val, afterObj);
                post = afterObj;
            }
        } else if (node instanceof LoadIndexedNode lin) {
            AbstractMemory afterArr = evalNode(lin.array(), post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            AbstractMemory afterIdx = evalNode(lin.index(), afterArr, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            AliasSet bases = accessBaseSetForNodeEval(lin.array(), afterIdx);
            Function<AccessPath, AccessPath> idxTransform = indexTransform(lin.index(), afterIdx);

            IntInterval idxIv = getNodeResultInterval(lin.index(), afterIdx);
            boolean preciseIndex = isPoint(idxIv);

            IntInterval val;
            if (preciseIndex) {
                val = afterIdx.readFrom(bases, idxTransform).copyOf();
            } else {
                IntInterval precisePart = afterIdx.readFrom(bases, idxTransform);
                IntInterval summaryPart = afterIdx.readFrom(bases, AccessPath::appendArrayWildcard);
                val = precisePart.copyOf();
                val.joinWith(summaryPart);
            }

            bindNodeResult(node, val, afterIdx);
            post = afterIdx;
        } else if (node instanceof ArrayLengthNode aln) {
            AbstractMemory afterArr = evalNode(aln.array(), post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            Node arr = aln.array();

            IntInterval lenFromArrayVal = getNodeResultInterval(arr, afterArr);
            if (lenFromArrayVal != null && !lenFromArrayVal.isBot() && !lenFromArrayVal.isTop()) {
                bindNodeResult(aln, lenFromArrayVal, afterArr);
                post = afterArr;
            } else {
                NewArrayNode newArr = resolveNewArray(arr);
                if (newArr != null) {
                    IntInterval lenIv = getNodeResultInterval(newArr.length(), afterArr);
                    if (lenIv == null || lenIv.isTop() || lenIv.isBot()) {
                        IntInterval len = new IntInterval(0, IntInterval.POS_INF);
                        bindNodeResult(node, len, afterArr);
                    } else {
                        bindNodeResult(node, lenIv, afterArr);
                    }
                } else {
                    IntInterval len = new IntInterval(0, IntInterval.POS_INF);
                    bindNodeResult(node, len, afterArr);
                }
                post = afterArr;
            }
        } else if (node instanceof NewInstanceNode nii) {
            String aid = "alloc" + Integer.toHexString(System.identityHashCode(nii));
            AccessPath root = AccessPath.forAllocSite(aid);
            post.bindTempByName(nodeId(nii), root);
        } else if (node instanceof NewArrayNode nan) {
            String aid = "alloc" + Integer.toHexString(System.identityHashCode(nan));
            AccessPath root = AccessPath.forAllocSite(aid);
            post.bindTempByName(nodeId(nan), root);
            IntInterval size = getNodeResultInterval(nan.length(), post);
            bindNodeResult(node, size, post);

        } else if (node instanceof Invoke inv) {
            var invokeLogger = AbstractInterpretationLogger.getInstance();
            invokeLogger.log("[InvokeEval] Enter invoke node: " + inv + ", targetMethod=" + (inv.callTarget() != null ? inv.callTarget().targetMethod() : "<null>") +
                    ", preMemory=" + post, LoggerVerbosity.DEBUG);

            List<Node> args = new ArrayList<>(inv.callTarget().arguments());
            AbstractMemory afterArgs = post.copyOf();
            List<AbstractMemory> argDomains = new ArrayList<>(args.size());
            List<IntInterval> argIntervals = new ArrayList<>(args.size());

            for (Node arg : args) {
                afterArgs = evalNode(arg, afterArgs, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
                abstractState.setPostCondition(arg, afterArgs.copyOf());
                argDomains.add(afterArgs.copyOf());
                IntInterval ai = getNodeResultInterval(arg, afterArgs);
                argIntervals.add(ai);
            }

            invokeLogger.log("[InvokeEval] Collected argument intervals: " + argIntervals, LoggerVerbosity.DEBUG);
            var callerMethod = iteratorContext.getCurrentAnalysisMethod();
            InvokeInput<AbstractMemory> input = InvokeInput.of(callerMethod, abstractState, inv, args, argDomains);
            var outcome = invokeCallBack.handleInvoke(input);

            if (outcome == null) {
                invokeLogger.log("[InvokeEval] Invoke outcome is null, binding TOP.", LoggerVerbosity.DEBUG);
                IntInterval top = new IntInterval();
                top.setToTop();
                bindNodeResult(inv.asNode(), top, post);
            } else if (!outcome.isOk()) {
                invokeLogger.log("[InvokeEval] Invoke outcome indicates ERROR: " + outcome + ", setting whole memory TOP.", LoggerVerbosity.DEBUG);
                post.setToTop();
                IntInterval top = new IntInterval();
                top.setToTop();
                bindNodeResult(inv.asNode(), top, post);
            } else if (outcome.summary() != null) {
                Summary<AbstractMemory> sum = outcome.summary();
                AbstractMemory calleePost = sum.getPostCondition();
                IntInterval retVal = calleePost.readStore(AccessPath.forLocal("ret"));

                if (retVal == null) {
                    retVal = new IntInterval();
                    retVal.setToTop();
                    invokeLogger.log("[InvokeEval] Summary found but return value missing; using TOP.", LoggerVerbosity.DEBUG);
                } else {
                    invokeLogger.log("[InvokeEval] Summary return interval: " + retVal, LoggerVerbosity.DEBUG);
                }
                bindNodeResult(inv.asNode(), retVal, post);
            } else {
                invokeLogger.log("[InvokeEval] Outcome without summary; binding TOP.", LoggerVerbosity.DEBUG);
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
                    post.joinWith(evalNode(phi, post, abstractState, invokeCallBack, evalStack, iteratorContext));
                }
            }
        } else if (node instanceof IfNode ifNode) {
            Node cond = ifNode.condition();
            post = evalNode(cond, post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
            IntInterval ix = getNodeResultInterval(cond, post);
            bindNodeResult(ifNode, ix, post);
        }

        logger.log("Computed post-condition: " + post + " for node: " + node, LoggerVerbosity.DEBUG);
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
        logger.log("Evaluating node: " + node + " with input memory: " + in, LoggerVerbosity.DEBUG);
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
            case CompareNode cmpNode -> {
                return evalCompareNode(cmpNode, mem);
            }
            case UnsignedRightShiftNode unsignedRightShiftNode -> {
                return evalUnsignedRightShiftNode(unsignedRightShiftNode, mem);
            }
            case RightShiftNode rightShiftNode -> {
                return evalRightShiftNode(rightShiftNode, mem);
            }
            case LeftShiftNode leftShiftNode -> {
                return evalLeftShiftNode(leftShiftNode, mem);
            }
            case IsNullNode isNullNode -> {
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
            case AllocatedObjectNode allocatedObjectNode -> {
                return evalAllocatedObjectNode(allocatedObjectNode, mem);
            }
            default -> {
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
            logger.log("  Constant node " + cn + " evaluated to interval: " + iv, LoggerVerbosity.DEBUG);
            bindNodeResult(cn, iv, mem);
        } else {
            IntInterval top = new IntInterval();
            top.setToTop();
            logger.log("  Constant node " + cn + " is non-integer, using top", LoggerVerbosity.DEBUG);
            bindNodeResult(cn, top, mem);
        }
        return mem;
    }

    private AbstractMemory evalParameterNode(ParameterNode pn, AbstractMemory mem) {
        String pname = "param" + pn.index();
        AccessPath root = AccessPath.forLocal(pname);
        mem.bindParamByName(pname, root);
        mem.bindTempByName(nodeId(pn), root);
        if (!mem.hasStoreEntry(root)) {
            IntInterval top = new IntInterval();
            top.setToTop();
            mem.writeStoreStrong(root, top);
        }
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

//            logger.log("  PhiNode not found in state, evaluating conservatively", LoggerVerbosity.DEBUG);
        }
        boolean isLoopHeader = (merge instanceof LoopBeginNode);
        boolean isFirstVisit = false;

        if (isLoopHeader && iteratorContext != null) {
            isFirstVisit = iteratorContext.isFirstVisit(merge);
        }

//        logger.log("Evaluating PhiNode: " + phi + " (loop header: " + isLoopHeader + ", first visit: " + isFirstVisit + ")", LoggerVerbosity.DEBUG);
        IntInterval acc = new IntInterval();
        acc.setToBot();

        int numInputs = phi.valueCount();
        for (int i = 0; i < numInputs; i++) {
            Node input = phi.valueAt(i);
            if (isLoopHeader && isFirstVisit && i == numInputs - 1) {
//                logger.log("  Input[" + i + "]: " + input + " = SKIPPED (back-edge on first iteration)", LoggerVerbosity.DEBUG);
                continue;
            }

            IntInterval v;
            if (isLoopHeader && !isFirstVisit && i == numInputs - 1) {
//                logger.log("    Evaluating back-edge with originalIn: " + originalIn, LoggerVerbosity.DEBUG);
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
        bindNodeResult(phi, acc, mem);
        logger.log("  PhiNode result: " + acc, LoggerVerbosity.DEBUG);
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

    private AbstractMemory evalCompareNode(CompareNode cmpNode, AbstractMemory mem) {
        IntInterval ix = getNodeResultInterval(cmpNode.getX(), mem);
        IntInterval iy = getNodeResultInterval(cmpNode.getY(), mem);
        IntInterval res = new IntInterval();
        if (ix.isBot() || iy.isBot()) {
            res.setToBot();
        } else if (ix.isTop() || iy.isTop()) {
            res.setToTop();
        }

        switch (cmpNode) {
            case IntegerLessThanNode iltn -> res = getResultIntegerLessThanNode(ix, iy);
            case IntegerBelowNode ibn -> res = getResultIntegerBelowNode(ix, iy);
            case IntegerEqualsNode ien -> res = getResultIntegerEqualsNode(ix, iy);
            default -> {
            }
        }
        bindNodeResult(cmpNode, res, mem);
        return mem;
    }

    private IntInterval getResultIntegerLessThanNode(IntInterval ix, IntInterval iy) {
        assert !ix.isTop() && !ix.isBot() && !iy.isTop() && !iy.isBot();
        if (!ix.isUpperInfinite() && !iy.isLowerInfinite() && ix.getUpper() < iy.getLower()) {
            return new IntInterval(1, 1);
        }
        if (!ix.isLowerInfinite() && !iy.isUpperInfinite() && ix.getLower() >= iy.getUpper()) {
            return new IntInterval(0, 0);
        }
        return new IntInterval(0, 1);
    }

    private IntInterval getResultIntegerBelowNode(IntInterval ix, IntInterval iy) {
        assert !ix.isTop() && !ix.isBot() && !iy.isTop() && !iy.isBot();
        if (isNonNegative(ix) && isNonNegative(iy) && !ix.isUpperInfinite() && !iy.isLowerInfinite() && ix.getUpper() < iy.getLower()) {
            return new IntInterval(1, 1);
        }
        if (isNonNegative(ix) && isNonNegative(iy) && !ix.isLowerInfinite() && !iy.isUpperInfinite() && ix.getLower() >= iy.getUpper()) {
            return new IntInterval(0, 0);
        }
        return new IntInterval(0, 1);
    }

    private IntInterval getResultIntegerEqualsNode(IntInterval ix, IntInterval iy) {
        assert !ix.isTop() && !ix.isBot() && !iy.isTop() && !iy.isBot();
        if (ix.getUpper() == iy.getUpper() && ix.getLower() == iy.getLower()) {
            return new IntInterval(1, 1);
        }
        return new IntInterval(0, 0);
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
        String aid = "allocVArr" + Integer.toHexString(System.identityHashCode(node));
        AccessPath root = AccessPath.forAllocSite(aid);
        mem.bindTempByName(nodeId(node), root);
        return mem;
    }

    private AbstractMemory evalAllocatedObjectNode(AllocatedObjectNode node, AbstractMemory mem) {
        String aid = "allocObj" + Integer.toHexString(System.identityHashCode(node));
        AccessPath root = AccessPath.forAllocSite(aid);
        mem.bindTempByName(nodeId(node), root);

        if (node.getVirtualObject() instanceof VirtualArrayNode vArr) {
            var bb = AbstractInterpretationServices.getInstance().getInflation();
            var constRef = bb.getConstantReflectionProvider();
            var lenNode = vArr.findLength(ArrayLengthProvider.FindLengthMode.SEARCH_ONLY, constRef);
            if (lenNode instanceof ConstantNode cn &&
                    cn.asJavaConstant() != null &&
                    cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                long len = cn.asJavaConstant().asLong();
                if (len >= 0) {
                    IntInterval sizeIv = new IntInterval(len, len);
                    bindNodeResult(node, sizeIv, mem);
                }
            }
        }

        return mem;
    }


    private AbstractMemory evalPiNode(PiNode piNode, AbstractMemory mem) {
        IntInterval base = getNodeResultInterval(piNode.object(), mem);
        IntInterval refined = (base != null) ? base.copyOf() : new IntInterval();
        if (base == null) {
            refined.setToTop();
        }
        Stamp s = piNode.piStamp();
        if (s instanceof IntegerStamp integerStamp && !refined.isBot() && !refined.isTop()) {
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
                    refined.setToTop();
                }
            }
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

        AccessPath mapped = mem.lookupTempByName(nid);
        IntInterval result;
        if (mapped != null) {
            result = mem.readStore(mapped);
        } else {
            AccessPath p = AccessPath.forLocal(nid);
            result = mem.readStore(p);
        }

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
            return null;
        }
        // TODO: we will have to handle PiNode here
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
