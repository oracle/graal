/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.processor;

import static javax.lang.model.type.TypeKind.VOID;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import jdk.graal.compiler.processor.AbstractProcessor;

/**
 * Processor for the {@code jdk.graal.compiler.lir.GenerateStub} and
 * {@code jdk.graal.compiler.lir.GeneratedStubsHolder} annotation.
 */
public class IntrinsicStubProcessor extends AbstractProcessor {

    enum TargetVM {
        hotspot,
        substrate
    }

    enum Arch {
        AMD64,
        AArch64,
    }

    private static final String NODE_INTRINSIC_CLASS_NAME = "jdk.graal.compiler.graph.Node.NodeIntrinsic";
    private static final String GENERATE_STUB_CLASS_NAME = "jdk.graal.compiler.lir.GenerateStub";
    private static final String GENERATE_STUB_DEFAULT_CLASS_NAME = "jdk.graal.compiler.lir.GenerateStub.Default";
    private static final String GENERATE_STUBS_CLASS_NAME = "jdk.graal.compiler.lir.GenerateStubs";
    private static final String GENERATED_STUBS_HOLDER_CLASS_NAME = "jdk.graal.compiler.lir.GeneratedStubsHolder";
    private static final String CONSTANT_NODE_PARAMETER_CLASS_NAME = "jdk.graal.compiler.graph.Node.ConstantNodeParameter";

    private static final String RTC_SUFFIX = "RTC";

