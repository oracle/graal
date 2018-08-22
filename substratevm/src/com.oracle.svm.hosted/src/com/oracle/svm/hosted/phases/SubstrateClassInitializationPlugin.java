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

import java.lang.reflect.Method;

import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateClassInitializationPlugin implements ClassInitializationPlugin {

    private static final Method ENSURE_INITIALIZED_METHOD;

    static {
        try {
            ENSURE_INITIALIZED_METHOD = DynamicHub.class.getDeclaredMethod("ensureInitialized");
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

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
    public boolean shouldApply(GraphBuilderContext builder, ResolvedJavaType type) {
        return !type.isInitialized();
    }

    @Override
    public ValueNode apply(GraphBuilderContext builder, ResolvedJavaType type, FrameState frameState) {
        JavaConstant hub = SubstrateObjectConstant.forObject(host.dynamicHub(type));
        ValueNode[] args = new ValueNode[]{ConstantNode.forConstant(hub, builder.getMetaAccess(), builder.getGraph())};
        builder.handleReplacedInvoke(InvokeKind.Special, builder.getMetaAccess().lookupJavaMethod(ENSURE_INITIALIZED_METHOD), args, false);

        /*
         * The return value is only registered with Invoke nodes, and we do not need that feature
         * for now. So we can just return null.
         */
        return null;
    }
}
