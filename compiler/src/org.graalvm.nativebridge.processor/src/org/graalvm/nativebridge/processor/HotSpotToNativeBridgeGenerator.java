/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.nativebridge.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.nativebridge.processor.AbstractBridgeParser.CacheData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MarshallerData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MethodData;
import org.graalvm.nativebridge.processor.HotSpotToNativeBridgeParser.HotSpotToNativeDefinitionData;
import org.graalvm.nativebridge.processor.HotSpotToNativeBridgeParser.TypeCache;

final class HotSpotToNativeBridgeGenerator extends AbstractBridgeGenerator {

    private final TypeCache typeCache;

    HotSpotToNativeBridgeGenerator(HotSpotToNativeBridgeParser parser, TypeCache typeCache) {
        super(parser);
        this.typeCache = typeCache;
    }

    @Override
    void generate(DefinitionData data) throws IOException {
        TypeElement annotatedElement = (TypeElement) data.annotatedType.asElement();
        CodeBuilder builder = new CodeBuilder(getEnclosingPackageElement(annotatedElement), types, typeCache);
        generateTopLevel(builder, data);
        String content = builder.build();
        writeSourceFile(data, content);
    }

    @Override
    HotSpotToNativeMarshallerSnippets marshallerSnippets(DefinitionData data, MarshallerData marshallerData) {
        return HotSpotToNativeMarshallerSnippets.forData(data, marshallerData, types, typeCache);
    }

    private void generateTopLevel(CodeBuilder builder, DefinitionData data) {
        builder.classStart(EnumSet.of(Modifier.FINAL), data.getTargetClassSimpleName(), null, Collections.emptyList());
        builder.indent();

        builder.lineEnd("");
        FactoryMethodInfo factoryMethod = generateStartPointFactory(builder, data, Arrays.asList(typeCache.nativeIsolate, typeCache.nativeObject),
                        "HotSpotToNativeStartPoint", "createHotSpotToNative");
        builder.lineEnd("");

        generateHSToNativeStartPoint(builder, data, factoryMethod);
        builder.lineEnd("");
        generateHSToNativeEndPoint(builder, data);
        builder.dedent();
        builder.line("}");
    }

