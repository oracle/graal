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
    private final int specificationIndex;
    private final int varArgsIndex;
    private final boolean implicit;
    private final TypeMirror type;

    public ActualParameter(ParameterSpec specification, TypeMirror actualType, int specificationIndex, int varArgsIndex, boolean implicit) {
        this.specification = specification;
        this.type = actualType;
        this.typeSystemType = null;

        this.specificationIndex = specificationIndex;
        this.implicit = implicit;
        String valueName = specification.getName() + "Value";

        if (specification.isIndexed()) {
            valueName += specificationIndex;
        }
        this.varArgsIndex = varArgsIndex;
        this.localName = valueName;
    }

    public ActualParameter(ParameterSpec specification, TypeData actualType, int specificationIndex, int varArgsIndex, boolean implicit) {
        this(specification, actualType.getPrimitiveType(), specificationIndex, varArgsIndex, implicit);
        this.typeSystemType = actualType;
    }

    public ActualParameter(ActualParameter parameter, TypeData otherType) {
        this(parameter.specification, otherType, parameter.specificationIndex, parameter.varArgsIndex, parameter.implicit);
    }

    public ActualParameter(ActualParameter parameter) {
        this.specification = parameter.specification;
        this.type = parameter.type;
        this.typeSystemType = parameter.typeSystemType;
        this.specificationIndex = parameter.specificationIndex;
        this.implicit = parameter.implicit;
        this.localName = parameter.localName;
        this.varArgsIndex = parameter.varArgsIndex;
    }

    public int getVarArgsIndex() {
        return varArgsIndex;
    }

    public boolean isImplicit() {
        return implicit;
    }

    public int getSpecificationIndex() {
        return specificationIndex;
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

    public TypeData getTypeSystemType() {
        return typeSystemType;
    }

    public boolean isVarArgs() {
        return varArgsIndex >= 0;
    }

    public ActualParameter getPreviousParameter() {
        return method.getPreviousParam(this);
    }

    @Override
    public String toString() {
        return Utils.getSimpleName(type);
    }
}
