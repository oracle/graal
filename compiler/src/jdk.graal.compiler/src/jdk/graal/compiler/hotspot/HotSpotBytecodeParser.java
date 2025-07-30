/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase.Instance;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedNodeIntrinsicInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * HotSpot specific implementation of the bytecode parser.
 */
public class HotSpotBytecodeParser extends BytecodeParser {

    protected HotSpotBytecodeParser(Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
    }

    @Override
    protected Object lookupConstant(int cpi, int opcode, boolean allowBootstrapMethodInvocation) {
        try {
            return super.lookupConstant(cpi, opcode, allowBootstrapMethodInvocation);
        } catch (BootstrapMethodError e) {
            DeoptimizeNode deopt = append(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint));
            /*
             * Track source position for deopt nodes even if
             * GraphBuilderConfiguration.trackNodeSourcePosition is not set.
             */
            deopt.updateNodeSourcePosition(() -> createBytecodePosition());
            return e;
        }
    }

    @Override
    public GuardingNode intrinsicRangeCheck(LogicNode condition, boolean negated) {
        return doIntrinsicRangeCheck(this, condition, negated);
    }

    public static final String BAD_NODE_INTRINSIC_PLUGIN_CONTEXT = "@NodeIntrinsic plugin can't be used outside of Snippet context: ";

    @Override
    protected boolean applyInvocationPlugin(CallTargetNode.InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType, InvocationPlugin plugin) {
        // It's an error to call generated invocation plugins outside of snippets.
        if (plugin instanceof GeneratedNodeIntrinsicInvocationPlugin nodeIntrinsicPlugin) {
            // Snippets are never parsed in libgraal, and they are the root of the compilation
            // in jargraal, so check the root method for the Snippet annotation.
            if (LibGraalSupport.inLibGraalRuntime() || graph.method().getAnnotation(Snippet.class) == null) {
                throw new PermanentBailoutException(BAD_NODE_INTRINSIC_PLUGIN_CONTEXT + nodeIntrinsicPlugin.getSource().getSimpleName());
            }
        }
        return super.applyInvocationPlugin(invokeKind, args, targetMethod, resultType, plugin);
    }

    public static FixedGuardNode doIntrinsicRangeCheck(GraphBuilderContext context, LogicNode condition, boolean negated) {
        /*
         * On HotSpot it's simplest to always deoptimize. We could dispatch to the fallback code
         * instead but that will greatly expand what's emitted for the intrinsic since it will have
         * both the fast and slow versions inline. Actually deoptimizing here is unlikely as the
         * most common uses of this method are in plugins for JDK internal methods. Those methods
         * are generally unlikely to have arguments that lead to exceptions. The deopt action of
         * None also keeps this guard from turning into a floating so it will stay fixed in the
         * control flow.
         */
        return context.add(new FixedGuardNode(condition, DeoptimizationReason.BoundsCheckException, DeoptimizationAction.None, !negated));
    }

    /**
     * {@code OnStackReplacementPhase.initLocal()} can clear non-live oop locals since JVMCI can
     * supply the oop map for a method at a specific BCI.
     */
    @Override
    protected boolean mustClearNonLiveLocalsAtOSREntry() {
        return false;
    }

    @Override
    protected boolean needBarrierAfterFieldStore(ResolvedJavaField field) {
        if (method.isConstructor() && field instanceof HotSpotResolvedJavaField hfield && hfield.isStable()) {
            return true;
        }

        return super.needBarrierAfterFieldStore(field);
    }
}
