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

import javax.lang.model.element.*;

public class TemplateMethod {

    private final Template template;
    private final MethodSpec specification;
    private final ExecutableElement method;
    private final AnnotationMirror markerAnnotation;
    private final ActualParameter returnType;
    private final ActualParameter[] parameters;

    public TemplateMethod(Template template, MethodSpec specification, ExecutableElement method, AnnotationMirror markerAnnotation, ActualParameter returnType, ActualParameter[] parameters) {
        this.template = template;
        this.specification = specification;
        this.method = method;
        this.markerAnnotation = markerAnnotation;
        this.returnType = returnType;
        this.parameters = parameters;

        if (parameters != null) {
            for (ActualParameter param : parameters) {
                param.setMethod(this);
            }
        }
    }

    public TemplateMethod(TemplateMethod method) {
        this(method.template, method.specification, method.method, method.markerAnnotation, method.returnType, method.parameters);
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

    public ActualParameter[] getParameters() {
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

    public ActualParameter findParameter(ParameterSpec spec) {
        for (ActualParameter param : getParameters()) {
            if (param.getSpecification() == spec) {
                return param;
            }
        }
        return null;
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
        return getClass().getSimpleName() + " [method = " + method + "]";
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
