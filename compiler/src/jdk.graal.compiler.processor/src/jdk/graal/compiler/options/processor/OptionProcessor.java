/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.options.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import jdk.graal.compiler.processor.AbstractProcessor;

/**
 * Processes static fields annotated with {@code Option}. The class in which one or more such fields
 * are declared can optionally implement {@code OptionsContainer}. An {@code OptionDescriptors}
 * subclass is generated for each option declaring class. The name of the generated class is the
 * name of the declaring class with each {@code "."} in the non-package part of the name replaced by
 * {@code "_"} followed by a {@code "_OptionDescriptors"} suffix. Examples:
 *
 * <pre>
 * declaring class                             | generated OptionDescriptors class
 * --------------------------------------------+--------------------------------------------------------------------
 * j.g.c.common.GraalOptions                   | j.g.c.common.GraalOptions_OptionDescriptors
 * j.g.c.common.inlining.InliningPhase.Options | j.g.c.common.inlining.InliningPhase_Options_OptionDescriptors
 * </pre>
 */
@SupportedAnnotationTypes({"jdk.graal.compiler.options.Option"})
public class OptionProcessor extends AbstractProcessor {

    private static final String OPTION_CLASS_NAME = "jdk.graal.compiler.options.Option";
    private static final String OPTION_KEY_CLASS_NAME = "jdk.graal.compiler.options.OptionKey";
    private static final String OPTION_TYPE_CLASS_NAME = "jdk.graal.compiler.options.OptionType";
    private static final String OPTION_STABILITY_CLASS_NAME = "jdk.graal.compiler.options.OptionStability";
    private static final String OPTION_DESCRIPTOR_CLASS_NAME = "jdk.graal.compiler.options.OptionDescriptor";
    private static final String OPTION_DESCRIPTORS_CLASS_NAME = "jdk.graal.compiler.options.OptionDescriptors";
    private static final String OPTIONS_CONTAINER_CLASS_NAME = "jdk.graal.compiler.options.OptionsContainer";

    private final Set<Element> processed = new LinkedHashSet<>();

    private TypeMirror optionTypeMirror;
    private TypeMirror optionKeyTypeMirror;

    private void processElement(Element element, OptionsDeclarer optionsDeclarer) {

        if (!element.getModifiers().contains(Modifier.STATIC)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field must be static", element);
            return;
        }
        if (element.getModifiers().contains(Modifier.PRIVATE)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field cannot be private", element);
            return;
        }

        AnnotationMirror annotation = getAnnotation(element, optionTypeMirror);
        assert annotation != null;
        assert element instanceof VariableElement;
        assert element.getKind() == ElementKind.FIELD;
        VariableElement field = (VariableElement) element;
        String fieldName = field.getSimpleName().toString();

        Types types = processingEnv.getTypeUtils();

        TypeMirror fieldType = field.asType();
        if (fieldType.getKind() != TypeKind.DECLARED) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field must be of type " + OPTION_KEY_CLASS_NAME, element);
            return;
        }
        DeclaredType declaredFieldType = (DeclaredType) fieldType;

