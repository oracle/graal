/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.io.*;
import java.lang.annotation.*;
import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.dsl.processor.ast.*;
import com.oracle.truffle.dsl.processor.ast.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.compiler.*;

/**
 * THIS IS NOT PUBLIC API.
 */
public class Utils {

    public static ExecutableElement findExecutableElement(DeclaredType type, String name) {
        List<? extends ExecutableElement> elements = ElementFilter.methodsIn(type.asElement().getEnclosedElements());
        for (ExecutableElement executableElement : elements) {
            if (executableElement.getSimpleName().toString().equals(name)) {
                return executableElement;
            }
        }
        return null;
    }

    public static boolean needsCastTo(ProcessorContext context, TypeMirror sourceType, TypeMirror targetType) {
        if (typeEquals(sourceType, targetType)) {
            return false;
        } else if (isObject(targetType)) {
            return false;
        } else if (isVoid(targetType)) {
            return false;
        } else if (isAssignable(context, sourceType, targetType)) {
            return false;
        }
        return true;
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

    public static String getMethodBody(ProcessingEnvironment env, ExecutableElement method) {
        if (method instanceof CodeExecutableElement) {
            return ((CodeExecutableElement) method).getBody();
        } else {
            return CompilerFactory.getCompiler(method).getMethodBody(env, method);
        }
    }

    public static TypeMirror boxType(ProcessorContext context, TypeMirror primitiveType) {
        TypeMirror boxedType = primitiveType;
        if (boxedType.getKind().isPrimitive()) {
            boxedType = context.getEnvironment().getTypeUtils().boxedClass((PrimitiveType) boxedType).asType();
        }
        return boxedType;
    }

    public static List<TypeMirror> asTypeMirrors(List<? extends Element> elements) {
        List<TypeMirror> types = new ArrayList<>(elements.size());
        for (Element element : elements) {
            types.add(element.asType());
        }
        return types;
    }

    public static DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs) {
        return new DeclaredCodeTypeMirror(typeElem, Arrays.asList(typeArgs));
    }

    public static List<AnnotationMirror> collectAnnotations(ProcessorContext context, AnnotationMirror markerAnnotation, String elementName, Element element,
                    Class<? extends Annotation> annotationClass) {
        List<AnnotationMirror> result = new ArrayList<>();
        if (markerAnnotation != null) {
            result.addAll(Utils.getAnnotationValueList(AnnotationMirror.class, markerAnnotation, elementName));
        }
        AnnotationMirror explicit = Utils.findAnnotationMirror(context.getEnvironment(), element, annotationClass);
        if (explicit != null) {
            result.add(explicit);
        }
        return result;
    }

    public static TypeMirror getCommonSuperType(ProcessorContext context, TypeMirror[] types) {
        if (types.length == 0) {
            return context.getType(Object.class);
        }
        TypeMirror prev = types[0];
        for (int i = 1; i < types.length; i++) {
            prev = getCommonSuperType(context, prev, types[i]);
        }
        return prev;
    }

