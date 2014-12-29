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

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;

public class TypeSystemNodeFactory {

    private final ProcessorContext context;
    private final TypeSystemData typeSystem;
    private final DSLOptions options;

    public TypeSystemNodeFactory(ProcessorContext context, TypeSystemData typeSystem) {
        this.context = context;
        this.typeSystem = typeSystem;
        this.options = typeSystem.getOptions();
    }

    public static TypeMirror nodeType(TypeSystemData typeSystem) {
        TypeMirror parentType = TypeSystemCodeGenerator.createTypeSystemGen(typeSystem);
        return new GeneratedTypeMirror(getQualifiedName(parentType), typeName(typeSystem));
    }

    public static String typeName(TypeSystemData typeSystem) {
        return getTypeId(typeSystem.getTemplateType().asType()) + "Node";
    }

    public static String acceptAndExecuteName() {
        return "acceptAndExecute";
    }

    public static String executeName(TypeData type) {
        if (type == null) {
            return acceptAndExecuteName();
        } else if (type.isGeneric()) {
            return "executeGeneric";
        } else {
            return "execute" + getTypeId(type.getBoxedType());
        }
    }

    public static String voidBoxingExecuteName(TypeData type) {
        return executeName(type) + "Void";
    }

    public CodeTypeElement create() {
        String typeName = typeName(typeSystem);
        TypeMirror baseType = context.getType(SpecializationNode.class);
        CodeTypeElement clazz = GeneratorUtils.createClass(typeSystem, null, modifiers(PUBLIC, ABSTRACT, STATIC), typeName, baseType);

        for (ExecutableElement constructor : ElementFilter.constructorsIn(ElementUtils.fromTypeMirror(baseType).getEnclosedElements())) {
            clazz.add(GeneratorUtils.createSuperConstructor(context, clazz, constructor));
        }

        for (TypeData type : typeSystem.getTypes()) {
            clazz.add(createExecuteMethod(type));
            if (GeneratorUtils.isTypeBoxingOptimized(options.voidBoxingOptimization(), type)) {
                clazz.add(createVoidBoxingExecuteMethod(type));
            }
        }
        return clazz;
    }

    private Element createVoidBoxingExecuteMethod(TypeData type) {
        TypeData voidType = typeSystem.getVoidType();
        String methodName = voidBoxingExecuteName(type);
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), voidType.getPrimitiveType(), methodName);
        method.addParameter(new CodeVariableElement(context.getType(VirtualFrame.class), "frame"));

        CodeTreeBuilder builder = method.createBuilder();
        builder.startTryBlock();
        builder.startStatement().startCall(executeName(type)).string("frame").end().end();
        builder.end();
        builder.startCatchBlock(context.getType(UnexpectedResultException.class), "e");
        builder.end();

        return method;
    }

    private Element createExecuteMethod(TypeData type) {
        TypeData genericType = typeSystem.getGenericTypeData();
        String methodName = executeName(type);
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), type.getPrimitiveType(), methodName);
        method.addParameter(new CodeVariableElement(context.getType(VirtualFrame.class), "frame"));

        if (type.isGeneric()) {
            method.getModifiers().add(ABSTRACT);
        } else {
            CodeTreeBuilder builder = method.createBuilder();
            CodeTree executeGeneric = builder.create().startCall(executeName(genericType)).string("frame").end().build();
            if (!type.isVoid()) {
                method.getThrownTypes().add(context.getType(UnexpectedResultException.class));
            }
            builder.startReturn().tree(TypeSystemCodeGenerator.expect(type, executeGeneric)).end();
        }

        return method;
    }
}
