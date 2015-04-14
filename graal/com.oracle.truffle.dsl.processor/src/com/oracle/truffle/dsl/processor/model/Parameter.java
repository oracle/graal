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
package com.oracle.truffle.dsl.processor.model;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

public final class Parameter {

    private final ParameterSpec specification;
    private TemplateMethod method;
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

    void setMethod(TemplateMethod method) {
        this.method = method;
    }

    public ParameterSpec getSpecification() {
        return specification;
    }

    public TemplateMethod getMethod() {
        return method;
    }

    public TypeMirror getType() {
        return type;
    }

    public boolean isTypeVarArgs() {
        return typeVarArgsIndex >= 0;
    }

    public Parameter getPreviousParameter() {
        return method.getPreviousParam(this);
    }

    @Override
    public String toString() {
        return "Parameter [localName=" + localName + ", type=" + getType() + ", variableElement=" + variableElement + "]";
    }

}
