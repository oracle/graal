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

import javax.lang.model.element.*;
import javax.lang.model.type.*;

public class CodeTree extends CodeElement<CodeTree> {

    private final CodeTreeKind kind;

    private TypeMirror type;
    private final String string;

    CodeTree(CodeTreeKind kind, TypeMirror type, String string) {
        this.kind = kind;
        this.type = type;
        this.string = string;
    }

    public TypeMirror getType() {
        return type;
    }

    public CodeTreeKind getCodeKind() {
        return kind;
    }

    public String getString() {
        return string;
    }

    public <P> void acceptCodeElementScanner(CodeElementScanner<?, P> s, P p) {
        s.visitTree(this, p);
    }

    public void setType(TypeMirror type) {
        this.type = type;
    }

    @Override
    public TypeMirror asType() {
        return type;
    }

    @Override
    public ElementKind getKind() {
        return ElementKind.OTHER;
    }

    @Override
    public Name getSimpleName() {
        return CodeNames.of(getString());
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> v, P p) {
        if (v instanceof CodeElementScanner<?, ?>) {
            acceptCodeElementScanner((CodeElementScanner<?, P>) v, p);
            return null;
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