    private void generateHSToNativeStartPoint(CodeBuilder builder, DefinitionData data,
                    FactoryMethodInfo factoryMethodInfo) {
        HotSpotToNativeDefinitionData hsData = ((HotSpotToNativeDefinitionData) data);

        CacheSnippets cacheSnippets = cacheSnippets(data);
        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), "HotSpotToNativeStartPoint",
                        data.annotatedType, Collections.emptyList());
        builder.indent();
        builder.lineEnd("");

        generateMarshallerFields(builder, data, typeCache.jniHotSpotMarshaller, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC);
        builder.line("");

        if (!data.getAllCustomMarshallers().isEmpty()) {
            builder.methodStart(EnumSet.of(Modifier.STATIC), null,
                            null, Collections.emptyList(), Collections.emptyList());
            builder.indent();
            builder.lineStart().write(typeCache.jniConfig).write(" config = ").invokeStatic(hsData.jniConfig, "getInstance").lineEnd(";");
            generateMarshallerLookups(builder, data, null, true, typeCache.jniHotSpotMarshaller);
            builder.dedent();
            builder.line("}");
        }

        generateCacheFields(builder, cacheSnippets, data);
        builder.lineEnd("");
        builder.methodStart(Collections.emptySet(), "HotSpotToNativeStartPoint", null,
                        factoryMethodInfo.parameters, Collections.emptyList());
        builder.indent();
        builder.lineStart().call("super", parameterNames(factoryMethodInfo.superCallParameters)).lineEnd(";");
        generateCacheFieldsInit(builder, cacheSnippets, data);
        builder.dedent();
        builder.line("}");

        for (MethodData methodData : data.toGenerate) {
            generateHSToNativeStartMethod(builder, cacheSnippets, data, methodData);
        }
        for (MethodData methodData : data.toGenerate) {
            generateHSToNativeNativeMethod(builder, data, methodData);
        }

        builder.dedent();
        builder.line("}");
    }

    private void generateHSToNativeStartMethod(CodeBuilder builder, CacheSnippets cacheSnippets,
                    DefinitionData data, MethodData methodData) {
        builder.line("");
        overrideMethod(builder, methodData);
        builder.indent();
        CodeBuilder receiverCastStatement;
        CharSequence receiver;
        CharSequence receiverNativeObject;
        int nonReceiverParameterStart;
        if (data.hasExplicitReceiver()) {
            receiver = methodData.element.getParameters().get(0).getSimpleName();
            receiverCastStatement = new CodeBuilder(builder).write(typeCache.nativeObject).write(" nativeObject = (").write(typeCache.nativeObject).write(") ").write(receiver).write(";");
            receiverNativeObject = "nativeObject";
            nonReceiverParameterStart = 1;
        } else {
            receiver = data.endPointHandle != null ? data.endPointHandle.getSimpleName() : "this";
            receiverCastStatement = null;
            receiverNativeObject = receiver;
            nonReceiverParameterStart = 0;
        }
        CharSequence scopeVarName = "nativeIsolateThread";
        CodeBuilder getIsolateCall = new CodeBuilder(builder).invoke(receiverNativeObject, "getIsolate");
        CodeBuilder enterScope = new CodeBuilder(builder).write(typeCache.nativeIsolateThread).space().write(scopeVarName).write(" = ").invoke(getIsolateCall.build(), "enter").write(";");
        CodeBuilder leaveScope = new CodeBuilder(builder).invoke(scopeVarName, "leave").write(";");
        CodeBuilder valueBuilder = new CodeBuilder(builder);
        List<CharSequence> actualParameters = new ArrayList<>();
        actualParameters.add(new CodeBuilder(valueBuilder).invoke(scopeVarName, "getIsolateThreadId").build());
        actualParameters.add(new CodeBuilder(valueBuilder).invoke(receiverNativeObject, "getHandle").build());
        List<? extends VariableElement> formalParameters = methodData.element.getParameters();
        List<? extends TypeMirror> formalParameterTypes = methodData.type.getParameterTypes();
        for (int i = nonReceiverParameterStart; i < formalParameters.size(); i++) {
            MarshallerData marshaller = methodData.getParameterMarshaller(i);
            actualParameters.add(marshallerSnippets(data, marshaller).marshallParameter(valueBuilder,
                            formalParameterTypes.get(i), formalParameters.get(i).getSimpleName(), null));
        }
        CodeBuilder nativeCallBuilder = new CodeBuilder(valueBuilder);
        String nativeMethodName = jniMethodName(methodData.element);
        nativeCallBuilder.call(nativeMethodName, actualParameters.toArray(new CharSequence[actualParameters.size()]));
        valueBuilder.write(marshallerSnippets(data, methodData.getReturnTypeMarshaller()).unmarshallResult(
                        valueBuilder, methodData.type.getReturnType(), nativeCallBuilder.build(), receiverNativeObject, null));
        CacheData cacheData = methodData.cachedData;
        if (cacheData != null) {
            String cacheFieldName = cacheData.cacheFieldName;
            String resFieldName = cacheFieldName + "Result";
            builder.lineStart().write(cacheData.cacheEntryType).space().write(resFieldName).write(" = ").write(cacheSnippets.readCache(builder, cacheFieldName, receiver)).lineEnd(";");
            builder.lineStart("if (").write(resFieldName).lineEnd(" == null) {");
            builder.indent();
            if (receiverCastStatement != null) {
                builder.line(receiverCastStatement.build());
            }
            builder.line(enterScope.build());
            builder.line("try {");
            builder.indent();
            builder.lineStart().write(resFieldName).write(" = ").write(valueBuilder.build()).lineEnd(";");
            builder.lineStart().write(cacheSnippets.writeCache(builder, cacheFieldName, receiver, resFieldName)).lineEnd(";");
            builder.dedent();
            builder.line("} finally {");
            builder.indent();
            builder.line(leaveScope.build());
            builder.dedent();
            builder.line("}");
            builder.dedent();
            builder.line("}");
            builder.lineStart("return ").write(resFieldName).lineEnd(";");
        } else {
            if (receiverCastStatement != null) {
                builder.line(receiverCastStatement.build());
            }
            builder.line(enterScope.build());
            builder.line("try {");
            builder.indent();
            builder.lineStart();
            if (methodData.type.getReturnType().getKind() != TypeKind.VOID) {
                builder.write("return ");
            }
            builder.write(valueBuilder.build());
            builder.lineEnd(";");
            builder.dedent();
            builder.line("} finally {");
            builder.indent();
            builder.line(leaveScope.build());
            builder.dedent();
            builder.line("}");
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateHSToNativeNativeMethod(CodeBuilder builder, DefinitionData data, MethodData methodData) {
        builder.line("");
        String nativeMethodName = jniMethodName(methodData.element);
        TypeMirror nativeMethodReturnType = marshallerSnippets(data, methodData.getReturnTypeMarshaller()).getEndPointMethodParameterType(methodData.type.getReturnType());
        List<CodeBuilder.Parameter> nativeMethodParameters = new ArrayList<>();
        PrimitiveType longType = types.getPrimitiveType(TypeKind.LONG);
        nativeMethodParameters.add(CodeBuilder.newParameter(longType, "isolateThread"));
        nativeMethodParameters.add(CodeBuilder.newParameter(longType, "objectId"));
        int nonReceiverParameterStart = data.hasExplicitReceiver() ? 1 : 0;
        List<? extends VariableElement> parameters = methodData.element.getParameters();
        List<? extends TypeMirror> parameterTypes = methodData.type.getParameterTypes();
        for (int i = nonReceiverParameterStart; i < parameters.size(); i++) {
            TypeMirror nativeMethodParameter = marshallerSnippets(data, methodData.getParameterMarshaller(i)).getEndPointMethodParameterType(parameterTypes.get(i));
            nativeMethodParameters.add(CodeBuilder.newParameter(nativeMethodParameter, parameters.get(i).getSimpleName()));
        }
        builder.methodStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.NATIVE),
                        nativeMethodName,
                        nativeMethodReturnType,
                        nativeMethodParameters,
                        methodData.type.getThrownTypes());
    }

    private void generateHSToNativeEndPoint(CodeBuilder builder, DefinitionData data) {
        HotSpotToNativeDefinitionData hsData = ((HotSpotToNativeDefinitionData) data);

        builder.lineStart().annotation(typeCache.suppressWarnings, "unused").lineEnd("");
        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), "HotSpotToNativeEndPoint", null, Collections.emptyList());
        builder.indent();
        builder.lineEnd("");

        generateMarshallerFields(builder, data, typeCache.jniNativeMarshaller, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
        builder.line("");

        if (!data.getAllCustomMarshallers().isEmpty()) {
            builder.methodStart(EnumSet.of(Modifier.STATIC), null,
                            null, Collections.emptyList(), Collections.emptyList());
            builder.indent();
            builder.lineStart().write(typeCache.jniConfig).write(" config = ").invokeStatic(hsData.jniConfig, "getInstance").lineEnd(";");
            generateMarshallerLookups(builder, data, null, true, typeCache.jniNativeMarshaller);
            builder.dedent();
            builder.line("}");
            builder.line("");
        }

        for (MethodData methodData : data.toGenerate) {
            String entryPointSymbolName = jniCMethodSymbol(data, methodData);
            generateHSToNativeEndMethod(builder, data, methodData, entryPointSymbolName);
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateHSToNativeEndMethod(CodeBuilder builder, DefinitionData data, MethodData methodData, String entryPointName) {
        builder.lineEnd("");
        Map<String, Object> centryPointAttrs = new LinkedHashMap<>();
        centryPointAttrs.put("name", entryPointName);
        DeclaredType centryPointPredicate = ((HotSpotToNativeBridgeParser.HotSpotToNativeDefinitionData) data).centryPointPredicate;
        if (centryPointPredicate != null) {
            centryPointAttrs.put("include", centryPointPredicate);
        }
        builder.lineStart().annotationWithAttributes(typeCache.centryPoint, centryPointAttrs).lineEnd("");
        List<? extends VariableElement> methodParameters = methodData.element.getParameters();
        List<? extends TypeMirror> methodParameterTypes = methodData.type.getParameterTypes();
        Set<CharSequence> warnings = new TreeSet<>(Comparator.comparing(CharSequence::toString));
        Collections.addAll(warnings, "try", "unused");
        if (!data.hasExplicitReceiver() && isParameterizedType(data.serviceType)) {
            warnings.add("unchecked");
        }
        for (int i = 0; i < methodParameters.size(); i++) {
            warnings.addAll(marshallerSnippets(data, methodData.getParameterMarshaller(i)).getEndPointSuppressedWarnings(builder, methodParameterTypes.get(i)));
        }
        builder.lineStart().annotation(typeCache.suppressWarnings, warnings.toArray(new CharSequence[warnings.size()])).lineEnd("");
        List<CodeBuilder.Parameter> params = new ArrayList<>();
        CharSequence jniEnvVariable = "jniEnv";
        params.add(CodeBuilder.newParameter(typeCache.jniEnv, jniEnvVariable));
        params.add(CodeBuilder.newParameter(typeCache.jClass, "jniClazz"));
        CodeBuilder isolateAnnotationBuilder = new CodeBuilder(builder);
        isolateAnnotationBuilder.annotation(typeCache.isolateThreadContext, null);
        params.add(CodeBuilder.newParameter(types.getPrimitiveType(TypeKind.LONG), "isolateThread", isolateAnnotationBuilder.build()));
        params.add(CodeBuilder.newParameter(types.getPrimitiveType(TypeKind.LONG), "objectId"));
        int parameterStartIndex = data.hasExplicitReceiver() ? 1 : 0;
        for (int i = parameterStartIndex; i < methodParameters.size(); i++) {
            TypeMirror nativeMethodType = marshallerSnippets(data, methodData.getParameterMarshaller(i)).getEndPointMethodParameterType(methodParameterTypes.get(i));
            params.add(CodeBuilder.newParameter(jniTypeForJavaType(nativeMethodType, types, typeCache), methodParameters.get(i).getSimpleName()));
        }
        CharSequence methodName = methodData.element.getSimpleName();
        HotSpotToNativeMarshallerSnippets returnTypeSnippets = marshallerSnippets(data, methodData.getReturnTypeMarshaller());
        TypeMirror returnType = returnTypeSnippets.getEndPointMethodParameterType(methodData.type.getReturnType());
        boolean voidReturnType = returnType.getKind() == TypeKind.VOID;
        boolean primitiveReturnType = returnType.getKind().isPrimitive();
        boolean objectReturnType = !(primitiveReturnType || voidReturnType);
        builder.methodStart(EnumSet.of(Modifier.STATIC), methodName, jniTypeForJavaType(returnType, types, typeCache), params, Collections.emptyList());
        builder.indent();
        String scopeName = data.getTargetClassSimpleName() + "::" + methodName;
        String scopeInit = new CodeBuilder(builder).write(typeCache.jniMethodScope).write(" scope = ").newInstance(typeCache.jniMethodScope, "\"" + scopeName + "\"", params.get(0).name).build();
        if (objectReturnType) {
            builder.lineStart(scopeInit).lineEnd(";");
            scopeInit = new CodeBuilder(builder).write(typeCache.jniMethodScope).write(" sc = scope").build();
        }
        builder.lineStart("try (").write(scopeInit).lineEnd(") {");
        builder.indent();

        CharSequence[] actualParameters = new CharSequence[methodParameters.size()];
        int nonReceiverParameterStart;
        if (data.hasExplicitReceiver()) {
            actualParameters[0] = "receiverObject";
            nonReceiverParameterStart = 1;
        } else {
            nonReceiverParameterStart = 0;
        }

        boolean hasPostUnmarshall = false;
        Map<String, CharSequence> parameterValueOverrides = new HashMap<>();
        // Generate pre unmarshall statements
        for (int i = nonReceiverParameterStart; i < methodParameters.size(); i++) {
            hasPostUnmarshall |= marshallerSnippets(data, methodData.getParameterMarshaller(i)).preUnmarshallParameter(builder, methodParameterTypes.get(i), methodParameters.get(i).getSimpleName(),
                            params.get(0).name, parameterValueOverrides);
        }

        // Encode arguments.
        for (int i = nonReceiverParameterStart; i < methodParameters.size(); i++) {
            CharSequence parameterName = methodParameters.get(i).getSimpleName();
            CharSequence parameterValueOverride = parameterValueOverrides.get(parameterName.toString());
            if (parameterValueOverride != null) {
                actualParameters[i] = parameterValueOverride;
            } else {
                actualParameters[i] = marshallerSnippets(data, methodData.getParameterMarshaller(i)).unmarshallParameter(builder, methodParameterTypes.get(i), parameterName, params.get(0).name);
            }
        }

        CharSequence resolvedDispatch;
        if (data.hasExplicitReceiver()) {
            TypeMirror receiverType = data.delegateAccessor.getParameters().get(0).asType();
            CodeBuilder classLiteralBuilder = new CodeBuilder(builder).classLiteral(receiverType);
            CharSequence nativeObject = "nativeObject";
            resolvedDispatch = "resolvedDispatch";
            builder.lineStart().write(receiverType).space().write(nativeObject).write(" = ").invokeStatic(typeCache.nativeObjectHandles, "resolve", "objectId", classLiteralBuilder.build()).lineEnd(
                            ";");
            builder.lineStart().write(data.serviceType).space().write(resolvedDispatch).write(" = ").invokeStatic(data.annotatedType, data.delegateAccessor.getSimpleName(), nativeObject).lineEnd(
                            ";");
            builder.lineStart().write(typeCache.object).space().write("receiverObject").write(" = ").invokeStatic(data.annotatedType, data.receiverAccessor.getSimpleName(), nativeObject).lineEnd(
                            ";");
        } else {
            resolvedDispatch = "receiverObject";
            CodeBuilder classLiteralBuilder = new CodeBuilder(builder).classLiteral(data.serviceType);
            builder.lineStart().write(data.serviceType).write(" receiverObject = ").invokeStatic(typeCache.nativeObjectHandles, "resolve", "objectId", classLiteralBuilder.build()).lineEnd(";");
        }
        CharSequence resultSnippet = new CodeBuilder(builder).invoke(resolvedDispatch, methodData.receiverMethod != null ? methodData.receiverMethod : methodName, actualParameters).build();
        resultSnippet = returnTypeSnippets.marshallResult(builder, returnType, resultSnippet, params.get(0).name);
        CharSequence resultVariable = null;
        if (voidReturnType) {
            builder.lineStart().write(resultSnippet).lineEnd(";");
        } else if (primitiveReturnType) {
            if (hasPostUnmarshall) {
                resultVariable = "result";
                builder.lineStart().write(returnType).space().write(resultVariable).write(" = ");
            } else {
                builder.lineStart("return ");
            }
            builder.write(resultSnippet).lineEnd(";");
        } else {
            builder.lineStart().invoke("scope", "setObjectResult", resultSnippet).lineEnd(";");
        }

        // Generate post unmarshall statements.
        for (int i = nonReceiverParameterStart; i < methodParameters.size(); i++) {
            HotSpotToNativeMarshallerSnippets marshallerSnippets = marshallerSnippets(data, methodData.getParameterMarshaller(i));
            marshallerSnippets.postUnmarshallParameter(builder, methodParameterTypes.get(i), methodParameters.get(i).getSimpleName(), params.get(0).name, resultVariable);
        }
        if (resultVariable != null) {
            // Return was deferred after post unmarshalling statements.
            // Do it now.
            builder.lineStart("return ").write(resultVariable).lineEnd(";");
        }
        builder.dedent();
        String exceptionVariable = "e";
        builder.lineStart("} catch (").write(typeCache.throwable).space().write(exceptionVariable).lineEnd(") {");
        builder.indent();
        CodeBuilder defaultExceptionHandler = new CodeBuilder(builder).invokeStatic(typeCache.jniExceptionWrapper, "throwInHotSpot", jniEnvVariable, exceptionVariable).write(";");
        if (data.exceptionHandler != null) {
            CharSequence[] args = new CharSequence[]{jniEnvVariable, exceptionVariable};
            builder.lineStart("if (!").invokeStatic(data.annotatedType, data.exceptionHandler.getSimpleName(), args).lineEnd(") {");
            builder.indent();
            builder.line(defaultExceptionHandler.build());
            builder.dedent();
            builder.line("}");
        } else {
            builder.line(defaultExceptionHandler.build());
        }
        if (primitiveReturnType) {
            builder.lineStart("return ").writeDefaultValue(returnType).lineEnd(";");
        } else if (objectReturnType) {
            String cnull = new CodeBuilder(builder).invokeStatic(typeCache.wordFactory, "nullPointer").build();
            builder.lineStart().invoke("scope", "setObjectResult", cnull).lineEnd(";");
        }
        builder.dedent();
        builder.line("}");
        if (objectReturnType) {
            builder.lineStart("return ").invoke("scope", "getObjectResult").lineEnd(";");
        }
        builder.dedent();
        builder.line("}");
    }

    private static String jniMethodName(ExecutableElement methodElement) {
        return methodElement.getSimpleName() + "0";
    }

    private String jniCMethodSymbol(DefinitionData data, MethodData methodData) {
        String packageName = getEnclosingPackageElement((TypeElement) data.annotatedType.asElement()).getQualifiedName().toString();
        String classSimpleName = data.getTargetClassSimpleName() + "$HotSpotToNativeStartPoint";
        String entryPointName = String.format("Java_%s_%s_%s",
                        Utilities.cSymbol(packageName),
                        Utilities.cSymbol(classSimpleName),
                        Utilities.cSymbol(jniMethodName(methodData.element)));
        if (methodData.hasOverload()) {
            StringBuilder sb = new StringBuilder(entryPointName);
            sb.append("__");
            encodeTypeForJniCMethodSymbol(sb, types.getPrimitiveType(TypeKind.LONG));   // Isolate
                                                                                        // thread
            encodeTypeForJniCMethodSymbol(sb, types.getPrimitiveType(TypeKind.LONG));   // Object
                                                                                        // handle
            List<? extends VariableElement> params = methodData.element.getParameters();
            int nonReceiverParameterStart = data.hasExplicitReceiver() ? 1 : 0;
            for (int i = nonReceiverParameterStart; i < params.size(); i++) {
                encodeTypeForJniCMethodSymbol(sb, types.erasure(params.get(i).asType()));
            }
            entryPointName = sb.toString();
        }
        return entryPointName;
    }

    private static void encodeTypeForJniCMethodSymbol(StringBuilder into, TypeMirror type) {
        Utilities.encodeType(into, type, "_3",
                        (te) -> "L" + Utilities.cSymbol(te.getQualifiedName().toString()) + "_2");
    }

    private abstract static class HotSpotToNativeMarshallerSnippets extends MarshallerSnippets {

        final TypeCache cache;

        HotSpotToNativeMarshallerSnippets(MarshallerData marshallerData, Types types, TypeCache typeCache) {
            super(marshallerData, types, typeCache);
            this.cache = typeCache;
        }

        static HotSpotToNativeMarshallerSnippets forData(DefinitionData data, MarshallerData marshallerData, Types types, TypeCache typeCache) {
            switch (marshallerData.kind) {
                case VALUE:
                    return new HotSpotToNativeMarshallerSnippets.DirectSnippets(marshallerData, types, typeCache);
                case REFERENCE:
                    return new HotSpotToNativeMarshallerSnippets.ReferenceSnippets(data, marshallerData, types, typeCache);
                case CUSTOM:
                    return new HotSpotToNativeMarshallerSnippets.CustomSnippets(marshallerData, types, typeCache);
                default:
                    throw new IllegalArgumentException(String.valueOf(marshallerData.kind));
            }
        }

        private static final class DirectSnippets extends HotSpotToNativeMarshallerSnippets {

            DirectSnippets(MarshallerData marshallerData, Types types, TypeCache cache) {
                super(marshallerData, types, cache);
            }

            @Override
            TypeMirror getEndPointMethodParameterType(TypeMirror type) {
                return type;
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence jniEnvFieldName) {
                return formalParameter;
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence jniEnvFieldName) {
                return invocationSnippet;
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
                if (types.isSameType(cache.string, resultType)) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createHSString", jniEnvFieldName, invocationSnippet).build();
                } else if (resultType.getKind() == TypeKind.ARRAY) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createHSArray", jniEnvFieldName, invocationSnippet).build();
                } else {
                    return invocationSnippet;
                }
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName) {
                if (types.isSameType(cache.string, parameterType)) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createString", jniEnvFieldName, parameterName).build();
                } else if (parameterType.getKind() == TypeKind.ARRAY) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createArray", jniEnvFieldName, parameterName).build();
                } else {
                    return parameterName;
                }
            }

            @Override
            boolean preUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverride) {
                boolean hasDirectionModifiers = isArrayWithDirectionModifiers(parameterType);
                if (hasDirectionModifiers) {
                    AnnotationMirror in = findIn(marshallerData.annotations);
                    AnnotationMirror out = findOut(marshallerData.annotations);
                    currentBuilder.lineStart().write(parameterType).space().write(outArrayLocal(parameterName)).write(" = ");
                    if (in != null) {
                        CharSequence arrayLengthParameter = getArrayLengthParameterName(in);
                        if (arrayLengthParameter != null) {
                            currentBuilder.newArray(((ArrayType) parameterType).getComponentType(), arrayLengthParameter).lineEnd(";");
                            CharSequence arrayOffsetSnippet;
                            CharSequence arrayOffsetParameter = getArrayOffsetParameterName(in);
                            if (arrayOffsetParameter != null) {
                                arrayOffsetSnippet = arrayOffsetParameter;
                                parameterValueOverride.put(arrayOffsetParameter.toString(), "0");
                            } else {
                                arrayOffsetSnippet = "0";
                            }
                            currentBuilder.lineStart().invokeStatic(cache.jniUtil, "arrayCopy",
                                            jniEnvFieldName, parameterName, arrayOffsetSnippet, outArrayLocal(parameterName), "0", arrayLengthParameter).lineEnd(";");
                        } else {
                            currentBuilder.invokeStatic(cache.jniUtil, "createArray", jniEnvFieldName, parameterName).lineEnd(";");
                        }
                    } else {
                        CharSequence arrayLengthSnippet;
                        CharSequence arrayLengthParameter = getArrayLengthParameterName(out);
                        if (arrayLengthParameter != null) {
                            arrayLengthSnippet = arrayLengthParameter;
                            CharSequence arrayOffsetParameter = getArrayOffsetParameterName(out);
                            if (arrayOffsetParameter != null) {
                                parameterValueOverride.put(arrayOffsetParameter.toString(), "0");
                            }
                        } else {
                            arrayLengthSnippet = new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "GetArrayLength", jniEnvFieldName, parameterName).build();
                        }
                        currentBuilder.newArray(((ArrayType) parameterType).getComponentType(), arrayLengthSnippet).lineEnd(";");
                    }
                    parameterValueOverride.put(parameterName.toString(), outArrayLocal(parameterName));
                }
                return hasDirectionModifiers;
            }

            @Override
            void postUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName, CharSequence resultVariableName) {
                if (isArrayWithDirectionModifiers(parameterType)) {
                    AnnotationMirror out = findOut(marshallerData.annotations);
                    if (out != null) {
                        CharSequence arrayLengthSnippet;
                        boolean trimToResult = trimToResult(out);
                        CharSequence arrayLengthParameter = getArrayLengthParameterName(out);
                        if (trimToResult) {
                            arrayLengthSnippet = resultVariableName;
                        } else if (arrayLengthParameter != null) {
                            arrayLengthSnippet = arrayLengthParameter;
                        } else {
                            arrayLengthSnippet = new CodeBuilder(currentBuilder).memberSelect(outArrayLocal(parameterName), "length", false).build();
                        }
                        CharSequence arrayOffsetSnippet;
                        CharSequence arrayOffsetParameter = getArrayOffsetParameterName(out);
                        if (arrayOffsetParameter != null) {
                            arrayOffsetSnippet = arrayOffsetParameter;
                        } else {
                            arrayOffsetSnippet = "0";
                        }
                        CodeBuilder copySnippet = new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "arrayCopy",
                                        jniEnvFieldName, outArrayLocal(parameterName), "0", parameterName, arrayOffsetSnippet, arrayLengthSnippet);
                        if (trimToResult) {
                            currentBuilder.lineStart("if (").write(resultVariableName).lineEnd(" > 0) {");
                            currentBuilder.indent();
                            currentBuilder.lineStart(copySnippet.build()).lineEnd(";");
                            currentBuilder.dedent();
                            currentBuilder.line("}");
                        } else {
                            currentBuilder.lineStart(copySnippet.build()).lineEnd(";");
                        }
                    }
                }
            }
        }

        private static final class ReferenceSnippets extends HotSpotToNativeMarshallerSnippets {

            private final DefinitionData data;

            ReferenceSnippets(DefinitionData data, MarshallerData marshallerData, Types types, TypeCache cache) {
                super(marshallerData, types, cache);
                this.data = data;
            }

            @Override
            TypeMirror getEndPointMethodParameterType(TypeMirror type) {
                return types.getPrimitiveType(TypeKind.LONG);
            }

            @Override
            Set<CharSequence> getEndPointSuppressedWarnings(CodeBuilder currentBuilder, TypeMirror type) {
                if (isParameterizedType(type)) {
                    return Collections.singleton("unchecked");
                } else {
                    return Collections.emptySet();
                }
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence jniEnvFieldName) {
                CodeBuilder receiver;
                if (types.isSubtype(marshallerData.forType, cache.nativeObject)) {
                    receiver = new CodeBuilder(currentBuilder).write("((").write(cache.nativeObject).write(")").write(formalParameter).write(")");
                } else {
                    CharSequence cast = new CodeBuilder(currentBuilder).cast(marshallerData.forType, formalParameter).build();
                    receiver = new CodeBuilder(currentBuilder).memberSelect(cast, marshallerData.nonDefaultReceiver.getSimpleName(), true);
                }
                return new CodeBuilder(currentBuilder).invoke(receiver.build(), "getHandle").build();
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence jniEnvFieldName) {
                CharSequence isolateSnippet = new CodeBuilder(currentBuilder).invoke(receiver, "getIsolate").build();
                List<CharSequence> args = new ArrayList<>(Arrays.asList(
                                isolateSnippet,
                                invocationSnippet));
                boolean hasGeneratedFactory = !marshallerData.annotations.isEmpty();
                if (hasGeneratedFactory) {
                    if (!types.isSubtype(marshallerData.forType, cache.nativeObject)) {
                        args = Collections.singletonList(new CodeBuilder(currentBuilder).newInstance(cache.nativeObject, args.toArray(new CharSequence[args.size()])).build());
                    }
                    CharSequence type = new CodeBuilder(currentBuilder).write(types.erasure(marshallerData.forType)).write("Gen").build();
                    return new CodeBuilder(currentBuilder).invoke(type,
                                    "createHotSpotToNative", args.toArray(new CharSequence[args.size()])).build();
                } else {
                    return new CodeBuilder(currentBuilder).newInstance((DeclaredType) types.erasure(marshallerData.forType),
                                    args.toArray(new CharSequence[args.size()])).build();
                }
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invokeStatic(cache.nativeObjectHandles, "create", invocationSnippet).build();
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName) {
                TypeMirror receiverType = marshallerData.useReceiverResolver ? data.receiverAccessor.getParameters().get(0).asType() : parameterType;
                CharSequence classLiteral = new CodeBuilder(currentBuilder).classLiteral(receiverType).build();
                CodeBuilder result = new CodeBuilder(currentBuilder).invokeStatic(cache.nativeObjectHandles, "resolve", parameterName, classLiteral);
                if (marshallerData.useReceiverResolver) {
                    result = new CodeBuilder(result).invokeStatic(data.annotatedType, data.receiverAccessor.getSimpleName(), result.build());
                }
                return result.build();
            }
        }

        private static final class CustomSnippets extends HotSpotToNativeMarshallerSnippets {

            CustomSnippets(MarshallerData marshallerData, Types types, TypeCache cache) {
                super(marshallerData, types, cache);
            }

            @Override
            TypeMirror getEndPointMethodParameterType(TypeMirror type) {
                return cache.object;
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "marshall", formalParameter).build();
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "unmarshall", invocationSnippet).build();
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "marshall", jniEnvFieldName, invocationSnippet).build();
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "unmarshall", jniEnvFieldName, parameterName).build();
            }
        }
    }
}
