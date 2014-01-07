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
package com.oracle.truffle.dsl.processor.template;

import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.typesystem.*;

public class ActualParameter {

    private final ParameterSpec specification;
    private TypeData typeSystemType;
    private TemplateMethod method;
    private final String localName;
    private final int specificationVarArgsIndex;
    private final int typeVarArgsIndex;
    private final TypeMirror actualType;

    public ActualParameter(ParameterSpec specification, TypeMirror actualType, int specificationVarArgsIndex, int typeVarArgsIndex) {
        this.specification = specification;
        this.actualType = actualType;
        this.typeSystemType = null;

        this.specificationVarArgsIndex = specificationVarArgsIndex;

        String valueName = specification.getName() + "Value";
        if (specificationVarArgsIndex > -1) {
            valueName += specificationVarArgsIndex;
        }
        this.typeVarArgsIndex = typeVarArgsIndex;
        this.localName = valueName;
    }

    public ActualParameter(ParameterSpec specification, TypeData actualType, int specificationIndex, int varArgsIndex) {
        this(specification, actualType.getPrimitiveType(), specificationIndex, varArgsIndex);
        this.typeSystemType = actualType;
    }

    public ActualParameter(ActualParameter parameter, TypeData otherType) {
        this(parameter.specification, otherType, parameter.specificationVarArgsIndex, parameter.typeVarArgsIndex);
    }

    public ActualParameter(ActualParameter parameter) {
        this.specification = parameter.specification;
        this.actualType = parameter.actualType;
        this.typeSystemType = parameter.typeSystemType;
        this.specificationVarArgsIndex = parameter.specificationVarArgsIndex;
        this.localName = parameter.localName;
        this.typeVarArgsIndex = parameter.typeVarArgsIndex;
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
        return actualType;
    }

    public TypeData getTypeSystemType() {
        return typeSystemType;
    }

    public boolean isTypeVarArgs() {
        return typeVarArgsIndex >= 0;
    }

    public ActualParameter getPreviousParameter() {
        return method.getPreviousParam(this);
    }

    @Override
    public String toString() {
        return Utils.getSimpleName(actualType);
    }
}
