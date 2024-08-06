/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hosted.runtimecompilation;

import static com.oracle.svm.common.meta.MultiMethod.ORIGINAL_METHOD;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class RuntimeCompiledMethod {
    final AnalysisMethod runtimeMethod;
    final AnalysisMethod originalMethod;

    /**
     * Collection of all methods inlined into this method. All methods contained here are of the
     * {@link MultiMethod#ORIGINAL_METHOD} type.
     */
    final Collection<AnalysisMethod> inlinedMethods;

    RuntimeCompiledMethod(AnalysisMethod runtimeMethod, Collection<AnalysisMethod> inlinedMethods) {
        this.runtimeMethod = runtimeMethod;
        assert SubstrateCompilationDirectives.isRuntimeCompiledMethod(runtimeMethod) : runtimeMethod;
        this.originalMethod = runtimeMethod.getMultiMethod(ORIGINAL_METHOD);
        assert originalMethod != null;
        this.inlinedMethods = inlinedMethods;
    }

    public AnalysisMethod getRuntimeMethod() {
        return runtimeMethod;
    }

    public AnalysisMethod getOriginalMethod() {
        return originalMethod;
    }

    public Collection<AnalysisMethod> getInlinedMethods() {
        return inlinedMethods;
    }

    public Collection<ResolvedJavaMethod> getInvokeTargets() {
        List<ResolvedJavaMethod> targets = new ArrayList<>();
        for (var invoke : runtimeMethod.getInvokes()) {
            if (invoke.isDeoptInvokeTypeFlow()) {
                // deopt invoke type flows are not real targets
                continue;
            }
            targets.add(invoke.getTargetMethod());
        }
        return targets;
    }
}