    private TypeElement nodeIntrinsic;
    private TypeElement generatedStubsHolder;
    private TypeElement generateStub;
    private TypeElement generateStubDefault;
    private TypeElement generateStubs;
    private TypeMirror constantNodeParameter;
    private boolean malformedInput = false;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(GENERATE_STUB_CLASS_NAME, GENERATE_STUB_DEFAULT_CLASS_NAME, GENERATE_STUBS_CLASS_NAME,
                        GENERATED_STUBS_HOLDER_CLASS_NAME)));
    }

    private record GenerateStubClass(
                    TypeElement clazz,
                    List<GenerateStub> stubs) {
    }

    private record GenerateStub(
                    String name,
                    AnnotationMirror annotation,
                    ExecutableElement method,
                    RuntimeCheckedFlagsMethod runtimeCheckedFlagsMethod,
                    List<FeaturesGetter> minimumFeaturesGetters,
                    List<FeaturesGetter> guardedFeaturesGetters,
                    List<FeaturesGetter> runtimeFeaturesGetters) {
    }

    private record RuntimeCheckedFlagsMethod(
                    ExecutableElement method,
                    int runtimeCheckedFlagsParameterIndex) {

    }

    private record FeaturesGetter(
                    String amd64Getter,
                    String aarch64Getter) {
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            nodeIntrinsic = getTypeElement(NODE_INTRINSIC_CLASS_NAME);
            generatedStubsHolder = getTypeElement(GENERATED_STUBS_HOLDER_CLASS_NAME);
            generateStub = getTypeElement(GENERATE_STUB_CLASS_NAME);
            generateStubDefault = getTypeElement(GENERATE_STUB_DEFAULT_CLASS_NAME);
            generateStubs = getTypeElement(GENERATE_STUBS_CLASS_NAME);
            constantNodeParameter = getType(CONSTANT_NODE_PARAMETER_CLASS_NAME);
            for (Element holder : roundEnv.getElementsAnnotatedWith(generatedStubsHolder)) {
                AnnotationMirror generatedStubsHolderAnnotation = getAnnotation(holder, generatedStubsHolder.asType());
                TargetVM targetVM = TargetVM.valueOf(getAnnotationValue(generatedStubsHolderAnnotation, "targetVM", String.class));
                List<GenerateStubClass> classes = new ArrayList<>();
                for (TypeMirror sourceType : getAnnotationValueList(generatedStubsHolderAnnotation, "sources", TypeMirror.class)) {
                    TypeElement source = asTypeElement(sourceType);
                    AnnotationMirror classStubDefault = getAnnotation(source, generateStubDefault.asType());
                    List<GenerateStub> stubs = new ArrayList<>();
                    for (Element e : source.getEnclosedElements()) {
                        AnnotationMirror generateStubAnnotation = getAnnotation(e, generateStub.asType());
                        AnnotationMirror methodStubDefault = getAnnotation(e, generateStubDefault.asType());
                        if (generateStubAnnotation != null) {
                            extractStubs(targetVM, source, stubs, (ExecutableElement) e, generateStubAnnotation, List.of(generateStubAnnotation), methodStubDefault, classStubDefault);
                        }
                        AnnotationMirror generateStubsAnnotation = getAnnotation(e, generateStubs.asType());
                        if (generateStubsAnnotation != null) {
                            List<AnnotationMirror> values = getAnnotationValueList(generateStubsAnnotation, "value", AnnotationMirror.class);
                            extractStubs(targetVM, source, stubs, (ExecutableElement) e, generateStubsAnnotation, values, methodStubDefault, classStubDefault);
                        }
                    }
                    classes.add(new GenerateStubClass(source, stubs));
                }
                validateUniqueStubNames(targetVM, classes);
                if (!malformedInput) {
                    createStubs(targetVM, (TypeElement) holder, classes);
                }
            }
        }
        return true;
    }

    private void extractStubs(TargetVM targetVM,
                    TypeElement source,
                    List<GenerateStub> stubs,
                    ExecutableElement method,
                    AnnotationMirror annotation,
                    List<AnnotationMirror> generateStubAnnotations,
                    AnnotationMirror methodStubDefault,
                    AnnotationMirror classStubDefault) {
        if (getAnnotation(method, nodeIntrinsic.asType()) == null) {
            String msg = String.format("methods annotated with %s must also be annotated with %s", annotation, nodeIntrinsic);
            env().getMessager().printMessage(Diagnostic.Kind.ERROR, msg, method, annotation);
            malformedInput = true;
        }
        RuntimeCheckedFlagsMethod rtc = findRuntimeCheckedFlagsVariant(source, method, annotation);
        for (AnnotationMirror generateStubAnnotationValue : generateStubAnnotations) {
            var minimumFeaturesGetters = extractFeaturesGetters(targetVM, source, generateStubAnnotationValue,
                            "minimumCPUFeaturesAMD64", "minimumCPUFeaturesAARCH64", methodStubDefault, classStubDefault);
            var guardedFeaturesGetters = extractFeaturesGetters(targetVM, source, generateStubAnnotationValue,
                            "guardedCPUFeaturesAMD64", "guardedCPUFeaturesAARCH64", methodStubDefault, classStubDefault);
            var runtimeFeaturesGetters = extractFeaturesGetters(targetVM, source, generateStubAnnotationValue,
                            "runtimeCPUFeaturesAMD64", "runtimeCPUFeaturesAARCH64", methodStubDefault, classStubDefault);
            String name = getAnnotationValue(generateStubAnnotationValue, "name", String.class);
            if (name.isEmpty()) {
                name = method.getSimpleName().toString();
            }
            stubs.add(new GenerateStub(name, generateStubAnnotationValue, method, rtc, minimumFeaturesGetters, guardedFeaturesGetters, runtimeFeaturesGetters));
        }
    }

    private List<FeaturesGetter> extractFeaturesGetters(TargetVM targetVM, TypeElement source, AnnotationMirror genStub,
                    String amd64Attribute, String aarch64Attribute, AnnotationMirror methodStubDefault, AnnotationMirror classStubDefault) {
        if (targetVM != TargetVM.substrate) {
            return List.of();
        }
        List<String> amd64Getters = resolveFeatureGetters(genStub, methodStubDefault, classStubDefault, amd64Attribute);
        List<String> aarch64Getters = resolveFeatureGetters(genStub, methodStubDefault, classStubDefault, aarch64Attribute);
        if (amd64Getters.stream().anyMatch(String::isEmpty) || aarch64Getters.stream().anyMatch(String::isEmpty)) {
            env().getMessager().printMessage(Diagnostic.Kind.ERROR, "CPU feature getter names must not be empty", source, genStub);
            malformedInput = true;
            return List.of();
        }
        int variants = Math.max(amd64Getters.size(), aarch64Getters.size());
        ArrayList<FeaturesGetter> result = new ArrayList<>(variants);
        for (int i = 0; i < variants; i++) {
            String amd64Getter = i < amd64Getters.size() ? amd64Getters.get(i) : "";
            String aarch64Getter = i < aarch64Getters.size() ? aarch64Getters.get(i) : "";
            result.add(new FeaturesGetter(amd64Getter, aarch64Getter));
        }
        return result;
    }

    private static List<String> resolveFeatureGetters(AnnotationMirror genStub, AnnotationMirror methodStubDefault, AnnotationMirror classStubDefault, String attribute) {
        if (hasFeatureGetters(genStub, attribute)) {
            return getFeatureGetters(genStub, attribute);
        }
        if (methodStubDefault != null && hasFeatureGetters(methodStubDefault, attribute)) {
            return getFeatureGetters(methodStubDefault, attribute);
        }
        if (classStubDefault != null && hasFeatureGetters(classStubDefault, attribute)) {
            return getFeatureGetters(classStubDefault, attribute);
        }
        return List.of();
    }

    private static boolean hasFeatureGetters(AnnotationMirror annotation, String attribute) {
        ExecutableElement method = getAnnotationAttribute(annotation, attribute);
        return annotation.getElementValues().containsKey(method);
    }

    private static List<String> getFeatureGetters(AnnotationMirror annotation, String attribute) {
        ExecutableElement method = getAnnotationAttribute(annotation, attribute);
        if (method.getReturnType().getKind() == TypeKind.ARRAY) {
            return getAnnotationValueList(annotation, attribute, String.class);
        }
        String getter = getAnnotationValue(annotation, attribute, String.class);
        return getter.isEmpty() ? List.of() : List.of(getter);
    }

    private static ExecutableElement getAnnotationAttribute(AnnotationMirror annotation, String attribute) {
        for (ExecutableElement method : ElementFilter.methodsIn(annotation.getAnnotationType().asElement().getEnclosedElements())) {
            if (method.getSimpleName().contentEquals(attribute)) {
                return method;
            }
        }
        throw new IllegalArgumentException("no annotation attribute named " + attribute);
    }

    private RuntimeCheckedFlagsMethod findRuntimeCheckedFlagsVariant(TypeElement clazz, ExecutableElement method, AnnotationMirror annotation) {
        for (Element e : clazz.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD && getAnnotation(e, nodeIntrinsic.asType()) != null) {
                ExecutableElement cur = (ExecutableElement) e;
                RuntimeCheckedFlagsMethod runtimeCheckedFlagsVariant = checkRuntimeCheckedFlagsVariant(method, cur);
                if (runtimeCheckedFlagsVariant != null) {
                    return runtimeCheckedFlagsVariant;
                }
            }
        }
        env().getMessager().printMessage(Diagnostic.Kind.ERROR, method + ": Could not find runtime checked flags variant. " +
                        "For every method annotated with @GenerateStub, a second @NodeIntrinsic method with the same signature + an additional @ConstantNodeParameter EnumSet<CPUFeature> parameter for runtime checked CPU flags is required.",
                        method, annotation);
        malformedInput = true;
        return null;
    }

    private static RuntimeCheckedFlagsMethod checkRuntimeCheckedFlagsVariant(ExecutableElement method, ExecutableElement cur) {
        if (!cur.getReturnType().equals(method.getReturnType()) || cur.getParameters().size() != method.getParameters().size() + 1) {
            return null;
        }
        int iCur = 0;
        int iDiff = -1;
        for (VariableElement p : method.getParameters()) {
            VariableElement pCur = cur.getParameters().get(iCur++);
            if (!p.asType().equals(pCur.asType())) {
                if (iDiff < 0 && p.asType().equals(cur.getParameters().get(iCur).asType()) && pCur.asType().toString().startsWith("java.util.EnumSet")) {
                    iDiff = iCur - 1;
                    iCur++;
                } else {
                    return null;
                }
            }
        }
        assert iCur == cur.getParameters().size() - 1;
        if (iDiff < 0) {
            iDiff = iCur;
            if (!cur.getParameters().get(iDiff).asType().toString().startsWith("java.util.EnumSet")) {
                return null;
            }
        }
        return new RuntimeCheckedFlagsMethod(cur, iDiff);
    }

    private void validateUniqueStubNames(TargetVM targetVM, List<GenerateStubClass> classes) {
        Map<String, GenerateStub> stubsByName = new LinkedHashMap<>();
        for (GenerateStubClass genClass : classes) {
            for (GenerateStub stub : genClass.stubs()) {
                validateUniqueStubName(stubsByName, stub.name(), stub);
                if (targetVM == TargetVM.substrate) {
                    for (int i = 0; i < getRuntimeFeaturesGetters(stub).size(); i++) {
                        validateUniqueStubName(stubsByName, rtcVariantName(stub.name(), i), stub);
                    }
                }
            }
        }
    }

    private void validateUniqueStubName(Map<String, GenerateStub> stubsByName, String name, GenerateStub stub) {
        GenerateStub existing = stubsByName.putIfAbsent(name, stub);
        if (existing != null) {
            env().getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "duplicate generated stub name: %s, annotations: %s, %s".formatted(name, existing.annotation(), stub.annotation()),
                            stub.method(), stub.annotation());
            malformedInput = true;
        }
    }

    private void createStubs(TargetVM targetVM, TypeElement holder, List<GenerateStubClass> classes) {
        PackageElement pkg = (PackageElement) holder.getEnclosingElement();
        String genClassName = holder.getSimpleName() + "Gen";
        String pkgQualifiedName = pkg.getQualifiedName().toString();
        String qualifiedGenClassName = pkgQualifiedName + "." + genClassName;
        try {
            JavaFileObject factory = env().getFiler().createSourceFile(qualifiedGenClassName, holder);
            try (PrintWriter out = new PrintWriter(factory.openWriter())) {
                out.printf("// CheckStyle: stop header check\n");
                out.printf("// CheckStyle: stop line length check\n");
                out.printf("// GENERATED CONTENT - DO NOT EDIT\n");
                out.printf("// GENERATOR: %s\n", getClass().getName());
                out.printf("package %s;\n", pkgQualifiedName);
                out.printf("\n");
                Set<String> imports = new LinkedHashSet<>();
                switch (targetVM) {
                    case hotspot:
                        imports.addAll(List.of(
                                        "jdk.graal.compiler.api.replacements.Snippet",
                                        "jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage",
                                        "jdk.graal.compiler.hotspot.meta.HotSpotProviders",
                                        "jdk.graal.compiler.options.OptionValues",
                                        "jdk.graal.compiler.hotspot.stubs.SnippetStub"));
                        break;
                    case substrate:
                        imports.addAll(List.of(
                                        "com.oracle.svm.core.SubstrateTarget",
                                        "com.oracle.svm.shared.Uninterruptible",
                                        "com.oracle.svm.guest.staging.snippets.SubstrateForeignCallTarget",
                                        "com.oracle.svm.graal.RuntimeCPUFeatureRegion",
                                        "jdk.graal.compiler.api.replacements.Fold",
                                        "jdk.graal.compiler.debug.GraalError",
                                        "java.util.EnumSet",
                                        "java.util.List",
                                        "jdk.vm.ci.code.Architecture",
                                        "jdk.vm.ci.aarch64.AArch64",
                                        "jdk.vm.ci.amd64.AMD64"));
                        break;
                }
                for (GenerateStubClass genClass : classes) {
                    imports.add(genClass.clazz.toString());
                    for (GenerateStub gen : genClass.stubs) {
                        for (VariableElement p : gen.method.getParameters()) {
                            if (getAnnotation(p, constantNodeParameter) != null && !p.asType().getKind().isPrimitive()) {
                                imports.add(p.asType().toString());
                            }
                        }
                        if (targetVM == TargetVM.substrate && !gen.guardedFeaturesGetters().isEmpty()) {
                            imports.add("com.oracle.svm.core.cpufeature.RuntimeCPUFeatureCheck");
                        }
                    }
                }
                for (String i : imports.stream().sorted().toList()) {
                    int lastDot = i.lastIndexOf('.');
                    if (pkgQualifiedName.length() != lastDot || !i.startsWith(pkgQualifiedName)) {
                        out.printf("import %s;\n", i);
                    }
                }
                out.println();
                out.printf("""
                                /*
                                 * Generated by IntrinsicStubProcessor.
                                 *
                                 * @see %s
                                 */
                                """, holder.getQualifiedName());
                out.printf("public class %s ", genClassName);
                if (targetVM == TargetVM.hotspot) {
                    out.printf("extends SnippetStub ");
                }
                out.printf("{\n");
                out.println();
                if (targetVM == TargetVM.hotspot) {
                    out.printf("    public %s(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {\n", genClassName);
                    out.printf("        super(linkage.getDescriptor().getName(), options, providers, linkage);\n");
                    out.printf("    }\n");
                    out.println();
                }
                if (targetVM == TargetVM.substrate) {
                    emitSubstrateFeatureMetadata(out, classes, imports);
                }
                for (GenerateStubClass genClass : classes) {
                    for (GenerateStub gen : genClass.stubs) {
                        String stubName = gen.name();
                        List<String> params = getAnnotationValueList(gen.annotation, "parameters", String.class);
                        switch (targetVM) {
                            case hotspot:
                                generateStub(targetVM, out, imports, genClass, stubName, params, gen);
                                break;
                            case substrate:
                                List<String> guardedCPUFeatures = new ArrayList<>(gen.guardedFeaturesGetters.size());
                                for (int i = 0; i < gen.guardedFeaturesGetters.size(); i++) {
                                    guardedCPUFeatures.add(String.format("getGuardedCPUFeatures(\"%s\", %d)", stubName, i));
                                }

                                generateStub(targetVM, out, imports, genClass, stubName, params, gen,
                                                gen.runtimeCheckedFlagsMethod.method,
                                                null,
                                                guardedCPUFeatures,
                                                gen.runtimeCheckedFlagsMethod.runtimeCheckedFlagsParameterIndex,
                                                -1);

                                List<FeaturesGetter> runtimeFeaturesGetters = getRuntimeFeaturesGetters(gen);
                                for (int i = 0; i < runtimeFeaturesGetters.size(); i++) {
                                    generateStub(targetVM, out, imports, genClass, rtcVariantName(stubName, i), params, gen,
                                                    gen.runtimeCheckedFlagsMethod.method,
                                                    String.format("getRuntimeCPUFeatures(\"%s\", %d)", stubName, i),
                                                    List.of(),
                                                    gen.runtimeCheckedFlagsMethod.runtimeCheckedFlagsParameterIndex,
                                                    i);
                                }
                                break;
                        }
                    }
                }
                if (targetVM == TargetVM.substrate) {
                    emitStaticFeatureGuarantees(out, classes, imports);
                }
                out.printf("}\n");
            }
        } catch (IOException e) {
            env().getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }
    }

    private static String rtcVariantName(String stubName, int i) {
        return stubName + RTC_SUFFIX + i;
    }

    private void emitSubstrateFeatureMetadata(PrintWriter out, List<GenerateStubClass> classes, Set<String> imports) {
        emitFeatureMetadata(out, "getMinimumCPUFeatures", GenerateStub::minimumFeaturesGetters, classes, imports, true);
        emitRuntimeFeatureMetadata(out, classes, imports);
        emitIndexedFeatureSetGetter(out, "getRuntimeCPUFeatures");
        emitFeatureMetadata(out, "getGuardedCPUFeatures", GenerateStub::guardedFeaturesGetters, classes, imports, false);
        emitIndexedFeatureSetGetter(out, "getGuardedCPUFeatures");
    }

    private static void emitFeatureSetsGetterForAllArch(PrintWriter out, String getter) {
        out.printf("""
                        @SuppressWarnings("unused")
                        public static List<EnumSet<?>> %s(String name) {
                            Architecture arch = SubstrateTarget.getArchitecture();
                            return switch (arch) {
                        %s
                                default -> throw GraalError.unsupportedArchitecture(arch);
                            };
                        }
                        """.formatted(
                        getter,
                        Arrays.stream(Arch.values()).map(arch -> "case %s %s -> %s%s(name);".indent(8).formatted(
                                        arch, arch.toString().toLowerCase(Locale.ROOT), getter, arch)).collect(Collectors.joining()).stripTrailing()).indent(4));
        out.println();
    }

    private static void emitIndexedFeatureSetGetter(PrintWriter out, String getter) {
        out.printf("""
                        @SuppressWarnings("unused")
                        @Fold
                        public static EnumSet<?> %s(String name, int variant) {
                            List<EnumSet<?>> features = %s(name);
                            return variant < features.size() ? features.get(variant) : null;
                        }
                        """.formatted(getter, getter).indent(4));
        out.println();
    }

    private void emitFeatureMetadata(PrintWriter out, String getter, Function<GenerateStub, List<FeaturesGetter>> featuresGetters,
                    List<GenerateStubClass> classes, Set<String> imports, boolean defaultToEmptySet) {
        emitFeatureSetsGetterForAllArch(out, getter);

        for (Arch arch : Arch.values()) {
            Map<List<String>, List<String>> stubsByFeatureGetters = groupStubsByFeatureGetters(arch, classes, featuresGetters, imports);

            /*
             * Use the default case for stubs without declared getters: either an implicit empty
             * minimum feature set or an empty list, according to defaultToEmptySet.
             */
            stubsByFeatureGetters.remove(List.of());

            String defaultValue = defaultToEmptySet ? "List.of(%s)".formatted(getEmptyFeatureSet(arch)) : "List.of()";
            emitFeatureSetsGetterForArch(out, getter, arch, stubsByFeatureGetters, defaultValue);
        }
    }

    private void emitRuntimeFeatureMetadata(PrintWriter out, List<GenerateStubClass> classes, Set<String> imports) {
        emitFeatureSetsGetterForAllArch(out, "getRuntimeCPUFeatures");

        for (Arch arch : Arch.values()) {
            Map<List<String>, List<String>> stubsByFeatureGetters = new LinkedHashMap<>();
            for (GenerateStubClass genClass : classes) {
                for (GenerateStub stub : genClass.stubs()) {
                    List<String> runtimeGetters = getArchFeaturesGetters(arch, genClass, stub.runtimeFeaturesGetters(), imports);
                    List<String> getters = new ArrayList<>(runtimeGetters);
                    List<String> minimumGetters = getArchFeaturesGetters(arch, genClass, stub.minimumFeaturesGetters(), imports);
                    getters.addAll(minimumGetters.isEmpty() ? List.of(getEmptyFeatureSet(arch)) : minimumGetters);
                    stubsByFeatureGetters.computeIfAbsent(List.copyOf(getters), k -> new ArrayList<>()).add(stub.name());
                }
            }

            /* Omit switch cases that would return the same value as the default. */
            stubsByFeatureGetters.remove(List.of(getEmptyFeatureSet(arch)));

            emitFeatureSetsGetterForArch(out, "getRuntimeCPUFeatures", arch, stubsByFeatureGetters, "List.of(%s)".formatted(getEmptyFeatureSet(arch)));
        }
    }

    private static void emitFeatureSetsGetterForArch(PrintWriter out, String getter, Arch arch, Map<List<String>, List<String>> stubsByFeatureGetters, String defaultValue) {
        out.printf("""
                        @SuppressWarnings("unused")
                        public static List<EnumSet<?>> %s%s(String name) {
                        """.indent(4), getter, arch);
        if (!stubsByFeatureGetters.isEmpty()) {
            out.print("switch (name) {".indent(8));
            stubsByFeatureGetters.forEach((featureGetters, stubNames) -> {
                emitStubCases(out, stubNames);
                out.printf("return List.of(%s);".indent(16), String.join(", ", featureGetters));
            });
            out.print("}".indent(8));
        }
        out.printf("""
                            return %s;
                        }
                        """.indent(4), defaultValue);
        out.println();
    }

    private static List<FeaturesGetter> getRuntimeFeaturesGetters(GenerateStub stub) {
        List<FeaturesGetter> getters = new ArrayList<>(stub.runtimeFeaturesGetters());
        getters.addAll(stub.minimumFeaturesGetters());
        return getters;
    }

    private List<String> getArchFeaturesGetters(Arch arch, GenerateStubClass genClass, List<FeaturesGetter> featuresGetters, Set<String> imports) {
        return featuresGetters.stream().map(featuresGetter -> getArchFeaturesGetter(arch, genClass, featuresGetter, imports)).filter(
                        featureGetter -> !featureGetter.isEmpty()).toList();
    }

    private static void emitStubCases(PrintWriter out, List<String> stubNames) {
        out.printf(stubNames.stream().map(name -> String.format("case \"%s\":", name)).collect(Collectors.joining("\n")).indent(12));
    }

    private Map<List<String>, List<String>> groupStubsByFeatureGetters(Arch arch, List<GenerateStubClass> classes,
                    Function<GenerateStub, List<FeaturesGetter>> featuresGetters, Set<String> imports) {
        Map<List<String>, List<String>> stubsByFeatureGetters = new LinkedHashMap<>();
        for (GenerateStubClass genClass : classes) {
            for (GenerateStub stub : genClass.stubs()) {
                List<String> archGetters = getArchFeaturesGetters(arch, genClass, featuresGetters.apply(stub), imports);
                if (!archGetters.isEmpty()) {
                    stubsByFeatureGetters.computeIfAbsent(archGetters, k -> new ArrayList<>()).add(stub.name());
                }
            }
        }
        return stubsByFeatureGetters;
    }

    private String getArchFeaturesGetter(Arch arch, GenerateStubClass genClass, FeaturesGetter featuresGetter, Set<String> imports) {
        if (featuresGetter == null) {
            return "";
        }
        return switch (arch) {
            case AMD64 -> featuresGetter.amd64Getter.isEmpty() ? ""
                            : "%s.%s()".formatted(
                                            simpleTypeName(Objects.requireNonNullElse(findStaticMethodReceiver(genClass.clazz, featuresGetter.amd64Getter), genClass.clazz).asType(), imports),
                                            featuresGetter.amd64Getter);
            case AArch64 -> featuresGetter.aarch64Getter.isEmpty() ? ""
                            : "%s.%s()".formatted(
                                            simpleTypeName(Objects.requireNonNullElse(findStaticMethodReceiver(genClass.clazz, featuresGetter.aarch64Getter), genClass.clazz).asType(), imports),
                                            featuresGetter.aarch64Getter);
        };
    }

    private List<String> getFeatureGetterSeeReferences(GenerateStubClass genClass, GenerateStub stub, int runtimeVariant, Set<String> imports) {
        return Arrays.stream(Arch.values()).flatMap(arch -> {
            List<String> getters;
            if (runtimeVariant >= 0) {
                getters = new ArrayList<>(getArchFeaturesGetters(arch, genClass, stub.runtimeFeaturesGetters(), imports));
                getters.addAll(getArchFeaturesGetters(arch, genClass, stub.minimumFeaturesGetters(), imports));
                getters = runtimeVariant < getters.size() ? List.of(getters.get(runtimeVariant)) : List.of();
            } else {
                getters = getArchFeaturesGetters(arch, genClass, stub.minimumFeaturesGetters(), imports);
            }
            return getters.stream();
        }).map(getter -> {
            int separator = getter.lastIndexOf('.');
            return getter.substring(0, separator) + "#" + getter.substring(separator + 1);
        }).distinct().toList();
    }

    private static String getEmptyFeatureSet(Arch arch) {
        return switch (arch) {
            case AMD64 -> "EnumSet.noneOf(AMD64.CPUFeature.class)";
            case AArch64 -> "EnumSet.noneOf(AArch64.CPUFeature.class)";
        };
    }

    private void generateStub(TargetVM targetVM, PrintWriter out, Set<String> imports, GenerateStubClass genClass, String stubName, List<String> params, GenerateStub stub) {
        generateStub(targetVM, out, imports, genClass, stubName, params, stub, null, null, List.of(), -1, -1);
    }

    /**
     * Generates an intrinsic method (snippet or foreign call target).
     * <p>
     * Standard variant (on HotSpot or no runtime-checked CPU features on SVM):
     *
     * <pre>
     * private static int intrinsic(...parameters) {
     *     return IntrinsicNode.intrinsic(...parameters);
     * }
     * </pre>
     *
     * Runtime-guarded variant with baseline fallback (used for AOT code on Native Image):
     *
     * <pre>
     * private static int intrinsic(...parameters) {
     *     var features = getGuardedCPUFeatures("intrinsic")
     *     if (RuntimeCPUFeatureCheck.isSupported(features)) {
     *         var region = RuntimeCPUFeatureRegion.enter(features);
     *         try {
     *             return IntrinsicNode.intrinsic(...parameters, features);
     *         } finally {
     *             region.leave();
     *         }
     *     } else {
     *         return IntrinsicNode.intrinsic(parameters, length);
     *     }
     * }
     * </pre>
     *
     * Runtime-compilation variant with distinct CPU features (used for JIT code on Native Image):
     *
     * <pre>
     * private static int intrinsicRTC0(...parameters) {
     *     var features = getRuntimeCPUFeatures("intrinsic", 0)
     *     var region = RuntimeCPUFeatureRegion.enter(features);
     *     try {
     *         return IntrinsicNode.intrinsic(...parameters, features);
     *     } finally {
     *         region.leave();
     *     }
     * }
     * </pre>
     */
    private void generateStub(TargetVM targetVM, PrintWriter out, Set<String> imports, GenerateStubClass genClass, String methodName, List<String> params, GenerateStub stub,
                    ExecutableElement rtcMethod, String requiredCPUFeatures, List<String> guardedCPUFeatures, int runtimeCheckedFeaturesParameterIndex, int runtimeVariant) {
        boolean rtcVariant = runtimeVariant >= 0;
        assert !rtcVariant || (requiredCPUFeatures != null && runtimeCheckedFeaturesParameterIndex >= 0);
        ExecutableElement method = stub.method();
        final Name className = genClass.clazz.getSimpleName();
        final int level = 4;
        int indent = level;

        final String see = String.format("%s#%s(%s)", className, method.getSimpleName(),
                        method.getParameters().stream().//
                                        map(VariableElement::asType).//
                                        map(pt -> simpleTypeName(pt, imports)).//
                                        collect(Collectors.joining(",")));

        switch (targetVM) {
            case hotspot:
                out.printf("""
                                /**
                                 * @see %s
                                 */
                                @Snippet
                                """.indent(indent), see);
                break;
            case substrate:
                String variant = rtcVariant
                                ? "Variant for JIT code."
                                : "Variant for AOT code.";
                out.print("/**".indent(indent));
                out.printf(" * %s".indent(indent), variant);
                out.print(" *".indent(indent));
                out.printf(" * @see %s".indent(indent), see);
                List<String> featureGetterSeeReferences = getFeatureGetterSeeReferences(genClass, stub, runtimeVariant, imports);
                for (String featureGetterSeeReference : featureGetterSeeReferences) {
                    out.printf(" * @see %s".indent(indent), featureGetterSeeReference);
                }
                out.print(" */".indent(indent));
                out.print("""
                                @Uninterruptible(reason = "Must not do a safepoint check.")
                                @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
                                """.indent(indent));
                break;
        }
        out.printf("private static %s %s(%s) {".indent(indent),
                        method.getReturnType(),
                        methodName,
                        method.getParameters().stream().//
                                        filter(p -> getAnnotation(p, constantNodeParameter) == null).//
                                        map(p -> simpleTypeName(p.asType(), imports) + " " + p.getSimpleName()).//
                                        collect(Collectors.joining(", ")));
        indent += level;

        if (!guardedCPUFeatures.isEmpty()) {
            for (int i = 0; i < guardedCPUFeatures.size(); i++) {
                String guardedCPUFeaturesVar = "guardedFeatures" + i;
                out.printf("var %s = %s;".indent(indent), guardedCPUFeaturesVar, guardedCPUFeatures.get(i));
                /*
                 * Note: RuntimeCPUFeatureCheck only supports specific features; if we check for any
                 * "unsupported" features, even if they're implied by any of the checked features,
                 * the feature check will unconditionally fail and constant-fold to false. Minimize
                 * the guarded CPU feature sets in order to allow the check to succeed.
                 *
                 * Null check: Since we generate only one method for all architectures, a guarded
                 * feature getter can be absent for the current architecture, in which case the
                 * indexed getter returns null and the branch is skipped.
                 */
                out.printf("if (%s != null && RuntimeCPUFeatureCheck.isSupported(%s)) {".indent(indent),
                                guardedCPUFeaturesVar, guardedCPUFeaturesVar);
                indent += level;

                out.printf("""
                                RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(%s);
                                try {
                                """.indent(indent),
                                guardedCPUFeaturesVar);
                indent += level;

                out.printf(generateStubCall(className, rtcMethod, method, params, guardedCPUFeaturesVar, runtimeCheckedFeaturesParameterIndex).indent(indent));

                indent -= level;
                out.printf("""
                                } finally {
                                    region.leave();
                                }
                                """.indent(indent));

                indent -= level;
                out.printf("}".indent(indent));
            }
        }
        if (requiredCPUFeatures != null) {
            out.printf("var regionFeatures = %s;".indent(indent), requiredCPUFeatures);
            String requiredCPUFeaturesVar = "regionFeatures";
            out.printf("""
                            RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(%s);
                            try {
                            """.indent(indent),
                            requiredCPUFeaturesVar);
            indent += level;

            out.printf(generateStubCall(className, rtcMethod, method, params, requiredCPUFeaturesVar, runtimeCheckedFeaturesParameterIndex).indent(indent));

            indent -= level;
            out.printf("""
                            } finally {
                                region.leave();
                            }
                            """.indent(indent));
        } else {
            out.printf(generateStubCall(className, method, method, params, null, -1).indent(indent));
        }
        indent -= level;
        out.printf("}".indent(indent));
        out.println();
    }

    private String generateStubCall(Name className, ExecutableElement targetMethod, ExecutableElement sourceMethod, List<String> constParams,
                    String runtimeCheckedFeatures, int runtimeCheckedFeaturesParameterIndex) {
        StringBuilder out = new StringBuilder();
        int iConst = 0;
        int iSource = 0;
        List<? extends VariableElement> targetParams = targetMethod.getParameters();
        List<? extends VariableElement> sourceParams = sourceMethod.getParameters();
        for (int i = 0; i < targetParams.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            VariableElement targetParam = targetParams.get(i);
            if (getAnnotation(targetParam, constantNodeParameter) == null) {
                out.append(sourceParams.get(iSource++).getSimpleName().toString());
            } else {
                if (i == runtimeCheckedFeaturesParameterIndex) {
                    out.append(runtimeCheckedFeatures);
                } else {
                    if (!targetParam.asType().getKind().isPrimitive()) {
                        out.append(asTypeElement(targetParam.asType()).getSimpleName()).append(".");
                    }
                    out.append(constParams.get(iConst++));
                    iSource++;
                }
            }
        }
        return String.format("%s%s.%s(%s);%s",
                        targetMethod.getReturnType().getKind() == VOID ? "" : "return ",
                        className, targetMethod.getSimpleName(), out,
                        targetMethod.getReturnType().getKind() == VOID ? "\nreturn;" : "");
    }

    private void emitStaticFeatureGuarantees(PrintWriter out, List<GenerateStubClass> classes, Set<String> imports) {
        Set<List<String>> getterLists = new LinkedHashSet<>();
        for (Arch arch : Arch.values()) {
            for (GenerateStubClass genClass : classes) {
                for (GenerateStub stub : genClass.stubs()) {
                    List<String> runtimeGetters = getArchFeaturesGetters(arch, genClass, stub.runtimeFeaturesGetters(), imports);
                    if (runtimeGetters.size() > 1) {
                        getterLists.add(runtimeGetters);
                    }
                    List<String> guardedGetters = getArchFeaturesGetters(arch, genClass, stub.guardedFeaturesGetters(), imports);
                    if (guardedGetters.size() > 1) {
                        getterLists.add(guardedGetters);
                    }
                    List<String> minimumGetters = getArchFeaturesGetters(arch, genClass, stub.minimumFeaturesGetters(), imports);
                    if (minimumGetters.size() > 1) {
                        getterLists.add(minimumGetters);
                    }
                }
            }
        }
        if (!getterLists.isEmpty()) {
            out.print("""
                            @SuppressWarnings("unlikely-arg-type")
                            private static void guaranteeCPUFeatureOrder(List<EnumSet<?>> features, String... getters) {
                                for (int i = 0; i < features.size(); i++) {
                                    EnumSet<?> featureSet = features.get(i);
                                    for (int j = 0; j < i; j++) {
                                        EnumSet<?> previous = features.get(j);
                                        GraalError.guarantee(!featureSet.equals(previous), "%s and %s return duplicate CPU feature variants: %s", getters[j], getters[i], featureSet);
                                        GraalError.guarantee(!featureSet.containsAll(previous) || previous.containsAll(featureSet),
                                                        "%s must precede %s because CPU feature supersets must precede their subsets: %s, %s", getters[i], getters[j], previous, featureSet);
                                    }
                                }
                            }
                            """.indent(4));
            out.println();
            out.print("static {".indent(4));
            for (List<String> getters : getterLists) {
                out.print("guaranteeCPUFeatureOrder(List.of(".indent(8));
                for (int i = 0; i < getters.size(); i++) {
                    String suffix = i + 1 < getters.size() ? "," : "),";
                    out.print((getters.get(i) + suffix).indent(24));
                }
                for (int i = 0; i < getters.size(); i++) {
                    String getterName = getters.get(i);
                    String suffix = i + 1 < getters.size() ? "," : ");";
                    out.print(("\"" + getterName.substring(0, getterName.length() - 2) + "\"" + suffix).indent(24));
                }
            }
            out.print("}".indent(4));
            out.println();
        }
    }

    private static String simpleTypeName(TypeMirror type, Set<String> imports) {
        String typeName = type.toString();
        return isJavaLangTypeName(typeName) || imports.contains(typeName) ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
    }

    private static boolean isJavaLangTypeName(String typeName) {
        return typeName.startsWith("java.lang.") && typeName.lastIndexOf('.') == "java.lang.".length() - 1;
    }

    private TypeElement findStaticMethodReceiver(TypeElement typeElement, String methodName) {
        ArrayDeque<TypeElement> pending = new ArrayDeque<>();
        Set<TypeElement> visited = new LinkedHashSet<>();
        pending.push(typeElement);
        while (!pending.isEmpty()) {
            TypeElement current = pending.pop();
            if (!visited.add(current)) {
                continue;
            }
            for (ExecutableElement method : ElementFilter.methodsIn(current.getEnclosedElements())) {
                if (method.getModifiers().contains(Modifier.STATIC) && method.getSimpleName().contentEquals(methodName)) {
                    return current;
                }
            }
            List<? extends TypeMirror> interfaces = current.getInterfaces();
            for (int i = interfaces.size() - 1; i >= 0; i--) {
                pending.push((TypeElement) env().getTypeUtils().asElement(interfaces.get(i)));
            }
            TypeMirror superclassType = current.getSuperclass();
            if (superclassType.getKind() != TypeKind.NONE) {
                pending.push((TypeElement) env().getTypeUtils().asElement(superclassType));
            }
        }
        return null;
    }
}
