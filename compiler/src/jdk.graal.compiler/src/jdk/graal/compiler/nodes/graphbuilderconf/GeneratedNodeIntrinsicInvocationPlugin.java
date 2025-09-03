/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.graphbuilderconf;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A plugin generated from an {@link jdk.graal.compiler.graph.Node.NodeIntrinsic @NodeIntrinsic}
 * annotation.
 */
public abstract class GeneratedNodeIntrinsicInvocationPlugin extends GeneratedInvocationPlugin {

    public GeneratedNodeIntrinsicInvocationPlugin(String name, Type... argumentTypes) {
        super(name, argumentTypes);
    }

    @Override
    public final Class<? extends Annotation> getSource() {
        return Node.NodeIntrinsic.class;
    }

    protected boolean verifyForeignCallDescriptor(GraphBuilderTool b, ResolvedJavaMethod targetMethod, ForeignCallDescriptor descriptor) {
        MetaAccessProvider metaAccess = b.getMetaAccess();
        int parameters = 1;
        for (Class<?> arg : descriptor.getArgumentTypes()) {
            ResolvedJavaType res = metaAccess.lookupJavaType(arg);
            ResolvedJavaType parameterType = (ResolvedJavaType) targetMethod.getSignature().getParameterType(parameters, targetMethod.getDeclaringClass());
            assert parameterType.equals(res) : descriptor + ": parameter " + parameters + " mismatch: " + res + " != " + parameterType;
            parameters++;
        }
        return true;
    }
}
