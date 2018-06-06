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
package com.oracle.truffle.dsl.processor.generator;

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createTransferToInterpreterAndInvalidate;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.createConstantName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.List;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.model.ImplicitCastData;
import com.oracle.truffle.dsl.processor.model.TypeCastData;
import com.oracle.truffle.dsl.processor.model.TypeCheckData;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;

public class TypeSystemCodeGenerator extends CodeTypeElementFactory<TypeSystemData> {

    private static final String LOCAL_VALUE = "value";

    static CodeTree implicitCastFlat(TypeSystemData typeSystem, TypeMirror type, CodeTree value, CodeTree state) {
        return callImplictMethodFlat(typeSystem, type, asImplicitTypeMethodName(typeSystem, type), value, state);
    }

    static CodeTree implicitCheckFlat(TypeSystemData typeSystem, TypeMirror type, CodeTree value, CodeTree state) {
        return callImplictMethodFlat(typeSystem, type, isImplicitTypeMethodName(typeSystem, type), value, state);
    }

    static CodeTree implicitSpecializeFlat(TypeSystemData typeSystem, TypeMirror type, CodeTree value) {
        return callImplictMethodFlat(typeSystem, type, specializeImplicitTypeMethodName(typeSystem, type), value, null);
    }

    private static CodeTree callImplictMethodFlat(TypeSystemData typeSystem, TypeMirror type, String methodName, CodeTree value, CodeTree state) {
        if (ElementUtils.isObject(type)) {
            return value;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startStaticCall(createTypeSystemGen(typeSystem), methodName);
        if (state != null) {
            builder.tree(state);
        }
        builder.tree(value);
        builder.end();
        return builder.build();
    }

    static CodeTree implicitExpectFlat(TypeSystemData typeSystem, TypeMirror type, CodeTree value, CodeTree state) {
        return callImplictMethodFlat(typeSystem, type, expectImplicitTypeMethodName(typeSystem, type), value, state);
    }

    static CodeTree cast(TypeSystemData typeSystem, TypeMirror type, String content) {
        return cast(typeSystem, type, CodeTreeBuilder.singleString(content));
    }

    static CodeTree invokeImplicitCast(TypeSystemData typeSystem, ImplicitCastData cast, CodeTree expression) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startStaticCall(createTypeSystemGen(typeSystem), cast.getMethodName()).tree(expression);
        builder.end();
        return builder.build();
    }

