/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.dfanalysis;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;

import jdk.graal.compiler.core.common.util.IntList;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.vm.ci.meta.TriState;

/**
 * <p>
 * Interface to define an analysis domain for {@link DFAnalysis}. This class is intended to be a
 * stateless description of the <a href="https://en.wikipedia.org/wiki/Lattice_(order)">complete
 * lattice</a> representing the desired analysis domain.
 * </p>
 * <p>
 * The given lattice should be formed by elements that are immutable and exhibit value semantics.
 * For those elements, a partial order (isWeakerThan (interpreted as "is less precise than"),
 * {@code <}) should be defined so that {@code unrestricted <= x <= unevaluated} for all elements
 * {@code x}. {@code unevaluated} intuitively represents a yet to be evaluated value and is the
 * strongest possible value in the lattice while {@code unrestricted} is the weakest possible
 * element that denotes that the analysis tried, but can not infer any information for the given
 * point in the program. {@code x < y} describes a relationship between two values where we "know
 * less about {@code x} than about {@code y}" or we have "stronger" information about {@code y} in
 * the sense that the information about {@code y} is more precise or applicable in fewer branches of
 * the program. If both {@code x} and {@code y} describe a set of possible values, {@code y} would
 * be a subset of {@code x}.
 * </p>
 * <p>
 * To form a lattice, two operations need to be implemented. <br/>
 * {@link AnalysisDomainDefinition#merge} calculates the most precise result after two paths
 * converge at a control flow merge node. When using sets of possible values, this operation equates
 * to set union. The merge operation is used to calculate a domain value for a phi node, given
 * domain values for the phi's inputs. <br/>
 * {@link AnalysisDomainDefinition#strengthen} adds additional information to a value when entering
 * a specific control flow branch making the information more precise. It calculates the most
 * general value that encodes all restrictions of both inputs. When using sets of possible values,
 * this operation equates to set intersection. The strengthen operation is used to calculate a
 * domain value for a pi node or a similar {@link InferredFactNode}, given a domain value for the
 * node's input and information about the controlling condition.
 * </p>
 * <p>
 * These two operations need be complete in the sense that given any two inputs, there will always
 * be a resulting element that is part of the given lattice. Additionally, these two operations need
 * to be commutative and associative. They also interact with the partial order in the sense that
 * {@code merge(x,y) <= x and y} and {@code strengthen(x,y) >= x and y}. In fact, isWeakerThan can
 * be calculated by {@code x != y && merge(x,y) == x or y}, but in many cases, there is a more
 * efficient way to calculate this relation instead of calculating a possibly expensive
 * {@code merge}. To define a cheap isWeakerThan function, override the
 * {@link AnalysisDomainDefinition#isWeakerThan} method.
 * </p>
 * <p>
 * The dataflow analysis uses the domain definition to iteratively compute domain values for nodes
 * in the graph. Over the course of the analysis, the domain value for every node will gradually
 * weaken until a fixed point is reached.
 * </p>
 * <p>
 * This domain definition is intended as a collection of stateless functions. The dataflow analysis
 * uses these functions to iteratively compute domain values for nodes in the graph. Over the course
 * of the analysis, the domain value for every node will gradually weaken until a fixed point is
 * reached.
 * </p>
 * <p>
 * Example: {@link jdk.graal.compiler.core.common.type.Stamp}s form complete lattices with
 * {@code unrestricted} as is, {@code empty} as {@code unevaluated}, {@code meet} as {@code merge}
 * and {@code join} as {@code strengthen}.
 * </p>
 *
 * @param <ELEM_TYPE> Type of the domain elements used for the analysis.
 */
