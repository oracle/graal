/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class CodeBuilder {

    private static final int INDENT_SIZE = 4;
    private static final Comparator<TypeElement> FQN_COMPARATOR = Comparator.comparing(a -> a.getQualifiedName().toString());

    private final CodeBuilder parent;
    private final PackageElement pkg;
    private final Types types;
    private final AbstractBridgeParser.AbstractTypeCache typeCache;
    private final Collection<TypeElement> toImport;
    private final StringBuilder body;
    private int indentLevel;
    private Scope scope;

    CodeBuilder(PackageElement pkg, Types types, AbstractBridgeParser.AbstractTypeCache typeCache) {
        this(null, pkg, types, typeCache, new TreeSet<>(FQN_COMPARATOR), new StringBuilder(), null);
    }

    CodeBuilder(CodeBuilder parent) {
        this(parent, parent.pkg, parent.types, parent.typeCache, parent.toImport, new StringBuilder(), parent.scope);
    }

    private CodeBuilder(CodeBuilder parent, PackageElement pkg, Types types, AbstractBridgeParser.AbstractTypeCache typeCache, Collection<TypeElement> toImport, StringBuilder body, Scope scope) {
        this.parent = parent;
        this.pkg = pkg;
        this.types = types;
        this.typeCache = typeCache;
        this.toImport = toImport;
        this.body = body;
        this.scope = scope;
    }

    CodeBuilder indent() {
        indentLevel++;
        return this;
    }

    CodeBuilder dedent() {
        indentLevel--;
        return this;
    }

    CodeBuilder classStart(Set<Modifier> modifiers, CharSequence name, DeclaredType superClass, List<DeclaredType> superInterfaces) {
        scope = new Scope(superClass != null ? superClass : typeCache.object, superInterfaces, scope);
        lineStart();
        writeModifiers(modifiers).spaceIfNeeded().write("class ").write(name);
        if (superClass != null) {
            write(" extends ").write(superClass);
        }
        if (!superInterfaces.isEmpty()) {
            write(" implements ");
            for (Iterator<DeclaredType> it = superInterfaces.iterator(); it.hasNext();) {
                write(it.next());
                if (it.hasNext()) {
                    write(", ");
                }
            }
        }
        lineEnd(" {");
        return this;
    }

    CodeBuilder classEnd() {
        scope = scope.parent;
        return line("}");
    }

    CodeBuilder methodStart(Set<Modifier> modifiers, CharSequence name, TypeMirror returnType,
                    List<? extends Parameter> params, List<? extends TypeMirror> exceptions) {
        lineStart();
        writeModifiers(modifiers).spaceIfNeeded();
        if (returnType != null) {
            write(returnType).space();
        }
        if (name != null) {
            write(name).write("(");
            for (Iterator<? extends Parameter> it = params.iterator(); it.hasNext();) {
                Parameter param = it.next();
                for (CharSequence annotation : param.annotations) {
                    write(annotation).space();
                }
                write(param.type).space().write(param.name);
                if (it.hasNext()) {
                    write(", ");
                }
            }
            write(")");
        }
        if (!exceptions.isEmpty()) {
            write(" throws ");
            for (Iterator<? extends TypeMirror> it = exceptions.iterator(); it.hasNext();) {
                write(it.next());
                if (it.hasNext()) {
                    write(", ");
                }
            }
        }
        if (modifiers.contains(Modifier.ABSTRACT) || modifiers.contains(Modifier.NATIVE)) {
            lineEnd(";");
        } else {
            lineEnd(" {");
        }
        return this;
    }

    CodeBuilder call(CharSequence methodName, CharSequence... args) {
        write(methodName).write("(");
        for (int i = 0; i < args.length; i++) {
            write(args[i]);
            if ((i + 1) < args.length) {
                write(", ");
            }
        }
        return write(")");
    }

    CodeBuilder newArray(TypeMirror componentType, CharSequence length) {
        return write("new ").write(componentType).write("[").write(length).write("]");
    }

    CodeBuilder newInstance(DeclaredType type, CharSequence... args) {
        return newInstance(new CodeBuilder(this).write(type).build(), Collections.emptyList(), args);
    }

    CodeBuilder newInstance(DeclaredType type, List<TypeMirror> actualTypeParameters, CharSequence... args) {
        return newInstance(new CodeBuilder(this).write(type).build(), actualTypeParameters, args);
    }

    CodeBuilder newInstance(CharSequence type, CharSequence... args) {
        return newInstance(type, Collections.emptyList(), args);
    }

    CodeBuilder newInstance(CharSequence type, List<TypeMirror> actualTypeParameters, CharSequence... args) {
        write("new ").write(type);
        if (!actualTypeParameters.isEmpty()) {
            write("<");
            for (Iterator<TypeMirror> it = actualTypeParameters.iterator(); it.hasNext();) {
                write(it.next());
                if (it.hasNext()) {
                    write(", ");
                }
            }
            write(">");
        }
        write("(");
        for (int i = 0; i < args.length; i++) {
            write(args[i]);
            if ((i + 1) < args.length) {
                write(", ");
            }
        }
        return write(")");
    }

    CodeBuilder invoke(CharSequence receiver, CharSequence methodName, CharSequence... args) {
        if (receiver != null) {
            write(receiver).write(".");
        }
        return call(methodName, args);
    }

    CodeBuilder invokeStatic(DeclaredType receiver, CharSequence methodName, CharSequence... args) {
        return write(types.erasure(receiver)).write(".").call(methodName, args);
    }

    CodeBuilder memberSelect(CharSequence receiver, CharSequence memberName, boolean brackets) {
        if (receiver != null) {
            if (brackets) {
                write("(");
            }
            write(receiver);
            if (brackets) {
                write(")");
            }
            write(".");
        }
        return write(memberName);
    }

    CodeBuilder memberSelect(TypeMirror clazz, CharSequence memberName, boolean brackets) {
        return memberSelect(new CodeBuilder(this).write(clazz).build(), memberName, brackets);
    }

    CodeBuilder parameterizedType(DeclaredType parameterizedType, TypeMirror... actualTypeParameters) {
        write(types.erasure(parameterizedType));
        write("<");
        for (int i = 0; i < actualTypeParameters.length; i++) {
            write(actualTypeParameters[i]);
            if (i + 1 < actualTypeParameters.length) {
                write(", ");
            }
        }
        return write(">");
    }

    CodeBuilder annotation(DeclaredType type, Object value) {
        write("@").write(type);
        if (value != null) {
            write("(").writeAnnotationAttributeValue(value).write(")");
        }
        return this;
    }

    CodeBuilder annotationWithAttributes(DeclaredType type, Map<? extends CharSequence, Object> attributes) {
        write("@").write(type);
        if (!attributes.isEmpty()) {
            write("(");
            for (Iterator<? extends Map.Entry<? extends CharSequence, Object>> it = attributes.entrySet().iterator(); it.hasNext();) {
                Map.Entry<? extends CharSequence, Object> e = it.next();
                write(e.getKey()).write(" = ").writeAnnotationAttributeValue(e.getValue());
                if (it.hasNext()) {
                    write(", ");
                }
            }
            write(")");
        }
        return this;
    }

    CodeBuilder classLiteral(TypeMirror type) {
        return write(types.erasure(type)).write(".class");
    }

    CodeBuilder typeLiteral(TypeMirror type) {
        return newInstance((DeclaredType) types.erasure(typeCache.typeLiteral), Collections.singletonList(type)).write("{}");
    }

    CodeBuilder cast(TypeMirror type, CharSequence value) {
        return cast(type, value, false);
    }

    CodeBuilder cast(TypeMirror type, CharSequence value, boolean brackets) {
        if (brackets) {
            write("(");
        }
        write("(").write(type).write(")").space().write(value);
        if (brackets) {
            write(")");
        }
        return this;
    }

    CodeBuilder forLoop(List<? extends CharSequence> init, CharSequence termination, List<? extends CharSequence> increment) {
        write("for(");
        boolean firstStm = true;
        for (CharSequence initStm : init) {
            if (firstStm) {
                firstStm = false;
            } else {
                write(",");
                space();
            }
            write(initStm);
        }
        write(";");
        if (termination != null) {
            space();
            write(termination);
        }
        write(";");
        firstStm = true;
        for (CharSequence incrementStm : increment) {
            if (firstStm) {
                firstStm = false;
            } else {
                write(",");
            }
            space();
            write(incrementStm);
        }
        return write(")");
    }

    CodeBuilder arrayElement(CharSequence array, CharSequence index) {
        return write(array).write("[").write(index).write("]");
    }

    CodeBuilder writeAnnotationAttributeValue(Object value) {
        if (value.getClass() == String.class) {
            write('"' + (String) value + '"');
        } else if (value instanceof DeclaredType) {
            classLiteral((DeclaredType) value);
        } else if (value.getClass().isArray()) {
            write("{");
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                writeAnnotationAttributeValue(Array.get(value, i));
                if ((i + 1) < len) {
                    write(", ");
                }
            }
            write("}");
        } else {
            write(String.valueOf(value));
        }
        return this;
    }

    CodeBuilder writeDefaultValue(TypeMirror type) {
        switch (types.erasure(type).getKind()) {
            case VOID:
                throw new IllegalArgumentException("The void type does not have default value.");
            case BOOLEAN:
                write("false");
                break;
            case BYTE:
            case CHAR:
            case INT:
            case LONG:
            case SHORT:
                write("0");
                break;
            case DOUBLE:
                write("0.0d");
                break;
            case FLOAT:
                write("0.0f");
                break;
            case DECLARED:
                write("null");
                break;
        }
        return this;
    }

    CodeBuilder writeModifiers(Set<Modifier> modifiers) {
        if (modifiers.contains(Modifier.ABSTRACT)) {
            write("abstract");
        }
        if (modifiers.contains(Modifier.PRIVATE)) {
            spaceIfNeeded();
            write("private");
        }
        if (modifiers.contains(Modifier.PROTECTED)) {
            spaceIfNeeded();
            write("protected");
        }
        if (modifiers.contains(Modifier.PUBLIC)) {
            spaceIfNeeded();
            write("public");
        }
        if (modifiers.contains(Modifier.STATIC)) {
            spaceIfNeeded();
            write("static");
        }
        if (modifiers.contains(Modifier.SYNCHRONIZED)) {
            spaceIfNeeded();
            write("synchronized");
        }
        if (modifiers.contains(Modifier.NATIVE)) {
            spaceIfNeeded();
            write("native");
        }
        if (modifiers.contains(Modifier.VOLATILE)) {
            spaceIfNeeded();
            write("volatile");
        }
        if (modifiers.contains(Modifier.FINAL)) {
            spaceIfNeeded();
            write("final");
        }
        return this;
    }

    CodeBuilder lineStart(CharSequence text) {
        return write(new String(new char[indentLevel * INDENT_SIZE]).replace('\0', ' ')).write(text);
    }

    CodeBuilder emptyLine() {
        return lineEnd("");
    }

    CodeBuilder lineStart() {
        return lineStart("");
    }

    CodeBuilder lineEnd(CharSequence text) {
        return write(text).write("\n");
    }

    CodeBuilder line(CharSequence line) {
        return lineStart(line).lineEnd("");
    }

    CodeBuilder write(CharSequence str) {
        body.append(str);
        return this;
    }

    CodeBuilder write(TypeElement te) {
        Element teEnclosing = te.getEnclosingElement();
        if (!teEnclosing.equals(pkg) && !isJavaLang(teEnclosing) && !isElementVisible(te)) {
            toImport.add(te);
        }
        return write(te.getSimpleName());
    }

    private boolean isElementVisible(TypeElement te) {
        return scope != null && scope.isElementVisible(te, types);
    }

    private static boolean isJavaLang(Element element) {
        return element.getKind() == ElementKind.PACKAGE && ((PackageElement) element).getQualifiedName().contentEquals("java.lang");
    }

    CodeBuilder write(TypeMirror type) {
        switch (type.getKind()) {
            case ARRAY:
                write(((ArrayType) type).getComponentType()).write("[]");
                break;
            case BOOLEAN:
                write("boolean");
                break;
            case BYTE:
                write("byte");
                break;
            case CHAR:
                write("char");
                break;
            case DECLARED:
                DeclaredType declaredType = ((DeclaredType) type);
                List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
                if (!typeArguments.isEmpty()) {
                    parameterizedType(declaredType, typeArguments.toArray(new TypeMirror[0]));
                } else {
                    write((TypeElement) declaredType.asElement());
                }
                break;
            case DOUBLE:
                write("double");
                break;
            case FLOAT:
                write("float");
                break;
            case INT:
                write("int");
                break;
            case LONG:
                write("long");
                break;
            case SHORT:
                write("short");
                break;
            case VOID:
                write("void");
                break;
            case WILDCARD:
                write("?");
                break;
        }
        return this;
    }

    CodeBuilder space() {
        return write(" ");
    }

    CodeBuilder spaceIfNeeded() {
        if (body.length() > 0 && !Character.isSpaceChar(body.charAt(body.length() - 1))) {
            write(" ");
        }
        return this;
    }

    String build() {
        if (parent == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("package ").append(pkg.getQualifiedName()).append(";\n\n");
            for (TypeElement typeElement : toImport) {
                sb.append("import ").append(typeElement.getQualifiedName()).append(";\n");
            }
            sb.append("\n");
            sb.append(body);
            return sb.toString();
        } else {
            return body.toString();
        }
    }

    static Parameter newParameter(TypeMirror type, CharSequence name, CharSequence... annotations) {
        return new Parameter(type, name, annotations);
    }

    static List<? extends Parameter> newParameters(List<? extends VariableElement> params,
                    List<? extends TypeMirror> parameterTypes) {
        if (params.size() != parameterTypes.size()) {
            throw new IllegalArgumentException(String.format("params.size(%d) != parameterTypes.size(%d)",
                            params.size(), parameterTypes.size()));
        }
        List<Parameter> result = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            result.add(newParameter(parameterTypes.get(i), params.get(i).getSimpleName()));
        }
        return result;
    }

    static final class Parameter {

        final TypeMirror type;
        final CharSequence name;
        final CharSequence[] annotations;

        private Parameter(TypeMirror type, CharSequence name, CharSequence[] annotations) {
            this.type = type;
            this.name = name;
            this.annotations = annotations;
        }
    }

    private static final class Scope {

        private final Scope parent;
        private final DeclaredType superClass;
        private final List<DeclaredType> superInterfaces;

        Scope(DeclaredType superClass, List<DeclaredType> superInterfaces, Scope parent) {
            this.superClass = superClass;
            this.superInterfaces = superInterfaces;
            this.parent = parent;
        }

        boolean isElementVisible(TypeElement type, Types types) {
            Element owner = type.getEnclosingElement();
            if (owner.getKind().isClass() || owner.getKind().isInterface()) {
                return isElementVisibleImpl((TypeElement) owner, types);
            }
            return false;
        }

        private boolean isElementVisibleImpl(TypeElement owner, Types types) {
            if (isInherited(owner, (TypeElement) ((DeclaredType) types.erasure(superClass)).asElement(), types)) {
                return true;
            }
            for (DeclaredType superInterface : superInterfaces) {
                if (isInherited(owner, (TypeElement) ((DeclaredType) types.erasure(superInterface)).asElement(), types)) {
                    return true;
                }
            }
            if (parent != null) {
                return parent.isElementVisibleImpl(owner, types);
            }
            return false;
        }

        private static boolean isInherited(TypeElement toCheck, TypeElement type, Types types) {
            if (toCheck.equals(type)) {
                return true;
            }
            TypeMirror superClz = type.getSuperclass();
            if (superClz.getKind() != TypeKind.NONE) {
                if (isInherited(toCheck, (TypeElement) ((DeclaredType) types.erasure(superClz)).asElement(), types)) {
                    return true;
                }
            }
            for (TypeMirror superIfc : type.getInterfaces()) {
                if (isInherited(toCheck, (TypeElement) ((DeclaredType) types.erasure(superIfc)).asElement(), types)) {
                    return true;
                }
            }
            return false;
        }
    }
}
