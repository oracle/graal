/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.virtual;

import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Establishes a new opaque value for a field in read-elimination caches. The node behaves like a
 * synthetic write while the high-tier optimizations run, but lowers to only a field read.
 */
@NodeInfo(nameTemplate = "KillFieldReadCache#{p#field/s}")
public final class FieldReadCacheKillNode extends AccessFieldNode implements SingleMemoryKill, IterableNodeType {

    public static final NodeClass<FieldReadCacheKillNode> TYPE = NodeClass.create(FieldReadCacheKillNode.class);

    public FieldReadCacheKillNode(Assumptions assumptions, ValueNode object, ResolvedJavaField field) {
        super(TYPE, StampFactory.forDeclaredType(assumptions, field.getType(), false).getTrustedStamp(), object, field, MemoryOrderMode.PLAIN, false);
        assert field.isFinal() : "field read cache kills currently require final fields";
        assert !field.isStatic() : "field read cache kills currently require instance fields";
    }

    @Override
    public FieldLocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }

    @Override
    public void lower(LoweringTool tool) {
        if (hasNoUsages()) {
            graph().removeFixed(this);
            return;
        }
        LoadFieldNode load = graph().add(LoadFieldNode.createOverrideImmutable(LoadFieldNode.create(graph().getAssumptions(), object(), field())));
        graph().replaceFixedWithFixed(this, load);
        tool.getLowerer().lower(load, tool);
    }
}