public interface AnalysisDomainDefinition<ELEM_TYPE> {
    /**
     * <p>
     * Retrieves the {@code unevaluated} element for a given node. This method may return
     * {@code null} if the given node is not of interest to the analysis at hand (see
     * {@link AnalysisDomainDefinition#isOfInterest}). An {@code unevaluated} element represents the
     * strongest element in the given lattice.
     * </p>
     * <p>
     * This method may produce different {@code unevaluated} elements for different types of nodes.
     * This effectively splits the lattice of all elements into multiple smaller sub-lattices, one
     * for each type (e.g. integers, floating point numbers, logic values, etc.). While this does
     * not affect the mechanics of the analysis, it may ease the use of {@code unevaluated} elements
     * in calculations. To unify everything into a singular lattice, just return one global
     * {@code unevaluated} element here. This is the same for
     * {@link AnalysisDomainDefinition#unrestricted(ValueNode)}.
     * </p>
     */
    ELEM_TYPE unevaluated(ValueNode node);

    /**
     * Retrieves the {@code unevaluated} element for the smaller sub-lattice of which {@code elem}
     * is a part of.
     */
    ELEM_TYPE unevaluated(ELEM_TYPE elem);

    /**
     * Retrieves the {@code unrestricted} element for a given node. This method may return
     * {@code null} if the given node is not of interest to the analysis at hand (see
     * {@link AnalysisDomainDefinition#isOfInterest}). The {@code unrestricted} element represents
     * the weakest element in the given lattice.
     */
    ELEM_TYPE unrestricted(ValueNode node);

    /**
     * Retrieves the {@code unrestricted} element for the smaller sub-lattice of which {@code elem}
     * is a part of.
     */
    ELEM_TYPE unrestricted(ELEM_TYPE elem);

    /**
     * Calculates a value at a point where values from two control flow paths (represented by the
     * two arguments) merge into one (this operation is also called meet, union of the sets of
     * possible values, greatest lower bound). The element produced by this method must satisfy
     * {@code x merge y <= x and y} and {@code unrestricted merge x == unrestricted}. This method
     * must always produce a valid domain element given any two domain elements as inputs. This
     * method also must be associative and commutative.
     */
    ELEM_TYPE merge(ELEM_TYPE x, ELEM_TYPE y);

    /**
     * Calculates the value with added restrictions after entering a particular control flow path
     * (this operation is also called join, intersection of the sets of possible values, lowest
     * upper bound). The element produced by this method must satisfy
     * {@code x strengthen y >= x and y} and {@code unevaluated strengthen x == unevaluated}. This
     * method must always produce a valid domain element given any two domain elements as inputs.
     * This method also must be associative and commutative.
     */
    ELEM_TYPE strengthen(ELEM_TYPE x, ELEM_TYPE y);

    /**
     * <p>
     * The transfer function for applying the given node's abstract semantics to the currently known
     * analysis information for its inputs.
     * </p>
     * <p>
     * For example, in the domain of integer stamps, if {@code node} is an addition and the input
     * map contains stamps [1 - 10] and [5 - 20] for its inputs, the transfer function should
     * produce a stamp [6 - 30].
     * </p>
     * <p>
     * Generally, this function is expected to treat unevaluated values as unrestricted, which can
     * be done implicitly by using {@link DFAMap#getOrUnrestricted} to obtain information about
     * input nodes. This transfer function must uphold monotonicity, meaning, interpreting
     * unevaluated values as unrestricted and given increasingly weaker inputs, the result of the
     * transfer function must be weaker or equal to the result given stronger inputs (i.e. given
     * inputs {@code a <= x & b <= y: transferForAdd(a, b) <= transferForAdd(x, y)}).
     * </p>
     * <p>
     * Nodes that may produce a stronger than unrestricted value if they still have unevaluated
     * inputs can pose problems with monotonicity. Consider for example a Conditional node that
     * first only has its value inputs evaluated ({@code cond: ???, trueVal: 1, falseVal: 2}). In
     * this case we could return the set {1, 2} as the set of possible results for the conditional.
     * If now the condition becomes evaluated to a constant true
     * ({@code cond: ???, trueVal: 1, falseVal: 2}) we could return {1} as the set of possible
     * results, which is stronger than the earlier result. This is despite upholding monotonicity
     * <i>interpreting unevaluated inputs as unrestricted</i>. In this case, even though all inputs
     * behave in a monotone manner, the output does not.
     * </p>
     * <p>
     * The framework has a mechanism to deal with such situations. To be able to deal with nodes
     * that may produce a stronger than unrestricted result even though not having all inputs
     * evaluated, a special case in {@link AnalysisDomainDefinition#countUnevaluatedInputs} has to
     * be implemented to inform the framework that the result given is preliminary since not all
     * inputs have been evaluated.
     * </p>
     *
     * @param node the node to evaluate.
     * @param map the current state of the analysis, intended to be used to retrieve the domain
     *            elements currently associated with the inputs of the given node.
     * @return a new domain element representing possible values that the given node may produce.
     */
    ELEM_TYPE transfer(ValueNode node, DFAMap<ELEM_TYPE> map);

