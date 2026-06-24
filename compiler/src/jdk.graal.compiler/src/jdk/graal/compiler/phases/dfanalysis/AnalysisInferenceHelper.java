/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.dfanalysis;

import static jdk.graal.compiler.phases.dfanalysis.DFAMap.getEndsForPhiInput;
import static jdk.graal.compiler.phases.dfanalysis.DFAnalysis.isMemoryUsage;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;

import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.vm.ci.meta.TriState;

/**
 * <p>
 * This class provides logic to detect and insert data flow information inferrable by control flow
 * into the graph. Consider the following example:
 * </p>
 *
 * <pre>
 * if (x == null) {
 *     use0(x);
 * } else {
 *     use1(x);
 * }
 * use2(x);
 * </pre>
 *
 * This is effectively transformed into:
 *
 * <pre>
 * if (x == null) {
 *     xAsNull = inferredFact(x, null);
 *     use0(xAsNull);
 * } else {
 *     xAsNonNull = inferredFact(x, nonNull);
 *     use1(xAsNonNull);
 * }
 * use2(x);
 * </pre>
 *
 * <p>
 * Here we can infer, based on the equals condition and control flow, that for use0 x is always
 * {@code null} and for use1 it is anything but {@code null}, while we can not say anything about x
 * at use2. To now insert this information into the graph, we use {@link InferredFactNode}s. We may
 * insert such nodes for usages that are dominated by the target branch (in this case for inserting
 * the inference x==null the target branch is the true branch of the if-condition). This usually
 * requires a full schedule of the graph, but here we do not have such a schedule. We must therefore
 * approximate it instead by transitively checking usages until we reach a node associated with
 * control flow in some way (see {@link AnalysisInferenceHelper#insertInference} and
 * {@link AnalysisInferenceHelper#checkTransitiveUsages}).
 * </p>
 * <p>
 * To obtain inferred information, the {@link AnalysisDomainDefinition#calcInferrableValues} method
 * of the analysis domain at hand is used. Since this method has very similar restrictions to
 * {@link AnalysisDomainDefinition#transfer}, we may violate monotonicity of the analysis when first
 * inserting new {@link InferredFactNode}s as documented in
 * {@link AnalysisInferenceHelper#createInferenceNode(DFAnalysis, ValueNode, AbstractBeginNode, ValueNode, Object, ValueNode)}.
 * </p>
 * <p>
 * Helper class that contains the logic to generate data flow information based on given control
 * flow. This class only holds static methods. The entry point to this logic is
 * {@link AnalysisInferenceHelper#generateInferredFacts}. It is intentionally package private as the
 * contained logic is only needed for {@link DFAnalysis}.
 * </p>
 */
final class AnalysisInferenceHelper {

