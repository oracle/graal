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

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.model.*;
import com.oracle.truffle.dsl.processor.parser.*;

class SpecializedNodeFactory extends NodeBaseFactory {

    protected final CodeTypeElement nodeGen;

    public SpecializedNodeFactory(ProcessorContext context, NodeData node, SpecializationData specialization, CodeTypeElement nodeGen) {
        super(context, node, specialization);
        this.nodeGen = nodeGen;
    }

    @Override
    public CodeTypeElement create() {
        TypeMirror baseType = node.getNodeType();
        if (nodeGen != null) {
            baseType = nodeGen.asType();
        }
        CodeTypeElement clazz = GeneratorUtils.createClass(node, modifiers(PRIVATE, FINAL), nodeSpecializationClassName(specialization), baseType, false);

        if (specialization.isSpecialized() || specialization.isUninitialized()) {
            clazz.add(createGetMetadata0(false));
            clazz.add(createMetadataLiteral());
        }

        NodeCost cost;
        if (specialization.isGeneric()) {
            cost = NodeCost.MEGAMORPHIC;
        } else if (specialization.isUninitialized()) {
            cost = NodeCost.UNINITIALIZED;
        } else if (specialization.isPolymorphic()) {
            cost = NodeCost.POLYMORPHIC;
        } else if (specialization.isSpecialized()) {
            cost = NodeCost.MONOMORPHIC;
        } else {
            throw new AssertionError();
        }
        clazz.getAnnotationMirrors().add(createNodeInfo(cost));

        if (specialization.isUninitialized() && node.getGenericSpecialization().isReachable()) {
            clazz.add(createUninitializedGetCostOverride());
        }

        createConstructors(clazz);

        createExecuteMethods(clazz);
        createCachedExecuteMethods(clazz);

        if (specialization.isUninitialized()) {
            if (specialization.getNode().isFallbackReachable()) {
                CodeVariableElement var = new CodeVariableElement(modifiers(Modifier.PRIVATE), context.getType(boolean.class), CONTAINS_FALLBACK);
                var.addAnnotationMirror(new CodeAnnotationMirror(context.getTruffleTypes().getCompilationFinal()));
                clazz.add(var);
            }
            clazz.add(createExecuteUninitialized());
        }

        if (!specialization.isUninitialized() && specialization.getNode().needsRewrites(context)) {
            clazz.add(createCopyConstructorFactoryMethod(clazz, nodeGen.asType()));
        } else {
            for (ExecutableElement constructor : ElementFilter.constructorsIn(clazz.getEnclosedElements())) {
                if (constructor.getParameters().size() == 1 && ((CodeVariableElement) constructor.getParameters().get(0)).getType().equals(nodeGen.asType())) {
                    // skip copy constructor - not used
                    continue;
                }
                clazz.add(createConstructorFactoryMethod(clazz, constructor));
            }
        }

        return clazz;
    }

    private Element createUninitializedGetCostOverride() {
        TypeMirror returnType = context.getTruffleTypes().getNodeCost();
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getCost");
        CodeTreeBuilder builder = method.createBuilder();
        builder.startIf().string(CONTAINS_FALLBACK).end().startBlock();
        builder.startReturn().staticReference(returnType, "MONOMORPHIC").end();
        builder.end();
        builder.startReturn().string("super.getCost()").end();
        return method;
    }

