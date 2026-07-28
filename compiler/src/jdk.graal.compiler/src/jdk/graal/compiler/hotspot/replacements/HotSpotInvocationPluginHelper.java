/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HotSpotFieldLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.replacements.InvocationPluginHelper;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A helper class for HotSpot specific invocation plugins. In particular it adds helpers for
 * correctly performing reads of HotSpot specific fields.
 */
public class HotSpotInvocationPluginHelper extends InvocationPluginHelper {

    private final GraalHotSpotVMConfig config;
    private final BarrierSet barrierSet;

    public HotSpotInvocationPluginHelper(GraphBuilderContext b, ResolvedJavaMethod targetMethod, GraalHotSpotVMConfig config) {
        super(b, targetMethod);
        this.config = config;
        this.barrierSet = b.getPlatformConfigurationProvider().getBarrierSet();
    }

    public ValueNode readKlassFromClass(ValueNode clazz) {
        return b.add(ClassGetHubNode.create(clazz, b.getMetaAccess(), b.getConstantReflection()));
    }

    public ValueNode read(HotSpotFieldLocationIdentity field, ValueNode base) {
        assert StampTool.isPointerNonNull(base) || base.stamp(NodeView.DEFAULT).getStackKind() == getWordKind() : "must be null guarded";
        AddressNode address = field.asOffsetAddress(config, base);
        Stamp stamp = field.stampOrDefault(getWordKind());
        ReadNode read = b.add(new ReadNode(address, field, stamp, barrierSet.readBarrierType(field, address, stamp), MemoryOrderMode.PLAIN));
        ValueNode returnValue = b.add(ReadNode.canonicalizeRead(read, read.getAddress(), read.getLocationIdentity(), b, NodeView.DEFAULT));
        if (stamp instanceof IntegerStamp integerStamp) {
            int inputBits = integerStamp.getBits();
            int resultBits = integerStamp.getStackKind().getBitCount();
            if (inputBits < resultBits) {
                return b.add(integerStamp.lowerBound() >= 0 ? ZeroExtendNode.create(returnValue, resultBits, NodeView.DEFAULT) : SignExtendNode.create(returnValue, resultBits, NodeView.DEFAULT));
            }
        }
        return returnValue;
    }

    /**
     * Read {@code Klass:_layout_helper}.
     */
    public ValueNode klassLayoutHelper(ValueNode klass) {
        return b.add(KlassLayoutHelperNode.create(config, klass, b.getConstantReflection()));
    }

    public PiNode emitNullReturnGuard(ValueNode pointer, ValueNode returnValue, double probability) {
        GuardingNode nonnullGuard = emitReturnIf(IsNullNode.create(pointer), returnValue, probability);
        return piCast(pointer, nonnullGuard, ((AbstractPointerStamp) pointer.stamp(NodeView.DEFAULT)).asNonNull());
    }
}
