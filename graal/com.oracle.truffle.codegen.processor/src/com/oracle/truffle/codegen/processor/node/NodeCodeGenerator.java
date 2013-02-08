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
package com.oracle.truffle.codegen.processor.node;

import static com.oracle.truffle.codegen.processor.Utils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.ast.*;
import com.oracle.truffle.codegen.processor.node.NodeFieldData.ExecutionKind;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class NodeCodeGenerator extends CompilationUnitFactory<NodeData> {

    private static final String THIS_NODE_LOCAL_VAR_NAME = "thisNode";

    public NodeCodeGenerator(ProcessorContext context) {
        super(context);
    }

    private TypeMirror getUnexpectedValueException() {
        return getContext().getTruffleTypes().getUnexpectedValueException();
    }

    private static String factoryClassName(NodeData node) {
        return nodeClassName(node) + "Factory";
    }

    private static String nodeClassName(NodeData node) {
        return Utils.getSimpleName(node.getTemplateType().asType());
    }

    private static String nodeClassName(SpecializationData specialization) {
        String name = "";
        if (specialization.getNode().getSpecializations().length > 1) {
            name = specialization.getMethodName();
            if (name.startsWith("do")) {
                name = name.substring(2);
            }
        }
        name += nodeClassName(specialization.getNode());
        if (name.equals(Utils.getSimpleName(specialization.getNode().getNodeType())) || name.equals(Utils.getSimpleName(specialization.getNode().getTemplateType()))) {
            name = name + "Impl";
        }

        return name;
    }

    private static String valueName(NodeFieldData field) {
        return field.getName() + "Value";
    }

    private static String valueName(TemplateMethod method, ActualParameter param) {
        NodeData node = (NodeData) method.getTemplate();
        NodeFieldData field = node.findField(param.getSpecification().getName());
        if (field != null) {
            return valueName(field);
        } else {
            return param.getSpecification().getName();
        }
    }

    private void addValueParameters(CodeExecutableElement method, TemplateMethod specialization, boolean forceFrame) {
        if (forceFrame) {
            method.addParameter(new CodeVariableElement(getContext().getTruffleTypes().getFrame(), "frame"));
        }
        for (ActualParameter parameter : specialization.getParameters()) {
            ParameterSpec spec = parameter.getSpecification();
            if (forceFrame && spec.getName().equals("frame")) {
                continue;
            }
            method.addParameter(new CodeVariableElement(parameter.getActualType(), valueName(specialization, parameter)));
        }
    }

    private static void addValueParameterNames(CodeTreeBuilder builder, TemplateMethod specialization, String unexpectedValueName, boolean forceFrame) {
        if (forceFrame) {
            builder.string("frame");
        }
        for (ActualParameter parameter : specialization.getParameters()) {
            ParameterSpec spec = parameter.getSpecification();
            if (forceFrame && spec.getName().equals("frame")) {
                continue;
            }

            if (unexpectedValueName != null && spec.getName().equals(unexpectedValueName)) {
                builder.string("ex.getResult()");
            } else {
                builder.string(valueName(specialization, parameter));
            }
        }
    }

    private static void addValueParameterNamesWithCasts(ProcessorContext context, CodeTreeBuilder body, SpecializationData specialization) {
        for (ActualParameter param : specialization.getParameters()) {
            TypeData typeData = param.getActualTypeData(specialization.getNode().getTypeSystem());
            if (typeData == null || typeData.isGeneric()) {
                body.string(valueName(specialization, param));
            } else {
                String methodName = TypeSystemCodeGenerator.asTypeMethodName(typeData);
                startCallTypeSystemMethod(context, body, specialization.getNode(), methodName);
                body.string(valueName(specialization, param));
                body.end().end();
            }
        }
    }

    private static String genClassName(Template operation) {
        return getSimpleName(operation.getTemplateType()) + "Gen";
    }

    private static void startCallOperationMethod(CodeTreeBuilder body, TemplateMethod method) {
        body.startGroup();
        if (body.findMethod().getModifiers().contains(STATIC)) {
            body.string(THIS_NODE_LOCAL_VAR_NAME);
        } else {
            body.string("super");
        }
        body.string(".");
        body.startCall(method.getMethodName());
    }

    private static void startCallTypeSystemMethod(ProcessorContext context, CodeTreeBuilder body, NodeData node, String methodName) {
        VariableElement singleton = TypeSystemCodeGenerator.findSingleton(context, node.getTypeSystem());
        assert singleton != null;

        body.startGroup();
        body.staticReference(singleton.getEnclosingElement().asType(), singleton.getSimpleName().toString());
        body.string(".").startCall(methodName);
    }

    private static void emitGuards(ProcessorContext context, CodeTreeBuilder body, String prefix, SpecializationData specialization, boolean onSpecialization, boolean needsCast) {
        TypeSystemData typeSystem = specialization.getNode().getTypeSystem();
        // Implict guards based on method signature
        String andOperator = prefix;
        for (NodeFieldData field : specialization.getNode().getFields()) {
            ActualParameter param = specialization.findParameter(field.getName());
            TypeData type = param.getActualTypeData(typeSystem);
            if (type == null || type.isGeneric()) {
                continue;
            }

            body.string(andOperator);
            startCallTypeSystemMethod(context, body, specialization.getNode(), TypeSystemCodeGenerator.isTypeMethodName(type));
            body.string(valueName(specialization, param));
            body.end().end(); // call
            andOperator = " && ";
        }

        if (specialization.getGuards().length > 0) {
            // Explicitly specified guards
            for (SpecializationGuardData guard : specialization.getGuards()) {
                if ((guard.isOnSpecialization() && onSpecialization) || (guard.isOnExecution() && !onSpecialization)) {
                    body.string(andOperator);

                    startCallOperationMethod(body, guard.getGuardDeclaration());

                    if (needsCast) {
                        addValueParameterNamesWithCasts(context, body, specialization);
                    } else {
                        addValueParameterNames(body, specialization, null, false);
                    }
                    body.end().end(); // call
                    andOperator = " && ";
                }
            }
        }
    }

    @Override
    protected void createChildren(NodeData node) {
        Map<NodeData, List<TypeElement>> childTypes = new LinkedHashMap<>();
        if (node.getDeclaredChildren() != null && !node.getDeclaredChildren().isEmpty()) {
            for (NodeData nodeChild : node.getDeclaredChildren()) {
                NodeCodeGenerator generator = new NodeCodeGenerator(getContext());
                childTypes.put(nodeChild, generator.process(null, nodeChild).getEnclosedElements());
            }
        }

        if (node.getExtensionElements() != null && !node.getExtensionElements().isEmpty()) {
            NodeGenFactory factory = new NodeGenFactory(context);
            add(factory, node);
        }

        if (node.getSpecializations() == null) {
            return;
        }

        if (node.needsFactory() || childTypes.size() > 0) {
            add(new NodeFactoryFactory(context, childTypes), node);
        }
    }

    private class NodeGenFactory extends ClassElementFactory<NodeData> {

        public NodeGenFactory(ProcessorContext context) {
            super(context);
        }

        @Override
        protected CodeTypeElement create(NodeData node) {
            CodeTypeElement clazz = createClass(node, modifiers(PUBLIC, ABSTRACT), genClassName(node), node.getTemplateType().asType(), false);

            for (ExecutableElement executable : ElementFilter.constructorsIn(node.getTemplateType().getEnclosedElements())) {
                CodeExecutableElement superConstructor = createSuperConstructor(clazz, executable);

                if (superConstructor != null) {
                    if (superConstructor.getParameters().size() == 1 && Utils.typeEquals(superConstructor.getParameters().get(0).asType(), node.getTemplateType().asType())) {
                        String originalName = superConstructor.getParameters().get(0).getSimpleName().toString();
                        superConstructor.getParameters().clear();
                        superConstructor.getParameters().add(new CodeVariableElement(clazz.asType(), originalName));
                    }
                    clazz.add(superConstructor);
                }
            }

            if (node.getExtensionElements() != null) {
                clazz.getEnclosedElements().addAll(node.getExtensionElements());
            }

            node.setNodeType(clazz.asType());

            return clazz;
        }

    }

    private class NodeFactoryFactory extends ClassElementFactory<NodeData> {

        private final Map<NodeData, List<TypeElement>> childTypes;

        public NodeFactoryFactory(ProcessorContext context, Map<NodeData, List<TypeElement>> childElements) {
            super(context);
            this.childTypes = childElements;
        }

        @Override
        protected CodeTypeElement create(NodeData node) {
            Modifier visibility = Utils.getVisibility(node.getTemplateType().getModifiers());
            CodeTypeElement clazz = createClass(node, modifiers(), factoryClassName(node), null, false);
            if (visibility != null) {
                clazz.getModifiers().add(visibility);
            }
            clazz.getModifiers().add(Modifier.FINAL);
            clazz.add(createConstructorUsingFields(modifiers(PRIVATE), clazz));
            return clazz;
        }

        @Override
        protected void createChildren(NodeData node) {
            CodeTypeElement clazz = getElement();

            Modifier createVisibility = Utils.getVisibility(clazz.getModifiers());

            if (node.needsFactory()) {
                createFactoryMethods(node, clazz, createVisibility);

                if (node.getSpecializations().length > 1) {
                    clazz.add(createCreateSpecializedMethod(node, createVisibility));
                }

                if (node.needsRewrites(getContext())) {
                    clazz.add(createSpecializeMethod(node));
                    clazz.add(createGeneratedGenericMethod(node));
                }

                for (SpecializationData specialization : node.getSpecializations()) {
                    add(new SpecializedNodeFactory(context), specialization);
                }
            }

            for (NodeData childNode : childTypes.keySet()) {
                if (childNode.getTemplateType().getModifiers().contains(Modifier.PRIVATE)) {
                    continue;
                }

                for (TypeElement type : childTypes.get(childNode)) {
                    Set<Modifier> typeModifiers = ((CodeTypeElement) type).getModifiers();
                    Modifier visibility = Utils.getVisibility(type.getModifiers());
                    typeModifiers.clear();
                    if (visibility != null) {
                        typeModifiers.add(visibility);
                    }

                    typeModifiers.add(Modifier.STATIC);
                    typeModifiers.add(Modifier.FINAL);
                    clazz.add(type);
                }
            }
        }

        private void createFactoryMethods(NodeData node, CodeTypeElement clazz, Modifier createVisibility) {
            for (ExecutableElement constructor : ElementFilter.constructorsIn(Utils.fromTypeMirror(node.getNodeType()).getEnclosedElements())) {
                if (constructor.getModifiers().contains(PRIVATE)) {
                    continue;
                }

                // skip node rewrite constructor
                if (constructor.getParameters().size() == 1 && typeEquals(constructor.getParameters().get(0).asType(), node.getNodeType())) {
                    continue;
                }

                clazz.add(createCreateMethod(node, createVisibility, constructor));
            }
        }

        private CodeExecutableElement createCreateMethod(NodeData node, Modifier visibility, ExecutableElement constructor) {
            CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), constructor);
            method.setSimpleName(CodeNames.of("create"));
            method.getModifiers().clear();
            if (visibility != null) {
                method.getModifiers().add(visibility);
            }
            method.getModifiers().add(Modifier.STATIC);
            method.setReturnType(node.getNodeType());

            CodeTreeBuilder body = method.createBuilder();
            body.startReturn();
            if (node.getSpecializations().length == 0) {
                body.null_();
            } else {
                body.startNew(nodeClassName(node.getSpecializations()[0]));
                for (VariableElement var : method.getParameters()) {
                    body.string(var.getSimpleName().toString());
                }
                body.end();
            }
            body.end();
            return method;
        }

        private CodeExecutableElement createCreateSpecializedMethod(NodeData node, Modifier visibility) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(), node.getNodeType(), "createSpecialized");
            if (visibility != null) {
                method.getModifiers().add(visibility);
            }
            method.getModifiers().add(Modifier.STATIC);

            method.addParameter(new CodeVariableElement(node.getNodeType(), THIS_NODE_LOCAL_VAR_NAME));
            method.addParameter(new CodeVariableElement(getContext().getType(Class.class), "specializationClass"));

            CodeTreeBuilder body = method.createBuilder();
            boolean first = true;
            for (TypeData type : node.getTypeSystem().getTypes()) {
                SpecializationData specialization = node.findUniqueSpecialization(type);
                if (specialization != null && !type.isGeneric()) {
                    if (first) {
                        body.startIf();
                        first = false;
                    } else {
                        body.startElseIf();
                    }
                    body.string("specializationClass == ").type(type.getBoxedType()).string(".class").end().startBlock();
                    body.startReturn().startNew(nodeClassName(specialization));
                    body.string(THIS_NODE_LOCAL_VAR_NAME);
                    body.end().end(); // new, return

                    body.end(); // if
                }
            }
            body.startReturn().startNew(nodeClassName(node.getGenericSpecialization()));
            body.string(THIS_NODE_LOCAL_VAR_NAME);
            body.end().end();
            return method;
        }

        private CodeExecutableElement createSpecializeMethod(NodeData node) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE, STATIC), node.getNodeType(), "specialize");
            method.addParameter(new CodeVariableElement(node.getNodeType(), THIS_NODE_LOCAL_VAR_NAME));
            method.addParameter(new CodeVariableElement(getContext().getType(Class.class), "minimumState"));
            addValueParameters(method, node.getGenericSpecialization(), false);

            CodeTreeBuilder body = method.createBuilder();
            body.startStatement().string("boolean allowed = (minimumState == ").string(nodeClassName(node.getSpecializations()[0])).string(".class)").end();

            for (int i = 1; i < node.getSpecializations().length; i++) {
                SpecializationData specialization = node.getSpecializations()[i];
                body.startStatement().string("allowed = allowed || (minimumState == ").string(nodeClassName(specialization)).string(".class)").end();

                if (specialization.isGeneric()) {
                    body.startIf().string("allowed").end().startBlock();
                } else {
                    body.startIf().string("allowed");
                    emitGuards(getContext(), body, " && ", specialization, true, true);
                    body.end().startBlock();
                }
                body.startReturn().startNew(nodeClassName(specialization));
                body.string(THIS_NODE_LOCAL_VAR_NAME);
                body.end().end();
                body.end(); // block
            }
            body.startThrow().startNew(getContext().getType(IllegalArgumentException.class)).end().end();

            return method;
        }

        private CodeExecutableElement createGeneratedGenericMethod(NodeData node) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE, STATIC), node.getGenericSpecialization().getReturnType().getActualType(), "generatedGeneric");
            method.addParameter(new CodeVariableElement(node.getNodeType(), THIS_NODE_LOCAL_VAR_NAME));
            addValueParameters(method, node.getGenericSpecialization(), true);

            CodeTreeBuilder builder = method.createBuilder();
            boolean ifStarted = false;
            for (int i = 0; i < node.getSpecializations().length; i++) {
                SpecializationData specialization = node.getSpecializations()[i];
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

                emitInvokeDoMethod(builder, specialization, 0);
                builder.end();
            }
            return method;
        }

        private void emitInvokeDoMethod(CodeTreeBuilder builder, SpecializationData specialization, int level) {
            if (specialization.getExceptions().length > 0) {
                builder.startTryBlock();
            }

            builder.startReturn();
            startCallOperationMethod(builder, specialization);
            addValueParameterNamesWithCasts(context, builder, specialization);
            builder.end().end(); // start call operation
            builder.end(); // return

            if (specialization.getExceptions().length > 0) {
                for (SpecializationThrowsData exception : specialization.getExceptions()) {
                    builder.end().startCatchBlock(exception.getJavaClass(), "ex" + level);
                    emitInvokeDoMethod(builder, exception.getTransitionTo(), level + 1);
                }
                builder.end();
            }
        }
    }

    private class SpecializedNodeFactory extends ClassElementFactory<SpecializationData> {

        public SpecializedNodeFactory(ProcessorContext context) {
            super(context);
        }

        @Override
        public CodeTypeElement create(SpecializationData specialization) {
            NodeData node = specialization.getNode();
            CodeTypeElement clazz = createClass(node, modifiers(PRIVATE, STATIC, FINAL), nodeClassName(specialization), node.getNodeType(), false);
            return clazz;
        }

        @Override
        protected void createChildren(SpecializationData specialization) {
            CodeTypeElement clazz = getElement();
            NodeData node = specialization.getNode();

            TypeElement superTypeElement = Utils.fromTypeMirror(clazz.getSuperclass());
            for (ExecutableElement constructor : ElementFilter.constructorsIn(superTypeElement.getEnclosedElements())) {
                ExecutableElement superConstructor = createSuperConstructor(clazz, constructor);
                if (superConstructor != null) {
                    clazz.add(superConstructor);
                }
            }

            for (ExecutableTypeData execType : node.getExecutableTypes()) {
                if (execType.isFinal()) {
                    continue;
                }
                CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), execType.getMethod());
                if (method.getParameters().size() == 1) {
                    CodeVariableElement var = CodeVariableElement.clone(method.getParameters().get(0));
                    var.setName("frame");
                    method.getParameters().set(0, var);
                }
                method.getModifiers().remove(Modifier.ABSTRACT);
                clazz.add(method);

                TypeData primaryType = specialization.getReturnType().getActualTypeData(node.getTypeSystem());
                if (primaryType == execType.getType()) {
                    buildFunctionalExecuteMethod(method.createBuilder(), specialization);
                } else {
                    buildCastingExecuteMethod(method.createBuilder(), specialization, execType.getType());
                }
            }

            if (node.needsRewrites(getContext()) && !specialization.isGeneric() && !specialization.isUninitialized()) {
                buildSpecializeStateMethod(clazz, specialization);
            }
        }

        private void buildCastingExecuteMethod(CodeTreeBuilder builder, SpecializationData specialization, TypeData type) {
            NodeData node = specialization.getNode();
            TypeSystemData typeSystem = node.getTypeSystem();

            ExecutableTypeData castedType = node.findExecutableType(type);
            TypeData primaryType = specialization.getReturnType().getActualTypeData(typeSystem);
            ExecutableTypeData execType = specialization.getNode().findExecutableType(primaryType);

            boolean needsTry = execType.hasUnexpectedValue(getContext());
            boolean returnVoid = type.isVoid();

            CodeTree primaryExecuteCall = null;

            CodeTreeBuilder executeBuilder = CodeTreeBuilder.createBuilder();
            buildExecute(executeBuilder, null, execType);
            primaryExecuteCall = executeBuilder.getRoot();

            if (needsTry) {
                if (!returnVoid) {
                    builder.declaration(primaryType.getPrimitiveType(), "value");
                }
                builder.startTryBlock();

                if (returnVoid) {
                    builder.statement(primaryExecuteCall);
                } else {
                    builder.startStatement();
                    builder.string("value = ");
                    builder.tree(primaryExecuteCall);
                    builder.end();
                }

                builder.end().startCatchBlock(getUnexpectedValueException(), "ex");
                if (returnVoid) {
                    builder.string("// ignore").newLine();
                } else {
                    builder.startReturn();
                    builder.tree(castPrimaryExecute(node, castedType, CodeTreeBuilder.singleString("ex.getResult()")));
                    builder.end();
                }
                builder.end();

                if (!returnVoid) {
                    builder.startReturn();
                    builder.tree(castPrimaryExecute(node, castedType, CodeTreeBuilder.singleString("value")));
                    builder.end();
                }
            } else {
                if (returnVoid) {
                    builder.statement(primaryExecuteCall);
                } else {
                    builder.startReturn();
                    builder.tree(castPrimaryExecute(node, castedType, primaryExecuteCall));
                    builder.end();
                }
            }
        }

        private CodeTree castPrimaryExecute(NodeData node, ExecutableTypeData castedType, CodeTree value) {
            if (castedType.getType().isVoid()) {
                return value;
            }
            if (castedType.getType().isGeneric()) {
                return value;
            }

            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            if (castedType.hasUnexpectedValue(getContext())) {
                startCallTypeSystemMethod(getContext(), builder, node, TypeSystemCodeGenerator.expectTypeMethodName(castedType.getType()));
            } else {
                startCallTypeSystemMethod(getContext(), builder, node, TypeSystemCodeGenerator.asTypeMethodName(castedType.getType()));
            }
            builder.tree(value);
            builder.end().end();
            return builder.getRoot();
        }

        private void buildFunctionalExecuteMethod(CodeTreeBuilder builder, SpecializationData specialization) {
            NodeData node = specialization.getNode();
            TypeSystemData typeSystem = node.getTypeSystem();

            for (NodeFieldData field : node.getFields()) {
                if (field.getExecutionKind() == ExecutionKind.IGNORE) {
                    continue;
                }

                ActualParameter parameterType = specialization.findParameter(field.getName());

                if (parameterType.getActualTypeData(typeSystem).isGeneric()) {
                    buildGenericValueExecute(builder, specialization, field, null);
                } else {
                    buildSpecializedValueExecute(builder, specialization, field);
                }
            }

            if (specialization.hasDynamicGuards()) {
                builder.startIf();
                emitGuards(getContext(), builder, "", specialization, false, false);
                builder.end().startBlock();
            }
            if (specialization.getExceptions().length > 0) {
                builder.startTryBlock();
            }

            if (specialization.isUninitialized()) {
                for (TemplateMethod listener : node.getSpecializationListeners()) {
                    builder.startStatement();
                    startCallOperationMethod(builder, listener);
                    addValueParameterNames(builder, listener, null, false);
                    builder.end().end();
                    builder.end(); // statement
                }

                builder.startStatement();
                builder.startCall("replace");
                if (node.needsRewrites(getContext())) {
                    builder.startCall(factoryClassName(node), "specialize");
                    builder.string("this");
                    builder.typeLiteral(builder.getRoot().getEnclosingClass().asType());
                    addValueParameterNames(builder, specialization, null, false);
                    builder.end(); // call replace, call specialize
                } else {
                    builder.startCall(factoryClassName(node), "createSpecialized").string("this").string("null").end();
                }
                builder.end().end();
            }

            if ((specialization.isUninitialized() || specialization.isGeneric()) && node.needsRewrites(getContext())) {
                builder.startReturn().startCall(factoryClassName(node), "generatedGeneric");
                builder.string("this");
                addValueParameterNames(builder, specialization, null, true);
                builder.end().end();
            } else {
                builder.startReturn();

                if (specialization.isUninitialized()) {
                    startCallOperationMethod(builder, specialization.getNode().getGenericSpecialization());
                } else {
                    startCallOperationMethod(builder, specialization);
                }
                addValueParameterNames(builder, specialization, null, false);
                builder.end().end(); // operation call
                builder.end(); // return
            }

            if (specialization.getExceptions().length > 0) {
                for (SpecializationThrowsData exception : specialization.getExceptions()) {
                    builder.end().startCatchBlock(exception.getJavaClass(), "ex");
                    buildThrowSpecialize(builder, specialization, exception.getTransitionTo(), null);
                }
                builder.end();
            }
            if (specialization.hasDynamicGuards()) {
                builder.end().startElseBlock();
                buildThrowSpecialize(builder, specialization, specialization.findNextSpecialization(), null);
                builder.end();
            }
        }

        private void buildGenericValueExecute(CodeTreeBuilder builder, SpecializationData specialization, NodeFieldData field, NodeFieldData exceptionSpec) {
            ActualParameter specParameter = specialization.findParameter(field.getName());

            boolean shortCircuit = startShortCircuit(builder, specialization, field, exceptionSpec);

            builder.startStatement();
            if (!shortCircuit) {
                builder.type(specialization.getNode().getTypeSystem().getGenericType());
                builder.string(" ");
            }

            builder.string(valueName(specialization, specParameter));
            builder.string(" = ");
            ExecutableTypeData genericExecutableType = field.getNodeData().findGenericExecutableType(getContext());
            if (genericExecutableType == null) {
                throw new AssertionError("Must have generic executable type. Parser validation most likely failed. " + Arrays.toString(field.getNodeData().getExecutableTypes()));
            }
            buildExecute(builder, field, genericExecutableType);
            builder.end();

            endShortCircuit(builder, shortCircuit);
        }

        private void buildExecute(CodeTreeBuilder builder, NodeFieldData field, ExecutableTypeData execType) {
            if (field != null) {
                Element accessElement = field.getAccessElement();
                if (accessElement.getKind() == ElementKind.METHOD) {
                    builder.startCall(accessElement.getSimpleName().toString()).end();
                } else if (accessElement.getKind() == ElementKind.FIELD) {
                    builder.string("this.").string(accessElement.getSimpleName().toString());
                } else {
                    throw new AssertionError();
                }
                builder.string(".");
            }
            builder.startCall(execType.getMethodName());
            if (execType.getParameters().length == 1) {
                builder.string("frame");
            }
            builder.end();
        }

        private void buildSpecializedValueExecute(CodeTreeBuilder builder, SpecializationData specialization, NodeFieldData field) {
            ActualParameter param = specialization.findParameter(field.getName());
            boolean shortCircuit = startShortCircuit(builder, specialization, field, null);

            if (!shortCircuit) {
                builder.startStatement().type(param.getActualType()).string(" ").string(valueName(specialization, param)).end();
            }

            ExecutableTypeData execType = field.getNodeData().findExecutableType(param.getActualTypeData(field.getNodeData().getTypeSystem()));

            if (execType.hasUnexpectedValue(getContext())) {
                builder.startTryBlock();
            }

            builder.startStatement().string(valueName(field)).string(" = ");
            buildExecute(builder, field, execType);
            builder.end();

            if (execType.hasUnexpectedValue(getContext())) {
                builder.end().startCatchBlock(getUnexpectedValueException(), "ex");
                boolean execute = false;
                for (NodeFieldData exField : specialization.getNode().getFields()) {
                    if (exField.getExecutionKind() == ExecutionKind.IGNORE) {
                        continue;
                    }
                    if (execute) {
                        buildGenericValueExecute(builder, specialization.getNode().getGenericSpecialization(), exField, field);
                    } else if (exField == field) {
                        execute = true;
                    }
                }
                buildThrowSpecialize(builder, specialization, specialization.findNextSpecialization(), param.getSpecification());
                builder.end(); // catch block
            }

            endShortCircuit(builder, shortCircuit);
            builder.newLine();
        }

        private boolean startShortCircuit(CodeTreeBuilder builder, SpecializationData specialization, NodeFieldData forField, NodeFieldData exceptionField) {
            if (forField.getExecutionKind() != ExecutionKind.SHORT_CIRCUIT) {
                return false;
            }

            ActualParameter parameter = specialization.findParameter(forField.getName());
            ActualParameter shortCircuitParam = specialization.getPreviousParam(parameter);

            int shortCircuitIndex = 0;
            for (NodeFieldData field : specialization.getNode().getFields()) {
                if (field.getExecutionKind() == ExecutionKind.SHORT_CIRCUIT) {
                    if (field == forField) {
                        break;
                    }
                    shortCircuitIndex++;
                }
            }

            builder.startStatement().type(shortCircuitParam.getActualType()).string(" ").string(valueName(specialization, shortCircuitParam)).string(" = ");
            ShortCircuitData shortCircuitData = specialization.getShortCircuits()[shortCircuitIndex];

            startCallOperationMethod(builder, shortCircuitData);
            addValueParameterNames(builder, shortCircuitData, exceptionField != null ? exceptionField.getName() : null, false);
            builder.end().end(); // call operation

            builder.end(); // statement

            builder.declaration(parameter.getActualType(), valueName(specialization, parameter), CodeTreeBuilder.createBuilder().defaultValue(parameter.getActualType()));
            builder.startIf().string(shortCircuitParam.getSpecification().getName()).end();
            builder.startBlock();

            return true;
        }

        private void endShortCircuit(CodeTreeBuilder builder, boolean shortCircuit) {
            if (shortCircuit) {
                builder.end();
            }
        }

        private void buildThrowSpecialize(CodeTreeBuilder builder, SpecializationData currentSpecialization, SpecializationData nextSpecialization, ParameterSpec exceptionSpec) {
            boolean canThrowUnexpected = Utils.canThrowType(builder.findMethod().getThrownTypes(), getContext().getTruffleTypes().getUnexpectedValueException());

            CodeTreeBuilder specializeCall = CodeTreeBuilder.createBuilder();
            specializeCall.startCall("specialize");
            specializeCall.string(nodeClassName(nextSpecialization) + ".class");
            addValueParameterNames(specializeCall, nextSpecialization.getNode().getGenericSpecialization(), exceptionSpec != null ? exceptionSpec.getName() : null, true);
            specializeCall.end().end();

            TypeData expectedType = currentSpecialization.getReturnType().getActualTypeData(currentSpecialization.getNode().getTypeSystem());
            if (canThrowUnexpected) {
                builder.startReturn();
                startCallTypeSystemMethod(context, builder, currentSpecialization.getNode(), TypeSystemCodeGenerator.expectTypeMethodName(expectedType));
                builder.tree(specializeCall.getRoot());
                builder.end().end();
                builder.end(); // return
            } else {
                builder.startReturn();
                if (!expectedType.isVoid() && !expectedType.isGeneric()) {
                    startCallTypeSystemMethod(context, builder, currentSpecialization.getNode(), TypeSystemCodeGenerator.asTypeMethodName(expectedType));
                    builder.tree(specializeCall.getRoot());
                    builder.end().end();
                } else {
                    builder.tree(specializeCall.getRoot());
                }
                builder.end();
            }

        }

        private void buildSpecializeStateMethod(CodeTypeElement clazz, SpecializationData specialization) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE), specialization.getNode().getTypeSystem().getGenericType(), "specialize");
            method.addParameter(new CodeVariableElement(getContext().getType(Class.class), "minimumState"));
            addValueParameters(method, specialization.getNode().getGenericSpecialization(), true);
            clazz.add(method);

            CodeTreeBuilder builder = method.createBuilder();
            for (TemplateMethod listener : specialization.getNode().getSpecializationListeners()) {
                builder.startStatement();
                startCallOperationMethod(builder, listener);
                addValueParameterNames(builder, listener, null, false);
                builder.end().end(); // call operation
                builder.end(); // statement
            }

            builder.startStatement();
            builder.startCall("replace");
            builder.startCall(factoryClassName(specialization.getNode()), "specialize").string("this").string("minimumState");
            addValueParameterNames(builder, specialization.getNode().getGenericSpecialization(), null, false);
            builder.end();
            builder.end(); // call replace
            builder.end(); // statement

            ExecutableElement generatedGeneric = clazz.getEnclosingClass().getMethod("generatedGeneric");

            CodeTreeBuilder genericBuilder = CodeTreeBuilder.createBuilder();
            genericBuilder.startCall(factoryClassName(specialization.getNode()), "generatedGeneric");
            genericBuilder.string("this");
            addValueParameterNames(genericBuilder, specialization.getNode().getGenericSpecialization(), null, true);
            genericBuilder.end(); // call generated generic

            if (Utils.isVoid(generatedGeneric.getReturnType())) {
                builder.declaration(generatedGeneric.getReturnType(), "genericResult", genericBuilder.getRoot());
                builder.startReturn().string("null").end();
            } else {
                builder.startReturn().tree(genericBuilder.getRoot()).end();
            }
        }

    }

}