    static CodeTree cast(TypeSystemData typeSystem, TypeMirror type, CodeTree content) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        TypeCastData cast = typeSystem.getCast(type);
        if (cast == null) {
            builder.cast(ElementUtils.fillInGenericWildcards(type), content);
        } else {
            builder.startStaticCall(typeSystem.getTemplateType().asType(), cast.getMethodName()).tree(content).end();
        }
        return builder.build();
    }

    static CodeTree expect(TypeSystemData typeSystem, TypeMirror type, CodeTree content) {
        if (ElementUtils.isObject(type) || ElementUtils.isVoid(type)) {
            return content;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        if (typeSystem.hasType(type)) {
            builder.startStaticCall(createTypeSystemGen(typeSystem), expectTypeMethodName(typeSystem, type)).tree(content).end();
        } else {
            builder.startCall(expectTypeMethodName(typeSystem, type)).tree(content).end();
        }

        return builder.build();
    }

    static CodeExecutableElement createExpectMethod(Modifier visibility, TypeSystemData typeSystem, TypeMirror sourceTypeOriginal, TypeMirror expectedTypeOriginal) {
        TypeMirror expectedType = ElementUtils.fillInGenericWildcards(expectedTypeOriginal);
        TypeMirror sourceType = ElementUtils.fillInGenericWildcards(sourceTypeOriginal);
        if (ElementUtils.isObject(expectedType) || ElementUtils.isVoid(expectedType)) {
            return null;
        }

        CodeExecutableElement method = new CodeExecutableElement(modifiers(STATIC), expectedType, TypeSystemCodeGenerator.expectTypeMethodName(typeSystem, expectedType));
        method.setVisibility(visibility);
        method.addParameter(new CodeVariableElement(sourceType, LOCAL_VALUE));
        method.addThrownType(typeSystem.getContext().getTruffleTypes().getUnexpectedValueException());

        CodeTreeBuilder body = method.createBuilder();
        body.startIf().tree(check(typeSystem, expectedType, LOCAL_VALUE)).end().startBlock();
        body.startReturn().tree(cast(typeSystem, expectedType, LOCAL_VALUE)).end();
        body.end();
        body.startThrow().startNew(typeSystem.getContext().getTruffleTypes().getUnexpectedValueException()).string(LOCAL_VALUE).end().end();
        return method;
    }

    static CodeTypeMirror createTypeSystemGen(TypeSystemData typeSystem) {
        return new GeneratedTypeMirror(ElementUtils.getPackageName(typeSystem.getTemplateType()), typeName(typeSystem));
    }

    static CodeTree check(TypeSystemData typeSystem, TypeMirror type, String content) {
        return check(typeSystem, type, CodeTreeBuilder.singleString(content));
    }

    static CodeTree check(TypeSystemData typeSystem, TypeMirror type, CodeTree content) {
        if (ElementUtils.isObject(type)) {
            return content;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        TypeCheckData check = typeSystem.getCheck(type);
        if (check == null) {
            builder.instanceOf(content, ElementUtils.boxType(typeSystem.getContext(), type));
        } else {
            builder.startStaticCall(typeSystem.getTemplateType().asType(), check.getMethodName()).tree(content).end();
        }
        return builder.build();
    }

    static String isTypeMethodName(TypeSystemData typeSystem, TypeMirror type) {
        return "is" + getTypeId(typeSystem, type);
    }

    private static String getTypeId(TypeSystemData typeSystem, TypeMirror type) {
        return ElementUtils.getTypeId(typeSystem.boxType(type));
    }

    static String isImplicitTypeMethodName(TypeSystemData typeSystem, TypeMirror type) {
        return "isImplicit" + getTypeId(typeSystem, type);
    }

    static String asTypeMethodName(TypeSystemData typeSystem, TypeMirror type) {
        return "as" + getTypeId(typeSystem, type);
    }

    static String asImplicitTypeMethodName(TypeSystemData typeSystem, TypeMirror type) {
        return "asImplicit" + getTypeId(typeSystem, type);
    }

    static String specializeImplicitTypeMethodName(TypeSystemData typeSystem, TypeMirror type) {
        return "specializeImplicit" + getTypeId(typeSystem, type);
    }

    static String expectImplicitTypeMethodName(TypeSystemData typeSystem, TypeMirror type) {
        return "expectImplicit" + getTypeId(typeSystem, type);
    }

    static String getImplicitClass(TypeSystemData typeSystem, TypeMirror type) {
        return "getImplicit" + getTypeId(typeSystem, type) + "Class";
    }

    static String expectTypeMethodName(TypeSystemData typeSystem, TypeMirror type) {
        return "expect" + getTypeId(typeSystem, type);
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

        return clazz;
    }

    private static class TypeClassFactory {

        private final ProcessorContext context;
        private final TypeSystemData typeSystem;

        TypeClassFactory(ProcessorContext context, TypeSystemData typeSystem) {
            this.context = context;
            this.typeSystem = typeSystem;
        }

        public CodeTypeElement create() {
            String name = typeName(typeSystem);
            CodeTypeElement clazz = GeneratorUtils.createClass(typeSystem, null, modifiers(PUBLIC, FINAL), name, typeSystem.getTemplateType().asType());

            clazz.add(GeneratorUtils.createConstructorUsingFields(modifiers(PROTECTED), clazz));
            CodeVariableElement singleton = createSingleton(clazz);
            clazz.add(singleton);

            for (TypeMirror type : typeSystem.getLegacyTypes()) {
                if (ElementUtils.isVoid(type) || ElementUtils.isObject(type)) {
                    continue;
                }

                clazz.addOptional(createIsTypeMethod(type));
                clazz.addOptional(createAsTypeMethod(type));
                clazz.addOptional(createExpectTypeMethod(type, context.getType(Object.class)));

            }

            List<TypeMirror> lookupTargetTypes = typeSystem.lookupTargetTypes();
            for (TypeMirror type : lookupTargetTypes) {
                clazz.add(createExpectImplicitTypeMethodFlat(type));
                clazz.add(createIsImplicitTypeMethodFlat(type));
                clazz.add(createAsImplicitTypeMethodFlat(type));
                clazz.add(createSpecializeImplictTypeMethodFlat(type));
            }
            return clazz;
        }

        private CodeVariableElement createSingleton(CodeTypeElement clazz) {
            CodeVariableElement field = new CodeVariableElement(modifiers(PUBLIC, STATIC, FINAL), clazz.asType(), singletonName(typeSystem));
            field.createInitBuilder().startNew(clazz.asType()).end();

            CodeAnnotationMirror annotationMirror = new CodeAnnotationMirror((DeclaredType) context.getType(Deprecated.class));
            field.getAnnotationMirrors().add(annotationMirror);

            return field;
        }

        private CodeExecutableElement createSpecializeImplictTypeMethodFlat(TypeMirror type) {
            String name = specializeImplicitTypeMethodName(typeSystem, type);
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), context.getType(int.class), name);
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));

            List<TypeMirror> sourceTypes = typeSystem.lookupSourceTypes(type);

            CodeTreeBuilder builder = method.createBuilder();
            boolean elseIf = false;
            int mask = 1;
            for (TypeMirror sourceType : sourceTypes) {
                elseIf = builder.startIf(elseIf);
                builder.tree(check(typeSystem, sourceType, LOCAL_VALUE));
                builder.end().startBlock();
                builder.startReturn();
                builder.string("0b", Integer.toBinaryString(mask));
                builder.end();
                builder.end();
                mask = mask << 1;
            }

            builder.startElseBlock();
            builder.startReturn().string("0").end();
            builder.end();
            return method;
        }

        private CodeExecutableElement createExpectImplicitTypeMethodFlat(TypeMirror type) {
            String name = expectImplicitTypeMethodName(typeSystem, type);
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), type, name);
            method.addParameter(new CodeVariableElement(context.getType(int.class), "state"));
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));
            method.getThrownTypes().add(context.getType(UnexpectedResultException.class));
            List<TypeMirror> sourceTypes = typeSystem.lookupSourceTypes(type);

            CodeTreeBuilder builder = method.createBuilder();
            boolean elseIf = false;

            int mask = 1;
            for (TypeMirror sourceType : sourceTypes) {
                elseIf = builder.startIf(elseIf);
                builder.string("(state & 0b").string(Integer.toBinaryString(mask)).string(") != 0 && ");
                builder.tree(check(typeSystem, sourceType, LOCAL_VALUE));
                builder.end().startBlock();

                builder.startReturn();
                ImplicitCastData cast = typeSystem.lookupCast(sourceType, type);
                if (cast != null) {
                    builder.startCall(cast.getMethodName());
                }
                builder.tree(cast(typeSystem, sourceType, LOCAL_VALUE)).end();
                if (cast != null) {
                    builder.end();
                }
                builder.end();
                builder.end();
                mask = mask << 1;
            }

            builder.startElseBlock();
            builder.startThrow().startNew(context.getType(UnexpectedResultException.class)).string(LOCAL_VALUE).end().end();
            builder.end();
            return method;
        }

        private CodeExecutableElement createAsImplicitTypeMethodFlat(TypeMirror type) {
            String name = asImplicitTypeMethodName(typeSystem, type);
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), type, name);
            method.addParameter(new CodeVariableElement(context.getType(int.class), "state"));
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));

            List<TypeMirror> sourceTypes = typeSystem.lookupSourceTypes(type);

            CodeTreeBuilder builder = method.createBuilder();
            boolean elseIf = false;

            int mask = 1;
            for (TypeMirror sourceType : sourceTypes) {
                elseIf = builder.startIf(elseIf);
                builder.string("(state & 0b").string(Integer.toBinaryString(mask)).string(") != 0 && ");
                builder.tree(check(typeSystem, sourceType, LOCAL_VALUE));
                builder.end().startBlock();

                builder.startReturn();
                ImplicitCastData cast = typeSystem.lookupCast(sourceType, type);
                if (cast != null) {
                    builder.startCall(cast.getMethodName());
                }
                builder.tree(cast(typeSystem, sourceType, LOCAL_VALUE)).end();
                if (cast != null) {
                    builder.end();
                }
                builder.end();
                builder.end();
                mask = mask << 1;
            }

            builder.startElseBlock();
            builder.tree(createTransferToInterpreterAndInvalidate());
            builder.startThrow().startNew(context.getType(IllegalArgumentException.class)).doubleQuote("Illegal type ").end().end();
            builder.end();
            return method;
        }

        private CodeExecutableElement createIsImplicitTypeMethodFlat(TypeMirror type) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), context.getType(boolean.class), TypeSystemCodeGenerator.isImplicitTypeMethodName(typeSystem, type));
            method.addParameter(new CodeVariableElement(context.getType(int.class), "state"));
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));
            CodeTreeBuilder builder = method.createBuilder();

            List<TypeMirror> sourceTypes = typeSystem.lookupSourceTypes(type);

            builder.startReturn();
            String sep = "";
            int mask = 1;
            for (TypeMirror sourceType : sourceTypes) {
                builder.string(sep);
                builder.string("((state & 0b").string(Integer.toBinaryString(mask)).string(") != 0 && ");
                builder.tree(check(typeSystem, sourceType, LOCAL_VALUE));
                builder.string(")");
                if (sourceTypes.lastIndexOf(sourceType) != sourceTypes.size() - 1) {
                    builder.newLine();
                }
                if (sep.equals("")) {
                    builder.startIndention();
                }
                sep = " || ";
                mask = mask << 1;
            }
            builder.end();
            builder.end();
            return method;
        }

        private CodeExecutableElement createIsTypeMethod(TypeMirror type) {
            if (typeSystem.getCheck(type) != null) {
                return null;
            }

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), context.getType(boolean.class), TypeSystemCodeGenerator.isTypeMethodName(typeSystem, type));
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));

            CodeTreeBuilder body = method.createBuilder();
            body.startReturn().tree(check(typeSystem, type, LOCAL_VALUE)).end();

            return method;
        }

        private CodeExecutableElement createAsTypeMethod(TypeMirror type) {
            if (typeSystem.getCast(type) != null) {
                return null;
            }

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), type, TypeSystemCodeGenerator.asTypeMethodName(typeSystem, type));
            method.addParameter(new CodeVariableElement(context.getType(Object.class), LOCAL_VALUE));

            CodeTreeBuilder body = method.createBuilder();
            String assertMessage = typeName(typeSystem) + "." + asTypeMethodName(typeSystem, type) + ": " + ElementUtils.getSimpleName(type) + " expected";
            body.startAssert().tree(check(typeSystem, type, LOCAL_VALUE)).string(" : ").doubleQuote(assertMessage).end();
            body.startReturn().tree(cast(typeSystem, type, LOCAL_VALUE)).end();

            return method;
        }

        private CodeExecutableElement createExpectTypeMethod(TypeMirror expectedType, TypeMirror sourceType) {
            return createExpectMethod(Modifier.PUBLIC, typeSystem, sourceType, expectedType);
        }
    }

}
