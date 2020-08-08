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
package com.oracle.truffle.dsl.processor.java.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;

public class CodeTypeElement extends CodeElement<Element> implements TypeElement {

    private final List<? extends CodeImport> imports = parentableList(this, new ArrayList<CodeImport>());

    private final PackageElement packageElement;

    private Name simpleName;
    private final Name packageName;
    private Name qualifiedName;

    private final List<TypeMirror> implementsInterfaces = new ArrayList<>();
    private final List<TypeParameterElement> typeParameters = parentableList(this, new ArrayList<>());
    private ElementKind kind;
    private TypeMirror superClass;
    private CodeTree docTree;

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

    public CodeTreeBuilder createDocBuilder() {
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        builder.setEnclosingElement(this);
        this.docTree = builder.getTree();
        return builder;
    }

    public CodeTree getDocTree() {
        return docTree;
    }

    public void setDocTree(CodeTree docTree) {
        this.docTree = docTree;
    }

    public void setSimpleName(Name simpleName) {
        this.simpleName = simpleName;
        this.qualifiedName = createQualifiedName();
    }

    @Override
    public TypeMirror asType() {
        return mirror;
    }

    public void setKind(ElementKind kind) {
        this.kind = kind;
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
    public List<TypeParameterElement> getTypeParameters() {
        return typeParameters;
    }

    public boolean isTopLevelClass() {
        return super.getEnclosingElement() instanceof CodeCompilationUnit || super.getEnclosingElement() == null;
    }

    private Name createQualifiedName() {
        TypeElement enclosingType = getEnclosingClass();

        String name;
        if (enclosingType == null) {
            if (packageName == null || packageName.length() == 0) {
                name = simpleName.toString();
            } else {
                name = packageName + "." + simpleName;
            }
        } else {
            name = enclosingType.getQualifiedName() + "." + simpleName;
        }
        return CodeNames.of(name);
    }

    @Override
    public void setEnclosingElement(Element element) {
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

    public static CodeTypeElement cloneShallow(TypeElement typeElement) {
        CodeTypeElement copy = new CodeTypeElement(new HashSet<>(typeElement.getModifiers()), typeElement.getKind(), ElementUtils.findPackageElement(typeElement),
                        typeElement.getSimpleName().toString());
        copy.setEnclosingElement(typeElement.getEnclosingElement());
        copy.setSuperClass(typeElement.getSuperclass());
        copy.getTypeParameters().addAll(typeElement.getTypeParameters());
        copy.getImplements().addAll(typeElement.getInterfaces());
        copy.getAnnotationMirrors().addAll(typeElement.getAnnotationMirrors());
        copy.getEnclosedElements().addAll(CompilerFactory.getCompiler(typeElement).getEnclosedElementsInDeclarationOrder(typeElement));
        return copy;
    }

}