    public static TypeMirror getCommonSuperType(ProcessorContext context, TypeMirror type1, TypeMirror type2) {
        if (typeEquals(type1, type2)) {
            return type1;
        }
        TypeElement element1 = fromTypeMirror(type1);
        TypeElement element2 = fromTypeMirror(type2);
        if (element1 == null || element2 == null) {
            return context.getType(Object.class);
        }

        List<TypeElement> element1Types = getDirectSuperTypes(element1);
        element1Types.add(0, element1);
        List<TypeElement> element2Types = getDirectSuperTypes(element2);
        element2Types.add(0, element2);

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
        // TODO toString does not guarantee a good signature
        return method.toString();
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

    public static boolean isAssignable(ProcessorContext context, TypeMirror from, TypeMirror to) {
        if (!(from instanceof CodeTypeMirror) && !(to instanceof CodeTypeMirror)) {
            return context.getEnvironment().getTypeUtils().isAssignable(context.reloadType(from), context.reloadType(to));
        } else {
            return isAssignableImpl(context, from, to);
        }
    }

    private static boolean isAssignableImpl(ProcessorContext context, TypeMirror from, TypeMirror to) {
        // JLS 5.1.1 identity conversion
        if (Utils.typeEquals(from, to)) {
            return true;
        }

        if (isObject(to)) {
            return true;
        }

        // JLS 5.1.2 widening primitives
        if (Utils.isPrimitive(from) && Utils.isPrimitive(to)) {
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
        } else if (Utils.isPrimitive(from) || Utils.isPrimitive(to)) {
            return false;
        }

        if (from instanceof ArrayType && to instanceof ArrayType) {
            return isAssignable(context, ((ArrayType) from).getComponentType(), ((ArrayType) to).getComponentType());
        }

        if (from instanceof ArrayType || to instanceof ArrayType) {
            return false;
        }

        TypeElement fromType = Utils.fromTypeMirror(from);
        TypeElement toType = Utils.fromTypeMirror(to);
        if (fromType == null || toType == null) {
            return false;
        }
        // JLS 5.1.6 narrowing reference conversion

        List<TypeElement> superTypes = Utils.getSuperTypes(fromType);
        for (TypeElement superType : superTypes) {
            if (Utils.typeEquals(superType.asType(), to)) {
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
                return getDeclaredName((DeclaredType) mirror);
            case ARRAY:
                return getSimpleName(((ArrayType) mirror).getComponentType()) + "[]";
            case VOID:
                return "void";
            case WILDCARD:
                return getWildcardName((WildcardType) mirror);
            case TYPEVAR:
                return "?";
            case ERROR:
                throw new CompileErrorException("Type error " + mirror);
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

    private static String getDeclaredName(DeclaredType element) {
        String simpleName = fixECJBinaryNameIssue(element.asElement().getSimpleName().toString());

        if (element.getTypeArguments().size() == 0) {
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

    public static boolean isVoid(TypeMirror mirror) {
        return mirror.getKind() == TypeKind.VOID;
    }

    public static boolean isPrimitive(TypeMirror mirror) {
        return mirror.getKind().isPrimitive();
    }

    public static boolean isPrimitiveOrVoid(TypeMirror mirror) {
        return isPrimitive(mirror) || isVoid(mirror);
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

    public static VariableElement findDeclaredField(TypeMirror type, String singletonName) {
        List<VariableElement> elements = ElementFilter.fieldsIn(fromTypeMirror(type).getEnclosedElements());
        for (VariableElement var : elements) {
            if (var.getSimpleName().toString().equals(singletonName)) {
                return var;
            }
        }
        return null;
    }

    public static TypeElement findRootEnclosingType(Element element) {
        List<Element> elements = getElementHierarchy(element);

        for (int i = elements.size() - 1; i >= 0; i--) {
            if (elements.get(i).getKind().isClass()) {
                return (TypeElement) elements.get(i);
            }
        }

        return null;
    }

    public static List<Element> getElementHierarchy(Element e) {
        List<Element> elements = new ArrayList<>();
        elements.add(e);

        Element enclosing = e.getEnclosingElement();
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            elements.add(enclosing);
            enclosing = enclosing.getEnclosingElement();
        }
        if (enclosing != null) {
            elements.add(enclosing);
        }
        return elements;
    }

    public static TypeElement findNearestEnclosingType(Element element) {
        List<Element> elements = getElementHierarchy(element);
        for (Element e : elements) {
            if (e.getKind().isClass()) {
                return (TypeElement) e;
            }
        }
        return null;
    }

    public static List<TypeElement> getDirectSuperTypes(TypeElement element) {
        List<TypeElement> types = new ArrayList<>();
        if (element.getSuperclass() != null) {
            TypeElement superElement = fromTypeMirror(element.getSuperclass());
            if (superElement != null) {
                types.add(superElement);
                types.addAll(getDirectSuperTypes(superElement));
            }
        }

        return types;
    }

    public static List<TypeMirror> getAssignableTypes(ProcessorContext context, TypeMirror type) {
        if (isPrimitive(type)) {
            return Arrays.asList(type, boxType(context, type), context.getType(Object.class));
        } else if (type.getKind() == TypeKind.ARRAY) {
            return Arrays.asList(type, context.getType(Object.class));
        } else if (type.getKind() == TypeKind.DECLARED) {
            List<TypeElement> types = getSuperTypes(fromTypeMirror(type));
            List<TypeMirror> mirrors = new ArrayList<>(types.size());
            mirrors.add(type);
            for (TypeElement typeElement : types) {
                mirrors.add(typeElement.asType());
            }
            return mirrors;
        } else {
            return Collections.emptyList();
        }
    }

    public static List<TypeElement> getSuperTypes(TypeElement element) {
        List<TypeElement> types = new ArrayList<>();
        List<TypeElement> superTypes = null;
        List<TypeElement> superInterfaces = null;
        if (element.getSuperclass() != null) {
            TypeElement superElement = fromTypeMirror(element.getSuperclass());
            if (superElement != null) {
                types.add(superElement);
                superTypes = getSuperTypes(superElement);
            }
        }
        for (TypeMirror interfaceMirror : element.getInterfaces()) {
            TypeElement interfaceElement = fromTypeMirror(interfaceMirror);
            if (interfaceElement != null) {
                types.add(interfaceElement);
                superInterfaces = getSuperTypes(interfaceElement);
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

    public static String getPackageName(TypeElement element) {
        return findPackageElement(element).getQualifiedName().toString();
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
            default:
                throw new RuntimeException("Unknown type specified " + mirror.getKind());
        }
    }

    public static String createConstantName(String simpleName) {
        // TODO use camel case to produce underscores.
        return simpleName.toString().toUpperCase();
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

    @SuppressWarnings("unchecked")
    public static <T> List<T> getAnnotationValueList(Class<T> expectedListType, AnnotationMirror mirror, String name) {
        List<? extends AnnotationValue> values = getAnnotationValue(List.class, mirror, name);
        List<T> result = new ArrayList<>();

        if (values != null) {
            for (AnnotationValue value : values) {
                T annotationValue = resolveAnnotationValue(expectedListType, value);
                if (annotationValue != null) {
                    result.add(annotationValue);
                }
            }
        }
        return result;
    }

    public static <T> T getAnnotationValue(Class<T> expectedType, AnnotationMirror mirror, String name) {
        return resolveAnnotationValue(expectedType, getAnnotationValue(mirror, name));
    }

    @SuppressWarnings({"unchecked"})
    private static <T> T resolveAnnotationValue(Class<T> expectedType, AnnotationValue value) {
        if (value == null) {
            return null;
        }

        Object unboxedValue = value.accept(new AnnotationValueVisitorImpl(), null);
        if (unboxedValue != null) {
            if (expectedType == TypeMirror.class && unboxedValue instanceof String) {
                return null;
            }
            if (!expectedType.isAssignableFrom(unboxedValue.getClass())) {
                throw new ClassCastException(unboxedValue.getClass().getName() + " not assignable from " + expectedType.getName());
            }
        }
        return (T) unboxedValue;
    }

    public static AnnotationValue getAnnotationValue(AnnotationMirror mirror, String name) {
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
        if (value == null) {
            value = valueMethod.getDefaultValue();
        }

        return value;
    }

    private static class AnnotationValueVisitorImpl extends AbstractAnnotationValueVisitor7<Object, Void> {

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

    public static boolean getAnnotationValueBoolean(AnnotationMirror mirror, String name) {
        return (Boolean) getAnnotationValue(mirror, name).getValue();
    }

    public static String printException(Throwable e) {
        StringWriter string = new StringWriter();
        PrintWriter writer = new PrintWriter(string);
        e.printStackTrace(writer);
        writer.flush();
        return e.getMessage() + "\r\n" + string.toString();
    }

    public static AnnotationMirror findAnnotationMirror(ProcessingEnvironment processingEnv, Element element, Class<?> annotationClass) {
        return findAnnotationMirror(processingEnv, element.getAnnotationMirrors(), annotationClass);
    }

    public static AnnotationMirror findAnnotationMirror(ProcessingEnvironment processingEnv, List<? extends AnnotationMirror> mirrors, Class<?> annotationClass) {
        TypeElement expectedAnnotationType = processingEnv.getElementUtils().getTypeElement(annotationClass.getCanonicalName());
        return findAnnotationMirror(mirrors, expectedAnnotationType);
    }

    public static AnnotationMirror findAnnotationMirror(List<? extends AnnotationMirror> mirrors, TypeElement expectedAnnotationType) {
        for (AnnotationMirror mirror : mirrors) {
            DeclaredType annotationType = mirror.getAnnotationType();
            TypeElement actualAnnotationType = (TypeElement) annotationType.asElement();
            if (actualAnnotationType.equals(expectedAnnotationType)) {
                return mirror;
            }
        }
        return null;
    }

    private static PackageElement findPackageElement(Element type) {
        List<Element> hierarchy = getElementHierarchy(type);
        for (Element element : hierarchy) {
            if (element.getKind() == ElementKind.PACKAGE) {
                return (PackageElement) element;
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
                if (param1.getKind() != TypeKind.TYPEVAR && param2.getKind() != TypeKind.TYPEVAR) {
                    if (!getQualifiedName(param1).equals(getQualifiedName(param2))) {
                        continue method;
                    }
                }
            }
            return method;
        }
        return null;
    }

    private static boolean isDeclaredMethod(TypeElement element, String name, TypeMirror[] params) {
        return getDeclaredMethod(element, name, params) != null;
    }

    public static boolean isDeclaredMethodInSuperType(TypeElement element, String name, TypeMirror[] params) {
        List<TypeElement> superElements = getSuperTypes(element);

        for (TypeElement typeElement : superElements) {
            if (isDeclaredMethod(typeElement, name, params)) {
                return true;
            }
        }
        return false;
    }

    private static ExecutableElement getDeclaredMethodInSuperType(TypeElement element, String name, TypeMirror[] params) {
        List<TypeElement> superElements = getSuperTypes(element);

        for (TypeElement typeElement : superElements) {
            ExecutableElement declared = getDeclaredMethod(typeElement, name, params);
            if (declared != null) {
                return declared;
            }
        }
        return null;
    }

    public static ExecutableElement getDeclaredMethodRecursive(TypeElement element, String name, TypeMirror[] params) {
        ExecutableElement declared = getDeclaredMethod(element, name, params);
        if (declared != null) {
            return declared;
        }
        return getDeclaredMethodInSuperType(element, name, params);
    }

    public static boolean typeEquals(TypeMirror type1, TypeMirror type2) {
        if (type1 == null && type2 == null) {
            return true;
        } else if (type1 == null || type2 == null) {
            return false;
        } else if (type1 == type2) {
            return true;
        }
        String qualified1 = getQualifiedName(type1);
        String qualified2 = getQualifiedName(type2);

        if (type1.getKind() == TypeKind.ARRAY || type2.getKind() == TypeKind.ARRAY) {
            if (type1.getKind() == TypeKind.ARRAY && type2.getKind() == TypeKind.ARRAY) {
                return typeEquals(((ArrayType) type1).getComponentType(), ((ArrayType) type2).getComponentType());
            } else {
                return false;
            }
        }
        return qualified1.equals(qualified2);
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

    public static boolean canThrowType(List<? extends TypeMirror> thrownTypes, TypeMirror exceptionType) {
        if (Utils.containsType(thrownTypes, exceptionType)) {
            return true;
        }

        if (isRuntimeException(exceptionType)) {
            return true;
        }

        // search for any super types
        TypeElement exceptionTypeElement = fromTypeMirror(exceptionType);
        List<TypeElement> superTypes = getSuperTypes(exceptionTypeElement);
        for (TypeElement typeElement : superTypes) {
            if (Utils.containsType(thrownTypes, typeElement.asType())) {
                return true;
            }
        }

        return false;
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
        String typeName = getQualifiedName(type);
        if (!typeSuperSet.contains(Throwable.class.getCanonicalName()) && !typeName.equals(Throwable.class.getCanonicalName())) {
            throw new IllegalArgumentException("Given type does not extend Throwable.");
        }
        return typeSuperSet.contains(RuntimeException.class.getCanonicalName()) || typeName.equals(RuntimeException.class.getCanonicalName());
    }

    private static boolean containsType(Collection<? extends TypeMirror> collection, TypeMirror type) {
        for (TypeMirror otherTypeMirror : collection) {
            if (typeEquals(otherTypeMirror, type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTopLevelClass(TypeMirror importType) {
        TypeElement type = fromTypeMirror(importType);
        if (type != null && type.getEnclosingElement() != null) {
            return !type.getEnclosingElement().getKind().isClass();
        }
        return true;
    }

    public static boolean isObject(TypeMirror actualType) {
        return actualType.getKind() == TypeKind.DECLARED && getQualifiedName(actualType).equals("java.lang.Object");
    }

    public static boolean isFieldAccessible(Element element, VariableElement variable) {
        TypeElement type = Utils.findNearestEnclosingType(element);
        TypeElement varType = Utils.findNearestEnclosingType(variable);

        while (type != null) {
            if (typeEquals(type.asType(), varType.asType())) {
                return true;
            }
            if (type.getSuperclass() != null) {
                type = Utils.fromTypeMirror(type.getSuperclass());
            } else {
                type = null;
            }
        }
        return false;
    }
}
