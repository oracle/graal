/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.generator;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.internal.DSLOptions.TypeBoxingOptimization;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;

public class GeneratorUtils {

    public static CodeTree createTransferToInterpreterAndInvalidate() {
        ProcessorContext context = ProcessorContext.getInstance();
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startStatement().startStaticCall(context.getType(CompilerDirectives.class), "transferToInterpreterAndInvalidate").end().end();
        return builder.build();
    }

    public static CodeExecutableElement createConstructorUsingFields(Set<Modifier> modifiers, CodeTypeElement clazz) {
        TypeElement superClass = fromTypeMirror(clazz.getSuperclass());
        ExecutableElement constructor = findConstructor(superClass);
        return createConstructorUsingFields(modifiers, clazz, constructor);
    }

    public static CodeExecutableElement createConstructorUsingFields(Set<Modifier> modifiers, CodeTypeElement clazz, ExecutableElement constructor) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers, null, clazz.getSimpleName().toString());
        CodeTreeBuilder builder = method.createBuilder();
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
            builder.string(fieldName);
            builder.end(); // statement
        }

        return method;
    }

    public static boolean isTypeBoxingOptimized(TypeBoxingOptimization boxing, TypeMirror type) {
        switch (boxing) {
            case NONE:
                return false;
            case ALWAYS:
                return !ElementUtils.isObject(type) && !ElementUtils.isVoid(type);
            case PRIMITIVE:
                return ElementUtils.isPrimitive(type);
            default:
                throw new AssertionError();
        }
    }

    private static ExecutableElement findConstructor(TypeElement clazz) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(clazz.getEnclosedElements());
        if (constructors.isEmpty()) {
            return null;
        } else {
            return constructors.get(0);
        }
    }

    public static CodeExecutableElement createSuperConstructor(ProcessorContext context, TypeElement type, ExecutableElement element) {
        if (element.getModifiers().contains(Modifier.PRIVATE)) {
            return null;
        }
        CodeExecutableElement executable = CodeExecutableElement.clone(context.getEnvironment(), element);
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

    public static CodeTypeElement createClass(Template sourceModel, TemplateMethod sourceMethod, Set<Modifier> modifiers, String simpleName, TypeMirror superType) {
        TypeElement templateType = sourceModel.getTemplateType();

        ProcessorContext context = ProcessorContext.getInstance();

        PackageElement pack = context.getEnvironment().getElementUtils().getPackageOf(templateType);
        CodeTypeElement clazz = new CodeTypeElement(modifiers, ElementKind.CLASS, pack, simpleName);
        TypeMirror resolvedSuperType = superType;
        if (resolvedSuperType == null) {
            resolvedSuperType = context.getType(Object.class);
        }
        clazz.setSuperClass(resolvedSuperType);

        CodeAnnotationMirror generatedByAnnotation = new CodeAnnotationMirror((DeclaredType) context.getType(GeneratedBy.class));
        generatedByAnnotation.setElementValue(generatedByAnnotation.findExecutableElement("value"), new CodeAnnotationValue(templateType.asType()));
        if (sourceMethod != null && sourceMethod.getMethod() != null) {
            generatedByAnnotation.setElementValue(generatedByAnnotation.findExecutableElement("methodName"), new CodeAnnotationValue(sourceMethod.createReferenceName()));
        }

        clazz.addAnnotationMirror(generatedByAnnotation);
        return clazz;
    }

    public static List<ExecutableElement> findUserConstructors(TypeMirror nodeType) {
        List<ExecutableElement> constructors = new ArrayList<>();
        for (ExecutableElement constructor : ElementFilter.constructorsIn(ElementUtils.fromTypeMirror(nodeType).getEnclosedElements())) {
            if (constructor.getModifiers().contains(PRIVATE)) {
                continue;
            }
            if (isCopyConstructor(constructor)) {
                continue;
            }
            constructors.add(constructor);
        }

        if (constructors.isEmpty()) {
            constructors.add(new CodeExecutableElement(null, ElementUtils.getSimpleName(nodeType)));
        }

        return constructors;
    }

    public static boolean isCopyConstructor(ExecutableElement element) {
        if (element.getParameters().size() != 1) {
            return false;
        }
        VariableElement var = element.getParameters().get(0);
        TypeElement enclosingType = ElementUtils.findNearestEnclosingType(var);
        if (ElementUtils.typeEquals(var.asType(), enclosingType.asType())) {
            return true;
        }
        List<TypeElement> types = ElementUtils.getDirectSuperTypes(enclosingType);
        for (TypeElement type : types) {
            if (!(type instanceof CodeTypeElement)) {
                // no copy constructors which are not generated types
                return false;
            }

            if (ElementUtils.typeEquals(var.asType(), type.asType())) {
                return true;
            }
        }
        return false;
    }

}
