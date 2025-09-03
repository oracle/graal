/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import org.graalvm.nativebridge.processor.AbstractBridgeParser.MarshallerData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.CacheData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.ServiceDefinitionData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.MethodData;
import org.graalvm.nativebridge.processor.CodeBuilder.Parameter;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

final class ProcessToProcessServiceGenerator extends AbstractServiceGenerator {

    private static final String END_POINT_SIMPLE_NAME = "ProcessToProcessEndPoint";
    private static final String START_POINT_SIMPLE_NAME = "ProcessToProcessStartPoint";
    private static final String START_POINT_RESULT_VARIABLE = "startPointResult";
    private static final String END_POINT_RESULT_VARIABLE = "endPointResult";
    private static final String SERVICE_SCOPE_FIELD = "SERVICE_SCOPE";

    private final ProcessToProcessTypeCache typeCache;
    private final FactoryMethodInfo factoryMethod;

    ProcessToProcessServiceGenerator(ProcessToProcessServiceParser parser, ProcessToProcessTypeCache typeCache, ServiceDefinitionData definitionData) {
        super(parser, typeCache, definitionData);
        this.typeCache = typeCache;
        this.factoryMethod = resolveFactoryMethod(FACTORY_METHOD_NAME, START_POINT_SIMPLE_NAME, END_POINT_SIMPLE_NAME);
    }

    @Override
    void generateCommonCustomDispatchFactoryReturn(CodeBuilder builder) {
        builder.lineStart("return ").newInstance(factoryMethod.startPointSimpleName, parameterNames(factoryMethod.constructorParameters)).lineEnd(";");
    }

