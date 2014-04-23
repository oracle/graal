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
package com.oracle.truffle.dsl.processor.ast;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.dsl.processor.ast.CodeTypeMirror.DeclaredCodeTypeMirror;

public class CodeTypeElement extends CodeElement<Element> implements TypeElement {

    private final List<? extends CodeImport> imports = parentableList(this, new ArrayList<CodeImport>());

    private final PackageElement packageElement;

    private final Name simpleName;
    private final Name packageName;
    private Name qualifiedName;

    private final List<TypeMirror> implementsInterfaces = new ArrayList<>();
    private final ElementKind kind;
    private TypeMirror superClass;

    private final DeclaredCodeTypeMirror mirror = new DeclaredCodeTypeMirror(this);

    public CodeTypeElement(Set<Modifier> modifiers, ElementKind kind, PackageElement packageElement, String simpleName) {
        super(modifiers);
        this.kind = kind;
        this.packageElement = packageElement;
        this.simpleName = CodeNames.of(simpleName);
        if (this.packageElement != null) {
            this.packageName = packageElement.getQualifiedName();
        } else {
            this.packageName = CodeNames.of("default");
        }
        this.qualifiedName = createQualifiedName();
    }

    @Override
    public TypeMirror asType() {
        return mirror;
    }

    @Override
    public ElementKind getKind() {
        return kind;
    }

    public boolean containsField(String name) {
        for (VariableElement field : getFields()) {
            if (field.getSimpleName().toString().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public NestingKind getNestingKind() {
        return isTopLevelClass() ? NestingKind.TOP_LEVEL : NestingKind.LOCAL;
    }

    @Override
    public Element getEnclosingElement() {
        if (isTopLevelClass()) {
            return packageElement;
        } else {
            return super.getEnclosingElement();
        }
    }

    @Override
    public TypeMirror getSuperclass() {
        return superClass;
    }

    @Override
    public List<TypeMirror> getInterfaces() {
        return implementsInterfaces;
    }

    @Override
    public List<? extends TypeParameterElement> getTypeParameters() {
        return Collections.emptyList();
    }

    public boolean isTopLevelClass() {
        return super.getEnclosingElement() instanceof CodeCompilationUnit;
    }

    public CodeVariableElement getField(String name) {
        for (VariableElement field : ElementFilter.fieldsIn(getEnclosedElements())) {
            if (field.getSimpleName().toString().equals(name)) {
                return (CodeVariableElement) field;
            }
        }
        return null;
    }

    private Name createQualifiedName() {
        TypeElement enclosingType = getEnclosingClass();
        if (enclosingType == null) {
            return CodeNames.of(packageName + "." + simpleName);
        } else {
            return CodeNames.of(enclosingType.getQualifiedName() + "." + simpleName);
        }
    }

    @Override
    void setEnclosingElement(Element element) {
        super.setEnclosingElement(element);

        // update qualified name on container change
        this.qualifiedName = createQualifiedName();
    }

    public Name getPackageName() {
        return packageName;
    }

    @Override
    public Name getQualifiedName() {
        return qualifiedName;
    }

    @Override
    public Name getSimpleName() {
        return simpleName;
    }

    public void setSuperClass(TypeMirror superType) {
        this.superClass = superType;
    }

    public List<? extends CodeImport> getImports() {
        return imports;
    }

    public List<TypeMirror> getImplements() {
        return implementsInterfaces;
    }

    @Override
    public int hashCode() {
        return getQualifiedName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof TypeElement) {
            return getQualifiedName().equals(((TypeElement) obj).getQualifiedName());
        }
        return false;
    }

    public List<VariableElement> getFields() {
        return ElementFilter.fieldsIn(getEnclosedElements());
    }

    public ExecutableElement getMethod(String name) {
        for (Element element : getEnclosedElements()) {
            if (element.getKind() == ElementKind.METHOD && element.getSimpleName().toString().equals(name)) {
                return (ExecutableElement) element;
            }
        }
        return null;
    }

    public List<ExecutableElement> getMethods() {
        return ElementFilter.methodsIn(getEnclosedElements());
    }

    public List<TypeElement> getInnerClasses() {
        return ElementFilter.typesIn(getEnclosedElements());
    }

    @Override
    public String toString() {
        return getQualifiedName().toString();
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> v, P p) {
        return v.visitType(this, p);
    }

}
