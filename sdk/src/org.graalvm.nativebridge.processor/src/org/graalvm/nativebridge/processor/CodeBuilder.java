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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;

public final class CodeBuilder {

    private static final int INDENT_SIZE = 4;
    private static final Comparator<TypeElement> FQN_COMPARATOR = Comparator.comparing(a -> a.getQualifiedName().toString());

    private final CodeBuilder parent;
    private final PackageElement pkg;
    public final Types types;
    public final BaseTypeCache typeCache;
    private final Collection<TypeElement> toImport;
    private final Map<String, TypeElement> importedTypeNames;
    private final StringBuilder body;
    private int indentLevel;
    private Scope scope;

    public CodeBuilder(PackageElement pkg, Types types, BaseTypeCache typeCache) {
        this(null, pkg, types, typeCache, new TreeSet<>(FQN_COMPARATOR), new StringBuilder(), null);
    }

    public CodeBuilder(CodeBuilder parent) {
        this(parent, parent.pkg, parent.types, parent.typeCache, parent.toImport, new StringBuilder(), parent.scope);
    }

    public CodeBuilder(CodeBuilder parent, PackageElement pkg, Types types, BaseTypeCache typeCache, Collection<TypeElement> toImport, StringBuilder body, Scope scope) {
        this.parent = parent;
        this.pkg = pkg;
        this.types = types;
        this.typeCache = typeCache;
        this.toImport = toImport;
        this.importedTypeNames = toImport.stream().collect(Collectors.toMap((e) -> e.getSimpleName().toString(), Function.identity()));
        this.body = body;
        this.scope = scope;
    }

    public int position() {
        return body.length();
    }

    public CodeBuilder indent() {
        indentLevel++;
        return this;
    }

    public CodeBuilder dedent() {
        indentLevel--;
        return this;
    }

