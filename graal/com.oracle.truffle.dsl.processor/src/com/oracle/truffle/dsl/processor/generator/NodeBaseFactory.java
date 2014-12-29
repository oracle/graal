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

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.model.*;
import com.oracle.truffle.dsl.processor.model.NodeChildData.Cardinality;
import com.oracle.truffle.dsl.processor.parser.*;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

class NodeBaseFactory extends AbstractClassElementFactory<SpecializationData> {

    private static final String THIS_NODE_LOCAL_VAR_NAME = "thisNode";

    static final String EXECUTE_CHAINED = "executeChained0";
    private static final String SPECIALIZE = "specialize0";
    private static final String DSLSHARE_REWRITE = "rewrite";
    private static final String DSLSHARE_FIND_ROOT = "findRoot";
    private static final String DSLSHARE_REWRITE_TO_POLYMORHPIC = "rewriteToPolymorphic";
    static final String EXECUTE_UNINITIALIZED = "executeUninitialized0";
    private static final String REWRITE = "rewrite0";
    private static final String CREATE_INFO = "createInfo0";
    static final String CONTAINS_FALLBACK = "containsFallback";

    static final String EMPTY_CLASS_ARRAY = "EMPTY_CLASS_ARRAY";

    static final String METADATA_FIELD_NAME = "METADATA";

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

