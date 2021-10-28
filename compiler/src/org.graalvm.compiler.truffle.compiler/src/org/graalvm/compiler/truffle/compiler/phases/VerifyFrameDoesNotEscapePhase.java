/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases;

import org.graalvm.compiler.graph.GraalGraphError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.truffle.compiler.nodes.frame.NewFrameNode;

/**
 * Compiler phase for verifying that the Truffle virtual frame does not escape and can therefore be
 * escape analyzed.
 */
public class VerifyFrameDoesNotEscapePhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (NewFrameNode virtualFrame : graph.getNodes(NewFrameNode.TYPE)) {
            for (MethodCallTargetNode callTarget : virtualFrame.usages().filter(MethodCallTargetNode.class)) {
                if (callTarget.invoke() != null) {
                    String properties = callTarget.getDebugProperties().toString();
                    String arguments = callTarget.arguments().toString();
                    Throwable exception = new GraalGraphError("Frame escapes at: %s#%s\nproperties:%s\narguments: %s", callTarget, callTarget.targetMethod(), properties, arguments);
                    throw GraphUtil.approxSourceException(callTarget, exception);
                }
            }
        }
    }
}
