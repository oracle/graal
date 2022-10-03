/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.operations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.Template;

public class SingleOperationData extends Template {
    private String name;
    private MethodProperties mainProperties;
    private NodeData nodeData;
    private OperationsData parent;
    private final Set<TypeMirror> throwDeclarations = new HashSet<>();
    private final boolean isShortCircuit;
    private boolean shortCircuitContinueWhen;

    public enum ParameterKind {
        STACK_VALUE,
        VARIADIC,
        VIRTUAL_FRAME,
        LOCAL_SETTER,
        LOCAL_SETTER_ARRAY;

        public TypeMirror getParameterType(ProcessorContext context) {
            switch (this) {
                case STACK_VALUE:
                    return context.getType(Object.class);
                case VARIADIC:
                    return new ArrayCodeTypeMirror(context.getType(Object.class));
                case VIRTUAL_FRAME:
                    return context.getTypes().VirtualFrame;
                case LOCAL_SETTER:
                    return context.getTypes().LocalSetter;
                case LOCAL_SETTER_ARRAY:
                    return new ArrayCodeTypeMirror(context.getTypes().LocalSetter);
                default:
                    throw new IllegalArgumentException("" + this);
            }
        }

        public boolean isStackValue() {
            switch (this) {
                case STACK_VALUE:
                case VARIADIC:
                    return true;
                case VIRTUAL_FRAME:
                case LOCAL_SETTER:
                case LOCAL_SETTER_ARRAY:
                    return false;
                default:
                    throw new IllegalArgumentException(this.toString());
            }
        }
    }

    public static class MethodProperties {
        public final ExecutableElement element;
        public final List<ParameterKind> parameters;
        public final boolean isVariadic;
        public final boolean returnsValue;
        public final int numStackValues;
        public final int numLocalReferences;

        public MethodProperties(ExecutableElement element, List<ParameterKind> parameters, boolean isVariadic, boolean returnsValue, int numLocalReferences) {
            this.element = element;
            this.parameters = parameters;
            int stackValues = 0;
            for (ParameterKind param : parameters) {
                if (param.isStackValue()) {
                    stackValues++;
                }
            }
            this.numStackValues = stackValues;
            this.isVariadic = isVariadic;
            this.returnsValue = returnsValue;
            this.numLocalReferences = numLocalReferences;
        }

        public void checkMatches(SingleOperationData data, MethodProperties other) {
            if (other.numStackValues != numStackValues) {
                data.addError(element, "All methods must have same number of arguments");
            }

            if (other.isVariadic != isVariadic) {
                data.addError(element, "All methods must (not) be variadic");
            }

            if (other.returnsValue != returnsValue) {
                data.addError(element, "All methods must (not) return value");
            }

            if (other.numLocalReferences != numLocalReferences) {
                data.addError(element, "All methods must have same number of local references");
            }
        }

        @Override
        public String toString() {
            return "Props[parameters=" + parameters + ", variadic=" + isVariadic + ", returns=" + returnsValue + ", numStackValues=" + numStackValues + ", numLocalReferences=" + numLocalReferences +
                            "]";
        }
    }

    public SingleOperationData(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation, OperationsData parent, String name, boolean isShortCircuit) {
        super(context, templateType, annotation);
        this.parent = parent;
        this.name = name;
        this.isShortCircuit = isShortCircuit;
    }

    @Override
    public MessageContainer getBaseContainer() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<TypeMirror> getThrowDeclarations() {
        return throwDeclarations;
    }

    public MethodProperties getMainProperties() {
        return mainProperties;
    }

    public boolean isShortCircuit() {
        return isShortCircuit;
    }

    public void setMainProperties(MethodProperties mainProperties) {
        this.mainProperties = mainProperties;
    }

    public NodeData getNodeData() {
        return nodeData;
    }

    void setNodeData(NodeData data) {
        this.nodeData = data;
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return getTemplateTypeAnnotation();
    }

    @Override
    public AnnotationValue getMessageAnnotationValue() {
        return null;
    }

    @Override
    public Element getMessageElement() {
        if (getMessageAnnotation() != null) {
            return parent.getMessageElement();
        } else {
            return getTemplateType();
        }
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        if (nodeData == null) {
            return List.of();
        }
        return List.of(nodeData);
    }

    public boolean getShortCircuitContinueWhen() {
        return shortCircuitContinueWhen;
    }

    public void setShortCircuitContinueWhen(boolean shortCircuitContinueWhen) {
        this.shortCircuitContinueWhen = shortCircuitContinueWhen;
    }

}
