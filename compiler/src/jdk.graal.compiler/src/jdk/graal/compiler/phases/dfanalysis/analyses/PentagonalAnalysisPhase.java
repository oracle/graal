/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.dfanalysis.analyses;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.core.common.calc.FloatConvertCategory;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.util.IntList;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.CompressionNode;
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
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.IntegerLowerThanNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.PointerEqualsNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.ValueProxy;
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
import jdk.graal.compiler.phases.dfanalysis.analyses.Pentagon.FloatPentagon;
import jdk.graal.compiler.phases.dfanalysis.analyses.Pentagon.IntegerPentagon;
import jdk.graal.compiler.phases.dfanalysis.analyses.Pentagon.LogicPentagon;
import jdk.graal.compiler.phases.dfanalysis.analyses.Pentagon.ObjectPentagon;
import jdk.graal.compiler.phases.dfanalysis.analyses.Pentagon.StampPentagon;
import jdk.graal.compiler.replacements.nodes.arithmetic.BinaryIntegerExactArithmeticSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerMulExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerMulExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerNegExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerNegExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactSplitNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.TriState;

public final class PentagonalAnalysisPhase extends PostRunCanonicalizationPhase<CoreProviders> {
    public static final class Options {
        // @formatter:off
        @Option(help = "Sets the number of pessimistic evaluations of loop PHIs before widening kicks in", type = OptionType.Debug)
        public static final OptionKey<Integer> PA_WidenAfter = new OptionKey<>(2);

        @Option(help = "Enables additional reasoning for non-null object constants not representable by pure object stamps", type = OptionType.Debug)
        public static final OptionKey<Boolean> PA_UseNonNullObjConstants = new OptionKey<>(true);

        @Option(help = "Enables type tracking for object stamps", type = OptionType.Debug)
        public static final OptionKey<Boolean> PA_TrackTypes = new OptionKey<>(true);
        // @formatter:on
    }

    private static final class DomainOfPentagons implements AnalysisDomainDefinition<Pentagon> {
        private final ConstantReflectionProvider constantReflection;
        private final int widenAfter;
        private final boolean useNonNullObjConstants;
        private final boolean doTypes;

        public DomainOfPentagons(ConstantReflectionProvider constantReflection, int widenAfter, boolean useNonNullObjConstants, boolean doTypes) {
            this.constantReflection = constantReflection;
            this.widenAfter = widenAfter;
            this.useNonNullObjConstants = useNonNullObjConstants;
            this.doTypes = doTypes;
        }

        @Override
        public Pentagon unevaluated(ValueNode node) {
            if (node instanceof LogicNode) {
                return LogicPentagon.UNEVALUATED;
            } else {
                return switch (node.stamp(NodeView.DEFAULT)) {
                    case IntegerStamp iStamp ->
                        IntegerPentagon.UNEVALUATED[CodeUtil.log2(iStamp.getBits())];
                    case FloatStamp fStamp ->
                        fStamp.getBits() == Float.SIZE
                                        ? FloatPentagon.FLOAT_UNEVALUATED
                                        : FloatPentagon.DOUBLE_UNEVALUATED;
                    case AbstractObjectStamp oStamp ->
                        Pentagon.of((AbstractObjectStamp) oStamp.empty());
                    default ->
                        null;
                };
            }
        }

        @Override
        public Pentagon unevaluated(Pentagon elem) {
            return switch (elem) {
                case LogicPentagon ignored ->
                    LogicPentagon.UNEVALUATED;
                case IntegerPentagon vp ->
                    IntegerPentagon.UNEVALUATED[CodeUtil.log2(vp.range.getBits())];
                case FloatPentagon fp ->
                    fp.stamp.getBits() == Float.SIZE
                                    ? FloatPentagon.FLOAT_UNEVALUATED
                                    : FloatPentagon.DOUBLE_UNEVALUATED;
                case ObjectPentagon op ->
                    op.isUnevaluated() ? op : Pentagon.of((AbstractObjectStamp) op.stamp.empty());
            };
        }

        @Override
        public Pentagon unrestricted(ValueNode node) {
            if (node instanceof LogicNode) {
                return LogicPentagon.UNRESTRICTED;
            } else {
                return switch (node.stamp(NodeView.DEFAULT)) {
                    case IntegerStamp iStamp ->
                        IntegerPentagon.UNRESTRICTED[CodeUtil.log2(iStamp.getBits())];
                    case FloatStamp fStamp ->
                        fStamp.getBits() == Float.SIZE
                                        ? FloatPentagon.FLOAT_UNRESTRICTED
                                        : FloatPentagon.DOUBLE_UNRESTRICTED;
                    case AbstractObjectStamp oStamp ->
                        Pentagon.of((AbstractObjectStamp) oStamp.unrestricted());
                    default ->
                        null;
                };
            }
        }

        @Override
        public Pentagon unrestricted(Pentagon elem) {
            return switch (elem) {
                case LogicPentagon ignored ->
                    LogicPentagon.UNRESTRICTED;
                case IntegerPentagon vp ->
                    IntegerPentagon.UNRESTRICTED[CodeUtil.log2(vp.range.getBits())];
                case FloatPentagon fp ->
                    fp.stamp.getBits() == Float.SIZE
                                    ? FloatPentagon.FLOAT_UNRESTRICTED
                                    : FloatPentagon.DOUBLE_UNRESTRICTED;
                case ObjectPentagon op ->
                    op.isUnrestricted() ? op : Pentagon.of((AbstractObjectStamp) op.stamp.unrestricted());
            };
        }

        @Override
        public Pentagon merge(Pentagon x, Pentagon y) {
            return normalize(x.merge(y));
        }

        @Override
        public Pentagon strengthen(Pentagon x, Pentagon y) {
            return normalize(x.strengthen(y));
        }

