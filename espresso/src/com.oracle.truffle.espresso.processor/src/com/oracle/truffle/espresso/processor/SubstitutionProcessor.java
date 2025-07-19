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

import java.util.ArrayList;
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
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import com.oracle.truffle.espresso.processor.builders.ClassBuilder;
import com.oracle.truffle.espresso.processor.builders.FieldBuilder;
import com.oracle.truffle.espresso.processor.builders.IndentingStringBuilder;
import com.oracle.truffle.espresso.processor.builders.MethodBuilder;
import com.oracle.truffle.espresso.processor.builders.ModifierBuilder;
import com.oracle.truffle.espresso.processor.builders.StatementBuilder;

public final class SubstitutionProcessor extends EspressoProcessor {
    // @EspressoSubstitutions
    private TypeElement espressoSubstitutions;
    // @Substitution
    private TypeElement substitutionAnnotation;
    // @InlineInBytecode
    private TypeElement inlineInBytecodeAnnotation;

    // @JavaType
    private TypeElement javaType;
    // NoProvider.class
    private TypeElement noProvider;

    // InlinedMethodPredicate.class
    private TypeElement noPredicate;

    // region Various String constants.

    private static final String SUBSTITUTION_PACKAGE = "com.oracle.truffle.espresso.substitutions";

    private static final String ESPRESSO_SUBSTITUTIONS = SUBSTITUTION_PACKAGE + "." + "EspressoSubstitutions";
    private static final String SUBSTITUTION = SUBSTITUTION_PACKAGE + "." + "Substitution";
    private static final String INLINE_IN_BYTECODE = SUBSTITUTION_PACKAGE + "." + "InlineInBytecode";
    private static final String JAVA_TYPE = SUBSTITUTION_PACKAGE + "." + "JavaType";
    private static final String NO_PROVIDER = SUBSTITUTION_PACKAGE + "." + "SubstitutionNamesProvider" + "." + "NoProvider";

    private static final String SUBSTITUTOR = "JavaSubstitution";

    private static final String GET_METHOD_NAME = "getMethodNames";
    private static final String SUBSTITUTION_CLASS_NAMES = "substitutionClassNames";

    private static final String INSTANCE = "INSTANCE";

    private static final String VIRTUAL_FRAME_IMPORT = "com.oracle.truffle.api.frame.VirtualFrame";
    private static final String ESPRESSO_FRAME = "EspressoFrame";
    private static final String ESPRESSO_FRAME_IMPORT = "com.oracle.truffle.espresso.nodes.EspressoFrame";
    private static final String INLINED_FRAME_ACCESS = "InlinedFrameAccess";
    private static final String INLINED_FRAME_ACCESS_IMPORT = "com.oracle.truffle.espresso.nodes.quick.invoke.inline." + INLINED_FRAME_ACCESS;
    private static final String INLINED_METHOD_PREDICATE = "InlinedMethodPredicate";
    private static final String INLINED_METHOD_PREDICATE_IMPORT = "com.oracle.truffle.espresso.nodes.quick.invoke.inline." + INLINED_METHOD_PREDICATE;

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
        final TypeMirror languageFilter;
        final boolean inlineInBytecode;
        final TypeMirror guardValue;
        final byte flags;
        final TypeMirror group;

        SubstitutorHelper(EspressoProcessor processor, Element target, String targetClassName, String guestMethodName, List<String> guestTypeNames, String returnType,
                        boolean hasReceiver, TypeMirror nameProvider, TypeMirror languageFilter, boolean inlineInBytecode, TypeMirror guardValue, TypeElement substitutionClass,
                        byte flags, TypeMirror group) {
            super(processor, target, processor.getTypeElement(SUBSTITUTION), substitutionClass);
            this.targetClassName = targetClassName;
            this.guestMethodName = guestMethodName;
            this.guestTypeNames = guestTypeNames;
            this.returnType = returnType;
            this.hasReceiver = hasReceiver;
            this.nameProvider = nameProvider;
            this.languageFilter = languageFilter;
            this.inlineInBytecode = inlineInBytecode;
            this.guardValue = guardValue;
            this.flags = flags;
            this.group = group;
        }

