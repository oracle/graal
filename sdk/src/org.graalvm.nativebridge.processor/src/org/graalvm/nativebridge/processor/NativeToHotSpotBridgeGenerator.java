/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.AbstractMap.SimpleImmutableEntry;
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
import org.graalvm.nativebridge.processor.AbstractBridgeParser.AbstractEndPointMethodProvider;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MarshallerData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MethodData;
import org.graalvm.nativebridge.processor.NativeToHotSpotBridgeParser.TypeCache;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

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
    MarshallerSnippet marshallerSnippets(MarshallerData marshallerData) {
        return NativeToHotSpotMarshallerSnippet.forData(this, marshallerData);
    }

    private void generateNativeToHSStartPoint(CodeBuilder builder, FactoryMethodInfo factoryMethodInfo) {
        CacheSnippet cacheSnippets = cacheSnippets();
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

        generateJNIData(builder);
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

    private void generateJNIData(CodeBuilder builder) {
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
            List<? extends TypeMirror> signature = parser.endPointMethodProvider.getEndPointSignature(methodData, definitionData.serviceType, definitionData.hasCustomDispatch());
            boolean hasOutMarshalledParameter = false;
            for (int i = 0; i < parameterTypes.size(); i++) {
                MarshallerData marshallerData = methodData.getParameterMarshaller(i);
                if (marshallerData.isCustom()) {
                    TypeMirror parameterType = parameterTypes.get(i);
                    if (isOutParameter(marshallerData, parameterType, false)) {
                        hasOutMarshalledParameter = true;
                    }
                }
            }
            TypeMirror returnType = hasOutMarshalledParameter ? types.getArrayType(types.getPrimitiveType(TypeKind.BYTE))
                            : parser.endPointMethodProvider.getEndPointMethodParameterType(methodData.getReturnTypeMarshaller(), methodData.type.getReturnType());
            builder.lineStart("this." + fieldName).write(" = ").invokeStatic(typeCache.jNIMethod, "findMethod", jniEnv.name, END_POINT_CLASS_FIELD, "true",
                            "\"" + methodData.element.getSimpleName() + "\"",
                            "\"" + Utilities.encodeMethodSignature(parser.elements, types, returnType, signature.toArray(new TypeMirror[0])) + "\"").lineEnd(";");
        }
    }

    private void generateNativeToHSStartMethod(CodeBuilder builder, CacheSnippet cacheSnippets, MethodData methodData) {
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
        MarshallerData firstOutMarshalledParameter = null;
        for (int i = nonReceiverParameterStart; i < methodData.element.getParameters().size(); i++) {
            MarshallerData marshallerData = methodData.getParameterMarshaller(i);
            TypeMirror parameterType = methodData.type.getParameterTypes().get(i);
            if (isBinaryMarshallable(marshallerData, parameterType, false)) {
                binaryMarshalledParameters.add(i);
            }
            if (isOutParameter(marshallerData, parameterType, false)) {
                hasOutParameters = true;
                if (firstOutMarshalledParameter == null && marshallerData.isCustom()) {
                    firstOutMarshalledParameter = marshallerData;
                }
            }
        }
        MarshallerData resultMarshallerData = methodData.getReturnTypeMarshaller();
        int staticBufferSize = getStaticBufferSize(binaryMarshalledParameters.size(), resultMarshallerData.isCustom(), firstOutMarshalledParameter != null);
        AbstractBridgeParser.CacheData cacheData = methodData.cachedData;
        CharSequence args;
        CharSequence env;
        TypeMirror returnType = methodData.type.getReturnType();
        boolean voidMethod = returnType.getKind() == TypeKind.VOID;
        MarshallerSnippet resultMarshallerSnippets = marshallerSnippets(resultMarshallerData);
        if (cacheData != null) {
            if (hasOutParameters) {
                throw new IllegalStateException("Idempotent cannot be used with Out parameters.");
            }
            String cacheFieldName = cacheData.cacheFieldName;
            String resultVariable = cacheFieldName + "Result";
            builder.lineStart().write(cacheData.cacheEntryType).space().write(resultVariable).write(" = ").write(cacheSnippets.readCache(builder, cacheFieldName, receiver)).lineEnd(";");
            builder.lineStart("if (").write(resultVariable).lineEnd(" == null) {");
            builder.indent();
            builder.line("try {");
            builder.indent();
            env = generateLookupJNIEnv(builder);
            CharSequence staticMarshallBufferVar = generateStaticBuffer(builder, staticBufferSize);
            Map<String, CharSequence> parameterValueOverrides = new HashMap<>();
            BinaryMarshallVariables binaryMarshallVars = generateBinaryMarshallProlog(builder, methodData, nonReceiverParameterStart,
                            binaryMarshalledParameters, staticMarshallBufferVar, staticBufferSize, firstOutMarshalledParameter != null,
                            parameterValueOverrides);
            boolean hasPostMarshall = generatePreMarshall(builder, methodData, nonReceiverParameterStart, binaryMarshallVars != null ? binaryMarshallVars.outputVariable : null, env,
                            parameterValueOverrides);
            if (binaryMarshallVars != null) {
                generateBinaryMarshallEpilogue(builder, binaryMarshallVars, env, firstOutMarshalledParameter != null);
            }
            args = generatePushArgs(builder, methodData, nonReceiverParameterStart, env, receiver, parameterValueOverrides, binaryMarshalledParameters,
                            binaryMarshallVars != null ? binaryMarshallVars.jniBufferVariable : null);
            CharSequence jniCall = callHotSpot(builder, methodData, env, args, resultMarshallerData);
            Map.Entry<CharSequence, CharSequence> binaryUnmarshallVars = null;
            PreUnmarshallResult preUnmarshallResult = generatePreUnmarshallResult(builder, resultMarshallerSnippets, binaryMarshallVars, returnType, jniCall, receiver, env,
                            resultMarshallerData.isCustom(), staticMarshallBufferVar, staticBufferSize);
            if (preUnmarshallResult.binaryInput != null) {
                assert preUnmarshallResult.binaryInputResourcesToFree != null;
                binaryUnmarshallVars = new SimpleImmutableEntry<>(preUnmarshallResult.binaryInputResourcesToFree, preUnmarshallResult.binaryInput);
            }
            builder.lineStart().write(resultVariable).write(" = ");
            builder.write(resultMarshallerSnippets.unmarshallResult(builder, returnType, preUnmarshallResult.result, receiver, binaryUnmarshallVars != null ? binaryUnmarshallVars.getValue() : null,
                            env));
            builder.lineEnd(";");
            if (hasPostMarshall) {
                binaryUnmarshallVars = generatePostMarshall(builder, methodData, nonReceiverParameterStart, binaryMarshallVars, binaryUnmarshallVars, env, resultVariable,
                                staticMarshallBufferVar, staticBufferSize, hasOutParameters);
            }
            builder.lineStart().write(cacheSnippets.writeCache(builder, cacheFieldName, receiver, resultVariable)).lineEnd(";");
            if (binaryMarshallVars != null) {
                generateCleanupBinaryInputForJByteArray(builder, binaryMarshallVars.cBufferVariable, staticMarshallBufferVar);
            } else if (binaryUnmarshallVars != null) {
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
            Map<String, CharSequence> parameterValueOverrides = new HashMap<>();
            BinaryMarshallVariables binaryMarshallVars = generateBinaryMarshallProlog(builder, methodData, nonReceiverParameterStart,
                            binaryMarshalledParameters, staticMarshallBufferVar, staticBufferSize, firstOutMarshalledParameter != null,
                            parameterValueOverrides);
            boolean hasPostMarshall = generatePreMarshall(builder, methodData, nonReceiverParameterStart, binaryMarshallVars != null ? binaryMarshallVars.outputVariable : null, env,
                            parameterValueOverrides);
            if (binaryMarshallVars != null) {
                generateBinaryMarshallEpilogue(builder, binaryMarshallVars, env, firstOutMarshalledParameter != null);
            }
            args = generatePushArgs(builder, methodData, nonReceiverParameterStart, env, receiver, parameterValueOverrides, binaryMarshalledParameters,
                            binaryMarshallVars != null ? binaryMarshallVars.jniBufferVariable : null);
            CharSequence resultVariable = null;
            Map.Entry<CharSequence, CharSequence> binaryUnmarshallVars = null;
            if (firstOutMarshalledParameter != null) {
                hasPostMarshall = true;
                CharSequence jniCall = callHotSpot(builder, methodData, env, args, firstOutMarshalledParameter);
                MarshallerSnippet customMarshalledSnippets = marshallerSnippets(firstOutMarshalledParameter);
                CharSequence endPointResult = generateStoreEndPointResult(builder, customMarshalledSnippets, returnType, jniCall, env);
                binaryUnmarshallVars = generateCreateBinaryInputForJByteArray(builder, binaryMarshallVars, endPointResult, "marshalledResultInput", env, staticMarshallBufferVar, staticBufferSize);
                if (!voidMethod) {
                    resultVariable = "result";
                    resultMarshallerSnippets.read(builder, returnType, resultVariable, binaryUnmarshallVars.getValue(), env);
                }
            } else {
                CharSequence jniCall = callHotSpot(builder, methodData, env, args, resultMarshallerData);
                PreUnmarshallResult preUnmarshallResult = generatePreUnmarshallResult(builder, resultMarshallerSnippets, binaryMarshallVars, returnType, jniCall, receiver, env,
                                resultMarshallerData.isCustom(), staticMarshallBufferVar, staticBufferSize);
                if (preUnmarshallResult.binaryInput != null) {
                    assert preUnmarshallResult.binaryInputResourcesToFree != null;
                    binaryUnmarshallVars = new SimpleImmutableEntry<>(preUnmarshallResult.binaryInputResourcesToFree, preUnmarshallResult.binaryInput);
                }
                if (hasPostMarshall && !voidMethod) {
                    resultVariable = "result";
                }
                builder.lineStart();
                if (resultVariable != null) {
                    builder.write(returnType).space().write(resultVariable).write(" = ");
                } else if (!voidMethod) {
                    builder.write("return ");
                }
                builder.write(resultMarshallerSnippets.unmarshallResult(builder, returnType, preUnmarshallResult.result, receiver,
                                binaryUnmarshallVars != null ? binaryUnmarshallVars.getValue() : null,
                                env));
                builder.lineEnd(";");
            }
            if (hasPostMarshall) {
                binaryUnmarshallVars = generatePostMarshall(builder, methodData, nonReceiverParameterStart, binaryMarshallVars, binaryUnmarshallVars,
                                env, resultVariable, staticMarshallBufferVar, staticBufferSize, hasOutParameters);
            }
            if (resultVariable != null) {
                // Return was deferred after post unmarshalling statements.
                // Do it now.
                builder.lineStart("return ").write(resultVariable).lineEnd(";");
            }
            if (binaryMarshallVars != null) {
                generateCleanupBinaryInputForJByteArray(builder, binaryMarshallVars.cBufferVariable, staticMarshallBufferVar);
            } else if (binaryUnmarshallVars != null) {
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

    private static CharSequence generateStoreEndPointResult(CodeBuilder builder, MarshallerSnippet snippets, TypeMirror returnType, CharSequence nativeCall, CharSequence jniEnvFieldName) {
        return snippets.storeRawResult(builder, returnType, nativeCall, jniEnvFieldName);
    }

    private PreUnmarshallResult generatePreUnmarshallResult(CodeBuilder builder, MarshallerSnippet snippets, BinaryMarshallVariables binaryMarshallVars, TypeMirror returnType,
                    CharSequence nativeCall, CharSequence receiver, CharSequence jniEnvFieldName, boolean hasBinaryMarshalledResult,
                    CharSequence staticMarshallBufferVar, int staticBufferSize) {
        CharSequence result = nativeCall;
        CharSequence endPointResultVariable = generateStoreEndPointResult(builder, snippets, returnType, nativeCall, jniEnvFieldName);
        result = endPointResultVariable != null ? endPointResultVariable : result;
        Map.Entry<CharSequence, CharSequence> resourceInputPair = hasBinaryMarshalledResult
                        ? generateCreateBinaryInputForJByteArray(builder, binaryMarshallVars, result, "marshalledResultInput", jniEnvFieldName, staticMarshallBufferVar, staticBufferSize)
                        : null;
        CharSequence resource = resourceInputPair != null ? resourceInputPair.getKey() : null;
        CharSequence input = resourceInputPair != null ? resourceInputPair.getValue() : null;
        CharSequence resultVariable = snippets.preUnmarshallResult(builder, returnType, result, receiver, input, jniEnvFieldName);
        result = resultVariable != null ? resultVariable : result;
        return new PreUnmarshallResult(result, resource, input, resultVariable != null);
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

    private BinaryMarshallVariables generateBinaryMarshallProlog(CodeBuilder builder, MethodData methodData, int nonReceiverParameterStart,
                    List<Integer> binaryMarshalledParameters, CharSequence staticBufferVar, int staticBufferLength, boolean reserveSpaceForResult,
                    Map<String, CharSequence> parameterValueOverrides) {
        if (binaryMarshalledParameters.isEmpty()) {
            generatePreMarshallLocalVariables(builder, methodData, nonReceiverParameterStart, parameterValueOverrides);
        } else {
            CharSequence jniBufferVar = "marshalledParameters";
            CharSequence cBufferVar = "marshallBuffer";
            CharSequence sizeVar = "marshallBufferSize";
            CharSequence marshalledParametersOutputVar = "marshalledParametersOutput";
            CharSequence sizeEstimateVar = generateSizeEstimate(builder, methodData, binaryMarshalledParameters, reserveSpaceForResult);
            builder.lineStart().write(typeCache.cCharPointer).space().write(cBufferVar).lineEnd(";");
            builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(sizeVar).lineEnd(";");
            builder.lineStart("if (").write(sizeEstimateVar).write(" > ").write(Integer.toString(staticBufferLength)).lineEnd(") {");
            builder.indent();
            builder.lineStart(cBufferVar).write(" = ").invokeStatic(typeCache.unmanagedMemory, "malloc", sizeEstimateVar).lineEnd(";");
            builder.lineStart(sizeVar).write(" = ").write(sizeEstimateVar).lineEnd(";");
            builder.dedent();
            builder.line("} else {");
            builder.indent();
            builder.lineStart(cBufferVar).write(" = ").write(staticBufferVar).lineEnd(";");
            builder.lineStart(sizeVar).write(" = ").write(Integer.toString(staticBufferLength)).lineEnd(";");
            builder.dedent();
            builder.line("}");
            builder.line("try {");
            builder.indent();
            generatePreMarshallLocalVariables(builder, methodData, nonReceiverParameterStart, parameterValueOverrides);
            builder.lineStart().write(typeCache.jByteArray).space().write(jniBufferVar).lineEnd(";");
            builder.lineStart("try (").write(typeCache.cCharPointerBinaryOutput).space().write(marshalledParametersOutputVar).write(" = ").invokeStatic(typeCache.binaryOutput, "create", cBufferVar,
                            sizeVar, "false").lineEnd(") {");
            builder.indent();
            return new BinaryMarshallVariables(jniBufferVar, cBufferVar, sizeVar, marshalledParametersOutputVar);
        }
        return null;
    }

    private void generateBinaryMarshallEpilogue(CodeBuilder builder, BinaryMarshallVariables binaryMarshallVars, CharSequence jniEnv, boolean hasCustomOutParameter) {
        CharSequence marshalledParametersPositionVar = "marshalledParametersPosition";
        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(marshalledParametersPositionVar).write(" = ").invoke(binaryMarshallVars.outputVariable, "getPosition").lineEnd(
                        ";");
        CharSequence jniBufferLength;
        if (hasCustomOutParameter) {
            jniBufferLength = new CodeBuilder(builder).invokeStatic(typeCache.math, "max", marshalledParametersPositionVar, binaryMarshallVars.sizeVariable).build();
        } else {
            jniBufferLength = marshalledParametersPositionVar;
        }
        builder.lineStart().write(binaryMarshallVars.jniBufferVariable).write(" = ").invokeStatic(typeCache.jniUtil, "NewByteArray", jniEnv, jniBufferLength).lineEnd(";");
        CharSequence address = new CodeBuilder(builder).invoke(binaryMarshallVars.outputVariable, "getAddress").build();
        builder.lineStart().invokeStatic(typeCache.jniUtil, "SetByteArrayRegion", jniEnv, binaryMarshallVars.jniBufferVariable, "0", marshalledParametersPositionVar, address).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    private Map.Entry<CharSequence, CharSequence> generateCreateBinaryInputForJByteArray(CodeBuilder builder, BinaryMarshallVariables binaryMarshallVars, CharSequence jByteArray,
                    CharSequence marshalledResultInputVar, CharSequence jniEnv, CharSequence staticMarshallBufferVar, int staticBufferSize) {
        CharSequence marshalledDataLengthVar = "marshalledDataLength";
        CharSequence marshallBufferVar = "marshallBuffer";
        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(marshalledDataLengthVar).write(" = ").invokeStatic(typeCache.jniUtil, "GetArrayLength", jniEnv,
                        jByteArray).lineEnd(";");
        if (binaryMarshallVars != null) {
            builder.lineStart("if (").write(binaryMarshallVars.sizeVariable).write(" < ").write(marshalledDataLengthVar).lineEnd(") {");
            builder.indent();
            generateUnmanagedFree(builder, marshallBufferVar, staticMarshallBufferVar);
            builder.lineStart(marshallBufferVar).write(" = ").invokeStatic(typeCache.unmanagedMemory, "malloc", marshalledDataLengthVar).lineEnd(";");
            builder.dedent();
            builder.line("}");
        } else {
            builder.lineStart().write(typeCache.cCharPointer).space().write(marshallBufferVar).write(" = ").write(marshalledDataLengthVar).write(" <= ").write(
                            Integer.toString(staticBufferSize)).write(
                                            " ? ").write(staticMarshallBufferVar).write(" : ").invokeStatic(typeCache.unmanagedMemory, "malloc", marshalledDataLengthVar).lineEnd(";");
            builder.line("try {");
            builder.indent();
        }
        builder.lineStart().invokeStatic(typeCache.jniUtil, "GetByteArrayRegion", jniEnv, jByteArray, "0", marshalledDataLengthVar, marshallBufferVar).lineEnd(";");
        builder.lineStart().write(typeCache.binaryInput).space().write(marshalledResultInputVar).write(" = ").invokeStatic(typeCache.binaryInput, "create", marshallBufferVar,
                        marshalledDataLengthVar).lineEnd(";");
        return new SimpleImmutableEntry<>(marshallBufferVar, marshalledResultInputVar);
    }

    private void generateCleanupBinaryInputForJByteArray(CodeBuilder builder, CharSequence marshallBufferVar, CharSequence staticMarshallBufferVar) {
        builder.dedent();
        builder.line("} finally {");
        builder.indent();
        generateUnmanagedFree(builder, marshallBufferVar, staticMarshallBufferVar);
        builder.dedent();
        builder.line("}");
    }

    private void generateUnmanagedFree(CodeBuilder builder, CharSequence marshallBufferVar, CharSequence staticMarshallBufferVar) {
        builder.lineStart("if (").write(marshallBufferVar).write(" != ").write(staticMarshallBufferVar).lineEnd(") {");
        builder.indent();
        builder.lineStart().invokeStatic(typeCache.unmanagedMemory, "free", marshallBufferVar).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    private void generatePreMarshallLocalVariables(CodeBuilder builder, MethodData methodData, int nonReceiverParameterStart, Map<String, CharSequence> parameterValueOverrides) {
        List<? extends VariableElement> formalParameters = methodData.element.getParameters();
        List<? extends TypeMirror> formalParameterTypes = methodData.type.getParameterTypes();
        for (int i = nonReceiverParameterStart; i < formalParameters.size(); i++) {
            MarshallerData marshaller = methodData.getParameterMarshaller(i);
            marshallerSnippets(marshaller).declarePerMarshalledParameterVariable(builder, formalParameterTypes.get(i),
                            formalParameters.get(i).getSimpleName(), parameterValueOverrides);
        }
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
                    BinaryMarshallVariables binaryMarshallVars, Map.Entry<CharSequence, CharSequence> inputData, CharSequence jniEnv,
                    CharSequence resultVariable, CharSequence staticMarshallBufferVar, int staticBufferSize, boolean hasOutParameters) {
        Map.Entry<CharSequence, CharSequence> res = inputData;
        if (hasOutParameters && res == null) {
            res = generateCreateBinaryInputForJByteArray(builder, binaryMarshallVars, binaryMarshallVars.jniBufferVariable, "marshalledParametersInput", jniEnv, staticMarshallBufferVar,
                            staticBufferSize);
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
            MarshallerSnippet marshallerSnippets = marshallerSnippets(marshaller);
            if (parameterValueOverride != null) {
                value = parameterValueOverride;
            } else {
                value = marshallerSnippets.marshallParameter(builder, formalParameterTypes.get(i), parameterName, null, jniEnv);
            }
            builder.lineStart().invoke(address, jValueSetterName(parser.endPointMethodProvider.getEndPointMethodParameterType(marshaller, formalParameterTypes.get(i))), value).lineEnd(";");
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
        generateSizeEstimate(builder, sizeVar, marshalledParameters, false, true);
        builder.lineStart().write(typeCache.byteArrayBinaryOutput).space().write(marshalledResultOutputVar).write(" = ");
        if (marshalledData != null) {
            builder.write(sizeVar).write(" > ").memberSelect(marshalledData, "length", false).write(" ? ").invokeStatic(typeCache.byteArrayBinaryOutput, "create", sizeVar).write(" : ").invokeStatic(
                            typeCache.binaryOutput, "create", marshalledData);
        } else {
            builder.invokeStatic(typeCache.byteArrayBinaryOutput, "create", sizeVar);
        }
        builder.lineEnd(";");
    }

    private CharSequence generateSizeEstimate(CodeBuilder builder, MethodData methodData, List<Integer> customParameters, boolean reserveSpaceForResult) {
        CharSequence sizeVar = "marshalledParametersSizeEstimate";
        List<? extends VariableElement> parameters = methodData.element.getParameters();
        List<? extends TypeMirror> parameterTypes = methodData.type.getParameterTypes();
        List<MarshalledParameter> marshallers = new ArrayList<>();
        if (reserveSpaceForResult && methodData.type.getReturnType().getKind() != TypeKind.VOID) {
            marshallers.add(new MarshalledParameter(methodData.type.getReturnType(), methodData.getReturnTypeMarshaller()));
        }
        for (int index : customParameters) {
            marshallers.add(new MarshalledParameter(parameters.get(index).getSimpleName(), parameterTypes.get(index), false, methodData.getParameterMarshaller(index)));
        }
        generateSizeEstimate(builder, sizeVar, marshallers, true, false);
        return sizeVar;
    }

    private CharSequence jValueSetterName(TypeMirror type) {
        return switch (types.erasure(type).getKind()) {
            case BOOLEAN -> "setBoolean";
            case BYTE -> "setByte";
            case CHAR -> "setChar";
            case SHORT -> "setShort";
            case INT -> "setInt";
            case LONG -> "setLong";
            case FLOAT -> "setFloat";
            case DOUBLE -> "setDouble";
            case ARRAY, DECLARED -> "setJObject";
            default -> throw new IllegalArgumentException(types.erasure(type).getKind().toString());
        };
    }

    private CharSequence callHotSpot(CodeBuilder builder, MethodData methodData, CharSequence jniEnv, CharSequence args,
                    MarshallerData marshaller) {
        CharSequence hsCallsInstance = new CodeBuilder(builder).invokeStatic(typeCache.foreignException, "getJNICalls").build();
        TypeMirror retType = methodData.type.getReturnType();
        return new CodeBuilder(builder).invoke(hsCallsInstance, callHotSpotName(parser.endPointMethodProvider.getEndPointMethodParameterType(marshaller, retType)),
                        jniEnv, "jniMethods_." + END_POINT_CLASS_FIELD, "jniMethods_." + jMethodIdField(methodData), args).build();
    }

    private CharSequence callHotSpotName(TypeMirror type) {
        return switch (types.erasure(type).getKind()) {
            case VOID -> "callStaticVoid";
            case BOOLEAN -> "callStaticBoolean";
            case BYTE -> "callStaticByte";
            case CHAR -> "callStaticChar";
            case SHORT -> "callStaticShort";
            case INT -> "callStaticInt";
            case LONG -> "callStaticLong";
            case FLOAT -> "callStaticFloat";
            case DOUBLE -> "callStaticDouble";
            case ARRAY, DECLARED -> "callStaticJObject";
            default -> throw new IllegalArgumentException(types.erasure(type).getKind().toString());
        };
    }

    private static CharSequence jMethodIdField(MethodData methodData) {
        StringBuilder name = new StringBuilder(methodData.element.getSimpleName());
        name.append("Method");
        if (methodData.hasOverload()) {
            name.append(1 + methodData.overloadId);
        }
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
        warnings.add("unused");
        for (int i = 0; i < methodParameters.size(); i++) {
            warnings.addAll(marshallerSnippets(methodData.getParameterMarshaller(i)).getEndPointSuppressedWarnings(builder, methodParameterTypes.get(i)));
        }
        builder.lineStart().annotation(typeCache.suppressWarnings, warnings.toArray(new CharSequence[0])).lineEnd("");
        List<CodeBuilder.Parameter> params = new ArrayList<>();
        if (!definitionData.hasCustomDispatch()) {
            params.add(CodeBuilder.newParameter(definitionData.serviceType, "receiverObject"));
        }
        if (needsExplicitIsolateParameter(methodData)) {
            params.add(CodeBuilder.newParameter(types.getPrimitiveType(TypeKind.LONG), REFERENCE_ISOLATE_ADDRESS_NAME));
        }
        int marshalledDataCount = 0;
        Map.Entry<MarshallerData, TypeMirror> firstOutMarshalledParameter = null;
        List<MarshalledParameter> outParameters = new ArrayList<>();
        for (int i = 0; i < methodParameters.size(); i++) {
            MarshallerData marshallerData = methodData.getParameterMarshaller(i);
            CharSequence parameterName = methodParameters.get(i).getSimpleName();
            TypeMirror parameterType = methodParameterTypes.get(i);
            if (isBinaryMarshallable(marshallerData, methodParameterTypes.get(i), false)) {
                marshalledDataCount++;
            } else {
                params.add(CodeBuilder.newParameter(parser.endPointMethodProvider.getEndPointMethodParameterType(marshallerData, parameterType), parameterName));
            }
            if (isOutParameter(marshallerData, parameterType, false)) {
                outParameters.add(new MarshalledParameter(parameterName, parameterType, false, marshallerData));
                if (firstOutMarshalledParameter == null && marshallerData.isCustom()) {
                    firstOutMarshalledParameter = new SimpleImmutableEntry<>(marshallerData, parameterType);
                }
            }
        }
        if (marshalledDataCount > 0) {
            params.add(CodeBuilder.newParameter(types.getArrayType(types.getPrimitiveType(TypeKind.BYTE)), MARSHALLED_DATA_PARAMETER));
        }
        MarshallerData returnTypeMarshaller = methodData.getReturnTypeMarshaller();
        CharSequence methodName = methodData.element.getSimpleName();
        TypeMirror receiverMethodReturnType = methodData.type.getReturnType();
        TypeMirror endMethodReturnType = firstOutMarshalledParameter != null
                        ? parser.endPointMethodProvider.getEndPointMethodParameterType(firstOutMarshalledParameter.getKey(), firstOutMarshalledParameter.getValue())
                        : parser.endPointMethodProvider.getEndPointMethodParameterType(returnTypeMarshaller, receiverMethodReturnType);
        builder.lineStart().annotation(typeCache.jNIEntryPoint, null).lineEnd("");
        builder.methodStart(EnumSet.of(Modifier.STATIC), methodName, endMethodReturnType, params, Collections.emptyList());
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
                actualParameters[i] = marshallerSnippets(marshallerData).unmarshallParameter(builder, parameterType, parameterName,
                                marshalledParametersInputVar, null);
            }
        }

        CharSequence resultSnippet = new CodeBuilder(builder).invoke(resolvedDispatch, methodData.receiverMethod != null ? methodData.receiverMethod : methodName, actualParameters).build();
        CharSequence resultVariable = null;
        if (receiverMethodReturnType.getKind() == TypeKind.VOID) {
            builder.lineStart().write(resultSnippet).lineEnd(";");
        } else {
            resultVariable = "endPointResult";
            builder.lineStart().write(receiverMethodReturnType).space().write(resultVariable).write(" = ").write(resultSnippet).lineEnd(";");
        }

        CharSequence marshalledOutputVar = null;
        boolean hasBinaryMarshalledResult = firstOutMarshalledParameter != null || methodData.getReturnTypeMarshaller().isCustom();
        if (hasBinaryMarshalledResult) {
            List<MarshalledParameter> marshallers = new ArrayList<>();
            if (resultVariable != null) {
                marshallers.add(new MarshalledParameter(resultVariable, receiverMethodReturnType, true, methodData.getReturnTypeMarshaller()));
            }
            marshallers.addAll(outParameters);
            marshalledOutputVar = "marshalledResultOutput";
            generateByteArrayBinaryOutputInit(builder, marshalledOutputVar, marshallers, marshalledDataCount > 0 ? MARSHALLED_DATA_PARAMETER : null);
        }

        // Do result marshalling for non-void methods
        if (firstOutMarshalledParameter != null) {
            resultSnippet = marshallerSnippets(firstOutMarshalledParameter.getKey()).marshallResult(builder, null, null, marshalledOutputVar, null);
            if (resultVariable != null) {
                marshallerSnippets(methodData.getReturnTypeMarshaller()).write(builder, receiverMethodReturnType, resultVariable, marshalledOutputVar, null);
            }
        } else if (resultVariable != null) {
            MarshallerSnippet returnTypeSnippets = marshallerSnippets(methodData.getReturnTypeMarshaller());
            CharSequence resultOverride = returnTypeSnippets.preMarshallResult(builder, receiverMethodReturnType, resultVariable, marshalledOutputVar, null);
            if (resultOverride != null) {
                resultVariable = resultOverride;
            }
            resultSnippet = returnTypeSnippets.marshallResult(builder, receiverMethodReturnType, resultVariable, marshalledOutputVar, null);
        }

        if (!outParameters.isEmpty() && marshalledOutputVar == null) {
            marshalledOutputVar = "marshalledParametersOutput";
            builder.lineStart().write(typeCache.byteArrayBinaryOutput).space().write(marshalledOutputVar).write(" = ").invokeStatic(typeCache.binaryOutput, "create",
                            MARSHALLED_DATA_PARAMETER).lineEnd(";");
        }

        // Generate post unmarshall statements.
        for (int i = nonReceiverParameterStart; i < methodParameters.size(); i++) {
            MarshallerSnippet marshallerSnippets = marshallerSnippets(methodData.getParameterMarshaller(i));
            marshallerSnippets.postUnmarshallParameter(builder, methodParameterTypes.get(i), methodParameters.get(i).getSimpleName(), marshalledOutputVar, null, resultVariable);
        }

        // Generate return value
        if (endMethodReturnType.getKind() != TypeKind.VOID) {
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

    static boolean needsExplicitIsolateParameter(MethodData methodData) {
        for (int i = 0; i < methodData.element.getParameters().size(); i++) {
            MarshallerData marshallerData = methodData.getParameterMarshaller(i);
            if (marshallerData.kind == MarshallerData.Kind.REFERENCE && !marshallerData.sameDirection) {
                return true;
            }
        }
        return false;
    }

    private record BinaryMarshallVariables(CharSequence jniBufferVariable, CharSequence cBufferVariable, CharSequence sizeVariable, CharSequence outputVariable) {
    }

    private abstract static class NativeToHotSpotMarshallerSnippet extends MarshallerSnippet {

        final TypeCache cache;

        NativeToHotSpotMarshallerSnippet(NativeToHotSpotBridgeGenerator generator, MarshallerData marshallerData) {
            super(generator, marshallerData);
            this.cache = generator.typeCache;
        }

        static NativeToHotSpotMarshallerSnippet forData(NativeToHotSpotBridgeGenerator generator, MarshallerData marshallerData) {
            return switch (marshallerData.kind) {
                case VALUE -> new DirectSnippet(generator, marshallerData);
                case REFERENCE ->
                    new ReferenceSnippet(generator, marshallerData);
                case RAW_REFERENCE ->
                    new RawReferenceSnippet(generator, marshallerData);
                case CUSTOM -> new CustomSnippet(generator, marshallerData);
            };
        }

        private static final class DirectSnippet extends NativeToHotSpotMarshallerSnippet {

            DirectSnippet(NativeToHotSpotBridgeGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
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
                    CodeBuilder castBuilder = new CodeBuilder(currentBuilder).cast(Utilities.jniTypeForJavaType(resultType, types, cache), invocationSnippet);
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
            void declarePerMarshalledParameterVariable(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, Map<String, CharSequence> parameterValueOverrides) {
                boolean hasDirectionModifiers = isArrayWithDirectionModifiers(parameterType);
                if (hasDirectionModifiers) {
                    TypeMirror jniType = Utilities.jniTypeForJavaType(parameterType, types, cache);
                    CharSequence outLocal = outArrayLocal(parameterName);
                    currentBuilder.lineStart().write(jniType).space().write(outLocal).lineEnd(";");
                    parameterValueOverrides.put(parameterName.toString(), outLocal);
                }
            }

            @Override
            boolean preMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverride) {
                boolean hasDirectionModifiers = isArrayWithDirectionModifiers(parameterType);
                if (hasDirectionModifiers) {
                    TypeMirror jniType = Utilities.jniTypeForJavaType(parameterType, types, cache);
                    currentBuilder.lineStart().write(outArrayLocal(parameterName)).write(" = ");
                    if (marshallerData.in != null) {
                        CharSequence arrayLengthParameter = marshallerData.in.lengthParameter;
                        if (arrayLengthParameter != null) {
                            currentBuilder.invokeStatic(cache.jniUtil, arrayFactoryMethodName(jniType), jniEnvFieldName, arrayLengthParameter).lineEnd(";");
                            CharSequence arrayOffsetSnippet;
                            CharSequence arrayOffsetParameter = marshallerData.in.offsetParameter;
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
                        CharSequence arrayLengthParameter = marshallerData.out.lengthParameter;
                        if (arrayLengthParameter != null) {
                            arrayLengthSnippet = arrayLengthParameter;
                            CharSequence arrayOffsetParameter = marshallerData.out.offsetParameter;
                            if (arrayOffsetParameter != null) {
                                parameterValueOverride.put(arrayOffsetParameter.toString(), "0");
                            }
                        } else {
                            arrayLengthSnippet = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                        }
                        currentBuilder.invokeStatic(cache.jniUtil, arrayFactoryMethodName(jniType), jniEnvFieldName, arrayLengthSnippet).lineEnd(";");
                    }
                }
                return hasDirectionModifiers;
            }

            @Override
            void postMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence receiver, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName, CharSequence resultVariableName) {
                if (isArrayWithDirectionModifiers(parameterType)) {
                    if (marshallerData.out != null) {
                        CharSequence arrayLengthSnippet;
                        boolean trimToResult = marshallerData.out.trimToResult;
                        CharSequence arrayLengthParameter = marshallerData.out.lengthParameter;
                        if (trimToResult) {
                            arrayLengthSnippet = resultVariableName;
                        } else if (arrayLengthParameter != null) {
                            arrayLengthSnippet = arrayLengthParameter;
                        } else {
                            arrayLengthSnippet = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                        }
                        CharSequence arrayOffsetSnippet;
                        CharSequence arrayOffsetParameter = marshallerData.out.offsetParameter;
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

            @Override
            void write(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                CharSequence[] args;
                if (resultType.getKind() == TypeKind.ARRAY) {
                    CharSequence len = new CodeBuilder(currentBuilder).memberSelect(invocationSnippet, "length", false).build();
                    currentBuilder.lineStart().invoke(marshalledResultOutput, "writeInt", len).lineEnd(";");
                    args = new CharSequence[]{invocationSnippet, "0", len};
                } else {
                    args = new CharSequence[]{invocationSnippet};
                }
                currentBuilder.lineStart().invoke(marshalledResultOutput, lookupDirectSnippetWriteMethod(resultType), args).lineEnd(";");
            }

            @Override
            void read(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence resultVariable, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                currentBuilder.lineStart().write(resultType).space().write(resultVariable).write(" = ");
                CharSequence[] args;
                if (resultType.getKind() == TypeKind.ARRAY) {
                    CharSequence len = new CodeBuilder(currentBuilder).invoke(marshalledResultInput, "readInt").build();
                    currentBuilder.newArray(((ArrayType) resultType).getComponentType(), len).lineEnd(";");
                    currentBuilder.lineStart();
                    args = new CharSequence[]{resultVariable, "0", new CodeBuilder(currentBuilder).memberSelect(resultVariable, "length", false).build()};
                } else {
                    args = new CharSequence[0];
                }
                currentBuilder.invoke(marshalledResultInput, lookupDirectSnippetReadMethod(resultType), args).lineEnd(";");
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

        private static final class ReferenceSnippet extends NativeToHotSpotMarshallerSnippet {

            private final DefinitionData data;
            private final AbstractEndPointMethodProvider endPointMethodProvider;

            ReferenceSnippet(NativeToHotSpotBridgeGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
                this.data = generator.definitionData;
                this.endPointMethodProvider = generator.parser.endPointMethodProvider;
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
            void declarePerMarshalledParameterVariable(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, Map<String, CharSequence> parameterValueOverrides) {
                if (parameterType.getKind() == TypeKind.ARRAY && marshallerData.sameDirection) {
                    TypeMirror hsParameterType = Utilities.jniTypeForJavaType(endPointMethodProvider.getEndPointMethodParameterType(marshallerData, parameterType), types, cache);
                    CharSequence targetArrayVariable = Utilities.javaMemberName("hs", parameterName);
                    currentBuilder.lineStart().write(hsParameterType).space().write(targetArrayVariable).lineEnd(";");
                }
            }

            @Override
            boolean preMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverrides) {
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                    CharSequence arrayLength = null;
                    CharSequence arrayOffsetParameter = null;
                    if (marshallerData.in != null) {
                        arrayLength = marshallerData.in.lengthParameter;
                        arrayOffsetParameter = marshallerData.in.offsetParameter;
                    } else if (marshallerData.out != null) {
                        arrayLength = marshallerData.out.lengthParameter;
                        arrayOffsetParameter = marshallerData.out.offsetParameter;
                    }
                    if (arrayLength == null) {
                        arrayLength = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                    }
                    if (arrayOffsetParameter != null) {
                        parameterValueOverrides.put(arrayOffsetParameter.toString(), "0");
                    }
                    boolean needsCopy = marshallerData.out == null || marshallerData.in != null;
                    if (marshallerData.sameDirection) {
                        CharSequence targetArrayVariable = Utilities.javaMemberName("hs", parameterName);
                        if (needsCopy) {
                            CharSequence copySnippet = marshallCopyHSObjectArrayToHotSpot(currentBuilder, componentType, parameterName, arrayOffsetParameter, arrayLength, jniEnvFieldName);
                            currentBuilder.lineStart().write(targetArrayVariable).write(" = ").write(copySnippet).lineEnd(";");
                        } else {
                            generateAllocateJObjectArray(currentBuilder, componentType, parameterName, targetArrayVariable, arrayLength, jniEnvFieldName);
                        }
                    } else {
                        currentBuilder.lineStart("if (").write(parameterName).write(" != null) ").lineEnd("{");
                        currentBuilder.indent();
                        generateWriteNativeObjectArray(currentBuilder, parameterName, needsCopy, true, arrayOffsetParameter, arrayLength, marshalledParametersOutput,
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
                    if (marshallerData.out != null) {
                        // Out parameter. We need to copy the content from buffer to array.
                        currentBuilder.lineStart("if (").write(parameterName).write(" != null) ").lineEnd("{");
                        currentBuilder.indent();
                        CharSequence sourceArrayVariable = Utilities.javaMemberName("hs", parameterName);
                        TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                        boolean trimToResult = marshallerData.out.trimToResult;
                        CharSequence arrayLength;
                        if (trimToResult) {
                            arrayLength = resultVariableName;
                        } else {
                            arrayLength = marshallerData.out.lengthParameter;
                        }
                        if (arrayLength == null) {
                            arrayLength = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                        }
                        CharSequence arrayOffsetParameter = marshallerData.out.offsetParameter;
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
                    currentBuilder.lineStart().write(Utilities.jniTypeForJavaType(resultType, types, cache)).space().write(resultVariable).write(" = ").write(invocationSnippet).lineEnd(";");
                    return resultVariable;
                } else if (resultType.getKind() == TypeKind.ARRAY) {
                    CharSequence jLongArray = new CodeBuilder(currentBuilder).cast(cache.jLongArray, invocationSnippet).build();
                    currentBuilder.lineStart().write(endPointMethodProvider.getEndPointMethodParameterType(marshallerData, resultType)).space().write(resultVariable).write(" = ").invokeStatic(
                                    cache.jniUtil, "createArray", jniEnvFieldName,
                                    jLongArray).lineEnd(";");
                    return resultVariable;
                } else {
                    return null;
                }
            }

            @Override
            CharSequence preUnmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence marshalledResultInput,
                            CharSequence jniEnvFieldName) {
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
                    currentBuilder.lineStart().write(endPointMethodProvider.getEndPointMethodParameterType(marshallerData, resultType)).space().write(hsResult).lineEnd(";");
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
                    currentBuilder.lineStart().write(parameterType).space().write(parameterName).lineEnd(";");
                    CharSequence arrayLengthVariable = parameterName + "Length";
                    currentBuilder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(arrayLengthVariable).write(" = ").invoke(marshalledParametersInput, "readInt").lineEnd(";");
                    currentBuilder.lineStart("if(").write(arrayLengthVariable).write(" != -1) ").lineEnd("{");
                    currentBuilder.indent();
                    currentBuilder.lineStart(parameterName).write(" = ").newArray(componentType, arrayLengthVariable).lineEnd(";");
                    if (marshallerData.out == null || marshallerData.in != null) {
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
                    if (marshallerData.out != null) {
                        CharSequence arrayLengthSnippet;
                        CharSequence arrayLengthParameter = marshallerData.out.lengthParameter;
                        if (marshallerData.out.trimToResult) {
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

            @Override
            void write(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                throw new UnsupportedOperationException("Marshalling to BinaryOutput is not supported for references.");
            }

            @Override
            void read(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence resultVariable, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                throw new UnsupportedOperationException("Marshalling to BinaryOutput is not supported for references.");
            }
        }

        private static final class RawReferenceSnippet extends NativeToHotSpotMarshallerSnippet {

            RawReferenceSnippet(NativeToHotSpotBridgeGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
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

            @Override
            void write(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                throw new UnsupportedOperationException("Marshalling to BinaryOutput is not supported for raw references.");
            }

            @Override
            void read(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence resultVariable, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                throw new UnsupportedOperationException("Marshalling to BinaryOutput is not supported for raw references.");
            }
        }

        private static final class CustomSnippet extends NativeToHotSpotMarshallerSnippet {

            CustomSnippet(NativeToHotSpotBridgeGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
            }

            @Override
            boolean preMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverrides) {
                writeCustomObject(currentBuilder, parameterType, parameterName, marshalledParametersOutput);
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
            CharSequence preUnmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence marshalledResultInput,
                            CharSequence jniEnvFieldName) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    CharSequence resultVariable = "hsResult";
                    readCustomObject(currentBuilder, resultType, resultVariable, marshalledResultInput);
                    return resultVariable;
                } else {
                    return null;
                }
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence marshalledResultInput,
                            CharSequence jniEnvFieldName) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    return "hsResult";
                } else {
                    return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "read", marshalledResultInput).build();
                }
            }

            @Override
            CharSequence preMarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                writeCustomObject(currentBuilder, resultType, invocationSnippet, marshalledResultOutput);
                return null;
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                return new CodeBuilder(currentBuilder).invoke(marshalledResultOutput, "getArray").build();
            }

            @Override
            boolean preUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput, CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverride) {
                readCustomObject(currentBuilder, parameterType, parameterName, marshalledParametersInput);
                return true;
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput, CharSequence jniEnvFieldName) {
                return parameterName;

            }

            @Override
            void postUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName,
                            CharSequence resultVariableName) {
                if (marshallerData.out != null) {
                    writeCustomObjectUpdate(currentBuilder, parameterType, parameterName, marshalledResultOutput);
                }
            }

            @Override
            void postMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence receiver, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName, CharSequence resultVariableName) {
                if (marshallerData.out != null) {
                    readCustomObjectUpdate(currentBuilder, parameterType, parameterName, marshalledParametersInput, false);
                }
            }

            @Override
            void write(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                writeCustomObject(currentBuilder, resultType, invocationSnippet, marshalledResultOutput);
            }

            @Override
            void read(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence resultVariable, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                readCustomObject(currentBuilder, resultType, resultVariable, marshalledResultInput);
            }
        }
    }
}
