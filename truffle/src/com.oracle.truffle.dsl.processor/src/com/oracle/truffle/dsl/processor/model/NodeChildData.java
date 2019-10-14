/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.model;

import java.util.Collections;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;

public class NodeChildData extends MessageContainer {

    public enum Cardinality {
        ONE,
        MANY;

        public boolean isMany() {
            return this == MANY;
        }

        public boolean isOne() {
            return this == ONE;
        }
    }

    private final Element sourceElement;
    private final AnnotationMirror sourceAnnotationMirror;
    private final String name;
    private final TypeMirror type;
    private final TypeMirror originalType;
    private final Element accessElement;
    private final Cardinality cardinality;
    private final AnnotationValue executeWithValue;

    private List<NodeExecutionData> executeWith = Collections.emptyList();

    private NodeData childNode;

    public NodeChildData(Element sourceElement, AnnotationMirror sourceMirror, String name, TypeMirror nodeType, TypeMirror originalNodeType, Element accessElement, Cardinality cardinality,
                    AnnotationValue executeWithValue) {
        this.sourceElement = sourceElement;
        this.sourceAnnotationMirror = sourceMirror;
        this.name = name;
        this.type = nodeType;
        this.originalType = originalNodeType;
        this.accessElement = accessElement;
        this.cardinality = cardinality;
        this.executeWithValue = executeWithValue;
    }

    public boolean needsGeneratedField() {
        return accessElement == null || accessElement.getKind() != ElementKind.FIELD;
    }

    public AnnotationValue getExecuteWithValue() {
        return executeWithValue;
    }

    public List<NodeExecutionData> getExecuteWith() {
        return executeWith;
    }

    public void setExecuteWith(List<NodeExecutionData> executeWith) {
        this.executeWith = executeWith;
    }

    public ExecutableTypeData findExecutableType(TypeMirror targetType) {
        return childNode.findExecutableType(targetType, getExecuteWith().size());
    }

    public List<ExecutableTypeData> findGenericExecutableTypes() {
        return childNode.findGenericExecutableTypes(getExecuteWith().size());
    }

    public ExecutableTypeData findAnyGenericExecutableType(ProcessorContext context) {
        return childNode.findAnyGenericExecutableType(context, getExecuteWith().size());
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

    public void setNode(NodeData nodeData) {
        this.childNode = nodeData;
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

    public NodeData getNodeData() {
        return childNode;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "NodeFieldData[name=" + getName() + ", kind=" + cardinality + ", node=" + getNodeData() + "]";
    }

}
