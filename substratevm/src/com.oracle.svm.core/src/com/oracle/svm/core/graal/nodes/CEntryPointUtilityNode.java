/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.DeoptimizingNode.DeoptBefore;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.c.function.CEntryPointActions;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_2, size = SIZE_2, allowedUsageTypes = {InputType.Memory})
public final class CEntryPointUtilityNode extends DeoptimizingFixedWithNextNode implements Lowerable, SingleMemoryKill, DeoptBefore {

    public static final NodeClass<CEntryPointUtilityNode> TYPE = NodeClass.create(CEntryPointUtilityNode.class);

    /**
     * @see CurrentIsolate
     * @see CEntryPointActions
     */
    public enum UtilityAction {
        IsAttached(JavaKind.Boolean),
        FailFatally(JavaKind.Void);

        final JavaKind resultKind;

        UtilityAction(JavaKind resultKind) {
            this.resultKind = resultKind;
        }
    }

    protected final UtilityAction utilityAction;

    @OptionalInput protected ValueNode parameter0;
    @OptionalInput protected ValueNode parameter1;

    public CEntryPointUtilityNode(UtilityAction utilityAction, ValueNode parameter) {
        this(utilityAction, parameter, null);
    }

    public CEntryPointUtilityNode(UtilityAction utilityAction, ValueNode parameter0, ValueNode parameter1) {
        super(TYPE, StampFactory.forKind(utilityAction.resultKind));
        this.utilityAction = utilityAction;
        this.parameter0 = parameter0;
        this.parameter1 = parameter1;
    }

    public UtilityAction getUtilityAction() {
        return utilityAction;
    }

    public ValueNode getParameter0() {
        return parameter0;
    }

    public ValueNode getParameter1() {
        return parameter1;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public boolean canUseAsStateDuring() {
        return true;
    }

}
