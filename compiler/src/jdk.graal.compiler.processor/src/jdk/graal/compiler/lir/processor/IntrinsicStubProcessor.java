/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
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

    private static final String NODE_INTRINSIC_CLASS_NAME = "jdk.graal.compiler.graph.Node.NodeIntrinsic";
    private static final String GENERATE_STUB_CLASS_NAME = "jdk.graal.compiler.lir.GenerateStub";
    private static final String GENERATE_STUBS_CLASS_NAME = "jdk.graal.compiler.lir.GenerateStubs";
    private static final String GENERATED_STUBS_HOLDER_CLASS_NAME = "jdk.graal.compiler.lir.GeneratedStubsHolder";
    private static final String CONSTANT_NODE_PARAMETER_CLASS_NAME = "jdk.graal.compiler.graph.Node.ConstantNodeParameter";

    private TypeElement nodeIntrinsic;
    private TypeElement generatedStubsHolder;
    private TypeElement generateStub;
    private TypeElement generateStubs;
    private TypeMirror constantNodeParameter;
    private boolean malformedInput = false;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(GENERATE_STUB_CLASS_NAME, GENERATE_STUBS_CLASS_NAME, GENERATED_STUBS_HOLDER_CLASS_NAME)));
    }

    private static final class GenerateStubClass {
        private final TypeElement clazz;
        private final ArrayList<GenerateStub> stubs;
        private final Set<MinimumFeaturesGetter> featureGetters;

        private GenerateStubClass(TypeElement clazz, ArrayList<GenerateStub> stubs, Set<MinimumFeaturesGetter> featureGetters) {
            this.clazz = clazz;
            this.stubs = stubs;
            this.featureGetters = featureGetters;
        }
    }

    private static final class GenerateStub {
        private final AnnotationMirror annotation;
        private final ExecutableElement method;
        private final RuntimeCheckedFlagsMethod runtimeCheckedFlagsMethod;
        private final MinimumFeaturesGetter minimumFeaturesGetter;

        private GenerateStub(AnnotationMirror annotation, ExecutableElement method, RuntimeCheckedFlagsMethod runtimeCheckedFlagsMethod, MinimumFeaturesGetter minimumFeaturesGetter) {
            this.annotation = annotation;
            this.method = method;
            this.runtimeCheckedFlagsMethod = runtimeCheckedFlagsMethod;
            this.minimumFeaturesGetter = minimumFeaturesGetter;
        }
    }

    private static final class RuntimeCheckedFlagsMethod {

        private final ExecutableElement method;
        private final int runtimeCheckedFlagsParameterIndex;

        private RuntimeCheckedFlagsMethod(ExecutableElement method, int runtimeCheckedFlagsParameterIndex) {
            this.method = method;
            this.runtimeCheckedFlagsParameterIndex = runtimeCheckedFlagsParameterIndex;
        }
    }

    private static final class MinimumFeaturesGetter {
        private final String amd64Getter;
        private final String aarch64Getter;
        private String name;

        private MinimumFeaturesGetter(String amd64Getter, String aarch64Getter) {
            this.amd64Getter = amd64Getter;
            this.aarch64Getter = aarch64Getter;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MinimumFeaturesGetter that = (MinimumFeaturesGetter) o;
            return amd64Getter.equals(that.amd64Getter) && aarch64Getter.equals(that.aarch64Getter);
        }

        @Override
        public int hashCode() {
            int result = amd64Getter.hashCode();
            result = 31 * result + aarch64Getter.hashCode();
            return result;
        }
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            nodeIntrinsic = getTypeElement(NODE_INTRINSIC_CLASS_NAME);
            generatedStubsHolder = getTypeElement(GENERATED_STUBS_HOLDER_CLASS_NAME);
            generateStub = getTypeElement(GENERATE_STUB_CLASS_NAME);
            generateStubs = getTypeElement(GENERATE_STUBS_CLASS_NAME);
            constantNodeParameter = getType(CONSTANT_NODE_PARAMETER_CLASS_NAME);
            for (Element holder : roundEnv.getElementsAnnotatedWith(generatedStubsHolder)) {
                AnnotationMirror generatedStubsHolderAnnotation = getAnnotation(holder, generatedStubsHolder.asType());
                TargetVM targetVM = TargetVM.valueOf(getAnnotationValue(generatedStubsHolderAnnotation, "targetVM", String.class));
                ArrayList<GenerateStubClass> classes = new ArrayList<>();
                for (TypeMirror sourceType : getAnnotationValueList(generatedStubsHolderAnnotation, "sources", TypeMirror.class)) {
                    TypeElement source = asTypeElement(sourceType);
                    ArrayList<GenerateStub> stubs = new ArrayList<>();
                    HashMap<MinimumFeaturesGetter, MinimumFeaturesGetter> minimumFeatureGetters = new LinkedHashMap<>();
                    for (Element e : source.getEnclosedElements()) {
                        AnnotationMirror generateStubAnnotation = getAnnotation(e, generateStub.asType());
                        if (generateStubAnnotation != null) {
                            extractStubs(targetVM, source, stubs, minimumFeatureGetters, (ExecutableElement) e, generateStubAnnotation, List.of(generateStubAnnotation));
                        }
                        AnnotationMirror generateStubsAnnotation = getAnnotation(e, generateStubs.asType());
                        if (generateStubsAnnotation != null) {
                            List<AnnotationMirror> values = getAnnotationValueList(generateStubsAnnotation, "value", AnnotationMirror.class);
                            extractStubs(targetVM, source, stubs, minimumFeatureGetters, (ExecutableElement) e, generateStubsAnnotation, values);
                        }
                    }
                    classes.add(new GenerateStubClass(source, stubs, minimumFeatureGetters.keySet()));
                }
                if (!malformedInput) {
                    createStubs(this, targetVM, (TypeElement) holder, classes);
                }
            }
        }
        return true;
    }

    private void extractStubs(TargetVM targetVM,
                    TypeElement source,
                    ArrayList<GenerateStub> stubs,
                    HashMap<MinimumFeaturesGetter, MinimumFeaturesGetter> minimumFeatureGetters,
                    ExecutableElement method,
                    AnnotationMirror annotation,
                    List<AnnotationMirror> generateStubAnnotations) {
        if (getAnnotation(method, nodeIntrinsic.asType()) == null) {
            String msg = String.format("methods annotated with %s must also be annotated with %s", annotation, nodeIntrinsic);
            env().getMessager().printMessage(Diagnostic.Kind.ERROR, msg, method, annotation);
            malformedInput = true;
        }
        RuntimeCheckedFlagsMethod rtc = findRuntimeCheckedFlagsVariant(this, source, method, annotation);
        for (AnnotationMirror generateStubAnnotationValue : generateStubAnnotations) {
            MinimumFeaturesGetter minimumFeaturesGetter = extractMinimumFeaturesGetter(targetVM, minimumFeatureGetters, generateStubAnnotationValue);
            stubs.add(new GenerateStub(generateStubAnnotationValue, method, rtc, minimumFeaturesGetter));
        }
    }

    private static MinimumFeaturesGetter extractMinimumFeaturesGetter(TargetVM targetVM, HashMap<MinimumFeaturesGetter, MinimumFeaturesGetter> minimumFeatureGetters, AnnotationMirror genStub) {
        if (targetVM == TargetVM.substrate) {
            String amd64Getter = getAnnotationValue(genStub, "minimumCPUFeaturesAMD64", String.class);
            String aarch64Getter = getAnnotationValue(genStub, "minimumCPUFeaturesAARCH64", String.class);
            if (!amd64Getter.isEmpty() || !aarch64Getter.isEmpty()) {
                MinimumFeaturesGetter minimumFeaturesGetter = new MinimumFeaturesGetter(amd64Getter, aarch64Getter);
                MinimumFeaturesGetter existing = minimumFeatureGetters.putIfAbsent(minimumFeaturesGetter, minimumFeaturesGetter);
                return existing == null ? minimumFeaturesGetter : existing;
            }
        }
        return null;
    }

    private RuntimeCheckedFlagsMethod findRuntimeCheckedFlagsVariant(AbstractProcessor processor, TypeElement clazz, ExecutableElement method, AnnotationMirror annotation) {
        for (Element e : clazz.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD && processor.getAnnotation(e, nodeIntrinsic.asType()) != null) {
                ExecutableElement cur = (ExecutableElement) e;
                RuntimeCheckedFlagsMethod runtimeCheckedFlagsVariant = checkRuntimeCheckedFlagsVariant(method, cur);
                if (runtimeCheckedFlagsVariant != null) {
                    return runtimeCheckedFlagsVariant;
                }
            }
        }
        processor.env().getMessager().printMessage(Diagnostic.Kind.ERROR, method + ": Could not find runtime checked flags variant. " +
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

    private void createStubs(AbstractProcessor processor, TargetVM targetVM, TypeElement holder, ArrayList<GenerateStubClass> classes) {
        PackageElement pkg = (PackageElement) holder.getEnclosingElement();
        String genClassName = holder.getSimpleName() + "Gen";
        String pkgQualifiedName = pkg.getQualifiedName().toString();
        String qualifiedGenClassName = pkgQualifiedName + "." + genClassName;
        Set<String> uniqueNames = new LinkedHashSet<>();
        try {
            JavaFileObject factory = processor.env().getFiler().createSourceFile(qualifiedGenClassName, holder);
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
                                        "com.oracle.svm.core.SubstrateTargetDescription",
                                        "com.oracle.svm.core.Uninterruptible",
                                        "com.oracle.svm.core.snippets.SubstrateForeignCallTarget",
                                        "com.oracle.svm.core.cpufeature.Stubs",
                                        "com.oracle.svm.graal.RuntimeCPUFeatureRegion",
                                        "jdk.graal.compiler.api.replacements.Fold",
                                        "jdk.graal.compiler.debug.GraalError",
                                        "org.graalvm.nativeimage.ImageSingletons",
                                        "java.util.EnumSet",
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
                    }
                }
                for (String i : imports) {
                    int lastDot = i.lastIndexOf('.');
                    if (pkgQualifiedName.length() != lastDot || !i.startsWith(pkgQualifiedName)) {
                        out.printf("import %s;\n", i);
                    }
                }
                out.printf("\n");
                out.println("// generated by: IntrinsicStubProcessor");
                out.printf("public class %s ", genClassName);
                if (targetVM == TargetVM.hotspot) {
                    out.printf("extends SnippetStub ");
                }
                out.printf("{\n");
                if (targetVM == TargetVM.hotspot) {
                    out.printf("\n");
                    out.printf("    public %s(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {\n", genClassName);
                    out.printf("        super(linkage.getDescriptor().getName(), options, providers, linkage);\n");
                    out.printf("    }\n");
                } else {
                    out.printf("    @SuppressWarnings(\"unused\") private static final EnumSet<AMD64.CPUFeature> EMPTY_CPU_FEATURES_AMD64 = EnumSet.noneOf(AMD64.CPUFeature.class);\n");
                    out.printf("    @SuppressWarnings(\"unused\") private static final EnumSet<AArch64.CPUFeature> EMPTY_CPU_FEATURES_AARCH64 = EnumSet.noneOf(AArch64.CPUFeature.class);\n");
                }
                out.printf("\n");
                for (GenerateStubClass genClass : classes) {
                    if (targetVM == TargetVM.substrate) {
                        int n = 0;
                        for (MinimumFeaturesGetter featuresGetter : genClass.featureGetters) {
                            featuresGetter.setName(String.format("%s_getMinimumFeatures%s", genClass.clazz.getSimpleName(), n++ > 0 ? "_" + n : ""));
                            out.printf("    @Fold\n");
                            out.printf("    public static EnumSet<?> %s() {\n", featuresGetter.getName());
                            out.printf("        Architecture arch = ImageSingletons.lookup(SubstrateTargetDescription.class).arch;\n");
                            out.printf("        if (arch instanceof jdk.vm.ci.amd64.AMD64) {\n");
                            if (featuresGetter.amd64Getter.isEmpty()) {
                                out.printf("            return EMPTY_CPU_FEATURES_AMD64;\n");
                            } else {
                                out.printf("            return %s.%s();\n", genClass.clazz.getSimpleName(), featuresGetter.amd64Getter);
                            }
                            out.printf("        }\n");
                            out.printf("        if (arch instanceof jdk.vm.ci.aarch64.AArch64) {\n");
                            if (featuresGetter.aarch64Getter.isEmpty()) {
                                out.printf("            return EMPTY_CPU_FEATURES_AARCH64;\n");
                            } else {
                                out.printf("            return %s.%s();\n", genClass.clazz.getSimpleName(), featuresGetter.aarch64Getter);
                            }
                            out.printf("        }\n");
                            out.printf("        throw GraalError.unsupportedArchitecture(arch);\n");
                            out.printf("    }\n");
                            out.printf("\n");

                        }
                    }
                    for (GenerateStub gen : genClass.stubs) {
                        String name = getAnnotationValue(gen.annotation, "name", String.class);
                        if (name.isEmpty()) {
                            name = gen.method.getSimpleName().toString();
                        }
                        if (!uniqueNames.add(name)) {
                            processor.env().getMessager().printMessage(Diagnostic.Kind.ERROR, "duplicate stub name: " + name, gen.method, gen.annotation);
                        }
                        List<String> params = getAnnotationValueList(gen.annotation, "parameters", String.class);
                        Name className = genClass.clazz.getSimpleName();
                        switch (targetVM) {
                            case hotspot:
                                generateStub(targetVM, out, className, name, params, gen.method, null, 0);
                                break;
                            case substrate:
                                if (gen.minimumFeaturesGetter == null) {
                                    generateStub(targetVM, out, className, name, params, gen.method, null,
                                                    0);
                                } else {
                                    generateStub(targetVM, out, className, name, params,
                                                    gen.runtimeCheckedFlagsMethod.method,
                                                    gen.minimumFeaturesGetter.getName() + "()",
                                                    gen.runtimeCheckedFlagsMethod.runtimeCheckedFlagsParameterIndex);
                                }
                                generateStub(targetVM, out, className, name + "RTC", params,
                                                gen.runtimeCheckedFlagsMethod.method,
                                                String.format("Stubs.getRuntimeCheckedCPUFeatures(%s.class)", className),
                                                gen.runtimeCheckedFlagsMethod.runtimeCheckedFlagsParameterIndex);
                                break;
                        }
                    }
                }
                out.printf("}\n");
            }
        } catch (IOException e) {
            processor.env().getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }
    }

    private void generateStub(TargetVM targetVM, PrintWriter out, Name className, String methodName, List<String> params, ExecutableElement m, String runtimeCheckedFeatures,
                    int runtimeCheckedFeaturesParameterIndex) {
        out.printf("    // method: %s.%s\n", className, removePackageNames(m.toString()));
        if (runtimeCheckedFeatures != null) {
            out.println("    // runtime-checked CPU features variant, i.e. variant compiled with CPU features not present in the feature set selected by the -march option");
        }
        switch (targetVM) {
            case hotspot:
                out.printf("    @Snippet\n");
                break;
            case substrate:
                out.printf("    @Uninterruptible(reason = \"Must not do a safepoint check.\")\n");
                out.printf("    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)\n");
                break;
        }
        out.printf("    private static %s %s(", m.getReturnType(), methodName);
        out.printf(m.getParameters().stream().filter(p -> getAnnotation(p, constantNodeParameter) == null).map(p -> p.asType() + " " + p.getSimpleName()).collect(Collectors.joining(", ")));
        out.printf(") {\n");
        if (runtimeCheckedFeatures != null) {
            out.printf("        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(%s);\n", runtimeCheckedFeatures);
            out.printf("        try {\n    ");
        }
        out.printf("        %s%s.%s(", m.getReturnType().getKind() == VOID ? "" : "return ", className, m.getSimpleName());
        int iConst = 0;
        List<? extends VariableElement> parameters = m.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement p = parameters.get(i);
            if (i > 0) {
                out.printf(", ");
            }
            if (getAnnotation(p, constantNodeParameter) == null) {
                out.printf(p.getSimpleName().toString());
            } else {
                if (runtimeCheckedFeatures != null && i == runtimeCheckedFeaturesParameterIndex) {
                    out.printf(runtimeCheckedFeatures);
                } else {
                    if (!p.asType().getKind().isPrimitive()) {
                        out.printf("%s.", asTypeElement(p.asType()).getSimpleName());
                    }
                    out.printf(params.get(iConst++));
                }
            }
        }
        out.printf(");\n");
        if (runtimeCheckedFeatures != null) {
            out.printf("        } finally {\n");
            out.printf("            region.leave();\n");
            out.printf("        }\n");
        }
        out.printf("    }\n");
        out.printf("\n");
    }

    private static String removePackageNames(String signature) {
        StringBuilder sb = new StringBuilder(signature.length());
        int i = signature.indexOf('(') + 1;
        assert i > 0;
        sb.append(signature, 0, i);
        while (i < signature.length()) {
            int end = signature.indexOf(',', i);
            if (end < 0) {
                end = signature.length() - 1;
            }
            int start = signature.lastIndexOf('.', end);
            if (start < i) {
                start = i;
            } else {
                start++;
            }
            i = end + 1;
            sb.append(signature, start, i);
        }
        return sb.toString();
    }
}
