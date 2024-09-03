/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo
public abstract class ReflectionGetCallerClassNode extends MacroWithExceptionNode implements Canonicalizable {

    public static final NodeClass<ReflectionGetCallerClassNode> TYPE = NodeClass.create(ReflectionGetCallerClassNode.class);

    protected ReflectionGetCallerClassNode(NodeClass<? extends ReflectionGetCallerClassNode> c, MacroNode.MacroParams p) {
        super(c, p);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ConstantNode callerClassNode = getCallerClassNode(tool.getMetaAccess(), tool.getConstantReflection());
        if (callerClassNode != null) {
            return callerClassNode;
        }
        return this;
    }

    /**
     * If inlining is deep enough this method returns a {@link ConstantNode} of the caller class by
     * walking the stack.
     *
     * @param metaAccess
     * @return ConstantNode of the caller class, or null
     */
    private ConstantNode getCallerClassNode(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        // Walk back up the frame states to find the caller at the required depth.
        FrameState state = stateAfter();

        // Cf. JVM_GetCallerClass
        // NOTE: Start the loop at depth 1 because the current frame state does
        // not include the Reflection.getCallerClass() frame.
        for (int n = 1; state != null; state = state.outerFrameState(), n++) {
            ResolvedJavaMethod method = state.getMethod();
            switch (n) {
                case 0:
                    throw GraalError.shouldNotReachHere("current frame state does not include the Reflection.getCallerClass frame"); // ExcludeFromJacocoGeneratedReport
                case 1:
                    // Frame 0 and 1 must be caller sensitive (see JVM_GetCallerClass).
                    if (!isCallerSensitive(method)) {
                        return null;  // bail-out; let JVM_GetCallerClass do the work
                    }
                    break;
                default:
                    if (!ignoredBySecurityStackWalk(metaAccess, method)) {
                        // We have reached the desired frame; return the holder class.
                        ResolvedJavaType callerClass = method.getDeclaringClass();
                        return ConstantNode.forConstant(constantReflection.asJavaClass(callerClass), metaAccess);
                    }
                    break;
            }
        }
        return null;  // bail-out; let JVM_GetCallerClass do the work
    }

    protected abstract boolean isCallerSensitive(ResolvedJavaMethod method);

    protected abstract boolean ignoredBySecurityStackWalk(MetaAccessProvider metaAccess, ResolvedJavaMethod method);
}
