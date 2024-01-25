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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.c.function.CEntryPointActions;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.DeoptimizingFixedWithNextNode;
import jdk.graal.compiler.nodes.DeoptimizingNode.DeoptBefore;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_8, size = SIZE_8, allowedUsageTypes = {InputType.Memory})
public final class CEntryPointEnterNode extends DeoptimizingFixedWithNextNode implements Lowerable, SingleMemoryKill, DeoptBefore {

    public static final NodeClass<CEntryPointEnterNode> TYPE = NodeClass.create(CEntryPointEnterNode.class);

    /** @see CEntryPointActions */
    public enum EnterAction {
        CreateIsolate,
        AttachThread,
        Enter,
        EnterByIsolate,
    }

    protected final EnterAction enterAction;

    @OptionalInput protected ValueNode parameter;
    private final boolean startedByIsolate;
    private final boolean ensureJavaThread;

    public static CEntryPointEnterNode createIsolate(ValueNode parameters) {
        return new CEntryPointEnterNode(EnterAction.CreateIsolate, parameters, false, false);
    }

    public static CEntryPointEnterNode attachThread(ValueNode isolate, boolean startedByIsolate, boolean ensureJavaThread) {
        return new CEntryPointEnterNode(EnterAction.AttachThread, isolate, startedByIsolate, ensureJavaThread);
    }

    public static CEntryPointEnterNode enter(ValueNode isolateThread) {
        return new CEntryPointEnterNode(EnterAction.Enter, isolateThread, false, false);
    }

    public static CEntryPointEnterNode enterByIsolate(ValueNode isolate) {
        return new CEntryPointEnterNode(EnterAction.EnterByIsolate, isolate, false, false);
    }

    protected CEntryPointEnterNode(EnterAction enterAction, ValueNode parameter, boolean startedByCurrentIsolate, boolean ensureJavaThread) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.enterAction = enterAction;
        this.parameter = parameter;
        this.startedByIsolate = startedByCurrentIsolate;
        this.ensureJavaThread = ensureJavaThread;
    }

    public EnterAction getEnterAction() {
        return enterAction;
    }

    public ValueNode getParameter() {
        return parameter;
    }

    public boolean getStartedByIsolate() {
        return startedByIsolate;
    }

    public boolean getEnsureJavaThread() {
        return ensureJavaThread;
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
