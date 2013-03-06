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
package com.oracle.truffle.codegen.processor.codewriter;

import static com.oracle.truffle.codegen.processor.Utils.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.ast.*;

public final class OrganizedImports {

    private final Map<TypeMirror, Integer> importUsage = new HashMap<>();
    private final Map<TypeMirror, Integer> staticImportUsage = new HashMap<>();

    private final Map<String, TypeMirror> simpleNamesUsed = new HashMap<>();

    private final Set<String> declaredStaticMethods = new HashSet<>();
    private final Set<String> declaredStaticFields = new HashSet<>();
    private final Set<String> ambiguousStaticMethods = new HashSet<>();
    private final Set<String> ambiguousStaticFields = new HashSet<>();

    private final CodeTypeElement topLevelClass;

    private OrganizedImports(CodeTypeElement topLevelClass) {
        this.topLevelClass = topLevelClass;
    }

    public static OrganizedImports organize(CodeTypeElement topLevelClass) {
        OrganizedImports organized = new OrganizedImports(topLevelClass);

        OrganizedImports.ReferenceCollector reference = new ReferenceCollector();
        topLevelClass.accept(reference, null);

        OrganizedImports.ImportResolver resolver = new ImportResolver(reference, organized);
        topLevelClass.accept(resolver, null);
        return organized;
    }

