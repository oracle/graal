/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedPluginInjectionProvider;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class NodeIntrinsificationProvider implements GeneratedPluginInjectionProvider {

    public static final TargetDescription INJECTED_TARGET = null;

    private final MetaAccessProvider metaAccess;
    private final SnippetReflectionProvider snippetReflection;
    private final ForeignCallsProvider foreignCalls;
    private final WordTypes wordTypes;
    private final TargetDescription target;

    public NodeIntrinsificationProvider(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, ForeignCallsProvider foreignCalls, WordTypes wordTypes, TargetDescription target) {
        this.metaAccess = metaAccess;
        this.snippetReflection = snippetReflection;
        this.foreignCalls = foreignCalls;
        this.wordTypes = wordTypes;
        this.target = target;
    }

    @Override
    public Stamp getInjectedStamp(Class<?> type, boolean nonNull) {
        JavaKind kind = JavaKind.fromJavaClass(type);
        if (kind == JavaKind.Object) {
            ResolvedJavaType returnType = metaAccess.lookupJavaType(type);
            if (wordTypes.isWord(returnType)) {
                return wordTypes.getWordStamp(returnType);
            } else {
                return StampFactory.object(TypeReference.createWithoutAssumptions(returnType), nonNull);
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
        } else if (type.equals(ForeignCallsProvider.class) || type.equals(ArrayCopyForeignCalls.class)) {
            return type.cast(foreignCalls);
        } else if (type.equals(SnippetReflectionProvider.class)) {
            return type.cast(snippetReflection);
        } else if (type.equals(WordTypes.class)) {
            return type.cast(wordTypes);
        } else if (type.equals(TargetDescription.class)) {
            return type.cast(target);
        } else {
            throw new GraalError("Cannot handle injected argument of type %s.", type.getName());
        }
    }
}
