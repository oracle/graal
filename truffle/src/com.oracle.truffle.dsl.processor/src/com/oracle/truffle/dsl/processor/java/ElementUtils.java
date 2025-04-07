/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.java;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Repeatable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractAnnotationValueVisitor8;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import com.oracle.truffle.dsl.processor.CompileErrorException;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeNames.NameImpl;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.WildcardTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.GeneratedElement;
import com.oracle.truffle.dsl.processor.model.SpecializationData.Idempotence;

/**
 * THIS IS NOT PUBLIC API.
 */
public class ElementUtils {

    public static ExecutableElement findMethod(Class<?> type, String methodName) {
        ProcessorContext context = ProcessorContext.getInstance();
        DeclaredType typeElement = context.getDeclaredType(type);
        return findMethod(typeElement, methodName);
    }

    public static ExecutableElement findMethod(DeclaredType type, String methodName) {
        ProcessorContext context = ProcessorContext.getInstance();
        return findMethod(context.getTypeElement(type), methodName);
    }

    public static ExecutableElement findMethod(TypeElement typeElement, String methodName) {
        for (ExecutableElement method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if (method.getSimpleName().contentEquals(methodName)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Finds the method named {@code methodName} defined by {@code typeElement}. Returns
     * {@code null} if no method exists. Throws an error if more than one method exists.
     * <p>
     * Overloads are disambiguated using the arity and types of {@code parameters}. These elements
     * can be null, in which case the parameter type is not checked.
     */
    public static ExecutableElement findInstanceMethod(TypeElement typeElement, String methodName, TypeMirror[] parameterTypes) {
        List<ExecutableElement> matches = ElementFilter.methodsIn(typeElement.getEnclosedElements()).stream() //
                        .filter(method -> method.getSimpleName().toString().equals(methodName)) //
                        .filter(method -> !method.getModifiers().contains(STATIC)) //
                        .filter(method -> parametersMatch(parameterTypes, method)) //
                        .collect(Collectors.toList());
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            throw new AssertionError(String.format("Type %s defines more than one method named %s (parameter types: %s)", typeElement.getSimpleName(), methodName, parameterTypes));
        }
        return matches.getFirst();
    }

    private static boolean parametersMatch(TypeMirror[] parameterTypes, ExecutableElement method) {
        if (parameterTypes == null) {
            return true;
        }
        List<? extends VariableElement> params = method.getParameters();
        if (parameterTypes.length != params.size()) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] == null) {
                continue;
            }
            if (!ElementUtils.typeEquals(parameterTypes[i], params.get(i).asType())) {
                return false;
            }
        }
        return true;
    }

    public static List<ExecutableElement> findAllPublicMethods(DeclaredType type, String methodName) {
        ProcessorContext context = ProcessorContext.getInstance();
        List<ExecutableElement> methods = new ArrayList<>();
        TypeElement typeElement = context.getTypeElement(type);
        for (ExecutableElement method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if (method.getModifiers().contains(Modifier.PUBLIC) && method.getSimpleName().toString().equals(methodName)) {
                methods.add(method);
            }
        }
        return methods;
    }

    public static List<Element> getEnumValues(TypeElement type) {
        List<Element> values = new ArrayList<>();
        for (Element element : type.getEnclosedElements()) {
            if (element.getKind() == ElementKind.ENUM_CONSTANT) {
                values.add(element);
            }
        }
        return values;
    }

    public static ExecutableElement findMethod(DeclaredType type, String methodName, int parameterCount) {
        ProcessorContext context = ProcessorContext.getInstance();
        TypeElement typeElement = context.getTypeElement(type);
        for (ExecutableElement method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if (method.getParameters().size() == parameterCount && method.getSimpleName().contentEquals(methodName)) {
                return method;
            }
        }
        return null;
    }

    public static ExecutableElement findStaticMethod(TypeElement type, String methodName) {
        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (method.getModifiers().contains(Modifier.STATIC) && method.getSimpleName().contentEquals(methodName)) {
                return method;
            }
        }
        return null;
    }

    public static String defaultValue(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case VOID:
                return "";
            case ARRAY:
            case DECLARED:
            case PACKAGE:
            case NULL:
                return "null";
            case BOOLEAN:
                return "false";
            case BYTE:
                return "(byte) 0";
            case CHAR:
                return "(char) 0";
            case DOUBLE:
                return "0.0D";
            case LONG:
                return "0L";
            case INT:
                return "0";
            case FLOAT:
                return "0.0F";
            case SHORT:
                return "(short) 0";
            default:
                throw new AssertionError();
        }
    }

    public static TypeElement getTypeElement(final CharSequence typeName) {
        return ProcessorContext.getInstance().getTypeElement(typeName);
    }

    public static TypeElement getTypeElement(DeclaredType type) {
        return (TypeElement) type.asElement();
    }

    public static TypeElement findTypeElement(CodeTypeElement typeElement, String name) {
        for (TypeElement nestedType : ElementFilter.typesIn(typeElement.getEnclosedElements())) {
            if (nestedType.getSimpleName().toString().equals(name)) {
                return nestedType;
            }
        }
        return null;
    }

    public static ExecutableElement findExecutableElement(DeclaredType type, String name) {
        return findExecutableElement(type.asElement(), name);
    }

    public static ExecutableElement findExecutableElement(Element element, String name) {
        List<? extends ExecutableElement> elements = ElementFilter.methodsIn(element.getEnclosedElements());
        for (ExecutableElement executableElement : elements) {
            if (executableElement.getSimpleName().contentEquals(name) && !isDeprecated(executableElement)) {
                return executableElement;
            }
        }
        return null;
    }

    public static ExecutableElement findExecutableElement(DeclaredType type, String name, int argumentCount) {
        return findExecutableElement(type.asElement(), name, argumentCount);
    }

    public static ExecutableElement findExecutableElement(Element element, String name, int argumentCount) {
        for (ExecutableElement executableElement : ElementFilter.methodsIn(element.getEnclosedElements())) {
            if (executableElement.getParameters().size() == argumentCount && executableElement.getSimpleName().contentEquals(name) && !isDeprecated(executableElement)) {
                return executableElement;
            }
        }
        return null;
    }

    public static VariableElement findVariableElement(DeclaredType type, String name) {
        return findVariableElement(type.asElement(), name);
    }

    public static VariableElement findVariableElement(Element element, String name) {
        List<? extends VariableElement> elements = ElementFilter.fieldsIn(element.getEnclosedElements());
        for (VariableElement variableElement : elements) {
            if (variableElement.getSimpleName().contentEquals(name)) {
                return variableElement;
            }
        }
        return null;
    }

    public static boolean needsCastTo(TypeMirror sourceType, TypeMirror targetType) {
        if (typeEquals(sourceType, targetType)) {
            return false;
        } else if (isObject(targetType)) {
            return false;
        } else if (isVoid(targetType)) {
            return false;
        } else if (isAssignable(sourceType, targetType)) {
            return false;
        }
        return true;
    }

    public static String createReferenceName(ExecutableElement method) {
        StringBuilder b = new StringBuilder();

        // if (method.getEnclosingElement() != null) {
        // b.append(method.getEnclosingElement().getSimpleName());
        // b.append('#');
        // }

        b.append(method.getSimpleName().toString());
        b.append("(");

        String sep = "";
        for (VariableElement parameter : method.getParameters()) {
            b.append(sep);
            b.append(ElementUtils.getSimpleName(parameter.asType()));
            sep = ", ";
        }

        b.append(")");
        return b.toString();
    }

    public static TypeMirror boxType(TypeMirror type) {
        return boxType(ProcessorContext.getInstance(), type);
    }

    public static TypeMirror boxType(ProcessorContext context, TypeMirror primitiveType) {
        if (primitiveType == null) {
            return null;
        }
        TypeMirror boxedType = primitiveType;
        if (boxedType.getKind().isPrimitive()) {
            boxedType = context.getEnvironment().getTypeUtils().boxedClass((PrimitiveType) boxedType).asType();
        }
        return boxedType;
    }

    public static DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs) {
        return new DeclaredCodeTypeMirror(typeElem, Arrays.asList(typeArgs));
    }

    /**
     * This method converts a raw type to a generic type with wildcard arguments.
     *
     * For example, {@code List} becomes {@code List<?>}.
     */
    public static TypeMirror rawTypeToWildcardedType(ProcessorContext context, TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return type;
        }
        DeclaredType declaredType = (DeclaredType) type;
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
        if (typeParameters.isEmpty()) {
            return type;
        }
        if (declaredType instanceof DeclaredCodeTypeMirror) {
            // Special case: generated types
            List<TypeMirror> typeArguments = new ArrayList<>(typeParameters.size());
            for (int i = 0; i < typeArguments.size(); i++) {
                typeArguments.add(new WildcardTypeMirror(null, null));
            }
            return new DeclaredCodeTypeMirror(typeElement, typeArguments);
        }

        Types typeUtils = context.getEnvironment().getTypeUtils();
        TypeMirror[] typeArguments = new TypeMirror[typeParameters.size()];
        for (int i = 0; i < typeArguments.length; i++) {
            typeArguments[i] = typeUtils.getWildcardType(null, null);
        }
        return typeUtils.getDeclaredType(typeElement, typeArguments);
    }

    public static List<AnnotationMirror> collectAnnotations(AnnotationMirror markerAnnotation, String elementName, Element element, DeclaredType annotationClass) {
        List<AnnotationMirror> result = new ArrayList<>();
        if (markerAnnotation != null) {
            result.addAll(ElementUtils.getAnnotationValueList(AnnotationMirror.class, markerAnnotation, elementName));
        }
        AnnotationMirror explicit = ElementUtils.findAnnotationMirror(element, annotationClass);
        if (explicit != null) {
            result.add(explicit);
        }
        return result;
    }

    public static TypeMirror getCommonSuperType(ProcessorContext context, Collection<TypeMirror> types) {
        if (types.isEmpty()) {
            return context.getType(Object.class);
        }
        Iterator<TypeMirror> typesIterator = types.iterator();
        TypeMirror prev = typesIterator.next();
        while (typesIterator.hasNext()) {
            prev = getCommonSuperType(context, prev, typesIterator.next());
        }
        return prev;
    }

    private static TypeMirror getCommonSuperType(ProcessorContext context, TypeMirror type1, TypeMirror type2) {
        if (typeEquals(type1, type2)) {
            return type1;
        }
        if (isVoid(type1)) {
            return type2;
        } else if (isVoid(type2)) {
            return type1;
        }
        if (isObject(type1)) {
            return type1;
        } else if (isObject(type2)) {
            return type2;
        }

        if (isPrimitive(type1) || isPrimitive(type2)) {
            return context.getType(Object.class);
        }

        if (isSubtype(type1, type2)) {
            return type2;
        } else if (isSubtype(type2, type1)) {
            return type1;
        }

        TypeElement element1 = fromTypeMirror(type1);
        TypeElement element2 = fromTypeMirror(type2);

        if (element1 == null || element2 == null) {
            return context.getType(Object.class);
        }

        List<TypeElement> element1Types = getSuperTypes(element1);
        List<TypeElement> element2Types = getSuperTypes(element2);

        for (TypeElement superType1 : element1Types) {
            for (TypeElement superType2 : element2Types) {
                if (typeEquals(superType1.asType(), superType2.asType())) {
                    return superType2.asType();
                }
            }
        }

        return context.getType(Object.class);
    }

    public static String getReadableSignature(ExecutableElement method) {
        StringBuilder builder = new StringBuilder();
        builder.append(method.getSimpleName().toString());
        builder.append("(");
        String sep = "";
        for (VariableElement var : method.getParameters()) {
            builder.append(sep);
            builder.append(getSimpleName(var.asType()));
            sep = ", ";
        }
        builder.append(")");
        return builder.toString();
    }

    public static boolean hasOverloads(TypeElement enclosingType, ExecutableElement e) {
        Name name = e.getSimpleName();
        for (ExecutableElement otherExecutable : ElementFilter.methodsIn(enclosingType.getEnclosedElements())) {
            if (nameEquals(name, otherExecutable.getSimpleName())) {
                if (!ElementUtils.elementEquals(e, otherExecutable)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getReadableSignature(ExecutableElement method, int highlightParameter) {
        StringBuilder builder = new StringBuilder();
        builder.append(method.getSimpleName().toString());
        builder.append("(");
        VariableElement var = method.getParameters().get(highlightParameter);
        if (highlightParameter > 0) {
            // not first parameter
            builder.append("..., ");
        }

        builder.append(getSimpleName(var.asType())).append(" ");
        builder.append(var.getSimpleName().toString());

        if (highlightParameter < method.getParameters().size() - 1) {
            // not last
            builder.append(", ...");
        }
        builder.append(")");
        return builder.toString();
    }

    public static boolean hasError(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case DOUBLE:
            case FLOAT:
            case INT:
            case SHORT:
            case LONG:
            case DECLARED:
            case VOID:
            case TYPEVAR:
                return false;
            case ARRAY:
                return hasError(((ArrayType) mirror).getComponentType());
            case ERROR:
                return true;
            default:
                throw new RuntimeException("Unknown type specified " + mirror.getKind() + " mirror: " + mirror);
        }
    }

    public static boolean isSubtypeBoxed(ProcessorContext context, TypeMirror from, TypeMirror to) {
        return isSubtype(boxType(context, from), boxType(context, to));
    }

    public static boolean isSubtype(TypeMirror type1, TypeMirror type2) {
        if (type1 instanceof CodeTypeMirror || type2 instanceof CodeTypeMirror) {
            if (ElementUtils.typeEquals(type1, type2)) {
                return true;
            } else {
                // unsupported
                return false;
            }
        }
        return ProcessorContext.getInstance().getEnvironment().getTypeUtils().isSubtype(type1, type2);
    }

    public static boolean isAssignable(TypeMirror from, TypeMirror to) {
        if (typeEquals(from, to)) {
            return true;
        } else if (isVoid(to)) {
            return true;
        } else if (isNone(to)) {
            return false;
        } else if (isObject(to)) {
            return true;
        }
        if (isInvalidType(from) || isInvalidType(to)) {
            // workaround for eclipse compiler bug: v4.7.3a throws IllegalArgumentException or
            // ClassCastException
            return false;
        }
        ProcessorContext context = ProcessorContext.getInstance();
        if (!(from instanceof CodeTypeMirror) && !(to instanceof CodeTypeMirror)) {
            Types typeUtils = context.getEnvironment().getTypeUtils();
            TypeMirror reloadFrom = context.reloadType(from);
            TypeMirror reloadTo = context.reloadType(to);
            TypeMirror erasedFrom = typeUtils.erasure(reloadFrom);
            TypeMirror erasedTo = typeUtils.erasure(reloadTo);
            return typeUtils.isAssignable(erasedFrom, erasedTo);
        } else {
            return isAssignableImpl(from, to);
        }
    }

    private static boolean isInvalidType(TypeMirror reloadFrom) {
        return reloadFrom.getKind() == TypeKind.NONE || reloadFrom.getKind() == TypeKind.ERROR || reloadFrom.getKind() == TypeKind.VOID;
    }

    private static boolean isAssignableImpl(TypeMirror from, TypeMirror to) {
        // JLS 5.1.1 identity conversion
        if (ElementUtils.typeEquals(from, to)) {
            return true;
        }

        if (isObject(to)) {
            return true;
        }

        // JLS 5.1.2 widening primitives
        if (ElementUtils.isPrimitive(from) && ElementUtils.isPrimitive(to)) {
            TypeKind fromKind = from.getKind();
            TypeKind toKind = to.getKind();
            switch (fromKind) {
                case BYTE:
                    switch (toKind) {
                        case SHORT:
                        case INT:
                        case LONG:
                        case FLOAT:
                        case DOUBLE:
                            return true;
                    }
                    break;
                case SHORT:
                    switch (toKind) {
                        case INT:
                        case LONG:
                        case FLOAT:
                        case DOUBLE:
                            return true;
                    }
                    break;
                case CHAR:
                    switch (toKind) {
                        case INT:
                        case LONG:
                        case FLOAT:
                        case DOUBLE:
                            return true;
                    }
                    break;
                case INT:
                    switch (toKind) {
                        case LONG:
                        case FLOAT:
                        case DOUBLE:
                            return true;
                    }
                    break;
                case LONG:
                    switch (toKind) {
                        case FLOAT:
                        case DOUBLE:
                            return true;
                    }
                    break;
                case FLOAT:
                    switch (toKind) {
                        case DOUBLE:
                            return true;
                    }
                    break;

            }
            return false;
        } else if (ElementUtils.isPrimitive(from) || ElementUtils.isPrimitive(to)) {
            return false;
        }

        if (from.getKind() == TypeKind.ARRAY && to.getKind() == TypeKind.ARRAY) {
            return isAssignable(((ArrayType) from).getComponentType(), ((ArrayType) to).getComponentType());
        }

        if (from.getKind() == TypeKind.ARRAY || to.getKind() == TypeKind.ARRAY) {
            return false;
        }

        TypeElement fromType = ElementUtils.fromTypeMirror(from);
        TypeElement toType = ElementUtils.fromTypeMirror(to);
        if (fromType == null || toType == null) {
            return false;
        }
        // JLS 5.1.6 narrowing reference conversion

        List<TypeElement> superTypes = ElementUtils.getSuperTypes(fromType);
        for (TypeElement superType : superTypes) {
            if (ElementUtils.typeEquals(superType.asType(), to)) {
                return true;
            }
        }

        // TODO GR-38632 more spec
        return false;
    }

    public static Set<Modifier> modifiers(Modifier... modifier) {
        return new LinkedHashSet<>(Arrays.asList(modifier));
    }

    public static String getTypeSimpleId(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case BOOLEAN:
                return "Boolean";
            case BYTE:
                return "Byte";
            case CHAR:
                return "Char";
            case DOUBLE:
                return "Double";
            case FLOAT:
                return "Float";
            case SHORT:
                return "Short";
            case INT:
                return "Int";
            case LONG:
                return "Long";
            case DECLARED:
                return fixECJBinaryNameIssue(((DeclaredType) mirror).asElement().getSimpleName().toString());
            case ARRAY:
                return getTypeSimpleId(((ArrayType) mirror).getComponentType()) + "Array";
            case VOID:
                return "Void";
            case NULL:
                return "Null";
            case WILDCARD:
                StringBuilder b = new StringBuilder();
                WildcardType type = (WildcardType) mirror;
                if (type.getExtendsBound() != null) {
                    b.append("Extends").append(getTypeSimpleId(type.getExtendsBound()));
                } else if (type.getSuperBound() != null) {
                    b.append("Super").append(getTypeSimpleId(type.getExtendsBound()));
                }
                return b.toString();
            case TYPEVAR:
                return "Any";
            case ERROR:
                throw new CompileErrorException("Type error " + mirror);
            default:
                throw new RuntimeException("Unknown type specified " + mirror.getKind() + " mirror: " + mirror);
        }
    }

    public static String getSimpleName(TypeElement element) {
        return getSimpleName(element.asType());
    }

    public static String getSimpleName(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case BOOLEAN:
                return "boolean";
            case BYTE:
                return "byte";
            case CHAR:
                return "char";
            case DOUBLE:
                return "double";
            case FLOAT:
                return "float";
            case SHORT:
                return "short";
            case INT:
                return "int";
            case LONG:
                return "long";
            case DECLARED:
                return getDeclaredName((DeclaredType) mirror, true);
            case ARRAY:
                return getSimpleName(((ArrayType) mirror).getComponentType()) + "[]";
            case VOID:
                return "void";
            case NULL:
                return "null";
            case WILDCARD:
                return getWildcardName((WildcardType) mirror);
            case TYPEVAR:
                return ((TypeVariable) mirror).asElement().getSimpleName().toString();
            case ERROR:
                throw new CompileErrorException("Type error " + mirror);
            case NONE:
                return "None";
            default:
                throw new RuntimeException("Unknown type specified " + mirror.getKind() + " mirror: " + mirror);
        }
    }

    private static String getWildcardName(WildcardType type) {
        StringBuilder b = new StringBuilder("?");
        if (type.getExtendsBound() != null) {
            b.append(" extends ").append(getSimpleName(type.getExtendsBound()));
        } else if (type.getSuperBound() != null) {
            b.append(" super ").append(getSimpleName(type.getSuperBound()));
        }
        return b.toString();
    }

    public static String getDeclaredName(DeclaredType element, boolean includeTypeVariables) {
        String simpleName = fixECJBinaryNameIssue(element.asElement().getSimpleName().toString());

        if (!includeTypeVariables || element.getTypeArguments().size() == 0) {
            return simpleName;
        }

        StringBuilder b = new StringBuilder(simpleName);
        b.append("<");
        if (element.getTypeArguments().size() > 0) {
            for (int i = 0; i < element.getTypeArguments().size(); i++) {
                b.append(getSimpleName(element.getTypeArguments().get(i)));
                if (i < element.getTypeArguments().size() - 1) {
                    b.append(", ");
                }
            }
        }
        b.append(">");
        return b.toString();
    }

    public static String fixECJBinaryNameIssue(String name) {
        if (name.contains("$")) {
            int lastIndex = name.lastIndexOf('$');
            return name.substring(lastIndex + 1, name.length());
        }
        return name;
    }

    public static String getClassQualifiedName(TypeElement e) {
        StringBuilder b = new StringBuilder();
        buildClassQualifiedNameImpl(e, b);
        return b.toString();
    }

    private static void buildClassQualifiedNameImpl(Element e, StringBuilder classNames) {
        if (e == null) {
            return;
        } else if (e.getKind() == ElementKind.PACKAGE) {
            String packageName = getPackageName(e);
            if (packageName != null) {
                classNames.append(packageName);
            }
        } else {
            Element enclosingElement = e.getEnclosingElement();
            buildClassQualifiedNameImpl(enclosingElement, classNames);
            if (enclosingElement.getKind().isClass()) {
                classNames.append("$");
            } else {
                classNames.append(".");
            }
            classNames.append(e.getSimpleName().toString());
        }
    }

    public static String getQualifiedName(TypeElement element) {
        String qualifiedName = element.getQualifiedName().toString();
        if (qualifiedName.contains("$")) {
            /*
             * If a class gets loaded in its binary form by the ECJ compiler it fails to produce the
             * proper canonical class name. It leaves the $ in the qualified name of the class. So
             * one instance of a TypeElement may be loaded in binary and one in source form. The
             * current type comparison in #typeEquals compares by the qualified name so the
             * qualified name must match. This is basically a hack to fix the returned qualified
             * name of eclipse.
             */
            qualifiedName = qualifiedName.replace('$', '.');
        }
        return qualifiedName;
    }

    public static TypeMirror fromQualifiedName(String name) {
        ProcessorContext context = ProcessorContext.getInstance();
        TypeKind primitiveType;
        switch (name) {
            case "boolean":
                primitiveType = TypeKind.BOOLEAN;
                break;
            case "byte":
                primitiveType = TypeKind.BYTE;
                break;
            case "char":
                primitiveType = TypeKind.CHAR;
                break;
            case "double":
                primitiveType = TypeKind.DOUBLE;
                break;
            case "short":
                primitiveType = TypeKind.SHORT;
                break;
            case "float":
                primitiveType = TypeKind.FLOAT;
                break;
            case "int":
                primitiveType = TypeKind.INT;
                break;
            case "long":
                primitiveType = TypeKind.LONG;
                break;
            case "void":
                primitiveType = TypeKind.VOID;
                break;
            case "null":
                primitiveType = TypeKind.NULL;
                break;
            default:
                return context.getDeclaredType(name);
        }
        return context.getEnvironment().getTypeUtils().getPrimitiveType(primitiveType);

    }

    public static String getQualifiedName(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case BOOLEAN:
                return "boolean";
            case BYTE:
                return "byte";
            case CHAR:
                return "char";
            case DOUBLE:
                return "double";
            case SHORT:
                return "short";
            case FLOAT:
                return "float";
            case INT:
                return "int";
            case LONG:
                return "long";
            case DECLARED:
                return getQualifiedName(fromTypeMirror(mirror));
            case ARRAY:
                return getQualifiedName(((ArrayType) mirror).getComponentType());
            case VOID:
                return "void";
            case NULL:
                return "null";
            case TYPEVAR:
                return getSimpleName(mirror);
            case ERROR:
                throw new CompileErrorException("Type error " + mirror);
            case EXECUTABLE:
                return mirror.toString();
            case NONE:
                return "$none";
            default:
                throw new RuntimeException("Unknown type specified " + mirror + " mirror: " + mirror);
        }
    }

    public static boolean isNone(TypeMirror mirror) {
        return mirror != null && isInvalidType(mirror);
    }

    public static boolean isVoid(TypeMirror mirror) {
        return mirror != null && mirror.getKind() == TypeKind.VOID;
    }

    public static boolean isPrimitive(TypeMirror mirror) {
        return mirror != null && mirror.getKind().isPrimitive();
    }

    public static boolean isFinal(TypeMirror mirror) {
        if (isPrimitive(mirror) || isVoid(mirror)) {
            return true;
        }
        if (mirror.getKind() == TypeKind.DECLARED) {
            Element element = ((DeclaredType) mirror).asElement();
            if (element.getKind().isClass() && element.getModifiers().contains(Modifier.FINAL)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getQualifiedSuperTypeNames(TypeElement element) {
        List<TypeElement> types = getSuperTypes(element);
        List<String> qualifiedNames = new ArrayList<>();
        for (TypeElement type : types) {
            qualifiedNames.add(getQualifiedName(type));
        }
        return qualifiedNames;
    }

    public static List<TypeElement> getDeclaredTypes(TypeElement element) {
        return ElementFilter.typesIn(element.getEnclosedElements());
    }

    public static boolean isEnclosedIn(Element enclosedIn, Element element) {
        if (element == null) {
            return false;
        } else if (typeEquals(enclosedIn.asType(), element.asType())) {
            return true;
        } else {
            return isEnclosedIn(enclosedIn, element.getEnclosingElement());
        }
    }

    public static List<Element> getElementHierarchy(Element e) {
        List<Element> elements = new ArrayList<>();
        Element enclosing = null;
        if (e != null) {
            elements.add(e);
            enclosing = e.getEnclosingElement();
        }
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            elements.add(enclosing);
            enclosing = enclosing.getEnclosingElement();
        }
        if (enclosing != null) {
            elements.add(enclosing);
        }
        return elements;
    }

    public static Optional<TypeElement> findRootEnclosingType(Element element) {
        TypeElement parentType = findParentEnclosingType(element).orElse(null);
        if (parentType == null) {
            return findNearestEnclosingType(element);
        } else {
            return findRootEnclosingType(parentType);
        }
    }

    public static Optional<TypeElement> findParentEnclosingType(Element element) {
        if (element == null) {
            return Optional.empty();
        }
        return findNearestEnclosingType(element.getEnclosingElement());
    }

    public static Optional<TypeElement> findNearestEnclosingType(Element e) {
        if (e != null) {
            if (e.getKind().isInterface() || e.getKind().isClass()) {
                return Optional.of((TypeElement) e);
            }
            Element enclosing = e.getEnclosingElement();
            if (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
                return findNearestEnclosingType(enclosing);
            }
        }
        return Optional.empty();
    }

    public static List<TypeElement> getDirectSuperTypes(TypeElement element) {
        List<TypeElement> types = new ArrayList<>();
        TypeElement superElement = getSuperType(element);
        if (superElement != null) {
            types.add(superElement);
            types.addAll(getDirectSuperTypes(superElement));
        }

        return types;
    }

    /**
     * Gets the element representing the {@linkplain TypeElement#getSuperclass() super class} of a
     * given type element.
     */
    public static TypeElement getSuperType(TypeElement element) {
        if (element == null) {
            return null;
        } else if (element.getSuperclass() != null) {
            return fromTypeMirror(element.getSuperclass());
        }
        return null;
    }

    public static boolean isDeprecated(TypeMirror baseType) {
        if (baseType != null && baseType.getKind() == TypeKind.DECLARED) {
            return isDeprecated(((DeclaredType) baseType).asElement());
        }
        return false;
    }

    public static boolean isDeprecated(Element baseType) {
        DeclaredType deprecated = ProcessorContext.getInstance().getDeclaredType(Deprecated.class);
        return ElementUtils.findAnnotationMirror(baseType.getAnnotationMirrors(), deprecated) != null;
    }

    public static boolean isPackageDeprecated(TypeElement baseType) {
        DeclaredType deprecated = ProcessorContext.getInstance().getDeclaredType(Deprecated.class);
        List<TypeElement> superTypes = getSuperTypes(baseType);
        superTypes.add(baseType);
        for (TypeElement type : superTypes) {
            PackageElement pack = ElementUtils.findPackageElement(type);
            if ((pack != null && ElementUtils.findAnnotationMirror(pack.getAnnotationMirrors(), deprecated) != null)) {
                return true;
            }
        }
        return false;
    }

    public static List<TypeElement> getSuperTypes(TypeElement element) {
        List<TypeElement> types = new ArrayList<>();
        List<TypeElement> superTypes = null;
        List<TypeElement> superInterfaces = null;
        TypeElement superElement = getSuperType(element);
        if (superElement != null) {
            types.add(superElement);
            superTypes = getSuperTypes(superElement);
        }
        for (TypeMirror interfaceMirror : element.getInterfaces()) {
            TypeElement interfaceElement = fromTypeMirror(interfaceMirror);
            if (interfaceElement != null) {
                types.add(interfaceElement);
                if (superInterfaces == null) {
                    superInterfaces = getSuperTypes(interfaceElement);
                } else {
                    superInterfaces.addAll(getSuperTypes(interfaceElement));
                }
            }
        }

        if (superTypes != null) {
            types.addAll(superTypes);
        }

        if (superInterfaces != null) {
            types.addAll(superInterfaces);
        }

        return types;
    }

    public static String getPackageName(Element element) {
        PackageElement pack = findPackageElement(element);
        if (pack == null) {
            return null;
        }
        return pack.getQualifiedName().toString();
    }

    public static String getEnclosedQualifiedName(DeclaredType mirror) {
        Element e = ((TypeElement) mirror.asElement()).getEnclosingElement();
        if (e.getKind() == ElementKind.PACKAGE) {
            return ((PackageElement) e).getQualifiedName().toString();
        } else if (e.getKind().isInterface() || e.getKind().isClass()) {
            return getQualifiedName((TypeElement) e);
        } else {
            return null;
        }
    }

    public static PackageElement getPackageElement(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case DOUBLE:
            case FLOAT:
            case SHORT:
            case INT:
            case LONG:
            case VOID:
            case NULL:
            case TYPEVAR:
                return null;
            case DECLARED:
                PackageElement pack = findPackageElement(fromTypeMirror(mirror));
                if (pack == null) {
                    throw new IllegalArgumentException("No package element found for declared type " + getSimpleName(mirror));
                }
                return pack;
            case ARRAY:
                return getPackageElement(((ArrayType) mirror).getComponentType());
            case EXECUTABLE:
                return null;
            default:
                throw new RuntimeException("Unknown type specified " + mirror.getKind());
        }
    }

    public static String getPackageName(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case DOUBLE:
            case FLOAT:
            case SHORT:
            case INT:
            case LONG:
            case VOID:
            case NULL:
            case TYPEVAR:
                return null;
            case DECLARED:
                PackageElement pack = findPackageElement(fromTypeMirror(mirror));
                if (pack == null) {
                    throw new IllegalArgumentException("No package element found for declared type " + getSimpleName(mirror));
                }
                return pack.getQualifiedName().toString();
            case ARRAY:
                return getSimpleName(((ArrayType) mirror).getComponentType());
            case EXECUTABLE:
                return null;
            default:
                throw new RuntimeException("Unknown type specified " + mirror.getKind());
        }
    }

    public static String createConstantName(String simpleName) {
        StringBuilder b = new StringBuilder(simpleName);

        int i = 0;
        while (i < b.length()) {
            char c = b.charAt(i);
            if (Character.isUpperCase(c) && i != 0 &&
                            Character.isUpperCase(b.charAt(i - 1))) {
                b.setCharAt(i, Character.toLowerCase(c));
            }
            i++;
        }

        i = 0;
        while (i < b.length()) {
            char c = b.charAt(i);
            if (i > 0 && Character.isUpperCase(b.charAt(i - 1)) && Character.isUpperCase(c)) {
                b.insert(i, '_');
                i++;
            } else if (Character.isLowerCase(c)) {
                b.setCharAt(i, Character.toUpperCase(c));
            }
            i++;
        }
        return b.toString();
    }

    public static TypeElement fromTypeMirror(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case DECLARED:
                return (TypeElement) ((DeclaredType) mirror).asElement();
            case ARRAY:
                return fromTypeMirror(((ArrayType) mirror).getComponentType());
            default:
                return null;
        }
    }

    /**
     * Temporary local implementation of
     * {@link ElementUtils#getAnnotationValue(javax.lang.model.element.AnnotationMirror, java.lang.String)}
     * . The {@code ElementUtils.getAnnotationValue} does not work on Eclipse JDT compiler when an
     * annotation type is nested in a generic type, see issue:
     * https://bugs.eclipse.org/bugs/show_bug.cgi?id=544940
     */
    public static AnnotationValue getAnnotationValue(AnnotationMirror mirror, String name) {
        return getAnnotationValue(mirror, name, true);
    }

    /**
     * Temporary local implementation of
     * {@link ElementUtils#getAnnotationValue(javax.lang.model.element.AnnotationMirror, java.lang.String, boolean)}
     * . The {@code ElementUtils.getAnnotationValue} does not work on Eclipse JDT compiler when an
     * annotation type is nested in a generic type, see issue:
     * https://bugs.eclipse.org/bugs/show_bug.cgi?id=544940
     */
    public static AnnotationValue getAnnotationValue(AnnotationMirror mirror, String name, boolean resolveDefault) {
        if (mirror instanceof CodeAnnotationMirror) {
            ExecutableElement valueMethod = null;
            for (ExecutableElement method : ElementFilter.methodsIn(mirror.getAnnotationType().asElement().getEnclosedElements())) {
                if (method.getSimpleName().toString().equals(name)) {
                    valueMethod = method;
                    break;
                }
            }
            if (valueMethod == null) {
                return null;
            }
            AnnotationValue value = mirror.getElementValues().get(valueMethod);
            if (resolveDefault) {
                if (value == null) {
                    value = valueMethod.getDefaultValue();
                }
            }

            return value;
        } else {
            Map<? extends ExecutableElement, ? extends AnnotationValue> valuesMap = resolveDefault
                            ? ProcessorContext.getInstance().getEnvironment().getElementUtils().getElementValuesWithDefaults(mirror)
                            : mirror.getElementValues();
            for (ExecutableElement e : valuesMap.keySet()) {
                if (name.contentEquals(e.getSimpleName())) {
                    return valuesMap.get(e);
                }
            }
        }
        return null;
    }

    public static <T> List<T> getAnnotationValueList(Class<T> expectedListType, AnnotationMirror mirror, String name) {
        List<?> values = ElementUtils.resolveAnnotationValue(List.class, getAnnotationValue(mirror, name));
        List<T> result = new ArrayList<>();
        if (values != null) {
            for (Object value : values) {
                T annotationValue = ElementUtils.resolveAnnotationValue(expectedListType, (AnnotationValue) value);
                if (annotationValue != null) {
                    result.add(annotationValue);
                }
            }
        }
        return result;
    }

    public static <T> T getAnnotationValue(Class<T> expectedType, AnnotationMirror mirror, String name) {
        return getAnnotationValue(expectedType, mirror, name, true);
    }

    public static <T> T getAnnotationValue(Class<T> expectedType, AnnotationMirror mirror, String name, boolean resolveDefault) {
        return resolveAnnotationValue(expectedType, getAnnotationValue(mirror, name, resolveDefault));
    }

    public static <T> T resolveAnnotationValue(Class<T> expectedType, AnnotationValue value) {
        if (value == null) {
            return null;
        }
        Object unboxedValue = unboxAnnotationValue(value);
        if (unboxedValue != null) {
            if (expectedType == TypeMirror.class && unboxedValue instanceof String) {
                return null;
            }
            if (!expectedType.isAssignableFrom(unboxedValue.getClass())) {
                throw new ClassCastException(unboxedValue.getClass().getName() + " not assignable from " + expectedType.getName());
            }
        }
        return expectedType.cast(unboxedValue);
    }

    public static Object unboxAnnotationValue(AnnotationValue value) {
        return value.accept(new AnnotationValueVisitorImpl(), null);
    }

    private static final class AnnotationValueVisitorImpl extends AbstractAnnotationValueVisitor8<Object, Void> {

        @Override
        public Object visitBoolean(boolean b, Void p) {
            return Boolean.valueOf(b);
        }

        @Override
        public Object visitByte(byte b, Void p) {
            return Byte.valueOf(b);
        }

        @Override
        public Object visitChar(char c, Void p) {
            return c;
        }

        @Override
        public Object visitDouble(double d, Void p) {
            return d;
        }

        @Override
        public Object visitFloat(float f, Void p) {
            return f;
        }

        @Override
        public Object visitInt(int i, Void p) {
            return i;
        }

        @Override
        public Object visitLong(long i, Void p) {
            return i;
        }

        @Override
        public Object visitShort(short s, Void p) {
            return s;
        }

        @Override
        public Object visitString(String s, Void p) {
            return s;
        }

        @Override
        public Object visitType(TypeMirror t, Void p) {
            return t;
        }

        @Override
        public Object visitEnumConstant(VariableElement c, Void p) {
            return c;
        }

        @Override
        public Object visitAnnotation(AnnotationMirror a, Void p) {
            return a;
        }

        @Override
        public Object visitArray(List<? extends AnnotationValue> vals, Void p) {
            return vals;
        }

    }

    public static String printException(Throwable e) {
        StringWriter string = new StringWriter();
        PrintWriter writer = new PrintWriter(string);
        e.printStackTrace(writer);
        writer.flush();
        string.flush();
        return e.getMessage() + System.lineSeparator() + string.toString();
    }

    public static AnnotationMirror findAnnotationMirror(Element element, Class<?> expectedAnnotationType) {
        return findAnnotationMirror(element.getAnnotationMirrors(), ProcessorContext.getInstance().getType(expectedAnnotationType));
    }

    public static AnnotationMirror findAnnotationMirror(List<? extends AnnotationMirror> mirrors, TypeMirror expectedAnnotationType) {
        for (AnnotationMirror mirror : mirrors) {
            if (typeEquals(mirror.getAnnotationType(), expectedAnnotationType)) {
                return mirror;
            }
        }
        return null;
    }

    public static AnnotationMirror findAnnotationMirror(Element element, TypeMirror annotationType) {
        return findAnnotationMirror(element.getAnnotationMirrors(), annotationType);
    }

    public static PackageElement findPackageElement(Element e) {
        if (e != null) {
            if (e.getKind() == ElementKind.PACKAGE) {
                return (PackageElement) e;
            }
            Element enclosing = e.getEnclosingElement();
            if (enclosing != null) {
                return findPackageElement(enclosing);
            }
        }
        return null;
    }

    public static String firstLetterUpperCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1, name.length());
    }

    public static String firstLetterLowerCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1, name.length());
    }

    public static ExecutableElement getDeclaredMethod(TypeElement element, String name, TypeMirror[] params) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(element.getEnclosedElements());
        method: for (ExecutableElement method : methods) {
            if (!method.getSimpleName().toString().equals(name)) {
                continue;
            }
            if (method.getParameters().size() != params.length) {
                continue;
            }
            for (int i = 0; i < params.length; i++) {
                TypeMirror param1 = params[i];
                TypeMirror param2 = method.getParameters().get(i).asType();
                if (param1 != null && param1.getKind() != TypeKind.TYPEVAR && param2 != null && param2.getKind() != TypeKind.TYPEVAR) {
                    if (!typeEquals(param1, param2)) {
                        continue method;
                    }
                }
            }
            return method;
        }
        return null;
    }

    public static boolean isDeclaredMethodInSuperType(TypeElement element, String name, TypeMirror[] params) {
        return !getDeclaredMethodsInSuperTypes(element, name, params).isEmpty();
    }

    /**
     * Gets the methods in the super type hierarchy (excluding interfaces) that are overridden by a
     * method in a subtype.
     *
     * @param declaringElement the subtype element declaring the method
     * @param name the name of the method
     * @param params the signature of the method
     */
    public static List<ExecutableElement> getDeclaredMethodsInSuperTypes(TypeElement declaringElement, String name, TypeMirror... params) {
        List<ExecutableElement> superMethods = new ArrayList<>();
        List<TypeElement> superElements = getSuperTypes(declaringElement);

        for (TypeElement superElement : superElements) {
            ExecutableElement superMethod = getDeclaredMethod(superElement, name, params);
            if (superMethod != null) {
                superMethods.add(superMethod);
            }
        }
        return superMethods;
    }

    /**
     * Determines whether {@code declaringElement} or any of its direct super types override a
     * default interface method.
     * <p>
     * Any declaration of the given method and signature in the direct super type hierarchy - even
     * if it is abstract - is considered to override the default method.
     *
     * @param declaringElement the type to check
     * @param name the name of the default interface method
     * @param params the signature of the method
     * @return true if any the default method is overridden
     */
    public static boolean isDefaultMethodOverridden(TypeElement declaringElement, String name, TypeMirror... params) {
        TypeElement element = declaringElement;
        while (element != null) {
            if (getDeclaredMethod(element, name, params) != null) {
                return true;
            }
            element = getSuperType(element);
        }
        return false;
    }

    public static boolean packageEquals(TypeMirror type1, TypeMirror type2) {
        return packageEquals(getPackageElement(type1), getPackageElement(type2));
    }

    public static boolean packageEquals(PackageElement pack1, PackageElement pack2) {
        return nameEquals(pack1.getQualifiedName(), pack2.getQualifiedName());
    }

    public static boolean nameEquals(Name name1, Name name2) {
        if (name1 instanceof NameImpl) {
            return name2.contentEquals(name1.toString());
        } else if (name2 instanceof NameImpl) {
            return name1.contentEquals(name2.toString());
        }
        return Objects.equals(name1, name2);
    }

    public static boolean typeEquals(TypeMirror type1, TypeMirror type2) {
        if (type1 == type2) {
            return true;
        } else if (type1 == null || type2 == null) {
            return false;
        } else {
            TypeKind kind1 = type1.getKind();
            if (kind1 == type2.getKind()) {
                switch (kind1) {
                    case ARRAY:
                        return typeEquals(((ArrayType) type1).getComponentType(), ((ArrayType) type2).getComponentType());
                    case DECLARED:
                        TypeElement type1Element = (TypeElement) ((DeclaredType) type1).asElement();
                        TypeElement type2Element = (TypeElement) ((DeclaredType) type2).asElement();
                        return nameEquals(type1Element.getQualifiedName(), type2Element.getQualifiedName());
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                    case DOUBLE:
                    case FLOAT:
                    case INT:
                    case LONG:
                    case SHORT:
                    case VOID:
                    case NULL:
                    case NONE:
                        return true;
                    default:
                        return getUniqueIdentifier(type1).equals(getUniqueIdentifier(type2));
                }
            } else {
                return false;
            }
        }
    }

    public static boolean typeEqualsAny(TypeMirror type1, TypeMirror... types) {
        for (TypeMirror type2 : types) {
            if (typeEquals(type1, type2)) {
                return true;
            }
        }
        return false;
    }

    public static boolean typeEqualsAny(TypeMirror type1, List<? extends TypeMirror> types) {
        for (TypeMirror type2 : types) {
            if (typeEquals(type1, type2)) {
                return true;
            }
        }
        return false;
    }

    public static boolean areTypesCompatible(TypeMirror type1, TypeMirror type2) {
        if (typeEquals(type1, type2)) {
            return true;
        } else if (kindIsIntegral(type1.getKind())) {
            return kindIsIntegral(type2.getKind());
        } else if (type1.getKind() == TypeKind.NULL) {
            if (type2.getKind() == TypeKind.NULL) {
                return false;
            }
            return true;
        } else if (type2.getKind() == TypeKind.NULL) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean kindIsIntegral(TypeKind kind) {
        return kind == TypeKind.BYTE || kind == TypeKind.SHORT || kind == TypeKind.INT || kind == TypeKind.LONG;
    }

    public static List<String> getUniqueIdentifiers(List<TypeMirror> typeMirror) {
        List<String> ids = new ArrayList<>();
        for (TypeMirror type : typeMirror) {
            ids.add(getUniqueIdentifier(type));
        }
        return ids;
    }

    public static String getUniqueIdentifier(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.ARRAY) {
            return getUniqueIdentifier(((ArrayType) typeMirror).getComponentType()) + "[]";
        } else if (typeMirror.getKind() == TypeKind.TYPEVAR) {
            Element element = ((TypeVariable) typeMirror).asElement();
            String variableName = element.getSimpleName().toString();
            if (element.getEnclosingElement().getKind().isClass()) {
                return getUniqueIdentifier(element.getEnclosingElement().asType()) + "." + variableName;
            } else {
                return variableName;
            }
        } else {
            return getQualifiedName(typeMirror);
        }
    }

    public static int compareByTypeHierarchy(TypeMirror t1, TypeMirror t2) {
        if (typeEquals(t1, t2)) {
            return 0;
        }
        Set<String> t1SuperSet = new HashSet<>(getQualifiedSuperTypeNames(fromTypeMirror(t1)));
        if (t1SuperSet.contains(getQualifiedName(t2))) {
            return -1;
        }

        Set<String> t2SuperSet = new HashSet<>(getQualifiedSuperTypeNames(fromTypeMirror(t2)));
        if (t2SuperSet.contains(getQualifiedName(t1))) {
            return 1;
        }
        return 0;
    }

    public static int compareByTypeHierarchy(TypeMirror t1, Set<String> t1SuperSet, TypeMirror t2, Set<String> t2SuperSet) {
        if (typeEquals(t1, t2)) {
            return 0;
        }
        if (t1SuperSet.contains(getQualifiedName(t2))) {
            return -1;
        }

        if (t2SuperSet.contains(getQualifiedName(t1))) {
            return 1;
        }
        return 0;
    }

    public static boolean canThrowTypeExact(List<? extends TypeMirror> thrownTypes, TypeMirror exceptionType) {
        if (ElementUtils.containsType(thrownTypes, exceptionType)) {
            return true;
        }

        if (isRuntimeException(exceptionType)) {
            return true;
        }

        return false;
    }

    public static boolean canThrowType(List<? extends TypeMirror> thrownTypes, TypeMirror exceptionType) {
        if (canThrowTypeExact(thrownTypes, exceptionType)) {
            return true;
        }

        // search for any super types
        for (TypeElement typeElement : getSuperTypes(fromTypeMirror(exceptionType))) {
            if (ElementUtils.containsType(thrownTypes, typeElement.asType())) {
                return true;
            }
        }

        return false;
    }

    public static void setFinal(Set<Modifier> modifiers, boolean enabled) {
        if (enabled) {
            modifiers.add(Modifier.FINAL);
        } else {
            modifiers.remove(Modifier.FINAL);
        }
    }

    public static void setVisibility(Set<Modifier> modifiers, Modifier visibility) {
        Modifier current = getVisibility(modifiers);
        if (current != visibility) {
            if (current != null) {
                modifiers.remove(current);
            }
            if (visibility != null) {
                modifiers.add(visibility);
            }
        }
    }

    public static Modifier getVisibility(Set<Modifier> modifier) {
        for (Modifier mod : modifier) {
            if (mod == Modifier.PUBLIC || mod == Modifier.PRIVATE || mod == Modifier.PROTECTED) {
                return mod;
            }
        }
        return null;
    }

    private static boolean isRuntimeException(TypeMirror type) {
        Set<String> typeSuperSet = new HashSet<>(getQualifiedSuperTypeNames(fromTypeMirror(type)));
        return typeSuperSet.contains(RuntimeException.class.getCanonicalName()) || getQualifiedName(type).equals(RuntimeException.class.getCanonicalName());
    }

    private static boolean containsType(Collection<? extends TypeMirror> collection, TypeMirror type) {
        for (TypeMirror otherTypeMirror : collection) {
            if (typeEquals(otherTypeMirror, type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isObject(TypeMirror actualType) {
        return actualType.getKind() == TypeKind.DECLARED && castTypeElement(actualType).getQualifiedName().contentEquals("java.lang.Object");
    }

    public static TypeMirror fillInGenericWildcards(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return type;
        }
        DeclaredType declaredType = (DeclaredType) type;
        TypeElement element = (TypeElement) declaredType.asElement();
        if (element == null) {
            return type;
        }
        int typeParameters = element.getTypeParameters().size();
        if (typeParameters > 0 && declaredType.getTypeArguments().size() != typeParameters) {
            return ProcessorContext.getInstance().getEnvironment().getTypeUtils().erasure(type);
        }
        return type;
    }

    public static boolean hasGenericTypes(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            if (!((DeclaredType) type).getTypeArguments().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static TypeMirror eraseGenericTypes(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return type;
        }
        DeclaredType declaredType = (DeclaredType) type;
        if (declaredType.getTypeArguments().size() == 0) {
            return type;
        }
        return new DeclaredCodeTypeMirror((TypeElement) declaredType.asElement());
    }

    public static boolean variableEquals(VariableElement var1, VariableElement var2) {
        if (var1 == var2) {
            return true;
        } else if (var1 == null || var2 == null) {
            return false;
        }
        if (!var1.getSimpleName().equals(var2.getSimpleName())) {
            return false;
        }
        if (!ElementUtils.typeEquals(var1.asType(), var2.asType())) {
            return false;
        }
        if (!ElementUtils.elementEquals(var1.getEnclosingElement(), var2.getEnclosingElement())) {
            return false;
        }
        return true;
    }

    public static boolean signatureEquals(ExecutableElement e1, ExecutableElement e2) {
        if (!nameEquals(e1.getSimpleName(), e2.getSimpleName())) {
            return false;
        }
        if (e1.getParameters().size() != e2.getParameters().size()) {
            return false;
        }
        if (!typeEquals(e1.getReturnType(), e2.getReturnType())) {
            return false;
        }
        for (int i = 0; i < e1.getParameters().size(); i++) {
            if (!typeEquals(e1.getParameters().get(i).asType(), e2.getParameters().get(i).asType())) {
                return false;
            }
        }
        return true;
    }

    public static boolean executableEquals(ExecutableElement e1, ExecutableElement e2) {
        if (!signatureEquals(e1, e2)) {
            return false;
        }
        if (!ElementUtils.elementEquals(e1.getEnclosingElement(), e2.getEnclosingElement())) {
            return false;
        }
        return true;
    }

    public static boolean isOverridable(ExecutableElement ex) {
        Set<Modifier> mods = ex.getModifiers();
        return !mods.contains(FINAL) && !mods.contains(STATIC) && (mods.contains(PUBLIC) || mods.contains(PROTECTED));
    }

    public static List<ExecutableElement> getOverridableMethods(TypeElement t) {
        return ElementFilter.methodsIn(t.getEnclosedElements()).stream() //
                        .filter(ElementUtils::isOverridable).collect(Collectors.toList());
    }

    /**
     * Returns true if e1 is an override of e2.
     */
    public static boolean isOverride(ExecutableElement e1, ExecutableElement e2) {
        if (!isOverridable(e2)) {
            return false;
        }

        Set<Modifier> mods1 = e1.getModifiers();
        Set<Modifier> mods2 = e2.getModifiers();
        if (mods2.contains(PUBLIC)) {
            if (!mods1.contains(PUBLIC)) {
                return false;
            }
        } else { // e2 is protected
            if (!mods1.contains(PUBLIC) && !mods1.contains(PROTECTED)) {
                return false;
            }
        }
        if (mods1.contains(STATIC)) {
            return false;
        }

        // NB: we don't check covariance of return type or contravariance of parameters.
        return signatureEquals(e1, e2);
    }

    public static ExecutableElement findOverride(TypeElement subclass, ExecutableElement method) {
        for (ExecutableElement subclassMethod : ElementFilter.methodsIn(subclass.getEnclosedElements())) {
            if (ElementUtils.isOverride(subclassMethod, method)) {
                return subclassMethod;
            }
        }
        return null;
    }

    public static boolean elementEquals(Element element1, Element element2) {
        if (element1 == element2) {
            return true;
        } else if (element1 == null || element2 == null) {
            return false;
        } else if (element1.getKind() != element2.getKind()) {
            return false;
        }
        switch (element1.getKind()) {
            case FIELD:
            case ENUM_CONSTANT:
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER:
            case RESOURCE_VARIABLE:
                return variableEquals((VariableElement) element1, (VariableElement) element2);
            case CONSTRUCTOR:
            case METHOD:
            case INSTANCE_INIT:
            case STATIC_INIT:
                return executableEquals((ExecutableElement) element1, (ExecutableElement) element2);
            case CLASS:
            case ENUM:
            case INTERFACE:
            case ANNOTATION_TYPE:
            case RECORD:
                return typeEquals(element1.asType(), element2.asType());
            case PACKAGE:
                return packageEquals(((PackageElement) element1), (((PackageElement) element2)));
            case TYPE_PARAMETER:
                return nameEquals(element1.getSimpleName(), element2.getSimpleName());
            case MODULE:
                return nameEquals(((ModuleElement) element1).getQualifiedName(), ((ModuleElement) element2).getQualifiedName());
            default:
                throw new AssertionError("unsupported element type: " + element1.getKind());
        }
    }

    public static List<TypeMirror> sortTypes(List<TypeMirror> list, final boolean reverse) {
        Collections.sort(list, new Comparator<TypeMirror>() {
            public int compare(TypeMirror o1, TypeMirror o2) {
                if (reverse) {
                    return compareType(o2, o1);
                } else {
                    return compareType(o1, o2);
                }
            }
        });
        return list;
    }

    public static int compareType(TypeMirror signature1, TypeMirror signature2) {
        if (signature1 == null) {
            return 1;
        } else if (signature2 == null) {
            return -1;
        }

        if (ElementUtils.typeEquals(signature1, signature2)) {
            return 0;
        }

        if (signature1.getKind() == TypeKind.DECLARED && signature2.getKind() == TypeKind.DECLARED) {
            TypeElement element1 = ElementUtils.fromTypeMirror(signature1);
            TypeElement element2 = ElementUtils.fromTypeMirror(signature2);

            if (ElementUtils.getDirectSuperTypes(element1).contains(element2)) {
                return -1;
            } else if (ElementUtils.getDirectSuperTypes(element2).contains(element1)) {
                return 1;
            }
        }
        return ElementUtils.getSimpleName(signature1).compareTo(ElementUtils.getSimpleName(signature2));
    }

    public static List<TypeMirror> uniqueSortedTypes(Collection<TypeMirror> types, boolean reverse) {
        return sortTypes(new ArrayList<>(uniqueTypes(types)), reverse);
    }

    @SuppressWarnings("cast")
    public static Collection<TypeMirror> uniqueTypes(Collection<TypeMirror> types) {
        if (types.isEmpty()) {
            return types;
        } else if (types.size() <= 1) {
            return types;
        }
        Map<String, TypeMirror> uniqueTypeMap = new LinkedHashMap<>();
        for (TypeMirror type : types) {
            uniqueTypeMap.put(ElementUtils.getUniqueIdentifier(type), type);
        }
        return uniqueTypeMap.values();
    }

    public static int compareMethod(ExecutableElement method1, ExecutableElement method2) {
        List<? extends VariableElement> parameters1 = method1.getParameters();
        List<? extends VariableElement> parameters2 = method2.getParameters();
        if (parameters1.size() != parameters2.size()) {
            return Integer.compare(parameters1.size(), parameters2.size());
        }

        int result = 0;
        for (int i = 0; i < parameters1.size(); i++) {
            VariableElement var1 = parameters1.get(i);
            VariableElement var2 = parameters2.get(i);
            result = compareType(var1.asType(), var2.asType());
            if (result != 0) {
                return result;
            }
        }

        result = method1.getSimpleName().toString().compareTo(method2.getSimpleName().toString());

        if (result == 0) {
            // if still no difference sort by enclosing type name
            TypeElement enclosingType1 = ElementUtils.findNearestEnclosingType(method1).orElseThrow(AssertionError::new);
            TypeElement enclosingType2 = ElementUtils.findNearestEnclosingType(method2).orElseThrow(AssertionError::new);
            result = enclosingType1.getQualifiedName().toString().compareTo(enclosingType2.getQualifiedName().toString());
        }
        return result;
    }

    public static List<AnnotationMirror> getRepeatedAnnotation(List<? extends AnnotationMirror> mirrors, DeclaredType base) {
        DeclaredType repeatableType = ProcessorContext.getInstance().getDeclaredType(Repeatable.class);
        AnnotationMirror repeatable = findAnnotationMirror(base.asElement(), repeatableType);
        TypeMirror repeat = null;
        if (repeatable != null) {
            repeat = ElementUtils.getAnnotationValue(TypeMirror.class, repeatable, "value");
        }
        List<AnnotationMirror> annotationMirrors = new ArrayList<>();
        AnnotationMirror repeatMirror = repeat != null ? ElementUtils.findAnnotationMirror(mirrors, repeat) : null;
        if (repeatMirror != null) {
            annotationMirrors.addAll(ElementUtils.getAnnotationValueList(AnnotationMirror.class, repeatMirror, "value"));
        }
        AnnotationMirror baseMirror = ElementUtils.findAnnotationMirror(mirrors, base);
        if (baseMirror != null) {
            annotationMirrors.add(baseMirror);
        }
        return annotationMirrors;
    }

    public static boolean isVisible(Element accessingElement, Element accessedElement) {
        Modifier visibility = ElementUtils.getVisibility(accessedElement.getModifiers());
        if (accessedElement.getKind() == ElementKind.PARAMETER) {
            Element methodElement = accessedElement.getEnclosingElement();
            if (methodElement == null) {
                // parameter with disconnected method. need to assume visible.
                return true;
            }
            // if parameter is referenced, make sure method is visible
            return isVisible(accessingElement, methodElement);
        }

        if (visibility == Modifier.PUBLIC) {
            return true;
        } else if (visibility == Modifier.PRIVATE) {
            return false;
        } else {
            if (visibility == Modifier.PROTECTED) {
                TypeElement accessedType = findNearestEnclosingType(accessedElement).orElse(null);
                TypeElement accessingType = findNearestEnclosingType(accessingElement).orElse(null);
                if (accessedType != null && accessingType != null) {
                    if (ElementUtils.typeEquals(accessedType.asType(), accessingType.asType())) {
                        return true;
                    } else if (ElementUtils.isSubtype(accessingType.asType(), accessedType.asType())) {
                        return true;
                    }
                }
            }
            PackageElement thisPackageElement = ElementUtils.findPackageElement(accessingElement);
            PackageElement otherPackageElement = ElementUtils.findPackageElement(accessedElement);
            if (otherPackageElement != null && !ElementUtils.packageEquals(thisPackageElement, otherPackageElement)) {
                return false;
            }
            Element enclosing = accessedElement.getEnclosingElement();
            while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
                if (!isVisible(accessingElement, enclosing)) {
                    return false;
                }
                enclosing = enclosing.getEnclosingElement();
            }
        }
        return true;
    }

    public static TypeElement castTypeElement(TypeMirror mirror) {
        if (mirror.getKind() == TypeKind.DECLARED) {
            return (TypeElement) ((DeclaredType) mirror).asElement();
        }
        return null;
    }

    public static String getReadableReference(Element relativeTo, Element element) {
        String parent;
        switch (element.getKind()) {
            case CLASS:
            case INTERFACE:
            case ENUM:
                // same package
                TypeElement type = (TypeElement) element;
                if (ElementUtils.elementEquals(findPackageElement(relativeTo),
                                findPackageElement(element))) {
                    if (!isDeclaredIn(relativeTo, type)) {
                        Element enclosing = element.getEnclosingElement();
                        if (enclosing.getKind().isClass() || enclosing.getKind().isInterface()) {
                            return getReadableReference(relativeTo, enclosing) + "." + getSimpleName(type);
                        }
                    }
                    return getSimpleName(type);
                } else {
                    return getQualifiedName(type);
                }
            case PACKAGE:
                return ((PackageElement) element).getQualifiedName().toString();
            case CONSTRUCTOR:
            case METHOD:
                parent = getReadableReference(relativeTo, element.getEnclosingElement());
                return parent + "." + getReadableSignature((ExecutableElement) element);
            case PARAMETER:
                Element enclosing = element.getEnclosingElement();
                if (enclosing instanceof ExecutableElement) {
                    ExecutableElement method = (ExecutableElement) enclosing;
                    int highlightIndex = method.getParameters().indexOf(element);
                    if (highlightIndex != -1) {
                        parent = getReadableReference(relativeTo, method.getEnclosingElement());
                        return parent + "." + getReadableSignature(method, highlightIndex);
                    }
                }
                parent = getReadableReference(relativeTo, enclosing);
                return " parameter " + element.getSimpleName().toString() + " in " + parent;
            case FIELD:
                parent = getReadableReference(relativeTo, element.getEnclosingElement());
                return parent + "." + element.getSimpleName().toString();
            default:
                return "Unknown Element";
        }
    }

    public static boolean isDeclaredIn(Element search, Element elementHierarchy) {
        Element searchEnclosing = search.getEnclosingElement();
        while (searchEnclosing != null) {
            if (ElementUtils.elementEquals(searchEnclosing, elementHierarchy)) {
                return true;
            }
            searchEnclosing = searchEnclosing.getEnclosingElement();
        }
        return false;

    }

    public static String getBinaryName(TypeElement provider) {
        if (provider instanceof GeneratedElement) {
            String packageName = getPackageName(provider);
            Element enclosing = provider.getEnclosingElement();
            StringBuilder b = new StringBuilder();
            b.append(provider.getSimpleName().toString());
            while (enclosing != null) {
                ElementKind kind = enclosing.getKind();
                if ((kind.isClass() || kind.isInterface()) && enclosing instanceof TypeElement) {
                    b.insert(0, enclosing.getSimpleName().toString() + "$");
                } else {
                    break;
                }
                enclosing = enclosing.getEnclosingElement();
            }
            b.insert(0, packageName + ".");
            return b.toString();
        } else {
            return ProcessorContext.getInstance().getEnvironment().getElementUtils().getBinaryName(provider).toString();
        }
    }

    public static String basicTypeId(TypeMirror type) {
        return switch (type.getKind()) {
            case BOOLEAN -> "Z";
            case BYTE -> "B";
            case SHORT -> "S";
            case INT -> "I";
            case LONG -> "J";
            case CHAR -> "C";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            default -> "L";
        };
    }

    public static final int COMPRESSED_POINTER_SIZE = 4;
    public static final int COMPRESSED_HEADER_SIZE = 12;

    public static int getCompressedReferenceSize(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case BOOLEAN:
            case BYTE:
                return 1;
            case SHORT:
            case CHAR:
                return 2;
            case INT:
            case FLOAT:
                return 4;
            case DOUBLE:
            case LONG:
                return 8;
            case DECLARED:
            case ARRAY:
            case TYPEVAR:
                return COMPRESSED_POINTER_SIZE;
            case VOID:
            case NULL:
            case EXECUTABLE:
                // unknown
                return 0;
            default:
                throw new RuntimeException("Unknown type specified " + mirror.getKind());
        }
    }

    public static Idempotence getIdempotent(ExecutableElement method) {
        TruffleTypes types = ProcessorContext.types();
        if (findAnnotationMirror(method, types.Idempotent) != null) {
            return Idempotence.IDEMPOTENT;
        }
        if (findAnnotationMirror(method, types.NonIdempotent) != null) {
            return Idempotence.NON_IDEMPOTENT;
        }

        if (types.isBuiltinIdempotent(method)) {
            return Idempotence.IDEMPOTENT;
        }
        if (types.isBuiltinNonIdempotent(method)) {
            return Idempotence.NON_IDEMPOTENT;
        }

        return Idempotence.UNKNOWN;
    }

    /**
     * Loads all members in declaration order but filters members of truffle Node and Object. Useful
     * to load all members of template types.
     */
    public static List<Element> loadFilteredMembers(TypeElement templateType) {
        ProcessorContext context = ProcessorContext.getInstance();
        List<Element> elements = loadAllMembers(templateType);
        Iterator<Element> elementIterator = elements.iterator();
        while (elementIterator.hasNext()) {
            Element element = elementIterator.next();
            // not interested in methods of Node
            if (typeEquals(element.getEnclosingElement().asType(), context.getTypes().Node)) {
                elementIterator.remove();
            }
            // not interested in methods of Object
            if (typeEquals(element.getEnclosingElement().asType(), context.getType(Object.class))) {
                elementIterator.remove();
            }
        }
        return elements;
    }

    /**
     * Loads all members in declaration order. This returns members of the entire type hierarcy.
     */
    public static List<Element> loadAllMembers(TypeElement templateType) {
        return newElementList(CompilerFactory.getCompiler(templateType).getAllMembersInDeclarationOrder(ProcessorContext.getInstance().getEnvironment(), templateType));
    }

    /**
     * @see "https://bugs.openjdk.java.net/browse/JDK-8039214"
     */
    @SuppressWarnings("unused")
    public static List<Element> newElementList(List<? extends Element> src) {
        List<Element> workaround = new ArrayList<Element>(src);
        return workaround;
    }

    public static ExecutableElement findOverride(ExecutableElement method, TypeElement type) {
        TypeElement searchType = type;
        while (searchType != null && !elementEquals(method.getEnclosingElement(), searchType)) {
            ExecutableElement override = findInstanceMethod(searchType, method.getSimpleName().toString(), method.getParameters().stream().map(VariableElement::asType).toArray(TypeMirror[]::new));
            if (override != null) {
                return override;
            }
            searchType = castTypeElement(searchType.getSuperclass());
        }
        return null;
    }

}
