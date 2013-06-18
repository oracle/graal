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
package com.oracle.truffle.codegen.processor.typesystem;

import static com.oracle.truffle.codegen.processor.Utils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.ast.*;
import com.oracle.truffle.codegen.processor.template.*;

public class TypeSystemCodeGenerator extends CompilationUnitFactory<TypeSystemData> {

    public TypeSystemCodeGenerator(ProcessorContext context) {
        super(context);
    }

    public static String isTypeMethodName(TypeData type) {
        return "is" + Utils.getTypeId(type.getBoxedType());
    }

    public static String asTypeMethodName(TypeData type) {
        return "as" + Utils.getTypeId(type.getBoxedType());
    }

    public static String expectTypeMethodName(TypeData type) {
        return "expect" + Utils.getTypeId(type.getBoxedType());
    }

    /**
     * Finds the generated singleton field for a TypeSytemData instance. TypeSystemCodeGenerator
     * must be applied to the TypeSystemData model before use.
     */
    public static VariableElement findSingleton(ProcessorContext context, TypeSystemData typeSystem) {
        TypeMirror type = context.findGeneratedClassBySimpleName(TypeClassFactory.typeName(typeSystem), typeSystem);
        return Utils.findDeclaredField(type, TypeClassFactory.singletonName(typeSystem.getTemplateType().asType()));
    }

    @Override
    protected void createChildren(TypeSystemData m) {
        add(new TypeClassFactory(context), m);
    }

    protected static class TypeClassFactory extends ClassElementFactory<TypeSystemData> {

        private static final String LOCAL_VALUE = "value";

        public TypeClassFactory(ProcessorContext context) {
            super(context);
        }

        @Override
        public CodeTypeElement create(TypeSystemData typeSystem) {
            String name = typeName(typeSystem);
            CodeTypeElement clazz = createClass(typeSystem, modifiers(PUBLIC), name, typeSystem.getTemplateType().asType(), false);

            clazz.add(createConstructorUsingFields(modifiers(PROTECTED), clazz));
            CodeVariableElement singleton = createSingleton(clazz);
            clazz.add(singleton);

            for (TypeData type : typeSystem.getTypes()) {
                if (!type.isGeneric()) {
                    CodeExecutableElement isType = createIsTypeMethod(type);
                    if (isType != null) {
                        clazz.add(isType);
                    }
                    CodeExecutableElement asType = createAsTypeMethod(type);
                    if (asType != null) {
                        clazz.add(asType);
                    }

                    for (TypeData sourceType : collectExpectSourceTypes(type)) {
                        CodeExecutableElement expect = createExpectTypeMethod(type, sourceType);
                        if (expect != null) {
                            clazz.add(expect);
                        }
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
            return new ArrayList<>(sourceTypes);
        }

        private static String typeName(TypeSystemData typeSystem) {
            String name = getSimpleName(typeSystem.getTemplateType());
            return name + "Gen";
        }

        private static String singletonName(TypeMirror type) {
            return createConstantName(getSimpleName(type));
        }

        private CodeVariableElement createSingleton(CodeTypeElement clazz) {
            CodeVariableElement field = new CodeVariableElement(modifiers(PUBLIC, STATIC, FINAL), clazz.asType(), singletonName(getModel().getTemplateType().asType()));
            field.createInitBuilder().startNew(clazz.asType()).end();
            return field;
        }

        private CodeExecutableElement createIsTypeMethod(TypeData type) {
            if (!type.getTypeChecks().isEmpty()) {
                return null;
            }

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getContext().getType(boolean.class), TypeSystemCodeGenerator.isTypeMethodName(type));
            method.addParameter(new CodeVariableElement(getContext().getType(Object.class), LOCAL_VALUE));

            CodeTreeBuilder body = method.createBuilder();
            body.startReturn().instanceOf(LOCAL_VALUE, type.getBoxedType()).end();

            return method;
        }

        private CodeExecutableElement createAsTypeMethod(TypeData type) {
            if (!type.getTypeCasts().isEmpty()) {
                return null;
            }

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), type.getPrimitiveType(), TypeSystemCodeGenerator.asTypeMethodName(type));
            method.addParameter(new CodeVariableElement(getContext().getType(Object.class), LOCAL_VALUE));

            CodeTreeBuilder body = method.createBuilder();
            body.startAssert().startCall(isTypeMethodName(type)).string(LOCAL_VALUE).end().end();
            body.startReturn().cast(type.getPrimitiveType(), body.create().string(LOCAL_VALUE).getTree()).end();

            return method;
        }

        private CodeExecutableElement createExpectTypeMethod(TypeData expectedType, TypeData sourceType) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), expectedType.getPrimitiveType(), TypeSystemCodeGenerator.expectTypeMethodName(expectedType));
            method.addParameter(new CodeVariableElement(sourceType.getPrimitiveType(), LOCAL_VALUE));
            method.addThrownType(getContext().getTruffleTypes().getUnexpectedValueException());

            CodeTreeBuilder body = method.createBuilder();
            body.startIf().startCall(null, TypeSystemCodeGenerator.isTypeMethodName(expectedType)).string(LOCAL_VALUE).end().end().startBlock();
            body.startReturn().startCall(null, TypeSystemCodeGenerator.asTypeMethodName(expectedType)).string(LOCAL_VALUE).end().end();
            body.end(); // if-block
            body.startThrow().startNew(getContext().getTruffleTypes().getUnexpectedValueException()).string(LOCAL_VALUE).end().end();

            return method;
        }

    }
}
