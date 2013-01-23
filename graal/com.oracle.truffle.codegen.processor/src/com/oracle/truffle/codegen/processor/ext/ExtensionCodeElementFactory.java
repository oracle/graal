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
package com.oracle.truffle.codegen.processor.ext;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.api.element.*;
import com.oracle.truffle.codegen.processor.ast.*;

final class ExtensionCodeElementFactory implements WritableElementFactory {

    private final ProcessorContext context;

    Element generatorElement;
    AnnotationMirror generatorAnnotationMirror;

    public ExtensionCodeElementFactory(ProcessorContext context) {
        this.context = context;
    }

    @Override
    public WritableExecutableElement cloneExecutableElement(ExecutableElement method) {
        return updateGenerators(CodeExecutableElement.clone(context.getEnvironment(), method));
    }

    @Override
    public WritableVariableElement cloneVariableElement(VariableElement var) {
        return updateGenerators(CodeVariableElement.clone(var));
    }

    @Override
    public WritableAnnotationMirror cloneAnnotationMirror(AnnotationMirror mirror) {
        return CodeAnnotationMirror.clone(mirror);
    }

    @Override
    public WritableVariableElement createParameter(TypeMirror type, String simpleName) {
        return updateGenerators(new CodeVariableElement(Utils.modifiers(), type, simpleName));
    }

    @Override
    public WritableExecutableElement createExecutableElement(TypeMirror returnType, String methodName) {
        return updateGenerators(new CodeExecutableElement(returnType, methodName));
    }

    @Override
    public TypeMirror createTypeMirror(Class<?> javaClass) {
        return context.getType(javaClass);
    }

    @Override
    public WritableAnnotationMirror createAnnotationMirror(DeclaredType typeMirror) {
        return new CodeAnnotationMirror(typeMirror);
    }

    @Override
    public Name createName(String name) {
        return CodeNames.of(name);
    }

    @Override
    public AnnotationValue createAnnotationValue(Object value) {
        return new CodeAnnotationValue(value);
    }

    private <E extends GeneratedElement> E updateGenerators(E element) {
        element.setGeneratorElement(generatorElement);
        element.setGeneratorAnnotationMirror(generatorAnnotationMirror);
        return element;
    }

}
