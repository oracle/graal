/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.replacements.ReplacementsImpl;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

final class ParsingInlineInvokePlugin implements InlineInvokePlugin {

    private final PartialEvaluator partialEvaluator;
    private final ReplacementsImpl replacements;
    private final InvocationPlugins invocationPlugins;
    private final LoopExplosionPlugin loopExplosionPlugin;

    ParsingInlineInvokePlugin(PartialEvaluator partialEvaluator, ReplacementsImpl replacements, InvocationPlugins invocationPlugins, LoopExplosionPlugin loopExplosionPlugin) {
        this.partialEvaluator = partialEvaluator;
        this.replacements = replacements;
        this.invocationPlugins = invocationPlugins;
        this.loopExplosionPlugin = loopExplosionPlugin;
    }

    private boolean hasMethodHandleArgument(ValueNode[] arguments) {
        for (ValueNode argument : arguments) {
            if (argument.isConstant()) {
                JavaConstant constant = argument.asJavaConstant();
                if (constant.getJavaKind() == JavaKind.Object && constant.isNonNull() && partialEvaluator.knownTruffleTypes.classMethodHandle.isInstance(constant)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
        if (invocationPlugins.lookupInvocation(original) != null || replacements.hasSubstitution(original)) {
            /*
             * During partial evaluation, the invocation plugin or the substitution might trigger,
             * so we want the call to remain (we have better type information and more constant
             * values during partial evaluation). But there is no guarantee for that, so we also
             * need to preserve exception handler information for the call.
             */
            return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
        } else if (loopExplosionPlugin.loopExplosionKind(original) != LoopExplosionPlugin.LoopExplosionKind.NONE) {
            return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
        }

        InlineInfo inlineInfo = PartialEvaluator.asInlineInfo(original);
        if (!inlineInfo.allowsInlining()) {
            return inlineInfo;
        }
        for (ResolvedJavaMethod neverInlineMethod : partialEvaluator.getNeverInlineMethods()) {
            if (original.equals(neverInlineMethod)) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }
        }
        if (hasMethodHandleArgument(arguments)) {
            /*
             * We want to inline invokes that have a constant MethodHandle parameter to remove
             * invokedynamic related calls as early as possible.
             */
            return inlineInfo;
        }
        return null;
    }
}
