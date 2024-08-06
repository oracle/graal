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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConvertNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.HubGetClassNodeInterface;
import jdk.graal.compiler.nodes.spi.Lowerable;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Read {@code Klass::_java_mirror} and incorporate non-null type information into stamp. This is
 * also used by {@link ClassGetHubNode} to eliminate chains of {@code klass._java_mirror._klass}.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class HubGetClassNode extends FloatingNode implements Lowerable, Canonicalizable, ConvertNode, HubGetClassNodeInterface {
    public static final NodeClass<HubGetClassNode> TYPE = NodeClass.create(HubGetClassNode.class);
    @Input protected ValueNode hub;

    public HubGetClassNode(@InjectedNodeParameter MetaAccessProvider metaAccess, ValueNode hub) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createWithoutAssumptions(metaAccess.lookupJavaType(Class.class))));
        this.hub = hub;
    }

    @Override
    public ValueNode getHub() {
        return hub;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        } else {
            MetaAccessProvider metaAccess = tool.getMetaAccess();
            if (metaAccess != null && hub.isConstant()) {
                ResolvedJavaType exactType = tool.getConstantReflection().asJavaType(hub.asConstant());
                if (exactType != null) {
                    return ConstantNode.forConstant(tool.getConstantReflection().asJavaClass(exactType), metaAccess);
                }
            }
            return this;
        }
    }

    @NodeIntrinsic
    public static native Class<?> readClass(KlassPointer hub);

    @Override
    public ValueNode getValue() {
        return hub;
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return c;
        }
        return constantReflection.asJavaClass(constantReflection.asJavaType(c));
    }

    @Override
    public Constant reverse(Constant c, ConstantReflectionProvider constantReflection) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return c;
        }
        ResolvedJavaType type = constantReflection.asJavaType(c);
        if (type.isPrimitive()) {
            return JavaConstant.NULL_POINTER;
        } else {
            return constantReflection.asObjectHub(type);
        }
    }

    /**
     * Any concrete Klass* has a corresponding {@link java.lang.Class}.
     */
    @Override
    public boolean isLossless() {
        return true;
    }

    @Override
    public boolean mayNullCheckSkipConversion() {
        return true;
    }
}
