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
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.library.ResolvedExports;
import com.oracle.truffle.api.library.ResolvedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeParameterElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class LibraryGenerator extends CodeTypeElementFactory<LibraryData> {

    private static final String ACCEPTS = "accepts";

    private ProcessorContext context;
    private LibraryData model;

    class MessageObjects {
        final LibraryMessage model;
        final int messageIndex;
        int cacheIndex;
        CodeVariableElement messageField;

        MessageObjects(LibraryMessage message, int messageIndex) {
            this.model = message;
            this.messageIndex = messageIndex;
        }
    }

    @Override
    public List<CodeTypeElement> create(ProcessorContext context1, LibraryData model1) {
        this.context = context1;
        this.model = model1;
        CodeTreeBuilder builder;
        if (model1.hasErrors()) {
            return Collections.emptyList();
        }

        final TypeElement libraryType = model.getTemplateType();
        final TypeMirror libraryTypeMirror = libraryType.asType();

        TypeMirror baseType = new CodeTypeMirror.DeclaredCodeTypeMirror(context.getTypeElement(ResolvedLibrary.class),
                        Arrays.asList(libraryTypeMirror));

        CodeTypeElement genClass = createClass(model, null, modifiers(FINAL), createGenTypeName(model), baseType);
        CodeVariableElement instance = genClass.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), genClass.asType(), "INSTANCE"));
        builder = instance.createInitBuilder().startNew(genClass.asType()).end();

        CodeTreeBuilder statics = genClass.add(new CodeExecutableElement(modifiers(STATIC), null, "<cinit>")).createBuilder();
        statics.startStatement();
        statics.startStaticCall(context.getType(ResolvedLibrary.class), "register");
        statics.typeLiteral(libraryTypeMirror).string(instance.getName()).end();
        statics.end().end();

        List<MessageObjects> methods = new ArrayList<>();
        for (int messageIndex = 0; messageIndex < model.getMethods().size(); messageIndex++) {
            LibraryMessage message = model.getMethods().get(messageIndex);
            if (message.hasErrors()) {
                continue;
            }
            MessageObjects objects = new MessageObjects(message, messageIndex);
            methods.add(objects);
        }

        CodeExecutableElement getDefault = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(ResolvedLibrary.class), "getDefaultClass"));
        getDefault.getModifiers().remove(Modifier.ABSTRACT);
        getDefault.renameArguments("receiver");
        builder = getDefault.createBuilder();

        assert model.getDefaultExports().size() > 0;

        boolean elseIf = false;
        int index = 0;
        for (LibraryDefaultExportData defaultExport : model.getDefaultExports()) {
            TypeMirror defaultProviderReceiverType = defaultExport.getReceiverType();
            int ifCount = 0;
            if (ElementUtils.typeEquals(defaultProviderReceiverType, context.getType(Object.class))) {
                if (elseIf) {
                    builder.startElseBlock();
                    ifCount++;
                }
                // assert that this provider is the last element
                // everything after should not be reachable and should be already
                // filtered by the parser
                assert index == model.getDefaultExports().size() - 1;
            } else {
                elseIf = builder.startIf(elseIf);
                ifCount++;
                builder.string("receiver").instanceOf(ElementUtils.boxType(context, defaultProviderReceiverType));
                builder.end().startBlock();
            }
            if (defaultExport.getImplType() == null) {
                TypeMirror[] defaultTypeMirrors = crateDefaultImpl(genClass, methods);
                statics.startStatement().startStaticCall(context.getType(ResolvedExports.class), "register");
                statics.typeLiteral(defaultTypeMirrors[0]).startNew(defaultTypeMirrors[1]).end().end();
                statics.end().end();
                builder.startReturn().typeLiteral(defaultTypeMirrors[0]).end();
            } else {
                builder.startReturn().typeLiteral(defaultExport.getImplType()).end();
            }
            builder.end(ifCount);
            index++;
        }
        genClass.add(getDefault);

        // class MessageImpl
        final CodeTypeElement messageClass = createClass(model, null, modifiers(PRIVATE, STATIC), "MessageImpl", context.getType(Message.class));
        messageClass.add(new CodeVariableElement(modifiers(FINAL), context.getType(int.class), "index"));
        CodeExecutableElement messageConstructor = new CodeExecutableElement(modifiers(), null, messageClass.getSimpleName().toString());
        messageConstructor.addParameter(new CodeVariableElement(context.getType(String.class), "name"));
        messageConstructor.addParameter(new CodeVariableElement(context.getType(int.class), "index"));
        messageConstructor.addParameter(new CodeVariableElement(context.getType(Class.class), "returnType"));
        messageConstructor.addParameter(new CodeVariableElement(context.getType(Class[].class), "parameters"));
        messageConstructor.setVarArgs(true);
        builder = messageConstructor.createBuilder();
        builder.startStatement().startSuperCall().typeLiteral(libraryTypeMirror).string("name").string("returnType").string("parameters").end().end();
        builder.statement("this.index = index");
        messageClass.add(messageConstructor);
        genClass.add(messageClass);

        // class Proxy
        CodeTypeElement proxyClass = createClass(model, null, modifiers(PRIVATE, STATIC, FINAL), "Proxy", libraryTypeMirror);
        genClass.add(proxyClass);
        CodeVariableElement libField = proxyClass.add(new CodeVariableElement(modifiers(PRIVATE), context.getType(ReflectionLibrary.class), "lib"));
        libField.addAnnotationMirror(new CodeAnnotationMirror(context.getDeclaredType(Child.class)));
        proxyClass.add(GeneratorUtils.createConstructorUsingFields(modifiers(), proxyClass));

        for (MessageObjects message : methods) {
            if (message.model.getName().equals(ACCEPTS)) {
                continue;
            }
            message.messageField = proxyClass.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), context.getType(Message.class), createConstantName(message.model.getName())));
            builder = message.messageField.createInitBuilder();
            builder.startNew(messageClass.asType()).doubleQuote(message.model.getName()).string(String.valueOf(message.messageIndex));
            ExecutableElement method = message.model.getExecutable();
            builder.typeLiteral(method.getReturnType());
            for (int i = 0; i < method.getParameters().size(); i++) {
                builder.typeLiteral(method.getParameters().get(i).asType());
            }
            builder.end();
        }

        if (model.getAssertions() != null) {
            CodeExecutableElement createAssertions = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(ResolvedLibrary.class), "createAssertions"));
            createAssertions.getModifiers().remove(Modifier.ABSTRACT);
            ((CodeVariableElement) createAssertions.getParameters().get(0)).setType(libraryTypeMirror);
            createAssertions.renameArguments("delegate");
            createAssertions.setReturnType(libraryTypeMirror);
            createAssertions.createBuilder().startReturn().startNew(model.getAssertions()).string("delegate").end().end();
            genClass.add(createAssertions);
        }

        CodeExecutableElement createProxy = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(ResolvedLibrary.class), "createProxy"));
        createProxy.getModifiers().remove(Modifier.ABSTRACT);
        createProxy.renameArguments("library");
        createProxy.setReturnType(libraryTypeMirror);
        createProxy.createBuilder().startReturn().startNew(proxyClass.asType()).string("library").end().end();
        genClass.add(createProxy);

        for (MessageObjects message : methods) {
            CodeExecutableElement executeImpl = proxyClass.add(CodeExecutableElement.cloneNoAnnotations(message.model.getExecutable()));
            removeAbstractModifiers(executeImpl);
            if (executeImpl.getReturnType().getKind() == TypeKind.TYPEVAR) {
                executeImpl.getAnnotationMirrors().add(createSuppressWarningsUnchecked());
            }
            executeImpl.renameArguments("receiver_");
            builder = executeImpl.createBuilder();

            boolean uncheckedCast = false;
            if (message.model.getName().equals(ACCEPTS)) {
                builder.startReturn().string("lib.accepts(receiver_)").end();
            } else {
                injectReceiverType(executeImpl, 0, model.getSignatureReceiverType());
                builder.startTryBlock();
                builder.startReturn();
                if (ElementUtils.needsCastTo(context.getType(Object.class), executeImpl.getReturnType())) {
                    if (ElementUtils.hasGenericTypes(executeImpl.getReturnType())) {
                        uncheckedCast = true;
                    }
                    builder.cast(executeImpl.getReturnType());
                }

                builder.startCall("lib", "send").string("receiver_").field(null, message.messageField);
                for (VariableElement param : executeImpl.getParameters().subList(1, executeImpl.getParameters().size())) {
                    builder.string(param.getSimpleName().toString());
                }
                builder.end();
                builder.end();
                List<TypeMirror> exceptionTypes = new ArrayList<>(executeImpl.getThrownTypes());
                TypeMirror runtimeException = context.getType(RuntimeException.class);
                exceptionTypes.add(runtimeException);

                // reduce exception types
                Set<TypeMirror> remove = new HashSet<>();
                outer: for (TypeMirror type1 : exceptionTypes) {
                    for (TypeMirror type2 : exceptionTypes) {
                        if (type1 != type2 && ElementUtils.isAssignable(type1, type2)) {
                            remove.add(type1);
                            continue outer;
                        }
                    }
                }
                exceptionTypes.removeAll(remove);
                builder.end().startCatchBlock(exceptionTypes.toArray(new TypeMirror[0]), "e_");
                builder.startThrow().string("e_").end();
                builder.end().startCatchBlock(context.getType(Exception.class), "e_");
                builder.tree(GeneratorUtils.createTransferToInterpreter());
                builder.startThrow().startNew(context.getType(AssertionError.class)).end().end();
                builder.end();
            }
            if (uncheckedCast) {
                GeneratorUtils.mergeSupressWarnings(executeImpl, "unchecked");
            }
        }

        genClass.add(createGenericDispatch(methods, messageClass));

        // UncachedDispatch
        final CodeTypeElement uncachedDispatch = createClass(model, null, modifiers(PRIVATE, STATIC, FINAL), "UncachedDispatch", libraryTypeMirror);
        CodeExecutableElement getCost = uncachedDispatch.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Node.class), "getCost")));
        getCost.createBuilder().startReturn().staticReference(ElementUtils.findVariableElement(context.getDeclaredType(NodeCost.class), "MEGAMORPHIC")).end();
        for (MessageObjects message : methods) {
            CodeExecutableElement execute = uncachedDispatch.add(CodeExecutableElement.cloneNoAnnotations(message.model.getExecutable()));
            execute.renameArguments("receiver_");
            removeAbstractModifiers(execute);
            builder = execute.createBuilder();
            if (message.model.getName().equals(ACCEPTS)) {
                builder.returnTrue();
            } else {
                builder.startReturn().startCall("INSTANCE.getUncached(receiver_)", execute.getSimpleName().toString());
                for (VariableElement var : execute.getParameters()) {
                    builder.string(var.getSimpleName().toString());
                }
                builder.end().end();
            }
        }

        CodeExecutableElement isAdoptable = uncachedDispatch.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Node.class), "isAdoptable")));
        isAdoptable.createBuilder().returnFalse();

        genClass.add(uncachedDispatch);

        // CachedDispatch
        final CodeTypeElement cachedDispatch = createClass(model, null, modifiers(PRIVATE, ABSTRACT, STATIC), "CachedDispatch", libraryTypeMirror);
        CodeExecutableElement getLimit = cachedDispatch.add(new CodeExecutableElement(modifiers(ABSTRACT), context.getType(int.class), "getLimit"));
        CodeVariableElement libraryVar = cachedDispatch.add(new CodeVariableElement(modifiers(), libraryTypeMirror, "library"));
        libraryVar.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Child.class)));
        CodeVariableElement nextField = cachedDispatch.add(new CodeVariableElement(modifiers(), cachedDispatch.asType(), "next"));
        nextField.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Child.class)));
        cachedDispatch.add(GeneratorUtils.createConstructorUsingFields(modifiers(), cachedDispatch));
        for (MessageObjects message : methods) {
            CodeExecutableElement execute = cachedDispatch.add(CodeExecutableElement.cloneNoAnnotations(message.model.getExecutable()));
            execute.renameArguments("receiver_");
            removeAbstractModifiers(execute);
            builder = execute.createBuilder();
            if (message.model.getName().equals(ACCEPTS)) {
                builder.returnTrue();
            } else {
                builder.startDoBlock();
                builder.declaration(cachedDispatch.asType(), "current", "this");
                builder.startDoBlock();
                builder.declaration(libraryTypeMirror, "thisLibrary", "current.library");
                builder.startIf().string("thisLibrary != null && thisLibrary.accepts(receiver_)").end().startBlock();
                builder.startReturn().startCall("thisLibrary", execute.getSimpleName().toString());
                for (VariableElement var : execute.getParameters()) {
                    builder.string(var.getSimpleName().toString());
                }
                builder.end().end();
                builder.end(); // if block
                builder.statement("current = current.next");
                builder.end().startDoWhile().string("current != null").end().end();
                builder.startStatement().startStaticCall(context.getType(CompilerDirectives.class), "transferToInterpreterAndInvalidate").end().end();
                builder.statement("specialize(receiver_)");
                builder.end().startDoWhile().string("true").end();
                builder.end();
            }
        }

        // CachedDispatch.Next
        final CodeTypeElement cachedDispatchNext = createClass(model, null, modifiers(PRIVATE, STATIC, FINAL), "CachedDispatchNext", cachedDispatch.asType());
        cachedDispatchNext.add(GeneratorUtils.createConstructorUsingFields(modifiers(), cachedDispatchNext));
        CodeExecutableElement getLimitNext = cachedDispatchNext.add(CodeExecutableElement.clone(getLimit));
        removeAbstractModifiers(getLimitNext);
        builder = getLimitNext.createBuilder();
        builder.startThrow().startNew(context.getType(AssertionError.class)).end().end();
        genClass.add(cachedDispatchNext);

        DeclaredType nodeCost = context.getDeclaredType(NodeCost.class);
        getCost = cachedDispatchNext.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Node.class), "getCost")));
        getCost.createBuilder().startReturn().staticReference(ElementUtils.findVariableElement(nodeCost, "NONE")).end();

        // specialize
        CodeExecutableElement execute = cachedDispatch.add(new CodeExecutableElement(modifiers(PRIVATE), context.getType(void.class), "specialize"));
        execute.addParameter(new CodeVariableElement(model.getSignatureReceiverType(), "receiver_"));
        builder = execute.createBuilder();
        builder.declaration(cachedDispatch.asType(), "current", "this");
        builder.declaration(libraryTypeMirror, "thisLibrary", "current.library");
        builder.startIf().string("thisLibrary == null").end().startBlock();
        builder.statement("this.library = insert(INSTANCE.createCached(receiver_))");
        builder.end().startElseBlock();
        builder.declaration(context.getType(Lock.class), "lock", "getLock()");
        builder.statement("lock.lock()");
        builder.startTryBlock();
        builder.declaration("int", "count", "0");
        builder.startDoBlock();
        builder.declaration(libraryTypeMirror, "currentLibrary", "current.library");
        builder.startIf().string("currentLibrary != null && currentLibrary.accepts(receiver_)").end().startBlock();
        builder.returnStatement();
        builder.end();
        builder.statement("count++");
        builder.statement("current = current.next");
        builder.end().startDoWhile().string("current != null").end();
        builder.startIf().string("count >= getLimit()").end().startBlock();
        builder.statement("this.library = INSTANCE.getUncachedDispatch()");
        builder.statement("this.next = null");
        builder.end().startElseBlock();
        builder.startStatement().string("this.next = insert(");
        builder.startNew(cachedDispatchNext.asType()).string("INSTANCE.createCached(receiver_)").string("next").end();
        builder.string(")");
        builder.end(); // statement
        builder.end();
        builder.end();
        builder.end().startFinallyBlock(); // try
        builder.statement("lock.unlock()");
        builder.end();
        builder.end();

        // CacheDispatch.First
        final CodeTypeElement cachedDispatchFirst = createClass(model, null, modifiers(PRIVATE, STATIC, FINAL), "CachedDispatchFirst", cachedDispatch.asType());
        CodeVariableElement limit = cachedDispatchFirst.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), context.getType(int.class), "limit_"));
        cachedDispatchFirst.add(GeneratorUtils.createConstructorUsingFields(modifiers(), cachedDispatchFirst));

        CodeExecutableElement getLimitFirst = cachedDispatchFirst.add(CodeExecutableElement.clone(getLimit));
        removeAbstractModifiers(getLimitFirst);
        getLimitFirst.createBuilder().startReturn().string("this.", limit.getName()).end();
        genClass.add(cachedDispatchFirst);

        getCost = cachedDispatchFirst.add(CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(Node.class), "getCost")));
        builder = getCost.createBuilder();
        builder.startIf().string("this.library == INSTANCE.getUncachedDispatch()").end().startBlock();
        builder.startReturn().staticReference(ElementUtils.findVariableElement(nodeCost, "MEGAMORPHIC")).end();
        builder.end();
        builder.declaration(cachedDispatch.asType(), "current", "this");
        builder.statement("int count = 0");
        builder.startDoBlock();
        builder.startIf().string("current.library != null").end().startBlock();
        builder.statement("count++");
        builder.end();
        builder.statement("current = current.next");
        builder.end().startDoWhile().string("current != null").end().end();
        builder.startReturn().startStaticCall(nodeCost, "fromCount").string("count").end().end();

        genClass.add(cachedDispatch);

        CodeExecutableElement createCachedDispatch = CodeExecutableElement.clone(ElementUtils.findExecutableElement(context.getDeclaredType(ResolvedLibrary.class), "createCachedDispatchImpl"));
        createCachedDispatch.getModifiers().remove(Modifier.ABSTRACT);
        createCachedDispatch.setReturnType(libraryTypeMirror);
        createCachedDispatch.renameArguments("limit");
        builder = createCachedDispatch.createBuilder();
        builder.startReturn().startNew(cachedDispatchFirst.asType()).string("null").string("null").string("limit").end().end();
        genClass.add(createCachedDispatch);

        final CodeExecutableElement implConstructor = new CodeExecutableElement(modifiers(PRIVATE), null, genClass.getSimpleName().toString());
        genClass.add(implConstructor);
        builder = implConstructor.createBuilder();
        builder.startStatement();
        builder.startSuperCall().typeLiteral(model.getTemplateType().asType());
        builder.startStaticCall(context.getType(Collections.class), "unmodifiableList");
        builder.startStaticCall(context.getType(Arrays.class), "asList");
        for (MessageObjects message : methods) {
            if (message.messageField != null) {
                builder.field(null, message.messageField);
            }
        }
        builder.end().end(); // unmodfiiableList, asList
        builder.startNew(uncachedDispatch.asType()).end();
        builder.end(); // superCall
        builder.end(); // statement
        return Arrays.asList(genClass);
    }

    private TypeMirror[] crateDefaultImpl(CodeTypeElement genClass, List<MessageObjects> methods) {
        TypeMirror libraryTypeMirror = model.getTemplateType().asType();
        DeclaredType resolvedLibraryType = new CodeTypeMirror.DeclaredCodeTypeMirror(context.getTypeElement(ResolvedExports.class), Arrays.asList(libraryTypeMirror));
        CodeTreeBuilder builder;
        CodeTypeElement defaultClass = createClass(model, null, modifiers(PRIVATE, STATIC), "Default", libraryTypeMirror);
        defaultClass.add(new CodeVariableElement(modifiers(PRIVATE), context.getType(Class.class), "receiverClass"));
        genClass.add(defaultClass);
        CodeExecutableElement constructor = new CodeExecutableElement(modifiers(PRIVATE), null, "Default");
        constructor.getParameters().add(new CodeVariableElement(context.getType(Object.class), "receiver"));
        builder = constructor.createBuilder();
        builder.statement("this.receiverClass = receiver.getClass()");
        defaultClass.add(constructor);

        CodeExecutableElement accept = CodeExecutableElement.cloneNoAnnotations(ElementUtils.findExecutableElement(context.getDeclaredType(Library.class), "accepts"));
        accept.getModifiers().remove(Modifier.ABSTRACT);
        accept.renameArguments("receiver");
        builder = accept.createBuilder();
        builder.startReturn().string("receiverClass == receiver.getClass()").end();

        defaultClass.add(accept);

        // default fallback
        for (MessageObjects message : methods) {
            if (!message.model.getExecutable().getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }
            if (message.model.getName().equals(ACCEPTS)) {
                continue;
            }
            CodeExecutableElement cached = defaultClass.add(CodeExecutableElement.cloneNoAnnotations(message.model.getExecutable()));
            cached.renameArguments("receiver");
            removeAbstractModifiers(cached);
            builder = cached.createBuilder();
            builder.tree(GeneratorUtils.createTransferToInterpreter());
            builder.startThrow().startNew(context.getType(AbstractMethodError.class));
            builder.startGroup();
            builder.doubleQuote("Message '" + ElementUtils.getSimpleName(model.getTemplateType()) + "." + message.model.getName() + "' not implemented for Java type ").string(
                            " + receiver.getClass().getName()");
            builder.end();
            builder.end().end();
        }

        // DefaultGen
        CodeTypeElement defaultGen = createClass(model, null, modifiers(PRIVATE, STATIC), "DefaultGen", resolvedLibraryType);
        constructor = new CodeExecutableElement(modifiers(PRIVATE), null, "DefaultGen");
        builder = constructor.createBuilder();
        builder.startStatement().startSuperCall().typeLiteral(libraryTypeMirror).end().end().end();
        defaultGen.add(constructor);

        CodeExecutableElement createUncached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(resolvedLibraryType, "createUncached"));
        createUncached.setReturnType(libraryTypeMirror);
        createUncached.getModifiers().remove(Modifier.ABSTRACT);
        createUncached.renameArguments("receiver");
        builder = createUncached.createBuilder();
        builder.startReturn().startNew(defaultClass.asType()).string("receiver").end().end();
        defaultGen.add(createUncached);

        CodeExecutableElement createCached = CodeExecutableElement.clone(ElementUtils.findExecutableElement(resolvedLibraryType, "createCached"));
        createCached.setReturnType(libraryTypeMirror);
        createCached.getModifiers().remove(Modifier.ABSTRACT);
        createCached.renameArguments("receiver");
        builder = createCached.createBuilder();
        builder.startReturn().startNew(defaultClass.asType()).string("receiver").end().end();
        defaultGen.add(createCached);
        genClass.add(defaultGen);

        return new TypeMirror[]{defaultClass.asType(), defaultGen.asType()};
    }

    private CodeAnnotationMirror createSuppressWarningsUnchecked() {
        CodeAnnotationMirror suppressWarnings = new CodeAnnotationMirror(context.getDeclaredType(SuppressWarnings.class));
        suppressWarnings.setElementValue(suppressWarnings.findExecutableElement("value"), new CodeAnnotationValue(Arrays.asList(new CodeAnnotationValue("unchecked"))));
        return suppressWarnings;
    }

    private static void injectReceiverType(CodeExecutableElement method, int receiverIndex, TypeMirror type) {
        if (type == null) {
            throw new AssertionError();
        }
        CodeVariableElement receiverParameter = (CodeVariableElement) method.getParameters().get(receiverIndex);
        TypeParameterElement foundParameter = null;
        int foundIndex = 0;
        if (receiverParameter.asType().getKind() == TypeKind.TYPEVAR) {
            for (TypeParameterElement typeParameter : method.getTypeParameters()) {
                if (ElementUtils.elementEquals(((TypeVariable) receiverParameter.asType()).asElement(), typeParameter)) {
                    foundParameter = typeParameter;
                    break;
                }
                foundIndex++;
            }
        }
        if (foundParameter != null) {
            CodeTypeParameterElement newParameter = new CodeTypeParameterElement(foundParameter.getSimpleName());
            newParameter.getBounds().add(type);
            method.getTypeParameters().set(foundIndex, newParameter);
        } else {
            receiverParameter.setType(type);
        }
    }

    private static void removeAbstractModifiers(CodeExecutableElement uncachedImpl) {
        uncachedImpl.getModifiers().remove(ABSTRACT);
        uncachedImpl.getModifiers().remove(Modifier.DEFAULT);
    }

    private static String createConstantName(String name) {
        StringBuilder newName = new StringBuilder();
        boolean wasLowerCase = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (wasLowerCase) {
                    newName.append('_');
                }
                newName.append(c);
            } else {
                wasLowerCase = true;
                newName.append(Character.toUpperCase(c));
            }
        }
        return newName.toString();
    }

    private CodeExecutableElement createGenericDispatch(List<MessageObjects> methods, CodeTypeElement messageClass) {
        CodeTreeBuilder builder;
        CodeExecutableElement reflectionGenericDispatch = GeneratorUtils.override(ResolvedLibrary.class, "genericDispatch");
        reflectionGenericDispatch.getParameters().set(0, new CodeVariableElement(context.getType(Library.class), "library"));
        reflectionGenericDispatch.renameArguments("originalLib", "receiver", "message", "args", "offset");
        reflectionGenericDispatch.getModifiers().remove(ABSTRACT);
        builder = reflectionGenericDispatch.createBuilder();
        builder.declaration(model.getTemplateType().asType(), "lib", builder.create().cast(model.getTemplateType().asType()).string("originalLib"));
        builder.declaration(messageClass.asType(), "messageImpl", builder.create().cast(messageClass.asType()).string("message").build());
        builder.startIf().string("messageImpl.getParameterTypes().size() - 1 != args.length - offset").end().startBlock();
        builder.startStatement().startStaticCall(context.getType(CompilerDirectives.class), "transferToInterpreter").end().end();
        builder.startThrow().startNew(context.getType(IllegalArgumentException.class)).doubleQuote("Invalid number of arguments.").end().end();
        builder.end();
        boolean uncheckedCast = false;
        builder.startSwitch().string("messageImpl.index").end().startBlock();
        for (MessageObjects message : methods) {
            if (message.model.getName().equals(ACCEPTS)) {
                continue;
            }
            builder.startCase();
            builder.string(String.valueOf(message.messageIndex)).end();
            builder.startIndention();
            if (ElementUtils.isVoid(message.model.getExecutable().getReturnType())) {
                builder.startStatement();
            } else {
                builder.startReturn();
            }
            builder.startCall("lib", message.model.getName());
            builder.startGroup();
            if (!ElementUtils.typeEquals(context.getType(Object.class), model.getSignatureReceiverType())) {
                if (ElementUtils.hasGenericTypes(model.getSignatureReceiverType())) {
                    uncheckedCast = true;
                }
                builder.cast(model.getSignatureReceiverType());
            }
            builder.string("receiver");
            builder.end();
            int argumentIndex = 0;
            List<? extends VariableElement> parameters = message.model.getExecutable().getParameters();
            for (VariableElement parameter : parameters.subList(1, parameters.size())) {
                builder.startGroup();
                TypeMirror type = parameter.asType();
                if (!ElementUtils.typeEquals(context.getType(Object.class), type)) {
                    if (ElementUtils.hasGenericTypes(type)) {
                        uncheckedCast = true;
                    }
                    builder.cast(type);
                }
                if (argumentIndex == 0) {
                    builder.string("args[offset]");
                } else {
                    builder.string("args[offset + ", String.valueOf(argumentIndex), "]");
                }
                builder.end();
                argumentIndex++;
            }
            builder.end().end();
            if (ElementUtils.isVoid(message.model.getExecutable().getReturnType())) {
                builder.statement("return null");
            }
            builder.end();
        }
        builder.end();
        builder.startStatement().startStaticCall(context.getType(CompilerDirectives.class), "transferToInterpreter").end().end();
        builder.startThrow().startNew(context.getType(AbstractMethodError.class)).string("message.toString()").end().end();

        if (uncheckedCast) {
            GeneratorUtils.mergeSupressWarnings(reflectionGenericDispatch, "unchecked");
        }

        return reflectionGenericDispatch;
    }

    static String createGenTypeName(LibraryData type) {
        return ElementUtils.firstLetterUpperCase(type.getTemplateType().getSimpleName().toString()) + "Gen";
    }

}
