/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MarshallerData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.AbstractEndPointMethodProvider;
import org.graalvm.nativebridge.processor.AbstractServiceParser.CacheData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.ServiceDefinitionData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.MethodData;
import org.graalvm.nativebridge.processor.HotSpotToNativeServiceParser.HotSpotToNativeServiceDefinitionData;

final class HotSpotToNativeServiceGenerator extends AbstractNativeServiceGenerator {

    static final String START_POINT_SIMPLE_NAME = "HSToNativeStartPoint";
    static final String END_POINT_SIMPLE_NAME = "HSToNativeEndPoint";
    private static final String HOTSPOT_RESULT_VARIABLE = "hsResult";

    private final HotSpotToNativeTypeCache typeCache;
    private final FactoryMethodInfo factoryMethod;
    private boolean hasOtherHotSpotToNative;

    HotSpotToNativeServiceGenerator(AbstractServiceParser parser, HotSpotToNativeTypeCache typeCache, HotSpotToNativeServiceDefinitionData definitionData) {
        this(parser, typeCache, definitionData, START_POINT_SIMPLE_NAME, END_POINT_SIMPLE_NAME);
    }

    HotSpotToNativeServiceGenerator(AbstractServiceParser parser, HotSpotToNativeTypeCache typeCache, HotSpotToNativeServiceDefinitionData definitionData,
                    String startPointName, String endPointName) {
        super(parser, typeCache, definitionData, BinaryNameCache.create(definitionData, true, parser.types, parser.elements, typeCache));
        this.typeCache = typeCache;
        this.factoryMethod = resolveFactoryMethod(FACTORY_METHOD_NAME, startPointName, endPointName);
        this.hasOtherHotSpotToNative = false;
    }

    @Override
    HotSpotToNativeServiceDefinitionData getDefinition() {
        return (HotSpotToNativeServiceDefinitionData) super.getDefinition();
    }

    @Override
    void configureMultipleDefinitions(List<DefinitionData> otherDefinitions) {
        hasOtherHotSpotToNative = otherDefinitions.stream().anyMatch((d) -> d instanceof HotSpotToNativeServiceDefinitionData);
    }

    @Override
    boolean supportsCommonFactory() {
        return !hasOtherHotSpotToNative;
    }

    @Override
    void generateCommonCustomDispatchFactoryReturn(CodeBuilder builder) {
        builder.lineStart("return ").newInstance(factoryMethod.startPointSimpleName, parameterNames(factoryMethod.constructorParameters)).lineEnd(";");
    }

