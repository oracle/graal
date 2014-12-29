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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.*;

public class ImplicitCastNodeFactory {

    private final ProcessorContext context;
    private final TypeData forType;
    private final TypeSystemData typeSystem;
    private final DSLOptions options;
    private final List<TypeData> sourceTypes;

    public ImplicitCastNodeFactory(ProcessorContext context, TypeData forType) {
        this.context = context;
        this.forType = forType;
        this.typeSystem = forType.getTypeSystem();
        this.options = typeSystem.getOptions();
        this.sourceTypes = typeSystem.lookupSourceTypes(forType);
    }

    public static String typeName(TypeData type) {
        return "Implicit" + getTypeId(type.getBoxedType()) + "Cast";
    }

    public static TypeMirror type(TypeData type) {
        TypeSystemData typeSystem = type.getTypeSystem();
        String typeSystemName = TypeSystemCodeGenerator.typeName(typeSystem);
        return new GeneratedTypeMirror(ElementUtils.getPackageName(typeSystem.getTemplateType()) + "." + typeSystemName, typeName(type));
    }

    public static CodeTree create(TypeData type, CodeTree value) {
        return CodeTreeBuilder.createBuilder().startStaticCall(type(type), "create").tree(value).end().build();
    }

    public static CodeTree cast(String nodeName, CodeTree value) {
        return CodeTreeBuilder.createBuilder().startCall(nodeName, "cast").tree(value).end().build();
    }

    public static CodeTree check(String nodeName, CodeTree value) {
        return CodeTreeBuilder.createBuilder().startCall(nodeName, "check").tree(value).end().build();
    }

    private static String seenFieldName(TypeData type) {
        return "seen" + getTypeId(type.getBoxedType());
    }

    public CodeTypeElement create() {
        String typeName = typeName(forType);
        TypeMirror baseType = context.getType(Object.class);
        CodeTypeElement clazz = GeneratorUtils.createClass(typeSystem, null, modifiers(PUBLIC, FINAL, STATIC), typeName, baseType);

        for (TypeData sourceType : sourceTypes) {
            CodeVariableElement hasSeen = new CodeVariableElement(modifiers(PUBLIC), context.getType(boolean.class), seenFieldName(sourceType));
            hasSeen.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(CompilationFinal.class)));
            clazz.add(hasSeen);
        }

        clazz.add(createConstructor(clazz));
        if (isTypeBoxingOptimized(options.monomorphicTypeBoxingOptimization(), forType)) {
            clazz.add(createIsMonomorphic());
        }
        clazz.add(createCast(false));
        clazz.add(createCast(true));
        clazz.add(createCheck());
        clazz.add(createMerge(clazz));
        clazz.add(createCreate(clazz));

        return clazz;
    }

    private Element createIsMonomorphic() {
        String methodName = "isMonomorphic";
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), context.getType(boolean.class), methodName);
        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();
        String operator = "";
        for (TypeData sourceType : sourceTypes) {
            builder.string(operator);
            builder.string(seenFieldName(sourceType));
            operator = " ^ ";
        }
        builder.end();
        return method;
    }

    private static Element createConstructor(CodeTypeElement clazz) {
        return new CodeExecutableElement(modifiers(PRIVATE), null, clazz.getSimpleName().toString());
    }

    private Element createCreate(CodeTypeElement clazz) {
        String methodName = "create";
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), clazz.asType(), methodName);
        method.addParameter(new CodeVariableElement(typeSystem.getGenericType(), "value"));
        CodeTreeBuilder builder = method.createBuilder();

        builder.declaration(clazz.asType(), "newCast", builder.create().startNew(clazz.asType()).end());

        for (TypeData sourceType : sourceTypes) {
            String seenField = seenFieldName(sourceType);
            builder.startStatement();
            builder.string("newCast.").string(seenField).string(" = ").tree(TypeSystemCodeGenerator.check(sourceType, "value"));
            builder.end();
        }
        builder.startReturn().string("newCast").end();
        return method;
    }

    private Element createMerge(CodeTypeElement clazz) {
        String methodName = "merge";
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), context.getType(void.class), methodName);
        method.addParameter(new CodeVariableElement(clazz.asType(), "otherCast"));
        CodeTreeBuilder builder = method.createBuilder();

        for (TypeData sourceType : sourceTypes) {
            String seenField = seenFieldName(sourceType);
            builder.startStatement();
            builder.string("this.").string(seenField).string(" |= ").string("otherCast.").string(seenField);
            builder.end();
        }
        return method;
    }

    private Element createCheck() {
        String methodName = "check";
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), context.getType(boolean.class), methodName);
        method.addParameter(new CodeVariableElement(typeSystem.getGenericType(), "value"));
        CodeTreeBuilder builder = method.createBuilder();

        boolean elseIf = false;
        for (TypeData sourceType : sourceTypes) {
            elseIf = builder.startIf(elseIf);
            builder.string(seenFieldName(sourceType)).string(" && ").tree(TypeSystemCodeGenerator.check(sourceType, "value"));
            builder.end();
            builder.startBlock().returnTrue().end();
        }
        builder.returnFalse();
        return method;
    }

    private Element createCast(boolean expect) {
        String methodName = expect ? "expect" : "cast";
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), forType.getPrimitiveType(), methodName);
        method.addParameter(new CodeVariableElement(typeSystem.getGenericType(), "value"));
        if (expect) {
            method.getThrownTypes().add(context.getType(UnexpectedResultException.class));
        }

        CodeTreeBuilder builder = method.createBuilder();

        boolean elseIf = false;
        for (TypeData sourceType : sourceTypes) {
            elseIf = builder.startIf(elseIf);
            builder.string(seenFieldName(sourceType)).string(" && ").tree(TypeSystemCodeGenerator.check(sourceType, "value"));
            builder.end();
            builder.startBlock();
            builder.startReturn();
            CodeTree castTree = TypeSystemCodeGenerator.cast(sourceType, "value");
            ImplicitCastData cast = typeSystem.lookupCast(sourceType, forType);
            if (cast != null) {
                builder.tree(TypeSystemCodeGenerator.invokeImplicitCast(cast, castTree));
            } else {
                builder.tree(castTree);
            }
            builder.end();
            builder.end();
        }
        if (expect) {
            builder.startThrow().startNew(context.getType(UnexpectedResultException.class)).string("value").end().end();
        } else {
            builder.startStatement().startStaticCall(context.getType(CompilerDirectives.class), "transferToInterpreter").end().end();
            builder.startThrow().startNew(context.getType(AssertionError.class)).end().end();
        }
        return method;
    }

}
