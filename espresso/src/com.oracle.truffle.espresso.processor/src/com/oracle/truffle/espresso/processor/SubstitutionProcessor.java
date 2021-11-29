/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.processor;

import com.oracle.truffle.espresso.processor.builders.ClassBuilder;
import com.oracle.truffle.espresso.processor.builders.IndentingStringBuilder;
import com.oracle.truffle.espresso.processor.builders.MethodBuilder;
import com.oracle.truffle.espresso.processor.builders.ModifierBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

public final class SubstitutionProcessor extends EspressoProcessor {
    // @EspressoSubstitutions
    private TypeElement espressoSubstitutions;
    // @Substitution
    private TypeElement substitutionAnnotation;

    // @JavaType
    private TypeElement javaType;
    // NoProvider.class
    private TypeElement noProvider;
    // NoFilter.class
    private TypeElement noFilter;

    // StaticObject
    private TypeElement staticObjectElement;

    // region Various String constants.

    private static final String SUBSTITUTION_PACKAGE = "com.oracle.truffle.espresso.substitutions";

    private static final String ESPRESSO_SUBSTITUTIONS = SUBSTITUTION_PACKAGE + "." + "EspressoSubstitutions";
    private static final String SUBSTITUTION = SUBSTITUTION_PACKAGE + "." + "Substitution";
    private static final String STATIC_OBJECT = "com.oracle.truffle.espresso.runtime.StaticObject";
    private static final String JAVA_TYPE = SUBSTITUTION_PACKAGE + "." + "JavaType";
    private static final String NO_PROVIDER = SUBSTITUTION_PACKAGE + "." + "SubstitutionNamesProvider" + "." + "NoProvider";
    private static final String NO_FILTER = SUBSTITUTION_PACKAGE + "." + "VersionFilter" + "." + "NoFilter";

    private static final String SUBSTITUTOR = "JavaSubstitution";

    private static final String GET_METHOD_NAME = "getMethodNames";
    private static final String SUBSTITUTION_CLASS_NAMES = "substitutionClassNames";
    private static final String VERSION_FILTER_METHOD = "isValidFor";
    private static final String JAVA_VERSION = "com.oracle.truffle.espresso.runtime.JavaVersion";

    private static final String INSTANCE = "INSTANCE";

    public SubstitutionProcessor() {
        super(SUBSTITUTION_PACKAGE, SUBSTITUTOR);
    }

    static class SubstitutorHelper extends SubstitutionHelper {
        final String targetClassName;
        final String guestMethodName;
        final List<String> guestTypeNames;
        final String returnType;
        final boolean hasReceiver;
        final TypeMirror nameProvider;
        final TypeMirror versionFilter;

        SubstitutorHelper(EspressoProcessor processor, Element target, String targetClassName, String guestMethodName, List<String> guestTypeNames, String returnType,
                        boolean hasReceiver, TypeMirror nameProvider, TypeMirror versionFilter) {
            super(processor, target, processor.getTypeElement(SUBSTITUTION));
            this.targetClassName = targetClassName;
            this.guestMethodName = guestMethodName;
            this.guestTypeNames = guestTypeNames;
            this.returnType = returnType;
            this.hasReceiver = hasReceiver;
            this.nameProvider = nameProvider;
            this.versionFilter = versionFilter;
        }

    }

    private String extractInvocation(String className, int nParameters, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        if (helper.isNodeTarget()) {
            ExecutableElement nodeExecute = findNodeExecute(helper.getNodeTarget());
            String nodeMethodName = nodeExecute.getSimpleName().toString();
            str.append("this.node.").append(nodeMethodName).append("(");
        } else {
            String methodName = helper.getMethodTarget().getSimpleName().toString();
            str.append(className).append(".").append(methodName).append("(");
        }
        boolean first = true;
        for (int i = 0; i < nParameters; i++) {
            first = checkFirst(str, first);
            str.append(ARG_NAME).append(i);
        }
        first = appendInvocationMetaInformation(str, first, helper);
        str.append(");\n");
        return str.toString();
    }

