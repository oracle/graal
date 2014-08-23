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

import javax.lang.model.type.*;

public class CodeTree {

    private final CodeTreeKind kind;

    private CodeTree parent;

    private TypeMirror type;
    private final String string;

    private List<CodeTree> children;

    CodeTree(CodeTree parent, CodeTreeKind kind, TypeMirror type, String string) {
        this.parent = parent;
        this.kind = kind;
        this.type = type;
        this.string = string;
    }

    public void setParent(CodeTree parent) {
        this.parent = parent;
    }

    public CodeTree getParent() {
        return parent;
    }

    public TypeMirror getType() {
        return type;
    }

    public void add(CodeTree element) {
        if (children == null) {
            children = new ArrayList<>();
        }
        element.setParent(this);
        children.add(element);
    }

    public final List<CodeTree> getEnclosedElements() {
        return children;
    }

    public final CodeTreeKind getCodeKind() {
        return kind;
    }

    public String getString() {
        return string;
    }

    public void setType(TypeMirror type) {
        this.type = type;
    }

}