    @Override
    void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName) {
        /*
         * For classes with custom dispatch, the factory method is shared with all other isolate
         * based implementations and generated in `NativeBridgeProcessor`. For classes generated
         * with both HotSpotToNative and NativeToNative, the factory methods are created by
         * NativeToNativeBridgeGenerator.
         */
        if (!getDefinition().hasCustomDispatch() && !hasOtherHotSpotToNative) {
            builder.lineEnd("");
            generateStartPointFactory(builder, factoryMethod);
        }
    }

    @Override
    void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        generateHSToNativeStartPoint(builder, factoryMethod);
        builder.lineEnd("");
        generateHSToNativeEndPoint(builder, targetClassSimpleName);
    }

    @Override
    HotSpotToNativeMarshallerSnippet marshallerSnippets(MarshallerData marshallerData) {
        return HotSpotToNativeMarshallerSnippet.forData(this, marshallerData);
    }

    private void generateHSToNativeStartPoint(CodeBuilder builder, FactoryMethodInfo factoryMethodInfo) {
        CacheSnippet cacheSnippets = cacheSnippets();
        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), factoryMethod.startPointSimpleName,
                        getDefinition().annotatedType, Collections.emptyList());
        builder.indent();
        builder.lineEnd("");

        generateMarshallerFields(builder, true, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC);
        builder.line("");

        if (!getDefinition().getAllCustomMarshallers().isEmpty()) {
            builder.methodStart(EnumSet.of(Modifier.STATIC), null,
                            null, Collections.emptyList(), Collections.emptyList());
            builder.indent();
            builder.lineStart().write(typeCache.marshallerConfig).write(" marshallerConfig = ").invokeStatic(getDefinition().marshallerConfig, "getInstance").lineEnd(";");
            generateMarshallerLookups(builder, true);
            builder.dedent();
            builder.line("}");
            builder.line("");
        }

        if (!getDefinition().hasCustomDispatch()) {
            generatePeerField(builder, getDefinition().peerType, PEER_FIELD);
            builder.line("");
        }

        if (generateCacheFields(builder, cacheSnippets)) {
            builder.lineEnd("");
        }

        builder.methodStart(Collections.emptySet(), factoryMethod.startPointSimpleName, null,
                        factoryMethodInfo.constructorParameters, Collections.emptyList());
        builder.indent();
        builder.lineStart().call("super", parameterNames(factoryMethodInfo.superCallParameters)).lineEnd(";");
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
            generateHSToNativeStartMethod(builder, cacheSnippets, methodData);
        }
        for (MethodData methodData : getDefinition().toGenerate) {
            generateHSToNativeNativeMethod(builder, methodData);
        }

        builder.dedent();
        builder.classEnd();
    }

    private void generateHSToNativeStartMethod(CodeBuilder builder, CacheSnippet cacheSnippets, MethodData methodData) {
        builder.line("");
        overrideMethod(builder, methodData);
        builder.indent();
        CodeBuilder receiverCastStatement;
        CharSequence receiver;
        CharSequence receiverNativePeer;
        int nonReceiverParameterStart;
        if (getDefinition().hasCustomDispatch()) {
            receiver = methodData.element.getParameters().get(0).getSimpleName();
            CharSequence foreignObject = new CodeBuilder(builder).cast(typeCache.foreignObject, receiver, true).build();
            CharSequence getPeer = new CodeBuilder(builder).invoke(foreignObject, "getPeer").build();
            receiverCastStatement = new CodeBuilder(builder).write(typeCache.nativePeer).write(" nativePeer = (").write(typeCache.nativePeer).write(") ").write(getPeer).write(";");
            receiverNativePeer = "nativePeer";
            nonReceiverParameterStart = 1;
        } else {
            receiver = PEER_FIELD;
            receiverCastStatement = null;
            receiverNativePeer = receiver;
            nonReceiverParameterStart = 0;
        }
        CharSequence nativeIsolateVar = "nativeIsolate";
        CharSequence scopeVarName = "nativeIsolateThread";
        CodeBuilder getNativeIsolate = new CodeBuilder(builder).write(typeCache.nativeIsolate).space().write(nativeIsolateVar).write(" = ").invoke(receiverNativePeer, "getIsolate").write(";");
        CodeBuilder enterScope = new CodeBuilder(builder).write(typeCache.nativeIsolateThread).space().write(scopeVarName).write(" = ").invoke(nativeIsolateVar, "enter").write(";");
        List<CharSequence> syntheticPrependParameters = new ArrayList<>();
        List<CharSequence> syntheticAppendParameters = new ArrayList<>();
        syntheticPrependParameters.add(new CodeBuilder(builder).invoke(scopeVarName, "getIsolateThreadId").build());
        syntheticPrependParameters.add(new CodeBuilder(builder).invoke(receiverNativePeer, "getHandle").build());
        List<? extends VariableElement> formalParameters = methodData.element.getParameters();
        List<? extends TypeMirror> formalParameterTypes = methodData.type.getParameterTypes();
        CharSequence marshalledParametersOutput = null;
        List<MarshalledParameter> binaryMarshalledParameters = new ArrayList<>();
        List<MarshalledParameter> allParameters = new ArrayList<>();
        boolean hasOutParameters = false;
        MarshallerData firstOutMarshalledParameter = null;
        for (int i = nonReceiverParameterStart; i < formalParameters.size(); i++) {
            MarshallerData marshaller = methodData.getParameterMarshaller(i);
            TypeMirror parameterType = formalParameterTypes.get(i);
            CharSequence parameterName = formalParameters.get(i).getSimpleName();
            MarshalledParameter marshalledParameter = new MarshalledParameter(parameterName, parameterType, false, marshaller);
            if (isBinaryMarshallable(marshaller, parameterType, true)) {
                marshalledParametersOutput = "marshalledParametersOutput";
                binaryMarshalledParameters.add(marshalledParameter);
            }
            allParameters.add(marshalledParameter);
            if (isOutParameter(marshaller, parameterType, true)) {
                hasOutParameters = true;
                if (firstOutMarshalledParameter == null && marshaller.isCustom()) {
                    firstOutMarshalledParameter = marshaller;
                }
            }
        }
        if (marshalledParametersOutput != null) {
            syntheticAppendParameters.add(new CodeBuilder(builder).invoke(marshalledParametersOutput, "getArray").build());
        }
        String nativeMethodName = getParser().endPointMethodProvider.getEntryPointMethodName(methodData);
        CacheData cacheData = methodData.cachedData;
        TypeMirror returnType = methodData.type.getReturnType();
        boolean hasBinaryMarshalledResult = methodData.getReturnTypeMarshaller().isCustom();
        Map<String, CharSequence> overrides = new HashMap<>();
        if (cacheData != null) {
            if (hasOutParameters) {
                throw new IllegalStateException("Idempotent cannot be used with Out parameters.");
            }
            String cacheFieldName = cacheData.cacheFieldName;
            String resFieldName = cacheFieldName + "Result";
            builder.lineStart().write(cacheData.cacheEntryType).space().write(resFieldName).write(" = ").write(cacheSnippets.readCache(builder, cacheFieldName, receiver)).lineEnd(";");
            builder.lineStart("if (").write(resFieldName).lineEnd(" == null) {");
            builder.indent();
            if (receiverCastStatement != null) {
                builder.line(receiverCastStatement.build());
            }
            builder.line(getNativeIsolate.build());
            builder.line(enterScope.build());
            builder.line("try {");
            builder.indent();
            generateByteArrayBinaryOutputInit(builder, methodData, marshalledParametersOutput, binaryMarshalledParameters, firstOutMarshalledParameter != null);
            boolean needsPostMarshallParameters = generatePreMarshallParameters(builder, allParameters, marshalledParametersOutput, overrides);
            CharSequence nativeCall = generateMarshallParameters(builder, nativeMethodName, allParameters, marshalledParametersOutput, syntheticPrependParameters, syntheticAppendParameters,
                            overrides);
            MarshallerSnippet marshallerSnippets = marshallerSnippets(methodData.getReturnTypeMarshaller());
            PreUnmarshallResult preUnmarshallResult = generatePreUnmarshallResult(builder, marshallerSnippets, returnType, nativeCall, nativeIsolateVar, hasBinaryMarshalledResult);
            CharSequence value = marshallerSnippets.unmarshallResult(builder, returnType, preUnmarshallResult.result, nativeIsolateVar, preUnmarshallResult.binaryInput, null);
            builder.lineStart().write(resFieldName).write(" = ").write(value).lineEnd(";");
            builder.lineStart().write(cacheSnippets.writeCache(builder, cacheFieldName, receiver, resFieldName)).lineEnd(";");
            if (needsPostMarshallParameters) {
                generatePostMarshallParameters(builder, binaryMarshalledParameters, nativeIsolateVar, marshalledParametersOutput, preUnmarshallResult.binaryInput, resFieldName, hasOutParameters);
            }
            builder.dedent();
            generateHSToNativeStartPointExceptionHandlers(builder, nativeIsolateVar, scopeVarName);
            builder.dedent();
            builder.line("}");
            builder.lineStart("return ").write(resFieldName).lineEnd(";");
        } else {
            if (receiverCastStatement != null) {
                builder.line(receiverCastStatement.build());
            }
            builder.line(getNativeIsolate.build());
            builder.line(enterScope.build());
            builder.line("try {");
            builder.indent();
            generateByteArrayBinaryOutputInit(builder, methodData, marshalledParametersOutput, binaryMarshalledParameters, firstOutMarshalledParameter != null);
            boolean needsPostMarshallParameters = generatePreMarshallParameters(builder, allParameters, marshalledParametersOutput, overrides);
            CharSequence nativeCall = generateMarshallParameters(builder, nativeMethodName, allParameters, marshalledParametersOutput, syntheticPrependParameters, syntheticAppendParameters,
                            overrides);
            MarshallerSnippet resultMarshallerSnippets = marshallerSnippets(methodData.getReturnTypeMarshaller());
            boolean voidReturnType = returnType.getKind() == TypeKind.VOID;
            CharSequence resultVariable = null;
            PreUnmarshallResult preUnmarshallResult;
            if (firstOutMarshalledParameter != null) {
                MarshallerSnippet customMarshalledSnippets = marshallerSnippets(firstOutMarshalledParameter);
                CharSequence resultInput = generateBinaryInputForResult(builder, customMarshalledSnippets, returnType, nativeCall);
                preUnmarshallResult = new PreUnmarshallResult(HOTSPOT_RESULT_VARIABLE, null, resultInput, false);
                if (!voidReturnType) {
                    resultVariable = HOTSPOT_RESULT_VARIABLE;
                    resultMarshallerSnippets.read(builder, returnType, resultVariable, nativeIsolateVar, preUnmarshallResult.binaryInput, null);
                }
                needsPostMarshallParameters = true;
            } else {
                preUnmarshallResult = generatePreUnmarshallResult(builder, resultMarshallerSnippets, returnType, nativeCall, nativeIsolateVar, hasBinaryMarshalledResult);
                builder.lineStart();
                if (!voidReturnType) {
                    if (needsPostMarshallParameters) {
                        if (preUnmarshallResult.unmarshalled) {
                            resultVariable = preUnmarshallResult.result;
                        } else {
                            resultVariable = HOTSPOT_RESULT_VARIABLE;
                            builder.write(returnType).space().write(resultVariable).write(" = ");
                        }
                    } else {
                        builder.write("return ");
                    }
                }
                CharSequence value = resultMarshallerSnippets.unmarshallResult(builder, returnType, preUnmarshallResult.result, nativeIsolateVar, preUnmarshallResult.binaryInput, null);
                builder.write(value);
                builder.lineEnd(";");
            }
            if (needsPostMarshallParameters) {
                generatePostMarshallParameters(builder, binaryMarshalledParameters, nativeIsolateVar, marshalledParametersOutput, preUnmarshallResult.binaryInput, resultVariable,
                                hasOutParameters);
                if (!voidReturnType) {
                    builder.lineStart().write("return ").write(resultVariable).lineEnd(";");
                }
            }
            builder.dedent();
            generateHSToNativeStartPointExceptionHandlers(builder, nativeIsolateVar, scopeVarName);
        }
        builder.dedent();
        builder.line("}");
    }

    private void generateHSToNativeStartPointExceptionHandlers(CodeBuilder builder, CharSequence nativeIsolateVar, CharSequence scopeVarName) {
        CharSequence foreignException = "foreignException";
        CharSequence throwUnboxedException = new CodeBuilder(builder).write("throw").space().invoke(foreignException, "throwOriginalException",
                        nativeIsolateVar,
                        getDefinition().getCustomMarshaller(typeCache.throwable, null, types).name).write(";").build();
        builder.lineStart("} catch (").write(typeCache.foreignException).space().write(foreignException).write(") ").lineEnd("{");
        builder.indent();
        builder.line(throwUnboxedException);
        builder.dedent();
        builder.line("} finally {");
        builder.indent();
        builder.lineStart().invoke(scopeVarName, "leave").lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    private boolean generatePreMarshallParameters(CodeBuilder builder, List<MarshalledParameter> marshalledParameters, CharSequence marshalledParametersOutput,
                    Map<String, CharSequence> parameterValueOverrides) {
        boolean needsPostMarshall = false;
        for (MarshalledParameter marshalledParameter : marshalledParameters) {
            MarshallerSnippet snippets = marshallerSnippets(marshalledParameter.marshallerData);
            needsPostMarshall |= snippets.preMarshallParameter(builder, marshalledParameter.parameterType, marshalledParameter.parameterName, marshalledParametersOutput, null,
                            parameterValueOverrides);
        }
        return needsPostMarshall;
    }

    private CharSequence generateMarshallParameters(CodeBuilder builder, CharSequence nativeMethodName, List<MarshalledParameter> marshalledParameters, CharSequence marshalledParametersOutput,
                    List<CharSequence> syntheticPrependParameters, List<CharSequence> syntheticAppendParameters, Map<String, CharSequence> parameterValueOverrides) {
        List<CharSequence> actualParameters = new ArrayList<>(syntheticPrependParameters);
        for (MarshalledParameter marshalledParameter : marshalledParameters) {
            CharSequence override = parameterValueOverrides.get(marshalledParameter.parameterName.toString());
            CharSequence useValue = override != null ? override : marshalledParameter.parameterName;
            MarshallerSnippet snippets = marshallerSnippets(marshalledParameter.marshallerData);
            CharSequence expr = snippets.marshallParameter(builder, marshalledParameter.parameterType, useValue, marshalledParametersOutput, null);
            if (expr != null) {
                actualParameters.add(expr);
            }
        }
        actualParameters.addAll(syntheticAppendParameters);
        return new CodeBuilder(builder).call(nativeMethodName, actualParameters.toArray(new CharSequence[0])).build();
    }

    private void generatePostMarshallParameters(CodeBuilder builder, List<MarshalledParameter> marshalledParameters, CharSequence nativeIsolateVar, CharSequence marshalledParametersOutput,
                    CharSequence marshalledResultInput, CharSequence resultsVariableName, boolean hasOutParameters) {
        CharSequence useInput = marshalledResultInput;
        if (hasOutParameters && useInput == null) {
            CharSequence blob = new CodeBuilder(builder).invoke(marshalledParametersOutput, "getArray").build();
            useInput = "marshalledParametersInput";
            builder.lineStart().write(typeCache.binaryInput).space().write(useInput).write(" = ").invokeStatic(typeCache.binaryInput, "create", blob).lineEnd(";");
        }
        for (MarshalledParameter marshalledParameter : marshalledParameters) {
            MarshallerSnippet snippets = marshallerSnippets(marshalledParameter.marshallerData);
            snippets.postMarshallParameter(builder, marshalledParameter.parameterType, marshalledParameter.parameterName, nativeIsolateVar, useInput, null, resultsVariableName);
        }
    }

    private PreUnmarshallResult generatePreUnmarshallResult(CodeBuilder builder, MarshallerSnippet snippets, TypeMirror returnType, CharSequence nativeCall,
                    CharSequence nativeIsolateVar, boolean hasBinaryMarshalledResult) {
        CharSequence result = generateStoreResult(builder, snippets, returnType, nativeCall);
        CharSequence marshalledResultInput = hasBinaryMarshalledResult ? generateBinaryInputInitForByteArray(builder, result) : null;
        CharSequence resultVariable = snippets.preUnmarshallResult(builder, returnType, result, nativeIsolateVar, marshalledResultInput, null);
        result = resultVariable != null ? resultVariable : result;
        return new PreUnmarshallResult(result, null, marshalledResultInput, resultVariable != null);
    }

    private CharSequence generateBinaryInputForResult(CodeBuilder builder, MarshallerSnippet snippets, TypeMirror returnType, CharSequence nativeCall) {
        CharSequence result = generateStoreResult(builder, snippets, returnType, nativeCall);
        return generateBinaryInputInitForByteArray(builder, result);
    }

    private static CharSequence generateStoreResult(CodeBuilder builder, MarshallerSnippet snippets, TypeMirror returnType, CharSequence nativeCall) {
        CharSequence endPointResultVariable = snippets.storeRawResult(builder, returnType, nativeCall, null);
        return endPointResultVariable != null ? endPointResultVariable : nativeCall;
    }

    private CharSequence generateBinaryInputInitForByteArray(CodeBuilder builder, CharSequence byteArray) {
        CharSequence marshalledResultInput = "marshalledResultInput";
        builder.lineStart().write(typeCache.binaryInput).space().write(marshalledResultInput).write(" = ").invokeStatic(typeCache.binaryInput, "create", byteArray).lineEnd(";");
        return marshalledResultInput;
    }

    private void generateByteArrayBinaryOutputInit(CodeBuilder builder, MethodData methodData, CharSequence marshalledParametersOutputVar, List<MarshalledParameter> marshalledParameters,
                    boolean reserveSpaceForResult) {
        if (marshalledParametersOutputVar != null) {
            CharSequence sizeVar = "marshalledParametersSizeEstimate";
            List<MarshalledParameter> usedParameters;
            if (reserveSpaceForResult && methodData.type.getReturnType().getKind() != TypeKind.VOID) {
                usedParameters = new ArrayList<>(1 + marshalledParameters.size());
                usedParameters.add(new MarshalledParameter(methodData.type.getReturnType(), methodData.getReturnTypeMarshaller()));
                usedParameters.addAll(marshalledParameters);
            } else {
                usedParameters = marshalledParameters;
            }
            generateSizeEstimate(builder, sizeVar, usedParameters, true, false);
            builder.lineStart().write(typeCache.byteArrayBinaryOutput).space().write(marshalledParametersOutputVar).write(" = ").invokeStatic(typeCache.byteArrayBinaryOutput, "create",
                            sizeVar).lineEnd(";");
        }
    }

    private CharSequence generateCCharPointerBinaryOutputInit(CodeBuilder builder, CharSequence marshalledResultOutputVar, boolean hasMarshalledArgs,
                    List<MarshalledParameter> marshalledParameters,
                    CharSequence marshallBufferVar, CharSequence staticMarshallBufferVar,
                    CharSequence marshalledDataLengthVar, CharSequence staticBufferSize) {
        CharSequence sizeEstimateVar = "marshalledResultSizeEstimate";
        generateSizeEstimate(builder, sizeEstimateVar, marshalledParameters, false, true);
        CharSequence marshallBufferLengthVar = "marshallBufferLength";
        if (hasMarshalledArgs) {
            builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(marshallBufferLengthVar).write(" = ").invokeStatic(typeCache.math, "max", staticBufferSize,
                            marshalledDataLengthVar).lineEnd(";");
        }
        CharSequence preAllocatedSizeVar = hasMarshalledArgs ? marshallBufferLengthVar : staticBufferSize;
        CharSequence[] preAllocatedArgs = {
                        hasMarshalledArgs ? marshallBufferVar : staticMarshallBufferVar,
                        preAllocatedSizeVar,
                        "false"
        };
        CodeBuilder code = new CodeBuilder(builder).write(typeCache.cCharPointerBinaryOutput).space().write(marshalledResultOutputVar).write(" = ").write(sizeEstimateVar).write(" > ").write(
                        preAllocatedSizeVar).write(" ? ").invokeStatic(typeCache.cCharPointerBinaryOutput, "create", sizeEstimateVar).write(" : ").invokeStatic(typeCache.binaryOutput, "create",
                                        preAllocatedArgs);
        return code.build();
    }

    private CharSequence generateCCharPointerBinaryOutputInitForMarshalledData(CodeBuilder builder, CharSequence marshalledResultOutputVar, CharSequence marshallBufferVar,
                    CharSequence marshalledDataLengthVar) {
        CharSequence[] preAllocatedArgs = {
                        marshallBufferVar,
                        marshalledDataLengthVar,
                        "false"
        };
        CodeBuilder code = new CodeBuilder(builder).write(typeCache.cCharPointerBinaryOutput).space().write(marshalledResultOutputVar).write(" = ").invokeStatic(typeCache.binaryOutput, "create",
                        preAllocatedArgs);
        return code.build();
    }

    private void generateHSToNativeNativeMethod(CodeBuilder builder, MethodData methodData) {
        builder.line("");
        String nativeMethodName = getParser().endPointMethodProvider.getEntryPointMethodName(methodData);
        List<CodeBuilder.Parameter> nativeMethodParameters = new ArrayList<>();
        PrimitiveType longType = types.getPrimitiveType(TypeKind.LONG);
        nativeMethodParameters.add(CodeBuilder.newParameter(longType, "isolateThread"));
        nativeMethodParameters.add(CodeBuilder.newParameter(longType, "objectId"));
        int nonReceiverParameterStart = getDefinition().hasCustomDispatch() ? 1 : 0;
        List<? extends VariableElement> parameters = methodData.element.getParameters();
        List<? extends TypeMirror> parameterTypes = methodData.type.getParameterTypes();
        boolean hasMarshallerData = false;
        boolean hasOutMarshalledParameter = false;
        for (int i = nonReceiverParameterStart; i < parameters.size(); i++) {
            MarshallerData marshallerData = methodData.getParameterMarshaller(i);
            TypeMirror parameterType = parameterTypes.get(i);
            if (isBinaryMarshallable(marshallerData, parameterType, true)) {
                hasMarshallerData = true;
                if (marshallerData.isCustom() && isOutParameter(marshallerData, parameterType, true)) {
                    hasOutMarshalledParameter = true;
                }
            } else {
                TypeMirror nativeMethodParameter = getParser().endPointMethodProvider.getEntryPointMethodParameterType(marshallerData, parameterType);
                nativeMethodParameters.add(CodeBuilder.newParameter(nativeMethodParameter, parameters.get(i).getSimpleName()));
            }
        }
        if (hasMarshallerData) {
            nativeMethodParameters.add(CodeBuilder.newParameter(types.getArrayType(types.getPrimitiveType(TypeKind.BYTE)), MARSHALLED_DATA_PARAMETER));
        }
        TypeMirror nativeMethodReturnType = hasOutMarshalledParameter ? types.getArrayType(types.getPrimitiveType(TypeKind.BYTE))
                        : getParser().endPointMethodProvider.getEntryPointMethodParameterType(methodData.getReturnTypeMarshaller(), methodData.type.getReturnType());
        builder.methodStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.NATIVE),
                        nativeMethodName,
                        nativeMethodReturnType,
                        nativeMethodParameters,
                        methodData.type.getThrownTypes());
    }

    private void generateHSToNativeEndPoint(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineStart().annotation(typeCache.suppressWarnings, "unused").lineEnd("");
        builder.classStart(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), factoryMethod.endPointSimpleName, null, Collections.emptyList());
        builder.indent();
        builder.lineEnd("");

        binaryNameCache.generateCache(builder);

        generateMarshallerFields(builder, true, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

        if (!getDefinition().getAllCustomMarshallers().isEmpty()) {
            builder.methodStart(EnumSet.of(Modifier.STATIC), null,
                            null, Collections.emptyList(), Collections.emptyList());
            builder.indent();
            builder.lineStart().write(typeCache.marshallerConfig).write(" marshallerConfig = ").invokeStatic(getDefinition().marshallerConfig, "getInstance").lineEnd(";");
            generateMarshallerLookups(builder, true);
            builder.dedent();
            builder.line("}");
            builder.line("");
        }

        for (MethodData methodData : getDefinition().toGenerate) {
            String entryPointSymbolName = jniCMethodSymbol(methodData, targetClassSimpleName);
            generateHSToNativeEndMethod(builder, methodData, entryPointSymbolName, targetClassSimpleName);
        }
        builder.dedent();
        builder.classEnd();
    }

    private void generateCEntryPointAnnotation(CodeBuilder builder, CharSequence entryPointName) {
        Map<String, Object> centryPointAttrs = new LinkedHashMap<>();
        centryPointAttrs.put("name", entryPointName);
        DeclaredType centryPointPredicate = getDefinition().centryPointPredicate;
        if (centryPointPredicate != null) {
            centryPointAttrs.put("include", centryPointPredicate);
        }
        builder.lineStart().annotationWithAttributes(typeCache.centryPoint, centryPointAttrs).lineEnd("");
    }

    private void generateHSToNativeEndMethod(CodeBuilder builder, MethodData methodData, String entryPointName, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        generateCEntryPointAnnotation(builder, entryPointName);
        List<? extends VariableElement> methodParameters = methodData.element.getParameters();
        List<? extends TypeMirror> methodParameterTypes = methodData.type.getParameterTypes();
        Set<CharSequence> warnings = new TreeSet<>(Comparator.comparing(CharSequence::toString));
        Collections.addAll(warnings, "try", "unused");
        if (!getDefinition().hasCustomDispatch() && Utilities.isParameterizedType(getDefinition().serviceType)) {
            warnings.add("unchecked");
        }
        for (int i = 0; i < methodParameters.size(); i++) {
            warnings.addAll(marshallerSnippets(methodData.getParameterMarshaller(i)).getEndPointSuppressedWarnings(builder, methodParameterTypes.get(i)));
        }
        builder.lineStart().annotation(typeCache.suppressWarnings, warnings.toArray(new CharSequence[0])).lineEnd("");
        List<CodeBuilder.Parameter> params = new ArrayList<>();
        CharSequence jniEnvVariable = "jniEnv";
        params.add(CodeBuilder.newParameter(typeCache.jniEnv, jniEnvVariable));
        params.add(CodeBuilder.newParameter(typeCache.jClass, "jniClazz"));
        CodeBuilder isolateAnnotationBuilder = new CodeBuilder(builder);
        isolateAnnotationBuilder.annotation(typeCache.isolateThreadContext, null);
        params.add(CodeBuilder.newParameter(types.getPrimitiveType(TypeKind.LONG), "isolateThread", isolateAnnotationBuilder.build()));
        params.add(CodeBuilder.newParameter(types.getPrimitiveType(TypeKind.LONG), "objectId"));
        int parameterStartIndex = getDefinition().hasCustomDispatch() ? 1 : 0;
        int marshalledDataCount = 0;
        Map.Entry<MarshallerData, TypeMirror> firstOutMarshalledParameter = null;
        List<MarshalledParameter> outParameters = new ArrayList<>();
        for (int i = parameterStartIndex; i < methodParameters.size(); i++) {
            MarshallerData marshallerData = methodData.getParameterMarshaller(i);
            CharSequence parameterName = methodParameters.get(i).getSimpleName();
            TypeMirror parameterType = methodParameterTypes.get(i);
            if (isBinaryMarshallable(marshallerData, parameterType, true)) {
                marshalledDataCount++;
            } else {
                TypeMirror endMethodParameterType = getParser().endPointMethodProvider.getEndPointMethodParameterType(marshallerData, methodParameterTypes.get(i));
                params.add(CodeBuilder.newParameter(endMethodParameterType, methodParameters.get(i).getSimpleName()));
            }
            if (isOutParameter(marshallerData, parameterType, true)) {
                outParameters.add(new MarshalledParameter(parameterName, parameterType, false, marshallerData));
                if (firstOutMarshalledParameter == null && marshallerData.isCustom()) {
                    firstOutMarshalledParameter = new SimpleImmutableEntry<>(marshallerData, parameterType);
                }
            }
        }
        if (marshalledDataCount > 0) {
            params.add(CodeBuilder.newParameter(typeCache.jByteArray, MARSHALLED_DATA_PARAMETER));
        }
        CharSequence methodName = methodData.element.getSimpleName();
        MarshallerData receiverMethodReturnTypeMarshaller = methodData.getReturnTypeMarshaller();
        TypeMirror receiverMethodReturnType = methodData.type.getReturnType();
        TypeMirror endMethodReturnType = firstOutMarshalledParameter != null
                        ? getParser().endPointMethodProvider.getEndPointMethodParameterType(firstOutMarshalledParameter.getKey(), firstOutMarshalledParameter.getValue())
                        : getParser().endPointMethodProvider.getEndPointMethodParameterType(receiverMethodReturnTypeMarshaller, receiverMethodReturnType);
        boolean voidReceiverMethod = receiverMethodReturnType.getKind() == TypeKind.VOID;
        boolean voidEndMethod = endMethodReturnType.getKind() == TypeKind.VOID;
        boolean primitiveEndMethod = endMethodReturnType.getKind().isPrimitive();
        boolean objectReturnType = !(primitiveEndMethod || voidEndMethod);
        builder.methodStart(EnumSet.of(Modifier.STATIC), methodName, endMethodReturnType, params, Collections.emptyList());
        builder.indent();
        String scopeName = targetClassSimpleName + "::" + methodName;
        String scopeInit = new CodeBuilder(builder).write(typeCache.jniMethodScope).write(" scope = ").invokeStatic(typeCache.foreignException, "openJNIMethodScope", "\"" + scopeName + "\"",
                        jniEnvVariable).build();
        if (objectReturnType) {
            builder.lineStart(scopeInit).lineEnd(";");
            scopeInit = new CodeBuilder(builder).write(typeCache.jniMethodScope).write(" sc = scope").build();
        }
        builder.lineStart("try (").write(scopeInit).lineEnd(") {");
        builder.indent();

        CharSequence[] actualParameters = new CharSequence[methodParameters.size()];
        int nonReceiverParameterStart;
        if (getDefinition().hasCustomDispatch()) {
            actualParameters[0] = "receiverObject";
            nonReceiverParameterStart = 1;
        } else {
            nonReceiverParameterStart = 0;
        }

        CharSequence resolvedDispatch;
        if (getDefinition().hasCustomDispatch()) {
            TypeMirror receiverType = getDefinition().customDispatchAccessor.getParameters().get(0).asType();
            CodeBuilder classLiteralBuilder = new CodeBuilder(builder).classLiteral(receiverType);
            CharSequence nativePeer = "nativePeer";
            resolvedDispatch = "resolvedDispatch";
            builder.lineStart().write(receiverType).space().write(nativePeer).write(" = ").invokeStatic(typeCache.referenceHandles, "resolve", "objectId", classLiteralBuilder.build()).lineEnd(
                            ";");
            builder.lineStart().write(getDefinition().serviceType).space().write(resolvedDispatch).write(" = ").invokeStatic(getDefinition().annotatedType,
                            getDefinition().customDispatchAccessor.getSimpleName(),
                            nativePeer).lineEnd(
                                            ";");
            builder.lineStart().write(typeCache.object).space().write("receiverObject").write(" = ").invokeStatic(getDefinition().annotatedType, getDefinition().customReceiverAccessor.getSimpleName(),
                            nativePeer).lineEnd(
                                            ";");
        } else {
            resolvedDispatch = "receiverObject";
            CodeBuilder classLiteralBuilder = new CodeBuilder(builder).classLiteral(getDefinition().serviceType);
            builder.lineStart().write(getDefinition().serviceType).write(" receiverObject = ").invokeStatic(typeCache.referenceHandles, "resolve", "objectId", classLiteralBuilder.build()).lineEnd(
                            ";");
        }

        // Create binary input for marshalled parameters
        CharSequence marshalledParametersInputVar = "marshalledParametersInput";
        CharSequence staticMarshallBufferVar = "staticMarshallBuffer";
        CharSequence marshallBufferVar = "marshallBuffer";
        CharSequence marshalledDataLengthVar = "marshalledDataLength";
        CharSequence isolateVar = "hsIsolate";
        CharSequence staticBufferSize = null;
        boolean marshalledResult = methodData.getReturnTypeMarshaller().isCustom();
        if (marshalledDataCount > 0 || marshalledResult) {
            staticBufferSize = Integer.toString(getStaticBufferSize(marshalledDataCount, marshalledResult, firstOutMarshalledParameter != null));
            builder.lineStart().write(typeCache.cCharPointer).space().write(staticMarshallBufferVar).write(" = ").invokeStatic(typeCache.stackValue, "get", staticBufferSize).lineEnd(";");
        }
        if (marshalledDataCount > 0) {
            builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(marshalledDataLengthVar).write(" = ").invokeStatic(typeCache.jniUtil, "GetArrayLength", jniEnvVariable,
                            MARSHALLED_DATA_PARAMETER).lineEnd(";");
            builder.lineStart().write(typeCache.cCharPointer).space().write(marshallBufferVar).write(" = ").write(marshalledDataLengthVar).write(" <= ").write(staticBufferSize).write(" ? ").write(
                            staticMarshallBufferVar).write(" : ").invokeStatic(typeCache.unmanagedMemory, "malloc", marshalledDataLengthVar).lineEnd(";");
            builder.line("try {");
            builder.indent();
            builder.lineStart().invokeStatic(typeCache.jniUtil, "GetByteArrayRegion", jniEnvVariable, MARSHALLED_DATA_PARAMETER, "0", marshalledDataLengthVar, marshallBufferVar).lineEnd(";");
            builder.lineStart().write(typeCache.binaryInput).space().write(marshalledParametersInputVar).write(" = ").invokeStatic(typeCache.binaryInput, "create", marshallBufferVar,
                            marshalledDataLengthVar).lineEnd(";");
            builder.lineStart().write(typeCache.hsIsolate).space().write(isolateVar).write(" = ").invokeStatic(typeCache.hsIsolate, "get").lineEnd(";");
        }

        Map<String, CharSequence> parameterValueOverrides = new HashMap<>();
        // Generate pre unmarshall statements
        for (int i = nonReceiverParameterStart; i < methodParameters.size(); i++) {
            marshallerSnippets(methodData.getParameterMarshaller(i)).preUnmarshallParameter(builder, methodParameterTypes.get(i), methodParameters.get(i).getSimpleName(),
                            isolateVar, marshalledParametersInputVar, jniEnvVariable, parameterValueOverrides);
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
                actualParameters[i] = marshallerSnippets(marshallerData).unmarshallParameter(builder, parameterType, parameterName, "null", marshalledParametersInputVar, jniEnvVariable);
            }
        }

        CharSequence resultSnippet = new CodeBuilder(builder).invoke(resolvedDispatch, methodData.receiverMethod != null ? methodData.receiverMethod : methodName, actualParameters).build();
        CharSequence resultVariable = null;
        if (voidReceiverMethod) {
            builder.lineStart().write(resultSnippet).lineEnd(";");
        } else {
            resultVariable = "endPointResult";
            builder.lineStart().write(receiverMethodReturnType).space().write(resultVariable).write(" = ").write(resultSnippet).lineEnd(";");
        }

        CharSequence marshalledOutputVar = null;
        boolean hasBinaryMarshalledResult = methodData.getReturnTypeMarshaller().isCustom() || firstOutMarshalledParameter != null;
        if (hasBinaryMarshalledResult) {
            List<MarshalledParameter> marshallers = new ArrayList<>();
            if (!voidReceiverMethod) {
                marshallers.add(new MarshalledParameter(resultVariable, receiverMethodReturnType, true, methodData.getReturnTypeMarshaller()));
            }
            marshallers.addAll(outParameters);
            marshalledOutputVar = "marshalledResultOutput";
            CharSequence binaryOutputInit = generateCCharPointerBinaryOutputInit(builder, marshalledOutputVar, marshalledDataCount > 0,
                            marshallers, marshallBufferVar, staticMarshallBufferVar, marshalledDataLengthVar, staticBufferSize);
            builder.lineStart("try (").write(binaryOutputInit).lineEnd(") {");
            builder.indent();
        }

        // Do result marshalling for non-void methods
        if (firstOutMarshalledParameter != null) {
            resultSnippet = marshallerSnippets(firstOutMarshalledParameter.getKey()).marshallResult(builder, null, null, marshalledOutputVar, null);
            if (resultVariable != null) {
                marshallerSnippets(methodData.getReturnTypeMarshaller()).write(builder, receiverMethodReturnType, resultVariable, marshalledOutputVar, null);
            }
        } else if (resultVariable != null) {
            MarshallerSnippet receiverMethodReturnTypeSnippets = marshallerSnippets(receiverMethodReturnTypeMarshaller);
            CharSequence resultOverride = receiverMethodReturnTypeSnippets.preMarshallResult(builder, receiverMethodReturnType, resultVariable, marshalledOutputVar, jniEnvVariable);
            if (resultOverride != null) {
                resultVariable = resultOverride;
            }
            resultSnippet = receiverMethodReturnTypeSnippets.marshallResult(builder, receiverMethodReturnType, resultVariable, marshalledOutputVar, jniEnvVariable);
        }

        if (!outParameters.isEmpty() && marshalledOutputVar == null) {
            marshalledOutputVar = "marshalledParametersOutput";
            CharSequence binaryOutputInit = generateCCharPointerBinaryOutputInitForMarshalledData(builder, marshalledOutputVar, marshallBufferVar, marshalledDataLengthVar);
            builder.lineStart("try (").write(binaryOutputInit).lineEnd(") {");
            builder.indent();
        }

        // Generate post unmarshall statements.
        for (int i = nonReceiverParameterStart; i < methodParameters.size(); i++) {
            HotSpotToNativeMarshallerSnippet marshallerSnippets = marshallerSnippets(methodData.getParameterMarshaller(i));
            marshallerSnippets.postUnmarshallParameter(builder, methodParameterTypes.get(i), methodParameters.get(i).getSimpleName(), marshalledOutputVar, jniEnvVariable, resultVariable);
        }

        // Clean up binary output for result marshalling
        if (marshalledOutputVar != null) {
            CharSequence posVar = "marshalledResultPosition";
            builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(posVar).write(" = ").invoke(marshalledOutputVar, "getPosition").lineEnd(";");
            CharSequence jByteArrayInit = new CodeBuilder(builder).invokeStatic(typeCache.jniUtil, "NewByteArray", jniEnvVariable, posVar).build();
            CharSequence target;
            if (hasBinaryMarshalledResult) {
                builder.lineStart().write(typeCache.jByteArray).space().write(resultSnippet).write(" = ");
                if (marshalledDataCount > 0) {
                    builder.write(posVar).write(" <= ").write(marshalledDataLengthVar).write(" ? ").write(MARSHALLED_DATA_PARAMETER).write(" : ").write(jByteArrayInit).lineEnd(";");
                } else {
                    builder.write(jByteArrayInit).lineEnd(";");
                }
                target = resultSnippet;
            } else {
                target = MARSHALLED_DATA_PARAMETER;
            }
            CharSequence address = new CodeBuilder(builder).invoke(marshalledOutputVar, "getAddress").build();
            builder.lineStart().invokeStatic(typeCache.jniUtil, "SetByteArrayRegion", jniEnvVariable, target, "0", posVar, address).lineEnd(";");
        }

        // Generate return value
        if (primitiveEndMethod) {
            builder.lineStart("return ").write(resultSnippet).lineEnd(";");
        } else if (!voidEndMethod) {
            builder.lineStart().invoke("scope", "setObjectResult", resultSnippet).lineEnd(";");
        }

        // Close binary output for result marshalling
        if (marshalledOutputVar != null) {
            builder.dedent();
            builder.line("}");
        }

        // Clean up binary input for marshalled parameters
        if (marshalledDataCount > 0) {
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
        builder.dedent();
        String exceptionVariable = "e";
        builder.lineStart("} catch (").write(typeCache.throwable).space().write(exceptionVariable).lineEnd(") {");
        builder.indent();
        CharSequence newForeignException = new CodeBuilder(builder).invokeStatic(typeCache.foreignException, "forThrowable",
                        exceptionVariable, getDefinition().getCustomMarshaller(typeCache.throwable, null, types).name).build();
        builder.lineStart().invoke(newForeignException, "throwUsingJNI", jniEnvVariable).lineEnd(";");
        if (primitiveEndMethod) {
            builder.lineStart("return ").writeDefaultValue(endMethodReturnType).lineEnd(";");
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

    private String jniCMethodSymbol(MethodData methodData, CharSequence targetClassSimpleName) {
        String packageName = Utilities.getEnclosingPackageElement((TypeElement) getDefinition().annotatedType.asElement()).getQualifiedName().toString();
        String classSimpleName = targetClassSimpleName + "$" + factoryMethod.startPointSimpleName;
        StringBuilder dylibSymbol = new StringBuilder();
        dylibSymbol.append("Java_");
        dylibSymbol.append(Utilities.cSymbol(packageName));
        dylibSymbol.append("_");
        dylibSymbol.append(Utilities.cSymbol(classSimpleName));
        dylibSymbol.append("_");
        dylibSymbol.append(Utilities.cSymbol(getParser().endPointMethodProvider.getEntryPointMethodName(methodData)));
        if (methodData.hasOverload()) {
            // function has an overload, we need to add a signature to the dynamic library symbol
            dylibSymbol.append("__");
            for (TypeMirror parameterType : getParser().endPointMethodProvider.getEntryPointSignature(methodData, getDefinition().hasCustomDispatch())) {
                encodeTypeForJniCMethodSymbol(dylibSymbol, types.erasure(parameterType));
            }
        }
        return dylibSymbol.toString();
    }

    private void encodeTypeForJniCMethodSymbol(StringBuilder into, TypeMirror type) {
        Utilities.encodeType(into, type, "_3", (te) -> "L" + Utilities.cSymbol(getParser().elements.getBinaryName(te).toString()) + "_2");
    }

    private abstract static class HotSpotToNativeMarshallerSnippet extends MarshallerSnippet {

        final HotSpotToNativeTypeCache cache;

        HotSpotToNativeMarshallerSnippet(HotSpotToNativeServiceGenerator generator, MarshallerData marshallerData) {
            super(generator, marshallerData);
            this.cache = generator.typeCache;
        }

        static HotSpotToNativeMarshallerSnippet forData(HotSpotToNativeServiceGenerator generator, MarshallerData marshallerData) {
            return switch (marshallerData.kind) {
                case VALUE -> new DirectSnippet(generator, marshallerData);
                case REFERENCE ->
                    new ReferenceSnippet(generator, marshallerData);
                case PEER_REFERENCE ->
                    new PeerReferenceSnippet(generator, marshallerData);
                case CUSTOM -> new CustomSnippet(generator, marshallerData);
            };
        }

        private static final class DirectSnippet extends HotSpotToNativeMarshallerSnippet {

            DirectSnippet(HotSpotToNativeServiceGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName) {
                return formalParameter;
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet,
                            CharSequence isolateVar, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                return invocationSnippet;
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                if (types.isSameType(cache.string, resultType)) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createHSString", jniEnvFieldName, invocationSnippet).build();
                } else if (resultType.getKind() == TypeKind.ARRAY) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createHSArray", jniEnvFieldName, invocationSnippet).build();
                } else {
                    return invocationSnippet;
                }
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName) {
                if (types.isSameType(cache.string, parameterType)) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createString", jniEnvFieldName, parameterName).build();
                } else if (parameterType.getKind() == TypeKind.ARRAY) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createArray", jniEnvFieldName, parameterName).build();
                } else {
                    return parameterName;
                }
            }

            @Override
            boolean preUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverride) {
                boolean hasDirectionModifiers = isArrayWithDirectionModifiers(parameterType);
                if (hasDirectionModifiers) {
                    currentBuilder.lineStart().write(parameterType).space().write(outArrayLocal(parameterName)).write(" = ");
                    if (marshallerData.in != null) {
                        CharSequence arrayLengthParameter = marshallerData.in.lengthParameter;
                        if (arrayLengthParameter != null) {
                            currentBuilder.newArray(((ArrayType) parameterType).getComponentType(), arrayLengthParameter).lineEnd(";");
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
                            currentBuilder.invokeStatic(cache.jniUtil, "createArray", jniEnvFieldName, parameterName).lineEnd(";");
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
                            arrayLengthSnippet = new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "GetArrayLength", jniEnvFieldName, parameterName).build();
                        }
                        currentBuilder.newArray(((ArrayType) parameterType).getComponentType(), arrayLengthSnippet).lineEnd(";");
                    }
                    parameterValueOverride.put(parameterName.toString(), outArrayLocal(parameterName));
                }
                return hasDirectionModifiers;
            }

            @Override
            void postUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName,
                            CharSequence resultVariableName) {
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
                            arrayLengthSnippet = new CodeBuilder(currentBuilder).memberSelect(outArrayLocal(parameterName), "length", false).build();
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
            void read(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence resultVariable, CharSequence isolateVar, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
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
        }

        private static final class ReferenceSnippet extends HotSpotToNativeMarshallerSnippet {

            private final ServiceDefinitionData data;
            private final AbstractEndPointMethodProvider endPointMethodProvider;

            ReferenceSnippet(HotSpotToNativeServiceGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
                this.data = generator.getDefinition();
                this.endPointMethodProvider = generator.getParser().endPointMethodProvider;
            }

            @Override
            Set<CharSequence> getEndPointSuppressedWarnings(CodeBuilder currentBuilder, TypeMirror type) {
                if (Utilities.isParameterizedType(type)) {
                    return Collections.singleton("unchecked");
                } else {
                    return Collections.emptySet();
                }
            }

            @Override
            boolean preMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverrides) {
                if (marshallerData.sameDirection && parameterType.getKind() == TypeKind.ARRAY) {
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
                    currentBuilder.lineStart("if(").write(parameterName).write(" != null) ").lineEnd("{");
                    currentBuilder.indent();
                    WriteArrayPolicy copyContent = marshallerData.out == null || marshallerData.in != null ? WriteArrayPolicy.STORE : WriteArrayPolicy.RESERVE;
                    TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                    generateWriteReferenceArray(currentBuilder, parameterName, copyContent, true, arrayOffsetParameter, arrayLength, marshalledParametersOutput,
                                    (element) -> marshallParameter(currentBuilder, componentType, element, marshalledParametersOutput, jniEnvFieldName));
                    currentBuilder.dedent();
                    currentBuilder.line("} else {");
                    currentBuilder.indent();
                    currentBuilder.lineStart().invoke(marshalledParametersOutput, "writeInt", "-1").lineEnd(";");
                    currentBuilder.dedent();
                    currentBuilder.line("}");
                    return true;
                }
                return false;
            }

            @Override
            void postMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName, CharSequence resultVariableName) {
                if (marshallerData.sameDirection && parameterType.getKind() == TypeKind.ARRAY) {
                    if (marshallerData.out != null) {
                        // Out parameter. We need to copy the content from buffer to array.
                        boolean trimToResult = marshallerData.out.trimToResult;
                        CharSequence arrayLengthParameter = marshallerData.out.lengthParameter;
                        CharSequence arrayLengthSnippet;
                        if (trimToResult) {
                            arrayLengthSnippet = resultVariableName;
                        } else if (arrayLengthParameter != null) {
                            arrayLengthSnippet = arrayLengthParameter;
                        } else {
                            arrayLengthSnippet = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                        }
                        CharSequence arrayOffsetParameter = marshallerData.out.offsetParameter;
                        currentBuilder.lineStart("if(").write(parameterName).write(" != null) ").lineEnd("{");
                        currentBuilder.indent();
                        TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                        generateReadReferenceArray(currentBuilder, parameterName, arrayOffsetParameter, arrayLengthSnippet, marshalledParametersInput,
                                        (element) -> unmarshallResult(currentBuilder, componentType, element, isolateVar, marshalledParametersInput, null));
                        currentBuilder.dedent();
                        currentBuilder.line("}");
                    }
                }
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName) {
                if (marshallerData.sameDirection) {
                    if (parameterType.getKind() == TypeKind.ARRAY) {
                        // Already marshalled and written by preMarshallParameter. Return no code
                        // (null).
                        return null;
                    } else {
                        return marshallHotSpotToNativeProxyInHotSpot(currentBuilder, formalParameter);
                    }
                } else {
                    return formalParameter;
                }
            }

            @Override
            CharSequence storeRawResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
                if (marshallerData.sameDirection) {
                    CharSequence resultVariable = "endPointResult";
                    currentBuilder.lineStart().write(endPointMethodProvider.getEntryPointMethodParameterType(marshallerData, resultType)).space().write(resultVariable).write(" = ").write(
                                    invocationSnippet).lineEnd(";");
                    return resultVariable;
                } else {
                    return null;
                }
            }

            @Override
            CharSequence preUnmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet,
                            CharSequence isolateVar, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                if (marshallerData.sameDirection && resultType.getKind() == TypeKind.ARRAY) {
                    TypeMirror componentType = ((ArrayType) resultType).getComponentType();
                    currentBuilder.lineStart().write(resultType).space().write(HOTSPOT_RESULT_VARIABLE).lineEnd(";");
                    currentBuilder.lineStart("if (").write(invocationSnippet).write(" != null) ").lineEnd("{");
                    currentBuilder.indent();
                    CharSequence arrayLength = new CodeBuilder(currentBuilder).memberSelect(invocationSnippet, "length", false).build();
                    currentBuilder.lineStart(HOTSPOT_RESULT_VARIABLE).write(" = ").newArray(componentType, arrayLength).lineEnd(";");
                    CharSequence hsResultIndexVariable = HOTSPOT_RESULT_VARIABLE + "Index";
                    currentBuilder.lineStart().forLoop(
                                    List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(hsResultIndexVariable).write(" = 0").build()),
                                    new CodeBuilder(currentBuilder).write(hsResultIndexVariable).write(" < ").write(arrayLength).build(),
                                    List.of(new CodeBuilder(currentBuilder).write(hsResultIndexVariable).write("++").build())).lineEnd(" {");
                    currentBuilder.indent();
                    CharSequence resultElement = new CodeBuilder(currentBuilder).arrayElement(invocationSnippet, hsResultIndexVariable).build();
                    currentBuilder.lineStart().arrayElement(HOTSPOT_RESULT_VARIABLE, hsResultIndexVariable).write(" = ").write(
                                    unmarshallResult(currentBuilder, componentType, resultElement, isolateVar, null, jniEnvFieldName)).lineEnd(";");
                    currentBuilder.dedent();
                    currentBuilder.line("}");
                    currentBuilder.dedent();
                    currentBuilder.line("} else {");
                    currentBuilder.indent();
                    currentBuilder.lineStart(HOTSPOT_RESULT_VARIABLE).write(" = null").lineEnd(";");
                    currentBuilder.dedent();
                    currentBuilder.line("}");
                    return HOTSPOT_RESULT_VARIABLE;
                }
                return null;
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet,
                            CharSequence isolateVar, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                if (marshallerData.sameDirection) {
                    if (resultType.getKind() == TypeKind.ARRAY) {
                        // Already unmarshalled by preUnmarshallResult. Just return the
                        // invocationSnippet.
                        return invocationSnippet;
                    } else {
                        return unmarshallHotSpotToNativeProxyInHotSpot(currentBuilder, invocationSnippet, isolateVar);
                    }
                } else {
                    return unmarshallNativeToHotSpotProxyInHotSpot(currentBuilder, resultType, invocationSnippet, data);
                }
            }

            @Override
            CharSequence preMarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    TypeMirror hsResultType = endPointMethodProvider.getEndPointMethodParameterType(marshallerData, resultType);
                    TypeMirror componentType = ((ArrayType) resultType).getComponentType();
                    CharSequence hsArrayLength = new CodeBuilder(currentBuilder).memberSelect(invocationSnippet, "length", false).build();
                    currentBuilder.lineStart().write(hsResultType).space().write(HOTSPOT_RESULT_VARIABLE).lineEnd(";");
                    if (marshallerData.sameDirection) {
                        currentBuilder.lineStart("if (").write(invocationSnippet).write(" != null) ").lineEnd("{");
                        currentBuilder.indent();
                        CharSequence hsResultHandlesVariable = HOTSPOT_RESULT_VARIABLE + "Handles";
                        currentBuilder.lineStart().write(types.getArrayType(types.getPrimitiveType(TypeKind.LONG))).space().write(hsResultHandlesVariable).write(" = ").newArray(
                                        types.getPrimitiveType(TypeKind.LONG), hsArrayLength).lineEnd(";");
                        CharSequence indexVariable = hsResultHandlesVariable + "Index";
                        currentBuilder.lineStart().forLoop(List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(indexVariable).write(" = 0").build()),
                                        new CodeBuilder(currentBuilder).write(indexVariable).write(" < ").write(hsArrayLength).build(),
                                        List.of(new CodeBuilder(currentBuilder).write(indexVariable).write("++").build())).lineEnd(" {");
                        currentBuilder.indent();
                        CharSequence arrayElement = new CodeBuilder(currentBuilder).arrayElement(invocationSnippet, indexVariable).build();
                        CharSequence value = marshallResult(currentBuilder, componentType, arrayElement, null, jniEnvFieldName);
                        currentBuilder.lineStart().arrayElement(hsResultHandlesVariable, indexVariable).write(" = ").write(value).lineEnd(";");
                        currentBuilder.dedent();
                        currentBuilder.line("}");
                        currentBuilder.lineStart(HOTSPOT_RESULT_VARIABLE).write(" = ").invokeStatic(cache.jniUtil, "createHSArray", jniEnvFieldName, hsResultHandlesVariable).lineEnd(";");
                        currentBuilder.dedent();
                        currentBuilder.line("} else {");
                        currentBuilder.indent();
                        currentBuilder.lineStart(HOTSPOT_RESULT_VARIABLE).write(" = ").invokeStatic(cache.wordFactory, "nullPointer").lineEnd(";");
                        currentBuilder.dedent();
                        currentBuilder.line("}");
                    } else {
                        generateCopyHSPeerArrayToHotSpot(currentBuilder, HOTSPOT_RESULT_VARIABLE, componentType, invocationSnippet, null, hsArrayLength, jniEnvFieldName);
                    }
                    return HOTSPOT_RESULT_VARIABLE;
                }
                return null;
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    // Already marshalled by preMarshallResult. Just return the invocationSnippet.
                    return invocationSnippet;
                } else if (marshallerData.sameDirection) {
                    return marshallHotSpotToNativeProxyInNative(currentBuilder, invocationSnippet);
                } else {
                    return marshallNativeToHotSpotProxyInNative(currentBuilder, invocationSnippet);
                }
            }

            @Override
            boolean preUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledResultInput,
                            CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverride) {
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                    if (marshallerData.sameDirection) {
                        currentBuilder.lineStart().write(parameterType).space().write(parameterName).lineEnd(";");
                        CharSequence arrayLengthVariable = parameterName + "Length";
                        currentBuilder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(arrayLengthVariable).write(" = ").invoke(marshalledResultInput, "readInt").lineEnd(";");
                        currentBuilder.lineStart("if(").write(arrayLengthVariable).write(" != -1) ").lineEnd("{");
                        currentBuilder.indent();
                        currentBuilder.lineStart(parameterName).write(" = ").newArray(componentType, arrayLengthVariable).lineEnd(";");
                        if (marshallerData.out == null || marshallerData.in != null) {
                            // Default (in), in or in-out parameter
                            generateReadReferenceArray(currentBuilder, parameterName, null, arrayLengthVariable, marshalledResultInput,
                                            (element) -> unmarshallParameter(currentBuilder, componentType, element, isolateVar, marshalledResultInput, jniEnvFieldName));
                        }
                        currentBuilder.dedent();
                        currentBuilder.line("} else {");
                        currentBuilder.indent();
                        currentBuilder.lineStart(parameterName).write(" = null").lineEnd(";");
                        currentBuilder.dedent();
                        currentBuilder.line("}");
                    } else {
                        CharSequence arrayVariable = outArrayLocal(parameterName);
                        parameterValueOverride.put(parameterName.toString(), arrayVariable);

                        currentBuilder.lineStart().write(parameterType).space().write(arrayVariable).lineEnd(";");
                        currentBuilder.lineStart().write("if (").invoke(parameterName, "isNonNull").lineEnd("){");
                        currentBuilder.indent();
                        // Default direction modifier (`in`) or explicit `in` modifier.
                        boolean needsCopy = marshallerData.out == null || marshallerData.in != null;
                        CharSequence arrayLengthParameter = null;
                        CharSequence arrayOffsetParameter = null;
                        if (marshallerData.in != null) {
                            arrayLengthParameter = marshallerData.in.lengthParameter;
                            arrayOffsetParameter = marshallerData.in.offsetParameter;
                        } else if (marshallerData.out != null) {
                            arrayLengthParameter = marshallerData.out.lengthParameter;
                            arrayOffsetParameter = marshallerData.out.offsetParameter;
                        }
                        if (arrayLengthParameter == null) {
                            CharSequence getArrayLengthSnippet = new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "GetArrayLength", jniEnvFieldName, parameterName).build();
                            if (needsCopy) {
                                // Create a local variable to prevent an expensive GetArrayLength VM
                                // call inside a loop
                                arrayLengthParameter = parameterName + "Length";
                                currentBuilder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(arrayLengthParameter).write(" = ").write(getArrayLengthSnippet).lineEnd(";");
                            } else {
                                arrayLengthParameter = getArrayLengthSnippet;
                            }
                        }
                        if (arrayOffsetParameter != null) {
                            parameterValueOverride.put(arrayOffsetParameter.toString(), "0");
                        }
                        currentBuilder.lineStart(arrayVariable).write(" = ").newArray(componentType, arrayLengthParameter).lineEnd(";");
                        if (needsCopy) {
                            generateCopyHotSpotToHSPeerArray(currentBuilder, arrayVariable, null, arrayLengthParameter, parameterName, arrayOffsetParameter, jniEnvFieldName,
                                            (element) -> unmarshallParameter(currentBuilder, componentType, element, isolateVar, marshalledResultInput, jniEnvFieldName));
                        }
                        currentBuilder.dedent();
                        currentBuilder.line("} else {");
                        currentBuilder.indent();
                        currentBuilder.lineStart(arrayVariable).lineEnd(" = null;");
                        currentBuilder.dedent();
                        currentBuilder.line("}");
                    }
                    return true;
                }
                return false;
            }

            @Override
            void postUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName,
                            CharSequence resultVariableName) {
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    if (marshallerData.out != null) {
                        boolean trimToResult = marshallerData.out.trimToResult;
                        CharSequence arrayLengthParameter = marshallerData.out.lengthParameter;
                        if (marshallerData.sameDirection) {
                            CharSequence arrayLengthSnippet;
                            if (trimToResult) {
                                arrayLengthSnippet = resultVariableName;
                            } else if (arrayLengthParameter != null) {
                                arrayLengthSnippet = arrayLengthParameter;
                            } else {
                                arrayLengthSnippet = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                            }
                            currentBuilder.lineStart("if(").write(parameterName).write(" != null) ").lineEnd("{");
                            currentBuilder.indent();
                            CharSequence indexVariable = parameterName + "Index";
                            currentBuilder.lineStart().forLoop(List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(indexVariable).write(" = 0").build()),
                                            new CodeBuilder(currentBuilder).write(indexVariable).write(" < ").write(arrayLengthSnippet).build(),
                                            List.of(new CodeBuilder(currentBuilder).write(indexVariable).write("++").build())).lineEnd(" {");
                            currentBuilder.indent();
                            CharSequence element = new CodeBuilder(currentBuilder).arrayElement(parameterName, indexVariable).build();
                            TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                            currentBuilder.lineStart().invoke(marshalledResultOutput, "writeLong", marshallResult(currentBuilder, componentType, element, null, jniEnvFieldName)).lineEnd(";");
                            currentBuilder.dedent();
                            currentBuilder.line("}");
                            currentBuilder.dedent();
                            currentBuilder.line("}");
                        } else {
                            TypeMirror componentType = ((ArrayType) parameterType).getComponentType();
                            CharSequence arrayVariable = outArrayLocal(parameterName);
                            CharSequence arrayLengthSnippet;
                            if (trimToResult) {
                                arrayLengthSnippet = resultVariableName;
                            } else if (arrayLengthParameter != null) {
                                arrayLengthSnippet = arrayLengthParameter;
                            } else {
                                arrayLengthSnippet = new CodeBuilder(currentBuilder).memberSelect(arrayVariable, "length", false).build();
                            }
                            CharSequence arrayOffsetParameter = marshallerData.out.offsetParameter;
                            CharSequence arrayNullCheck = new CodeBuilder(currentBuilder).write(arrayVariable).write(" != null").build();
                            currentBuilder.lineStart("if (").write(arrayNullCheck).lineEnd(") {");
                            currentBuilder.indent();
                            String arrayIndexVariable = outArrayLocal(parameterName) + "Index";
                            currentBuilder.lineStart().forLoop(
                                            List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(arrayIndexVariable).write(" = 0").build()),
                                            new CodeBuilder(currentBuilder).write(arrayIndexVariable).write(" < ").write(arrayLengthSnippet).build(),
                                            List.of(new CodeBuilder(currentBuilder).write(arrayIndexVariable).write("++").build())).lineEnd(" {");
                            currentBuilder.indent();
                            String arrayElementVariable = arrayVariable + "Element";
                            currentBuilder.lineStart().write(componentType).space().write(arrayElementVariable).write(" = ").arrayElement(arrayVariable, arrayIndexVariable).lineEnd(";");
                            CharSequence index = arrayOffsetParameter == null ? arrayIndexVariable
                                            : new CodeBuilder(currentBuilder).write(arrayOffsetParameter).write(" + ").write(arrayIndexVariable).build();
                            CharSequence value = marshallResult(currentBuilder, componentType, arrayElementVariable, null, jniEnvFieldName);
                            currentBuilder.lineStart().invokeStatic(cache.jniUtil, "SetObjectArrayElement", jniEnvFieldName, parameterName, index, value).lineEnd(";");
                            currentBuilder.dedent();
                            currentBuilder.line("}");
                            currentBuilder.dedent();
                            currentBuilder.line("}");
                        }
                    }
                }
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName) {
                if (parameterType.getKind() == TypeKind.ARRAY) {
                    // Already marshalled by preUnmarshallParameter. Return parameterName;
                    return parameterName;
                } else {
                    if (marshallerData.sameDirection) {
                        return unmarshallHotSpotToNativeProxyInNative(currentBuilder, parameterType, parameterName, data);
                    } else {
                        return unmarshallNativeToHotSpotProxyInNative(currentBuilder, parameterName, jniEnvFieldName);
                    }
                }
            }

            @Override
            void write(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                throw new UnsupportedOperationException("Marshalling to BinaryOutput is not supported for references.");
            }

            @Override
            void read(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence resultVariable, CharSequence isolateVar, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                throw new UnsupportedOperationException("Marshalling to BinaryOutput is not supported for references.");
            }
        }

        private static final class PeerReferenceSnippet extends HotSpotToNativeMarshallerSnippet {

            PeerReferenceSnippet(HotSpotToNativeServiceGenerator generator, MarshallerData marshallerData) {
                super(generator, marshallerData);
            }

            @Override
            CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName) {
                if (marshallerData.sameDirection) {
                    CharSequence value = new CodeBuilder(currentBuilder).cast(cache.nativePeer, formalParameter, true).build();
                    return new CodeBuilder(currentBuilder).invoke(value, "getHandle").build();
                } else {
                    return formalParameter;
                }
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName) {
                if (marshallerData.sameDirection) {
                    CharSequence typeLiteral = new CodeBuilder(currentBuilder).classLiteral(parameterType).build();
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.referenceHandles, "resolve", parameterName, typeLiteral).build();
                } else {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.peer, "create", jniEnvFieldName, parameterName).build();
                }
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                if (marshallerData.sameDirection) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.referenceHandles, "create", invocationSnippet).build();
                } else {
                    CharSequence value = new CodeBuilder(currentBuilder).cast(cache.hSPeer, invocationSnippet, true).build();
                    return new CodeBuilder(currentBuilder).invoke(value, "getJObject").build();
                }
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet,
                            CharSequence isolateVar, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                if (marshallerData.sameDirection) {
                    return new CodeBuilder(currentBuilder).invokeStatic(cache.peer, "create", isolateVar, invocationSnippet).build();
                } else {
                    return invocationSnippet;
                }
            }

            @Override
            void write(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                throw new UnsupportedOperationException("Marshalling to BinaryOutput is not supported for raw references.");
            }

            @Override
            void read(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence resultVariable, CharSequence isolateVar, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                throw new UnsupportedOperationException("Marshalling to BinaryOutput is not supported for raw references.");
            }
        }

        private static final class CustomSnippet extends HotSpotToNativeMarshallerSnippet {

            CustomSnippet(HotSpotToNativeServiceGenerator generator, MarshallerData marshallerData) {
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
            CharSequence preUnmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence isolateVar, CharSequence marshalledResultInput,
                            CharSequence jniEnvFieldName) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    CharSequence resultVariable = HOTSPOT_RESULT_VARIABLE;
                    readCustomObject(currentBuilder, resultType, resultVariable, isolateVar, marshalledResultInput, true);
                    return resultVariable;
                } else {
                    return null;
                }
            }

            @Override
            CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet,
                            CharSequence isolateVar, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                if (resultType.getKind() == TypeKind.ARRAY) {
                    return HOTSPOT_RESULT_VARIABLE;
                } else {
                    return new CodeBuilder(currentBuilder).invoke(marshallerData.name, "read", isolateVar, marshalledResultInput).build();
                }
            }

            @Override
            CharSequence storeRawResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
                CharSequence resultVariable = "endPointResult";
                currentBuilder.lineStart().write(types.getArrayType(types.getPrimitiveType(TypeKind.BYTE))).space().write(resultVariable).write(" = ").write(invocationSnippet).lineEnd(";");
                return resultVariable;
            }

            @Override
            CharSequence preMarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                writeCustomObject(currentBuilder, resultType, invocationSnippet, marshalledResultOutput);
                return null;
            }

            @Override
            CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                return "marshalledResult";
            }

            @Override
            boolean preUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName,
                            Map<String, CharSequence> parameterValueOverride) {
                readCustomObject(currentBuilder, parameterType, parameterName, isolateVar, marshalledParametersInput, true);
                return true;
            }

            @Override
            CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName) {
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
            void postMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                            CharSequence jniEnvFieldName, CharSequence resultVariableName) {
                if (marshallerData.out != null) {
                    CharSequence offset = resolveOffset(marshallerData.out);
                    CharSequence length = updateLengthSnippet(currentBuilder, parameterName, resultVariableName);
                    readCustomObjectUpdate(currentBuilder, parameterType, parameterName, isolateVar, marshalledParametersInput, offset, length, false);
                }
            }

            @Override
            void write(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
                writeCustomObject(currentBuilder, resultType, invocationSnippet, marshalledResultOutput);
            }

            @Override
            void read(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence resultVariable, CharSequence isolateVar, CharSequence marshalledResultInput, CharSequence jniEnvFieldName) {
                readCustomObject(currentBuilder, resultType, resultVariable, isolateVar, marshalledResultInput, true);
            }
        }
    }
}
