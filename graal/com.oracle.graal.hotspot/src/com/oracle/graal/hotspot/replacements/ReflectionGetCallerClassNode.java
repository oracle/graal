/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.compiler.GraalCompiler.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.nodes.*;

public class ReflectionGetCallerClassNode extends MacroNode implements Canonicalizable, Lowerable {

    public ReflectionGetCallerClassNode(Invoke invoke) {
        super(invoke);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ConstantNode callerClassNode = getCallerClassNode(tool.getMetaAccess());
        if (callerClassNode != null) {
            return callerClassNode;
        }
        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        ConstantNode callerClassNode = getCallerClassNode(tool.getMetaAccess());

        if (callerClassNode != null) {
            graph().replaceFixedWithFloating(this, graph().addOrUniqueWithInputs(callerClassNode));
        } else {
            InvokeNode invoke = createInvoke();
            graph().replaceFixedWithFixed(this, invoke);
            invoke.lower(tool);
        }
    }

    /**
     * If inlining is deep enough this method returns a {@link ConstantNode} of the caller class by
     * walking the the stack.
     *
     * @param metaAccess
     * @return ConstantNode of the caller class, or null
     */
    private ConstantNode getCallerClassNode(MetaAccessProvider metaAccess) {
        if (!shouldIntrinsify(getTargetMethod())) {
            return null;
        }

        // Walk back up the frame states to find the caller at the required depth.
        FrameState state = stateAfter();

        // Cf. JVM_GetCallerClass
        // NOTE: Start the loop at depth 1 because the current frame state does
        // not include the Reflection.getCallerClass() frame.
        for (int n = 1; state != null; state = state.outerFrameState(), n++) {
            HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) state.method();
            switch (n) {
                case 0:
                    throw GraalInternalError.shouldNotReachHere("current frame state does not include the Reflection.getCallerClass frame");
                case 1:
                    // Frame 0 and 1 must be caller sensitive (see JVM_GetCallerClass).
                    if (!method.isCallerSensitive()) {
                        return null;  // bail-out; let JVM_GetCallerClass do the work
                    }
                    break;
                default:
                    if (!method.ignoredBySecurityStackWalk()) {
                        // We have reached the desired frame; return the holder class.
                        HotSpotResolvedObjectType callerClass = method.getDeclaringClass();
                        return ConstantNode.forConstant(HotSpotObjectConstant.forObject(callerClass.mirror()), metaAccess);
                    }
                    break;
            }
        }
        return null;  // bail-out; let JVM_GetCallerClass do the work
    }

}
