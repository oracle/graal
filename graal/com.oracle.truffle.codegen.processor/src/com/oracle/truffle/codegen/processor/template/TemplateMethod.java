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
import com.oracle.truffle.codegen.processor.typesystem.*;

/**
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
public class TemplateMethod extends MessageContainer implements Comparable<TemplateMethod> {

    private String id;
    private final Template template;
    private final MethodSpec specification;
    private final ExecutableElement method;
    private final AnnotationMirror markerAnnotation;
    private ActualParameter returnType;
    private List<ActualParameter> parameters;

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
        getMessages().addAll(method.getMessages());
    }

    public void setParameters(List<ActualParameter> parameters) {
        this.parameters = parameters;
    }

    @Override
    public Element getMessageElement() {
        return method;
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return markerAnnotation;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        return Collections.emptyList();
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

    public void replaceParameter(String localName, ActualParameter newParameter) {
        if (returnType.getLocalName().equals(localName)) {
            returnType = newParameter;
            returnType.setMethod(this);
        }

        for (ListIterator<ActualParameter> iterator = parameters.listIterator(); iterator.hasNext();) {
            ActualParameter parameter = iterator.next();
            if (parameter.getLocalName().equals(localName)) {
                iterator.set(newParameter);
                newParameter.setMethod(this);
            }
        }
    }

    public List<ActualParameter> getRequiredParameters() {
        List<ActualParameter> requiredParameters = new ArrayList<>();
        for (ActualParameter parameter : getParameters()) {
            if (getSpecification().getRequired().contains(parameter.getSpecification())) {
                requiredParameters.add(parameter);
            }
        }
        return requiredParameters;
    }

    public List<ActualParameter> getParameters() {
        return parameters;
    }

    public List<ActualParameter> findParameters(ParameterSpec spec) {
        List<ActualParameter> foundParameters = new ArrayList<>();
        for (ActualParameter param : getReturnTypeAndParameters()) {
            if (param.getSpecification().equals(spec)) {
                foundParameters.add(param);
            }
        }
        return foundParameters;
    }

    public ActualParameter findParameter(String valueName) {
        for (ActualParameter param : getReturnTypeAndParameters()) {
            if (param.getLocalName().equals(valueName)) {
                return param;
            }
        }
        return null;
    }

    public List<ActualParameter> getReturnTypeAndParameters() {
        List<ActualParameter> allParameters = new ArrayList<>(getParameters().size() + 1);
        if (getReturnType() != null) {
            allParameters.add(getReturnType());
        }
        allParameters.addAll(getParameters());
        return Collections.unmodifiableList(allParameters);
    }

    public boolean canBeAccessedByInstanceOf(ProcessorContext context, TypeMirror type) {
        TypeMirror methodType = Utils.findNearestEnclosingType(getMethod()).asType();
        return Utils.isAssignable(context, type, methodType) || Utils.isAssignable(context, methodType, type);
    }

    public ExecutableElement getMethod() {
        return method;
    }

    public String getMethodName() {
        if (getMethod() != null) {
            return getMethod().getSimpleName().toString();
        } else {
            return "$synthetic";
        }
    }

    public AnnotationMirror getMarkerAnnotation() {
        return markerAnnotation;
    }

    @Override
    public String toString() {
        return String.format("%s [id = %s, method = %s]", getClass().getSimpleName(), getId(), getMethod());
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

    public TypeData getReturnSignature() {
        return getReturnType().getTypeSystemType();
    }

    public List<TypeData> getSignature() {
        List<TypeData> types = new ArrayList<>();
        for (ActualParameter parameter : getParameters()) {
            if (!parameter.getSpecification().isSignature()) {
                continue;
            }
            TypeData typeData = parameter.getTypeSystemType();
            if (typeData != null) {
                types.add(typeData);
            }
        }
        return types;
    }

    public List<ActualParameter> getSignatureParameters() {
        List<ActualParameter> types = new ArrayList<>();
        for (ActualParameter parameter : getParameters()) {
            if (!parameter.getSpecification().isSignature()) {
                continue;
            }
            types.add(parameter);
        }
        return types;
    }

    @Override
    public int compareTo(TemplateMethod o) {
        if (this == o) {
            return 0;
        }

        int compare = compareBySignature(o);
        if (compare == 0) {
            // if signature sorting failed sort by id
            compare = getId().compareTo(o.getId());
        }
        if (compare == 0) {
            // if still no difference sort by enclosing type name
            TypeElement enclosingType1 = Utils.findNearestEnclosingType(getMethod());
            TypeElement enclosingType2 = Utils.findNearestEnclosingType(o.getMethod());
            compare = enclosingType1.getQualifiedName().toString().compareTo(enclosingType2.getQualifiedName().toString());
        }
        return compare;
    }

    public List<ActualParameter> getParametersAfter(ActualParameter genericParameter) {
        boolean found = false;
        List<ActualParameter> foundParameters = new ArrayList<>();
        for (ActualParameter param : getParameters()) {
            if (param.getLocalName().equals(genericParameter.getLocalName())) {
                found = true;
            } else if (found) {
                foundParameters.add(param);
            }
        }
        return foundParameters;
    }

    public int compareBySignature(TemplateMethod compareMethod) {
        TypeSystemData typeSystem = getTemplate().getTypeSystem();
        if (typeSystem != compareMethod.getTemplate().getTypeSystem()) {
            throw new IllegalStateException("Cannot compare two methods with different type systems.");
        }

        List<TypeData> signature1 = getSignature();
        List<TypeData> signature2 = compareMethod.getSignature();
        if (signature1.size() != signature2.size()) {
            return signature2.size() - signature1.size();
        }

        int result = 0;
        for (int i = 0; i < signature1.size(); i++) {
            int typeResult = compareActualParameter(typeSystem, signature1.get(i), signature2.get(i));
            if (result == 0) {
                result = typeResult;
            } else if (typeResult != 0 && Math.signum(result) != Math.signum(typeResult)) {
                // We cannot define an order.
                return 0;
            }
        }
        if (result == 0) {
            TypeData returnSignature1 = getReturnSignature();
            TypeData returnSignature2 = compareMethod.getReturnSignature();

            result = compareActualParameter(typeSystem, returnSignature1, returnSignature2);
        }

        return result;
    }

    private static int compareActualParameter(TypeSystemData typeSystem, TypeData t1, TypeData t2) {
        int index1 = typeSystem.findType(t1);
        int index2 = typeSystem.findType(t2);
        return index1 - index2;
    }

}
