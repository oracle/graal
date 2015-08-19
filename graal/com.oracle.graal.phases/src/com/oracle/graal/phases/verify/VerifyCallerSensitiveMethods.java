/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.verify;

import jdk.internal.jvmci.meta.*;
import sun.reflect.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * Verifies a method is annotated with {@link CallerSensitive} iff it calls
 * {@link Reflection#getCallerClass()}.
 */
public class VerifyCallerSensitiveMethods extends VerifyPhase<PhaseContext> {

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        Invoke invoke = callsReflectionGetCallerClass(graph, context);
        if (invoke != null) {
            if (graph.method().getAnnotation(CallerSensitive.class) == null) {
                StackTraceElement e = graph.method().asStackTraceElement(invoke.bci());
                throw new VerificationError(String.format("%s: method that calls Reflection.getCallerClass() must be annotated with @CallerSensitive", e));
            }

        } else if (graph.method().getAnnotation(CallerSensitive.class) != null) {
            throw new VerificationError(String.format("%s: method annotated with @CallerSensitive does not call Reflection.getCallerClass()", graph.method().format("%H.%n(%p)")));
        }
        return true;
    }

    private static Invoke callsReflectionGetCallerClass(StructuredGraph graph, PhaseContext context) {
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            ResolvedJavaType reflectionType = context.getMetaAccess().lookupJavaType(Reflection.class);
            if (callee.getDeclaringClass().equals(reflectionType)) {
                if (callee.getName().equals("getCallerClass")) {
                    return t.invoke();
                }
            }
        }
        return null;
    }
}