    /**
     * <p>
     * This method is called for control split nodes that are believed to be reachable to determine
     * the reachability of their successors. The returned array is not mutated and can therefore be
     * reused for later calls of this method.
     * </p>
     * <p>
     * For example, if {@code split} is an IfNode with its input being evaluated to {@code true} the
     * expected return is {@code [true, false]}. This indicates that the true branch is potentially
     * reachable while the false branch is not reachable given the current state of the analysis.
     * </p>
     *
     * @param split the node to evaluate.
     * @param map the current state of the analysis, intended to be used to retrieve the domain
     *            elements currently associated with the inputs of the given node.
     * @return an array indicating for each successor if it is reachable given the current state of
     *         the analysis, or null if the analysis does not allow for reasoning about control flow
     *         for the given node.
     */
    boolean[] splitReachability(ControlSplitNode split, DFAMap<ELEM_TYPE> map);

    /**
     * <p>
     * This method is called to calculate dataflow facts generated by nodes that influence control
     * flow. The result should NOT return the actual full calculated value for each input in each
     * successor branch, but rather ONLY the additional information that can be inferred for each
     * given input in each branch which is caused by the provided node.
     * </p>
     * <p>
     * The first dimension of the returned array represents the successors (true/false or switch key
     * successors) the associated information is meant for, while the second dimension is for the
     * inputs of the given node. Each level of the return value may be null, thereby indicating that
     * for the given combination of node, successor and input there will never exist any inferrable
     * information about the given value.
     * </p>
     * <p>
     * For example, if the given node is an IntegerEqualsNode with inputs {@code X = unrestricted}
     * and {@code Y = 1}, the expected result is the table below. Keep in mind that this ony
     * describes <b>additional</b> information that can be inferred for each input. This is why in
     * this example the additional information for Y is always just unknown.
     * </p>
     *
     * <pre>
     *               |   X   |      Y
     * ------------------------------------
     *  true branch  |   1   | unrestricted
     *  false branch | not 1 | unrestricted
     * </pre>
     *
     * <p>
     * To easily build such tables, use the static "inference" helper methods of this interface like
     * {@link AnalysisDomainDefinition#ifInference}.
     * </p>
     *
     * @param node the node to calculate inferrable information for.
     * @param map the current state of the analysis, intended to be used to retrieve the domain
     *            elements currently associated with the inputs of the given node.
     * @return a table containing for each branch and input any additional information that can be
     *         inferred by the given node.
     */
    ELEM_TYPE[][] calcInferrableValues(ValueNode node, DFAMap<ELEM_TYPE> map);

