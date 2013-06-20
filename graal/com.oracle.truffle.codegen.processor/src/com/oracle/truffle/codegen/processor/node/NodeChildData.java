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

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class NodeChildData extends MessageContainer {

    public enum Cardinality {
        ONE, MANY;

        public boolean isMany() {
            return this == MANY;
        }

        public boolean isOne() {
            return this == ONE;
        }
    }

    public enum ExecutionKind {
        DEFAULT, SHORT_CIRCUIT
    }

    private final Element sourceElement;
    private final AnnotationMirror sourceAnnotationMirror;

    private final String name;
    private final TypeMirror type;
    private final TypeMirror originalType;
    private final Element accessElement;

    private final Cardinality cardinality;
    private final ExecutionKind executionKind;

    private List<NodeChildData> executeWith = Collections.emptyList();

    private NodeData nodeData;

    public NodeChildData(Element sourceElement, AnnotationMirror sourceMirror, String name, TypeMirror nodeType, TypeMirror originalNodeType, Element accessElement, Cardinality cardinality,
                    ExecutionKind executionKind) {
        this.sourceElement = sourceElement;
        this.sourceAnnotationMirror = sourceMirror;
        this.name = name;
        this.type = nodeType;
        this.originalType = originalNodeType;
        this.accessElement = accessElement;
        this.cardinality = cardinality;
        this.executionKind = executionKind;
    }

    public List<NodeChildData> getExecuteWith() {
        return executeWith;
    }

    void setExecuteWith(List<NodeChildData> executeWith) {
        this.executeWith = executeWith;
    }

    public ExecutableTypeData findExecutableType(ProcessorContext context, TypeData targetType) {
        ExecutableTypeData executableType = nodeData.findExecutableType(targetType, getExecuteWith().size());
        if (executableType == null) {
            executableType = findAnyGenericExecutableType(context);
        }
        return executableType;
    }

    public List<ExecutableTypeData> findGenericExecutableTypes(ProcessorContext context) {
        return nodeData.findGenericExecutableTypes(context, getExecuteWith().size());
    }

    public ExecutableTypeData findAnyGenericExecutableType(ProcessorContext context) {
        return nodeData.findAnyGenericExecutableType(context, getExecuteWith().size());
    }

    public List<ExecutableTypeData> findExecutableTypes() {
        return nodeData.getExecutableTypes(getExecuteWith().size());
    }

    public TypeMirror getOriginalType() {
        return originalType;
    }

    @Override
    public Element getMessageElement() {
        return sourceElement;
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return sourceAnnotationMirror;
    }

    public boolean isShortCircuit() {
        return executionKind == ExecutionKind.SHORT_CIRCUIT;
    }

    void setNode(NodeData nodeData) {
        this.nodeData = nodeData;
        if (nodeData != null) {
            getMessages().addAll(nodeData.collectMessages());
        }
    }

    public Element getAccessElement() {
        return accessElement;
    }

    public TypeMirror getNodeType() {
        return type;
    }

    public Cardinality getCardinality() {
        return cardinality;
    }

    public ExecutionKind getExecutionKind() {
        return executionKind;
    }

    public NodeData getNodeData() {
        return nodeData;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "NodeFieldData[name=" + getName() + ", kind=" + cardinality + ", execution=" + executionKind + ", node=" + getNodeData() + "]";
    }

}
