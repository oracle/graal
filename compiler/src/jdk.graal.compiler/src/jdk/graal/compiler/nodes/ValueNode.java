/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import java.util.Iterator;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.spi.NodeValueMap;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * This class represents a value within the graph, including local variables, phis, and all other
 * instructions.
 */
@NodeInfo
public abstract class ValueNode extends Node implements ValueNodeInterface {

    public static final NodeClass<ValueNode> TYPE = NodeClass.create(ValueNode.class);

    public static final ValueNode[] EMPTY_ARRAY = {};
    /**
     * The kind of this value. This is {@link JavaKind#Void} for instructions that produce no value.
     * This kind is guaranteed to be a {@linkplain JavaKind#getStackKind() stack kind}.
     */
    protected Stamp stamp;

    public ValueNode(NodeClass<? extends ValueNode> c, Stamp stamp) {
        super(c);
        this.stamp = stamp;
    }

    public final Stamp stamp(NodeView view) {
        return view.stamp(this);
    }

    public final void setStamp(Stamp stamp) {
        this.stamp = stamp;
        assert !isAlive() || !inferStamp() : "setStamp called on a node that overrides inferStamp: " + this;
    }

    @Override
    public final StructuredGraph graph() {
        return (StructuredGraph) super.graph();
    }

    /**
     * Checks if the given stamp is different than the current one (
     * {@code newStamp.equals(oldStamp) == false}). If it is different then the new stamp will
     * become the current stamp for this node.
     *
     * @return true if the stamp has changed, false otherwise.
     */
    protected final boolean updateStamp(Stamp newStamp) {
        if (newStamp == null || newStamp.equals(stamp)) {
            return false;
        } else {
            stamp = newStamp;
            return true;
        }
    }

    /**
     * This method can be overridden by subclasses of {@link ValueNode} if they need to recompute
     * their stamp if their inputs change. A typical implementation will compute the stamp and pass
     * it to {@link #updateStamp(Stamp)}, whose return value can be used as the result of this
     * method.
     *
     * @return true if the stamp has changed, false otherwise.
     */
    public boolean inferStamp() {
        return false;
    }

    public final JavaKind getStackKind() {
        return stamp(NodeView.DEFAULT).getStackKind();
    }

    /**
     * Checks whether this value is a constant (i.e. it is of type {@link ConstantNode}.
     *
     * @return {@code true} if this value is a constant
     */
    public final boolean isConstant() {
        return this instanceof ConstantNode;
    }

    private static final NodePredicate IS_CONSTANT = new NodePredicate() {
        @Override
        public boolean apply(Node n) {
            return n instanceof ConstantNode;
        }
    };

    public static NodePredicate isConstantPredicate() {
        return IS_CONSTANT;
    }

    /**
     * Checks whether this value represents the null constant.
     *
     * @return {@code true} if this value represents the null constant
     */
    public final boolean isNullConstant() {
        JavaConstant value = asJavaConstant();
        return value != null && value.isNull();
    }

    public final boolean isDefaultConstant() {
        Constant value = asConstant();
        return value != null && value.isDefaultForKind();
    }

    /**
     * Convert this value to a constant if it is a constant, otherwise return null.
     *
     * @return the {@link JavaConstant} represented by this value if it is a constant; {@code null}
     *         otherwise
     */
    public final Constant asConstant() {
        if (this instanceof ConstantNode) {
            return ((ConstantNode) this).getValue();
        } else {
            return null;
        }
    }

    public boolean isIllegalConstant() {
        return isConstant() && asConstant().equals(JavaConstant.forIllegal());
    }

    public final boolean isJavaConstant() {
        return isConstant() && asConstant() instanceof JavaConstant;
    }

    public final JavaConstant asJavaConstant() {
        Constant value = asConstant();
        if (value instanceof JavaConstant) {
            return (JavaConstant) value;
        } else {
            return null;
        }
    }

    /* This method is final to ensure that it can be de-virtualized and inlined. */
    @Override
    public final ValueNode asNode() {
        return this;
    }

    protected void updateUsagesInterface(ValueNodeInterface oldInput, ValueNodeInterface newInput) {
        updateUsages(oldInput == null ? null : oldInput.asNode(), newInput == null ? null : newInput.asNode());
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        if (getStackKind() != JavaKind.Void && type == InputType.Value) {
            return true;
        } else {
            return super.isAllowedUsageType(type);
        }
    }

