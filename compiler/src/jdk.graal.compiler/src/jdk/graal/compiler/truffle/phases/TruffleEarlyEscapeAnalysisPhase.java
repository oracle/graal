/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.phases;

import java.util.Map;
import java.util.function.Function;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.truffle.KnownTruffleTypes;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Implements the semantics for the CompilerDirectives.EarlyEscapeAnalysis annotation.
 */
public final class TruffleEarlyEscapeAnalysisPhase extends PartialEscapePhase {

    private final ResolvedJavaType unwrappedAnnotationType;

    public TruffleEarlyEscapeAnalysisPhase(CanonicalizerPhase canonicalizer, OptionValues options, KnownTruffleTypes truffleTypes,
                    Function<ResolvedJavaType, ResolvedJavaType> unwrapType) {
        super(false, canonicalizer, options);
        this.unwrappedAnnotationType = unwrapType.apply(truffleTypes.CompilerDirectives_EarlyEscapeAnalysis);
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected boolean matchGraph(StructuredGraph graph) {
        Map<ResolvedJavaType, AnnotationValue> declaredAnnotationValues = AnnotationValueSupport.getDeclaredAnnotationValues(graph.method());
        if (!declaredAnnotationValues.containsKey(unwrappedAnnotationType)) {
            return false;
        }
        // we do not respect the PE only option for Truffle because PE depends on escape analysis
        // semantics.
        return true;
    }
}
