/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Repeatable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
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
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.GeneratedElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;

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
        TypeElement typeElement = context.getTypeElement(type);
        for (ExecutableElement method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if (method.getSimpleName().toString().equals(methodName)) {
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

    public static TypeMirror getType(ProcessingEnvironment processingEnv, Class<?> element) {
        if (element.isArray()) {
            return processingEnv.getTypeUtils().getArrayType(getType(processingEnv, element.getComponentType()));
        }
        if (element.isPrimitive()) {
            if (element == void.class) {
                return processingEnv.getTypeUtils().getNoType(TypeKind.VOID);
            }
            TypeKind typeKind;
            if (element == boolean.class) {
                typeKind = TypeKind.BOOLEAN;
            } else if (element == byte.class) {
                typeKind = TypeKind.BYTE;
            } else if (element == short.class) {
                typeKind = TypeKind.SHORT;
            } else if (element == char.class) {
                typeKind = TypeKind.CHAR;
            } else if (element == int.class) {
                typeKind = TypeKind.INT;
            } else if (element == long.class) {
                typeKind = TypeKind.LONG;
            } else if (element == float.class) {
                typeKind = TypeKind.FLOAT;
            } else if (element == double.class) {
                typeKind = TypeKind.DOUBLE;
            } else {
                assert false;
                return null;
            }
            return processingEnv.getTypeUtils().getPrimitiveType(typeKind);
        } else {
            TypeElement typeElement = getTypeElement(processingEnv, element.getCanonicalName());
            if (typeElement == null) {
                return null;
            }
            return processingEnv.getTypeUtils().erasure(typeElement.asType());
        }
    }

    public static TypeElement getTypeElement(final ProcessingEnvironment processingEnv, final CharSequence typeName) {
        return ModuleCache.getTypeElement(processingEnv, typeName);
    }

    public static ExecutableElement findExecutableElement(DeclaredType type, String name) {
        List<? extends ExecutableElement> elements = ElementFilter.methodsIn(type.asElement().getEnclosedElements());
        for (ExecutableElement executableElement : elements) {
            if (executableElement.getSimpleName().toString().equals(name) && !isDeprecated(executableElement)) {
                return executableElement;
            }
        }
        return null;
    }

    public static VariableElement findVariableElement(DeclaredType type, String name) {
        List<? extends VariableElement> elements = ElementFilter.fieldsIn(type.asElement().getEnclosedElements());
        for (VariableElement variableElement : elements) {
            if (variableElement.getSimpleName().toString().equals(name)) {
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

        // TODO more spec
        return false;
    }

    public static Set<Modifier> modifiers(Modifier... modifier) {
        return new LinkedHashSet<>(Arrays.asList(modifier));
    }

    public static String getTypeId(TypeMirror mirror) {
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
                return getTypeId(((ArrayType) mirror).getComponentType()) + "Array";
            case VOID:
                return "Void";
            case NULL:
                return "Null";
            case WILDCARD:
                StringBuilder b = new StringBuilder();
                WildcardType type = (WildcardType) mirror;
                if (type.getExtendsBound() != null) {
                    b.append("Extends").append(getTypeId(type.getExtendsBound()));
                } else if (type.getSuperBound() != null) {
                    b.append("Super").append(getTypeId(type.getExtendsBound()));
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
        StringBuilder b = new StringBuilder();
        if (type.getExtendsBound() != null) {
            b.append("? extends ").append(getSimpleName(type.getExtendsBound()));
        } else if (type.getSuperBound() != null) {
            b.append("? super ").append(getSimpleName(type.getExtendsBound()));
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
                return ((ExecutableType) mirror).toString();
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
            if (Character.isUpperCase(c) && i != 0) {
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

    private static class AnnotationValueVisitorImpl extends AbstractAnnotationValueVisitor8<Object, Void> {

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

    private static ExecutableElement getDeclaredMethod(TypeElement element, String name, TypeMirror[] params) {
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
                    if (!getQualifiedName(param1).equals(getQualifiedName(param2))) {
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

    public static boolean typeEquals(TypeMirror type1, TypeMirror type2) {
        if (type1 == type2) {
            return true;
        } else if (type1 == null || type2 == null) {
            return false;
        } else {
            if (type1.getKind() == type2.getKind()) {
                return getUniqueIdentifier(type1).equals(getUniqueIdentifier(type2));
            } else {
                return false;
            }
        }
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
        return actualType.getKind() == TypeKind.DECLARED && getQualifiedName(actualType).equals("java.lang.Object");
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
        if (!e1.getSimpleName().toString().equals(e2.getSimpleName().toString())) {
            return false;
        }
        if (e1.getParameters().size() != e2.getParameters().size()) {
            return false;
        }
        if (!ElementUtils.typeEquals(e1.getReturnType(), e2.getReturnType())) {
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

    public static boolean elementEquals(Element element1, Element element2) {
        if (element1 == element2) {
            return true;
        } else if (element1 == null || element2 == null) {
            return false;
        } else if (element1.getKind() != element2.getKind()) {
            return false;
        } else if (element1 instanceof VariableElement) {
            return variableEquals((VariableElement) element1, (VariableElement) element2);
        } else if (element1 instanceof ExecutableElement) {
            return executableEquals((ExecutableElement) element1, (ExecutableElement) element2);
        } else if (element1 instanceof TypeElement) {
            return typeEquals(element1.asType(), element2.asType());
        } else if (element1 instanceof PackageElement) {
            return element1.getSimpleName().equals(element2.getSimpleName());
        } else if (element1 instanceof TypeParameterElement) {
            return element1.getSimpleName().toString().equals(element2.getSimpleName().toString());
        } else {
            throw new AssertionError("unsupported element type");
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
        if (types.isEmpty()) {
            return new ArrayList<>(0);
        } else if (types.size() <= 1) {
            if (types instanceof List) {
                return (List<TypeMirror>) types;
            } else {
                return new ArrayList<>(types);
            }
        }
        Map<String, TypeMirror> sourceTypes = new HashMap<>();
        for (TypeMirror type : types) {
            sourceTypes.put(ElementUtils.getUniqueIdentifier(type), type);
        }
        return sortTypes(new ArrayList<>(sourceTypes.values()), reverse);
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
            String thisPackageElement = ElementUtils.getPackageName(accessingElement);
            String otherPackageElement = ElementUtils.getPackageName(accessedElement);
            if (otherPackageElement != null && !thisPackageElement.equals(otherPackageElement)) {
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
                parent = getReadableReference(relativeTo, element.getEnclosingElement());
                return parent + " parameter " + element.getSimpleName().toString();
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

}
