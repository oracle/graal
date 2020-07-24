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
package com.oracle.truffle.dsl.processor.java.transform;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.findNearestEnclosingType;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getDeclaredTypes;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getPackageName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getQualifiedName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSuperTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractAnnotationValueVisitor8;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeElementScanner;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeImport;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeKind;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;

public final class OrganizedImports {

    private final Map<String, String> classImportUsage = new HashMap<>();
    private final Map<String, Boolean> noImportSymbols = new HashMap<>();
    private final Map<String, Set<String>> autoImportCache = new HashMap<>();

    private final CodeTypeElement topLevelClass;

    private OrganizedImports(CodeTypeElement topLevelClass) {
        this.topLevelClass = topLevelClass;
    }

    public static OrganizedImports organize(CodeTypeElement topLevelClass) {
        OrganizedImports organized = new OrganizedImports(topLevelClass);
        organized.organizeImpl();
        return organized;
    }

    private void organizeImpl() {
        ImportTypeReferenceVisitor reference = new ImportTypeReferenceVisitor();
        topLevelClass.accept(reference, null);
    }

    public String createTypeReference(Element enclosedElement, TypeMirror type, boolean raw) {
        switch (type.getKind()) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case DOUBLE:
            case FLOAT:
            case SHORT:
            case INT:
            case LONG:
            case VOID:
                return ElementUtils.getSimpleName(type);
            case DECLARED:
                return createDeclaredTypeName(enclosedElement, (DeclaredType) type, raw);
            case ARRAY:
                return createTypeReference(enclosedElement, ((ArrayType) type).getComponentType(), raw) + "[]";
            case WILDCARD:
                return createWildcardName(enclosedElement, (WildcardType) type);
            case TYPEVAR:
                TypeVariable var = (TypeVariable) type;
                return var.asElement().getSimpleName().toString();
            default:
                throw new RuntimeException("Unknown type specified " + type.getKind() + " mirror: " + type);
        }
    }

    public String createStaticFieldReference(Element enclosedElement, TypeMirror type, String fieldName) {
        return createStaticReference(enclosedElement, type, fieldName);
    }

    public String createStaticMethodReference(Element enclosedElement, TypeMirror type, String methodName) {
        return createStaticReference(enclosedElement, type, methodName);
    }

    private String createStaticReference(Element enclosedElement, TypeMirror type, String name) {
        // ambiguous import
        return createTypeReference(enclosedElement, type, true) + "." + name;
    }

    private String createWildcardName(Element enclosedElement, WildcardType type) {
        StringBuilder b = new StringBuilder();
        if (type.getExtendsBound() != null) {
            b.append("? extends ").append(createTypeReference(enclosedElement, type.getExtendsBound(), false));
        } else if (type.getSuperBound() != null) {
            b.append("? super ").append(createTypeReference(enclosedElement, type.getExtendsBound(), false));
        } else {
            b.append("?");
        }
        return b.toString();
    }

    private String createDeclaredTypeName(Element enclosedElement, DeclaredType type, boolean raw) {
        String name = ElementUtils.fixECJBinaryNameIssue(type.asElement().getSimpleName().toString());
        if (ElementUtils.isDeprecated(type.asElement())) {
            name = ElementUtils.getQualifiedName(type);
        } else if (classImportUsage.containsKey(name)) {
            String qualifiedImport = classImportUsage.get(name);
            String qualifiedName = ElementUtils.getEnclosedQualifiedName(type);
            if (qualifiedImport == null || !qualifiedName.equals(qualifiedImport)) {
                name = ElementUtils.getQualifiedName(type);
            }
        }
        if (raw) {
            return name;
        }

        List<? extends TypeMirror> typeArguments = type.getTypeArguments();
        List<? extends TypeParameterElement> parameters = ((TypeElement) type.asElement()).getTypeParameters();
        if (parameters.isEmpty()) {
            return name;
        }

        StringBuilder b = new StringBuilder(name);
        b.append("<");
        for (int i = 0; i < parameters.size(); i++) {
            TypeMirror genericType = i < typeArguments.size() ? typeArguments.get(i) : null;
            TypeMirror parameterGenericType = parameters.get(i).asType();
            if (genericType != null && !ElementUtils.typeEquals(genericType, parameterGenericType)) {
                b.append(createTypeReference(enclosedElement, genericType, false));
            } else {
                b.append("?");
            }

            if (i < typeArguments.size() - 1) {
                b.append(", ");
            }
        }
        b.append(">");
        return b.toString();
    }

    public Set<CodeImport> generateImports() {
        Set<CodeImport> imports = new HashSet<>();

        imports.addAll(generateImports(classImportUsage));

        return imports;
    }

    private boolean needsImport(Element enclosed, TypeMirror importType) {
        String importPackagName = getPackageName(importType);
        TypeElement enclosedType = findNearestEnclosingType(enclosed).orElse(null);

        if (importPackagName == null) {
            return false;
        } else if (importPackagName.equals("java.lang")) {
            return false;
        } else if (importPackagName.equals(getPackageName(topLevelClass)) &&
                        anyEqualEnclosingTypes(enclosed, ElementUtils.castTypeElement(importType))) {
            return false; // same enclosing element -> no import
        } else if (importType instanceof GeneratedTypeMirror && ElementUtils.getPackageName(importType).isEmpty()) {
            return false;
        } else if (ElementUtils.isDeprecated(importType)) {
            return false;
        }

        String enclosedElementId = ElementUtils.getUniqueIdentifier(enclosedType.asType());
        Set<String> autoImportedTypes = autoImportCache.get(enclosedElementId);
        if (autoImportedTypes == null) {
            autoImportedTypes = new HashSet<>();
            autoImportCache.put(enclosedElementId, autoImportedTypes);

            collectImplicitImports(autoImportedTypes, enclosedElementId, enclosedType);
        }

        String qualifiedName = getQualifiedName(importType);
        if (autoImportedTypes.contains(qualifiedName)) {
            return false;
        }

        return true;
    }

    private void collectImplicitImports(Set<String> autoImportedTypes, String enclosedElementId, TypeElement enclosedType) {
        List<Element> elements = ElementUtils.getElementHierarchy(enclosedType);
        for (Element element : elements) {
            if (element.getKind().isClass() || element.getKind().isInterface()) {
                collectSuperTypeImports((TypeElement) element, autoImportedTypes);
                collectInnerTypeImports((TypeElement) element, autoImportedTypes);
            }
        }
        autoImportCache.put(enclosedElementId, autoImportedTypes);
    }

    private static boolean anyEqualEnclosingTypes(Element enclosed, Element importElement) {
        Element enclosingElement = enclosed.getEnclosingElement();
        Element importEnclosingElement = importElement.getEnclosingElement();
        if (enclosingElement == null || importEnclosingElement == null) {
            return false;
        }
        if (!enclosingElement.getKind().isClass() || !importEnclosingElement.getKind().isClass()) {
            return false;
        }
        String qualified1 = ElementUtils.getQualifiedName((TypeElement) enclosingElement);
        String qualified2 = ElementUtils.getQualifiedName((TypeElement) importEnclosingElement);
        if (qualified1.equals(qualified2)) {
            return true;
        }
        return anyEqualEnclosingTypes(enclosingElement, importElement) || anyEqualEnclosingTypes(importElement, enclosingElement);
    }

    private Set<CodeImport> generateImports(Map<String, String> symbols) {
        TreeSet<CodeImport> importObjects = new TreeSet<>();
        for (String symbol : symbols.keySet()) {
            String importQualifiedName = symbols.get(symbol);
            Boolean needsImport = this.noImportSymbols.get(symbol);
            if (importQualifiedName != null && needsImport) {
                importObjects.add(new CodeImport(importQualifiedName, symbol, false));
            }
        }
        return importObjects;
    }

    private static void collectInnerTypeImports(TypeElement e, Set<String> autoImportedTypes) {
        autoImportedTypes.add(getQualifiedName(e));
        for (TypeElement innerClass : ElementFilter.typesIn(e.getEnclosedElements())) {
            autoImportedTypes.add(getQualifiedName(innerClass));
        }
    }

    private static void collectSuperTypeImports(TypeElement e, Set<String> autoImportedTypes) {
        List<TypeElement> superTypes = getSuperTypes(e);
        for (TypeElement superType : superTypes) {
            List<TypeElement> declaredTypes = getDeclaredTypes(superType);
            for (TypeElement declaredType : declaredTypes) {
                if (!superTypes.contains(declaredType)) {
                    autoImportedTypes.add(getQualifiedName(declaredType));
                }
            }
        }
    }

    private abstract static class TypeReferenceVisitor extends CodeElementScanner<Void, Void> {

        @Override
        public void visitTree(CodeTree e, Void p, Element enclosing) {
            if (e.getCodeKind() == CodeTreeKind.STATIC_FIELD_REFERENCE) {
                visitStaticFieldReference(enclosing, e.getType(), e.getString());
            } else if (e.getCodeKind() == CodeTreeKind.STATIC_METHOD_REFERENCE) {
                visitStaticMethodReference(enclosing, e.getType(), e.getString());
            } else if (e.getType() != null) {
                visitTypeReference(enclosing, e.getType());
            }
            super.visitTree(e, p, enclosing);
        }

        @Override
        public Void visitExecutable(CodeExecutableElement e, Void p) {
            if (e.getDocTree() != null) {
                visitTree(e.getDocTree(), null, e);
            }
            visitAnnotations(e, e.getAnnotationMirrors());
            if (e.getReturnType() != null) {
                visitTypeReference(e, e.getReturnType());
            }
            for (TypeMirror type : e.getThrownTypes()) {
                visitTypeReference(e, type);
            }
            return super.visitExecutable(e, p);
        }

        @Override
        public Void visitType(CodeTypeElement e, Void p) {
            Element enclosing = e;
            if (!e.isTopLevelClass()) {
                enclosing = e.getEnclosingElement();
            }
            visitAnnotations(enclosing, e.getAnnotationMirrors());

            if (e.getDocTree() != null) {
                visitTree(e.getDocTree(), null, enclosing);
            }

            visitTypeReference(e, e.getSuperclass());
            for (TypeMirror type : e.getImplements()) {
                visitTypeReference(e, type);
            }

            return super.visitType(e, p);
        }

        private void visitAnnotations(Element enclosingElement, List<? extends AnnotationMirror> mirrors) {
            for (AnnotationMirror mirror : mirrors) {
                visitAnnotation(enclosingElement, mirror);
            }
        }

        public void visitAnnotation(Element enclosingElement, AnnotationMirror e) {
            visitTypeReference(enclosingElement, e.getAnnotationType());
            if (!e.getElementValues().isEmpty()) {
                Map<? extends ExecutableElement, ? extends AnnotationValue> values = e.getElementValues();
                Set<? extends ExecutableElement> methodsSet = values.keySet();
                List<ExecutableElement> methodsList = new ArrayList<>();
                for (ExecutableElement method : methodsSet) {
                    if (values.get(method) == null) {
                        continue;
                    }
                    methodsList.add(method);
                }

                for (int i = 0; i < methodsList.size(); i++) {
                    AnnotationValue value = values.get(methodsList.get(i));
                    visitAnnotationValue(enclosingElement, value);
                }
            }
        }

        public void visitAnnotationValue(Element enclosingElement, AnnotationValue e) {
            e.accept(new AnnotationValueReferenceVisitor(enclosingElement), null);
        }

        private class AnnotationValueReferenceVisitor extends AbstractAnnotationValueVisitor8<Void, Void> {

            private final Element enclosingElement;

            AnnotationValueReferenceVisitor(Element enclosedElement) {
                this.enclosingElement = enclosedElement;
            }

            @Override
            public Void visitBoolean(boolean b, Void p) {
                return null;
            }

            @Override
            public Void visitByte(byte b, Void p) {
                return null;
            }

            @Override
            public Void visitChar(char c, Void p) {
                return null;
            }

            @Override
            public Void visitDouble(double d, Void p) {
                return null;
            }

            @Override
            public Void visitFloat(float f, Void p) {
                return null;
            }

            @Override
            public Void visitInt(int i, Void p) {
                return null;
            }

            @Override
            public Void visitLong(long i, Void p) {
                return null;
            }

            @Override
            public Void visitShort(short s, Void p) {
                return null;
            }

            @Override
            public Void visitString(String s, Void p) {
                return null;
            }

            @Override
            public Void visitType(TypeMirror t, Void p) {
                visitTypeReference(enclosingElement, t);
                return null;
            }

            @Override
            public Void visitEnumConstant(VariableElement c, Void p) {
                visitTypeReference(enclosingElement, c.asType());
                return null;
            }

            @Override
            public Void visitAnnotation(AnnotationMirror a, Void p) {
                TypeReferenceVisitor.this.visitAnnotation(enclosingElement, a);
                return null;
            }

            @Override
            public Void visitArray(List<? extends AnnotationValue> vals, Void p) {
                for (int i = 0; i < vals.size(); i++) {
                    TypeReferenceVisitor.this.visitAnnotationValue(enclosingElement, vals.get(i));
                }
                return null;
            }
        }

        @Override
        public Void visitVariable(VariableElement f, Void p) {
            visitAnnotations(f, f.getAnnotationMirrors());
            visitTypeReference(f, f.asType());
            return super.visitVariable(f, p);
        }

        @Override
        public void visitImport(CodeImport e, Void p) {
        }

        public abstract void visitTypeReference(Element enclosedType, TypeMirror type);

        public abstract void visitStaticMethodReference(Element enclosedType, TypeMirror type, String elementName);

        public abstract void visitStaticFieldReference(Element enclosedType, TypeMirror type, String elementName);

    }

    private class ImportTypeReferenceVisitor extends TypeReferenceVisitor {

        @Override
        public void visitStaticFieldReference(Element enclosedType, TypeMirror type, String elementName) {
            visitTypeReference(enclosedType, type);
        }

        @Override
        public void visitStaticMethodReference(Element enclosedType, TypeMirror type, String elementName) {
            visitTypeReference(enclosedType, type);
        }

        @Override
        public void visitTypeReference(Element enclosedType, TypeMirror type) {
            if (type != null) {
                switch (type.getKind()) {
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                    case DOUBLE:
                    case FLOAT:
                    case SHORT:
                    case INT:
                    case LONG:
                    case VOID:
                        return;
                    case DECLARED:
                        DeclaredType declared = (DeclaredType) type;
                        String declaredName = ElementUtils.getDeclaredName(declared, false);
                        String enclosedQualifiedName = ElementUtils.getEnclosedQualifiedName(declared);
                        registerSymbol(classImportUsage, enclosedQualifiedName, declaredName);
                        if (!needsImport(enclosedType, type)) {
                            noImportSymbols.putIfAbsent(declaredName, Boolean.FALSE);
                        } else {
                            noImportSymbols.put(declaredName, Boolean.TRUE);
                        }

                        for (TypeMirror argument : ((DeclaredType) type).getTypeArguments()) {
                            visitTypeReference(enclosedType, argument);
                        }
                        return;
                    case ARRAY:
                        visitTypeReference(enclosedType, ((ArrayType) type).getComponentType());
                        return;
                    case WILDCARD:
                        WildcardType wildcard = (WildcardType) type;
                        if (wildcard.getExtendsBound() != null) {
                            visitTypeReference(enclosedType, wildcard.getExtendsBound());
                        } else if (wildcard.getSuperBound() != null) {
                            visitTypeReference(enclosedType, wildcard.getSuperBound());
                        }
                        return;
                    case TYPEVAR:
                        return;
                    default:
                        throw new RuntimeException("Unknown type specified " + type.getKind() + " mirror: " + type);
                }

            }
        }

        private void registerSymbol(Map<String, String> symbolUsage, String elementQualifiedName, String elementName) {
            if (symbolUsage.containsKey(elementName)) {
                String otherQualifiedName = symbolUsage.get(elementName);
                if (otherQualifiedName == null) {
                    // already registered ambiguous
                    return;
                }
                if (!otherQualifiedName.equals(elementQualifiedName)) {
                    symbolUsage.put(elementName, null);
                }
            } else {
                symbolUsage.put(elementName, elementQualifiedName);
            }
        }

    }

}
