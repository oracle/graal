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
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
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
                    AbstractMemory narrowed = narrowOnLessThan(post, itn, true);
                    abstractState.getPreCondition(target).joinWith(narrowed);
                    return;
                } else if (target.equals(ifNode.falseSuccessor())) {
                    AbstractMemory narrowed = narrowOnLessThan(post, itn, false);
                    abstractState.getPreCondition(target).joinWith(narrowed);
                    abstractState.getPreCondition(target).joinWith(post);
                    return;
                }

                abstractState.getPreCondition(target).joinWith(abstractState.getPostCondition(source));
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
                // For soundness, we use wildcard [*] to represent all array elements
                // This is a weak update: we join with existing array content
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
            IntInterval len = new IntInterval(0, IntInterval.POS_INF);
            bindNodeResult(node, len, post);
        } else if (node instanceof ReturnNode rn) {
            if (rn.result() != null) {
                AbstractMemory after = evalNode(rn.result(), post, abstractState, invokeCallBack, new HashSet<>(), iteratorContext);
                IntInterval v = getNodeResultInterval(rn.result(), after);
                post = after.copyOf();
                post.bindLocalByName("ret", AccessPath.forLocal("ret"));
                post.writeStore(AccessPath.forLocal("ret"), v);
            }
        } else if (node instanceof AbstractMergeNode merge) {
            // Merge nodes need to evaluate their phi nodes with the incoming edge context
            // The phi nodes are in the usages of the merge node
            Set<Node> evalStack = new HashSet<>();
            for (Node usage : merge.usages()) {
                if (usage instanceof PhiNode phi) {
                    logger.log("Printing inputs of phiNode: " + phi, LoggerVerbosity.INFO);
                    for (Node input : phi.inputs()) {
                        logger.log("  Phi input: " + input, LoggerVerbosity.INFO);
                    }
                    // Evaluate the phi with the current predecessor context
                    // This updates the post-state with the phi's computed value
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

    /**
     * Evaluate the semantics of a specific node type.
     * This is separated from evalNode to keep the logic clean.
     */
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
            case AllocatedObjectNode allocatedObjectNode -> {
                return evalAllocatedObjectNode(allocatedObjectNode, mem);
            }
            case BinaryArithmeticNode<?> binaryArithmeticNode -> {
                return evalBinaryArithmeticNode(binaryArithmeticNode, mem);
            }
            case IntegerLessThanNode integerLessThanNode -> {
                return evalIntegerLessThanNode(integerLessThanNode, mem);
            }
            case Invoke invoke -> {
                return evalInvokeNode(invoke, mem, abstractState, invokeCallBack);
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
        // Parameters start with a top interval (unknown input)
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
            // We're in a different block (e.g., evaluating ValueProxy in exit block)
            // The phi was already computed during loop analysis
            // Look up the already-computed value from the abstract state
            logger.log("  PhiNode accessed from different block - looking up computed value", LoggerVerbosity.DEBUG);

            // First check if it's in current memory
            AccessPath existingPath = mem.lookupTempByName(nodeId(phi));
            if (existingPath != null) {
                IntInterval existingValue = mem.readStore(existingPath);
                logger.log("  PhiNode cached value: " + existingValue, LoggerVerbosity.DEBUG);
                return mem;
            }

            // If not found in current mem, get from abstract state's post-condition of the merge node
            AbstractMemory mergePost = abstractState.getPostCondition(merge);
            AccessPath mergePath = mergePost.lookupTempByName(nodeId(phi));
            if (mergePath != null) {
                IntInterval mergeValue = mergePost.readStore(mergePath);
                bindNodeResult(phi, mergeValue, mem);
                logger.log("  PhiNode value from merge post-condition: " + mergeValue, LoggerVerbosity.DEBUG);
                return mem;
            }

            // Fallback: evaluate conservatively
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

        // Determine which inputs to use
        int numInputs = phi.valueCount();
        for (int i = 0; i < numInputs; i++) {
            Node input = phi.valueAt(i);
            if (isLoopHeader && isFirstVisit && i == numInputs - 1) {
                // Skip last input on first iteration (it's the back-edge with uncomputed value)
                logger.log("  Input[" + i + "]: " + input + " = SKIPPED (back-edge on first iteration)", LoggerVerbosity.DEBUG);
                continue;
            }

            IntInterval v;
            if (isLoopHeader && !isFirstVisit && i == numInputs - 1) {
                // Back-edge on subsequent iteration: compute using previous iteration's phi values
                logger.log("    Evaluating back-edge with originalIn: " + originalIn, LoggerVerbosity.DEBUG);
                v = evalBackEdgeValue(input, originalIn);
                logger.log("  Input[" + i + "]: " + input + " = " + v + " (computed from previous iteration)", LoggerVerbosity.DEBUG);
            } else {
                // Entry edge: look up from originalIn
                v = getNodeResultInterval(input, originalIn);
                logger.log("  Input[" + i + "]: " + input + " = " + v, LoggerVerbosity.DEBUG);
            }

            acc.joinWith(v);
        }

        bindNodeResult(phi, acc, mem);
        logger.log("  PhiNode result: " + acc, LoggerVerbosity.INFO);
        return mem;
    }

    /**
     * Evaluate a back-edge value by computing it from values in the given memory.
     * This uses phi values from the previous iteration to compute updated values.
     */
    private IntInterval evalBackEdgeValue(Node node, AbstractMemory fromMem) {
        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("    evalBackEdgeValue for node: " + node, LoggerVerbosity.DEBUG);

        // For binary arithmetic nodes (Add, Sub, etc.), compute from operands
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
            }
        }

        // For other nodes, return top
        logger.log("      Returning top (unsupported node type)", LoggerVerbosity.DEBUG);
        IntInterval top = new IntInterval();
        top.setToTop();
        return top;
    }

    private AbstractMemory evalAllocatedObjectNode(AllocatedObjectNode alloc, AbstractMemory mem) {
        String aid = "alloc" + Integer.toHexString(System.identityHashCode(alloc));
        AccessPath root = AccessPath.forAllocSite(aid);
        mem.bindTempByName(nodeId(alloc), root);
        return mem;
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
        } else if (bin instanceof FloatDivNode) {
            res = ix.div(iy);
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

    private AbstractMemory evalInvokeNode(Invoke inv, AbstractMemory mem,
                                          AbstractState<AbstractMemory> abstractState,
                                          InvokeCallBack<AbstractMemory> invokeCallBack) {
        if (invokeCallBack != null) {
            var outcome = invokeCallBack.handleInvoke(inv, inv.asNode(), abstractState);
            if (outcome != null && outcome.isError()) {
                mem.setToTop();
            } else {
                IntInterval top = new IntInterval();
                top.setToTop();
                bindNodeResult(inv.asNode(), top, mem);
            }
        } else {
            IntInterval top = new IntInterval();
            top.setToTop();
            bindNodeResult(inv.asNode(), top, mem);
        }
        return mem;
    }

    private static void bindNodeResult(Node node, IntInterval val, AbstractMemory mem) {
        String id = nodeId(node);
        AccessPath p = AccessPath.forLocal(id);
        mem.bindTempByName(id, p);
        mem.writeStore(p, val.copyOf());
    }

    /* Read the interval associated with a node's temp in a local memory */
    private static IntInterval getNodeResultInterval(Node node, AbstractMemory mem) {
        String nid = nodeId(node);
        var logger = AbstractInterpretationLogger.getInstance();

        // CRITICAL FIX: Check if node is a constant FIRST before reading from store
        // Constants should be evaluated on-demand, not stored
        if (node instanceof ConstantNode cn) {
            if (cn.asJavaConstant() != null && cn.asJavaConstant().getJavaKind().isNumericInteger()) {
                long v = cn.asJavaConstant().asLong();
                IntInterval result = new IntInterval(v, v);
                logger.log("        getNodeResultInterval: constant " + node + " = " + result, LoggerVerbosity.DEBUG);
                return result;
            }
        }

        // For non-constants, read from the store using the access path
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

    private static AccessPath resolveFieldBase(Node objNode, ResolvedJavaField field, AbstractMemory mem) {
        if (objNode == null) {
            String className = field.getDeclaringClass().getName();
            return AccessPath.forStaticClass(className);
        }
        return accessBaseForNodeEval(objNode, mem);
    }
}
