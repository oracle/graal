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
package com.oracle.svm.hosted;

import java.security.CodeSource;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import jdk.graal.compiler.options.Option;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.substitute.SubstitutionField;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticallyRegisteredFeature
public class SubstitutionReportFeature implements InternalFeature {

    static class Options {
        @Option(help = "Report performed substitutions")//
        public static final HostedOptionKey<Boolean> ReportPerformedSubstitutions = new HostedOptionKey<>(false);
    }

    private final boolean enabled = Options.ReportPerformedSubstitutions.getValue();
    private final Map<String, Substitutions> substitutions = new TreeMap<>();

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return enabled;
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        FeatureImpl.AfterAnalysisAccessImpl accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        findSubstitutedTypes(accessImpl);
        findSubstitutedMethods(accessImpl);
        findSubstitutedFields(accessImpl);
        reportSubstitutions();
    }

    private void findSubstitutedTypes(FeatureImpl.AfterAnalysisAccessImpl access) {
        for (AnalysisType type : access.getUniverse().getTypes()) {
            if (type.isReachable() && !type.isArray()) {
                ResolvedJavaType t = type.getWrapped();
                if (t instanceof SubstitutionType) {
                    SubstitutionType subType = (SubstitutionType) t;
                    if (subType.isUserSubstitution()) {
                        String jarLocation = getTypeClassFileLocation(subType.getAnnotated());
                        substitutions.putIfAbsent(jarLocation, new Substitutions());
                        substitutions.get(jarLocation).addType(subType);
                    }
                }
            }
        }
    }

    private void findSubstitutedMethods(FeatureImpl.AfterAnalysisAccessImpl access) {
        for (AnalysisMethod method : access.getUniverse().getMethods()) {
            if (method.wrapped instanceof SubstitutionMethod) {
                SubstitutionMethod subMethod = (SubstitutionMethod) method.wrapped;
                if (subMethod.isUserSubstitution()) {
                    String jarLocation = getTypeClassFileLocation(subMethod.getAnnotated().getDeclaringClass());
                    substitutions.putIfAbsent(jarLocation, new Substitutions());
                    substitutions.get(jarLocation).addMethod(subMethod);
                }
            }
        }
    }

    private void findSubstitutedFields(FeatureImpl.AfterAnalysisAccessImpl access) {
        for (AnalysisField field : access.getUniverse().getFields()) {
            if (field.wrapped instanceof SubstitutionField) {
                SubstitutionField subField = (SubstitutionField) field.wrapped;
                if (subField.isUserSubstitution()) {
                    String jarLocation = getTypeClassFileLocation(subField.getAnnotated().getDeclaringClass());
                    substitutions.putIfAbsent(jarLocation, new Substitutions());
                    substitutions.get(jarLocation).addField(subField);
                }
            }
        }
    }

    private void reportSubstitutions() {
        ReportUtils.report("substitutions performed by native-image", SubstrateOptions.reportsPath(), "substitutions", "csv", pw -> {
            pw.println("location, category (type/method/field), original, annotated");
            for (Map.Entry<String, Substitutions> g : substitutions.entrySet()) {
                for (Map.Entry<ResolvedJavaType, ResolvedJavaType> e : g.getValue().getSubstitutedTypes().entrySet()) {
                    pw.println(formatSubstitution(g.getKey(), "type", e.getKey(), e.getValue(), SubstitutionReportFeature::formatType));
                }
                for (Map.Entry<ResolvedJavaMethod, ResolvedJavaMethod> e : g.getValue().getSubstitutedMethods().entrySet()) {
                    pw.println(formatSubstitution(g.getKey(), "method", e.getKey(), e.getValue(), SubstitutionReportFeature::formatMethod));
                }
                for (Map.Entry<ResolvedJavaField, ResolvedJavaField> e : g.getValue().getSubstitutedFields().entrySet()) {
                    pw.println(formatSubstitution(g.getKey(), "field", e.getKey(), e.getValue(), SubstitutionReportFeature::formatField));
                }
            }
        });
    }

    private static String formatType(ResolvedJavaType t) {
        return t.toJavaName(true);
    }

    private static String formatMethod(ResolvedJavaMethod method) {
        return method.format("%H#%n");
    }

    private static String formatField(ResolvedJavaField field) {
        return field.format("%H.%n");
    }

    private static String getTypeClassFileLocation(ResolvedJavaType type) {
        Class<?> annotatedClass = OriginalClassProvider.getJavaClass(type);
        CodeSource source = annotatedClass.getProtectionDomain().getCodeSource();
        return source == null ? "unknown" : source.getLocation().toString();
    }

    private static <T> String formatSubstitution(String jar, String type, T original, T annotated, Function<T, String> formatter) {
        return '\'' + jar + "'," + type + ',' + formatter.apply(original) + ',' + formatter.apply(annotated);
    }

    private static class Substitutions {
        private final Map<ResolvedJavaType, ResolvedJavaType> substitutedTypes = new TreeMap<>(Comparator.comparing(SubstitutionReportFeature::formatType));
        private final Map<ResolvedJavaMethod, ResolvedJavaMethod> substitutedMethods = new TreeMap<>(Comparator.comparing(SubstitutionReportFeature::formatMethod));
        private final Map<ResolvedJavaField, ResolvedJavaField> substitutedFields = new TreeMap<>(Comparator.comparing(SubstitutionReportFeature::formatField));

        public void addType(SubstitutionType type) {
            substitutedTypes.put(type.getOriginal(), type.getAnnotated());
        }

        public void addMethod(SubstitutionMethod method) {
            substitutedMethods.put(method.getOriginal(), method.getAnnotated());
        }

        public void addField(SubstitutionField field) {
            substitutedFields.put(field.getOriginal(), field.getAnnotated());
        }

        public Map<ResolvedJavaType, ResolvedJavaType> getSubstitutedTypes() {
            return substitutedTypes;
        }

        public Map<ResolvedJavaMethod, ResolvedJavaMethod> getSubstitutedMethods() {
            return substitutedMethods;
        }

        public Map<ResolvedJavaField, ResolvedJavaField> getSubstitutedFields() {
            return substitutedFields;
        }

    }
}
