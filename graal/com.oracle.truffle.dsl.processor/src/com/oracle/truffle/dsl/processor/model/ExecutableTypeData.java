/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;

public class ExecutableTypeData extends MessageContainer implements Comparable<ExecutableTypeData> {

    private final ExecutableElement method;
    private final TypeMirror returnType;
    private final TypeMirror frameParameter;
    private final List<TypeMirror> evaluatedParameters;
    private ExecutableTypeData delegatedTo;
    private final List<ExecutableTypeData> delegatedFrom = new ArrayList<>();

    private String uniqueName;

    public ExecutableTypeData(TypeMirror returnType, String uniqueName, TypeMirror frameParameter, List<TypeMirror> evaluatedParameters) {
        this.returnType = returnType;
        this.frameParameter = frameParameter;
        this.evaluatedParameters = evaluatedParameters;
        this.uniqueName = uniqueName;
        this.method = null;
    }

    public ExecutableTypeData(ExecutableElement method, int signatureSize, List<TypeMirror> frameTypes) {
        this.method = method;
        this.returnType = method.getReturnType();
        TypeMirror foundFrameParameter = null;
        List<? extends VariableElement> parameters = method.getParameters();

        int parameterIndex = 0;
        evaluatedParameters = new ArrayList<>();
        if (!parameters.isEmpty()) {
            TypeMirror firstParameter = parameters.get(0).asType();
            for (TypeMirror frameType : frameTypes) {
                if (ElementUtils.typeEquals(firstParameter, frameType)) {
                    foundFrameParameter = firstParameter;
                    parameterIndex++;
                    break;
                }
            }
        }

        int numberParameters = Math.max(parameters.size() - parameterIndex, signatureSize);
        for (int i = 0; i < numberParameters; i++) {
            TypeMirror parameter;
            if (method.isVarArgs() && parameterIndex >= parameters.size() - 1) {
                ArrayType varArgsArray = (ArrayType) parameters.get(parameters.size() - 1).asType();
                parameter = varArgsArray.getComponentType();
            } else if (parameterIndex < parameters.size()) {
                parameter = parameters.get(parameterIndex).asType();
            } else {
                break;
            }
            parameterIndex++;
            evaluatedParameters.add(parameter);
        }
        this.frameParameter = foundFrameParameter;
        this.uniqueName = "execute" + (ElementUtils.isObject(getReturnType()) ? "" : ElementUtils.getTypeId(getReturnType()));
    }

    public void addDelegatedFrom(ExecutableTypeData child) {
        this.delegatedFrom.add(child);
        child.delegatedTo = this;
    }

    public List<ExecutableTypeData> getDelegatedFrom() {
        return delegatedFrom;
    }

    public ExecutableTypeData getDelegatedTo() {
        return delegatedTo;
    }

    public ExecutableElement getMethod() {
        return method;
    }

    public String getUniqueName() {
        return uniqueName;
    }

    public void setUniqueName(String name) {
        this.uniqueName = name;
    }

    @Override
    public Element getMessageElement() {
        return method;
    }

    public List<TypeMirror> getEvaluatedParameters() {
        return evaluatedParameters;
    }

    public int getVarArgsIndex(int parameterIndex) {
        if (method.isVarArgs()) {
            int index = parameterIndex - (method.getParameters().size() - 1);
            return index;
        }
        return -1;
    }

    public int getParameterIndex(int signatureIndex) {
        return frameParameter != null ? signatureIndex + 1 : signatureIndex;
    }

    public TypeMirror getFrameParameter() {
        return frameParameter;
    }

    public TypeMirror getReturnType() {
        return returnType;
    }

    public boolean hasUnexpectedValue(ProcessorContext context) {
        return method == null ? false : ElementUtils.canThrowType(method.getThrownTypes(), context.getType(UnexpectedResultException.class));
    }

    public boolean isFinal() {
        return method == null ? false : method.getModifiers().contains(Modifier.FINAL);
    }

    public boolean isAbstract() {
        return method == null ? false : method.getModifiers().contains(Modifier.ABSTRACT);
    }

    public int getEvaluatedCount() {
        return evaluatedParameters.size();
    }

