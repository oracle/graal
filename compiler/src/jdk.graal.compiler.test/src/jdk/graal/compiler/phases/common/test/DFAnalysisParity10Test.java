/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.common.test;

import java.util.ListIterator;
import java.util.Optional;

import org.graalvm.collections.MapCursor;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerRemNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.dfanalysis.AnalysisDomainDefinition;
import jdk.graal.compiler.phases.dfanalysis.DFAMap;
import jdk.graal.compiler.phases.dfanalysis.DFAnalysis;
import jdk.graal.compiler.phases.dfanalysis.InferredFactNode;
import jdk.graal.compiler.phases.dfanalysis.analyses.LSCCPPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactSplitNode;

/**
 * This test class is intended to be a short and easy introduction to designing a dataflow analysis
 * using the dataflow analysis framework {@link DFAnalysis}.
 *
 * The example presented is an analysis of integer values that tracks if a given value is of "parity
 * 10", meaning if the given value is divisible by 10. This property is specifically chosen to be
 * information that can not be conveyed by {@link jdk.graal.compiler.core.common.type.Stamp} to show
 * that the framework is not bound to the Stamp system but rather can accommodate user defined
 * elements to describe data.
 */
public class DFAnalysisParity10Test extends DFAnalysisBaseTest {

    /**
     * This is an example where the return value can be found by the Parity10 analysis. Stamps do
     * not cover if a value is divisible by 10, therefore this case here is not covered.
     * Additionally, to prove that the only reachable return statement returns a constant 1, control
     * flow analysis is needed alongside the value flow analysis. The {@link DFAnalysis} framework
     * takes care of this control flow analysis internally.
     */
    @DFATestSnippet(anchors = 0, returns = "i32 [1]")
    public static int exampleSnippet(int a) {
        int ctr = a;
        int x = 10;
        // x is parity 10
        do {
            // loop for an unknown amount of iterations
            if (x % 10 != 0) {
                // detected as unreachable by the Parity10Phase
                GraalDirectives.controlFlowAnchor();
                x = 11;
                // if this was ever reached, x would not be parity 10 anymore
            }
            // x stays parity 10 because the assignment above is unreachable
        } while (ctr-- > 0);
        // x is still parity 10

        if (x % 10 == 0) {
            // this is reachable since x is parity 10
            return 1;
        } else {
            /*
             * Detected as unreachable by the Parity10Phase (Parity10Lattice#transfer in 'case
             * IntegerEqualsNode' and Parity10Lattice#splitReachability).
             */
            GraalDirectives.controlFlowAnchor();
            return 2;
        }
    }

    /**
     * <p>
     * This class defines an approximation for all possible values that a node can produce. Here we
     * store 5 different states for each integer value: is parity 10, is not parity 10, is zero, is
     * not zero and any. This class also knows a 6th state, empty, with which unevaluated nodes are
     * associated.
     * </p>
     * <p>
     * An instance of this class represents an element in the conceptual
     * <a href="https://en.wikipedia.org/wiki/Complete_lattice">complete lattice</a> representing
     * this parity 10 domain (declared in {@link Parity10Lattice}). The meet and join operations are
     * implemented here for ease of implementation, but this is not a requirement for the dataflow
     * analysis framework.
     * </p>
     * <p>
     * The statically allocated instances of this class form 2 lattices (one for parity values, one
     * for logic values) as shown below.
     * </p>
     *
     * <pre>
     * Lattice for parity elements in domain "Parity 10"
     *      UNEVALUATED
     *         /   \
     *      !P10   ZERO
     *        |     |
     *      !ZERO  P10
     *         \   /
     *     UNRESTRICTED
     * </pre>
     *
     * <pre>
     * Lattice for logic values in domain "Parity 10"
     *      UNEVALUATED
     *         /   \
     *      FALSE  TRUE
     *         \   /
     *     UNRESTRICTED
     * </pre>
     */
    private static final class ParityElement {
        /*
         * We use the 5th bit of the mask to denote if this element represents a parity value or a
         * logic value. The lower 4 bits are used to represent parity values while the 6th and 7th
         * bit are used to differentiate logic values.
         *
         * Unevaluated values are denoted by 0 while unrestricted values have all bits used by the
         * subtype (logic/parity) set to allow for meet and join to be implemented by bitwise OR and
         * bitwise AND.
         */
        private static final byte LOGIC_MASK = 0b1_0000;
        // @formatter:off
        private static final byte UNEVALUATED  = 0b0000;
        private static final byte NOT_PARITY   = 0b0001;
        private static final byte IS_ZERO      = 0b0010;
        private static final byte NOT_ZERO     = 0b0100 | NOT_PARITY;
        private static final byte IS_PARITY    = 0b1000 | IS_ZERO;
        private static final byte UNRESTRICTED = 0b1111;

