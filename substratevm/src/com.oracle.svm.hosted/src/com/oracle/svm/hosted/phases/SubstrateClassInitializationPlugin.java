/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.util.function.Supplier;

import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateClassInitializationPlugin implements ClassInitializationPlugin {

    private final SVMHost host;

    public SubstrateClassInitializationPlugin(SVMHost host) {
        this.host = host;
    }

    @Override
    public boolean supportsLazyInitialization(ConstantPool cp) {
        return true;
    }

    @Override
    public void loadReferencedType(GraphBuilderContext builder, ConstantPool constantPool, int cpi, int bytecode) {
        constantPool.loadReferencedType(cpi, bytecode);
    }

    @Override
    public boolean apply(GraphBuilderContext builder, ResolvedJavaType type, Supplier<FrameState> frameState) {
        var requiredForTypeReached = ClassInitializationSupport.singleton().requiresInitializationNodeForTypeReached(type);
        if (requiredForTypeReached ||
                        EnsureClassInitializedNode.needsRuntimeInitialization(builder.getMethod().getDeclaringClass(), type)) {
            assert !type.isArray() : "Array types must not have initialization nodes: " + type.getName();
            SnippetReflectionProvider snippetReflection = builder.getSnippetReflection();
            emitEnsureClassInitialized(builder, snippetReflection.forObject(host.dynamicHub(type)), frameState.get());
            return true;
        }
        return false;
    }

    private static void emitEnsureClassInitialized(GraphBuilderContext builder, JavaConstant hubConstant, FrameState frameState) {
        ValueNode hub = ConstantNode.forConstant(hubConstant, builder.getMetaAccess(), builder.getGraph());
        EnsureClassInitializedNode node = new EnsureClassInitializedNode(hub, frameState);
        builder.canonicalizeAndAdd(node);
    }
}
