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
package com.oracle.svm.interpreter;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.hub.CremaSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CremaSupportImpl implements CremaSupport {

    @Override
    public ResolvedJavaType createInterpreterType(DynamicHub hub, ResolvedJavaType type) {
        BuildTimeInterpreterUniverse btiUniverse = BuildTimeInterpreterUniverse.singleton();
        AnalysisType analysisType = (AnalysisType) type;
        AnalysisUniverse analysisUniverse = analysisType.getUniverse();

        /* query type from universe, maybe already exists (due to method creation) */
        InterpreterResolvedJavaType interpreterType = btiUniverse.getOrCreateType(analysisType);

        ResolvedJavaMethod[] declaredMethods = interpreterType.getDeclaredMethods();
        assert declaredMethods == null || declaredMethods == InterpreterResolvedJavaType.NO_METHODS : "should only be set once";

        if (analysisType.isPrimitive()) {
            return interpreterType;
        }

        List<InterpreterResolvedJavaMethod> methods = new ArrayList<>();

        for (ResolvedJavaMethod wrappedMethod : analysisType.getWrapped().getDeclaredMethods(false)) {
            if (!analysisUniverse.hostVM().platformSupported(wrappedMethod)) {
                /* ignore e.g. hosted methods */
                continue;
            }

            AnalysisMethod analysisMethod;
            try {
                analysisMethod = analysisUniverse.lookup(wrappedMethod);
            } catch (DeletedElementException e) {
                /* deleted via substitution */
                continue;
            }
            InterpreterResolvedJavaMethod method = btiUniverse.getOrCreateMethod(analysisMethod);
            method.setNativeEntryPoint(new MethodPointer(analysisMethod));
            methods.add(method);
        }

        ((InterpreterResolvedObjectType) interpreterType).setDeclaredMethods(methods.toArray(new InterpreterResolvedJavaMethod[0]));

        return interpreterType;
    }
}
