/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class SubstitutionProcessor extends EspressoProcessor {
    // @EspressoSubstitutions
    private TypeElement espressoSubstitutions;
    // @Substitution
    private TypeElement substitutionAnnotation;
    // @Host
    private TypeElement host;
    // NoProvider.class
    private TypeElement noProvider;

    // @Substitutions.java11()
    private ExecutableElement espressoSubstitutionsClassNameProvider;

    // @Substitution.hasReceiver()
    private ExecutableElement hasReceiverElement;
    // @Substitution.methodName()
    private ExecutableElement methodNameElement;
    // @Substitution.methodName()
    private ExecutableElement substitutionClassNameProvider;

    // @Host.value()
    private ExecutableElement hostValueElement;
    // @Host.typeName()
    private ExecutableElement hostTypeNameElement;

    // region Various String constants.

    private static final String SUBSTITUTION_PACKAGE = "com.oracle.truffle.espresso.substitutions";

    private static final String ESPRESSO_SUBSTITUTIONS = SUBSTITUTION_PACKAGE + "." + "EspressoSubstitutions";
    private static final String METHOD_SUBSTITUTION = SUBSTITUTION_PACKAGE + "." + "Substitution";
    private static final String HOST = SUBSTITUTION_PACKAGE + "." + "Host";
    private static final String NO_PROVIDER = SUBSTITUTION_PACKAGE + "." + "SubstitutionNamesProvider" + "." + "NoProvider";

    private static final String SUBSTITUTOR = "Substitutor";
    private static final String COLLECTOR = "SubstitutorCollector";

    private static final String INVOKE = "invoke(Object[] " + ARGS_NAME + ") {\n";

    private static final String GET_METHOD_NAME = "getMethodNames";
    private static final String SUBSTITUTION_CLASS_NAMES = "substitutionClassNames";

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

        public SubstitutorHelper(EspressoProcessor processor, ExecutableElement method, String targetClassName, String guestMethodName, List<String> guestTypeNames, String returnType,
                        boolean hasReceiver, TypeMirror nameProvider) {
            super(processor, method);
            this.targetClassName = targetClassName;
            this.guestMethodName = guestMethodName;
            this.guestTypeNames = guestTypeNames;
            this.returnType = returnType;
            this.hasReceiver = hasReceiver;
            this.nameProvider = nameProvider;
        }
    }

    private static String extractArg(int index, String clazz, String tabulation) {
        return tabulation + clazz + " " + ARG_NAME + index + " = " + castTo(ARGS_NAME + "[" + index + "]", clazz) + ";\n";
    }

    private static String extractInvocation(String className, String methodName, int nParameters, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        str.append(className).append(".").append(methodName).append("(");
        boolean first = true;
        for (int i = 0; i < nParameters; i++) {
            first = checkFirst(str, first);
            str.append(ARG_NAME).append(i);
        }
        first = appendInvocationMetaInformation(str, first, helper);
        str.append(");\n");
        return str.toString();
    }

    private static String generateParameterTypes(List<String> types, String tabulation) {
        StringBuilder str = new StringBuilder();
        str.append("new String[]{");
        boolean first = true;
        for (String type : types) {
            if (first) {
                str.append("\n");
                first = false;
            } else {
                str.append(",\n");
            }
            str.append(tabulation).append(TAB_1).append("\"").append(type).append("\"");
        }
        if (!first) {
            str.append("\n").append(tabulation);
        }
        str.append("}");
        return str.toString();
    }

    private void processElement(Element substitution) {
        assert substitution.getKind() == ElementKind.CLASS;
        TypeElement typeElement = (TypeElement) substitution;

        // Extract the class name. (Of the form Target_[...]).
        String className = typeElement.getSimpleName().toString();
        // Extract the default name provider, if it is specified.
        TypeMirror defaultNameProvider = getNameProvider(getAnnotation(substitution, espressoSubstitutions), espressoSubstitutionsClassNameProvider);

        for (Element method : substitution.getEnclosedElements()) {
            // Find the methods annotated with @Substitution.
            AnnotationMirror subst = getAnnotation(method, substitutionAnnotation);
            if (subst != null) {
                assert method.getKind() == ElementKind.METHOD;
                // Obtain the name of the method to be substituted in.
                String targetMethodName = method.getSimpleName().toString();

                // Obtain the host types of the parameters
                List<String> espressoTypes = getEspressoTypes((ExecutableElement) method);

                // Spawn the name of the Substitutor we will create.
                String substitutorName = getSubstitutorClassName(className, targetMethodName, espressoTypes);

                if (!classes.contains(substitutorName)) {
                    // Obtain the name of the substituted method.
                    AnnotationValue methodNameValue = getAttribute(subst, methodNameElement);
                    assert methodNameValue.getValue() instanceof String;
                    String actualMethodName = (String) methodNameValue.getValue();
                    if (actualMethodName.length() == 0) {
                        actualMethodName = targetMethodName;
                    }

                    // Obtain the (fully qualified) guest types parameters of the method.
                    List<String> guestTypes = getGuestTypes((ExecutableElement) method);

                    // Obtain the hasReceiver() value from the @Substitution annotation.
                    AnnotationValue hasReceiverValue = getAttribute(subst, hasReceiverElement);
                    assert hasReceiverValue != null;
                    boolean hasReceiver = (boolean) hasReceiverValue.getValue();

                    // Obtain the fully qualified guest return type of the method.
                    String returnType = getReturnTypeFromHost((ExecutableElement) method);

                    TypeMirror nameProvider = getNameProvider(subst, substitutionClassNameProvider);
                    nameProvider = nameProvider == null ? defaultNameProvider : nameProvider;
                    SubstitutorHelper helper = new SubstitutorHelper(this, (ExecutableElement) method, className, actualMethodName, guestTypes, returnType, hasReceiver, nameProvider);

                    // Create the contents of the source file
                    String classFile = spawnSubstitutor(
                                    className,
                                    targetMethodName,
                                    espressoTypes, helper);
                    commitSubstitution(substitutionAnnotation, substitutorName, classFile);
                }
            }
        }
    }

    private TypeMirror getNameProvider(AnnotationMirror annotation, ExecutableElement attribute) {
        AnnotationValue provider = getAttribute(annotation, attribute);
        if (provider != null) {
            assert provider.getValue() instanceof TypeMirror;
            TypeMirror value = (TypeMirror) provider.getValue();
            if (!processingEnv.getTypeUtils().isSameType(value, noProvider.asType())) {
                return value;
            }
        }
        return null;
    }

    String getReturnTypeFromHost(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        AnnotationMirror a = getAnnotation(returnType, host);
        if (a != null) {
            return getClassFromHost(a);
        }
        return getInternalName(returnType.toString());
    }

    private List<String> getGuestTypes(ExecutableElement inner) {
        ArrayList<String> parameterTypeNames = new ArrayList<>();
        for (VariableElement parameter : inner.getParameters()) {
            if (isActualParameter(parameter)) {
                AnnotationMirror mirror = getAnnotation(parameter.asType(), host);
                if (mirror != null) {
                    parameterTypeNames.add(getClassFromHost(mirror));
                } else {
                    // @Host annotation not found -> primitive or j.l.Object
                    String arg = getInternalName(parameter.asType().toString());
                    parameterTypeNames.add(arg);
                }
            }
        }
        return parameterTypeNames;
    }

    /**
     * @param a an @Host annotation
     * @return the fully qualified internal name of the guest class.
     */
    private String getClassFromHost(AnnotationMirror a) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> members = processingEnv.getElementUtils().getElementValuesWithDefaults(a);
        AnnotationValue v = members.get(hostValueElement);
        if (v != null) {
            assert v.getValue() instanceof TypeMirror;
            TypeMirror value = (TypeMirror) v.getValue();
            if (processingEnv.getTypeUtils().isSameType(value, host.asType())) {
                return (String) members.get(hostTypeNameElement).getValue();
            } else {
                return getInternalName(value.toString());
            }
        } else {
            System.err.println("value() member of @Host annotation not found");
        }
        throw new IllegalArgumentException();
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
        this.espressoSubstitutions = processingEnv.getElementUtils().getTypeElement(ESPRESSO_SUBSTITUTIONS);
        this.substitutionAnnotation = processingEnv.getElementUtils().getTypeElement(METHOD_SUBSTITUTION);
        this.host = processingEnv.getElementUtils().getTypeElement(HOST);
        this.noProvider = processingEnv.getElementUtils().getTypeElement(NO_PROVIDER);
        for (Element e : espressoSubstitutions.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                if (e.getSimpleName().contentEquals("nameProvider")) {
                    this.espressoSubstitutionsClassNameProvider = (ExecutableElement) e;
                }
            }
        }
        for (Element e : substitutionAnnotation.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                if (e.getSimpleName().contentEquals("methodName")) {
                    this.methodNameElement = (ExecutableElement) e;
                }
                if (e.getSimpleName().contentEquals("hasReceiver")) {
                    this.hasReceiverElement = (ExecutableElement) e;
                }
                if (e.getSimpleName().contentEquals("nameProvider")) {
                    this.substitutionClassNameProvider = (ExecutableElement) e;
                }
            }
        }
        for (Element e : host.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                if (e.getSimpleName().contentEquals("value")) {
                    this.hostValueElement = (ExecutableElement) e;
                }
                if (e.getSimpleName().contentEquals("typeName")) {
                    this.hostTypeNameElement = (ExecutableElement) e;
                }
            }
        }
        // Actual process
        // Initialize the collector.
        initCollector(COLLECTOR);
        for (Element e : env.getElementsAnnotatedWith(espressoSubstitutions)) {
            processElement(e);
        }
    }

    @Override
    String generateImports(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        SubstitutorHelper h = (SubstitutorHelper) helper;
        if (parameterTypeName.contains("StaticObject") || h.returnType.equals("V")) {
            str.append(IMPORT_STATIC_OBJECT);
        }
        str.append("\n");
        return str.toString();
    }

    @Override
    String generateFactoryConstructorAndBody(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        SubstitutorHelper h = (SubstitutorHelper) helper;
        str.append(TAB_3).append("super(\n");
        str.append(TAB_4).append(generateString(h.guestMethodName)).append(",\n");
        str.append(TAB_4).append(generateString(h.targetClassName)).append(",\n");
        str.append(TAB_4).append(generateString(h.returnType)).append(",\n");
        str.append(TAB_4).append(generateParameterTypes(h.guestTypeNames, TAB_4)).append(",\n");
        str.append(TAB_4).append(h.hasReceiver).append("\n");
        str.append(TAB_3).append(");\n");
        str.append(TAB_2).append("}\n");

        if (h.nameProvider != null) {
            str.append(generateNameProviders(targetMethodName, h));
        }

        return str.toString();
    }

    private static String generateNameProviders(String targetMethodName, SubstitutorHelper h) {
        StringBuilder str = new StringBuilder();
        String nameProvider = h.nameProvider.toString().substring((SUBSTITUTION_PACKAGE + ".").length());

        str.append("\n");
        str.append(TAB_2).append(OVERRIDE).append("\n");
        str.append(TAB_2).append(PUBLIC_FINAL).append(" ").append("String[] ").append(GET_METHOD_NAME).append("() {\n");
        str.append(TAB_3).append("return ").append(nameProvider);
        str.append(".").append(INSTANCE).append(".").append(GET_METHOD_NAME).append("(").append(generateString(targetMethodName)).append(");\n");
        str.append(TAB_2).append("}\n\n");
        str.append(TAB_2).append(OVERRIDE).append("\n");
        str.append(TAB_2).append(PUBLIC_FINAL).append(" ").append("String[] ").append(SUBSTITUTION_CLASS_NAMES).append("() {\n");
        str.append(TAB_3).append("return ").append(nameProvider);
        str.append(".").append(INSTANCE).append(".").append(SUBSTITUTION_CLASS_NAMES).append("();\n");
        str.append(TAB_2).append("}\n");
        return str.toString();
    }

    @Override
    String generateInvoke(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        String targetClass = className;
        StringBuilder str = new StringBuilder();
        SubstitutorHelper h = (SubstitutorHelper) helper;
        str.append(TAB_1).append(PUBLIC_FINAL_OBJECT).append(INVOKE);
        int argIndex = 0;
        for (String argType : parameterTypeName) {
            str.append(extractArg(argIndex++, argType, TAB_2));
        }
        if (h.returnType.equals("V")) {
            str.append(TAB_2).append(extractInvocation(targetClass, targetMethodName, argIndex, helper));
            str.append(TAB_2).append("return StaticObject.NULL;\n");
        } else {
            str.append(TAB_2).append("return ").append(extractInvocation(targetClass, targetMethodName, argIndex, helper));
        }
        str.append(TAB_1).append("}\n");
        str.append("}");
        return str.toString();
    }
}