        private static final byte LOGIC_UNEVALUATED  = (0b00 << 5) | LOGIC_MASK;
        private static final byte LOGIC_TRUE         = (0b01 << 5) | LOGIC_MASK;
        private static final byte LOGIC_FALSE        = (0b10 << 5) | LOGIC_MASK;
        private static final byte LOGIC_UNRESTRICTED = (0b11 << 5) | LOGIC_MASK;
        // @formatter:on

        // integer parity elements
        public static final ParityElement P_UNEVALUATED = new ParityElement(UNEVALUATED);
        public static final ParityElement P_NOT_PARITY = new ParityElement(NOT_PARITY);
        public static final ParityElement P_IS_ZERO = new ParityElement(IS_ZERO);
        public static final ParityElement P_NOT_ZERO = new ParityElement(NOT_ZERO);
        public static final ParityElement P_IS_PARITY = new ParityElement(IS_PARITY);
        public static final ParityElement P_UNRESTRICTED = new ParityElement(UNRESTRICTED);
        // logic elements
        public static final ParityElement L_UNEVALUATED = new ParityElement(LOGIC_UNEVALUATED);
        public static final ParityElement L_TRUE = new ParityElement(LOGIC_TRUE);
        public static final ParityElement L_FALSE = new ParityElement(LOGIC_FALSE);
        public static final ParityElement L_UNRESTRICTED = new ParityElement(LOGIC_UNRESTRICTED);

        private final byte mask;

        private ParityElement(byte mask) {
            this.mask = mask;
        }

        private static ParityElement of(int mask) {
            return switch (mask) {
                case UNEVALUATED -> P_UNEVALUATED;
                case IS_ZERO -> P_IS_ZERO;
                case NOT_ZERO -> P_NOT_ZERO;
                case IS_PARITY -> P_IS_PARITY;
                case NOT_PARITY -> P_NOT_PARITY;
                case LOGIC_UNEVALUATED -> L_UNEVALUATED;
                case LOGIC_TRUE -> L_TRUE;
                case LOGIC_FALSE -> L_FALSE;
                default -> (mask & LOGIC_MASK) == 0 ? P_UNRESTRICTED : L_UNRESTRICTED;
            };
        }

        public ParityElement unevaluated() {
            return (mask & LOGIC_MASK) == 0 ? P_UNEVALUATED : L_UNEVALUATED;
        }

        public ParityElement unrestricted() {
            return (mask & LOGIC_MASK) == 0 ? P_UNRESTRICTED : L_UNRESTRICTED;
        }

        public ParityElement merge(ParityElement other) {
            return of(mask | other.mask);
        }

        public ParityElement strengthen(ParityElement other) {
            return of(mask & other.mask);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof ParityElement other && mask == other.mask;
        }

        @Override
        public int hashCode() {
            return mask;
        }

        public boolean isNotParity() {
            return mask == NOT_PARITY;
        }

        public boolean isZero() {
            return mask == IS_ZERO;
        }

        public boolean isParity() {
            return (mask & IS_PARITY) != 0 && (mask & ~IS_PARITY) == 0;
        }

        public boolean isNotZero() {
            return (mask & NOT_ZERO) != 0 && (mask & ~NOT_ZERO) == 0;
        }

