/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.vector.nodes.consumer;

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.InputType.Value;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorOperation;
import jdk.graal.compiler.vector.nodes.SimplifiableVectorNode.VectorSimplifier;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.PartialVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorConsumerProxyNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorInitialIteratorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.CommutativeFoldVectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.FoldVectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.op.ConcatVectorNode;
import jdk.graal.compiler.vector.nodes.op.MapVectorNode;
import jdk.graal.compiler.vector.nodes.op.VectorHashStepNode;
import jdk.graal.compiler.vector.nodes.op.VectorPhi;
import jdk.graal.compiler.vector.nodes.op.VectorSubtractStepNode;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphNode;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphUtil;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.DuplicationReplacement;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.iterators.FilteredNodeIterable;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.ConvertNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.OpaqueNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

//@formatter:off
@NodeInfo(allowedUsageTypes = {Association, Value},
          cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot argue about vector nodes statically.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public final class FoldVectorNode extends FixedWithNextNode implements LowerableVectorConsumer, SubGraphNode, SimdifyableVectorOperation {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable vectorization of hashCode patterns.")
        public static final OptionKey<Boolean> VectorizeHashes = new OptionKey<>(true);
        // @formatter:on
    }

    public static final NodeClass<FoldVectorNode> TYPE = NodeClass.create(FoldVectorNode.class);
    protected StructuredGraph op;
    private final Direction direction;
    @Input ValueNode initial;
    @OptionalInput ValueNode originalInitial;
    @Input ValueNode length;
    @Input NodeInputList<ValueNode> vectorInputs;
    @Input NodeInputList<ValueNode> scalarInputs;
    /** @see LowerableVectorConsumer#vectorLoopMarker() */
    @OptionalInput(Association) VectorLoopMarkerNode vectorLoopMarker;

    private AnchoringNode loopAnchor;
    private double trustedBodyIterations;

    @NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
    public static final class AccumulatorNode extends FloatingNode {
        public static final NodeClass<AccumulatorNode> TYPE = NodeClass.create(AccumulatorNode.class);

        public AccumulatorNode(Stamp stamp) {
            super(TYPE, stamp);
        }
    }

    /**
     * Abstract base class for macro nodes that model more complex operations but behave like
     * associative-commutative binary arithmetic nodes for the purposes of a fold's inner graph.
     * After simdification these macro nodes are expanded to basic arithmetic operations.
     */
    @NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
    public abstract static class BinaryMacroNode extends BinaryNode {
        public static final NodeClass<BinaryMacroNode> TYPE = NodeClass.create(BinaryMacroNode.class);

        public BinaryMacroNode(NodeClass<? extends BinaryMacroNode> c, Stamp stamp, ValueNode x, ValueNode y) {
            super(c, stamp, x, y);
        }

        /**
         * Returns an operation that represents this macro node and determines the operation used
         * for the final combination of accumulator vector elements.
         */
        public abstract BinaryOp<?> getInnerBinaryOp();

        /**
         * Returns the expanded computation, which is added to the graph. Does not replace the
         * macro's usages.
         *
         * @param operationLength the SIMD length of the fold operation
         * @param simdLength the SIMD length of the macro node itself
         */
        public abstract ValueNode expand(int operationLength, int simdLength);

        /**
         * May insert necessary post loop actions before the final expansion of the node.
         *
         * @param graph graph this action should be inserted into
         * @param currentVector input of the action
         * @param simdLength current vector length in the given context
         * @return reference to the last node created or {@code currentVector} if no new node was
         *         inserted
         */
        public ValueNode preExpand(StructuredGraph graph, ValueNode currentVector, int simdLength) {
            return currentVector;
        }

        /**
         * Returns a node which can be used to initialize a fold over this operation. The node is
         * added to the given graph.
         */
        public abstract VectorNode initialVector(FoldVectorNode fold);
    }

    public FoldVectorNode(StructuredGraph op, ValueNode initial, ValueNode length, Direction direction, List<? extends ValueNode> vectorInputs, List<? extends ValueNode> scalarInputs) {
        super(TYPE, initial.stamp(NodeView.DEFAULT).unrestricted());
        this.op = op;
        this.initial = initial;
        this.originalInitial = null;
        this.length = length;
        this.direction = direction;
        this.vectorInputs = new NodeInputList<>(this, vectorInputs);
        this.scalarInputs = new NodeInputList<>(this, scalarInputs);
        this.trustedBodyIterations = -1;
    }

    @Override
    public StructuredGraph getOp() {
        return op;
    }

    private void setLength(ValueNode newLength) {
        updateUsages(length, newLength);
        length = newLength;
    }

    @Override
    public ValueNode getLength() {
        return length;
    }

    @Override
    public Direction direction() {
        return direction;
    }

    @Override
    public List<ValueNode> getVectorInputs() {
        return vectorInputs;
    }

    @Override
    public List<ValueNode> getScalarInputs() {
        return scalarInputs;
    }

    public ValueNode getInitial() {
        return initial;
    }

    public ValueNode getOriginalInitial() {
        // When finishLowering() overrides the initial value, this saves its previous value. This is
        // needed for lowering macro nodes.
        return originalInitial;
    }

    public VectorNode getVectorInput(int i) {
        return (VectorNode) vectorInputs.get(i);
    }

    @Override
    public int getMaxVectorLength(VectorArchitecture arch) {
        return arch.getMaxVectorLength(initial.stamp(NodeView.DEFAULT));
    }

    @Override
    public boolean getSupportsAlignment() {
        return false;
    }

    @Override
    public VectorLoopMarkerNode vectorLoopMarker() {
        return vectorLoopMarker;
    }

    @Override
    public void setVectorLoopMarker(VectorLoopMarkerNode vectorLoopMarker) {
        GraalError.guarantee(this.vectorLoopMarker == null, "vectorLoopMarker may only be set once");
        updateUsages(this.vectorLoopMarker, vectorLoopMarker);
        this.vectorLoopMarker = vectorLoopMarker;
    }

    @Override
    public void lower(LowTierContext context) {
        if (vectorInputs.filter(ConcatVectorNode.class).isNotEmpty()) {
            throw GraalError.shouldNotReachHereUnexpectedValue(vectorInputs); // ExcludeFromJacocoGeneratedReport
        }

        ValueNode wordLength = IntegerConvertNode.convertUnsigned(length, StampFactory.forUnsignedInteger(context.getTarget().wordSize * 8), graph(), NodeView.DEFAULT);
        setLength(wordLength);
    }

    @Override
    @SuppressWarnings("try")
    public void simplifyTree(VectorSimplifier simplifier) {
        if (usages().isEmpty()) {
            graph().removeFixed(this);
            return;
        }

        for (int i = 0; i < vectorInputs.count(); i++) {
            VectorNode input = (VectorNode) vectorInputs.get(i);
            VectorNode simplified = simplifier.simplifyLengthHint(input, length);

            if (input != simplified) {
                vectorInputs.set(i, simplified.asNode());
            }
        }

        SubGraphUtil.simplifyInputs(this, false);

        extractMaps(simplifier);

        simplifier.canonicalize(op);

        VectorLoopNode loop = this.vectorLoop();
        if (SubGraphUtil.getResult(this) instanceof AccumulatorNode || length.asJavaConstant() != null && length.asJavaConstant().asLong() == 0) {
            if (loop != null) {
                loop.removeConsumer(this);
            }
            this.replaceAtUsages(initial);
            graph().removeFixed(this);
            return;
        } else if (SubGraphUtil.getResult(this).isConstant()) {
            try (DebugCloseable position = withNodeSourcePosition()) {
                JavaConstant value = SubGraphUtil.getResult(this).asJavaConstant();
                ValueNode valueNode = ConstantNode.forPrimitive(this.stamp(NodeView.DEFAULT), value, graph());

                LogicNode zeroLength = CompareNode.createCompareNode(graph(), CanonicalCondition.EQ, length, ConstantNode.forIntegerStamp(length.stamp(NodeView.DEFAULT), 0, graph()),
                                simplifier.getConstantReflection(), NodeView.DEFAULT);
                ValueNode ret = graph().unique(new ConditionalNode(zeroLength, initial, valueNode));

                if (loop != null) {
                    loop.removeConsumer(this);
                }
                graph().replaceFixed(this, ret);
                return;
            }
        } else if (SubGraphUtil.getAccumulatorAllowNull(op) == null) {
            /*
             * We have optimized away the accumulator in this fold. There is nothing to simplify
             * further, we will generate scalar code for this fold.
             */
            return;
        } else if (!SubGraphUtil.containsValidAccumulator(op)) {
            // We may have optimized away the binary operation on the accumulator (see
            // VectorFoldTest.accumulatorSimplifiedAway2). If all we're left with are some type
            // conversions on the accumulator, we can inline those operations and remove the entire
            // fold computation.
            AccumulatorNode acc = SubGraphUtil.getAccumulator(op);
            Node n = acc;
            while (n.hasExactlyOneUsage() && n.usages().first() instanceof IntegerConvertNode<?>) {
                n = n.usages().first();
            }
            if (n == SubGraphUtil.getResult(this)) {
                // The "fold" only consists of conversions on the accumulator.
                Node inlinedOperation = inlineOp(initial, ValueNode.EMPTY_ARRAY);
                if (loop != null) {
                    loop.removeConsumer(this);
                }
                this.replaceAtUsages(inlinedOperation);
                graph().removeFixed(this);
                return;
            }
        }

        SubGraphUtil.removeUnusedInputs(this);

        if (Options.VectorizeHashes.getValue(graph().getOptions())) {
            if (vectorInputs.filter(ConcatVectorNode.class).isEmpty()) {
                // Try to recognize a hash-shaped computation in the inner graph. We must do this as
                // a simplification rather than at the point of creation of the vector fold node:
                // Other simplifications (especially extraction of computations to separate map
                // nodes) must run first to allow us to recognize more instances of the hash
                // pattern.
                boolean foundHash = VectorHashStepNode.maybeTransformToHash(this);
                if (foundHash && isPartOfALoop()) {
                    vectorLoop().recomputeFoldAssociativity();
                }
            }
        }

        if (VectorSubtractStepNode.replaceSubNodes(this) && isPartOfALoop()) {
            vectorLoop().recomputeFoldAssociativity();
        }

        AccumulatorNode accumulator = SubGraphUtil.getAccumulator(op);
        ValueNode returnValue = SubGraphUtil.getResult(this);
        if (returnValue instanceof IntegerConvertNode<?> && accumulator.hasExactlyOneUsage() && accumulator.singleUsage() instanceof NarrowNode) {
            NarrowNode narrow = (NarrowNode) accumulator.singleUsage();
            IntegerConvertNode<?> returnConvert = (IntegerConvertNode<?>) returnValue;
            if (narrow.getInputBits() == returnConvert.getResultBits() && narrow.getResultBits() == returnConvert.getInputBits()) {
                narrowFold(narrow, accumulator, returnConvert);
            }
        }

        if (vectorLoop() == null || vectorLoop().mayRemoveConcatsFromLoop()) {
            try (DebugCloseable position = withNodeSourcePosition()) {
                splitOnConcatInput(simplifier);
            }
        }
    }

    /**
     * If this is a loop like (must be checked by the caller):
     *
     * <pre>
     * accumulator = initial;
     * loop {
     *     accumulator = Extend(Narrow(accumulator) + someNarrowValue);
     * }
     * someUsage = accumulator;
     * </pre>
     *
     * We can eliminate the redundant conversions. Propagate the narrow to the initial value and the
     * extend to the outside usage of the result:
     *
     * <pre>
     * accumulator = Narrow(initial);
     * loop {
     *     accumulator = accumulator + someNarrowValue;
     * }
     * someUsage = Extend(accumulator);
     * </pre>
     */
    private void narrowFold(NarrowNode narrow, AccumulatorNode accumulator, IntegerConvertNode<?> returnConvert) {
        Stamp narrowStamp = narrow.stamp(NodeView.DEFAULT);
        ValueNode narrowInitial = graph().addOrUnique(IntegerConvertNode.convert(getInitial(), narrowStamp.unrestricted(), NodeView.DEFAULT));
        this.setInitial(narrowInitial);
        ValueNode narrowAccumulator = op.unique(new AccumulatorNode(narrowStamp));
        narrow.replaceAndDelete(narrowAccumulator);
        accumulator.safeDelete();
        ValueNode narrowResult = returnConvert.getValue();
        GraalError.guarantee(returnConvert.hasExactlyOneUsage() && returnConvert.singleUsage() instanceof ReturnNode,
                        "the fold result conversion should only be used by the subgraph return: %s", returnConvert);
        ReturnNode oldReturn = (ReturnNode) returnConvert.singleUsage();
        ReturnNode newReturn = op.add(new ReturnNode(narrowResult, oldReturn.getMemoryMap()));
        oldReturn.replaceAndDelete(newReturn);
        returnConvert.safeDelete();

        /*
         * Change this fold's stamp in-place and add an extend node to its usages. This uses a
         * temporary placeholder for the extend to avoid problems with trying to replace a node by a
         * node with a different stamp.
         */
        OpaqueNode extendPlaceholder = graph().addWithoutUnique(new OpaqueValueNode(this));
        this.replaceAtUsages(extendPlaceholder, InputType.Value);
        Stamp wideStamp = this.stamp(NodeView.DEFAULT);
        this.setStamp(narrowResult.stamp(NodeView.DEFAULT));
        boolean zeroExtend = (returnConvert instanceof ZeroExtendNode);
        ValueNode outerExtend = graph().addOrUnique(IntegerConvertNode.convert(this, wideStamp, zeroExtend, NodeView.DEFAULT));
        extendPlaceholder.replaceAndDelete(outerExtend);
    }

    private void setInitial(ValueNode initial) {
        updateUsages(this.initial, initial);
        this.initial = initial;
    }

    private void setOriginalInitial(ValueNode originalInitial) {
        updateUsages(this.originalInitial, originalInitial);
        this.originalInitial = originalInitial;
    }

    private void extractMaps(VectorSimplifier simplifier) {
        NodeBitMap notExtractable = new NodeBitMap(op);
        NodeBitMap border = new NodeBitMap(op);
        Queue<Node> workQueue = new ArrayDeque<>();

        AccumulatorNode acc = op.getNodes().filter(AccumulatorNode.class).first();
        if (acc != null) {
            workQueue.add(acc);
        }

        while (!workQueue.isEmpty()) {
            Node node = workQueue.remove();
            if (notExtractable.contains(node)) {
                continue;
            }

            notExtractable.mark(node);
            border.clear(node);

            for (Node usage : node.usages()) {
                workQueue.add(usage);
            }
            for (Node input : node.inputs()) {
                if (!notExtractable.contains(input)) {
                    if (node instanceof ConditionalNode && ((ConditionalNode) node).condition() == input) {
                        // Don't allow conditions to end up as border nodes, this would lead to
                        // illegal graphs.
                    } else {
                        border.mark(input);
                    }
                    for (Node usage : input.usages()) {
                        if (usage != node && !notExtractable.contains(usage)) {
                            workQueue.add(usage);
                        }
                    }
                }
            }
        }

        for (Node node : border) {
            ValueNode borderNode = (ValueNode) node;
            if (borderNode instanceof ParameterNode param && !SubGraphUtil.isScalarInput(param)) {
                /*
                 * Extracting a map here would just give a subgraph that returns its only parameter
                 * immediately. There is nothing to simplify in that case.
                 */
                continue;
            }
            if (borderNode instanceof ConvertNode) {
                ConvertNode convert = (ConvertNode) borderNode;
                if (PrimitiveStamp.getBits(convert.getValue().stamp(NodeView.DEFAULT)) < PrimitiveStamp.getBits(convert.asNode().stamp(NodeView.DEFAULT))) {
                    // keep ConvertNode in the fold, to increase the supported vector length
                    borderNode = convert.getValue();
                }
            }

            VectorStamp retStamp = new VectorStamp(borderNode.stamp(NodeView.DEFAULT));
            MapVectorNode map = graph().addWithoutUnique(new MapVectorNode(retStamp, SubGraphUtil.createSubGraph(graph())));
            Node copy = SubGraphUtil.copyTransitive(this, borderNode, map);

            ReturnNode ret = map.getOp().add(new ReturnNode((ValueNode) copy));
            map.getOp().addAfterFixed(map.getOp().start(), ret);

            VectorNode simplified = simplifier.simplify(map);

            ParameterNode newInput = SubGraphUtil.findOrAddVectorInput(this, simplified);
            if (newInput != borderNode) {
                borderNode.replaceAtUsages(newInput);
            }
        }
    }

    @Override
    public void setLoopAnchor(AnchoringNode anchor) {
        loopAnchor = anchor;
    }

    @Override
    public AnchoringNode getLoopAnchor() {
        return loopAnchor;
    }

    public boolean isAssociativeAndCommutative() {
        AccumulatorNode accumulator = SubGraphUtil.getAccumulatorAllowNull(op);
        if (accumulator == null) {
            return false;
        }
        return isAssociativeAndCommutative(accumulator);
    }

    private boolean isAssociativeAndCommutative(AccumulatorNode accumulator) {
        if (!hasExactlyOneNonConditionalUsage(accumulator)) {
            return false;
        }

        Node previous = accumulator;
        Node node = onlyNonConditionalUsage(accumulator);
        BinaryOp<?> arithmeticOp = null;
        boolean seenHashStep = false;
        boolean seenConditional = false;
        boolean isFirstNode = true;
        while (node.hasExactlyOneUsage()) {
            if (node instanceof BinaryArithmeticNode) {
                BinaryArithmeticNode<?> binary = (BinaryArithmeticNode<?>) node;
                if (!binary.getArithmeticOp().isAssociative() || !binary.getArithmeticOp().isCommutative() || getCommonExpansionOp(arithmeticOp, binary.getArithmeticOp()) == null) {
                    // this is potentially too strict, see GR-5867
                    return false;
                }
                if (CommutativeFoldVectorIterator.getNeutralElement(binary) == null) {
                    return false;
                }
                arithmeticOp = binary.getArithmeticOp();
            } else if ((isFirstNode || isLastNode(node)) && firstAndLastSubGraphNodeCancelEachOther()) {
                // nothing to do
            } else if (node instanceof ConditionalNode conditional) {
                if (seenHashStep) {
                    // can't mix hashing with conditional
                    return false;
                }
                ValueNode needsToBeAcc = conditional.trueValue() == previous ? conditional.falseValue() : conditional.trueValue();
                if (needsToBeAcc != accumulator) {
                    // all inputs of a conditional along the accumulator path need to be derived
                    // from the accumulator
                    return false;
                }
                seenConditional = true;
            } else if (node instanceof BinaryMacroNode) {
                if (node instanceof VectorHashStepNode) {
                    if (seenConditional || arithmeticOp != null) {
                        // can't mix hashing with conditional
                        // hashing must be above any arithmetic
                        return false;
                    }
                    seenHashStep = true;
                } else if (node instanceof VectorSubtractStepNode subtractStep) {
                    /**
                     * This condition is checked in {@link VectorSubtractStepNode#replaceSubNodes}.
                     * This may be too strict (see GR-46024).
                     */
                    GraalError.guarantee(subtractStep.getX() == previous, "the accumulator path must never traverse a Y-edge of a VectorSubtractStepNode");
                } else {
                    throw GraalError.shouldNotReachHere("BinaryMacroNodes need special treatment in FoldVectorNode#isAssociativeAndCommutative");
                }

                // macroInnerOp is allowed to be neither associative nor commutative
                BinaryOp<?> macroInnerOp = ((BinaryMacroNode) node).getInnerBinaryOp();
                if (getCommonExpansionOp(arithmeticOp, macroInnerOp) == null) {
                    return false;
                }
                arithmeticOp = macroInnerOp;
            } else {
                return false;
            }

            previous = node;
            node = node.usages().first();
            isFirstNode = false;
        }

        return node instanceof ReturnNode;
    }

    /**
     * Calculates the common operation used for the final combination of accumulator elements or
     * {@code null} if there is none.
     */
    private static BinaryOp<?> getCommonExpansionOp(BinaryOp<?> op1, BinaryOp<?> op2) {
        BinaryOp<?> exp1 = getExpansionOp(op1);
        BinaryOp<?> exp2 = getExpansionOp(op2);
        if (exp1 == null) {
            return exp2;
        } else if (exp2 == null || exp1.equals(exp2)) {
            return exp1;
        } else {
            return null;
        }
    }

    /**
     * Returns the operation used for the final combination of accumulator elements for this
     * operation. Subtractions in folds are expanded with addition (see
     * {@link VectorSubtractStepNode}).
     */
    private static BinaryOp<?> getExpansionOp(BinaryOp<?> op) {
        if (IntegerStamp.OPS.getSub().equals(op)) {
            return IntegerStamp.OPS.getAdd();
        } else {
            return op;
        }
    }

    private static boolean hasExactlyOneNonConditionalUsage(Node node) {
        return onlyNonConditionalUsage(node) != null;
    }

    public static Node onlyNonConditionalUsage(Node node) {
        FilteredNodeIterable<Node> nonConditionalUsages = node.usages().filter(usage -> !(usage instanceof ConditionalNode));
        if (nonConditionalUsages.count() == 1) {
            return nonConditionalUsages.first();
        } else {
            return null;
        }
    }

    private boolean containingGroupIsAssociativeAndCommutative() {
        if (!isPartOfALoop()) {
            return true;
        } else {
            return vectorLoop().allFoldsAreAssociativeAndCommutative();
        }
    }

    private boolean canCreateCommutativeIterator() {
        return isAssociativeAndCommutative() && containingGroupIsAssociativeAndCommutative();
    }

    private boolean isLastNode(Node node) {
        return node == SubGraphUtil.getResult(this);
    }

    // this is better than nothing but we should try to remove these nodes completely from the
    // inner-most vectorized loop (see GR-5853).
    public boolean firstAndLastSubGraphNodeCancelEachOther() {
        AccumulatorNode accumulator = SubGraphUtil.getAccumulator(this);
        assert hasExactlyOneNonConditionalUsage(accumulator) : accumulator;
        Node firstNode = onlyNonConditionalUsage(accumulator);
        ValueNode lastNode = SubGraphUtil.getResult(this);
        if (firstNode instanceof IntegerConvertNode && lastNode instanceof IntegerConvertNode) {
            IntegerConvertNode<?> firstConversion = (IntegerConvertNode<?>) firstNode;
            IntegerConvertNode<?> lastConversion = (IntegerConvertNode<?>) lastNode;
            return firstConversion.getInputBits() == lastConversion.getResultBits() && firstConversion.getResultBits() == lastConversion.getInputBits();
        }
        return false;
    }

    @Override
    public void finishLowering(LowTierContext context) {
        if (isAssociativeAndCommutative() && onlyNonConditionalUsage(SubGraphUtil.getAccumulator(this)) instanceof BinaryMacroNode) {
            // This is a vectorized fold containing a complex operation which handles the initial
            // value differently. After lowering we can now figure out whether an actual
            // SIMD loop will be generated. If yes, then we need to adjust the initial value.
            int maxStepLength = 0;
            VectorConsumerProxyNode consumerProxy;
            if (isPartOfALoop()) {
                consumerProxy = vectorLoop().usages().filter(VectorConsumerProxyNode.class).first();
            } else {
                consumerProxy = usages().filter(VectorConsumerProxyNode.class).first();
            }
            for (Node usage : consumerProxy.usages()) {
                if (usage instanceof PartialVectorConsumerNode) {
                    maxStepLength = Math.max(maxStepLength, ((PartialVectorConsumerNode) usage).getStepLength());
                }
            }

            if (maxStepLength > 1) {
                // We will generate a commutative fold iterator that includes the original initial
                // value for this fold in its starting vector for the vector loop. Don't add the
                // initial value at the end as we would do it usually. Only if the vector length is
                // zero do we need to use the initial value as the final value for the fold.
                ConstantNode lengthZeroConst = ConstantNode.forIntegerStamp(getLength().stamp(NodeView.DEFAULT), 0);
                LogicNode lengthIsZero = CompareNode.createCompareNode(CanonicalCondition.EQ, getLength(), lengthZeroConst, context.getConstantReflection(), NodeView.DEFAULT);
                ConstantNode zeroConst = ConstantNode.forIntegerStamp(stamp(NodeView.DEFAULT), 0);
                ValueNode initialConditional = graph().addOrUniqueWithInputs(ConditionalNode.create(lengthIsZero, getInitial(), zeroConst, NodeView.DEFAULT));
                assert originalInitial == null : "can only override the initial value once";
                setOriginalInitial(initial);
                setInitial(initialConditional);
            }
        }
    }

    @Override
    public VectorConsumerIterator createInitialIterator(TargetDescription target) {
        ValueNode index = ConstantNode.forIntegerKind(target.wordJavaKind, 0, graph());
        AnchoringNode anchor = getAnchor();

        VectorIterator[] values = new VectorIterator[vectorInputs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = VectorInitialIteratorNode.createInitialIterator(getVectorInput(i), anchor, target);
        }

        if (canCreateCommutativeIterator()) {
            return new CommutativeFoldVectorIterator(index, initial, values, 1, null);
        } else {
            return new FoldVectorIterator(index, initial, values);
        }
    }

    @Override
    public VectorConsumerIterator createPhiIterator(int minInputStepLength, int maxInputStepLength, PhiNode phi, TargetDescription target) {
        AbstractMergeNode merge = phi.merge();
        ValueNode index = graph().addOrUnique(new ValuePhiNode(StampFactory.forKind(target.wordJavaKind), merge));
        Stamp foldStamp = initial.stamp(NodeView.DEFAULT).unrestricted();
        ValueNode current = graph().addWithoutUnique(new ValuePhiNode(foldStamp, merge));
        AnchoringNode anchor = getAnchor();

        VectorIterator[] values = new VectorIterator[vectorInputs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = VectorInitialIteratorNode.createPhiIterator(merge, getVectorInput(i), anchor, target);
        }

        if (canCreateCommutativeIterator()) {
            VectorPhi currentVector = null;
            assert minInputStepLength >= 0 && minInputStepLength <= maxInputStepLength : "stepLength = [" + minInputStepLength + "," + maxInputStepLength + "]";
            // we only create a VectorPhi if the step length of all phi inputs matches. if we have a
            // mismatch, we fold each vector into a scalar instead.
            if (minInputStepLength > 1 && minInputStepLength == maxInputStepLength) {
                currentVector = graph().addWithoutUnique(new VectorPhi(new VectorStamp(foldStamp), merge));
            }
            return new CommutativeFoldVectorIterator(index, current, values, maxInputStepLength, currentVector);
        } else {
            return new FoldVectorIterator(index, current, values);
        }
    }

    @Override
    public ValueNode simdify(VectorArchitecture arch, ValueNode... simdInputs) {
        ValueNode[] values = new ValueNode[simdInputs.length];
        ValueNode acc = initial;

        assert NumUtil.isUnsignedNbit(31, length.asJavaConstant().asLong()) : length;
        int constLength = length.asJavaConstant().asInt();
        if (constLength == 1) {
            acc = inlineOp(acc, simdInputs);
        } else {
            /*
             * Horizontally reduce the vector to a scalar. Compare
             * SimdArithmeticReducePattern.reduce.
             */
            BinaryNode binary = checkInlineableBinaryArithmetic();
            if (binary != null) {
                GraalError.guarantee(simdInputs.length == 1, "expected exactly one input");
                ValueNode currentVector = simdInputs[0];
                int currentLength = constLength;
                GraalError.guarantee(CodeUtil.isPowerOf2(currentLength), "expected power of 2");
                /**
                 * Build a binary cascade like:
                 *
                 * <pre>
                 *   SimdCut(0, length/2)  SimdCut(length/2, length/2)
                 *                      \  /
                 *                       op
                 *                      /  \
                 *   SimdCut(0, length/4)  SimdCut(length/4, length/4)
                 *                      \  /
                 *                       op
                 *                       ...
                 * </pre>
                 *
                 * as long as the half of the current vector length is a legal vector length for the
                 * binary operation.
                 */
                BinaryOp<?> rootOperation;
                if (binary instanceof BinaryArithmeticNode<?>) {
                    rootOperation = ((BinaryArithmeticNode<?>) binary).getArithmeticOp();
                    GraalError.guarantee(rootOperation.isAssociative() && rootOperation.isCommutative(), "need associative-commutative op");
                } else if (binary instanceof BinaryMacroNode) {
                    // this operation does not have to be associative and commutative
                    rootOperation = ((BinaryMacroNode) binary).getInnerBinaryOp();
                } else {
                    throw GraalError.shouldNotReachHere("unexpected binary node"); // ExcludeFromJacocoGeneratedReport
                }

                if (binary instanceof BinaryMacroNode) {
                    // in the case of binary macro node we need an additional step before
                    // continuing with the binary cascade
                    currentVector = ((BinaryMacroNode) binary).preExpand(graph(), currentVector, currentLength);
                }

                Stamp elementStamp = ((SimdStamp) currentVector.stamp(NodeView.DEFAULT)).getComponent(0);
                while (arch.getSupportedVectorArithmeticLength(elementStamp, currentLength / 2, rootOperation) == currentLength / 2) {
                    ValueNode left = graph().unique(new SimdCutNode(currentVector, 0, currentLength / 2));
                    ValueNode right = graph().unique(new SimdCutNode(currentVector, currentLength / 2, currentLength / 2));
                    currentVector = inlineOp(left, right);
                    if (currentVector instanceof BinaryMacroNode) {
                        ValueNode expansion = graph().addOrUniqueWithInputs(((BinaryMacroNode) currentVector).expand(currentLength, 1));
                        currentVector.replaceAndDelete(expansion);
                        currentVector = expansion;
                    }
                    currentVector.inferStamp();
                    currentLength /= 2;
                }
                acc = currentVector;

                /* Once we've reached an unsupported length, finish with a linear cascade. */
                if (currentLength > 1) {
                    acc = graph().unique(new SimdCutNode(acc, 0, 1));
                    for (int iteration = 1; iteration < currentLength; iteration++) {
                        ValueNode value = graph().unique(new SimdCutNode(currentVector, iteration, 1));
                        acc = inlineOp(acc, value);
                    }
                }
                /*
                 * Finally add the fold's initial value (unless it's a macro operation, in which
                 * case it's already part of the vector). The order of operations doesn't matter
                 * because we are using an associative-commutative operation.
                 */
                if (!(binary instanceof BinaryMacroNode)) {
                    acc = inlineOp(acc, initial);
                }
            } else {
                // in the case of a binary macro node we need an additional step before continuing
                // with the linear cascade
                ValueNode result = SubGraphUtil.getResult(this);
                if (result instanceof BinaryMacroNode) {
                    GraalError.guarantee(simdInputs.length == 1, "expected exactly one input");
                    simdInputs[0] = ((BinaryMacroNode) result).preExpand(graph(), simdInputs[0], constLength);
                }
                /**
                 * Not a simple inlineable binary operation. Build a linear cascade like:
                 *
                 * <pre>
                 *   SimdCut(0, 1)  SimdCut(1, 1)
                 *               \  /
                 *                op   SimdCut(2, 1)
                 *                  \  /
                 *                   op
                 *                   ...    SimdCut(length-1, 1)
                 *                      \  /
                 *                       op
                 * </pre>
                 */
                for (int iteration = 0; iteration < constLength; iteration++) {
                    for (int i = 0; i < values.length; i++) {
                        values[i] = graph().unique(new SimdCutNode(simdInputs[i], iteration, 1));
                    }
                    acc = inlineOp(acc, values);
                }
            }
        }

        graph().replaceFixed(this, acc);
        return acc;
    }

    /**
     * Check if this fold's operation is exactly of the following shape (modulo commuting of the
     * binary's inputs).
     *
     * <pre>
     *         Param(0)    Accumulator
     *               \      /
     *           BinaryArithmetic (or BinaryMacro)
     *                   |
     *                Return
     * </pre>
     *
     * @return the binary node, if the pattern matches; {@code null} otherwise
     */
    private BinaryNode checkInlineableBinaryArithmetic() {
        /**
         * If a hash step is combined with binary arithmetic, this graph will only contain the hash
         * step due to it being the first node below the accumulator and
         * {@link CommutativeFoldVectorIterator#selectNodesForPartialFold(FoldVectorNode)} only
         * transferring the first binary arithmetic or macro node to this partial fold. This will
         * not affect the calculation though as any additional binary arithmetic is checked to be
         * the same as {@link VectorHashStepNode.secondaryOp} in
         * {@link FoldVectorNode#isAssociativeAndCommutative(AccumulatorNode)}
         */
        ValueNode result = SubGraphUtil.getResult(this);
        if (!(result instanceof BinaryArithmeticNode || result instanceof BinaryMacroNode)) {
            return null;
        }
        BinaryNode binary = (BinaryNode) result;
        ValueNode x = binary.getX();
        ValueNode y = binary.getY();
        if (x instanceof AccumulatorNode) {
            x = binary.getY();
            y = binary.getX();
        }
        if (x instanceof ParameterNode && y instanceof AccumulatorNode) {
            return binary;
        } else {
            return null;
        }
    }

    private ValueNode inlineOp(final ValueNode acc, ValueNode... values) {

        NodePredicate toInline = new NodePredicate() {

            @Override
            public boolean apply(Node n) {
                return n instanceof ArithmeticOperation || n instanceof ConstantNode || n instanceof ConditionalNode || n instanceof LogicNode || n instanceof BinaryMacroNode;
            }
        };

        NodePredicate noInline = new NodePredicate() {

            @Override
            public boolean apply(Node n) {
                return n instanceof StartNode || n instanceof ReturnNode || n instanceof AccumulatorNode || n instanceof ParameterNode;
            }
        };

        NodeIterable<Node> nodes = op.getNodes().filter(toInline);

        if (nodes.count() + op.getNodes().filter(noInline).count() != op.getNodes().count()) {
            // some nodes in the subgraph are not covered by any of the two filters
            StringBuilder unhandledNodes = new StringBuilder("");
            for (Node n : op.getNodes()) {
                if (!toInline.apply(n) && !noInline.apply(n)) {
                    if (!unhandledNodes.isEmpty()) {
                        unhandledNodes.append(", ");
                    }
                    unhandledNodes.append(n.toString());
                }
            }
            GraalError.shouldNotReachHere(String.format("Node(s) not handled during inling of FoldVectorNode SubGraph: %s", unhandledNodes.toString()));
        }

        UnmodifiableEconomicMap<Node, Node> duplicates = graph().addDuplicates(nodes, op, nodes.count(), new DuplicationReplacement() {

            @Override
            public Node replacement(Node original) {
                if (original instanceof ParameterNode param) {
                    if (SubGraphUtil.isScalarInput(param)) {
                        /* The index is represented as rawScalarIndex + SCALAR_OFFSET. */
                        int scalarIndex = param.index() - SubGraphUtil.SCALAR_OFFSET;
                        return scalarInputs.get(scalarIndex);
                    } else {
                        return values[param.index()];
                    }
                } else if (original instanceof AccumulatorNode) {
                    return acc;
                } else {
                    return original;
                }
            }
        });

        return (ValueNode) duplicates.get(SubGraphUtil.getResult(this));
    }

    @Override
    protected void afterClone(Node node) {
        FoldVectorNode other = (FoldVectorNode) node;
        op = (StructuredGraph) other.op.copy(getDebug());
        super.afterClone(other);
    }

    /**
     * This method handles the case that a {@link FoldVectorNode} has at least one
     * {@link ConcatVectorNode} as one of its inputs. In such a case, we need to do the following
     * transformation:
     *
     * <p>
     * before:
     *
     * <pre>
     * originalFold = FoldVectorNode(initialValue, originalFoldLength, ConcatVectorNode(A, B))
     * </pre>
     *
     * afterwards:
     *
     * <pre>
     * remainingLength = originalFoldLength;
     * foldALength = min(A.length, remainingLength);
     * foldA = FoldVectorNode(initialValue, foldALength, A);
     *
     * remainingLength = remainingLength - foldALength
     * foldBLength = min(B.length, remainingLength);
     * foldB = FoldVectorNode(foldA, foldBLength, B);
     * </pre>
     *
     * If a {@link FoldVectorNode} has more than one {@link ConcatVectorNode} inputs, we need to
     * generate all 2 ^ countInputs(ConcatVectorNode) possible combinations. E.g.:
     *
     * <p>
     * before:
     *
     * <pre>
     * originalFold = FoldVectorNode(initialValue, originalFoldLength, ConcatVectorNode(A, B), ConcatVectorNode(C, D));
     * </pre>
     *
     * afterwards:
     *
     * <pre>
     * remainingLength = originalFoldLength;
     * foldACLength = min(A.length, C.length, remainingLength);
     * foldAC = FoldVectorNode(initialValue, foldACLength, A, C);
     *
     * remainingLength = remainingLength - foldACLength;
     * foldADLength = min(A.length - foldACLength, D.length, remainingLength);
     * foldAD = FoldVectorNode(foldAC, foldADLength, A >> foldACLength, D);
     *
     * remainingLength = remainingLength - foldADLength;
     * foldBCLength = min(B.length, C.length - foldACLength, remainingLength);
     * foldBC = FoldVectorNode(foldAD, foldBCLength, B, C >> foldACLength);
     *
     * remainingLength = remainingLength - foldBCLength;
     * foldBDLength = min(B.length - foldBCLength, D.length - foldADLength, remainingLength);
     * foldBD = FoldVectorNode(foldBC, foldBDLength, B >> foldBCLength, D >> foldADLength);
     * </pre>
     */
    private void splitOnConcatInput(VectorSimplifier simplifier) {
        SplitConcatUtil.CombinationTable combinationTable = new SplitConcatUtil.CombinationTable();
        for (ConcatVectorNode concat : vectorInputs.filter(ConcatVectorNode.class)) {
            combinationTable.add(concat);
        }

        // create fold operations for all possible input combinations
        ArrayList<ArrayList<SplitConcatUtil.VectorEntry>> combinations = combinationTable.rows;
        if (!combinations.isEmpty()) {
            List<ValueNode> currentVectorInputs = vectorInputs.snapshot();
            ArrayList<FoldVectorNode> createdFoldNodes = new ArrayList<>();
            ConstantReflectionProvider constantReflection = simplifier.getConstantReflection();
            ValueNode remainingLength = this.length;
            ValueNode currentInitialValue = initial;
            for (int i = 0; i < combinations.size(); i++) {
                ArrayList<SplitConcatUtil.VectorEntry> concatInputs = combinations.get(i);
                List<ValueNode> foldVectorInputs = SplitConcatUtil.getVectorInputsForNextOperation(currentVectorInputs, concatInputs);
                ValueNode foldLength = SplitConcatUtil.minLength(graph(), constantReflection, concatInputs, remainingLength);
                FoldVectorNode foldVectorNode = graph().add(new FoldVectorNode((StructuredGraph) op.copy(getDebug()), currentInitialValue, foldLength, direction(), foldVectorInputs, scalarInputs));
                graph().addBeforeFixed(this, foldVectorNode);
                createdFoldNodes.add(foldVectorNode);

                SplitConcatUtil.shiftInputs(graph(), constantReflection, currentVectorInputs, concatInputs, foldLength);
                remainingLength = BinaryArithmeticNode.sub(graph(), remainingLength, foldLength, NodeView.DEFAULT);
                currentInitialValue = foldVectorNode;
            }

            // remove the existing fold operation
            assert currentInitialValue != initial && currentInitialValue instanceof FoldVectorNode : currentInitialValue + " " + initial;
            graph().replaceFixed(this, currentInitialValue);

            // trigger a recursive simplification of the newly generated fold nodes
            for (FoldVectorNode createdFoldNode : createdFoldNodes) {
                createdFoldNode.simplifyTree(simplifier);
            }
        }
    }

    @Override
    public boolean allowUnrolling() {
        // Do not allow unrolling of the consumer or tail loop if this a commutative operation
        // containing a conditional. In that case, we would run into the
        // CommutativeFoldVectorIterator.createInitialMapSubgraph path, which cannot deal with
        // conditionals.
        // Also, don't unroll macro operations, which have complex expansion requirements.
        if (isAssociativeAndCommutative()) {
            for (Node n : op.getNodes()) {
                if (n instanceof ConditionalNode || n instanceof BinaryMacroNode) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public double trustedBodyIterations() {
        return trustedBodyIterations;
    }

    @Override
    public void setTrustedBodyIterations(double trustedBodyIterations) {
        this.trustedBodyIterations = trustedBodyIterations;
    }
}
