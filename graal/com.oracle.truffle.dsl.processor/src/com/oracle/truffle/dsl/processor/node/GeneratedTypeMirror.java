package com.oracle.truffle.dsl.processor.node;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.ast.*;
import com.oracle.truffle.dsl.processor.ast.CodeTypeMirror.*;

public final class GeneratedTypeMirror extends DeclaredCodeTypeMirror {

    public GeneratedTypeMirror(String packageName, String name) {
        super(new GeneratedTypeElement(Collections.<Modifier> emptySet(), ElementKind.CLASS, new GeneratedPackageElement(packageName), name));
    }

    public static final class GeneratedPackageElement extends CodeElement<Element> implements PackageElement {

        private final Name qualifiedName;
        private final Name simpleName;

        public GeneratedPackageElement(String qualifiedName) {
            this.qualifiedName = CodeNames.of(qualifiedName);
            int lastIndex = qualifiedName.lastIndexOf('.');
            if (lastIndex == -1) {
                simpleName = CodeNames.of("");
            } else {
                simpleName = CodeNames.of(qualifiedName.substring(lastIndex, qualifiedName.length()));
            }
        }

        public TypeMirror asType() {
            throw new UnsupportedOperationException();
        }

        public ElementKind getKind() {
            return ElementKind.PACKAGE;
        }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitPackage(this, p);
        }

        public Name getQualifiedName() {
            return qualifiedName;
        }

        public Name getSimpleName() {
            return simpleName;
        }

        public boolean isUnnamed() {
            return simpleName.toString().equals("");
        }

        @Override
        public int hashCode() {
            return qualifiedName.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PackageElement) {
                return qualifiedName.equals(((PackageElement) obj).getQualifiedName());
            }
            return super.equals(obj);
        }
    }

    public static final class GeneratedTypeElement extends CodeTypeElement {

        public GeneratedTypeElement(Set<Modifier> modifiers, ElementKind kind, PackageElement packageElement, String simpleName) {
            super(modifiers, kind, packageElement, simpleName);
            setEnclosingElement(packageElement);
        }

    }

}