        @Override
        public TypeMirror getCollectTarget() {
            return group;
        }
    }

    private String extractInvocation(int nParameters, SubstitutorHelper helper) {
        StringBuilder str = new StringBuilder();
        if (helper.isNodeTarget()) {
            ExecutableElement nodeExecute = findNodeExecute(helper.getNodeTarget());
            String nodeMethodName = nodeExecute.getSimpleName().toString();
            str.append("this.node.").append(nodeMethodName).append("(");
        } else {
            String methodName = helper.getMethodTarget().getSimpleName().toString();
            str.append(helper.getEnclosingClass().getQualifiedName().toString()).append(".").append(methodName).append("(");
        }
        boolean first = true;
        for (int i = 0; i < nParameters; i++) {
            first = checkFirst(str, first);
            str.append(ARG_NAME).append(i);
        }
        first = appendInvocationMetaInformation(str, first, helper);
        str.append(")");
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
        AnnotationMirror annotation = getAnnotation(substitution, espressoSubstitutions);
        assert annotation != null;
        String className = typeElement.getSimpleName().toString();

        // Extract the class name. (Of the form Target_[...]).
        // Obtain the guest class that will be substituted.
        String targetClassName = className;
        if (className.startsWith("Target_")) {
            // Simple default case: substitution is using the "Target_" scheme.
            targetClassName = "L" + className.substring("Target_".length()).replace("_", "/") + ";";
        }
        int successfulScheme = 0;
        // If it exists, collect the value of EspressoSubstitutions.value()
        TypeMirror targetClass = getAnnotationValue(annotation, "value", TypeMirror.class);
        assert targetClass != null; // Default value is EspressoSubstitutions.class
        // If it exists, collect the value of EspressoSubstitutions.type()
        String targetType = getAnnotationValue(annotation, "type", String.class);

        if (!processingEnv.getTypeUtils().isSameType(targetClass, espressoSubstitutions.asType())) {
            targetClassName = "L" + targetClass.toString().replace(".", "/") + ";";
            successfulScheme++;
        }
        if (targetType != null && !targetType.isEmpty()) {
            targetClassName = targetType;
            successfulScheme++;
        }
        if (successfulScheme > 1) {
            throw new AssertionError("Both 'value' and 'type' are specified for @EspressoSubstitution " + className);
        }

        // Get the name provider. Will override the previously obtained target class name.
        TypeMirror defaultNameProvider = getNameProvider(annotation);

        // Thr group to be used for the @Collect annotation
        TypeMirror group = getAnnotationValue(annotation, "group", TypeMirror.class);

        for (Element element : substitution.getEnclosedElements()) {
            processSubstitution(element, className, defaultNameProvider, targetClassName, typeElement, group);
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
            if (!processingEnv.getTypeUtils().isSameType(typeMirror, staticObject.asType())) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                headerMessage + " is not of type StaticObject", element);
            }
            // @JavaType annotation check is done in SubstitutionProcessor.getGuestTypes
        }
    }

    private void checkInjectedParameter(String headerMessage, TypeMirror typeMirror, Element element) {
        AnnotationMirror injectMirror = getAnnotation(typeMirror, inject);
        if (injectMirror == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            headerMessage + " must be annotated with @Inject", element);
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

    void processSubstitution(Element element, String className, TypeMirror defaultNameProvider, String targetClassName, TypeElement substitutionClass, TypeMirror group) {
        assert element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CLASS;
        TypeElement declaringClass = (TypeElement) element.getEnclosingElement();
        String targetPackage = env().getElementUtils().getPackageOf(declaringClass).getQualifiedName().toString();

        // Class wide @InlineInBytecode annotation.
        AnnotationMirror classWideInline = getAnnotation(declaringClass, inlineInBytecodeAnnotation);

        // Find the methods annotated with @Substitution.
        AnnotationMirror subst = getAnnotation(element, substitutionAnnotation);
        if (subst != null) {

            // Sanity check.
            checkSubstitutionElement(element);

            // Obtain the name of the element to be substituted in.
            String targetMethodName = getSubstutitutedMethodName(element);

            /*
             * Obtain the actual target method to call in the substitution. This is the method that
             * will be called in the substitution: Either element itself, for method substitutions.
             * Or the execute method of the Truffle node, for node substitutions.
             */
            ExecutableElement targetMethod = getTargetMethod(element);

            // Obtain the host types of the parameters
            List<String> espressoTypes = getEspressoTypes(targetMethod);

            // Spawn the name of the Substitutor we will create.
            String substitutorName = getSubstitutorClassName(className, element.getSimpleName().toString(), espressoTypes);

            // Obtain the hasReceiver() value from the @Substitution annotation.
            boolean hasReceiver = getAnnotationValue(subst, "hasReceiver", Boolean.class);

            // Obtain the (fully qualified) guest types parameters of the element.
            List<String> guestTypes = getGuestTypes(targetMethod, hasReceiver);

            // Obtain the fully qualified guest return type of the element.
            String returnType = getReturnTypeFromHost(targetMethod);

            TypeMirror nameProvider = getNameProvider(subst);
            nameProvider = nameProvider == null ? defaultNameProvider : nameProvider;

            TypeMirror languageFilter = getLanguageFilter(subst);

            List<Byte> flagsList = getAnnotationValueList(subst, "flags", Byte.class);
            byte flags = 0;
            for (Byte flag : flagsList) {
                flags |= flag;
            }

            TypeMirror encodedInlineGuard = getInlineValue(classWideInline, element);
            boolean inlineInBytecode = encodedInlineGuard != null ||
                            // Implicit inlining of trivial substitutions.
                            isFlag(flags, SubstitutionFlag.IsTrivial);
            TypeMirror decodedInlineGuard = (encodedInlineGuard == null || processingEnv.getTypeUtils().isSameType(encodedInlineGuard, noPredicate.asType()))
                            ? null
                            : encodedInlineGuard;

            if (inlineInBytecode) {
                flags |= SubstitutionFlag.InlineInBytecode;
            }

            SubstitutorHelper helper = new SubstitutorHelper(this, element, targetClassName, targetMethodName, guestTypes, returnType, hasReceiver, nameProvider, languageFilter,
                            inlineInBytecode, decodedInlineGuard, substitutionClass, flags, group);

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

    private static TypeMirror getLanguageFilter(AnnotationMirror annotation) {
        return getAnnotationValue(annotation, "languageFilter", TypeMirror.class);
    }

    /**
     * Returns a tri-state String, depending on the return value:
     * <ul>
     * <li>If {@code null}: No bytecode-level inlining for this substitution.</li>
     * <li>If equals {@code noPredicate}: No guard for a bytecode-level inlined substitution.</li>
     * <li>Else: Guarded bytecode-level inlining for this substitution.</li>
     * </ul>
     */
    private TypeMirror getInlineValue(AnnotationMirror classWideAnnotation, Element element) {
        AnnotationMirror inline = getAnnotation(element, inlineInBytecodeAnnotation);
        inline = (inline == null) ? classWideAnnotation : inline;
        if (inline == null) {
            // No bytecode-level inlining.
            return null;
        }
        return getAnnotationValue(inline, "guard", TypeMirror.class);
    }

    String getReturnTypeFromHost(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        AnnotationMirror a = getAnnotation(returnType, javaType);
        if (a != null) {
            // The return type element points to the actual return type, not the specific usage,
            // passing the method as anchor for reporting errors instead.
            return getClassFromJavaType(a, method);
        }
        return getInternalName(returnType);
    }

    private List<String> getGuestTypes(ExecutableElement inner, boolean hasReceiver) {
        ArrayList<String> parameterTypeNames = new ArrayList<>();
        boolean isReceiver = hasReceiver;
        for (VariableElement parameter : inner.getParameters()) {
            if (isActualParameter(parameter)) {
                AnnotationMirror mirror = getAnnotation(parameter.asType(), javaType);
                if (mirror != null) {
                    parameterTypeNames.add(getClassFromJavaType(mirror, parameter));
                } else {
                    // @JavaType annotation not found -> primitive or j.l.Object
                    // All StaticObject(s) parameters must be annotated with @JavaType.
                    if (!isReceiver && processingEnv.getTypeUtils().isSameType(parameter.asType(), staticObject.asType())) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "non-receiver StaticObject parameters require the @JavaType annotation", parameter);
                    }
                    String arg = getInternalName(parameter.asType());
                    parameterTypeNames.add(arg);
                }
                isReceiver = false;
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
            internalName = getInternalName(value);
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

    /**
     * Given a type, returns its fully qualified internal name.
     * 
     * In particular,
     * <li>Primitives (boolean, int) use their JVM signature (Z, I).
     * <li>Use "/" rather than "." to separate packages (/ex: java.lang.Object ->
     * Ljava/lang/Object;)
     * <li>Array types use "[" followed by the internal name of the component type.
     */
    private String getInternalName(TypeMirror type) {
        int arrayDims = 0;
        TypeMirror elementalType = type;
        while (elementalType.getKind() == TypeKind.ARRAY) {
            elementalType = ((ArrayType) elementalType).getComponentType();
            arrayDims += 1;
        }

        if (arrayDims == 0) {
            return getNonArrayInternalName(type);
        }
        StringBuilder sb = new StringBuilder();
        sb.repeat('[', arrayDims);
        sb.append(getNonArrayInternalName(elementalType));
        return sb.toString();
    }

    private String getNonArrayInternalName(TypeMirror type) {
        TypeKind typeKind = type.getKind();
        assert typeKind != TypeKind.ARRAY;
        if (typeKind.isPrimitive() || typeKind == TypeKind.VOID) {
            return switch (typeKind) {
                case BOOLEAN -> "Z";
                case BYTE -> "B";
                case CHAR -> "C";
                case SHORT -> "S";
                case INT -> "I";
                case FLOAT -> "F";
                case DOUBLE -> "D";
                case LONG -> "J";
                case VOID -> "V";
                default -> throw new IllegalStateException("Unexpected primitive type kind: " + typeKind);
            };
        }
        if (typeKind != TypeKind.DECLARED) {
            throw new IllegalStateException("Unexpected type kind: " + typeKind);
        }
        Element element = processingEnv.getTypeUtils().asElement(type);
        Name binaryName = processingEnv.getElementUtils().getBinaryName((TypeElement) element);
        StringBuilder sb = new StringBuilder();
        sb.append("L").append(binaryName).append(';');
        int idx = sb.indexOf(".", 1);
        while (idx >= 0) {
            sb.setCharAt(idx, '/');
            idx = sb.indexOf(".", idx + 1);
        }
        return sb.toString();
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
        this.inlineInBytecodeAnnotation = getTypeElement(INLINE_IN_BYTECODE);
        this.javaType = getTypeElement(JAVA_TYPE);
        this.noProvider = getTypeElement(NO_PROVIDER);
        this.noPredicate = getTypeElement(INLINED_METHOD_PREDICATE_IMPORT);

        verifyAnnotationMembers(espressoSubstitutions, "value", "nameProvider");
        verifyAnnotationMembers(substitutionAnnotation, "methodName", "nameProvider", "languageFilter");
        verifyAnnotationMembers(inlineInBytecodeAnnotation, "guard");
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
        if (h.inlineInBytecode) {
            expectedImports.add(VIRTUAL_FRAME_IMPORT);
            expectedImports.add(INLINED_FRAME_ACCESS_IMPORT);
            if (!parameterTypeName.isEmpty()) {
                expectedImports.add(ESPRESSO_FRAME_IMPORT);
            }
        }
        if (parameterTypeName.contains("StaticObject") || h.returnType.equals("V")) {
            expectedImports.add(IMPORT_STATIC_OBJECT);
        }
        if (helper.isNodeTarget()) {
            expectedImports.add(helper.getNodeTarget().getQualifiedName().toString());
        }
        return expectedImports;
    }

    @Override
    FieldBuilder generateFactoryConstructor(FieldBuilder factoryBuilder, String substitutorName, String factoryType, String substitutorType, String targetMethodName, List<String> parameterTypeName,
                    SubstitutionHelper helper) {
        /*- Calls:
            new Factory(Object methodName,
                        Object substitutionClassName,
                        String returnType,
                        String[] parameterTypes,
                        boolean hasReceiver,
                        LanguageFilter filter,
                        byte flags,
                        InlinedMethodPredicate guard,
                        Supplier<? extends JavaSubstitution> factory);
        }
         */
        SubstitutorHelper h = (SubstitutorHelper) helper;
        StatementBuilder declaration = new StatementBuilder();
        declaration.addContent("new ", factoryType, "(").addLine().raiseIndent();
        if (h.nameProvider == null) {
            declaration.addContent(ProcessorUtils.stringify(h.guestMethodName), ',').addLine();
            declaration.addContent(ProcessorUtils.stringify(h.targetClassName), ',').addLine();
        } else {
            declaration.addContent(h.nameProvider, '.', INSTANCE, '.', GET_METHOD_NAME, '(', ProcessorUtils.stringify(h.guestMethodName), "),").addLine();
            declaration.addContent(h.nameProvider, '.', INSTANCE, '.', SUBSTITUTION_CLASS_NAMES, "(),").addLine();
        }
        declaration.addContent(ProcessorUtils.stringify(h.returnType), ",").addLine();
        declaration.addContent(generateParameterTypes(h.guestTypeNames, 4), ',').addLine();
        declaration.addContent(h.hasReceiver, ',').addLine();
        declaration.addContent(h.languageFilter, '.', INSTANCE, ',').addLine();
        declaration.addContent("(byte) ", h.flags, ',').addLine();
        declaration.addContent(h.guardValue != null ? (h.guardValue + "." + INSTANCE) : "null", ',').addLine();
        declaration.addContent(substitutorName + "::new").addLine();
        declaration.lowerIndent().addContent(")");
        factoryBuilder.withDeclaration(declaration);

        return factoryBuilder;
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
        setEspressoContextVar(invoke, helper);
        if (h.returnType.equals("V")) {
            invoke.addBodyLine(extractInvocation(argIndex, h).trim(), ";\n");
            invoke.addBodyLine("return StaticObject.NULL;");
        } else {
            invoke.addBodyLine("return ", extractInvocation(argIndex, h).trim(), ";\n");
        }
        classBuilder.withMethod(invoke);
        if (h.inlineInBytecode) {
            return generateInvokeInlined(classBuilder, parameterTypeName, helper);
        }
        return classBuilder;
    }

    @SuppressWarnings("fallthrough")
    private ClassBuilder generateInvokeInlined(ClassBuilder classBuilder, List<String> parameterTypeName, SubstitutionHelper helper) {
        SubstitutorHelper h = (SubstitutorHelper) helper;
        MethodBuilder invoke = new MethodBuilder("invokeInlined") //
                        .withOverrideAnnotation() //
                        .withModifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withParams("VirtualFrame frame", "int top", "InlinedFrameAccess frameAccess") //
                        .withReturnType("void");
        int delta = 1;
        int argCount = parameterTypeName.size();
        for (int argIndex = argCount - 1; argIndex >= 0; argIndex--) {

            String argType = parameterTypeName.get(argIndex);
            String popMethod;
            boolean doCast = false;
            int slotCount = 1;

            switch (argType) {
                case "byte":
                case "boolean":
                case "char":
                case "short":
                    doCast = true;
                    // fall through
                case "int":
                    popMethod = "popInt";
                    break;
                case "float":
                    popMethod = "popFloat";
                    break;
                case "long":
                    slotCount = 2;
                    popMethod = "popLong";
                    break;
                case "double":
                    slotCount = 2;
                    popMethod = "popDouble";
                    break;
                default:
                    popMethod = "popObject";
                    break;
            }

            if (argType.equals("boolean")) {
                invoke.addBodyLine(argType, " ", ARG_NAME, argIndex, " = ", ESPRESSO_FRAME + "." + popMethod + "(frame, top - " + delta + ") != 0", ";");
            } else {
                String cast = doCast ? "(" + argType + ") " : "";
                invoke.addBodyLine(argType, " ", ARG_NAME, argIndex, " = ", cast, ESPRESSO_FRAME + "." + popMethod + "(frame, top - " + delta + ")", ";");
            }
            delta += slotCount;
        }
        setEspressoContextVar(invoke, helper);
        if (h.returnType.equals("V")) {
            invoke.addBodyLine(extractInvocation(argCount, h).trim(), ";");
        } else {
            invoke.addBodyLine("frameAccess.pushResult(frame, ", extractResultToPush(extractInvocation(argCount, h).trim(), h), ");");
        }
        return classBuilder.withMethod(invoke);
    }

    private static String extractResultToPush(String invocation, SubstitutorHelper h) {
        if (h.returnType.equals("Z")) {
            return "(" + invocation + ") ? 1 : 0";
        } else {
            return invocation;
        }
    }

    private static boolean isFlag(byte flags, byte flag) {
        return (flags & flag) != 0;
    }
}
