/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi.nodes;

import static jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.replacements.nodes.MacroWithExceptionNode;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIBoxingUtils;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIExpansionPhase;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIOperations;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIType;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIUtils;
import jdk.vm.ci.meta.JavaKind;

/**
 * Common superclass for the nodes representing intrinsified Vector API operations. These represent
 * method calls that might be transformed into one (or a few) raw SIMD instructions. If such a
 * transformation is not possible or not beneficial, the node can be transformed back into an invoke
 * and possibly inlined.
 * <p/>
 *
 * The Vector API is designed so that each intrinsic can represent a large class of related
 * operations. For example, {@link VectorAPIBinaryOpNode} represents all lanewise binary operations
 * on all types of vectors and masks. The exact operation as well as the types of the vectors
 * involved are passed as arguments to the intrinsic method, so they are input nodes in the macro
 * node's argument list. Such operation and type input nodes will typically be constants, either
 * when the node is originally built, or through later rounds of inlining, partial escape analysis,
 * and canonicalization.
 * <p/>
 *
 * The API provided by this class, which must be implemented by concrete subclasses, consists of the
 * following methods:
 * <ul>
 * <li>{@link #vectorInputs} for determining which inputs to this node represent other Vector API
 * vector values</li>
 * <li>{@link #vectorStamp} for computing the {@link SimdStamp} (if any) of the vector value
 * produced by this node</li>
 * <li>{@link #canExpand} for determining if this node can be expanded to SIMD code</li>
 * <li>{@link #expand} for performing the actual expansion</li>
 * </ul>
 * These methods are used by {@link VectorAPIExpansionPhase} to decide if and how to expand these
 * macro nodes to raw SIMD computations.
 */
@NodeInfo
public abstract class VectorAPIMacroNode extends MacroWithExceptionNode implements IterableNodeType, Simplifiable {

    public static final NodeClass<VectorAPIMacroNode> TYPE = NodeClass.create(VectorAPIMacroNode.class);

    /**
     * A constant representing the vector produced by this macro node, if it can be constant folded.
     * Is {@code null} for nodes that we cannot constant fold, or that do not produce a vector at
     * all. This is deliberately private, as not even subclasses should access it. Use
     * {@link #maybeConstantValue} instead.
     */
    private final SimdConstant constantValue;

    protected VectorAPIMacroNode(NodeClass<? extends VectorAPIMacroNode> type, MacroParams macroParams, SimdConstant constantValue) {
        super(type, macroParams);
        this.constantValue = constantValue;
    }

    /**
     * Returns those inputs of this node that represent Vector API values. These are often other
     * {@link VectorAPIMacroNode}s but can also be references to Vector API objects (from constants
     * or memory reads), or value phis or proxies connecting such values.
     */
    public abstract Iterable<ValueNode> vectorInputs();

    /**
     * Returns a {@link SimdStamp} representing the vector values produced by this node, or a
     * {@code void} stamp if this node does not produce a value. Returns {@code null} if this node
     * should have a {@link SimdStamp} but the precise stamp is not known yet; it may become known
     * through later inlining or canonicalization.
     */
    public abstract Stamp vectorStamp();

