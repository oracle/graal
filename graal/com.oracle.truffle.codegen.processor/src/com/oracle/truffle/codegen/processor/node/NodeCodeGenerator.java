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

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.ast.*;
import com.oracle.truffle.codegen.processor.node.NodeChildData.Cardinality;
import com.oracle.truffle.codegen.processor.node.NodeChildData.ExecutionKind;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class NodeCodeGenerator extends CompilationUnitFactory<NodeData> {

    private static final String THIS_NODE_LOCAL_VAR_NAME = "thisNode";

    private static final String EXECUTE_GENERIC_NAME = "executeGeneric_";
    private static final String EXECUTE_SPECIALIZE_NAME = "executeAndSpecialize_";

    public NodeCodeGenerator(ProcessorContext context) {
        super(context);
    }

    private TypeMirror getUnexpectedValueException() {
        return getContext().getTruffleTypes().getUnexpectedValueException();
    }

    private static String factoryClassName(NodeData node) {
        return node.getNodeId() + "Factory";
    }

    private static String nodeSpecializationClassName(SpecializationData specialization) {
        String nodeid = specialization.getNode().getNodeId();
        if (nodeid.endsWith("Node") && !nodeid.equals("Node")) {
            nodeid = nodeid.substring(0, nodeid.length() - 4);
        }

        String name = Utils.firstLetterUpperCase(nodeid);
        name += Utils.firstLetterUpperCase(specialization.getId());
        name += "Node";
        return name;
    }

    private static String valueNameEvaluated(ActualParameter targetParameter) {
        return valueName(targetParameter) + "Evaluated";
    }

    private static String valueName(ActualParameter param) {
        return param.getLocalName();
    }

    private static String castValueName(ActualParameter parameter) {
        return valueName(parameter) + "Cast";
    }

    private void addInternalValueParameters(CodeExecutableElement method, TemplateMethod specialization, boolean forceFrame) {
        if (forceFrame && specialization.getSpecification().findParameterSpec("frame") != null) {
            method.addParameter(new CodeVariableElement(getContext().getTruffleTypes().getFrame(), "frameValue"));
        }
        for (ActualParameter parameter : specialization.getParameters()) {
            ParameterSpec spec = parameter.getSpecification();
            if (forceFrame && spec.getName().equals("frame")) {
                continue;
            }
            if (spec.isLocal()) {
                continue;
            }

            method.addParameter(new CodeVariableElement(parameter.getType(), valueName(parameter)));
        }
    }

    private void addInternalValueParameterNames(CodeTreeBuilder builder, TemplateMethod source, TemplateMethod specialization, String unexpectedValueName, boolean forceFrame, boolean includeImplicit) {
        if (forceFrame && specialization.getSpecification().findParameterSpec("frame") != null) {
            builder.string("frameValue");
        }
        for (ActualParameter parameter : specialization.getParameters()) {
            ParameterSpec spec = parameter.getSpecification();
            if (forceFrame && spec.getName().equals("frame")) {
                continue;
            }

            if (!includeImplicit && (parameter.isImplicit())) {
                continue;
            }
            if (parameter.getSpecification().isLocal()) {
                continue;
            }

            ActualParameter sourceParameter = source.findParameter(parameter.getLocalName());

            if (unexpectedValueName != null && parameter.getLocalName().equals(unexpectedValueName)) {
                builder.cast(parameter.getType(), CodeTreeBuilder.singleString("ex.getResult()"));
            } else if (sourceParameter != null) {
                builder.string(valueName(sourceParameter, parameter));
            } else {
                builder.string(valueName(parameter));
            }
        }
    }

    private String valueName(ActualParameter sourceParameter, ActualParameter targetParameter) {
        if (sourceParameter != null) {
            if (!sourceParameter.getSpecification().isSignature()) {
                return valueName(targetParameter);
            } else if (sourceParameter.getTypeSystemType() != null && targetParameter.getTypeSystemType() != null) {
                if (sourceParameter.getTypeSystemType().needsCastTo(getContext(), targetParameter.getTypeSystemType())) {
                    return castValueName(targetParameter);
                }
            }
            return valueName(targetParameter);
        } else {
            return valueName(targetParameter);
        }
    }

    private CodeTree createTemplateMethodCall(CodeTreeBuilder parent, CodeTree target, TemplateMethod sourceMethod, TemplateMethod targetMethod, String unexpectedValueName) {
        CodeTreeBuilder builder = parent.create();

        boolean castedValues = sourceMethod != targetMethod;

        builder.startGroup();
        ExecutableElement method = targetMethod.getMethod();
        if (method == null) {
            throw new IllegalStateException("Cannot call synthetic operation methods.");
        }
        TypeElement targetClass = Utils.findNearestEnclosingType(method.getEnclosingElement());
        NodeData node = (NodeData) targetMethod.getTemplate();

        if (target == null) {
            boolean accessible = targetMethod.canBeAccessedByInstanceOf(getContext(), node.getNodeType());
            if (accessible) {
                if (builder.findMethod().getModifiers().contains(STATIC)) {
                    if (method.getModifiers().contains(STATIC)) {
                        builder.type(targetClass.asType());
                    } else {
                        builder.string(THIS_NODE_LOCAL_VAR_NAME);
                    }
                } else {
                    if (targetMethod instanceof ExecutableTypeData) {
                        builder.string("this");
                    } else {
                        builder.string("super");
                    }
                }
            } else {
                if (method.getModifiers().contains(STATIC)) {
                    builder.type(targetClass.asType());
                } else {
                    ActualParameter parameter = null;
                    for (ActualParameter searchParameter : targetMethod.getParameters()) {
                        if (searchParameter.getSpecification().isSignature()) {
                            parameter = searchParameter;
                            break;
                        }
                    }
                    ActualParameter sourceParameter = sourceMethod.findParameter(parameter.getLocalName());
                    assert parameter != null;

                    if (castedValues && sourceParameter != null) {
                        builder.string(valueName(sourceParameter, parameter));
                    } else {
                        builder.string(valueName(parameter));
                    }
                }
            }
            builder.string(".");
        } else {
            builder.tree(target);
        }
        builder.startCall(method.getSimpleName().toString());

        for (ActualParameter targetParameter : targetMethod.getParameters()) {
            ActualParameter valueParameter = sourceMethod.findParameter(targetParameter.getLocalName());
            if (valueParameter == null) {
                valueParameter = targetParameter;
            }
            TypeData targetType = targetParameter.getTypeSystemType();

            if (targetParameter.isImplicit() || valueParameter.isImplicit()) {
                continue;
            }

            TypeData valueType = null;
            if (valueParameter != null) {
                valueType = valueParameter.getTypeSystemType();
            }

            if (targetParameter.getSpecification().isLocal()) {
                builder.startGroup();
                if (builder.findMethod().getModifiers().contains(Modifier.STATIC)) {
                    builder.string(THIS_NODE_LOCAL_VAR_NAME).string(".");
                } else {
                    builder.string("this.");
                }
                builder.string(targetParameter.getSpecification().getName());
                builder.end();
            } else if (unexpectedValueName != null && targetParameter.getLocalName().equals(unexpectedValueName)) {
                builder.string("ex.getResult()");
            } else if (targetType == null || targetType.isGeneric() || (valueType != null && valueType.equalsType(targetType))) {
                builder.startGroup();

                if (valueType != null && sourceMethod.getMethodName().equals(targetMethod.getMethodName()) && !valueType.isGeneric() && targetType.isGeneric()) {
                    builder.string("(");
                    builder.type(targetType.getPrimitiveType());
                    builder.string(") ");
                }
                builder.string(valueName(targetParameter));
                builder.end();
            } else {
                builder.string(castValueName(targetParameter));
            }
        }

        builder.end().end();

        return builder.getRoot();
    }

    private static String baseClassName(NodeData node) {
        String nodeid = node.getNodeId();
        if (nodeid.endsWith("Node") && !nodeid.equals("Node")) {
            nodeid = nodeid.substring(0, nodeid.length() - 4);
        }
        String name = Utils.firstLetterUpperCase(nodeid);
        name += "BaseNode";
        return name;
    }

    private static CodeTree createCallTypeSystemMethod(ProcessorContext context, CodeTreeBuilder parent, NodeData node, String methodName, CodeTree value) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        startCallTypeSystemMethod(context, builder, node, methodName);
        builder.tree(value);
        builder.end().end();
        return builder.getRoot();
    }

    private static void startCallTypeSystemMethod(ProcessorContext context, CodeTreeBuilder body, NodeData node, String methodName) {
        VariableElement singleton = TypeSystemCodeGenerator.findSingleton(context, node.getTypeSystem());
        assert singleton != null;

        body.startGroup();
        body.staticReference(singleton.getEnclosingElement().asType(), singleton.getSimpleName().toString());
        body.string(".").startCall(methodName);
    }

    private CodeTree createGuardAndCast(CodeTreeBuilder parent, String conditionPrefix, SpecializationData sourceSpecialization, SpecializationData targetSpecialization, boolean castValues,
                    CodeTree guardedStatements, CodeTree elseStatements, boolean emitAssumptions) {

        NodeData node = targetSpecialization.getNode();
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        CodeTree implicitGuards = createImplicitGuards(parent, conditionPrefix, sourceSpecialization, targetSpecialization, emitAssumptions);
        CodeTree explicitGuards = createExplicitGuards(parent, implicitGuards == null ? conditionPrefix : null, sourceSpecialization, targetSpecialization);

        Set<String> valuesNeedsCast;
        if (castValues) {
            // cast all
            valuesNeedsCast = null;
        } else {
            // find out which values needs a cast
            valuesNeedsCast = new HashSet<>();
            for (GuardData guard : targetSpecialization.getGuards()) {
                for (ActualParameter targetParameter : guard.getParameters()) {
                    NodeChildData field = node.findChild(targetParameter.getSpecification().getName());
                    if (field == null) {
                        continue;
                    }
                    TypeData targetType = targetParameter.getTypeSystemType();
                    ActualParameter sourceParameter = sourceSpecialization.findParameter(targetParameter.getLocalName());
                    if (sourceParameter == null) {
                        sourceParameter = targetParameter;
                    }
                    TypeData sourceType = sourceParameter.getTypeSystemType();

                    if (sourceType.needsCastTo(getContext(), targetType)) {
                        valuesNeedsCast.add(targetParameter.getLocalName());
                    }
                }
            }
        }

        int ifCount = 0;

        if (implicitGuards != null) {
            builder.startIf();
            builder.tree(implicitGuards);
            builder.end();
            builder.startBlock();
            ifCount++;
        }

        builder.tree(createCasts(parent, valuesNeedsCast, sourceSpecialization, targetSpecialization));

        if (explicitGuards != null) {
            builder.startIf();
            builder.tree(explicitGuards);
            builder.end();
            builder.startBlock();
            ifCount++;
        }

        if (implicitGuards == null && explicitGuards == null && conditionPrefix != null && !conditionPrefix.isEmpty()) {
            builder.startIf();
            builder.string(conditionPrefix);
            builder.end().startBlock();
            ifCount++;
        }

        builder.tree(guardedStatements);

        builder.end(ifCount);
        if (elseStatements != null && ifCount > 0) {
            builder.tree(elseStatements);
        }
        return builder.getRoot();
    }

    private CodeTree createExplicitGuards(CodeTreeBuilder parent, String conditionPrefix, TemplateMethod valueSpecialization, SpecializationData guardedSpecialization) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        String andOperator = conditionPrefix != null ? conditionPrefix + " && " : "";
        if (guardedSpecialization.getGuards().size() > 0) {
            // Explicitly specified guards
            for (GuardData guard : guardedSpecialization.getGuards()) {
                builder.string(andOperator);
                builder.tree(createTemplateMethodCall(parent, null, valueSpecialization, guard, null));
                andOperator = " && ";
            }
        }

        return builder.isEmpty() ? null : builder.getRoot();
    }

    private CodeTree createCasts(CodeTreeBuilder parent, Set<String> castWhiteList, TemplateMethod valueSpecialization, SpecializationData guardedSpecialization) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        // Implict guards based on method signature
        for (ActualParameter guardedParam : guardedSpecialization.getParameters()) {
            NodeChildData field = guardedSpecialization.getNode().findChild(guardedParam.getSpecification().getName());
            if (field == null) {
                continue;
            }
            ActualParameter valueParam = valueSpecialization.findParameter(guardedParam.getLocalName());

            if (valueParam == null) {
                /*
                 * If used inside a function execute method. The value param may not exist. In that
                 * case it assumes that the value is already converted.
                 */
                valueParam = guardedParam;
            }

            if (castWhiteList != null && !castWhiteList.contains(guardedParam.getLocalName())) {
                continue;
            }

            CodeTree cast = createCast(parent, field, valueParam, guardedParam);
            if (cast == null) {
                continue;
            }
            builder.tree(cast);
        }

        return builder.getRoot();
    }

    private CodeTree createImplicitGuards(CodeTreeBuilder parent, String conditionPrefix, SpecializationData valueSpecialization, SpecializationData guardedSpecialization, boolean emitAssumptions) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        // Implict guards based on method signature
        String andOperator = conditionPrefix != null ? conditionPrefix + " && " : "";

        if (emitAssumptions) {
            for (String assumption : guardedSpecialization.getAssumptions()) {
                builder.string(andOperator);
                builder.string("this");
                builder.string(".").string(assumption).string(".isValid()");
                andOperator = " && ";
            }
        }

        for (ActualParameter guardedParam : guardedSpecialization.getParameters()) {
            NodeChildData field = guardedSpecialization.getNode().findChild(guardedParam.getSpecification().getName());
            if (field == null) {
                continue;
            }
            ActualParameter valueParam = valueSpecialization.findParameter(guardedParam.getLocalName());

            if (valueParam == null) {
                /*
                 * If used inside a function execute method. The value param may not exist. In that
                 * case it assumes that the value is already converted.
                 */
                valueParam = guardedParam;
            }

            CodeTree implicitGuard = createImplicitGuard(builder, field, valueParam, guardedParam);
            if (implicitGuard == null) {
                continue;
            }

            builder.string(andOperator);
            builder.tree(implicitGuard);
            andOperator = " && ";
        }

        return builder.isEmpty() ? null : builder.getRoot();
    }

    private CodeTree createImplicitGuard(CodeTreeBuilder parent, NodeChildData field, ActualParameter source, ActualParameter target) {
        NodeData node = field.getNodeData();
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);

        TypeData targetType = target.getTypeSystemType();
        TypeData sourceType = source.getTypeSystemType();

        if (!sourceType.needsCastTo(getContext(), targetType)) {
            return null;
        }

        builder.startGroup();

        if (field.isShortCircuit()) {
            ActualParameter shortCircuit = target.getPreviousParameter();
            assert shortCircuit != null;
            builder.string("(");
            builder.string("!").string(valueName(shortCircuit));
            builder.string(" || ");
        }

        startCallTypeSystemMethod(getContext(), builder, node, TypeSystemCodeGenerator.isTypeMethodName(target.getTypeSystemType()));
        builder.string(valueName(source));
        builder.end().end(); // call

        if (field.isShortCircuit()) {
            builder.string(")");
        }

        builder.end(); // group

        return builder.getRoot();
    }

    private CodeTree createCast(CodeTreeBuilder parent, NodeChildData field, ActualParameter source, ActualParameter target) {
        NodeData node = field.getNodeData();
        TypeData sourceType = source.getTypeSystemType();
        TypeData targetType = target.getTypeSystemType();

        if (!sourceType.needsCastTo(getContext(), targetType)) {
            return null;
        }

        CodeTree condition = null;
        if (field.isShortCircuit()) {
            ActualParameter shortCircuit = target.getPreviousParameter();
            assert shortCircuit != null;
            condition = CodeTreeBuilder.singleString(valueName(shortCircuit));
        }

        CodeTree value = createCallTypeSystemMethod(context, parent, node, TypeSystemCodeGenerator.asTypeMethodName(targetType), CodeTreeBuilder.singleString(valueName(target)));

        return createLazyAssignment(parent, castValueName(target), target.getType(), condition, value);
    }

    /**
     * <pre>
     * variant1 $condition != null
     * 
     * $type $name = defaultValue($type);
     * if ($condition) {
     *     $name = $value;
     * }
     * 
     * variant2 $condition != null
     * $type $name = $value;
     * </pre>
     * 
     * .
     */
    private static CodeTree createLazyAssignment(CodeTreeBuilder parent, String name, TypeMirror type, CodeTree condition, CodeTree value) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        if (condition == null) {
            builder.declaration(type, name, value);
        } else {
            builder.declaration(type, name, new CodeTreeBuilder(parent).defaultValue(type).getRoot());

            builder.startIf().tree(condition).end();
            builder.startBlock();
            builder.startStatement();
            builder.string(name);
            builder.string(" = ");
            builder.tree(value);
            builder.end(); // statement
            builder.end(); // block
        }
        return builder.getRoot();
    }

    private void emitEncounteredSynthetic(CodeTreeBuilder builder) {
        builder.startThrow().startNew(getContext().getType(UnsupportedOperationException.class)).end().end();
    }

    private static List<ExecutableElement> findUserConstructors(TypeMirror nodeType) {
        List<ExecutableElement> constructors = new ArrayList<>();
        for (ExecutableElement constructor : ElementFilter.constructorsIn(Utils.fromTypeMirror(nodeType).getEnclosedElements())) {
            if (constructor.getModifiers().contains(PRIVATE)) {
                continue;
            }
            if (isCopyConstructor(constructor)) {
                continue;
            }
            constructors.add(constructor);
        }

        if (constructors.isEmpty()) {
            constructors.add(new CodeExecutableElement(null, Utils.getSimpleName(nodeType)));
        }

        return constructors;
    }

    private static ExecutableElement findCopyConstructor(TypeMirror type) {
        for (ExecutableElement constructor : ElementFilter.constructorsIn(Utils.fromTypeMirror(type).getEnclosedElements())) {
            if (constructor.getModifiers().contains(PRIVATE)) {
                continue;
            }
            if (isCopyConstructor(constructor)) {
                return constructor;
            }
        }

        return null;
    }

    private static boolean isCopyConstructor(ExecutableElement element) {
        if (element.getParameters().size() != 1) {
            return false;
        }
        VariableElement var = element.getParameters().get(0);
        TypeElement type = Utils.findNearestEnclosingType(var);

        if (!Utils.typeEquals(var.asType(), type.asType())) {
            return false;
        }
        return true;
    }

    private static CodeTree createReturnNewSpecialization(CodeTreeBuilder parent, SpecializationData specialization, String thisLocalVariableName, boolean hasCopyConstructor) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        builder.startReturn().startNew(nodeSpecializationClassName(specialization));
        if (hasCopyConstructor) {
            builder.string(thisLocalVariableName);
        }
        builder.end().end();
        return builder.getRoot();
    }

    @Override
    protected void createChildren(NodeData node) {
        Map<NodeData, List<TypeElement>> childTypes = new LinkedHashMap<>();
        if (node.getDeclaredNodes() != null && !node.getDeclaredNodes().isEmpty()) {
            for (NodeData nodeChild : node.getDeclaredNodes()) {
                NodeCodeGenerator generator = new NodeCodeGenerator(getContext());
                childTypes.put(nodeChild, generator.process(null, nodeChild).getEnclosedElements());
            }
        }

        if (node.needsFactory() || node.getNodeDeclaringChildren().size() > 0) {
            add(new NodeFactoryFactory(context, childTypes), node);
        }
    }

    private class NodeBaseFactory extends ClassElementFactory<NodeData> {

        public NodeBaseFactory(ProcessorContext context) {
            super(context);
        }

        @Override
        protected CodeTypeElement create(NodeData node) {
            CodeTypeElement clazz = createClass(node, modifiers(PRIVATE, ABSTRACT, STATIC), baseClassName(node), node.getNodeType(), false);

            for (NodeChildData child : node.getChildren()) {
                clazz.add(createChildField(child));

                if (child.getAccessElement() != null && child.getAccessElement().getModifiers().contains(Modifier.ABSTRACT)) {
                    ExecutableElement getter = (ExecutableElement) child.getAccessElement();
                    CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), getter);
                    method.getModifiers().remove(Modifier.ABSTRACT);
                    method.createBuilder().startReturn().string("this.").string(child.getName()).end();
                    clazz.add(method);
                }
            }

            for (String assumption : node.getAssumptions()) {
                clazz.add(createAssumptionField(assumption));
            }

            createConstructors(node, clazz);

            if (node.getExtensionElements() != null) {
                clazz.getEnclosedElements().addAll(node.getExtensionElements());
            }

            return clazz;
        }

        @Override
        protected void createChildren(NodeData node) {
            CodeTypeElement clazz = getElement();

            if (node.needsRewrites(context)) {
                clazz.add(createGenericExecute(node, EXECUTE_SPECIALIZE_NAME, true));
            }

            if (node.getGenericSpecialization() != null) {
                clazz.add(createGenericExecute(node, EXECUTE_GENERIC_NAME, false));
            }
        }

        private void createConstructors(NodeData node, CodeTypeElement clazz) {
            List<ExecutableElement> constructors = findUserConstructors(node.getNodeType());
            if (constructors.isEmpty()) {
                clazz.add(createUserConstructor(clazz, null));
            } else {
                for (ExecutableElement constructor : constructors) {
                    clazz.add(createUserConstructor(clazz, constructor));
                }
            }
            if (node.needsRewrites(getContext())) {
                clazz.add(createCopyConstructor(clazz, findCopyConstructor(node.getNodeType())));
            }
        }

        private CodeExecutableElement createUserConstructor(CodeTypeElement type, ExecutableElement superConstructor) {
            CodeExecutableElement method = new CodeExecutableElement(null, type.getSimpleName().toString());
            CodeTreeBuilder builder = method.createBuilder();

            if (superConstructor != null) {
                for (VariableElement param : superConstructor.getParameters()) {
                    method.getParameters().add(CodeVariableElement.clone(param));
                }
            }

            for (VariableElement var : type.getFields()) {
                method.getParameters().add(new CodeVariableElement(var.asType(), var.getSimpleName().toString()));
            }

            if (superConstructor != null) {
                builder.startStatement().startSuperCall();
                for (VariableElement param : superConstructor.getParameters()) {
                    builder.string(param.getSimpleName().toString());
                }
                builder.end().end();
            }

            for (VariableElement var : type.getFields()) {
                builder.startStatement();
                String varName = var.getSimpleName().toString();
                builder.string("this.").string(varName);
                if (Utils.isAssignable(getContext(), var.asType(), getContext().getTruffleTypes().getNode())) {
                    builder.string(" = adoptChild(").string(varName).string(")");
                } else if (Utils.isAssignable(getContext(), var.asType(), getContext().getTruffleTypes().getNodeArray())) {
                    builder.string(" = adoptChildren(").string(varName).string(")");
                } else {
                    builder.string(" = ").string(varName);
                }
                builder.end();
            }
            return method;
        }

        private CodeExecutableElement createCopyConstructor(CodeTypeElement type, ExecutableElement superConstructor) {
            CodeExecutableElement method = new CodeExecutableElement(null, type.getSimpleName().toString());
            CodeTreeBuilder builder = method.createBuilder();
            if (!(superConstructor == null && type.getFields().isEmpty())) {
                method.getParameters().add(new CodeVariableElement(type.asType(), "copy"));
            }

            if (superConstructor != null) {
                builder.startStatement().startSuperCall().string("copy").end().end();
            }

            for (VariableElement var : type.getFields()) {
                builder.startStatement();
                String varName = var.getSimpleName().toString();
                builder.string("this.").string(varName);
                if (Utils.isAssignable(getContext(), var.asType(), getContext().getTruffleTypes().getNode())) {
                    builder.string(" = adoptChild(copy.").string(varName).string(")");
                } else if (Utils.isAssignable(getContext(), var.asType(), getContext().getTruffleTypes().getNodeArray())) {
                    builder.string(" = adoptChildren(copy.").string(varName).string(")");
                } else {
                    builder.string(" = copy.").string(varName);
                }
                builder.end();
            }
            return method;
        }

        private CodeVariableElement createAssumptionField(String assumption) {
            CodeVariableElement var = new CodeVariableElement(getContext().getTruffleTypes().getAssumption(), assumption);
            var.getModifiers().add(Modifier.FINAL);
            return var;
        }

        private CodeVariableElement createChildField(NodeChildData child) {
            CodeVariableElement var = new CodeVariableElement(child.getNodeType(), child.getName());
            var.getModifiers().add(Modifier.PROTECTED);

            DeclaredType annotationType;
            if (child.getCardinality() == Cardinality.MANY) {
                var.getModifiers().add(Modifier.FINAL);
                annotationType = getContext().getTruffleTypes().getChildrenAnnotation();
            } else {
                annotationType = getContext().getTruffleTypes().getChildAnnotation();
            }

            var.getAnnotationMirrors().add(new CodeAnnotationMirror(annotationType));
            return var;
        }

        private CodeExecutableElement createGenericExecute(NodeData node, String name, boolean specialize) {
            TypeMirror genericReturnType = node.getGenericSpecialization().getReturnType().getType();

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), genericReturnType, name);
            CodeTreeBuilder builder = method.createBuilder();

            String prefix = null;
            if (specialize) {
                method.addParameter(new CodeVariableElement(getContext().getType(Class.class), "minimumState"));

                builder.startStatement();
                builder.startStaticCall(getContext().getTruffleTypes().getCompilerAsserts(), "neverPartOfCompilation").end();
                builder.end();

                emitSpecializationListeners(builder, node);
                builder.defaultDeclaration(node.getGenericSpecialization().getReturnSignature().getPrimitiveType(), "result");
                if (node.getGenericSpecialization().isUseSpecializationsForGeneric()) {
                    builder.defaultDeclaration(getContext().getType(boolean.class), "resultIsSet");
                }
                builder.startStatement().string("boolean allowed = (minimumState == ").string(nodeSpecializationClassName(node.getSpecializations().get(0))).string(".class)").end();
                prefix = null;
            }

            addInternalValueParameters(method, node.getGenericSpecialization(), true);

            List<SpecializationData> specializations = node.getSpecializations();
            if (!specialize && !node.getGenericSpecialization().isUseSpecializationsForGeneric()) {
                specializations = Arrays.asList(node.getGenericSpecialization());
            }

            // group specializations for reachabiltiy
            List<SpecializationData> unreachableSpecializations = new ArrayList<>();
            List<SpecializationData> filteredSpecializations = new ArrayList<>();
            if (!specialize) {
                unreachableSpecializations = new ArrayList<>();
                filteredSpecializations = new ArrayList<>();
                boolean unreachable = false;
                for (SpecializationData specialization : specializations) {
                    if (unreachable) {
                        unreachableSpecializations.add(specialization);
                    } else {
                        filteredSpecializations.add(specialization);
                        if (!specialization.isUninitialized() && !specialization.hasRewrite(getContext())) {
                            unreachable = true;
                        }
                    }
                }
            } else {
                unreachableSpecializations = Collections.emptyList();
                filteredSpecializations = specializations;
            }

            for (SpecializationData current : filteredSpecializations) {
                if (current.isUninitialized()) {
                    continue;
                }
                CodeTreeBuilder execute = new CodeTreeBuilder(builder);

                execute.tree(createGenericInvoke(builder, current, specialize));

                if (specialize && !current.isGeneric()) {
                    builder.startStatement().string("allowed = allowed || (minimumState == ").string(nodeSpecializationClassName(current)).string(".class)").end();
                }

                builder.tree(createGuardAndCast(builder, prefix, current.getNode().getGenericSpecialization(), current, true, execute.getRoot(), null, true));
            }

            for (SpecializationData specializationData : unreachableSpecializations) {
                builder.string("// unreachable ").string(specializationData.getId()).newLine();
            }

            return method;
        }

        private CodeTree createGenericInvoke(CodeTreeBuilder parent, SpecializationData current, boolean specialize) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            if (!current.getExceptions().isEmpty()) {
                builder.startTryBlock();
            }

            CodeTree executeCall = null;
            if (current.getMethod() != null) {
                executeCall = createTemplateMethodCall(builder, null, current.getNode().getGenericSpecialization(), current, null);
            }

            if (specialize && executeCall == null && !current.getNode().getGenericSpecialization().isUseSpecializationsForGeneric()) {
                emitEncounteredSynthetic(builder);
            } else if (specialize) {

                if (current.getNode().getGenericSpecialization().isUseSpecializationsForGeneric()) {
                    builder.startIf().string("!resultIsSet").end().startBlock();
                    if (executeCall != null) {
                        if (current.getReturnSignature().isVoid()) {
                            builder.statement(executeCall);
                        } else {
                            builder.startStatement().string("result = ").tree(executeCall).end();
                        }
                        builder.statement("resultIsSet = true");
                    } else {
                        emitEncounteredSynthetic(builder);
                    }
                    builder.end();
                }

                if (!current.isGeneric()) {
                    builder.startIf().string("allowed").end().startBlock();
                }

                if (!current.getNode().getGenericSpecialization().isUseSpecializationsForGeneric()) {
                    if (current.getReturnSignature().isVoid()) {
                        builder.statement(executeCall);
                    } else {
                        builder.startStatement().string("result = ").tree(executeCall).end();
                    }
                }

                builder.startStatement().startCall("super", "replace");
                builder.startGroup().startNew(nodeSpecializationClassName(current)).string("this").end().end();
                builder.end().end();

                if (current.getReturnSignature().isVoid()) {
                    builder.returnStatement();
                } else {
                    builder.startReturn().string("result").end();
                }
                if (!current.isGeneric()) {
                    builder.end();
                }
            } else {
                if (executeCall == null) {
                    emitEncounteredSynthetic(builder);
                } else {
                    builder.startReturn().tree(executeCall).end();
                }
            }

            if (!current.getExceptions().isEmpty()) {
                for (SpecializationThrowsData exception : current.getExceptions()) {
                    builder.end().startCatchBlock(exception.getJavaClass(), "rewriteEx");
                    builder.string("// fall through").newLine();
                }
                builder.end();
            }

            return builder.getRoot();
        }

        private void emitSpecializationListeners(CodeTreeBuilder builder, NodeData node) {
            for (TemplateMethod listener : node.getSpecializationListeners()) {
                builder.startStatement();
                builder.tree(createTemplateMethodCall(builder, null, listener, listener, null));
                builder.end(); // statement
            }
        }
    }

    private class NodeFactoryFactory extends ClassElementFactory<NodeData> {

        private final Map<NodeData, List<TypeElement>> childTypes;

        private CodeTypeElement generatedNode;

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
                NodeBaseFactory factory = new NodeBaseFactory(context);
                add(factory, node);
                generatedNode = factory.getElement();

                if (node.needsRewrites(context)) {
                    clazz.add(createCreateSpecializedMethod(node, createVisibility));
                }

                createFactoryMethods(node, clazz, createVisibility);

                for (SpecializationData specialization : node.getSpecializations()) {
                    add(new SpecializedNodeFactory(context, generatedNode), specialization);
                }

                TypeMirror nodeFactory = Utils.getDeclaredType(Utils.fromTypeMirror(getContext().getType(NodeFactory.class)), node.getNodeType());
                clazz.getImplements().add(nodeFactory);
                clazz.add(createCreateNodeMethod(node));
                clazz.add(createCreateNodeSpecializedMethod(node));
                clazz.add(createGetNodeClassMethod(node));
                clazz.add(createGetNodeSignaturesMethod());
                clazz.add(createGetChildrenSignatureMethod(node));
                clazz.add(createGetInstanceMethod(node, createVisibility));
                clazz.add(createInstanceConstant(node, clazz.asType()));
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

            List<NodeData> children = node.getNodeDeclaringChildren();
            if (node.getParent() == null && children.size() > 0) {
                clazz.add(createGetFactories(node));
            }

        }

        private CodeExecutableElement createGetNodeClassMethod(NodeData node) {
            TypeMirror returnType = Utils.getDeclaredType(Utils.fromTypeMirror(getContext().getType(Class.class)), node.getNodeType());
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getNodeClass");
            CodeTreeBuilder builder = method.createBuilder();
            builder.startReturn().typeLiteral(node.getNodeType()).end();
            return method;
        }

        private CodeExecutableElement createGetNodeSignaturesMethod() {
            TypeElement listType = Utils.fromTypeMirror(getContext().getType(List.class));
            TypeMirror classType = getContext().getType(Class.class);
            TypeMirror returnType = Utils.getDeclaredType(listType, Utils.getDeclaredType(listType, classType));
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getNodeSignatures");
            CodeTreeBuilder builder = method.createBuilder();
            builder.startReturn();
            builder.startStaticCall(getContext().getType(Arrays.class), "asList");
            List<ExecutableElement> constructors = findUserConstructors(generatedNode.asType());
            for (ExecutableElement constructor : constructors) {
                builder.tree(createAsList(builder, Utils.asTypeMirrors(constructor.getParameters()), classType));
            }
            builder.end();
            builder.end();
            return method;
        }

        private CodeExecutableElement createGetChildrenSignatureMethod(NodeData node) {
            Types types = getContext().getEnvironment().getTypeUtils();
            TypeElement listType = Utils.fromTypeMirror(getContext().getType(List.class));
            TypeMirror classType = getContext().getType(Class.class);
            TypeMirror nodeType = getContext().getTruffleTypes().getNode();
            TypeMirror wildcardNodeType = types.getWildcardType(nodeType, null);
            classType = Utils.getDeclaredType(Utils.fromTypeMirror(classType), wildcardNodeType);
            TypeMirror returnType = Utils.getDeclaredType(listType, classType);

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getExecutionSignature");
            CodeTreeBuilder builder = method.createBuilder();

            List<TypeMirror> signatureTypes = new ArrayList<>();
            assert !node.getSpecializations().isEmpty();
            SpecializationData data = node.getSpecializations().get(0);
            for (ActualParameter parameter : data.getParameters()) {
                ParameterSpec spec = parameter.getSpecification();
                NodeChildData field = node.findChild(spec.getName());
                if (field == null) {
                    continue;
                }

                TypeMirror type;
                if (field.getCardinality() == Cardinality.MANY && field.getNodeType().getKind() == TypeKind.ARRAY) {
                    type = ((ArrayType) field.getNodeType()).getComponentType();
                } else {
                    type = field.getNodeType();
                }

                signatureTypes.add(type);
            }

            builder.startReturn().tree(createAsList(builder, signatureTypes, classType)).end();
            return method;
        }

        private CodeTree createAsList(CodeTreeBuilder parent, List<TypeMirror> types, TypeMirror elementClass) {
            CodeTreeBuilder builder = parent.create();
            builder.startGroup();
            builder.type(getContext().getType(Arrays.class));
            builder.string(".<").type(elementClass).string(">");
            builder.startCall("asList");
            for (TypeMirror typeMirror : types) {
                builder.typeLiteral(typeMirror);
            }
            builder.end().end();
            return builder.getRoot();
        }

        private CodeExecutableElement createCreateNodeMethod(NodeData node) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), node.getNodeType(), "createNode");
            CodeVariableElement arguments = new CodeVariableElement(getContext().getType(Object.class), "arguments");
            method.setVarArgs(true);
            method.addParameter(arguments);

            CodeTreeBuilder builder = method.createBuilder();
            List<ExecutableElement> signatures = findUserConstructors(generatedNode.asType());
            boolean ifStarted = false;

            for (ExecutableElement element : signatures) {
                ifStarted = builder.startIf(ifStarted);
                builder.string("arguments.length == " + element.getParameters().size());

                int index = 0;
                for (VariableElement param : element.getParameters()) {
                    builder.string(" && ");
                    if (!param.asType().getKind().isPrimitive()) {
                        builder.string("(arguments[" + index + "] == null || ");
                    }
                    builder.string("arguments[" + index + "] instanceof ");
                    builder.type(Utils.boxType(getContext(), param.asType()));
                    if (!param.asType().getKind().isPrimitive()) {
                        builder.string(")");
                    }
                    index++;
                }
                builder.end();
                builder.startBlock();

                builder.startReturn().startCall("create");
                index = 0;
                for (VariableElement param : element.getParameters()) {
                    builder.startGroup();
                    builder.string("(").type(param.asType()).string(") ");
                    builder.string("arguments[").string(String.valueOf(index)).string("]");
                    builder.end();
                    index++;
                }
                builder.end().end();

                builder.end(); // block
            }

            builder.startElseBlock();
            builder.startThrow().startNew(getContext().getType(IllegalArgumentException.class));
            builder.doubleQuote("Invalid create signature.");
            builder.end().end();
            builder.end(); // else block
            return method;
        }

        private CodeExecutableElement createCreateNodeSpecializedMethod(NodeData node) {
            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), node.getNodeType(), "createNodeSpecialized");
            CodeVariableElement nodeParam = new CodeVariableElement(node.getNodeType(), THIS_NODE_LOCAL_VAR_NAME);
            CodeVariableElement arguments = new CodeVariableElement(getContext().getType(Class.class), "types");
            method.addParameter(nodeParam);
            method.addParameter(arguments);
            method.setVarArgs(true);

            CodeTreeBuilder builder = method.createBuilder();
            if (!node.needsRewrites(getContext())) {
                builder.startThrow().startNew(getContext().getType(UnsupportedOperationException.class)).end().end();
            } else {
                builder.startIf();
                builder.string("types.length == 1");
                builder.end();
                builder.startBlock();

                builder.startReturn().startCall("createSpecialized");
                builder.startGroup();
                builder.string(THIS_NODE_LOCAL_VAR_NAME);
                builder.end();
                builder.string("types[0]");
                builder.end().end();

                builder.end();
                builder.startElseBlock();
                builder.startThrow().startNew(getContext().getType(IllegalArgumentException.class));
                builder.doubleQuote("Invalid createSpecialized signature.");
                builder.end().end();
                builder.end();
            }

            return method;
        }

        private ExecutableElement createGetInstanceMethod(NodeData node, Modifier visibility) {
            TypeElement nodeFactoryType = Utils.fromTypeMirror(getContext().getType(NodeFactory.class));
            TypeMirror returnType = Utils.getDeclaredType(nodeFactoryType, node.getNodeType());

            CodeExecutableElement method = new CodeExecutableElement(modifiers(), returnType, "getInstance");
            if (visibility != null) {
                method.getModifiers().add(visibility);
            }
            method.getModifiers().add(Modifier.STATIC);

            String varName = instanceVarName(node);

            CodeTreeBuilder builder = method.createBuilder();
            builder.startIf();
            builder.string(varName).string(" == null");
            builder.end().startBlock();

            builder.startStatement();
            builder.string(varName);
            builder.string(" = ");
            builder.startNew(factoryClassName(node)).end();
            builder.end();

            builder.end();
            builder.startReturn().string(varName).end();
            return method;
        }

        private String instanceVarName(NodeData node) {
            if (node.getParent() != null) {
                return Utils.firstLetterLowerCase(factoryClassName(node)) + "Instance";
            } else {
                return "instance";
            }
        }

        private CodeVariableElement createInstanceConstant(NodeData node, TypeMirror factoryType) {
            String varName = instanceVarName(node);
            CodeVariableElement var = new CodeVariableElement(modifiers(), factoryType, varName);
            var.getModifiers().add(Modifier.PRIVATE);
            var.getModifiers().add(Modifier.STATIC);
            return var;
        }

        private ExecutableElement createGetFactories(NodeData node) {
            List<NodeData> children = node.getNodeDeclaringChildren();
            if (node.needsFactory()) {
                children.add(node);
            }

            List<TypeMirror> nodeTypesList = new ArrayList<>();
            TypeMirror prev = null;
            boolean allSame = true;
            for (NodeData child : children) {
                nodeTypesList.add(child.getNodeType());
                if (prev != null && !Utils.typeEquals(child.getNodeType(), prev)) {
                    allSame = false;
                }
                prev = child.getNodeType();
            }
            TypeMirror commonNodeSuperType = Utils.getCommonSuperType(getContext(), nodeTypesList.toArray(new TypeMirror[nodeTypesList.size()]));

            Types types = getContext().getEnvironment().getTypeUtils();
            TypeMirror factoryType = getContext().getType(NodeFactory.class);
            TypeMirror baseType;
            if (allSame) {
                baseType = Utils.getDeclaredType(Utils.fromTypeMirror(factoryType), commonNodeSuperType);
            } else {
                baseType = Utils.getDeclaredType(Utils.fromTypeMirror(factoryType), types.getWildcardType(commonNodeSuperType, null));
            }
            TypeMirror listType = Utils.getDeclaredType(Utils.fromTypeMirror(getContext().getType(List.class)), baseType);

            CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, STATIC), listType, "getFactories");

            CodeTreeBuilder builder = method.createBuilder();
            builder.startReturn();
            builder.startStaticCall(getContext().getType(Arrays.class), "asList");

            for (NodeData child : children) {
                builder.startGroup();
                NodeData childNode = child;
                List<NodeData> factories = new ArrayList<>();
                while (childNode.getParent() != null) {
                    factories.add(childNode);
                    childNode = childNode.getParent();
                }
                Collections.reverse(factories);
                for (NodeData nodeData : factories) {
                    builder.string(factoryClassName(nodeData)).string(".");
                }
                builder.string("getInstance()");
                builder.end();
            }
            builder.end();
            builder.end();
            return method;
        }

        private void createFactoryMethods(NodeData node, CodeTypeElement clazz, Modifier createVisibility) {
            List<ExecutableElement> constructors = findUserConstructors(generatedNode.asType());
            for (ExecutableElement constructor : constructors) {
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
            if (node.getSpecializations().isEmpty()) {
                body.null_();
            } else {
                body.startNew(nodeSpecializationClassName(node.getSpecializations().get(0)));
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

            boolean hasCopyConstructor = findCopyConstructor(generatedNode.asType()) != null;

            final String thisLocalVariableName = THIS_NODE_LOCAL_VAR_NAME + "Cast";

            if (hasCopyConstructor) {
                body.startStatement();
                body.type(generatedNode.asType()).string(" ").string(thisLocalVariableName);
                body.string(" = ").string("(").type(generatedNode.asType()).string(") ").string(THIS_NODE_LOCAL_VAR_NAME);
                body.end();
            }

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
                    body.tree(createReturnNewSpecialization(body, specialization, thisLocalVariableName, hasCopyConstructor));

                    body.end(); // if
                }
            }
            body.tree(createReturnNewSpecialization(body, node.getGenericSpecialization(), thisLocalVariableName, hasCopyConstructor));
            return method;
        }

    }

    private class SpecializedNodeFactory extends ClassElementFactory<SpecializationData> {

        private final CodeTypeElement nodeGen;

        public SpecializedNodeFactory(ProcessorContext context, CodeTypeElement nodeGen) {
            super(context);
            this.nodeGen = nodeGen;
        }

        @Override
        public CodeTypeElement create(SpecializationData specialization) {
            NodeData node = specialization.getNode();
            TypeMirror baseType = node.getNodeType();
            if (nodeGen != null) {
                baseType = nodeGen.asType();
            }
            CodeTypeElement clazz = createClass(node, modifiers(PRIVATE, STATIC, FINAL), nodeSpecializationClassName(specialization), baseType, false);

            String shortName = specialization.getNode().getShortName();
            CodeAnnotationMirror nodeInfoMirror = new CodeAnnotationMirror(getContext().getTruffleTypes().getNodeInfoAnnotation());
            if (shortName != null) {
                nodeInfoMirror.setElementValue(nodeInfoMirror.findExecutableElement("shortName"), new CodeAnnotationValue(shortName));
            }

            DeclaredType nodeinfoKind = getContext().getTruffleTypes().getNodeInfoKind();
            VariableElement kind;
            if (specialization.isGeneric()) {
                kind = Utils.findVariableElement(nodeinfoKind, "GENERIC");
            } else if (specialization.isUninitialized()) {
                kind = Utils.findVariableElement(nodeinfoKind, "UNINIALIZED");
            } else {
                kind = Utils.findVariableElement(nodeinfoKind, "SPECIALIZED");
            }

            nodeInfoMirror.setElementValue(nodeInfoMirror.findExecutableElement("kind"), new CodeAnnotationValue(kind));

            clazz.getAnnotationMirrors().add(nodeInfoMirror);

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
                CodeExecutableElement executeMethod = createExecutableTypeOverride(execType);
                clazz.add(executeMethod);
                CodeTreeBuilder builder = executeMethod.createBuilder();
                CodeTree result = createExecuteBody(builder, specialization, execType);
                if (result != null) {
                    builder.tree(result);
                } else {
                    clazz.remove(executeMethod);
                }
            }
        }

        private CodeTree createExecuteBody(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData execType) {
            TypeData primaryType = specialization.getReturnType().getTypeSystemType();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            ExecutableTypeData foundEvaluatedPrimaryType = findFunctionalExecutableType(specialization, execType.getEvaluatedCount());

            if (execType == foundEvaluatedPrimaryType || foundEvaluatedPrimaryType == null) {
                builder.tree(createFunctionalExecute(builder, specialization, execType));
            } else if (needsCastingExecuteMethod(execType, primaryType)) {
                builder.tree(createCastingExecute(builder, specialization, execType, foundEvaluatedPrimaryType));
            } else {
                return null;
            }

            return builder.getRoot();
        }

        private CodeExecutableElement createExecutableTypeOverride(ExecutableTypeData execType) {
            CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), execType.getMethod());

            int i = 0;
            for (VariableElement param : method.getParameters()) {
                CodeVariableElement var = CodeVariableElement.clone(param);
                ActualParameter actualParameter = execType.getParameters().get(i);
                if (actualParameter.getSpecification().isSignature()) {
                    var.setName(valueNameEvaluated(actualParameter));
                } else {
                    var.setName(valueName(actualParameter));
                }
                method.getParameters().set(i, var);
                i++;
            }

            method.getAnnotationMirrors().clear();
            method.getModifiers().remove(Modifier.ABSTRACT);
            return method;
        }

        private boolean needsCastingExecuteMethod(ExecutableTypeData execType, TypeData primaryType) {
            if (execType.isAbstract()) {
                return true;
            }
            if (Utils.isPrimitiveOrVoid(primaryType.getPrimitiveType()) && Utils.isPrimitiveOrVoid(execType.getType().getPrimitiveType())) {
                return true;
            }
            if (execType.getType().isGeneric()) {
                return true;
            }
            return false;
        }

        private ExecutableTypeData findFunctionalExecutableType(SpecializationData specialization, int evaluatedCount) {
            TypeData primaryType = specialization.getReturnType().getTypeSystemType();
            List<ExecutableTypeData> otherTypes = specialization.getNode().getExecutableTypes(evaluatedCount);

            List<ExecutableTypeData> filteredTypes = new ArrayList<>();
            for (ExecutableTypeData compareType : otherTypes) {
                if (!Utils.typeEquals(compareType.getType().getPrimitiveType(), primaryType.getPrimitiveType())) {
                    continue;
                }
                filteredTypes.add(compareType);
            }

            for (ExecutableTypeData compareType : filteredTypes) {
                if (compareType.startsWithSignature(specialization)) {
                    return compareType;
                }
            }

            for (ExecutableTypeData compareType : otherTypes) {
                if (compareType.startsWithSignature(specialization)) {
                    return compareType;
                }
            }

            return null;
        }

        private CodeTree createCastingExecute(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData executable, ExecutableTypeData castExecutable) {
            TypeData type = executable.getType();
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            NodeData node = specialization.getNode();

            ExecutableTypeData castedType = node.findExecutableType(type, 0);
            TypeData primaryType = castExecutable.getType();

            boolean needsTry = castExecutable.hasUnexpectedValue(getContext());
            boolean returnVoid = type.isVoid();

            List<ActualParameter> executeParameters = new ArrayList<>();
            for (ActualParameter sourceParameter : executable.getParameters()) {
                if (!sourceParameter.getSpecification().isSignature()) {
                    continue;
                }

                ActualParameter targetParameter = castExecutable.findParameter(sourceParameter.getLocalName());
                if (targetParameter != null) {
                    executeParameters.add(targetParameter);
                }
            }

            builder.tree(createExecuteChildren(builder, executable, specialization, executeParameters, null, true));

            CodeTree primaryExecuteCall = createTemplateMethodCall(builder, null, executable, castExecutable, null);
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
                    builder.tree(createExpectExecutableType(node, specialization.getNode().getTypeSystem().getGenericTypeData(), castedType, CodeTreeBuilder.singleString("ex.getResult()")));
                    builder.end();
                }
                builder.end();

                if (!returnVoid) {
                    builder.startReturn();
                    builder.tree(createExpectExecutableType(node, castExecutable.getReturnSignature(), executable, CodeTreeBuilder.singleString("value")));
                    builder.end();
                }
            } else {
                if (returnVoid) {
                    builder.statement(primaryExecuteCall);
                } else {
                    builder.startReturn();
                    builder.tree(createExpectExecutableType(node, castExecutable.getReturnSignature(), executable, primaryExecuteCall));
                    builder.end();
                }
            }

            return builder.getRoot();
        }

        private CodeTree createExpectExecutableType(NodeData node, TypeData sourceType, ExecutableTypeData castedType, CodeTree value) {
            boolean hasUnexpected = castedType.hasUnexpectedValue(getContext());
            return createCastType(node, sourceType, castedType.getType(), hasUnexpected, value);
        }

        private CodeTree createCastType(NodeData node, TypeData sourceType, TypeData targetType, boolean expect, CodeTree value) {
            if (targetType == null) {
                return value;
            } else if (!sourceType.needsCastTo(getContext(), targetType)) {
                return value;
            }

            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            String targetMethodName;
            if (expect) {
                targetMethodName = TypeSystemCodeGenerator.expectTypeMethodName(targetType);
            } else {
                targetMethodName = TypeSystemCodeGenerator.asTypeMethodName(targetType);
            }
            startCallTypeSystemMethod(getContext(), builder, node, targetMethodName);

            builder.tree(value);
            builder.end().end();
            return builder.getRoot();
        }

        private CodeTree createFunctionalExecute(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData executable) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            if (specialization.isUninitialized()) {
                builder.tree(createDeoptimize(builder));
            }

            builder.tree(createExecuteChildren(builder, executable, specialization, specialization.getParameters(), null, false));

            CodeTree executeNode;
            executeNode = createExecute(builder, executable, specialization);

            SpecializationData next = specialization.findNextSpecialization();
            CodeTree returnSpecialized = null;
            if (next != null) {
                CodeTreeBuilder returnBuilder = new CodeTreeBuilder(builder);
                returnBuilder.tree(createDeoptimize(builder));
                returnBuilder.tree(createReturnExecuteAndSpecialize(builder, executable, next, null));
                returnSpecialized = returnBuilder.getRoot();
            }
            builder.tree(createGuardAndCast(builder, null, specialization, specialization, true, executeNode, returnSpecialized, false));

            return builder.getRoot();
        }

        private CodeTree createDeoptimize(CodeTreeBuilder parent) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            builder.startStatement();
            builder.startStaticCall(getContext().getTruffleTypes().getCompilerDirectives(), "transferToInterpreter").end();
            builder.end();
            return builder.getRoot();
        }

        private CodeTree createExecute(CodeTreeBuilder parent, ExecutableTypeData executable, SpecializationData specialization) {
            NodeData node = specialization.getNode();
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            if (!specialization.getExceptions().isEmpty() || !specialization.getAssumptions().isEmpty()) {
                builder.startTryBlock();
            }

            for (String assumption : specialization.getAssumptions()) {
                builder.startStatement();
                builder.string("this.").string(assumption).string(".check()");
                builder.end();
            }

            CodeTreeBuilder returnBuilder = new CodeTreeBuilder(parent);
            if (specialization.isUninitialized()) {
                returnBuilder.startCall("super", EXECUTE_SPECIALIZE_NAME);
                returnBuilder.startGroup().string(nodeSpecializationClassName(specialization)).string(".class").end();
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, true, true);
                returnBuilder.end();
            } else if (specialization.getMethod() == null && !node.needsRewrites(context)) {
                emitEncounteredSynthetic(builder);
            } else if (specialization.isGeneric()) {
                returnBuilder.startCall("super", EXECUTE_GENERIC_NAME);
                addInternalValueParameterNames(returnBuilder, specialization, specialization, null, true, true);
                returnBuilder.end();
            } else {
                returnBuilder.tree(createTemplateMethodCall(returnBuilder, null, specialization, specialization, null));
            }

            if (!returnBuilder.isEmpty()) {
                builder.startReturn();

                TypeData targetType = node.getTypeSystem().findTypeData(builder.findMethod().getReturnType());
                TypeData sourceType = specialization.getReturnType().getTypeSystemType();

                if (targetType == null || sourceType == null) {
                    builder.tree(returnBuilder.getRoot());
                } else if (sourceType.needsCastTo(getContext(), targetType)) {
                    builder.tree(createCallTypeSystemMethod(context, parent, node, TypeSystemCodeGenerator.expectTypeMethodName(targetType), returnBuilder.getRoot()));
                } else {
                    builder.tree(returnBuilder.getRoot());
                }
                builder.end();
            }

            if (!specialization.getExceptions().isEmpty()) {
                for (SpecializationThrowsData exception : specialization.getExceptions()) {
                    builder.end().startCatchBlock(exception.getJavaClass(), "ex");
                    builder.tree(createDeoptimize(builder));
                    builder.tree(createReturnExecuteAndSpecialize(parent, executable, exception.getTransitionTo(), null));
                }
                builder.end();
            }
            if (!specialization.getAssumptions().isEmpty()) {
                builder.end().startCatchBlock(getContext().getTruffleTypes().getInvalidAssumption(), "ex");
                builder.tree(createReturnExecuteAndSpecialize(parent, executable, specialization.findNextSpecialization(), null));
                builder.end();
            }

            return builder.getRoot();
        }

        private CodeTree createExecuteChildren(CodeTreeBuilder parent, ExecutableTypeData sourceExecutable, SpecializationData specialization, List<ActualParameter> targetParameters,
                        ActualParameter unexpectedParameter, boolean cast) {
            NodeData sourceNode = specialization.getNode();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            for (ActualParameter targetParameter : targetParameters) {
                NodeChildData field = sourceNode.findChild(targetParameter.getSpecification().getName());
                if (!targetParameter.getSpecification().isSignature()) {
                    continue;
                }

                TypeData targetType = targetParameter.getTypeSystemType();
                ExecutableTypeData targetExecutable = null;
                if (field != null) {
                    targetExecutable = field.findExecutableType(getContext(), targetType);
                }

                ActualParameter sourceParameter = sourceExecutable.findParameter(targetParameter.getLocalName());

                String targetVariableName = valueName(targetParameter);
                CodeTree executionExpression = null;
                if ((sourceParameter != null && cast) || sourceParameter != null) {
                    TypeData sourceType = sourceParameter.getTypeSystemType();
                    if (targetExecutable == null || !sourceType.needsCastTo(getContext(), targetType)) {
                        if (field != null && field.isShortCircuit() && sourceParameter != null) {
                            builder.tree(createShortCircuitValue(builder, specialization, field, targetParameter.getPreviousParameter(), unexpectedParameter));
                        }
                        builder.startStatement();
                        builder.type(targetParameter.getType()).string(" ");
                        builder.string(valueName(targetParameter)).string(" = ");
                        builder.tree(CodeTreeBuilder.singleString(valueNameEvaluated(targetParameter)));
                        builder.end();
                        continue;
                    } else {
                        CodeTree valueTree = CodeTreeBuilder.singleString(valueNameEvaluated(targetParameter));
                        executionExpression = createExpectExecutableType(sourceNode, sourceType, targetExecutable, valueTree);
                    }
                } else if (sourceParameter == null) {
                    executionExpression = createExecuteChildExpression(builder, field, targetParameter, unexpectedParameter);
                }

                if (executionExpression != null) {
                    CodeTreeVariable executionVar = new CodeTreeVariable();
                    CodeTree shortCircuitTree = createShortCircuitTree(builder, executionVar, targetVariableName, specialization, targetParameter, unexpectedParameter);
                    CodeTree unexpectedTree = createCatchUnexpectedTree(builder, executionExpression, targetVariableName, specialization, sourceExecutable, targetExecutable, targetParameter,
                                    shortCircuitTree != executionVar);

                    executionVar.set(unexpectedTree);
                    builder.tree(shortCircuitTree);
                }
            }
            return builder.getRoot();
        }

        private CodeTree createCatchUnexpectedTree(CodeTreeBuilder parent, CodeTree body, String targetVariableName, SpecializationData specialization, ExecutableTypeData currentExecutable,
                        ExecutableTypeData targetExecutable, ActualParameter param, boolean shortCircuit) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            boolean unexpected = targetExecutable.hasUnexpectedValue(getContext());
            boolean cast = false;
            if (targetExecutable.getType().needsCastTo(getContext(), param.getTypeSystemType())) {
                unexpected = true;
                cast = true;
            }

            builder.startStatement();

            if (!shortCircuit) {
                builder.type(param.getType()).string(" ").string(targetVariableName);
            }

            if (unexpected) {
                if (!shortCircuit) {
                    builder.end();
                }
                builder.startTryBlock();
                builder.startStatement();
                builder.string(targetVariableName);
            } else if (shortCircuit) {
                builder.startStatement();
                builder.string(targetVariableName);
            }
            builder.string(" = ");
            if (cast) {
                builder.tree(createCastType(specialization.getNode(), targetExecutable.getType(), param.getTypeSystemType(), true, body));
            } else {
                builder.tree(body);
            }
            builder.end();

            if (unexpected) {
                builder.end().startCatchBlock(getUnexpectedValueException(), "ex");
                SpecializationData generic = specialization.getNode().getGenericSpecialization();
                ActualParameter genericParameter = generic.findParameter(param.getLocalName());

                List<ActualParameter> genericParameters = generic.getParametersAfter(genericParameter);
                builder.tree(createDeoptimize(builder));
                builder.tree(createExecuteChildren(parent, currentExecutable, generic, genericParameters, genericParameter, false));
                builder.tree(createReturnExecuteAndSpecialize(builder, currentExecutable, specialization.findNextSpecialization(), param));
                builder.end(); // catch block
            }

            return builder.getRoot();
        }

        private CodeTree createExecuteChildExpression(CodeTreeBuilder parent, NodeChildData targetField, ActualParameter sourceParameter, ActualParameter unexpectedParameter) {
            TypeData type = sourceParameter.getTypeSystemType();
            ExecutableTypeData execType = targetField.findExecutableType(getContext(), type);

            /*
             * FIXME Temporary deactivated due to partial evaluation failure else if
             * (accessElement.getKind() == ElementKind.METHOD) {
             * builder.startCall(accessElement.getSimpleName().toString()).end(); }
             */
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            if (targetField != null) {
                Element accessElement = targetField.getAccessElement();
                if (accessElement == null || accessElement.getKind() == ElementKind.METHOD) {
                    builder.string("this.").string(targetField.getName());
                } else if (accessElement.getKind() == ElementKind.FIELD) {
                    builder.string("this.").string(accessElement.getSimpleName().toString());
                } else {
                    throw new AssertionError();
                }
                if (sourceParameter.getSpecification().isIndexed()) {
                    builder.string("[" + sourceParameter.getIndex() + "]");
                }
                builder.string(".");
            }

            builder.startCall(execType.getMethodName());

            List<ActualParameter> signatureParameters = getModel().getSignatureParameters();
            int index = 0;
            for (ActualParameter parameter : execType.getParameters()) {

                if (!parameter.getSpecification().isSignature()) {
                    builder.string(parameter.getLocalName());
                } else {
                    if (index < signatureParameters.size()) {
                        ActualParameter specializationParam = signatureParameters.get(index);

                        TypeData targetType = parameter.getTypeSystemType();
                        TypeData sourceType = specializationParam.getTypeSystemType();
                        String localName = specializationParam.getLocalName();
                        if (unexpectedParameter != null && unexpectedParameter.getLocalName().equals(specializationParam.getLocalName())) {
                            localName = "ex.getResult()";
                            sourceType = getModel().getNode().getTypeSystem().getGenericTypeData();
                        }

                        CodeTree value = CodeTreeBuilder.singleString(localName);

                        if (sourceType.needsCastTo(getContext(), targetType)) {
                            value = createCallTypeSystemMethod(getContext(), builder, getModel().getNode(), TypeSystemCodeGenerator.asTypeMethodName(targetType), value);
                        }
                        builder.tree(value);
                    } else {
                        builder.defaultValue(parameter.getType());
                    }
                    index++;
                }
            }

            builder.end();

            return builder.getRoot();
        }

        private CodeTree createShortCircuitTree(CodeTreeBuilder parent, CodeTree body, String targetVariableName, SpecializationData specialization, ActualParameter parameter,
                        ActualParameter exceptionParam) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            NodeChildData forField = specialization.getNode().findChild(parameter.getSpecification().getName());
            if (forField == null) {
                return body;
            }

            if (forField.getExecutionKind() != ExecutionKind.SHORT_CIRCUIT) {
                return body;
            }

            ActualParameter shortCircuitParam = specialization.getPreviousParam(parameter);

            builder.tree(createShortCircuitValue(builder, specialization, forField, shortCircuitParam, exceptionParam));

            builder.declaration(parameter.getType(), targetVariableName, CodeTreeBuilder.createBuilder().defaultValue(parameter.getType()));
            builder.startIf().string(shortCircuitParam.getLocalName()).end();
            builder.startBlock();
            builder.tree(body);
            builder.end();

            return builder.getRoot();
        }

        private CodeTree createShortCircuitValue(CodeTreeBuilder parent, SpecializationData specialization, NodeChildData forField, ActualParameter shortCircuitParam, ActualParameter exceptionParam) {
            CodeTreeBuilder builder = new CodeTreeBuilder(parent);
            int shortCircuitIndex = 0;
            for (NodeChildData field : specialization.getNode().getChildren()) {
                if (field.getExecutionKind() == ExecutionKind.SHORT_CIRCUIT) {
                    if (field == forField) {
                        break;
                    }
                    shortCircuitIndex++;
                }
            }

            builder.startStatement().type(shortCircuitParam.getType()).string(" ").string(valueName(shortCircuitParam)).string(" = ");
            ShortCircuitData shortCircuitData = specialization.getShortCircuits().get(shortCircuitIndex);
            builder.tree(createTemplateMethodCall(builder, null, specialization, shortCircuitData, exceptionParam != null ? exceptionParam.getLocalName() : null));
            builder.end(); // statement

            return builder.getRoot();
        }

        private CodeTree createReturnExecuteAndSpecialize(CodeTreeBuilder parent, ExecutableTypeData executable, SpecializationData nextSpecialization, ActualParameter exceptionParam) {

            SpecializationData generic = nextSpecialization.getNode().getGenericSpecialization();
            CodeTreeBuilder specializeCall = new CodeTreeBuilder(parent);
            specializeCall.startCall(EXECUTE_SPECIALIZE_NAME);
            specializeCall.string(nodeSpecializationClassName(nextSpecialization) + ".class");
            addInternalValueParameterNames(specializeCall, generic, nextSpecialization.getNode().getGenericSpecialization(), exceptionParam != null ? exceptionParam.getLocalName() : null, true, true);
            specializeCall.end().end();

            CodeTreeBuilder builder = new CodeTreeBuilder(parent);

            builder.startReturn();
            builder.tree(createExpectExecutableType(nextSpecialization.getNode(), generic.getReturnSignature(), executable, specializeCall.getRoot()));
            builder.end();

            return builder.getRoot();
        }

    }

}
