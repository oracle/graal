/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.phases;

import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantReflectionProvider;
import com.oracle.svm.common.ClassInitializationNode;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;

import java.util.function.Supplier;

/**
 * Emmit a class initialization barrier for analysis purpose.
 */
public class StandaloneClassInitializationPlugin extends NoClassInitializationPlugin {
    private final StandaloneConstantReflectionProvider constantReflectionProvider;

    public StandaloneClassInitializationPlugin(StandaloneConstantReflectionProvider constantReflectionProvider, boolean printWarnings) {
        super(printWarnings);
        this.constantReflectionProvider = constantReflectionProvider;
    }

    @Override
    public boolean apply(GraphBuilderContext builder, ResolvedJavaType type, Supplier<FrameState> frameState) {
        ResolvedJavaType declaringType = builder.getMethod().getDeclaringClass();
        if (type.isArray() || type.equals(declaringType)) {
            return false;
        }
        if (type.isAssignableFrom(declaringType)) {
            if (type.isInterface()) {
                /*
                 * Initialization of a class only triggers initialization of an implemented
                 * interface if that interface declares default methods.
                 */
                return !type.declaresDefaultMethods();
            } else {
                /* Initialization of a class triggers initialization of all superclasses. */
                return false;
            }
        }
        ValueNode hub = ConstantNode.forConstant(constantReflectionProvider.asJavaClass(type), builder.getMetaAccess(), builder.getGraph());
        ClassInitializationNode node = new ClassInitializationNode(hub, frameState.get());
        builder.add(node);
        return true;
    }
}
