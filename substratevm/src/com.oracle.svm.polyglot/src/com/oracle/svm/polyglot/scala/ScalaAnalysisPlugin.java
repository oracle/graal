/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.polyglot.scala;

import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Registers array classes for reflective instantiation.
 *
 * Scala uses ClassTags to reflectively instantiate arrays. This plugin tracks creations of class
 * tags and marks types as in heap.
 */
public final class ScalaAnalysisPlugin implements NodePlugin {

    /**
     * Should be high enough to never be encountered in practice and low enough so types do not take
     * much space in the native image heap. Current number is chosen as ClassTag has methods for
     * instantiating arrays up to 5 levels of nesting, and we want to support them.
     */
    private static final int SUPPORTED_LEVEL_OF_NESTED_ARRAYS = 5;

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        /* ClassTag(<type>.class) */
        if (method.getDeclaringClass().getName().equals("Lscala/reflect/ClassTag$;") && method.getName().equals("apply") && args.length == 2) {
            JavaConstant clazzConstant = args[1].asJavaConstant();
            if (clazzConstant != null) {
                AnalysisType type = (AnalysisType) b.getConstantReflection().asJavaType(clazzConstant);
                for (int i = 0; i < SUPPORTED_LEVEL_OF_NESTED_ARRAYS; i++) {
                    type = type.getArrayClass();
                    type.registerAsInstantiated(b.getGraph().currentNodeSourcePosition());
                }
            }
        }
        return false;
    }
}
