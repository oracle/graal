/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
            return ElementUtils.findAnnotationMirror(method, getAnnotationType()) != null;
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