    private static String generateParameterTypes(List<String> types, int tabulation) {
        IndentingStringBuilder sb = new IndentingStringBuilder(0);
        sb.appendLine("new String[]{");
        sb.setIndentLevel(tabulation + 1);
        for (String type : types) {
            sb.append('\"').append(type).appendLine("\",");
        }
        sb.lowerIndentLevel();
        sb.append('}');
        return sb.toString();
    }

    private void processElement(Element substitution) {
        assert substitution.getKind() == ElementKind.CLASS;
        TypeElement typeElement = (TypeElement) substitution;

        // Extract the class name. (Of the form Target_[...]).
        String className = typeElement.getSimpleName().toString();
        // Extract the default name provider, if it is specified.
        TypeMirror defaultNameProvider = getNameProvider(getAnnotation(substitution, espressoSubstitutions));

        for (Element element : substitution.getEnclosedElements()) {
            processSubstitution(element, className, defaultNameProvider);
        }
    }

    private void checkParameterOrReturnType(String headerMessage, TypeMirror typeMirror, Element element) {
        if (typeMirror.getKind().isPrimitive()) {
            if (getAnnotation(typeMirror, javaType) != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                headerMessage + " (primitive type) cannot be annotated with @JavaType", element);
            }
        } else if (typeMirror.getKind() != TypeKind.VOID) {
            // Reference type.
            if (!processingEnv.getTypeUtils().isSameType(typeMirror, staticObjectElement.asType())) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                headerMessage + " is not of type StaticObject", element);
            }
            AnnotationMirror javaTypeMirror = getAnnotation(typeMirror, javaType);
            if (javaTypeMirror == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                headerMessage + " must be annotated with e.g. @JavaType(String.class) according to the substituted method signature", element);
            }
        }
    }

    private void checkInjectedParameter(String headerMessage, TypeMirror typeMirror, Element element) {
        AnnotationMirror injectMirror = getAnnotation(typeMirror, inject);
        if (injectMirror == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            headerMessage + " must be annotated with @Inject", element);
        }

        List<TypeElement> allowedTypes = Arrays.asList(meta, substitutionProfiler, espressoContext);
        boolean unsupportedType = allowedTypes.stream().noneMatch(allowedType -> env().getTypeUtils().isSameType(typeMirror, allowedType.asType()));
        if (unsupportedType) {
            String allowedNames = allowedTypes.stream().map(t -> t.getSimpleName().toString()).collect(Collectors.joining(", "));
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                            headerMessage + " type not supported, allowed types: " + allowedNames, element);
        }
    }

    private void checkTargetMethod(ExecutableElement targetElement) {
        for (VariableElement param : targetElement.getParameters()) {
            if (isActualParameter(param)) {
                checkParameterOrReturnType("Substitution parameter", param.asType(), param);
            } else {
                checkInjectedParameter("Substitution parameter", param.asType(), param);
            }
        }
        checkParameterOrReturnType("Substitution return type", targetElement.getReturnType(), targetElement);
    }

    private void checkSubstitutionElement(Element element) {
        if (element.getKind() == ElementKind.METHOD) {
            ExecutableElement methodElement = (ExecutableElement) element;
            Set<Modifier> modifiers = methodElement.getModifiers();
            if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.PROTECTED)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Substitution method cannot be private nor protected", element);
            }
            if (!modifiers.contains(Modifier.STATIC)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Substitution method must be static", element);
            }
            checkTargetMethod(methodElement);
        }
        if (element.getKind() == ElementKind.CLASS) {
            TypeElement typeElement = (TypeElement) element;
            Set<Modifier> modifiers = typeElement.getModifiers();
            if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.PROTECTED)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Substitution method cannot be private nor protected", element);
            }
            ExecutableElement targetMethod = findNodeExecute(typeElement);
            if (targetMethod != null) {
                checkTargetMethod(targetMethod);
            }
        }
    }

    /**
     * Converts from CamelCase to lowerCamelCase.
     */
    private static String toLowerCamelCase(String s) {
        if (s.isEmpty()) {
            return s;
        }
        int codePoint0 = s.codePointAt(0);
        String tail = s.substring(Character.charCount(codePoint0));
        return new StringBuilder(s.length()) //
                        .appendCodePoint(Character.toLowerCase(codePoint0)) //
                        .append(tail) //
                        .toString();
    }

    @Override
    protected String getSubstutitutedMethodName(Element targetElement) {
        AnnotationMirror subst = getAnnotation(targetElement, substitutionAnnotation);
        String name = getAnnotationValue(subst, "methodName", String.class);
        if (name.isEmpty()) {
            if (targetElement.getKind() == ElementKind.CLASS) {
                // If methodName is not specified, use camel case version of the class name.
                // e.g. IsNull node -> isNull.
                String elementName = targetElement.getSimpleName().toString();
                name = toLowerCamelCase(elementName);
            } else if (targetElement.getKind() == ElementKind.METHOD) {
                // If methodName is not specified, use the target method name.
                name = targetElement.getSimpleName().toString();
            } else {
                throw new AssertionError("Unexpected: " + targetElement);
            }
        }

        return name;
    }

    void processSubstitution(Element element, String className, TypeMirror defaultNameProvider) {
        assert element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CLASS;
        TypeElement declaringClass = (TypeElement) element.getEnclosingElement();
        String targetPackage = env().getElementUtils().getPackageOf(declaringClass).getQualifiedName().toString();

        // Find the methods annotated with @Substitution.
        AnnotationMirror subst = getAnnotation(element, substitutionAnnotation);
        if (subst != null) {

            // Sanity check.
            checkSubstitutionElement(element);

            // Obtain the name of the element to be substituted in.
            String targetMethodName = getSubstutitutedMethodName(element);

            /**
             * Obtain the actual target method to call in the substitution. This is the method that
             * will be called in the substitution: Either element itself, for method substitutions.
             * Or the execute* method of the Truffle node, for node substitutions.
             */
            ExecutableElement targetMethod = getTargetMethod(element);

            // Obtain the host types of the parameters
            List<String> espressoTypes = getEspressoTypes(targetMethod);

            // Spawn the name of the Substitutor we will create.
            String substitutorName = getSubstitutorClassName(className, element.getSimpleName().toString(), espressoTypes);

            String actualMethodName = getSubstutitutedMethodName(element);

            // Obtain the (fully qualified) guest types parameters of the element.
            List<String> guestTypes = getGuestTypes(targetMethod);

            // Obtain the hasReceiver() value from the @Substitution annotation.
            boolean hasReceiver = getAnnotationValue(subst, "hasReceiver", Boolean.class);

            // Obtain the fully qualified guest return type of the element.
            String returnType = getReturnTypeFromHost(targetMethod);

            TypeMirror nameProvider = getNameProvider(subst);
            nameProvider = nameProvider == null ? defaultNameProvider : nameProvider;

            TypeMirror versionFilter = getVersionFilter(subst);
            SubstitutorHelper helper = new SubstitutorHelper(this, element, className, actualMethodName, guestTypes, returnType, hasReceiver, nameProvider, versionFilter);

            // Create the contents of the source file
            String classFile = spawnSubstitutor(
                            substitutorName,
                            targetPackage,
                            className,
                            targetMethodName,
                            espressoTypes, helper);
            commitSubstitution(substitutionAnnotation, targetPackage, substitutorName, classFile);
        }
    }

    private TypeMirror getNameProvider(AnnotationMirror annotation) {
        TypeMirror provider = getAnnotationValue(annotation, "nameProvider", TypeMirror.class);
        if (provider != null) {
            if (!processingEnv.getTypeUtils().isSameType(provider, noProvider.asType())) {
                return provider;
            }
        }
        return null;
    }

    private TypeMirror getVersionFilter(AnnotationMirror annotation) {
        TypeMirror versionFilter = getAnnotationValue(annotation, "versionFilter", TypeMirror.class);
        if (versionFilter != null) {
            if (!processingEnv.getTypeUtils().isSameType(versionFilter, noFilter.asType())) {
                return versionFilter;
            }
        }
        return null;
    }

    String getReturnTypeFromHost(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        AnnotationMirror a = getAnnotation(returnType, javaType);
        if (a != null) {
            // The return type element points to the actual return type, not the specific usage,
            // passing the method as anchor for reporting errors instead.
            return getClassFromJavaType(a, method);
        }
        return getInternalName(returnType.toString());
    }

    private List<String> getGuestTypes(ExecutableElement inner) {
        ArrayList<String> parameterTypeNames = new ArrayList<>();
        for (VariableElement parameter : inner.getParameters()) {
            if (isActualParameter(parameter)) {
                AnnotationMirror mirror = getAnnotation(parameter.asType(), javaType);
                if (mirror != null) {
                    parameterTypeNames.add(getClassFromJavaType(mirror, parameter));
                } else {
                    // @JavaType annotation not found -> primitive or j.l.Object
                    // All StaticObject(s) parameters must be annotated with @JavaType.
                    if (processingEnv.getTypeUtils().isSameType(parameter.asType(), staticObjectElement.asType())) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "StaticObject parameters require the @JavaType annotation", parameter);
                    }
                    String arg = getInternalName(parameter.asType().toString());
                    parameterTypeNames.add(arg);
                }
            }
        }
        return parameterTypeNames;
    }

    static boolean isValidInternalType(String internalName) {
        if (internalName.isEmpty()) {
            return false;
        }
        if (internalName.length() == 1) {
            switch (internalName.charAt(0)) {
                case 'B': // byte
                case 'C': // char
                case 'D': // double
                case 'F': // float
                case 'I': // int
                case 'J': // long
                case 'S': // short
                case 'V': // void
                case 'Z': // boolean
                    return true;
                default:
                    return false;
            }
        }
        if (internalName.startsWith("[")) {
            return isValidInternalType(internalName.substring(1));
        }
        return internalName.length() >= 3 && internalName.startsWith("L") && internalName.endsWith(";");
    }

    /**
     * @param annotation @JavaType annotation
     * @param element element containing the @JavaType annotation for error reporting
     *
     * @return the fully qualified internal name of the guest class.
     */
    private String getClassFromJavaType(AnnotationMirror annotation, Element element) {
        String internalName = getAnnotationValue(annotation, "internalName", String.class);
        // .internalName overrides .value .
        if (internalName == null || internalName.isEmpty()) {
            TypeMirror value = getAnnotationValue(annotation, "value", TypeMirror.class);
            internalName = getInternalName(value.toString());
            // JavaType.value = JavaType.class is used as the "no type" type, forbid accidental
            // usages.
            if (processingEnv.getTypeUtils().isSameType(value, javaType.asType())) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Empty @JavaType, must specify a type", element, annotation);
            }
        }
        if (!isValidInternalType(internalName)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Invalid internalName: " + internalName, element, annotation);
        }
        // . is allowed in type names by the spec, as part of the name, not as separator.
        // This avoids a common error e.g. using . instead of / as separator.
        if (internalName.contains(".")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Invalid . in internalName: '" + internalName + "'. Use / instead e.g. Ljava/lang/String;", element, annotation);
        }
        return internalName;
    }

    private static String getArraySubstring(int nbDim) {
        char[] chars = new char[nbDim];
        Arrays.fill(chars, '[');
        return new String(chars);
    }

    /**
     * Given a qualified class name, returns the fully qualified internal name of the class.
     * 
     * In particular,
     * <li>This transforms primitives (boolean, int) to their JVM signature (Z, I).
     * <li>Replaces "." by "/" and, if not present, prepends a "L" an appends a ";" to reference
     * types (/ex: java.lang.Object -> Ljava/lang/Object;)
     * <li>If an array is passed, it is of the form "java.lang.Object[]" or "byte[][]". This
     * prepends the correct number of "[" before applying this function to the name without the
     * "[]". (/ex: byte[][] -> [[B)
     */
    private String getInternalName(String className) {
        int arrayStart = className.indexOf("[]");
        if (arrayStart != -1) {
            int nbDim = 0;
            boolean isOpen = false;
            for (int i = arrayStart; i < className.length(); i++) {
                if (isOpen) {
                    if (className.charAt(i) != ']') {
                        throw new IllegalArgumentException("Malformed class name: " + className);
                    }
                    nbDim++;
                } else {
                    if (className.charAt(i) != '[') {
                        throw new IllegalArgumentException("Malformed class name: " + className);
                    }
                }
                isOpen = !isOpen;
            }
            return getArraySubstring(nbDim) + getInternalName(className.substring(0, arrayStart));
        }

        if (className.startsWith("[") || className.endsWith(";")) {
            return className.replace('.', '/');
        }
        switch (className) {
            case "boolean":
                return "Z";
            case "byte":
                return "B";
            case "char":
                return "C";
            case "short":
                return "S";
            case "int":
                return "I";
            case "float":
                return "F";
            case "double":
                return "D";
            case "long":
                return "J";
            case "void":
                return "V";
        }
        // Reference type.
        return "L" + className.replace('.', '/') + ";";
    }

    private List<String> getEspressoTypes(ExecutableElement inner) {
        List<String> espressoTypes = new ArrayList<>();
        for (VariableElement parameter : inner.getParameters()) {
            if (isActualParameter(parameter)) {
                String arg = parameter.asType().toString();
                String result = extractSimpleType(arg);
                espressoTypes.add(result);
            }
        }
        return espressoTypes;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(ESPRESSO_SUBSTITUTIONS);
        return annotations;
    }

    @Override
    void processImpl(RoundEnvironment env) {
        // Set up the different annotations, along with their values, that we will need.
        this.espressoSubstitutions = getTypeElement(ESPRESSO_SUBSTITUTIONS);
        this.substitutionAnnotation = getTypeElement(SUBSTITUTION);
        this.staticObjectElement = getTypeElement(STATIC_OBJECT);
        this.javaType = getTypeElement(JAVA_TYPE);
        this.noProvider = getTypeElement(NO_PROVIDER);
        this.noFilter = getTypeElement(NO_FILTER);

        verifyAnnotationMembers(espressoSubstitutions, "value", "nameProvider");
        verifyAnnotationMembers(substitutionAnnotation, "methodName", "nameProvider", "versionFilter");
        verifyAnnotationMembers(javaType, "value", "internalName");

        // Actual process
        for (Element e : env.getElementsAnnotatedWith(espressoSubstitutions)) {
            if (!e.getModifiers().contains(Modifier.FINAL)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Enclosing class for substitutions must be final", e);
            }
            processElement(e);
        }
    }

    private void verifyAnnotationMembers(TypeElement annotation, String... methods) {
        List<Name> enclosedMethods = annotation.getEnclosedElements().stream().filter(e -> e.getKind() == ElementKind.METHOD).map(Element::getSimpleName).collect(Collectors.toList());

        for (String methodName : methods) {
            if (enclosedMethods.stream().noneMatch(em -> em.contentEquals(methodName))) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Annotation is missing member: " + methodName, annotation);
            }
        }
    }

    @Override
    List<String> expectedImports(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        List<String> expectedImports = new ArrayList<>();
        SubstitutorHelper h = (SubstitutorHelper) helper;
        expectedImports.add(substitutorPackage + "." + SUBSTITUTOR);
        if (parameterTypeName.contains("StaticObject") || h.returnType.equals("V")) {
            expectedImports.add(IMPORT_STATIC_OBJECT);
        }
        if (helper.isNodeTarget()) {
            expectedImports.add(helper.getNodeTarget().getQualifiedName().toString());
        }
        return expectedImports;
    }

    @Override
    ClassBuilder generateFactoryConstructor(ClassBuilder factoryBuilder, String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        SubstitutorHelper h = (SubstitutorHelper) helper;
        MethodBuilder factoryConstructor = new MethodBuilder(FACTORY) //
                        .asConstructor() //
                        .withModifiers(new ModifierBuilder().asPublic()) //
                        .addBodyLine("super(") //
                        .addIndentedBodyLine(1, ProcessorUtils.stringify(h.guestMethodName), ',') //
                        .addIndentedBodyLine(1, ProcessorUtils.stringify(h.targetClassName), ',') //
                        .addIndentedBodyLine(1, ProcessorUtils.stringify(h.returnType), ',') //
                        .addIndentedBodyLine(1, generateParameterTypes(h.guestTypeNames, 4), ',') //
                        .addIndentedBodyLine(1, h.hasReceiver) //
                        .addBodyLine(");");
        factoryBuilder.withMethod(factoryConstructor);

        if (h.nameProvider != null) {
            factoryBuilder.withMethod(generateGetMethodNames(h.guestMethodName, h));
            factoryBuilder.withMethod(generateSubstitutionClassNames(h));
        }

        if (h.versionFilter != null) {
            factoryBuilder.withMethod(generateIsValidFor(h));
        }

        return factoryBuilder;
    }

    private static MethodBuilder generateGetMethodNames(String targetMethodName, SubstitutorHelper h) {
        String nameProvider = h.nameProvider.toString().substring((SUBSTITUTION_PACKAGE + ".").length());
        MethodBuilder getMethodNamesMethod = new MethodBuilder(GET_METHOD_NAME) //
                        .withOverrideAnnotation() //
                        .withModifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withReturnType("String[]") //
                        .addBodyLine("return ", nameProvider, '.', INSTANCE, '.', GET_METHOD_NAME, '(', ProcessorUtils.stringify(targetMethodName), ");");
        return getMethodNamesMethod;
    }

    private static MethodBuilder generateSubstitutionClassNames(SubstitutorHelper h) {
        String nameProvider = h.nameProvider.toString().substring((SUBSTITUTION_PACKAGE + ".").length());
        MethodBuilder substitutionClassNamesMethod = new MethodBuilder(SUBSTITUTION_CLASS_NAMES) //
                        .withOverrideAnnotation() //
                        .withModifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withReturnType("String[]") //
                        .addBodyLine("return ", nameProvider, '.', INSTANCE, '.', SUBSTITUTION_CLASS_NAMES, "();");
        return substitutionClassNamesMethod;
    }

    private static MethodBuilder generateIsValidFor(SubstitutorHelper h) {
        String versionFilter = h.versionFilter.toString();
        MethodBuilder generateIsValidForMethod = new MethodBuilder(VERSION_FILTER_METHOD) //
                        .withOverrideAnnotation() //
                        .withModifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withReturnType("boolean") //
                        .withParams(JAVA_VERSION + " version") //
                        .addBodyLine("return ", versionFilter, '.', INSTANCE, '.', VERSION_FILTER_METHOD, "(version);");
        return generateIsValidForMethod;
    }

    @Override
    ClassBuilder generateInvoke(ClassBuilder classBuilder, String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        SubstitutorHelper h = (SubstitutorHelper) helper;
        MethodBuilder invoke = new MethodBuilder("invoke") //
                        .withOverrideAnnotation() //
                        .withModifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withParams("Object[] " + ARGS_NAME) //
                        .withReturnType("Object");
        int argIndex = 0;
        for (String argType : parameterTypeName) {
            invoke.addBodyLine(argType, " ", ARG_NAME, argIndex, " = ", castTo(ARGS_NAME + "[" + argIndex + "]", argType), ";");
            argIndex++;
        }
        if (h.returnType.equals("V")) {
            invoke.addBodyLine(extractInvocation(className, argIndex, helper).trim());
            invoke.addBodyLine("return StaticObject.NULL;");
        } else {
            invoke.addBodyLine("return ", extractInvocation(className, argIndex, helper).trim());
        }
        return classBuilder.withMethod(invoke);
    }
}