        @Override
        public String toString() {
            return switch (mask) {
                case UNEVALUATED -> "parity(-)";
                case IS_ZERO -> "parity(0)";
                case NOT_ZERO -> "parity(!0)";
                case IS_PARITY -> "parity(mod10)";
                case NOT_PARITY -> "parity(!mod10)";
                case UNRESTRICTED -> "parity(+)";
                case LOGIC_UNEVALUATED -> "logic(-)";
                case LOGIC_TRUE -> "logic(TRUE)";
                case LOGIC_FALSE -> "logic(FALSE)";
                case LOGIC_UNRESTRICTED -> "logic(+)";
                default -> (mask & LOGIC_MASK) == 0 ? "parity(???)" : "logic(???)";
            };
        }
    }

    /**
     * <p>
     * This class is the heart of the parity 10 analysis built on the dataflow analysis framework.
     * It defines the domain that the analysis will use in the form of a
     * <a href="https://en.wikipedia.org/wiki/Complete_lattice">complete lattice</a>. The elements
     * in this domain are represented by instances of {@link ParityElement}. Domain definition
     * instances are supposed to be essentially stateless. They only exist to provide functions to
     * interact with a domain element unknown to the framework, and to calculate domain elements for
     * operations like for example additions.
     * </p>
     * <p>
     * This domain evaluates nodes that produce integers by describing if their result values are
     * zero or of parity 10.
     * </p>
     */
    private static final class Parity10Lattice implements AnalysisDomainDefinition<ParityElement> {
        @Override
        public ParityElement merge(ParityElement x, ParityElement y) {
            return x.merge(y);
        }

        @Override
        public ParityElement strengthen(ParityElement x, ParityElement y) {
            return x.strengthen(y);
        }

        @Override
        public ParityElement unevaluated(ValueNode node) {
            return node instanceof LogicNode ? ParityElement.L_UNEVALUATED : node.stamp(NodeView.DEFAULT).isIntegerStamp() ? ParityElement.P_UNEVALUATED : null;
        }

        @Override
        public ParityElement unevaluated(ParityElement elem) {
            return elem.unevaluated();
        }

        @Override
        public ParityElement unrestricted(ValueNode node) {
            return node instanceof LogicNode ? ParityElement.L_UNRESTRICTED : node.stamp(NodeView.DEFAULT).isIntegerStamp() ? ParityElement.P_UNRESTRICTED : null;
        }

        @Override
        public ParityElement unrestricted(ParityElement elem) {
            return elem.unrestricted();
        }

        /**
         * This method calculates a result for the operation the given node represents, given the
         * values of its inputs.
         */
        @Override
        public ParityElement transfer(ValueNode node, DFAMap<ParityElement> map) {
            return switch (node) {
                /*
                 * Constant nodes simply produce a value. Therefore, we calculate the appropriate
                 * domain element for the given constant.
                 */
                case ConstantNode c -> {
                    long val = ((IntegerStamp) c.stamp(NodeView.DEFAULT)).lowerBound();
                    yield val == 0 ? ParityElement.P_IS_ZERO : val % 10 == 0 ? ParityElement.P_IS_PARITY : ParityElement.P_NOT_PARITY;
                }
                case LogicConstantNode c ->
                    c.getValue() ? ParityElement.L_TRUE : ParityElement.L_FALSE;
                /*
                 * Proxy nodes just pass through the value of the original node.
                 */
                case ProxyNode proxy ->
                    map.getOrUnrestricted(proxy.value());
                /*
                 * Integer additions that are guaranteed not to overflow preserve parity 10.
                 * Therefore, we can evaluate the exact addition nodes here.
                 */
                case IntegerAddExactNode add ->
                    calcAdd(map.getOrUnrestricted(add.getX()), map.getOrUnrestricted(add.getY()));
                case IntegerAddExactSplitNode add ->
                    calcAdd(map.getOrUnrestricted(add.getX()), map.getOrUnrestricted(add.getY()));
                /*
                 * The parity 10 allows for some reasoning about equality checks. If both inputs are
                 * zero, we can evaluate this node to the domain element representing 'true'. If one
                 * value is not-zero/not-parity and the other one is zero/parity, we can conclude
                 * that the result of this equality operation is 'false'.
                 */
                case IntegerEqualsNode eq -> {
                    ParityElement x = map.getOrUnrestricted(eq.getX());
                    ParityElement y = map.getOrUnrestricted(eq.getY());
                    boolean x0 = x.isZero();
                    boolean y0 = y.isZero();
                    if (x0 && y0) {
                        yield ParityElement.L_TRUE;
                    } else if (x0 && y.isNotZero() || y0 && x.isNotZero()) {
                        yield ParityElement.L_FALSE;
                    } else {
                        yield ParityElement.L_UNRESTRICTED;
                    }
                }
                /*
                 * If we encounter an operation of the shape 'x % 10' we know the result to be zero
                 * in the case that x is parity 10. Equally, the result is sure to not-zero if x is
                 * not-parity 10.
                 */
                case SignedFloatingIntegerRemNode rem -> {
                    if (rem.getY().isJavaConstant() && rem.getY().asJavaConstant().asLong() == 10) {
                        // this is a modulo 10 operation
                        if (map.getOrUnrestricted(rem.getX()).isParity()) {
                            yield ParityElement.P_IS_ZERO;
                        } else if (map.getOrUnrestricted(rem.getX()).isNotParity()) {
                            yield ParityElement.P_NOT_ZERO;
                        } else {
                            yield ParityElement.P_UNRESTRICTED;
                        }
                    } else {
                        // not a modulo 10 operation
                        yield ParityElement.P_UNRESTRICTED;
                    }
                }
                /*
                 * For all other nodes this analysis can not determine a parity 10 information. To
                 * indicate this, we return unrestricted here, meaning all values are possible for
                 * the given node.
                 */
                default -> unrestricted(node);
            };
        }

