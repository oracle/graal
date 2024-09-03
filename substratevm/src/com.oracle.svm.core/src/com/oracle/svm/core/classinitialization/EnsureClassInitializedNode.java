/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.classinitialization;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(size = NodeSize.SIZE_16, cycles = NodeCycles.CYCLES_2, cyclesRationale = "Class initialization only runs at most once at run time, so the amortized cost is only the is-initialized check")
@NodeIntrinsicFactory
public class EnsureClassInitializedNode extends WithExceptionNode implements Canonicalizable, StateSplit, SingleMemoryKill, Lowerable {

    public static final NodeClass<EnsureClassInitializedNode> TYPE = NodeClass.create(EnsureClassInitializedNode.class);

    @Input private ValueNode hub;
    @Input(InputType.State) private FrameState stateAfter;

    public static boolean intrinsify(GraphBuilderContext b, ValueNode hub) {
        b.add(new EnsureClassInitializedNode(b.nullCheckedValue(hub)));
        return true;
    }

    public EnsureClassInitializedNode(ValueNode hub, FrameState stateAfter) {
        super(TYPE, StampFactory.forVoid());
        this.hub = hub;
        assert StampTool.isPointerNonNull(hub) : "Hub must already be null-checked";
        this.stateAfter = stateAfter;
    }

    public EnsureClassInitializedNode(ValueNode hub) {
        this(hub, null);
    }

    public ValueNode getHub() {
        return hub;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState stateAfter) {
        updateUsages(this.stateAfter, stateAfter);
        this.stateAfter = stateAfter;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public ResolvedJavaType constantTypeOrNull(ConstantReflectionProvider constantReflection) {
        if (hub.isConstant()) {
            return constantReflection.asJavaType(hub.asConstant());
        } else {
            return null;
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ResolvedJavaType type = constantTypeOrNull(tool.getConstantReflection());
        if (type != null) {
            TypeReachedProvider typeReachedProvider = (TypeReachedProvider) tool.getConstantReflection();
            if (!typeReachedProvider.initializationCheckRequired(type)) {
                for (FrameState cur = stateAfter; cur != null; cur = cur.outerFrameState()) {
                    if (!needsRuntimeInitialization(cur.getMethod().getDeclaringClass(), type)) {
                        return null;
                    }
                }
            }
        }
        return this;
    }

    /**
     * Return true if the type needs to be initialized at run time, i.e., it has not been already
     * initialized during image generation or initialization is implied by the declaringClass.
     */
    public static boolean needsRuntimeInitialization(ResolvedJavaType declaringClass, ResolvedJavaType type) {
        if (type.isInitialized() || type.isArray() || type.equals(declaringClass)) {
            /*
             * The simple cases: the type is already initialized, or the type is an exact match with
             * the declaring class.
             */
            return false;

        } else if (declaringClass.isInterface()) {
            /*
             * Initialization of an interface does not trigger initialization of superinterfaces.
             * Regardless whether any of the involved interfaces declares default methods.
             */
            return true;

        } else if (type.isAssignableFrom(declaringClass)) {
            if (type.isInterface()) {
                /*
                 * Initialization of a class only triggers initialization of an implemented
                 * interface if that interface declares default methods.
                 */
                return !type.declaresDefaultMethods();
            } else {
                /* Initialization of a class triggers initialization of all superclasses. */
                return false;
            }

        } else {
            /* No relationship between the two types. */
            return true;
        }
    }

    @NodeIntrinsic
    public static native void ensureClassInitialized(Class<?> clazz);
}