    public String useImport(TypeMirror type) {
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
                return Utils.getSimpleName(type);
            case DECLARED:
                return createDeclaredTypeName((DeclaredType) type);
            case ARRAY:
                return useImport(((ArrayType) type).getComponentType()) + "[]";
            case WILDCARD:
                return createWildcardName((WildcardType) type);
            case TYPEVAR:
                return "?";
            default:
                throw new RuntimeException("Unknown type specified " + type.getKind() + " mirror: " + type);
        }
    }

    private String createWildcardName(WildcardType type) {
        StringBuilder b = new StringBuilder();
        if (type.getExtendsBound() != null) {
            b.append("? extends ").append(useImport(type.getExtendsBound()));
        } else if (type.getSuperBound() != null) {
            b.append("? super ").append(useImport(type.getExtendsBound()));
        }
        return b.toString();
    }

    private String createDeclaredTypeName(DeclaredType type) {
        String name = type.asElement().getSimpleName().toString();

        TypeMirror usedByType = simpleNamesUsed.get(name);
        if (usedByType == null) {
            simpleNamesUsed.put(name, type);
            usedByType = type;
        }

        if (typeEquals(type, usedByType)) {
            addUsage(type, importUsage);
        } else {
            name = getQualifiedName(type);
        }

        if (type.getTypeArguments().size() == 0) {
            return name;
        }

        StringBuilder b = new StringBuilder(name);
        b.append("<");
        if (type.getTypeArguments().size() > 0) {
            for (int i = 0; i < type.getTypeArguments().size(); i++) {
                b.append(useImport(type.getTypeArguments().get(i)));
                if (i < type.getTypeArguments().size() - 1) {
                    b.append(", ");
                }
            }
        }
        b.append(">");
        return b.toString();
    }

    public String useStaticFieldImport(TypeMirror type, String fieldName) {
        return useStaticImport(type, fieldName, ambiguousStaticFields, declaredStaticFields);
    }

    public String useStaticMethodImport(TypeMirror type, String methodName) {
        return useStaticImport(type, methodName, ambiguousStaticMethods, declaredStaticMethods);
    }

    private String useStaticImport(TypeMirror type, String name, Set<String> ambiguousSymbols, Set<String> declaredSymbols) {
        if (ambiguousSymbols.contains(name)) {
            // ambiguous import
            return useImport(type) + "." + name;
        } else if (!declaredSymbols.contains(name)) {
            // not imported at all
            return useImport(type) + "." + name;
        } else {
            // import declared and not ambiguous
            addUsage(type, staticImportUsage);
            return name;
        }
    }

    public Set<CodeImport> generateImports() {
        Set<CodeImport> imports = new HashSet<>();

        imports.addAll(generateImports(topLevelClass, importUsage.keySet()));
        imports.addAll(generateStaticImports(topLevelClass, staticImportUsage.keySet()));

        return imports;
    }

    void clearStaticImports() {
        declaredStaticFields.clear();
        declaredStaticMethods.clear();
        ambiguousStaticFields.clear();
        ambiguousStaticMethods.clear();
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

    private static Set<CodeImport> generateImports(CodeTypeElement e, Set<TypeMirror> toGenerate) {
        Set<String> autoImportedTypes = new HashSet<>();

        // if type is declared inside a super type of this class -> no import
        collectSuperTypeImports(e, autoImportedTypes);
        collectInnerTypeImports(e, autoImportedTypes);

        TreeSet<CodeImport> importObjects = new TreeSet<>();
        for (TypeMirror importType : toGenerate) {
            String importTypePackageName = getPackageName(importType);
            if (importTypePackageName == null) {
                continue; // no package name -> no import
            }

            if (importTypePackageName.equals("java.lang")) {
                continue; // java.lang is automatically imported
            }

            if (importTypePackageName.equals(getPackageName(e)) && Utils.isTopLevelClass(importType)) {
                continue; // same package name -> no import
            }

            String qualifiedName = getQualifiedName(importType);

            if (autoImportedTypes.contains(qualifiedName)) {
                continue;
            }

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

    private static Set<CodeImport> generateStaticImports(CodeTypeElement e, Set<TypeMirror> toGenerate) {
        Set<String> autoImportedStaticTypes = new HashSet<>();

        // if type is declared inside a super type of this class -> no import
        autoImportedStaticTypes.add(getQualifiedName(e));
        autoImportedStaticTypes.addAll(getQualifiedSuperTypeNames(e));

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

    private static void addUsage(TypeMirror type, Map<TypeMirror, Integer> usageMap) {
        if (type != null) {
            Integer value = usageMap.get(type);
            if (value == null) {
                usageMap.put(type, 1);
            } else {
                usageMap.put(type, value + 1);
            }
        }
    }

    private static class ReferenceCollector extends CodeElementScanner<Void, Void> {

        final Map<TypeMirror, Integer> typeReferences = new HashMap<>();
        final Map<TypeMirror, Integer> staticTypeReferences = new HashMap<>();

        @Override
        public void visitTree(CodeTree e, Void p) {
            if (e.getCodeKind() == CodeTreeKind.STATIC_FIELD_REFERENCE) {
                addStaticImport(e.getType());
            } else if (e.getCodeKind() == CodeTreeKind.STATIC_METHOD_REFERENCE) {
                addStaticImport(e.getType());
            } else {
                addImport(e.getType());
            }
            super.visitTree(e, p);
        }

        @Override
        public Void visitExecutable(CodeExecutableElement e, Void p) {
            visitAnnotations(e.getAnnotationMirrors());
            if (e.getReturnType() != null) {
                addImport(e.getReturnType());
            }
            for (TypeMirror type : e.getThrownTypes()) {
                addImport(type);
            }
            return super.visitExecutable(e, p);
        }

        @Override
        public Void visitType(CodeTypeElement e, Void p) {
            visitAnnotations(e.getAnnotationMirrors());

            addImport(e.getSuperclass());
            for (TypeMirror type : e.getImplements()) {
                addImport(type);
            }

            return super.visitType(e, p);
        }

        @Override
        public Void visitVariable(VariableElement f, Void p) {
            visitAnnotations(f.getAnnotationMirrors());
            addImport(f.asType());
            return super.visitVariable(f, p);
        }

        private void visitAnnotations(List<? extends AnnotationMirror> mirrors) {
            for (AnnotationMirror mirror : mirrors) {
                visitAnnotation(mirror);
            }
        }

        public void visitAnnotation(AnnotationMirror e) {
            addImport(e.getAnnotationType());
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
                    visitAnnotationValue(value);
                }
            }
        }

        public void visitAnnotationValue(AnnotationValue e) {
            e.accept(new AnnotationValueReferenceVisitor(), null);
        }

        private class AnnotationValueReferenceVisitor extends AbstractAnnotationValueVisitor7<Void, Void> {

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
                addImport(t);
                return null;
            }

            @Override
            public Void visitEnumConstant(VariableElement c, Void p) {
                addImport(c.asType());
                return null;
            }

            @Override
            public Void visitAnnotation(AnnotationMirror a, Void p) {
                ReferenceCollector.this.visitAnnotation(a);
                return null;
            }

            @Override
            public Void visitArray(List<? extends AnnotationValue> vals, Void p) {
                for (int i = 0; i < vals.size(); i++) {
                    ReferenceCollector.this.visitAnnotationValue(vals.get(i));
                }
                return null;
            }
        }

        @Override
        public void visitImport(CodeImport e, Void p) {
        }

        private void addStaticImport(TypeMirror type) {
            addUsage(type, staticTypeReferences);
        }

        private void addImport(TypeMirror type) {
            addUsage(type, typeReferences);
        }

    }

    private static class ImportResolver extends CodeElementScanner<Void, Void> {

        private final ReferenceCollector collector;
        private final OrganizedImports organizedImports;

        public ImportResolver(OrganizedImports.ReferenceCollector collector, OrganizedImports organizedImports) {
            this.collector = collector;
            this.organizedImports = organizedImports;
        }

        @Override
        public Void visitType(CodeTypeElement e, Void p) {
            if (e.isTopLevelClass()) {
                organizedImports.clearStaticImports();

                organizedImports.processStaticImports(e);
                List<TypeElement> types = Utils.getSuperTypes(e);
                for (TypeElement typeElement : types) {
                    organizedImports.processStaticImports(typeElement);
                }

                for (TypeMirror type : collector.staticTypeReferences.keySet()) {
                    TypeElement element = fromTypeMirror(type);
                    if (element != null) {
                        // already processed by supertype
                        if (types.contains(element)) {
                            continue;
                        }
                        organizedImports.processStaticImports(element);
                    }
                }

                for (TypeMirror imp : collector.typeReferences.keySet()) {
                    organizedImports.useImport(imp);
                }
            }
            return super.visitType(e, p);
        }

        @Override
        public void visitTree(CodeTree e, Void p) {
            if (e.getCodeKind() == CodeTreeKind.TYPE) {
                organizedImports.useImport(e.getType());
            } else if (e.getCodeKind() == CodeTreeKind.STATIC_FIELD_REFERENCE) {
                organizedImports.useStaticFieldImport(e.getType(), e.getString());
            } else if (e.getCodeKind() == CodeTreeKind.STATIC_METHOD_REFERENCE) {
                organizedImports.useStaticMethodImport(e.getType(), e.getString());
            }
            super.visitTree(e, p);
        }
    }

}
