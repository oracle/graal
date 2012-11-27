/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.snippets;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;

public class IntrinsifyArrayCopyPhase extends Phase {
    private final GraalCodeCacheProvider runtime;
    private ResolvedJavaMethod arrayCopy;
    private ResolvedJavaMethod byteArrayCopy;
    private ResolvedJavaMethod shortArrayCopy;
    private ResolvedJavaMethod charArrayCopy;
    private ResolvedJavaMethod intArrayCopy;
    private ResolvedJavaMethod longArrayCopy;
    private ResolvedJavaMethod floatArrayCopy;
    private ResolvedJavaMethod doubleArrayCopy;
    private ResolvedJavaMethod objectArrayCopy;

    public IntrinsifyArrayCopyPhase(GraalCodeCacheProvider runtime) {
        this.runtime = runtime;
        try {
            byteArrayCopy = getArrayCopySnippet(runtime, byte.class);
            charArrayCopy = getArrayCopySnippet(runtime, char.class);
            shortArrayCopy = getArrayCopySnippet(runtime, short.class);
            intArrayCopy = getArrayCopySnippet(runtime, int.class);
            longArrayCopy = getArrayCopySnippet(runtime, long.class);
            floatArrayCopy = getArrayCopySnippet(runtime, float.class);
            doubleArrayCopy = getArrayCopySnippet(runtime, double.class);
            objectArrayCopy = getArrayCopySnippet(runtime, Object.class);
            arrayCopy = runtime.lookupJavaMethod(System.class.getDeclaredMethod("arraycopy", Object.class, int.class, Object.class, int.class, int.class));
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private static ResolvedJavaMethod getArrayCopySnippet(CodeCacheProvider runtime, Class<?> componentClass) throws NoSuchMethodException {
        Class<?> arrayClass = Array.newInstance(componentClass, 0).getClass();
        return runtime.lookupJavaMethod(ArrayCopySnippets.class.getDeclaredMethod("arraycopy", arrayClass, int.class, arrayClass, int.class, int.class));
    }

    @Override
    protected void run(StructuredGraph graph) {
        boolean hits = false;
        for (MethodCallTargetNode methodCallTarget : graph.getNodes(MethodCallTargetNode.class)) {
            ResolvedJavaMethod targetMethod = methodCallTarget.targetMethod();
            ResolvedJavaMethod snippetMethod = null;
            if (targetMethod == arrayCopy) {
                ValueNode src = methodCallTarget.arguments().get(0);
                ValueNode dest = methodCallTarget.arguments().get(2);
                assert src != null && dest != null;
                ResolvedJavaType srcType = src.objectStamp().type();
                ResolvedJavaType destType = dest.objectStamp().type();
                if (srcType != null
                                && srcType.isArray()
                                && destType != null
                                && destType.isArray()) {
                    Kind componentKind = srcType.getComponentType().getKind();
                    if (srcType.getComponentType() == destType.getComponentType()) {
                        if (componentKind == Kind.Int) {
                            snippetMethod = intArrayCopy;
                        } else if (componentKind == Kind.Char) {
                            snippetMethod = charArrayCopy;
                        } else if (componentKind == Kind.Long) {
                            snippetMethod = longArrayCopy;
                        } else if (componentKind == Kind.Byte) {
                            snippetMethod = byteArrayCopy;
                        } else if (componentKind == Kind.Short) {
                            snippetMethod = shortArrayCopy;
                        } else if (componentKind == Kind.Float) {
                            snippetMethod = floatArrayCopy;
                        } else if (componentKind == Kind.Double) {
                            snippetMethod = doubleArrayCopy;
                        } else if (componentKind == Kind.Object) {
                            snippetMethod = objectArrayCopy;
                        }
                    } else if (componentKind == Kind.Object
                                    && srcType.getComponentType().isAssignableTo(destType.getComponentType())) {
                        snippetMethod = objectArrayCopy;
                    }
                }
            }

            if (snippetMethod != null) {
                StructuredGraph snippetGraph = (StructuredGraph) snippetMethod.getCompilerStorage().get(Graph.class);
                assert snippetGraph != null : "ArrayCopySnippets should be installed";
                hits = true;
                Debug.log("%s > Intinsify (%s)", Debug.currentScope(), snippetMethod.getSignature().getParameterType(0, snippetMethod.getDeclaringClass()).getComponentType());
                InliningUtil.inline(methodCallTarget.invoke(), snippetGraph, false);
            }
        }
        if (GraalOptions.OptCanonicalizer && hits) {
            new CanonicalizerPhase(null, runtime, null).apply(graph);
        }
    }
}