    private CodeVariableElement createMetadataLiteral() {
        CodeVariableElement includes = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), context.getTruffleTypes().getDslMetadata(), METADATA_FIELD_NAME);

        CodeTreeBuilder builder = includes.createInitBuilder();

        Set<SpecializationData> contains = specialization.getContains();
        if (specialization.isUninitialized()) {
            contains = new HashSet<>();

            SpecializationData polymorphic = node.getPolymorphicSpecialization();
            if (polymorphic != null) {
                contains.addAll(polymorphic.getContains());
            }
            SpecializationData generic = node.getGenericSpecialization();
            if (generic != null) {
                contains.addAll(generic.getContains());
            }
        }

        builder.startNew(context.getTruffleTypes().getDslMetadata());
        builder.startGroup().string(nodeSpecializationClassName(getSpecialization()), ".class").end();
        builder.tree(createSpecializationListLiteral(builder, contains));
        builder.tree(createSpecializationListLiteral(builder, getSpecialization().getExcludedBy()));
        builder.tree(createSpecializationTypeLiteral(builder, SpecializationData.getSignatureTypes(getSpecialization())));
        builder.string("0").string("0");
        builder.end();
        return includes;
    }

    private CodeTree createSpecializationTypeLiteral(CodeTreeBuilder parent, List<TypeMirror> list) {
        ArrayType classArray = new ArrayCodeTypeMirror(context.getType(Class.class));
        CodeTreeBuilder builder = parent.create();

        if (list.isEmpty()) {
            builder.staticReference(context.getTruffleTypes().getDslMetadata(), EMPTY_CLASS_ARRAY);
        } else {
            builder.startNewArray(classArray, null);
            for (TypeMirror type : list) {
                builder.typeLiteral(type);
            }
            builder.end();
        }

        return builder.getRoot();
    }

    private CodeTree createSpecializationListLiteral(CodeTreeBuilder parent, Set<SpecializationData> list) {
        ArrayType classArray = new ArrayCodeTypeMirror(context.getType(Class.class));
        CodeTreeBuilder builder = parent.create();

        if (list.isEmpty()) {
            builder.staticReference(context.getTruffleTypes().getDslMetadata(), EMPTY_CLASS_ARRAY);
        } else {
            builder.startNewArray(classArray, null);
            for (SpecializationData current : list) {
                SpecializationData s = current;
                if (s.isGeneric() || s.isPolymorphic()) {
                    s = getSpecialization().getNode().getUninitializedSpecialization();
                }
                builder.startGroup().string(nodeSpecializationClassName(s)).string(".class").end();
            }
            builder.end();
        }

        return builder.getRoot();
    }

    protected CodeAnnotationMirror createNodeInfo(NodeCost cost) {
        String shortName = node.getShortName();
        CodeAnnotationMirror nodeInfoMirror = new CodeAnnotationMirror(context.getTruffleTypes().getNodeInfoAnnotation());
        if (shortName != null) {
            nodeInfoMirror.setElementValue(nodeInfoMirror.findExecutableElement("shortName"), new CodeAnnotationValue(shortName));
        }

        DeclaredType nodeinfoCost = context.getTruffleTypes().getNodeCost();
        VariableElement varKind = ElementUtils.findVariableElement(nodeinfoCost, cost.name());

        nodeInfoMirror.setElementValue(nodeInfoMirror.findExecutableElement("cost"), new CodeAnnotationValue(varKind));
        return nodeInfoMirror;
    }

    protected void createConstructors(CodeTypeElement clazz) {
        TypeElement superTypeElement = ElementUtils.fromTypeMirror(clazz.getSuperclass());
        for (ExecutableElement constructor : ElementFilter.constructorsIn(superTypeElement.getEnclosedElements())) {
            if (specialization.isUninitialized()) {
                // ignore copy constructors for uninitialized if not polymorphic
                if (isCopyConstructor(constructor) && !node.isPolymorphic(context)) {
                    continue;
                }
            } else if (node.getUninitializedSpecialization() != null) {
                // ignore others than copy constructors for specialized nodes
                if (!isCopyConstructor(constructor)) {
                    continue;
                }
            }

            CodeExecutableElement superConstructor = GeneratorUtils.createSuperConstructor(context, clazz, constructor);
            if (superConstructor == null) {
                continue;
            }
            CodeTree body = superConstructor.getBodyTree();
            CodeTreeBuilder builder = superConstructor.createBuilder();
            builder.tree(body);

            if (superConstructor != null) {
                for (Parameter param : getImplicitTypeParameters(getSpecialization())) {
                    clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), context.getType(Class.class), implicitTypeName(param)));
                    superConstructor.getParameters().add(new CodeVariableElement(context.getType(Class.class), implicitTypeName(param)));

                    builder.startStatement();
                    builder.string("this.").string(implicitTypeName(param)).string(" = ").string(implicitTypeName(param));
                    builder.end();
                }

                clazz.add(superConstructor);
            }
        }
    }

    protected void createExecuteMethods(CodeTypeElement clazz) {

        List<ExecutableTypeData> primaryExecutes = null;
        int lastEvaluatedCount = -1;

        for (ExecutableTypeData execType : node.getExecutableTypes()) {
            if (execType.isFinal()) {
                continue;
            }
            if (execType.getEvaluatedCount() != lastEvaluatedCount) {
                lastEvaluatedCount = execType.getEvaluatedCount();
                primaryExecutes = findFunctionalExecutableType(lastEvaluatedCount);
            }

            CodeExecutableElement executeMethod = createExecutableTypeOverride(execType, true);
            clazz.add(executeMethod);
            CodeTreeBuilder builder = executeMethod.getBuilder();
            CodeTree result = createExecuteBody(builder, execType, primaryExecutes);
            if (result != null) {
                builder.tree(result);
            } else {
                clazz.remove(executeMethod);
            }
        }
    }

    protected void createCachedExecuteMethods(CodeTypeElement clazz) {
        if (!node.isPolymorphic(context)) {
            return;
        }

        final SpecializationData polymorphic = node.getPolymorphicSpecialization();
        ExecutableElement executeCached = nodeGen.getMethod(EXECUTE_CHAINED);
        CodeExecutableElement executeMethod = CodeExecutableElement.clone(context.getEnvironment(), executeCached);
        executeMethod.getModifiers().remove(Modifier.ABSTRACT);
        CodeTreeBuilder builder = executeMethod.createBuilder();

        if (specialization.isPolymorphic()) {
            builder.startReturn().startCall("this.next0", EXECUTE_CHAINED);
            addInternalValueParameterNames(builder, polymorphic, polymorphic, null, true, false, null);
            builder.end().end();
        } else if (specialization.isUninitialized()) {
            builder.tree(createDeoptimizeUninitialized(node, builder));
            builder.startReturn().startCall("this", EXECUTE_UNINITIALIZED);
            addInternalValueParameterNames(builder, polymorphic, polymorphic, null, true, false, null);
            builder.end().end();
        } else {
            CodeTreeBuilder elseBuilder = new CodeTreeBuilder(builder);
            elseBuilder.startReturn().startCall("this.next0", EXECUTE_CHAINED);
            addInternalValueParameterNames(elseBuilder, polymorphic, polymorphic, null, true, false, null);
            elseBuilder.end().end();

            builder.tree(createExecuteTree(builder, polymorphic, SpecializationGroup.create(specialization), new CodeBlock<SpecializationData>() {

                public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                    return createGenericInvoke(b, polymorphic, current);
                }
            }, elseBuilder.getRoot(), false, true, true, false));
        }
        clazz.add(executeMethod);
    }

    private static CodeTree createDeoptimizeUninitialized(NodeData node, CodeTreeBuilder parent) {
        CodeTreeBuilder builder = parent.create();
        if (node.getGenericSpecialization().isReachable()) {
            builder.startIf().string("!containsFallback").end().startBlock();
            builder.tree(createDeoptimize(builder));
            builder.end();
        } else {
            builder.tree(createDeoptimize(builder));
        }
        return builder.getRoot();
    }

    private CodeTree createExecuteBody(CodeTreeBuilder parent, ExecutableTypeData execType, List<ExecutableTypeData> primaryExecutes) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);

        if (primaryExecutes.contains(execType) || primaryExecutes.isEmpty()) {
            builder.tree(createFunctionalExecute(builder, execType));
        } else if (needsCastingExecuteMethod(execType)) {
            assert !primaryExecutes.isEmpty();
            builder.tree(createCastingExecute(builder, execType, primaryExecutes.get(0)));
        } else {
            return null;
        }

        return builder.getRoot();
    }

    private CodeExecutableElement createExecutableTypeOverride(ExecutableTypeData execType, boolean evaluated) {
        CodeExecutableElement method = CodeExecutableElement.clone(context.getEnvironment(), execType.getMethod());

        method.getAnnotationMirrors().clear();
        for (VariableElement variable : method.getParameters()) {
            variable.getAnnotationMirrors().clear();
        }

        CodeTreeBuilder builder = method.createBuilder();
        int i = 0;
        int signatureIndex = -1;
        for (VariableElement param : method.getParameters()) {
            CodeVariableElement var = CodeVariableElement.clone(param);
            Parameter actualParameter = i < execType.getParameters().size() ? execType.getParameters().get(i) : null;
            String name;
            if (actualParameter != null) {
                if (actualParameter.getSpecification().isSignature()) {
                    signatureIndex++;
                }

                if (evaluated && actualParameter.getSpecification().isSignature()) {
                    name = valueNameEvaluated(actualParameter);
                } else {
                    name = valueName(actualParameter);
                }

                int varArgCount = getSpecialization().getSignatureSize() - signatureIndex;
                if (evaluated && actualParameter.isTypeVarArgs()) {
                    Parameter baseVarArgs = actualParameter;
                    name = valueName(baseVarArgs) + "Args";

                    builder.startAssert().string(name).string(" != null").end();
                    builder.startAssert().string(name).string(".length == ").string(String.valueOf(varArgCount)).end();
                    if (varArgCount > 0) {
                        List<Parameter> varArgsParameter = execType.getParameters().subList(i, execType.getParameters().size());
                        for (Parameter varArg : varArgsParameter) {
                            if (varArgCount <= 0) {
                                break;
                            }
                            TypeMirror type = baseVarArgs.getType();
                            if (type.getKind() == TypeKind.ARRAY) {
                                type = ((ArrayType) type).getComponentType();
                            }
                            builder.declaration(type, valueNameEvaluated(varArg), name + "[" + varArg.getTypeVarArgsIndex() + "]");
                            varArgCount--;
                        }
                    }
                }
            } else {
                name = "arg" + i;
            }
            var.setName(name);
            method.getParameters().set(i, var);
            i++;
        }

        method.getAnnotationMirrors().clear();
        method.getModifiers().remove(Modifier.ABSTRACT);
        return method;
    }

    private static boolean needsCastingExecuteMethod(ExecutableTypeData execType) {
        if (execType.isAbstract()) {
            return true;
        }
        if (execType.getType().isGeneric()) {
            return true;
        }
        return false;
    }

    private List<ExecutableTypeData> findFunctionalExecutableType(int evaluatedCount) {
        TypeData primaryType = specialization.getReturnType().getTypeSystemType();
        List<ExecutableTypeData> otherTypes = specialization.getNode().getExecutableTypes(evaluatedCount);

        List<ExecutableTypeData> filteredTypes = new ArrayList<>();
        for (ExecutableTypeData compareType : otherTypes) {
            if (ElementUtils.typeEquals(compareType.getType().getPrimitiveType(), primaryType.getPrimitiveType())) {
                filteredTypes.add(compareType);
            }
        }

        // no direct matches found use generic where the type is Object
        if (filteredTypes.isEmpty()) {
            for (ExecutableTypeData compareType : otherTypes) {
                if (compareType.getType().isGeneric() && !compareType.hasUnexpectedValue(context)) {
                    filteredTypes.add(compareType);
                }
            }
        }

        if (filteredTypes.isEmpty()) {
            for (ExecutableTypeData compareType : otherTypes) {
                if (compareType.getType().isGeneric()) {
                    filteredTypes.add(compareType);
                }
            }
        }

        return filteredTypes;
    }

    private CodeTree createFunctionalExecute(CodeTreeBuilder parent, final ExecutableTypeData executable) {
        CodeTreeBuilder builder = new CodeTreeBuilder(parent);

        if (specialization.isUninitialized()) {
            builder.tree(createDeoptimizeUninitialized(specialization.getNode(), builder));
        }

        builder.tree(createExecuteChildren(builder, executable, specialization, specialization.getParameters(), null));

        CodeTree returnSpecialized = null;

        if (specialization.findNextSpecialization() != null) {
            CodeTreeBuilder returnBuilder = new CodeTreeBuilder(builder);
            returnBuilder.tree(createDeoptimize(builder));
            returnBuilder.tree(createCallRewriteMonomorphic(builder, executable.hasUnexpectedValue(context), executable.getType(), null, "One of guards " + specialization.getGuards() + " failed"));
            returnSpecialized = returnBuilder.getRoot();
        }

        builder.tree(createExecuteTree(builder, specialization, SpecializationGroup.create(specialization), new CodeBlock<SpecializationData>() {

            public CodeTree create(CodeTreeBuilder b, SpecializationData current) {
                return createExecute(b, executable);
            }
        }, returnSpecialized, false, false, false, false));

        return builder.getRoot();
    }

    private CodeTree createExecute(CodeTreeBuilder parent, ExecutableTypeData executable) {
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
        if (specialization.isPolymorphic()) {
            returnBuilder.startCall("next0", EXECUTE_CHAINED);
            addInternalValueParameterNames(returnBuilder, specialization, specialization, null, true, false, null);
            returnBuilder.end();
        } else if (specialization.isUninitialized()) {
            returnBuilder.startCall(EXECUTE_UNINITIALIZED);
            addInternalValueParameterNames(returnBuilder, specialization, specialization, null, true, false, null);
            returnBuilder.end();
        } else if (specialization.getMethod() == null && !node.needsRewrites(context)) {
            emitEncounteredSynthetic(builder, getSpecialization().getNode(), specialization);
        } else {
            returnBuilder.tree(createTemplateMethodCall(returnBuilder, null, specialization, specialization, null));
        }

        if (!returnBuilder.isEmpty()) {
            TypeData targetType = node.getTypeSystem().findTypeData(builder.findMethod().getReturnType());
            TypeData sourceType = specialization.getReturnType().getTypeSystemType();

            builder.startReturn();
            if (targetType == null || sourceType == null) {
                builder.tree(returnBuilder.getRoot());
            } else if (sourceType.needsCastTo(targetType)) {
                CodeTree cast;
                if (executable.hasUnexpectedValue(context)) {
                    cast = TypeSystemCodeGenerator.expect(targetType, returnBuilder.getRoot());
                } else {
                    cast = TypeSystemCodeGenerator.cast(targetType, returnBuilder.getRoot());
                }
                builder.tree(cast);
            } else {
                builder.tree(returnBuilder.getRoot());
            }
            builder.end();
        }

        if (!specialization.getExceptions().isEmpty()) {
            for (SpecializationThrowsData exception : specialization.getExceptions()) {
                builder.end().startCatchBlock(exception.getJavaClass(), "ex");
                builder.tree(createDeoptimize(builder));
                builder.tree(createCallRewriteMonomorphic(parent, executable.hasUnexpectedValue(context), executable.getType(), null, "Thrown " + ElementUtils.getSimpleName(exception.getJavaClass())));
            }
            builder.end();
        }
        if (!specialization.getAssumptions().isEmpty()) {
            builder.end().startCatchBlock(context.getTruffleTypes().getInvalidAssumption(), "ex");
            builder.tree(createCallRewriteMonomorphic(parent, executable.hasUnexpectedValue(context), executable.getType(), null, "Assumption failed"));
            builder.end();
        }

        return builder.getRoot();
    }

    private CodeExecutableElement createCopyConstructorFactoryMethod(CodeTypeElement clazz, TypeMirror baseType) {
        List<Parameter> implicitTypeParams = getImplicitTypeParameters(specialization);
        String baseName = "current";
        CodeExecutableElement method = new CodeExecutableElement(modifiers(STATIC), specialization.getNode().getNodeType(), NodeFactoryFactory.FACTORY_METHOD_NAME);
        method.addParameter(new CodeVariableElement(specialization.getNode().getNodeType(), baseName));
        for (Parameter implicitTypeParam : implicitTypeParams) {
            method.addParameter(new CodeVariableElement(context.getType(Class.class), implicitTypeName(implicitTypeParam)));
        }
        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();
        builder.startNew(clazz.asType());
        builder.startGroup().cast(baseType, CodeTreeBuilder.singleString(baseName)).end();
        for (Parameter param : implicitTypeParams) {
            builder.string(implicitTypeName(param));
        }
        builder.end().end();
        return method;
    }

    private CodeExecutableElement createConstructorFactoryMethod(CodeTypeElement clazz, ExecutableElement constructor) {
        List<? extends VariableElement> parameters = constructor.getParameters();
        CodeExecutableElement method = new CodeExecutableElement(modifiers(STATIC), specialization.getNode().getNodeType(), NodeFactoryFactory.FACTORY_METHOD_NAME,
                        parameters.toArray(new CodeVariableElement[parameters.size()]));
        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();
        builder.startNew(clazz.asType());
        for (VariableElement param : parameters) {
            builder.string(((CodeVariableElement) param).getName());
        }
        builder.end().end();
        return method;
    }
}