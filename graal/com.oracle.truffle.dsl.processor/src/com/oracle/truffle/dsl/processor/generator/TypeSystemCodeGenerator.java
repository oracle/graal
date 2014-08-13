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
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;

public class TypeSystemCodeGenerator extends AbstractCompilationUnitFactory<TypeSystemData> {

    public static String isTypeMethodName(TypeData type) {
        return "is" + ElementUtils.getTypeId(type.getBoxedType());
    }

    public static String isImplicitTypeMethodName(TypeData type) {
        return "isImplicit" + ElementUtils.getTypeId(type.getBoxedType());
    }

    public static String asTypeMethodName(TypeData type) {
        return "as" + ElementUtils.getTypeId(type.getBoxedType());
    }

    public static String asImplicitTypeMethodName(TypeData type) {
        return "asImplicit" + ElementUtils.getTypeId(type.getBoxedType());
    }

    public static String getImplicitClass(TypeData type) {
        return "getImplicit" + ElementUtils.getTypeId(type.getBoxedType()) + "Class";
    }

    public static String expectTypeMethodName(TypeData type) {
        return "expect" + ElementUtils.getTypeId(type.getBoxedType());
    }

    public static String typeName(TypeSystemData typeSystem) {
        String name = getSimpleName(typeSystem.getTemplateType());
        return name + "Gen";
    }

    public static String singletonName(TypeSystemData type) {
        return createConstantName(getSimpleName(type.getTemplateType().asType()));
    }

    @Override
    protected void createChildren(TypeSystemData m) {
        add(new TypeClassFactory(), m);
    }

    private static class TypeClassFactory extends AbstractClassElementFactory<TypeSystemData> {

        private static final String LOCAL_VALUE = "value";

        @Override
        public CodeTypeElement create(TypeSystemData typeSystem) {
            String name = typeName(typeSystem);
            CodeTypeElement clazz = createClass(typeSystem, modifiers(PUBLIC, FINAL), name, typeSystem.getTemplateType().asType(), false);

            clazz.add(createConstructorUsingFields(modifiers(PROTECTED), clazz));
            CodeVariableElement singleton = createSingleton(clazz);
            clazz.add(singleton);

            for (TypeData type : typeSystem.getTypes()) {
                if (type.isGeneric() || type.isVoid()) {
                    continue;
                }
                clazz.addOptional(createIsTypeMethod(type));
                clazz.addOptional(createAsTypeMethod(type));

                for (TypeData sourceType : collectExpectSourceTypes(type)) {
                    clazz.addOptional(createExpectTypeMethod(type, sourceType));
                }

                clazz.addOptional(createAsImplicitTypeMethod(type, true));
                clazz.addOptional(createAsImplicitTypeMethod(type, false));
                clazz.addOptional(createIsImplicitTypeMethod(type, true));
                clazz.addOptional(createIsImplicitTypeMethod(type, false));
                clazz.addOptional(createGetTypeIndex(type));
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

        private CodeVariableElement createSingleton(CodeTypeElement clazz) {
            CodeVariableElement field = new CodeVariableElement(modifiers(PUBLIC, STATIC, FINAL), clazz.asType(), singletonName(getModel()));
            field.createInitBuilder().startNew(clazz.asType()).end();
            return field;
        }

        private CodeExecutableElement createIsImplicitTypeMethod(TypeData type, boolean typed) {
            TypeSystemData typeSystem = getModel();
            List<ImplicitCastData> casts = typeSystem.lookupByTargetType(type);
            if (casts.isEmpty()) {
                return null;
            }
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getContext().getType(boolean.class), TypeSystemCodeGenerator.isImplicitTypeMethodName(type));
            method.addParameter(new CodeVariableElement(getContext().getType(Object.class), LOCAL_VALUE));
            if (typed) {
                method.addParameter(new CodeVariableElement(getContext().getType(Class.class), "typeHint"));
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
                builder.startCall(isTypeMethodName(sourceType)).string(LOCAL_VALUE).end();
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

        private CodeExecutableElement createAsImplicitTypeMethod(TypeData type, boolean typed) {
            TypeSystemData typeSystem = getModel();
            List<ImplicitCastData> casts = typeSystem.lookupByTargetType(type);
            if (casts.isEmpty()) {
                return null;
            }
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), type.getPrimitiveType(), TypeSystemCodeGenerator.asImplicitTypeMethodName(type));
            method.addParameter(new CodeVariableElement(getContext().getType(Object.class), LOCAL_VALUE));
            if (typed) {
                method.addParameter(new CodeVariableElement(getContext().getType(Class.class), "typeHint"));
            }

            List<TypeData> sourceTypes = typeSystem.lookupSourceTypes(type);

            CodeTreeBuilder builder = method.createBuilder();
            boolean elseIf = false;
            for (TypeData sourceType : sourceTypes) {
                elseIf = builder.startIf(elseIf);
                if (typed) {
                    builder.string("typeHint == ").typeLiteral(sourceType.getPrimitiveType());
                } else {
                    builder.startCall(isTypeMethodName(sourceType)).string(LOCAL_VALUE).end();
                }

                builder.end().startBlock();

                builder.startReturn();
                ImplicitCastData cast = typeSystem.lookupCast(sourceType, type);
                if (cast != null) {
                    builder.startCall(cast.getMethodName());
                }
                builder.startCall(asTypeMethodName(sourceType)).string(LOCAL_VALUE).end();
                if (cast != null) {
                    builder.end();
                }
                builder.end();
                builder.end();
            }

            builder.startElseBlock();
            builder.startStatement().startStaticCall(getContext().getTruffleTypes().getCompilerDirectives(), "transferToInterpreterAndInvalidate").end().end();
            builder.startThrow().startNew(getContext().getType(IllegalArgumentException.class)).doubleQuote("Illegal type ").end().end();
            builder.end();
            return method;
        }

        private CodeExecutableElement createGetTypeIndex(TypeData type) {
            TypeSystemData typeSystem = getModel();
            List<ImplicitCastData> casts = typeSystem.lookupByTargetType(type);
            if (casts.isEmpty()) {
                return null;
            }
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getContext().getType(Class.class), TypeSystemCodeGenerator.getImplicitClass(type));
            method.addParameter(new CodeVariableElement(getContext().getType(Object.class), LOCAL_VALUE));

            List<TypeData> sourceTypes = typeSystem.lookupSourceTypes(type);
            CodeTreeBuilder builder = method.createBuilder();
            boolean elseIf = false;
            for (TypeData sourceType : sourceTypes) {
                elseIf = builder.startIf(elseIf);
                builder.startCall(isTypeMethodName(sourceType)).string(LOCAL_VALUE).end();
                builder.end().startBlock();
                builder.startReturn().typeLiteral(sourceType.getPrimitiveType()).end();
                builder.end();
            }

            builder.startElseBlock();
            builder.startStatement().startStaticCall(getContext().getTruffleTypes().getCompilerDirectives(), "transferToInterpreterAndInvalidate").end().end();
            builder.startThrow().startNew(getContext().getType(IllegalArgumentException.class)).doubleQuote("Illegal type ").end().end();
            builder.end();

            return method;
        }

