package com.oracle.svm.hosted;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.substitute.SubstitutionField;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.hosted.Feature;

import java.security.CodeSource;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

@AutomaticFeature
public class SubstitutionReportFeature implements Feature {

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
        AnalysisUniverse universe = access.getUniverse();
        for (AnalysisType type : universe.getTypes()) {
            if (type.isReachable() && !type.isArray()) {
                ResolvedJavaType t = type.getWrappedWithoutResolve();
                if (t instanceof SubstitutionType) {
                    SubstitutionType subType = (SubstitutionType) t;
                    if (subType.isUserSubstitution()) {
                        String jarLocation = getJarLocation(subType.getAnnotated());
                        substitutions.putIfAbsent(jarLocation, new Substitutions());
                        substitutions.get(jarLocation).addType(subType);
                    }
                }
            }
        }
    }

    private void findSubstitutedMethods(FeatureImpl.AfterAnalysisAccessImpl access) {
        AnalysisUniverse universe = access.getUniverse();
        for (AnalysisMethod method : universe.getMethods()) {
            if (method.wrapped instanceof SubstitutionMethod) {
                SubstitutionMethod subMethod = (SubstitutionMethod) method.wrapped;
                if (subMethod.isUserSubstitution()) {
                    String jarLocation = getJarLocation(subMethod.getAnnotated().getDeclaringClass());
                    substitutions.putIfAbsent(jarLocation, new Substitutions());
                    substitutions.get(jarLocation).addMethod(subMethod);
                }
            }
        }
    }

    private void findSubstitutedFields(FeatureImpl.AfterAnalysisAccessImpl access) {
        AnalysisUniverse universe = access.getUniverse();
        for (AnalysisField field : universe.getFields()) {
            if (field.wrapped instanceof SubstitutionField) {
                SubstitutionField subField = (SubstitutionField) field.wrapped;
                if (subField.isUserSubstitution()) {
                    String jarLocation = getJarLocation(subField.getAnnotated().getDeclaringClass());
                    substitutions.putIfAbsent(jarLocation, new Substitutions());
                    substitutions.get(jarLocation).addField(subField);
                }
            }
        }
    }

    private void reportSubstitutions() {
        ReportUtils.report("substitutions performed by native-image","reports", "substitutions", "csv", pw -> {
            pw.println("location , category (type/method/field) , original , annotated");
            for (Map.Entry<String, Substitutions> g : substitutions.entrySet()) {
                for (Map.Entry<ResolvedJavaType, ResolvedJavaType> e : g.getValue().getSubstitutedTypes().entrySet()) {
                    pw.println(formatSubstitution(g.getKey(), "type", e.getKey(), e.getValue(), t -> t.toJavaName(true)));
                }
                for (Map.Entry<ResolvedJavaMethod, ResolvedJavaMethod> e : g.getValue().getSubstitutedMethods().entrySet()) {
                    pw.println(formatSubstitution(g.getKey(), "method", e.getKey(), e.getValue(), this::formatMethod));
                }
                for (Map.Entry<ResolvedJavaField, ResolvedJavaField> e : g.getValue().getSubstitutedFields().entrySet()) {
                    pw.println(formatSubstitution(g.getKey(), "field", e.getKey(), e.getValue(), this::formatField));
                }
            }
        });
    }

    private String getJarLocation(ResolvedJavaType type) {
        Class<?> annotatedClass = OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), type);
        CodeSource source = annotatedClass.getProtectionDomain().getCodeSource();
        return source == null ? "unknown" : source.getLocation().toString();
    }

    private String formatMethod(ResolvedJavaMethod method) {
        return method.getDeclaringClass().toJavaName(true) + "::" + method.getName();
    }

    private String formatField(ResolvedJavaField field) {
        return field.getDeclaringClass().toJavaName(true) + "#" + field.getName();
    }

    private <T> String formatSubstitution(String jar, String type, T original, T annotated, Function<T, String> formatter) {
        return '\'' + jar + "'," + type + ',' + formatter.apply(original) + ',' + formatter.apply(annotated);
    }

    private static class Substitutions {
        private final Map<ResolvedJavaType, ResolvedJavaType> substitutedTypes = new TreeMap<>(new ResolvedJavaTypeComparator());
        private final Map<ResolvedJavaMethod, ResolvedJavaMethod> substitutedMethods = new TreeMap<>(new ResolvedJavaMethodComparator());
        private final Map<ResolvedJavaField, ResolvedJavaField> substitutedFields = new TreeMap<>(new ResolvedJavaFieldComparator());

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

        private static class ResolvedJavaTypeComparator implements Comparator<ResolvedJavaType> {
            @Override
            public int compare(ResolvedJavaType t1, ResolvedJavaType t2) {
                return t1.toJavaName(true).compareTo(t2.toJavaName(true));
            }
        }

        private static class ResolvedJavaMethodComparator implements Comparator<ResolvedJavaMethod> {
            @Override
            public int compare(ResolvedJavaMethod m1, ResolvedJavaMethod m2) {
                int cmp = m1.getDeclaringClass().toJavaName(true).compareTo(m2.getDeclaringClass().toJavaName(true));
                return cmp != 0 ? cmp : m1.getName().compareTo(m2.getName());
            }
        }

        private static class ResolvedJavaFieldComparator implements Comparator<ResolvedJavaField> {
            @Override
            public int compare(ResolvedJavaField f1, ResolvedJavaField f2) {
                int cmp = f1.getDeclaringClass().toJavaName(true).compareTo(f2.getDeclaringClass().toJavaName(true));
                return cmp != 0 ? cmp : f1.getName().compareTo(f2.getName());
            }
        }
    }
}
