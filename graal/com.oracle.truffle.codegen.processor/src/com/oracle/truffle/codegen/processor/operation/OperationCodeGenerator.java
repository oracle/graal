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
package com.oracle.truffle.codegen.processor.operation;

import static com.oracle.truffle.codegen.processor.Utils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.ast.*;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.template.ParameterSpec.Kind;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class OperationCodeGenerator extends CompilationUnitFactory<OperationData> {

    private static final String OPERATION_FIELD_NAME =  "operation";

    public OperationCodeGenerator(ProcessorContext context) {
        super(context);
    }

    private TypeMirror getUnexpectedValueException() {
        return getContext().getTruffleTypes().getUnexpectedValueException();
    }

    private static String operationClassName(OperationData operation) {
        return Utils.getSimpleName(operation.getTemplateType().asType());
    }

    private static String factoryClassName(OperationData operation) {
        return operationClassName(operation) + "Factory";
    }

    private static String nodeClassName(OperationData operation) {
        String name = operationClassName(operation);
        if (name.length() > 2 && name.endsWith("Op")) {
            name = name.substring(0, name.length() - 2);
        }
        return name + "Node";
    }

    private static String nodeClassName(SpecializationData specialization) {
        String name = "";
        if (specialization.getOperation().getAllMethods().length > 1) {
            name = specialization.getMethodName();
            if (name.startsWith("do")) {
                name = name.substring(2);
            }
        }
        return name + nodeClassName(specialization.getOperation());
    }

    private static String nodeVariableName(ParameterSpec spec) {
        if (spec.getKind() == Kind.EXECUTE) {
            return spec.getName() + "Node";
        }
        return spec.getName();
    }

    private static String nodeVariableName(ActualParameter param) {
        return nodeVariableName(param.getSpecification());
    }

    private static String valueVariableName(ParameterSpec spec) {
        if (spec.getKind() == Kind.EXECUTE) {
            return spec.getName() + "Value";
        }
        return spec.getName();
    }

    private static String valueVariableName(ActualParameter param) {
        return valueVariableName(param.getSpecification());
    }

    public static String executeMethodName(TypeData type) {
        if (type.isGeneric()) {
            return "executeGeneric";
        }
        return "execute" + Utils.getSimpleName(type.getBoxedType());
    }

    private static EnumSet<Kind> kinds(Kind... values) {
        EnumSet<Kind> result = EnumSet.noneOf(Kind.class);
        for (Kind value : values) {
            result.add(value);
        }
        return result;
    }

    private static void addNodeParameters(CodeExecutableElement method, OperationData operation, EnumSet<Kind> included) {
        for (ParameterSpec spec : operation.getSpecification().getParameters()) {
            if (included.contains(spec.getKind())) {
                if (spec.getKind() == Kind.EXECUTE) {
                    method.addParameter(new CodeVariableElement(operation.getTypeSystem().getNodeType(), nodeVariableName(spec)));
                } else {
                    method.addParameter(new CodeVariableElement(spec.getValueType(), nodeVariableName(spec)));
                }
            }
        }
        if (included.contains(Kind.CONSTRUCTOR_FIELD)) {
            for (OperationFieldData field : operation.getConstructorFields()) {
                method.addParameter(new CodeVariableElement(field.getJavaClass(), field.getName()));
            }
        }
    }

    private static void addValueParameters(CodeExecutableElement method, OperationData operation, EnumSet<Kind> included) {
        for (ParameterSpec spec : operation.getSpecification().getParameters()) {
            if (included.contains(spec.getKind())) {
                method.addParameter(new CodeVariableElement(spec.getValueType(), valueVariableName(spec)));
            }
        }
        if (included.contains(Kind.CONSTRUCTOR_FIELD)) {
            for (OperationFieldData field : operation.getConstructorFields()) {
                method.addParameter(new CodeVariableElement(field.getJavaClass(), field.getName()));
            }
        }
    }

    private static void addOperationFieldName(CodeTreeBuilder body, OperationData operation) {
        if (!operation.isUseSingleton()) {
            body.string(OPERATION_FIELD_NAME);
        }
    }

    private static void addOperationVariable(Set<Modifier> modifiers, Element element, OperationData operation, CodeTypeElement operationGen) {
        if (!operation.isUseSingleton()) {
            TypeMirror type = findOperationType(operation, operationGen);
            if (element instanceof CodeExecutableElement) {
                ((CodeExecutableElement) element).addParameter(new CodeVariableElement(modifiers, type, OPERATION_FIELD_NAME));
            } else if (element instanceof CodeTypeElement) {
                ((CodeTypeElement) element).add(new CodeVariableElement(modifiers, type, OPERATION_FIELD_NAME));
            }
        }
    }

    private static void addNodeNames(CodeTreeBuilder body, OperationData operation, EnumSet<Kind> included) {
        for (ParameterSpec spec : operation.getSpecification().getParameters()) {
            if (included.contains(spec.getKind())) {
                body.string(nodeVariableName(spec));
            }
        }
        if (included.contains(Kind.CONSTRUCTOR_FIELD)) {
            for (OperationFieldData field : operation.getConstructorFields()) {
                body.string(field.getName());
            }
        }
    }

    private static void addValueNames(CodeTreeBuilder body, OperationData operation, EnumSet<Kind> included) {
        for (ParameterSpec spec : operation.getSpecification().getParameters()) {
            if (included.contains(spec.getKind())) {
                body.string(valueVariableName(spec));
            }
        }
        if (included.contains(Kind.CONSTRUCTOR_FIELD)) {
            for (OperationFieldData field : operation.getConstructorFields()) {
                body.string(field.getName());
            }
        }
    }

    private static void addValueNamesWithCasts(ProcessorContext context, CodeTreeBuilder body, SpecializationData specialization, EnumSet<Kind> included) {
        for (ActualParameter spec : specialization.getParameters()) {
            if (included.contains(spec.getSpecification().getKind())) {
                TypeData typeData = spec.getActualTypeData(specialization.getOperation().getTypeSystem());
                if (typeData.isGeneric()) {
                    body.string(valueVariableName(spec));
                } else {
                    String methodName = TypeSystemCodeGenerator.asTypeMethodName(typeData);
                    startCallTypeSystemMethod(context, body, specialization.getOperation(), methodName);
                    body.string(valueVariableName(spec));
                    body.end().end();
                }
            }
        }
    }

    public static VariableElement findSingleton(ProcessorContext context, OperationData operation) {
        TypeMirror type = context.findGeneratedClassBySimpleName(OperationCodeGenerator.genClassName(operation), operation);
        return Utils.findDeclaredField(type, OperationCodeGenerator.singletonName(operation));
    }

    private static TypeMirror findOperationType(OperationData operation, CodeTypeElement operationGen) {
        if (operation.hasExtensions()) {
            // return generated type
            return operationGen.asType();
        } else {
            // return default type
            return operation.getTemplateType().asType();
        }
    }

    private static String genClassName(OperationData operation) {
        String name = getSimpleName(operation.getTemplateType());
        return name + "Gen";
    }

    private static String singletonName(OperationData operation) {
        return createConstantName(getSimpleName(operation.getTemplateType().asType()));
    }

    private static void startCallOperationMethod(CodeTreeBuilder body, OperationData operation, TemplateMethod method) {
        body.startGroup();

        if (operation.isUseSingleton()) {
            body.string(singletonName(operation));
            body.string(".").startCall(method.getMethodName());
        } else {
            body.string(OPERATION_FIELD_NAME);
            body.string(".");
            body.startCall(method.getMethodName());
        }
    }

    private static void startCallTypeSystemMethod(ProcessorContext context, CodeTreeBuilder body,  OperationData operation, String methodName) {
        VariableElement singleton = TypeSystemCodeGenerator.findSingleton(context, operation.getTypeSystem());
        assert singleton != null;

        body.startGroup();
        body.staticReference(singleton.getEnclosingElement().asType(), singleton.getSimpleName().toString());
        body.string(".").startCall(methodName);
    }

    private static void emitGuards(ProcessorContext context, CodeTreeBuilder body, String prefix, SpecializationData specialization, boolean onSpecialization, boolean needsCast) {
        // Implict guards based on method signature
        String andOperator = prefix;
        for (ActualParameter param : specialization.getParameters()) {
            if (param.getSpecification().getKind() == Kind.EXECUTE && !param.getActualTypeData(specialization.getOperation().getTypeSystem()).isGeneric()) {
                body.string(andOperator);
                startCallTypeSystemMethod(context, body, specialization.getOperation(),
                                TypeSystemCodeGenerator.isTypeMethodName(param.getActualTypeData(specialization.getOperation().getTypeSystem())));
                body.string(valueVariableName(param));
                body.end().end(); // call
                andOperator = " && ";
            }
        }

        if (specialization.getGuards().length > 0) {
            // Explicitly specified guards
            for (SpecializationGuardData guard : specialization.getGuards()) {
                if ((guard.isOnSpecialization() && onSpecialization)
                                || (guard.isOnExecution() && !onSpecialization)) {
                    body.string(andOperator);

                    if (guard.getGuardDeclaration().getOrigin() == specialization.getOperation()) {
                        startCallOperationMethod(body, specialization.getOperation(), guard.getGuardDeclaration());
                    } else {
                        startCallTypeSystemMethod(context, body, specialization.getOperation(), guard.getGuardMethod());
                    }

                    if (needsCast) {
                        addValueNamesWithCasts(context, body, specialization, kinds(Kind.EXECUTE, Kind.SHORT_CIRCUIT));
                    } else {
                        addValueNames(body, specialization.getOperation(), kinds(Kind.EXECUTE, Kind.SHORT_CIRCUIT));
                    }
                    body.end().end(); // call
                    andOperator = " && ";
                }
            }
        }
    }

    @Override
    protected void createChildren(OperationData m) {
        CodeTypeElement operationGen = null;
        if (m.hasExtensions()) {
            OperationGenFactory factory = new OperationGenFactory(context);
            add(factory, m);
            operationGen = (CodeTypeElement) factory.getElement();
        }
        if (m.generateFactory) {
            add(new OperationNodeFactory(context, operationGen), m);
        }
    }

    protected class OperationGenFactory extends ClassElementFactory<OperationData> {

        public OperationGenFactory(ProcessorContext context) {
            super(context);
        }

        @Override
        protected CodeTypeElement create(OperationData operation) {
            CodeTypeElement clazz = createClass(operation, modifiers(PUBLIC), genClassName(operation), operation.getTemplateType().asType(), false);

            clazz.add(createConstructorUsingFields(modifiers(PUBLIC), clazz));

            if (operation.getExtensionElements() != null) {
                clazz.getEnclosedElements().addAll(operation.getExtensionElements());
            }
            return clazz;
        }

    }



    protected class OperationNodeFactory extends ClassElementFactory<OperationData> {

        private final CodeTypeElement operationGen;

        public OperationNodeFactory(ProcessorContext context, CodeTypeElement operationGen) {
            super(context);
            this.operationGen = operationGen;
        }

        @Override
        protected CodeTypeElement create(OperationData operation) {
            CodeTypeElement clazz = createClass(operation, modifiers(PUBLIC, FINAL), factoryClassName(operation), null, false);

            if (operation.isUseSingleton()) {
                TypeMirror type = findOperationType(operation, operationGen);
                CodeVariableElement singleton = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), type, singletonName(operation));
                clazz.add(singleton);
                CodeTreeBuilder singletonInit = singleton.createInitBuilder();
                singletonInit.startNew(type).end();
            }

            clazz.add(createConstructorUsingFields(modifiers(PRIVATE), clazz));
            clazz.add(createCreateMethod(operation));
            if (operation.getAllMethods().length > 1) {
                clazz.add(createCreateSpecializedMethod(operation));
            }
            if (operation.needsRewrites()) {
                clazz.add(createSpecializeMethod(operation));
                clazz.add(createGeneratedGenericMethod(operation));
            }

            return clazz;
        }

        @Override
        protected void createChildren(OperationData operation) {
            for (SpecializationData specialization : operation.getAllMethods()) {
                add(new SpecializationNodeFactory(context, operationGen), specialization);
            }
        }

        private CodeExecutableElement createCreateMethod(OperationData operation) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), operation.getNodeType(), "create");
            addNodeParameters(method, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE, Kind.CONSTRUCTOR_FIELD));

            CodeTreeBuilder body = method.createBuilder();
            body.startReturn();
            if (operation.getAllMethods().length == 0) {
                body.null_();
            } else {
                body.startNew(nodeClassName(operation.getAllMethods()[0]));
                emitNewOperation(body, operation);
                addNodeNames(body, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));
                body.end();
            }
            body.end();

            return method;
        }

        private void emitNewOperation(CodeTreeBuilder body, OperationData operation) {
            if (!operation.isUseSingleton()) {
                body.startGroup();
                if (operation.hasExtensions()) {
                    body.startNew(genClassName(operation));
                } else {
                    body.startNew(operation.getTemplateType().asType());
                }
                addNodeNames(body, operation, kinds(Kind.CONSTRUCTOR_FIELD));
                body.end();
                body.end();
            }
        }

        private CodeExecutableElement createCreateSpecializedMethod(OperationData operation) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, STATIC), operation.getNodeType(), "createSpecialized");
            method.addParameter(new CodeVariableElement(getContext().getType(Class.class), "specializationClass"));
            addOperationVariable(modifiers(), method, operation, operationGen);
            addNodeParameters(method, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));

            CodeTreeBuilder body = method.createBuilder();

            boolean first = true;
            for (TypeData type : operation.getTypeSystem().getTypes()) {
                SpecializationData specialization = operation.findUniqueSpecialization(type);
                if (specialization != null && !type.isGeneric()) {
                    if (first) {
                        body.startIf();
                        first = false;
                    } else {
                        body.startElseIf();
                    }
                    body.string("specializationClass == ").type(type.getBoxedType()).string(".class").end().startBlock();
                    body.startReturn().startNew(nodeClassName(specialization));
                    addOperationFieldName(body, operation);
                    addNodeNames(body, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));
                    body.end().end();
                    body.end();
                }
            }
            body.startReturn().startNew(nodeClassName(operation.getGenericSpecialization()));
            addOperationFieldName(body, operation);
            addNodeNames(body, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));
            body.end().end();
            return method;
        }

        private CodeExecutableElement createSpecializeMethod(OperationData operation) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, STATIC), operation.getNodeType(), "specialize");
            method.addParameter(new CodeVariableElement(getContext().getType(Class.class), "minimumState"));
            addOperationVariable(modifiers(), method, operation, operationGen);
            addValueParameters(method, operation, kinds(Kind.EXECUTE));
            addNodeParameters(method, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));

            CodeTreeBuilder body = method.createBuilder();
            body.startStatement().string("boolean allowed = (minimumState == ").string(nodeClassName(operation.getAllMethods()[0])).string(".class)").end();

            for (int i = 1; i < operation.getAllMethods().length; i++) {
                SpecializationData specialization = operation.getAllMethods()[i];
                body.startStatement().string("allowed = allowed || (minimumState == ").string(nodeClassName(specialization)).string(".class)").end();

                if (specialization.isGeneric()) {
                    body.startIf().string("allowed").end().startBlock();
                } else {
                    body.startIf().string("allowed");
                    emitGuards(getContext(), body, " && ", specialization, true, true);
                    body.end().startBlock();
                }
                body.startReturn().startNew(nodeClassName(specialization));

                addOperationFieldName(body, operation);
                addNodeNames(body, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));
                body.end().end();
                body.end(); // block
            }
            body.startThrow().startNew(getContext().getType(IllegalArgumentException.class)).end().end();

            return method;
        }


        private void emitInvokeDoMethod(CodeTreeBuilder builder, SpecializationData specialization) {
            if (specialization.getExceptions().length > 0) {
                builder.startTryBlock();
            }

            builder.startReturn();
            startCallOperationMethod(builder, specialization.getOperation(), specialization);
            for (ActualParameter param : specialization.getParameters()) {
                boolean needsCast = param.getSpecification().getKind() == Kind.EXECUTE && !param.getActualTypeData(specialization.getOperation().getTypeSystem()).isGeneric();
                if (needsCast) {
                    startCallTypeSystemMethod(getContext(), builder, specialization.getOperation(), TypeSystemCodeGenerator.asTypeMethodName(param.getActualTypeData(specialization.getOperation().getTypeSystem())));
                }
                builder.string(valueVariableName(param));
                if (needsCast) {
                    builder.end().end();
                }
            }
            builder.end().end(); // start call operation
            builder.end(); // return

            if (specialization.getExceptions().length > 0) {
                for (SpecializationThrowsData exception : specialization.getExceptions()) {
                    builder.end().startCatchBlock(exception.getJavaClass(), "ex");
                    emitInvokeDoMethod(builder, exception.getTransitionTo());
                }
                builder.end();
            }
        }

        private CodeExecutableElement createGeneratedGenericMethod(OperationData operation) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, STATIC), operation.getGenericSpecialization().getReturnType().getActualType(), "generatedGeneric");
            addOperationVariable(modifiers(), method, operation, operationGen);
            addValueParameters(method, operation, kinds(Kind.SIGNATURE, Kind.EXECUTE, Kind.SHORT_CIRCUIT, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));

            CodeTreeBuilder builder = method.createBuilder();
            boolean ifStarted = false;
            for (int i = 0; i < operation.getSpecializations().length; i++) {
                SpecializationData specialization = operation.getSpecializations()[i];
                if (specialization.isUninitialized()) {
                    continue;
                }
                if (!specialization.isGeneric()) {
                    if (!ifStarted) {
                        builder.startIf();
                        ifStarted = true;
                    } else {
                        builder.startElseIf();
                    }
                    emitGuards(getContext(), builder, "", specialization, false, true);
                    builder.end().startBlock();
                } else {
                    builder.startElseBlock();
                }

                emitInvokeDoMethod(builder, specialization);
                builder.end();
            }
            return method;
        }
    }

    protected class SpecializationNodeFactory extends ClassElementFactory<SpecializationData> {

        private CodeTypeElement operationGen;

        public SpecializationNodeFactory(ProcessorContext context, CodeTypeElement operationGen) {
            super(context);
            this.operationGen = operationGen;
        }

        @Override
        public CodeTypeElement create(SpecializationData specialization) {
            OperationData operation = specialization.getOperation();
            CodeTypeElement clazz = createClass(operation, modifiers(PRIVATE, STATIC, FINAL), nodeClassName(specialization), operation.getNodeType(), false);


            CodeExecutableElement constructor = new CodeExecutableElement(modifiers(PROTECTED), null, clazz.getSimpleName().toString());
            clazz.add(constructor);

            CodeTreeBuilder builder = constructor.createBuilder();
            builder.startStatement().startSuperCall();
            addNodeNames(builder, operation, kinds(Kind.SUPER_ATTRIBUTE));
            builder.end().end();

            if (!operation.isUseSingleton()) {
                addOperationVariable(modifiers(), constructor, operation, operationGen);
                addOperationVariable(modifiers(PRIVATE, FINAL), clazz, operation, operationGen);
                builder.startStatement();
                builder.string("this.");
                addOperationFieldName(builder, operation);
                builder.string(" = ");
                addOperationFieldName(builder, operation);
                builder.end();
            }
            addNodeParameters(constructor, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));


            for (ParameterSpec spec : operation.getSpecification().getParameters()) {
                String name = nodeVariableName(spec);

                boolean isNodeAttribute = spec.getKind() == Kind.ATTRIBUTE && getContext().getEnvironment().getTypeUtils().isAssignable(spec.getValueType(), getContext().getTruffleTypes().getNode());
                boolean isNodeArrayAttribute = spec.getKind() == Kind.ATTRIBUTE &&
                                getContext().getEnvironment().getTypeUtils().isAssignable(spec.getValueType(), getContext().getTruffleTypes().getNodeArray());
                if (spec.getKind() == Kind.EXECUTE || isNodeAttribute || isNodeArrayAttribute) {
                    CodeVariableElement field = new CodeVariableElement(modifiers(PRIVATE), operation.getTypeSystem().getNodeType(), name);
                    clazz.add(field);
                    field.addAnnotationMirror(new CodeAnnotationMirror((DeclaredType) getContext().getTruffleTypes().getStableAnnotation()));
                    if (isNodeArrayAttribute) {
                        field.addAnnotationMirror(new CodeAnnotationMirror((DeclaredType) getContext().getTruffleTypes().getContentStableAnnotation()));
                    }
                    builder.startStatement().string("this.").string(name).string(" = adoptChild(").string(name).string(")").end();
                } else if (spec.getKind() == Kind.ATTRIBUTE) {
                    clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), spec.getValueType(), name));
                    builder.startStatement().string("this.").string(name).string(" = ").string(name).end();
                }
            }

            TypeSystemData typeSystem = operation.getTypeSystem();
            for (TypeData type : typeSystem.getTypes()) {
                CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), type.getPrimitiveType(), executeMethodName(type));
                clazz.add(method);
                addValueParameters(method, operation, kinds(Kind.SIGNATURE));
                if (!type.isGeneric()) {
                    method.addThrownType(getUnexpectedValueException());
                }

                if (specialization.getReturnType().getActualTypeData(typeSystem) == type) {
                    buildFunctionalExecuteMethod(method.createBuilder(), operation, specialization);
                } else {
                    buildCastingExecuteMethod(method.createBuilder(), operation, specialization, type);
                }
            }

            if (typeSystem.getVoidType() != null)  {
                CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), typeSystem.getVoidType().getPrimitiveType(), executeMethodName(typeSystem.getVoidType()));
                addValueParameters(method, operation, kinds(Kind.SIGNATURE));
                buildCastingExecuteMethod(method.createBuilder(), operation, specialization, typeSystem.getVoidType());
                clazz.add(method);
            }

            if (operation.needsRewrites() && !specialization.isGeneric() && !specialization.isUninitialized()) {
                buildSpecializeStateMethod(clazz, operation);
            }
            buildSpecializeClassMethod(clazz, operation);

            return clazz;
        }

        private void buildCastingExecuteMethod(CodeTreeBuilder builder, OperationData operation, SpecializationData specialization, TypeData type) {
            if (!type.isVoid()) {
                builder.startStatement().type(specialization.getReturnType().getActualType()).string(" result").end();
            }

            boolean needsTry = !specialization.getReturnType().getActualTypeData(operation.getTypeSystem()).isGeneric();
            if (needsTry) {
                builder.startTryBlock();
            }

            builder.startStatement();
            if (!type.isVoid()) {
                builder.string("result = ");
            }
            builder.startCall(executeMethodName(specialization.getReturnType().getActualTypeData(operation.getTypeSystem()))).string("frame").end();
            builder.end(); // statement

            if (needsTry) {
                builder.end().startCatchBlock(getUnexpectedValueException(), "ex");

                if (!type.isVoid()) {
                    builder.startReturn();
                    if (!type.isGeneric()) {
                        startCallTypeSystemMethod(getContext(), builder, operation, TypeSystemCodeGenerator.expectTypeMethodName(type));
                    }

                    builder.string("ex.getResult()");

                    if (!type.isGeneric()) {
                        builder.end().end();
                    }
                    builder.end(); // return
                } else {
                    builder.string("// ignore").newLine();
                }
            }
            builder.end(); // try/catch

            if (!type.isVoid()) {
                builder.startReturn();
                if (!type.isGeneric()) {
                    startCallTypeSystemMethod(getContext(), builder, operation, TypeSystemCodeGenerator.expectTypeMethodName(type));
                }
                builder.string("result");
                if (!type.isGeneric()) {
                    builder.end().end();
                }
                builder.end(); // return
            }
        }

        private void buildFunctionalExecuteMethod(CodeTreeBuilder builder, OperationData operation, SpecializationData specialization) {
            ActualParameter previousShortCircuitParameter = null;
            for (ActualParameter param : specialization.getParameters()) {
                if (param.getSpecification().getKind() != Kind.EXECUTE) {
                    // Nothing to do.
                } else if (param.getActualTypeData(operation.getTypeSystem()).isGeneric()) {
                    buildGenericValueExecute(builder, operation, param, null);
                } else {
                    buildSpecializedValueExecute(builder, operation, specialization, param);
                }

                assert param.getSpecification().getKind() == Kind.SHORT_CIRCUIT || previousShortCircuitParameter == null;
            }

            if (specialization.hasDynamicGuards()) {
                builder.startIf();
                emitGuards(getContext(), builder, "", specialization, false, false);
                builder.end().startBlock();
            }
            if (specialization.getExceptions().length > 0) {
                builder.startTryBlock();
            }

            if (specialization.isUninitialized() && operation.needsRewrites()) {
                for (TemplateMethod listener : operation.getSpecializationListeners()) {
                    builder.startStatement();
                    startCallOperationMethod(builder, operation, listener);
                    for (ActualParameter param : listener.getParameters()) {
                        builder.string(valueVariableName(param));
                    }
                    builder.end().end();
                    builder.end(); // statement
                }

                builder.startStatement();
                builder.startCall("replace");
                builder.startCall(factoryClassName(operation), "specialize");
                builder.typeLiteral(builder.getRoot().getEnclosingClass().asType());
                addOperationFieldName(builder, operation);
                addValueNames(builder, operation, kinds(Kind.EXECUTE));
                addNodeNames(builder, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));
                builder.end().end().end();
            }

            if ((specialization.isUninitialized() || specialization.isGeneric()) && operation.needsRewrites()) {
                builder.startReturn().startCall(factoryClassName(specialization.getOperation()), "generatedGeneric");
                addOperationFieldName(builder, operation);
                addValueNames(builder, operation, kinds(Kind.SIGNATURE, Kind.EXECUTE, Kind.SHORT_CIRCUIT, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));
                builder.end().end();
            } else {
                builder.startReturn();

                if (specialization.isUninitialized()) {
                    startCallOperationMethod(builder, specialization.getOperation(), specialization.getOperation().getGenericSpecialization());
                } else {
                    startCallOperationMethod(builder, specialization.getOperation(), specialization);
                }
                for (ActualParameter param : specialization.getParameters()) {
                    builder.string(valueVariableName(param));
                }
                builder.end().end(); // operation call
                builder.end(); // return
            }

            if (specialization.getExceptions().length > 0) {
                for (SpecializationThrowsData exception : specialization.getExceptions()) {
                    builder.end().startCatchBlock(exception.getJavaClass(), "ex");
                    buildThrowSpecialize(builder, operation, exception.getTransitionTo(), null);
                }
                builder.end();
            }
            if (specialization.hasDynamicGuards()) {
                builder.end().startElseBlock();
                buildThrowSpecialize(builder, operation, specialization.findNextSpecialization(), null);
                builder.end();
            }
        }

        private void buildGenericValueExecute(CodeTreeBuilder builder, OperationData operation, ActualParameter param, ParameterSpec exceptionSpec) {
            boolean shortCircuit = startShortCircuit(builder, operation.getGenericSpecialization(),
                            operation.getGenericSpecialization().findParameter(param.getSpecification().getName()), exceptionSpec);

            builder.startStatement();
            if (!shortCircuit) {
                builder.type(operation.getTypeSystem().getGenericType());
                builder.string(" ");
            }
            builder.string(valueVariableName(param));
            builder.string(" = ").startCall(nodeVariableName(param), executeMethodName(operation.getTypeSystem().getGenericTypeData())).string("frame").end();
            builder.end();

            endShortCircuit(builder, shortCircuit);
        }

        private boolean startShortCircuit(CodeTreeBuilder builder, SpecializationData specialization,
                        ActualParameter forParam, ParameterSpec exceptionParam) {
            ActualParameter shortCircuitParam = specialization.getPreviousParam(forParam);
            if (shortCircuitParam == null || shortCircuitParam.getSpecification().getKind() != Kind.SHORT_CIRCUIT) {
                return false;
            }

            int shortCircuitIndex = 0;
            for (ActualParameter parameter : specialization.getParameters()) {
                if (parameter.getSpecification().getKind() == Kind.SHORT_CIRCUIT) {
                    if (parameter == shortCircuitParam) {
                        break;
                    }
                    shortCircuitIndex++;
                }
            }

            builder.startStatement().type(shortCircuitParam.getActualType()).string(" ").string(shortCircuitParam.getSpecification().getName()).string(" = ");
            ShortCircuitData shortCircuitData = specialization.getShortCircuits()[shortCircuitIndex];

            startCallOperationMethod(builder, specialization.getOperation(), shortCircuitData);
            for (ActualParameter callParam : shortCircuitData.getParameters()) {
                ParameterSpec spec = callParam.getSpecification();
                if (spec.getKind() == Kind.EXECUTE || spec.getKind() == Kind.SHORT_CIRCUIT || spec.getKind() == Kind.SIGNATURE) {
                    if (exceptionParam != null && callParam.getSpecification().getName().equals(exceptionParam.getName())) {
                        builder.string("ex.getResult()");
                    } else {
                        builder.string(valueVariableName(spec));
                    }
                }
            }
            builder.end().end(); // call operation

            builder.end(); // statement

            builder.declaration(forParam.getActualType(), valueVariableName(forParam),
                            CodeTreeBuilder.createBuilder().defaultValue(forParam.getActualType()));
            builder.startIf().string(valueVariableName(shortCircuitParam)).end();
            builder.startBlock();

            return true;
        }


        private void endShortCircuit(CodeTreeBuilder builder, boolean shortCircuit) {
            if (shortCircuit) {
                builder.end();
            }
        }

        private void buildSpecializedValueExecute(CodeTreeBuilder builder, OperationData operation, SpecializationData specialization, ActualParameter param) {
            boolean shortCircuit = startShortCircuit(builder, specialization, param, null);

            if (!shortCircuit) {
                builder.startStatement().type(param.getActualType()).string(" ").string(valueVariableName(param)).end();
            }

            builder.startTryBlock();
            builder.startStatement().string(valueVariableName(param)).string(" = ");
            builder.startCall(nodeVariableName(param), executeMethodName(param.getActualTypeData(operation.getTypeSystem()))).string("frame").end();
            builder.end();

            builder.end().startCatchBlock(getUnexpectedValueException(), "ex");
            boolean execute = false;
            for (ActualParameter exParam : specialization.getParameters()) {
                if (exParam.getSpecification().getKind() != Kind.EXECUTE) {
                    // Nothing to do.
                } else if (execute) {
                    buildGenericValueExecute(builder, operation, exParam, param.getSpecification());
                } else if (exParam == param) {
                    execute = true;
                }
            }

            buildThrowSpecialize(builder, operation, specialization.findNextSpecialization(), param.getSpecification());
            builder.end(); // catch block

            endShortCircuit(builder, shortCircuit);
            builder.newLine();
        }

        private void buildThrowSpecialize(CodeTreeBuilder builder, OperationData operation, SpecializationData nextSpecialization, ParameterSpec exceptionSpec) {
            builder.startThrow().startCall("specialize");
            builder.string(nodeClassName(nextSpecialization) + ".class");
            addValueNames(builder, operation, kinds(Kind.SIGNATURE));
            for (ParameterSpec spec : operation.getSpecification().getParameters()) {
                if (spec.getKind() == Kind.EXECUTE || spec.getKind() == Kind.SHORT_CIRCUIT) {
                    if (spec == exceptionSpec) {
                        builder.string("ex.getResult()");
                    } else {
                        builder.string(valueVariableName(spec));
                    }
                }
            }
            builder.end().end();
        }

        private void buildSpecializeStateMethod(CodeTypeElement clazz, OperationData operation) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE), getUnexpectedValueException(), "specialize");
            method.addParameter(new CodeVariableElement(getContext().getType(Class.class), "minimumState"));
            addValueParameters(method, operation, kinds(Kind.SIGNATURE, Kind.EXECUTE, Kind.SHORT_CIRCUIT));
            clazz.add(method);
            CodeTreeBuilder builder = method.createBuilder();

            for (TemplateMethod listener : operation.getSpecializationListeners()) {
                builder.startStatement();
                startCallOperationMethod(builder, operation, listener);
                for (ActualParameter param : listener.getParameters()) {
                    builder.string(valueVariableName(param));
                }
                builder.end().end();
                builder.end(); // statement
            }

            builder.startStatement();
            builder.startCall("replace");
            builder.startCall(factoryClassName(operation), "specialize").string("minimumState");
            addOperationFieldName(builder, operation);
            addValueNames(builder, operation, kinds(Kind.EXECUTE));
            addNodeNames(builder, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));
            builder.end().end().end();

            builder.startReturn().startNew(getUnexpectedValueException()).startCall(factoryClassName(operation), "generatedGeneric");
            addOperationFieldName(builder, operation);
            addValueNames(builder, operation, kinds(Kind.SIGNATURE, Kind.EXECUTE, Kind.SHORT_CIRCUIT, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));
            builder.end().end().end();
        }

        private void buildSpecializeClassMethod(CodeTypeElement clazz, OperationData operation) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), getContext().getType(void.class), "specialize");
            method.addParameter(new CodeVariableElement(getContext().getType(Class.class), "specializationClass"));
            if (!isDeclaredMethodInSuperType(clazz, method.getSimpleName().toString(), method.getParameterTypes())) {
                return;
            }
            clazz.add(method);
            CodeTreeBuilder builder = method.createBuilder();

            builder.startStatement();
            builder.startCall("replace");
            builder.startCall(factoryClassName(operation), "createSpecialized").string("specializationClass");
            addOperationFieldName(builder, operation);
            addNodeNames(builder, operation, kinds(Kind.EXECUTE, Kind.ATTRIBUTE, Kind.SUPER_ATTRIBUTE));
            builder.end().end().end();
        }
    }
}
