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

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;

public class ExecutableTypeData extends MessageContainer implements Comparable<ExecutableTypeData> {

    private final ExecutableElement method;
    private final TypeMirror frameParameter;
    private final List<TypeMirror> evaluatedParameters;

    public ExecutableTypeData(ExecutableElement method, int signatureSize, List<TypeMirror> frameTypes) {
        this.method = method;
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
    }

    public ExecutableElement getMethod() {
        return method;
    }

    public String getName() {
        return method.getSimpleName().toString();
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
        return method.getReturnType();
    }

    public boolean hasUnexpectedValue(ProcessorContext context) {
        return ElementUtils.canThrowType(method.getThrownTypes(), context.getType(UnexpectedResultException.class));
    }

    public boolean isFinal() {
        return method.getModifiers().contains(Modifier.FINAL);
    }

    public boolean isAbstract() {
        return method.getModifiers().contains(Modifier.ABSTRACT);
    }

    public int getEvaluatedCount() {
        return evaluatedParameters.size();
    }

    public int compareTo(ExecutableTypeData o) {
        return ElementUtils.compareMethod(method, o.getMethod());
    }

}
