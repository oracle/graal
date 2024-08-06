/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.ClassInclusionPolicy;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.TimerCollection;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class StandalonePointsToAnalysis extends PointsToAnalysis {
    private final Set<AnalysisMethod> addedClinits = ConcurrentHashMap.newKeySet();

    public StandalonePointsToAnalysis(OptionValues options, AnalysisUniverse universe, HostVM hostVM, AnalysisMetaAccess metaAccess, SnippetReflectionProvider snippetReflectionProvider,
                    ConstantReflectionProvider constantReflectionProvider, WordTypes wordTypes, DebugContext debugContext, TimerCollection timerCollection, ClassInclusionPolicy classInclusionPolicy) {
        super(options, universe, hostVM, metaAccess, snippetReflectionProvider, constantReflectionProvider, wordTypes, new UnsupportedFeatures(), debugContext, timerCollection, classInclusionPolicy);
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        // No need to keep method graphs for standalone analysis.
        universe.getMethods().forEach(AnalysisMethod::clearAnalyzedGraph);
        universe.getMethods().clear();
        universe.getFields().clear();
        addedClinits.clear();
    }

    @Override
    public void initializeMetaData(AnalysisType type) {

    }

    @Override
    public void onTypeReachable(AnalysisType type) {
        AnalysisMethod clinitMethod = type.getClassInitializer();
        if (clinitMethod != null && !addedClinits.contains(clinitMethod)) {
            addRootMethod(clinitMethod, true, "Class initializer added onTypeReachable, in " + StandalonePointsToAnalysis.class);
            addedClinits.add(clinitMethod);
        }
    }
}
