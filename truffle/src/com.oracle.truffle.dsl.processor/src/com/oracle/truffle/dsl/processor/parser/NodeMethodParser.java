/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.dsl.processor.parser;

import java.util.Arrays;
import java.util.Collection;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.MethodSpec;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.NodeFieldData;
import com.oracle.truffle.dsl.processor.model.ParameterSpec;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;

public abstract class NodeMethodParser<E extends TemplateMethod> extends TemplateMethodParser<NodeData, E> {

    public NodeMethodParser(ProcessorContext context, NodeData node) {
        super(context, node);
    }

    public NodeData getNode() {
        return template;
    }

    protected ParameterSpec createValueParameterSpec(NodeExecutionData execution) {
        ParameterSpec spec = new ParameterSpec(execution.getName(), getPossibleParameterTypes(execution));
        spec.setExecution(execution);
        return spec;
    }

    protected Collection<TypeMirror> getPossibleParameterTypes(NodeExecutionData execution) {
        return getNode().getGenericTypes(execution);
    }

    protected ParameterSpec createReturnParameterSpec() {
        ParameterSpec returnValue = new ParameterSpec("returnValue", getPossibleReturnTypes());
        returnValue.setExecution(getNode().getThisExecution());
        return returnValue;
    }

    protected Collection<TypeMirror> getPossibleReturnTypes() {
        return Arrays.asList(getNode().getGenericType(getNode().getThisExecution()));
    }

    @Override
    public boolean isParsable(ExecutableElement method) {
        if (getAnnotationType() != null) {
            return ElementUtils.findAnnotationMirror(getContext().getEnvironment(), method, getAnnotationType()) != null;
        }

        return true;
    }

    @SuppressWarnings("unused")
    protected final MethodSpec createDefaultMethodSpec(ExecutableElement method, AnnotationMirror mirror, boolean shortCircuitsEnabled, String shortCircuitName) {
        MethodSpec methodSpec = new MethodSpec(createReturnParameterSpec());

        addDefaultFrame(methodSpec);
        addDefaultFieldMethodSpec(methodSpec);
        addDefaultChildren(shortCircuitName, methodSpec);

        return methodSpec;
    }

    private void addDefaultChildren(String breakName, MethodSpec spec) {
        if (getNode().getChildren() == null) {
            // children are null when parsing executable types
            return;
        }

        for (NodeExecutionData execution : getNode().getChildExecutions()) {
            if (breakName != null && execution.getIndexedName().equals(breakName)) {
                break;
            }

            spec.addRequired(createValueParameterSpec(execution));
        }
    }

    protected void addDefaultFrame(MethodSpec methodSpec) {
        if (getNode().supportsFrame()) {
            methodSpec.addOptional(new ParameterSpec("frame", getNode().getFrameType()));
        }
    }

    protected void addDefaultFieldMethodSpec(MethodSpec methodSpec) {
        for (NodeFieldData field : getNode().getFields()) {
            if (field.getGetter() == null) {
                ParameterSpec spec = new ParameterSpec(field.getName(), field.getType());
                spec.setLocal(true);
                methodSpec.addOptional(spec);
            }
        }
    }

}
