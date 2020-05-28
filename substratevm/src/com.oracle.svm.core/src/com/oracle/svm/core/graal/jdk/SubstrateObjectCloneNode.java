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
package com.oracle.svm.core.graal.jdk;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.DeoptimizingNode.DeoptBefore;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.nodes.BasicObjectCloneNode;
import org.graalvm.compiler.replacements.nodes.MacroNode;

import com.oracle.svm.core.meta.SharedType;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

@NodeInfo(allowedUsageTypes = {InputType.Memory})
public final class SubstrateObjectCloneNode extends BasicObjectCloneNode implements DeoptBefore {
    public static final NodeClass<SubstrateObjectCloneNode> TYPE = NodeClass.create(SubstrateObjectCloneNode.class);

    @OptionalInput(InputType.State) protected FrameState stateBefore;

    public SubstrateObjectCloneNode(MacroParams p) {
        super(TYPE, p);
    }

    @Override
    public LoadFieldNode genLoadFieldNode(Assumptions assumptions, ValueNode originalAlias, ResolvedJavaField field) {
        if (field.getJavaKind() == JavaKind.Object && field.getType() instanceof SharedType) {
            /*
             * We have the static analysis to check interface types, e.g.., if a parameter of field
             * has a declared interface type and is assigned something that does not implement the
             * interface, the static analysis reports an error.
             */
            TypeReference trusted = TypeReference.createTrustedWithoutAssumptions((SharedType) field.getType());
            StampPair pair = StampPair.createSingle(StampFactory.object(trusted, false));
            return LoadFieldNode.createOverrideStamp(pair, originalAlias, field);
        } else {
            return super.genLoadFieldNode(assumptions, originalAlias, field);
        }
    }

    /**
     * Even though this implementation is the same as {@link Lowerable#lower}, it is required
     * because we would actually inherit {@link MacroNode#lower} which we do not want.
     */
    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    @Override
    public void setStateBefore(FrameState f) {
        updateUsages(stateBefore, f);
        stateBefore = f;
    }
}
