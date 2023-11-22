/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public final class Utilities {

    private Utilities() {
    }

    public static void encodeType(StringBuilder into, TypeMirror type, String arrayStart, Function<TypeElement, String> declaredTypeNameFactory) {
        String desc;
        switch (type.getKind()) {
            case BOOLEAN:
                desc = "Z";
                break;
            case BYTE:
                desc = "B";
                break;
            case CHAR:
                desc = "C";
                break;
            case SHORT:
                desc = "S";
                break;
            case INT:
                desc = "I";
                break;
            case LONG:
                desc = "J";
                break;
            case FLOAT:
                desc = "F";
                break;
            case DOUBLE:
                desc = "D";
                break;
            case VOID:
                desc = "V";
                break;
            case ARRAY:
                into.append(arrayStart);
                encodeType(into, ((ArrayType) type).getComponentType(), arrayStart, declaredTypeNameFactory);
                return;
            case DECLARED:
                desc = declaredTypeNameFactory.apply((TypeElement) ((DeclaredType) type).asElement());
                break;
            default:
                throw new IllegalArgumentException(String.valueOf(type.getKind()));
        }
        into.append(desc);
    }

    public static CharSequence encodeMethodSignature(Elements elements, Types types, TypeMirror returnType, TypeMirror... parameterTypes) {
        Function<TypeElement, String> toBinaryName = (e) -> 'L' + elements.getBinaryName(e).toString().replace('.', '/') + ';';
        StringBuilder builder = new StringBuilder();
        builder.append('(');
        for (TypeMirror parameterType : parameterTypes) {
            encodeType(builder, types.erasure(parameterType), "[", toBinaryName);
        }
        builder.append(')');
        encodeType(builder, types.erasure(returnType), "[", toBinaryName);
        return builder;
    }

    public static CharSequence getTypeName(TypeMirror type) {
        StringBuilder res = new StringBuilder();
        Utilities.printType(res, type, false);
        return res;
    }

    public static CharSequence printMethod(ExecutableElement method) {
        return printMethod(method.getModifiers(), method.getSimpleName(), method.getReturnType(), method.getParameters().toArray(new VariableElement[0]));
    }

    public static CharSequence printMethod(Set<Modifier> modifiers, CharSequence name, TypeMirror returnType, VariableElement... parameters) {
        return printMethod(modifiers, name, returnType,
                        Arrays.stream(parameters).map((e) -> new SimpleImmutableEntry<>(e.asType(), e.getSimpleName())).collect(Collectors.toList()));
    }

    public static CharSequence printMethod(Set<Modifier> modifiers, CharSequence name, TypeMirror returnType, TypeMirror... parameters) {
        List<Map.Entry<? extends TypeMirror, ? extends CharSequence>> params = new ArrayList<>(parameters.length);
        int i = 0;
        for (TypeMirror parameter : parameters) {
            params.add(new SimpleImmutableEntry<>(parameter, "p" + i++));
        }
        return printMethod(modifiers, name, returnType, params);
    }

    public static CharSequence printMethod(Set<Modifier> modifiers, CharSequence name, TypeMirror returnType,
                    List<? extends Map.Entry<? extends TypeMirror, ? extends CharSequence>> parameters) {
        StringBuilder res = new StringBuilder();
        printModifiers(res, modifiers);
        if (!res.isEmpty()) {
            res.append(" ");
        }
        if (returnType.getKind() != TypeKind.NONE) {
            printType(res, returnType, false);
            res.append(" ");
        }
        res.append(name);
        res.append("(");
        for (Map.Entry<? extends TypeMirror, ? extends CharSequence> parameter : parameters) {
            printType(res, parameter.getKey(), false);
            res.append(" ");
            res.append(parameter.getValue());
            res.append(", ");
        }
        res.delete(res.length() - 2, res.length());
        res.append(")");
        return res;
    }

    public static CharSequence printField(VariableElement field) {
        return printField(field.getModifiers(), field.getSimpleName(), field.asType());
    }

    public static CharSequence printField(Set<Modifier> modifiers, CharSequence name, TypeMirror type) {
        StringBuilder res = new StringBuilder();
        printModifiers(res, modifiers);
        if (!res.isEmpty()) {
            res.append(" ");
        }
        printType(res, type, false);
        res.append(" ");
        res.append(name);
        return res;
    }

    public static void printModifiers(StringBuilder into, Set<Modifier> modifiers) {
        int index = 0;
        for (Modifier modifier : modifiers) {
            switch (modifier) {
                case ABSTRACT:
                    into.append("abstract");
                    break;
                case PRIVATE:
                    into.append("private");
                    break;
                case PROTECTED:
                    into.append("protected");
                    break;
                case PUBLIC:
                    into.append("public");
                    break;
                case STATIC:
                    into.append("static");
                    break;
                case SYNCHRONIZED:
                    into.append("synchronized");
                    break;
                case NATIVE:
                    into.append("native");
                    break;
                case VOLATILE:
                    into.append("volatile");
                    break;
                case FINAL:
                    into.append("final");
                    break;
            }
            if (++index != modifiers.size()) {
                into.append(" ");
            }
        }
    }

    public static void printType(StringBuilder into, TypeMirror type, boolean fqn) {
        switch (type.getKind()) {
            case ARRAY:
                into.append(((ArrayType) type).getComponentType()).append("[]");
                break;
            case BOOLEAN:
                into.append("boolean");
                break;
            case BYTE:
                into.append("byte");
                break;
            case CHAR:
                into.append("char");
                break;
            case DECLARED:
                into.append(fqn ? getQualifiedName(type) : getSimpleName(type));
                break;
            case DOUBLE:
                into.append("double");
                break;
            case FLOAT:
                into.append("float");
                break;
            case INT:
                into.append("int");
                break;
            case LONG:
                into.append("long");
                break;
            case SHORT:
                into.append("short");
                break;
            case VOID:
                into.append("void");
                break;
        }
    }

    public static String getQualifiedName(TypeMirror type) {
        return ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
    }

    public static String getSimpleName(TypeMirror type) {
        return ((DeclaredType) type).asElement().getSimpleName().toString();
    }

    public static String cSymbol(String name) {
        return name.replace("_", "_1").replace("$", "_00024").replace('.', '_');
    }

    static TypeMirror jniTypeForJavaType(TypeMirror javaType, Types types, AbstractBridgeParser.AbstractTypeCache cache) {
        if (javaType.getKind().isPrimitive() || javaType.getKind() == TypeKind.VOID) {
            return javaType;
        }
        TypeMirror erasedType = types.erasure(javaType);
        switch (erasedType.getKind()) {
            case DECLARED:
                if (types.isSameType(cache.string, javaType)) {
                    return cache.jString;
                } else if (types.isSameType(cache.clazz, javaType)) {
                    return cache.jClass;
                } else if (types.isSubtype(javaType, cache.throwable)) {
                    return cache.jThrowable;
                } else {
                    return cache.jObject;
                }
            case ARRAY:
                TypeMirror componentType = types.erasure(((ArrayType) erasedType).getComponentType());
                return switch (componentType.getKind()) {
                    case BOOLEAN -> cache.jBooleanArray;
                    case BYTE -> cache.jByteArray;
                    case CHAR -> cache.jCharArray;
                    case SHORT -> cache.jShortArray;
                    case INT -> cache.jIntArray;
                    case LONG -> cache.jLongArray;
                    case FLOAT -> cache.jFloatArray;
                    case DOUBLE -> cache.jDoubleArray;
                    case DECLARED -> cache.jObjectArray;
                    default ->
                        throw new UnsupportedOperationException("Not supported for array of " + componentType.getKind());
                };
            default:
                throw new UnsupportedOperationException("Not supported for " + javaType.getKind());
        }
    }

    public static boolean contains(Collection<? extends TypeMirror> collection, TypeMirror mirror, Types types) {
        for (TypeMirror element : collection) {
            if (types.isSameType(element, mirror)) {
                return true;
            }
        }
        return false;
    }

    public static boolean equals(List<? extends TypeMirror> first, List<? extends TypeMirror> second, Types types) {
        if (first.size() != second.size()) {
            return false;
        }
        for (Iterator<? extends TypeMirror> fit = first.iterator(), sit = second.iterator(); fit.hasNext();) {
            TypeMirror ft = fit.next();
            TypeMirror st = sit.next();
            if (!types.isSameType(ft, st)) {
                return false;
            }
        }
        return true;
    }

    public static List<? extends TypeMirror> erasure(List<? extends TypeMirror> list, Types types) {
        List<TypeMirror> result = new ArrayList<>(list.size());
        for (TypeMirror type : list) {
            result.add(types.erasure(type));
        }
        return result;
    }

    public static PackageElement getEnclosingPackageElement(TypeElement typeElement) {
        Element element = typeElement.getEnclosingElement();
        while (element != null && element.getKind() != ElementKind.PACKAGE) {
            element = element.getEnclosingElement();
        }
        return (PackageElement) element;
    }

    public static CharSequence javaMemberName(CharSequence... nameComponents) {
        StringBuilder result = new StringBuilder();
        for (CharSequence component : nameComponents) {
            if (result.isEmpty()) {
                result.append(component);
            } else {
                String strComponent = component.toString();
                result.append(Character.toUpperCase(strComponent.charAt(0)));
                result.append(strComponent.substring(1));
            }
        }
        return result.toString();
    }

    public static Comparator<ExecutableElement> executableElementComparator(Elements elements, Types types) {
        return new ExecutableElementComparator(elements, types);
    }

    private static final class ExecutableElementComparator implements Comparator<ExecutableElement> {

        private final Elements elements;
        private final Types types;

        ExecutableElementComparator(Elements elements, Types types) {
            this.elements = elements;
            this.types = types;
        }

        @Override
        public int compare(ExecutableElement o1, ExecutableElement o2) {
            int res = o1.getSimpleName().toString().compareTo(o2.getSimpleName().toString());
            if (res == 0) {
                TypeMirror[] pt1 = ((ExecutableType) o1.asType()).getParameterTypes().toArray(new TypeMirror[0]);
                TypeMirror[] pt2 = ((ExecutableType) o2.asType()).getParameterTypes().toArray(new TypeMirror[0]);
                String sig1 = encodeMethodSignature(elements, types, o1.getReturnType(), pt1).toString();
                String sig2 = encodeMethodSignature(elements, types, o2.getReturnType(), pt2).toString();
                res = sig1.compareTo(sig2);
            }
            return res;
        }
    }
}
