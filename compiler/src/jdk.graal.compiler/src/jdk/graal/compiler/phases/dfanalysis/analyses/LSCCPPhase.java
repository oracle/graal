/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.dfanalysis.analyses;

import java.util.Arrays;
import java.util.Optional;

import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
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
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
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

/**
 * <p>
 * This phase implements a variation of sparse conditional constant propagation as described in
 * <a href="https://dl.acm.org/doi/10.1145/3679007.3685059">this paper<a/>.
 * </p>
 * <p>
 * The domain of constants is represented using stamps which have all of their information stripped
 * except if a value is constant or not (see {@link ConstantNormalizedStamps#normalize}). The
 * lattice used for this analysis is as follows.
 * </p>
 *
 * <pre>
 * Lattice of constant normalized stamps:
 *                          UNEVALUATED
 *                     __________|____________
 *                    /   /  /   |   \   \    \
 *    *other constants*  -1  1  ...  0  NaN  null
 *                    \   |  |  /     \  |    /
 *                    NON-SPECIAL      \_|___/
 *                           \          /
 *                           UNRESTRICTED
 * </pre>
 *
 * <p>
 * This lattice defines EMPTY as {@code unevaluated}, UNRESTRICTED as is, and a tier where each
 * element represents a constant. Additionally, this lattice specifies a "non-special" tier.
 * "Special" values in this context are defined as {@code 0} for integer values, {@code NaN} for
 * floating point values and {@code null} for reference types. A value of "non-special" denotes that
 * any value is possible except the special value for the given type.
 * </p>
 */
public final class LSCCPPhase extends PostRunCanonicalizationPhase<CoreProviders> {
    public static final class Options {
        // @formatter:off
        @Option(help = "Allows LSCCP to infer information from conditions of control flow branches", type = OptionType.Debug)
        public static final OptionKey<Boolean> LSCCP_AllowInferences = new OptionKey<>(true);
        // @formatter:on
    }

