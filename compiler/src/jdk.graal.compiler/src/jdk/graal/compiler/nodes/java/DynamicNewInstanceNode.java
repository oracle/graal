/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.lang.reflect.Modifier;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo
public final class DynamicNewInstanceNode extends AbstractNewObjectNode implements Canonicalizable {
    public static final NodeClass<DynamicNewInstanceNode> TYPE = NodeClass.create(DynamicNewInstanceNode.class);

    @Input ValueNode clazz;

    public static void createAndPush(GraphBuilderContext b, ValueNode clazz, boolean validateClass) {
        ResolvedJavaType constantType = tryConvertToNonDynamic(clazz, b);
        if (constantType != null) {
            b.addPush(JavaKind.Object, new NewInstanceNode(constantType, true));
        } else {
            ValueNode clazzLegal = validateClass ? b.add(new ValidateNewInstanceClassNode(clazz)) : clazz;
            b.addPush(JavaKind.Object, new DynamicNewInstanceNode(clazzLegal, true));
        }
    }

    protected DynamicNewInstanceNode(ValueNode clazz, boolean fillContents) {
        super(TYPE, StampFactory.objectNonNull(), fillContents, null);
        this.clazz = clazz;
        assert ((ObjectStamp) clazz.stamp(NodeView.DEFAULT)).nonNull();
    }

    public ValueNode getInstanceType() {
        return clazz;
    }

    static ResolvedJavaType tryConvertToNonDynamic(ValueNode clazz, CoreProviders tool) {
        if (clazz.isConstant()) {
            ResolvedJavaType type = tool.getConstantReflection().asJavaType(clazz.asConstant());
            if (type != null && !throwsInstantiationException(type, tool.getMetaAccess()) && tool.getMetaAccessExtensionProvider().canConstantFoldDynamicAllocation(type)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ResolvedJavaType type = tryConvertToNonDynamic(clazz, tool);
        if (type != null) {
            return new NewInstanceNode(type, fillContents(), stateBefore());
        }
        return this;
    }

    public static boolean throwsInstantiationExceptionInjectedProbability(double probability, Class<?> type, Class<?> classClass) {
        /*
         * This method is for use in a snippet and therefore injects probabilities for each
         * disjunct.
         */
        return probability(probability, type.isPrimitive()) ||
                        probability(probability, type.isArray()) ||
                        probability(probability, type.isInterface()) ||
                        probability(probability, Modifier.isAbstract(type.getModifiers())) ||
                        probability(probability, type == classClass);
    }

    public static boolean throwsInstantiationException(ResolvedJavaType type, MetaAccessProvider metaAccess) {
        return type.isPrimitive() || type.isArray() || type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type.equals(metaAccess.lookupJavaType(Class.class));
    }
}