    /**
     * Checks if this node has usages other than the given node {@code node}.
     *
     * @param node node which is ignored when searching for usages
     * @param nodeValueMap
     * @return true if this node has other usages, false otherwise
     */
    public boolean hasUsagesOtherThan(ValueNode node, NodeValueMap nodeValueMap) {
        for (Node usage : usages()) {
            if (usage != node && usage instanceof ValueNode && nodeValueMap.hasOperand(usage)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean checkReplaceAtUsagesInvariants(Node other) {
        assert other == null || other instanceof ValueNode : Assertions.errorMessage(this, other);
        if (this.hasUsages() && !this.stamp(NodeView.DEFAULT).isEmpty() && !(other instanceof PhiNode) && other != null) {
            Stamp thisStamp = stamp(NodeView.DEFAULT);
            Stamp otherStamp = ((ValueNode) other).stamp(NodeView.DEFAULT);
            assert thisStamp.isCompatible(otherStamp) : "stamp have to be compatible";
            boolean morePrecise = otherStamp.join(thisStamp).equals(otherStamp);
            assert morePrecise : "stamp can only get more precise " + toString(Verbosity.All) + " " +
                            other.toString(Verbosity.All);
        }
        return true;
    }

    /**
     * Determines whether this node is recursively equal to the other node while ignoring
     * differences in {@linkplain jdk.graal.compiler.graph.Node.Successor control-flow} edges and
     * inputs of the given {@code ignoredInputType}. Recursive equality is only applied to
     * {@link FloatingNode}s reachable via inputs; {@link FixedNode}s are only considered equal if
     * they are the same node.
     *
     * WARNING: This method is useful only in very particular contexts, and only for limited
     * purposes. For example, it might be used to check if two values are equal up to memory or
     * guard inputs. Some external knowledge about the memory graph, data flow, control flow, etc.,
     * is required to interpret the meaning of this "equality". It is <b>not</b> legal in general to
     * replace one of the "equal" nodes with the other.
     */
    public boolean recursivelyDataFlowEqualsUpTo(FloatingNode that, InputType ignoredInputType) {
        if (this == that) {
            return true;
        }
        if (that == null || !recursiveDataFlowEqualsHelper(this, that)) {
            return false;
        }

        NodeBitMap visited = new NodeBitMap(graph());
        /*
         * "These" nodes are the ones recursively reachable from this, "those" are the ones
         * recursively reachable from that.
         *
         * Invariants: These two stacks always have the same size. Their elements at corresponding
         * positions are never null, have equal node classes, are value equal but not reference
         * equal, and come from matching input positions of nodes further down the stack.
         */
        NodeStack these = new NodeStack();
        NodeStack those = new NodeStack();
        these.push(this);
        those.push(that);
        while (!these.isEmpty()) {
            assert !those.isEmpty();
            Node thisNode = these.pop();
            Node thatNode = those.pop();
            if (visited.isMarked(thisNode)) {
                continue;
            }
            visited.mark(thisNode);
            Iterator<Position> theseInputs = thisNode.inputPositions().iterator();
            Iterator<Position> thoseInputs = thatNode.inputPositions().iterator();
            while (theseInputs.hasNext() && thoseInputs.hasNext()) {
                Position thisPos = theseInputs.next();
                Position thatPos = thoseInputs.next();
                if (thisPos.getIndex() != thatPos.getIndex() || thisPos.getSubIndex() != thatPos.getSubIndex()) {
                    return false;
                }
                assert thisPos.getInputType() == thatPos.getInputType() : thisPos.getInputType() + "!=" + thatPos.getInputType() +
                                Assertions.errorMessageContext(" thisNode", thisNode, "thatNode", thatNode);
                if (thisPos.getInputType() == ignoredInputType) {
                    continue;
                }
                Node thisInput = thisPos.get(thisNode);
                Node thatInput = thatPos.get(thatNode);
                if (thisInput == thatInput) {
                    continue;
                }
                if (thisInput == null || thatInput == null || !recursiveDataFlowEqualsHelper(thisInput, thatInput)) {
                    return false;
                }
                these.push(thisInput);
                those.push(thatInput);
            }
            if (theseInputs.hasNext() || thoseInputs.hasNext()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Determines if the two nodes can be considered "value equals" in the context of
     * {@link #recursivelyDataFlowEqualsUpTo(FloatingNode, InputType)}. Specifically, checks if the
     * two nodes are of the same floating, non-identity class, have equal data fields, and are not
     * memory accesses that we could only consider equal if they were GVN'ed. This method only looks
     * at node classes and data fields, all inputs must be checked separately.
     *
     * @return {@code true} if the two nodes can be considered equal, {@code false} otherwise
     */
    private static boolean recursiveDataFlowEqualsHelper(Node thisNode, Node thatNode) {
        GraalError.guarantee(thisNode != thatNode, "identity should be checked by the caller");
        if (thisNode == null || thatNode == null || thisNode.getNodeClass() != thatNode.getNodeClass()) {
            return false;
        }
        if (!(thisNode instanceof FloatingNode) || thisNode instanceof NodeWithIdentity) {
            return false;
        }
        if (!thisNode.valueEquals(thatNode)) {
            return false;
        }
        if (thisNode instanceof MemoryAccess access) {
            if (access.getLocationIdentity().isAny() || access.getLocationIdentity().isMutable()) {
                return false;
            }
        }
        return true;
    }
}