        /**
         * Since the dataflow analysis framework does not know the class representing domain
         * elements, it can not calculate for a given value if a successor branch of an IfNode can
         * be considered unreachable. This method implements a simple check for reachability of
         * successor branches of a given IfNode. As a neat micro optimization, the returned arrays
         * are pre-allocated because the framework guarantees to not modify the contents of the
         * returned arrays, making them reusable.
         *
         * This example analysis only allows for reasoning about IfNodes and returns null for all
         * other control split node types.
         */
        @Override
        public boolean[] splitReachability(ControlSplitNode split, DFAMap<ParityElement> map) {
            if (split instanceof IfNode ifNode) {
                ParityElement cond = map.getOrUnrestricted(ifNode.condition());
                return isEqual(cond, ParityElement.L_TRUE) ? TRUE_FALSE : isEqual(cond, ParityElement.L_FALSE) ? FALSE_TRUE : TRUE_TRUE;
            } else {
                return null;
            }
        }

        /**
         * If an equals check succeeds, we can augment our knowledge about the inputs of the equals
         * check by adding the information of the other input to the current knowledge of one input.
         */
        @Override
        public ParityElement[][] calcInferrableValues(ValueNode node, DFAMap<ParityElement> map) {
            if (node instanceof IntegerEqualsNode eq) {
                return AnalysisDomainDefinition.ifInference(ParityElement.class,
                                // inferences for the true branch
                                AnalysisDomainDefinition.binaryLogicInference(ParityElement.class,
                                                /*
                                                 * For input X we can assume that if the equals
                                                 * check is true, it is at least the same parity as
                                                 * we already know input Y to be.
                                                 */
                                                map.getOrUnrestricted(eq.getY()),
                                                // similar for input Y
                                                map.getOrUnrestricted(eq.getX())),
                                /*
                                 * We will never produce an inference for the false branch,
                                 * therefore we can just return null here to indicate that.
                                 */
                                null);
            } else {
                /*
                 * If we never produce an inference for a node, we can just return null here to
                 * indicate that.
                 */
                return null;
            }
        }