    public LSCCPPhase(CanonicalizerPhase canonicalizer) {
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

        if (DFAnalysis.Options.DFA_PreCanonicalize.getValue(graph.getOptions())) {
            CanonicalizerPhase.create().apply(graph, context);
        }

        try (DebugContext.Scope scope = graph.getDebug().scope("LSCCPApplication")) {
            boolean inferencesEnabled = Options.LSCCP_AllowInferences.getValue(graph.getOptions()) && DFAnalysis.Options.DFA_AllowInferences.getValue(graph.getOptions());
            // run analysis
            DFAnalysis<Stamp> analysis = DFAnalysis.create(Stamp.class, graph, CONSTANT_NORMALIZED_STAMPS, nd -> switch (nd) {
                case ConstantNode ignored -> true;
                case LogicConstantNode ignored -> true;
                case PiNode ignored -> true;
                case IsNullNode ignored -> inferencesEnabled; // only useful for inferences
                case IntegerSwitchNode ignored -> true;
                default -> false;
            });
            DFAMap<Stamp> map = analysis.run();

            // replace constants in graph
            MapCursor<ValueNode, Stamp> stampCursor = map.getEntries();
            int constantsFound = 0;
            int branchesEliminated = 0;
            while (stampCursor.advance()) {
                ValueNode node = stampCursor.getKey();
                Stamp stamp = stampCursor.getValue();
                if (stamp.isConstant() && !(node instanceof ConstantNode || node instanceof LogicConstantNode)) {
                    constantsFound++;
                    String tNodeString = graph.getDebug().isLogEnabled(DebugContext.VERY_DETAILED_LEVEL) ? node.toString() : "";
                    if (stamp instanceof LogicValueStamp logicStamp) {
                        graph.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "replacing %s with constant %s", node, stateForLogicStamp(logicStamp));
                        node.replaceAtUsagesAndDelete(LogicConstantNode.forBoolean(stateForLogicStamp(logicStamp).toBoolean(), graph));
                        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Replaced %s with constant", node);
                    } else {
                        if (stamp instanceof AbstractObjectStamp constantObjectStamp) {
                            if (node.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp curStamp) {
                                /*
                                 * Type information is not tracked by this analysis (see
                                 * ConstantNormalizedStamps#normalize). To generate proper
                                 * constants, we need to restore the type information. For that we
                                 * just read the type information from the stamp of the node we are
                                 * trying to replace with a null-constant.
                                 */
                                stamp = constantObjectStamp.asType(curStamp.type(), curStamp.isExactType(), curStamp.isAlwaysArray());
                            } else {
                                throw GraalError.shouldNotReachHere("trying to insert AbstractObjectStamp for node that previously had no AbstractObjectStamp");
                            }
                        }
                        if (stamp.asConstant() instanceof JavaConstant javaConstant) {
                            ConstantNode c = graph.addOrUnique(new ConstantNode(javaConstant, stamp));
                            GraalError.guarantee(c.stamp(NodeView.DEFAULT).isCompatible(node.stamp(NodeView.DEFAULT)),
                                            "Incompatible stamps at CCP replacement (replacing node %s with stamp %s by constant with stamp %s)",
                                            node, node.stamp(NodeView.DEFAULT), c.stamp(NodeView.DEFAULT));
                            node.replaceAtUsages(c);
                            graph.getOptimizationLog().report(LSCCPPhase.class, "LsccpConstantReplacement", node);
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

    private static final AnalysisDomainDefinition<Stamp> CONSTANT_NORMALIZED_STAMPS = new ConstantNormalizedStamps();

    /**
     * A lattice of stamps. Elements of the lattice are normalized in the sense that arbitrary value
     * ranges will be mapped to unrestricted or "non-special" stamps. For example, an integer stamp
     * [-2 - 2] will be normalized to unrestricted, [2 - 4] will be normalized to "not zero" (but
     * otherwise unrestricted). For object stamps we erase all type information because we do not
     * track it fully. (see {@link ConstantNormalizedStamps#normalize})
     */
    private static final class ConstantNormalizedStamps implements AnalysisDomainDefinition<Stamp> {
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
            assert x.getClass().equals(y.getClass()) : "incompatible stamps";
            if (isEqual(x, y)) {
                return TriState.FALSE;
            }
            if (x.isUnrestricted() || y.isEmpty()) {
                // x weakerThan y if: x is unrestricted and y is not, or if y is empty and x is not
                return TriState.TRUE;
            }
            if (x.isEmpty() || y.isUnrestricted()) {
                // x not weakerThan y if: x is empty and y is not, or if y is unrestricted and x is
                // not
                return TriState.FALSE;
            }
            if (x.isConstant() && y.isConstant()) {
                // different constants are not comparable
                return TriState.UNKNOWN;
            }
            if (!x.isConstant()) {
                assert isNonSpecial(x) && y.isConstant() : "all other cases should be covered already";
                // comparing non-special x to constant y
                if (isNonSpecial(y)) {
                    /*
                     * x weakerThan y if: x is non-special and y is a constant that is above
                     * non-special (i.e., not a special constant)
                     */
                    return TriState.TRUE;
                } else {
                    // non-special x is not comparable to special constant y
                    return TriState.UNKNOWN;
                }
            }
            assert x.isConstant() && !y.isConstant() && isNonSpecial(y) : "all other cases should be covered already";
            if (isNonSpecial(x)) {
                /*
                 * x not weakerThan y if: x is a constant that is above non-special (i.e., not a
                 * special constant) and y is non-special
                 */
                return TriState.FALSE;
            } else {
                // special constant x is not comparable to non-special y
                return TriState.UNKNOWN;
            }
        }

        @Override
        public boolean isEqual(Stamp x, Stamp y) {
            assert x.getClass().equals(y.getClass()) : "incompatible stamps";
            return x.equals(y);
        }

        @Override
        public Stamp transfer(ValueNode node, DFAMap<Stamp> map) {
            Stamp result = switch (node) {
                case ConstantNode constant ->
                    constant.stamp(NodeView.DEFAULT);
                case LogicConstantNode constant ->
                    stampForBoolean(constant.getValue());
                case ProxyNode proxy ->
                    map.getOrUnrestricted(proxy.value());
                case PiNode pi -> {
                    /*
                     * This node can produce a stronger than unrestricted results while still having
                     * its input unevaluated.
                     *
                     * If a pi for example marks a value as non-zero and has an unevaluated input
                     * this method returns a non-zero value. If later the input is evaluated to a
                     * constant 1, this function returns the stronger value constant 1 even though
                     * its input (now 1 instead of unevaluated which is interpreted as unrestricted)
                     * is technically weaker.
                     *
                     * An according case in ConstantNormalizedStamps#countUnevaluatedInputs has been
                     * implemented.
                     */
                    Stamp input = map.getOrUnrestricted(pi.getOriginalNode());
                    Stamp piStamp = normalize(pi.piStamp());
                    if (isUnevaluated(input)) {
                        // if we do not have an input, the piStamp is our best guess
                        yield piStamp;
                    } else {
                        Stamp improved = normalize(input.improveWith(piStamp));
                        if (improved.isEmpty()) {
                            /*
                             * If we encounter incompatible values, we ignore the PI and just use
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
                        yield merge(map.getOrUnevaluated(pi), improved);
                    }
                }
                case ConditionalNode conditional -> {
                    TriState cond = stateForLogicStamp((LogicValueStamp) map.getOrUnrestricted(conditional.condition()));
                    Stamp evaluationResult = normalize(switch (cond) {
                        case TRUE -> map.getOrUnrestricted(conditional.trueValue());
                        case FALSE -> map.getOrUnrestricted(conditional.falseValue());
                        case UNKNOWN -> map.getOrUnrestricted(conditional.trueValue()).meet(map.getOrUnrestricted(conditional.falseValue()));
                    });
                    /*
                     * This node can produce a stronger than unrestricted results while still having
                     * unevaluated inputs (i.e. when only the condition is unevaluated).
                     *
                     * If a conditional for example as its value inputs evaluated but not its
                     * condition like (cond: ???, trueVal: 1, falseVal: 2) we return a non-special
                     * result here even though it is a weaker result than the one we return later
                     * when the condition becomes evaluated (cond: true, trueVal: 1, falseVal: 2)
                     * namely in this case constant 1.
                     *
                     * Since we still want to produce a result even in the case where the condition
                     * stays unevaluated throughout the analysis, an according case is implemented
                     * in ConstantNormalizedStamps#countUnevaluatedInputs.
                     */
                    yield evaluationResult;
                }
                case ShortCircuitOrNode or -> {
                    TriState x = stateForLogicStamp((LogicValueStamp) map.getOrUnrestricted(or.getX()));
                    x = x.isUnknown() ? x : TriState.get(x.toBoolean() ^ or.isXNegated());
                    TriState y = stateForLogicStamp((LogicValueStamp) map.getOrUnrestricted(or.getY()));
                    y = y.isUnknown() ? y : TriState.get(y.toBoolean() ^ or.isYNegated());
                    if (x == TriState.TRUE || y == TriState.TRUE) {
                        yield LogicValueStamp.TRUE;
                    } else if (x == TriState.FALSE && y == TriState.FALSE) {
                        yield LogicValueStamp.FALSE;
                    } else {
                        yield LogicValueStamp.UNRESTRICTED;
                    }
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
            GraalError.guarantee(result != null, "For interesting nodes like %s we should always return UNRESTRICTED instead of null", node);
            return normalize(result);
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
                    } else if (isNonSpecial(value) && switchNode instanceof IntegerSwitchNode integerSwitch) {
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
            if (!Options.LSCCP_AllowInferences.getValue(node.getDebug().getOptions())) {
                return null;
            }
            return switch (node) {
                case IsNullNode nullNode -> {
                    ValueNode value = nullNode.getValue();
                    AbstractPointerStamp vStamp = (AbstractPointerStamp) map.getOrUnrestricted(value);
                    yield AnalysisDomainDefinition.ifInference(Stamp.class,
                                    AnalysisDomainDefinition.unaryLogicInference(Stamp.class, normalize(vStamp.asAlwaysNull())),
                                    AnalysisDomainDefinition.unaryLogicInference(Stamp.class, normalize(vStamp.asNonNull())));
                }
                case CompareNode compareNode -> {
                    ValueNode x = compareNode.getX();
                    ValueNode y = compareNode.getY();
                    Stamp unrestX = unrestricted(x);
                    Stamp unrestY = unrestricted(y);
                    Stamp xStamp = map.getOrUnrestricted(x);
                    Stamp yStamp = map.getOrUnrestricted(y);
                    // only calculate additional information for X and Y caused by this compare
                    Stamp xt = compareNode.getSucceedingStampForX(false, unrestX, yStamp);
                    Stamp yt = compareNode.getSucceedingStampForY(false, xStamp, unrestY);
                    Stamp xf = compareNode.getSucceedingStampForX(true, unrestX, yStamp);
                    Stamp yf = compareNode.getSucceedingStampForY(true, xStamp, unrestY);
                    yield AnalysisDomainDefinition.ifInference(Stamp.class,
                                    // true branch
                                    AnalysisDomainDefinition.binaryLogicInference(Stamp.class,
                                                    xt == null ? unrestX : normalize(xt),
                                                    yt == null ? unrestY : normalize(yt)),
                                    // false branch
                                    AnalysisDomainDefinition.binaryLogicInference(Stamp.class,
                                                    xf == null ? unrestX : normalize(xf),
                                                    yf == null ? unrestY : normalize(yf)));
                }
                case IntegerSwitchNode iSwitch -> {
                    IntegerStamp vUnev = (IntegerStamp) unevaluated(iSwitch.switchValue());
                    Stamp[] inferences = new IntegerStamp[iSwitch.getSuccessorCount()];
                    Arrays.fill(inferences, vUnev);
                    for (int i = 0; i < iSwitch.keyCount(); i++) {
                        Stamp keyStamp = IntegerStamp.createConstant(vUnev.getBits(), iSwitch.intKeyAt(i));
                        int branch = iSwitch.keySuccessorIndex(i);
                        inferences[branch] = normalize(merge(inferences[branch], keyStamp));
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
                case PiNode pi -> map.isEvaluated(pi.getOriginalNode()) ? 0 : 1;
                case ConditionalNode cond -> {
                    int unevalCnt = 0;
                    if (!map.isEvaluated(cond.condition())) {
                        unevalCnt++;
                    }
                    if (!map.isEvaluated(cond.trueValue())) {
                        unevalCnt++;
                    }
                    if (!map.isEvaluated(cond.falseValue())) {
                        unevalCnt++;
                    }
                    yield unevalCnt;
                }
                default -> AnalysisDomainDefinition.super.countUnevaluatedInputs(node, map);
            };
        }

        /*
         * Non-zero/non-NaN stamps to reduce allocations.
         *
         * The stamp for an N-bit integer is at index log2(N), the stamp for an N-bit float is at
         * index N/32-1.
         */
        private static final IntegerStamp[] INT_NON_ZERO = new IntegerStamp[]{
                        IntegerStamp.create(1, CodeUtil.minValue(1), CodeUtil.maxValue(1), 0, CodeUtil.mask(1), false),
                        null,
                        null,
                        IntegerStamp.create(8, CodeUtil.minValue(8), CodeUtil.maxValue(8), 0, CodeUtil.mask(8), false),
                        IntegerStamp.create(16, CodeUtil.minValue(16), CodeUtil.maxValue(16), 0, CodeUtil.mask(16), false),
                        IntegerStamp.create(32, CodeUtil.minValue(32), CodeUtil.maxValue(32), 0, CodeUtil.mask(32), false),
                        IntegerStamp.create(64, CodeUtil.minValue(64), CodeUtil.maxValue(64), 0, CodeUtil.mask(64), false)
        };
        private static final FloatStamp[] FLOAT_NON_NAN = new FloatStamp[]{
                        FloatStamp.create(32, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true),
                        FloatStamp.create(64, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true)
        };

        /**
         * Remove all incompletely tracked information (meaning everything but information about
         * whether the stamp represents a constant or a non-special value) in order to not
         * involuntarily make false assumptions based on incomplete information.
         *
         * For object stamps, we can only track nullability, therefore we erase all other
         * information.
         */
        private static Stamp normalize(Stamp input) {
            return switch (input) {
                case LogicValueStamp lStamp -> lStamp;
                case IntegerStamp iStamp -> {
                    if (iStamp.isEmpty() || iStamp.isConstant()) {
                        yield iStamp;
                    }
                    if (isNonSpecial(iStamp)) {
                        yield INT_NON_ZERO[CodeUtil.log2(iStamp.getBits())];
                    }
                    yield iStamp.unrestricted();
                }
                case FloatStamp fStamp -> {
                    if (fStamp.isEmpty() || fStamp.isConstant()) {
                        yield fStamp;
                    }
                    if (isNonSpecial(fStamp)) {
                        yield FLOAT_NON_NAN[fStamp.getBits() / 32 - 1];
                    }
                    yield fStamp.unrestricted();
                }
                case PrimitiveStamp pStamp -> throw GraalError.shouldNotReachHere("Unknown PrimitiveStamp '%s'".formatted(pStamp.getClass().getName()));
                case AbstractObjectStamp oStamp -> {
                    if (oStamp.isEmpty()) {
                        yield oStamp;
                    }
                    if (oStamp.isConstant()) {
                        yield oStamp.asType(null, false, false);
                    }
                    if (isNonSpecial(oStamp)) {
                        yield oStamp.asType(null, false, false);
                    }
                    yield oStamp.unrestricted();
                }
                default -> throw GraalError.shouldNotReachHere("Unknown stamp type '%s'".formatted(input.getClass().getName()));
            };
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

    private static boolean isNonSpecial(Stamp s) {
        return switch (s) {
            case IntegerStamp i -> !i.canBeZero();
            case FloatStamp f -> f.isNonNaN();
            case AbstractPointerStamp p -> p.nonNull();
            default -> false;
        };
    }
}
