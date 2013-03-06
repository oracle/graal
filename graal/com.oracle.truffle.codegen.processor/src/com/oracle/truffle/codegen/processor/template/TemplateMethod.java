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
package com.oracle.truffle.codegen.processor.template;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.codegen.processor.*;

public class TemplateMethod {

    private String id;
    private final Template template;
    private final MethodSpec specification;
    private final ExecutableElement method;
    private final AnnotationMirror markerAnnotation;
    private final ActualParameter returnType;
    private final List<ActualParameter> parameters;

    public TemplateMethod(String id, Template template, MethodSpec specification, ExecutableElement method, AnnotationMirror markerAnnotation, ActualParameter returnType,
                    List<ActualParameter> parameters) {
        this.template = template;
        this.specification = specification;
        this.method = method;
        this.markerAnnotation = markerAnnotation;
        this.returnType = returnType;
        this.parameters = parameters;
        this.id = id;

        if (parameters != null) {
            for (ActualParameter param : parameters) {
                param.setMethod(this);
            }
        }
    }

    public TemplateMethod(TemplateMethod method) {
        this(method.id, method.template, method.specification, method.method, method.markerAnnotation, method.returnType, method.parameters);
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Template getTemplate() {
        return template;
    }

    public MethodSpec getSpecification() {
        return specification;
    }

    public ActualParameter getReturnType() {
        return returnType;
    }

    public List<ActualParameter> getParameters() {
        return parameters;
    }

    public ActualParameter findParameter(String valueName) {
        for (ActualParameter param : getParameters()) {
            if (param.getName().equals(valueName)) {
                return param;
            }
        }
        return null;
    }

    public List<ActualParameter> getReturnTypeAndParameters() {
        List<ActualParameter> allParameters = new ArrayList<>(getParameters().size() + 1);
        allParameters.add(getReturnType());
        allParameters.addAll(getParameters());
        return Collections.unmodifiableList(allParameters);
    }

    public ActualParameter findParameter(ParameterSpec spec) {
        for (ActualParameter param : getParameters()) {
            if (param.getSpecification().getName().equals(spec.getName())) {
                return param;
            }
        }
        return null;
    }

    public boolean canBeAccessedByInstanceOf(TypeMirror type) {
        TypeMirror methodType = Utils.findNearestEnclosingType(getMethod()).asType();
        return Utils.isAssignable(type, methodType) || Utils.isAssignable(methodType, type);
    }

    public ExecutableElement getMethod() {
        return method;
    }

    public String getMethodName() {
        return getMethod().getSimpleName().toString();
    }

    public AnnotationMirror getMarkerAnnotation() {
        return markerAnnotation;
    }

    @Override
    public String toString() {
        return "id = " + getId() + ", " + getClass().getSimpleName() + " [method = " + getMethod() + "]";
    }

    public ActualParameter getPreviousParam(ActualParameter searchParam) {
        ActualParameter prev = null;
        for (ActualParameter param : getParameters()) {
            if (param == searchParam) {
                return prev;
            }
            prev = param;
        }
        return prev;
    }
}
