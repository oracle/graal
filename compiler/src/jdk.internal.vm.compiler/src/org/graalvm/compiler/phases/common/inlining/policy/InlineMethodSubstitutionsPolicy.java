/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.inlining.policy;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.inlining.info.InlineInfo;
import org.graalvm.compiler.phases.common.inlining.walker.MethodInvocation;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Inline every method which would be replaced by a substitution. Useful for testing purposes.
 */
public final class InlineMethodSubstitutionsPolicy extends InlineEverythingPolicy {

    @Override
    public Decision isWorthInlining(Replacements replacements, MethodInvocation invocation, InlineInfo calleeInfo, int inliningDepth, boolean fullyProcessed) {
        OptionValues options = calleeInfo.graph().getOptions();
        final boolean isTracing = GraalOptions.TraceInlining.getValue(options) || calleeInfo.graph().getDebug().hasCompilationListener();
        CallTargetNode callTarget = invocation.callee().invoke().callTarget();
        if (callTarget instanceof MethodCallTargetNode) {
            ResolvedJavaMethod calleeMethod = callTarget.targetMethod();
            if (replacements.hasSubstitution(calleeMethod, options)) {
                return Decision.YES.withReason(isTracing, "has a method subtitution");
            }
        }
        return Decision.NO.withReason(isTracing, "does not have a method substitution");
    }
}