        /**
         * Helper method that calculates if adding the given values preserves or breaks parity. This
         * calculation is done without taking overflows into account. To ensure correctness, the
         * caller must verify that no overflow can occur.
         */
        private static ParityElement calcAdd(ParityElement x, ParityElement y) {
            if (x.isZero()) {
                return y;
            } else if (y.isZero()) {
                return x;
            } else if (x.isParity()) {
                if (y.isParity()) {
                    /* x mod 10 == 0, y mod 10 == 0 -> (x+y) mod 10 == 0 */
                    return ParityElement.P_IS_PARITY;
                } else if (y.isNotParity()) {
                    /* x mod 10 == 0, y mod 10 != 0 -> (x+y) mod 10 != 0 */
                    return ParityElement.P_NOT_PARITY;
                } else {
                    /* x mod 10 == 0, y != 0 (otherwise unknown) -> (x+y) unknown */
                    return ParityElement.P_UNRESTRICTED;
                }
            } else if (y.isParity()) {
                if (x.isParity()) {
                    /* x mod 10 == 0, y mod 10 == 0 -> (x+y) mod 10 == 0 */
                    return ParityElement.P_IS_PARITY;
                } else if (x.isNotParity()) {
                    /* x mod 10 != 0, y mod 10 == 0 -> (x+y) mod 10 != 0 */
                    return ParityElement.P_NOT_PARITY;
                } else {
                    /* x != 0 (otherwise unknown), y mod 10 == 0 -> (x+y) unknown */
                    return ParityElement.P_UNRESTRICTED;
                }
            } else {
                return ParityElement.P_UNRESTRICTED;
            }
        }
    }

    /**
     * <p>
     * This is a simple example of how to use the dataflow analysis framework to calculate possibly
     * provable conditions for if-statements.
     * </p>
     * <p>
     * There are a few important things to note here. First, each instance of the analysis framework
     * created with {@link DFAnalysis#create} is only to be used one time, since throughout running
     * the analysis, the internal state changes. Second, running the analysis creates a map of nodes
     * to their respective domain elements calculated using the domain definition. Lastly, the
     * analysis inserts special nodes ({@link InferredFactNode}) into the graph representing a value
     * in a special branch. These nodes are intended to be only short-lived and need to be cleaned
     * up after the analysis by calling {@link DFAnalysis#cleanup}.
     * </p>
     * <p>
     * To initialize the analysis, a list of node types used as starting points for the analysis
     * must be passed in {@link DFAnalysis#create}. In this case, the parity 10 analysis starts at
     * all constant nodes.
     * </p>
     */
    private static class Parity10Phase extends PostRunCanonicalizationPhase<CoreProviders> {

        private static final Parity10Lattice PARITY_10_DOMAIN = new Parity10Lattice();

        Parity10Phase(CanonicalizerPhase canonicalizer) {
            super(canonicalizer);
        }

        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            /*
             * We can only use the framework before value proxy removal. The framework has an
             * optimization built in that prevents spillage of temporary values that occur during
             * the evaluation of a loop to nodes below the loop by only propagating through value
             * proxies when the according loop has been fully evaluated. This saves a lot of
             * evaluations on nodes that use values from a loop above.
             *
             * The framework should work (with a performance penalty) even without value proxies but
             * this not tested and therefore not allowed.
             */
            return NotApplicable.unlessRunBefore(this, GraphState.StageFlag.VALUE_PROXY_REMOVAL, graphState);
        }

        @Override
        @SuppressWarnings({"unused", "try"})
        protected void run(StructuredGraph graph, CoreProviders context) {
            try (DebugContext.Scope scope = graph.getDebug().scope("Parity10Application")) {

                // create analysis
                DFAnalysis<ParityElement> analysis = DFAnalysis.create(ParityElement.class, graph, PARITY_10_DOMAIN, nd -> switch (nd) {
                    case ConstantNode ignored -> true;
                    case LogicConstantNode ignored -> true;
                    default -> false;
                });

                // run analysis
                DFAMap<ParityElement> result = analysis.run();

                // replace the logic constants we found in graph
                MapCursor<ValueNode, ParityElement> stampCursor = result.getEntries();
                while (stampCursor.advance()) {
                    ValueNode node = stampCursor.getKey();
                    ParityElement value = stampCursor.getValue();
                    if (value.equals(ParityElement.L_TRUE)) {
                        replaceWithLogicConstant(graph, (LogicNode) node, true);
                    } else if (value.equals(ParityElement.L_FALSE)) {
                        replaceWithLogicConstant(graph, (LogicNode) node, false);
                    }
                }

                // run cleanup
                analysis.cleanup();
            }
        }