        @Override
        public Pentagon[] controlFlowMerge(List<ValuePhiNode> phis, IntList reachableInputIndices, DFAMap<Pentagon> map) {
            /*
             * For non-integer phis, we just do a standard merge across all inputs.
             * 
             * For integer phis, we want to apply additional reasoning to find symbolic relations
             * between phis at this merge. To do this, we apply a 3-step process: First, we do a
             * standard merge across inputs. Second, we try to prove symbolic relations between each
             * pair of integer phis at this merge. Third and finally, we add together the symbolic
             * bounds found by the standard merge as well as the extra reasoning and construct the
             * actual integer pentagons.
             *
             * For these data structures we possibly overallocate capacity to ensure no
             * reallocations are needed. Additionally, by overallocating we can use quick and easy
             * indexing operations instead of calculating the index of another phi.
             */
            Pentagon[] results = new Pentagon[phis.size()];
            IntList iPhis = new IntList(phis.size());
            IntegerStamp[] intermediateRanges = new IntegerStamp[phis.size()];
            Set<ValueNode>[] specialSUBs = new Set[phis.size()];
            Set<ValueNode>[] specialLBs = new Set[phis.size()];
            Set<ValueNode>[] rawSUBs = new Set[phis.size()];
            Set<ValueNode>[] rawLBs = new Set[phis.size()];
            // do the standard merge across all reachable inputs
            for (int i = 0; i < phis.size(); i++) {
                ValuePhiNode phi = phis.get(i);
                if (phi.stamp(NodeView.DEFAULT) instanceof IntegerStamp iStamp) {
                    iPhis.add(i);
                    // merge integer stamps for inputs
                    Stamp iRes = iStamp.empty();
                    for (int j = 0; j < reachableInputIndices.size(); j++) {
                        iRes = iRes.meet(map.getOrUnrestricted(phi.valueAt(reachableInputIndices.get(j))).asInteger().range);
                    }
                    intermediateRanges[i] = (IntegerStamp) iRes;
                    // initialize sets
                    specialSUBs[i] = new HashSet<>();
                    specialLBs[i] = new HashSet<>();
                    // merge symbolic bounds
                    IntegerPentagon firstInput = map.getOrUnrestricted(phi.valueAt(reachableInputIndices.get(0))).asInteger();
                    Set<ValueNode> sub = new HashSet<>(firstInput.strictUpperBounds);
                    Set<ValueNode> lb = new HashSet<>(firstInput.lowerBounds);
                    for (int j = 1; j < reachableInputIndices.size(); j++) {
                        IntegerPentagon nextInput = map.getOrUnrestricted(phi.valueAt(reachableInputIndices.get(j))).asInteger();
                        sub.retainAll(nextInput.strictUpperBounds);
                        lb.retainAll(nextInput.lowerBounds);
                        if (sub.isEmpty() && lb.isEmpty()) {
                            // early exit
                            break;
                        }
                    }
                    rawSUBs[i] = sub;
                    rawLBs[i] = lb;
                } else {
                    // just do a per-PHI merge for all non-integer phis
                    Pentagon ptg = unevaluated(phi);
                    for (int j = 0; j < reachableInputIndices.size(); j++) {
                        ptg = ptg.merge(map.getOrUnrestricted(phi.valueAt(reachableInputIndices.get(j))));
                    }
                    results[i] = ptg;
                }
            }

            /*
             * Second pass: For all integer phis we do extra calculations to try to infer pairwise
             * symbolic relations between all integer phis located at this merge.
             */
            for (int i = 0; i < iPhis.size(); i++) {
                int thisIdx = iPhis.get(i);
                ValuePhiNode phi = phis.get(thisIdx);
                if (phi.stamp(NodeView.DEFAULT).isIntegerStamp()) {
                    // special treatment calculating symbolic bounds
                    for (int j = 0; j < iPhis.size(); j++) {
                        int otherIdx = iPhis.get(j);
                        if (thisIdx == otherIdx) {
                            // this is the current phi
                            continue;
                        }
                        ValuePhiNode otherPhi = phis.get(otherIdx);
                        // check the easy case first
                        if (intermediateRanges[thisIdx].upperBound() < intermediateRanges[otherIdx].lowerBound()) {
                            specialSUBs[thisIdx].add(otherPhi);
                            specialLBs[otherIdx].add(phi);
                            // we already achieved the best result, we can jump to the next phi
                            continue;
                        }
                        /*
                         * If we can establish a lower-than relationship across all incoming
                         * branches individually, we can prove a lower-than relationship between the
                         * given phis.
                         */
                        boolean sub = true; // is phi < other?
                        boolean lb = true; // is phi <= other?
                        for (int k = 0; k < reachableInputIndices.size(); k++) {
                            int inputIdx = reachableInputIndices.get(k);
                            IntegerPentagon x = map.getOrUnrestricted(phi.valueAt(inputIdx)).asInteger();
                            IntegerPentagon y = map.getOrUnrestricted(otherPhi.valueAt(inputIdx)).asInteger();
                            if (sub) {
                                if (!IntegerPentagon.isLowerThan(x, otherPhi, y)) {
                                    sub = false;
                                    if (!IntegerPentagon.isLowerEqual(phi, x, otherPhi, y)) {
                                        lb = false;
                                        // no symbolic relationship found
                                        break;
                                    }
                                }
                            } else if (lb) {
                                // maybe we can still prove lower bounds
                                if (!IntegerPentagon.isLowerEqual(phi, x, otherPhi, y)) {
                                    lb = false;
                                    // no symbolic relationship found
                                    break;
                                }
                            }
                        }
                        if (sub) {
                            specialSUBs[thisIdx].add(otherPhi);
                        }
                        if (lb) {
                            specialLBs[otherIdx].add(phi);
                        }
                    }
                } else {
                    // just do a per-PHI merge for all non-integer phis
                    Pentagon ptg = unevaluated(phi);
                    for (int j = 0; j < reachableInputIndices.size(); j++) {
                        ptg = ptg.merge(map.getOrUnrestricted(phi.valueAt(reachableInputIndices.get(j))));
                    }
                    results[thisIdx] = ptg;
                }
            }

            // Third pass: Construct integer pentagons
            for (int i = 0; i < iPhis.size(); i++) {
                int idx = iPhis.get(i);
                Set<ValueNode> lb = rawLBs[idx];
                Set<ValueNode> sub = rawSUBs[idx];
                /*
                 * We found symbolic bounds by merging the inputs and also by using inter-phi
                 * reasoning. All of those are correct bounds to be added up for the result.
                 */
                lb.addAll(specialLBs[idx]);
                sub.addAll(specialSUBs[idx]);
                // nodes should not store lower bounds to themselves
                lb.remove(phis.get(idx));
                // construct the pentagon
                results[idx] = Pentagon.of(intermediateRanges[idx], lb, sub);
            }

            return results;
        }