        if (!types.isSubtype(fieldType, types.erasure(optionKeyTypeMirror))) {
            String msg = String.format("Option field type %s is not a subclass of %s", fieldType, optionKeyTypeMirror);
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, element);
            return;
        }

        if (!field.getModifiers().contains(Modifier.STATIC)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field must be static", element);
            return;
        }
        if (field.getModifiers().contains(Modifier.PRIVATE)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field cannot be private", element);
            return;
        }

        String optionName = getAnnotationValue(annotation, "name", String.class);
        if (optionName.isEmpty()) {
            optionName = fieldName;
        }

        if (!Character.isUpperCase(optionName.charAt(0))) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option name must start with an upper case letter", element);
            return;
        }

        DeclaredType declaredOptionKeyType = declaredFieldType;
        while (!types.isSameType(types.erasure(declaredOptionKeyType), types.erasure(optionKeyTypeMirror))) {
            List<? extends TypeMirror> directSupertypes = types.directSupertypes(declaredFieldType);
            assert !directSupertypes.isEmpty();
            declaredOptionKeyType = (DeclaredType) directSupertypes.getFirst();
        }

        assert !declaredOptionKeyType.getTypeArguments().isEmpty();
        String optionType = declaredOptionKeyType.getTypeArguments().getFirst().toString();
        if (optionType.startsWith("java.lang.")) {
            optionType = optionType.substring("java.lang.".length());
        }
        if (optionType.contains("<")) {
            optionType = optionType.substring(0, optionType.indexOf("<"));
        }

        Element enclosing = element.getEnclosingElement();
        String declaringClass = "";
        String separator = "";
        Set<Element> originatingElements = optionsDeclarer.originatingElements;
        originatingElements.add(field);
        PackageElement enclosingPackage = null;
        while (enclosing != null) {
            if (enclosing.getKind() == ElementKind.CLASS || enclosing.getKind() == ElementKind.INTERFACE || enclosing.getKind() == ElementKind.ENUM) {
                if (enclosing.getModifiers().contains(Modifier.PRIVATE)) {
                    String msg = String.format("Option field cannot be declared in a private %s %s", enclosing.getKind().name().toLowerCase(), enclosing);
                    processingEnv.getMessager().printMessage(Kind.ERROR, msg, element);
                    return;
                }
                originatingElements.add(enclosing);
                declaringClass = enclosing.getSimpleName() + separator + declaringClass;
                separator = ".";
            } else if (enclosing.getKind() == ElementKind.PACKAGE) {
                enclosingPackage = (PackageElement) enclosing;
                break;
            } else {
                processingEnv.getMessager().printMessage(Kind.ERROR, "Unexpected enclosing element kind: " + enclosing.getKind(), element);
                return;
            }
            enclosing = enclosing.getEnclosingElement();
        }
        if (enclosingPackage == null || enclosingPackage.isUnnamed()) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field cannot be declared in the unnamed package", element);
            return;
        }

        String help = getAnnotationValue(annotation, "help", String.class);
        List<String> helpLines;
        if (help.startsWith("file:")) {
            String path = help.substring("file:".length());
            Filer filer = processingEnv.getFiler();
            try {
                FileObject file;
                try {
                    file = filer.getResource(StandardLocation.SOURCE_PATH, enclosingPackage.getQualifiedName(), path);
                } catch (IllegalArgumentException | IOException e) {
                    // Handle the case when a compiler doesn't support the SOURCE_PATH location
                    file = filer.getResource(StandardLocation.CLASS_OUTPUT, enclosingPackage.getQualifiedName(), path);
                }
                try (InputStream in = file.openInputStream()) {
                    help = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    helpLines = List.of(help.split("\\r?\\n"));
                }
            } catch (IOException e) {
                String msg = String.format("Error reading %s containing the help text for option field: %s", path, e);
                processingEnv.getMessager().printMessage(Kind.ERROR, msg, element);
                return;
            }
        } else {
            helpLines = List.of(help.split("\\n"));
        }

        String briefHelp = helpLines.getFirst();
        if (briefHelp.isEmpty()) {
            if (helpLines.size() > 1) {
                processingEnv.getMessager().printMessage(Kind.ERROR, "First line of multi-line help text cannot be empty", element);
                return;
            }
        } else {
            if (helpLines.size() > 1) {
                if (briefHelp.charAt(briefHelp.length() - 1) != '.' && !helpLines.get(1).isBlank()) {
                    processingEnv.getMessager().printMessage(Kind.ERROR,
                                    "First line of multi-line help text must end with a period or be followed by a blank line", element);
                    return;
                }
            }
            char firstChar = briefHelp.charAt(0);
            if (!Character.isUpperCase(firstChar)) {
                processingEnv.getMessager().printMessage(Kind.ERROR, "Option help text must start with an upper case letter", element);
                return;
            }
        }

        String stability = getAnnotationValue(annotation, "stability", VariableElement.class).getSimpleName().toString();
        if (stability.equals("STABLE")) {
            if (briefHelp.isEmpty()) {
                processingEnv.getMessager().printMessage(Kind.ERROR, "A stable option must have non-empty help text", element);
                return;
            }
        }

        String optionTypeName = getAnnotationValue(annotation, "type", VariableElement.class).getSimpleName().toString();
        if (!optionTypeName.equals("Debug")) {
            if (briefHelp.isEmpty()) {
                processingEnv.getMessager().printMessage(Kind.ERROR, "Non debug options must always have a option help text " + optionName);
            }
        }
        boolean deprecated = getAnnotationValue(annotation, "deprecated", Boolean.class);
        String deprecationMessage = getAnnotationValue(annotation, "deprecationMessage", String.class);
        OptionInfo info = new OptionInfo(optionName, optionTypeName, String.join("\n", helpLines), optionType, declaringClass, fieldName, stability, deprecated, deprecationMessage);
        optionsDeclarer.options.add(info);
    }

    private static String literal(String help) {
        String quoted = help.replace("\\", "\\\\").replace("\"", "\\\"");
        if (help.indexOf('\n') != -1) {
            return "\"\"\"\n" + quoted + "\"\"\"";
        }
        return "\"" + quoted + "\"";
    }

    static void createOptionsDescriptorsFile(ProcessingEnvironment processingEnv, OptionsDeclarer optionsDeclarer) {
        Element[] originatingElements = optionsDeclarer.originatingElements.toArray(new Element[0]);
        String optionsDescriptorsClassName = optionsDeclarer.getOptionDescriptorsClassName();
        Filer filer = processingEnv.getFiler();
        try (PrintWriter out = createSourceFile(optionsDeclarer.packageName, optionsDescriptorsClassName, filer, originatingElements)) {

            out.println("// CheckStyle: stop header check");
            out.println("// CheckStyle: stop line length check");
            out.println("// GENERATED CONTENT - DO NOT EDIT");
            out.println("// Source: " + optionsDeclarer.classNameWithoutPackage + ".java");
            out.println("package " + optionsDeclarer.packageName + ";");
            out.println("");
            out.println("import java.util.*;");
            out.println("import " + getPackageName(OPTION_DESCRIPTORS_CLASS_NAME) + ".*;");
            out.println("import " + OPTION_TYPE_CLASS_NAME + ";");
            out.println("import " + OPTION_STABILITY_CLASS_NAME + ";");
            out.println("");
            String implementsClause = " implements " + getSimpleName(OPTION_DESCRIPTORS_CLASS_NAME);

            out.println("public class " + optionsDescriptorsClassName + implementsClause + " {");

            String desc = getSimpleName(OPTION_DESCRIPTOR_CLASS_NAME);

            out.println("    private OptionsContainer cachedContainer;");
            out.println("    @Override");
            out.println("    public OptionsContainer getContainer() {");
            out.println("        if (cachedContainer == null) {");
            if (optionsDeclarer.implementsOptionsContainer) {
                out.printf("            cachedContainer = new %s();%n", optionsDeclarer.classNameWithoutPackage);
            } else {
                out.printf("            cachedContainer = new OptionsContainer.Default(%s.class);%n", optionsDeclarer.classNameWithoutPackage);
            }
            out.println("        }");
            out.println("        return cachedContainer;");
            out.println("    }");
            out.println();

            Collections.sort(optionsDeclarer.options);

            out.println("    @Override");
            out.println("    public OptionDescriptor get(String value) {");
            out.println("        switch (getContainer().unprefixed(value)) {");
            out.println("        // CheckStyle: stop line length check");
            for (OptionInfo option : optionsDeclarer.options) {
                String name = option.name;
                String optionField = option.declaringClass + "." + option.field;
                out.println("        case \"" + name + "\": {");
                String optionType = option.optionType;
                String type = option.type;
                String help = option.help;
                String fieldName = option.field;
                String stability = option.stability;
                boolean deprecated = option.deprecated;
                String deprecationMessage = option.deprecationMessage;
                out.printf("            return " + desc + ".create(\n");
                out.printf("                /*name*/ \"%s\",\n", name);
                out.printf("                /*optionType*/ %s.%s,\n", getSimpleName(OPTION_TYPE_CLASS_NAME), optionType);
                out.printf("                /*optionValueType*/ %s.class,\n", type);
                out.printf("                /*help*/ %s,\n", literal(help));
                out.printf("                /*container*/ getContainer(),\n");
                out.printf("                /*option*/ %s,\n", optionField);
                out.printf("                /*fieldName*/ \"%s\",\n", fieldName);
                out.printf("                /*stability*/ %s.%s,\n", getSimpleName(OPTION_STABILITY_CLASS_NAME), stability);
                out.printf("                /*deprecated*/ %b,\n", deprecated);
                out.printf("                /*deprecationMessage*/ \"%s\");\n", deprecationMessage);
                out.println("        }");
            }
            out.println("        // CheckStyle: resume line length check");
            out.println("        }");
            out.println("        return null;");
            out.println("    }");
            out.println();

            out.println("    @Override");
            out.println("    public Iterator<" + desc + "> iterator() {");
            out.println("        return new Iterator<" + (processingEnv.getSourceVersion().compareTo(SourceVersion.RELEASE_8) <= 0 ? desc : "") + ">() {");
            out.println("            int i = 0;");
            out.println("            @Override");
            out.println("            public boolean hasNext() {");
            out.println("                return i < " + optionsDeclarer.options.size() + ";");
            out.println("            }");
            out.println("            @Override");
            out.println("            public OptionDescriptor next() {");
            out.println("                switch (i++) {");
            for (int i = 0; i < optionsDeclarer.options.size(); i++) {
                OptionInfo option = optionsDeclarer.options.get(i);
                out.println("                    case " + i + ": return get(\"" + option.name + "\");");
            }
            out.println("                }");
            out.println("                throw new NoSuchElementException();");
            out.println("            }");
            out.println("        };");
            out.println("    }");
            out.println("}");
        }
    }

    /**
     * The details of a single option, derived from an {@code @Option} annotated field.
     */
    record OptionInfo(String name, String optionType, String help, String type,
                    String declaringClass, String field, String stability, boolean deprecated,
                    String deprecationMessage) implements Comparable<OptionInfo> {

        @Override
        public int compareTo(OptionInfo other) {
            return name.compareTo(other.name);
        }

        @Override
        public String toString() {
            return declaringClass + "." + field;
        }
    }

    /**
     * Metadata about a class declaring one or more options.
     *
     * @param element a class declaring one or more options
     * @param classNameWithoutPackage the name of the class without the package prefix but with the
     *            enclosing classes
     * @param packageName the package containing the class
     * @param implementsOptionsContainer specifies if the class implements OptionsContainer
     * @param options list for collecting info for each {@code Option} annotated field
     * @param originatingElements set for collecting the elements causally associated with the
     *            creation of the OptionDescriptors class
     */
    record OptionsDeclarer(Element element,
                    String classNameWithoutPackage,
                    String packageName,
                    boolean implementsOptionsContainer,
                    List<OptionInfo> options,
                    Set<Element> originatingElements) {

        static OptionsDeclarer ERROR = new OptionsDeclarer(null, null, null, false, null, null);
        static OptionsDeclarer create(ProcessingEnvironment env, Element optionsDeclarerElement, boolean implementsOptionsContainer) {
            Element e = optionsDeclarerElement;

            List<String> simpleNames = new ArrayList<>();
            while (e.getKind() != ElementKind.PACKAGE) {
                if (!e.getKind().isDeclaredType()) {
                    String message = String.format("Options enclosing element %s is not a declared type (%s)", e, e.getKind());
                    env.getMessager().printMessage(Kind.ERROR, message, optionsDeclarerElement);
                    return ERROR;
                }
                String simpleName = e.getSimpleName().toString();
                if (simpleName.indexOf('_') != -1) {
                    String message = String.format("Options enclosing element %s cannot have '_' in its name", e);
                    env.getMessager().printMessage(Kind.ERROR, message, optionsDeclarerElement);
                    return ERROR;
                }
                simpleNames.add(simpleName);
                e = e.getEnclosingElement();
            }
            String className = String.join(".", simpleNames.reversed());
            String packageName = ((PackageElement) e).getQualifiedName().toString();
            return new OptionsDeclarer(optionsDeclarerElement, className, packageName, implementsOptionsContainer, new ArrayList<>(), new LinkedHashSet<>());
        }

        String getOptionDescriptorsClassName() {
            return classNameWithoutPackage.replace('.', '_') + '_' + getSimpleName(OPTION_DESCRIPTORS_CLASS_NAME);
        }
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        TypeElement optionTypeElement = getTypeElement(OPTION_CLASS_NAME);
        TypeElement optionsContainerTypeElement = getTypeElement(OPTIONS_CONTAINER_CLASS_NAME);

        optionTypeMirror = optionTypeElement.asType();
        optionKeyTypeMirror = getTypeElement(OPTION_KEY_CLASS_NAME).asType();
        boolean ok = true;

        Map<Element, OptionsDeclarer> map = new LinkedHashMap<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(optionTypeElement)) {
            if (!processed.contains(element)) {
                processed.add(element);
                Element enclosingElement = element.getEnclosingElement();
                OptionsDeclarer optionsDeclarer = map.get(enclosingElement);
                if (optionsDeclarer == null) {
                    boolean implementsOptionsContainer = processingEnv.getTypeUtils().isAssignable(enclosingElement.asType(), optionsContainerTypeElement.asType());
                    optionsDeclarer = OptionsDeclarer.create(processingEnv, enclosingElement, implementsOptionsContainer);
                    map.put(enclosingElement, optionsDeclarer);
                }
                if (!element.getEnclosingElement().getSimpleName().toString().endsWith("Options")) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Option declaring classes must have a name that ends with 'Options'", element.getEnclosingElement());
                }
                if (optionsDeclarer != OptionsDeclarer.ERROR) {
                    processElement(element, optionsDeclarer);
                } else {
                    ok = false;
                }
            }
        }

        if (ok) {
            for (OptionsDeclarer optionsDeclarer : map.values()) {
                createOptionsDescriptorsFile(processingEnv, optionsDeclarer);
            }
        }

        return true;
    }
}
