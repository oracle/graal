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
package com.oracle.truffle.codegen.processor.operation;

import java.util.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.template.ParameterSpec.Cardinality;
import com.oracle.truffle.codegen.processor.template.ParameterSpec.Kind;


public abstract class OperationMethodParser<E extends TemplateMethod> extends TemplateMethodParser<E>{

    private final OperationData operation;

    public OperationMethodParser(ProcessorContext context, OperationData operation) {
        super(context);
        this.operation = operation;
    }

    public OperationData getOperation() {
        return operation;
    }

    protected ParameterSpec createValueParameterSpec(String valueName) {
        return new ParameterSpec(valueName, operation.getTypeSystem(),
                        Kind.EXECUTE, false, Cardinality.ONE);
    }

    protected ParameterSpec createReturnParameterSpec() {
        return createValueParameterSpec("operation");
    }

    protected final MethodSpec createDefaultMethodSpec(String shortCircuitName) {
        List<ParameterSpec> defaultParameters = new ArrayList<>();
        ParameterSpec frameSpec = new ParameterSpec("frame", getContext().getTruffleTypes().getFrame(), Kind.SIGNATURE, true);
        defaultParameters.add(frameSpec);

        for (String valueName : operation.getValues()) {
            defaultParameters.add(createValueParameterSpec(valueName));
        }

        for (String valueName : operation.getShortCircuitValues()) {
            if (shortCircuitName != null && valueName.equals(shortCircuitName)) {
                break;
            }

            defaultParameters.add(new ParameterSpec(shortCircuitValueName(valueName),
                            getContext().getType(boolean.class), Kind.SHORT_CIRCUIT, false));

            defaultParameters.add(createValueParameterSpec(valueName));
        }

        for (OperationFieldData field : operation.getSuperFields()) {
            defaultParameters.add(new ParameterSpec(field.getName(), field.getJavaClass(), Kind.SUPER_ATTRIBUTE, true));
        }

        for (OperationFieldData field : operation.getOperationFields()) {
            defaultParameters.add(new ParameterSpec(field.getName(), field.getJavaClass(), Kind.ATTRIBUTE, false));
        }

        return new MethodSpec(createReturnParameterSpec(), defaultParameters);
    }

    private static String shortCircuitValueName(String valueName) {
        return "has" + Utils.firstLetterUpperCase(valueName) + "Value";
    }

}
