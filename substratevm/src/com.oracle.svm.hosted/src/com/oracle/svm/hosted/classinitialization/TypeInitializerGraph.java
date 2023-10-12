/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

import com.oracle.graal.pointsto.classinitialization.AbstractTypeInitializerGraph;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;

import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.nativeimage.AnnotationAccess;

/**
 * Specify the class initialization safety rules only available for native image generation.
 *
 * NOTE: the dependency between methods and type initializers is maintained by the
 * {@link SubstrateClassInitializationPlugin} that emits {@link EnsureClassInitializedNode} for
 * every load, store, call, and instantiation in the bytecode. These dependencies are collected in
 * {@link SVMHost#getInitializedClasses}.
 */
final class TypeInitializerGraph extends AbstractTypeInitializerGraph {
    private final ProvenSafeClassInitializationSupport classInitializationSupport;

    TypeInitializerGraph(ProvenSafeClassInitializationSupport classInitializationSupport, AnalysisUniverse universe) {
        super(universe);
        this.classInitializationSupport = classInitializationSupport;
    }

    /**
     * A type initializer is initially unsafe only if it was marked by the user as such.
     */
    private Safety initialTypeInitializerSafety(AnalysisType t) {
        return classInitializationSupport.specifiedInitKindFor(t.getJavaClass()) == InitKind.BUILD_TIME || classInitializationSupport.canBeProvenSafe(t.getJavaClass())
                        ? Safety.SAFE
                        : Safety.UNSAFE;
    }

    @Override
    protected Safety initialMethodSafety(AnalysisMethod m) {
        return isSubstitutedMethod(m) ? Safety.UNSAFE : super.initialMethodSafety(m);
    }

    private boolean isSubstitutedMethod(AnalysisMethod m) {
        return classInitializationSupport.maybeInitializeAtBuildTime(m.getDeclaringClass()) && m.getWrapped() instanceof SubstitutionMethod;
    }

    @Override
    protected void initTypeSafety(AnalysisType t) {
        ResolvedJavaType rt = t.getWrapped();
        boolean isSubstituted = false;
        if (rt instanceof SubstitutionType) {
            SubstitutionType substitutionType = (SubstitutionType) rt;
            isSubstituted = AnnotationAccess.isAnnotationPresent(substitutionType, Substitute.class) || AnnotationAccess.isAnnotationPresent(substitutionType, Delete.class);
        }
        types.put(t, isSubstituted ? Safety.UNSAFE : initialTypeInitializerSafety(t));
    }
}
