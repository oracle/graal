/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
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

    /**
     * The value of {@link GraalHotSpotVMConfig#klassLayoutHelperNeutralValue}.
     */
    private final int klassLayoutHelperNeutralValue;

    public KlassLayoutHelperNode(ValueNode klass, int klassLayoutHelperNeutralValue) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.klass = klass;
        this.klassLayoutHelperNeutralValue = klassLayoutHelperNeutralValue;
    }

    public static ValueNode create(GraalHotSpotVMConfig config, ValueNode klass, ConstantReflectionProvider constantReflection) {
        return canonical(null, klass, constantReflection, config.klassLayoutHelperNeutralValue);
    }

    public static boolean intrinsify(GraphBuilderContext b, @InjectedNodeParameter GraalHotSpotVMConfig config, ValueNode klass) {
        ValueNode valueNode = create(config, klass, b.getConstantReflection());
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
                        return updateStamp(StampFactory.forInteger(JavaKind.Int, klassLayoutHelperNeutralValue, Integer.MAX_VALUE));
                    }
                    if (type.isArray()) {
                        return updateStamp(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, klassLayoutHelperNeutralValue - 1));
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
            return canonical(this, klass, tool.getConstantReflection(), klassLayoutHelperNeutralValue);
        }
    }

    private static ValueNode canonical(KlassLayoutHelperNode klassLayoutHelperNode, ValueNode klass, ConstantReflectionProvider constantReflection, int klassLayoutHelperNeutralValue) {
        KlassLayoutHelperNode self = klassLayoutHelperNode;
        if (klass.isConstant() && !klass.asConstant().isDefaultForKind()) {
            HotSpotResolvedObjectType javaType = (HotSpotResolvedObjectType) constantReflection.asJavaType(klass.asConstant());
            return ConstantNode.forInt(javaType.layoutHelper());
        }
        if (klass instanceof LoadHubNode) {
            LoadHubNode hub = (LoadHubNode) klass;
            Stamp hubStamp = hub.getValue().stamp(NodeView.DEFAULT);
            if (hubStamp instanceof ObjectStamp) {
                ObjectStamp ostamp = (ObjectStamp) hubStamp;
                HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) ostamp.type();
                if (type != null && type.isArray() && !type.getComponentType().isPrimitive()) {
                    // The layout for all object arrays is the same.
                    return ConstantNode.forInt(type.layoutHelper());
                }
            }
        }
        if (self == null) {
            self = new KlassLayoutHelperNode(klass, klassLayoutHelperNeutralValue);
        }
        return self;
    }

    public ValueNode getHub() {
        return klass;
    }
}