    @Override
    void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName) {
        if (!getDefinition().hasCustomDispatch()) {
            builder.lineEnd("");
            generateStartPointFactory(builder, factoryMethod);
        }
    }

    @Override
    void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        if (generateExportedMethodIdFields(builder)) {
            builder.lineEnd("");
        }
        generateDispatchMethod(builder);
        builder.lineEnd("");
        generateProcessStartPoint(builder);
        builder.lineEnd("");
        generateProcessEndPoint(builder);
    }

    private void generateProcessStartPoint(CodeBuilder builder) {
        CacheSnippet cacheSnippets = cacheSnippets();

        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), factoryMethod.startPointSimpleName,
                        getDefinition().annotatedType, List.of());
        builder.indent();
        builder.lineEnd("");
        CharSequence definitionClassLiteral = new CodeBuilder(builder).classLiteral(getDefinition().annotatedType).build();
        CharSequence factoryServiceGen = new CodeBuilder(builder).write(getDefinition().factory).write("Gen").build();
        builder.lineStart().writeModifiers(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)).space().write(types.getPrimitiveType(TypeKind.INT)).space().//
                        write(SERVICE_SCOPE_FIELD).write(" = ").invoke(factoryServiceGen, "lookupServiceId", definitionClassLiteral).//
                        write(" << 16").//
                        lineEnd(";");
        if (generateMarshallerFields(builder, true, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)) {
            builder.line("");
        }
        builder.methodStart(EnumSet.of(Modifier.STATIC), null,
                        null, List.of(), List.of());
        builder.indent();
        builder.lineStart().write(typeCache.marshallerConfig).write(" marshallerConfig = ").invokeStatic(getDefinition().marshallerConfig, "getInstance").lineEnd(";");
        generateMarshallerLookups(builder, true);
        builder.dedent();
        builder.line("}");
        builder.lineEnd("");
        CharSequence throwableMarshallerVar = getDefinition().throwableMarshaller.name;

        if (!getDefinition().hasCustomDispatch()) {
            generatePeerField(builder, getDefinition().peerType, PEER_FIELD);
            builder.line("");
        }

        if (generateCacheFields(builder, cacheSnippets)) {
            builder.lineEnd("");
        }

        builder.methodStart(Set.of(), factoryMethod.startPointSimpleName, null,
                        factoryMethod.constructorParameters, List.of());
        builder.indent();
        builder.lineStart().call("super", parameterNames(factoryMethod.superCallParameters)).lineEnd(";");
        if (!getDefinition().hasCustomDispatch()) {
            builder.lineStart().memberSelect("this", PEER_FIELD, false).write(" = ").write(PEER_FIELD).lineEnd(";");
        }
        generateCacheFieldsInit(builder, cacheSnippets);
        builder.dedent();
        builder.line("}");

        if (!getDefinition().hasCustomDispatch()) {
            builder.line("");
            generateGetPeerMethod(builder, getDefinition().peerType, PEER_FIELD);
            builder.line("");
            generateSetPeerMethod(builder, getDefinition().peerType, PEER_FIELD);
        }

        for (MethodData methodData : getDefinition().toGenerate) {
            generateProcessStartMethod(builder, cacheSnippets, methodData, throwableMarshallerVar);
        }

        builder.dedent();
        builder.classEnd();
    }

    private void generateProcessStartMethod(CodeBuilder builder, CacheSnippet cacheSnippets, MethodData methodData, CharSequence throwableMarshallerVar) {
        builder.line("");
        overrideMethod(builder, methodData);
        builder.indent();
        CharSequence receiver;
        CharSequence receiverProcessPeer;
        int nonReceiverParameterStart;
        if (getDefinition().hasCustomDispatch()) {
            receiver = methodData.element.getParameters().get(0).getSimpleName();
            receiverProcessPeer = "processPeer";
            nonReceiverParameterStart = 1;
        } else {
            receiver = "this";
            receiverProcessPeer = PEER_FIELD;
            nonReceiverParameterStart = 0;
        }
        CharSequence processIsolateVar = "processIsolate";
        CharSequence scopeVar = "processIsolateThread";
        CharSequence binaryOutputVar = "marshalledParametersOutput";
        CharSequence binaryInputVar = "marshalledResult";

        List<? extends VariableElement> formalParameters = methodData.element.getParameters();
        List<? extends TypeMirror> formalParameterTypes = methodData.type.getParameterTypes();
        boolean hasOutParameters = false;
        List<MarshalledParameter> allParameters = new ArrayList<>();
        allParameters.add(new MarshalledParameter(receiverProcessPeer, typeCache.processPeer, false, MarshallerData.reference(typeCache.processPeer, false, true, null, null)));
        for (int i = nonReceiverParameterStart; i < formalParameters.size(); i++) {
            VariableElement formalParameter = formalParameters.get(i);
            TypeMirror formalParameterType = formalParameterTypes.get(i);
            MarshallerData marshaller = methodData.getParameterMarshaller(i);
            allParameters.add(new MarshalledParameter(formalParameter.getSimpleName(), formalParameterType, false, marshaller));
            if (isOutParameter(marshaller, formalParameterType)) {
                hasOutParameters = true;
            }
        }

        CacheData cacheData = methodData.cachedData;
        if (cacheData != null) {
            if (hasOutParameters) {
                throw new IllegalStateException("Idempotent cannot be used with Out parameters.");
            }
            String cacheFieldName = cacheData.cacheFieldName;
            String resFieldName = cacheFieldName + "Result";
            builder.lineStart().write(cacheData.cacheEntryType).space().write(resFieldName).write(" = ").write(cacheSnippets.readCache(builder, cacheFieldName, receiver)).lineEnd(";");
            builder.lineStart("if (").write(resFieldName).lineEnd(" == null) {");
            builder.indent();
            generateEnter(builder, receiver, receiverProcessPeer, processIsolateVar, scopeVar);
            generateByteArrayBinaryOutputInit(builder, binaryOutputVar, allParameters);
            generateMarshallParameters(builder, methodData, binaryOutputVar, allParameters);
            builder.lineStart().write(typeCache.binaryInput).space().write(binaryInputVar).lineEnd(";");
            builder.line("try {");
            builder.indent();
            generateSendReceive(builder, scopeVar, binaryOutputVar, binaryInputVar);
            builder.dedent();
            generateStartPointExceptionHandlers(builder, methodData, receiver, processIsolateVar, throwableMarshallerVar);
            generateUnmarshallResult(builder, methodData, processIsolateVar, binaryInputVar, resFieldName, false);
            builder.lineStart().write(cacheSnippets.writeCache(builder, cacheFieldName, receiver, resFieldName)).lineEnd(";");
            generateLeave(builder, scopeVar);
            builder.dedent();
            builder.line("}");
            builder.lineStart("return ").write(resFieldName).lineEnd(";");
        } else {
            generateEnter(builder, receiver, receiverProcessPeer, processIsolateVar, scopeVar);
            generateByteArrayBinaryOutputInit(builder, binaryOutputVar, allParameters);
            generateMarshallParameters(builder, methodData, binaryOutputVar, allParameters);
            boolean voidReturnType = methodData.type.getReturnType().getKind() == TypeKind.VOID;
            boolean needsBinaryInput = !voidReturnType || hasOutParameters;
            if (needsBinaryInput) {
                builder.lineStart().write(typeCache.binaryInput).space().write(binaryInputVar).lineEnd(";");
            } else {
                binaryInputVar = null;
            }
            builder.line("try {");
            builder.indent();
            generateSendReceive(builder, scopeVar, binaryOutputVar, binaryInputVar);
            builder.dedent();
            generateStartPointExceptionHandlers(builder, methodData, receiver, processIsolateVar, throwableMarshallerVar);
            CharSequence resultVar = null;
            if (!voidReturnType) {
                resultVar = generateUnmarshallResult(builder, methodData, processIsolateVar, binaryInputVar, null, hasOutParameters);
            }
            if (hasOutParameters) {
                assert resultVar != null;
                generateUnmarshallOutParameters(builder, processIsolateVar, binaryInputVar, resultVar, allParameters);
            }
            if (resultVar != null) {
                builder.lineStart("return").space().write(resultVar).lineEnd(";");
            }
            generateLeave(builder, scopeVar);
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateEnter(CodeBuilder builder, CharSequence receiver, CharSequence receiverProcessPeer, CharSequence processIsolateVar, CharSequence scopeVar) {
        if (getDefinition().hasCustomDispatch()) {
            CharSequence foreignObject = new CodeBuilder(builder).cast(typeCache.foreignObject, receiver, true).build();
            CharSequence getPeer = new CodeBuilder(builder).invoke(foreignObject, "getPeer").build();
            builder.lineStart().write(typeCache.processPeer).space().write(receiverProcessPeer).write(" = ").cast(typeCache.processPeer, getPeer).lineEnd(";");
        }
        builder.lineStart().write(typeCache.processIsolate).space().write(processIsolateVar).write(" = ").invoke(receiverProcessPeer, "getIsolate").lineEnd(";");
        builder.lineStart().write(typeCache.processIsolateThread).space().write(scopeVar).write(" = ").invoke(processIsolateVar, "enter").lineEnd(";");
        builder.line("try {");
        builder.indent();
    }

    private static void generateLeave(CodeBuilder builder, CharSequence scopeVar) {
        builder.dedent();
        builder.line("} finally {");
        builder.indent();
        builder.lineStart().invoke(scopeVar, "leave").lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    private void generateByteArrayBinaryOutputInit(CodeBuilder builder, CharSequence binaryOutVar, List<MarshalledParameter> parameters) {
        CharSequence sizeEstimateVar = "marshalledParametersSizeEstimate";
        List<MarshalledParameter> methodIdAndParameters = new ArrayList<>(1 + parameters.size());
        /*
         * For process isolate RPC, it's advisable to overestimate the buffer size by accounting for
         * the result size as well.
         */
        TypeMirror intType = types.getPrimitiveType(TypeKind.INT);
        methodIdAndParameters.add(new MarshalledParameter("_", intType, false, MarshallerData.NO_MARSHALLER));
        methodIdAndParameters.addAll(parameters);
        generateSizeEstimate(builder, sizeEstimateVar, methodIdAndParameters, true, false);
        builder.lineStart().write(typeCache.byteArrayBinaryOutput).space().write(binaryOutVar).write(" = ").invokeStatic(typeCache.byteArrayBinaryOutput, "create", sizeEstimateVar).lineEnd(";");
    }

    private void generateMarshallParameters(CodeBuilder builder, MethodData methodData, CharSequence binaryOutputVar, List<MarshalledParameter> parameters) {
        CharSequence id = new CodeBuilder(builder).write(SERVICE_SCOPE_FIELD).write(" | ").write(methodId(methodData)).build();
        builder.lineStart().invoke(binaryOutputVar, "writeInt", id).lineEnd(";");
        List<MarshalledParameter> deferredParameters = new ArrayList<>(parameters.size());
        for (MarshalledParameter parameter : parameters) {
            if (isDeferred(parameter)) {
                deferredParameters.add(parameter);
            } else {
                MarshallerSnippet.forData(this, parameter.marshallerData).marshallParameter(builder, parameter.parameterType, parameter.parameterName, binaryOutputVar);
            }
        }
        for (MarshalledParameter parameter : deferredParameters) {
            MarshallerSnippet.forData(this, parameter.marshallerData).marshallParameter(builder, parameter.parameterType, parameter.parameterName, binaryOutputVar);
        }
    }

    private static boolean isDeferred(MarshalledParameter parameter) {
        return Optional.ofNullable(parameter.marshallerData.in).or(() -> Optional.ofNullable(parameter.marshallerData.out)).map(ProcessToProcessServiceGenerator::resolveLength).isPresent();
    }

    private static void generateSendReceive(CodeBuilder builder, CharSequence scopeVar, CharSequence paramsVar, CharSequence resultVar) {
        builder.lineStart();
        if (resultVar != null) {
            builder.write(resultVar).write(" = ");
        }
        builder.invoke(scopeVar, "sendAndReceive", paramsVar).lineEnd(";");
    }

    private void generateStartPointExceptionHandlers(CodeBuilder builder, MethodData methodData, CharSequence receiverVar, CharSequence processIsolateVar, CharSequence throwableMarshallerVar) {
        CharSequence isolateDeathExceptionVar = "isolateDeathException";
        CharSequence foreignExceptionVar = "foreignException";
        if (methodData.isolateDeathHandler != null) {
            builder.lineStart("} catch (").write(typeCache.isolateDeathException).space().write(isolateDeathExceptionVar).lineEnd(") {");
            builder.indent();
            builder.lineStart().invokeStatic(methodData.isolateDeathHandler.handlerType(), "handleIsolateDeath", receiverVar, isolateDeathExceptionVar).lineEnd(";");
            builder.lineStart("throw ").newInstance(getTypeCache().assertionError, "\"Should not reach here.\"").lineEnd(";");
            builder.dedent();
        }
        builder.lineStart("} catch (").write(typeCache.foreignException).space().write(foreignExceptionVar).lineEnd(") {");
        builder.indent();
        builder.lineStart("throw ").invoke(foreignExceptionVar, "throwOriginalException", processIsolateVar, throwableMarshallerVar).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    private CharSequence generateUnmarshallResult(CodeBuilder builder, MethodData methodData, CharSequence processIsolateVar, CharSequence binaryInputVar, CharSequence existingResultVar,
                    boolean hasOutParameters) {
        MarshallerSnippet snippet = MarshallerSnippet.forData(this, methodData.getReturnTypeMarshaller());
        return snippet.unmarshallResult(builder, methodData.type.getReturnType(), processIsolateVar, binaryInputVar, existingResultVar, hasOutParameters);
    }

    private void generateUnmarshallOutParameters(CodeBuilder builder, CharSequence processIsolateVar, CharSequence binaryInputVar, CharSequence resultVar, List<MarshalledParameter> parameters) {
        for (MarshalledParameter parameter : parameters) {
            if (isOutParameter(parameter.marshallerData, parameter.parameterType)) {
                MarshallerSnippet.forData(this, parameter.marshallerData).unmarshallOutParameter(builder, parameter.parameterType, processIsolateVar, parameter.parameterName, binaryInputVar,
                                resultVar);
            }
        }
    }

    private static boolean isOutParameter(MarshallerData marshaller, TypeMirror type) {
        return isBinaryMarshallable(marshaller, type) && marshaller.out != null;
    }

    private static boolean isBinaryMarshallable(MarshallerData marshaller, TypeMirror type) {
        return marshaller.isCustom() || ((marshaller.isReference() || marshaller.isValue()) && type.getKind() == TypeKind.ARRAY);
    }

    private boolean generateExportedMethodIdFields(CodeBuilder builder) {
        boolean modified = false;
        Set<Modifier> modifiers = EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
        int id = 0;
        for (MethodData method : getDefinition().toGenerate) {
            String name = methodId(method);
            builder.lineStart().writeModifiers(modifiers).space().write(types.getPrimitiveType(TypeKind.INT)).space().write(name).write(" = ").write(Integer.toString(id++)).lineEnd(";");
            modified = true;
        }
        return modified;
    }

    private void generateDispatchMethod(CodeBuilder builder) {
        CharSequence messageIdVar = "messageId";
        CharSequence isolateVar = "processIsolate";
        CharSequence inputVar = "binaryInput";
        TypeMirror intType = types.getPrimitiveType(TypeKind.INT);
        builder.methodStart(Set.of(Modifier.STATIC), "dispatch", typeCache.binaryOutput,
                        List.of(CodeBuilder.newParameter(intType, messageIdVar),
                                        CodeBuilder.newParameter(typeCache.processIsolate, isolateVar),
                                        CodeBuilder.newParameter(typeCache.binaryInput, inputVar)),
                        List.of(typeCache.throwable));
        builder.indent();
        if (getDefinition().toGenerate.isEmpty()) {
            builder.lineStart("throw ").newInstance(typeCache.unsupportedOperationException, "\"No exported methods.\"").lineEnd(";");
        } else {
            builder.lineStart("return switch (").write(messageIdVar).write(" & 0xffff)").lineEnd(" {");
            builder.indent();
            for (MethodData method : getDefinition().toGenerate) {
                builder.lineStart("case ").write(methodId(method)).write(" -> ").//
                                invoke(END_POINT_SIMPLE_NAME, getParser().endPointMethodProvider.getEndPointMethodName(method), isolateVar, inputVar).//
                                lineEnd(";");
            }
            CharSequence message = new CodeBuilder(builder).stringLiteral("Unknown message id ").write(" + ").write(messageIdVar).build();
            builder.lineStart("default -> throw ").newInstance(typeCache.illegalArgumentException, message).lineEnd(";");
            builder.dedent();
            builder.line("};");
        }
        builder.dedent();
        builder.line("}");
    }

    private String methodId(MethodData method) {
        String name = getParser().endPointMethodProvider.getEndPointMethodName(method);
        StringBuilder upperCaseName = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i != 0 && Character.isUpperCase(c)) {
                upperCaseName.append("_");
            } else {
                c = Character.toUpperCase(c);
            }
            upperCaseName.append(c);
        }
        return String.format("%s_ID", upperCaseName);
    }

    private void generateProcessEndPoint(CodeBuilder builder) {
        builder.lineStart().annotation(typeCache.suppressWarnings, "unused").lineEnd("");
        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), factoryMethod.endPointSimpleName, null, List.of());
        builder.indent();

        if (!getDefinition().getUserCustomMarshallers().isEmpty()) {
            builder.line("");
            generateMarshallerFields(builder, false, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC);
            builder.line("");
            builder.methodStart(EnumSet.of(Modifier.STATIC), null,
                            null, List.of(), List.of());
            builder.indent();
            builder.lineStart().write(typeCache.marshallerConfig).write(" marshallerConfig = ").invokeStatic(getDefinition().marshallerConfig, "getInstance").lineEnd(";");
            generateMarshallerLookups(builder, false);
            builder.dedent();
            builder.line("}");
        }

        for (MethodData methodData : getDefinition().toGenerate) {
            generateProcessEndMethod(builder, methodData);
        }

        builder.dedent();
        builder.classEnd();
    }

    private void generateProcessEndMethod(CodeBuilder builder, MethodData methodData) {
        CharSequence isolateVar = "processIsolate";
        CharSequence binaryInputVar = "binaryInput";
        CharSequence binaryOutputVar = "binaryOutput";
        builder.line("");
        List<? extends VariableElement> parameters = methodData.element.getParameters();
        List<? extends TypeMirror> parameterTypes = methodData.type.getParameterTypes();
        Set<CharSequence> warnings = new TreeSet<>(Comparator.comparing(CharSequence::toString));
        if (!getDefinition().hasCustomDispatch() && Utilities.isParameterizedType(getDefinition().serviceType)) {
            warnings.add("unchecked");
        }
        for (int i = 0; i < parameterTypes.size(); i++) {
            warnings.addAll(MarshallerSnippet.forData(this, methodData.getParameterMarshaller(i)).getEndPointSuppressedWarnings(parameterTypes.get(i)));
        }
        builder.lineStart().annotation(typeCache.suppressWarnings, warnings.toArray(new CharSequence[0])).lineEnd("");
        List<Parameter> args = List.of(
                        CodeBuilder.newParameter(typeCache.processIsolate, isolateVar),
                        CodeBuilder.newParameter(typeCache.binaryInput, binaryInputVar));
        builder.methodStart(Set.of(Modifier.STATIC), getParser().endPointMethodProvider.getEndPointMethodName(methodData),
                        typeCache.binaryOutput, args, List.of(typeCache.throwable));
        builder.indent();
        int parameterStartIndex;
        CharSequence dispatchVar;
        CharSequence handle = new CodeBuilder(builder).invoke(binaryInputVar, "readLong").build();
        CharSequence[] actualParameters = new CharSequence[parameters.size()];
        if (getDefinition().hasCustomDispatch()) {
            parameterStartIndex = 1;
            CharSequence processPeer = "processPeer";
            dispatchVar = "dispatchObject";
            CharSequence receiverVar = "receiverObject";
            TypeMirror receiverType = getDefinition().customDispatchAccessor.getParameters().get(0).asType();
            CharSequence classLiteral = new CodeBuilder(builder).classLiteral(receiverType).build();
            builder.lineStart().write(receiverType).space().write(processPeer).write(" = ").invokeStatic(typeCache.referenceHandles, "resolve", handle, classLiteral).lineEnd(";");
            builder.lineStart().write(getDefinition().serviceType).space().write(dispatchVar).write(" = ").invokeStatic(getDefinition().annotatedType,
                            getDefinition().customDispatchAccessor.getSimpleName(), processPeer).lineEnd(";");
            builder.lineStart().write(typeCache.object).space().write(receiverVar).write(" = ").invokeStatic(getDefinition().annotatedType, getDefinition().customReceiverAccessor.getSimpleName(),
                            processPeer).lineEnd(";");
            actualParameters[0] = receiverVar;
        } else {
            parameterStartIndex = 0;
            dispatchVar = "receiverObject";
            CharSequence classLiteral = new CodeBuilder(builder).classLiteral(getDefinition().serviceType).build();
            builder.lineStart().write(getDefinition().serviceType).write(" receiverObject = ").invokeStatic(typeCache.referenceHandles, "resolve", handle, classLiteral).lineEnd(";");
        }
        List<MarshalledParameter> allParameters = new ArrayList<>();
        boolean hasOutParameters = false;
        for (int i = parameterStartIndex; i < parameters.size(); i++) {
            VariableElement formalParameter = parameters.get(i);
            TypeMirror formalParameterType = parameterTypes.get(i);
            MarshallerData marshaller = methodData.getParameterMarshaller(i);
            allParameters.add(new MarshalledParameter(formalParameter.getSimpleName(), formalParameterType, false, marshaller));
            actualParameters[i] = formalParameter.getSimpleName();
            if (isOutParameter(marshaller, formalParameterType)) {
                hasOutParameters = true;
            }
        }

        generateUnmarshallParameters(builder, isolateVar, binaryInputVar, allParameters);

        CharSequence methodName = methodData.receiverMethod != null ? methodData.receiverMethod : methodData.element.getSimpleName();
        TypeMirror returnType = methodData.type.getReturnType();
        boolean voidReturnType = returnType.getKind() == TypeKind.VOID;
        builder.lineStart();
        if (!voidReturnType) {
            builder.write(returnType).space().write(END_POINT_RESULT_VARIABLE).write(" = ");
        }
        builder.invoke(dispatchVar, methodName, actualParameters).lineEnd(";");

        if (!voidReturnType || hasOutParameters) {
            builder.lineStart().write(typeCache.binaryOutput).space().write(binaryOutputVar).write(" = ").invokeStatic(typeCache.binaryOutput, "claimBuffer", binaryInputVar).lineEnd(";");
            if (!voidReturnType) {
                generateMarshallResult(builder, methodData, END_POINT_RESULT_VARIABLE, binaryOutputVar);
            }
            if (hasOutParameters) {
                generateMarshallOutParameters(builder, binaryOutputVar, END_POINT_RESULT_VARIABLE, allParameters);
            }
            builder.lineStart("return ").write(binaryOutputVar).lineEnd(";");
        } else {
            builder.lineStart("return ").write("null").lineEnd(";");
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateUnmarshallParameters(CodeBuilder builder, CharSequence isolateVar, CharSequence binaryInputVar, List<MarshalledParameter> parameters) {
        List<MarshalledParameter> deferredParameters = new ArrayList<>();
        for (MarshalledParameter parameter : parameters) {
            if (isDeferred(parameter)) {
                deferredParameters.add(parameter);
            } else {
                MarshallerSnippet.forData(this, parameter.marshallerData).unmarshallParameter(builder, parameter.parameterType, parameter.parameterName, isolateVar, binaryInputVar);
            }
        }
        for (MarshalledParameter parameter : deferredParameters) {
            MarshallerSnippet.forData(this, parameter.marshallerData).unmarshallParameter(builder, parameter.parameterType, parameter.parameterName, isolateVar, binaryInputVar);
        }
    }

    private void generateMarshallResult(CodeBuilder builder, MethodData methodData, CharSequence resultVar, CharSequence binaryOutputVar) {
        MarshallerSnippet snippet = MarshallerSnippet.forData(this, methodData.getReturnTypeMarshaller());
        snippet.marshallResult(builder, methodData.type.getReturnType(), resultVar, binaryOutputVar);
    }

    private void generateMarshallOutParameters(CodeBuilder builder, CharSequence binaryOutputVar, CharSequence resultVar, List<MarshalledParameter> parameters) {
        for (MarshalledParameter parameter : parameters) {
            if (isOutParameter(parameter.marshallerData, parameter.parameterType)) {
                MarshallerSnippet.forData(this, parameter.marshallerData).marshallOutParameter(builder, parameter.parameterType, parameter.parameterName, binaryOutputVar, resultVar);
            }
        }
    }

    private abstract static class MarshallerSnippet extends AbstractMarshallerSnippet {

        final ProcessToProcessTypeCache typeCache;

        MarshallerSnippet(ProcessToProcessServiceGenerator generator, MarshallerData marshallerData) {
            super(marshallerData, generator.types, generator.typeCache);
            this.typeCache = generator.typeCache;
        }

        abstract void marshallParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar);

        abstract CharSequence unmarshallResult(CodeBuilder builder, TypeMirror resultType, CharSequence processIsolateVar, CharSequence binaryInputVar, CharSequence existingResultVar,
                        boolean hasOutParameters);

        abstract void unmarshallOutParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence processIsolateVar, CharSequence formalParameter, CharSequence binaryInputVar,
                        CharSequence resultVar);

        abstract void unmarshallParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence isolateVar, CharSequence binaryInputVar);

        abstract void marshallResult(CodeBuilder builder, TypeMirror resultType, CharSequence resultVar, CharSequence binaryOutputVar);

        abstract void marshallOutParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar, CharSequence resultVar);

        abstract Collection<CharSequence> getEndPointSuppressedWarnings(TypeMirror pasrameterType);

        private static final class DirectSnippet extends MarshallerSnippet {

            DirectSnippet(ProcessToProcessServiceGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
            }

            @Override
            void marshallParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar) {
                writeValue(builder, parameterType, formalParameter, binaryOutputVar);
            }

            private void writeValue(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar) {
                CharSequence method = lookupDirectSnippetWriteMethod(parameterType);
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                    if (componentType.getKind().isPrimitive()) {
                        builder.lineStart().write("if (").write(formalParameter).write(" != null) ").lineEnd("{");
                        builder.indent();
                        CharSequence length = new CodeBuilder(builder).memberSelect(formalParameter, "length", false).build();
                        boolean hasExplicitLength = false;
                        if (marshallerData.in != null) {
                            CharSequence l;
                            if ((l = resolveLength(marshallerData.in)) != null) {
                                hasExplicitLength = true;
                                builder.lineStart().invoke(binaryOutputVar, "writeBoolean", "true").lineEnd(";");
                                length = l;
                            } else {
                                builder.lineStart().invoke(binaryOutputVar, "writeInt", length).lineEnd(";");
                            }
                            CharSequence offset = "0";
                            CharSequence o;
                            if ((o = resolveOffset(marshallerData.in)) != null) {
                                offset = o;
                            }
                            builder.lineStart().invoke(binaryOutputVar, "write", formalParameter, offset, length).lineEnd(";");
                        } else if (marshallerData.out != null) {
                            if (resolveLength(marshallerData.out) != null) {
                                hasExplicitLength = true;
                                builder.lineStart().invoke(binaryOutputVar, "writeBoolean", "true").lineEnd(";");
                            } else {
                                builder.lineStart().invoke(binaryOutputVar, "writeInt", length).lineEnd(";");
                            }
                        } else {
                            builder.lineStart().invoke(binaryOutputVar, "writeInt", length).lineEnd(";");
                            builder.lineStart().invoke(binaryOutputVar, "write", formalParameter, "0", length).lineEnd(";");
                        }
                        builder.dedent();
                        builder.line("} else {");
                        builder.indent();
                        if (hasExplicitLength) {
                            builder.lineStart().invoke(binaryOutputVar, "writeBoolean", "false").lineEnd(";");
                        } else {
                            builder.lineStart().invoke(binaryOutputVar, "writeInt", "-1").lineEnd(";");
                        }
                        builder.dedent();
                        builder.line("}");
                    } else {
                        throw unsupported(parameterType);
                    }
                } else {
                    builder.lineStart().invoke(binaryOutputVar, method, formalParameter).lineEnd(";");
                }
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder builder, TypeMirror resultType, CharSequence processIsolateVar, CharSequence binaryInputVar, CharSequence existingResultVar,
                            boolean hasOutParameters) {
                CharSequence method = lookupDirectSnippetReadMethod(resultType);
                if (resultType.getKind() == TypeKind.ARRAY) {
                    TypeMirror componentType = ((ArrayType) resultType).getComponentType();
                    if (componentType.getKind().isPrimitive()) {
                        CharSequence useResultVar = existingResultVar != null ? existingResultVar : START_POINT_RESULT_VARIABLE;
                        CharSequence resultLengthVar = useResultVar + "Length";
                        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(resultLengthVar).write(" = ").invoke(binaryInputVar, "readInt").lineEnd(";");
                        if (existingResultVar == null) {
                            builder.lineStart().write(resultType).space().write(useResultVar).lineEnd(";");
                        }
                        builder.lineStart().write("if (").write(resultLengthVar).write(" != -1) ").lineEnd("{");
                        builder.indent();
                        builder.lineStart().write(useResultVar).write(" = ").newArray(componentType, resultLengthVar).lineEnd(";");
                        builder.lineStart().invoke(binaryInputVar, method, useResultVar, "0", resultLengthVar).lineEnd(";");
                        builder.dedent();
                        builder.line("} else {");
                        builder.indent();
                        builder.lineStart().write(useResultVar).write(" = ").write("null").lineEnd(";");
                        builder.dedent();
                        builder.line("}");
                        return useResultVar;
                    } else {
                        throw unsupported(resultType);
                    }
                } else {
                    CharSequence useResultVar = null;
                    boolean needsResultVarDeclaration = false;
                    if (existingResultVar != null) {
                        useResultVar = existingResultVar;
                    } else if (hasOutParameters) {
                        useResultVar = START_POINT_RESULT_VARIABLE;
                        needsResultVarDeclaration = true;
                    }
                    if (useResultVar != null) {
                        builder.lineStart();
                        if (needsResultVarDeclaration) {
                            builder.write(resultType).space();
                        }
                        builder.write(useResultVar).write(" = ");
                    } else {
                        builder.lineStart("return").space();
                    }
                    builder.invoke(binaryInputVar, method).lineEnd(";");
                    return useResultVar;
                }
            }

            @Override
            void unmarshallOutParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence processIsolateVar, CharSequence formalParameter, CharSequence binaryInputVar,
                            CharSequence resultVar) {
                if (parameterType.getKind() != TypeKind.ARRAY) {
                    throw unsupported(parameterType);
                }
                TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                if (componentType.getKind().isPrimitive()) {
                    CharSequence offset = resolveOffset(marshallerData.out);
                    if (offset == null) {
                        offset = "0";
                    }
                    CharSequence length = updateLengthSnippet(builder, formalParameter, resultVar);
                    builder.lineStart("if (").write(formalParameter).write(" != null");
                    if (marshallerData.out.trimToResult) {
                        builder.write(" && ").write(resultVar).write(" > 0");
                    }
                    builder.write(") ").lineEnd("{");
                    builder.indent();
                    builder.lineStart().invoke(binaryInputVar, "read", formalParameter, offset, length).lineEnd(";");
                    builder.dedent();
                    builder.line("}");
                } else {
                    throw unsupported(parameterType);
                }
            }

            @Override
            void unmarshallParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence isolateVar, CharSequence binaryInputVar) {
                CharSequence method = lookupDirectSnippetReadMethod(parameterType);
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                    if (componentType.getKind().isPrimitive()) {
                        boolean hasExplicitLength = false;
                        boolean pureOutParameter = false;
                        CharSequence length = null;
                        if (marshallerData.in != null) {
                            CharSequence l;
                            if ((l = resolveLength(marshallerData.in)) != null) {
                                hasExplicitLength = true;
                                length = l;
                            }
                        } else if (marshallerData.out != null) {
                            pureOutParameter = true;
                            CharSequence l;
                            if ((l = resolveLength(marshallerData.out)) != null) {
                                hasExplicitLength = true;
                                length = l;
                            }
                        }
                        CharSequence nonNullCondition;
                        if (hasExplicitLength) {
                            nonNullCondition = new CodeBuilder(builder).invoke(binaryInputVar, "readBoolean").build();
                        } else {
                            length = formalParameter + "Length";
                            builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(length).write(" = ").invoke(binaryInputVar, "readInt").lineEnd(";");
                            nonNullCondition = new CodeBuilder(builder).write(length).write(" != -1").build();
                        }
                        builder.lineStart().write(parameterType).space().write(formalParameter).lineEnd(";");
                        builder.lineStart().write("if (").write(nonNullCondition).lineEnd(") {");
                        builder.indent();
                        builder.lineStart(formalParameter).write(" = ").newArray(componentType, length).lineEnd(";");
                        if (!pureOutParameter) {
                            builder.lineStart().invoke(binaryInputVar, "read", formalParameter, "0", length).lineEnd(";");
                        }
                        builder.dedent();
                        builder.line("} else {");
                        builder.indent();
                        builder.lineStart().write(formalParameter).write(" = ").write("null").lineEnd(";");
                        builder.dedent();
                        builder.line("}");
                    } else {
                        throw unsupported(parameterType);
                    }
                } else {
                    builder.lineStart().write(parameterType).space().write(formalParameter).write(" = ").invoke(binaryInputVar, method).lineEnd(";");
                }
            }

            @Override
            void marshallResult(CodeBuilder builder, TypeMirror resultType, CharSequence resultVar, CharSequence binaryOutputVar) {
                CharSequence method = lookupDirectSnippetWriteMethod(resultType);
                if (resultType.getKind() == TypeKind.ARRAY) {
                    TypeMirror componentType = ((ArrayType) resultType).getComponentType();
                    if (componentType.getKind().isPrimitive()) {
                        builder.lineStart().write("if (").write(resultVar).write(" != null) ").lineEnd("{");
                        builder.indent();
                        CharSequence length = new CodeBuilder(builder).memberSelect(resultVar, "length", false).build();
                        builder.lineStart().invoke(binaryOutputVar, "writeInt", length).lineEnd(";");
                        builder.lineStart().invoke(binaryOutputVar, "write", resultVar, "0", length).lineEnd(";");
                        builder.dedent();
                        builder.line("} else {");
                        builder.indent();
                        builder.lineStart().invoke(binaryOutputVar, "writeInt", "-1").lineEnd(";");
                        builder.dedent();
                        builder.line("}");
                    } else {
                        throw unsupported(resultType);
                    }
                } else {
                    builder.lineStart().invoke(binaryOutputVar, method, resultVar).lineEnd(";");
                }
            }

            @Override
            void marshallOutParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar, CharSequence resultVar) {
                if (parameterType.getKind() != TypeKind.ARRAY) {
                    throw unsupported(parameterType);
                }
                TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                if (componentType.getKind().isPrimitive()) {
                    CharSequence length = updateLengthSnippet(builder, formalParameter, resultVar);
                    builder.lineStart("if (").write(formalParameter).write(" != null");
                    if (marshallerData.out.trimToResult) {
                        builder.write(" && ").write(resultVar).write(" > 0");
                    }
                    builder.write(") ").lineEnd("{");
                    builder.indent();
                    builder.lineStart().invoke(binaryOutputVar, "write", formalParameter, "0", length).lineEnd(";");
                    builder.dedent();
                    builder.line("}");
                } else {
                    throw unsupported(parameterType);
                }
            }

            @Override
            Collection<CharSequence> getEndPointSuppressedWarnings(TypeMirror pasrameterType) {
                return Set.of();
            }
        }

        private static final class ReferenceSnippet extends MarshallerSnippet {

            private final ServiceDefinitionData definitionData;

            ReferenceSnippet(ProcessToProcessServiceGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
                this.definitionData = generator.getDefinition();
            }

            @Override
            void marshallParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar) {
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    CharSequence length = null;
                    CharSequence offset = null;
                    if (marshallerData.in != null) {
                        length = resolveLength(marshallerData.in);
                        offset = resolveOffset(marshallerData.in);
                    } else if (marshallerData.out != null) {
                        length = resolveLength(marshallerData.out);
                        offset = resolveOffset(marshallerData.out);
                    }
                    if (length == null) {
                        length = new CodeBuilder(builder).memberSelect(formalParameter, "length", false).build();
                    }
                    if (offset != null) {
                        offset = "0";
                    }
                    Function<CharSequence, CharSequence> marshallElement;
                    if (marshallerData.sameDirection) {
                        TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                        marshallElement = (e) -> unboxProxy(builder, componentType, e);
                    } else {
                        marshallElement = (e) -> pinReference(builder, e);
                    }

                    writeArray(builder, formalParameter, binaryOutputVar, offset, length, marshallElement);
                } else {
                    CharSequence handle;
                    if (marshallerData.sameDirection) {
                        if (parameterType == typeCache.processPeer) {
                            /*
                             * Special handling for receiver PEER_FIELD to safe getPeer call.
                             */
                            handle = new CodeBuilder(builder).invoke(formalParameter, "getHandle").build();
                        } else {
                            handle = unboxProxy(builder, parameterType, formalParameter);
                        }
                    } else {
                        handle = pinReference(builder, formalParameter);
                    }
                    builder.lineStart().invoke(binaryOutputVar, "writeLong", handle).lineEnd(";");
                }
            }

            private CharSequence createProxyForReceiver(CodeBuilder builder, CharSequence isolateVar, CharSequence handleVar) {
                return createProxyForIsolate(builder, isolateVar, handleVar);
            }

            private CharSequence createProxyForIsolate(CodeBuilder builder, CharSequence isolateVar, CharSequence handleVar) {
                List<CharSequence> args = List.of(isolateVar, handleVar);
                CharSequence proxy;
                CodeBuilder peer = new CodeBuilder(builder).invokeStatic(typeCache.peer, "create", args.toArray(new CharSequence[0]));
                if (marshallerData.customDispatchFactory != null) {
                    CodeBuilder foreignObject = new CodeBuilder(builder).invokeStatic(typeCache.foreignObject, "createUnbound", peer.build());
                    proxy = new CodeBuilder(builder).invokeStatic((DeclaredType) marshallerData.customDispatchFactory.getEnclosingElement().asType(),
                                    marshallerData.customDispatchFactory.getSimpleName(), foreignObject.build()).build();
                } else if (types.isSameType(marshallerData.forType, typeCache.foreignObject)) {
                    proxy = new CodeBuilder(builder).invokeStatic(typeCache.foreignObject, "createUnbound", peer.build()).build();
                } else {
                    CharSequence generatedProxyClass = new CodeBuilder(builder).write(types.erasure(marshallerData.forType)).write("Gen").build();
                    proxy = new CodeBuilder(builder).invoke(generatedProxyClass, FACTORY_METHOD_NAME, peer.build()).build();
                }
                CodeBuilder result = new CodeBuilder(builder);
                result.write(handleVar).write(" != 0L ? ").write(proxy).write(" : ").write("null");
                return result.build();
            }

            private void createProxyArrayForReceiver(CodeBuilder builder, TypeMirror type, WriteArrayPolicy writeArrayPolicy, CharSequence processIsolateVar, CharSequence binaryInputVar,
                            CharSequence resultVar) {
                readArray(builder, type, writeArrayPolicy, binaryInputVar, resultVar, (e) -> createProxyForReceiver(builder, processIsolateVar, e));
            }

            private void createProxyArrayForIsolate(CodeBuilder builder, TypeMirror type, WriteArrayPolicy writeArrayPolicy, CharSequence isolateVar, CharSequence binaryInputVar,
                            CharSequence resultVar) {
                readArray(builder, type, writeArrayPolicy, binaryInputVar, resultVar, (e) -> createProxyForIsolate(builder, isolateVar, e));
            }

            private void readArray(CodeBuilder builder, TypeMirror type, WriteArrayPolicy writeArrayPolicy, CharSequence binaryInputVar, CharSequence resultVar,
                            Function<CharSequence, CharSequence> createProxy) {
                assert writeArrayPolicy == WriteArrayPolicy.STORE || writeArrayPolicy == WriteArrayPolicy.IGNORE;
                TypeMirror componentType = ((ArrayType) type).getComponentType();
                CharSequence resultLenVar = resultVar + "Length";
                builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(resultLenVar).write(" = ").invoke(binaryInputVar, "readInt").lineEnd(";");
                builder.lineStart("if (").write(resultLenVar).write(" != -1) ").lineEnd("{");
                builder.indent();
                builder.lineStart(resultVar).write(" = ").newArray(componentType, resultLenVar).lineEnd(";");
                if (writeArrayPolicy == WriteArrayPolicy.STORE) {
                    generateReadReferenceArray(builder, resultVar, null, resultLenVar, binaryInputVar, createProxy);
                }
                builder.dedent();
                builder.line("} else {");
                builder.indent();
                builder.lineStart().write(resultVar).write(" = ").write("null").lineEnd(";");
                builder.dedent();
                builder.line("}");
            }

            private void writeArray(CodeBuilder builder, CharSequence formalParameter, CharSequence binaryOutputVar,
                            CharSequence offsetVar, CharSequence lengthVar, Function<CharSequence, CharSequence> marshallElement) {
                builder.lineStart("if (").write(formalParameter).write(" != null) ").lineEnd("{");
                builder.indent();
                WriteArrayPolicy writeArrayPolicy = marshallerData.out == null || marshallerData.in != null ? WriteArrayPolicy.STORE : WriteArrayPolicy.IGNORE;
                generateWriteReferenceArray(builder, formalParameter, writeArrayPolicy, true, offsetVar, lengthVar, binaryOutputVar, marshallElement);
                builder.dedent();
                builder.line("} else {");
                builder.indent();
                builder.lineStart().invoke(binaryOutputVar, "writeInt", "-1").lineEnd(";");
                builder.dedent();
                builder.line("}");
            }

            private CharSequence unboxProxy(CodeBuilder builder, TypeMirror type, CharSequence value) {
                CharSequence target;
                if (types.isAssignable(type, typeCache.foreignObject)) {
                    target = value;
                } else {
                    target = new CodeBuilder(builder).cast(typeCache.foreignObject, value, true).build();
                }
                CharSequence peer = new CodeBuilder(builder).invoke(target, "getPeer").build();
                return new CodeBuilder(builder).invoke(peer, "getHandle").build();
            }

            private CharSequence pinReference(CodeBuilder builder, CharSequence formalParameter) {
                return new CodeBuilder(builder).invokeStatic(typeCache.referenceHandles, "create", formalParameter).build();
            }

            private CharSequence resolveReference(CodeBuilder builder, TypeMirror resultType, CharSequence handleVar) {
                CharSequence classLiteral = new CodeBuilder(builder).classLiteral(resultType).build();
                CharSequence result = new CodeBuilder(builder).invokeStatic(typeCache.referenceHandles, "resolve", handleVar, classLiteral).build();
                if (marshallerData.useCustomReceiverAccessor) {
                    result = new CodeBuilder(builder).invokeStatic(definitionData.annotatedType, definitionData.customReceiverAccessor.getSimpleName(), result).build();
                }
                return result;
            }

            private void resolveReferenceArray(CodeBuilder builder, TypeMirror type, WriteArrayPolicy writeArrayPolicy, CharSequence binaryInputVar, CharSequence resultVar) {
                TypeMirror componentType = ((ArrayType) type).getComponentType();
                readArray(builder, type, writeArrayPolicy, binaryInputVar, resultVar, (e) -> resolveReference(builder, componentType, e));
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder builder, TypeMirror resultType, CharSequence processIsolateVar, CharSequence binaryInputVar, CharSequence existingResultVar,
                            boolean hasOutParameters) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    CharSequence useResultVar;
                    if (existingResultVar != null) {
                        useResultVar = existingResultVar;
                    } else {
                        useResultVar = START_POINT_RESULT_VARIABLE;
                        builder.lineStart().write(resultType).space().write(useResultVar).lineEnd(";");
                    }
                    if (marshallerData.sameDirection) {
                        createProxyArrayForReceiver(builder, resultType, WriteArrayPolicy.STORE, processIsolateVar, binaryInputVar, useResultVar);
                    } else {
                        resolveReferenceArray(builder, resultType, WriteArrayPolicy.STORE, binaryInputVar, useResultVar);
                    }
                    return useResultVar;
                } else {
                    CharSequence unmarshall;
                    if (marshallerData.sameDirection) {
                        CharSequence handleVar = declareHandleVariable(builder, existingResultVar != null ? existingResultVar : START_POINT_RESULT_VARIABLE, binaryInputVar);
                        unmarshall = createProxyForReceiver(builder, processIsolateVar, handleVar);
                    } else {
                        CharSequence handleVar = new CodeBuilder(builder).invoke(binaryInputVar, "readLong").build();
                        unmarshall = resolveReference(builder, resultType, handleVar);
                    }
                    if (existingResultVar != null) {
                        builder.lineStart(existingResultVar).write(" = ");
                    } else {
                        builder.lineStart("return").space();
                    }
                    builder.write(unmarshall).lineEnd(";");
                    return existingResultVar;
                }
            }

            private CharSequence declareHandleVariable(CodeBuilder builder, CharSequence forVariable, CharSequence binaryInputVar) {
                CharSequence handleVar = String.format("%sForeignObjectHandle", forVariable);
                builder.lineStart().write(types.getPrimitiveType(TypeKind.LONG)).space().write(handleVar).write(" = ").invoke(binaryInputVar, "readLong").lineEnd(";");
                return handleVar;
            }

            @Override
            void unmarshallOutParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence processIsolateVar, CharSequence formalParameter, CharSequence binaryInputVar,
                            CharSequence resultVar) {
                if (parameterType.getKind() != TypeKind.ARRAY) {
                    throw unsupported(parameterType);
                }
                CharSequence offset = resolveOffset(marshallerData.out);
                CharSequence length = updateLengthSnippet(builder, formalParameter, resultVar);
                builder.lineStart("if (").write(formalParameter).write(" != null");
                if (marshallerData.out.trimToResult) {
                    builder.write(" && ").write(resultVar).write(" > 0");
                }
                builder.write(") ").lineEnd("{");
                builder.indent();
                TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                Function<CharSequence, CharSequence> unmarshallElement;
                if (marshallerData.sameDirection) {
                    unmarshallElement = (e) -> createProxyForReceiver(builder, processIsolateVar, e);
                } else {
                    unmarshallElement = (e) -> resolveReference(builder, componentType, e);
                }
                generateReadReferenceArray(builder, formalParameter, offset, length, binaryInputVar, unmarshallElement);
                builder.dedent();
                builder.line("}");
            }

            @Override
            void unmarshallParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence isolateVar, CharSequence binaryInputVar) {
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    builder.lineStart().write(parameterType).space().write(formalParameter).lineEnd(";");
                    boolean pureOutParameter = marshallerData.out != null && marshallerData.in == null;
                    WriteArrayPolicy readArrayPolicy = pureOutParameter ? WriteArrayPolicy.IGNORE : WriteArrayPolicy.STORE;
                    if (marshallerData.sameDirection) {
                        resolveReferenceArray(builder, parameterType, readArrayPolicy, binaryInputVar, formalParameter);
                    } else {
                        createProxyArrayForIsolate(builder, parameterType, readArrayPolicy, isolateVar, binaryInputVar, formalParameter);

                    }
                } else {
                    CharSequence object;
                    if (marshallerData.sameDirection) {
                        CharSequence readHandle = new CodeBuilder(builder).invoke(binaryInputVar, "readLong").build();
                        object = resolveReference(builder, parameterType, readHandle);
                    } else {
                        CharSequence handleVar = declareHandleVariable(builder, formalParameter, binaryInputVar);
                        object = createProxyForIsolate(builder, isolateVar, handleVar);
                    }
                    builder.lineStart().write(parameterType).space().write(formalParameter).write(" = ").write(object).lineEnd(";");
                }
            }

            @Override
            void marshallResult(CodeBuilder builder, TypeMirror resultType, CharSequence resultVar, CharSequence binaryOutputVar) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    CharSequence length = new CodeBuilder(builder).memberSelect(resultVar, "length", false).build();
                    Function<CharSequence, CharSequence> marshallElement;
                    if (marshallerData.sameDirection) {
                        marshallElement = (e) -> pinReference(builder, e);
                    } else {
                        TypeMirror componentType = ((ArrayType) resultType).getComponentType();
                        marshallElement = (e) -> unboxProxy(builder, componentType, e);
                    }
                    writeArray(builder, resultVar, binaryOutputVar, null, length, marshallElement);
                } else {
                    CharSequence marshall;
                    if (marshallerData.sameDirection) {
                        marshall = pinReference(builder, resultVar);
                    } else {
                        marshall = unboxProxy(builder, resultType, resultVar);
                    }
                    builder.lineStart().invoke(binaryOutputVar, "writeLong", marshall).lineEnd(";");
                }
            }

            @Override
            void marshallOutParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar, CharSequence resultVar) {
                if (parameterType.getKind() != TypeKind.ARRAY) {
                    throw unsupported(parameterType);
                }
                CharSequence length = updateLengthSnippet(builder, formalParameter, resultVar);
                builder.lineStart("if (").write(formalParameter).write(" != null");
                if (marshallerData.out.trimToResult) {
                    builder.write(" && ").write(resultVar).write(" > 0");
                }
                builder.write(") ").lineEnd("{");
                builder.indent();
                TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                Function<CharSequence, CharSequence> marshallElement;
                if (marshallerData.sameDirection) {
                    marshallElement = (e) -> pinReference(builder, e);
                } else {
                    marshallElement = (e) -> unboxProxy(builder, componentType, e);
                }
                generateWriteReferenceArray(builder, formalParameter, WriteArrayPolicy.STORE, false, null, length, binaryOutputVar, marshallElement);
                builder.dedent();
                builder.line("}");
            }

            @Override
            Collection<CharSequence> getEndPointSuppressedWarnings(TypeMirror parameterType) {
                if (marshallerData.sameDirection && Utilities.isParameterizedType(parameterType)) {
                    return Set.of("unchecked");
                } else {
                    return Set.of();
                }
            }
        }

        private static final class PeerReferenceSnippet extends MarshallerSnippet {

            PeerReferenceSnippet(ProcessToProcessServiceGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
            }

            @Override
            void marshallParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar) {
                if (marshallerData.sameDirection) {
                    CharSequence foreignObject = new CodeBuilder(builder).cast(typeCache.foreignObject, formalParameter, true).build();
                    CharSequence peer = new CodeBuilder(builder).invoke(foreignObject, "getPeer").build();
                    CharSequence handle = new CodeBuilder(builder).invoke(peer, "getHandle").build();
                    builder.lineStart().invoke(binaryOutputVar, "writeLong", handle).lineEnd(";");
                } else {
                    CharSequence value = new CodeBuilder(builder).invokeStatic(typeCache.referenceHandles, "create", formalParameter).build();
                    builder.lineStart().invoke(binaryOutputVar, "writeLong", value).lineEnd(";");
                }
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder builder, TypeMirror resultType, CharSequence processIsolateVar, CharSequence binaryInputVar, CharSequence existingResultVar,
                            boolean hasOutParameters) {
                CharSequence handle = new CodeBuilder(builder).invoke(binaryInputVar, "readLong").build();
                if (existingResultVar != null) {
                    builder.lineStart(existingResultVar).write(" = ");
                } else {
                    builder.lineStart("return").space();
                }
                if (marshallerData.sameDirection) {
                    builder.invokeStatic(typeCache.peer, "create", processIsolateVar, handle).lineEnd(";");
                } else {
                    CharSequence typeLiteral = new CodeBuilder(builder).classLiteral(resultType).build();
                    builder.invokeStatic(typeCache.referenceHandles, "resolve", handle, typeLiteral).lineEnd(";");
                }
                return existingResultVar;
            }

            @Override
            void unmarshallOutParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence processIsolateVar, CharSequence formalParameter, CharSequence binaryInputVar,
                            CharSequence resultVar) {
                throw new UnsupportedOperationException();
            }

            @Override
            void unmarshallParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence isolateVar, CharSequence binaryInputVar) {
                if (marshallerData.sameDirection) {
                    CharSequence handle = new CodeBuilder(builder).invoke(binaryInputVar, "readLong").build();
                    CharSequence typeLiteral = new CodeBuilder(builder).classLiteral(parameterType).build();
                    builder.lineStart().write(parameterType).space().write(formalParameter).write(" = ").invokeStatic(typeCache.referenceHandles, "resolve", handle, typeLiteral).lineEnd(";");
                } else {
                    CharSequence read = new CodeBuilder(builder).invoke(binaryInputVar, "readLong").build();
                    builder.lineStart().write(parameterType).space().write(formalParameter).write(" = ").invokeStatic(typeCache.peer, "create", isolateVar, read).lineEnd(";");
                }
            }

            @Override
            void marshallResult(CodeBuilder builder, TypeMirror resultType, CharSequence resultVar, CharSequence binaryOutputVar) {
                if (marshallerData.sameDirection) {
                    CharSequence value = new CodeBuilder(builder).invokeStatic(typeCache.referenceHandles, "create", resultVar).build();
                    builder.lineStart().invoke(binaryOutputVar, "writeLong", value).lineEnd(";");
                } else {
                    CharSequence processPeer = new CodeBuilder(builder).cast(typeCache.processPeer, resultVar, true).build();
                    CharSequence handle = new CodeBuilder(builder).invoke(processPeer, "getHandle").build();
                    builder.lineStart().invoke(binaryOutputVar, "writeLong", handle).lineEnd(";");
                }
            }

            @Override
            void marshallOutParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar, CharSequence resultVar) {
                throw new UnsupportedOperationException();
            }

            @Override
            Collection<CharSequence> getEndPointSuppressedWarnings(TypeMirror parameterType) {
                if (marshallerData.sameDirection && Utilities.isParameterizedType(parameterType)) {
                    return Set.of("unchecked");
                } else {
                    return Set.of();
                }
            }
        }

        private static final class CustomSnippet extends MarshallerSnippet {

            CustomSnippet(ProcessToProcessServiceGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
            }

            @Override
            void marshallParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar) {
                writeCustomObject(builder, parameterType, formalParameter, binaryOutputVar);
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder builder, TypeMirror resultType, CharSequence processIsolateVar, CharSequence binaryInputVar, CharSequence existingResultVar,
                            boolean hasOutParameters) {
                if (resultType.getKind() == TypeKind.ARRAY || existingResultVar != null || hasOutParameters) {
                    CharSequence useResultVar;
                    boolean needsResultVarDeclaration;
                    if (existingResultVar != null) {
                        useResultVar = existingResultVar;
                        needsResultVarDeclaration = false;
                    } else {
                        useResultVar = START_POINT_RESULT_VARIABLE;
                        needsResultVarDeclaration = true;
                    }
                    readCustomObject(builder, resultType, useResultVar, processIsolateVar, binaryInputVar, needsResultVarDeclaration);
                    return useResultVar;
                } else {
                    builder.lineStart("return ").invoke(marshallerData.name, "read", processIsolateVar, binaryInputVar).lineEnd(";");
                    return null;
                }
            }

            @Override
            void unmarshallOutParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence processIsolateVar, CharSequence formalParameter, CharSequence binaryInputVar,
                            CharSequence resultVar) {
                CharSequence offset = resolveOffset(marshallerData.out);
                CharSequence length = updateLengthSnippet(builder, formalParameter, resultVar);
                readCustomObjectUpdate(builder, parameterType, formalParameter, processIsolateVar, binaryInputVar, offset, length, false);
            }

            @Override
            void unmarshallParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence processIsolateVar, CharSequence binaryInputVar) {
                readCustomObject(builder, parameterType, formalParameter, processIsolateVar, binaryInputVar, true);
            }

            @Override
            void marshallResult(CodeBuilder builder, TypeMirror resultType, CharSequence resultVar, CharSequence binaryOutputVar) {
                writeCustomObject(builder, resultType, resultVar, binaryOutputVar);
            }

            @Override
            void marshallOutParameter(CodeBuilder builder, TypeMirror parameterType, CharSequence formalParameter, CharSequence binaryOutputVar, CharSequence resultVar) {
                writeCustomObjectUpdate(builder, parameterType, formalParameter, binaryOutputVar);
            }

            @Override
            Collection<CharSequence> getEndPointSuppressedWarnings(TypeMirror parameterType) {
                return Set.of();
            }
        }

        static MarshallerSnippet forData(ProcessToProcessServiceGenerator generator, MarshallerData marshallerData) {
            return switch (marshallerData.kind) {
                case VALUE -> new DirectSnippet(generator, marshallerData);
                case REFERENCE -> new ReferenceSnippet(generator, marshallerData);
                case PEER_REFERENCE -> new PeerReferenceSnippet(generator, marshallerData);
                case CUSTOM -> new CustomSnippet(generator, marshallerData);
            };
        }
    }
}