        @Override
        public Pentagon transfer(ValueNode node, DFAMap<Pentagon> map) {
            Pentagon transferResult = switch (node) {
                case ConstantNode cn ->
                    switch (cn.stamp(NodeView.DEFAULT)) {
                        case IntegerStamp iStamp ->
                            Pentagon.of(iStamp, Set.of(), Set.of());
                        case FloatStamp fStamp ->
                            Pentagon.of(fStamp);
                        /*
                         * If non-null object constants are disabled, we fall back to a simple
                         * non-null stamp. Non-null constants can only arise from constant nodes,
                         * therefore by disabling them here, no non-null object constants will exist
                         * thoughout the analysis.
                         */
                        case AbstractObjectStamp oStamp ->
                            oStamp.alwaysNull() || !useNonNullObjConstants ? Pentagon.of(oStamp) : Pentagon.of(cn);
                        default -> null;
                    };

                case LogicConstantNode cn ->
                    Pentagon.of(TriState.get(cn.getValue()));

                case ProxyNode proxy -> {
                    Pentagon value = map.getOrUnrestricted(proxy.value());
                    yield value instanceof IntegerPentagon iPtg
                                    /*
                                     * For integers, we can say that (since proxy nodes do not
                                     * change the value) the current result is larger or equal to
                                     * the input.
                                     */
                                    ? Pentagon.of(iPtg.range, setUnion(iPtg.lowerBounds, proxy.value()), iPtg.strictUpperBounds)
                                    // for all other values, we simply pass through the input
                                    : value;
                }

                case DynamicPiNode dpi -> {
                    Pentagon type = map.getOrUnrestricted(dpi.getOriginalNode());
                    if (!(type instanceof ObjectPentagon oPtg)) {
                        // no idea what this is, just return unrestricted
                        yield unrestricted(type);
                    }
                    if (oPtg.isConstant() && !oPtg.stamp.alwaysNull()) {
                        // nothing can be better than a constant with the specific type information
                        yield oPtg;
                    }
                    AbstractObjectStamp graphStamp = (AbstractObjectStamp) dpi.stamp(NodeView.DEFAULT);
                    // take type from graph
                    AbstractObjectStamp typeImproved = oPtg.stamp.asType(graphStamp.type(), graphStamp.isExactType(), graphStamp.isAlwaysArray());
                    // take nullability from node
                    Stamp improved = dpi.allowsNull() ? typeImproved : typeImproved.asNonNull();
                    /*
                     * DynamicPis may produce a non-unrestricted value even if its input is
                     * unevaluated. The case for PiNodes in countUnevaluatedInputs is reused for
                     * DynamicPis.
                     */
                    yield Pentagon.of((AbstractObjectStamp) improved);
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
                    Pentagon input = map.getOrUnrestricted(pi.getOriginalNode());
                    Pentagon rawResult = switch (input) {
                        case LogicPentagon ignored ->
                            // simply pass through logic values
                            input;
                        case IntegerPentagon iPtg -> {
                            Stamp piStamp = pi.piStamp();
                            if (isUnevaluated(input)) {
                                // we know original <= pi because original == pi
                                yield Pentagon.of((IntegerStamp) piStamp, Set.of(pi.getOriginalNode()), iPtg.strictUpperBounds);
                            } else {
                                Stamp improvedStamp = iPtg.range.improveWith(piStamp);
                                if (improvedStamp.isEmpty()) {
                                    /*
                                     * If we encounter incompatible values, we just return the pi
                                     * stamp. Doing so might reduce the accuracy of the result but
                                     * theoretically, all pis should lie below inferred facts anyway
                                     * which are scheduled with priority and with respect to control
                                     * flow analysis.
                                     *
                                     * If we reach here, our analysis fell short of the graph stamps
                                     * somewhere.
                                     */
                                    improvedStamp = piStamp;
                                }
                                // we know original <= pi because original == pi
                                yield Pentagon.of((IntegerStamp) improvedStamp, setUnion(iPtg.lowerBounds, pi.getOriginalNode()), iPtg.strictUpperBounds);
                            }
                        }
                        case FloatPentagon fPtg -> {
                            Stamp improved = fPtg.stamp.improveWith(pi.piStamp());
                            if (improved.isEmpty()) {
                                // same reasoning as above for integers
                                improved = pi.piStamp();
                            }
                            yield Pentagon.of((FloatStamp) improved);
                        }
                        case ObjectPentagon oPtg -> {
                            AbstractObjectStamp graphStamp = (AbstractObjectStamp) pi.stamp(NodeView.DEFAULT);
                            AbstractObjectStamp improved = (AbstractObjectStamp) oPtg.stamp.improveWith(pi.piStamp());
                            if (oPtg.isNonNullConstant() && !improved.isEmpty()) {
                                // such constants can not be improved
                                // also, the types are compatible
                                yield oPtg;
                            } else {
                                if (improved.isEmpty()) {
                                    // same reasoning as above for integers
                                    improved = (AbstractObjectStamp) pi.piStamp();
                                }
                                /*
                                 * Pis may encode some weird typecast, which are not properly
                                 * expressible using object stamps (e.g. intersection types). We do
                                 * not want to interfere here. Therefore, we drop all non-null
                                 * constant propagation and take the type of the pi's graph stamp.
                                 */
                                yield Pentagon.of(improved.asType(graphStamp.type(), graphStamp.isExactType(), graphStamp.isAlwaysArray()));
                            }
                        }
                    };

                    /*
                     * If we had conflicting information despite an evaluated input in the last
                     * iteration, we need to merge that result into the current result to uphold
                     * monotonicity.
                     */
                    yield map.hasNewlyEvaluatedInputs(pi) ? rawResult : merge(map.getOrUnevaluated(pi), rawResult);
                }

                // handle generals ValueProxies here (PIs are handled above)
                case ValueProxy proxy -> {
                    Pentagon value = map.getOrUnrestricted(proxy.getOriginalNode());
                    yield value instanceof IntegerPentagon iPtg
                                    /*
                                     * For integers, we can say that (since proxy nodes do not
                                     * change the value) the current result is larger or equal to
                                     * the input.
                                     */
                                    ? Pentagon.of(iPtg.range, setUnion(iPtg.lowerBounds, proxy.getOriginalNode()), iPtg.strictUpperBounds)
                                    // for all other values, we simply pass through the input
                                    : value;
                }

                /*
                 * Conditionals may produce stronger-than-unrestricted results while the condition
                 * is still unevaluated. An appropriate case is implemented in
                 * countUnevaluatedInputs.
                 */
                case ConditionalNode conditional ->
                    switch (map.getOrUnrestricted(conditional.condition()).asLogic().logic) {
                        case TRUE -> map.getOrUnrestricted(conditional.trueValue());
                        case FALSE -> map.getOrUnrestricted(conditional.falseValue());
                        case UNKNOWN -> map.getOrUnrestricted(conditional.trueValue()).merge(map.getOrUnrestricted(conditional.falseValue()));
                    };

                /*
                 * This node may produce an output if ony one input is evaluated. An according case
                 * is provided in countUnevaluatedInputs.
                 */
                case ShortCircuitOrNode or -> {
                    TriState x = map.getOrUnrestricted(or.getX()).asLogic().logic;
                    x = x.isUnknown() ? x : TriState.get(x.toBoolean() ^ or.isXNegated());
                    TriState y = map.getOrUnrestricted(or.getY()).asLogic().logic;
                    y = y.isUnknown() ? y : TriState.get(y.toBoolean() ^ or.isYNegated());
                    if (x == TriState.TRUE || y == TriState.TRUE) {
                        yield LogicPentagon.TRUE;
                    } else if (x == TriState.FALSE && y == TriState.FALSE) {
                        yield LogicPentagon.FALSE;
                    } else {
                        yield LogicPentagon.UNRESTRICTED;
                    }
                }

                case ReadNode read ->
                    Pentagon.ofGeneralStamp(read.stamp(NodeView.DEFAULT));

                case FloatingReadNode read ->
                    Pentagon.ofGeneralStamp(read.stamp(NodeView.DEFAULT));

                case AllocatedObjectNode allocObj ->
                    Pentagon.of((AbstractObjectStamp) allocObj.stamp(NodeView.DEFAULT));

                /*
                 * All logic unary operations are all handled here
                 * 
                 * If the input is not evaluated yet, we do not touch this node (i.e. return
                 * unrestricted). An example for this is an int to float conversion that returns
                 * non-NaN for unevaluated inputs which blocks further discovery of possible
                 * constants.
                 */
                case UnaryOpLogicNode unary -> {
                    if (!map.isEvaluated(unary.getValue()) || !(map.getOrUnrestricted(unary.getValue()) instanceof StampPentagon sPtg)) {
                        yield unrestricted(unary);
                    }
                    yield Pentagon.of(unary.tryFold(sPtg.getStamp()));
                }

                /*
                 * Logic binary operations are handled here.
                 *
                 * Some of these nodes may need special handling because we might be able to do
                 * additional reasoning (more than pure stamps are capable of) for some of these
                 * nodes using the extra information from pentagons.
                 */
                case IntegerLessThanNode lt -> {
                    IntegerPentagon px = map.getOrUnrestricted(lt.getX()).asInteger();
                    IntegerPentagon py = map.getOrUnrestricted(lt.getY()).asInteger();
                    TriState eval;
                    if (IntegerPentagon.isLowerThan(px, lt.getY(), py)) {
                        eval = TriState.TRUE;
                    } else if (IntegerPentagon.isLowerEqual(lt.getY(), py, lt.getX(), px)) {
                        eval = TriState.FALSE;
                    } else {
                        eval = lt.tryFold(px.range, py.range);
                    }
                    yield Pentagon.of(eval);
                }

                case IntegerBelowNode lt -> {
                    IntegerPentagon px = map.getOrUnrestricted(lt.getX()).asInteger();
                    IntegerPentagon py = map.getOrUnrestricted(lt.getY()).asInteger();
                    /*
                     * We can prove the unsigned comparison using upper bounds in the obvious case
                     * (x >= 0) and also if both x and y are negative.
                     */
                    TriState eval;
                    if (px.range.lowerBound() >= 0 || px.range.upperBound() < 0 && py.range.upperBound() < 0) {
                        if (IntegerPentagon.isLowerThan(px, lt.getY(), py)) {
                            eval = TriState.TRUE;
                        } else if (IntegerPentagon.isLowerEqual(lt.getY(), py, lt.getX(), px)) {
                            eval = TriState.FALSE;
                        } else {
                            eval = lt.tryFold(px.range, py.range);
                        }
                    } else {
                        eval = lt.tryFold(px.range, py.range);
                    }
                    yield Pentagon.of(eval);
                }

                case PointerEqualsNode peq -> {
                    if (!map.isEvaluated(peq.getX()) || !map.isEvaluated(peq.getY())) {
                        // we do not want to deal with general pointer stamps
                        yield LogicPentagon.UNRESTRICTED;
                    }
                    ObjectPentagon x = map.getOrUnrestricted(peq.getX()).asObject();
                    ObjectPentagon y = map.getOrUnrestricted(peq.getY()).asObject();
                    if (x.isConstant() && y.isConstant()) {
                        // if both sides are constant we can dis-/prove equality
                        yield Pentagon.of(x.equals(y));
                    }
                    // else we fall back to a simple tryFold
                    yield Pentagon.of(peq.tryFold(x.stamp, y.stamp));
                }

                /*
                 * This transfer case is just a catch-all to allow for general stamp based reasoning
                 * for such nodes.
                 */
                case BinaryOpLogicNode binary -> {
                    /*
                     * Generally, binary nodes that have one input edge not evaluated yet may
                     * produce misleading results, therefore we skip them here for now by just
                     * returning unrestricted.
                     */
                    if (!map.isEvaluated(binary.getX()) || !map.isEvaluated(binary.getY())) {
                        yield unrestricted(binary);
                    }
                    /*
                     * As far as I am aware, there are no BinaryOpLogicNodes that take other logic
                     * values as inputs. Therefore, we assume both inputs to be StampPentagons.
                     */
                    Pentagon rawX = map.getOrUnrestricted(binary.getX());
                    Pentagon rawY = map.getOrUnrestricted(binary.getY());
                    if (!(rawX instanceof StampPentagon xPtg) || !(rawY instanceof StampPentagon yPtg)) {
                        throw GraalError.shouldNotReachHere("Non-Stamp pentagons (%s, %s) for BinaryOpLogicNode".formatted(rawX, rawY));
                    }
                    yield Pentagon.of(binary.tryFold(xPtg.getStamp(), yPtg.getStamp()));
                }

                /*
                 * General unary nodes are handled here
                 * 
                 * Integer to floating point is never NaN (and therefore produces a non-unrestricted
                 * value even though its input is unevaluated). An according case is provided in
                 * countUnevaluatedInputs.
                 */
                case FloatConvertNode fConv ->
                    Pentagon.ofGeneralStamp(fConv.foldStamp(map.getOrUnrestricted(fConv.getValue()).asStamp().getStamp()));

                case CompressionNode cpr -> {
                    if (!map.isEvaluated(cpr.getValue())) {
                        yield unrestricted(cpr);
                    }
                    ObjectPentagon vPtg = map.getOrUnrestricted(cpr.getValue()).asObject();
                    AbstractObjectStamp rStamp = (AbstractObjectStamp) cpr.foldStamp(vPtg.stamp);
                    if (vPtg.isNonNullConstant()) {
                        // do the de-/compression manually
                        Constant transformed = cpr.convert(vPtg.constant, constantReflection);
                        yield Pentagon.of(rStamp, transformed);
                    } else {
                        yield Pentagon.of(rStamp);
                    }
                }

                /*
                 * This transfer case is just a catch-all to allow for general stamp based reasoning
                 * for general unary nodes.
                 */
                case UnaryNode uNode -> {
                    // protect against problems with unevaluated inputs for unknown unary nodes
                    if (!map.isEvaluated(uNode.getValue())) {
                        yield unrestricted(uNode);
                    }
                    Pentagon input = map.getOrUnrestricted(uNode.getValue());
                    if (input instanceof StampPentagon sPtg) {
                        yield Pentagon.ofGeneralStamp(uNode.foldStamp(sPtg.getStamp()));
                    } else {
                        // no idea how to handle this node, therefore we default to unrestricted
                        yield unrestricted(uNode);
                    }
                }

                case AddNode add -> {
                    if (add.stamp(NodeView.DEFAULT).isIntegerStamp()) {
                        IntegerPentagon px = map.getOrUnrestricted(add.getX()).asInteger();
                        IntegerPentagon py = map.getOrUnrestricted(add.getY()).asInteger();
                        IntegerStamp nuStamp = (IntegerStamp) add.foldStamp(px.range, py.range);
                        Set<ValueNode> nuLb;
                        Set<ValueNode> nuSub;
                        if (!IntegerStamp.addCanOverflow(px.range, py.range)) {
                            if (py.range.lowerBound() >= 0) {
                                nuLb = setUnion(px.lowerBounds, add.getX());
                            } else {
                                nuLb = Set.of();
                            }
                            if (px.range.lowerBound() >= 0) {
                                nuLb = setUnion(nuLb, py.lowerBounds, add.getY());
                            }
                            if (py.range.upperBound() < 0) {
                                nuSub = setUnion(px.strictUpperBounds, add.getX());
                            } else if (py.range.upperBound() == 0) {
                                nuSub = px.strictUpperBounds;
                            } else {
                                nuSub = Set.of();
                            }
                            if (px.range.upperBound() < 0) {
                                nuSub = setUnion(nuSub, py.strictUpperBounds, add.getY());
                            } else if (px.range.upperBound() == 0) {
                                nuSub = setUnion(nuSub, py.strictUpperBounds);
                            }
                        } else {
                            nuLb = Set.of();
                            nuSub = Set.of();
                        }
                        yield Pentagon.of(nuStamp, nuLb, nuSub);
                    } else {
                        GraalError.guarantee(add.stamp(NodeView.DEFAULT).isFloatStamp(), "Unexpected stamp %s for AddNode", add.stamp(NodeView.DEFAULT));
                        yield Pentagon.of((FloatStamp) add.foldStamp(map.getOrUnrestricted(add.getX()).asFloat().stamp,
                                        map.getOrUnrestricted(add.getY()).asFloat().stamp));
                    }
                }

                case UnsignedRightShiftNode ushr -> {
                    IntegerStamp stmp = (IntegerStamp) ushr.foldStamp(map.getOrUnrestricted(ushr.getX()).asInteger().range, map.getOrUnrestricted(ushr.getY()).asInteger().range);
                    Set<ValueNode> sub = Set.of();
                    if (ushr.getX() instanceof AddNode add && ushr.getY().isJavaConstant() && ushr.getY().asJavaConstant().asLong() == 1) {
                        /*
                         * The addition, the unsigned shift right and the constant form a pattern in
                         * this case. We evaluate the pattern here, at the node forming the output
                         * of the pattern. To ensure proper evaluation of the pattern in the future,
                         * we need to register all nodes of the pattern, starting with the output
                         * node (the shift) and then all upper nodes of the pattern in no particular
                         * order (the addition and the constant).
                         */
                        map.registerPattern(ushr, add, ushr.getY());
                        IntegerPentagon aX = map.getOrUnrestricted(add.getX()).asInteger();
                        IntegerPentagon aY = map.getOrUnrestricted(add.getY()).asInteger();
                        if (aX.range.lowerBound() >= 0 && aY.range.lowerBound() >= 0) {
                            // arithmetic mean pattern, all common upper bounds of the inputs of the
                            // addition are also upper bounds of the arithmetic mean result
                            sub = setIntersection(aX.strictUpperBounds, aY.strictUpperBounds);
                            // also the upper bound is at most the maximum of the add's inputs
                            stmp = (IntegerStamp) stmp.improveWith(IntegerStamp.create(stmp.getBits(), CodeUtil.minValue(stmp.getBits()),
                                            Math.max(aX.getStamp().upperBound(), aY.getStamp().upperBound())));
                        }
                    }
                    yield Pentagon.of(stmp, Set.of(), sub);
                }

                /*
                 * This transfer case is just a catch-all to allow for general stamp based reasoning
                 * for general binary nodes.
                 */
                case BinaryNode bNode -> {
                    // protect against problems with unevaluated inputs for unknown binary nodes
                    if (!map.isEvaluated(bNode.getX()) || !map.isEvaluated(bNode.getY())) {
                        yield unrestricted(bNode);
                    }
                    Pentagon rawX = map.getOrUnrestricted(bNode.getX());
                    Pentagon rawY = map.getOrUnrestricted(bNode.getY());
                    if (rawX instanceof StampPentagon xPtg && rawY instanceof StampPentagon yPtg) {
                        // apply stamp based reasoning
                        yield Pentagon.ofGeneralStamp(bNode.foldStamp(xPtg.getStamp(), yPtg.getStamp()));
                    } else {
                        // no idea how to handle this node, default to unrestricted
                        yield unrestricted(bNode);
                    }
                }

                /*
                 * Exact arithmetic related nodes.
                 */
                case IntegerExactOverflowNode exactBinaryOverflow -> Pentagon.of(isOverflowing(exactBinaryOverflow,
                                map.getOrUnrestricted(exactBinaryOverflow.getX()).asInteger().range,
                                map.getOrUnrestricted(exactBinaryOverflow.getY()).asInteger().range));

                case IntegerNegExactOverflowNode exactNegOverflow -> {
                    IntegerStamp valueStamp = map.getOrUnrestricted(exactNegOverflow.getValue()).asInteger().range;
                    PrimitiveConstant value = (PrimitiveConstant) valueStamp.asConstant();
                    yield value != null
                                    ? Pentagon.of(value.asLong() == CodeUtil.minValue(valueStamp.getBits()))
                                    : LogicPentagon.UNRESTRICTED;
                }

                case IntegerNegExactSplitNode exactNegate -> {
                    IntegerStamp valueStamp = map.getOrUnrestricted(exactNegate.getValue()).asInteger().range;
                    yield Pentagon.of((IntegerStamp) ArithmeticOpTable.forStamp(valueStamp).getNeg().foldStamp(valueStamp), Set.of(), Set.of());
                }

                case BinaryIntegerExactArithmeticSplitNode exactBinary -> {
                    IntegerPentagon x = map.getOrUnrestricted(exactBinary.getX()).asInteger();
                    IntegerPentagon y = map.getOrUnrestricted(exactBinary.getY()).asInteger();
                    Stamp splitStamp = exactBinary.stamp(NodeView.DEFAULT);
                    ArithmeticOpTable table = ArithmeticOpTable.forStamp(splitStamp);
                    final ArithmeticOpTable.BinaryOp<?> op;
                    Set<ValueNode> subs;
                    Set<ValueNode> lbs;
                    switch (exactBinary) {
                        case IntegerAddExactSplitNode ignored -> {
                            op = table.getAdd();
                            if (y.range.lowerBound() >= 0) {
                                lbs = setUnion(x.lowerBounds, exactBinary.getX());
                            } else {
                                lbs = Set.of();
                            }
                            if (x.range.lowerBound() >= 0) {
                                lbs = setUnion(lbs, y.lowerBounds, exactBinary.getY());
                            }
                            if (y.range.upperBound() < 0) {
                                subs = setUnion(x.strictUpperBounds, exactBinary.getX());
                            } else if (y.range.upperBound() == 0) {
                                subs = x.strictUpperBounds;
                            } else {
                                subs = Set.of();
                            }
                            if (x.range.upperBound() < 0) {
                                subs = setUnion(subs, y.strictUpperBounds, exactBinary.getY());
                            } else if (x.range.upperBound() == 0) {
                                subs = setUnion(subs, y.strictUpperBounds);
                            }
                        }
                        case IntegerSubExactSplitNode ignored -> {
                            op = table.getSub();
                            if (y.range.lowerBound() > 0) {
                                subs = Set.of(exactBinary.getX());
                            } else {
                                subs = Set.of();
                            }
                            if (y.range.upperBound() <= 0) {
                                lbs = setUnion(x.lowerBounds, exactBinary.getX());
                            } else {
                                lbs = Set.of();
                            }
                        }
                        case IntegerMulExactSplitNode ignored -> {
                            op = table.getMul();
                            subs = Set.of();
                            lbs = Set.of();
                        }
                        default -> throw GraalError.shouldNotReachHere("Unknown binary exact arithmetic split node " + exactBinary.getClass().getName());
                    }
                    yield Pentagon.of((IntegerStamp) op.foldStamp(x.range, y.range), lbs, subs);
                }

                case null ->
                    throw GraalError.shouldNotReachHere("can not evaluate node 'null'");

                default ->
                    unrestricted(node);
            };
            return normalize(transferResult);
        }