    public CodeBuilder classStart(Set<Modifier> modifiers, CharSequence name, DeclaredType superClass, List<DeclaredType> superInterfaces) {
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

    public CodeBuilder classEnd() {
        scope = scope.parent;
        return line("}");
    }

    public CodeBuilder methodStart(Set<Modifier> modifiers, CharSequence name, TypeMirror returnType,
                    List<? extends Parameter> params, List<? extends TypeMirror> exceptions,
                    List<? extends TypeParameterElement> typeParams) {
        lineStart();
        writeModifiers(modifiers).spaceIfNeeded();

        if (typeParams != null && !typeParams.isEmpty()) {
            write("<");
            for (Iterator<? extends TypeParameterElement> it = typeParams.iterator(); it.hasNext();) {
                TypeParameterElement param = it.next();
                write(param.getSimpleName().toString());
                if (needsBound(param)) {
                    write(" extends ");
                    List<? extends TypeMirror> bounds = param.getBounds();
                    for (Iterator<? extends TypeMirror> boundsIt = bounds.iterator(); it.hasNext();) {
                        TypeMirror bound = boundsIt.next();
                        write(bound);
                        if (boundsIt.hasNext()) {
                            write(", ");
                        }
                    }
                }
                if (it.hasNext()) {
                    write(", ");
                }
            }
            write("> ");
        }

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
                TypeMirror useType = param.type;
                if (param.isVarArg) {
                    if (it.hasNext()) {
                        throw new AssertionError("VarArg argument must be last one.");
                    }
                    if (useType.getKind() != TypeKind.ARRAY) {
                        throw new AssertionError("VarArg argument must be array.");
                    }
                    useType = ((ArrayType) useType).getComponentType();
                }
                write(useType);
                if (param.isVarArg) {
                    write("...");
                }
                space().write(param.name);
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

    private boolean needsBound(TypeParameterElement typeParameter) {
        var bounds = typeParameter.getBounds();
        return switch (bounds.size()) {
            case 0 -> false;
            case 1 -> {
                yield !types.isSameType(bounds.get(0), typeCache.object);
            }
            default -> true;
        };
    }

    public CodeBuilder methodStart(Set<Modifier> modifiers, CharSequence name, TypeMirror returnType,
                    List<? extends Parameter> params, List<? extends TypeMirror> exceptions) {
        return methodStart(modifiers, name, returnType, params, exceptions, List.of());
    }

    public CodeBuilder call(CharSequence methodName, CharSequence... args) {
        write(methodName).write("(");
        for (int i = 0; i < args.length; i++) {
            write(args[i]);
            if ((i + 1) < args.length) {
                write(", ");
            }
        }
        return write(")");
    }

    public CodeBuilder newArray(TypeMirror componentType, CharSequence length) {
        return write("new ").write(componentType).write("[").write(length).write("]");
    }

    public CodeBuilder newInstance(DeclaredType type, CharSequence... args) {
        return newInstance(new CodeBuilder(this).write(type).build(), Collections.emptyList(), args);
    }

    public CodeBuilder newInstance(DeclaredType type, List<TypeMirror> actualTypeParameters, CharSequence... args) {
        return newInstance(new CodeBuilder(this).write(type).build(), actualTypeParameters, args);
    }

    public CodeBuilder newInstance(CharSequence type, CharSequence... args) {
        return newInstance(type, Collections.emptyList(), args);
    }

    public CodeBuilder newInstance(CharSequence type, List<TypeMirror> actualTypeParameters, CharSequence... args) {
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

    public CodeBuilder invoke(CharSequence receiver, CharSequence methodName, CharSequence... args) {
        if (receiver != null) {
            write(receiver).write(".");
        }
        return call(methodName, args);
    }

    public CodeBuilder invokeStatic(DeclaredType receiver, CharSequence methodName, CharSequence... args) {
        return write(types.erasure(receiver)).write(".").call(methodName, args);
    }

    public CodeBuilder memberSelect(CharSequence receiver, CharSequence memberName, boolean brackets) {
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

    public CodeBuilder memberSelect(TypeMirror clazz, CharSequence memberName, boolean brackets) {
        return memberSelect(new CodeBuilder(this).write(clazz).build(), memberName, brackets);
    }

    public CodeBuilder parameterizedType(DeclaredType parameterizedType, TypeMirror... actualTypeParameters) {
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

    public CodeBuilder annotation(DeclaredType type, Object value) {
        write("@").write(type);
        if (value != null) {
            write("(").writeAnnotationAttributeValue(value).write(")");
        }
        return this;
    }

    public CodeBuilder annotationWithAttributes(DeclaredType type, Map<? extends CharSequence, Object> attributes) {
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

    public CodeBuilder classLiteral(TypeMirror type) {
        return write(types.erasure(type)).write(".class");
    }

    public CodeBuilder cast(TypeMirror type, CharSequence value) {
        return cast(type, value, false);
    }

    public CodeBuilder cast(TypeMirror type, CharSequence value, boolean brackets) {
        if (brackets) {
            write("(");
        }
        write("(").write(type).write(")").space().write(value);
        if (brackets) {
            write(")");
        }
        return this;
    }

    public CodeBuilder forLoop(List<? extends CharSequence> init, CharSequence termination, List<? extends CharSequence> increment) {
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

    public CodeBuilder forEachLoop(TypeMirror componentType, CharSequence variable, CharSequence iterable) {
        write("for (").write(componentType).space().write(variable).write(" : ").write(iterable).write(")");
        return this;
    }

    public CodeBuilder arrayElement(CharSequence array, CharSequence index) {
        return write(array).write("[").write(index).write("]");
    }

    public CodeBuilder writeAnnotationAttributeValue(Object value) {
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

    public CodeBuilder writeDefaultValue(TypeMirror type) {
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

    public CodeBuilder writeModifiers(Set<Modifier> modifiers) {
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

    public CodeBuilder lineStart(CharSequence text) {
        return write(new String(new char[indentLevel * INDENT_SIZE]).replace('\0', ' ')).write(text);
    }

    public CodeBuilder emptyLine() {
        return lineEnd("");
    }

    public CodeBuilder lineStart() {
        return lineStart("");
    }

    public CodeBuilder lineEnd(CharSequence text) {
        return write(text).write("\n");
    }

    public CodeBuilder line(CharSequence line) {
        return lineStart(line).lineEnd("");
    }

    public CodeBuilder write(CharSequence str) {
        body.append(str);
        return this;
    }

    public <T> CodeBuilder writeJoined(Iterable<T> values, String separator, BiConsumer<CodeBuilder, T> consumer) {
        Iterator<T> it = values.iterator();
        while (it.hasNext()) {
            consumer.accept(this, it.next());
            if (it.hasNext()) {
                write(separator);
            }
        }
        return this;
    }

    public <T> CodeBuilder writeCommaList(Iterable<T> values, BiConsumer<CodeBuilder, T> consumer) {
        return writeJoined(values, ", ", consumer);
    }

    public CodeBuilder write(TypeElement te) {
        Element teEnclosing = te.getEnclosingElement();
        String simpleName = te.getSimpleName().toString();

        boolean qualifiedName = false;
        if (!teEnclosing.equals(pkg) && !isJavaLang(teEnclosing) && !isElementVisible(te)) {
            TypeElement element = importedTypeNames.get(simpleName);

            if (element == null || element.getQualifiedName().equals(te.getQualifiedName())) {
                toImport.add(te);
                importedTypeNames.put(simpleName, te);
                qualifiedName = false;
            } else {
                qualifiedName = true;
            }
        }
        if (qualifiedName) {
            return write(te.getQualifiedName().toString());
        } else {
            return write(simpleName);
        }
    }

    private boolean isElementVisible(TypeElement te) {
        return scope != null && scope.isElementVisible(te, types);
    }

    private static boolean isJavaLang(Element element) {
        return element.getKind() == ElementKind.PACKAGE && ((PackageElement) element).getQualifiedName().contentEquals("java.lang");
    }

    public CodeBuilder write(TypeMirror type) {
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
                if (typeArguments.isEmpty()) {
                    write((TypeElement) declaredType.asElement());
                } else {
                    parameterizedType(declaredType, typeArguments.toArray(new TypeMirror[0]));
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
            case TYPEVAR:
                write(((TypeVariable) type).asElement().getSimpleName());
                break;
            case WILDCARD:
                WildcardType wildcardType = (WildcardType) type;
                TypeMirror upperBound = wildcardType.getExtendsBound();
                TypeMirror lowerBound = wildcardType.getSuperBound();
                assert upperBound == null || lowerBound == null;
                write("?");
                if (upperBound != null) {
                    write(" extends ").write(upperBound);
                } else if (lowerBound != null) {
                    write(" super ").write(lowerBound);
                }
                break;
        }
        return this;
    }

    public CodeBuilder space() {
        return write(" ");
    }

    public CodeBuilder spaceIfNeeded() {
        if (body.length() > 0 && !Character.isSpaceChar(body.charAt(body.length() - 1))) {
            write(" ");
        }
        return this;
    }

    public String build() {
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

    public String buildBody() {
        return body.toString();
    }

    public static Parameter newParameter(TypeMirror type, CharSequence name, CharSequence... annotations) {
        return newParameter(type, name, false, annotations);
    }

    public static Parameter newParameter(TypeMirror type, CharSequence name, boolean isVarArg, CharSequence... annotations) {
        return new Parameter(type, name, isVarArg, annotations);
    }

    public static List<Parameter> newParameters(List<? extends VariableElement> params,
                    List<? extends TypeMirror> parameterTypes, boolean isVarArg) {
        if (params.size() != parameterTypes.size()) {
            throw new IllegalArgumentException(String.format("params.size(%d) != parameterTypes.size(%d)",
                            params.size(), parameterTypes.size()));
        }
        List<Parameter> result = new ArrayList<>();
        int size = params.size();
        for (int i = 0; i < size; i++) {
            result.add(newParameter(parameterTypes.get(i), params.get(i).getSimpleName(), isVarArg && (i + 1 == size)));
        }
        return result;
    }

    public static final class Parameter {

        public final TypeMirror type;
        public final CharSequence name;
        public final boolean isVarArg;
        public final CharSequence[] annotations;

        private Parameter(TypeMirror type, CharSequence name, boolean isVarArg, CharSequence[] annotations) {
            this.type = type;
            this.name = name;
            this.isVarArg = isVarArg;
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
