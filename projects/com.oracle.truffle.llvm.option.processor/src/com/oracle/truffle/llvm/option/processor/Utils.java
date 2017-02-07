/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.option.processor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

/**
 * Methods of this class are copied from com.oracle.truffle.dsl.processor.java.ElementUtils.
 */
final class Utils {
    public static String printException(Throwable e) {
        StringWriter string = new StringWriter();
        PrintWriter writer = new PrintWriter(string);
        e.printStackTrace(writer);
        writer.flush();
        string.flush();
        return e.getMessage() + "\r\n" + string.toString();
    }

    static String getSimpleSubClassName(TypeElement innerClass) {
        return getSimpleName(innerClass) + "Gen";
    }

    static String getSimpleName(Element element) {
        return getSimpleName(element.asType());
    }

    private static TypeMirror getTypeMirror(ProcessingEnvironment env, Class<?> clazz) {
        String name = clazz.getCanonicalName();
        TypeElement elem = env.getElementUtils().getTypeElement(name);
        return elem.asType();
    }

    private static String getSimpleName(TypeMirror mirror) {
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
                return "?";
            case ERROR:
                throw new RuntimeException("Type error " + mirror);
            default:
                throw new RuntimeException("Unknown type specified " + mirror.getKind() + " mirror: " + mirror);
        }
    }

    private static String getQualifiedName(TypeMirror mirror) {
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
                throw new RuntimeException("Type error " + mirror);
            case EXECUTABLE:
                return ((ExecutableType) mirror).toString();
            case NONE:
                return "$none";
            default:
                throw new RuntimeException("Unknown type specified " + mirror + " mirror: " + mirror);
        }
    }

    private static String getQualifiedName(TypeElement element) {
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

    private static TypeElement fromTypeMirror(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case DECLARED:
                return (TypeElement) ((DeclaredType) mirror).asElement();
            case ARRAY:
                return fromTypeMirror(((ArrayType) mirror).getComponentType());
            default:
                return null;
        }
    }

    private static String getDeclaredName(DeclaredType element, boolean includeTypeVariables) {
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

    private static String fixECJBinaryNameIssue(String name) {
        if (name.contains("$")) {
            int lastIndex = name.lastIndexOf('$');
            return name.substring(lastIndex + 1, name.length());
        }
        return name;
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

    static String getPackageName(TypeElement element) {
        return findPackageElement(element).getQualifiedName().toString();
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

    private static List<Element> getElementHierarchy(Element e) {
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

    static String getFullOptionsClassName(TypeElement clazz) {
        return getPackageName(clazz) + "." + getSimpleSubClassName(clazz);
    }

    private static boolean typeEquals(TypeMirror type1, TypeMirror type2) {
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

    private static String getUniqueIdentifier(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.ARRAY) {
            return getUniqueIdentifier(((ArrayType) typeMirror).getComponentType()) + "[]";
        } else {
            return getQualifiedName(typeMirror);
        }
    }

    static boolean isInt(ProcessingEnvironment processingEnv, TypeMirror e) {
        return Utils.typeEquals(e, Utils.getTypeMirror(processingEnv, Integer.class));
    }

    static boolean isBoolean(ProcessingEnvironment processingEnv, TypeMirror e) {
        return Utils.typeEquals(e, Utils.getTypeMirror(processingEnv, Boolean.class));
    }

    static boolean isString(ProcessingEnvironment processingEnv, TypeMirror e) {
        return Utils.typeEquals(e, Utils.getTypeMirror(processingEnv, String.class));
    }

    static boolean isStringArr(TypeMirror e) {
        return e.toString().equals("java.lang.String[]");
    }

}
