/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.replacements.NodeIntrinsificationPhase.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.Node.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderPlugin.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.replacements.*;

final class HotSpotAnnotatedInvocationPlugin implements GenericInvocationPlugin {
    private final HotSpotSuitesProvider suites;

    public HotSpotAnnotatedInvocationPlugin(HotSpotSuitesProvider suites) {
        this.suites = suites;
    }

    public boolean apply(GraphBuilderContext builder, ResolvedJavaMethod method, ValueNode[] args) {
        if (builder.parsingReplacement()) {
            NodeIntrinsificationPhase intrins = suites.getNodeIntrinsification();
            NodeIntrinsic intrinsic = intrins.getIntrinsic(method);
            if (intrinsic != null) {
                Signature sig = method.getSignature();
                Kind returnKind = sig.getReturnKind();
                Stamp stamp = StampFactory.forKind(returnKind);
                if (returnKind == Kind.Object) {
                    JavaType returnType = sig.getReturnType(method.getDeclaringClass());
                    if (returnType instanceof ResolvedJavaType) {
                        stamp = StampFactory.declared((ResolvedJavaType) returnType);
                    }
                }

                ValueNode res = intrins.createIntrinsicNode(Arrays.asList(args), stamp, method, builder.getGraph(), intrinsic);
                res = builder.append(res);
                if (res.getKind().getStackKind() != Kind.Void) {
                    builder.push(returnKind.getStackKind(), res);
                }
                return true;
            } else if (intrins.isFoldable(method)) {
                ResolvedJavaType[] parameterTypes = resolveJavaTypes(method.toParameterTypes(), method.getDeclaringClass());
                JavaConstant constant = intrins.tryFold(Arrays.asList(args), parameterTypes, method);
                if (!COULD_NOT_FOLD.equals(constant)) {
                    if (constant != null) {
                        // Replace the invoke with the result of the call
                        ConstantNode res = builder.append(ConstantNode.forConstant(constant, suites.getMetaAccess()));
                        builder.push(res.getKind().getStackKind(), builder.append(res));
                    } else {
                        // This must be a void invoke
                        assert method.getSignature().getReturnKind() == Kind.Void;
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
