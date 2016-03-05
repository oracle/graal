/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.java;

import static com.oracle.graal.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.ArrayStoreException;
import static jdk.vm.ci.meta.DeoptimizationReason.ClassCastException;
import static jdk.vm.ci.meta.DeoptimizationReason.UnreachedCode;

import com.oracle.graal.compiler.common.type.CheckedJavaType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Graph;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.extended.ValueAnchorNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.ValueProxy;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.type.StampTool;

/**
 * Implements a type check against a compile-time known type.
 */
@NodeInfo
public class CheckCastNode extends FixedWithNextNode implements Canonicalizable, Lowerable, Virtualizable, ValueProxy {

    public static final NodeClass<CheckCastNode> TYPE = NodeClass.create(CheckCastNode.class);
    @Input protected ValueNode object;
    protected final CheckedJavaType type;
    protected final JavaTypeProfile profile;

    /**
     * Determines the exception thrown by this node if the check fails: {@link ClassCastException}
     * if false; {@link ArrayStoreException} if true.
     */
    protected final boolean forStoreCheck;

    public CheckCastNode(CheckedJavaType type, ValueNode object, JavaTypeProfile profile, boolean forStoreCheck) {
        this(TYPE, type, object, profile, forStoreCheck);
    }

    protected CheckCastNode(NodeClass<? extends CheckCastNode> c, CheckedJavaType type, ValueNode object, JavaTypeProfile profile, boolean forStoreCheck) {
        super(c, StampFactory.declaredTrusted(type.getType()).improveWith(object.stamp()));
        assert object.stamp() instanceof ObjectStamp : object;
        assert type != null;
        this.type = type;
        this.object = object;
        this.profile = profile;
        this.forStoreCheck = forStoreCheck;
    }

    public static ValueNode create(CheckedJavaType type, ValueNode object, JavaTypeProfile profile, boolean forStoreCheck) {
        ValueNode synonym = findSynonym(type, object);
        if (synonym != null) {
            return synonym;
        }
        assert object.stamp() instanceof ObjectStamp : object;
        return new CheckCastNode(type, object, profile, forStoreCheck);
    }

    public boolean isForStoreCheck() {
        return forStoreCheck;
    }

    /**
     * Lowers a {@link CheckCastNode}. That is:
     *
     * <pre>
     * 1: A a = ...
     * 2: B b = (B) a;
     * </pre>
     *
     * is lowered to:
     *
     * <pre>
     * 1: A a = ...
     * 2: B b = guardingPi(a == null || a instanceof B, a, stamp(B))
     * </pre>
     *
     * or if a is known to be non-null:
     *
     * <pre>
     * 1: A a = ...
     * 2: B b = guardingPi(a instanceof B, a, stamp(B, non-null))
     * </pre>
     *
     * Note: we use {@link Graph#addWithoutUnique} as opposed to {@link Graph#unique} for the new
     * {@link InstanceOfNode} to maintain the invariant checked by
     * {@code LoweringPhase.checkUsagesAreScheduled()}.
     */
    @Override
    public void lower(LoweringTool tool) {
        Stamp newStamp = StampFactory.declaredTrusted(type.getType()).improveWith(object().stamp());
        LogicNode condition;
        LogicNode innerNode = null;
        ValueNode theValue = object;
        if (newStamp.isEmpty()) {
            // This is a check cast that will always fail
            condition = LogicConstantNode.contradiction(graph());
            newStamp = StampFactory.declaredTrusted(type.getType());
        } else if (StampTool.isPointerNonNull(object)) {
            condition = graph().addWithoutUnique(InstanceOfNode.create(type, object, profile));
            innerNode = condition;
        } else {
            if (profile != null && profile.getNullSeen() == TriState.FALSE) {
                FixedGuardNode nullCheck = graph().add(new FixedGuardNode(graph().unique(new IsNullNode(object)), UnreachedCode, InvalidateReprofile, JavaConstant.NULL_POINTER, true));
                PiNode nullGuarded = graph().unique(new PiNode(object, object().stamp().join(StampFactory.objectNonNull()), nullCheck));
                LogicNode typeTest = graph().addWithoutUnique(InstanceOfNode.create(type, nullGuarded, profile));
                innerNode = typeTest;
                graph().addBeforeFixed(this, nullCheck);
                condition = typeTest;
                /*
                 * The PiNode is injecting an extra guard into the type so make sure it's used in
                 * the GuardingPi, otherwise we can lose the null guard if the InstanceOf is
                 * optimized away.
                 */
                theValue = nullGuarded;
                newStamp = newStamp.join(StampFactory.objectNonNull());
                nullCheck.lower(tool);
            } else {
                // TODO (ds) replace with probability of null-seen when available
                double shortCircuitProbability = NOT_FREQUENT_PROBABILITY;
                LogicNode typeTest = graph().addOrUnique(InstanceOfNode.create(type, object, profile));
                innerNode = typeTest;
                condition = LogicNode.or(graph().unique(new IsNullNode(object)), typeTest, shortCircuitProbability);
            }
        }
        GuardingNode guard = tool.createGuard(next(), condition, forStoreCheck ? ArrayStoreException : ClassCastException, InvalidateReprofile);
        ValueAnchorNode valueAnchor = graph().add(new ValueAnchorNode((ValueNode) guard));
        PiNode piNode = graph().unique(new PiNode(theValue, newStamp, (ValueNode) guard));
        this.replaceAtUsages(piNode);
        graph().replaceFixedWithFixed(this, valueAnchor);

        if (innerNode instanceof Lowerable) {
            tool.getLowerer().lower(innerNode, tool);
        }
    }

    @Override
    public boolean inferStamp() {
        if (object().stamp() instanceof ObjectStamp) {
            ObjectStamp castStamp = (ObjectStamp) StampFactory.declaredTrusted(type.getType());
            return updateStamp(castStamp.improveWith(object().stamp()));
        }
        return false;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ValueNode synonym = findSynonym(type, object());
        if (synonym != null) {
            return synonym;
        }
        return this;
    }

    public static ValueNode findSynonym(CheckedJavaType type, ValueNode object) {
        ResolvedJavaType objectType = StampTool.typeOrNull(object);
        if (objectType != null && type.getType().isAssignableFrom(objectType)) {
            // we don't have to check for null types here because they will also pass the
            // checkcast.
            return object;
        }

        if (StampTool.isPointerAlwaysNull(object)) {
            return object;
        }
        return null;
    }

    public ValueNode object() {
        return object;
    }

    /**
     * Gets the type being cast to.
     */
    public CheckedJavaType type() {
        return type;
    }

    public JavaTypeProfile profile() {
        return profile;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object);
        if (tryFold(alias.stamp()) == TriState.TRUE) {
            tool.replaceWith(alias);
        }
    }

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }

    public TriState tryFold(Stamp testStamp) {
        if (testStamp instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) testStamp;
            ResolvedJavaType objectType = objectStamp.type();
            if (objectType != null && type.getType().isAssignableFrom(objectType)) {
                return TriState.TRUE;
            } else if (objectStamp.alwaysNull()) {
                return TriState.TRUE;
            }
        }
        return TriState.UNKNOWN;
    }
}
