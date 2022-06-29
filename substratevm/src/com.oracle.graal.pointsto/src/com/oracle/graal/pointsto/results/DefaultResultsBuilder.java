/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.results;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.TriState;

/**
 * Implementation of the ResultsBuilder providing no feedback for optimizations. Used in
 * Reachability Analysis.
 */
public class DefaultResultsBuilder extends AbstractAnalysisResultsBuilder {
    public DefaultResultsBuilder(BigBang bb, Universe converter) {
        super(bb, converter);
    }

    @Override
    public StaticAnalysisResults makeOrApplyResults(AnalysisMethod method) {
        return StaticAnalysisResults.NO_RESULTS;
    }

    @Override
    public JavaTypeProfile makeTypeProfile(AnalysisField field) {
        return new JavaTypeProfile(TriState.UNKNOWN, 1, new JavaTypeProfile.ProfiledType[0]);
    }
}
