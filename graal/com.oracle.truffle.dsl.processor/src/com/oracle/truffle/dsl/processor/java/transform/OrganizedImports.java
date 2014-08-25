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
package com.oracle.truffle.dsl.processor.java.transform;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;

public final class OrganizedImports {

    private final Map<String, String> classImportUsage = new HashMap<>();
    private final Map<String, String> staticImportUsage = new HashMap<>();
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

    public String createTypeReference(Element enclosedElement, TypeMirror type) {
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
                return createDeclaredTypeName(enclosedElement, (DeclaredType) type);
            case ARRAY:
                return createTypeReference(enclosedElement, ((ArrayType) type).getComponentType()) + "[]";
            case WILDCARD:
                return createWildcardName(enclosedElement, (WildcardType) type);
            case TYPEVAR:
                return "?";
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
        String qualifiedName = staticImportUsage.get(name);
        if (qualifiedName == null) {
            // ambiguous import
            return createTypeReference(enclosedElement, type) + "." + name;
        } else {
            // import declared and not ambiguous
            return name;
        }
    }

    private String createWildcardName(Element enclosedElement, WildcardType type) {
        StringBuilder b = new StringBuilder();
        if (type.getExtendsBound() != null) {
            b.append("? extends ").append(createTypeReference(enclosedElement, type.getExtendsBound()));
        } else if (type.getSuperBound() != null) {
            b.append("? super ").append(createTypeReference(enclosedElement, type.getExtendsBound()));
        } else {
            b.append("?");
        }
        return b.toString();
    }

    private String createDeclaredTypeName(Element enclosedElement, DeclaredType type) {
        String name = ElementUtils.fixECJBinaryNameIssue(type.asElement().getSimpleName().toString());
        if (classImportUsage.containsKey(name)) {
            String qualifiedImport = classImportUsage.get(name);
            String qualifiedName = ElementUtils.getEnclosedQualifiedName(type);

            if (!qualifiedName.equals(qualifiedImport)) {
                name = qualifiedName;
            }
        }

        List<? extends TypeMirror> genericTypes = type.getTypeArguments();
        if (genericTypes.size() == 0) {
            return name;
        }

        StringBuilder b = new StringBuilder(name);
        b.append("<");
        for (int i = 0; i < genericTypes.size(); i++) {
            TypeMirror genericType = i < genericTypes.size() ? genericTypes.get(i) : null;
            if (genericType != null) {
                b.append(createTypeReference(enclosedElement, genericType));
            } else {
                b.append("?");
            }

            if (i < genericTypes.size() - 1) {
                b.append(", ");
            }
        }
        b.append(">");
        return b.toString();
    }

    public Set<CodeImport> generateImports() {
        Set<CodeImport> imports = new HashSet<>();

        imports.addAll(generateImports(classImportUsage));
        imports.addAll(generateStaticImports(staticImportUsage));

        return imports;
    }

    private boolean needsImport(Element enclosed, TypeMirror importType) {
        String importPackagName = getPackageName(importType);
        TypeElement enclosedElement = findNearestEnclosingType(enclosed);
        if (importPackagName == null) {
            return false;
        } else if (importPackagName.equals("java.lang")) {
            return false;
        } else if (importPackagName.equals(getPackageName(topLevelClass)) && ElementUtils.isTopLevelClass(importType)) {
            return false; // same package name -> no import
        }

        String enclosedElementId = ElementUtils.getUniqueIdentifier(enclosedElement.asType());
        Set<String> autoImportedTypes = autoImportCache.get(enclosedElementId);
        if (autoImportedTypes == null) {
            List<Element> elements = ElementUtils.getElementHierarchy(enclosedElement);
            autoImportedTypes = new HashSet<>();
            for (Element element : elements) {
                if (element.getKind().isClass()) {
                    collectSuperTypeImports((TypeElement) element, autoImportedTypes);
                    collectInnerTypeImports((TypeElement) element, autoImportedTypes);
                }
            }
            autoImportCache.put(enclosedElementId, autoImportedTypes);
        }

        String qualifiedName = getQualifiedName(importType);
        if (autoImportedTypes.contains(qualifiedName)) {
            return false;
        }

        return true;
    }

    private static Set<CodeImport> generateImports(Map<String, String> symbols) {
        TreeSet<CodeImport> importObjects = new TreeSet<>();
        for (String symbol : symbols.keySet()) {
            String packageName = symbols.get(symbol);
            if (packageName != null) {
                importObjects.add(new CodeImport(packageName, symbol, false));
            }
        }
        return importObjects;
    }

    private static void collectInnerTypeImports(TypeElement e, Set<String> autoImportedTypes) {
        autoImportedTypes.add(getQualifiedName(e));
        for (TypeElement innerClass : ElementFilter.typesIn(e.getEnclosedElements())) {
            collectInnerTypeImports(innerClass, autoImportedTypes);
        }
    }

    private static void collectSuperTypeImports(TypeElement e, Set<String> autoImportedTypes) {
        List<TypeElement> superTypes = getSuperTypes(e);
        for (TypeElement superType : superTypes) {
            List<TypeElement> declaredTypes = getDeclaredTypes(superType);
            for (TypeElement declaredType : declaredTypes) {
                autoImportedTypes.add(getQualifiedName(declaredType));
            }
        }
    }

    private Set<CodeImport> generateStaticImports(Map<String, String> toGenerate) {
        Set<String> autoImportedStaticTypes = new HashSet<>();

        // if type is declared inside a super type of this class -> no import
        autoImportedStaticTypes.add(getQualifiedName(topLevelClass));
        autoImportedStaticTypes.addAll(getQualifiedSuperTypeNames(topLevelClass));

        TreeSet<CodeImport> importObjects = new TreeSet<>();
        for (String symbol : toGenerate.keySet()) {
            String qualifiedName = toGenerate.get(symbol);
            if (qualifiedName == null) {
                // ambiguous
                continue;
            }
            // not not import
            if (autoImportedStaticTypes.contains(qualifiedName)) {
                continue;
            }

            importObjects.add(new CodeImport(qualifiedName, symbol, true));
        }

        return importObjects;
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
            visitAnnotations(e, e.getAnnotationMirrors());

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

        private class AnnotationValueReferenceVisitor extends AbstractAnnotationValueVisitor7<Void, Void> {

            private final Element enclosingElement;

            public AnnotationValueReferenceVisitor(Element enclosedElement) {
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
            registerSymbol(staticImportUsage, ElementUtils.getQualifiedName(type), elementName);
        }

        @Override
        public void visitStaticMethodReference(Element enclosedType, TypeMirror type, String elementName) {
            registerSymbol(staticImportUsage, ElementUtils.getQualifiedName(type), elementName);
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
                        if (needsImport(enclosedType, type)) {
                            DeclaredType declard = (DeclaredType) type;
                            registerSymbol(classImportUsage, ElementUtils.getEnclosedQualifiedName(declard), ElementUtils.getDeclaredName(declard, false));
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
