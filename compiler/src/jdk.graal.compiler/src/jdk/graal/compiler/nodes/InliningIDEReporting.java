/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.ide.IDEReport.getFilePath;
import static jdk.graal.compiler.ide.IDEReport.getInliningTrace;
import static jdk.graal.compiler.ide.IDEReport.getLineNumber;
import static jdk.graal.compiler.ide.IDEReport.runIfEnabled;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.ide.IDEReport;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Provides methods for reporting inlining decisions to the IDE.
 */
public final class InliningIDEReporting {

    private InliningIDEReporting() {
    }

    /**
     * Reports inlining of a method to the IDE.
     *
     * @param srcPos the source position of the inlined method call
     * @param callee the method being inlined
     * @param phase the phase during which the inlining occurred
     */
    public static void reportInlining(NodeSourcePosition srcPos, ResolvedJavaMethod callee, String phase) {
        var calleeName = callee.getName();
        if (calleeName == null){
            return;
        }
        IDEReport.runIfEnabled(ideReport -> {
            var calleeDeclFilePath = getFilePath(callee.getDeclaringClass());
            if (calleeDeclFilePath != null) {
                ideReport.saveMethodInlined(calleeDeclFilePath, callee.getDeclaringClass().toJavaName(), calleeName, callee.getSignature().toMethodDescriptor());
            }
        });
        if (srcPos == null || srcPos.getMethod() == null) {
            return;
        }
        runIfEnabled(ideReport -> {
            var invocationSiteFilePath = getFilePath(srcPos.getMethod().getDeclaringClass());
            if (invocationSiteFilePath != null) {
                var className = srcPos.getMethod().getDeclaringClass().toJavaName();
                var line = getLineNumber(srcPos);
                ideReport.saveLineReport(invocationSiteFilePath, className, line, "Call to " + calleeName + " is inlined by phase " + phase, getInliningTrace(srcPos));
            }
        });
    }

}
