/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.LinkedHashSet;
import java.util.Set;

// TODO: Handle FlightRecorder enabling/disabling.
final class FlightRecorderInstrumentation {

    private static final Set<InstrumentedMethodPattern> instrumentedMethodPatterns = createInstrumentedPatterns();

    private static Set<InstrumentedMethodPattern> createInstrumentedPatterns() {
        Set<InstrumentedMethodPattern> patterns = new LinkedHashSet<>();
        patterns.add(new InstrumentedMethodPattern("begin", "()V"));
        patterns.add(new InstrumentedMethodPattern("commit", "()V"));
        patterns.add(new InstrumentedMethodPattern("end", "()V"));
        patterns.add(new InstrumentedMethodPattern("isEnabled", "()Z"));
        patterns.add(new InstrumentedMethodPattern("set", "(ILjava/lang/Object;)V"));
        patterns.add(new InstrumentedMethodPattern("shouldCommit", "()Z"));
        return patterns;
    }

    private FlightRecorderInstrumentation() {
    }

    static boolean isInstrumented(ResolvedJavaMethod method, KnownTruffleTypes types) {
        if (("traceThrowable".equals(method.getName()) || "traceError".equals(method.getName())) && "Ljdk/jfr/internal/instrument/ThrowableTracer;".equals(method.getDeclaringClass().getName())) {
            return true;
        }

        // Fast check, the JFR instrumented methods are marked as synthetic.
        if (!method.isSynthetic() || method.isBridge() || method.isStatic()) {
            return false;
        }

        if (!instrumentedMethodPatterns.contains(InstrumentedMethodPattern.forJavaMethod(method))) {
            return false;
        }

        ResolvedJavaType methodOwner = method.getDeclaringClass();
        return types.Event != null && types.Event.isAssignableFrom(methodOwner);
    }

    private record InstrumentedMethodPattern(String name, String signature) {

        static InstrumentedMethodPattern forJavaMethod(JavaMethod method) {
            return new InstrumentedMethodPattern(method.getName(), method.getSignature().toMethodDescriptor());
        }
    }
}