    public boolean canDelegateTo(NodeData node, ExecutableTypeData to) {
        ExecutableTypeData from = this;
        if (to.getEvaluatedCount() < from.getEvaluatedCount()) {
            return false;
        }

        ProcessorContext context = node.getContext();

        // we cannot delegate from generic to unexpected
        if (!from.hasUnexpectedValue(context) && to.hasUnexpectedValue(context)) {
            return false;
        }

        // we can skip the return type check for void. everything is assignable to void.
        if (!isVoid(from.getReturnType())) {
            if (!isSubtypeBoxed(context, from.getReturnType(), to.getReturnType()) && !isSubtypeBoxed(context, to.getReturnType(), from.getReturnType())) {
                return false;
            }
        }
        if (from.getFrameParameter() != to.getFrameParameter() && from.getFrameParameter() != null && to.getFrameParameter() != null &&
                        !isSubtypeBoxed(context, from.getFrameParameter(), to.getFrameParameter())) {
            return false;
        }

        for (int i = 0; i < from.getEvaluatedCount(); i++) {
            if (!isSubtypeBoxed(context, from.getEvaluatedParameters().get(i), to.getEvaluatedParameters().get(i))) {
                return false;
            }
        }

        for (int i = from.getEvaluatedCount(); i < to.getEvaluatedCount(); i++) {
            TypeMirror delegateToParameter = to.getEvaluatedParameters().get(i);
            if (i < node.getChildExecutions().size()) {
                List<TypeMirror> genericTypes = node.getGenericTypes(node.getChildExecutions().get(i));

                boolean typeFound = false;
                for (TypeMirror generic : genericTypes) {
                    if (isSubtypeBoxed(context, generic, delegateToParameter)) {
                        typeFound = true;
                    }
                }
                if (!typeFound) {
                    return false;
                }
            }
        }

        return true;
    }

    public int compareTo(ExecutableTypeData o2) {
        ExecutableTypeData o1 = this;
        ProcessorContext context = ProcessorContext.getInstance();

        int result = Integer.compare(o2.getEvaluatedCount(), o1.getEvaluatedCount());
        if (result != 0) {
            return result;
        }

        result = Boolean.compare(o1.hasUnexpectedValue(context), o2.hasUnexpectedValue(context));
        if (result != 0) {
            return result;
        }

        result = compareType(context, o1.getReturnType(), o2.getReturnType());
        if (result != 0) {
            return result;
        }
        result = compareType(context, o1.getFrameParameter(), o2.getFrameParameter());
        if (result != 0) {
            return result;
        }

        for (int i = 0; i < o1.getEvaluatedCount(); i++) {
            result = compareType(context, o1.getEvaluatedParameters().get(i), o2.getEvaluatedParameters().get(i));
            if (result != 0) {
                return result;
            }
        }

        result = o1.getUniqueName().compareTo(o2.getUniqueName());
        if (result != 0) {
            return result;
        }

        if (o1.getMethod() != null && o2.getMethod() != null) {
            result = ElementUtils.compareMethod(o1.getMethod(), o2.getMethod());
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    public static int compareType(ProcessorContext context, TypeMirror signature1, TypeMirror signature2) {
        if (signature1 == null) {
            if (signature2 == null) {
                return 0;
            }
            return -1;
        } else if (signature2 == null) {
            return 1;
        }
        if (ElementUtils.typeEquals(signature1, signature2)) {
            return 0;
        }
        if (isVoid(signature1)) {
            if (isVoid(signature2)) {
                return 0;
            }
            return 1;
        } else if (isVoid(signature2)) {
            return -1;
        }

        TypeMirror boxedType1 = ElementUtils.boxType(context, signature1);
        TypeMirror boxedType2 = ElementUtils.boxType(context, signature2);

        if (ElementUtils.isSubtype(boxedType1, boxedType2)) {
            if (ElementUtils.isSubtype(boxedType2, boxedType1)) {
                return 0;
            }
            return 1;
        } else if (ElementUtils.isSubtype(boxedType2, boxedType1)) {
            return -1;
        } else {
            return ElementUtils.getSimpleName(signature1).compareTo(ElementUtils.getSimpleName(signature2));
        }
    }

    @Override
    public String toString() {
        return method != null ? ElementUtils.createReferenceName(method) : getUniqueName() + evaluatedParameters.toString();
    }

    public boolean sameParameters(ExecutableTypeData other) {
        if (!typeEquals(other.getFrameParameter(), getFrameParameter())) {
            return false;
        }

        if (getEvaluatedCount() != other.getEvaluatedCount()) {
            return false;
        }

        for (int i = 0; i < getEvaluatedCount(); i++) {
            if (!typeEquals(getEvaluatedParameters().get(i), other.getEvaluatedParameters().get(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean sameSignature(ExecutableTypeData other) {
        if (!typeEquals(other.getReturnType(), getReturnType())) {
            return false;
        }

        if (other.getFrameParameter() != null) {
            if (!typeEquals(getFrameParameter(), other.getFrameParameter())) {
                return false;
            }
        }

        if (getEvaluatedCount() != other.getEvaluatedCount()) {
            return false;
        }

        for (int i = 0; i < getEvaluatedCount(); i++) {
            if (!typeEquals(getEvaluatedParameters().get(i), other.getEvaluatedParameters().get(i))) {
                return false;
            }
        }

        return true;
    }
}
