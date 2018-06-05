/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.nodes;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.HashSet;
import java.util.Set;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * Run-time check that the actual {@link #getInput() input} to this node conforms to the expected
 * type state.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public class AssertTypeStateNode extends AssertValueNode implements Virtualizable {
    public static final NodeClass<AssertTypeStateNode> TYPE = NodeClass.create(AssertTypeStateNode.class);

    protected final JavaTypeProfile typeState;

    public static void create(ValueNode input, JavaTypeProfile typeState) {
        insert(input, input.graph().add(new AssertTypeStateNode(input.stamp(NodeView.DEFAULT), typeState)));
    }

    protected AssertTypeStateNode(Stamp stamp, JavaTypeProfile typeState) {
        super(TYPE, stamp);
        this.typeState = typeState;
    }

    public JavaTypeProfile getTypeState() {
        return typeState;
    }

    @Override
    protected boolean alwaysHolds(boolean reportError) {
        if (getInput().isConstant()) {
            if (getInput().asJavaConstant().isNull()) {
                if (typeState.getNullSeen() == TriState.FALSE) {
                    if (reportError) {
                        throw shouldNotReachHere("Null constant not compatible with type state: " + this + " : " + getTypeState());
                    }
                } else {
                    return true;
                }
            } else {
                Set<ResolvedJavaType> ourTypes = new HashSet<>();
                addAllTypes(ourTypes, typeState);
                if (!ourTypes.contains(StampTool.typeOrNull(getInput()))) {
                    if (reportError) {
                        throw shouldNotReachHere("Constant object not compatible with type state: " + this + " : " + getTypeState() + ", " + StampTool.typeOrNull(getInput()));
                    }
                } else {
                    return true;
                }

            }
        }
        if (getInput() instanceof AssertTypeStateNode) {
            JavaTypeProfile inputTypeState = ((AssertTypeStateNode) getInput()).getTypeState();
            Set<ResolvedJavaType> inputTypes = new HashSet<>();
            addAllTypes(inputTypes, inputTypeState);

            Set<ResolvedJavaType> ourTypes = new HashSet<>();
            addAllTypes(ourTypes, typeState);

            if (ourTypes.containsAll(inputTypes)) {
                /* Another node is already checking the same or a stronger type state. */
                return true;
            }
        }
        return false;
    }

    private static void addAllTypes(Set<ResolvedJavaType> set, JavaTypeProfile types) {
        for (ProfiledType type : types.getTypes()) {
            set.add(type.getType());
        }
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getInput());
        if (alias instanceof VirtualObjectNode) {
            Set<ResolvedJavaType> ourTypes = new HashSet<>();
            addAllTypes(ourTypes, typeState);
            if (!ourTypes.contains(StampTool.typeOrNull(alias))) {
                throw shouldNotReachHere("Virtual object not compatible with type state: " + this + " : " + getTypeState() + ", " + alias.stamp(NodeView.DEFAULT));
            }
            tool.replaceWith(alias);
        }
    }
}
