/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.model;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class NodeFieldData extends MessageContainer {

    private final Element messageElement;
    private final AnnotationMirror messageAnnotation;
    private final boolean generated;
    private ExecutableElement getter;
    private ExecutableElement setter;
    private final VariableElement variable;

    public NodeFieldData(Element messageElement, AnnotationMirror messageAnnotation, VariableElement variableElement, boolean generated) {
        this.messageElement = messageElement;
        this.messageAnnotation = messageAnnotation;
        this.generated = generated;
        this.variable = variableElement;
    }

    public VariableElement getVariable() {
        return variable;
    }

    public ExecutableElement getSetter() {
        return setter;
    }

    public boolean isSettable() {
        return isGenerated() && getSetter() != null && getGetter().getModifiers().contains(javax.lang.model.element.Modifier.ABSTRACT);
    }

    public void setSetter(ExecutableElement setter) {
        this.setter = setter;
    }

    public void setGetter(ExecutableElement getter) {
        this.getter = getter;
    }

    @Override
    public Element getMessageElement() {
        return messageElement;
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return messageAnnotation;
    }

    public String getName() {
        return variable.getSimpleName().toString();
    }

    public TypeMirror getType() {
        return variable.asType();
    }

    public boolean isGenerated() {
        return generated;
    }

    public ExecutableElement getGetter() {
        return getter;
    }

}
