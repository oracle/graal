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

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.util.*;

/**
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
public class TemplateMethod extends MessageContainer implements Comparable<TemplateMethod> {

    public static final int NO_NATURAL_ORDER = -1;

    private String id;
    private final Template template;
    private final int naturalOrder;
    private final MethodSpec specification;
    private final ExecutableElement method;
    private final AnnotationMirror markerAnnotation;
    private Parameter returnType;
    private List<Parameter> parameters;

    public TemplateMethod(String id, int naturalOrder, Template template, MethodSpec specification, ExecutableElement method, AnnotationMirror markerAnnotation, Parameter returnType,
                    List<Parameter> parameters) {
        this.template = template;
        this.specification = specification;
        this.naturalOrder = naturalOrder;
        this.method = method;
        this.markerAnnotation = markerAnnotation;
        this.returnType = returnType;
        this.parameters = new ArrayList<>();
        for (Parameter param : parameters) {
            Parameter newParam = new Parameter(param);
            this.parameters.add(newParam);
            newParam.setMethod(this);
        }
        this.id = id;
    }

    public int getNaturalOrder() {
        return naturalOrder;
    }

    public TemplateMethod(TemplateMethod method) {
        this(method.id, method.naturalOrder, method.template, method.specification, method.method, method.markerAnnotation, method.returnType, method.parameters);
        getMessages().addAll(method.getMessages());
    }

    public TemplateMethod(TemplateMethod method, ExecutableElement executable) {
        this(method.id, method.naturalOrder, method.template, method.specification, executable, method.markerAnnotation, method.returnType, method.parameters);
        getMessages().addAll(method.getMessages());
    }

    public void setParameters(List<Parameter> parameters) {
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

    public Parameter getReturnType() {
        return returnType;
    }

    public void replaceParameter(String localName, Parameter newParameter) {
        if (returnType.getLocalName().equals(localName)) {
            returnType = newParameter;
            returnType.setMethod(this);
        }

        for (ListIterator<Parameter> iterator = parameters.listIterator(); iterator.hasNext();) {
            Parameter parameter = iterator.next();
            if (parameter.getLocalName().equals(localName)) {
                iterator.set(newParameter);
                newParameter.setMethod(this);
            }
        }
    }

    public List<Parameter> getRequiredParameters() {
        List<Parameter> requiredParameters = new ArrayList<>();
        for (Parameter parameter : getParameters()) {
            if (getSpecification().getRequired().contains(parameter.getSpecification())) {
                requiredParameters.add(parameter);
            }
        }
        return requiredParameters;
    }

    public Iterable<Parameter> getSignatureParameters() {
        return new FilteredIterable<>(getParameters(), new Predicate<Parameter>() {
            public boolean evaluate(Parameter value) {
                return value.getSpecification().isSignature();
            }
        });
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public List<Parameter> findParameters(ParameterSpec spec) {
        List<Parameter> foundParameters = new ArrayList<>();
        for (Parameter param : getReturnTypeAndParameters()) {
            if (param.getSpecification().getName().equals(spec.getName())) {
                foundParameters.add(param);
            }
        }
        return foundParameters;
    }

    public List<Parameter> findByExecutionData(NodeExecutionData execution) {
        List<Parameter> foundParameters = new ArrayList<>();
        for (Parameter parameter : getParameters()) {
            ParameterSpec spec = parameter.getSpecification();
            if (spec != null && spec.getExecution() != null && spec.getExecution().equals(execution) && parameter.getSpecification().isSignature()) {
                foundParameters.add(parameter);
            }
        }
        return foundParameters;
    }

    public Parameter findParameter(String valueName) {
        for (Parameter param : getReturnTypeAndParameters()) {
            if (param.getLocalName().equals(valueName)) {
                return param;
            }
        }
        return null;
    }

    public List<Parameter> getReturnTypeAndParameters() {
        List<Parameter> allParameters = new ArrayList<>(getParameters().size() + 1);
        if (getReturnType() != null) {
            allParameters.add(getReturnType());
        }
        allParameters.addAll(getParameters());
        return Collections.unmodifiableList(allParameters);
    }

    public boolean canBeAccessedByInstanceOf(TypeMirror type) {
        TypeMirror methodType = ElementUtils.findNearestEnclosingType(getMethod()).asType();
        return ElementUtils.isAssignable(type, methodType) || ElementUtils.isAssignable(methodType, type);
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

    public Parameter getPreviousParam(Parameter searchParam) {
        Parameter prev = null;
        for (Parameter param : getParameters()) {
            if (param == searchParam) {
                return prev;
            }
            prev = param;
        }
        return prev;
    }

    @SuppressWarnings("unused")
    public int getSignatureSize() {
        int signatureSize = 0;
        for (Parameter parameter : getSignatureParameters()) {
            signatureSize++;
        }
        return signatureSize;
    }

    public TypeSignature getTypeSignature() {
        TypeSignature signature = new TypeSignature();
        signature.types.add(getReturnType().getTypeSystemType());
        for (Parameter parameter : getSignatureParameters()) {
            TypeData typeData = parameter.getTypeSystemType();
            if (typeData != null) {
                signature.types.add(typeData);
            }
        }
        return signature;
    }

    public Parameter getSignatureParameter(int searchIndex) {
        int index = 0;
        for (Parameter parameter : getParameters()) {
            if (!parameter.getSpecification().isSignature()) {
                continue;
            }
            if (index == searchIndex) {
                return parameter;
            }
            index++;
        }
        return null;
    }

    public void updateSignature(TypeSignature signature) {
        // TODO(CH): fails in normal usage - output ok though
        // assert signature.size() >= 1;

        int signatureIndex = 0;
        for (Parameter parameter : getReturnTypeAndParameters()) {
            if (!parameter.getSpecification().isSignature()) {
                continue;
            }
            if (signatureIndex >= signature.size()) {
                break;
            }
            TypeData newType = signature.get(signatureIndex++);
            if (!parameter.getTypeSystemType().equals(newType)) {
                replaceParameter(parameter.getLocalName(), new Parameter(parameter, newType));
            }
        }
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
            TypeElement enclosingType1 = ElementUtils.findNearestEnclosingType(getMethod());
            TypeElement enclosingType2 = ElementUtils.findNearestEnclosingType(o.getMethod());
            compare = enclosingType1.getQualifiedName().toString().compareTo(enclosingType2.getQualifiedName().toString());
        }
        return compare;
    }

    public List<Parameter> getParametersAfter(Parameter genericParameter) {
        boolean found = false;
        List<Parameter> foundParameters = new ArrayList<>();
        for (Parameter param : getParameters()) {
            if (param.getLocalName().equals(genericParameter.getLocalName())) {
                found = true;
            } else if (found) {
                foundParameters.add(param);
            }
        }
        return foundParameters;
    }

    public int compareBySignature(TemplateMethod compareMethod) {
        final TypeSystemData typeSystem = getTemplate().getTypeSystem();
        if (typeSystem != compareMethod.getTemplate().getTypeSystem()) {
            throw new IllegalStateException("Cannot compare two methods with different type systems.");
        }

        List<TypeMirror> signature1 = getSignatureTypes(this);
        List<TypeMirror> signature2 = getSignatureTypes(compareMethod);

        int result = 0;
        for (int i = 0; i < Math.max(signature1.size(), signature2.size()); i++) {
            TypeMirror t1 = i < signature1.size() ? signature1.get(i) : null;
            TypeMirror t2 = i < signature2.size() ? signature2.get(i) : null;
            result = compareParameter(typeSystem, t1, t2);
            if (result != 0) {
                break;
            }
        }

        return result;
    }

    protected static int compareParameter(TypeSystemData data, TypeMirror signature1, TypeMirror signature2) {
        if (signature1 == null) {
            return 1;
        } else if (signature2 == null) {
            return -1;
        }

        if (ElementUtils.typeEquals(signature1, signature2)) {
            return 0;
        }

        int index1 = data.findType(signature1);
        int index2 = data.findType(signature2);
        if (index1 != -1 && index2 != -1) {
            return index1 - index2;
        }

        // TODO this version if subclass of should be improved.
        if (signature1.getKind() == TypeKind.DECLARED && signature2.getKind() == TypeKind.DECLARED) {
            TypeElement element1 = ElementUtils.fromTypeMirror(signature1);
            TypeElement element2 = ElementUtils.fromTypeMirror(signature2);

            if (ElementUtils.getDirectSuperTypes(element1).contains(element2)) {
                return -1;
            } else if (ElementUtils.getDirectSuperTypes(element2).contains(element1)) {
                return 1;
            }
        }
        return ElementUtils.getSimpleName(signature1).compareTo(ElementUtils.getSimpleName(signature2));
    }

    public static List<TypeMirror> getSignatureTypes(TemplateMethod method) {
        List<TypeMirror> types = new ArrayList<>();
        for (Parameter param : method.getSignatureParameters()) {
            types.add(param.getType());
        }
        return types;
    }

    public static class TypeSignature implements Iterable<TypeData>, Comparable<TypeSignature> {

        private final List<TypeData> types;

        public TypeSignature() {
            this.types = new ArrayList<>();
        }

        public TypeSignature(List<TypeData> signature) {
            this.types = signature;
        }

        @Override
        public int hashCode() {
            return types.hashCode();
        }

        public int size() {
            return types.size();
        }

        public TypeData get(int index) {
            return types.get(index);
        }

        public int compareTo(TypeSignature other) {
            if (this == other) {
                return 0;
            } else if (types.size() != other.types.size()) {
                return types.size() - other.types.size();
            } else if (types.isEmpty()) {
                return 0;
            }

            for (int i = 0; i < types.size(); i++) {
                TypeData type1 = types.get(i);
                TypeData type2 = other.types.get(i);

                int comparison = type1.compareTo(type2);
                if (comparison != 0) {
                    return comparison;
                }
            }

            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TypeSignature) {
                return ((TypeSignature) obj).types.equals(types);
            }
            return super.equals(obj);
        }

        public Iterator<TypeData> iterator() {
            return types.iterator();
        }

        @Override
        public String toString() {
            return types.toString();
        }
    }

}
