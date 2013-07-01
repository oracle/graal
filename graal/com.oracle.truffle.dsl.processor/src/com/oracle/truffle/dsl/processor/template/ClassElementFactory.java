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
package com.oracle.truffle.dsl.processor.template;

import static com.oracle.truffle.dsl.processor.Utils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.ast.*;

public abstract class ClassElementFactory<M> extends CodeElementFactory<M> {

    public ClassElementFactory(ProcessorContext context) {
        super(context);
    }

    @Override
    protected abstract CodeTypeElement create(M m);

    @Override
    public CodeTypeElement getElement() {
        return (CodeTypeElement) super.getElement();
    }

    protected CodeExecutableElement createConstructorUsingFields(Set<Modifier> modifiers, CodeTypeElement clazz) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers, null, clazz.getSimpleName().toString());
        CodeTreeBuilder builder = method.createBuilder();
        TypeElement superClass = fromTypeMirror(clazz.getSuperclass());
        ExecutableElement constructor = findConstructor(superClass);
        if (constructor != null && constructor.getParameters().size() > 0) {
            builder.startStatement();
            builder.startSuperCall();
            for (VariableElement parameter : constructor.getParameters()) {
                method.addParameter(new CodeVariableElement(parameter.asType(), parameter.getSimpleName().toString()));
                builder.string(parameter.getSimpleName().toString());
            }
            builder.end(); // super
            builder.end(); // statement
        }

        for (VariableElement field : clazz.getFields()) {
            if (field.getModifiers().contains(STATIC)) {
                continue;
            }
            String fieldName = field.getSimpleName().toString();
            method.addParameter(new CodeVariableElement(field.asType(), fieldName));
            builder.startStatement();
            builder.string("this.");
            builder.string(fieldName);
            builder.string(" = ");
            if (isAssignable(getContext(), field.asType(), getContext().getTruffleTypes().getNode())) {
                builder.string("adoptChild(").string(fieldName).string(")");
            } else {
                builder.string(fieldName);
            }
            builder.end(); // statement
        }

        return method;
    }

    private static ExecutableElement findConstructor(TypeElement clazz) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(clazz.getEnclosedElements());
        if (constructors.isEmpty()) {
            return null;
        } else {
            return constructors.get(0);
        }
    }

    protected CodeExecutableElement createSuperConstructor(TypeElement type, ExecutableElement element) {
        if (element.getModifiers().contains(Modifier.PRIVATE)) {
            return null;
        }
        CodeExecutableElement executable = CodeExecutableElement.clone(getContext().getEnvironment(), element);
        executable.setReturnType(null);
        executable.setSimpleName(CodeNames.of(type.getSimpleName().toString()));
        CodeTreeBuilder b = executable.createBuilder();
        b.startStatement();
        b.startSuperCall();
        for (VariableElement v : element.getParameters()) {
            b.string(v.getSimpleName().toString());
        }
        b.end();
        b.end();

        return executable;
    }

    protected CodeTypeElement createClass(Template model, Set<Modifier> modifiers, String simpleName, TypeMirror superType, boolean enumType) {
        TypeElement templateType = model.getTemplateType();

        PackageElement pack = getContext().getEnvironment().getElementUtils().getPackageOf(templateType);
        CodeTypeElement clazz = new CodeTypeElement(modifiers, enumType ? ElementKind.ENUM : ElementKind.CLASS, pack, simpleName);
        TypeMirror resolvedSuperType = superType;
        if (resolvedSuperType == null) {
            resolvedSuperType = getContext().getType(Object.class);
        }
        clazz.setSuperClass(resolvedSuperType);

        CodeAnnotationMirror generatedByAnnotation = new CodeAnnotationMirror((DeclaredType) getContext().getType(GeneratedBy.class));
        generatedByAnnotation.setElementValue(generatedByAnnotation.findExecutableElement("value"), new CodeAnnotationValue(templateType.asType()));
        if (model.getTemplateMethodName() != null) {
            generatedByAnnotation.setElementValue(generatedByAnnotation.findExecutableElement("methodName"), new CodeAnnotationValue(model.getTemplateMethodName()));
        }

        clazz.addAnnotationMirror(generatedByAnnotation);

        context.registerType(model.getTemplateType(), clazz.asType());

        return clazz;
    }
}
