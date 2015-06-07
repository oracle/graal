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

import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static com.oracle.jvmci.meta.DeoptimizationAction.*;
import static com.oracle.jvmci.meta.DeoptimizationReason.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.jvmci.meta.*;
import com.oracle.jvmci.meta.Assumptions.AssumptionResult;

/**
 * Implements a type check against a compile-time known type.
 */
@NodeInfo
public class CheckCastNode extends FixedWithNextNode implements Canonicalizable, Simplifiable, Lowerable, Virtualizable, ValueProxy {

    public static final NodeClass<CheckCastNode> TYPE = NodeClass.create(CheckCastNode.class);
    @Input protected ValueNode object;
    protected final ResolvedJavaType type;
    protected final JavaTypeProfile profile;

    /**
     * Determines the exception thrown by this node if the check fails: {@link ClassCastException}
     * if false; {@link ArrayStoreException} if true.
     */
    protected final boolean forStoreCheck;

    public CheckCastNode(ResolvedJavaType type, ValueNode object, JavaTypeProfile profile, boolean forStoreCheck) {
        this(TYPE, type, object, profile, forStoreCheck);
    }

    protected CheckCastNode(NodeClass<? extends CheckCastNode> c, ResolvedJavaType type, ValueNode object, JavaTypeProfile profile, boolean forStoreCheck) {
        super(c, StampFactory.declaredTrusted(type).improveWith(object.stamp()));
        assert object.stamp() instanceof ObjectStamp : object;
        assert type != null;
        this.type = type;
        this.object = object;
        this.profile = profile;
        this.forStoreCheck = forStoreCheck;
    }

    public static ValueNode create(ResolvedJavaType inputType, ValueNode object, JavaTypeProfile profile, boolean forStoreCheck, Assumptions assumptions) {
        ResolvedJavaType type = inputType;
        ValueNode synonym = findSynonym(type, object);
        if (synonym != null) {
            return synonym;
        }
        assert object.stamp() instanceof ObjectStamp : object;
        if (assumptions != null) {
            AssumptionResult<ResolvedJavaType> leafConcreteSubtype = type.findLeafConcreteSubtype();
            if (leafConcreteSubtype != null && !leafConcreteSubtype.getResult().equals(type)) {
                assumptions.record(leafConcreteSubtype);
                type = leafConcreteSubtype.getResult();
            }
        }
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
        Stamp newStamp = StampFactory.declaredTrusted(type).improveWith(object().stamp());
        LogicNode condition;
        LogicNode innerNode = null;
        ValueNode theValue = object;
        if (newStamp.isEmpty()) {
            // This is a check cast that will always fail
            condition = LogicConstantNode.contradiction(graph());
            newStamp = StampFactory.declaredTrusted(type);
        } else if (StampTool.isPointerNonNull(object)) {
            condition = graph().addWithoutUnique(InstanceOfNode.create(type, object, profile));
            innerNode = condition;
        } else {
            if (profile != null && profile.getNullSeen() == TriState.FALSE) {
                FixedGuardNode nullCheck = graph().add(new FixedGuardNode(graph().unique(new IsNullNode(object)), UnreachedCode, InvalidateReprofile, true));
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
        GuardingNode guard = tool.createGuard(next(), condition, forStoreCheck ? ArrayStoreException : ClassCastException, InvalidateReprofile, false);
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
            ObjectStamp castStamp = (ObjectStamp) StampFactory.declaredTrusted(type);
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

        Assumptions assumptions = graph().getAssumptions();
        if (assumptions != null) {
            AssumptionResult<ResolvedJavaType> leafConcreteSubtype = type.findLeafConcreteSubtype();
            if (leafConcreteSubtype != null && !leafConcreteSubtype.getResult().equals(type)) {
                // Propagate more precise type information to usages of the checkcast.
                assumptions.record(leafConcreteSubtype);
                return new CheckCastNode(leafConcreteSubtype.getResult(), object, profile, forStoreCheck);
            }
        }

        return this;
    }

    protected static ValueNode findSynonym(ResolvedJavaType type, ValueNode object) {
        ResolvedJavaType objectType = StampTool.typeOrNull(object);
        if (objectType != null && type.isAssignableFrom(objectType)) {
            // we don't have to check for null types here because they will also pass the
            // checkcast.
            return object;
        }

        if (StampTool.isPointerAlwaysNull(object)) {
            return object;
        }
        return null;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        // if the previous node is also a checkcast, with a less precise and compatible type,
        // replace both with one checkcast checking the more specific type.
        if (predecessor() instanceof CheckCastNode) {
            CheckCastNode ccn = (CheckCastNode) predecessor();
            if (ccn != null && ccn.type != null && ccn == object && ccn.forStoreCheck == forStoreCheck && ccn.type.isAssignableFrom(type)) {
                StructuredGraph graph = ccn.graph();
                CheckCastNode newccn = graph.add(new CheckCastNode(type, ccn.object, ccn.profile, ccn.forStoreCheck));
                graph.replaceFixedWithFixed(ccn, newccn);
                replaceAtUsages(newccn);
                graph.removeFixed(this);
            }
        }
    }

    public ValueNode object() {
        return object;
    }

    /**
     * Gets the type being cast to.
     */
    public ResolvedJavaType type() {
        return type;
    }

    public JavaTypeProfile profile() {
        return profile;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual) {
            if (type.isAssignableFrom(state.getVirtualObject().type())) {
                tool.replaceWithVirtual(state.getVirtualObject());
            }
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
            if (objectType != null && type.isAssignableFrom(objectType)) {
                return TriState.TRUE;
            } else if (objectStamp.alwaysNull()) {
                return TriState.TRUE;
            }
        }
        return TriState.UNKNOWN;
    }
}
