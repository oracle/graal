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
package com.oracle.truffle.dsl.processor.java.model;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

public final class GeneratedPackageElement extends CodeElement<Element> implements PackageElement {

    private final Name qualifiedName;
    private final Name simpleName;

    public GeneratedPackageElement(String qualifiedName) {
        super(Collections.<Modifier> emptySet());
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