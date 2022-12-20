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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.nativebridge.processor.CodeBuilder.Parameter;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MarshallerData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MethodData;
import org.graalvm.nativebridge.processor.NativeToHotSpotBridgeParser.TypeCache;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

public class NativeToHotSpotBridgeGenerator extends AbstractBridgeGenerator {

    private static final String END_POINT_SIMPLE_NAME = "EndPoint";
    private static final String END_POINT_CLASS_FIELD = "endPointClass";
    private static final String START_POINT_SIMPLE_NAME = "StartPoint";
    static final String START_POINT_FACTORY_NAME = "createNativeToHS";
    private static final String REFERENCE_ISOLATE_ADDRESS_NAME = "referenceIsolateAddress";

    private final TypeCache typeCache;
    private final FactoryMethodInfo factoryMethod;

    NativeToHotSpotBridgeGenerator(NativeToHotSpotBridgeParser parser, TypeCache typeCache, DefinitionData definitionData) {
        super(parser, definitionData, typeCache, BinaryNameCache.create(definitionData, false, parser.types, parser.elements, typeCache));
        this.typeCache = typeCache;
        this.factoryMethod = resolveFactoryMethod(START_POINT_FACTORY_NAME, START_POINT_SIMPLE_NAME, END_POINT_SIMPLE_NAME, CodeBuilder.newParameter(typeCache.jniEnv, "jniEnv"));
    }