    /**
     * <p>
     * Nodes like for example the PIs or ConditionalNodes may produce suboptimal values if they
     * evaluated without having all inputs evaluated. Consider for example a ConditionalNode with
     * constant inputs and an unevaluated condition ({@code cond: ???, trueVal: 1, falseVal: 2}).
     * Optimally implementing the transfer function here would yield a set of possible values of {1,
     * 2}. If later the condition becomes known and is for example true
     * ({@code cond: true, trueVal: 1, falseVal: 2}), the optimal result would be {1}, which is
     * stronger than the previous result which is generally not allowed.
     * </p>
     * <p>
     * Recall {@link AnalysisDomainDefinition#transfer}: "<i>Interpreting unevaluated values as
     * unrestricted</i> and given increasingly weaker inputs, the result of the transfer function
     * must be weaker or equal to the result given stronger inputs."
     * </p>
     * <p>
     * This allows the transfer function to break monotonicity even though all inputs (when not
     * reinterpreted) are behaving monotone. To handle such situations, the framework needs to know
     * if a given node still has unevaluated inputs.
     * </p>
     * <p>
     * This function must provide the framework with the number of unevaluated inputs for all nodes
     * that produce a stronger than unrestricted value even if they still have unevaluated inputs.
     * This is so that the framework can mark such results as preliminary and reset them if needed
     * to still allow cases like the earlier example with the ConditionalNode.
     * </p>
     * 
     * @param node the node to check the inputs for
     * @param map the current state of the analysis, intended to be used to retrieve the domain
     *            elements currently associated with the inputs of the given node.
     * @return the number of unevaluated inputs if this node is such a special node and has
     *         unevaluated inputs that are relevant to transfer, else this method is supposed to
     *         return {@code -1}.
     */
    default int countUnevaluatedInputs(ValueNode node, DFAMap<ELEM_TYPE> map) {
        return -1;
    }

    /*
     * =============================================================================================
     * Optional methods to ensure traces through the domain are finite and of reasonable length. If
     * 'supportsWidening' returns true, 'widen' is expected to be implemented. If 'supportsWidening'
     * returns false, 'widen' will never be called.
     * =============================================================================================
     */

    default boolean supportsWidening() {
        return false;
    }

    default void widen(List<ValuePhiNode> phis, IntList reachableInputIndices, ELEM_TYPE[] result, DFAMap<ELEM_TYPE> map, int evalCnt) {
        throw GraalError.unimplementedParent();
    }

    /*
     * =============================================================================================
     * Default helper methods for easy access (they may be overridden to provide a better
     * implementation tailored to specific to the analysis domain). Overriding 'isWeakerThan' is
     * recommended to avoid a possibly expensive calculation of 'merge'.
     * =============================================================================================
     */

    default ELEM_TYPE[] controlFlowMerge(List<ValuePhiNode> phis, IntList reachableInputIndices, DFAMap<ELEM_TYPE> map) {
        @SuppressWarnings("unchecked")
        ELEM_TYPE[] phiElems = (ELEM_TYPE[]) Array.newInstance(map.getGenericType(), phis.size());
        // for all phis
        for (int i = 0; i < phis.size(); i++) {
            ValuePhiNode curPhi = phis.get(i);
            ELEM_TYPE result = unevaluated(curPhi);
            // calculate the merge over all reachable inputs
            for (int j = 0; j < reachableInputIndices.size(); j++) {
                int rIdx = reachableInputIndices.get(j);
                result = merge(result, map.getOrUnrestricted(curPhi.valueAt(rIdx)));
                if (isUnrestricted(result)) {
                    // nothing to be gained here, early exit
                    break;
                }
            }
            phiElems[i] = result;
        }
        return phiElems;
    }

    default boolean isUnevaluated(ELEM_TYPE elem) {
        return isEqual(elem, unevaluated(elem));
    }

    default boolean isUnrestricted(ELEM_TYPE elem) {
        return isEqual(elem, unrestricted(elem));
    }

    default boolean isEqual(ELEM_TYPE x, ELEM_TYPE y) {
        return Objects.equals(x, y);
    }

    default TriState isWeakerOrEqual(ELEM_TYPE x, ELEM_TYPE y) {
        return isEqual(x, y) ? TriState.TRUE : isWeakerThan(x, y);
    }

