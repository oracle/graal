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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.nativebridge.processor.AbstractBridgeGenerator.CodeBuilder.Parameter;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MarshallerData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MethodData;
import org.graalvm.nativebridge.processor.NativeToHotSpotBridgeParser.TypeCache;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

public class NativeToHotSpotBridgeGenerator extends AbstractBridgeGenerator {

    private static final String END_POINT_SIMPLE_NAME = "NativeToHotSpotEndPoint";
    private static final String END_POINT_CLASS_FIELD = "endPointClass";
    private static final String JNI_CONFIG_FIELD = "jniConfig";
    private static final String HOTSPOT_CALLS_FIELD = "hotSpotCalls";
    private static final String START_POINT_SIMPLE_NAME = "NativeToHotSpotStartPoint";
    private static final String START_POINT_FACTORY_NAME = "createNativeToHotSpot";
    private static final String EXCEPTION_HANDLER_IMPL_NAME = "ExceptionHandlerImpl";

    private final TypeCache typeCache;

    NativeToHotSpotBridgeGenerator(NativeToHotSpotBridgeParser parser, TypeCache typeCache) {
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
    MarshallerSnippets marshallerSnippets(DefinitionData data, MarshallerData marshallerData) {
        return NativeToHotSpotMarshallerSnippets.forData(data, marshallerData, types, typeCache);
    }

    private void generateTopLevel(CodeBuilder builder, DefinitionData data) {
        builder.classStart(EnumSet.of(Modifier.FINAL), data.getTargetClassSimpleName(), null, Collections.emptyList());
        builder.indent();
        builder.lineEnd("");
        FactoryMethodInfo factoryMethod = generateStartPointFactory(builder, data, Collections.emptySet(),
                        START_POINT_SIMPLE_NAME, START_POINT_FACTORY_NAME, CodeBuilder.newParameter(typeCache.jniEnv, "jniEnv"));
        builder.lineEnd("");

        generateNativeToHSStartPoint(builder, data, factoryMethod);
        builder.lineEnd("");
        generateNativeToHSEndPoint(builder, data);
        if (data.exceptionHandler != null) {
            builder.lineEnd("");
            generateExceptionHandlerImpl(builder, data);
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateNativeToHSStartPoint(CodeBuilder builder, DefinitionData data, FactoryMethodInfo factoryMethodInfo) {
        CacheSnippets cacheSnippets = cacheSnippets(data);
        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), START_POINT_SIMPLE_NAME,
                        data.annotatedType, Collections.emptyList());
        builder.indent();
        builder.lineEnd("");
        if (data.exceptionHandler != null) {
            generateHotSpotCallsField(builder);
        }
        generateMarshallerFields(builder, data, typeCache.jniNativeMarshaller, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

        if (!data.getAllCustomMarshallers().isEmpty()) {
            builder.methodStart(EnumSet.of(Modifier.STATIC), null,
                            null, Collections.emptyList(), Collections.emptyList());
            builder.indent();
            builder.lineStart().write(typeCache.jniConfig).write(" config = ").invokeStatic(data.jniConfig, "getInstance").lineEnd(";");
            generateMarshallerLookups(builder, data, null, true, typeCache.jniNativeMarshaller);
            builder.dedent();
            builder.line("}");
            builder.line("");
        }

        if (!data.toGenerate.isEmpty()) {
            builder.classStart(EnumSet.of(Modifier.STATIC, Modifier.FINAL), "JNIData",
                            null, Collections.emptyList());
            builder.indent();
            builder.emptyLine();
            builder.lineStart().writeModifiers(EnumSet.of(Modifier.STATIC)).space().write("JNIData").space().write("cache_").lineEnd(";");
            generateJNIFields(builder, data);
            builder.emptyLine();
            Parameter jniEnv = CodeBuilder.newParameter(typeCache.jniEnv, "jniEnv");
            builder.methodStart(Collections.emptySet(), "JNIData", null,
                            Arrays.asList(jniEnv), Collections.emptyList());
            builder.indent();
            generateJNIFieldsInit(builder, data, jniEnv);
            builder.dedent();
            builder.line("}");

            builder.dedent();
            builder.line("}");

            builder.emptyLine();
            builder.lineStart().writeModifiers(EnumSet.of(Modifier.FINAL)).space().write("JNIData").space().write("jniMethods_").lineEnd(";");
        }

        generateCacheFields(builder, cacheSnippets, data);

        builder.emptyLine();

        builder.methodStart(Collections.emptySet(), START_POINT_SIMPLE_NAME, null,
                        factoryMethodInfo.parameters, Collections.emptyList());
        builder.indent();
        builder.lineStart().call("super", parameterNames(factoryMethodInfo.superCallParameters)).lineEnd(";");

        if (!data.toGenerate.isEmpty()) {
            builder.line("JNIData localJNI = JNIData.cache_;");
            builder.line("if (localJNI == null) {");
            Parameter foundJNIEnv = findParameterOfType(typeCache.jniEnv, factoryMethodInfo.parameters);
            builder.indent().lineStart();
            builder.write("localJNI = JNIData.cache_ = new JNIData(").write(foundJNIEnv.name).write(")");
            builder.lineEnd(";").dedent();
            builder.line("}");
            builder.lineStart().write("this.jniMethods_ = localJNI").lineEnd(";");
        }

        generateCacheFieldsInit(builder, cacheSnippets, data);
        builder.dedent();
        builder.line("}");

        for (MethodData methodData : data.toGenerate) {
            generateNativeToHSStartMethod(builder, cacheSnippets(data), data, methodData);
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateHotSpotCallsField(CodeBuilder builder) {
        Set<Modifier> modSet = EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
        CodeBuilder handlerImplSnippet = new CodeBuilder(builder).newInstance(EXCEPTION_HANDLER_IMPL_NAME);
        builder.lineStart().writeModifiers(modSet).space().write(typeCache.hotSpotCalls).space().write(HOTSPOT_CALLS_FIELD).write(" = ").invokeStatic(typeCache.hotSpotCalls,
                        "createWithExceptionHandler", handlerImplSnippet.build()).lineEnd(";");
    }

    private CodeBuilder.Parameter findParameterOfType(TypeMirror requiredType, Collection<CodeBuilder.Parameter> parameters) {
        for (CodeBuilder.Parameter parameter : parameters) {
            if (types.isSameType(requiredType, parameter.type)) {
                return parameter;
            }
        }
        return null;
    }

    private void generateJNIFields(CodeBuilder builder, DefinitionData data) {
        Set<Modifier> modSet = EnumSet.of(Modifier.FINAL);
        builder.lineStart().writeModifiers(modSet).space().write(typeCache.jClass).space().write(END_POINT_CLASS_FIELD).lineEnd(";");
        for (MethodData methodData : data.toGenerate) {
            CharSequence fieldName = jMethodIdField(methodData);
            builder.lineStart().writeModifiers(modSet).space().write(typeCache.jNIMethod).space().write(fieldName).lineEnd(";");
        }
    }

    private void generateJNIFieldsInit(CodeBuilder builder, DefinitionData data, CodeBuilder.Parameter jniEnv) {
        builder.lineStart("this." + END_POINT_CLASS_FIELD).write(" = ").invokeStatic(typeCache.jNIClassCache, "lookupClass",
                        jniEnv.name, END_POINT_SIMPLE_NAME + ".class").lineEnd(";");
        for (MethodData methodData : data.toGenerate) {
            CharSequence fieldName = jMethodIdField(methodData);
            List<? extends TypeMirror> parameterTypes = methodData.type.getParameterTypes();
            List<CharSequence> signature = new ArrayList<>(1 + parameterTypes.size());
            TypeMirror endPointParameterType = marshallerSnippets(data, methodData.getReturnTypeMarshaller()).getEndPointMethodParameterType(methodData.type.getReturnType());
            signature.add(new CodeBuilder(builder).classLiteral(endPointParameterType).build());
            if (!data.hasExplicitReceiver()) {
                signature.add(new CodeBuilder(builder).classLiteral(data.serviceType).build());
            }
            for (int i = 0; i < parameterTypes.size(); i++) {
                TypeMirror parameterType = parameterTypes.get(i);
                endPointParameterType = marshallerSnippets(data, methodData.getParameterMarshaller(i)).getEndPointMethodParameterType(parameterType);
                signature.add(new CodeBuilder(builder).classLiteral(endPointParameterType).build());
            }
            builder.lineStart("this." + fieldName).write(" = ").invokeStatic(typeCache.jNIMethod, "findMethod",
                            jniEnv.name, END_POINT_CLASS_FIELD, "true",
                            '"' + methodData.element.getSimpleName().toString() + '"',
                            new CodeBuilder(builder).invokeStatic(typeCache.jniUtil, " encodeMethodSignature", signature.toArray(new CharSequence[0])).build()).lineEnd(";");
        }
    }

    private void generateNativeToHSStartMethod(CodeBuilder builder, CacheSnippets cacheSnippets, DefinitionData data, MethodData methodData) {
        builder.line("");
        overrideMethod(builder, methodData);
        builder.indent();
        CharSequence receiver;
        int nonReceiverParameterStart;
        if (data.hasExplicitReceiver()) {
            receiver = methodData.element.getParameters().get(0).getSimpleName();
            nonReceiverParameterStart = 1;
        } else {
            receiver = data.endPointHandle != null ? data.endPointHandle.getSimpleName() : "this";
            nonReceiverParameterStart = 0;
        }
        AbstractBridgeParser.CacheData cacheData = methodData.cachedData;
        CharSequence args;
        CharSequence env;
        if (cacheData != null) {
            String cacheFieldName = cacheData.cacheFieldName;
            String resultVariable = cacheFieldName + "Result";
            builder.lineStart().write(cacheData.cacheEntryType).space().write(resultVariable).write(" = ").write(cacheSnippets.readCache(builder, cacheFieldName, receiver)).lineEnd(";");
            builder.lineStart("if (").write(resultVariable).lineEnd(" == null) {");
            builder.indent();
            env = generateLookupJNIEnv(builder);
            Map<String, CharSequence> parameterValueOverrides = new HashMap<>();
            boolean hasPostMarshall = generatePreMarshall(builder, data, methodData, nonReceiverParameterStart, env, parameterValueOverrides);
            args = generatePushArgs(builder, data, methodData, nonReceiverParameterStart, env, receiver, parameterValueOverrides);
            builder.lineStart().write(resultVariable).write(" = ");
            generateCallHotSpot(builder, data, methodData, env, args, receiver);
            builder.lineEnd(";");
            if (hasPostMarshall) {
                generatePostMarshall(builder, data, methodData, nonReceiverParameterStart, env, resultVariable);
            }
            builder.lineStart().write(cacheSnippets.writeCache(builder, cacheFieldName, receiver, resultVariable)).lineEnd(";");
            builder.dedent();
            builder.line("}");
            builder.lineStart("return ").write(resultVariable).lineEnd(";");
        } else {
            env = generateLookupJNIEnv(builder);
            Map<String, CharSequence> parameterValueOverrides = new HashMap<>();
            boolean hasPostMarshall = generatePreMarshall(builder, data, methodData, nonReceiverParameterStart, env, parameterValueOverrides);
            args = generatePushArgs(builder, data, methodData, nonReceiverParameterStart, env, receiver, parameterValueOverrides);
            builder.lineStart();
            TypeMirror returnType = methodData.type.getReturnType();
            CharSequence resultVariable = null;
            if (returnType.getKind() != TypeKind.VOID) {
                if (hasPostMarshall) {
                    resultVariable = "result";
                    builder.write(returnType).space().write(resultVariable).write(" = ");
                } else {
                    builder.write("return ");
                }
            }
            generateCallHotSpot(builder, data, methodData, env, args, receiver);
            builder.lineEnd(";");
            if (hasPostMarshall) {
                generatePostMarshall(builder, data, methodData, nonReceiverParameterStart, env, resultVariable);
            }
            if (resultVariable != null) {
                // Return was deferred after post unmarshalling statements.
                // Do it now.
                builder.lineStart("return ").write(resultVariable).lineEnd(";");
            }
        }
        builder.dedent();
        builder.line("}");
    }

    private CharSequence generateLookupJNIEnv(CodeBuilder builder) {
        CharSequence res = "jniEnv";
        builder.lineStart().write(typeCache.jniEnv).space().write(res).write(" = ").invokeStatic(typeCache.jniMethodScope, "env").lineEnd(";");
        return res;
    }

    private boolean generatePreMarshall(CodeBuilder builder, DefinitionData data, MethodData methodData, int nonReceiverParameterStart,
                    CharSequence jniEnv, Map<String, CharSequence> parameterValueOverrides) {
        boolean hasPostMarshall = false;
        List<? extends VariableElement> formalParameters = methodData.element.getParameters();
        List<? extends TypeMirror> formalParameterTypes = methodData.type.getParameterTypes();
        for (int i = nonReceiverParameterStart; i < formalParameters.size(); i++) {
            MarshallerData marshaller = methodData.getParameterMarshaller(i);
            hasPostMarshall |= marshallerSnippets(data, marshaller).preMarshallParameter(builder, formalParameterTypes.get(i),
                            formalParameters.get(i).getSimpleName(), jniEnv, parameterValueOverrides);
        }
        return hasPostMarshall;
    }

    private void generatePostMarshall(CodeBuilder builder, DefinitionData data, MethodData methodData, int nonReceiverParameterStart,
                    CharSequence jniEnv, CharSequence resultVariable) {
        List<? extends VariableElement> formalParameters = methodData.element.getParameters();
        List<? extends TypeMirror> formalParameterTypes = methodData.type.getParameterTypes();
        for (int i = nonReceiverParameterStart; i < formalParameters.size(); i++) {
            MarshallerData marshaller = methodData.getParameterMarshaller(i);
            marshallerSnippets(data, marshaller).postMarshallParameter(builder, formalParameterTypes.get(i),
                            formalParameters.get(i).getSimpleName(), jniEnv, resultVariable);
        }
    }

    private CharSequence generatePushArgs(CodeBuilder builder, DefinitionData data, MethodData methodData, int nonReceiverParameterStart,
                    CharSequence jniEnv, CharSequence receiver, Map<String, CharSequence> parameterValueOverrides) {
        String jniArgs = "jniArgs";
        List<? extends VariableElement> parameters = methodData.element.getParameters();
        int argumentCount = parameters.size() + (data.hasExplicitReceiver() ? 0 : 1);
        CodeBuilder jValueClassLiteral = new CodeBuilder(builder).classLiteral(typeCache.jValue);
        builder.lineStart().write(typeCache.jValue).space().write(jniArgs).write(" = ").invokeStatic(typeCache.stackValue, "get", Integer.toString(argumentCount),
                        jValueClassLiteral.build()).lineEnd(";");
        CharSequence address = new CodeBuilder(builder).invoke(jniArgs, "addressOf", "0").build();
        CharSequence receiverHSObject;
        if (data.hasExplicitReceiver()) {
            receiverHSObject = new CodeBuilder(builder).cast(typeCache.hSObject, receiver, true).build();
        } else {
            receiverHSObject = receiver;
        }
        CharSequence value = new CodeBuilder(builder).invoke(receiverHSObject, "getHandle").build();
        builder.lineStart().invoke(address, "setJObject", value).lineEnd(";");
        List<? extends VariableElement> formalParameters = methodData.element.getParameters();
        List<? extends TypeMirror> formalParameterTypes = methodData.type.getParameterTypes();
        for (int i = nonReceiverParameterStart, j = 1; i < formalParameters.size(); i++, j++) {
            address = new CodeBuilder(builder).invoke(jniArgs, "addressOf", Integer.toString(j)).build();
            CharSequence parameterName = formalParameters.get(i).getSimpleName();
            CharSequence parameterValueOverride = parameterValueOverrides.get(parameterName.toString());
            if (parameterValueOverride != null) {
                value = parameterValueOverride;
            } else {
                MarshallerData marshaller = methodData.getParameterMarshaller(i);
                value = marshallerSnippets(data, marshaller).marshallParameter(builder,
                                formalParameterTypes.get(i), parameterName, jniEnv);
            }
            builder.lineStart().invoke(address, jValueSetterName(formalParameterTypes.get(i)), value).lineEnd(";");
        }
        return jniArgs;
    }

    private CharSequence jValueSetterName(TypeMirror type) {
        switch (types.erasure(type).getKind()) {
            case BOOLEAN:
                return "setBoolean";
            case BYTE:
                return "setByte";
            case CHAR:
                return "setChar";
            case SHORT:
                return "setShort";
            case INT:
                return "setInt";
            case LONG:
                return "setLong";
            case FLOAT:
                return "setFloat";
            case DOUBLE:
                return "setDouble";
            case ARRAY:
            case DECLARED:
                return "setJObject";
            default:
                throw new IllegalArgumentException(types.erasure(type).getKind().toString());
        }
    }

    private void generateCallHotSpot(CodeBuilder builder, DefinitionData data, MethodData methodData, CharSequence jniEnv, CharSequence args, CharSequence receiver) {
        CharSequence hsCallsInstance;
        if (data.exceptionHandler != null) {
            hsCallsInstance = HOTSPOT_CALLS_FIELD;
        } else {
            hsCallsInstance = new CodeBuilder(builder).invokeStatic(typeCache.hotSpotCalls, "getDefault").build();
        }
        CodeBuilder jniCallBuilder = new CodeBuilder(builder).invoke(hsCallsInstance, callHotSpotName(methodData.type.getReturnType()), jniEnv, "jniMethods_." + END_POINT_CLASS_FIELD,
                        "jniMethods_." + jMethodIdField(methodData), args);
        builder.write(marshallerSnippets(data, methodData.getReturnTypeMarshaller()).unmarshallResult(builder, methodData.type.getReturnType(), jniCallBuilder.build(), receiver, jniEnv));
    }

    private CharSequence callHotSpotName(TypeMirror type) {
        switch (types.erasure(type).getKind()) {
            case VOID:
                return "callStaticVoid";
            case BOOLEAN:
                return "callStaticBoolean";
            case BYTE:
                return "callStaticByte";
            case CHAR:
                return "callStaticChar";
            case SHORT:
                return "callStaticShort";
            case INT:
                return "callStaticInt";
            case LONG:
                return "callStaticLong";
            case FLOAT:
                return "callStaticFloat";
            case DOUBLE:
                return "callStaticDouble";
            case ARRAY:
            case DECLARED:
                return "callStaticJObject";
            default:
                throw new IllegalArgumentException(types.erasure(type).getKind().toString());
        }
    }

    private static CharSequence jMethodIdField(MethodData methodData) {
        StringBuilder name = new StringBuilder(methodData.element.getSimpleName());
        if (methodData.hasOverload()) {
            name.append(methodData.overloadId);
        }
        name.append("Method");
        return name;
    }

    private void generateNativeToHSEndPoint(CodeBuilder builder, DefinitionData data) {
        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC), END_POINT_SIMPLE_NAME, null, Collections.emptyList());
        builder.indent();
        builder.lineEnd("");

        generateMarshallerFields(builder, data, typeCache.jniHotSpotMarshaller, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

        if (!data.getAllCustomMarshallers().isEmpty()) {
            builder.methodStart(EnumSet.of(Modifier.STATIC), null,
                            null, Collections.emptyList(), Collections.emptyList());
            builder.indent();
            builder.lineStart().write(typeCache.jniConfig).write(" config = ").invokeStatic(data.jniConfig, "getInstance").lineEnd(";");
            generateMarshallerLookups(builder, data, null, true, typeCache.jniHotSpotMarshaller);
            builder.dedent();
            builder.line("}");
        }

        for (MethodData methodData : data.toGenerate) {
            generateNativeToHSEndMethod(builder, data, methodData);
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateNativeToHSEndMethod(CodeBuilder builder, DefinitionData data, MethodData methodData) {
        builder.lineEnd("");
        List<? extends VariableElement> methodParameters = methodData.element.getParameters();
        List<? extends TypeMirror> methodParameterTypes = methodData.type.getParameterTypes();
        Set<CharSequence> warnings = new TreeSet<>(Comparator.comparing(CharSequence::toString));
        for (int i = 0; i < methodParameters.size(); i++) {
            warnings.addAll(marshallerSnippets(data, methodData.getParameterMarshaller(i)).getEndPointSuppressedWarnings(builder, methodParameterTypes.get(i)));
        }
        if (!warnings.isEmpty()) {
            builder.lineStart().annotation(typeCache.suppressWarnings, warnings.toArray(new CharSequence[warnings.size()])).lineEnd("");
        }
        List<CodeBuilder.Parameter> params = new ArrayList<>();
        if (!data.hasExplicitReceiver()) {
            params.add(CodeBuilder.newParameter(data.serviceType, "receiverObject"));
        }
        for (int i = 0; i < methodParameters.size(); i++) {
            TypeMirror parameterType = marshallerSnippets(data, methodData.getParameterMarshaller(i)).getEndPointMethodParameterType(methodParameterTypes.get(i));
            params.add(CodeBuilder.newParameter(parameterType, methodParameters.get(i).getSimpleName()));
        }
        CharSequence methodName = methodData.element.getSimpleName();
        TypeMirror returnType = marshallerSnippets(data, methodData.getReturnTypeMarshaller()).getEndPointMethodParameterType(methodData.type.getReturnType());
        boolean voidReturnType = returnType.getKind() == TypeKind.VOID;
        builder.lineStart().annotation(typeCache.suppressWarnings, "unused").lineEnd("");
        builder.methodStart(EnumSet.of(Modifier.STATIC), methodName, returnType, params, methodData.type.getThrownTypes());
        builder.indent();
        // Encode arguments
        CharSequence[] actualParameters = new CharSequence[methodParameters.size()];
        int nonReceiverParameterStart;
        if (data.hasExplicitReceiver()) {
            actualParameters[0] = "receiverObject";
            nonReceiverParameterStart = 1;
        } else {
            nonReceiverParameterStart = 0;
        }
        for (int i = nonReceiverParameterStart; i < methodParameters.size(); i++) {
            actualParameters[i] = marshallerSnippets(data, methodData.getParameterMarshaller(i)).unmarshallParameter(builder, methodParameterTypes.get(i), methodParameters.get(i).getSimpleName(),
                            null);
        }
        CharSequence resolvedDispatch;
        if (data.hasExplicitReceiver()) {
            CharSequence explicitReceiver = methodParameters.get(0).getSimpleName();
            resolvedDispatch = "resolvedDispatch";
            builder.lineStart().write(data.serviceType).space().write(resolvedDispatch).write(" = ").invokeStatic(data.annotatedType, data.delegateAccessor.getSimpleName(), explicitReceiver).lineEnd(
                            ";");
            builder.lineStart().write(typeCache.object).space().write("receiverObject").write(" = ").invokeStatic(data.annotatedType, data.receiverAccessor.getSimpleName(), explicitReceiver).lineEnd(
                            ";");
        } else {
            resolvedDispatch = "receiverObject";
        }
        CharSequence resultSnippet = new CodeBuilder(builder).invoke(resolvedDispatch, methodData.receiverMethod != null ? methodData.receiverMethod : methodName, actualParameters).build();
        resultSnippet = marshallerSnippets(data, methodData.getReturnTypeMarshaller()).marshallResult(builder, returnType, resultSnippet, null);
        if (voidReturnType) {
            builder.lineStart().write(resultSnippet).lineEnd(";");
        } else {
            builder.lineStart("return ").write(resultSnippet).lineEnd(";");
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateExceptionHandlerImpl(CodeBuilder builder, DefinitionData data) {
        ExecutableElement handleExceptionMethod = ElementFilter.methodsIn(typeCache.jNIExceptionHandler.asElement().getEnclosedElements()).stream().filter(
                        (e) -> "handleException".contentEquals(e.getSimpleName())).findAny().get();
        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), EXCEPTION_HANDLER_IMPL_NAME,
                        null, Collections.singletonList(typeCache.jNIExceptionHandler));
        builder.indent();
        builder.lineEnd("");
        // Implement ExceptionHandler#handleException method
        overrideMethod(builder, handleExceptionMethod, (ExecutableType) handleExceptionMethod.asType());
        builder.indent();
        builder.lineStart().write(types.getPrimitiveType(TypeKind.BOOLEAN)).space().write("exceptionHandled").lineEnd(";");
        builder.line("try {");
        builder.indent();
        builder.lineStart("exceptionHandled").write(" = ").invokeStatic((DeclaredType) data.exceptionHandler.getEnclosingElement().asType(), data.exceptionHandler.getSimpleName(), "context").lineEnd(
                        ";");
        builder.dedent();
        builder.lineStart("} catch (").write(typeCache.throwable).space().write("throwable").lineEnd(") {");
        builder.indent();
        builder.lineStart("throw").space().invoke(null, "silenceException", new CodeBuilder(builder).classLiteral(typeCache.runtimeException).build(), "throwable").lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.line("if (!exceptionHandled) {");
        builder.indent();
        builder.lineStart().invoke("context", "throwJNIExceptionWrapper").lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
        // Generate helper silenceException method
        builder.lineEnd("");
        builder.lineStart().annotation(typeCache.suppressWarnings, new CharSequence[]{"unchecked", "unused"}).lineEnd("");
        builder.line("private static <E extends Throwable> E silenceException(Class<E> type, Throwable throwable) throws E {");
        builder.indent();
        builder.line("throw (E) throwable;");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
    }

    private abstract static class NativeToHotSpotMarshallerSnippets extends MarshallerSnippets {

        final TypeCache cache;

        NativeToHotSpotMarshallerSnippets(MarshallerData marshallerData, Types types, TypeCache typeCache) {
            super(marshallerData, types, typeCache);
            this.cache = typeCache;
        }

        static NativeToHotSpotMarshallerSnippets forData(DefinitionData data, MarshallerData marshallerData, Types types, TypeCache typeCache) {
            switch (marshallerData.kind) {
                case VALUE:
                    return new DirectSnippets(marshallerData, types, typeCache);
                case REFERENCE:
                    return new ReferenceSnippets(data, marshallerData, types, typeCache);
                case CUSTOM:
                    return new CustomSnippets(marshallerData, types, typeCache);
                default:
                    throw new IllegalArgumentException(String.valueOf(marshallerData.kind));
            }
        }

        private static final class DirectSnippets extends NativeToHotSpotMarshallerSnippets {

            DirectSnippets(MarshallerData marshallerData, Types types, TypeCache cache) {
                super(marshallerData, types, cache);
            }

            @Override
            TypeMirror getEndPointMethodParameterType(TypeMirror type) {
                return type;
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence jniEnvFieldName) {
                if (types.isSameType(cache.string, parameterType)) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createHSString", jniEnvFieldName, formalParameter).build();
                } else if (parameterType.getKind() == TypeKind.ARRAY) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createHSArray", jniEnvFieldName, formalParameter).build();
                } else {
                    return formalParameter;
                }
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence jniEnvFieldName) {
                if (types.isSameType(cache.string, resultType)) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createString", jniEnvFieldName, invocationSnippet).build();
                } else if (resultType.getKind() == TypeKind.ARRAY) {
                    CodeBuilder castBuilder = new CodeBuilder(currentBuilder).cast(jniTypeForJavaType(resultType, types, cache), invocationSnippet);
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createArray", jniEnvFieldName, castBuilder.build()).build();
                } else {
                    return invocationSnippet;
                }
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
                return invocationSnippet;
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName) {
                return parameterName;
            }

            @Override
            boolean preMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverride) {
                boolean hasDirectionModifiers = isArrayWithDirectionModifiers(parameterType);
                if (hasDirectionModifiers) {
                    TypeMirror jniType = jniTypeForJavaType(parameterType, types, cache);
                    currentBuilder.lineStart().write(jniType).space().write(outArrayLocal(parameterName)).write(" = ");
                    AnnotationMirror in = findIn(marshallerData.annotations);
                    AnnotationMirror out = findOut(marshallerData.annotations);
                    if (in != null) {
                        CharSequence arrayLengthParameter = getArrayLengthParameterName(in);
                        if (arrayLengthParameter != null) {
                            currentBuilder.invokeStatic(cache.jniUtil, arrayFactoryMethodName(jniType), jniEnvFieldName, arrayLengthParameter).lineEnd(";");
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
                            currentBuilder.invokeStatic(cache.jniUtil, "createHSArray", jniEnvFieldName, parameterName).lineEnd(";");
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
                            arrayLengthSnippet = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                        }
                        currentBuilder.invokeStatic(cache.jniUtil, arrayFactoryMethodName(jniType), jniEnvFieldName, arrayLengthSnippet).lineEnd(";");
                    }
                    parameterValueOverride.put(parameterName.toString(), outArrayLocal(parameterName));
                }
                return hasDirectionModifiers;
            }

            @Override
            void postMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName, CharSequence resultVariableName) {
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
                            arrayLengthSnippet = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
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

            private static CharSequence arrayFactoryMethodName(TypeMirror jniArrayType) {
                TypeKind kind = jniArrayType.getKind();
                if (kind != TypeKind.DECLARED) {
                    throw new IllegalArgumentException("Expected declared type, got " + String.valueOf(jniArrayType) + " of kind " + kind);
                }
                String simpleName = ((DeclaredType) jniArrayType).asElement().getSimpleName().toString();
                if (simpleName.charAt(0) != 'J') {
                    throw new IllegalArgumentException("Expected type with J(.)+Array simple name pattern, got " + simpleName);
                }
                return "New" + simpleName.substring(1);
            }
        }

        private static final class ReferenceSnippets extends NativeToHotSpotMarshallerSnippets {

            private final DefinitionData data;

            ReferenceSnippets(DefinitionData data, MarshallerData marshallerData, Types types, TypeCache cache) {
                super(marshallerData, types, cache);
                this.data = data;
            }

            @Override
            TypeMirror getEndPointMethodParameterType(TypeMirror type) {
                return cache.object;
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
                if (types.isSubtype(marshallerData.forType, cache.hSObject)) {
                    receiver = new CodeBuilder(currentBuilder).write("((").write(cache.hSObject).write(")").write(formalParameter).write(")");
                } else {
                    CharSequence cast = new CodeBuilder(currentBuilder).cast(marshallerData.forType, formalParameter).build();
                    receiver = new CodeBuilder(currentBuilder).memberSelect(cast, marshallerData.nonDefaultReceiver.getSimpleName(), true);
                }
                return new CodeBuilder(currentBuilder).invoke(receiver.build(), "getHandle").build();
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence jniEnvFieldName) {
                List<CharSequence> args = new ArrayList<>(Arrays.asList(
                                jniEnvFieldName,
                                invocationSnippet));
                boolean hasGeneratedFactory = !marshallerData.annotations.isEmpty();
                if (hasGeneratedFactory) {
                    if (types.isSubtype(marshallerData.forType, cache.hSObject)) {
                        args.add(JNI_CONFIG_FIELD);
                    } else {
                        List<CharSequence> newArgs = new ArrayList<>();
                        newArgs.add(new CodeBuilder(currentBuilder).newInstance(cache.hSObject, args.toArray(new CharSequence[args.size()])).build());
                        if (needsJNIEnv((DeclaredType) marshallerData.forType)) {
                            newArgs.add(jniEnvFieldName);
                        }
                        newArgs.add(JNI_CONFIG_FIELD);
                        args = newArgs;
                    }
                    CharSequence type = new CodeBuilder(currentBuilder).write(types.erasure(marshallerData.forType)).write("Gen").build();
                    return new CodeBuilder(currentBuilder).invoke(type,
                                    "createNativeToHotSpot", args.toArray(new CharSequence[args.size()])).build();
                } else {
                    return new CodeBuilder(currentBuilder).newInstance((DeclaredType) types.erasure(marshallerData.forType),
                                    args.toArray(new CharSequence[args.size()])).build();
                }
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
                return invocationSnippet;
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName) {
                TypeMirror receiverType = marshallerData.useReceiverResolver ? data.receiverAccessor.getParameters().get(0).asType() : parameterType;
                CodeBuilder result = new CodeBuilder(currentBuilder).cast(receiverType, parameterName);
                if (marshallerData.useReceiverResolver) {
                    result = new CodeBuilder(result).invokeStatic(data.annotatedType, data.receiverAccessor.getSimpleName(), result.build());
                }
                return result.build();
            }

            private boolean needsJNIEnv(DeclaredType type) {
                for (ExecutableElement executable : ElementFilter.constructorsIn(type.asElement().getEnclosedElements())) {
                    List<? extends VariableElement> parameters = executable.getParameters();
                    if (parameters.size() >= 2 && types.isAssignable(cache.hSObject, parameters.get(0).asType()) &&
                                    types.isAssignable(cache.jniEnv, parameters.get(1).asType())) {
                        return true;
                    }
                }
                return false;
            }
        }

        private static final class CustomSnippets extends NativeToHotSpotMarshallerSnippets {

            CustomSnippets(MarshallerData marshallerData, Types types, TypeCache cache) {
                super(marshallerData, types, cache);
            }

            @Override
            TypeMirror getEndPointMethodParameterType(TypeMirror type) {
                return cache.object;
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "marshall", jniEnvFieldName, formalParameter).build();
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "unmarshall", jniEnvFieldName, invocationSnippet).build();
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "marshall", invocationSnippet).build();
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "unmarshall", parameterName).build();
            }
        }
    }
}
