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
package com.oracle.graal.replacements;

import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.replacements.NodeIntrinsificationPhase.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.word.*;

/**
 * An {@link GenericInvocationPlugin} that handles methods annotated by {@link Fold},
 * {@link NodeIntrinsic} and all annotations supported by a given {@link WordOperationPlugin}.
 */
public class DefaultGenericInvocationPlugin implements GenericInvocationPlugin {
    protected final NodeIntrinsificationPhase nodeIntrinsification;
    protected final WordOperationPlugin wordOperationPlugin;

    public DefaultGenericInvocationPlugin(NodeIntrinsificationPhase nodeIntrinsification, WordOperationPlugin wordOperationPlugin) {
        this.nodeIntrinsification = nodeIntrinsification;
        this.wordOperationPlugin = wordOperationPlugin;
    }

    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (b.parsingReplacement() && wordOperationPlugin.apply(b, method, args)) {
            return true;
        } else if (b.parsingReplacement()) {
            NodeIntrinsic intrinsic = nodeIntrinsification.getIntrinsic(method);
            if (intrinsic != null) {
                Signature sig = method.getSignature();
                Kind returnKind = sig.getReturnKind();
                Stamp stamp = StampFactory.forKind(returnKind);
                if (returnKind == Kind.Object) {
                    JavaType returnType = sig.getReturnType(method.getDeclaringClass());
                    if (returnType instanceof ResolvedJavaType) {
                        ResolvedJavaType resolvedReturnType = (ResolvedJavaType) returnType;
                        WordTypes wordTypes = wordOperationPlugin.getWordTypes();
                        if (wordTypes.isWord(resolvedReturnType)) {
                            stamp = wordTypes.getWordStamp(resolvedReturnType);
                        } else {
                            stamp = StampFactory.declared(resolvedReturnType);
                        }
                    }
                }

                return processNodeIntrinsic(b, method, intrinsic, Arrays.asList(args), returnKind, stamp);
            } else if (nodeIntrinsification.isFoldable(method)) {
                ResolvedJavaType[] parameterTypes = resolveJavaTypes(method.toParameterTypes(), method.getDeclaringClass());
                JavaConstant constant = nodeIntrinsification.tryFold(Arrays.asList(args), parameterTypes, method);
                if (!COULD_NOT_FOLD.equals(constant)) {
                    if (constant != null) {
                        // Replace the invoke with the result of the call
                        ConstantNode res = b.append(ConstantNode.forConstant(constant, b.getMetaAccess()));
                        b.addPush(res.getKind().getStackKind(), res);
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

    protected boolean processNodeIntrinsic(GraphBuilderContext b, ResolvedJavaMethod method, NodeIntrinsic intrinsic, List<ValueNode> args, Kind returnKind, Stamp stamp) {
        ValueNode res = createNodeIntrinsic(b, method, intrinsic, args, stamp);
        if (res == null) {
            return false;
        }
        if (res instanceof UnsafeCopyNode) {
            UnsafeCopyNode copy = (UnsafeCopyNode) res;
            UnsafeLoadNode value = b.append(new UnsafeLoadNode(copy.sourceObject(), copy.sourceOffset(), copy.accessKind(), copy.getLocationIdentity()));
            b.add(new UnsafeStoreNode(copy.destinationObject(), copy.destinationOffset(), value, copy.accessKind(), copy.getLocationIdentity()));
            return true;
        } else if (res instanceof ForeignCallNode) {
            ForeignCallNode foreign = (ForeignCallNode) res;
            foreign.setBci(b.bci());
        }

        res = b.append(res);
        if (returnKind != Kind.Void) {
            assert res.getKind().getStackKind() != Kind.Void;
            b.push(returnKind.getStackKind(), res);
        } else {
            assert res.getKind().getStackKind() == Kind.Void;
        }

        if (res instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) res;
            if (stateSplit.stateAfter() == null) {
                stateSplit.setStateAfter(b.createStateAfter());
            }
        }

        return true;
    }

    protected ValueNode createNodeIntrinsic(GraphBuilderContext b, ResolvedJavaMethod method, NodeIntrinsic intrinsic, List<ValueNode> args, Stamp stamp) {
        ValueNode res = nodeIntrinsification.createIntrinsicNode(args, stamp, method, b.getGraph(), intrinsic);
        assert res != null || b.getRootMethod().getAnnotation(Snippet.class) != null : String.format(
                        "Could not create node intrinsic for call to %s as one of the arguments expected to be constant isn't: arguments=%s", method.format("%H.%n(%p)"), args);
        return res;
    }
}
