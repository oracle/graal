/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.nodes.graphbuilderconf.NodeIntrinsicPluginFactory.InjectionProvider;
import com.oracle.graal.word.WordTypes;

public class NodeIntrinsificationProvider implements InjectionProvider {

    private final MetaAccessProvider metaAccess;
    private final SnippetReflectionProvider snippetReflection;
    private final ForeignCallsProvider foreignCalls;
    private final WordTypes wordTypes;

    public NodeIntrinsificationProvider(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, ForeignCallsProvider foreignCalls, WordTypes wordTypes) {
        this.metaAccess = metaAccess;
        this.snippetReflection = snippetReflection;
        this.foreignCalls = foreignCalls;
        this.wordTypes = wordTypes;
    }

    @Override
    public Stamp getReturnStamp(Class<?> type) {
        JavaKind kind = JavaKind.fromJavaClass(type);
        if (kind == JavaKind.Object) {
            ResolvedJavaType returnType = metaAccess.lookupJavaType(type);
            if (wordTypes.isWord(returnType)) {
                return wordTypes.getWordStamp(returnType);
            } else {
                return StampFactory.declared(returnType);
            }
        } else {
            return StampFactory.forKind(kind);
        }
    }

    @Override
    public <T> T getInjectedArgument(Class<T> type) {
        T injected = snippetReflection.getInjectedNodeIntrinsicParameter(type);
        if (injected != null) {
            return injected;
        } else if (type.equals(ForeignCallsProvider.class)) {
            return type.cast(foreignCalls);
        } else if (type.equals(SnippetReflectionProvider.class)) {
            return type.cast(snippetReflection);
        } else {
            throw new JVMCIError("Cannot handle injected argument of type %s.", type.getName());
        }
    }
}