    /**
     * <p>
     * Default way of calculating a isWeakerThan relationship. Overriding this method is highly
     * encouraged because often, calculating a full {@link AnalysisDomainDefinition#merge} is
     * expensive while simply checking for isWeakerThan is doable in an easier, more efficient way.
     * </p>
     * <p>
     * This operation represents the notion of "weaker information", meaning for {@code x < y}
     * {@code x} encodes a less precise information than {@code y}.
     * </p>
     *
     * @return {@code UNKNOWN} if the input values are not comparable, {@code TRUE} if x is strictly
     *         weaker than y, {@code FALSE} otherwise
     */
    default TriState isWeakerThan(ELEM_TYPE x, ELEM_TYPE y) {
        final ELEM_TYPE merged = merge(x, y);
        final boolean mex = isEqual(merged, x);
        final boolean mey = isEqual(merged, y);
        return !mex && !mey ? TriState.UNKNOWN : TriState.get(mex && !mey);
    }

    /**
     * This method is used to check if the given analysis is intended to evaluate a given node. This
     * check is done during the analysis to possibly skip irrelevant parts of the graph. All "nodes
     * of interest" must return a non-null value for
     * {@link AnalysisDomainDefinition#unevaluated(ValueNode)},
     * {@link AnalysisDomainDefinition#unrestricted(ValueNode)} and
     * {@link AnalysisDomainDefinition#transfer}.
     */
    default boolean isOfInterest(ValueNode node) {
        return unevaluated(node) != null;
    }

    /*
     * =============================================================================================
     * Helper methods to make building inference matrix more explicit.
     * =============================================================================================
     */
    @SuppressWarnings("unchecked")
    static <T> T[][] ifInference(Class<T> type, T[] trueBranch, T[] falseBranch) {
        if (trueBranch == null && falseBranch == null) {
            return null;
        } else {
            T[][] branches = (T[][]) Array.newInstance(type.arrayType(), 2);
            branches[0] = trueBranch;
            branches[1] = falseBranch;
            return branches;
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T[] binaryLogicInference(Class<T> type, T x, T y) {
        if (x == null && y == null) {
            return null;
        } else {
            T[] inputs = (T[]) Array.newInstance(type, 2);
            inputs[0] = x;
            inputs[1] = y;
            return inputs;
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T[] unaryLogicInference(Class<T> type, T value) {
        if (value == null) {
            return null;
        } else {
            T[] input = (T[]) Array.newInstance(type, 1);
            input[0] = value;
            return input;
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T[][] switchInference(Class<T> type, T[] inBranches) {
        if (inBranches == null) {
            return null;
        } else {
            T[][] branches = (T[][]) Array.newInstance(type.arrayType(), inBranches.length);
            for (int i = 0; i < branches.length; i++) {
                branches[i] = (T[]) Array.newInstance(type, 1);
                branches[i][0] = inBranches[i];
            }
            return branches;
        }
    }

    /*
     * =============================================================================================
     * Static constant objects to reduce allocations ("public static final" is implicit for fields
     * in interfaces). These arrays must not be modified.
     * =============================================================================================
     */
    /** Both branches of an {@code if} are reachable. */
    boolean[] TRUE_TRUE = new boolean[]{true, true};
    /** Only the {@code true} branch of an {@code if} is reachable. */
    boolean[] TRUE_FALSE = new boolean[]{true, false};
    /** Only the {@code false} branch of an {@code if} is reachable. */
    boolean[] FALSE_TRUE = new boolean[]{false, true};

    /*
     * =============================================================================================
     * Static helper methods for counting unevaluated inputs.
     * =============================================================================================
     */

    static int countUnevaluated(DFAMap<?> map, ValueNode a) {
        return map.isEvaluated(a) ? 0 : 1;
    }

    static int countUnevaluated(DFAMap<?> map, ValueNode a, ValueNode b) {
        int r = 0;
        if (!map.isEvaluated(a)) {
            r++;
        }
        if (!map.isEvaluated(b)) {
            r++;
        }
        return r;
    }

    static int countUnevaluated(DFAMap<?> map, ValueNode a, ValueNode b, ValueNode c) {
        int r = 0;
        if (!map.isEvaluated(a)) {
            r++;
        }
        if (!map.isEvaluated(b)) {
            r++;
        }
        if (!map.isEvaluated(c)) {
            r++;
        }
        return r;
    }
}