        @Override
        public boolean[] splitReachability(ControlSplitNode split, DFAMap<Pentagon> map) {
            switch (split) {
                case IfNode ifNode -> {
                    // boolean[] {trueSuccessor, falseSuccessor}
                    TriState condition = map.getOrUnrestricted(ifNode.condition()).asLogic().logic;
                    return switch (condition) {
                        case UNKNOWN -> TRUE_TRUE;
                        case TRUE -> TRUE_FALSE;
                        case FALSE -> FALSE_TRUE;
                    };
                }
                case IntegerSwitchNode switchNode -> {
                    Stamp value = map.getOrUnrestricted(switchNode.value()).asInteger().range;
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
                    } else if (!((IntegerStamp) value).canBeZero()) {
                        // all non 0 successors are reachable
                        for (int i = 0; i < switchNode.keyCount(); i++) {
                            reach[switchNode.keySuccessorIndex(i)] |= switchNode.intKeyAt(i) != 0;
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
                    return switch (isOverflowing(exactBinary, map.getOrUnrestricted(exactBinary.getX()).asInteger().range,
                                    map.getOrUnrestricted(exactBinary.getY()).asInteger().range)) {
                        case UNKNOWN -> TRUE_TRUE;
                        case TRUE -> FALSE_TRUE;
                        case FALSE -> TRUE_FALSE;
                    };
                }
                case IntegerNegExactSplitNode exactNegate -> {
                    // boolean[] {defaultSuccessor, overflowSuccessor}
                    IntegerStamp valueStamp = map.getOrUnrestricted(exactNegate.getValue()).asInteger().range;
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
        public Pentagon[][] calcInferrableValues(ValueNode node, DFAMap<Pentagon> map) {
            return switch (node) {
                case IsNullNode nullNode -> {
                    ValueNode value = nullNode.getValue();
                    AbstractPointerStamp unrest = unrestricted(value).asObject().getStamp();
                    yield AnalysisDomainDefinition.ifInference(Pentagon.class,
                                    AnalysisDomainDefinition.unaryLogicInference(Pentagon.class,
                                                    Pentagon.of((AbstractObjectStamp) unrest.asAlwaysNull())),
                                    AnalysisDomainDefinition.unaryLogicInference(Pentagon.class,
                                                    Pentagon.of((AbstractObjectStamp) unrest.asNonNull())));
                }
                case CompareNode compareNode -> {
                    ValueNode x = compareNode.getX();
                    ValueNode y = compareNode.getY();
                    Stamp unrestX = x.stamp(NodeView.DEFAULT).unrestricted();
                    Stamp unrestY = y.stamp(NodeView.DEFAULT).unrestricted();
                    StampPentagon xVal = map.getOrUnrestricted(x).asStamp();
                    StampPentagon yVal = map.getOrUnrestricted(y).asStamp();
                    // only calculate additional information for X and Y caused by this compare
                    Stamp rXt = compareNode.getSucceedingStampForX(false, unrestX, yVal.getStamp());
                    Stamp rYt = compareNode.getSucceedingStampForY(false, xVal.getStamp(), unrestY);
                    Stamp rXf = compareNode.getSucceedingStampForX(true, unrestX, yVal.getStamp());
                    Stamp rYf = compareNode.getSucceedingStampForY(true, xVal.getStamp(), unrestY);
                    rXt = rXt == null ? unrestX : rXt;
                    rYt = rYt == null ? unrestY : rYt;
                    rXf = rXf == null ? unrestX : rXf;
                    rYf = rYf == null ? unrestY : rYf;
                    if (xVal instanceof IntegerPentagon xInt) {
                        IntegerPentagon yInt = yVal.asInteger();
                        Set<ValueNode> subXt, subYt, subXf, subYf;
                        Set<ValueNode> lbXt, lbYt, lbXf, lbYf;
                        switch (compareNode) {
                            case IntegerBelowNode below -> {
                                // we can only infer a signed strict upper bound in the unsigned
                                // case if y is not larger than signed int max
                                if (yInt.range.lowerBound() >= 0) {
                                    // same as standard lower-than
                                    subXt = setUnion(yInt.strictUpperBounds, y);
                                    lbYt = setUnion(xInt.lowerBounds, x);
                                } else {
                                    subXt = Set.of();
                                    lbYt = Set.of();
                                }
                                subYt = Set.of();
                                lbXt = Set.of();
                                subXf = Set.of();
                                lbYf = Set.of();
                                if (xInt.range.lowerBound() >= 0) {
                                    // same as standard lower-than
                                    subYf = xInt.strictUpperBounds;
                                    lbXf = setUnion(yInt.lowerBounds, y);
                                } else {
                                    subYf = Set.of();
                                    lbXf = Set.of();
                                }
                            }
                            case IntegerLowerThanNode lessThan -> {
                                // x is lower than y, therefore all subs of y also are subs for x,
                                // including y itself
                                subXt = setUnion(yInt.strictUpperBounds, y);
                                subYt = Set.of();
                                // we also store the inverse information (lower bounds for y)
                                lbXt = Set.of();
                                lbYt = setUnion(xInt.lowerBounds, x);
                                // y is lower or equal to x, therefore all subs of x are also subs
                                // for y, but x is not a sub of y
                                subXf = Set.of();
                                subYf = xInt.strictUpperBounds;
                                // we also store the inverse information (lower bounds for x), also
                                // y is an additional lower bound to x
                                lbXf = setUnion(yInt.lowerBounds, y);
                                lbYf = Set.of();
                            }
                            case IntegerEqualsNode iEquals -> {
                                // if x and y are equal, they have the same upper bounds
                                subXt = yInt.strictUpperBounds;
                                subYt = xInt.strictUpperBounds;
                                lbXt = yInt.lowerBounds;
                                lbYt = xInt.lowerBounds;
                                // if not equal, we can not infer any new strict upper bounds
                                subXf = Set.of();
                                subYf = Set.of();
                                lbXf = Set.of();
                                lbYf = Set.of();
                            }
                            default -> {
                                // no new information regarding strict upper bounds
                                subXt = Set.of();
                                subYt = Set.of();
                                subXf = Set.of();
                                subYf = Set.of();
                                lbXt = Set.of();
                                lbYt = Set.of();
                                lbXf = Set.of();
                                lbYf = Set.of();
                            }
                        }
                        /*
                         * Here we might encounter impossible values (at inferences in unreachable
                         * branches), which are canonicalized to UNEVALUATED. Since we should not
                         * infer UNEVALUATED inputs, we default to UNRESTRICTED.
                         */
                        Pentagon pXt = Pentagon.of((IntegerStamp) rXt, lbXt, subXt);
                        pXt = pXt.isUnevaluated() ? unrestricted(pXt) : pXt;
                        Pentagon pYt = Pentagon.of((IntegerStamp) rYt, lbYt, subYt);
                        pYt = pYt.isUnevaluated() ? unrestricted(pYt) : pYt;
                        Pentagon pXf = Pentagon.of((IntegerStamp) rXf, lbXf, subXf);
                        pXf = pXf.isUnevaluated() ? unrestricted(pXf) : pXf;
                        Pentagon pYf = Pentagon.of((IntegerStamp) rYf, lbYf, subYf);
                        pYf = pYf.isUnevaluated() ? unrestricted(pYf) : pYf;
                        yield AnalysisDomainDefinition.ifInference(Pentagon.class,
                                        // true branch
                                        AnalysisDomainDefinition.binaryLogicInference(Pentagon.class, pXt, pYt),
                                        // false branch
                                        AnalysisDomainDefinition.binaryLogicInference(Pentagon.class, pXf, pYf));
                    } else if (node instanceof PointerEqualsNode peq &&
                                    peq.getX().stamp(NodeView.DEFAULT).isObjectStamp() &&
                                    peq.getY().stamp(NodeView.DEFAULT).isObjectStamp()) {
                        ObjectPentagon xObj = xVal.asObject();
                        ObjectPentagon yObj = yVal.asObject();
                        final ObjectPentagon pXt, pYt;
                        if (xObj.isNonNullConstant()) {
                            pYt = xObj;
                        } else {
                            pYt = Pentagon.of((AbstractObjectStamp) rYt);
                        }
                        if (yObj.isNonNullConstant()) {
                            pXt = yObj;
                        } else {
                            pXt = Pentagon.of((AbstractObjectStamp) rXt);
                        }
                        yield AnalysisDomainDefinition.ifInference(Pentagon.class,
                                        // true branch
                                        AnalysisDomainDefinition.binaryLogicInference(Pentagon.class, pXt, pYt),
                                        // false branch
                                        AnalysisDomainDefinition.binaryLogicInference(Pentagon.class,
                                                        Pentagon.of((AbstractObjectStamp) rXf),
                                                        Pentagon.of((AbstractObjectStamp) rYf)));
                    } else {
                        yield AnalysisDomainDefinition.ifInference(Pentagon.class,
                                        // true branch
                                        AnalysisDomainDefinition.binaryLogicInference(Pentagon.class,
                                                        Pentagon.ofGeneralStamp(rXt),
                                                        Pentagon.ofGeneralStamp(rYt)),
                                        // false branch
                                        AnalysisDomainDefinition.binaryLogicInference(Pentagon.class,
                                                        Pentagon.ofGeneralStamp(rXf),
                                                        Pentagon.ofGeneralStamp(rYf)));
                    }
                }
                case IntegerSwitchNode iSwitch -> {
                    IntegerStamp vUnev = (IntegerStamp) iSwitch.switchValue().stamp(NodeView.DEFAULT).empty();
                    IntegerStamp[] inferredStamps = new IntegerStamp[iSwitch.getSuccessorCount()];
                    Arrays.fill(inferredStamps, vUnev);
                    for (int i = 0; i < iSwitch.keyCount(); i++) {
                        IntegerStamp keyStamp = IntegerStamp.createConstant(vUnev.getBits(), iSwitch.intKeyAt(i));
                        int branch = iSwitch.keySuccessorIndex(i);
                        inferredStamps[branch] = (IntegerStamp) inferredStamps[branch].meet(keyStamp);
                    }
                    Pentagon[] inferences = new Pentagon[inferredStamps.length];
                    for (int i = 0; i < inferredStamps.length; i++) {
                        if (inferredStamps[i].isEmpty() || inferredStamps[i].isUnrestricted()) {
                            /*
                             * The information this inference is calculated by is static, and we
                             * were not able to calculate an inference here. Therefore, we will
                             * never calculate an inference here, therefore we can return null.
                             */
                            inferences[i] = null;
                        } else {
                            inferences[i] = Pentagon.of(inferredStamps[i], Set.of(), Set.of());
                        }
                    }
                    yield AnalysisDomainDefinition.switchInference(Pentagon.class, inferences);
                }
                default -> null;
            };
        }

        @Override
        public int countUnevaluatedInputs(ValueNode node, DFAMap<Pentagon> map) {
            return switch (node) {
                case ProxyNode proxy ->
                    AnalysisDomainDefinition.countUnevaluated(map, proxy.getOriginalNode());
                case ValueProxy proxy ->
                    AnalysisDomainDefinition.countUnevaluated(map, proxy.getOriginalNode());
                case ConditionalNode conditional ->
                    AnalysisDomainDefinition.countUnevaluated(map,
                                    conditional.condition(),
                                    conditional.trueValue(),
                                    conditional.falseValue());
                case ShortCircuitOrNode so ->
                    AnalysisDomainDefinition.countUnevaluated(map, so.getX(), so.getY());
                case UnsignedRightShiftNode ushr -> {
                    int unev = AnalysisDomainDefinition.countUnevaluated(map, ushr.getX(), ushr.getY());
                    if (ushr.getX() instanceof AddNode add && ushr.getY().isJavaConstant() && ushr.getY().asJavaConstant().asLong() == 1) {
                        // also count all inputs of pattern
                        unev += AnalysisDomainDefinition.countUnevaluated(map, add.getX(), add.getY());
                    }
                    yield unev;
                }
                case FloatConvertNode fconv ->
                    fconv.getFloatConvert().getCategory() == FloatConvertCategory.IntegerToFloatingPoint
                                    // int to float conversions yield a non-NaN result for
                                    // unevaluated inputs
                                    ? AnalysisDomainDefinition.countUnevaluated(map, fconv.getValue())
                                    // treat all other float conversions normally
                                    : AnalysisDomainDefinition.super.countUnevaluatedInputs(node, map);
                case AddNode add ->
                    AnalysisDomainDefinition.countUnevaluated(map, add.getX(), add.getY());
                case BinaryIntegerExactArithmeticSplitNode binaryExactSplit ->
                    AnalysisDomainDefinition.countUnevaluated(map, binaryExactSplit.getX(), binaryExactSplit.getY());
                default ->
                    AnalysisDomainDefinition.super.countUnevaluatedInputs(node, map);
            };
        }

        @Override
        public boolean supportsWidening() {
            return true;
        }

        @Override
        public void widen(List<ValuePhiNode> phis, IntList reachableInputIndices, Pentagon[] result, DFAMap<Pentagon> map, int evalCnt) {
            if (evalCnt >= widenAfter) {
                // for all phis
                for (int i = 0; i < phis.size(); i++) {
                    ValuePhiNode phi = phis.get(i);
                    Pentagon prelim = result[i];
                    if (isEqual(map.getOrUnevaluated(phi), prelim)) {
                        // we do not need to widen here, this phi was not updated
                        continue;
                    }
                    if ((evalCnt == widenAfter) && prelim instanceof IntegerPentagon iPtg) {
                        // special widening heuristic to catch non-overflowing counted loops
                        if (iPtg.range.lowerBound() >= 0) {
                            // positive integer
                            IntegerPentagon lastStep = map.getOrUnevaluated(phi).asInteger();
                            if (iPtg.range.lowerBound() == lastStep.range.lowerBound()) {
                                // this may be a loop variable that counts upwards
                                int bits = iPtg.range.getBits();
                                long mayBeSet = (-1 << Math.min(Long.numberOfTrailingZeros(iPtg.range.mayBeSet()), Long.numberOfTrailingZeros(lastStep.range.mayBeSet()))) & CodeUtil.maxValue(bits);
                                long topMask = ~CodeUtil.mask(Long.numberOfTrailingZeros(mayBeSet));
                                long upper = CodeUtil.maxValue(bits) & topMask;
                                if (upper > iPtg.range.upperBound()) {
                                    // given the last step, it looks like there will be another step
                                    IntegerStamp wideAttempt = IntegerStamp.create(bits, iPtg.range.lowerBound(), upper, 0, mayBeSet);
                                    if (stampWeaker(phi.stamp(NodeView.DEFAULT), wideAttempt)) {
                                        // we actually have meaningful information gain here
                                        result[i] = Pentagon.of(wideAttempt, Set.of(), Set.of());
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                    if (!(prelim instanceof StampPentagon sPtg)) {
                        assert prelim instanceof LogicPentagon : "this should never occur due to Pentagon class structure";
                        result[i] = LogicPentagon.UNRESTRICTED;
                    } else {
                        // for stamp based widening, we drop all extra info and try to widen to just
                        // the graph stamp
                        Stamp base = sPtg.getStamp();
                        result[i] = stampWeaker(phi.stamp(NodeView.DEFAULT), base)
                                        // if possible, generalize range to graph stamp
                                        ? Pentagon.ofGeneralStamp(phi.stamp(NodeView.DEFAULT))
                                        // else generalize to unrestricted
                                        : unrestricted(phi);
                    }
                }
            }
        }

        @Override
        public TriState isWeakerThan(Pentagon x, Pentagon y) {
            if (x.equals(y) || x.isUnevaluated() || y.isUnrestricted()) {
                // nothing is stronger than empty and nothing is weaker than unrestricted
                return TriState.FALSE;
            } else if (x.isUnrestricted() || y.isUnevaluated()) {
                // nothing is weaker than unrestricted and nothing is stronger than empty
                return TriState.TRUE;
            }
            // we have differing stamps here
            GraalError.guarantee(x.getClass().equals(y.getClass()), "incompatible pentagon comparison %s weakerThan %s", x, y);
            switch (x) {
                case LogicPentagon lx -> {
                    assert y instanceof LogicPentagon ly && lx.logic.isKnown() && ly.logic.isKnown() &&
                                    lx.logic != ly.logic : "expected TRUE weakerThan FALSE (or FALSE weakerThan TRUE), got %s weakerThan %s".formatted(lx.logic.name(),
                                                    ((LogicPentagon) y).logic.name());
                    return TriState.UNKNOWN;
                }
                case IntegerPentagon vx -> {
                    IntegerPentagon vy = (IntegerPentagon) y;
                    IntegerStamp mergedRange = (IntegerStamp) vx.range.meet(vy.range);
                    boolean rangeIsWeakerEqual = mergedRange.equals(vx.range);
                    boolean rangeIsStrongerEqual = mergedRange.equals(vy.range);
                    if (!rangeIsWeakerEqual && !rangeIsStrongerEqual) {
                        // we cannot recover from uncomparable ranges
                        return TriState.UNKNOWN;
                    }
                    if (rangeIsWeakerEqual && isSubSet(vx.lowerBounds, vy.lowerBounds) && isSubSet(vx.strictUpperBounds, vy.strictUpperBounds)) {
                        // we checked all conditions for weaker and ruled out equality
                        return TriState.TRUE;
                    }
                    // weaker checks failed, we try checking for stronger
                    if (rangeIsStrongerEqual && isSubSet(vy.lowerBounds, vx.lowerBounds) && isSubSet(vy.strictUpperBounds, vx.strictUpperBounds)) {
                        // we checked all conditions for stronger and ruled out equality
                        return TriState.FALSE;
                    }
                    // if x is neither weaker nor stronger than y, then x and y are uncomparable
                    return TriState.UNKNOWN;
                }
                case FloatPentagon fx -> {
                    // just merge stamps and check
                    FloatPentagon fy = y.asFloat();
                    Stamp merge = fx.stamp.meet(fy.stamp);
                    if (merge.equals(fx.stamp)) {
                        return TriState.TRUE;
                    } else if (merge.equals(fy.stamp)) {
                        return TriState.FALSE;
                    } else {
                        return TriState.UNKNOWN;
                    }
                }
                case ObjectPentagon ox -> {
                    ObjectPentagon oy = y.asObject();
                    if (ox.isConstant() && oy.isConstant()) {
                        // different constants are not comparable
                        return TriState.UNKNOWN;
                    }
                    AbstractObjectStamp merge = (AbstractObjectStamp) ox.stamp.meet(oy.stamp);
                    // if there are constants involved, only check for compatibility, not
                    // stamp-equalness
                    if (ox.isConstant()) {
                        // if compatible, y is weaker than x (represented by FALSE)
                        return merge.equals(oy.stamp) ? TriState.FALSE : TriState.UNKNOWN;
                    }
                    if (oy.isConstant()) {
                        return merge.equals(ox.stamp) ? TriState.TRUE : TriState.UNKNOWN;
                    }
                    return merge.equals(ox.stamp)
                                    ? TriState.TRUE
                                    : merge.equals(oy.stamp)
                                                    ? TriState.FALSE
                                                    : TriState.UNKNOWN;
                }
            }
        }

        @Override
        public boolean isUnevaluated(Pentagon elem) {
            return elem.isUnevaluated();
        }

        @Override
        public boolean isUnrestricted(Pentagon elem) {
            return elem.isUnrestricted();
        }

        /**
         * Check if op results in an overflow given the input stamps.
         *
         * @return {@link TriState#UNKNOWN} if one or both inputs are not constant.
         */
        private static TriState isOverflowing(ValueNode op, IntegerStamp xStamp, IntegerStamp yStamp) {
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
                    case IntegerAddExactOverflowNode ignored -> LoopUtility.addExact(bits, x, y);
                    case IntegerSubExactOverflowNode ignored -> LoopUtility.subtractExact(bits, x, y);
                    case IntegerMulExactOverflowNode ignored -> LoopUtility.multiplyExact(bits, x, y);
                    case IntegerAddExactSplitNode ignored -> LoopUtility.addExact(bits, x, y);
                    case IntegerSubExactSplitNode ignored -> LoopUtility.subtractExact(bits, x, y);
                    case IntegerMulExactSplitNode ignored -> LoopUtility.multiplyExact(bits, x, y);
                    default -> throw GraalError.shouldNotReachHere("unexpected IntegerExactNode " + op.getClass().getSimpleName());
                }
                // if the operation succeeded, we know it does not overflow
                return TriState.FALSE;
            } catch (ArithmeticException ignored) {
                // if the operation failed, we know it does overflow
                return TriState.TRUE;
            }
        }

        private static Set<ValueNode> setUnion(Set<ValueNode> x, ValueNode y) {
            if (x.contains(y)) {
                return x;
            }
            Set<ValueNode> res = new HashSet<>(x == null ? Set.of() : x);
            res.add(y);
            return res;
        }

        private static Set<ValueNode> setUnion(Set<ValueNode> x, Set<ValueNode> y) {
            Set<ValueNode> res = new HashSet<>(x == null ? Set.of() : x);
            res.addAll(y);
            return res;
        }

        private static Set<ValueNode> setUnion(Set<ValueNode> x, Set<ValueNode> y, ValueNode z) {
            Set<ValueNode> res = new HashSet<>(x == null ? Set.of() : x);
            res.addAll(y);
            res.add(z);
            return res;
        }

        private static Set<ValueNode> setIntersection(Set<ValueNode> x, Set<ValueNode> y) {
            if (x == null) {
                return y;
            }
            Set<ValueNode> res = new HashSet<>(x == null ? Set.of() : x);
            res.retainAll(y);
            return res;
        }

        private static boolean isSubSet(Set<ValueNode> x, Set<ValueNode> y) {
            if (x == null) {
                return y == null;
            }
            if (y == null || x.isEmpty()) {
                return true;
            }
            // first do an easy length check
            if (x.size() > y.size()) {
                return false;
            }
            // only then do the actual expensive check
            for (ValueNode elem : x) {
                if (!y.contains(elem)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean stampWeaker(Stamp x, Stamp y) {
            return !x.equals(y) && x.meet(y).equals(x);
        }

        private Pentagon normalize(Pentagon input) {
            if (doTypes || !(input instanceof ObjectPentagon obj) || obj.isNonNullConstant()) {
                return input;
            }
            AbstractObjectStamp oStamp = obj.stamp;
            if (oStamp.isEmpty() || oStamp.isUnrestricted()) {
                return obj;
            }
            if (oStamp.isConstant() || oStamp.nonNull()) {
                return Pentagon.of(oStamp.asType(null, false, false));
            }
            return Pentagon.of((AbstractObjectStamp) oStamp.unrestricted());
        }
    }

    public PentagonalAnalysisPhase(CanonicalizerPhase canonicalizer) {
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
        final DomainOfPentagons domainOfPentagons = new DomainOfPentagons(
                        context.getConstantReflection(),
                        Options.PA_WidenAfter.getValue(graph.getOptions()),
                        Options.PA_UseNonNullObjConstants.getValue(graph.getOptions()),
                        Options.PA_TrackTypes.getValue(graph.getOptions()));

        try (DebugContext.Scope scope = graph.getDebug().scope("PentagonApplication")) {
            // run analysis
            DFAnalysis<Pentagon> analysis = DFAnalysis.create(Pentagon.class, graph, domainOfPentagons,
                            nd -> switch (nd) {
                                case ConstantNode ignored -> true;
                                case LogicConstantNode ignored -> true;
                                case LogicNode ln -> {
                                    for (Node input : ln.inputs()) {
                                        if (!(input instanceof ValueNode vin) || !domainOfPentagons.isOfInterest(vin)) {
                                            // we are not interested in logic nodes the inputs of
                                            // which we do not
                                            // understand
                                            yield false;
                                        }
                                    }
                                    yield true;
                                }
                                case IntegerSwitchNode ignored -> true;
                                case ValueProxy vp -> domainOfPentagons.isOfInterest(vp.asNode());
                                case ProxyNode pn -> domainOfPentagons.isOfInterest(pn);
                                case FloatConvertNode fc -> fc.getFloatConvert().getCategory() == FloatConvertCategory.IntegerToFloatingPoint;
                                case FloatingReadNode fRead -> domainOfPentagons.isOfInterest(fRead) && !fRead.stamp(NodeView.DEFAULT).isUnrestricted();
                                case ReadNode rn -> domainOfPentagons.isOfInterest(rn) && !rn.stamp(NodeView.DEFAULT).isUnrestricted();
                                case AllocatedObjectNode aon -> !aon.stamp(NodeView.DEFAULT).isUnrestricted();
                                default -> false;
                            });
            DFAMap<Pentagon> map = analysis.run();

            // replace constants in graph
            MapCursor<ValueNode, Pentagon> stampCursor = map.getEntries();
            int constantsFound = 0;
            while (stampCursor.advance()) {
                ValueNode node = stampCursor.getKey();
                Pentagon pentagon = stampCursor.getValue();
                if (pentagon.isConstant() && !(node instanceof ConstantNode || node instanceof LogicConstantNode)) {
                    constantsFound++;
                    String tNodeString = graph.getDebug().isLogEnabled(DebugContext.VERY_DETAILED_LEVEL) ? node.toString() : "";
                    if (pentagon instanceof LogicPentagon lp) {
                        graph.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "replacing %s with constant %s", node, lp.logic);
                        LogicConstantNode c = LogicConstantNode.forBoolean(lp.logic.toBoolean(), graph);
                        node.replaceAtUsagesAndDelete(c);
                        graph.getOptimizationLog().report(PentagonalAnalysisPhase.class, "PentagonsConstantReplacement", node);
                    } else {
                        StampPentagon sp = pentagon.asStamp();
                        /*
                         * TODO something with null constants (and maybe object constants in
                         * general) is weird because they might float away or something (cc David
                         * Leopoldseder)
                         */
                        Stamp stamp = sp.getStamp();
                        GraalError.guarantee(stamp.getClass().equals(node.stamp(NodeView.DEFAULT).getClass()), "Mismatch in stamp types (in graph: %s, analysis: %s)",
                                        node.stamp(NodeView.DEFAULT).getClass().getSimpleName(), stamp.getClass().getSimpleName());
                        ConstantNode c = null;
                        if (stamp.asConstant() instanceof JavaConstant javaConstant) {
                            c = graph.addOrUnique(new ConstantNode(javaConstant, stamp));
                        } else if (sp instanceof ObjectPentagon op && op.isNonNullConstant()) {
                            c = graph.addOrUnique(ConstantNode.forConstant(op.stamp, op.constant, context.getMetaAccess()));
                        }
                        if (c != null) {
                            GraalError.guarantee(c.stamp(NodeView.DEFAULT).isCompatible(node.stamp(NodeView.DEFAULT)),
                                            "Incompatible stamps at constant replacement (replacing node %s with stamp %s by constant with stamp %s)",
                                            node, node.stamp(NodeView.DEFAULT), c.stamp(NodeView.DEFAULT));
                            node.replaceAtUsages(c);
                            graph.getOptimizationLog().report(StampAnalysisPhase.class, "PentagonsConstantReplacement", node);
                        }
                    }
                }
            }

            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "After constant replacement");

            // cleanup stuff that the analysis left behind
            analysis.cleanup();

            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "After PentagonalAnalysis cleanup");
        }
    }
}