    /**
     * This is the main entry point for inserting inferred facts into the graph. It collects the
     * relevant branches and queries the {@link AnalysisDomainDefinition} of the given analysis for
     * inferrable values.
     */
    static <T> void generateInferredFacts(DFAnalysis<T> analysis, ValueNode node) {
        final AnalysisDomainDefinition<T> domain = analysis.domain;
        final DFAMap<T> elemMap = analysis.elemMap;
        final EconomicMap<LogicNode, AbstractBeginNode[][]> logicBranchCache = analysis.logicBranchCache;

        ValueNode infGenerator = null;
        ValueNode[] inputs = null;
        AbstractBeginNode[][] allTargetBranches = null;
        T[][] toInsert = null;

        /*
         * Collect inference targets (inputs of generating node) and target branches (branches in
         * which we can infer data flow information about the inference targets) based on the given
         * generating node.
         */
        if (node instanceof UnaryOpLogicNode unaryOpNode) {
            infGenerator = unaryOpNode;
            inputs = new ValueNode[]{unaryOpNode.getValue()};
            allTargetBranches = getTFBranchesForLogic(logicBranchCache, unaryOpNode);
            if (allTargetBranches.length > 0) {
                // only calculate inferences if we actually might need them
                toInsert = domain.calcInferrableValues(unaryOpNode, elemMap);
                GraalError.guarantee(toInsert == null || (toInsert.length == 2 &&
                                (toInsert[0] == null || toInsert[0].length == 1) &&
                                (toInsert[1] == null || toInsert[1].length == 1)),
                                "Expected inferrable values of shape 2x1 (true/false, input) or null for UnaryOpLogicNode");
            }
        } else if (node instanceof BinaryOpLogicNode binaryOpNode) {
            infGenerator = binaryOpNode;
            inputs = new ValueNode[]{binaryOpNode.getX(), binaryOpNode.getY()};
            allTargetBranches = getTFBranchesForLogic(logicBranchCache, binaryOpNode);
            if (allTargetBranches.length > 0) {
                // only calculate inferences if we actually might need them
                toInsert = domain.calcInferrableValues(binaryOpNode, elemMap);
                GraalError.guarantee(toInsert == null || (toInsert.length == 2 &&
                                (toInsert[0] == null || toInsert[0].length == 2) &&
                                (toInsert[1] == null || toInsert[1].length == 2)),
                                "Expected inferrable values of shape 2x2 (true/false, X/Y) or null for BinaryOpLogicNode");
            }
        } else if (node instanceof SwitchNode switchNode) {
            infGenerator = switchNode;
            inputs = new ValueNode[]{switchNode.value()};
            allTargetBranches = new AbstractBeginNode[1][switchNode.blockSuccessorCount()];
            for (int i = 0; i < allTargetBranches.length; i++) {
                allTargetBranches[0][i] = switchNode.blockSuccessor(i);
            }
            toInsert = domain.calcInferrableValues(switchNode, elemMap);
            if (toInsert != null) {
                GraalError.guarantee(toInsert.length == switchNode.blockSuccessorCount(),
                                "Expected inferrable values of shape nx1 (switch-successors, value) or null for SwitchNode");
                for (T[] valueArray : toInsert) {
                    GraalError.guarantee(valueArray == null || valueArray.length == 1,
                                    "Expected inferrable values of shape nx1 (switch-successors, value) or null for SwitchNode");
                }
            }
        }

        // apply inferred information
        if (toInsert != null) {
            for (AbstractBeginNode[] branches : allTargetBranches) {
                // per control flow split, we collect dominated inferences for optimization, since a
                // node cannot be dominated by more than 1 successor
                EconomicSet<ValueNode> dominatedInferences = EconomicSet.create();
                GraalError.guarantee(toInsert.length == branches.length, "Expected values for %s branches, got %s values", branches.length, toInsert.length);
                for (int bIdx = 0; bIdx < branches.length; bIdx++) {
                    AbstractBeginNode branch = branches[bIdx];
                    T[] values = toInsert[bIdx];
                    if (branch != null && values != null) {
                        // we have a valid branch and values to insert
                        GraalError.guarantee(values.length == inputs.length, "Expected values for %s inputs, got %s values", inputs.length, values.length);
                        for (int vIdx = 0; vIdx < inputs.length; vIdx++) {
                            T value = values[vIdx];
                            ValueNode input = inputs[vIdx];
                            if (value != null) {
                                // we have a valid value to insert for input
                                insertInference(analysis, infGenerator, input, branch, value, dominatedInferences);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final AbstractBeginNode[][] NO_BEGINS = new AbstractBeginNode[0][];

    /**
     * Collects all branches the logic node is used in (first dimension) and their branches (second
     * dimension).
     */
    private static AbstractBeginNode[][] getTFBranchesForLogic(EconomicMap<LogicNode, AbstractBeginNode[][]> logicBranchCache, LogicNode node) {
        if (logicBranchCache.containsKey(node)) {
            return logicBranchCache.get(node);
        }
        ArrayList<AbstractBeginNode[]> begins = null;
        for (Node usage : node.usages()) {
            if (usage instanceof IfNode ifNode) {
                if (begins == null) {
                    begins = new ArrayList<>();
                }
                begins.add(new AbstractBeginNode[]{ifNode.trueSuccessor(), ifNode.falseSuccessor()});
            } else if (usage instanceof ShortCircuitOrNode shortOr) {
                for (IfNode soUsage : shortOr.usages().filter(IfNode.class)) {
                    if (begins == null) {
                        begins = new ArrayList<>();
                    }
                    /*
                     * If the given input is negated, we insert the inferred true value into the
                     * false branch, normally we just insert the false value into the false branch
                     * and skip the true branch.
                     */
                    begins.add(shortOr.getX() == node && shortOr.isXNegated() || shortOr.getY() == node && shortOr.isYNegated()
                                    ? new AbstractBeginNode[]{soUsage.falseSuccessor(), null}
                                    : new AbstractBeginNode[]{null, soUsage.falseSuccessor()});
                }
            }
        }
        AbstractBeginNode[][] result = begins == null ? NO_BEGINS : begins.toArray(AbstractBeginNode[][]::new);
        logicBranchCache.put(node, result);
        return result;
    }

    /**
     * Control flow splits like {@code if (x == 1) ... else ...} may result in additional inferrable
     * information for values that only applies in a specific branch.
     * <p>
     * This method attempts to insert the newly inferred information without requiring a full
     * schedule of the graph. This is done by approximating a schedule by transitively following
     * usages of the target and, when reaching a node with connection to the control flow part of
     * the graph, checking if the begin-node of the target branch dominates this usage.
     * <p>
     * The {@code usagesToSkip} set is amended with all nodes that are dominated by the current
     * {@code branch} during the dominance check. This set may be handed back to this method later,
     * when called for another successor branch of the same control split.
     */
    private static <T> void insertInference(DFAnalysis<T> analysis, ValueNode generator, ValueNode value, AbstractBeginNode branch, T fact, EconomicSet<ValueNode> usagesToSkip) {
        final Class<T> elementType = analysis.elementType;
        final StructuredGraph graph = analysis.graph;
        final DebugContext debug = analysis.debug;
        final ControlFlowGraph cfg = analysis.cfg;
        final WorkList workList = analysis.workList;
        final DFAMap<T> elemMap = analysis.elemMap;
        final AnalysisDomainDefinition<T> domain = analysis.domain;
        final EconomicMap<ValueNode, EconomicSet<Pair<ValueNode, AbstractBeginNode>>> attemptedInferences = analysis.attemptedInferences;

        GraalError.guarantee(!domain.isUnevaluated(fact), "we never expect to infer UNEVALUATED values, if nothing can be" +
                        "inferred about the value in the given branch, UNRESTRICTED should be inferred instead");

        // it only makes sense to actually print logs if we can generate an inference
        StringBuilder logBuilder = new StringBuilder();
        if (debug.isLogEnabled(DebugContext.VERY_DETAILED_LEVEL)) {
            logBuilder = new StringBuilder();
        }
        boolean printLog = false;

        log(logBuilder, "  Inserting inference %s in %s (now %s) with %s\n", value, branch, value.stamp(NodeView.DEFAULT), fact);

        /*
         * Actually inserting an inferred UNRESTRICTED value is not worth the effort since there is
         * no information to be gained here. Therefore, we only correct preexisting inferences to
         * UNRESTRICTED.
         *
         * The attemptedInferences represent a list of inferences (denoted by the value for which we
         * are inferring new information, the generator, and the branch in which the inference is
         * applicable) we already tried to calculate the expensive dominance check for. If we find
         * that we already did the dominance check for the current inference attempt in an earlier
         * attempt (see the second line after the logical OR), we can assume that all
         * needed/possible InferredFactNodes were already created. Therefore, we can skip dominance
         * calculation and insertion of new InferredFactNodes and just use the already existing
         * inference nodes without compromising on precision.
         */
        boolean onlyCorrectPreviousInferencess = domain.isUnrestricted(fact) ||
                        attemptedInferences.containsKey(value) && attemptedInferences.get(value).contains(Pair.create(generator, branch));

        debug.log(DebugContext.VERY_DETAILED_LEVEL, "   inserting [%s in %s] (onlyCorrecting=%s) %s", value, branch, onlyCorrectPreviousInferencess, fact);

        EconomicSet<ValuePhiNode> inferredForPhis = EconomicSet.create();
        EconomicSet<ValueNode> inferredForUsages = EconomicSet.create();
        ArrayList<InferredFactNode<T>> heldOffInferences = new ArrayList<>();
        // we need to find the right usages to insert the given fact into
        HIRBlock targetBranch = cfg.blockFor(branch);
        boolean watchOutForMemory = MemoryKill.isMemoryKill(value);
        for (ValueNode usage : value.usages().filter(ValueNode.class).snapshot()) {
            if (watchOutForMemory && isMemoryUsage(value, usage)) {
                // this is a memory edge which we can not reason about that here
                log(logBuilder, "        not doing it for %s (memory edge)\n", usage);
                continue;
            }

            // if we have inserted a fact on this branch previously, we can reuse this node
            if (usage instanceof InferredFactNode<?> oldWildcardInference && oldWildcardInference.matches(elementType, branch, value, generator)) {
                InferredFactNode<T> oldInference = oldWildcardInference.castTo(elementType);

                GraalError.guarantee(!analysis.elemMap.isEvaluated(oldInference) || domain.isWeakerOrEqual(fact, elemMap.getOrUnevaluated(oldInference)).isTrue(),
                                "can not raise value of %s(%s) to %s via inference",
                                oldInference, elemMap.getOrUnevaluated(oldInference), fact);
                T loggingOldInfo = oldInference.getAdditionalInformation();
                oldInference.overrideInformation(fact);
                debug.log(DebugContext.VERY_DETAILED_LEVEL, "updated inference %s to %s", oldInference, fact);
                debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "after updating inference %s to %s", oldInference, fact);
                workList.schedule(oldInference);
                printLog = true;
                log(logBuilder, "    updated stamp from %s to %s (now input %s with %s)\n", loggingOldInfo, fact,
                                oldInference.getOriginalNode(), elemMap.getOrUnevaluated(oldInference.getOriginalNode()));
                continue;
            }

            if (onlyCorrectPreviousInferencess) {
                // we don't want to create new inferences
                log(logBuilder, "        not doing it for %s (%s)\n", usage,
                                attemptedInferences.containsKey(value) && attemptedInferences.get(value).contains(Pair.create(generator, branch))
                                                ? "previously failed"
                                                : "only correcting");
                continue;
            }

            if (usage instanceof InferredFactNode<?> gif && gif.isOfGeneric(elementType)) {
                InferredFactNode<T> iFact = gif.castTo(elementType);
                HIRBlock block = cfg.blockFor(iFact.getGuard());
                if (!targetBranch.dominates(block)) {
                    log(logBuilder, "        not doing it for %s (%s does not dominate %s)\n", usage, targetBranch, block);
                    continue;
                }
                /*
                 * The given iFact is a direct usage of the value we want to insert additional
                 * inferred information into the graph for. The iFact is also dominated by the given
                 * branch, meaning, we should generally be able to insert the new inference here.
                 * But just blindly inserting the new inference here might sever the connection
                 * between iFact and its generating condition. Therefore, we need to wait with
                 * actually inserting the inferred fact as an input of the iFact until we can be
                 * sure that this does not destroy a graph shape this analysis relies on.
                 */
                heldOffInferences.add(iFact);
                continue;
            }

            // a little bit of special handling is needed when trying to insert into a PHI directly
            if (usage instanceof ValuePhiNode phi) {
                if (inferredForPhis.contains(phi)) {
                    /*
                     * This phi might already have come up as a usage before (i.e. when 'value' is
                     * used multiple times in the phi). We went over all usages of 'value' in phi
                     * already the first time the phi came up. Therefore, there is nothing left to
                     * do here.
                     */
                    continue;
                }
                inferredForPhis.add(phi);
                for (AbstractEndNode associatedEnd : getEndsForPhiInput(phi, value)) {
                    if (targetBranch.dominates(cfg.blockFor(associatedEnd))) {
                        // we can insert the inference here
                        log(logBuilder, "    inserting for %s ((PHI) %s dominates %s)\n", usage, targetBranch, cfg.blockFor(associatedEnd));
                        createInferenceNode(analysis, generator, branch, value, fact, phi, associatedEnd);
                        printLog = true;
                        inferredForUsages.add(phi);
                    } else {
                        log(logBuilder, "        not doing it for %s ((PHI) %s does not dominate %s)\n", usage, targetBranch, cfg.blockFor(associatedEnd));
                    }
                }
                continue;
            }

            /*
             * We can do just plain inserts into all other nodes. Now we just need to prove that all
             * transitive usages are dominated by the given branch.
             */

            if (usagesToSkip.contains(usage)) {
                log(logBuilder, "        not doing it for %s (already dominated by another branch)\n", usage);
            }

            if (!checkTransitiveUsages(analysis, logBuilder, usage, value, targetBranch)) {
                /*
                 * There is a usage that is not dominated by targetBranch, we don't insert an
                 * inference node here.
                 */
                continue;
            }

            /*
             * If we reach here, all found transitive usages of this usage are dominated by the
             * given begin, therefore we can insert the inferred fact into this usage.
             */
            log(logBuilder, "    inserting transitive %s\n", usage);
            createInferenceNode(analysis, generator, branch, value, fact, usage);
            printLog = true;
            inferredForUsages.add(usage);

            // this usage is dominated by the current branch and is therefore not dominated by any
            // other branch of the given control branch
            usagesToSkip.add(usage);
        }

        /*
         * Consider the following code with the optimal inferences inserted as pseudo instructions:
         */
        /*-
         * boolean c1 = a == 2
         * if (c1) {
         *     infer(a, 2)
         *     // ...
         * }
         * boolean c2 = a > 0
         * if (c2) {
         *     infer(a, 1..MAX)
         *     if (c1) {
         *         infer(a, 2)
         *         // ...
         *     }
         *     // ...
         * }
         */
        /*
         * For InferredFactNodes to produce an accurate result, we rely on a very specific graph
         * shape. Consider the graph structure at the first IF condition:
         */
        /*-
         *  Value(a)       Const(2)
         *   |     \        /
         *   |     EqualsNode
         *   |     /        \
         *   |    /        IfNode
         *   |   /         /    \
         *   |  |  Begin(true)  Begin(false)
         *    \ |     /
         *   Inferred(c1)
         *      |
         *    Use(a)
         */
        /*
         * The important shape is, that 'Inferred(c1)' references 'EqualsNode' as its generator and
         * the 'Value(a)' it is encoding additional information for, is a direct input of its
         * generator. If this shape is broken, 'Inferred(c1)' is not be accurately updated if the
         * inferrable information for 'Value(a)' caused by 'EqualsNode' changes.
         *
         * Now imagine, in the earlier example, we already inserted the two inferences caused by c1
         * and are now about to insert the inference caused by c2. If we just insert the new
         * InferredFactNode, we would end up with the following construct at the third IF node:
         */
        /*-
         *        Value(a)    Const(2)
         *        /      \     /
         *  Inferred(c2)  EqualsNode
         *     /         /    |
         *    |     ----    IfNode
         *    |   /         /    \
         *    |  |  Begin(true)  Begin(false)
         *    |  |    /
         *   Inferred(c1)
         *      |
         *    Use(a)
         */
        /*
         * (For 'Inferred(c2)' all inputs except its value input were omitted for clarity.) Here, we
         * first infer that 'a' is positive in 'Inferred(c2)' and then infer that 'a' is equal to 2
         * in 'Inferred(c1)'. Now the connection from 'Inferred(c1)' to 'Value(a)' is severed. If
         * now the analysis later comes to the conclusion that the other input for 'EqualsNode' is
         * not a constant 2, we need to update all inferences for 'Value(a)' caused by 'EqualsNode'.
         * Because 'Inferred(c1)' does not have 'Value(a)' as a direct input anymore, it is
         * crucially not detected as an inference that needs to be corrected, leading to an
         * incorrect result.
         *
         * Destroying such delicate graph shapes can only happen if a new inference is inserted as
         * an input to an already existing inference, which is why we hold off on such insertions
         * until we are sure that by inserting this new node, we maintain this graph shape, or
         * otherwise skip inserting this new node. We may insert a new inference as an input of an
         * already existing inference, if and only if the new inference is not only an input to the
         * existing inference, but crucially also to its generating node.
         *
         * Slightly modifying the example (by pulling the calculation for the second usage of c1
         * into the second IF condition) to yield the following graph shape is a valid insertion of
         * 'Inferred(c2)' even though it is an input to another preexisting inference. This is
         * because here the value input for 'Inferred(c1)' is still a direct input to its generating
         * 'EqualsNode':
         */
        /*-
         *      Value(a)
         *         |
         *     Inferred(c2)  Const(2)
         *      /       \    /
         *     /      EqualsNode
         *    /         /    \
         *   |     ----    IfNode
         *   |   /         /    \
         *   |  |  Begin(true)  Begin(false)
         *   |  |    /
         *  Inferred(c1)
         *      |
         *    Use(a)
         */
        for (InferredFactNode<T> iFact : heldOffInferences) {
            if (inferredForUsages.contains(iFact.getGenerator())) {
                createInferenceNode(analysis, generator, branch, value, fact, iFact);
                printLog = true;
            }
        }

        if (logBuilder != null && printLog) {
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "%s", logBuilder);
        }

        if (!onlyCorrectPreviousInferencess) {
            if (!attemptedInferences.containsKey(value)) {
                attemptedInferences.put(value, EconomicSet.create());
            }
            attemptedInferences.get(value).add(Pair.create(generator, branch));
        }

        if (Assertions.detailedAssertionsEnabled(graph.getOptions())) {
            // inferred fact node integrity check
            for (ValueNode iu : inferredForUsages) {
                if (iu instanceof InferredFactNode<?> gif && gif.isOfGeneric(elementType)) {
                    /*
                     * If a newly inserted inferred fact has another inferred fact node as a usage,
                     * we need to check that we did not sever the older inferred fact node and its
                     * condition.
                     */
                    GraalError.guarantee(gif.getGenerator().inputs().contains(gif.getOriginalNode()), "Broke inference %s while inserting inference %s in %s (%s)", gif, value, branch, fact);
                }
            }
        }
    }

    /**
     * Checks if all transitive usages of {@code usage} are dominated by {@code targetBranch}.
     */
    private static <T> boolean checkTransitiveUsages(DFAnalysis<T> analysis, StringBuilder logBuilder, ValueNode usage, ValueNode value, HIRBlock targetBranch) {
        if (true) {
            return recursiveTransitiveCheck(analysis.dominanceCache, new NodeFlood(analysis.graph), usage, analysis, logBuilder, usage, value, targetBranch);
        }

        final ControlFlowGraph cfg = analysis.cfg;
        NodeFlood usageWorkList = analysis.graph.createNodeFlood();
        usageWorkList.add(usage);
        int limit = 15;
        int loopCnt = 0;
        for (Node curTransitiveNode : usageWorkList) {
            CompilationAlarm.checkProgress(analysis.graph);
            if (loopCnt++ > limit) {
                return false;
            }
            if (!(curTransitiveNode instanceof ValueNode curTransitiveUsage)) {
                return false;
            }
            if (curTransitiveUsage instanceof ConditionalNode) {
                log(logBuilder, "        not doing it for %s -> %s (conditional)\n", usage, curTransitiveUsage);
                return false;
            }

            HIRBlock block = switch (curTransitiveUsage) {
                case InferredFactNode<?> iFact ->
                    iFact.isOfGeneric(analysis.elementType) ? cfg.blockFor(iFact.getGuard()) : null;
                case ProxyNode proxy -> cfg.blockFor(proxy.proxyPoint());
                case FixedNode fixed -> cfg.blockFor(fixed);
                case ValuePhiNode phi ->
                    throw GraalError.shouldNotReachHere("Found %s in usageWorkList while inserting inferred facts for %s".formatted(phi, value));
                default -> null;
            };
            if (block != null) {
                if (!targetBranch.dominates(block)) {
                    // no inference can be inserted for this usage
                    log(logBuilder, "        not doing it for %s -> %s (%s does not dominate %s)\n", usage, curTransitiveUsage, targetBranch, block);
                    return false;
                } else {
                    log(logBuilder, "    ((-> %s) %s dominates %s)\n", curTransitiveUsage, targetBranch, block);
                }
            } else {
                if (curTransitiveNode instanceof GuardedNode guarded) {
                    switch (checkGuarded(cfg, targetBranch, guarded)) {
                        case TRUE -> {
                            // the dominance check succeeded for this guard, therefore we skip
                            // to
                            // the next iteration
                            log(logBuilder, "    ((-> %s) %s dominates %s)\n", curTransitiveUsage, targetBranch, guarded);
                            continue;
                        }
                        case FALSE -> {
                            log(logBuilder, "        not doing it for %s -> %s (%s does not dominate %s)\n", usage, curTransitiveUsage, targetBranch, guarded);
                            return false;
                        }
                        // if the guard based dominance check did not yield a result (i.e.
                        // UNKNOWN),
                        // we continue on just like any other floating node
                    }
                }
                int usageCnt = 0;
                for (Node usageOfUsage : curTransitiveUsage.usages()) {
                    ValueNode u;
                    if (usageOfUsage instanceof ValueNode vu) {
                        u = vu;
                    } else {
                        // we do not want to mess with any unknown non-value nodes, therefore we
                        // do not insert an inference here
                        log(logBuilder, "        not doing it for %s -> %s (transitive non-value usage)\n", usage, usageOfUsage);
                        return false;
                    }
                    if (MemoryKill.isMemoryKill(curTransitiveUsage) && isMemoryUsage(curTransitiveUsage, u)) {
                        // we can not full reason about memory edges, therefore we can not
                        // insert an inference here
                        log(logBuilder, "        not doing it for %s -> %s (memory edge)\n", usage, curTransitiveUsage);
                        return false;
                    }
                    // to check phis, we need the associated input, therefore we do no add phis
                    // to the work list but rather evaluate domination here
                    if (u instanceof ValuePhiNode phi) {
                        for (AbstractEndNode associatedEnd : getEndsForPhiInput(phi, curTransitiveUsage)) {
                            if (targetBranch.dominates(cfg.blockFor(associatedEnd))) {
                                log(logBuilder, "    ((PHI) %s dominates %s)\n", targetBranch, cfg.blockFor(associatedEnd));
                            } else {
                                log(logBuilder, "        not doing it for %s -> %s ((PHI) %s does not dominate %s)\n",
                                                usage, curTransitiveUsage, targetBranch, cfg.blockFor(associatedEnd));
                                return false;
                            }
                        }
                    } else {
                        usageWorkList.add(u);
                    }
                    usageCnt++;
                }

                if (usageCnt < 1) {
                    // this a floating node without usages, we have no clue in which branch we
                    // are, therefore we do not insert an inference here
                    log(logBuilder, "        not doing it for %s -> %s (unused)\n", usage, curTransitiveUsage);
                    return false;
                }
            }
        }

        // if we pass this loop, all transitive usages are dominated by the targetBranch
        return true;
    }

    private static <T> boolean recursiveTransitiveCheck(EconomicMap<Node, HIRBlock> domMap, NodeFlood flood, Node curTransitiveNode, DFAnalysis<T> analysis, StringBuilder logBuilder,
                    ValueNode usage,
                    ValueNode value, HIRBlock targetBranch) {
        final ControlFlowGraph cfg = analysis.cfg;
        flood.add(curTransitiveNode);
        if (!(curTransitiveNode instanceof ValueNode curTransitiveUsage)) {
            return false;
        }
        if (curTransitiveUsage instanceof ConditionalNode) {
            log(logBuilder, "        not doing it for %s -> %s (conditional)\n", usage, curTransitiveUsage);
            return false;
        }

        HIRBlock block = switch (curTransitiveUsage) {
            case InferredFactNode<?> iFact ->
                iFact.isOfGeneric(analysis.elementType) ? cfg.blockFor(iFact.getGuard()) : null;
            case ProxyNode proxy -> cfg.blockFor(proxy.proxyPoint());
            case FixedNode fixed -> cfg.blockFor(fixed);
            case GuardedNode guarded -> guarded.getGuard() instanceof FixedNode fn ? cfg.blockFor(fn) : null;
            case ValuePhiNode phi ->
                throw GraalError.shouldNotReachHere("Found %s in usageWorkList while inserting inferred facts for %s".formatted(phi, value));
            default -> domMap.get(curTransitiveNode);
        };
        if (block != null) {
            domMap.put(curTransitiveNode, block);
            if (!targetBranch.dominates(block)) {
                // no inference can be inserted for this usage
                log(logBuilder, "        not doing it for %s -> %s (%s does not dominate %s)\n", usage, curTransitiveUsage, targetBranch, block);
                return false;
            } else {
                log(logBuilder, "    ((-> %s) %s dominates %s)\n", curTransitiveUsage, targetBranch, block);
                domMap.put(curTransitiveNode, block);
                return true;
            }
        } else {
            if (curTransitiveNode instanceof GuardedNode guarded) {
                switch (checkGuarded(cfg, targetBranch, guarded)) {
                    case TRUE -> {
                        // the dominance check succeeded for this guard, therefore we skip
                        // to the next iteration
                        log(logBuilder, "    ((-> %s) %s dominates %s)\n", curTransitiveUsage, targetBranch, guarded);
                        if (guarded instanceof FixedNode fxg) {
                            domMap.put(curTransitiveNode, cfg.blockFor(fxg));
                        }
                        return true;
                    }
                    case FALSE -> {
                        log(logBuilder, "        not doing it for %s -> %s (%s does not dominate %s)\n", usage, curTransitiveUsage, targetBranch, guarded);
                        return false;
                    }
                    // if the guard based dominance check did not yield a result (i.e.
                    // UNKNOWN), we continue on just like any other floating node
                }
            }
            int usageCnt = 0;
            HIRBlock myDom = null;
            for (Node usageOfUsage : curTransitiveUsage.usages()) {
                ValueNode u;
                if (usageOfUsage instanceof ValueNode vu) {
                    u = vu;
                } else {
                    // we do not want to mess with any unknown non-value nodes, therefore we
                    // do not insert an inference here
                    log(logBuilder, "        not doing it for %s -> %s (transitive non-value usage)\n", usage, usageOfUsage);
                    return false;
                }
                if (MemoryKill.isMemoryKill(curTransitiveUsage) && isMemoryUsage(curTransitiveUsage, u)) {
                    // we can not full reason about memory edges, therefore we can not
                    // insert an inference here
                    log(logBuilder, "        not doing it for %s -> %s (memory edge)\n", usage, curTransitiveUsage);
                    return false;
                }
                // to check phis, we need the associated input, therefore we do no add phis
                // to the work list but rather evaluate domination here
                if (u instanceof ValuePhiNode phi) {
                    var iList = getEndsForPhiInput(phi, curTransitiveUsage);
                    for (AbstractEndNode associatedEnd : iList) {
                        var blk = cfg.blockFor(associatedEnd);
                        if (targetBranch.dominates(blk)) {
                            log(logBuilder, "    ((PHI) %s dominates %s)\n", targetBranch, blk);
                        } else {
                            log(logBuilder, "        not doing it for %s -> %s ((PHI) %s does not dominate %s)\n",
                                            usage, curTransitiveUsage, targetBranch, blk);
                            return false;
                        }
                        myDom = myDom == null ? blk : (HIRBlock) AbstractControlFlowGraph.commonDominator(myDom, blk);
                    }
                } else {
                    if (!flood.isMarked(u) && !recursiveTransitiveCheck(domMap, flood, u, analysis, logBuilder, usage, value, targetBranch)) {
                        return false;
                    }
                    GraalError.guarantee(domMap.containsKey(u), "WHAT???");
                    myDom = myDom == null ? domMap.get(u) : (HIRBlock) AbstractControlFlowGraph.commonDominator(myDom, domMap.get(u));
                }
                usageCnt++;
            }

            if (usageCnt < 1) {
                // this a floating node without usages, we have no clue in which branch we
                // are, therefore we do not insert an inference here
                log(logBuilder, "        not doing it for %s -> %s (unused)\n", usage, curTransitiveUsage);
                return false;
            }
            domMap.put(curTransitiveNode, myDom);
            return true;
        }
    }

    private static TriState checkGuarded(ControlFlowGraph cfg, HIRBlock targetBranch, GuardedNode guardedNode) {
        if (true) {
            if (guardedNode.getGuard() instanceof FixedNode fn) {
                return TriState.get(targetBranch.dominates(cfg.blockFor(fn)));
            } else {
                return TriState.UNKNOWN;
            }
        }
        return switch (guardedNode.getGuard()) {
            case FixedNode fn ->
                TriState.get(targetBranch.dominates(cfg.blockFor(fn)));
            case GuardNode gn ->
                gn.getAnchor() instanceof FixedNode fn ? TriState.get(targetBranch.dominates(cfg.blockFor(fn))) : TriState.UNKNOWN;
            case MultiGuardNode mg -> {
                /*
                 * For a multiguard node to be dominated by the target branch, the target branch
                 * needs to dominate at least one input and all other inputs need to dominate the
                 * target branch. If the target branch dominates no input, the guard is not
                 * dominated by the target branch. We only check direct inputs of the multiguard. If
                 * one of these inputs floats itself, we abort the dominance check.
                 */
                boolean targetDominates = false;
                boolean othersDominate = true;
                for (ValueNode anchor : mg.getGuards()) {
                    if (!(anchor instanceof FixedNode fn)) {
                        yield TriState.UNKNOWN;
                    }
                    HIRBlock gBlock = cfg.blockFor(fn);
                    if (targetBranch.dominates(gBlock)) {
                        targetDominates = true;
                    } else if (!gBlock.dominates(targetBranch)) {
                        othersDominate = false;
                    }
                }
                yield targetDominates && othersDominate ? TriState.TRUE : TriState.FALSE;
            }
            case null, default ->
                TriState.UNKNOWN;
        };
    }

    private static <T> void createInferenceNode(DFAnalysis<T> analysis, ValueNode generator, AbstractBeginNode branch, ValueNode value, T fact, ValuePhiNode phiNode, AbstractEndNode at) {
        // validate input
        if (analysis.domain.isWeakerThan(analysis.elemMap.getOrUnevaluated(value), fact).isTrue()) {
            final ControlFlowGraph cfg = analysis.cfg;
            HIRBlock blkBranch = cfg.blockFor(branch);
            HIRBlock blkEnd = cfg.blockFor(at);
            /*-
             * We may only insert inferences for the following input edges:
             *  - all input edges of PHIs we have not evaluated yet
             *  - currently unreachable input edges of PHIs
             *  - currently optimistically assumed to be unreachable input edges of loop PHIs
             */
            GraalError.guarantee(!analysis.elemMap.isEvaluated(phiNode) ||
                            analysis.edgeMap.get(blkEnd, cfg.blockFor(phiNode.merge())) == DFEdgeMap.Reachability.UNREACHABLE ||
                            (analysis.workList.isOptimisticLoopPhi(phiNode) && !phiNode.merge().forwardEndAt(0).equals(at)),
                            "Must not change reachable input of PHI (%s) via resetting (inserting %s for %s with usage %s in %s at %s)",
                            phiNode, fact, value, phiNode.valueAt(at), branch, at);
            GraalError.guarantee(blkBranch.dominates(blkEnd), "Somehow failed dominance check");
        }
        createInferenceNode(analysis, generator, branch, value, fact, iFact -> phiNode.setValueAt(at, iFact));
    }

    private static <T> void createInferenceNode(DFAnalysis<T> analysis, ValueNode generator, AbstractBeginNode branch, ValueNode value, T fact, ValueNode usage) {
        assert !(usage instanceof ValuePhiNode) : "Insertions of inferences into PHI nodes need to specify at which usage to insert";
        analysis.debug.log(DebugContext.VERY_DETAILED_LEVEL, "  resetting %s (previously %s)", usage, analysis.elemMap.getOrUnevaluated(usage));
        /**
         * We may already have evaluated the node (and its usages) we are trying to insert the given
         * fact into. To still be able to compute an accurate result for these nodes, we need to reset
         * these nodes to be able to correctly recalculate their values from a blank slate.
         *
         * We can safely reset these nodes because inferences are only ever done to provide additional
         * context for values that have not yet (even transitively) been used in control flow related
         * calculations. Consider the following example:
         *
         * @formatter:off
         * const = 0
         * if (x == const)
         *     z = x + y;
         * else
         *     z = 4;
         * PHI(z);
         * @formatter:on
         *
         * Because value propagation is eager and unordered, we may evaluate the expression x + y to an
         * unknown domain element before we evaluate the condition x == 0. Once we inferred that x = 0
         * for this use because of x == 0, we might be able to evaluate x + y to a more precise value
         * than before. This violates monotonicity, which is normally forbidden: analysis intermediate
         * results may only become weaker (less precise) over the course of the analysis. We can safely
         * reset the value for x + y to UNEVALUATED and re-evaluate it in light of the new information
         * about x using {@link DFAMap#resetNodeAndUsages}.
         *
         * In our analysis this is safe because even though x + y may already be evaluated, PHI(z) (a
         * transitive usage of x related to control flow) is sure to not be evaluated yet because if
         * control flow was evaluated previously, const would have to be evaluated earlier (and thereby
         * the inference calculation would have happened earlier) due to conditions the given transfer
         * function must obey (for details, see {@link AnalysisDomainDefinition#transfer} and
         * {@link DFAnalysis}).
         */
        createInferenceNode(analysis, generator, branch, value, fact, inference -> value.replaceAtUsages(inference, usage::equals));
        analysis.elemMap.resetNodeAndUsages(usage);
    }

    private static <T> void createInferenceNode(DFAnalysis<T> analysis, ValueNode generator, AbstractBeginNode branch, ValueNode value, T fact, Consumer<InferredFactNode<T>> inserter) {
        final StructuredGraph graph = analysis.graph;
        final DebugContext debug = analysis.debug;
        final ControlFlowGraph cfg = analysis.cfg;
        final AnalysisDomainDefinition<T> domain = analysis.domain;
        final EconomicMap<ValueNode, EconomicSet<Pair<ValueNode, AbstractBeginNode>>> attemptedInferences = analysis.attemptedInferences;

        // we know at this point that this inference node is unique
        InferredFactNode<T> iFactNode = graph.addWithoutUnique(new InferredFactNode<>(analysis.elementType, domain, generator, value, branch, fact));
        inserter.accept(iFactNode);
        if (attemptedInferences.containsKey(value)) {
            // collect all attempted inferences for 'value' and the (maybe newly inserted)
            // iFactNode
            EconomicSet<Pair<ValueNode, AbstractBeginNode>> tried = EconomicSet.create(attemptedInferences.get(value));
            if (attemptedInferences.containsKey(iFactNode)) {
                tried.addAll(attemptedInferences.get(iFactNode));
            }
            // remove the current branch since inserting inferences into itself is impossible
            tried.remove(Pair.create(generator, branch));
            /*
             * Without actually attempting a dominance check for all usages of the newly created
             * InferredFactNode. This is because anything that is dominated by this new node, is
             * also dominated by 'value'. Therefore, we can safely assume all inferences, that were
             * attempted for 'value' as also attempted for the new node, skipping possibly expensive
             * dominance calculations in the future.
             */
            if (!tried.isEmpty()) {
                attemptedInferences.put(iFactNode, tried);
            }
        }
        debug.log(DebugContext.VERY_DETAILED_LEVEL, " inserting inference %s for %s on %s", fact, value, cfg.blockFor(branch));
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "after inference %s for %s on %s", iFactNode, value, cfg.blockFor(branch));
        analysis.inferences.add(iFactNode);
        analysis.workList.schedule(iFactNode);
    }

    private static void log(StringBuilder logBuilder, String format, Object arg) {
        if (logBuilder != null) {
            logBuilder.append(format.formatted(arg));
        }
    }

    private static void log(StringBuilder logBuilder, String format, Object arg0, Object arg1) {
        if (logBuilder != null) {
            logBuilder.append(format.formatted(arg0, arg1));
        }
    }

    private static void log(StringBuilder logBuilder, String format, Object arg0, Object arg1, Object arg2) {
        if (logBuilder != null) {
            logBuilder.append(format.formatted(arg0, arg1, arg2));
        }
    }

    private static void log(StringBuilder logBuilder, String format, Object arg0, Object arg1, Object arg2, Object arg3) {
        if (logBuilder != null) {
            logBuilder.append(format.formatted(arg0, arg1, arg2, arg3));
        }
    }
}
