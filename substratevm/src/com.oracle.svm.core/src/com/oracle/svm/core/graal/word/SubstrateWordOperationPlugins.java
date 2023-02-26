/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.word;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateWordOperationPlugins extends WordOperationPlugin {

    public SubstrateWordOperationPlugins(SnippetReflectionProvider snippetReflection, WordTypes wordTypes) {
        super(snippetReflection, wordTypes);
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (!wordTypes.isWordOperation(method)) {
            if (!method.getDeclaringClass().equals(b.getMetaAccess().lookupJavaType(DynamicHubAccess.class))) {
                return false;
            }
        }

        SubstrateOperation operation = AnnotationAccess.getAnnotation(method, SubstrateOperation.class);
        if (operation == null) {
            processWordOperation(b, args, wordTypes.getWordOperation(method, b.getMethod().getDeclaringClass()));
            return true;
        }
        processSubstrateOperation(b, method, args, operation);
        return true;
    }

    protected void processSubstrateOperation(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, SubstrateOperation operation) {
        switch (operation.opcode()) {
            case READ_FROM_HUB:
                JavaKind returnKind = method.getSignature().getReturnKind();
                GraalError.guarantee(args.length == 4, "arg length=%d operation=%s", args.length, operation);
                JavaKind readKind = wordTypes.asKind(method.getSignature().getReturnType(method.getDeclaringClass()));
                AddressNode address = makeAddress(b, args[0], args[1]);
                LocationIdentity location;
                assert args[2].isConstant() : args[2];
                location = snippetReflection.asObject(LocationIdentity.class, args[2].asJavaConstant());
                assert location != null : snippetReflection.asObject(Object.class, args[2].asJavaConstant());
                FixedAccessNode read = b.add(new ReadNode(address, location, StampFactory.forKind(readKind), BarrierType.NONE, MemoryOrderMode.PLAIN));
                if (!(args[3] instanceof ConstantNode)) {
                    // guard can be the null constant
                    read.setGuard((GuardingNode) args[3]);
                }
                b.push(returnKind, read);
                break;
            default:
                throw GraalError.shouldNotReachHere("Unknown operation " + operation);
        }
    }
}
