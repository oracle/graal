/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.replacements;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.type.ObjectStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.Node.NodeIntrinsicFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.FloatingNode;
import jdk.compiler.graal.nodes.extended.LoadHubNode;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.compiler.graal.nodes.spi.Lowerable;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Read {@code Klass::_layout_helper} and incorporate any useful stamp information based on any type
 * information in {@code klass}.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
@NodeIntrinsicFactory
public final class KlassLayoutHelperNode extends FloatingNode implements Canonicalizable, Lowerable {

    public static final NodeClass<KlassLayoutHelperNode> TYPE = NodeClass.create(KlassLayoutHelperNode.class);
    @Input protected ValueNode klass;
    protected final GraalHotSpotVMConfig config;

    public KlassLayoutHelperNode(@InjectedNodeParameter GraalHotSpotVMConfig config, ValueNode klass) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.config = config;
        this.klass = klass;
    }

    public static ValueNode create(GraalHotSpotVMConfig config, ValueNode klass, ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess) {
        Stamp stamp = StampFactory.forKind(JavaKind.Int);
        return canonical(null, config, klass, stamp, constantReflection, metaAccess);
    }

    public static boolean intrinsify(GraphBuilderContext b, @InjectedNodeParameter GraalHotSpotVMConfig config, ValueNode klass) {
        ValueNode valueNode = create(config, klass, b.getConstantReflection(), b.getMetaAccess());
        b.push(JavaKind.Int, b.append(valueNode));
        return true;
    }

    @Override
    public boolean inferStamp() {
        if (klass instanceof LoadHubNode) {
            LoadHubNode hub = (LoadHubNode) klass;
            Stamp hubStamp = hub.getValue().stamp(NodeView.DEFAULT);
            if (hubStamp instanceof ObjectStamp) {
                ObjectStamp objectStamp = (ObjectStamp) hubStamp;
                ResolvedJavaType type = objectStamp.type();
                if (type != null && !type.isJavaLangObject()) {
                    if (!type.isArray() && !type.isInterface()) {
                        /*
                         * Definitely some form of instance type.
                         */
                        return updateStamp(StampFactory.forInteger(JavaKind.Int, config.klassLayoutHelperNeutralValue, Integer.MAX_VALUE));
                    }
                    if (type.isArray()) {
                        return updateStamp(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, config.klassLayoutHelperNeutralValue - 1));
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        } else {
            return canonical(this, config, klass, stamp(NodeView.DEFAULT), tool.getConstantReflection(), tool.getMetaAccess());
        }
    }

    private static ValueNode canonical(KlassLayoutHelperNode klassLayoutHelperNode, GraalHotSpotVMConfig config, ValueNode klass, Stamp stamp, ConstantReflectionProvider constantReflection,
                    MetaAccessProvider metaAccess) {
        KlassLayoutHelperNode self = klassLayoutHelperNode;
        if (klass.isConstant()) {
            if (!klass.asConstant().isDefaultForKind()) {
                Constant constant = stamp.readConstant(constantReflection.getMemoryAccessProvider(), klass.asConstant(), config.klassLayoutHelperOffset);
                return ConstantNode.forConstant(stamp, constant, metaAccess);
            }
        }
        if (klass instanceof LoadHubNode) {
            LoadHubNode hub = (LoadHubNode) klass;
            Stamp hubStamp = hub.getValue().stamp(NodeView.DEFAULT);
            if (hubStamp instanceof ObjectStamp) {
                ObjectStamp ostamp = (ObjectStamp) hubStamp;
                HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) ostamp.type();
                if (type != null && type.isArray() && !type.getComponentType().isPrimitive()) {
                    // The layout for all object arrays is the same.
                    Constant constant = stamp.readConstant(constantReflection.getMemoryAccessProvider(), type.klass(), config.klassLayoutHelperOffset);
                    return ConstantNode.forConstant(stamp, constant, metaAccess);
                }
            }
        }
        if (self == null) {
            self = new KlassLayoutHelperNode(config, klass);
        }
        return self;
    }

    public ValueNode getHub() {
        return klass;
    }
}
