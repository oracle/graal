/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Type;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A generic plugin used to implement substitution of methods by snippets.
 */
public abstract class SnippetSubstitutionInvocationPlugin<T extends SnippetTemplate.AbstractTemplates> extends InvocationPlugin.InlineOnlyInvocationPlugin {

    private final boolean hasSideEffect;
    private final Class<T> templateClass;

    public SnippetSubstitutionInvocationPlugin(Class<T> templateClass, boolean hasSideEffect, String name, Type... argumentTypes) {
        super(name, argumentTypes);
        this.hasSideEffect = hasSideEffect;
        this.templateClass = templateClass;
    }

    public abstract SnippetTemplate.SnippetInfo getSnippet(T templates);

    @Override
    public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] args) {
        if (!b.isPluginEnabled(this)) {
            return false;
        }
        if (receiver != null) {
            // Perform the required null check
            ValueNode r = receiver.get();
            assert args[0] == r;
        }

        // Build the appropriate node to represent snippet until it's lowered
        Stamp stamp = b.getInvokeReturnStamp(b.getAssumptions()).getTrustedStamp();
        SnippetSubstitutionNode node;

        T templates = b.getReplacements().getSnippetTemplateCache(templateClass);
        GraalError.guarantee(templates != null, "Missing templates for " + templateClass);
        SnippetTemplate.SnippetInfo snippet = getSnippet(templates);
        if (hasSideEffect) {
            SnippetSubstitutionStateSplitNode split = new SnippetSubstitutionStateSplitNode(templates, snippet, targetMethod, stamp, args);
            split.setBci(b.bci());
            node = split;
        } else {
            node = new SnippetSubstitutionNode(templates, snippet, targetMethod, stamp, args);
        }

        // Transfer any extra constant arguments required for the lowering
        node.setConstantArguments(getConstantArguments(targetMethod));

        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        if (returnKind != JavaKind.Void) {
            b.addPush(returnKind, node);
        } else {
            b.add(node);
        }
        return true;
    }

    /**
     * Provide any extra arguments that should be passed to the {@link Snippet} as
     * {@link ConstantParameter} arguments.
     */
    @SuppressWarnings("unused")
    protected Object[] getConstantArguments(ResolvedJavaMethod targetMethod) {
        return null;
    }
}
