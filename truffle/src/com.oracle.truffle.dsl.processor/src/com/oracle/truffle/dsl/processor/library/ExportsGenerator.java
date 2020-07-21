/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.AnnotationProcessor;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionReducer;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.DSLExpressionGenerator;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.GeneratorMode;
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
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

public class ExportsGenerator extends CodeTypeElementFactory<ExportsData> {

    private static final String ACCEPTS = "accepts";
    private static final String ACCEPTS_METHOD_NAME = ACCEPTS + "_";
    private static final String ENABLED_MESSAGES_NAME = "ENABLED_MESSAGES";

    private final ProcessorContext context = ProcessorContext.getInstance();
    private final Map<String, CodeVariableElement> libraryConstants;

    public ExportsGenerator(Map<String, CodeVariableElement> libraryConstants) {
        this.libraryConstants = libraryConstants;
    }

    static class CacheKey {

        private final TypeMirror libraryType;
        private final DSLExpression expressionKey;
        private final CacheExpression cache;

        CacheKey(CacheExpression cache) {
            this.libraryType = cache.getParameter().getType();
            this.expressionKey = cache.getDefaultExpression().reduce(new DSLExpressionReducer() {
                public DSLExpression visitVariable(Variable binary) {
                    if (binary.getReceiver() == null) {
                        Variable newVar = new Variable(null, "receiver");
                        // we don't use the binary.getResolvedType() receiver type in order to group
                        // also between base and subclasses.
                        TypeMirror newReceiverType = ProcessorContext.getInstance().getType(Object.class);
                        newVar.setResolvedTargetType(newReceiverType);
                        newVar.setResolvedVariable(new CodeVariableElement(newReceiverType, "receiver"));
                        return newVar;
                    }
                    return binary;
                }

                public DSLExpression visitNegate(Negate negate) {
                    return negate;
                }

                public DSLExpression visitCall(Call binary) {
                    return binary;
                }

                public DSLExpression visitBinary(Binary binary) {
                    return binary;
                }
            });
            this.cache = cache;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ElementUtils.getTypeId(libraryType), expressionKey);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof CacheKey) {
                CacheKey other = (CacheKey) obj;
                return Objects.equals(libraryType, other.libraryType) && Objects.equals(expressionKey, other.expressionKey);
            }
            return false;
        }

        public CacheExpression getCache() {
            return cache;
        }

    }

    @Override
    public List<CodeTypeElement> create(ProcessorContext context1, AnnotationProcessor<?> processor, ExportsData exports) {
        libraryConstants.clear();

        String className = exports.getTemplateType().getSimpleName().toString() + "Gen";
        CodeTypeElement genClass = createClass(exports, null, modifiers(Modifier.FINAL), className, null);

        CodeTreeBuilder statics = genClass.add(new CodeExecutableElement(modifiers(STATIC), null, "<cinit>")).createBuilder();
        statics.startStatement();
        statics.startStaticCall(types.LibraryExport, "register");
        statics.typeLiteral(exports.getTemplateType().asType());

        genClass.add(GeneratorUtils.createConstructorUsingFields(modifiers(PRIVATE), genClass));

        for (ExportsLibrary libraryExports : exports.getExportedLibraries().values()) {
            if (libraryExports.hasErrors()) {
                continue;
            }

            if (libraryExports.needsDefaultExportProvider()) {
                // we need to make some classes publicly accessible so we need to make the outer
                // class public too.
                ElementUtils.setVisibility(genClass.getModifiers(), Modifier.PUBLIC);
                TypeElement provider = createDefaultExportProvider(libraryExports);
                genClass.add(provider);
                String serviceBinaryName = context.getEnvironment().getElementUtils().getBinaryName(ElementUtils.castTypeElement(context.getTypes().DefaultExportProvider)).toString();
                String serviceImplName = ElementUtils.getBinaryName(provider);
                processor.registerService(serviceBinaryName, serviceImplName, libraryExports.getTemplateType());
            }

            final TypeElement libraryBaseTypeElement = libraryExports.getLibrary().getTemplateType();
            final DeclaredType libraryBaseType = (DeclaredType) libraryBaseTypeElement.asType();

            CodeTypeElement uncachedClass = createUncached(libraryExports);
            CodeTypeElement cacheClass = createCached(libraryExports);

            CodeTypeElement resolvedExports = createResolvedExports(libraryExports, ElementUtils.getSimpleName(libraryBaseType) + "Exports", cacheClass, uncachedClass);
            resolvedExports.add(cacheClass);
            resolvedExports.add(uncachedClass);

            genClass.add(resolvedExports);
            statics.startNew(resolvedExports.asType()).end();
        }

        statics.end();
        statics.end();

        genClass.addAll(libraryConstants.values());
        return Arrays.asList(genClass);
    }

    private static void groupMergedLibraries(Collection<SpecializationData> specializations, final Map<CacheKey, List<CacheExpression>> mergedLibraries) {
        for (SpecializationData specialization : specializations) {
            if (!specialization.isReachable()) {
                continue;
            }
            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.isMergedLibrary()) {
                    mergedLibraries.computeIfAbsent(new CacheKey(cache), (b) -> new ArrayList<>()).add(cache);
                }
            }
        }
    }

    private static boolean useSingleton(ExportsLibrary library, boolean cached) {
        return library.isFinalReceiver() && !library.needsRewrites() && !library.needsDynamicDispatch() && !needsReceiver(library, cached);
    }

    CodeTypeElement createResolvedExports(ExportsLibrary library, String className, CodeTypeElement cacheClass, CodeTypeElement uncachedClass) {
        CodeTreeBuilder builder;
        final TypeElement libraryBaseTypeElement = library.getLibrary().getTemplateType();
        final DeclaredType libraryBaseType = (DeclaredType) libraryBaseTypeElement.asType();
        final TypeMirror exportReceiverType = library.getReceiverType();

        TypeMirror baseType = new CodeTypeMirror.DeclaredCodeTypeMirror(context.getTypeElement(types.LibraryExport),
                        Arrays.asList(libraryBaseType));
        CodeTypeElement exportsClass = createClass(library, null, modifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), className, baseType);

        CodeExecutableElement constructor = new CodeExecutableElement(modifiers(PRIVATE), null, exportsClass.getSimpleName().toString());
        builder = constructor.createBuilder();
        builder.startStatement().startSuperCall().typeLiteral(libraryBaseType).typeLiteral(library.getReceiverType()).string(Boolean.valueOf(library.isBuiltinDefaultExport()).toString()).end().end();
        exportsClass.add(constructor);

        if (library.hasExportDelegation()) {
            CodeVariableElement enabledMessagesVariable = exportsClass.add(new CodeVariableElement(modifiers(STATIC, FINAL), types.FinalBitSet, ENABLED_MESSAGES_NAME));
            CodeTreeBuilder init = enabledMessagesVariable.createInitBuilder();
            init.startCall("createMessageBitSet");
            init.staticReference(useLibraryConstant(library.getLibrary().getTemplateType().asType()));
            for (String message : library.getExportedMessages().keySet()) {
                if (message.equals(ACCEPTS)) {
                    continue;
                }
                init.doubleQuote(message);
            }
            init.end();
        }

        CodeVariableElement uncachedSingleton = null;
        if (useSingleton(library, false)) {
            GeneratedTypeMirror uncachedType = new GeneratedTypeMirror("", uncachedClass.getSimpleName().toString());
            uncachedSingleton = exportsClass.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), uncachedType, "UNCACHED"));
            builder = uncachedSingleton.createInitBuilder();
            builder.startNew(uncachedType).end();
        }

        CodeExecutableElement createUncached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.LibraryExport, "createUncached"));
        createUncached.setReturnType(libraryBaseType);
        createUncached.getModifiers().remove(Modifier.ABSTRACT);
        createUncached.renameArguments("receiver");
        builder = createUncached.createBuilder();
        if (!ElementUtils.typeEquals(exportReceiverType, context.getType(Object.class))) {
            builder.startAssert().string("receiver instanceof ").type(exportReceiverType).end();
        }

        builder.startStatement();
        builder.type(library.getLibrary().getTemplateType().asType());
        builder.string(" uncached = ");
        if (library.hasExportDelegation()) {
            builder.startCall("createDelegate");
            builder.staticReference(useLibraryConstant(library.getLibrary().getTemplateType().asType()));
        }
        if (uncachedSingleton != null) {
            builder.staticReference(uncachedSingleton);
        } else {
            List<ExecutableElement> constructors = ElementFilter.constructorsIn(uncachedClass.getEnclosedElements());
            builder.startNew(uncachedClass.getSimpleName().toString());
            if (!constructors.isEmpty()) {
                ExecutableElement uncachedClassConstructor = constructors.iterator().next();
                if (uncachedClassConstructor.getParameters().size() == 1) {
                    builder.string("receiver");
                }
            }
            builder.end();
        }
        if (library.hasExportDelegation()) {
            builder.end(); // create delegate
        }
        builder.end(); // statement

        builder.startReturn().string("uncached").end();

        exportsClass.add(createUncached);

        CodeVariableElement cacheSingleton = null;
        if (useSingleton(library, true)) {
            GeneratedTypeMirror cachedType = new GeneratedTypeMirror("", cacheClass.getSimpleName().toString());
            cacheSingleton = exportsClass.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), cachedType, "CACHE"));
            builder = cacheSingleton.createInitBuilder();
            builder.startNew(cachedType).end();
        }

        CodeExecutableElement createCached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.LibraryExport, "createCached"));
        createCached.setReturnType(libraryBaseType);
        createCached.getModifiers().remove(Modifier.ABSTRACT);
        createCached.renameArguments("receiver");
        builder = createCached.createBuilder();
        if (!ElementUtils.typeEquals(exportReceiverType, context.getType(Object.class))) {
            builder.startAssert().string("receiver instanceof ").type(exportReceiverType).end();
        }
        builder.startReturn();
        if (library.hasExportDelegation()) {
            builder.startCall("createDelegate");
            builder.staticReference(useLibraryConstant(library.getLibrary().getTemplateType().asType()));
        }
        if (cacheSingleton != null) {
            builder.staticReference(cacheSingleton);
        } else {
            List<ExecutableElement> constructors = ElementFilter.constructorsIn(cacheClass.getEnclosedElements());
            builder.startNew(cacheClass.getSimpleName().toString());
            if (!constructors.isEmpty()) {
                ExecutableElement cacheClassConstructor = constructors.iterator().next();
                if (cacheClassConstructor.getParameters().size() == 1) {
                    builder.string("receiver");
                }
            }
            builder.end();
        }
        if (library.hasExportDelegation()) {
            builder.end(); // create delegate
        }
        builder.end();
        exportsClass.add(createCached);

        return exportsClass;
    }

    private static CodeTree writeExpression(CacheExpression cache, String receiverName, TypeMirror receiverSourceType, TypeMirror receiverTargetType) {
        DSLExpression expression = cache.getDefaultExpression();
        Set<Variable> boundVariables = expression.findBoundVariables();
        Map<Variable, CodeTree> parameters = new HashMap<>();
        for (Variable variable : boundVariables) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            boolean needsCast = !variable.equals(expression);
            if (needsCast && !ElementUtils.isAssignable(receiverSourceType, receiverTargetType)) {
                builder.startParantheses();
                builder.cast(receiverTargetType);
            }
            builder.string(receiverName);
            if (needsCast && !ElementUtils.isAssignable(receiverSourceType, receiverTargetType)) {
                builder.end();
            }
            parameters.put(variable, builder.build());
        }
        return DSLExpressionGenerator.write(expression, null, parameters);
    }

    CodeTypeElement createDefaultExportProvider(ExportsLibrary libraryExports) {
        String libraryName = libraryExports.getLibrary().getTemplateType().getSimpleName().toString();
        CodeTypeElement providerClass = createClass(libraryExports, null, modifiers(PUBLIC, STATIC, FINAL), libraryName + "Provider", null);
        providerClass.getImplements().add(context.getTypes().DefaultExportProvider);

        for (ExecutableElement method : ElementFilter.methodsIn(context.getTypes().DefaultExportProvider.asElement().getEnclosedElements())) {
            CodeExecutableElement m = null;
            switch (method.getSimpleName().toString()) {
                case "getLibraryClassName":
                    m = CodeExecutableElement.cloneNoAnnotations(method);
                    m.createBuilder().startReturn().doubleQuote(context.getEnvironment().getElementUtils().getBinaryName(libraryExports.getLibrary().getTemplateType()).toString()).end();
                    break;
                case "getDefaultExport":
                    m = CodeExecutableElement.cloneNoAnnotations(method);
                    m.createBuilder().startReturn().typeLiteral(libraryExports.getTemplateType().asType()).end();
                    break;
                case "getReceiverClass":
                    m = CodeExecutableElement.cloneNoAnnotations(method);
                    m.createBuilder().startReturn().typeLiteral(libraryExports.getReceiverType()).end();
                    break;
                case "getPriority":
                    m = CodeExecutableElement.cloneNoAnnotations(method);
                    m.createBuilder().startReturn().string(String.valueOf(libraryExports.getDefaultExportPriority())).end();
                    break;
            }
            if (m != null) {
                m.getModifiers().remove(Modifier.ABSTRACT);
                providerClass.add(m);
            }
        }
        if (providerClass.getEnclosedElements().size() != 4) {
            throw new AssertionError();
        }
        return providerClass;
    }

    CodeTypeElement createCached(ExportsLibrary libraryExports) {
        TypeMirror exportReceiverType = libraryExports.getReceiverType();
        TypeElement libraryBaseTypeElement = libraryExports.getLibrary().getTemplateType();
        DeclaredType libraryBaseType = (DeclaredType) libraryBaseTypeElement.asType();

        final Map<CacheKey, List<CacheExpression>> mergedLibraries = new LinkedHashMap<>();
        for (ExportMessageData message : libraryExports.getExportedMessages().values()) {
            if (message.getSpecializedNode() != null) {
                groupMergedLibraries(message.getSpecializedNode().getSpecializations(), mergedLibraries);
            }
        }
        // caches initialized as part
        final ExportMessageData acceptsMessage = libraryExports.getExportedMessages().get(ACCEPTS);
        Map<CacheKey, List<CacheExpression>> eagerCaches = initializeEagerCaches(libraryExports);

        CodeTypeElement cacheClass = createClass(libraryExports, null, modifiers(PRIVATE, STATIC, FINAL), "Cached", libraryBaseType);
        CodeTree acceptsAssertions = createDynamicDispatchAssertions(libraryExports);

        Set<NodeData> cachedSharedNodes = new LinkedHashSet<>();
        for (ExportMessageData export : libraryExports.getExportedMessages().values()) {
            if (export.getSpecializedNode() != null) {
                cachedSharedNodes.add(export.getSpecializedNode());
            }
        }

        CodeExecutableElement constructor = cacheClass.add(GeneratorUtils.createConstructorUsingFields(modifiers(), cacheClass));
        if (needsReceiver(libraryExports, true)) {
            final String receiverName = "receiver";
            CodeTreeBuilder builder = constructor.appendBuilder();
            if (ElementUtils.needsCastTo(context.getType(Object.class), libraryExports.getReceiverType())) {
                constructor.addParameter(new CodeVariableElement(context.getType(Object.class), "originalReceiver"));
                builder.declaration(libraryExports.getReceiverType(), receiverName,
                                CodeTreeBuilder.createBuilder().maybeCast(context.getType(Object.class), libraryExports.getReceiverType(), "originalReceiver").build());
            } else {
                constructor.addParameter(new CodeVariableElement(context.getType(Object.class), receiverName));
            }

            for (CacheKey key : mergedLibraries.keySet()) {
                CodeTree mergedLibraryIdentifier = writeExpression(key.cache, receiverName, libraryExports.getReceiverType(), libraryExports.getReceiverType());
                String identifier = key.getCache().getMergedLibraryIdentifier();
                builder.startStatement();
                builder.string("this.", identifier, " = super.insert(");
                builder.staticReference(useLibraryConstant(key.libraryType)).startCall(".create").tree(mergedLibraryIdentifier).end();
                builder.string(")").end();
                CodeVariableElement var = cacheClass.add(new CodeVariableElement(modifiers(PRIVATE), key.libraryType, identifier));
                var.getAnnotationMirrors().add(new CodeAnnotationMirror(types.Node_Child));
            }

            if (acceptsMessage != null && acceptsMessage.getSpecializedNode() != null) {
                SpecializationData firstSpecialization = null;
                for (SpecializationData s : acceptsMessage.getSpecializedNode().getSpecializations()) {
                    if (!s.isSpecialized() || !s.isReachable()) {
                        continue;
                    }
                    firstSpecialization = s;
                    break;
                }

                FlatNodeGenFactory factory = new FlatNodeGenFactory(context, GeneratorMode.EXPORTED_MESSAGE, acceptsMessage.getSpecializedNode(),
                                cachedSharedNodes, libraryExports.getSharedExpressions(), libraryConstants);
                List<CacheExpression> caches = new ArrayList<>();
                for (CacheKey key : eagerCaches.keySet()) {
                    caches.add(key.cache);
                }
                if (firstSpecialization == null) {
                    throw new AssertionError();
                }
                builder.tree(factory.createInitializeCaches(firstSpecialization, caches, constructor, receiverName));
            }

        }

        CodeTreeBuilder builder;
        if (libraryExports.hasExportDelegation()) {
            cacheClass.getImplements().add(types.LibraryExport_DelegateExport);

            CodeExecutableElement getExportMessages = CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.LibraryExport_DelegateExport, "getDelegateExportMessages"));
            getExportMessages.getModifiers().remove(Modifier.ABSTRACT);
            builder = getExportMessages.createBuilder();
            builder.startReturn().string(ENABLED_MESSAGES_NAME).end();
            cacheClass.add(getExportMessages);

            CodeExecutableElement readDelegate = CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.LibraryExport_DelegateExport, "readDelegateExport"));
            readDelegate.getModifiers().remove(Modifier.ABSTRACT);
            readDelegate.renameArguments("receiver_");
            builder = readDelegate.createBuilder();
            builder.startReturn();
            builder.tree(createReceiverCast(libraryExports,
                            readDelegate.getParameters().get(0).asType(),
                            libraryExports.getReceiverType(),
                            CodeTreeBuilder.singleString("receiver_"), true));
            builder.string(".").string(libraryExports.getDelegationVariable().getSimpleName().toString());
            builder.end();
            cacheClass.add(readDelegate);

            // find merged library for the delegation
            CodeExecutableElement acceptsMethod = (CodeExecutableElement) libraryExports.getExportedMessages().get("accepts").getMessageElement();
            VariableElement delegateLibraryParam = acceptsMethod.getParameters().get(1);
            String mergedLibraryId = null;
            outer: for (Entry<CacheKey, List<CacheExpression>> library : mergedLibraries.entrySet()) {
                for (CacheExpression cache : library.getValue()) {
                    if (ElementUtils.variableEquals(cache.getParameter().getVariableElement(), delegateLibraryParam)) {
                        mergedLibraryId = library.getKey().getCache().getMergedLibraryIdentifier();
                        break outer;
                    }
                }
            }
            if (mergedLibraryId == null) {
                throw new AssertionError("Could not find merged library for export delegation.");
            }

            CodeExecutableElement getDelegateLibrary = CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.LibraryExport_DelegateExport, "getDelegateExportLibrary"));
            getDelegateLibrary.getModifiers().remove(Modifier.ABSTRACT);
            builder = getDelegateLibrary.createBuilder();
            builder.startReturn().string("this.", mergedLibraryId).end();
            cacheClass.add(getDelegateLibrary);
        }

        CodeTree defaultAccepts = createDefaultAccepts(cacheClass, constructor, libraryExports, exportReceiverType, "receiver", true);

        CodeExecutableElement accepts = CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.Library, ACCEPTS));
        accepts.getModifiers().remove(Modifier.ABSTRACT);
        accepts.renameArguments("receiver");
        builder = accepts.createBuilder();

        if (acceptsAssertions != null) {
            builder.tree(acceptsAssertions);
        }
        if (mergedLibraries.isEmpty()) {
            builder.startReturn();
            if (acceptsMessage == null || acceptsMessage.isGenerated()) {
                builder.tree(defaultAccepts);
            } else {
                builder.tree(defaultAccepts).string(" && accepts_(receiver)");
            }
            builder.end();
        } else {
            builder.startIf().string("!(").tree(defaultAccepts).string(")").end();
            builder.startBlock();
            builder.returnFalse();
            builder.end();
            for (CacheKey key : mergedLibraries.keySet()) {
                CodeTree mergedLibraryInitializer = writeExpression(key.cache, "receiver", context.getType(Object.class), libraryExports.getReceiverType());
                String identifier = key.getCache().getMergedLibraryIdentifier();
                builder.startElseIf();
                builder.string("!this.", identifier);
                builder.startCall(".accepts").tree(mergedLibraryInitializer).end();
                builder.end().startBlock();
                builder.returnFalse();
                builder.end();
            }
            builder.startElseBlock();
            if (acceptsMessage != null && !acceptsMessage.isGenerated()) {
                builder.startReturn();
                builder.string("accepts_(receiver)");
                builder.end();
            } else {
                builder.returnTrue();
            }
            builder.end();
        }

        cacheClass.addOptional(createCastMethod(libraryExports, exportReceiverType, true));
        cacheClass.add(accepts);

        if (!libraryExports.needsRewrites() && useSingleton(libraryExports, true)) {
            CodeExecutableElement isAdoptable = cacheClass.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.Node, "isAdoptable")));
            builder = isAdoptable.createBuilder();
            if (libraryExports.needsDynamicDispatch()) {
                builder.startReturn();
                builder.startCall("dynamicDispatch_", "isAdoptable").end();
                builder.end();
            } else {
                builder.returnFalse();
            }
        }

        if (libraryExports.isAllowTransition()) {
            TypeMirror libraryType = libraryExports.getLibrary().getTemplateType().asType();
            CodeExecutableElement fallback = cacheClass.add(new CodeExecutableElement(modifiers(PRIVATE), libraryType, "getFallback_"));
            fallback.addParameter(new CodeVariableElement(libraryExports.getLibrary().getSignatureReceiverType(), "receiver"));
            CodeVariableElement fallbackVar = cacheClass.add(new CodeVariableElement(modifiers(PRIVATE), libraryType, "fallback_"));
            fallbackVar.addAnnotationMirror(new CodeAnnotationMirror(types.Node_Child));

            builder = fallback.createBuilder();
            builder.declaration(libraryType, "localFallback", "this.fallback_");
            builder.startIf().string("localFallback == null").end().startBlock();
            builder.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            builder.startStatement();
            CodeTree transitionLimit = DSLExpressionGenerator.write(libraryExports.getTransitionLimit(), null, Collections.emptyMap());
            builder.string("this.fallback_ = localFallback = insert(").staticReference(useLibraryConstant(libraryType)).startCall(".createDispatched").tree(transitionLimit).end().string(")");
            builder.end(); // statement
            builder.end(); // block
            builder.startReturn().string("localFallback").end();
        }

        Map<NodeData, CodeTypeElement> sharedNodes = new HashMap<>();

        for (ExportMessageData export : libraryExports.getExportedMessages().values()) {
            if (export.isGenerated()) {
                continue;
            }
            LibraryMessage message = export.getResolvedMessage();

            TypeMirror cachedExportReceiverType = export.getExportsLibrary().getReceiverType();

            // cached execute
            NodeData cachedSpecializedNode = export.getSpecializedNode();
            CodeExecutableElement cachedExecute = null;
            if (cachedSpecializedNode == null) {
                if (!export.isMethod()) {
                    throw new AssertionError("Missing method export. Missed validation for " + export.getResolvedMessage().getSimpleName());
                }

                boolean isAccepts = message.getMessageElement().getSimpleName().toString().equals(ACCEPTS);
                TypeMirror modelReceiverType;
                if (isAccepts) {
                    modelReceiverType = context.getType(Object.class);
                } else {
                    modelReceiverType = message.getLibrary().getSignatureReceiverType();
                }
                ExecutableElement exportMethod = (ExecutableElement) export.getMessageElement();
                CodeTree cachedReceiverAccess = createReceiverCast(libraryExports, modelReceiverType, cachedExportReceiverType, CodeTreeBuilder.singleString("receiver"), true);
                cachedReceiverAccess = CodeTreeBuilder.createBuilder().startParantheses().tree(cachedReceiverAccess).end().build();
                cachedExecute = cacheClass.add(createDirectCall(cachedReceiverAccess, message, exportMethod));
            } else {
                CodeTypeElement dummyNodeClass = sharedNodes.get(cachedSpecializedNode);
                boolean shared = true;
                if (dummyNodeClass == null) {
                    FlatNodeGenFactory factory = new FlatNodeGenFactory(context, GeneratorMode.EXPORTED_MESSAGE, cachedSpecializedNode, cachedSharedNodes, libraryExports.getSharedExpressions(),
                                    libraryConstants);
                    dummyNodeClass = createClass(libraryExports, null, modifiers(), "Dummy", types.Node);
                    factory.create(dummyNodeClass);
                    sharedNodes.put(cachedSpecializedNode, dummyNodeClass);
                    shared = false;
                }

                for (Element element : dummyNodeClass.getEnclosedElements()) {
                    String simpleName = element.getSimpleName().toString();
                    if (element.getKind() == ElementKind.METHOD) {
                        if (simpleName.endsWith("AndSpecialize")) {
                            // nothing to do for specialize method
                        } else if (simpleName.startsWith(ExportsParser.EXECUTE_PREFIX) && simpleName.endsWith(ExportsParser.EXECUTE_SUFFIX)) {
                            CodeExecutableElement executable = (CodeExecutableElement) element;
                            executable.setVarArgs(message.getExecutable().isVarArgs());
                            cachedExecute = CodeExecutableElement.clone(executable);
                            cachedExecute.setSimpleName(CodeNames.of(message.getName()));
                            injectReceiverType(cachedExecute, libraryExports, cachedExportReceiverType, true);
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
            if (cachedExecute == null) {
                throw new AssertionError("execute not found");
            }
            if (message.getName().equals(ACCEPTS)) {
                if (export.getExportsLibrary().isFinalReceiver() && (cachedSpecializedNode == null || !cachedSpecializedNode.needsRewrites(context)) && eagerCaches.isEmpty()) {
                    cachedExecute.getModifiers().add(Modifier.STATIC);
                }
                cachedExecute.setSimpleName(CodeNames.of(ACCEPTS_METHOD_NAME));
                ElementUtils.setVisibility(cachedExecute.getModifiers(), Modifier.PRIVATE);
            } else {
                if (libraryExports.needsRewrites()) {
                    injectCachedAssertions(export.getExportsLibrary().getLibrary(), cachedExecute);
                }

                CodeTree originalBody = cachedExecute.getBodyTree();
                CodeTreeBuilder b = cachedExecute.createBuilder();
                if (libraryExports.isAllowTransition()) {
                    b.startAssert();
                    String name = cachedExecute.getParameters().get(0).getSimpleName().toString();
                    b.tree(createDefaultAccepts(null, null, libraryExports, libraryExports.getReceiverType(), name, true));
                    b.string(" : ").doubleQuote(INVALID_LIBRARY_USAGE_MESSAGE);
                    b.end();
                    b.startIf().startCall("this.accepts").string(name).end().end().startBlock();
                    b.tree(originalBody);
                    b.end();
                    b.startElseBlock();
                    b.startReturn();
                    b.startCall("getFallback_").string(name).end().string(".");
                    b.startCall(cachedExecute.getSimpleName().toString());
                    for (VariableElement param : cachedExecute.getParameters()) {
                        b.string(param.getSimpleName().toString());
                    }
                    b.end(); // call
                    b.end(); // return
                    b.end(); // block
                } else {
                    CodeTree customAcceptsAssertion;
                    if (mergedLibraries.isEmpty()) {
                        // use normal accepts call
                        customAcceptsAssertion = null;
                    } else {
                        // with merged libraries we need to use the default accepts
                        // for the assertion as merged libraries might require transitions
                        String name = b.findMethod().getParameters().get(0).getSimpleName().toString();
                        customAcceptsAssertion = createDefaultAccepts(null, null, libraryExports, exportReceiverType, name, true);
                    }
                    addAcceptsAssertion(b, customAcceptsAssertion);
                    b.tree(originalBody);
                }
            }
        }

        return cacheClass;
    }

    private static Map<CacheKey, List<CacheExpression>> initializeEagerCaches(ExportsLibrary libraryExports) {
        final ExportMessageData acceptsMessage = libraryExports.getExportedMessages().get(ACCEPTS);
        final Map<CacheKey, List<CacheExpression>> eagerCaches = new LinkedHashMap<>();
        if (acceptsMessage != null && acceptsMessage.getSpecializedNode() != null) {
            int specializationCount = 0;
            for (SpecializationData s : acceptsMessage.getSpecializedNode().getSpecializations()) {
                if (!s.isReachable() || !s.isSpecialized()) {
                    continue;
                }
                specializationCount++;
                for (CacheExpression cache : s.getCaches()) {
                    if (isEagerInitialize(acceptsMessage, cache)) {
                        eagerCaches.computeIfAbsent(new CacheKey(cache), (b) -> new ArrayList<>()).add(cache);
                    }
                }
            }
            if (specializationCount > 1) {
                Iterator<List<CacheExpression>> iterator = eagerCaches.values().iterator();
                while (iterator.hasNext()) {
                    List<CacheExpression> caches = iterator.next();
                    if (caches.size() != specializationCount) {
                        // if a cache is not contained on all specializations
                        // do not initialize it eagerly. Probably a pretty rare case
                        // for accepts exports.
                        iterator.remove();
                    }
                }
            }
        }

        Set<String> eagerSharedGroups = new HashSet<>();
        for (List<CacheExpression> caches : eagerCaches.values()) {
            for (CacheExpression cache : caches) {
                if (cache.isMergedLibrary()) {
                    // merged libraries are handled elsewhere
                    continue;
                }
                cache.setEagerInitialize(true);

                if (cache.getSharedGroup() != null) {
                    eagerSharedGroups.add(cache.getSharedGroup());
                }
            }
        }
        if (eagerSharedGroups.size() > 0) {
            for (ExportMessageData message : libraryExports.getExportedMessages().values()) {
                if (message.getSpecializedNode() != null) {
                    for (SpecializationData specialization : message.getSpecializedNode().getSpecializations()) {
                        if (!specialization.isReachable()) {
                            continue;
                        }
                        for (CacheExpression cache : specialization.getCaches()) {
                            if (cache.getSharedGroup() != null && eagerSharedGroups.contains(cache.getSharedGroup())) {
                                cache.setEagerInitialize(true);
                            }
                        }
                    }
                }
            }
        }
        return eagerCaches;
    }

    private static boolean needsReceiver(ExportsLibrary libraryExports, boolean cached) {
        if (cached) {
            for (ExportMessageData message : libraryExports.getExportedMessages().values()) {
                if (message.getSpecializedNode() != null) {
                    for (SpecializationData specialization : message.getSpecializedNode().getSpecializations()) {
                        if (!specialization.isReachable()) {
                            continue;
                        }
                        for (CacheExpression cache : specialization.getCaches()) {
                            if (cache.isMergedLibrary()) {
                                return true;
                            }
                            if (isEagerInitialize(message, cache)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return libraryExports.needsDynamicDispatch() || !libraryExports.isFinalReceiver();
    }

    private static boolean isEagerInitialize(ExportMessageData message, @SuppressWarnings("unused") CacheExpression cache) {
        // any cached in accepts can be always initialized and needs
        // a receiver to initialize.
        return message.getResolvedMessage().getName().equals(ACCEPTS);
    }

    private CodeExecutableElement createCastMethod(ExportsLibrary libraryExports, TypeMirror exportReceiverType, boolean cached) {
        if (!libraryExports.getLibrary().isDynamicDispatch()) {
            return null;
        }

        CodeTreeBuilder builder;
        CodeExecutableElement castMethod = CodeExecutableElement.cloneNoAnnotations(ElementUtils.findMethod(types.DynamicDispatchLibrary, "cast"));
        castMethod.getModifiers().remove(Modifier.ABSTRACT);
        castMethod.renameArguments("receiver");
        builder = castMethod.createBuilder();
        if ((!cached || libraryExports.isFinalReceiver()) && ElementUtils.needsCastTo(castMethod.getParameters().get(0).asType(), exportReceiverType)) {
            GeneratorUtils.mergeSupressWarnings(castMethod, "cast");
        }
        if (!cached) {
            GeneratorUtils.addBoundaryOrTransferToInterpreter(castMethod, builder);
        }
        builder.startReturn().tree(createReceiverCast(libraryExports, castMethod.getParameters().get(0).asType(), exportReceiverType, CodeTreeBuilder.singleString("receiver"), cached)).end();
        return castMethod;
    }

    private CodeTree createDefaultAccepts(CodeTypeElement libraryGen, CodeExecutableElement constructor,
                    ExportsLibrary libraryExports, TypeMirror exportReceiverType, String receiverName, boolean cached) {
        CodeTreeBuilder constructorBuilder = null;
        CodeTreeBuilder acceptsBuilder = CodeTreeBuilder.createBuilder();
        if (libraryExports.needsDynamicDispatch()) {
            if (libraryGen != null) {
                CodeVariableElement dynamicDispatchLibrary = libraryGen.add(new CodeVariableElement(modifiers(PRIVATE), types.DynamicDispatchLibrary, "dynamicDispatch_"));
                dynamicDispatchLibrary.addAnnotationMirror(new CodeAnnotationMirror(types.Node_Child));
            }

            if (constructor != null) {
                CodeVariableElement dispatchLibraryConstant = useDispatchLibraryConstant();
                constructorBuilder = constructor.appendBuilder();
                if (cached) {
                    constructorBuilder.startStatement().string("this.dynamicDispatch_ = insert(").staticReference(dispatchLibraryConstant).string(".create(receiver))").end();
                } else {
                    constructorBuilder.startStatement().string("this.dynamicDispatch_ = ").staticReference(dispatchLibraryConstant).string(".getUncached(receiver)").end();
                }
            }
            acceptsBuilder.string("dynamicDispatch_.accepts(" + receiverName + ") && dynamicDispatch_.dispatch(" + receiverName + ") == ");

            if (libraryExports.isDynamicDispatchTarget()) {
                acceptsBuilder.typeLiteral(libraryExports.getTemplateType().asType());
            } else {
                String name = "dynamicDispatchTarget_";
                if (libraryGen != null) {
                    libraryGen.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), context.getType(Class.class), name));
                }
                if (constructor != null) {
                    CodeVariableElement dispatchLibraryConstant = useDispatchLibraryConstant();
                    if (cached) {
                        constructorBuilder.startStatement();
                        constructorBuilder.string("this.dynamicDispatchTarget_ = ").staticReference(dispatchLibraryConstant).string(".getUncached(receiver).dispatch(receiver)");
                        constructorBuilder.end();
                    } else {
                        constructorBuilder.statement("this.dynamicDispatchTarget_ = dynamicDispatch_.dispatch(" + receiverName + ")");
                    }
                }
                acceptsBuilder.string(name);
            }
        } else {
            if (libraryExports.isFinalReceiver()) {
                acceptsBuilder.string(receiverName, " instanceof ").type(exportReceiverType);
            } else {
                TypeMirror receiverType = libraryExports.getReceiverType();
                TypeMirror receiverClassType = new CodeTypeMirror.DeclaredCodeTypeMirror(context.getTypeElement(Class.class),
                                Arrays.asList(new CodeTypeMirror.WildcardTypeMirror(receiverType, null)));
                if (libraryGen != null) {
                    libraryGen.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), receiverClassType, "receiverClass_"));
                }

                if (constructor != null) {
                    boolean doCast = !((TypeElement) ((DeclaredType) receiverType).asElement()).getTypeParameters().isEmpty();
                    CodeTreeBuilder builder = constructor.appendBuilder().startStatement().string("this.receiverClass_ = ");
                    if (doCast) {
                        builder.cast(receiverClassType);
                        constructor.addAnnotationMirror(LibraryGenerator.createSuppressWarningsUnchecked(context));
                    }
                    if (cached || ElementUtils.isObject(receiverType)) {
                        builder.string(receiverName + ".getClass()").end();
                    } else {
                        builder.string("(").cast(receiverType).string(receiverName + ").getClass()").end();
                    }
                }

                acceptsBuilder.string(receiverName, ".getClass() == this.receiverClass_");
            }
        }

        CodeTree defaultAccepts = acceptsBuilder.build();
        return defaultAccepts;
    }

    private CodeVariableElement useLibraryConstant(TypeMirror typeConstant) {
        return FlatNodeGenFactory.createLibraryConstant(libraryConstants, typeConstant);
    }

    private CodeVariableElement useDispatchLibraryConstant() {
        return useLibraryConstant(types.DynamicDispatchLibrary);
    }

    private CodeTree createDynamicDispatchAssertions(ExportsLibrary libraryExports) {
        if (libraryExports.needsDynamicDispatch() || libraryExports.getLibrary().isDynamicDispatch()) {
            // no assertions for dynamic dispatch itself.
            return null;
        }
        if (!libraryExports.getLibrary().isDynamicDispatchEnabled()) {
            return null;
        }
        CodeVariableElement dispatchLibraryConstant = useDispatchLibraryConstant();
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startAssert();
        if (libraryExports.isFinalReceiver()) {
            builder.string("!(receiver instanceof ").type(libraryExports.getReceiverType()).string(")");
        } else {
            builder.string("receiver.getClass() != this.receiverClass_");
        }
        builder.string(" || ");
        builder.staticReference(dispatchLibraryConstant).string(
                        ".getUncached().dispatch(receiver) == null : ").doubleQuote(
                                        "Invalid library export. Exported receiver with dynamic dispatch found but not expected.");
        builder.end();
        return builder.build();
    }

    CodeTypeElement createUncached(ExportsLibrary libraryExports) {
        final TypeMirror exportReceiverType = libraryExports.getReceiverType();

        final TypeElement libraryBaseTypeElement = libraryExports.getLibrary().getTemplateType();
        final DeclaredType libraryBaseType = (DeclaredType) libraryBaseTypeElement.asType();
        final TypeMirror libraryReceiverType = libraryExports.getLibrary().getExportsReceiverType();

        CodeTypeElement uncachedClass = createClass(libraryExports, null, modifiers(PRIVATE, STATIC, FINAL), "Uncached", libraryBaseType);

        CodeExecutableElement constructor = uncachedClass.add(GeneratorUtils.createConstructorUsingFields(modifiers(), uncachedClass));
        if (needsReceiver(libraryExports, false)) {
            constructor.addParameter(new CodeVariableElement(context.getType(Object.class), "receiver"));
        }
        CodeTreeBuilder builder;

        if (libraryExports.hasExportDelegation()) {
            uncachedClass.getImplements().add(types.LibraryExport_DelegateExport);

            CodeExecutableElement getExportMessages = CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.LibraryExport_DelegateExport, "getDelegateExportMessages"));
            getExportMessages.getModifiers().remove(Modifier.ABSTRACT);
            builder = getExportMessages.createBuilder();
            builder.startReturn().string(ENABLED_MESSAGES_NAME).end();
            uncachedClass.add(getExportMessages);

            CodeExecutableElement readDelegate = CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.LibraryExport_DelegateExport, "readDelegateExport"));
            readDelegate.getModifiers().remove(Modifier.ABSTRACT);
            readDelegate.renameArguments("receiver_");
            builder = readDelegate.createBuilder();
            builder.startReturn();
            builder.string("(");
            builder.tree(createReceiverCast(libraryExports,
                            readDelegate.getParameters().get(0).asType(),
                            libraryExports.getReceiverType(),
                            CodeTreeBuilder.singleString("receiver_"), false));
            builder.string(")");
            builder.string(".").string(libraryExports.getDelegationVariable().getSimpleName().toString());
            builder.end();
            uncachedClass.add(readDelegate);

            // find merged library for the delegation
            CodeExecutableElement getDelegateLibrary = CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.LibraryExport_DelegateExport, "getDelegateExportLibrary"));
            getDelegateLibrary.renameArguments("delegate_");
            getDelegateLibrary.getModifiers().remove(Modifier.ABSTRACT);
            builder = getDelegateLibrary.createBuilder();
            builder.startReturn();
            builder.staticReference(useLibraryConstant(libraryExports.getLibrary().getTemplateType().asType()));
            builder.string(".getUncached(delegate_)");
            builder.end();

            uncachedClass.add(getDelegateLibrary);
        }

        CodeTree acceptsAssertions = createDynamicDispatchAssertions(libraryExports);
        CodeTree defaultAccepts = createDefaultAccepts(uncachedClass, constructor, libraryExports, exportReceiverType, "receiver", false);

        CodeExecutableElement acceptUncached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.Library, ACCEPTS));
        acceptUncached.getModifiers().remove(Modifier.ABSTRACT);
        acceptUncached.renameArguments("receiver");
        builder = acceptUncached.createBuilder();
        GeneratorUtils.addBoundaryOrTransferToInterpreter(acceptUncached, builder);
        if (acceptsAssertions != null) {
            builder.tree(acceptsAssertions);
        }
        ExportMessageData accepts = libraryExports.getExportedMessages().get(ACCEPTS);
        if (accepts == null || accepts.isGenerated()) {
            builder.startReturn().tree(defaultAccepts).end();
        } else {
            builder.startReturn().tree(defaultAccepts).string(" && accepts_(receiver)").end();
        }
        builder.end();
        uncachedClass.add(acceptUncached);

        uncachedClass.addOptional(createCastMethod(libraryExports, exportReceiverType, false));

        Set<NodeData> uncachedSharedNodes = new LinkedHashSet<>();
        for (ExportMessageData export : libraryExports.getExportedMessages().values()) {
            if (export.getSpecializedNode() != null) {
                uncachedSharedNodes.add(export.getSpecializedNode());
            }
        }

        CodeExecutableElement isAdoptable = uncachedClass.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.Node, "isAdoptable")));
        isAdoptable.createBuilder().returnFalse();

        CodeExecutableElement getCost = uncachedClass.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(types.Node, "getCost")));
        getCost.createBuilder().startReturn().staticReference(ElementUtils.findVariableElement(types.NodeCost, "MEGAMORPHIC")).end();

        boolean firstNode = true;

        for (ExportMessageData export : libraryExports.getExportedMessages().values()) {
            if (export.isGenerated()) {
                continue;
            }
            LibraryMessage message = export.getResolvedMessage();

            // uncached execute
            TypeMirror uncachedReceiverType = export.getReceiverType();
            CodeTree uncachedReceiverExport = CodeTreeBuilder.createBuilder().maybeCast(libraryReceiverType, uncachedReceiverType, "receiver").build();
            CodeExecutableElement uncachedExecute;
            NodeData uncachedSpecializedNode = export.getSpecializedNode();

            if (uncachedSpecializedNode == null) {
                if (!export.isMethod()) {
                    throw new AssertionError("Missing method export. Missed validation for " + export.getResolvedMessage().getSimpleName());
                }
                ExecutableElement exportMethod = (ExecutableElement) export.getMessageElement();
                CodeExecutableElement directCall = createDirectCall(uncachedReceiverExport, message, exportMethod);
                uncachedExecute = uncachedClass.add(directCall);
                if (message.getName().equals(ACCEPTS)) {
                    directCall.getModifiers().add(Modifier.STATIC);
                }
            } else {
                FlatNodeGenFactory factory = new FlatNodeGenFactory(context, GeneratorMode.EXPORTED_MESSAGE, uncachedSpecializedNode, uncachedSharedNodes, Collections.emptyMap(), libraryConstants);
                CodeExecutableElement generatedUncached = factory.createUncached();
                if (firstNode) {
                    uncachedClass.getEnclosedElements().addAll(factory.createUncachedFields());
                    firstNode = false;
                }
                generatedUncached.getModifiers().remove(STATIC);
                ElementUtils.setVisibility(generatedUncached.getModifiers(), Modifier.PUBLIC);
                generatedUncached.setSimpleName(CodeNames.of(message.getName()));
                generatedUncached.setVarArgs(message.getExecutable().isVarArgs());
                injectReceiverType(generatedUncached, libraryExports, uncachedReceiverType, false);
                uncachedExecute = uncachedClass.add(generatedUncached);
            }
            if (message.getName().equals(ACCEPTS)) {
                uncachedExecute.getModifiers().add(Modifier.STATIC);
                uncachedExecute.setSimpleName(CodeNames.of(ACCEPTS + "_"));
                ElementUtils.setVisibility(uncachedExecute.getModifiers(), Modifier.PRIVATE);
            } else {
                // prepend accepts assertion
                CodeTree originalBody = uncachedExecute.getBodyTree();
                CodeTreeBuilder b = uncachedExecute.createBuilder();
                GeneratorUtils.addBoundaryOrTransferToInterpreter(uncachedExecute, b);
                addAcceptsAssertion(b, null);
                b.tree(originalBody);
            }
        }

        return uncachedClass;

    }

    static void injectCachedAssertions(LibraryData libraryData, CodeExecutableElement cachedExecute) {
        CodeTree body = cachedExecute.getBodyTree();
        CodeTreeBuilder builder = cachedExecute.createBuilder();
        ExecutableElement element = ElementUtils.findExecutableElement((DeclaredType) libraryData.getTemplateType().asType(), "assertAdopted");
        if (element != null) {
            builder.startAssert().string("assertAdopted()").end();
        } else {
            builder.startAssert().string("getRootNode() != null : ").doubleQuote("Invalid libray usage. Cached library must be adopted by a RootNode before it is executed.").end();
        }
        builder.tree(body);
    }

    private CodeExecutableElement createDirectCall(CodeTree receiverAccess, LibraryMessage message, ExecutableElement targetMethod) {
        CodeTreeBuilder builder;
        CodeExecutableElement cachedExecute = CodeExecutableElement.cloneNoAnnotations(message.getExecutable());
        cachedExecute.renameArguments("receiver");
        cachedExecute.getModifiers().remove(Modifier.DEFAULT);
        cachedExecute.getModifiers().remove(Modifier.ABSTRACT);
        builder = cachedExecute.createBuilder();
        if (targetMethod == null && message.getMessageElement().getModifiers().contains(Modifier.ABSTRACT)) {
            GeneratorUtils.addBoundaryOrTransferToInterpreter(cachedExecute, builder);
            builder.startThrow().startNew(context.getType(AbstractMethodError.class)).end().end();
        } else {
            builder.startReturn();
            if (targetMethod == null) {
                builder.startCall("super", message.getName());
                builder.tree(receiverAccess);
            } else if (targetMethod.getModifiers().contains(Modifier.STATIC)) {
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
        }
        return cachedExecute;
    }

    private void injectReceiverType(CodeExecutableElement executable, ExportsLibrary library, TypeMirror receiverType, boolean cached) {
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
        String newReceiverParamName = originalReceiverParamName + "_";
        receiverParam.setName(newReceiverParamName);
        CodeTree tree = executable.getBodyTree();
        CodeTreeBuilder executeBody = executable.createBuilder();
        CodeTree cast = createReceiverCast(library, modelReceiverType, receiverType, CodeTreeBuilder.singleString(newReceiverParamName), cached);
        executeBody.declaration(receiverType, originalReceiverParamName, cast);
        executeBody.tree(tree);
    }

    private CodeTree createReceiverCast(ExportsLibrary library, TypeMirror sourceType, TypeMirror targetType, CodeTree receiver, boolean cached) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        if (!cached || library.isFinalReceiver()) {
            builder.string("(").maybeCast(sourceType, targetType).tree(receiver).string(")");
        } else {
            if (library.needsDynamicDispatch()) {
                builder.maybeCast(context.getType(Object.class), targetType).startCall("dynamicDispatch_.cast").tree(receiver).end();
            } else {
                builder.startStaticCall(types.CompilerDirectives, "castExact").tree(receiver).string("receiverClass_").end();
            }
        }
        return builder.build();
    }

    private static void addAcceptsAssertion(CodeTreeBuilder executeBody, CodeTree customAccept) {
        String name = executeBody.findMethod().getParameters().get(0).getSimpleName().toString();
        CodeTree accepts;
        if (customAccept != null) {
            /*
             * use internal accepts only because otherwise transitions assertions would fail.
             */
            accepts = customAccept;
        } else {
            accepts = executeBody.create().string("this.accepts(", name, ")").build();
        }
        executeBody.startAssert().tree(accepts).string(" : ").doubleQuote(INVALID_LIBRARY_USAGE_MESSAGE).end();
    }

    private static final String INVALID_LIBRARY_USAGE_MESSAGE = "Invalid library usage. Library does not accept given receiver.";

}