    /**
     * Returns a {@link SimdConstant} representing the given node's value if it is known to be
     * constant. Returns {@code null} otherwise. The given node may be a {@link VectorAPIMacroNode},
     * but it may also be an object constant representing a Vector API value. The constant returned
     * here is not necessarily representable on the target, i.e., it may be larger than the target's
     * vector registers. Users must check for this.
     *
     * @param node the node for which to obtain a constant SIMD value
     * @param providers the providers used for constant lookup, must be non-{@code null} unless
     *            {@link #isSimdConstant} previously returned {@code true} for the given node
     */
    public static SimdConstant maybeConstantValue(ValueNode node, CoreProviders providers) {
        if (node instanceof VectorAPIMacroNode macro) {
            return macro.constantValue;
        } else {
            GraalError.guarantee(providers != null, "must only be called with non-null providers unless isSimdConstant(node)");
            if (node.isJavaConstant()) {
                ValueNode readConstant = VectorAPIBoxingUtils.tryReadSimdConstant(node.asJavaConstant(), providers);
                if (readConstant != null && readConstant.isConstant() && readConstant.asConstant() instanceof SimdConstant simdConstant) {
                    return simdConstant;
                }
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the given macro node is known to have a constant value. Note that
     * this returns {@code true} even if the constant value is not representable on the target
     * architecture, e.g., if it is a 256-bit vector but the target only has 128-bit vectors. See
     * {@link #isRepresentableSimdConstant} which also checks if the constant is representable on
     * the target.
     */
    public static boolean isSimdConstant(VectorAPIMacroNode macro) {
        return macro.constantValue != null;
    }

    /**
     * Checks if the given node {@linkplain #isSimdConstant is a constant} that is representable on
     * the given architecture. Returns {@code false} if the node is not a constant, or if it is too
     * large for the target architecture.
     */
    protected static boolean isRepresentableSimdConstant(VectorAPIMacroNode macro, VectorArchitecture vectorArch) {
        if (!isSimdConstant(macro)) {
            return false;
        }
        SimdStamp simdStamp = (SimdStamp) macro.vectorStamp();
        Stamp elementStamp = simdStamp.getComponent(0);
        int vectorLength = simdStamp.getVectorLength();
        return vectorArch.getSupportedVectorMoveLength(elementStamp, vectorLength) == vectorLength;
    }

    /**
     * Returns a {@link ConstantNode} representing this macro node's SIMD value. The caller must
     * have checked before that {@link #isRepresentableSimdConstant} returns {@code true} for the
     * node.
     */
    protected static ConstantNode asSimdConstant(VectorAPIMacroNode macro, VectorArchitecture vectorArch) {
        GraalError.guarantee(isRepresentableSimdConstant(macro, vectorArch), "must be a representable SIMD constant: %s", macro);
        SimdConstant constantValue = maybeConstantValue(macro, null);
        return new ConstantNode(constantValue, macro.vectorStamp());
    }

    /**
     * Canonicalizes the address computation in this macro node. Specifically, if the address's base
     * is a null pointer constant, the offset is to be interpreted as an absolute off-heap byte
     * address (from a MemorySegment access). In this case, we must remove the base altogether,
     * since the null pointer is not necessarily all zero bits.
     */
    protected static AddressNode improveAddress(AddressNode address) {
        if (address instanceof OffsetAddressNode offsetAddress && offsetAddress.getBase() != null && offsetAddress.getBase().isNullConstant()) {
            return OffsetAddressNode.create(offsetAddress.getOffset());
        }
        return address;
    }

    /**
     * Determines whether the operation represented by this node can be mapped to the target
     * {@code vectorArch}. Implementations must check whether all inputs representing vectors are
     * intrinsified to {@link VectorAPIMacroNode}s, whether all vector input stamps are compatible
     * with this node's {@link #vectorStamp()}, and whether the vector architecture supports the
     * actual operation.
     */
    public abstract boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps);

    /**
     * Computes an expansion of this node to direct SIMD code. Implementations may rely on the fact
     * that all vector inputs to this node have already been expanded, with their expansions
     * registered in the {@code expanded} map. This method should return a new node, which need not
     * be added to the graph. The caller is expected to add this node to the graph, add it to the
     * {@code expanded} map, and replace the original node with its expansion.
     */
    public abstract ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded);

    /**
     * Try to compute an improved stamp for this node. The {@code speciesClassIndex} refers to the
     * macro argument that gives this node's species class. When the macro node is originally built,
     * this argument will usually be a class constant referring to an exact vector type. But
     * sometimes it's a {@link GetClassNode} that is only constant folded later. So during
     * canonicalization, check again if we have a class constant now and can use it to refine the
     * stamp.
     *
     * @return this node's current stamp, or an improved version of the current stamp
     */
    protected ObjectStamp improveSpeciesStamp(CoreProviders providers, int speciesClassIndex) {
        ObjectStamp currentStamp = (ObjectStamp) stamp;
        if (!currentStamp.isExactType()) {
            ObjectStamp maybeNewStamp = VectorAPIUtils.nonNullStampForClassConstant(providers, getArgument(speciesClassIndex));
            if (maybeNewStamp != null && !stamp.equals(maybeNewStamp)) {
                return (ObjectStamp) stamp.improveWith(maybeNewStamp);
            }
        }
        return currentStamp;
    }

    /**
     * Try to compute an improved {@link #vectorStamp} for this node. If the given
     * {@code oldVectorStamp} is not {@code null}, it is returned unchanged. The input indices refer
     * to the macro arguments giving this node's vector class, element class, and length. When the
     * macro node is originally built, these arguments will usually be constants, but sometimes they
     * are only constant folded later.
     *
     * @return a {@link SimdStamp} matching the given inputs, if they are constants; {@code null}
     *         otherwise
     */
    protected static SimdStamp improveVectorStamp(SimdStamp oldVectorStamp, ValueNode[] arguments, int vmClassIndex, int elementClassIndex, int lengthIndex, CoreProviders providers) {
        if (oldVectorStamp != null) {
            return oldVectorStamp;
        }
        ValueNode vmClass = arguments[vmClassIndex];
        ValueNode eClass = arguments[elementClassIndex];
        ValueNode length = arguments[lengthIndex];
        StructuredGraph graph = vmClass.graph();
        if (graph == null || graph.isBeforeStage(GraphState.StageFlag.VECTOR_API_EXPANSION)) {
            /*
             * Don't compute SIMD stamps before we actually start a compilation. The reason we want
             * to delay until actual compilation time is that the stamp may differ between native
             * image build time and runtime compilation time since the native image target
             * architecture may have different CPU features from the runtime target architecture.
             */
            return null;
        }
        return VectorAPIUtils.stampForVectorClass(vmClass, eClass, length, providers);
    }

    protected static SimdStamp computeLogicStamp(SimdStamp inputStamp, VectorArchitecture vectorArch) {
        if (inputStamp == null) {
            return null;
        }
        return SimdStamp.broadcast(vectorArch.maskStamp(inputStamp.getComponent(0)), inputStamp.getVectorLength());
    }

    /**
     * Try to compute an improved binary op from the macro arguments, if needed. If {@code oldOp} is
     * not {@code null}, it is returned unchanged.
     */
    protected static ArithmeticOpTable.BinaryOp<?> improveBinaryOp(ArithmeticOpTable.BinaryOp<?> oldOp, ValueNode[] arguments, int oprIdArgIndex, SimdStamp vectorStamp, CoreProviders providers) {
        if (oldOp != null) {
            return oldOp;
        }
        int opcode = oprIdAsConstantInt(arguments, oprIdArgIndex, vectorStamp);
        if (opcode == -1) {
            return null;
        }
        if (vectorStamp.isIntegerStamp()) {
            return VectorAPIOperations.lookupIntegerBinaryOp(opcode);
        } else if (vectorStamp.isFloatStamp()) {
            return VectorAPIOperations.lookupFloatingPointBinaryOp(opcode);
        } else if (SimdStamp.isOpmask(vectorStamp)) {
            GraalError.guarantee(!VectorAPIUtils.vectorArchitecture(providers).logicVectorsAreBitmasks(), "opmasks not supported on this target");
            return VectorAPIOperations.lookupOpMaskBinaryOp(opcode);
        }
        return null;
    }

    protected static int oprIdAsConstantInt(ValueNode[] arguments, int oprIdArgIndex, SimdStamp vectorStamp) {
        if (vectorStamp == null) {
            return -1;
        }
        ValueNode oprId = arguments[oprIdArgIndex];
        if (!(oprId.isJavaConstant() && oprId.asJavaConstant().getJavaKind() == JavaKind.Int)) {
            return -1;
        }
        return oprId.asJavaConstant().asInt();
    }

    /**
     * If the {@code node}'s stamp according to the {@code tool}'s view is a non-null, exact-type
     * object stamp, return that stamp. Return {@code null} otherwise.
     */
    private static AbstractObjectStamp isExactNonNull(ValueNode node, SimplifierTool tool) {
        if (node.stamp(NodeView.from(tool)) instanceof AbstractObjectStamp stamp && stamp.isExactType() && stamp.nonNull()) {
            return stamp;
        } else {
            return null;
        }
    }

    @Override
    public final void simplify(SimplifierTool tool) {
        AbstractObjectStamp exactStamp = isExactNonNull(this, tool);
        if (exactStamp != null) {
            for (Node usage : usages()) {
                if (usage instanceof ValuePhiNode phi && phi.isLoopPhi() && phi.firstValue() == this && isExactNonNull(phi, tool) == null) {
                    /**
                     * The phi's first input (this node) has an exact type, but the phi itself does
                     * not. This happens often for the following common reduction pattern:
                     *
                     * <pre>
                     * final VectorSpecies<Integer> species = IntVector.SPECIES_PREFERRED;
                     * IntVector partialSums = IntVector.zero(species);   // this will have an exact
                     *                                                    // stamp
                     *
                     * loop {
                     *     // this will not have an exact stamp:
                     *     partialSums = partialSums.add(IntVector.fromArray(species, source, i));
                     *     // equivalently:
                     *     partialSums = partialSums.lanewise(ADD, IntVector.fromArray(species, source, i));
                     * }
                     * </pre>
                     *
                     * In this example, the zero is this macro node with a precise stamp (derived
                     * from the constant species), while the phi corresponds to the partialSums
                     * variable. This has an imprecise type because IntVector is an abstract type.
                     * We will inline through the add method but stop at the abstract
                     * IntVector.lanewise method, and we won't be able to make progress because
                     * lanewise is declared as returning an imprecise IntVector. Thus we can't
                     * intrinsify the vector arithmetic.
                     *
                     * With type profiles and deoptimization, the compiler can optimistically assume
                     * a precise type for the receiver of lanewise, inline accordingly, and see that
                     * it returns the same precise type. Canonicalization can then infer the precise
                     * type for the phi, and everything is statically typed in the graph.
                     *
                     * Without type profiles, or if we can't deoptimize for a bad type speculation,
                     * this simplification rule takes care of improving the phi's type. The improved
                     * type will then allow inlining through the lanewise method and allow us to
                     * intrinsify. Note that this problem isn't solvable by a general Java type
                     * analysis, we also need to know the domain-specific Vector API fact that the
                     * lanewise method always returns an object of the exact same type as its
                     * receiver.
                     *
                     * The propagation implemented here handles unary, binary, and ternary lanewise
                     * operations (e.g., abs, add, fma) and broadcasts of a scalar to a vector. It
                     * also handles control flow inside the loop, both with early loop ends and with
                     * merges inside the loop.
                     */
                    NodeFlood flood = new NodeFlood(graph());
                    flood.addAll(phi.usages());
                    for (Node n : flood) {
                        if (n instanceof PiNode pi) {
                            /* The pi's input is exact and non-null, the result must be too. */
                            addAllDelayPhis(pi.usages(), flood);
                        }
                        if (n instanceof MethodCallTargetNode callTarget) {
                            if (isTypeInvariantVectorAPIMethod(callTarget, flood)) {
                                flood.add(callTarget);
                                flood.add(callTarget.invoke().asNode());
                                addAllDelayPhis(callTarget.invoke().asNode().usages(), flood);
                            }
                        }
                        if (n instanceof ValuePhiNode otherPhi) {
                            addAllDelayPhis(otherPhi.usages(), flood);
                        }
                    }
                    boolean allBackValuesExact = true;
                    for (ValueNode backValue : phi.backValues()) {
                        if (!flood.isMarked(backValue)) {
                            allBackValuesExact = false;
                            break;
                        }
                    }
                    if (allBackValuesExact) {
                        /*
                         * Type propagation successfully returned along all of the loop's backedges.
                         * All the invokes we have added to the flood will return the same exact
                         * type. Record this in the graph.
                         */
                        for (Node n : flood.getVisited()) {
                            if (n instanceof Invoke invoke) {
                                FixedNode invokeNext = invoke.next();
                                invoke.setNext(null);
                                AbstractBeginNode guard = BeginNode.begin(invokeNext);
                                invoke.setNext(guard);
                                ValueNode pi = graph().addWithoutUnique(PiNode.create(invoke.asNode(), exactStamp, guard));
                                invoke.asNode().replaceAtUsages(pi, u -> u != pi && !(u instanceof FrameState));
                                tool.addToWorkList(pi.usages());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Set of known {@link #isTypeInvariantVectorAPIMethod type invariant} method names and their
     * arities.
     */
    private static final EconomicSet<Pair<String, Integer>> TYPE_INVARIANT_METHODS = EconomicSet.create();

    static {
        TYPE_INVARIANT_METHODS.add(Pair.create("broadcast", 2)); // scalar to vector
        TYPE_INVARIANT_METHODS.add(Pair.create("lanewise", 2));  // unary op
        TYPE_INVARIANT_METHODS.add(Pair.create("lanewise", 3));  // binary op
        TYPE_INVARIANT_METHODS.add(Pair.create("lanewise", 4));  // ternary op
    }

    /**
     * Returns true if the {@code callTarget} refers to a known "type invariant" method inside the
     * Vector API implementation. In this context, we use "type invariant" to mean a method that
     * returns a value of the same dynamic type as its receiver. For example, IntVector.lanewise is
     * type invariant in this sense: This abstract method is declared as returning an abstract
     * IntVector, but each of its overrides returns an exact type which is the same as the receiver
     * type.
     */
    private static boolean isTypeInvariantVectorAPIMethod(MethodCallTargetNode callTarget, NodeFlood knownExact) {
        NodeInputList<ValueNode> args = callTarget.arguments();
        if (!knownExact.isMarked(args.get(0))) {
            /* Receiver is not known to have an exact type. */
            return false;
        }
        if (TYPE_INVARIANT_METHODS.contains(Pair.create(callTarget.targetMethod().getName(), args.count())) &&
                        callTarget.targetMethod().format("%H").startsWith(VectorAPIType.VECTOR_PACKAGE_NAME)) {
            /* It's a known invariant method, and it really comes from the Vector API. */
            return true;
        }
        return false;
    }

    /**
     * Add all {@code nodes} to the {@code flood}, except only add phi nodes if all of their inputs
     * are already marked.
     */
    private static void addAllDelayPhis(Iterable<? extends Node> nodes, NodeFlood flood) {
        for (Node n : nodes) {
            if (n instanceof ValuePhiNode phiUsage) {
                boolean allValuesMarked = true;
                for (Node v : phiUsage.values()) {
                    if (!flood.isMarked(v)) {
                        allValuesMarked = false;
                        break;
                    }
                }
                if (allValuesMarked) {
                    flood.add(phiUsage);
                }
            } else {
                flood.add(n);
            }
        }
    }
}
