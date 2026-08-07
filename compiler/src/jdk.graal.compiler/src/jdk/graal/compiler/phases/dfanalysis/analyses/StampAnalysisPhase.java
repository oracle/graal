/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.dfanalysis.analyses;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.core.common.calc.FloatConvertCategory;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.util.IntList;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.DynamicPiNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.dfanalysis.AnalysisDomainDefinition;
import jdk.graal.compiler.phases.dfanalysis.DFAMap;
import jdk.graal.compiler.phases.dfanalysis.DFAnalysis;
import jdk.graal.compiler.replacements.nodes.arithmetic.BinaryIntegerExactArithmeticSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactArithmeticSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerMulExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerMulExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerNegExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerNegExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactSplitNode;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.TriState;

public final class StampAnalysisPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    public static final class Options {
        // @formatter:off
        @Option(help = "Sets the number of pessimistic evaluations of loop PHIs before widening kicks in", type = OptionType.Debug)
        public static final OptionKey<Integer> DFSA_WidenAfter = new OptionKey<>(2);

        @Option(help = "Includes types in the analysis for object stamps", type = OptionType.Debug)
        public static final OptionKey<Boolean> DFSA_DoTypes = new OptionKey<>(true);
        // @formatter:on
    }

    public StampAnalysisPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunBefore(this, GraphState.StageFlag.VALUE_PROXY_REMOVAL, graphState);
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        return graph.hasLoops() || DFAnalysis.Options.DFA_EvalAll.getValue(graph.getOptions());
    }

    @Override
    @SuppressWarnings({"try", "unused"})
    protected void run(StructuredGraph graph, CoreProviders context) {
        try (DebugContext.Scope scope = graph.getDebug().scope("RangeApplication")) {

            if (DFAnalysis.Options.DFA_PreCanonicalize.getValue(graph.getOptions())) {
                CanonicalizerPhase.create().apply(graph, context);
            }

            FullStampsStamps domain = new FullStampsStamps(Options.DFSA_DoTypes.getValue(graph.getOptions()));
            // run analysis
            DFAnalysis<Stamp> analysis = DFAnalysis.create(Stamp.class, graph, new FullStampsStamps(Options.DFSA_DoTypes.getValue(graph.getOptions())), nd -> switch (nd) {
                case ConstantNode ignored -> true;
                case LogicConstantNode ignored -> true;
                case LogicNode ln -> {
                    for (Node input : ln.inputs()) {
                        if (!(input instanceof ValueNode vin) || !domain.isOfInterest(vin)) {
                            // we are not interested in logic nodes the inputs of which we do not
                            // understand
                            yield false;
                        }
                    }
                    yield true;
                }
                case PiNode ignored -> true;
                case IntegerSwitchNode ignored -> true;
                case FloatConvertNode fc -> fc.getFloatConvert().getCategory() == FloatConvertCategory.IntegerToFloatingPoint;
                case ReadNode rn -> domain.isOfInterest(rn) && !rn.stamp(NodeView.DEFAULT).isUnrestricted();
                case AllocatedObjectNode aon -> !aon.stamp(NodeView.DEFAULT).isUnrestricted();
                default -> false;
            });
            DFAMap<Stamp> map = analysis.run();

            // replace constants in graph
            MapCursor<ValueNode, Stamp> stampCursor = map.getEntries();
            int constantsFound = 0;
            int branchesEliminated = 0;
            int typeMadeDifference = 0;
            while (stampCursor.advance()) {
                ValueNode node = stampCursor.getKey();
                Stamp stamp = stampCursor.getValue();
                if (stamp.isConstant() && !(node instanceof ConstantNode || node instanceof LogicConstantNode)) {
                    if (!Options.DFSA_DoTypes.getValue(graph.getOptions()) && stamp instanceof AbstractObjectStamp constantObjectStamp) {
                        if (node.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp curStamp) {
                            /*
                             * Type information is not tracked by this analysis (see
                             * ConstantNormalizedStamps#normalize). To generate proper constants, we
                             * need to restore the type information. For that we just read the type
                             * information from the stamp of the node we are trying to replace with
                             * a constant.
                             */
                            stamp = constantObjectStamp.asType(curStamp.type(), curStamp.isExactType(), curStamp.isAlwaysArray());
                        } else {
                            throw GraalError.shouldNotReachHere("trying to insert AbstractObjectStamp for node that previously had no AbstractObjectStamp");
                        }
                    }
                    constantsFound++;

                    String tNodeString = graph.getDebug().isLogEnabled(DebugContext.VERY_DETAILED_LEVEL) ? node.toString() : "";
                    if (stamp instanceof LogicValueStamp logicStamp) {
                        graph.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "replacing %s with constant %s", node, stateForLogicStamp(logicStamp));
                        LogicConstantNode c = LogicConstantNode.forBoolean(stateForLogicStamp(logicStamp).toBoolean(), graph);
                        node.replaceAtUsagesAndDelete(c);
                        graph.getOptimizationLog().report(StampAnalysisPhase.class, "FullStampsConstantReplacement", node);
                    } else {
                        GraalError.guarantee(stamp.getClass().equals(node.stamp(NodeView.DEFAULT).getClass()), "Mismatch in stamp types (in graph: %s, analysis: %s)",
                                        node.stamp(NodeView.DEFAULT).getClass().getSimpleName(), stamp.getClass().getSimpleName());
                        if (stamp.asConstant() instanceof JavaConstant javaConstant) {
                            ConstantNode c = graph.addOrUnique(new ConstantNode(javaConstant, stamp));
                            GraalError.guarantee(c.stamp(NodeView.DEFAULT).isCompatible(node.stamp(NodeView.DEFAULT)),
                                            "Incompatible stamps at constant replacement (replacing node %s with stamp %s by constant with stamp %s)",
                                            node, node.stamp(NodeView.DEFAULT), c.stamp(NodeView.DEFAULT));
                            node.replaceAtUsages(c);
                            graph.getOptimizationLog().report(StampAnalysisPhase.class, "FullStampsConstantReplacement", node);
                        }
                    }
                }
            }

            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "After constant replacement");

            // cleanup stuff that the analysis left behind
            analysis.cleanup();

            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "After LSCCP cleanup");
        }
    }

    private static final class FullStampsStamps implements AnalysisDomainDefinition<Stamp> {
        private final boolean doTypes;

        public FullStampsStamps(boolean doTypes) {
            this.doTypes = doTypes;
        }

        @Override
        public boolean isUnevaluated(Stamp elem) {
            return elem.isEmpty();
        }

        @Override
        public boolean isUnrestricted(Stamp elem) {
            return elem.isUnrestricted();
        }

        @Override
        public Stamp merge(Stamp x, Stamp y) {
            assert x.getClass().equals(y.getClass()) : "incompatible stamps";
            return normalize(x.meet(y));
        }

        @Override
        public Stamp strengthen(Stamp x, Stamp y) {
            assert x.getClass().equals(y.getClass()) : "incompatible stamps";
            return normalize(x.join(y));
        }

        @Override
        public Stamp unevaluated(ValueNode node) {
            return switch (node) {
                case LogicNode ignored -> LogicValueStamp.EMPTY;
                case IntegerSwitchNode iSwitch -> unevaluated(iSwitch.switchValue().stamp(NodeView.DEFAULT));
                default -> unevaluated(node.stamp(NodeView.DEFAULT));
            };
        }

        @Override
        public Stamp unevaluated(Stamp elem) {
            return switch (elem) {
                case LogicValueStamp lStamp -> lStamp.empty();
                case PrimitiveStamp pStamp -> pStamp.empty();
                case AbstractObjectStamp oStamp -> oStamp.empty();
                default -> null;
            };
        }

        @Override
        public Stamp unrestricted(ValueNode node) {
            return switch (node) {
                case LogicNode ignored -> LogicValueStamp.UNRESTRICTED;
                case IntegerSwitchNode iSwitch -> unrestricted(iSwitch.switchValue().stamp(NodeView.DEFAULT));
                default -> unrestricted(node.stamp(NodeView.DEFAULT));
            };
        }

        @Override
        public Stamp unrestricted(Stamp elem) {
            return switch (elem) {
                case LogicValueStamp lStamp -> lStamp.unrestricted();
                case PrimitiveStamp pStamp -> pStamp.unrestricted();
                case AbstractObjectStamp oStamp -> oStamp.unrestricted();
                default -> null;
            };
        }

        @Override
        public TriState isWeakerThan(Stamp x, Stamp y) {
            if (x.equals(y) || x.isEmpty() || y.isUnrestricted()) {
                // nothing is stronger than empty and nothing is weaker than unrestricted
                return TriState.FALSE;
            } else if (x.isUnrestricted() || y.isEmpty()) {
                // nothing is weaker than unrestricted and nothing is stronger than empty
                return TriState.TRUE;
            } else {
                /*
                 * Handle more complex cases by calculating a full stamp meet.
                 *
                 * If the two given stamps are comparable, usually no new stamp is instantiated
                 * anyway. Therefore, providing a special implementation here is not worth the risk
                 * as it would need to mimic the computation done for the stamp meet anyway.
                 */
                return AnalysisDomainDefinition.super.isWeakerThan(x, y);
            }
        }

        @Override
        public Stamp transfer(ValueNode node, DFAMap<Stamp> map) {
            Stamp transferResult = switch (node) {
                case ConstantNode constant ->
                    constant.stamp(NodeView.DEFAULT);
                case LogicConstantNode constant ->
                    stampForBoolean(constant.getValue());
                case ProxyNode proxy ->
                    map.getOrUnrestricted(proxy.value());
                case DynamicPiNode dpi -> {
                    Stamp type = map.getOrUnrestricted(dpi.getOriginalNode());
                    if (!(type instanceof AbstractObjectStamp aost)) {
                        // no idea what this is, just return graph stamp
                        yield dpi.stamp(NodeView.DEFAULT);
                    }
                    AbstractObjectStamp graphStamp = (AbstractObjectStamp) dpi.stamp(NodeView.DEFAULT);
                    // take type from graph
                    AbstractObjectStamp typeImproved = aost.asType(graphStamp.type(), graphStamp.isExactType(), graphStamp.isAlwaysArray());
                    // take nullability from node
                    Stamp improved = dpi.allowsNull() ? typeImproved : typeImproved.asNonNull();
                    /*
                     * DynamicPis may produce a non-unrestricted value even if its input is
                     * unevaluated. The case for PiNodes in countUnevaluatedInputs is reused for
                     * DynamicPis.
                     */
                    yield improved;
                }
                /*
                 * Pi nodes are special in the sense that they can generate new values even if their
                 * input is not evaluated yet. We want to capture all the pi's power while also not
                 * blocking possible future discovery of better values. Therefore, if we would raise
                 * the value of a pi, we instead reset it and all of its usages.
                 *
                 * The framework can take care of this internally. The only thing needed is,
                 * providing the number of currently unevaluated inputs in countUnevaluatedInputs.
                 */
                case PiNode pi -> {
                    Stamp input = map.getOrUnrestricted(pi.getOriginalNode());
                    Stamp piStamp = pi.piStamp();

                    Stamp result;
                    if (isUnevaluated(input)) {
                        result = piStamp;
                    } else {
                        Stamp improved = input.improveWith(piStamp);
                        if (improved.isEmpty()) {
                            /*
                             * If we encounter incompatible values, we ignore the PI and just return
                             * the input.
                             */
                            improved = input;
                        }

                        /*
                         * If we had conflicting stamps despite an evaluated input in the last
                         * iteration, we need to merge that result into the current result to uphold
                         * monotonicity.
                         *
                         * If we trust the pi stamp: [-1] transferPi([0..1000]) = [0..1000] which is
                         * weaker than [-1..0] transferPi([0..1000]) = [0] even though the first
                         * inputs are stronger.
                         */
                        result = merge(map.getOrUnevaluated(pi), improved);
                    }

                    if (result instanceof AbstractObjectStamp ios) {
                        // erase type information gains for object stamps because object stamps do
                        // not support intersection types
                        AbstractObjectStamp graphStamp = (AbstractObjectStamp) pi.stamp(NodeView.DEFAULT);
                        result = ios.asType(graphStamp.type(), graphStamp.isExactType(), graphStamp.isAlwaysArray());
                    }

                    yield result;
                }
                /*
                 * A ConditionalNode may produce a non-special result even if its condition is not
                 * evaluated yet which might be attempted to be raised to constant if information
                 * about the condition becomes available later.
                 *
                 * If for example both inputs are non-special constants and the condition is not
                 * evaluated yet, the result of the ConditionalNode is a non-special value. If later
                 * information about the condition becomes available, the result of the
                 * ConditionalNodes will be constant.
                 *
                 * The framework can handle such situations internally, when provided with an
                 * according case in countUnevaluatedInputs as is the case here.
                 */
                case ConditionalNode conditional ->
                    switch (stateForLogicStamp((LogicValueStamp) map.getOrUnrestricted(conditional.condition()))) {
                        case TRUE -> map.getOrUnrestricted(conditional.trueValue());
                        case FALSE -> map.getOrUnrestricted(conditional.falseValue());
                        case UNKNOWN -> map.getOrUnrestricted(conditional.trueValue()).meet(map.getOrUnrestricted(conditional.falseValue()));
                    };
                case ShortCircuitOrNode or -> {
                    TriState x = stateForLogicStamp((LogicValueStamp) map.getOrUnrestricted(or.getX()));
                    x = x.isUnknown() ? x : TriState.get(x.toBoolean() ^ or.isXNegated());
                    TriState y = stateForLogicStamp((LogicValueStamp) map.getOrUnrestricted(or.getY()));
                    y = y.isUnknown() ? y : TriState.get(y.toBoolean() ^ or.isYNegated());
                    final LogicValueStamp scoRes;
                    if (x == TriState.TRUE || y == TriState.TRUE) {
                        scoRes = LogicValueStamp.TRUE;
                    } else if (x == TriState.FALSE && y == TriState.FALSE) {
                        scoRes = LogicValueStamp.FALSE;
                    } else {
                        scoRes = LogicValueStamp.UNRESTRICTED;
                    }
                    /*
                     * This node may produce an output if ony one input is evaluated. An according
                     * case is provided in countUnevaluatedInputs.
                     */
                    yield scoRes;
                }
                case IntegerExactOverflowNode exactOverflowNode -> stampForTriState(isOverflowing(ExactOp.forOverflow(exactOverflowNode),
                                (IntegerStamp) map.getOrUnrestricted(exactOverflowNode.getX()),
                                (IntegerStamp) map.getOrUnrestricted(exactOverflowNode.getY())));
                case IntegerNegExactOverflowNode exactNegate -> {
                    IntegerStamp valueStamp = (IntegerStamp) map.getOrUnrestricted(exactNegate.getValue());
                    PrimitiveConstant value = (PrimitiveConstant) valueStamp.asConstant();
                    yield value != null ? stampForBoolean(value.asLong() == CodeUtil.minValue(valueStamp.getBits())) : LogicValueStamp.UNRESTRICTED;
                }
                case IntegerExactArithmeticSplitNode exactSplit -> {
                    if (exactSplit instanceof IntegerNegExactSplitNode exactNegate) {
                        IntegerStamp valueStamp = (IntegerStamp) map.getOrUnrestricted(exactNegate.getValue());
                        yield ArithmeticOpTable.forStamp(valueStamp).getNeg().foldStamp(valueStamp);
                    } else {
                        BinaryIntegerExactArithmeticSplitNode binarySplit = (BinaryIntegerExactArithmeticSplitNode) exactSplit;
                        Stamp splitStamp = binarySplit.stamp(NodeView.DEFAULT);
                        ArithmeticOpTable table = ArithmeticOpTable.forStamp(splitStamp);
                        ArithmeticOpTable.BinaryOp<?> op = switch (binarySplit) {
                            case IntegerAddExactSplitNode ignored -> table.getAdd();
                            case IntegerSubExactSplitNode ignored -> table.getSub();
                            case IntegerMulExactSplitNode ignored -> table.getMul();
                            default -> throw GraalError.shouldNotReachHere("Unknown binary exact arithmetic split node " + binarySplit.getClass().getName());
                        };
                        yield op.foldStamp(map.getOrUnrestricted(binarySplit.getX()), map.getOrUnrestricted(binarySplit.getY()));
                    }
                }
                /*
                 * Integer to floating point is never NaN (and therefore produces a non-unrestricted
                 * value even though its input is unevaluated. An according case is provided in
                 * countUnevaluatedInputs.
                 */
                case FloatConvertNode fconv ->
                    fconv.foldStamp(map.getOrUnrestricted(fconv.getValue()));
                case ReadNode read ->
                    read.stamp(NodeView.DEFAULT);
                case AllocatedObjectNode allocObj ->
                    allocObj.stamp(NodeView.DEFAULT);
                case UnaryOpLogicNode unary -> !map.isEvaluated(unary.getValue())
                                /*
                                 * If the input is not evaluated yet, we do not touch this node
                                 * (i.e. return unrestricted). An example for this is an int to
                                 * float conversion that returns non-NaN for unevaluated inputs
                                 * which blocks further discovery of possible constants.
                                 */
                                ? unrestricted(unary)
                                : stampForTriState(unary.tryFold(map.getOrUnrestricted(unary.getValue())));
                case BinaryOpLogicNode binary -> !map.isEvaluated(binary.getX()) || !map.isEvaluated(binary.getY())
                                /*
                                 * General binary nodes that have one input edge not evaluated yet
                                 * may produce misleading results, therefore we skip them here for
                                 * now by just returning unrestricted.
                                 */
                                ? unrestricted(binary)
                                : stampForTriState(binary.tryFold(map.getOrUnrestricted(binary.getX()), map.getOrUnrestricted(binary.getY())));
                case UnaryNode unary -> !map.isEvaluated(unary.getValue())
                                ? unrestricted(unary)
                                : unary.foldStamp(map.getOrUnrestricted(unary.getValue()));
                case BinaryNode binary -> !map.isEvaluated(binary.getX()) || !map.isEvaluated(binary.getY())
                                ? unrestricted(binary)
                                : binary.foldStamp(map.getOrUnrestricted(binary.getX()), map.getOrUnrestricted(binary.getY()));
                case null ->
                    throw GraalError.shouldNotReachHere("can not evaluate node 'null'");
                default ->
                    // If we do not know the node but initially indicated interest in the given
                    // node, we default to unrestricted
                    unrestricted(node);
            };
            return normalize(transferResult);
        }

        @Override
        public boolean[] splitReachability(ControlSplitNode split, DFAMap<Stamp> map) {
            switch (split) {
                case IfNode ifNode -> {
                    // boolean[] {trueSuccessor, falseSuccessor}
                    TriState condition = stateForLogicStamp((LogicValueStamp) map.getOrUnrestricted(ifNode.condition()));
                    return switch (condition) {
                        case UNKNOWN -> TRUE_TRUE;
                        case TRUE -> TRUE_FALSE;
                        case FALSE -> FALSE_TRUE;
                    };
                }
                case SwitchNode switchNode -> {
                    Stamp value = map.getOrUnrestricted(switchNode.value());
                    boolean[] reach = new boolean[switchNode.getSuccessorCount()];
                    if (value.isConstant()) {
                        /*
                         * Exactly one successor edge is reachable; valueConstant is guaranteed
                         * non-null, otherwise valueLevel would not be CONSTANT
                         */
                        Constant valueConstant = value.asConstant();
                        boolean foundKey = false;
                        for (int i = 0; i < switchNode.keyCount(); i++) {
                            if (valueConstant.equals(switchNode.keyAt(i))) {
                                reach[switchNode.keySuccessorIndex(i)] = true;
                                foundKey = true;
                            }
                        }
                        if (!foundKey) {
                            reach[switchNode.defaultSuccessorIndex()] = true;
                        }
                        boolean doNotTripTwice = false;
                        for (boolean b : reach) {
                            assert !(b && doNotTripTwice) : "nondeterminism???";
                            doNotTripTwice |= b;
                        }
                        assert doNotTripTwice : "we're on a road to nowhere";
                    } else if (switchNode instanceof IntegerSwitchNode integerSwitch && !((IntegerStamp) value).canBeZero()) {
                        // all non 0 successors are reachable
                        for (int i = 0; i < switchNode.keyCount(); i++) {
                            reach[switchNode.keySuccessorIndex(i)] |= integerSwitch.intKeyAt(i) != 0;
                        }
                        // default successor is always considered reachable for non 0
                        reach[switchNode.defaultSuccessorIndex()] = true;
                    } else {
                        // all successors are reachable
                        Arrays.fill(reach, true);
                    }
                    return reach;
                }
                case BinaryIntegerExactArithmeticSplitNode exactBinary -> {
                    // boolean[] {defaultSuccessor, overflowSuccessor}
                    return switch (isOverflowing(ExactOp.forBinarySplit(exactBinary), (IntegerStamp) map.getOrUnrestricted(exactBinary.getX()),
                                    (IntegerStamp) map.getOrUnrestricted(exactBinary.getY()))) {
                        case UNKNOWN -> TRUE_TRUE;
                        case TRUE -> FALSE_TRUE;
                        case FALSE -> TRUE_FALSE;
                    };
                }
                case IntegerNegExactSplitNode exactNegate -> {
                    // boolean[] {defaultSuccessor, overflowSuccessor}
                    IntegerStamp valueStamp = (IntegerStamp) map.getOrUnrestricted(exactNegate.getValue());
                    PrimitiveConstant value = (PrimitiveConstant) valueStamp.asConstant();
                    return value != null ? (value.asLong() == 1L << (valueStamp.getBits() - 1) ? FALSE_TRUE : TRUE_FALSE) : TRUE_TRUE;
                }
                case null ->
                    throw GraalError.shouldNotReachHere("can not compute successor reachability for split 'null'");
                default -> {
                    return null;
                }
            }
        }

        @Override
        public Stamp[][] calcInferrableValues(ValueNode node, DFAMap<Stamp> map) {
            return switch (node) {
                case IsNullNode nullNode -> {
                    ValueNode value = nullNode.getValue();
                    AbstractPointerStamp unrest = (AbstractPointerStamp) unrestricted(value);
                    yield AnalysisDomainDefinition.ifInference(Stamp.class,
                                    AnalysisDomainDefinition.unaryLogicInference(Stamp.class, unrest.asAlwaysNull()),
                                    AnalysisDomainDefinition.unaryLogicInference(Stamp.class, unrest.asNonNull()));
                }
                case CompareNode compareNode -> {
                    ValueNode x = compareNode.getX();
                    ValueNode y = compareNode.getY();
                    Stamp unrestX = unrestricted(x);
                    Stamp unrestY = unrestricted(y);
                    Stamp xStamp = map.getOrUnrestricted(x);
                    GraalError.guarantee(xStamp != null, "WHAT why is %s with stamp %s null when evaluating %s?", x, x.stamp(NodeView.DEFAULT), compareNode);
                    Stamp yStamp = map.getOrUnrestricted(y);
                    // only calculate additional information for X and Y caused by this compare
                    Stamp xt = compareNode.getSucceedingStampForX(false, unrestX, yStamp);
                    Stamp yt = compareNode.getSucceedingStampForY(false, xStamp, unrestY);
                    Stamp xf = compareNode.getSucceedingStampForX(true, unrestX, yStamp);
                    Stamp yf = compareNode.getSucceedingStampForY(true, xStamp, unrestY);
                    yield AnalysisDomainDefinition.ifInference(Stamp.class,
                                    // true branch
                                    AnalysisDomainDefinition.binaryLogicInference(Stamp.class,
                                                    xt == null ? unrestX : xt,
                                                    yt == null ? unrestY : yt),
                                    // false branch
                                    AnalysisDomainDefinition.binaryLogicInference(Stamp.class,
                                                    xf == null ? unrestX : xf,
                                                    yf == null ? unrestY : yf));
                }
                case IntegerSwitchNode iSwitch -> {
                    IntegerStamp vUnev = (IntegerStamp) unevaluated(iSwitch.switchValue());
                    Stamp[] inferences = new IntegerStamp[iSwitch.getSuccessorCount()];
                    Arrays.fill(inferences, vUnev);
                    for (int i = 0; i < iSwitch.keyCount(); i++) {
                        Stamp keyStamp = IntegerStamp.createConstant(vUnev.getBits(), iSwitch.intKeyAt(i));
                        int branch = iSwitch.keySuccessorIndex(i);
                        inferences[branch] = merge(inferences[branch], keyStamp);
                    }
                    for (int i = 0; i < inferences.length; i++) {
                        if (isUnevaluated(inferences[i])) {
                            inferences[i] = unrestricted(inferences[i]);
                        }
                    }
                    yield AnalysisDomainDefinition.switchInference(Stamp.class, inferences);
                }
                default -> null;
            };
        }

        @Override
        public int countUnevaluatedInputs(ValueNode node, DFAMap<Stamp> map) {
            return switch (node) {
                case PiNode pi ->
                    !map.isEvaluated(pi.getOriginalNode()) ? 1 : 0;
                case ConditionalNode conditional -> {
                    int unev = 0;
                    if (!map.isEvaluated(conditional.condition())) {
                        unev++;
                    }
                    if (!map.isEvaluated(conditional.trueValue())) {
                        unev++;
                    }
                    if (!map.isEvaluated(conditional.falseValue())) {
                        unev++;
                    }
                    yield unev;
                }
                case ShortCircuitOrNode so -> {
                    int unev = 0;
                    if (!map.isEvaluated(so.getX())) {
                        unev++;
                    }
                    if (!map.isEvaluated(so.getY())) {
                        unev++;
                    }
                    yield unev;
                }
                case FloatConvertNode fconv ->
                    fconv.getFloatConvert().getCategory() == FloatConvertCategory.IntegerToFloatingPoint
                                    // int to float conversions yield a non-NaN result for
                                    // unevaluated inputs
                                    ? map.isEvaluated(fconv.getValue()) ? 0 : 1
                                    // treat all other float conversions normally
                                    : AnalysisDomainDefinition.super.countUnevaluatedInputs(node, map);
                default ->
                    AnalysisDomainDefinition.super.countUnevaluatedInputs(node, map);
            };
        }

        @Override
        public boolean supportsWidening() {
            return true;
        }

        @Override
        public void widen(List<ValuePhiNode> phis, IntList reachableInputIndices, Stamp[] result, DFAMap<Stamp> map, int evalCnt) {
            if (evalCnt >= Options.DFSA_WidenAfter.getValue(phis.getFirst().getOptions())) {
                // for all phis
                for (int i = 0; i < phis.size(); i++) {
                    ValuePhiNode phi = phis.get(i);
                    if (isEqual(map.getOrUnevaluated(phi), result[i])) {
                        // we do not need to widen here, this phi was not updated
                        continue;
                    }
                    result[i] = isWeakerOrEqual(phi.stamp(NodeView.DEFAULT), result[i]).isTrue()
                                    // if possible, generalize to graph stamp
                                    ? phi.stamp(NodeView.DEFAULT)
                                    // else generalize to unrestricted
                                    : unrestricted(phi);
                }
            }
        }

        private Stamp normalize(Stamp input) {
            if (doTypes)
                return input;
            if (input instanceof AbstractObjectStamp oStamp) {
                if (oStamp.isEmpty()) {
                    return oStamp;
                }
                if (oStamp.isConstant() || oStamp.nonNull()) {
                    return oStamp.asType(null, false, false);
                }
                return oStamp.unrestricted();
            } else {
                return input;
            }
        }

        /**
         * Check if op results in an overflow given the input stamps.
         *
         * @return {@link TriState#UNKNOWN} if one or both inputs are not constant.
         */
        private static TriState isOverflowing(ExactOp op, IntegerStamp xStamp, IntegerStamp yStamp) {
            int bits = xStamp.getBits();
            PrimitiveConstant xConst = (PrimitiveConstant) xStamp.asConstant();
            PrimitiveConstant yConst = (PrimitiveConstant) yStamp.asConstant();
            if (xConst == null || yConst == null) {
                return TriState.UNKNOWN;
            }
            long x = xConst.asLong();
            long y = yConst.asLong();
            try {
                // try the exact operation
                switch (op) {
                    case ADD -> LoopUtility.addExact(bits, x, y);
                    case SUB -> LoopUtility.subtractExact(bits, x, y);
                    case MUL -> LoopUtility.multiplyExact(bits, x, y);
                }
                // if the operation succeeded, we know it does not overflow
                return TriState.FALSE;
            } catch (ArithmeticException ignored) {
                // if the operation failed, we know it does overflow
                return TriState.TRUE;
            }
        }

        enum ExactOp {
            ADD,
            SUB,
            MUL;

            public static ExactOp forOverflow(IntegerExactOverflowNode node) {
                return switch (node) {
                    case IntegerAddExactOverflowNode ignored -> ExactOp.ADD;
                    case IntegerSubExactOverflowNode ignored -> ExactOp.SUB;
                    case IntegerMulExactOverflowNode ignored -> ExactOp.MUL;
                    case null -> throw GraalError.shouldNotReachHere("got 'null' as IntegerExactOverflowNode");
                    default -> throw GraalError.shouldNotReachHere("unexpected IntegerExactOverflowNode " + node.getClass().getSimpleName());
                };
            }

            public static ExactOp forBinarySplit(BinaryIntegerExactArithmeticSplitNode node) {
                return switch (node) {
                    case IntegerAddExactSplitNode ignored -> ExactOp.ADD;
                    case IntegerSubExactSplitNode ignored -> ExactOp.SUB;
                    case IntegerMulExactSplitNode ignored -> ExactOp.MUL;
                    case null -> throw GraalError.shouldNotReachHere("got 'null' as IntegerExactOverflowNode");
                    default -> throw GraalError.shouldNotReachHere("unexpected IntegerExactOverflowNode " + node.getClass().getSimpleName());
                };
            }
        }
    }

    private static LogicValueStamp stampForBoolean(boolean value) {
        return value ? LogicValueStamp.TRUE : LogicValueStamp.FALSE;
    }

    private static LogicValueStamp stampForTriState(TriState state) {
        if (state == null) {
            return LogicValueStamp.EMPTY;
        }
        return switch (state) {
            case TRUE -> LogicValueStamp.TRUE;
            case FALSE -> LogicValueStamp.FALSE;
            case UNKNOWN -> LogicValueStamp.UNRESTRICTED;
        };
    }

    private static TriState stateForLogicStamp(LogicValueStamp stamp) {
        if (stamp.isEmpty()) {
            return null;
        } else if (stamp.equals(LogicValueStamp.TRUE)) {
            return TriState.TRUE;
        } else if (stamp.equals(LogicValueStamp.FALSE)) {
            return TriState.FALSE;
        } else {
            GraalError.guarantee(stamp.equals(LogicValueStamp.UNRESTRICTED), "unexpected LogicValueStamp %s", stamp);
            return TriState.UNKNOWN;
        }
    }
}
