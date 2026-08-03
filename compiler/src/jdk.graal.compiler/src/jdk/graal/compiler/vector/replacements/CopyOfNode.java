/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements;

import java.util.Arrays;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.UnreachableBeginNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.extended.HubGetClassNodeInterface;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.ArrayLengthProvider;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;
import jdk.graal.compiler.replacements.nodes.MacroWithExceptionNode;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This node intrinsifies {@link Arrays#copyOf} or {@link Arrays#copyOfRange}. It is lowered in
 * multiple stages:
 * <ul>
 * <li>If {@link LoweringTool#lowerOptimizableMacroNodes()} is {@code true}, or if the source is an
 * object array and the new array type is not known to match the source array type, the node is
 * {@link MacroWithExceptionNode replaced by the original invoke}.</li>
 * <li>Otherwise, this node is
 * {@link CopyOfSnippets.Templates#lower(CopyOfNode, LoweringTool, boolean) lowered} into a range
 * check and an {@link UncheckedCopyOfNode}.</li>
 * <li>The {@link UncheckedCopyOfNode} is then
 * {@link CopyOfSnippets.Templates#lower(CoreProviders, UncheckedCopyOfNode) lowered} into vector
 * nodes via {@code NodeVectorizationPhase}</li>
 * </ul>
 */
@NodeInfo
public abstract class CopyOfNode extends MacroWithExceptionNode implements Simplifiable, MemoryAccess, VirtualizableAllocation, ArrayLengthProvider, DeoptimizingNode.DeoptBefore {
    public static final NodeClass<CopyOfNode> TYPE = NodeClass.create(CopyOfNode.class);

    /**
     * @see Arrays#copyOf(byte[], int)
     */
    static void copyOfPrimitive(GraphBuilderContext b, ResolvedJavaMethod targetMethod, JavaKind elementKind, ValueNode original, ValueNode newLength, boolean needsExplicitException) {
        GraalError.guarantee(elementKind != JavaKind.Object, "%s cannot be used with element kind Object", targetMethod);
        ValueNode from = b.add(ConstantNode.forInt(0));
        ValueNode sourceLength = b.add(ArrayLengthNode.create(original, b.getConstantReflection()));
        CopyOfNode copyOfNode = new CopyOf(MacroParams.of(b, targetMethod, primitiveArrayReturnStamp(b, elementKind), original, newLength), elementKind, sourceLength, from, needsExplicitException);
        b.addPush(JavaKind.Object, copyOfNode);
    }

    /**
     * @see Arrays#copyOfRange(byte[], int, int)
     */
    static void copyOfRangePrimitive(GraphBuilderContext b, ResolvedJavaMethod targetMethod, JavaKind elementKind, ValueNode original, ValueNode from, ValueNode to, boolean needsExplicitException) {
        GraalError.guarantee(elementKind != JavaKind.Object, "Intrinsic for %s cannot be used with element kind Object", targetMethod);
        ValueNode newLength = b.add(SubNode.sub(to, from, NodeView.DEFAULT));
        ValueNode sourceLength = b.add(ArrayLengthNode.create(original, b.getConstantReflection()));
        CopyOfNode copyOfNode = new CopyOfRange(MacroParams.of(b, targetMethod, primitiveArrayReturnStamp(b, elementKind), original, from, to), elementKind, sourceLength, newLength,
                        needsExplicitException);
        b.addPush(JavaKind.Object, copyOfNode);
    }

    /**
     * Computes the result stamp for primitive-array copy plugins from the plugin registration kind.
     */
    private static StampPair primitiveArrayReturnStamp(GraphBuilderContext b, JavaKind elementKind) {
        return StampPair.createSingle(UncheckedCopyOfNode.computeArrayStamp(b.getMetaAccess(), elementKind));
    }

    /**
     * @see Arrays#copyOf(Object[], int, Class)
     */
    static void copyOfObject(GraphBuilderContext b, ResolvedJavaMethod targetMethod, StampPair returnStamp, ValueNode original, ValueNode newLength, ValueNode newArrayType,
                    boolean needsExplicitException) {
        GraalError.guarantee(newArrayType != null, "Intrinsic for %s must have non-null newArrayType", targetMethod);
        ValueNode from = b.add(ConstantNode.forInt(0));
        ValueNode sourceLength = b.add(ArrayLengthNode.create(original, b.getConstantReflection()));
        CopyOfNode copyOfNode = new CopyOf(MacroParams.of(b.getInvokeKind(), b.getMethod(), targetMethod, b.bci(), objectArrayReturnStamp(b, returnStamp), original, newLength, newArrayType),
                        JavaKind.Object, sourceLength, from, needsExplicitException);
        b.addPush(JavaKind.Object, copyOfNode);
    }

    /**
     * @see Arrays#copyOfRange(Object[], int, int, Class)
     */
    static void copyOfRangeObject(GraphBuilderContext b, ResolvedJavaMethod targetMethod, StampPair returnStamp, ValueNode original, ValueNode from, ValueNode to, ValueNode newArrayType,
                    boolean needsExplicitException) {
        GraalError.guarantee(newArrayType != null, "Intrinsic for %s must have non-null newArrayType", targetMethod);
        ValueNode newLength = b.add(SubNode.sub(to, from, NodeView.DEFAULT));
        ValueNode sourceLength = b.add(ArrayLengthNode.create(original, b.getConstantReflection()));
        CopyOfNode copyOfNode = new CopyOfRange(MacroParams.of(b.getInvokeKind(), b.getMethod(), targetMethod, b.bci(), objectArrayReturnStamp(b, returnStamp), original, from, to, newArrayType),
                        JavaKind.Object, sourceLength, newLength, needsExplicitException);
        b.addPush(JavaKind.Object, copyOfNode);
    }

    /**
     * Computes the result stamp for object-array copy plugins. Most graph-builder contexts already
     * provide an array return stamp for these intrinsics, but Ristretto parses runtime-loaded generic
     * bytecode with a weaker {@code Object} stamp. The intrinsic still has the Java-level invariant
     * that normal completion returns an array object, so the generic object-array stamp is the safe
     * lower bound when the invoke stamp is not already array-shaped.
     */
    private static StampPair objectArrayReturnStamp(GraphBuilderContext b, StampPair returnStamp) {
        Stamp trustedStamp = returnStamp.getTrustedStamp();
        if (trustedStamp instanceof ObjectStamp objectStamp && objectStamp.isAlwaysArray()) {
            return returnStamp;
        }
        return StampPair.createSingle(UncheckedCopyOfNode.computeArrayStamp(b.getMetaAccess(), JavaKind.Object));
    }

    // @formatter:off
    @NodeInfo(nameTemplate = "CopyOf",
            cycles = NodeCycles.CYCLES_UNKNOWN,
            cyclesRationale = "unknown copy length",
            size = NodeSize.SIZE_128,
            allowedUsageTypes = {InputType.Memory})
    // @formatter:on
    public static final class CopyOf extends CopyOfNode {
        public static final NodeClass<CopyOf> TYPE = NodeClass.create(CopyOf.class);

        @Input protected ValueNode from;

        protected CopyOf(MacroParams p, JavaKind elementKind, ValueNode sourceLength, ValueNode from, boolean needsExplicitException) {
            super(TYPE, p, elementKind, sourceLength, needsExplicitException);
            this.from = from;
        }

        @Override
        public ValueNode getFrom() {
            return from;
        }

        @Override
        public ValueNode getNewLength() {
            return arguments.get(1);
        }

    }

    // @formatter:off
    @NodeInfo(nameTemplate = "CopyOfRange",
            cycles = NodeCycles.CYCLES_UNKNOWN,
            cyclesRationale = "unknown copy length",
            size = NodeSize.SIZE_128,
            allowedUsageTypes = {InputType.Memory})
    // @formatter:on
    public static final class CopyOfRange extends CopyOfNode {
        public static final NodeClass<CopyOfRange> TYPE = NodeClass.create(CopyOfRange.class);

        @Input private ValueNode newLength;

        protected CopyOfRange(MacroParams p, JavaKind elementKind, ValueNode sourceLength, ValueNode newLength, boolean needsExplicitException) {
            super(TYPE, p, elementKind, sourceLength, needsExplicitException);
            this.newLength = newLength;
        }

        @Override
        public ValueNode getFrom() {
            return arguments.get(1);
        }

        @Override
        public ValueNode getNewLength() {
            return newLength;
        }

    }

    @Input protected ValueNode sourceLength;
    @OptionalInput(InputType.State) protected FrameState stateBefore;
    @OptionalInput(InputType.Memory) MemoryKill lastLocationAccess;

    private final JavaKind elementKind;
    private boolean isSameClass = false;
    private final boolean needsExplicitException;

    protected CopyOfNode(NodeClass<? extends CopyOfNode> c, MacroParams p, JavaKind elementKind, ValueNode sourceLength, boolean needsExplicitException) {
        super(c, p);
        this.elementKind = elementKind;
        this.sourceLength = sourceLength;
        this.needsExplicitException = needsExplicitException;
        GraalError.guarantee(stamp instanceof ObjectStamp objectStamp && objectStamp.isAlwaysArray(),
                        "CopyOf plugin must create an array ObjectStamp, got %s", stamp);
    }

    boolean isObjectArray() {
        return elementKind == JavaKind.Object;
    }

    public ValueNode getSource() {
        return arguments.get(0);
    }

    public ValueNode getSourceLength() {
        return sourceLength;
    }

    public abstract ValueNode getFrom();

    public abstract ValueNode getNewLength();

    public ValueNode getNewObjectArrayType() {
        if (isObjectArray()) {
            return arguments.last();
        }
        return null;
    }

    boolean needsExplicitException() {
        return needsExplicitException;
    }

    @Override
    public final boolean inferStamp() {
        // Because of complexities with the lowering of CopyOf it's unsafe to inject an improved
        // stamp before it's lowered.
        return false;
    }

    public JavaKind getElementKind() {
        return elementKind;
    }

    public void addSnippetArguments(Arguments args) {
        args.add("elementKind", getElementKind());
        args.add("needsExplicitException", needsExplicitException());
        args.add("source", getSource());
        args.add("sourceLength", getSourceLength());
        args.add("from", getFrom());
        args.add("newLength", getNewLength());
        args.add("newArrayType", getNewObjectArrayType());
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(elementKind);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ResolvedJavaType newArrayType;
        if (isObjectArray()) {
            if (!getNewObjectArrayType().isConstant()) {
                /*
                 * If it is an object array copy but the new array element type is not a constant
                 * then it cannot be virtualized.
                 */
                return;
            }
            /* Since the new object element type is a constant it can be virtualized. */
            newArrayType = tool.getConstantReflection().asJavaType(getNewObjectArrayType().asConstant());
        } else {
            /* For a primitive array copy the component type is derived from the element kind. */
            newArrayType = tool.getMetaAccess().lookupJavaType(elementKind.toJavaClass()).getArrayClass();
        }
        GraphUtil.virtualizeArrayCopy(tool, getSource(), getSourceLength(), getNewLength(), getFrom(), newArrayType.getComponentType(), elementKind, graph(), VirtualArrayNode::new);
    }

    @Override
    public ValueNode findLength(FindLengthMode mode, ConstantReflectionProvider constantReflection) {
        return getNewLength();
    }

    private static boolean verifyNoWordArray(ResolvedJavaType type, WordTypes wordTypes) {
        if (type != null && type.isArray()) {
            // FIXME cannot deal with Word array yet GR-32808
            GraalError.guarantee(!wordTypes.isWord(type.getElementalType()), "Unexpected Word type %s", type);
        }
        // no type information - cannot really do anything
        return true;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (tool.lowerOptimizableMacroNodes()) {
            super.lower(tool);
            return;
        }
        if (!isObjectArray()) {
            // We always lower copyOf on primitive arrays.
            replaceWithUncheckedCopyOfNode(tool);
            return;
        }
        verifyNoWordArray(StampTool.typeOrNull(getSource(), tool.getMetaAccess()), tool.getWordTypes());
        /*
         * Since CopyOfNode can be vectorized during the low-tier, we only postpone the reversal to
         * an Invoke if array types do not require type checks.
         */
        isSameClass = isSameClass || checkSameClass(getSource(), getNewObjectArrayType());
        ResolvedJavaType sourceArrayType = StampTool.typeOrNull(getSource());
        if (sourceArrayType != null && getNewObjectArrayType().asConstant() != null) {
            ResolvedJavaType sourceType = sourceArrayType.getComponentType();
            ResolvedJavaType newArrayType = tool.getConstantReflection().asJavaType(getNewObjectArrayType().asConstant());
            assert newArrayType.getComponentType() != null : "Null component type: " + newArrayType;
            if (newArrayType.getComponentType().isAssignableFrom(sourceType)) {
                replaceWithUncheckedCopyOfNode(tool);
                return;
            }
        }
        if (isSameClass) {
            replaceWithUncheckedCopyOfNode(tool);
            return;
        }

        // emit a dynamic type check
        replaceWithUncheckedCopyOfWithDynamicTypeCheck(tool);
    }

    private void replaceWithUncheckedCopyOfNode(LoweringTool tool) {
        replaceWithUncheckedCopyOfNode(tool, false);
    }

    private void replaceWithUncheckedCopyOfWithDynamicTypeCheck(LoweringTool tool) {
        replaceWithUncheckedCopyOfNode(tool, true);
    }

    private void replaceWithUncheckedCopyOfNode(LoweringTool tool, boolean needsDynamicTypeCheck) {
        CopyOfSnippets.Templates templates = tool.getReplacements().getSnippetTemplateCache(CopyOfSnippets.Templates.class);
        templates.lower(this, tool, needsDynamicTypeCheck);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (!needsExplicitException() && !(exceptionEdge instanceof UnreachableBeginNode)) {
            replaceWithNonThrowing();
        }
    }

    private static boolean checkSameClass(ValueNode source, ValueNode newClass) {
        // This check is performed during lowering to make ensure that the new class
        // corresponds to the source array class.
        if (newClass instanceof GetClassNode getClassNode) {
            return GraphUtil.unproxify(source) == GraphUtil.unproxify(getClassNode.getObject());
        }
        if (newClass instanceof LoadHubNode loadHub) {
            // This is the same as the last check, but for SVM.
            return GraphUtil.unproxify(source) == GraphUtil.unproxify(loadHub.getValue());
        }
        // After lowering, GetClassNode can be replaced by the following pattern.
        if (newClass instanceof HubGetClassNodeInterface hubGetClass) {
            if (hubGetClass.getHub() instanceof LoadHubNode hubNode) {
                return GraphUtil.unproxify(source) == GraphUtil.unproxify(hubNode.getValue());
            }
            return false;
        }
        // In the low-tier, we could still check the raw memory reads and offsets,
        // but that would make this code platform-specific.
        return false;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public void setStateBefore(FrameState f) {
        updateUsages(stateBefore, f);
        stateBefore = f;
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }
}
