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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

public abstract class AbstractAnalysisResultsBuilder {

    protected final PointsToAnalysis bb;

    /**
     * The universe used to convert analysis metadata to hosted metadata, or {@code null} if no
     * conversion should be performed.
     */
    protected final Universe converter;

    /* Caches for JavaTypeProfile with 0, 1, or more types. */
    private final JavaTypeProfile[] types0;
    private final JavaTypeProfile[] types1Null;
    private final JavaTypeProfile[] types1NonNull;
    private final Map<JavaTypeProfile, JavaTypeProfile> types;

    /* Caches for JavaMethodProfile with 0, 1, or more types. */
    private final JavaMethodProfile[] methods0;
    private final JavaMethodProfile[] methods1;
    private final Map<JavaMethodProfile, JavaMethodProfile> methods;

    protected AbstractAnalysisResultsBuilder(PointsToAnalysis bb, Universe converter) {
        this.bb = bb;
        this.converter = converter;

        types0 = new JavaTypeProfile[2];
        types1Null = new JavaTypeProfile[bb.getUniverse().getNextTypeId()];
        types1NonNull = new JavaTypeProfile[bb.getUniverse().getNextTypeId()];
        types = new HashMap<>();

        methods0 = new JavaMethodProfile[1];
        methods1 = new JavaMethodProfile[bb.getUniverse().getNextMethodId()];
        methods = new HashMap<>();
    }

    public BigBang getBigBang() {
        return bb;
    }

    public abstract StaticAnalysisResults makeOrApplyResults(AnalysisMethod method);

    public abstract JavaTypeProfile makeTypeProfile(AnalysisField field);

    protected JavaTypeProfile makeTypeProfile(TypeState typeState) {
        if (typeState == null || PointstoOptions.AnalysisSizeCutoff.getValue(bb.getOptions()) != -1 &&
                        typeState.typesCount() > PointstoOptions.AnalysisSizeCutoff.getValue(bb.getOptions())) {
            return null;
        }

        if (typeState.isEmpty()) {
            synchronized (types0) {
                return cachedTypeProfile(types0, 0, typeState);
            }
        } else if (typeState.isNull()) {
            synchronized (types0) {
                return cachedTypeProfile(types0, 1, typeState);
            }
        } else if (typeState.exactType() != null) {
            if (typeState.canBeNull()) {
                synchronized (types1Null) {
                    return cachedTypeProfile(types1Null, typeState.exactType().getId(), typeState);
                }
            } else {
                synchronized (types1NonNull) {
                    return cachedTypeProfile(types1NonNull, typeState.exactType().getId(), typeState);
                }
            }
        }
        synchronized (types) {
            JavaTypeProfile created = createTypeProfile(typeState);
            types.putIfAbsent(created, created);
            return created;
        }
    }

    private JavaTypeProfile cachedTypeProfile(JavaTypeProfile[] cache, int cacheIdx, TypeState typeState) {
        JavaTypeProfile result = cache[cacheIdx];
        if (result == null) {
            result = createTypeProfile(typeState);
            cache[cacheIdx] = result;
        }
        return result;
    }

    private JavaTypeProfile createTypeProfile(TypeState typeState) {
        double probability = 1d / typeState.typesCount();
        JavaTypeProfile.ProfiledType[] pitems = typeState.typesStream()
                        .map(analysisType -> converter == null ? analysisType : converter.lookup(analysisType))
                        .sorted()
                        .map(type -> new JavaTypeProfile.ProfiledType(type, probability))
                        .toArray(JavaTypeProfile.ProfiledType[]::new);

        return new JavaTypeProfile(TriState.get(typeState.canBeNull()), 0, pitems);
    }

    protected JavaMethodProfile makeMethodProfile(Collection<AnalysisMethod> callees) {
        if (PointstoOptions.AnalysisSizeCutoff.getValue(bb.getOptions()) != -1 && callees.size() > PointstoOptions.AnalysisSizeCutoff.getValue(bb.getOptions())) {
            return null;
        }
        if (callees.isEmpty()) {
            synchronized (methods0) {
                return cachedMethodProfile(methods0, 0, callees);
            }
        } else if (callees.size() == 1) {
            synchronized (methods1) {
                return cachedMethodProfile(methods1, callees.iterator().next().getId(), callees);
            }
        }
        synchronized (methods) {
            JavaMethodProfile created = createMethodProfile(callees);
            methods.putIfAbsent(created, created);
            return created;
        }
    }

    private JavaMethodProfile cachedMethodProfile(JavaMethodProfile[] cache, int cacheIdx, Collection<AnalysisMethod> callees) {
        JavaMethodProfile result = cache[cacheIdx];
        if (result == null) {
            result = createMethodProfile(callees);
            cache[cacheIdx] = result;
        }
        return result;
    }

    private JavaMethodProfile createMethodProfile(Collection<AnalysisMethod> callees) {
        JavaMethodProfile.ProfiledMethod[] pitems = new JavaMethodProfile.ProfiledMethod[callees.size()];
        double probability = 1d / pitems.length;

        int idx = 0;
        for (AnalysisMethod aMethod : callees) {
            ResolvedJavaMethod convertedMethod = converter == null ? aMethod : converter.lookup(aMethod);
            pitems[idx++] = new JavaMethodProfile.ProfiledMethod(convertedMethod, probability);
        }
        return new JavaMethodProfile(0, pitems);
    }
}
