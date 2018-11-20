/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.library;

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createClass;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.ResolvedExports;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.BooleanLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.ClassLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionVisitor;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.IntLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.Parameter;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

public class ExportsGenerator extends CodeTypeElementFactory<ExportsData> {

    private static final String ACCEPTS = "accepts";
    private static final String ACCEPTS_METHOD_NAME = ACCEPTS + "_";

    private ProcessorContext context;
    private ExportsData model;

    @Override
    public List<CodeTypeElement> create(ProcessorContext context1, ExportsData model1) {
        this.context = context1;
        this.model = model1;

        CodeTreeBuilder builder;
        TypeMirror resolvedExports = context.getType(ResolvedExports.class);
        // all libraries must have the same receiver
        // TODO verify that.
        TypeMirror exportReceiverType;
        if (model.getExportedLibraries().isEmpty()) {
            exportReceiverType = context.getType(Object.class);
        } else {
            exportReceiverType = model.getExportedLibraries().values().iterator().next().getReceiverClass();
        }

        String className = model.getTemplateType().getSimpleName().toString() + "Gen";
        CodeTypeElement genClass = createClass(model, null, modifiers(Modifier.FINAL), className, null);

        CodeTreeBuilder statics = genClass.add(new CodeExecutableElement(modifiers(STATIC), null, "<cinit>")).createBuilder();
        statics.startStatement();
        statics.startStaticCall(resolvedExports, "register");
        statics.typeLiteral(model1.getTemplateType().asType());

        genClass.add(GeneratorUtils.createConstructorUsingFields(modifiers(PRIVATE), genClass));
        Map<String, CodeVariableElement> libraryConstants = new LinkedHashMap<>();

        for (ExportsLibrary library : model.getExportedLibraries().values()) {
            if (library.hasErrors()) {
                continue;
            }
            TypeElement libraryBaseTypeElement = library.getLibrary().getTemplateType();
            DeclaredType libraryBaseType = (DeclaredType) libraryBaseTypeElement.asType();

            TypeMirror baseType = new CodeTypeMirror.DeclaredCodeTypeMirror(context.getTypeElement(ResolvedExports.class),
                            Arrays.asList(libraryBaseType));
            CodeTypeElement exportsClass = createClass(model, null, modifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), ElementUtils.getSimpleName(libraryBaseType) + "Exports", baseType);
            statics.startNew(exportsClass.asType()).end();

            final ExportMessageData accepts = library.getExportedMesssages().get(ACCEPTS);
            final boolean needsRewrites = library.needsRewrites();

            CodeExecutableElement constructor = new CodeExecutableElement(modifiers(PRIVATE), null, exportsClass.getSimpleName().toString());
            builder = constructor.createBuilder();
            builder.startStatement().startSuperCall().typeLiteral(libraryBaseType).end().end();
            exportsClass.add(constructor);

            builder = CodeTreeBuilder.createBuilder();
            builder.string("receiver instanceof ").type(exportReceiverType);
            if (library.isDispatched()) {
                builder.string(" && (").cast(exportReceiverType).string("receiver).dispatch() == ").typeLiteral(library.getTemplateType().asType());
            }
            CodeTree defaultAccepts = builder.build();

            CodeTypeElement uncachedClass = exportsClass.add(createClass(model, null, modifiers(PRIVATE, STATIC, FINAL), "Uncached", libraryBaseType));
            CodeExecutableElement acceptUncached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Library.class), ACCEPTS));
            acceptUncached.getModifiers().remove(Modifier.ABSTRACT);
            acceptUncached.renameArguments("receiver");

            builder = acceptUncached.createBuilder();
            if (accepts == null) {
                builder.startReturn().tree(defaultAccepts).end();
            } else {
                builder.startStatement().string("boolean accepts = ");
                builder.string("receiver instanceof ").type(exportReceiverType).string(" && ");
                builder.string(" accepts_(receiver)");
                builder.end();
                builder.startAssert().string("!accepts || ").tree(defaultAccepts).end();
                builder.startReturn().string("accepts").end();
            }
            builder.end();

            uncachedClass.add(acceptUncached);

            CodeVariableElement uncachedSingleton = exportsClass.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), uncachedClass.asType(), "UNCACHED"));
            builder = uncachedSingleton.createInitBuilder();
            builder.startNew(uncachedClass.asType()).end();

            CodeExecutableElement isAdoptable = uncachedClass.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Node.class), "isAdoptable")));
            isAdoptable.createBuilder().returnFalse();

            CodeExecutableElement getCost = uncachedClass.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Node.class), "getCost")));
            getCost.createBuilder().startReturn().staticReference(ElementUtils.findVariableElement(context.getDeclaredType(NodeCost.class), "MEGAMORPHIC")).end();

            CodeExecutableElement createUncached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(ResolvedExports.class), "createUncached"));
            createUncached.setReturnType(libraryBaseType);
            createUncached.getModifiers().remove(Modifier.ABSTRACT);
            createUncached.renameArguments("receiver");
            builder = createUncached.createBuilder();
            builder.startAssert().string("receiver instanceof ").type(exportReceiverType).end();
            builder.startReturn().staticReference(uncachedSingleton).end();
            exportsClass.add(createUncached);

            CodeTypeElement cacheClass = exportsClass.add(createClass(model, null, modifiers(PRIVATE, STATIC, FINAL), "Cached", libraryBaseType));
            CodeExecutableElement acceptCached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Library.class), ACCEPTS));
            acceptCached.getModifiers().remove(Modifier.ABSTRACT);
            acceptCached.renameArguments("receiver");
            builder = acceptCached.createBuilder();
            if (accepts == null) {
                builder.startReturn().tree(defaultAccepts).end();
            } else {
                builder.startStatement().string("boolean accepts = ");
                builder.string("receiver instanceof ").type(exportReceiverType).string(" && ");
                builder.string(" accepts_(receiver)");
                builder.end();
                builder.startAssert().string("!accepts || ").tree(defaultAccepts).end();
                builder.startReturn().string("accepts").end();
            }
            builder.end();
            cacheClass.add(acceptCached);

            CodeVariableElement cacheSingleton = null;
            if (!needsRewrites) {
                isAdoptable = cacheClass.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Node.class), "isAdoptable")));
                isAdoptable.createBuilder().returnFalse();

                cacheSingleton = exportsClass.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), cacheClass.asType(), "CACHE"));
                builder = cacheSingleton.createInitBuilder();
                builder.startNew(cacheClass.asType()).end();
            }

            CodeExecutableElement createCached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(ResolvedExports.class), "createCached"));
            createCached.setReturnType(libraryBaseType);
            createCached.getModifiers().remove(Modifier.ABSTRACT);
            createCached.renameArguments("receiver");
            builder = createCached.createBuilder();
            builder.startAssert().string("receiver instanceof ").type(exportReceiverType).end();
            builder.startReturn();
            if (cacheSingleton != null) {
                builder.staticReference(cacheSingleton);
            } else {
                builder.startNew(cacheClass.getSimpleName().toString()).end();
            }
            builder.end();
            exportsClass.add(createCached);

            genClass.add(exportsClass);

            Set<NodeData> cachedSharedNodes = new LinkedHashSet<>();
            for (ExportMessageData export : library.getExportedMesssages().values()) {
                ExportMessageElement nodeExport = export.getExportedClass();
                ExportMessageElement methodExport = export.getExportedMethod();
                if (nodeExport != null && nodeExport.getSpecializedNode() != null) {
                    cachedSharedNodes.add(nodeExport.getSpecializedNode());
                } else if (methodExport != null && methodExport.getSpecializedNode() != null) {
                    cachedSharedNodes.add(methodExport.getSpecializedNode());
                }
            }
            Map<NodeData, CodeTypeElement> sharedNodes = new HashMap<>();
            Map<CacheExpression, String> sharedCaches = computeSharedCaches(cachedSharedNodes);
            for (ExportMessageData export : library.getExportedMesssages().values()) {
                LibraryMessage message = export.getResolvedMessage();
                ExportMessageElement methodExport = export.getExportedMethod();
                ExportMessageElement nodeExport = export.getExportedClass();

                TypeMirror libraryReceiverType = export.getResolvedMessage().getLibrary().getExportsReceiverType();
                TypeMirror cachedExportReceiverType = nodeExport != null ? nodeExport.getReceiverType() : methodExport.getReceiverType();
                TypeMirror uncachedReceiverType = methodExport != null ? methodExport.getReceiverType() : nodeExport.getReceiverType();
                CodeTree cachedReceiverAccess = CodeTreeBuilder.createBuilder().maybeCast(libraryReceiverType, cachedExportReceiverType, "receiver").build();
                CodeTree uncachedReceiverExport = CodeTreeBuilder.createBuilder().maybeCast(libraryReceiverType, uncachedReceiverType, "receiver").build();

                // cached execute
                NodeData cachedSpecializedNode;
                if (nodeExport != null && nodeExport.getSpecializedNode() != null) {
                    cachedSpecializedNode = nodeExport.getSpecializedNode();
                } else if (methodExport != null && methodExport.getSpecializedNode() != null) {
                    cachedSpecializedNode = methodExport.getSpecializedNode();
                } else {
                    cachedSpecializedNode = null;
                }

                CodeExecutableElement cachedExecute = null;
                if (cachedSpecializedNode == null) {
                    if (methodExport == null) {
                        throw new AssertionError("Missing method export. Missed validation for " + export.getResolvedMessage().getSimpleName());
                    }
                    ExecutableElement exportMethod = (ExecutableElement) methodExport.getMessageElement();
                    cachedExecute = cacheClass.add(createDirectCall(cachedReceiverAccess, message, exportMethod));
                } else {
                    CodeTypeElement dummyClass = sharedNodes.get(cachedSpecializedNode);
                    boolean shared = true;
                    if (dummyClass == null) {
                        FlatNodeGenFactory factory = new FlatNodeGenFactory(context, cachedSpecializedNode, cachedSharedNodes, sharedCaches, libraryConstants);
                        dummyClass = createClass(model, null, modifiers(), "Dummy", context.getType(Node.class));
                        factory.create(dummyClass);
                        sharedNodes.put(cachedSpecializedNode, dummyClass);
                        shared = false;
                    }

                    for (Element element : dummyClass.getEnclosedElements()) {
                        String simpleName = element.getSimpleName().toString();
                        if (element.getKind() == ElementKind.METHOD) {
                            if (simpleName.endsWith("AndSpecialize")) {
                                // nothing to do for specialize method
                            } else if (simpleName.startsWith("execute")) {
                                CodeExecutableElement executable = (CodeExecutableElement) element;
                                executable.setVarArgs(message.getExecutable().isVarArgs());
                                cachedExecute = CodeExecutableElement.clone(executable);
                                cachedExecute.setSimpleName(CodeNames.of(message.getName()));
                                injectReceiverType(cachedExecute, library, cachedExportReceiverType);
                                cacheClass.getEnclosedElements().add(cachedExecute);
                                continue;
                            }
                        } else if (element.getKind() == ElementKind.CONSTRUCTOR) {
                            // no constructores needed
                            continue;
                        }
                        if (!shared) {
                            // only execute method needed for shared
                            cacheClass.getEnclosedElements().add(element);
                        }
                    }
                }
                if (message.getName().equals(ACCEPTS)) {
                    if (cachedSpecializedNode == null || !cachedSpecializedNode.needsRewrites(context)) {
                        cachedExecute.getModifiers().add(Modifier.STATIC);
                    }
                    cachedExecute.setSimpleName(CodeNames.of(ACCEPTS_METHOD_NAME));
                    ElementUtils.setVisibility(cachedExecute.getModifiers(), Modifier.PRIVATE);
                }

                // uncached execute
                CodeExecutableElement uncachedExecute;
                NodeData uncachedSpecializedNode;
                if (methodExport != null && methodExport.getSpecializedNode() != null && methodExport.getSpecializedNode().isUncachable()) {
                    uncachedSpecializedNode = methodExport.getSpecializedNode();
                } else if (methodExport == null && nodeExport != null && nodeExport.getSpecializedNode() != null && nodeExport.getSpecializedNode().isUncachable()) {
                    uncachedSpecializedNode = nodeExport.getSpecializedNode();
                } else {
                    uncachedSpecializedNode = null;
                }

                if (uncachedSpecializedNode == null) {
                    if (methodExport == null) {
                        throw new AssertionError("Missing method export. Missed validation for " + export.getResolvedMessage().getSimpleName());
                    }
                    ExecutableElement exportMethod = (ExecutableElement) methodExport.getMessageElement();
                    CodeExecutableElement directCall = createDirectCall(uncachedReceiverExport, message, exportMethod);
                    uncachedExecute = uncachedClass.add(directCall);
                    if (message.getName().equals(ACCEPTS)) {
                        directCall.getModifiers().add(Modifier.STATIC);
                    }
                } else {
                    FlatNodeGenFactory factory = new FlatNodeGenFactory(context, uncachedSpecializedNode, libraryConstants);
                    CodeExecutableElement generatedUncached = factory.createUncached();
                    generatedUncached.getModifiers().remove(STATIC);
                    ElementUtils.setVisibility(generatedUncached.getModifiers(), Modifier.PUBLIC);
                    generatedUncached.setSimpleName(CodeNames.of(message.getName()));
                    generatedUncached.setVarArgs(message.getExecutable().isVarArgs());
                    injectReceiverType(generatedUncached, library, uncachedReceiverType);
                    uncachedExecute = uncachedClass.add(generatedUncached);
                }
                if (message.getName().equals(ACCEPTS)) {
                    uncachedExecute.getModifiers().add(Modifier.STATIC);
                    uncachedExecute.setSimpleName(CodeNames.of(ACCEPTS + "_"));
                    ElementUtils.setVisibility(uncachedExecute.getModifiers(), Modifier.PRIVATE);
                }
                if (ElementUtils.findAnnotationMirror(uncachedExecute, TruffleBoundary.class) == null) {
                    uncachedExecute.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(TruffleBoundary.class)));
                }
            }
        }

        statics.end();
        statics.end();

        genClass.addAll(libraryConstants.values());
        return Arrays.asList(genClass);
    }

    private static Map<CacheExpression, String> computeSharedCaches(Collection<NodeData> inlinedNodes) {
        Map<SharableCache, List<CacheExpression>> sharableCaches = new LinkedHashMap<>();
        for (NodeData node : inlinedNodes) {
            for (SpecializationData specialization : node.getSpecializations()) {
                if (specialization == null) {
                    continue;
                } else if (specialization.hasMultipleInstances()) {
                    // cannot support specializations with multiple instances
                    continue;
                }
                for (CacheExpression cache : specialization.getCaches()) {
                    Set<Variable> boundVariables = cache.getDefaultExpression().findBoundVariables();
                    boolean bindsDynamicParameter = false;
                    // resolve bindings for local context
                    outer: for (Variable variable : boundVariables) {
                        boolean first = true;
                        for (Parameter parameter : specialization.getSignatureParameters()) {
                            if (first) {
                                // skip first receiver argument for this check.
                                // the receiver can be bound for cache sharing.
                                first = false;
                                continue;
                            }
                            if (ElementUtils.variableEquals(variable.getResolvedVariable(), parameter.getVariableElement())) {
                                bindsDynamicParameter = true;
                                break outer;
                            }
                        }
                    }
                    if (!bindsDynamicParameter) {
                        SharableCache sharable = new SharableCache(specialization, cache);
                        sharableCaches.computeIfAbsent(sharable, (c) -> new ArrayList<>()).add(cache);
                    }
                }
            }
        }
        Set<String> usedNames = new HashSet<>();
        Map<CacheExpression, String> sharedCaches = new LinkedHashMap<>();
        for (Entry<SharableCache, List<CacheExpression>> entry : sharableCaches.entrySet()) {
            if (entry.getValue().size() > 1) {
                String name = null;
                for (CacheExpression cacheExpression : entry.getValue()) {
                    if (name == null) {
                        name = cacheExpression.getParameter().getLocalName() + "_";
                        String originalName = name;
                        int conflict = 0;
                        while (usedNames.contains(name)) {
                            name = originalName + conflict;
                            conflict++;
                        }
                        usedNames.add(name);
                    }
                    sharedCaches.put(cacheExpression, name);
                }
            }
        }
        return sharedCaches;
    }

    private static CodeExecutableElement createDirectCall(CodeTree receiverAccess, LibraryMessage message, ExecutableElement targetMethod) {
        CodeTreeBuilder builder;
        CodeExecutableElement cachedExecute = CodeExecutableElement.cloneNoAnnotations(message.getExecutable());
        cachedExecute.renameArguments("receiver");
        cachedExecute.getModifiers().remove(Modifier.DEFAULT);
        cachedExecute.getModifiers().remove(Modifier.ABSTRACT);
        builder = cachedExecute.createBuilder();
        if (!message.getName().equals(ACCEPTS)) {
            addAcceptsAssertion(builder);
        }
        builder.startReturn();
        if (targetMethod.getModifiers().contains(Modifier.STATIC)) {
            builder.startStaticCall(targetMethod);
            builder.tree(receiverAccess);
        } else {
            builder.startCall(receiverAccess, targetMethod.getSimpleName().toString());
        }
        List<? extends VariableElement> parameters = message.getExecutable().getParameters();
        for (VariableElement parameter : parameters.subList(1, parameters.size())) {
            builder.string(parameter.getSimpleName().toString());
        }
        builder.end();
        builder.end();
        return cachedExecute;
    }

    private void injectReceiverType(CodeExecutableElement executable, ExportsLibrary library, TypeMirror receiverType) {
        TypeMirror modelReceiverType;
        boolean isAccepts = executable.getSimpleName().toString().equals(ACCEPTS);
        if (isAccepts) {
            modelReceiverType = context.getType(Object.class);
        } else {
            modelReceiverType = library.getLibrary().getSignatureReceiverType();
        }
        if (!ElementUtils.needsCastTo(modelReceiverType, receiverType)) {
            return;
        }
        CodeVariableElement receiverParam = ((CodeVariableElement) executable.getParameters().get(0));
        receiverParam.setType(modelReceiverType);
        String originalReceiverParamName = receiverParam.getName();
        receiverParam.setName(originalReceiverParamName + "_");
        CodeTree tree = executable.getBodyTree();
        CodeTreeBuilder executeBody = executable.createBuilder();
        if (!isAccepts) {
            addAcceptsAssertion(executeBody);
        }
        executeBody.declaration(receiverType, originalReceiverParamName, executeBody.create().cast(receiverType).string(originalReceiverParamName + "_").build());
        executeBody.tree(tree);
    }

    private static void addAcceptsAssertion(CodeTreeBuilder executeBody) {
        String name = executeBody.findMethod().getParameters().get(0).getSimpleName().toString();
        executeBody.startAssert().string("this.accepts(", name, ")").string(" : ").doubleQuote("Invalid library usage. Library does not accept given receiver.").end();
    }

    private static final class SharableCache {

        private final SpecializationData specialization;
        private final CacheExpression expression;
        private int hash = 1;

        SharableCache(SpecializationData specialization, CacheExpression expression) {
            this.specialization = specialization;
            this.expression = expression;
            expression.getDefaultExpression().accept(new DSLExpressionVisitor() {
                public void visitVariable(Variable binary) {
                    hash *= 31;
                }

                public void visitNegate(Negate negate) {
                    hash *= 31;
                }

                public void visitIntLiteral(IntLiteral binary) {
                    hash *= 31 + binary.getResolvedValueInt();
                }

                public void visitClassLiteral(ClassLiteral classLiteral) {
                    hash *= 31 + Objects.hash(classLiteral.getResolvedType());
                }

                public void visitCall(Call binary) {
                    hash *= 31 + Objects.hash(binary.getName());
                }

                public void visitBooleanLiteral(BooleanLiteral binary) {
                    hash *= 31 + Objects.hash(binary.getLiteral());
                }

                public void visitBinary(Binary binary) {
                    hash *= 31 + Objects.hash(binary.getOperator());
                }
            });
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SharableCache)) {
                return false;
            }
            SharableCache other = (SharableCache) obj;

            TypeMirror thisParametertype = expression.getParameter().getType();
            TypeMirror otherParametertype = other.expression.getParameter().getType();
            if (!ElementUtils.typeEquals(thisParametertype, otherParametertype)) {
                return false;
            }
            List<DSLExpression> otherExpressions = other.expression.getDefaultExpression().flatten();
            List<DSLExpression> expressions = expression.getDefaultExpression().flatten();
            if (otherExpressions.size() != expressions.size()) {
                return false;
            }
            Iterator<DSLExpression> otherExpression = otherExpressions.iterator();
            Iterator<DSLExpression> thisExpression = expressions.iterator();
            while (otherExpression.hasNext()) {
                DSLExpression e1 = thisExpression.next();
                DSLExpression e2 = otherExpression.next();
                if (e1.getClass() != e2.getClass()) {
                    return false;
                } else if (e1 instanceof Variable) {
                    VariableElement var1 = ((Variable) e1).getResolvedVariable();
                    VariableElement var2 = ((Variable) e2).getResolvedVariable();

                    if (var1.getKind() == ElementKind.PARAMETER && var2.getKind() == ElementKind.PARAMETER) {
                        Parameter p1 = specialization.findByVariable(var1);
                        Parameter p2 = other.specialization.findByVariable(var2);
                        NodeExecutionData execution1 = p1.getSpecification().getExecution();
                        NodeExecutionData execution2 = p2.getSpecification().getExecution();
                        if (execution1 != null && execution2 != null && execution1.getIndex() == execution2.getIndex()) {
                            continue;
                        }
                    }
                    if (!ElementUtils.variableEquals(var1, var2)) {
                        return false;
                    }
                } else if (e1 instanceof Call) {
                    ExecutableElement var1 = ((Call) e1).getResolvedMethod();
                    ExecutableElement var2 = ((Call) e2).getResolvedMethod();
                    if (!ElementUtils.executableEquals(var1, var2)) {
                        return false;
                    }
                } else if (e1 instanceof Binary) {
                    String var1 = ((Binary) e1).getOperator();
                    String var2 = ((Binary) e2).getOperator();
                    if (!Objects.equals(var1, var2)) {
                        return false;
                    }
                } else if (e1 instanceof Negate) {
                    assert e2 instanceof Negate;
                    // nothing to do
                } else if (!e1.equals(e2)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(expression.getParameter().getType(), hash);
        }

    }

}
