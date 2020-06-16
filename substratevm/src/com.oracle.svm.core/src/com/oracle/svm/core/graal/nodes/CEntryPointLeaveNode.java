/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.DeoptimizingNode.DeoptBefore;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.c.function.CEntryPointActions;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_8, size = NodeSize.SIZE_8, allowedUsageTypes = {InputType.Memory})
public class CEntryPointLeaveNode extends DeoptimizingFixedWithNextNode implements Lowerable, SingleMemoryKill, DeoptBefore {

    public static final NodeClass<CEntryPointLeaveNode> TYPE = NodeClass.create(CEntryPointLeaveNode.class);

    /** @see CEntryPointActions */
    public enum LeaveAction {
        Leave,
        DetachThread,
        TearDownIsolate,
        ExceptionAbort;
    }

    protected final LeaveAction leaveAction;
    @OptionalInput protected ValueNode exception;

    public CEntryPointLeaveNode(LeaveAction leaveAction) {
        this(leaveAction, null);
    }

    public CEntryPointLeaveNode(LeaveAction leaveAction, ValueNode exception) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert (leaveAction == LeaveAction.ExceptionAbort) == (exception != null);
        this.leaveAction = leaveAction;
        this.exception = exception;
    }

    public LeaveAction getLeaveAction() {
        return leaveAction;
    }

    public ValueNode getException() {
        return exception;
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
