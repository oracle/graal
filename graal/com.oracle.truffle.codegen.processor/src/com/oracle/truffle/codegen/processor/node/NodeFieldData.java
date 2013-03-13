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
package com.oracle.truffle.codegen.processor.node;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

public class NodeFieldData {

    public enum FieldKind {
        CHILD, CHILDREN
    }

    public enum ExecutionKind {
        DEFAULT, IGNORE, SHORT_CIRCUIT
    }

    private final VariableElement fieldElement;
    private final Element accessElement;
    private final AnnotationMirror childAnnotationMirror;

    private final FieldKind fieldKind;
    private final ExecutionKind executionKind;
    private NodeData nodeData;

    public NodeFieldData(NodeData typeNodeData, VariableElement fieldElement, Element accessElement, AnnotationMirror childAnnotationMirror, FieldKind fieldKind, ExecutionKind executionKind) {
        this.fieldElement = fieldElement;
        this.accessElement = accessElement;
        this.childAnnotationMirror = childAnnotationMirror;
        this.nodeData = typeNodeData;
        this.fieldKind = fieldKind;
        this.executionKind = executionKind;
    }

    NodeFieldData(NodeFieldData field) {
        this.fieldElement = field.fieldElement;
        this.accessElement = field.accessElement;
        this.childAnnotationMirror = field.childAnnotationMirror;
        this.fieldKind = field.fieldKind;
        this.executionKind = field.executionKind;
        this.nodeData = field.nodeData;
    }

    public boolean isShortCircuit() {
        return executionKind == ExecutionKind.SHORT_CIRCUIT;
    }

    void setNode(NodeData nodeData) {
        this.nodeData = nodeData;
    }

    public VariableElement getFieldElement() {
        return fieldElement;
    }

    public Element getAccessElement() {
        return accessElement;
    }

    public AnnotationMirror getChildAnnotationMirror() {
        return childAnnotationMirror;
    }

    public TypeMirror getType() {
        return fieldElement.asType();
    }

    public FieldKind getKind() {
        return fieldKind;
    }

    public ExecutionKind getExecutionKind() {
        return executionKind;
    }

    public NodeData getNodeData() {
        return nodeData;
    }

    public String getName() {
        return fieldElement.getSimpleName().toString();
    }

    @Override
    public String toString() {
        return "NodeFieldData[name=" + getName() + ", kind=" + fieldKind + ", execution=" + executionKind + ", node=" + getNodeData().toString() + "]";
    }

}
