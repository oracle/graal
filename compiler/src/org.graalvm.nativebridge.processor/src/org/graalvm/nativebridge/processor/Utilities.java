/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class Utilities {

    private Utilities() {
    }

    static void encodeType(StringBuilder into, TypeMirror type, String arrayStart, Function<TypeElement, String> declaredTypeNameFactory) {
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

    static CharSequence encodeMethodSignature(Elements elements, Types types, TypeMirror returnType, TypeMirror... parameterTypes) {
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

    static CharSequence getTypeName(TypeMirror type) {
        StringBuilder res = new StringBuilder();
        Utilities.printType(res, type, false);
        return res;
    }

    static CharSequence printMethod(ExecutableElement method) {
        return printMethod(method.getModifiers(), method.getSimpleName(), method.getReturnType(), method.getParameters().toArray(new VariableElement[0]));
    }

    static CharSequence printMethod(Set<Modifier> modifiers, CharSequence name, TypeMirror returnType, VariableElement... parameters) {
        return printMethod(modifiers, name, returnType,
                        Arrays.stream(parameters).map((e) -> new SimpleImmutableEntry<>(e.asType(), e.getSimpleName())).collect(Collectors.toList()));
    }

    static CharSequence printMethod(Set<Modifier> modifiers, CharSequence name, TypeMirror returnType, TypeMirror... parameters) {
        List<Map.Entry<? extends TypeMirror, ? extends CharSequence>> params = new ArrayList<>(parameters.length);
        int i = 0;
        for (TypeMirror parameter : parameters) {
            params.add(new SimpleImmutableEntry<>(parameter, "p" + i++));
        }
        return printMethod(modifiers, name, returnType, params);
    }

    static CharSequence printMethod(Set<Modifier> modifiers, CharSequence name, TypeMirror returnType,
                    List<? extends Map.Entry<? extends TypeMirror, ? extends CharSequence>> parameters) {
        StringBuilder res = new StringBuilder();
        printModifiers(res, modifiers);
        if (res.length() > 0) {
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

    static CharSequence printField(VariableElement field) {
        return printField(field.getModifiers(), field.getSimpleName(), field.asType());
    }

    static CharSequence printField(Set<Modifier> modifiers, CharSequence name, TypeMirror type) {
        StringBuilder res = new StringBuilder();
        printModifiers(res, modifiers);
        if (res.length() > 0) {
            res.append(" ");
        }
        printType(res, type, false);
        res.append(" ");
        res.append(name);
        return res;
    }

    private static void printModifiers(StringBuilder into, Set<Modifier> modifiers) {
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

    static void printType(StringBuilder into, TypeMirror type, boolean fqn) {
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
                TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
                into.append(fqn ? typeElement.getQualifiedName() : typeElement.getSimpleName());
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

    static String cSymbol(String name) {
        return name.replace("_", "_1").replace("$", "_00024").replace('.', '_');
    }

    static boolean contains(Collection<? extends TypeMirror> collection, TypeMirror mirror, Types types) {
        for (TypeMirror element : collection) {
            if (types.isSameType(element, mirror)) {
                return true;
            }
        }
        return false;
    }

    static PackageElement getEnclosingPackageElement(TypeElement typeElement) {
        return (PackageElement) typeElement.getEnclosingElement();
    }

    static CharSequence javaMemberName(CharSequence... nameComponents) {
        StringBuilder result = new StringBuilder();
        for (CharSequence component : nameComponents) {
            if (result.length() == 0) {
                result.append(component);
            } else {
                String strComponent = component.toString();
                result.append(Character.toUpperCase(strComponent.charAt(0)));
                result.append(strComponent.substring(1));
            }
        }
        return result.toString();
    }
}