        private CodeExecutableElement createIsTypeMethod(TypeData type) {
            if (!type.getTypeChecks().isEmpty()) {
                return null;
            }

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), getContext().getType(boolean.class), TypeSystemCodeGenerator.isTypeMethodName(type));
            method.addParameter(new CodeVariableElement(getContext().getType(Object.class), LOCAL_VALUE));

            DeclaredType suppressWarnings = (DeclaredType) getContext().getType(SuppressWarnings.class);
            CodeAnnotationMirror annotationMirror = new CodeAnnotationMirror(suppressWarnings);
            annotationMirror.setElementValue(annotationMirror.findExecutableElement("value"), new CodeAnnotationValue("static-method"));
            method.getAnnotationMirrors().add(annotationMirror);

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
            String assertMessage = typeName(getModel()) + "." + asTypeMethodName(type) + ": " + ElementUtils.getSimpleName(type.getBoxedType()) + " expected";
            body.startAssert().startCall(isTypeMethodName(type)).string(LOCAL_VALUE).end().string(" : ").doubleQuote(assertMessage).end();
            body.startReturn().cast(type.getPrimitiveType(), body.create().string(LOCAL_VALUE).getTree()).end();

            return method;
        }

        private CodeExecutableElement createExpectTypeMethod(TypeData expectedType, TypeData sourceType) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), expectedType.getPrimitiveType(), TypeSystemCodeGenerator.expectTypeMethodName(expectedType));
            method.addParameter(new CodeVariableElement(sourceType.getPrimitiveType(), LOCAL_VALUE));
            method.addThrownType(getContext().getTruffleTypes().getUnexpectedValueException());

            CodeTreeBuilder body = method.createBuilder();
            body.startIf().startCall(TypeSystemCodeGenerator.isTypeMethodName(expectedType)).string(LOCAL_VALUE).end().end().startBlock();
            body.startReturn().startCall(TypeSystemCodeGenerator.asTypeMethodName(expectedType)).string(LOCAL_VALUE).end().end();
            body.end(); // if-block
            body.startThrow().startNew(getContext().getTruffleTypes().getUnexpectedValueException()).string(LOCAL_VALUE).end().end();

            return method;
        }

    }
}
