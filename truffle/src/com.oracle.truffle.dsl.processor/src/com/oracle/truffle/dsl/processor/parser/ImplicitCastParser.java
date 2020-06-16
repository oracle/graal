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

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.ImplicitCastData;
import com.oracle.truffle.dsl.processor.model.MethodSpec;
import com.oracle.truffle.dsl.processor.model.Parameter;
import com.oracle.truffle.dsl.processor.model.ParameterSpec;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;

public class ImplicitCastParser extends TypeSystemMethodParser<ImplicitCastData> {

    public ImplicitCastParser(ProcessorContext context, TypeSystemData typeSystem) {
        super(context, typeSystem);
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.ImplicitCast;
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        MethodSpec spec = new MethodSpec(new ParameterSpec("target", getContext().getType(Object.class)));
        spec.addRequired(new ParameterSpec("source", getContext().getType(Object.class))).setSignature(true);
        return spec;
    }

    @Override
    public ImplicitCastData create(TemplateMethod method, boolean invalid) {
        if (invalid) {
            return new ImplicitCastData(method, null, null);
        }

        Parameter target = method.findParameter("targetValue");
        Parameter source = method.findParameter("sourceValue");

        TypeMirror targetType = target.getType();
        TypeMirror sourceType = source.getType();

        if (ElementUtils.typeEquals(targetType, sourceType)) {
            method.addError("Target type and source type of an @%s must not be the same type.", types.ImplicitCast.asElement().getSimpleName().toString());
        }

        if (!method.getMethod().getModifiers().contains(Modifier.STATIC)) {
            method.addError("@%s annotated method %s must be static.", types.ImplicitCast.asElement().getSimpleName().toString(), method.getMethodName());
        }

        return new ImplicitCastData(method, sourceType, targetType);
    }
}
