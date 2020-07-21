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
package com.oracle.truffle.dsl.processor.model;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public final class Parameter {

    private final ParameterSpec specification;
    private String localName;
    private final int specificationVarArgsIndex;
    private final int typeVarArgsIndex;
    private final VariableElement variableElement;
    private final TypeMirror type;

    public Parameter(ParameterSpec specification, VariableElement variableElement, int specificationVarArgsIndex, int typeVarArgsIndex) {
        this.specification = specification;
        this.variableElement = variableElement;
        this.type = variableElement.asType();
        this.specificationVarArgsIndex = specificationVarArgsIndex;

        String valueName = specification.getName() + "Value";
        if (specificationVarArgsIndex > -1) {
            valueName += specificationVarArgsIndex;
        }
        this.typeVarArgsIndex = typeVarArgsIndex;
        this.localName = valueName;
    }

    public Parameter(Parameter parameter) {
        this.specification = parameter.specification;
        this.specificationVarArgsIndex = parameter.specificationVarArgsIndex;
        this.localName = parameter.localName;
        this.typeVarArgsIndex = parameter.typeVarArgsIndex;
        this.variableElement = parameter.variableElement;
        this.type = parameter.type;
    }

    public Parameter(Parameter parameter, TypeMirror newType) {
        this.specification = parameter.specification;
        this.specificationVarArgsIndex = parameter.specificationVarArgsIndex;
        this.localName = parameter.localName;
        this.typeVarArgsIndex = parameter.typeVarArgsIndex;
        this.variableElement = parameter.variableElement;
        this.type = newType;
    }

    public Parameter(Parameter parameter, VariableElement newVariable) {
        this.specification = parameter.specification;
        this.specificationVarArgsIndex = parameter.specificationVarArgsIndex;
        this.localName = newVariable.getSimpleName().toString();
        this.typeVarArgsIndex = parameter.typeVarArgsIndex;
        this.variableElement = newVariable;
        this.type = newVariable.asType();
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public VariableElement getVariableElement() {
        return variableElement;
    }

    public int getTypeVarArgsIndex() {
        return typeVarArgsIndex;
    }

    public int getSpecificationVarArgsIndex() {
        return specificationVarArgsIndex;
    }

    public String getLocalName() {
        return localName;
    }

    public ParameterSpec getSpecification() {
        return specification;
    }

    public TypeMirror getType() {
        return type;
    }

    public boolean isTypeVarArgs() {
        return typeVarArgsIndex >= 0;
    }

    @Override
    public String toString() {
        return "Parameter [localName=" + localName + ", type=" + getType() + ", variableElement=" + variableElement + "]";
    }

}
