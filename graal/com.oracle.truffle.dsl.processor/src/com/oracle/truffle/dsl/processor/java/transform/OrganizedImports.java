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

    private final Set<TypeMirror> staticImportUsage = new HashSet<>();

    private final Map<String, TypeMirror> simpleNamesUsed = new HashMap<>();

    private final Set<String> declaredStaticMethods = new HashSet<>();
    private final Set<String> declaredStaticFields = new HashSet<>();
    private final Set<String> ambiguousStaticMethods = new HashSet<>();
    private final Set<String> ambiguousStaticFields = new HashSet<>();
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

        processStaticImports(topLevelClass);
        List<TypeElement> types = ElementUtils.getSuperTypes(topLevelClass);
        for (TypeElement typeElement : types) {
            processStaticImports(typeElement);
        }

        for (TypeMirror type : staticImportUsage) {
            TypeElement element = fromTypeMirror(type);
            if (element != null) {
                // already processed by supertype
                if (types.contains(element)) {
                    continue;
                }
                processStaticImports(element);
            }
        }
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
        return createStaticReference(enclosedElement, type, fieldName, ambiguousStaticFields);
    }

    public String createStaticMethodReference(Element enclosedElement, TypeMirror type, String methodName) {
        return createStaticReference(enclosedElement, type, methodName, ambiguousStaticMethods);
    }

    private String createStaticReference(Element enclosedElement, TypeMirror type, String name, Set<String> ambiguousSymbols) {
        if (ambiguousSymbols.contains(name)) {
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
        }
        return b.toString();
    }

    private String createDeclaredTypeName(Element enclosedElement, DeclaredType type) {
        String name;
        name = ElementUtils.fixECJBinaryNameIssue(type.asElement().getSimpleName().toString());

        if (needsImport(enclosedElement, type)) {
            TypeMirror usedByType = simpleNamesUsed.get(name);
            if (usedByType == null) {
                simpleNamesUsed.put(name, type);
                usedByType = type;
            }

            if (!typeEquals(type, usedByType)) {
                name = getQualifiedName(type);
            }
        }

        if (type.getTypeArguments().size() == 0) {
            return name;
        }

        StringBuilder b = new StringBuilder(name);
        b.append("<");
        if (type.getTypeArguments().size() > 0) {
            for (int i = 0; i < type.getTypeArguments().size(); i++) {
                b.append(createTypeReference(enclosedElement, type.getTypeArguments().get(i)));
                if (i < type.getTypeArguments().size() - 1) {
                    b.append(", ");
                }
            }
        }
        b.append(">");
        return b.toString();
    }

    public Set<CodeImport> generateImports() {
        Set<CodeImport> imports = new HashSet<>();

        imports.addAll(generateImports(simpleNamesUsed.values()));
        imports.addAll(generateStaticImports(staticImportUsage));

        return imports;
    }

    boolean processStaticImports(TypeElement element) {
        Set<String> importedMethods = new HashSet<>();
        List<ExecutableElement> methods = ElementFilter.methodsIn(element.getEnclosedElements());
        for (ExecutableElement method : methods) {
            if (method.getModifiers().contains(Modifier.STATIC)) {
                importedMethods.add(method.getSimpleName().toString());
            }
        }

        boolean allMethodsAmbiguous = processStaticImportElements(importedMethods, this.ambiguousStaticMethods, this.declaredStaticMethods);

        Set<String> importedFields = new HashSet<>();
        List<VariableElement> fields = ElementFilter.fieldsIn(element.getEnclosedElements());
        for (VariableElement field : fields) {
            if (field.getModifiers().contains(Modifier.STATIC)) {
                importedFields.add(field.getSimpleName().toString());
            }
        }

        boolean allFieldsAmbiguous = processStaticImportElements(importedFields, this.ambiguousStaticFields, this.declaredStaticFields);

        return allMethodsAmbiguous && allFieldsAmbiguous;
    }

    private static boolean processStaticImportElements(Set<String> newElements, Set<String> ambiguousElements, Set<String> declaredElements) {
        boolean allAmbiguous = false;
        if (declaredElements.containsAll(newElements)) {
            // all types already declared -> we can remove the import completely -> they will all
            // get ambiguous
            allAmbiguous = true;
        }
        Set<String> newAmbiguous = new HashSet<>();
        Set<String> newDeclared = new HashSet<>();

        for (String newElement : newElements) {
            if (declaredElements.contains(newElement)) {
                newAmbiguous.add(newElement);
            } else if (ambiguousElements.contains(newElement)) {
                // nothing to do
            } else {
                newDeclared.add(newElement);
            }
        }

        ambiguousElements.addAll(newAmbiguous);
        declaredElements.addAll(newDeclared);
        return allAmbiguous;
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

        Set<String> autoImportedTypes = autoImportCache.get(enclosedElement.toString());
        if (autoImportedTypes == null) {
            List<Element> elements = ElementUtils.getElementHierarchy(enclosedElement);
            autoImportedTypes = new HashSet<>();
            for (Element element : elements) {

                if (element.getKind().isClass()) {
                    collectSuperTypeImports((TypeElement) element, autoImportedTypes);
                    collectInnerTypeImports((TypeElement) element, autoImportedTypes);
                }
            }
            autoImportCache.put(enclosedElement.toString(), autoImportedTypes);
        }

        String qualifiedName = getQualifiedName(importType);
        if (autoImportedTypes.contains(qualifiedName)) {
            return false;
        }

        return true;
    }

    private static Set<CodeImport> generateImports(Collection<TypeMirror> toGenerate) {
        TreeSet<CodeImport> importObjects = new TreeSet<>();
        for (TypeMirror importType : toGenerate) {
            importObjects.add(new CodeImport(importType, getQualifiedName(importType), false));
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

    private Set<CodeImport> generateStaticImports(Set<TypeMirror> toGenerate) {
        Set<String> autoImportedStaticTypes = new HashSet<>();

        // if type is declared inside a super type of this class -> no import
        autoImportedStaticTypes.add(getQualifiedName(topLevelClass));
        autoImportedStaticTypes.addAll(getQualifiedSuperTypeNames(topLevelClass));

        TreeSet<CodeImport> importObjects = new TreeSet<>();
        for (TypeMirror importType : toGenerate) {
            if (getPackageName(importType) == null) {
                continue; // no package name -> no import
            }

            String qualifiedName = getQualifiedName(importType);
            if (autoImportedStaticTypes.contains(qualifiedName)) {
                continue;
            }

            importObjects.add(new CodeImport(importType, qualifiedName + ".*", true));
        }

        return importObjects;
    }

    private abstract static class TypeReferenceVisitor extends CodeElementScanner<Void, Void> {

        @Override
        public void visitTree(CodeTree e, Void p) {
            if (e.getCodeKind() == CodeTreeKind.STATIC_FIELD_REFERENCE) {
                visitStaticFieldReference(e, e.getType(), e.getString());
            } else if (e.getCodeKind() == CodeTreeKind.STATIC_METHOD_REFERENCE) {
                visitStaticMethodReference(e, e.getType(), e.getString());
            } else if (e.getType() != null) {
                visitTypeReference(e, e.getType());
            }
            super.visitTree(e, p);
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
            staticImportUsage.add(type);
        }

        @Override
        public void visitStaticMethodReference(Element enclosedType, TypeMirror type, String elementName) {
            staticImportUsage.add(type);
        }

        @Override
        public void visitTypeReference(Element enclosedType, TypeMirror type) {
            if (type != null) {
                createTypeReference(enclosedType, type);
            }
        }

    }

}