        private static void replaceWithLogicConstant(StructuredGraph graph, LogicNode toReplace, boolean constantValue) {
            String name = toReplace.toString();
            graph.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "replacing %s with constant %s", name, constantValue ? "TRUE" : "FALSE");
            LogicConstantNode newConst = LogicConstantNode.forBoolean(constantValue, graph);
            toReplace.replaceAtUsagesAndDelete(newConst);
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Replaced %s with constant %s", name, newConst);
        }
    }

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites suites = super.createSuites(opts);
        // insert Parity10Phase before LSCCPPhase
        ListIterator<BasePhase<? super MidTierContext>> pos = suites.getMidTier().findPhase(LSCCPPhase.class);
        pos.previous();
        pos.add(new Parity10Phase(createCanonicalizerPhase()));
        return suites;
    }

    // =================================================================================================================
    // More examples of code shapes detectable with the parity 10 analysis
    // =================================================================================================================

    @Test
    public void example() {
        test("exampleSnippet", 5);
    }

    @Test
    public void parity10() {
        test("parity10Snippet", 5);
    }

    @DFATestSnippet(anchors = 1, returns = "i32 [1]")
    public static int parity10Snippet(int a) {
        int ctr = a;
        int x = 10;
        // x is parity 10
        do {
            if (x % 10 != 0) {
                // detected as unreachable by the Parity10Phase
                GraalDirectives.controlFlowAnchor();
                x = 11;
            }
            // x stays parity 10 because the assignment above is unreachable
            if (ctr % 10 == 0) {
                // branch is reachable (we know nothing about ctr)
                GraalDirectives.controlFlowAnchor();
                x = 20;
                // x is still parity 10
            }
            // x stays parity 10
        } while (ctr-- > 0);
        // x is still parity 10

        // preserve parity with exact add with parity-10 value
        x = Math.addExact(x, 30);
        if (x % 10 == 0) {
            return 1;
        } else {
            /*
             * Detected as unreachable by the Parity10Phase (Parity10Lattice#transfer in 'case
             * IntegerEqualsNode' and Parity10Lattice#splitReachability).
             */
            GraalDirectives.controlFlowAnchor();
            return 2;
        }
    }

    @Test
    public void notParity10() {
        test("notParity10Snippet", 5);
    }

    @DFATestSnippet(anchors = 1, returns = "i32 [2]")
    public static int notParity10Snippet(int a) {
        int ctr = a;
        int x = 10;
        // x is parity 10
        do {
            if (x % 10 != 0) {
                // detected as unreachable by the Parity10Phase
                GraalDirectives.controlFlowAnchor();
                x = 11;
            }
            // x stays parity 10 because the assignment above is unreachable
            if (ctr % 10 == 0) {
                // branch is reachable (we know nothing about ctr)
                GraalDirectives.controlFlowAnchor();
                x = 20;
                // x is still parity 10
            }
            // x stays parity 10
        } while (ctr-- > 0);
        // x is still parity 10

        // destroy parity with exact add with non-parity-10 value
        x = Math.addExact(x, 35);
        if (x % 10 == 0) {
            /*
             * Detected as unreachable by the Parity10Phase (Parity10Lattice#transfer in 'case
             * IntegerEqualsNode' and Parity10Lattice#splitReachability).
             */
            GraalDirectives.controlFlowAnchor();
            return 1;
        } else {
            return 2;
        }
    }

    @Test
    public void inferParity10() {
        test("inferParity10Snippet", 5, 10);
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [1]")
    public static int inferParity10Snippet(int a, int b) {
        int ctr = a;
        int x = 10;
        // x is parity 10
        do {
            if (x % 10 != 0) {
                // detected as unreachable by the Parity10Phase
                GraalDirectives.controlFlowAnchor();
                x = 11;
            }
            // x stays parity 10 because the assignment above is unreachable
        } while (ctr-- > 0);
        // x is parity 10 here

        if (b == x) {
            /*
             * We know that b is parity 10 here, since it is equal to x which is parity 10. The
             * analysis creates a node for b at the start of the true-branch of this if statement,
             * augmenting all usages of b inside the branch with the information that b is indeed
             * parity 10. For details see Parity10Lattice#calcInferrableValues.
             */
            // this condition is provable by the parity 10 analysis.
            if (b % 10 == 0) {
                // reachable return
                return 1;
            } else {
                // detected as unreachable by the Parity10Phase
                GraalDirectives.controlFlowAnchor();
                return 2;
            }
        } else {
            // reachable return
            return 1;
        }
    }
}