    @Override
    protected CodeTypeElement create(SpecializationData specialization) {
        NodeData node = specialization.getNode();
        CodeTypeElement clazz = createClass(node, modifiers(PRIVATE, ABSTRACT, STATIC), baseClassName(node), node.getNodeType(), false);
        clazz.getImplements().add(context.getTruffleTypes().getDslNode());

        for (NodeChildData child : node.getChildren()) {
            clazz.add(createChildField(child));

            if (child.getAccessElement() != null && child.getAccessElement().getModifiers().contains(Modifier.ABSTRACT)) {
                ExecutableElement getter = (ExecutableElement) child.getAccessElement();
                CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), getter);
                method.getModifiers().remove(Modifier.ABSTRACT);
                CodeTreeBuilder builder = method.createBuilder();
                builder.startReturn().string("this.").string(child.getName()).end();
                clazz.add(method);
            }
        }

        for (NodeFieldData field : node.getFields()) {
            if (!field.isGenerated()) {
                continue;
            }

            clazz.add(new CodeVariableElement(modifiers(PROTECTED, FINAL), field.getType(), field.getName()));
            if (field.getGetter() != null && field.getGetter().getModifiers().contains(Modifier.ABSTRACT)) {
                CodeExecutableElement method = CodeExecutableElement.clone(getContext().getEnvironment(), field.getGetter());
                method.getModifiers().remove(Modifier.ABSTRACT);
                method.createBuilder().startReturn().string("this.").string(field.getName()).end();
                clazz.add(method);
            }
        }

        for (String assumption : node.getAssumptions()) {
            clazz.add(createAssumptionField(assumption));
        }

        createConstructors(node, clazz);

        return clazz;
    }

    @Override
    protected void createChildren(SpecializationData specialization) {
        NodeData node = specialization.getNode();
        CodeTypeElement clazz = getElement();

        SpecializationGroup rootGroup = createSpecializationGroups(node);

        if (node.needsRewrites(context)) {
            if (node.isPolymorphic(context)) {

                CodeVariableElement var = new CodeVariableElement(modifiers(PROTECTED), clazz.asType(), "next0");
                var.getAnnotationMirrors().add(new CodeAnnotationMirror(getContext().getTruffleTypes().getChildAnnotation()));
                clazz.add(var);

                CodeExecutableElement genericCachedExecute = createCachedExecute(node, node.getPolymorphicSpecialization());
                clazz.add(genericCachedExecute);

            }

            for (CodeExecutableElement method : createImplicitChildrenAccessors()) {
                clazz.add(method);
            }
            clazz.add(createInfoMessage(node));
            clazz.add(createMonomorphicRewrite());
            clazz.add(createCreateSpecializationMethod(node, rootGroup));
        }

        clazz.add(createAdoptChildren0());
        clazz.add(createGetMetadata0(true));
        clazz.add(createUpdateTypes0());
        clazz.add(createGetNext());
    }

    private Element createGetNext() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), context.getType(Node.class), "getNext0");
        CodeTreeBuilder builder = method.createBuilder();
        NodeData node = getModel().getNode();

        if (node.isPolymorphic(context)) {
            builder.startReturn().string("next0").end();
        } else {
            builder.returnNull();
        }

        return method;
    }

    protected final CodeExecutableElement createUpdateTypes0() {
        ArrayType classArray = new ArrayCodeTypeMirror(context.getType(Class.class));
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), context.getType(void.class), "updateTypes0");
        method.getParameters().add(new CodeVariableElement(classArray, "types"));

        if (getModel().isPolymorphic()) {
            CodeTreeBuilder builder = method.createBuilder();

            int index = 0;
            for (NodeExecutionData execution : getModel().getNode().getChildExecutions()) {
                String fieldName = polymorphicTypeName(execution);

                builder.startStatement();
                builder.string(fieldName).string(" = ").string("types[").string(String.valueOf(index)).string("]");
                builder.end();
                index++;
            }
        }

        return method;
    }

    protected final CodeExecutableElement createGetMetadata0(boolean empty) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), context.getTruffleTypes().getDslMetadata(), "getMetadata0");
        if (empty) {
            method.createBuilder().startReturn().staticReference(context.getTruffleTypes().getDslMetadata(), "NONE").end();
        } else {
            method.createBuilder().startReturn().string(METADATA_FIELD_NAME).end();
        }
        return method;
    }

    private CodeExecutableElement createAdoptChildren0() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC, FINAL), context.getType(void.class), "adoptChildren0");
        method.getParameters().add(new CodeVariableElement(context.getTruffleTypes().getNode(), "other"));
        method.getParameters().add(new CodeVariableElement(context.getTruffleTypes().getNode(), "newNext"));
        NodeData node = getModel().getNode();
        CodeTreeBuilder builder = method.createBuilder();
        List<NodeExecutionData> executions = node.getChildExecutions();

        if (executions.size() > 0) {
            builder.startIf().string("other == null").end().startBlock();
            for (NodeExecutionData execution : executions) {
                builder.startStatement().tree(createAccessChild(execution, "this")).string(" = null").end();
            }
            builder.end().startElseBlock();

            String access;
            if (executions.size() > 1) {
                builder.declaration(baseClassName(node), "otherCast", builder.create().cast(baseClassName(node)).string("other"));
                access = "otherCast";
            } else {
                assert executions.size() == 1;
                access = "((" + baseClassName(node) + ") other)";
            }
            for (NodeExecutionData execution : executions) {
                builder.startStatement().tree(createAccessChild(execution, "this")).string(" = ").tree(createAccessChild(execution, access)).end();
            }

            builder.end();
        }

        if (getModel().getNode().isPolymorphic(context)) {
            builder.startIf().string("newNext == null").end().startBlock();
            builder.statement("this.next0 = null");
            builder.end().startElseBlock();
            builder.statement("this.next0 = (" + baseClassName(getModel().getNode()) + ") newNext");
            builder.end();
        }

        return method;
    }

    private List<CodeExecutableElement> createImplicitChildrenAccessors() {
        NodeData node = getModel().getNode();
        List<Set<TypeData>> prototype = Collections.nCopies(node.getGenericSpecialization().getParameters().size(), null);
        List<Set<TypeData>> expectTypes = new ArrayList<>(prototype);

        for (ExecutableTypeData executableType : node.getExecutableTypes()) {
            for (int i = 0; i < executableType.getEvaluatedCount(); i++) {
                Parameter parameter = executableType.getSignatureParameter(i);
                if (i >= expectTypes.size()) {
                    break;
                }
                Set<TypeData> types = expectTypes.get(i);
                if (types == null) {
                    types = new TreeSet<>();
                    expectTypes.set(i, types);
                }
                types.add(parameter.getTypeSystemType());
            }
        }

        List<CodeExecutableElement> methods = new ArrayList<>();
        List<Set<TypeData>> visitedList = new ArrayList<>(prototype);
        for (SpecializationData spec : node.getSpecializations()) {
            int signatureIndex = -1;
            for (Parameter param : spec.getParameters()) {
                if (!param.getSpecification().isSignature()) {
                    continue;
                }
                signatureIndex++;
                Set<TypeData> visitedTypeData = visitedList.get(signatureIndex);
                if (visitedTypeData == null) {
                    visitedTypeData = new TreeSet<>();
                    visitedList.set(signatureIndex, visitedTypeData);
                }

                if (visitedTypeData.contains(param.getTypeSystemType())) {
                    continue;
                }
                visitedTypeData.add(param.getTypeSystemType());

                Set<TypeData> expect = expectTypes.get(signatureIndex);
                if (expect == null) {
                    expect = Collections.emptySet();
                }

                methods.addAll(createExecuteChilds(param, expect));
            }
        }
        return methods;
    }

    private CodeTree truffleBooleanOption(CodeTreeBuilder parent, String name) {
        CodeTreeBuilder builder = parent.create();
        builder.staticReference(getContext().getTruffleTypes().getTruffleOptions(), name);
        return builder.getRoot();
    }

    private final void addInternalValueParameters(CodeExecutableElement method, TemplateMethod specialization, boolean forceFrame, boolean disableFrame, boolean evaluated) {
        if (forceFrame && !disableFrame && specialization.getSpecification().findParameterSpec("frame") != null) {
            method.addParameter(new CodeVariableElement(getContext().getTruffleTypes().getFrame(), "frameValue"));
        }
        for (Parameter parameter : specialization.getParameters()) {
            ParameterSpec spec = parameter.getSpecification();
            if ((disableFrame || forceFrame) && spec.getName().equals("frame")) {
                continue;
            }
            if (spec.isLocal()) {
                continue;
            }

            String name = valueName(parameter);
            if (evaluated && spec.isSignature()) {
                name = valueNameEvaluated(parameter);
            }

            method.addParameter(new CodeVariableElement(parameter.getType(), name));
        }
    }

    private Element createInfoMessage(NodeData node) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, STATIC), getContext().getType(String.class), CREATE_INFO);
        method.addParameter(new CodeVariableElement(getContext().getType(String.class), "message"));
        addInternalValueParameters(method, node.getGenericSpecialization(), false, false, false);

        CodeTreeBuilder builder = method.createBuilder();

        builder.startIf().tree(truffleBooleanOption(builder, TruffleTypes.OPTION_DETAILED_REWRITE_REASONS)).end();
        builder.startBlock();

        builder.startStatement().string("StringBuilder builder = new StringBuilder(message)").end();
        builder.startStatement().startCall("builder", "append").doubleQuote(" (").end().end();

        String sep = null;
        for (Parameter parameter : node.getGenericSpecialization().getSignatureParameters()) {
            builder.startStatement();
            builder.string("builder");
            if (sep != null) {
                builder.startCall(".append").doubleQuote(sep).end();
            }
            builder.startCall(".append").doubleQuote(parameter.getLocalName()).end();
            builder.startCall(".append").doubleQuote(" = ").end();
            builder.startCall(".append").string(parameter.getLocalName()).end();
            builder.end();

            if (!ElementUtils.isPrimitive(parameter.getType())) {
                builder.startIf().string(parameter.getLocalName() + " != null").end();
                builder.startBlock();
            }
            builder.startStatement();
            if (ElementUtils.isPrimitive(parameter.getType())) {
                builder.startCall("builder.append").doubleQuote(" (" + ElementUtils.getSimpleName(parameter.getType()) + ")").end();
            } else {
                builder.startCall("builder.append").doubleQuote(" (").end();
                builder.startCall(".append").string(parameter.getLocalName() + ".getClass().getSimpleName()").end();
                builder.startCall(".append").doubleQuote(")").end();
            }
            builder.end();
            if (!ElementUtils.isPrimitive(parameter.getType())) {
                builder.end();
            }

            sep = ", ";
        }

        builder.startStatement().startCall("builder", "append").doubleQuote(")").end().end();
        builder.startReturn().string("builder.toString()").end();

        builder.end();
        builder.startElseBlock();
        builder.startReturn().string("message").end();
        builder.end();

        return method;
    }

    private CodeExecutableElement createCachedExecute(NodeData node, SpecializationData polymorph) {
        CodeExecutableElement cachedExecute = new CodeExecutableElement(modifiers(PROTECTED, ABSTRACT), polymorph.getReturnType().getType(), EXECUTE_CHAINED);
        addInternalValueParameters(cachedExecute, polymorph, true, false, false);

        ExecutableTypeData sourceExecutableType = node.findExecutableType(polymorph.getReturnType().getTypeSystemType(), 0);
        boolean sourceThrowsUnexpected = sourceExecutableType != null && sourceExecutableType.hasUnexpectedValue(getContext());
        if (sourceThrowsUnexpected && sourceExecutableType.getType().equals(node.getGenericSpecialization().getReturnType().getTypeSystemType())) {
            sourceThrowsUnexpected = false;
        }
        if (sourceThrowsUnexpected) {
            cachedExecute.getThrownTypes().add(getContext().getType(UnexpectedResultException.class));
        }
        return cachedExecute;

    }

    private void createConstructors(NodeData node, CodeTypeElement clazz) {
        List<ExecutableElement> constructors = findUserConstructors(node.getNodeType());
        ExecutableElement sourceSectionConstructor = null;
        if (constructors.isEmpty()) {
            clazz.add(createUserConstructor(clazz, null));
        } else {
            for (ExecutableElement constructor : constructors) {
                clazz.add(createUserConstructor(clazz, constructor));
                if (NodeParser.isSourceSectionConstructor(context, constructor)) {
                    sourceSectionConstructor = constructor;
                }
            }
        }
        if (node.needsRewrites(getContext())) {
            ExecutableElement copyConstructor = findCopyConstructor(node.getNodeType());
            clazz.add(createCopyConstructor(clazz, copyConstructor, sourceSectionConstructor));
        }
    }

    private CodeExecutableElement createUserConstructor(CodeTypeElement type, ExecutableElement superConstructor) {
        CodeExecutableElement method = new CodeExecutableElement(null, type.getSimpleName().toString());
        CodeTreeBuilder builder = method.createBuilder();

        NodeData node = getModel().getNode();

        if (superConstructor != null) {
            for (VariableElement param : superConstructor.getParameters()) {
                method.getParameters().add(CodeVariableElement.clone(param));
            }
        }

        if (superConstructor != null) {
            builder.startStatement().startSuperCall();
            for (VariableElement param : superConstructor.getParameters()) {
                builder.string(param.getSimpleName().toString());
            }
            builder.end().end();
        }

        for (VariableElement var : type.getFields()) {
            if (var.getModifiers().contains(STATIC)) {
                continue;
            }
            NodeChildData child = node.findChild(var.getSimpleName().toString());

            if (child != null) {
                method.getParameters().add(new CodeVariableElement(child.getOriginalType(), child.getName()));
            } else {
                method.getParameters().add(new CodeVariableElement(var.asType(), var.getSimpleName().toString()));
            }

            builder.startStatement();
            String fieldName = var.getSimpleName().toString();

            CodeTree init = createStaticCast(builder, child, fieldName);

            builder.string("this.").string(fieldName).string(" = ").tree(init);
            builder.end();
        }
        return method;
    }

    private CodeTree createStaticCast(CodeTreeBuilder parent, NodeChildData child, String fieldName) {
        NodeData parentNode = getModel().getNode();
        if (child != null) {
            CreateCastData createCast = parentNode.findCast(child.getName());
            if (createCast != null) {
                return createTemplateMethodCall(parent, null, parentNode.getGenericSpecialization(), createCast, null, fieldName);
            }
        }
        return CodeTreeBuilder.singleString(fieldName);
    }

    private CodeExecutableElement createCopyConstructor(CodeTypeElement type, ExecutableElement superConstructor, ExecutableElement sourceSectionConstructor) {
        CodeExecutableElement method = new CodeExecutableElement(null, type.getSimpleName().toString());
        CodeTreeBuilder builder = method.createBuilder();
        method.getParameters().add(new CodeVariableElement(type.asType(), "copy"));

        if (superConstructor != null) {
            builder.startStatement().startSuperCall().string("copy").end().end();
        } else if (sourceSectionConstructor != null) {
            builder.startStatement().startSuperCall().string("copy.getSourceSection()").end().end();
        }

        for (VariableElement var : type.getFields()) {
            if (var.getModifiers().contains(STATIC) || !var.getModifiers().contains(FINAL)) {
                continue;
            }
            final String varName = var.getSimpleName().toString();
            final TypeMirror varType = var.asType();
            if (ElementUtils.isAssignable(varType, getContext().getTruffleTypes().getNodeArray())) {
                CodeTree size = builder.create().string("copy.", varName, ".length").getRoot();
                builder.startStatement().string("this.").string(varName).string(" = ").startNewArray((ArrayType) varType, size).end().end();
            } else {
                builder.startStatement().string("this.", varName, " = copy.", varName).end();
            }
        }

        return method;
    }

    private CodeVariableElement createAssumptionField(String assumption) {
        CodeVariableElement var = new CodeVariableElement(getContext().getTruffleTypes().getAssumption(), assumption);
        var.getModifiers().add(Modifier.FINAL);
        return var;
    }

    private CodeVariableElement createChildField(NodeChildData child) {
        TypeMirror type = child.getNodeType();
        CodeVariableElement var = new CodeVariableElement(type, child.getName());
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

    private static SpecializationGroup createSpecializationGroups(final NodeData node) {
        List<SpecializationData> specializations = node.getSpecializations();
        List<SpecializationData> filteredSpecializations = new ArrayList<>();
        for (SpecializationData current : specializations) {
            if (current.isUninitialized() || current.isPolymorphic() || !current.isReachable()) {
                continue;
            }
            filteredSpecializations.add(current);
        }

        return SpecializationGroup.create(filteredSpecializations);
    }

    protected final CodeExecutableElement createExecuteUninitialized() {
        NodeData node = getModel().getNode();
        SpecializationData generic = node.getGenericSpecialization();
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED), generic.getReturnType().getType(), EXECUTE_UNINITIALIZED);
        addInternalValueParameters(method, generic, true, false, false);
        CodeTreeBuilder builder = method.createBuilder();

        boolean needsFrame = node.isFrameUsedByAnyGuard(getContext());
        CodeTreeBuilder createSpecializationCall = builder.create();
        createSpecializationCall.startCall(SPECIALIZE);
        addInternalValueParameterNames(createSpecializationCall, generic, generic, null, needsFrame, !needsFrame, null);
        createSpecializationCall.end();
        builder.declaration(baseClassName(node), "newNode", createSpecializationCall);

        if (generic.isReachable()) {
            builder.startIf().string("newNode == null").end().startBlock();

            builder.startIf().startStaticCall(context.getTruffleTypes().getCompilerDirectives(), "inInterpreter").end().end().startBlock();
            builder.statement("containsFallback = true");
            builder.end();
            builder.tree(createGenericInvoke(builder, generic, generic));
            builder.end();
            builder.startElseBlock();
            builder.tree(createDeoptimize(builder));
            builder.end();
        }

        builder.startReturn();
        builder.startStaticCall(context.getTruffleTypes().getDslShare(), "rewriteUninitialized").string("this").string("newNode").end();
        builder.string(".").startCall(EXECUTE_CHAINED);
        addInternalValueParameterNames(builder, generic, generic, null, true, false, null);
        builder.end();
        builder.end();

        if (generic.isReachable()) {
            builder.end();
        }

        return method;
    }

    private static CodeTree createInfoCall(CodeTreeBuilder parent, SpecializationData specialization, String reason) {
        CodeTreeBuilder builder = parent.create();
        builder.startCall(CREATE_INFO).string(reason);
        addInternalValueParameterNames(builder, specialization, specialization, null, false, false, null);
        builder.end();
        return builder.getRoot();
    }

    private CodeExecutableElement createMonomorphicRewrite() {
        NodeData node = getModel().getNode();

        SpecializationData generic = node.getGenericSpecialization();
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), generic.getReturnType().getType(), REWRITE);
        addInternalValueParameters(method, generic, true, false, false);
        method.addParameter(new CodeVariableElement(getContext().getType(String.class), "reason"));

        boolean needsFrame = node.isFrameUsedByAnyGuard(getContext());
        CodeTreeBuilder builder = method.createBuilder();

        builder.startStatement().startStaticCall(context.getTruffleTypes().getCompilerAsserts(), "neverPartOfCompilation").end().end();
        String baseClassName = baseClassName(getModel().getNode());
        CodeTreeBuilder createSpecializationCall = builder.create();
        createSpecializationCall.startCall(SPECIALIZE);
        addInternalValueParameterNames(createSpecializationCall, generic, generic, null, needsFrame, !needsFrame, null);
        createSpecializationCall.end();
        builder.declaration(baseClassName, "newNode", createSpecializationCall);

        builder.startIf().string("newNode == null").end().startBlock();
        builder.startStatement();
        String uninitializedName = nodeSpecializationClassName(node.getUninitializedSpecialization());
        builder.string("newNode = ").startNew(uninitializedName).string("this").end();
        builder.end();
        if (node.isFallbackReachable()) {
            builder.startStatement().string("((", uninitializedName, ") newNode).containsFallback = true").end();
        }
        builder.end();

        builder.startStatement();
        builder.type(getContext().getType(String.class)).string(" message = ").tree(createInfoCall(builder, generic, "reason"));
        builder.end();

        builder.declaration(baseClassName, "returnNode",
                        builder.create().startStaticCall(context.getTruffleTypes().getDslShare(), DSLSHARE_REWRITE).string("this").string("newNode").string("message").end().getRoot());
        builder.startIf().string("returnNode == null").end().startBlock();
        builder.tree(createRewritePolymorphic(builder, node, "this"));
        builder.end();

        builder.startReturn();
        builder.startCall("returnNode", EXECUTE_CHAINED);
        addInternalValueParameterNames(builder, node.getGenericSpecialization(), node.getGenericSpecialization(), null, true, false, null);
        builder.end();
        builder.end();

        return method;
    }

    private CodeTree createRewritePolymorphic(CodeTreeBuilder parent, NodeData node, String currentNode) {
        String polyClassName = nodePolymorphicClassName(node);
        CodeTreeBuilder builder = parent.create();

        builder.startStatement().string("returnNode = ");
        builder.startStaticCall(context.getTruffleTypes().getDslShare(), DSLSHARE_REWRITE_TO_POLYMORHPIC);
        builder.string("this");
        builder.tree(builder.create().startNew(nodeSpecializationClassName(node.getUninitializedSpecialization())).string(currentNode).end().getRoot());
        builder.tree(builder.create().startNew(polyClassName).string(currentNode).end().getRoot());
        builder.startGroup().cast(baseClassName(node)).startCall("copy").end().end();
        builder.string("newNode");
        builder.string("message");
        builder.end();
        builder.end();

        return builder.getRoot();
    }

    private CodeExecutableElement createCreateSpecializationMethod(NodeData node, SpecializationGroup group) {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, FINAL), new GeneratedTypeMirror(ElementUtils.getPackageName(node.getTemplateType()), baseClassName(node)),
                        SPECIALIZE);

        final boolean needsFrame = node.isFrameUsedByAnyGuard(getContext());

        if (!needsFrame) {
            method.getAnnotationMirrors().add(new CodeAnnotationMirror(getContext().getTruffleTypes().getTruffleBoundary()));
        }

        addInternalValueParameters(method, node.getGenericSpecialization(), needsFrame, !needsFrame, false);
        final CodeTreeBuilder builder = method.createBuilder();
        builder.tree(createExecuteTree(builder, node.getGenericSpecialization(), group, new CodeBlock<SpecializationData>() {

            public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                return createCreateSpecializationMethodBody0(builder, current, needsFrame);
            }
        }, null, false, true, false, true));

        emitUnreachableSpecializations(builder, node);

        return method;
    }

    private CodeTree createCreateSpecializationMethodBody0(CodeTreeBuilder parent, SpecializationData current, boolean useDeoptimize) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        if (current.isGeneric()) {
            builder.startReturn().nullLiteral().end();
        } else {
            String className = nodeSpecializationClassName(current);
            if (!current.getExcludedBy().isEmpty()) {
                builder.startIf().string("!").startStaticCall(context.getTruffleTypes().getDslShare(), "isExcluded");
                builder.string("this").string(nodeSpecializationClassName(current), ".", METADATA_FIELD_NAME).end().end();
                builder.startBlock();
            }

            if (current.getNode().getGenericSpecialization().isReachable() && useDeoptimize) {
                builder.tree(createDeoptimize(builder));
            }
            builder.startReturn();
            builder.cast(baseClassName(getModel().getNode()));
            builder.startGroup().startCall(className, NodeFactoryFactory.FACTORY_METHOD_NAME).string("this");
            for (Parameter param : current.getSignatureParameters()) {
                NodeChildData child = param.getSpecification().getExecution().getChild();
                List<TypeData> types = child.getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
                if (types.size() > 1) {
                    builder.string(implicitTypeName(param));
                }
            }
            builder.end().end();
            builder.end();

            if (!current.getExcludedBy().isEmpty()) {
                builder.end();
            }
        }
        return builder.getRoot();

    }

    private static void emitUnreachableSpecializations(final CodeTreeBuilder builder, NodeData node) {
        for (SpecializationData current : node.getSpecializations()) {
            if (current.isReachable()) {
                continue;
            }
            builder.string("// unreachable ").string(current.getId()).newLine();
        }
    }

    protected CodeTree createExecuteTree(CodeTreeBuilder outerParent, final SpecializationData source, final SpecializationGroup group, final CodeBlock<SpecializationData> guardedblock,
                    final CodeTree elseBlock, boolean forceElse, final boolean emitAssumptions, final boolean typedCasts, final boolean castForGuardsOnly) {
        return guard(outerParent, source, group, new CodeBlock<Integer>() {

            public CodeTree create(CodeTreeBuilder parent, Integer ifCount) {
                CodeTreeBuilder builder = parent.create();

                if (group.getSpecialization() != null) {
                    builder.tree(guardedblock.create(builder, group.getSpecialization()));

                    assert group.getChildren().isEmpty() : "missed a specialization";

                } else {
                    for (SpecializationGroup childGroup : group.getChildren()) {
                        builder.tree(createExecuteTree(builder, source, childGroup, guardedblock, null, false, emitAssumptions, typedCasts, castForGuardsOnly));
                    }
                }

                return builder.getRoot();
            }
        }, elseBlock, forceElse, emitAssumptions, typedCasts, castForGuardsOnly);
    }

    private CodeTree guard(CodeTreeBuilder parent, SpecializationData source, SpecializationGroup group, CodeBlock<Integer> bodyBlock, CodeTree elseBlock, boolean forceElse, boolean emitAssumptions,
                    boolean typedCasts, boolean castForGuardsOnly) {
        CodeTreeBuilder builder = parent.create();

        int ifCount = emitGuards(builder, source, group, emitAssumptions, typedCasts, castForGuardsOnly);

        if (isReachableGroup(group, ifCount)) {
            builder.tree(bodyBlock.create(builder, ifCount));
        }

        builder.end(ifCount);

        if (elseBlock != null) {
            if (ifCount > 0 || forceElse) {
                builder.tree(elseBlock);
            }
        }

        return builder.getRoot();
    }

    private static boolean isReachableGroup(SpecializationGroup group, int ifCount) {
        if (ifCount != 0) {
            return true;
        }
        SpecializationGroup previous = group.getPreviousGroup();
        if (previous == null || previous.findElseConnectableGuards().isEmpty()) {
            return true;
        }

        /*
         * Hacky else case. In this case the specialization is not reachable due to previous else
         * branch. This is only true if the minimum state is not checked.
         */
        if (previous.getGuards().size() == 1 && previous.getTypeGuards().isEmpty() && previous.getAssumptions().isEmpty() &&
                        (previous.getParent() == null || previous.getMaxSpecializationIndex() != previous.getParent().getMaxSpecializationIndex())) {
            return false;
        }

        return true;
    }

    private int emitGuards(CodeTreeBuilder builder, SpecializationData source, SpecializationGroup group, boolean emitAssumptions, boolean typedCasts, boolean castForGuardsOnly) {
        NodeData node = source.getNode();

        CodeTreeBuilder guardsBuilder = builder.create();
        CodeTreeBuilder castBuilder = builder.create();
        CodeTreeBuilder guardsCastBuilder = builder.create();

        String guardsAnd = "";
        String guardsCastAnd = "";

        if (emitAssumptions) {
            for (String assumption : group.getAssumptions()) {
                guardsBuilder.string(guardsAnd);
                guardsBuilder.string("this");
                guardsBuilder.string(".").string(assumption).string(".isValid()");
                guardsAnd = " && ";
            }
        }

        for (TypeGuard typeGuard : group.getTypeGuards()) {
            Parameter valueParam = source.getSignatureParameter(typeGuard.getSignatureIndex());

            if (valueParam == null) {
                /*
                 * If used inside a execute evaluated method then the value param may not exist. In
                 * that case we assume that the value is executed generic or of the current
                 * specialization.
                 */
                if (group.getSpecialization() != null) {
                    valueParam = group.getSpecialization().getSignatureParameter(typeGuard.getSignatureIndex());
                } else {
                    valueParam = node.getGenericSpecialization().getSignatureParameter(typeGuard.getSignatureIndex());
                }
            }

            NodeExecutionData execution = valueParam.getSpecification().getExecution();
            CodeTree implicitGuard = createTypeGuard(guardsBuilder, execution, valueParam, typeGuard.getType(), typedCasts);
            if (implicitGuard != null) {
                guardsBuilder.string(guardsAnd);
                guardsBuilder.tree(implicitGuard);
                guardsAnd = " && ";
            }

            CodeTree implicitGetType = null;
            if (castForGuardsOnly) {
                implicitGetType = createGetImplicitType(builder, execution, valueParam, typeGuard.getType());
            }

            boolean performCast = true;
            if (castForGuardsOnly) {
                // if cast for guards we just cast if the type guard is used inside a guard.
                performCast = group.isTypeGuardUsedInAnyGuardBelow(context, source, typeGuard);
            }

            if (performCast) {
                CodeTree cast = createCast(castBuilder, execution, valueParam, typeGuard.getType(), typedCasts);
                if (cast != null) {
                    castBuilder.tree(cast);
                }
            }
            if (implicitGetType != null) {
                castBuilder.tree(implicitGetType);
            }
        }
        List<GuardExpression> elseGuards = group.findElseConnectableGuards();

        for (GuardExpression guard : group.getGuards()) {
            if (elseGuards.contains(guard)) {
                continue;
            }

            if (needsTypeGuard(source, group, guard)) {
                guardsCastBuilder.tree(createMethodGuard(builder, guardsCastAnd, source, guard));
                guardsCastAnd = " && ";
            } else {
                guardsBuilder.tree(createMethodGuard(builder, guardsAnd, source, guard));
                guardsAnd = " && ";
            }
        }

        int ifCount = startGuardIf(builder, guardsBuilder, 0, elseGuards);
        builder.tree(castBuilder.getRoot());
        ifCount = startGuardIf(builder, guardsCastBuilder, ifCount, elseGuards);
        return ifCount;
    }

    private static int startGuardIf(CodeTreeBuilder builder, CodeTreeBuilder conditionBuilder, int ifCount, List<GuardExpression> elseGuard) {
        int newIfCount = ifCount;

        if (!conditionBuilder.isEmpty()) {
            if (ifCount == 0 && !elseGuard.isEmpty()) {
                builder.startElseIf();
            } else {
                builder.startIf();
            }
            builder.tree(conditionBuilder.getRoot());
            builder.end().startBlock();
            newIfCount++;
        } else if (ifCount == 0 && !elseGuard.isEmpty()) {
            builder.startElseBlock();
            newIfCount++;
        }
        return newIfCount;
    }

    private static boolean needsTypeGuard(SpecializationData source, SpecializationGroup group, GuardExpression guard) {
        for (Parameter parameter : guard.getResolvedGuard().getParameters()) {
            if (!parameter.getSpecification().isSignature()) {
                continue;
            }

            int signatureIndex = source.getNode().getChildExecutions().indexOf(parameter.getSpecification().getExecution());
            if (signatureIndex == -1) {
                continue;
            }

            TypeGuard typeGuard = group.findTypeGuard(signatureIndex);
            if (typeGuard != null) {
                TypeData requiredType = typeGuard.getType();

                Parameter sourceParameter = source.findParameter(parameter.getLocalName());
                if (sourceParameter == null) {
                    sourceParameter = source.getNode().getGenericSpecialization().findParameter(parameter.getLocalName());
                }

                if (ElementUtils.needsCastTo(sourceParameter.getType(), requiredType.getPrimitiveType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private CodeTree createTypeGuard(CodeTreeBuilder parent, NodeExecutionData execution, Parameter source, TypeData targetType, boolean typedCasts) {
        NodeData node = execution.getChild().getNodeData();

        CodeTreeBuilder builder = new CodeTreeBuilder(parent);

        TypeData sourceType = source.getTypeSystemType();

        if (!sourceType.needsCastTo(targetType)) {
            return null;
        }

        builder.startGroup();

        if (execution.isShortCircuit()) {
            Parameter shortCircuit = source.getPreviousParameter();
            assert shortCircuit != null;
            builder.string("(");
            builder.string("!").string(valueName(shortCircuit));
            builder.string(" || ");
        }

        String castMethodName;
        String castTypeName = null;
        List<TypeData> types = getModel().getNode().getTypeSystem().lookupSourceTypes(targetType);
        if (types.size() > 1) {
            castMethodName = TypeSystemCodeGenerator.isImplicitTypeMethodName(targetType);
            if (typedCasts) {
                castTypeName = implicitTypeName(source);
            }
        } else {
            castMethodName = TypeSystemCodeGenerator.isTypeMethodName(targetType);
        }

        startCallTypeSystemMethod(builder, node.getTypeSystem(), castMethodName);
        builder.string(valueName(source));
        if (castTypeName != null) {
            builder.string(castTypeName);
        }
        builder.end().end(); // call

        if (execution.isShortCircuit()) {
            builder.string(")");
        }

        builder.end(); // group

        return builder.getRoot();
    }

    // TODO merge redundancies with #createTypeGuard
    private CodeTree createCast(CodeTreeBuilder parent, NodeExecutionData execution, Parameter source, TypeData targetType, boolean typedCasts) {
        NodeData node = execution.getChild().getNodeData();
        TypeData sourceType = source.getTypeSystemType();

        if (!sourceType.needsCastTo(targetType)) {
            return null;
        }

        CodeTree condition = null;
        if (execution.isShortCircuit()) {
            Parameter shortCircuit = source.getPreviousParameter();
            assert shortCircuit != null;
            condition = CodeTreeBuilder.singleString(valueName(shortCircuit));
        }

        String castMethodName;
        String castTypeName = null;
        List<TypeData> types = getModel().getNode().getTypeSystem().lookupSourceTypes(targetType);
        if (types.size() > 1) {
            castMethodName = TypeSystemCodeGenerator.asImplicitTypeMethodName(targetType);
            if (typedCasts) {
                castTypeName = implicitTypeName(source);
            }
        } else {
            castMethodName = TypeSystemCodeGenerator.asTypeMethodName(targetType);
        }

        List<CodeTree> args = new ArrayList<>();
        args.add(CodeTreeBuilder.singleString(valueName(source)));
        if (castTypeName != null) {
            args.add(CodeTreeBuilder.singleString(castTypeName));
        }

        CodeTree cast = createCallTypeSystemMethod(parent, node, castMethodName, args.toArray(new CodeTree[0]));

        CodeTreeBuilder builder = parent.create();
        builder.tree(createLazyAssignment(parent, castValueName(source), targetType.getPrimitiveType(), condition, cast));

        return builder.getRoot();
    }

    private CodeTree createGetImplicitType(CodeTreeBuilder parent, NodeExecutionData execution, Parameter source, TypeData targetType) {
        CodeTree condition = null;
        if (execution.isShortCircuit()) {
            Parameter shortCircuit = source.getPreviousParameter();
            assert shortCircuit != null;
            condition = CodeTreeBuilder.singleString(valueName(shortCircuit));
        }

        CodeTreeBuilder builder = parent.create();
        List<TypeData> types = getModel().getNode().getTypeSystem().lookupSourceTypes(targetType);
        if (types.size() > 1) {
            CodeTree castType = createCallTypeSystemMethod(parent, execution.getChild().getNodeData(), TypeSystemCodeGenerator.getImplicitClass(targetType),
                            CodeTreeBuilder.singleString(valueName(source)));
            builder.tree(createLazyAssignment(builder, implicitTypeName(source), getContext().getType(Class.class), condition, castType));
        }
        return builder.getRoot();
    }

    private static CodeTree createMethodGuard(CodeTreeBuilder parent, String prefix, SpecializationData source, GuardExpression guard) {
        CodeTreeBuilder builder = parent.create();
        builder.string(prefix);
        if (guard.isNegated()) {
            builder.string("!");
        }
        builder.tree(createTemplateMethodCall(builder, null, source, guard.getResolvedGuard(), null));
        return builder.getRoot();
    }

    protected CodeTree createGenericInvoke(CodeTreeBuilder parent, SpecializationData source, SpecializationData current) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);

        if (current.getMethod() == null) {
            emitEncounteredSynthetic(builder, getModel().getNode(), current);
        } else {
            builder.startReturn().tree(createTemplateMethodCall(builder, null, source, current, null)).end();
        }

        return encloseThrowsWithFallThrough(parent, current, builder.getRoot());
    }

    private CodeTree encloseThrowsWithFallThrough(CodeTreeBuilder parent, SpecializationData current, CodeTree tree) {
        if (current.getExceptions().isEmpty()) {
            return tree;
        }
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);

        builder.startTryBlock();
        builder.tree(tree);
        for (SpecializationThrowsData exception : current.getExceptions()) {
            builder.end().startCatchBlock(exception.getJavaClass(), "rewriteEx");
            builder.tree(createDeoptimize(builder));
            builder.tree(createCallRewriteMonomorphic(builder, false, current.getNode().getGenericSpecialization().getReturnType().getTypeSystemType(), current, null,
                            "Thrown " + ElementUtils.getSimpleName(exception.getJavaClass())));
        }
        builder.end();

        return builder.getRoot();
    }

    protected CodeTree createCastingExecute(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData executable, ExecutableTypeData castExecutable) {
        TypeData type = executable.getType();
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        NodeData node = specialization.getNode();

        TypeData primaryType = castExecutable.getType();

        boolean needsTry = castExecutable.hasUnexpectedValue(getContext());
        boolean returnVoid = type.isVoid();

        List<Parameter> executeParameters = new ArrayList<>();
        for (Parameter sourceParameter : executable.getSignatureParameters()) {
            Parameter targetParameter = castExecutable.findParameter(sourceParameter.getLocalName());
            if (targetParameter != null) {
                executeParameters.add(targetParameter);
            }
        }

        // execute names are enforced no cast
        String[] executeParameterNames = new String[executeParameters.size()];
        for (int i = 0; i < executeParameterNames.length; i++) {
            executeParameterNames[i] = valueName(executeParameters.get(i));
        }

        builder.tree(createExecuteChildren(builder, executable, specialization, executeParameters, null));
        boolean hasUnexpected = executable.hasUnexpectedValue(getContext());

        CodeTree primaryExecuteCall = createTemplateMethodCall(builder, null, executable, castExecutable, null, executeParameterNames);
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
                builder.tree(createExpectExecutableType(node, specialization.getNode().getTypeSystem().getGenericTypeData(), hasUnexpected, executable.getType(),
                                CodeTreeBuilder.singleString("ex.getResult()")));
                builder.end();
            }
            builder.end();

            if (!returnVoid) {
                builder.startReturn();
                builder.tree(createExpectExecutableType(node, castExecutable.getReturnType().getTypeSystemType(), hasUnexpected, executable.getType(), CodeTreeBuilder.singleString("value")));
                builder.end();
            }
        } else {
            if (returnVoid) {
                builder.statement(primaryExecuteCall);
            } else {
                builder.startReturn();
                builder.tree(createExpectExecutableType(node, castExecutable.getReturnType().getTypeSystemType(), hasUnexpected, executable.getType(), primaryExecuteCall));
                builder.end();
            }
        }

        return builder.getRoot();
    }

    private static CodeTree createExpectExecutableType(NodeData node, TypeData sourceType, boolean hasUnexpected, TypeData exepctedType, CodeTree value) {
        return createCastType(node.getTypeSystem(), sourceType, exepctedType, hasUnexpected, value);
    }

    protected CodeTree createExecuteChildren(CodeTreeBuilder parent, ExecutableTypeData sourceExecutable, SpecializationData specialization, List<Parameter> targetParameters,
                    Parameter unexpectedParameter) {
        CodeTreeBuilder builder = parent.create();
        for (Parameter targetParameter : targetParameters) {
            if (!targetParameter.getSpecification().isSignature()) {
                continue;
            }
            NodeExecutionData execution = targetParameter.getSpecification().getExecution();
            CodeTree executionExpressions = createExecuteChild(builder, execution, sourceExecutable, targetParameter, unexpectedParameter);
            CodeTree unexpectedTree = createCatchUnexpectedTree(builder, executionExpressions, specialization, sourceExecutable, targetParameter, execution.isShortCircuit(), unexpectedParameter);
            CodeTree shortCircuitTree = createShortCircuitTree(builder, unexpectedTree, specialization, targetParameter, unexpectedParameter);

            if (shortCircuitTree == executionExpressions) {
                if (containsNewLine(executionExpressions)) {
                    builder.declaration(targetParameter.getType(), valueName(targetParameter));
                    builder.tree(shortCircuitTree);
                } else {
                    builder.startStatement().type(targetParameter.getType()).string(" ").tree(shortCircuitTree).end();
                }
            } else {
                builder.tree(shortCircuitTree);
            }

        }
        return builder.getRoot();
    }

    private ExecutableTypeData resolveExecutableType(NodeExecutionData execution, TypeData type) {
        ExecutableTypeData targetExecutable = execution.getChild().findExecutableType(getContext(), type);
        if (targetExecutable == null) {
            targetExecutable = execution.getChild().findAnyGenericExecutableType(getContext());
        }
        return targetExecutable;
    }

    private CodeTree createExecuteChild(CodeTreeBuilder parent, NodeExecutionData execution, ExecutableTypeData sourceExecutable, Parameter targetParameter, Parameter unexpectedParameter) {
        SpecializationData specialization = getModel();
        if (specialization.isPolymorphic() && targetParameter.getTypeSystemType().isGeneric() && unexpectedParameter == null) {
            List<TypeData> possiblePolymorphicTypes = lookupPolymorphicTargetTypes(targetParameter);
            if (possiblePolymorphicTypes.size() > 1) {
                CodeTreeBuilder builder = parent.create();

                boolean elseIf = false;
                for (TypeData possiblePolymoprhicType : possiblePolymorphicTypes) {
                    if (possiblePolymoprhicType.isGeneric()) {
                        continue;
                    }
                    elseIf = builder.startIf(elseIf);

                    Parameter sourceParameter = sourceExecutable.findParameter(targetParameter.getLocalName());
                    TypeData sourceType = sourceParameter != null ? sourceParameter.getTypeSystemType() : null;
                    builder.string(polymorphicTypeName(targetParameter.getSpecification().getExecution())).string(" == ").typeLiteral(possiblePolymoprhicType.getPrimitiveType());
                    builder.end().startBlock();
                    builder.startStatement();
                    builder.tree(createExecuteChildExpression(parent, execution, sourceType, new Parameter(targetParameter, possiblePolymoprhicType), unexpectedParameter, null));
                    builder.end();
                    builder.end();
                }

                builder.startElseBlock();
                builder.startStatement().tree(createExecuteChildImplicit(parent, execution, sourceExecutable, targetParameter, unexpectedParameter)).end();
                builder.end();

                return builder.getRoot();
            }
        }
        return createExecuteChildImplicit(parent, execution, sourceExecutable, targetParameter, unexpectedParameter);
    }

    protected static final List<Parameter> getImplicitTypeParameters(SpecializationData model) {
        List<Parameter> parameter = new ArrayList<>();
        for (Parameter param : model.getSignatureParameters()) {
            NodeChildData child = param.getSpecification().getExecution().getChild();
            List<TypeData> types = child.getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
            if (types.size() > 1) {
                parameter.add(param);
            }
        }
        return parameter;
    }

    private final List<TypeData> lookupPolymorphicTargetTypes(Parameter param) {
        SpecializationData specialization = getModel();
        Set<TypeData> possiblePolymorphicTypes = new HashSet<>();
        for (SpecializationData otherSpecialization : specialization.getNode().getSpecializations()) {
            if (!otherSpecialization.isSpecialized()) {
                continue;
            }
            Parameter otherParameter = otherSpecialization.findParameter(param.getLocalName());
            if (otherParameter != null) {
                possiblePolymorphicTypes.add(otherParameter.getTypeSystemType());
            }
        }
        List<TypeData> types = new ArrayList<>(possiblePolymorphicTypes);
        Collections.sort(types);
        return types;
    }

    private CodeTree createExecuteChildImplicit(CodeTreeBuilder parent, NodeExecutionData execution, ExecutableTypeData sourceExecutable, Parameter param, Parameter unexpectedParameter) {
        CodeTreeBuilder builder = parent.create();
        Parameter sourceParameter = sourceExecutable.findParameter(param.getLocalName());
        String childExecuteName = createExecuteChildMethodName(param, sourceParameter != null);
        if (childExecuteName != null) {
            builder.string(valueName(param));
            builder.string(" = ");
            builder.startCall(childExecuteName);

            for (Parameter parameters : sourceExecutable.getParameters()) {
                if (parameters.getSpecification().isSignature()) {
                    continue;
                }
                builder.string(parameters.getLocalName());
            }

            if (sourceParameter != null) {
                builder.string(valueNameEvaluated(sourceParameter));
            }

            builder.string(implicitTypeName(param));

            builder.end();
        } else {
            List<TypeData> sourceTypes = execution.getChild().getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
            TypeData expectType = sourceParameter != null ? sourceParameter.getTypeSystemType() : null;
            if (sourceTypes.size() > 1) {
                builder.tree(createExecuteChildImplicitExpressions(parent, param, expectType));
            } else {
                builder.tree(createExecuteChildExpression(parent, execution, expectType, param, unexpectedParameter, null));
            }
        }
        return builder.getRoot();
    }

    private static String createExecuteChildMethodName(Parameter param, boolean expect) {
        NodeExecutionData execution = param.getSpecification().getExecution();
        NodeChildData child = execution.getChild();
        if (child.getExecuteWith().size() > 0) {
            return null;
        }
        List<TypeData> sourceTypes = child.getNodeData().getTypeSystem().lookupSourceTypes(param.getTypeSystemType());
        if (sourceTypes.size() <= 1) {
            return null;
        }
        String prefix = expect ? "expect" : "execute";
        String suffix = execution.getIndex() > -1 ? String.valueOf(execution.getIndex()) : "";
        return prefix + ElementUtils.firstLetterUpperCase(child.getName()) + ElementUtils.firstLetterUpperCase(ElementUtils.getTypeId(param.getType())) + suffix;
    }

    private List<CodeExecutableElement> createExecuteChilds(Parameter param, Set<TypeData> expectTypes) {
        CodeExecutableElement executeMethod = createExecuteChild(param, null);
        if (executeMethod == null) {
            return Collections.emptyList();
        }
        List<CodeExecutableElement> childs = new ArrayList<>();
        childs.add(executeMethod);

        for (TypeData expectType : expectTypes) {
            CodeExecutableElement method = createExecuteChild(param, expectType);
            if (method != null) {
                childs.add(method);
            }
        }
        return childs;
    }

    private CodeExecutableElement createExecuteChild(Parameter param, TypeData expectType) {
        String childExecuteName = createExecuteChildMethodName(param, expectType != null);
        if (childExecuteName == null) {
            return null;
        }

        CodeExecutableElement method = new CodeExecutableElement(modifiers(PROTECTED, expectType != null ? STATIC : FINAL), param.getType(), childExecuteName);
        method.getThrownTypes().add(getContext().getTruffleTypes().getUnexpectedValueException());
        method.addParameter(new CodeVariableElement(getContext().getTruffleTypes().getFrame(), "frameValue"));
        if (expectType != null) {
            method.addParameter(new CodeVariableElement(expectType.getPrimitiveType(), valueNameEvaluated(param)));
        }
        method.addParameter(new CodeVariableElement(getContext().getType(Class.class), implicitTypeName(param)));

        CodeTreeBuilder builder = method.createBuilder();
        builder.declaration(param.getType(), valueName(param));
        builder.tree(createExecuteChildImplicitExpressions(builder, param, expectType));
        builder.startReturn().string(valueName(param)).end();

        return method;
    }

    private CodeTree createExecuteChildImplicitExpressions(CodeTreeBuilder parent, Parameter targetParameter, TypeData expectType) {
        CodeTreeBuilder builder = parent.create();
        NodeData node = getModel().getNode();
        NodeExecutionData execution = targetParameter.getSpecification().getExecution();
        List<TypeData> sourceTypes = node.getTypeSystem().lookupSourceTypes(targetParameter.getTypeSystemType());
        boolean elseIf = false;
        int index = 0;
        for (TypeData sourceType : sourceTypes) {
            if (index < sourceTypes.size() - 1) {
                elseIf = builder.startIf(elseIf);
                builder.string(implicitTypeName(targetParameter)).string(" == ").typeLiteral(sourceType.getPrimitiveType());
                builder.end();
                builder.startBlock();
            } else {
                builder.startElseBlock();
            }

            ExecutableTypeData implictExecutableTypeData = execution.getChild().findExecutableType(getContext(), sourceType);
            if (implictExecutableTypeData == null) {
                /*
                 * For children with executeWith.size() > 0 an executable type may not exist so use
                 * the generic executable type which is guaranteed to exist. An expect call is
                 * inserted automatically by #createExecuteExpression.
                 */
                implictExecutableTypeData = execution.getChild().getNodeData().findExecutableType(node.getTypeSystem().getGenericTypeData(), execution.getChild().getExecuteWith().size());
            }

            ImplicitCastData cast = execution.getChild().getNodeData().getTypeSystem().lookupCast(sourceType, targetParameter.getTypeSystemType());
            CodeTree execute = createExecuteChildExpression(builder, execution, expectType, targetParameter, null, cast);
            builder.statement(execute);
            builder.end();
            index++;
        }
        return builder.getRoot();
    }

    private CodeTree createExecuteChildExpression(CodeTreeBuilder parent, NodeExecutionData execution, TypeData sourceParameterType, Parameter targetParameter, Parameter unexpectedParameter,
                    ImplicitCastData cast) {
        // assignments: targetType <- castTargetType <- castSourceType <- sourceType
        TypeData sourceType = sourceParameterType;
        TypeData targetType = targetParameter.getTypeSystemType();
        TypeData castSourceType = targetType;
        TypeData castTargetType = targetType;

        if (cast != null) {
            castSourceType = cast.getSourceType();
            castTargetType = cast.getTargetType();
        }

        CodeTree expression;
        if (sourceType == null) {
            ExecutableTypeData targetExecutable = resolveExecutableType(execution, castSourceType);
            expression = createExecuteChildExpression(parent, execution, targetExecutable, unexpectedParameter);
            sourceType = targetExecutable.getType();
        } else {
            expression = CodeTreeBuilder.singleString(valueNameEvaluated(targetParameter));
        }

        // target = expectTargetType(implicitCast(expectCastSourceType(source)))
        TypeSystemData typeSystem = execution.getChild().getNodeData().getTypeSystem();
        expression = createExpectType(typeSystem, sourceType, castSourceType, expression);
        expression = createImplicitCast(parent, typeSystem, cast, expression);
        expression = createExpectType(typeSystem, castTargetType, targetType, expression);

        CodeTreeBuilder builder = parent.create();
        builder.string(valueName(targetParameter));
        builder.string(" = ");
        builder.tree(expression);
        return builder.getRoot();
    }

    private static CodeTree createImplicitCast(CodeTreeBuilder parent, TypeSystemData typeSystem, ImplicitCastData cast, CodeTree expression) {
        if (cast == null) {
            return expression;
        }
        CodeTreeBuilder builder = parent.create();
        startCallTypeSystemMethod(builder, typeSystem, cast.getMethodName());
        builder.tree(expression);
        builder.end().end();
        return builder.getRoot();
    }

    private boolean containsNewLine(CodeTree tree) {
        if (tree.getCodeKind() == CodeTreeKind.NEW_LINE) {
            return true;
        }

        List<CodeTree> enclosing = tree.getEnclosedElements();
        if (enclosing != null) {
            for (CodeTree codeTree : enclosing) {
                if (containsNewLine(codeTree)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUnexpected(Parameter sourceParameter, Parameter targetParameter, Parameter unexpectedParameter) {
        NodeExecutionData execution = targetParameter.getSpecification().getExecution();

        if (getModel().isPolymorphic() && targetParameter.getTypeSystemType().isGeneric() && unexpectedParameter == null) {
            // check for other polymorphic types
            List<TypeData> polymorphicTargetTypes = lookupPolymorphicTargetTypes(targetParameter);
            if (polymorphicTargetTypes.size() > 1) {
                for (TypeData polymorphicTargetType : polymorphicTargetTypes) {
                    if (hasUnexpectedType(execution, sourceParameter, polymorphicTargetType)) {
                        return true;
                    }
                }
            }
        }

        if (hasUnexpectedType(execution, sourceParameter, targetParameter.getTypeSystemType())) {
            return true;
        }
        return false;
    }

    private boolean hasUnexpectedType(NodeExecutionData execution, Parameter sourceParameter, TypeData targetType) {
        List<TypeData> implicitSourceTypes = getModel().getNode().getTypeSystem().lookupSourceTypes(targetType);

        for (TypeData implicitSourceType : implicitSourceTypes) {
            TypeData sourceType;
            ExecutableTypeData targetExecutable = resolveExecutableType(execution, implicitSourceType);
            if (sourceParameter != null) {
                sourceType = sourceParameter.getTypeSystemType();
            } else {
                if (targetExecutable.hasUnexpectedValue(getContext())) {
                    return true;
                }
                sourceType = targetExecutable.getType();
            }

            ImplicitCastData cast = getModel().getNode().getTypeSystem().lookupCast(implicitSourceType, targetType);
            if (cast != null) {
                if (cast.getSourceType().needsCastTo(targetType)) {
                    return true;
                }
            }

            if (sourceType.needsCastTo(targetType)) {
                return true;
            }
        }
        return false;
    }

    private CodeTree createCatchUnexpectedTree(CodeTreeBuilder parent, CodeTree body, SpecializationData specialization, ExecutableTypeData currentExecutable, Parameter param, boolean shortCircuit,
                    Parameter unexpectedParameter) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        Parameter sourceParameter = currentExecutable.findParameter(param.getLocalName());
        boolean unexpected = hasUnexpected(sourceParameter, param, unexpectedParameter);
        if (!unexpected) {
            return body;
        }

        if (!shortCircuit) {
            builder.declaration(param.getType(), valueName(param));
        }
        builder.startTryBlock();

        if (containsNewLine(body)) {
            builder.tree(body);
        } else {
            builder.statement(body);
        }

        builder.end().startCatchBlock(getUnexpectedValueException(), "ex");
        SpecializationData generic = specialization.getNode().getGenericSpecialization();
        Parameter genericParameter = generic.findParameter(param.getLocalName());

        List<Parameter> genericParameters = generic.getParametersAfter(genericParameter);
        builder.tree(createExecuteChildren(parent, currentExecutable, generic, genericParameters, genericParameter));
        if (specialization.isPolymorphic()) {
            builder.tree(createReturnOptimizeTypes(builder, currentExecutable, specialization, param));
        } else {
            builder.tree(createCallRewriteMonomorphic(builder, currentExecutable.hasUnexpectedValue(context), currentExecutable.getType(), specialization, param, "Expected " + param.getLocalName() +
                            " instanceof " + ElementUtils.getSimpleName(param.getType())));
        }
        builder.end(); // catch block

        return builder.getRoot();
    }

    private CodeTree createReturnOptimizeTypes(CodeTreeBuilder parent, ExecutableTypeData currentExecutable, SpecializationData specialization, Parameter param) {
        NodeData node = specialization.getNode();
        SpecializationData polymorphic = node.getPolymorphicSpecialization();

        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        builder.startStatement().string(polymorphicTypeName(param.getSpecification().getExecution())).string(" = ").typeLiteral(getContext().getType(Object.class)).end();

        builder.startReturn();

        CodeTreeBuilder execute = new CodeTreeBuilder(builder);
        execute.startCall("next0", EXECUTE_CHAINED);
        addInternalValueParameterNames(execute, specialization, polymorphic, param.getLocalName(), true, false, null);
        execute.end();

        TypeData sourceType = polymorphic.getReturnType().getTypeSystemType();

        builder.tree(createExpectExecutableType(node, sourceType, currentExecutable.hasUnexpectedValue(context), currentExecutable.getType(), execute.getRoot()));

        builder.end();
        return builder.getRoot();
    }

    private CodeTree createExecuteChildExpression(CodeTreeBuilder parent, NodeExecutionData targetExecution, ExecutableTypeData targetExecutable, Parameter unexpectedParameter) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        if (targetExecution != null) {
            builder.tree(createAccessChild(targetExecution, null));
            builder.string(".");
        }

        builder.startCall(targetExecutable.getMethodName());

        // TODO this should be merged with #createTemplateMethodCall
        int index = 0;
        for (Parameter parameter : targetExecutable.getParameters()) {

            if (!parameter.getSpecification().isSignature()) {
                builder.string(parameter.getLocalName());
            } else {

                if (index < targetExecution.getChild().getExecuteWith().size()) {
                    NodeChildData child = targetExecution.getChild().getExecuteWith().get(index);

                    ParameterSpec spec = getModel().getSpecification().findParameterSpec(child.getName());
                    List<Parameter> specializationParams = getModel().findParameters(spec);

                    if (specializationParams.isEmpty()) {
                        builder.defaultValue(parameter.getType());
                        continue;
                    }

                    Parameter specializationParam = specializationParams.get(0);

                    TypeData targetType = parameter.getTypeSystemType();
                    TypeData sourceType = specializationParam.getTypeSystemType();
                    String localName = specializationParam.getLocalName();

                    if (unexpectedParameter != null && unexpectedParameter.getLocalName().equals(specializationParam.getLocalName())) {
                        localName = "ex.getResult()";
                        sourceType = getModel().getNode().getTypeSystem().getGenericTypeData();
                    }

                    CodeTree value = CodeTreeBuilder.singleString(localName);

                    if (sourceType.needsCastTo(targetType)) {
                        value = createCallTypeSystemMethod(builder, getModel().getNode(), TypeSystemCodeGenerator.asTypeMethodName(targetType), value);
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

    private CodeTree createShortCircuitTree(CodeTreeBuilder parent, CodeTree body, SpecializationData specialization, Parameter parameter, Parameter exceptionParam) {
        NodeExecutionData execution = parameter.getSpecification().getExecution();
        if (execution == null || !execution.isShortCircuit()) {
            return body;
        }

        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        Parameter shortCircuitParam = specialization.getPreviousParam(parameter);
        builder.tree(createShortCircuitValue(builder, specialization, execution, shortCircuitParam, exceptionParam));
        builder.declaration(parameter.getType(), valueName(parameter), CodeTreeBuilder.createBuilder().defaultValue(parameter.getType()));
        builder.startIf().string(shortCircuitParam.getLocalName()).end();
        builder.startBlock();

        if (containsNewLine(body)) {
            builder.tree(body);
        } else {
            builder.statement(body);
        }
        builder.end();

        return builder.getRoot();
    }

    private static CodeTree createShortCircuitValue(CodeTreeBuilder parent, SpecializationData specialization, NodeExecutionData execution, Parameter shortCircuitParam, Parameter exceptionParam) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        int shortCircuitIndex = 0;
        for (NodeExecutionData otherExectuion : specialization.getNode().getChildExecutions()) {
            if (otherExectuion.isShortCircuit()) {
                if (otherExectuion == execution) {
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

    protected CodeTree createCallRewriteMonomorphic(CodeTreeBuilder parent, boolean hasUnexpected, TypeData returnType, SpecializationData current, Parameter exceptionParam, String reason) {
        NodeData node = current.getNode();
        SpecializationData generic = node.getGenericSpecialization();
        CodeTreeBuilder specializeCall = new CodeTreeBuilder(parent);
        specializeCall.startCall(REWRITE);
        addInternalValueParameterNames(specializeCall, generic, node.getGenericSpecialization(), exceptionParam != null ? exceptionParam.getLocalName() : null, true, false, null);
        specializeCall.doubleQuote(reason);
        specializeCall.end().end();

        CodeTreeBuilder builder = new CodeTreeBuilder(parent);

        builder.startReturn();
        builder.tree(createExpectExecutableType(node, generic.getReturnType().getTypeSystemType(), hasUnexpected, returnType, specializeCall.getRoot()));
        builder.end();

        return builder.getRoot();
    }

    static String valueNameEvaluated(Parameter targetParameter) {
        return valueName(targetParameter) + "Evaluated";
    }

    static String implicitTypeName(Parameter param) {
        return param.getLocalName() + "ImplicitType";
    }

    static String polymorphicTypeName(NodeExecutionData param) {
        return param.getName() + "PolymorphicType";
    }

    static String valueName(Parameter param) {
        return param.getLocalName();
    }

    private static CodeTree createAccessChild(NodeExecutionData targetExecution, String thisReference) {
        String reference = thisReference;
        if (reference == null) {
            reference = "this";
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        Element accessElement = targetExecution.getChild().getAccessElement();
        if (accessElement == null || accessElement.getKind() == ElementKind.METHOD) {
            builder.string(reference).string(".").string(targetExecution.getChild().getName());
        } else if (accessElement.getKind() == ElementKind.FIELD) {
            builder.string(reference).string(".").string(accessElement.getSimpleName().toString());
        } else {
            throw new AssertionError();
        }
        if (targetExecution.isIndexed()) {
            builder.string("[" + targetExecution.getIndex() + "]");
        }
        return builder.getRoot();
    }

    private static String castValueName(Parameter parameter) {
        return valueName(parameter) + "Cast";
    }

    static void addInternalValueParameterNames(CodeTreeBuilder builder, TemplateMethod source, TemplateMethod specialization, String unexpectedValueName, boolean forceFrame, boolean disableFrame,
                    Map<String, String> customNames) {
        if (forceFrame && !disableFrame && specialization.getSpecification().findParameterSpec("frame") != null) {
            builder.string("frameValue");
        }
        for (Parameter parameter : specialization.getParameters()) {
            ParameterSpec spec = parameter.getSpecification();
            if ((disableFrame || forceFrame) && spec.getName().equals("frame")) {
                continue;
            }

            if (parameter.getSpecification().isLocal()) {
                continue;
            }

            Parameter sourceParameter = source.findParameter(parameter.getLocalName());

            if (customNames != null && customNames.containsKey(parameter.getLocalName())) {
                builder.string(customNames.get(parameter.getLocalName()));
            } else if (unexpectedValueName != null && parameter.getLocalName().equals(unexpectedValueName)) {
                builder.cast(parameter.getType(), CodeTreeBuilder.singleString("ex.getResult()"));
            } else if (sourceParameter != null) {
                builder.string(valueName(sourceParameter, parameter));
            } else {
                builder.string(valueName(parameter));
            }
        }
    }

    private static String valueName(Parameter sourceParameter, Parameter targetParameter) {
        if (!sourceParameter.getSpecification().isSignature()) {
            return valueName(targetParameter);
        } else if (sourceParameter.getTypeSystemType() != null && targetParameter.getTypeSystemType() != null) {
            if (sourceParameter.getTypeSystemType().needsCastTo(targetParameter.getTypeSystemType())) {
                return castValueName(targetParameter);
            }
        }
        return valueName(targetParameter);
    }

    static CodeTree createTemplateMethodCall(CodeTreeBuilder parent, CodeTree target, TemplateMethod sourceMethod, TemplateMethod targetMethod, String unexpectedValueName,
                    String... customSignatureValueNames) {
        CodeTreeBuilder builder = parent.create();

        boolean castedValues = sourceMethod != targetMethod;

        builder.startGroup();
        ExecutableElement method = targetMethod.getMethod();
        if (method == null) {
            throw new UnsupportedOperationException();
        }
        TypeElement targetClass = ElementUtils.findNearestEnclosingType(method.getEnclosingElement());
        NodeData node = (NodeData) targetMethod.getTemplate();

        if (target == null) {
            boolean accessible = targetMethod.canBeAccessedByInstanceOf(node.getNodeType());
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
                    Parameter firstParameter = null;
                    for (Parameter searchParameter : targetMethod.getParameters()) {
                        if (searchParameter.getSpecification().isSignature()) {
                            firstParameter = searchParameter;
                            break;
                        }
                    }
                    if (firstParameter == null) {
                        throw new AssertionError();
                    }

                    Parameter sourceParameter = sourceMethod.findParameter(firstParameter.getLocalName());

                    if (castedValues && sourceParameter != null) {
                        builder.string(valueName(sourceParameter, firstParameter));
                    } else {
                        builder.string(valueName(firstParameter));
                    }
                }
            }
            builder.string(".");
        } else {
            builder.tree(target);
        }
        builder.startCall(method.getSimpleName().toString());

        int signatureIndex = 0;

        for (Parameter targetParameter : targetMethod.getParameters()) {
            Parameter valueParameter = null;
            if (sourceMethod != null) {
                valueParameter = sourceMethod.findParameter(targetParameter.getLocalName());
            }
            if (valueParameter == null) {
                valueParameter = targetParameter;
            }
            TypeMirror targetType = targetParameter.getType();
            TypeMirror valueType = null;
            if (valueParameter != null) {
                valueType = valueParameter.getType();
            }

            if (signatureIndex < customSignatureValueNames.length && targetParameter.getSpecification().isSignature()) {
                builder.string(customSignatureValueNames[signatureIndex]);
                signatureIndex++;
            } else if (targetParameter.getSpecification().isLocal()) {
                builder.startGroup();
                if (builder.findMethod().getModifiers().contains(Modifier.STATIC)) {
                    builder.string(THIS_NODE_LOCAL_VAR_NAME).string(".");
                } else {
                    builder.string("this.");
                }
                builder.string(targetParameter.getSpecification().getName());
                builder.end();
            } else if (unexpectedValueName != null && targetParameter.getLocalName().equals(unexpectedValueName)) {
                builder.cast(targetParameter.getType(), CodeTreeBuilder.singleString("ex.getResult()"));
            } else if (!ElementUtils.needsCastTo(valueType, targetType)) {
                builder.startGroup();
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
        String nodeid = resolveNodeId(node);
        String name = ElementUtils.firstLetterUpperCase(nodeid);
        name += "BaseNode";
        return name;
    }

    static CodeTree createCallTypeSystemMethod(CodeTreeBuilder parent, NodeData node, String methodName, CodeTree... args) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        startCallTypeSystemMethod(builder, node.getTypeSystem(), methodName);
        for (CodeTree arg : args) {
            builder.tree(arg);
        }
        builder.end().end();
        return builder.getRoot();
    }

    private static void startCallTypeSystemMethod(CodeTreeBuilder body, TypeSystemData typeSystem, String methodName) {
        GeneratedTypeMirror typeMirror = new GeneratedTypeMirror(ElementUtils.getPackageName(typeSystem.getTemplateType()), TypeSystemCodeGenerator.typeName(typeSystem));
        body.startGroup();
        body.staticReference(typeMirror, TypeSystemCodeGenerator.singletonName(typeSystem));
        body.string(".").startCall(methodName);
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

    void emitEncounteredSynthetic(CodeTreeBuilder builder, NodeData model, TemplateMethod current) {
        CodeTreeBuilder nodes = builder.create();
        CodeTreeBuilder arguments = builder.create();
        nodes.startCommaGroup();
        arguments.startCommaGroup();
        boolean empty = true;
        for (Parameter parameter : current.getParameters()) {
            NodeExecutionData executionData = parameter.getSpecification().getExecution();
            if (executionData != null) {
                if (executionData.isShortCircuit()) {
                    nodes.nullLiteral();
                    arguments.string(valueName(parameter.getPreviousParameter()));
                }
                nodes.tree(createAccessChild(executionData, "rootNode"));
                arguments.string(valueName(parameter));
                empty = false;
            }
        }
        nodes.end();
        arguments.end();
        builder.startStatement().startStaticCall(context.getTruffleTypes().getCompilerDirectives(), "transferToInterpreter").end().end();

        builder.declaration(baseClassName(model), "rootNode", builder.create().startStaticCall(context.getTruffleTypes().getDslShare(), DSLSHARE_FIND_ROOT).string("this").end());
        builder.startThrow().startNew(context.getType(UnsupportedSpecializationException.class));
        builder.string("rootNode");
        builder.startNewArray(context.getTruffleTypes().getNodeArray(), null);
        builder.tree(nodes.getRoot());
        builder.end();
        if (!empty) {
            builder.tree(arguments.getRoot());
        }
        builder.end().end();
    }

    private static ExecutableElement findCopyConstructor(TypeMirror type) {
        for (ExecutableElement constructor : ElementFilter.constructorsIn(ElementUtils.fromTypeMirror(type).getEnclosedElements())) {
            if (constructor.getModifiers().contains(PRIVATE)) {
                continue;
            }
            if (isCopyConstructor(constructor)) {
                return constructor;
            }
        }

        return null;
    }

    static String nodeSpecializationClassName(SpecializationData specialization) {
        String nodeid = resolveNodeId(specialization.getNode());
        String name = ElementUtils.firstLetterUpperCase(nodeid);
        name += ElementUtils.firstLetterUpperCase(specialization.getId());
        name += "Node";
        return name;
    }

    static String nodePolymorphicClassName(NodeData node) {
        return ElementUtils.firstLetterUpperCase(resolveNodeId(node)) + "PolymorphicNode";
    }

    private static String resolveNodeId(NodeData node) {
        String nodeid = node.getNodeId();
        if (nodeid.endsWith("Node") && !nodeid.equals("Node")) {
            nodeid = nodeid.substring(0, nodeid.length() - 4);
        }
        return nodeid;
    }

    private static CodeTree createCastType(TypeSystemData typeSystem, TypeData sourceType, TypeData targetType, boolean expect, CodeTree value) {
        if (targetType == null) {
            return value;
        } else if (sourceType != null && !sourceType.needsCastTo(targetType)) {
            return value;
        }

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String targetMethodName;
        if (expect) {
            targetMethodName = TypeSystemCodeGenerator.expectTypeMethodName(targetType);
        } else {
            targetMethodName = TypeSystemCodeGenerator.asTypeMethodName(targetType);
        }
        startCallTypeSystemMethod(builder, typeSystem, targetMethodName);
        builder.tree(value);
        builder.end().end();
        return builder.getRoot();
    }

    private static CodeTree createExpectType(TypeSystemData typeSystem, TypeData sourceType, TypeData targetType, CodeTree expression) {
        return createCastType(typeSystem, sourceType, targetType, true, expression);
    }

    static CodeTree createDeoptimize(CodeTreeBuilder parent) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);
        builder.startStatement();
        builder.startStaticCall(ProcessorContext.getInstance().getTruffleTypes().getCompilerDirectives(), "transferToInterpreterAndInvalidate").end();
        builder.end();
        return builder.getRoot();
    }

    private TypeMirror getUnexpectedValueException() {
        return getContext().getTruffleTypes().getUnexpectedValueException();
    }

    interface CodeBlock<T> {

        CodeTree create(CodeTreeBuilder parent, T value);

    }

}