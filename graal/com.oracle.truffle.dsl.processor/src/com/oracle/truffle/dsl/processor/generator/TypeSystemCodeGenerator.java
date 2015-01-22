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
package com.oracle.truffle.dsl.processor.generator;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.type.*;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;

public class TypeSystemCodeGenerator extends CodeTypeElementFactory<TypeSystemData> {

    public static CodeTree cast(TypeData type, String content) {
        return cast(type, CodeTreeBuilder.singleString(content));
    }

    public static CodeTree implicitType(TypeData type, CodeTree value) {
        if (type.isGeneric()) {
            return value;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        TypeSystemData typeSystem = type.getTypeSystem();
        builder.startStaticCall(createTypeSystemGen(typeSystem), getImplicitClass(type)).tree(value);
        builder.end();
        return builder.build();
    }

    public static CodeTree invokeImplicitCast(ImplicitCastData cast, CodeTree expression) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        TypeSystemData typeSystem = cast.getTargetType().getTypeSystem();
        builder.startStaticCall(createTypeSystemGen(typeSystem), cast.getMethodName()).tree(expression);
        builder.end();
        return builder.build();
    }

    public static CodeTree implicitCheck(TypeData type, CodeTree value, String typeHint) {
        if (type.isGeneric()) {
            return value;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        TypeSystemData typeSystem = type.getTypeSystem();
        builder.startStaticCall(createTypeSystemGen(typeSystem), isImplicitTypeMethodName(type)).tree(value);
        if (typeHint != null) {
            builder.string(typeHint);
        }
        builder.end();
        return builder.build();
    }

    public static CodeTree implicitExpect(TypeData type, CodeTree value, String typeHint) {
        if (type.isGeneric()) {
            return value;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        TypeSystemData typeSystem = type.getTypeSystem();
        builder.startStaticCall(createTypeSystemGen(typeSystem), expectImplicitTypeMethodName(type)).tree(value);
        if (typeHint != null) {
            builder.string(typeHint);
        }
        builder.end();
        return builder.build();
    }

    public static CodeTree implicitCast(TypeData type, CodeTree value, String typeHint) {
        if (type.isGeneric()) {
            return value;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        TypeSystemData typeSystem = type.getTypeSystem();
        builder.startStaticCall(createTypeSystemGen(typeSystem), asImplicitTypeMethodName(type)).tree(value);
        if (typeHint != null) {
            builder.string(typeHint);
        }
        builder.end();
        return builder.build();
    }

    public static CodeTree cast(TypeData type, CodeTree content) {
        if (type.isGeneric()) {
            return content;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        TypeSystemData typeSystem = type.getTypeSystem();

        if (type.isDefaultCast()) {
            builder.cast(type.getPrimitiveType(), content);
        } else {
            builder.startStaticCall(typeSystem.getTemplateType().asType(), type.getTypeCasts().get(0).getMethodName()).tree(content).end();
        }
        return builder.build();
    }

    public static CodeTree expect(TypeData type, CodeTree content) {
        if (type.isGeneric() || type.isVoid()) {
            return content;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        TypeSystemData typeSystem = type.getTypeSystem();
        builder.startStaticCall(createTypeSystemGen(typeSystem), expectTypeMethodName(type)).tree(content).end();
        return builder.build();
    }

    public static CodeTree expect(TypeData sourceType, TypeData targetType, CodeTree content) {
        if (sourceType != null && !sourceType.needsCastTo(targetType)) {
            return content;
        } else {
            return expect(targetType, content);
        }
    }

    public static CodeTypeMirror createTypeSystemGen(TypeSystemData typeSystem) {
        return new GeneratedTypeMirror(ElementUtils.getPackageName(typeSystem.getTemplateType()), typeName(typeSystem));
    }

    public static CodeTree check(TypeData type, String content) {
        return check(type, CodeTreeBuilder.singleString(content));
    }

    public static CodeTree check(TypeData type, CodeTree content) {
        if (type.isGeneric()) {
            return content;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        TypeSystemData typeSystem = type.getTypeSystem();

        if (type.isDefaultCheck()) {
            builder.instanceOf(content, type.getBoxedType());
        } else {
            builder.startStaticCall(typeSystem.getTemplateType().asType(), type.getTypeChecks().get(0).getMethodName()).tree(content).end();
        }
        return builder.build();
    }

    public static String isTypeMethodName(TypeData type) {
        return "is" + ElementUtils.getTypeId(type.getBoxedType());
    }

    static String isImplicitTypeMethodName(TypeData type) {
        return "isImplicit" + ElementUtils.getTypeId(type.getBoxedType());
    }

    public static String asTypeMethodName(TypeData type) {
        return "as" + ElementUtils.getTypeId(type.getBoxedType());
    }

    static String asImplicitTypeMethodName(TypeData type) {
        return "asImplicit" + ElementUtils.getTypeId(type.getBoxedType());
    }

    static String expectImplicitTypeMethodName(TypeData type) {
        return "expectImplicit" + ElementUtils.getTypeId(type.getBoxedType());
    }

    static String getImplicitClass(TypeData type) {
        return "getImplicit" + ElementUtils.getTypeId(type.getBoxedType()) + "Class";
    }

    public static String expectTypeMethodName(TypeData type) {
        return "expect" + ElementUtils.getTypeId(type.getBoxedType());
    }

    static String typeName(TypeSystemData typeSystem) {
        String name = getSimpleName(typeSystem.getTemplateType());
        return name + "Gen";
    }

    static String singletonName(TypeSystemData type) {
        return createConstantName(getSimpleName(type.getTemplateType().asType()));
    }

    @Override
    public CodeTypeElement create(ProcessorContext context, TypeSystemData typeSystem) {
        CodeTypeElement clazz = new TypeClassFactory(context, typeSystem).create();

        clazz.add(new TypeSystemNodeFactory(context, typeSystem).create());

        if (typeSystem.getOptions().implicitCastOptimization().isMergeCasts()) {
            for (TypeData type : typeSystem.getTypes()) {
                List<TypeData> sourceTypes = typeSystem.lookupSourceTypes(type);
                if (sourceTypes.size() > 1) {
                    clazz.add(new ImplicitCastNodeFactory(context, type).create());
                }
            }
        }
        return clazz;
    }

    private static class TypeClassFactory {

        private static final String LOCAL_VALUE = "value";

        private final ProcessorContext context;
        private final TypeSystemData typeSystem;

        public TypeClassFactory(ProcessorContext context, TypeSystemData typeSystem) {
            this.context = context;
            this.typeSystem = typeSystem;
        }

        public CodeTypeElement create() {
            String name = typeName(typeSystem);
            CodeTypeElement clazz = GeneratorUtils.createClass(typeSystem, null, modifiers(PUBLIC, FINAL), name, typeSystem.getTemplateType().asType());

            clazz.add(GeneratorUtils.createConstructorUsingFields(modifiers(PROTECTED), clazz));
            CodeVariableElement singleton = createSingleton(clazz);
            clazz.add(singleton);

            for (TypeData type : typeSystem.getTypes()) {
                if (type.isVoid() || type.isGeneric()) {
                    continue;
                }

                clazz.addOptional(createIsTypeMethod(type));
                clazz.addOptional(createAsTypeMethod(type));

                for (TypeData sourceType : collectExpectSourceTypes(type)) {
                    clazz.addOptional(createExpectTypeMethod(type, sourceType));
                }

                if (type.hasImplicitSourceTypes()) {
                    clazz.add(createAsImplicitTypeMethod(type, false));
                    if (typeSystem.getOptions().implicitCastOptimization().isNone()) {
                        clazz.add(createExpectImplicitTypeMethod(type, false));
                    }
                    clazz.add(createIsImplicitTypeMethod(type, false));

                    if (typeSystem.getOptions().implicitCastOptimization().isDuplicateTail()) {
                        clazz.add(createAsImplicitTypeMethod(type, true));
                        clazz.add(createExpectImplicitTypeMethod(type, true));
                        clazz.add(createIsImplicitTypeMethod(type, true));
                        clazz.add(createGetImplicitClass(type));
                    }
                }
            }

            return clazz;
        }

        private static List<TypeData> collectExpectSourceTypes(TypeData type) {
            Set<TypeData> sourceTypes = new HashSet<>();
            sourceTypes.add(type.getTypeSystem().getGenericTypeData());
            for (TypeCastData cast : type.getTypeCasts()) {
                sourceTypes.add(cast.getSourceType());
            }
            for (TypeCheckData cast : type.getTypeChecks()) {
                sourceTypes.add(cast.getCheckedType());
            }
            sourceTypes.remove(type);
            return new ArrayList<>(sourceTypes);
        }

        private CodeVariableElement createSingleton(CodeTypeElement clazz) {
            CodeVariableElement field = new CodeVariableElement(modifiers(PUBLIC, STATIC, FINAL), clazz.asType(), singletonName(typeSystem));
            field.createInitBuilder().startNew(clazz.asType()).end();

            CodeAnnotationMirror annotationMirror = new CodeAnnotationMirror((DeclaredType) context.getType(Deprecated.class));
            field.getAnnotationMirrors().add(annotationMirror);

            return field;
        }

        private CodeExecutableElement createIsImplicitTypeMethod(TypeData type, boolean typed) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), context.getType(boolean.class), TypeSystemCodeGenerator.isImplicitTypeMethodName(type));
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));
            if (typed) {
                method.addParameter(new CodeVariableElement(context.getType(Class.class), "typeHint"));
            }
            CodeTreeBuilder builder = method.createBuilder();

            List<TypeData> sourceTypes = typeSystem.lookupSourceTypes(type);

            builder.startReturn();
            String sep = "";
            for (TypeData sourceType : sourceTypes) {
                builder.string(sep);
                if (typed) {
                    builder.string("(typeHint == ").typeLiteral(sourceType.getPrimitiveType()).string(" && ");
                }
                builder.tree(check(sourceType, LOCAL_VALUE));
                if (typed) {
                    builder.string(")");
                }
                if (sourceTypes.lastIndexOf(sourceType) != sourceTypes.size() - 1) {
                    builder.newLine();
                }
                if (sep.equals("")) {
                    builder.startIndention();
                }
                sep = " || ";
            }
            builder.end();
            builder.end();
            return method;
        }

        private CodeExecutableElement createAsImplicitTypeMethod(TypeData type, boolean useTypeHint) {
            String name = asImplicitTypeMethodName(type);
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), type.getPrimitiveType(), name);
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));
            if (useTypeHint) {
                method.addParameter(new CodeVariableElement(context.getType(Class.class), "typeHint"));
            }

            List<TypeData> sourceTypes = typeSystem.lookupSourceTypes(type);

            CodeTreeBuilder builder = method.createBuilder();
            boolean elseIf = false;
            for (TypeData sourceType : sourceTypes) {
                elseIf = builder.startIf(elseIf);
                if (useTypeHint) {
                    builder.string("typeHint == ").typeLiteral(sourceType.getPrimitiveType());
                } else {
                    builder.tree(check(sourceType, LOCAL_VALUE));
                }

                builder.end().startBlock();

                builder.startReturn();
                ImplicitCastData cast = typeSystem.lookupCast(sourceType, type);
                if (cast != null) {
                    builder.startCall(cast.getMethodName());
                }
                builder.tree(cast(sourceType, LOCAL_VALUE)).end();
                if (cast != null) {
                    builder.end();
                }
                builder.end();
                builder.end();
            }

            builder.startElseBlock();
            builder.tree(createTransferToInterpreterAndInvalidate());
            builder.startThrow().startNew(context.getType(IllegalArgumentException.class)).doubleQuote("Illegal type ").end().end();
            builder.end();
            return method;
        }

        private CodeExecutableElement createExpectImplicitTypeMethod(TypeData type, boolean useTypeHint) {
            String name = expectImplicitTypeMethodName(type);
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), type.getPrimitiveType(), name);
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));
            if (useTypeHint) {
                method.addParameter(new CodeVariableElement(context.getType(Class.class), "typeHint"));
            }
            method.getThrownTypes().add(context.getType(UnexpectedResultException.class));

            List<TypeData> sourceTypes = typeSystem.lookupSourceTypes(type);

            CodeTreeBuilder builder = method.createBuilder();
            boolean elseIf = false;
            for (TypeData sourceType : sourceTypes) {
                elseIf = builder.startIf(elseIf);
                if (useTypeHint) {
                    builder.string("typeHint == ").typeLiteral(sourceType.getPrimitiveType());
                    builder.string(" && ");
                }
                builder.tree(check(sourceType, LOCAL_VALUE));

                builder.end().startBlock();

                builder.startReturn();
                ImplicitCastData cast = typeSystem.lookupCast(sourceType, type);
                if (cast != null) {
                    builder.startCall(cast.getMethodName());
                }
                builder.tree(cast(sourceType, LOCAL_VALUE)).end();
                if (cast != null) {
                    builder.end();
                }
                builder.end();
                builder.end();
            }

            builder.startElseBlock();
            builder.startThrow().startNew(context.getType(UnexpectedResultException.class)).string(LOCAL_VALUE).end().end();
            builder.end();
            return method;
        }

        private CodeExecutableElement createGetImplicitClass(TypeData type) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), context.getType(Class.class), TypeSystemCodeGenerator.getImplicitClass(type));
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));

            List<TypeData> sourceTypes = typeSystem.lookupSourceTypes(type);
            CodeTreeBuilder builder = method.createBuilder();
            boolean elseIf = false;
            for (TypeData sourceType : sourceTypes) {
                elseIf = builder.startIf(elseIf);
                builder.tree(check(sourceType, LOCAL_VALUE)).end();
                builder.end().startBlock();
                builder.startReturn().typeLiteral(sourceType.getPrimitiveType()).end();
                builder.end();
            }

            builder.startElseBlock();
            builder.tree(createTransferToInterpreterAndInvalidate());
            builder.startThrow().startNew(context.getType(IllegalArgumentException.class)).doubleQuote("Illegal type ").end().end();
            builder.end();

            return method;
        }

        private CodeExecutableElement createIsTypeMethod(TypeData type) {
            if (!type.getTypeChecks().isEmpty()) {
                return null;
            }

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), context.getType(boolean.class), TypeSystemCodeGenerator.isTypeMethodName(type));
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));

            CodeTreeBuilder body = method.createBuilder();
            body.startReturn().tree(check(type, LOCAL_VALUE)).end();

            return method;
        }

        private CodeExecutableElement createAsTypeMethod(TypeData type) {
            if (!type.getTypeCasts().isEmpty()) {
                return null;
            }

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), type.getPrimitiveType(), TypeSystemCodeGenerator.asTypeMethodName(type));
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));

            CodeTreeBuilder body = method.createBuilder();
            String assertMessage = typeName(typeSystem) + "." + asTypeMethodName(type) + ": " + ElementUtils.getSimpleName(type.getBoxedType()) + " expected";
            body.startAssert().tree(check(type, LOCAL_VALUE)).string(" : ").doubleQuote(assertMessage).end();
            body.startReturn().tree(cast(type, LOCAL_VALUE)).end();

            return method;
        }

        private CodeExecutableElement createExpectTypeMethod(TypeData expectedType, TypeData sourceType) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), expectedType.getPrimitiveType(), TypeSystemCodeGenerator.expectTypeMethodName(expectedType));
            method.addParameter(new CodeVariableElement(sourceType.getPrimitiveType(), LOCAL_VALUE));
            method.addThrownType(context.getTruffleTypes().getUnexpectedValueException());

            CodeTreeBuilder body = method.createBuilder();
            body.startIf().tree(check(expectedType, LOCAL_VALUE)).end().startBlock();
            body.startReturn().tree(cast(expectedType, LOCAL_VALUE)).end().end();
            body.end(); // if-block
            body.startThrow().startNew(context.getTruffleTypes().getUnexpectedValueException()).string(LOCAL_VALUE).end().end();

            return method;
        }

    }

}
