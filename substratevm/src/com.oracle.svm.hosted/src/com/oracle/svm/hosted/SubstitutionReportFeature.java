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
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.options.Option;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/// Feature that reports substitutions discovered during analysis: all with [Options#ReportPerformedSubstitutions],
/// or only user-authored with [Options#ReportPerformedUserSubstitutions].
/// > Note: The feature does not report `@Delete`, `@RecomputeFieldValue`, and `@InjectAccessors` annotations.
///
/// When enabled via [Options#ReportPerformedSubstitutions] or [Options#ReportPerformedUserSubstitutions],
/// this feature scans the analysis universe after the analysis phase completes and collects substitutions:
/// - type substitutions ([SubstitutionType])
/// - method substitutions ([SubstitutionMethod])
/// - field substitutions ([SubstitutionField])
///
/// The results are emitted as a CSV report using [ReportUtils] under the [SubstrateOptions#reportsPath()] directory.
/// The report is named:
/// `substitutions_<date>_<time>.csv` with the following columns:
/// location, category (type/method/field), original, annotated
/// where:
/// - location: the originating code source (e.g., JAR URL) of the annotated (replacement) element
/// - category: one of "type", "method", "field"
/// - original: the original element being substituted
/// - annotated: the annotated replacement element
@AutomaticallyRegisteredFeature
public class SubstitutionReportFeature implements InternalFeature {

    static class Options {
        @Option(help = "Write a CSV report of all substitutions discovered during analysis to reports/substitutions<date>-<time>.csv. Columns: location, category (type/method/field), original, annotated.") //
        public static final HostedOptionKey<Boolean> ReportPerformedSubstitutions = new HostedOptionKey<>(false);

        @Option(help = "Write a CSV report of user-authored substitutions originating from the application classpath or module path to reports/substitutions<date>-<time>.csv. Columns: location, category (type/method/field), original, annotated.") //
        public static final HostedOptionKey<Boolean> ReportPerformedUserSubstitutions = new HostedOptionKey<>(false);
    }

    private final boolean enabled = Options.ReportPerformedSubstitutions.getValue() || Options.ReportPerformedUserSubstitutions.getValue();

    /// Aggregated substitutions grouped by the [CodeSource] location (typically the JAR URL)
    /// of the annotated (replacement) elements.
    private final Map<String, Substitutions> substitutions = new TreeMap<>();

    /// Only participate in the image build when the reporting option is enabled.
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return enabled;
    }

    /// After analysis completes, collect all user-provided substitutions and write the CSV report.
    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        FeatureImpl.AfterAnalysisAccessImpl accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        findSubstitutedTypes(accessImpl);
        findSubstitutedMethods(accessImpl);
        findSubstitutedFields(accessImpl);
        reportSubstitutions();
    }

    /// Scans all reachable, non-array [AnalysisType] types and records those that are user
    /// substitutions.
    /// A [SubstitutionType] represents an annotated replacement for an original type.
    private void findSubstitutedTypes(FeatureImpl.AfterAnalysisAccessImpl access) {
        for (AnalysisType type : access.getUniverse().getTypes()) {
            if (type.isReachable() && !type.isArray()) {
                ResolvedJavaType t = type.getWrapped();
                if (t instanceof SubstitutionType substType) {
                    // Only report substitutions authored by users (filter out internal ones).
                    if (shouldReport(substType.isUserSubstitution())) {
                        String jarLocation = getTypeClassFileLocation(substType.getAnnotated());
                        substitutions.putIfAbsent(jarLocation, new Substitutions());
                        substitutions.get(jarLocation).addType(substType);
                    }
                }
            }
        }
    }

    /// Scans all [AnalysisMethod] methods in the analysis universe and records those that are
    /// user substitutions.
    /// A [SubstitutionMethod] holds both original and annotated (replacement) methods.
    private void findSubstitutedMethods(FeatureImpl.AfterAnalysisAccessImpl access) {
        for (AnalysisMethod method : access.getUniverse().getMethods()) {
            if (method.wrapped instanceof SubstitutionMethod substMethod) {
                if (shouldReport(substMethod.isUserSubstitution())) {
                    String jarLocation = getTypeClassFileLocation(substMethod.getAnnotated().getDeclaringClass());
                    substitutions.putIfAbsent(jarLocation, new Substitutions());
                    substitutions.get(jarLocation).addMethod(substMethod);
                }
            }
        }
    }

    /// Scans all [AnalysisField] fields in the analysis universe and records those that are
    /// user substitutions.
    /// A [SubstitutionField] holds both original and annotated (replacement) fields.
    private void findSubstitutedFields(FeatureImpl.AfterAnalysisAccessImpl access) {
        for (AnalysisField field : access.getUniverse().getFields()) {
            if (field.wrapped instanceof SubstitutionField substField) {
                if (shouldReport(substField.isUserSubstitution())) {
                    String jarLocation = getTypeClassFileLocation(substField.getAnnotated().getDeclaringClass());
                    substitutions.putIfAbsent(jarLocation, new Substitutions());
                    substitutions.get(jarLocation).addField(substField);
                }
            }
        }
    }

    /// Emits the CSV report with one row per discovered substitution.
    /// The output file will be created under [SubstrateOptions#reportsPath()] with the name
    /// `substitutions<date>_<time>.csv`. Rows are grouped by code source location and sorted using
    /// human-readable formatting for types, methods, and fields (see
    /// [#formatType(ResolvedJavaType)], [#formatMethod(ResolvedJavaMethod)],
    /// [#formatField(ResolvedJavaField)]).
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

    private static boolean shouldReport(boolean userSubstitution) {
        return userSubstitution || Options.ReportPerformedSubstitutions.getValue();
    }

    /// Fully-qualified type representation.
    private static String formatType(ResolvedJavaType t) {
        return t.toJavaName(true);
    }

    /// Human-readable method representation.
    /// Format: `pkg.Class#methodName`
    private static String formatMethod(ResolvedJavaMethod method) {
        return method.format("%H#%n");
    }

    /// Human-readable field representation.
    /// Format: `pkg.Class.fieldName`
    private static String formatField(ResolvedJavaField field) {
        return field.format("%H.%n");
    }

    /// Determines the [CodeSource] (typically the JAR URL) that contains the given type (via
    /// [OriginalClassProvider]).
    /// Falls back to "unknown" when no code source is available (e.g., dynamically defined
    /// classes).
    private static String getTypeClassFileLocation(ResolvedJavaType type) {
        Class<?> annotatedClass = OriginalClassProvider.getJavaClass(type);
        CodeSource source = annotatedClass.getProtectionDomain().getCodeSource();
        return source == null || source.getLocation() == null ? "unknown" : source.getLocation().toString();
    }

    /// Formats a single CSV row representing one substitution. The JAR location is quoted to guard
    /// against commas in URLs; other columns are formatted using the provided formatter.
    private static <T> String formatSubstitution(String jar, String type, T original, T annotated, Function<T, String> formatter) {
        return '\'' + jar + "'," + type + ',' + formatter.apply(original) + ',' + formatter.apply(annotated);
    }

    /// Container for collected substitutions for a single code source location.
    ///
    /// Maps are keyed by the original element and map to the annotated (replacement) element.
    /// TreeMaps are used with comparators based on the human-readable formatters (see
    /// [#formatType(ResolvedJavaType)], [#formatMethod(ResolvedJavaMethod)],
    /// [#formatField(ResolvedJavaField)]) so the output
    /// remains stable and easy to consume.
    private static final class Substitutions {
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