    @Override
    void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        generateStartPointFactory(builder, factoryMethod);
    }

    @Override
    void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        generateNativeToHSStartPoint(builder, factoryMethod);
        builder.lineEnd("");
        generateNativeToHSEndPoint(builder);
    }

    @Override
    MarshallerSnippets marshallerSnippets(MarshallerData marshallerData) {
        return NativeToHotSpotMarshallerSnippets.forData(parser.processor, definitionData, marshallerData, types, typeCache, binaryNameCache);
    }

    private void generateNativeToHSStartPoint(CodeBuilder builder, FactoryMethodInfo factoryMethodInfo) {
        CacheSnippets cacheSnippets = cacheSnippets();
        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), START_POINT_SIMPLE_NAME,
                        definitionData.annotatedType, Collections.emptyList());
        builder.indent();
        builder.lineEnd("");

        binaryNameCache.generateCache(builder);

        generateMarshallerFields(builder, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

        if (!definitionData.getAllCustomMarshallers().isEmpty()) {
            builder.methodStart(EnumSet.of(Modifier.STATIC), null,
                            null, Collections.emptyList(), Collections.emptyList());
            builder.indent();
            builder.lineStart().write(typeCache.jniConfig).write(" config = ").invokeStatic(definitionData.jniConfig, "getInstance").lineEnd(";");
            generateMarshallerLookups(builder);
            builder.dedent();
            builder.line("}");
            builder.line("");
        }

        if (!definitionData.toGenerate.isEmpty()) {
            builder.classStart(EnumSet.of(Modifier.STATIC, Modifier.FINAL), "JNIData",
                            null, Collections.emptyList());
            builder.indent();
            builder.emptyLine();
            builder.lineStart().writeModifiers(EnumSet.of(Modifier.STATIC)).space().write("JNIData").space().write("cache_").lineEnd(";");
            generateJNIFields(builder);
            builder.emptyLine();
            Parameter jniEnv = CodeBuilder.newParameter(typeCache.jniEnv, "jniEnv");
            builder.methodStart(Collections.emptySet(), "JNIData", null,
                            Collections.singletonList(jniEnv), Collections.emptyList());
            builder.indent();
            generateJNIFieldsInit(builder, jniEnv);
            builder.dedent();
            builder.line("}");

            builder.dedent();
            builder.classEnd();

            builder.emptyLine();
            builder.lineStart().writeModifiers(EnumSet.of(Modifier.FINAL)).space().write("JNIData").space().write("jniMethods_").lineEnd(";");
        }

        generateCacheFields(builder, cacheSnippets);

        builder.emptyLine();

        builder.methodStart(Collections.emptySet(), START_POINT_SIMPLE_NAME, null,
                        factoryMethodInfo.parameters, Collections.emptyList());
        builder.indent();
        builder.lineStart().call("super", parameterNames(factoryMethodInfo.superCallParameters)).lineEnd(";");

        if (!definitionData.toGenerate.isEmpty()) {
            builder.line("JNIData localJNI = JNIData.cache_;");
            builder.line("if (localJNI == null) {");
            Parameter foundJNIEnv = findParameterOfType(typeCache.jniEnv, factoryMethodInfo.parameters);
            builder.indent().lineStart();
            builder.write("localJNI = JNIData.cache_ = new JNIData(").write(foundJNIEnv.name).write(")");
            builder.lineEnd(";").dedent();
            builder.line("}");
            builder.lineStart().write("this.jniMethods_ = localJNI").lineEnd(";");
        }

        generateCacheFieldsInit(builder, cacheSnippets);
        builder.dedent();
        builder.line("}");

        for (MethodData methodData : definitionData.toGenerate) {
            generateNativeToHSStartMethod(builder, cacheSnippets(), methodData);
        }

        builder.dedent();
        builder.classEnd();
    }

    private CodeBuilder.Parameter findParameterOfType(TypeMirror requiredType, Collection<CodeBuilder.Parameter> parameters) {
        for (CodeBuilder.Parameter parameter : parameters) {
            if (types.isSameType(requiredType, parameter.type)) {
                return parameter;
            }
        }
        return null;
    }

    private void generateJNIFields(CodeBuilder builder) {
        Set<Modifier> modSet = EnumSet.of(Modifier.FINAL);
        builder.lineStart().writeModifiers(modSet).space().write(typeCache.jClass).space().write(END_POINT_CLASS_FIELD).lineEnd(";");
        for (MethodData methodData : definitionData.toGenerate) {
            CharSequence fieldName = jMethodIdField(methodData);
            builder.lineStart().writeModifiers(modSet).space().write(typeCache.jNIMethod).space().write(fieldName).lineEnd(";");
        }
    }

    private void generateJNIFieldsInit(CodeBuilder builder, CodeBuilder.Parameter jniEnv) {
        builder.lineStart("this." + END_POINT_CLASS_FIELD).write(" = ").invokeStatic(typeCache.jNIClassCache, "lookupClass",
                        jniEnv.name, END_POINT_SIMPLE_NAME + ".class").lineEnd(";");
        for (MethodData methodData : definitionData.toGenerate) {
            CharSequence fieldName = jMethodIdField(methodData);
            List<? extends TypeMirror> parameterTypes = methodData.type.getParameterTypes();
            List<TypeMirror> signature = new ArrayList<>(1 + parameterTypes.size());
            if (!definitionData.hasCustomDispatch()) {
                signature.add(definitionData.serviceType);
            }
            if (needsExplicitIsolateParameter(methodData)) {
                signature.add(types.getPrimitiveType(TypeKind.LONG));
            }
            int marshalledParametersCount = 0;
            for (int i = 0; i < parameterTypes.size(); i++) {
                MarshallerData marshallerData = methodData.getParameterMarshaller(i);
                if (marshallerData.isCustom()) {
                    marshalledParametersCount++;
                } else {
                    TypeMirror parameterType = parameterTypes.get(i);
                    signature.add(marshallerSnippets(marshallerData).getEndPointMethodParameterType(parameterType));
                }
            }
            if (marshalledParametersCount > 0) {
                signature.add(types.getArrayType(types.getPrimitiveType(TypeKind.BYTE)));
            }
            TypeMirror returnType = marshallerSnippets(methodData.getReturnTypeMarshaller()).getEndPointMethodParameterType(methodData.type.getReturnType());
            builder.lineStart("this." + fieldName).write(" = ").invokeStatic(typeCache.jNIMethod, "findMethod", jniEnv.name, END_POINT_CLASS_FIELD, "true",
                            "\"" + methodData.element.getSimpleName() + "\"",
                            "\"" + Utilities.encodeMethodSignature(parser.elements, types, returnType, signature.toArray(new TypeMirror[0])) + "\"").lineEnd(";");
        }
    }

    private void generateNativeToHSStartMethod(CodeBuilder builder, CacheSnippets cacheSnippets, MethodData methodData) {
        builder.line("");
        overrideMethod(builder, methodData);
        builder.indent();
        CharSequence receiver;
        int nonReceiverParameterStart;
        if (definitionData.hasCustomDispatch()) {
            receiver = methodData.element.getParameters().get(0).getSimpleName();
            nonReceiverParameterStart = 1;
        } else {
            receiver = definitionData.endPointHandle != null ? definitionData.endPointHandle.getSimpleName() : "this";
            nonReceiverParameterStart = 0;
        }
        List<Integer> binaryMarshalledParameters = new ArrayList<>();
        boolean hasOutParameters = false;
        for (int i = nonReceiverParameterStart; i < methodData.element.getParameters().size(); i++) {
            MarshallerData marshallerData = methodData.getParameterMarshaller(i);
            TypeMirror parameterType = methodData.type.getParameterTypes().get(i);
            if (isBinaryMarshallable(marshallerData, parameterType, false)) {
                binaryMarshalledParameters.add(i);
            }
            hasOutParameters |= isOutParameter(marshallerData, parameterType, false);
        }
        int staticBufferSize = getStaticBufferSize(binaryMarshalledParameters.size(), methodData.getReturnTypeMarshaller().isCustom());
        AbstractBridgeParser.CacheData cacheData = methodData.cachedData;
        CharSequence args;
        CharSequence env;
        TypeMirror returnType = methodData.type.getReturnType();
        boolean voidMethod = returnType.getKind() == TypeKind.VOID;
        MarshallerSnippets resultMarshallerSnippets = marshallerSnippets(methodData.getReturnTypeMarshaller());
        if (cacheData != null) {
            String cacheFieldName = cacheData.cacheFieldName;
            String resultVariable = cacheFieldName + "Result";
            builder.lineStart().write(cacheData.cacheEntryType).space().write(resultVariable).write(" = ").write(cacheSnippets.readCache(builder, cacheFieldName, receiver)).lineEnd(";");
            builder.lineStart("if (").write(resultVariable).lineEnd(" == null) {");
            builder.indent();
            builder.line("try {");
            builder.indent();
            env = generateLookupJNIEnv(builder);
            CharSequence staticMarshallBufferVar = generateStaticBuffer(builder, staticBufferSize);
            Map.Entry<CharSequence, CharSequence> binaryMarshallVars = generateBinaryMarshallProlog(builder, methodData, binaryMarshalledParameters, staticMarshallBufferVar, staticBufferSize);
            Map<String, CharSequence> parameterValueOverrides = new HashMap<>();
            boolean hasPostMarshall = generatePreMarshall(builder, methodData, nonReceiverParameterStart, binaryMarshallVars != null ? binaryMarshallVars.getValue() : null, env,
                            parameterValueOverrides);
            if (binaryMarshallVars != null) {
                generateBinaryMarshallEpilogue(builder, binaryMarshallVars.getValue(), binaryMarshallVars.getKey(), env);
            }
            args = generatePushArgs(builder, methodData, nonReceiverParameterStart, env, receiver, parameterValueOverrides, binaryMarshalledParameters,
                            binaryMarshallVars != null ? binaryMarshallVars.getKey() : null);
            CharSequence jniCall = callHotSpot(builder, methodData, env, args, resultMarshallerSnippets);
            PreUnmarshallResult preUnmarshallResult = generatePreUnmarshallResult(builder, resultMarshallerSnippets, returnType, jniCall, receiver, env);
            Map.Entry<CharSequence, CharSequence> binaryUnmarshallVars = null;
            if (methodData.getReturnTypeMarshaller().isCustom()) {
                binaryUnmarshallVars = generateCreateBinaryInputForJByteArray(builder, preUnmarshallResult.result, "marshalledResultInput", env, staticMarshallBufferVar, staticBufferSize);
            }
            builder.lineStart().write(resultVariable).write(" = ");
            builder.write(resultMarshallerSnippets.unmarshallResult(builder, returnType, preUnmarshallResult.result, receiver, binaryUnmarshallVars != null ? binaryUnmarshallVars.getValue() : null,
                            env));
            builder.lineEnd(";");
            if (hasPostMarshall) {
                binaryUnmarshallVars = generatePostMarshall(builder, methodData, nonReceiverParameterStart, binaryUnmarshallVars, binaryMarshallVars != null ? binaryMarshallVars.getKey() : null,
                                env, resultVariable, staticMarshallBufferVar, staticBufferSize, hasOutParameters);
            }
            builder.lineStart().write(cacheSnippets.writeCache(builder, cacheFieldName, receiver, resultVariable)).lineEnd(";");
            if (binaryUnmarshallVars != null) {
                generateCleanupBinaryInputForJByteArray(builder, binaryUnmarshallVars.getKey(), staticMarshallBufferVar);
            }
            builder.dedent();
            CharSequence foreignException = "foreignException";
            builder.lineStart("} catch (").write(typeCache.foreignException).space().write(foreignException).lineEnd(") {");
            builder.indent();
            builder.lineStart("throw ").invoke(foreignException, "throwOriginalException", definitionData.getCustomMarshaller(typeCache.throwable, null, types).name).lineEnd(";");
            builder.dedent();
            builder.line("}");
            builder.dedent();
            builder.line("}");
            builder.lineStart("return ").write(resultVariable).lineEnd(";");
        } else {
            builder.line("try {");
            builder.indent();
            env = generateLookupJNIEnv(builder);
            CharSequence staticMarshallBufferVar = generateStaticBuffer(builder, staticBufferSize);
            Map.Entry<CharSequence, CharSequence> binaryMarshallVars = generateBinaryMarshallProlog(builder, methodData, binaryMarshalledParameters, staticMarshallBufferVar, staticBufferSize);
            Map<String, CharSequence> parameterValueOverrides = new HashMap<>();
            boolean hasPostMarshall = generatePreMarshall(builder, methodData, nonReceiverParameterStart, binaryMarshallVars != null ? binaryMarshallVars.getValue() : null, env,
                            parameterValueOverrides);
            if (binaryMarshallVars != null) {
                generateBinaryMarshallEpilogue(builder, binaryMarshallVars.getValue(), binaryMarshallVars.getKey(), env);
            }
            args = generatePushArgs(builder, methodData, nonReceiverParameterStart, env, receiver, parameterValueOverrides, binaryMarshalledParameters,
                            binaryMarshallVars != null ? binaryMarshallVars.getKey() : null);
            CharSequence jniCall = callHotSpot(builder, methodData, env, args, resultMarshallerSnippets);
            PreUnmarshallResult preUnmarshallResult = generatePreUnmarshallResult(builder, resultMarshallerSnippets, returnType, jniCall, receiver, env);
            CharSequence resultVariable = null;
            if (hasPostMarshall && !voidMethod) {
                resultVariable = "result";
            }
            Map.Entry<CharSequence, CharSequence> binaryUnmarshallVars = null;
            if (methodData.getReturnTypeMarshaller().isCustom()) {
                binaryUnmarshallVars = generateCreateBinaryInputForJByteArray(builder, preUnmarshallResult.result, "marshalledResultInput", env, staticMarshallBufferVar, staticBufferSize);
            }
            builder.lineStart();
            if (resultVariable != null) {
                builder.write(returnType).space().write(resultVariable).write(" = ");
            } else if (!voidMethod) {
                builder.write("return ");
            }
            builder.write(resultMarshallerSnippets.unmarshallResult(builder, returnType, preUnmarshallResult.result, receiver, binaryUnmarshallVars != null ? binaryUnmarshallVars.getValue() : null,
                            env));
            builder.lineEnd(";");
            if (hasPostMarshall) {
                binaryUnmarshallVars = generatePostMarshall(builder, methodData, nonReceiverParameterStart, binaryUnmarshallVars, binaryMarshallVars != null ? binaryMarshallVars.getKey() : null,
                                env, resultVariable, staticMarshallBufferVar, staticBufferSize, hasOutParameters);
            }
            if (resultVariable != null) {
                // Return was deferred after post unmarshalling statements.
                // Do it now.
                builder.lineStart("return ").write(resultVariable).lineEnd(";");
            }
            if (binaryUnmarshallVars != null) {
                generateCleanupBinaryInputForJByteArray(builder, binaryUnmarshallVars.getKey(), staticMarshallBufferVar);
            }
            builder.dedent();
            CharSequence foreignException = "foreignException";
            builder.lineStart("} catch (").write(typeCache.foreignException).space().write(foreignException).lineEnd(") {");
            builder.indent();
            builder.lineStart("throw ").invoke(foreignException, "throwOriginalException", definitionData.getCustomMarshaller(typeCache.throwable, null, types).name).lineEnd(";");
            builder.dedent();
            builder.line("}");
        }
        builder.dedent();
        builder.line("}");
    }

    private static PreUnmarshallResult generatePreUnmarshallResult(CodeBuilder builder, MarshallerSnippets snippets, TypeMirror returnType, CharSequence nativeCall, CharSequence receiver,
                    CharSequence jniEnvFieldName) {
        CharSequence result = nativeCall;
        CharSequence endPointResultVariable = snippets.storeRawResult(builder, returnType, nativeCall, jniEnvFieldName);
        result = endPointResultVariable != null ? endPointResultVariable : result;
        CharSequence resultVariable = snippets.preUnmarshallResult(builder, returnType, result, receiver, jniEnvFieldName);
        result = resultVariable != null ? resultVariable : result;
        return new PreUnmarshallResult(result, null, resultVariable != null);
    }

    private CharSequence generateLookupJNIEnv(CodeBuilder builder) {
        CharSequence res = "jniEnv";
        builder.lineStart().write(typeCache.jniEnv).space().write(res).write(" = ").invokeStatic(typeCache.jniMethodScope, "env").lineEnd(";");
        return res;
    }

    private CharSequence generateStaticBuffer(CodeBuilder builder, int staticBufferSize) {
        if (staticBufferSize > 0) {
            CharSequence staticMarshallBufferVar = "staticMarshallBuffer";
            builder.lineStart().write(typeCache.cCharPointer).space().write(staticMarshallBufferVar).write(" = ").invokeStatic(typeCache.stackValue, "get", Integer.toString(staticBufferSize)).lineEnd(
                            ";");
            return staticMarshallBufferVar;
        } else {
            return null;
        }
    }

    private Map.Entry<CharSequence, CharSequence> generateBinaryMarshallProlog(CodeBuilder builder, MethodData methodData, List<Integer> binaryMarshalledParameters,
                    CharSequence staticBufferVar, int staticBufferLength) {
        if (!binaryMarshalledParameters.isEmpty()) {
            CharSequence marshalledParametersVar = "marshalledParameters";
            CharSequence marshalledParametersOutputVar = "marshalledParametersOutput";
            builder.lineStart().write(typeCache.jByteArray).space().write(marshalledParametersVar).lineEnd(";");
            CharSequence binaryOutputInit = generateCCharPointerBinaryOutputInit(builder, marshalledParametersOutputVar, methodData, binaryMarshalledParameters, staticBufferVar, staticBufferLength);
            builder.lineStart("try ").write("(").write(binaryOutputInit).lineEnd(") {");
            builder.indent();
            return new AbstractMap.SimpleImmutableEntry<>(marshalledParametersVar, marshalledParametersOutputVar);
        }
        return null;
    }

    private void generateBinaryMarshallEpilogue(CodeBuilder builder, CharSequence binaryOutputVar, CharSequence marshalledParametersHsArrayVar, CharSequence jniEnv) {
        CharSequence marshalledParametersPositionVar = "marshalledParametersPosition";
        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(marshalledParametersPositionVar).write(" = ").invoke(binaryOutputVar, "getPosition").lineEnd(";");
        builder.lineStart().write(marshalledParametersHsArrayVar).write(" = ").invokeStatic(typeCache.jniUtil, "NewByteArray", jniEnv, marshalledParametersPositionVar).lineEnd(";");
        CharSequence address = new CodeBuilder(builder).invoke(binaryOutputVar, "getAddress").build();
        builder.lineStart().invokeStatic(typeCache.jniUtil, "SetByteArrayRegion", jniEnv, marshalledParametersHsArrayVar, "0", marshalledParametersPositionVar, address).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    private Map.Entry<CharSequence, CharSequence> generateCreateBinaryInputForJByteArray(CodeBuilder builder, CharSequence jByteArray, CharSequence marshalledResultInputVar,
                    CharSequence jniEnv, CharSequence staticMarshallBufferVar, int staticBufferSize) {
        CharSequence marshalledDataLengthVar = "marshalledDataLength";
        CharSequence marshallBufferVar = "marshallBuffer";
        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(marshalledDataLengthVar).write(" = ").invokeStatic(typeCache.jniUtil, "GetArrayLength", jniEnv,
                        jByteArray).lineEnd(";");
        builder.lineStart().write(typeCache.cCharPointer).space().write(marshallBufferVar).write(" = ").write(marshalledDataLengthVar).write(" <= ").write(Integer.toString(staticBufferSize)).write(
                        " ? ").write(staticMarshallBufferVar).write(" : ").invokeStatic(typeCache.unmanagedMemory, "malloc", marshalledDataLengthVar).lineEnd(";");
        builder.line("try {");
        builder.indent();
        builder.lineStart().invokeStatic(typeCache.jniUtil, "GetByteArrayRegion", jniEnv, jByteArray, "0", marshalledDataLengthVar, marshallBufferVar).lineEnd(";");
        builder.lineStart().write(typeCache.binaryInput).space().write(marshalledResultInputVar).write(" = ").invokeStatic(typeCache.binaryInput, "create", marshallBufferVar,
                        marshalledDataLengthVar).lineEnd(";");
        return new AbstractMap.SimpleImmutableEntry<>(marshallBufferVar, marshalledResultInputVar);
    }

    private void generateCleanupBinaryInputForJByteArray(CodeBuilder builder, CharSequence marshallBufferVar, CharSequence staticMarshallBufferVar) {
        builder.dedent();
        builder.line("} finally {");
        builder.indent();
        builder.lineStart("if (").write(marshallBufferVar).write(" != ").write(staticMarshallBufferVar).lineEnd(") {");
        builder.indent();
        builder.lineStart().invokeStatic(typeCache.unmanagedMemory, "free", marshallBufferVar).lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
    }

    private boolean generatePreMarshall(CodeBuilder builder, MethodData methodData, int nonReceiverParameterStart,
                    CharSequence marshalledParametersOutput, CharSequence jniEnv, Map<String, CharSequence> parameterValueOverrides) {
        boolean hasPostMarshall = false;
        List<? extends VariableElement> formalParameters = methodData.element.getParameters();
        List<? extends TypeMirror> formalParameterTypes = methodData.type.getParameterTypes();
        for (int i = nonReceiverParameterStart; i < formalParameters.size(); i++) {
            MarshallerData marshaller = methodData.getParameterMarshaller(i);
            hasPostMarshall |= marshallerSnippets(marshaller).preMarshallParameter(builder, formalParameterTypes.get(i),
                            formalParameters.get(i).getSimpleName(), marshalledParametersOutput, jniEnv, parameterValueOverrides);
        }
        return hasPostMarshall;
    }

    private Map.Entry<CharSequence, CharSequence> generatePostMarshall(CodeBuilder builder, MethodData methodData, int nonReceiverParameterStart,
                    Map.Entry<CharSequence, CharSequence> inputData, CharSequence marshalledParametersVar, CharSequence jniEnv, CharSequence resultVariable,
                    CharSequence staticMarshallBufferVar, int staticBufferSize, boolean hasOutParameters) {
        Map.Entry<CharSequence, CharSequence> res = inputData;
        if (hasOutParameters && res == null) {
            res = generateCreateBinaryInputForJByteArray(builder, marshalledParametersVar, "marshalledParametersInput", jniEnv, staticMarshallBufferVar, staticBufferSize);
        }
        List<? extends VariableElement> formalParameters = methodData.element.getParameters();
        List<? extends TypeMirror> formalParameterTypes = methodData.type.getParameterTypes();
        for (int i = nonReceiverParameterStart; i < formalParameters.size(); i++) {
            MarshallerData marshaller = methodData.getParameterMarshaller(i);
            marshallerSnippets(marshaller).postMarshallParameter(builder, formalParameterTypes.get(i),
                            formalParameters.get(i).getSimpleName(), null, res != null ? res.getValue() : null, jniEnv, resultVariable);
        }
        return res;
    }

    private CharSequence generatePushArgs(CodeBuilder builder, MethodData methodData, int nonReceiverParameterStart,
                    CharSequence jniEnv, CharSequence receiver, Map<String, CharSequence> parameterValueOverrides,
                    Collection<Integer> binaryMarshalledParameters, CharSequence marshalledParametersVar) {
        String jniArgs = "jniArgs";
        List<? extends VariableElement> parameters = methodData.element.getParameters();
        boolean hasExplicitReceiver = definitionData.hasCustomDispatch();
        boolean hasExplicitIsolate = needsExplicitIsolateParameter(methodData);
        int argumentCount = parameters.size() - binaryMarshalledParameters.size() + (hasExplicitReceiver ? 0 : 1) + (hasExplicitIsolate ? 1 : 0) + (binaryMarshalledParameters.isEmpty() ? 0 : 1);
        CodeBuilder jValueClassLiteral = new CodeBuilder(builder).classLiteral(typeCache.jValue);
        builder.lineStart().write(typeCache.jValue).space().write(jniArgs).write(" = ").invokeStatic(typeCache.stackValue, "get", Integer.toString(argumentCount),
                        jValueClassLiteral.build()).lineEnd(";");
        CharSequence address = new CodeBuilder(builder).invoke(jniArgs, "addressOf", "0").build();
        CharSequence receiverHSObject;
        if (hasExplicitReceiver) {
            receiverHSObject = new CodeBuilder(builder).cast(typeCache.hSObject, receiver, true).build();
        } else {
            receiverHSObject = receiver;
        }
        CharSequence value = new CodeBuilder(builder).invoke(receiverHSObject, "getHandle").build();
        builder.lineStart().invoke(address, "setJObject", value).lineEnd(";");
        int stackIndex;
        if (hasExplicitIsolate) {
            address = new CodeBuilder(builder).invoke(jniArgs, "addressOf", "1").build();
            CharSequence currentIsolate = new CodeBuilder(builder).invoke(
                            new CodeBuilder(builder).invokeStatic(typeCache.currentIsolate, "getIsolate").build(),
                            "rawValue").build();
            builder.lineStart().invoke(address, "setLong", currentIsolate).lineEnd(";");
            stackIndex = 2;
        } else {
            stackIndex = 1;
        }
        List<? extends VariableElement> formalParameters = methodData.element.getParameters();
        List<? extends TypeMirror> formalParameterTypes = methodData.type.getParameterTypes();
        for (int i = nonReceiverParameterStart; i < formalParameters.size(); i++) {
            if (binaryMarshalledParameters.contains(i)) {
                continue;
            }
            address = new CodeBuilder(builder).invoke(jniArgs, "addressOf", Integer.toString(stackIndex++)).build();
            CharSequence parameterName = formalParameters.get(i).getSimpleName();
            CharSequence parameterValueOverride = parameterValueOverrides.get(parameterName.toString());
            MarshallerData marshaller = methodData.getParameterMarshaller(i);
            MarshallerSnippets marshallerSnippets = marshallerSnippets(marshaller);
            if (parameterValueOverride != null) {
                value = parameterValueOverride;
            } else {
                value = marshallerSnippets.marshallParameter(builder, formalParameterTypes.get(i), parameterName, null, jniEnv);
            }
            builder.lineStart().invoke(address, jValueSetterName(marshallerSnippets.getEndPointMethodParameterType(formalParameterTypes.get(i))), value).lineEnd(";");
        }
        if (marshalledParametersVar != null) {
            address = new CodeBuilder(builder).invoke(jniArgs, "addressOf", Integer.toString(stackIndex)).build();
            builder.lineStart().invoke(address, "setJObject", marshalledParametersVar).lineEnd(";");
        }
        return jniArgs;
    }

    private void generateByteArrayBinaryOutputInit(CodeBuilder builder, CharSequence marshalledResultOutputVar,
                    List<MarshalledParameter> marshalledParameters, CharSequence marshalledData) {
        CharSequence sizeVar = "marshalledResultSizeEstimate";
        generateSizeEstimate(builder, sizeVar, marshalledParameters, false);
        builder.lineStart().write(typeCache.byteArrayBinaryOutput).space().write(marshalledResultOutputVar).write(" = ");
        if (marshalledData != null) {
            builder.write(sizeVar).write(" > ").memberSelect(marshalledData, "length", false).write(" ? ").invokeStatic(typeCache.byteArrayBinaryOutput, "create", sizeVar).write(" : ").invokeStatic(
                            typeCache.binaryOutput, "create", marshalledData);
        } else {
            builder.invokeStatic(typeCache.byteArrayBinaryOutput, "create", sizeVar);
        }
        builder.lineEnd(";");
    }

    private CharSequence generateCCharPointerBinaryOutputInit(CodeBuilder builder, CharSequence marshalledParametersOutputVar,
                    MethodData methodData, List<Integer> customParameters,
                    CharSequence staticBufferVar, int staticBufferSize) {
        CharSequence sizeVar = "marshalledParametersSizeEstimate";
        List<? extends VariableElement> parameters = methodData.element.getParameters();
        List<? extends TypeMirror> parameterTypes = methodData.type.getParameterTypes();
        List<MarshalledParameter> marshallers = new ArrayList<>();
        for (int index : customParameters) {
            marshallers.add(new MarshalledParameter(parameters.get(index).getSimpleName(), parameterTypes.get(index), methodData.getParameterMarshaller(index)));
        }
        generateSizeEstimate(builder, sizeVar, marshallers, true);
        return new CodeBuilder(builder).write(typeCache.cCharPointerBinaryOutput).space().write(marshalledParametersOutputVar).write(" = ").write(sizeVar).write(" > ").write(
                        Integer.toString(staticBufferSize)).write(" ? ").invokeStatic(typeCache.cCharPointerBinaryOutput, "create", sizeVar).write(" : ").invokeStatic(typeCache.binaryOutput, "create",
                                        staticBufferVar, Integer.toString(staticBufferSize), "false").build();
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

    private CharSequence callHotSpot(CodeBuilder builder, MethodData methodData, CharSequence jniEnv, CharSequence args, MarshallerSnippets marshallerSnippets) {
        CharSequence hsCallsInstance = new CodeBuilder(builder).invokeStatic(typeCache.foreignException, "getJNICalls").build();
        TypeMirror retType = methodData.type.getReturnType();
        return new CodeBuilder(builder).invoke(hsCallsInstance, callHotSpotName(marshallerSnippets.getEndPointMethodParameterType(retType)),
                        jniEnv, "jniMethods_." + END_POINT_CLASS_FIELD, "jniMethods_." + jMethodIdField(methodData), args).build();
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

    private void generateNativeToHSEndPoint(CodeBuilder builder) {
        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC), END_POINT_SIMPLE_NAME, null, Collections.emptyList());
        builder.indent();
        builder.lineEnd("");

        generateMarshallerFields(builder, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

        if (!definitionData.getAllCustomMarshallers().isEmpty()) {
            builder.methodStart(EnumSet.of(Modifier.STATIC), null,
                            null, Collections.emptyList(), Collections.emptyList());
            builder.indent();
            builder.lineStart().write(typeCache.jniConfig).write(" config = ").invokeStatic(definitionData.jniConfig, "getInstance").lineEnd(";");
            generateMarshallerLookups(builder);
            builder.dedent();
            builder.line("}");
        }

        for (MethodData methodData : definitionData.toGenerate) {
            generateNativeToHSEndMethod(builder, methodData);
        }
        builder.dedent();
        builder.classEnd();
    }

    private void generateNativeToHSEndMethod(CodeBuilder builder, MethodData methodData) {
        builder.lineEnd("");
        List<? extends VariableElement> methodParameters = methodData.element.getParameters();
        List<? extends TypeMirror> methodParameterTypes = methodData.type.getParameterTypes();
        Set<CharSequence> warnings = new TreeSet<>(Comparator.comparing(CharSequence::toString));
        for (int i = 0; i < methodParameters.size(); i++) {
            warnings.addAll(marshallerSnippets(methodData.getParameterMarshaller(i)).getEndPointSuppressedWarnings(builder, methodParameterTypes.get(i)));
        }
        if (!warnings.isEmpty()) {
            builder.lineStart().annotation(typeCache.suppressWarnings, warnings.toArray(new CharSequence[0])).lineEnd("");
        }
        List<CodeBuilder.Parameter> params = new ArrayList<>();
        if (!definitionData.hasCustomDispatch()) {
            params.add(CodeBuilder.newParameter(definitionData.serviceType, "receiverObject"));
        }
        if (needsExplicitIsolateParameter(methodData)) {
            params.add(CodeBuilder.newParameter(types.getPrimitiveType(TypeKind.LONG), REFERENCE_ISOLATE_ADDRESS_NAME));
        }
        int marshalledDataCount = 0;
        List<MarshalledParameter> outParameters = new ArrayList<>();
        for (int i = 0; i < methodParameters.size(); i++) {
            MarshallerData marshallerData = methodData.getParameterMarshaller(i);
            CharSequence parameterName = methodParameters.get(i).getSimpleName();
            TypeMirror parameterType = methodParameterTypes.get(i);
            if (isBinaryMarshallable(marshallerData, methodParameterTypes.get(i), false)) {
                marshalledDataCount++;
            } else {
                params.add(CodeBuilder.newParameter(marshallerSnippets(marshallerData).getEndPointMethodParameterType(parameterType), parameterName));
            }
            if (isOutParameter(marshallerData, parameterType, false)) {
                outParameters.add(new MarshalledParameter(parameterName, parameterType, marshallerData));
            }
        }
        if (marshalledDataCount > 0) {
            params.add(CodeBuilder.newParameter(types.getArrayType(types.getPrimitiveType(TypeKind.BYTE)), MARSHALLED_DATA_PARAMETER));
        }
        CharSequence methodName = methodData.element.getSimpleName();
        TypeMirror returnType = marshallerSnippets(methodData.getReturnTypeMarshaller()).getEndPointMethodParameterType(methodData.type.getReturnType());
        boolean voidReturnType = returnType.getKind() == TypeKind.VOID;
        builder.lineStart().annotation(typeCache.suppressWarnings, "unused").lineEnd("");
        builder.lineStart().annotation(typeCache.jNIEntryPoint, null).lineEnd("");
        builder.methodStart(EnumSet.of(Modifier.STATIC), methodName, returnType, params, Collections.emptyList());
        builder.indent();
        // Encode arguments
        CharSequence[] actualParameters = new CharSequence[methodParameters.size()];
        int nonReceiverParameterStart;
        if (definitionData.hasCustomDispatch()) {
            actualParameters[0] = "receiverObject";
            nonReceiverParameterStart = 1;
        } else {
            nonReceiverParameterStart = 0;
        }

        builder.line("try {");
        builder.indent();
        CharSequence resolvedDispatch;
        if (definitionData.hasCustomDispatch()) {
            TypeMirror receiverType = definitionData.customDispatchAccessor.getParameters().get(0).asType();
            CharSequence receiverName = methodParameters.get(0).getSimpleName();
            boolean accessorNeedsCast = !types.isSameType(typeCache.object, receiverType);
            CharSequence customDispatch = accessorNeedsCast ? new CodeBuilder(builder).cast(receiverType, receiverName).build() : receiverName;
            resolvedDispatch = "resolvedDispatch";
            builder.lineStart().write(definitionData.serviceType).space().write(resolvedDispatch).write(" = ").invokeStatic(definitionData.annotatedType,
                            definitionData.customDispatchAccessor.getSimpleName(),
                            customDispatch).lineEnd(
                                            ";");
            builder.lineStart().write(typeCache.object).space().write("receiverObject").write(" = ").invokeStatic(definitionData.annotatedType, definitionData.customReceiverAccessor.getSimpleName(),
                            customDispatch).lineEnd(
                                            ";");
        } else {
            resolvedDispatch = "receiverObject";
        }

        Map<String, CharSequence> parameterValueOverrides = new HashMap<>();

        // Create binary input for marshalled parameters
        CharSequence marshalledParametersInputVar = "marshalledParametersInput";
        if (marshalledDataCount > 0) {
            builder.lineStart().write(typeCache.binaryInput).space().write(marshalledParametersInputVar).write(" = ").invokeStatic(typeCache.binaryInput, "create",
                            MARSHALLED_DATA_PARAMETER).lineEnd(";");
        }

        // Generate pre unmarshall statements
        for (int i = nonReceiverParameterStart; i < methodParameters.size(); i++) {
            marshallerSnippets(methodData.getParameterMarshaller(i)).preUnmarshallParameter(builder, methodParameterTypes.get(i), methodParameters.get(i).getSimpleName(),
                            marshalledParametersInputVar, params.get(0).name, parameterValueOverrides);
        }

        // Decode arguments.
        for (int i = nonReceiverParameterStart; i < methodParameters.size(); i++) {
            CharSequence parameterName = methodParameters.get(i).getSimpleName();
            CharSequence parameterValueOverride = parameterValueOverrides.get(parameterName.toString());
            MarshallerData marshallerData = methodData.getParameterMarshaller(i);
            if (parameterValueOverride != null) {
                actualParameters[i] = parameterValueOverride;
            } else {
                TypeMirror parameterType = methodParameterTypes.get(i);
                CharSequence unmarshallCall = marshallerSnippets(marshallerData).unmarshallParameter(builder, parameterType, parameterName,
                                marshalledParametersInputVar, null);
                if (marshallerData.isCustom()) {
                    builder.lineStart().write(parameterType).space().write(parameterName).write(" = ").write(unmarshallCall).lineEnd(";");
                    actualParameters[i] = parameterName;
                } else {
                    actualParameters[i] = unmarshallCall;
                }
            }
        }

        CharSequence resultSnippet = new CodeBuilder(builder).invoke(resolvedDispatch, methodData.receiverMethod != null ? methodData.receiverMethod : methodName, actualParameters).build();
        CharSequence resultVariable = null;
        if (voidReturnType) {
            builder.lineStart().write(resultSnippet).lineEnd(";");
        } else {
            resultVariable = "endPointResult";
            builder.lineStart().write(methodData.type.getReturnType()).space().write(resultVariable).write(" = ").write(resultSnippet).lineEnd(";");
        }

        CharSequence marshalledOutputVar = null;
        boolean hasBinaryMarshalledResult = methodData.getReturnTypeMarshaller().isCustom();
        if (hasBinaryMarshalledResult) {
            List<MarshalledParameter> marshallers = new ArrayList<>();
            marshallers.add(new MarshalledParameter(resultVariable, returnType, methodData.getReturnTypeMarshaller()));
            marshallers.addAll(outParameters);
            marshalledOutputVar = "marshalledResultOutput";
            generateByteArrayBinaryOutputInit(builder, marshalledOutputVar, marshallers, marshalledDataCount > 0 ? MARSHALLED_DATA_PARAMETER : null);
        }

        // Do result marshalling for non-void methods
        if (resultVariable != null) {
            MarshallerSnippets returnTypeSnippets = marshallerSnippets(methodData.getReturnTypeMarshaller());
            CharSequence resultOverride = returnTypeSnippets.preMarshallResult(builder, methodData.type.getReturnType(), resultVariable, marshalledOutputVar, null);
            if (resultOverride != null) {
                resultVariable = resultOverride;
            }
            resultSnippet = returnTypeSnippets.marshallResult(builder, methodData.type.getReturnType(), resultVariable, marshalledOutputVar, null);
        }

        if (!outParameters.isEmpty() && marshalledOutputVar == null) {
            marshalledOutputVar = "marshalledParametersOutput";
            builder.lineStart().write(typeCache.byteArrayBinaryOutput).space().write(marshalledOutputVar).write(" = ").invokeStatic(typeCache.binaryOutput, "create",
                            MARSHALLED_DATA_PARAMETER).lineEnd(";");
        }

        // Generate post unmarshall statements.
        for (int i = nonReceiverParameterStart; i < methodParameters.size(); i++) {
            MarshallerSnippets marshallerSnippets = marshallerSnippets(methodData.getParameterMarshaller(i));
            marshallerSnippets.postUnmarshallParameter(builder, methodParameterTypes.get(i), methodParameters.get(i).getSimpleName(), marshalledOutputVar, null, resultVariable);
        }

        // Generate return value
        if (!voidReturnType) {
            builder.lineStart("return ").write(resultSnippet).lineEnd(";");
        }

        builder.dedent();
        CharSequence e = "e";
        builder.lineStart("} catch (").write(typeCache.throwable).space().write(e).lineEnd(") {");
        builder.indent();
        builder.lineStart("throw ").invokeStatic(typeCache.foreignException, "forThrowable", e, definitionData.getCustomMarshaller(typeCache.throwable, null, types).name).lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
    }

    private static boolean needsExplicitIsolateParameter(MethodData methodData) {
        for (int i = 0; i < methodData.element.getParameters().size(); i++) {
            MarshallerData marshallerData = methodData.getParameterMarshaller(i);
            if (marshallerData.kind == MarshallerData.Kind.REFERENCE && !marshallerData.sameDirection) {
                return true;
            }
        }
        return false;
    }

    private abstract static class NativeToHotSpotMarshallerSnippets extends MarshallerSnippets {

        final TypeCache cache;

        NativeToHotSpotMarshallerSnippets(NativeBridgeProcessor processor, MarshallerData marshallerData, Types types, TypeCache typeCache, BinaryNameCache binaryNameCache) {
            super(processor, marshallerData, types, typeCache, binaryNameCache);
            this.cache = typeCache;
        }

        static NativeToHotSpotMarshallerSnippets forData(NativeBridgeProcessor processor, DefinitionData data, MarshallerData marshallerData,
                        Types types, TypeCache typeCache, BinaryNameCache binaryNameCache) {
            switch (marshallerData.kind) {
                case VALUE:
                    return new DirectSnippets(processor, marshallerData, types, typeCache, binaryNameCache);
                case REFERENCE:
                    return new ReferenceSnippets(processor, data, marshallerData, types, typeCache, binaryNameCache);
                case RAW_REFERENCE:
                    return new RawReferenceSnippets(processor, marshallerData, types, typeCache, binaryNameCache);
                case CUSTOM:
                    return new CustomSnippets(processor, marshallerData, types, typeCache, binaryNameCache);
                default:
                    throw new IllegalArgumentException(String.valueOf(marshallerData.kind));
            }
        }

        private static final class DirectSnippets extends NativeToHotSpotMarshallerSnippets {

            DirectSnippets(NativeBridgeProcessor processor, MarshallerData marshallerData, Types types, TypeCache cache, BinaryNameCache binaryNameCache) {
                super(processor, marshallerData, types, cache, binaryNameCache);
            }

            @Override
            TypeMirror getEndPointMethodParameterType(TypeMirror type) {
                return type;
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName) {
                if (types.isSameType(cache.string, parameterType)) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createHSString", jniEnvFieldName, formalParameter).build();
                } else if (parameterType.getKind() == TypeKind.ARRAY) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createHSArray", jniEnvFieldName, formalParameter).build();
                } else {
                    return formalParameter;
                }
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence marshalledResultInput,
                            CharSequence jniEnvFieldName) {
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
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                return invocationSnippet;
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput, CharSequence jniEnvFieldName) {
                return parameterName;
            }

            @Override
            boolean preMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName,
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
            void postMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence receiver, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName, CharSequence resultVariableName) {
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
                    throw new IllegalArgumentException("Expected declared type, got " + jniArrayType + " of kind " + kind);
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

            ReferenceSnippets(NativeBridgeProcessor processor, DefinitionData data, MarshallerData marshallerData, Types types, TypeCache cache, BinaryNameCache binaryNameCache) {
                super(processor, marshallerData, types, cache, binaryNameCache);
                this.data = data;
            }

            @Override
            TypeMirror getEndPointMethodParameterType(TypeMirror type) {
                if (marshallerData.sameDirection) {
                    return type;
                } else {
                    TypeMirror longType = types.getPrimitiveType(TypeKind.LONG);
                    return type.getKind() == TypeKind.ARRAY ? types.getArrayType(longType) : longType;
                }
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
            boolean preMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverrides) {
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                    AnnotationMirror in = findIn(marshallerData.annotations);
                    AnnotationMirror out = findOut(marshallerData.annotations);
                    CharSequence arrayLength = null;
                    CharSequence arrayOffsetParameter = null;
                    if (in != null) {
                        arrayLength = getArrayLengthParameterName(in);
                        arrayOffsetParameter = getArrayOffsetParameterName(in);
                    } else if (out != null) {
                        arrayLength = getArrayLengthParameterName(out);
                        arrayOffsetParameter = getArrayOffsetParameterName(out);
                    }
                    if (arrayLength == null) {
                        arrayLength = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                    }
                    if (arrayOffsetParameter != null) {
                        parameterValueOverrides.put(arrayOffsetParameter.toString(), "0");
                    }
                    boolean needsCopy = out == null || in != null;
                    if (marshallerData.sameDirection) {
                        TypeMirror hsParameterType = jniTypeForJavaType(getEndPointMethodParameterType(parameterType), types, cache);
                        CharSequence targetArrayVariable = Utilities.javaMemberName("hs", parameterName);
                        if (needsCopy) {
                            CharSequence copySnippet = marshallCopyHSObjectArrayToHotSpot(currentBuilder, componentType, parameterName, arrayOffsetParameter, arrayLength, jniEnvFieldName);
                            currentBuilder.lineStart().write(hsParameterType).space().write(targetArrayVariable).write(" = ").write(copySnippet).lineEnd(";");
                        } else {
                            currentBuilder.lineStart().write(hsParameterType).space().write(targetArrayVariable).lineEnd(";");
                            generateAllocateJObjectArray(currentBuilder, componentType, parameterName, targetArrayVariable, arrayLength, jniEnvFieldName);
                        }
                    } else {
                        currentBuilder.lineStart("if (").write(parameterName).write(" != null) ").lineEnd("{");
                        currentBuilder.indent();
                        boolean copyContent = out == null || in != null;
                        generateWriteNativeObjectArray(currentBuilder, parameterName, copyContent, true, arrayOffsetParameter, arrayLength, marshalledParametersOutput,
                                        (element) -> marshallParameter(currentBuilder, componentType, element, marshalledParametersOutput, jniEnvFieldName));
                        currentBuilder.dedent();
                        currentBuilder.line("} else {");
                        currentBuilder.indent();
                        currentBuilder.lineStart().invoke(marshalledParametersOutput, "writeInt", "-1").lineEnd(";");
                        currentBuilder.dedent();
                        currentBuilder.line("}");
                    }
                    return true;
                }
                return false;
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName) {
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    if (marshallerData.sameDirection) {
                        // Already marshalled by preMarshallParameter, return the target hotspot
                        // object array.
                        return Utilities.javaMemberName("hs", formalParameter);
                    } else {
                        // Already marshalled by preMarshallParameter, return no-code (null)
                        return null;
                    }
                }
                if (marshallerData.sameDirection) {
                    return marshallNativeToHotSpotProxyInNative(currentBuilder, formalParameter);
                } else {
                    return marshallHotSpotToNativeProxyInNative(currentBuilder, formalParameter);
                }
            }

            @Override
            void postMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence receiver, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName, CharSequence resultVariableName) {
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    AnnotationMirror out = findOut(marshallerData.annotations);
                    if (out != null) {
                        // Out parameter. We need to copy the content from buffer to array.
                        currentBuilder.lineStart("if (").write(parameterName).write(" != null) ").lineEnd("{");
                        currentBuilder.indent();
                        CharSequence sourceArrayVariable = Utilities.javaMemberName("hs", parameterName);
                        TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                        boolean trimToResult = trimToResult(out);
                        CharSequence arrayLength;
                        if (trimToResult) {
                            arrayLength = resultVariableName;
                        } else {
                            arrayLength = getArrayLengthParameterName(out);
                        }
                        if (arrayLength == null) {
                            arrayLength = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                        }
                        CharSequence arrayOffsetParameter = getArrayOffsetParameterName(out);
                        if (marshallerData.sameDirection) {
                            generateCopyHotSpotToHSObjectArray(currentBuilder, parameterName, arrayOffsetParameter, arrayLength, sourceArrayVariable, null, jniEnvFieldName,
                                            (element) -> unmarshallResult(currentBuilder, componentType, element, receiver, null, jniEnvFieldName));
                        } else {
                            generateReadNativeObjectArray(currentBuilder, parameterName, arrayOffsetParameter, arrayLength, marshalledParametersInput,
                                            (element) -> unmarshallResult(currentBuilder, componentType, element, null, marshalledParametersInput, jniEnvFieldName));
                        }
                        currentBuilder.dedent();
                        currentBuilder.line("}");
                    }
                }
            }

            @Override
            CharSequence storeRawResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
                CharSequence resultVariable = "endPointResult";
                if (marshallerData.sameDirection) {
                    currentBuilder.lineStart().write(jniTypeForJavaType(resultType, types, cache)).space().write(resultVariable).write(" = ").write(invocationSnippet).lineEnd(";");
                    return resultVariable;
                } else if (resultType.getKind() == TypeKind.ARRAY) {
                    CharSequence jLongArray = new CodeBuilder(currentBuilder).cast(cache.jLongArray, invocationSnippet).build();
                    currentBuilder.lineStart().write(getEndPointMethodParameterType(resultType)).space().write(resultVariable).write(" = ").invokeStatic(cache.jniUtil, "createArray", jniEnvFieldName,
                                    jLongArray).lineEnd(";");
                    return resultVariable;
                } else {
                    return null;
                }
            }

            @Override
            CharSequence preUnmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence jniEnvFieldName) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    CharSequence resultVar = "hsResult";
                    TypeMirror componentType = ((ArrayType) resultType).getComponentType();
                    currentBuilder.lineStart().write(resultType).space().write(resultVar).lineEnd(";");
                    if (marshallerData.sameDirection) {
                        currentBuilder.lineStart("if (").invoke(invocationSnippet, "isNonNull").write(") ").lineEnd("{");
                        currentBuilder.indent();
                        currentBuilder.lineStart(resultVar).write(" = ").newArray(componentType,
                                        new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "GetArrayLength", jniEnvFieldName, invocationSnippet).build()).lineEnd(";");
                        CharSequence lengthSnippet = new CodeBuilder(currentBuilder).memberSelect(resultVar, "length", false).build();
                        generateCopyHotSpotToHSObjectArray(currentBuilder, resultVar, null, lengthSnippet, invocationSnippet, null, jniEnvFieldName,
                                        (element) -> unmarshallResult(currentBuilder, componentType, element, receiver, null, jniEnvFieldName));
                    } else {
                        currentBuilder.lineStart("if(").write(invocationSnippet).write(" != null) ").lineEnd("{");
                        currentBuilder.indent();
                        CharSequence arrayLength = new CodeBuilder(currentBuilder).memberSelect(invocationSnippet, "length", false).build();
                        currentBuilder.lineStart(resultVar).write(" = ").newArray(componentType, arrayLength).lineEnd(";");
                        CharSequence index = resultVar + "Index";
                        currentBuilder.lineStart().forLoop(List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(index).write(" = 0").build()),
                                        new CodeBuilder(currentBuilder).write(index).write(" < ").write(arrayLength).build(),
                                        List.of(new CodeBuilder(currentBuilder).write(index).write("++").build())).lineEnd(" {");
                        currentBuilder.indent();
                        CharSequence element = new CodeBuilder(currentBuilder).arrayElement(invocationSnippet, index).build();
                        currentBuilder.lineStart().arrayElement(resultVar, index).write(" = ").write(unmarshallResult(currentBuilder, componentType, element, receiver, null, jniEnvFieldName)).lineEnd(
                                        ";");
                        currentBuilder.dedent();
                        currentBuilder.line("}");
                    }
                    currentBuilder.dedent();
                    currentBuilder.line("} else {");
                    currentBuilder.indent();
                    currentBuilder.lineStart(resultVar).write(" = null").lineEnd(";");
                    currentBuilder.dedent();
                    currentBuilder.line("}");
                    return resultVar;
                }
                return null;
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence marshalledResultInput,
                            CharSequence jniEnvFieldName) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    // Already unmarshalled by preUnmarshallResult. Just return the
                    // invocationSnippet.
                    return invocationSnippet;
                } else if (marshallerData.sameDirection) {
                    return unmarshallNativeToHotSpotProxyInNative(currentBuilder, invocationSnippet, jniEnvFieldName);
                } else {
                    return unmarshallHotSpotToNativeProxyInNative(currentBuilder, resultType, invocationSnippet, data);
                }
            }

            @Override
            CharSequence preMarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                if (!marshallerData.sameDirection && resultType.getKind() == TypeKind.ARRAY) {
                    CharSequence hsResult = "hsResult";
                    currentBuilder.lineStart().write(getEndPointMethodParameterType(resultType)).space().write(hsResult).lineEnd(";");
                    currentBuilder.lineStart("if (").write(invocationSnippet).write(" != null) ").lineEnd("{");
                    currentBuilder.indent();
                    CharSequence arrayLength = new CodeBuilder(currentBuilder).memberSelect(invocationSnippet, "length", false).build();
                    currentBuilder.lineStart(hsResult).write(" = ").newArray(types.getPrimitiveType(TypeKind.LONG), arrayLength).lineEnd(";");
                    CharSequence index = hsResult + "Index";
                    currentBuilder.lineStart().forLoop(List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(index).write(" = 0").build()),
                                    new CodeBuilder(currentBuilder).write(index).write(" < ").write(arrayLength).build(),
                                    List.of(new CodeBuilder(currentBuilder).write(index).write("++").build())).lineEnd(" {");
                    currentBuilder.indent();
                    TypeMirror componentType = ((ArrayType) resultType).getComponentType();
                    CharSequence arrayElement = new CodeBuilder(currentBuilder).arrayElement(invocationSnippet, index).build();
                    currentBuilder.lineStart().arrayElement(hsResult, index).write(" = ").write(marshallResult(currentBuilder, componentType, arrayElement, null, jniEnvFieldName)).lineEnd(";");
                    currentBuilder.dedent();
                    currentBuilder.line("}");
                    currentBuilder.dedent();
                    currentBuilder.line("} else {");
                    currentBuilder.indent();
                    currentBuilder.lineStart(hsResult).write(" = null").lineEnd(";");
                    currentBuilder.dedent();
                    currentBuilder.line("}");
                    return hsResult;
                }
                return null;
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    // Already marshalled by preMarshallResult. Just return the invocationSnippet.
                    return invocationSnippet;
                } else if (marshallerData.sameDirection) {
                    return invocationSnippet;
                } else {
                    return marshallHotSpotToNativeProxyInHotSpot(currentBuilder, invocationSnippet);
                }
            }

            @Override
            boolean preUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput, CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverride) {
                if (parameterType.getKind() == TypeKind.ARRAY && !marshallerData.sameDirection) {
                    TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                    AnnotationMirror in = findIn(marshallerData.annotations);
                    AnnotationMirror out = findOut(marshallerData.annotations);
                    currentBuilder.lineStart().write(parameterType).space().write(parameterName).lineEnd(";");
                    CharSequence arrayLengthVariable = parameterName + "Length";
                    currentBuilder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(arrayLengthVariable).write(" = ").invoke(marshalledParametersInput, "readInt").lineEnd(";");
                    currentBuilder.lineStart("if(").write(arrayLengthVariable).write(" != -1) ").lineEnd("{");
                    currentBuilder.indent();
                    currentBuilder.lineStart(parameterName).write(" = ").newArray(componentType, arrayLengthVariable).lineEnd(";");
                    if (out == null || in != null) {
                        // Default (in), in or in-out parameter
                        generateReadNativeObjectArray(currentBuilder, parameterName, null, arrayLengthVariable, marshalledParametersInput,
                                        (element) -> unmarshallParameter(currentBuilder, componentType, element, marshalledParametersInput, jniEnvFieldName));
                    }
                    currentBuilder.dedent();
                    currentBuilder.line("} else {");
                    currentBuilder.indent();
                    currentBuilder.lineStart(parameterName).write(" = null").lineEnd(";");
                    currentBuilder.dedent();
                    currentBuilder.line("}");
                }
                return false;
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput, CharSequence jniEnvFieldName) {
                if (marshallerData.sameDirection) {
                    return unmarshallNativeToHotSpotProxyInHotSpot(currentBuilder, parameterType, parameterName, data);
                } else if (parameterType.getKind() == TypeKind.ARRAY) {
                    return parameterName;
                } else {
                    CodeBuilder currentIsolateBuilder = new CodeBuilder(currentBuilder).invokeStatic(cache.nativeIsolate, "get", REFERENCE_ISOLATE_ADDRESS_NAME);
                    return unmarshallHotSpotToNativeProxyInHotSpot(currentBuilder, parameterName, currentIsolateBuilder.build());
                }
            }

            @Override
            void postUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName,
                            CharSequence resultVariableName) {
                if (!marshallerData.sameDirection && parameterType.getKind() == TypeKind.ARRAY) {
                    AnnotationMirror out = findOut(marshallerData.annotations);
                    if (out != null) {
                        CharSequence arrayLengthSnippet;
                        CharSequence arrayLengthParameter = getArrayLengthParameterName(out);
                        if (trimToResult(out)) {
                            arrayLengthSnippet = resultVariableName;
                        } else if (arrayLengthParameter != null) {
                            arrayLengthSnippet = arrayLengthParameter;
                        } else {
                            arrayLengthSnippet = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                        }
                        currentBuilder.lineStart("if (").write(parameterName).write(" != null) ").lineEnd("{");
                        currentBuilder.indent();
                        TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                        generateWriteNativeObjectArray(currentBuilder, parameterName, true, false, null, arrayLengthSnippet, marshalledResultOutput,
                                        (element) -> marshallResult(currentBuilder, componentType, element, marshalledResultOutput, jniEnvFieldName));
                        currentBuilder.dedent();
                        currentBuilder.line("}");
                    }
                }
            }
        }

        private static final class RawReferenceSnippets extends NativeToHotSpotMarshallerSnippets {

            RawReferenceSnippets(NativeBridgeProcessor processor, MarshallerData marshallerData, Types types, TypeCache cache, BinaryNameCache binaryNameCache) {
                super(processor, marshallerData, types, cache, binaryNameCache);
            }

            @Override
            TypeMirror getEndPointMethodParameterType(TypeMirror type) {
                return cache.object;
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName) {
                CharSequence value = new CodeBuilder(currentBuilder).cast(types.getPrimitiveType(TypeKind.LONG), formalParameter).build();
                return new CodeBuilder(currentBuilder).invokeStatic(cache.wordFactory, "pointer", value).build();
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput, CharSequence jniEnvFieldName) {
                return parameterName;
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                return invocationSnippet;
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence marshalledResultInput,
                            CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(invocationSnippet, "rawValue").build();
            }
        }

        private static final class CustomSnippets extends NativeToHotSpotMarshallerSnippets {

            CustomSnippets(NativeBridgeProcessor processor, MarshallerData marshallerData, Types types, TypeCache cache, BinaryNameCache binaryNameCache) {
                super(processor, marshallerData, types, cache, binaryNameCache);
            }

            @Override
            TypeMirror getEndPointMethodParameterType(TypeMirror type) {
                return types.getArrayType(types.getPrimitiveType(TypeKind.BYTE));
            }

            @Override
            boolean preMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverrides) {
                currentBuilder.lineStart().invoke(marshallerData.name, "write", marshalledParametersOutput, parameterName).lineEnd(";");
                return false;
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName) {
                // Already marshalled by preMarshallParameter, return no-code (null)
                return null;
            }

            @Override
            CharSequence storeRawResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
                CharSequence resultVariable = "endPointResult";
                currentBuilder.lineStart().write(cache.jByteArray).space().write(resultVariable).write(" = ").write(invocationSnippet).lineEnd(";");
                return resultVariable;
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence marshalledResultInput,
                            CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "read", marshalledResultInput).build();
            }

            @Override
            CharSequence preMarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                currentBuilder.lineStart().invoke(marshallerData.name, "write", marshalledResultOutput, invocationSnippet).lineEnd(";");
                return null;
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshalledResultOutput, "getArray").build();
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "read", marshalledParametersInput).build();
            }
        }
    }
}
