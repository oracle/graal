/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.UnionType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.graalvm.nativebridge.processor.AbstractBridgeParser.AbstractTypeCache;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MarshallerData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MethodData;

abstract class AbstractBridgeGenerator {

    static final String MARSHALLED_DATA_PARAMETER = "marshalledData";
    private static final int BYTES_PER_PARAMETER = 256;

    final AbstractBridgeParser parser;
    final DefinitionData definitionData;
    final Types types;
    final BinaryNameCache binaryNameCache;

    private final AbstractTypeCache typeCache;

    AbstractBridgeGenerator(AbstractBridgeParser parser, DefinitionData definitionData, AbstractTypeCache typeCache, BinaryNameCache binaryNameCache) {
        this.parser = parser;
        this.definitionData = definitionData;
        this.types = parser.types;
        this.typeCache = typeCache;
        this.binaryNameCache = binaryNameCache;
    }

    final FactoryMethodInfo resolveFactoryMethod(CharSequence factoryMethodName, CharSequence startPointSimpleName, CharSequence endPointSimpleName,
                    CodeBuilder.Parameter... additionalRequiredParameters) {
        List<CodeBuilder.Parameter> parameters = new ArrayList<>(definitionData.annotatedTypeConstructorParams.size() + 1);
        List<CodeBuilder.Parameter> superParameters = new ArrayList<>(definitionData.annotatedTypeConstructorParams.size());
        List<CodeBuilder.Parameter> requiredList = new ArrayList<>();
        Collections.addAll(requiredList, additionalRequiredParameters);
        for (VariableElement ve : definitionData.annotatedTypeConstructorParams) {
            TypeMirror parameterType = ve.asType();
            CodeBuilder.Parameter parameter = CodeBuilder.newParameter(parameterType, ve.getSimpleName());
            requiredList.removeIf((required) -> types.isSameType(required.type, parameterType));
            parameters.add(parameter);
            superParameters.add(parameter);
        }
        parameters.addAll(requiredList);
        return new FactoryMethodInfo(factoryMethodName, startPointSimpleName, endPointSimpleName, parameters, superParameters);
    }

    abstract void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName);

    abstract void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName);

    abstract MarshallerSnippet marshallerSnippets(MarshallerData marshallerData);

    void configureMultipleDefinitions(@SuppressWarnings("unused") List<DefinitionData> otherDefinitions) {
    }

    static int getStaticBufferSize(int marshalledParametersCount, boolean marshalledResult, boolean hasOutParameter) {
        int slots = marshalledParametersCount != 0 ? marshalledParametersCount : marshalledResult ? 1 : 0;
        if (hasOutParameter) {
            slots++;
        }
        return slots * BYTES_PER_PARAMETER;
    }

    final void generateStartPointFactory(CodeBuilder builder, FactoryMethodInfo factoryMethod) {
        builder.methodStart(EnumSet.of(Modifier.STATIC), factoryMethod.name, definitionData.annotatedType,
                        factoryMethod.parameters, Collections.emptyList());
        builder.indent();
        builder.lineStart("return ").newInstance(factoryMethod.startPointSimpleName, parameterNames(factoryMethod.parameters)).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    final void generateMarshallerLookups(CodeBuilder builder) {
        for (MarshallerData marshaller : definitionData.getAllCustomMarshallers()) {
            List<CharSequence> params = new ArrayList<>();
            if (types.isSameType(marshaller.forType, types.erasure(marshaller.forType))) {
                params.add(new CodeBuilder(builder).classLiteral(marshaller.forType).build());
            } else {
                params.add(typeLiteral(new CodeBuilder(builder), marshaller.forType).build());
            }
            for (AnnotationMirror annotationType : marshaller.annotations) {
                params.add(new CodeBuilder(builder).classLiteral(annotationType.getAnnotationType()).build());
            }
            builder.lineStart().write(marshaller.name).write(" = ");
            builder.invoke("config", "lookupMarshaller", params.toArray(new CharSequence[0])).lineEnd(";");
        }
    }

    public static CodeBuilder typeLiteral(CodeBuilder builder, TypeMirror type) {
        return builder.newInstance((DeclaredType) builder.types.erasure(((AbstractTypeCache) builder.typeCache).typeLiteral), Collections.singletonList(type)).write("{}");
    }

    final CacheSnippet cacheSnippets() {
        if (definitionData.hasCustomDispatch()) {
            return CacheSnippet.customDispatch(types, parser.typeCache);
        } else {
            return CacheSnippet.standardDispatch(types, parser.typeCache);
        }
    }

    final CodeBuilder overrideMethod(CodeBuilder builder, MethodData methodData) {
        for (AnnotationMirror mirror : methodData.element.getAnnotationMirrors()) {
            if (!Utilities.contains(definitionData.ignoreAnnotations, mirror.getAnnotationType(), types) &&
                            !Utilities.contains(definitionData.marshallerAnnotations, mirror.getAnnotationType(), types)) {
                builder.lineStart().annotation(mirror.getAnnotationType(), null).lineEnd("");
            }
        }
        return overrideMethod(builder, methodData.element, methodData.type);
    }

    final CodeBuilder overrideMethod(CodeBuilder builder, ExecutableElement methodElement, ExecutableType methodType) {
        builder.lineStart().annotation(parser.typeCache.override, null).lineEnd("");
        Set<Modifier> newModifiers = EnumSet.copyOf(methodElement.getModifiers());
        newModifiers.remove(Modifier.ABSTRACT);
        builder.methodStart(newModifiers, methodElement.getSimpleName(),
                        methodType.getReturnType(),
                        CodeBuilder.newParameters(methodElement.getParameters(), methodType.getParameterTypes(), methodElement.isVarArgs()),
                        methodType.getThrownTypes());
        return builder;
    }

    static boolean isParameterizedType(TypeMirror type) {
        switch (type.getKind()) {
            case DECLARED:
                return !((DeclaredType) type).getTypeArguments().isEmpty();
            case ARRAY:
                return isParameterizedType(((ArrayType) type).getComponentType());
            case INTERSECTION: {
                boolean res = false;
                for (TypeMirror t : ((IntersectionType) type).getBounds()) {
                    res |= isParameterizedType(t);
                }
                return res;
            }
            case TYPEVAR:
            case WILDCARD:
                return true;
            case UNION: {
                boolean res = false;
                for (TypeMirror t : ((UnionType) type).getAlternatives()) {
                    res |= isParameterizedType(t);
                }
                return res;
            }
            default:
                return false;
        }
    }

    void generateMarshallerFields(CodeBuilder builder, Modifier... modifiers) {
        for (MarshallerData marshaller : definitionData.getAllCustomMarshallers()) {
            Set<Modifier> modSet = EnumSet.noneOf(Modifier.class);
            Collections.addAll(modSet, modifiers);
            builder.lineStart().writeModifiers(modSet).space().parameterizedType(parser.typeCache.binaryMarshaller, marshaller.forType).space().write(marshaller.name).lineEnd(";");
        }
    }

    void generateCacheFields(CodeBuilder builder, HotSpotToNativeBridgeGenerator.CacheSnippet cacheSnippets) {
        for (AbstractBridgeParser.MethodData methodData : definitionData.toGenerate) {
            AbstractBridgeParser.CacheData cacheData = methodData.cachedData;
            if (cacheData != null) {
                Set<Modifier> modifiers = EnumSet.of(Modifier.PRIVATE);
                modifiers.addAll(cacheSnippets.modifiers(methodData.cachedData));
                builder.lineStart().writeModifiers(modifiers).space().write(cacheSnippets.entryType(builder, cacheData)).space().write(cacheData.cacheFieldName).lineEnd(";");
            }
        }
    }

    void generateCacheFieldsInit(CodeBuilder builder, CacheSnippet cacheSnippets) {
        for (AbstractBridgeParser.MethodData methodData : definitionData.toGenerate) {
            AbstractBridgeParser.CacheData cacheData = methodData.cachedData;
            if (cacheData != null) {
                CharSequence cacheFieldInit = cacheSnippets.initializeCacheField(builder, cacheData);
                if (cacheFieldInit != null) {
                    builder.lineStart("this.").write(cacheData.cacheFieldName).write(" = ").write(cacheFieldInit).lineEnd(";");
                }
            }
        }
    }

    void generateSizeEstimate(CodeBuilder builder, CharSequence targetVar, List<MarshalledParameter> marshalledParameters, boolean addReferenceArrayLength, boolean isOutParametersUpdate) {
        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(targetVar).write(" = ");
        boolean first = true;
        for (MarshalledParameter e : marshalledParameters) {
            if (!first) {
                builder.spaceIfNeeded().write("+").space();
            }
            int mark = builder.position();
            switch (e.marshallerData.kind) {
                case CUSTOM:
                    if (e.reserveSpace) {
                        // Reservation for custom types is hard to guess as we don't have an
                        // instance yet. It's highly probable that reallocation is needed anyway, we
                        // ignore it.
                    } else {
                        if (e.parameterType.getKind() == TypeKind.ARRAY) {
                            TypeMirror componentType = ((ArrayType) e.parameterType).getComponentType();
                            if (componentType.getKind() == TypeKind.ARRAY) {
                                // For multidimensional arrays it's impossible to infer size in a
                                // constant time, because any sub-array may be null.
                                // We rather use a constant estimate.
                                builder.write("128");
                            } else {
                                CharSequence firstElement = new CodeBuilder(builder).arrayElement(e.parameterName, "0").build();
                                builder.memberSelect(types.boxedClass(types.getPrimitiveType(TypeKind.INT)).asType(), "BYTES", false).write(" + ").write("(").write(e.parameterName).write(
                                                " != null && ").memberSelect(e.parameterName, "length", false).write(" > 0 ? ").memberSelect(e.parameterName, "length", false).write(" * ").invoke(
                                                                e.marshallerData.name, "inferSize", firstElement).write(" : 0").write(")");
                            }
                        } else {
                            CharSequence inferMethodName = isOutParametersUpdate && !e.isResult ? "inferUpdateSize" : "inferSize";
                            builder.invoke(e.marshallerData.name, inferMethodName, e.parameterName);
                        }
                    }
                    break;
                case REFERENCE:
                    if (e.parameterType.getKind() == TypeKind.ARRAY) {
                        if (e.reserveSpace) {
                            // Reservation for arrays is hard to guess as we don't have an instance
                            // yet. It's highly probable that reallocation is needed anyway, we
                            // ignore it.
                        } else {
                            CharSequence arrayLength = null;
                            if (e.marshallerData.in != null) {
                                arrayLength = e.marshallerData.in.lengthParameter;
                            } else if (e.marshallerData.out != null) {
                                arrayLength = e.marshallerData.out.lengthParameter;
                            }
                            if (arrayLength == null) {
                                arrayLength = new CodeBuilder(builder).memberSelect(e.parameterName, "length", false).build();
                            }
                            TypeMirror boxedLong = types.boxedClass(types.getPrimitiveType(TypeKind.LONG)).asType();
                            if (addReferenceArrayLength) {
                                TypeMirror boxedInt = types.boxedClass(types.getPrimitiveType(TypeKind.INT)).asType();
                                builder.memberSelect(boxedInt, "BYTES", false).write(" + ");
                            }
                            builder.write("(").write(e.parameterName).write(" != null ? ").write(arrayLength).write(" * ").memberSelect(boxedLong, "BYTES", false).write(" : 0").write(")");
                        }
                    } else {
                        generateSizeOf(builder, e.parameterName, types.getPrimitiveType(TypeKind.LONG), e.reserveSpace);
                    }
                    break;
                case VALUE:
                    generateSizeOf(builder, e.parameterName, e.parameterType, e.reserveSpace);
                    break;
                case RAW_REFERENCE:
                    generateSizeOf(builder, e.parameterName, types.getPrimitiveType(TypeKind.LONG), e.reserveSpace);
                    break;
                default:
                    throw new IllegalStateException(String.format("Unsupported marshaller %s of kind %s.", e.marshallerData.name, e.marshallerData.kind));
            }
            if (mark != builder.position()) {
                first = false;
            }
        }
        builder.lineEnd(";");
    }

    private void generateSizeOf(CodeBuilder builder, CharSequence variable, TypeMirror type, boolean reserveSpace) {
        switch (type.getKind()) {
            case BOOLEAN:
            case BYTE:
                builder.write("1");
                break;
            case SHORT:
            case CHAR:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
                builder.memberSelect(types.boxedClass((PrimitiveType) type).asType(), "BYTES", false);
                break;
            case ARRAY:
                if (reserveSpace) {
                    // Reservation for arrays is hard to guess as we don't have an instance yet.
                    // It's highly probable that reallocation is needed anyway, we ignore it.
                } else {
                    builder.memberSelect(variable, "length", false);
                    builder.write(" * ");
                    TypeMirror componentType = ((ArrayType) type).getComponentType();
                    if (componentType.getKind().isPrimitive()) {
                        generateSizeOf(builder, null, componentType, reserveSpace);
                    } else {
                        assert types.isSameType(typeCache.string, type);
                        builder.write("32");    // String array element size estimate
                    }
                }
                break;
            case DECLARED:
                if (reserveSpace) {
                    // Reservation for strings is hard to guess as we don't have an instance yet.
                    // It's highly probable that reallocation is needed anyway, we ignore it.
                } else {
                    assert types.isSameType(typeCache.string, type);
                    generateSizeOf(builder, null, types.getPrimitiveType(TypeKind.INT), reserveSpace);
                    builder.write(" + ");
                    builder.invoke(variable, "length");
                }
                break;
            default:
                throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    static CharSequence[] parameterNames(List<? extends CodeBuilder.Parameter> parameters) {
        return parameters.stream().map((p) -> p.name).toArray(CharSequence[]::new);
    }

    static boolean isBinaryMarshallable(MarshallerData marshaller, TypeMirror type, boolean hostToIsolate) {
        if (marshaller.isCustom()) {
            // Custom type is always marshalled
            return true;
        } else if (marshaller.isReference() && type.getKind() == TypeKind.ARRAY && hostToIsolate == marshaller.sameDirection) {
            // Arrays of NativeObject references (long handles) are marshalled
            return true;
        } else {
            return false;
        }
    }

    boolean isOutParameter(MarshallerData marshaller, TypeMirror type, boolean hostToIsolate) {
        return isBinaryMarshallable(marshaller, type, hostToIsolate) && marshaller.out != null;
    }

    abstract static class MarshallerSnippet {

        private final NativeBridgeProcessor processor;
        private final AbstractTypeCache cache;
        private final BinaryNameCache binaryNameCache;
        final MarshallerData marshallerData;
        final Types types;

        MarshallerSnippet(AbstractBridgeGenerator generator, MarshallerData marshallerData) {
            this.processor = generator.parser.processor;
            this.marshallerData = marshallerData;
            this.types = generator.types;
            this.cache = generator.typeCache;
            this.binaryNameCache = generator.binaryNameCache;
        }

        @SuppressWarnings("unused")
        Set<CharSequence> getEndPointSuppressedWarnings(CodeBuilder currentBuilder, TypeMirror type) {
            return Collections.emptySet();
        }

        abstract CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence marshalledParametersOutput,
                        CharSequence jniEnvFieldName);

        abstract CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput,
                        CharSequence jniEnvFieldName);

        @SuppressWarnings("unused")
        void declarePerMarshalledParameterVariable(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, Map<String, CharSequence> parameterValueOverrides) {
        }

        @SuppressWarnings("unused")
        boolean preMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput, CharSequence jniEnvFieldName,
                        Map<String, CharSequence> parameterValueOverrides) {
            return false;
        }

        @SuppressWarnings("unused")
        boolean preUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput, CharSequence jniEnvFieldName,
                        Map<String, CharSequence> parameterValueOverride) {
            return false;
        }

        @SuppressWarnings("unused")
        void postMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence receiver, CharSequence marshalledParametersInput,
                        CharSequence jniEnvFieldName, CharSequence resultVariableName) {
        }

        @SuppressWarnings("unused")
        void postUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName,
                        CharSequence resultVariableName) {
        }

        @SuppressWarnings("unused")
        CharSequence preMarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName) {
            return null;
        }

        @SuppressWarnings("unused")
        CharSequence storeRawResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
            return null;
        }

        @SuppressWarnings("unused")
        CharSequence preUnmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence marshalledResultInput,
                        CharSequence jniEnvFieldName) {
            return null;
        }

        abstract CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName);

        abstract CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence marshalledResultInput,
                        CharSequence jniEnvFieldName);

        abstract void write(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName);

        abstract void read(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence resultVariable, CharSequence marshalledResultInput, CharSequence jniEnvFieldName);

        static CharSequence outArrayLocal(CharSequence parameterName) {
            return parameterName + "Out";
        }

        final boolean isArrayWithDirectionModifiers(TypeMirror parameterType) {
            return parameterType.getKind() == TypeKind.ARRAY && (marshallerData.in != null || marshallerData.out != null);
        }

        final CharSequence unmarshallHotSpotToNativeProxyInNative(CodeBuilder builder, TypeMirror parameterType, CharSequence parameterName, DefinitionData data) {
            TypeMirror receiverType = marshallerData.useCustomReceiverAccessor ? data.customReceiverAccessor.getParameters().get(0).asType() : parameterType;
            CharSequence classLiteral = new CodeBuilder(builder).classLiteral(receiverType).build();
            CodeBuilder result = new CodeBuilder(builder).invokeStatic(cache.nativeObjectHandles, "resolve", parameterName, classLiteral);
            if (marshallerData.useCustomReceiverAccessor) {
                result = new CodeBuilder(result).invokeStatic(data.annotatedType, data.customReceiverAccessor.getSimpleName(), result.build());
            }
            return result.build();
        }

        final CharSequence unmarshallNativeToHotSpotProxyInNative(CodeBuilder builder, CharSequence parameterName, CharSequence jniEnvFieldName) {
            List<CharSequence> args = Arrays.asList(jniEnvFieldName, parameterName);
            boolean isHSObject = types.isSubtype(marshallerData.forType, cache.hSObject);
            if (marshallerData.hasGeneratedFactory && !isHSObject) {
                DeclaredType receiverType = (DeclaredType) marshallerData.nonDefaultReceiver.asType();
                List<CharSequence> newArgs = new ArrayList<>();
                newArgs.add(new CodeBuilder(builder).newInstance(receiverType, args.toArray(new CharSequence[0])).build());
                newArgs.add(jniEnvFieldName);
                args = newArgs;
            }
            CharSequence proxy = createProxy(builder, NativeToNativeBridgeGenerator.COMMON_START_POINT_FACTORY_NAME, NativeToHotSpotBridgeGenerator.START_POINT_FACTORY_NAME,
                            NativeToNativeBridgeGenerator.START_POINT_FACTORY_NAME, args);
            if (marshallerData.customDispatchFactory != null) {
                CodeBuilder factory = new CodeBuilder(builder).invokeStatic((DeclaredType) marshallerData.customDispatchFactory.getEnclosingElement().asType(),
                                marshallerData.customDispatchFactory.getSimpleName(), proxy);
                proxy = factory.build();
            }
            CodeBuilder result = new CodeBuilder(builder);
            result.invoke(parameterName, "isNonNull").write(" ? ").write(proxy).write(" : ").write("null");
            return result.build();
        }

        final CharSequence unmarshallHotSpotToNativeProxyInHotSpot(CodeBuilder builder, CharSequence parameterName, CharSequence currentIsolateSnippet) {
            List<CharSequence> args = Arrays.asList(currentIsolateSnippet, parameterName);
            boolean isNativeObject = types.isSubtype(marshallerData.forType, cache.nativeObject);
            if (marshallerData.hasGeneratedFactory && !isNativeObject) {
                args = Collections.singletonList(new CodeBuilder(builder).newInstance(cache.nativeObject, args.toArray(new CharSequence[0])).build());
            }
            CharSequence proxy = createProxy(builder, NativeToNativeBridgeGenerator.COMMON_START_POINT_FACTORY_NAME, HotSpotToNativeBridgeGenerator.START_POINT_FACTORY_NAME,
                            NativeToNativeBridgeGenerator.START_POINT_FACTORY_NAME, args);
            if (marshallerData.customDispatchFactory != null) {
                CodeBuilder factory = new CodeBuilder(builder).invokeStatic((DeclaredType) marshallerData.customDispatchFactory.getEnclosingElement().asType(),
                                marshallerData.customDispatchFactory.getSimpleName(), proxy);
                proxy = factory.build();
            }
            CodeBuilder result = new CodeBuilder(builder);
            result.write(parameterName).write(" != 0L ? ").write(proxy).write(" : ").write("null");
            return result.build();
        }

        final CharSequence unmarshallNativeToHotSpotProxyInHotSpot(CodeBuilder builder, TypeMirror parameterType, CharSequence parameterName, DefinitionData data) {
            TypeMirror receiverType = marshallerData.useCustomReceiverAccessor ? data.customReceiverAccessor.getParameters().get(0).asType() : parameterType;
            CharSequence result = parameterName;
            if (!types.isSubtype(parameterType, receiverType)) {
                result = new CodeBuilder(builder).cast(receiverType, parameterName).build();
            }
            if (marshallerData.useCustomReceiverAccessor) {
                result = new CodeBuilder(builder).invokeStatic(data.annotatedType, data.customReceiverAccessor.getSimpleName(), result).build();
            }
            return result;
        }

        final CharSequence lookupDirectSnippetWriteMethod(TypeMirror type) {
            if (types.isSameType(cache.string, type)) {
                return "writeUTF";
            } else if (type.getKind() == TypeKind.ARRAY) {
                return "write";
            } else {
                return switch (type.getKind()) {
                    case BOOLEAN -> "writeBoolean";
                    case BYTE -> "writeByte";
                    case CHAR -> "writeChar";
                    case SHORT -> "writeShort";
                    case INT -> "writeInt";
                    case LONG -> "writeLong";
                    case FLOAT -> "writeFloat";
                    case DOUBLE -> "writeDouble";
                    default -> throw new IllegalArgumentException("Unsupported kind " + type.getKind());
                };
            }
        }

        final CharSequence lookupDirectSnippetReadMethod(TypeMirror type) {
            if (types.isSameType(cache.string, type)) {
                return "readUTF";
            } else if (type.getKind() == TypeKind.ARRAY) {
                return "read";
            } else {
                return switch (type.getKind()) {
                    case BOOLEAN -> "readBoolean";
                    case BYTE -> "readByte";
                    case CHAR -> "readChar";
                    case SHORT -> "readShort";
                    case INT -> "readInt";
                    case LONG -> "readLong";
                    case FLOAT -> "readFloat";
                    case DOUBLE -> "readDouble";
                    default -> throw new IllegalArgumentException("Unsupported kind " + type.getKind());
                };
            }
        }

        final void writeCustomObject(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput) {
            if (parameterType.getKind() == TypeKind.ARRAY) {
                writeCustomObjectArray(currentBuilder, (ArrayType) parameterType, parameterName, marshalledParametersOutput);
            } else {
                currentBuilder.lineStart().invoke(marshallerData.name, "write", marshalledParametersOutput, parameterName).lineEnd(";");
            }
        }

        final void readCustomObject(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput) {
            if (parameterType.getKind() == TypeKind.ARRAY) {
                readCustomObjectArray(currentBuilder, (ArrayType) parameterType, parameterName, marshalledParametersInput);
            } else {
                currentBuilder.lineStart().write(parameterType).space().write(parameterName).write(" = ").invoke(marshallerData.name, "read", marshalledParametersInput).lineEnd(";");
            }
        }

        final void writeCustomObjectUpdate(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput) {
            if (parameterType.getKind() == TypeKind.ARRAY) {
                // For an array element, we cannot use BinaryInput#writeUpdatepdate, but we need to
                // re-read the whole element. Java array is a covariant type and the array
                // element may change its type.
                writeCustomObjectArray(currentBuilder, (ArrayType) parameterType, parameterName, marshalledParametersOutput);
            } else {
                currentBuilder.lineStart().invoke(marshallerData.name, "writeUpdate", marshalledParametersOutput, parameterName).lineEnd(";");
            }
        }

        final void readCustomObjectUpdate(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput, boolean inArray) {
            if (parameterType.getKind() == TypeKind.ARRAY) {
                updateCustomObjectArray(currentBuilder, (ArrayType) parameterType, parameterName, marshalledParametersInput);
            } else {
                if (inArray) {
                    // For an array element, we cannot use BinaryInput#writeUpdate, but we need to
                    // re-read the whole element. Java array is a covariant type and the array
                    // element may change its type.
                    currentBuilder.lineStart().write(parameterName).write(" = ").invoke(marshallerData.name, "read", marshalledParametersInput).lineEnd(";");
                } else {
                    currentBuilder.lineStart().invoke(marshallerData.name, "readUpdate", marshalledParametersInput, parameterName).lineEnd(";");
                }
            }
        }

        private void writeCustomObjectArray(CodeBuilder currentBuilder, ArrayType parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput) {
            currentBuilder.lineStart().write("if (").write(parameterName).write(" != null)").lineEnd(" {");
            currentBuilder.indent();
            CharSequence len = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
            currentBuilder.lineStart().invoke(marshalledParametersOutput, "writeInt", len).lineEnd(";");
            TypeMirror componentType = parameterType.getComponentType();
            CharSequence componentVariable = parameterName + "Element";
            currentBuilder.lineStart().forEachLoop(componentType, componentVariable, parameterName).lineEnd(" {");
            currentBuilder.indent();
            writeCustomObject(currentBuilder, componentType, componentVariable, marshalledParametersOutput);
            currentBuilder.dedent();
            currentBuilder.line("}");
            currentBuilder.dedent();
            currentBuilder.line("} else {");
            currentBuilder.indent();
            currentBuilder.lineStart().invoke(marshalledParametersOutput, "writeInt", "-1").lineEnd(";");
            currentBuilder.dedent();
            currentBuilder.line("}");
        }

        private void readCustomObjectArray(CodeBuilder currentBuilder, ArrayType parameterType, CharSequence parameterName, CharSequence marshalledParametersInput) {
            CharSequence len = parameterName + "Length";
            currentBuilder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(len).write(" = ").invoke(marshalledParametersInput, "readInt").lineEnd(";");
            currentBuilder.lineStart().write(parameterType).space().write(parameterName).lineEnd(";");
            currentBuilder.lineStart().write("if (").write(len).write(" != -1)").lineEnd(" {");
            currentBuilder.indent();
            TypeMirror componentType = parameterType.getComponentType();
            currentBuilder.lineStart(parameterName).write(" = ").newArray(componentType, len).lineEnd(";");
            CharSequence index = parameterName + "Index";
            List<CharSequence> init = List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(index).write(" = 0").build());
            CharSequence test = new CodeBuilder(currentBuilder).write(index).write(" < ").write(len).build();
            List<CharSequence> increment = List.of(new CodeBuilder(currentBuilder).write(index).write("++").build());
            currentBuilder.lineStart().forLoop(init, test, increment).lineEnd(" {");
            currentBuilder.indent();
            CharSequence componentVariable = parameterName + "Element";
            readCustomObject(currentBuilder, componentType, componentVariable, marshalledParametersInput);
            currentBuilder.lineStart().arrayElement(parameterName, index).write(" = ").write(componentVariable).lineEnd(";");
            currentBuilder.dedent();
            currentBuilder.line("}");
            currentBuilder.dedent();
            currentBuilder.line("} else {");
            currentBuilder.indent();
            currentBuilder.lineStart(parameterName).write(" = null").lineEnd(";");
            currentBuilder.dedent();
            currentBuilder.line("}");
        }

        private void updateCustomObjectArray(CodeBuilder currentBuilder, ArrayType parameterType, CharSequence parameterName, CharSequence marshalledParametersInput) {
            CharSequence len = parameterName + "Length";
            currentBuilder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(len).write(" = ").invoke(marshalledParametersInput, "readInt").lineEnd(";");
            currentBuilder.lineStart().write("if (").write(len).write(" != -1)").lineEnd(" {");
            currentBuilder.indent();
            CharSequence index = parameterName + "Index";
            List<CharSequence> init = List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(index).write(" = 0").build());
            CharSequence test = new CodeBuilder(currentBuilder).write(index).write(" < ").write(len).build();
            List<CharSequence> increment = List.of(new CodeBuilder(currentBuilder).write(index).write("++").build());
            currentBuilder.lineStart().forLoop(init, test, increment).lineEnd(" {");
            currentBuilder.indent();
            readCustomObjectUpdate(currentBuilder, parameterType.getComponentType(), new CodeBuilder(currentBuilder).arrayElement(parameterName, index).build(), marshalledParametersInput, true);
            currentBuilder.dedent();
            currentBuilder.line("}");
            currentBuilder.dedent();
            currentBuilder.line("}");
        }

        private CharSequence createProxy(CodeBuilder builder, CharSequence commonFactoryMethod, CharSequence jniFactoryMethod, CharSequence nativeFactoryMethod, List<CharSequence> args) {
            if (marshallerData.hasGeneratedFactory) {
                CharSequence type = new CodeBuilder(builder).write(types.erasure(marshallerData.forType)).write("Gen").build();
                CodeBuilder newInstanceBuilder = new CodeBuilder(builder);
                boolean hasJNI = hasJNIFactory(marshallerData);
                boolean hasNative = hasNativeFactory(marshallerData);
                if (hasJNI && hasNative) {
                    newInstanceBuilder.invoke(type, commonFactoryMethod, args.toArray(new CharSequence[0]));
                } else if (hasJNI) {
                    newInstanceBuilder.invoke(type, jniFactoryMethod, args.toArray(new CharSequence[0]));
                } else if (hasNative) {
                    newInstanceBuilder.invoke(type, nativeFactoryMethod, args.toArray(new CharSequence[0]));
                } else {
                    throw new IllegalStateException("Generated type must have JNI or Native start point.");
                }
                return newInstanceBuilder.build();
            } else {
                return new CodeBuilder(builder).newInstance((DeclaredType) types.erasure(marshallerData.forType),
                                args.toArray(new CharSequence[0])).build();
            }
        }

        private boolean hasJNIFactory(MarshallerData data) {
            Element element = ((DeclaredType) types.erasure(data.forType)).asElement();
            return processor.getAnnotation(element, cache.generateHSToNativeBridge) != null || processor.getAnnotation(element, cache.generateNativeToHSBridge) != null;
        }

        private boolean hasNativeFactory(MarshallerData data) {
            Element element = ((DeclaredType) types.erasure(data.forType)).asElement();
            return processor.getAnnotation(element, cache.generateNativeToNativeBridge) != null;
        }

        final CharSequence marshallHotSpotToNativeProxyInNative(CodeBuilder builder, CharSequence parameterName) {
            return new CodeBuilder(builder).invokeStatic(cache.nativeObjectHandles, "create", parameterName).build();
        }

        final CharSequence marshallNativeToHotSpotProxyInNative(CodeBuilder builder, CharSequence parameterName) {
            CodeBuilder receiver;
            if (types.isSubtype(marshallerData.forType, cache.hSObject)) {
                receiver = new CodeBuilder(builder).cast(cache.hSObject, parameterName, true);
            } else {
                CharSequence cast = new CodeBuilder(builder).cast(marshallerData.forType, parameterName).build();
                receiver = new CodeBuilder(builder).memberSelect(cast, marshallerData.nonDefaultReceiver.getSimpleName(), true);
            }
            return new CodeBuilder(builder).write(parameterName).write(" != null ? ").invoke(receiver.build(), "getHandle").write(" : ").invokeStatic(cache.wordFactory, "nullPointer").build();
        }

        final CharSequence marshallHotSpotToNativeProxyInHotSpot(CodeBuilder builder, CharSequence parameterName) {
            CodeBuilder receiver;
            if (types.isSubtype(marshallerData.forType, cache.nativeObject)) {
                receiver = new CodeBuilder(builder).write("((").write(cache.nativeObject).write(")").write(parameterName).write(")");
            } else {
                CharSequence cast = new CodeBuilder(builder).cast(marshallerData.forType, parameterName).build();
                receiver = new CodeBuilder(builder).memberSelect(cast, marshallerData.nonDefaultReceiver.getSimpleName(), true);
            }
            return new CodeBuilder(builder).write(parameterName).write(" != null ? ").invoke(receiver.build(), "getHandle").write(" : 0L").build();
        }

        final CharSequence marshallCopyHSObjectArrayToHotSpot(CodeBuilder currentBuilder, TypeMirror guestArrayComponentType, CharSequence guestArray,
                        CharSequence guestArrayOffsetSnippet, CharSequence guestArrayLengthSnippet, CharSequence jniEnvFieldName) {
            CharSequence componentTypeBinaryName = binaryNameCache.getCacheEntry(guestArrayComponentType);
            CharSequence useOffset = guestArrayOffsetSnippet != null ? guestArrayOffsetSnippet : "0";
            return new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "createHSArray", jniEnvFieldName, guestArray, useOffset, guestArrayLengthSnippet, componentTypeBinaryName).build();
        }

        final void generateAllocateJObjectArray(CodeBuilder currentBuilder, TypeMirror guestArrayComponentType, CharSequence guestArray,
                        CharSequence hsTargetArrayVariable, CharSequence guestArrayLengthSnippet, CharSequence jniEnvFieldName) {
            currentBuilder.lineStart("if (").write(guestArray).write(" != null) ").lineEnd("{");
            currentBuilder.indent();
            CharSequence nullptr = new CodeBuilder(currentBuilder).invokeStatic(cache.wordFactory, "nullPointer").build();
            CharSequence componentTypeBinaryName = binaryNameCache.getCacheEntry(guestArrayComponentType);
            CharSequence hsArrayComponentType = new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "findClass", jniEnvFieldName, nullptr, componentTypeBinaryName, "true").build();
            currentBuilder.lineStart(hsTargetArrayVariable).write(" = ").invokeStatic(cache.jniUtil, "NewObjectArray", jniEnvFieldName, guestArrayLengthSnippet, hsArrayComponentType, nullptr).lineEnd(
                            ";");
            currentBuilder.dedent();
            currentBuilder.line("} else {");
            currentBuilder.indent();
            currentBuilder.lineStart(hsTargetArrayVariable).write(" = ").write(nullptr).lineEnd(";");
            currentBuilder.dedent();
            currentBuilder.line("}");
        }

        final void generateCopyHotSpotToHSObjectArray(CodeBuilder currentBuilder, CharSequence guestTargetArrayVariable, CharSequence guestArrayOffsetSnippet, CharSequence guestArrayLengthSnippet,
                        CharSequence hsArray, CharSequence hsArrayOffsetSnippet, CharSequence jniEnvFieldName, Function<CharSequence, CharSequence> marshallFunction) {
            String arrayIndexVariable = guestTargetArrayVariable + "Index";
            currentBuilder.lineStart().forLoop(
                            List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(arrayIndexVariable).write(" = 0").build()),
                            new CodeBuilder(currentBuilder).write(arrayIndexVariable).write(" < ").write(guestArrayLengthSnippet).build(),
                            List.of(new CodeBuilder(currentBuilder).write(arrayIndexVariable).write("++").build())).lineEnd(" {");
            currentBuilder.indent();
            String arrayElementVariable = guestTargetArrayVariable + "Element";
            CharSequence sourceIndex = hsArrayOffsetSnippet == null ? arrayIndexVariable
                            : new CodeBuilder(currentBuilder).write(hsArrayOffsetSnippet).write(" + ").write(arrayIndexVariable).build();
            CharSequence sinkIndex = guestArrayOffsetSnippet == null ? arrayIndexVariable
                            : new CodeBuilder(currentBuilder).write(guestArrayOffsetSnippet).write(" + ").write(arrayIndexVariable).build();
            currentBuilder.lineStart().write(cache.jObject).space().write(arrayElementVariable).write(" = ").invokeStatic(cache.jniUtil, "GetObjectArrayElement", jniEnvFieldName, hsArray,
                            sourceIndex).lineEnd(";");
            currentBuilder.lineStart().arrayElement(guestTargetArrayVariable, sinkIndex).write(" = ").write(marshallFunction.apply(arrayElementVariable)).lineEnd(";");
            currentBuilder.dedent();
            currentBuilder.line("}");
        }

        final void generateWriteNativeObjectArray(CodeBuilder currentBuilder, CharSequence parameterName, boolean copyContent, boolean includeLength,
                        CharSequence arrayOffsetParameter, CharSequence arrayLengthSnippet, CharSequence outputVar,
                        Function<CharSequence, CharSequence> marshallFunction) {
            if (includeLength) {
                currentBuilder.lineStart().invoke(outputVar, "writeInt", arrayLengthSnippet).lineEnd(";");
            }
            if (copyContent) {
                CharSequence indexVariable = parameterName + "Index";
                currentBuilder.lineStart().forLoop(List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(indexVariable).write(" = 0").build()),
                                new CodeBuilder(currentBuilder).write(indexVariable).write(" < ").write(arrayLengthSnippet).build(),
                                List.of(new CodeBuilder(currentBuilder).write(indexVariable).write("++").build())).lineEnd(" {");
                currentBuilder.indent();
                CharSequence pos;
                if (arrayOffsetParameter != null) {
                    pos = new CodeBuilder(currentBuilder).write(arrayOffsetParameter).write(" + ").write(indexVariable).build();
                } else {
                    pos = indexVariable;
                }
                CharSequence parameterElement = new CodeBuilder(currentBuilder).arrayElement(parameterName, pos).build();
                currentBuilder.lineStart().invoke(outputVar, "writeLong",
                                marshallFunction.apply(parameterElement)).lineEnd(";");
                currentBuilder.dedent();
                currentBuilder.line("}");
            } else {
                // Pure out parameter. We don't need to copy the content, we only need to reserve
                // the space.
                CharSequence size = new CodeBuilder(currentBuilder).memberSelect(types.boxedClass(types.getPrimitiveType(TypeKind.LONG)).asType(), "BYTES", false).write(" * ").write(
                                arrayLengthSnippet).build();
                currentBuilder.lineStart().invoke(outputVar, "skip", size).lineEnd(";");
            }
        }

        final void generateReadNativeObjectArray(CodeBuilder currentBuilder, CharSequence parameterName,
                        CharSequence arrayOffsetParameter, CharSequence arrayLengthSnippet, CharSequence inputVar,
                        Function<CharSequence, CharSequence> marshallFunction) {
            CharSequence arrayIndexVariable = parameterName + "Index";
            currentBuilder.lineStart().forLoop(
                            List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(arrayIndexVariable).write(" = 0").build()),
                            new CodeBuilder(currentBuilder).write(arrayIndexVariable).write(" < ").write(arrayLengthSnippet).build(),
                            List.of(new CodeBuilder(currentBuilder).write(arrayIndexVariable).write("++").build())).lineEnd(" {");
            currentBuilder.indent();
            CharSequence handleVariable = parameterName + "Element";
            currentBuilder.lineStart().write(types.getPrimitiveType(TypeKind.LONG)).space().write(handleVariable).write(" = ").invoke(inputVar, "readLong").lineEnd(";");
            CharSequence arrayPosition;
            if (arrayOffsetParameter != null) {
                arrayPosition = new CodeBuilder(currentBuilder).write(arrayOffsetParameter).write(" + ").write(arrayIndexVariable).build();
            } else {
                arrayPosition = arrayIndexVariable;
            }
            currentBuilder.lineStart().arrayElement(parameterName, arrayPosition).write(" = ").write(marshallFunction.apply(handleVariable)).lineEnd(";");
            currentBuilder.dedent();
            currentBuilder.line("}");
        }
    }

    abstract static class CacheSnippet {

        final Types types;
        final AbstractTypeCache cache;

        CacheSnippet(Types type, AbstractTypeCache cache) {
            this.types = type;
            this.cache = cache;
        }

        abstract CharSequence entryType(CodeBuilder currentBuilder, AbstractBridgeParser.CacheData cacheData);

        abstract Set<Modifier> modifiers(AbstractBridgeParser.CacheData cacheData);

        abstract CharSequence initializeCacheField(CodeBuilder currentBuilder, AbstractBridgeParser.CacheData cacheData);

        abstract CharSequence readCache(CodeBuilder currentBuilder, CharSequence cacheField, CharSequence receiver);

        abstract CharSequence writeCache(CodeBuilder currentBuilder, CharSequence cacheField, CharSequence receiver, CharSequence value);

        static CacheSnippet standardDispatch(Types types, AbstractTypeCache cache) {
            return new StandardDispatch(types, cache);
        }

        static CacheSnippet customDispatch(Types types, AbstractTypeCache cache) {
            return new CustomDispatch(types, cache);
        }

        private static final class StandardDispatch extends CacheSnippet {

            StandardDispatch(Types types, AbstractTypeCache cache) {
                super(types, cache);
            }

            @Override
            CharSequence entryType(CodeBuilder currentBuilder, AbstractBridgeParser.CacheData cacheData) {
                return new CodeBuilder(currentBuilder).write(cacheData.cacheEntryType).build();
            }

            @Override
            Set<Modifier> modifiers(AbstractBridgeParser.CacheData cacheData) {
                return EnumSet.of(Modifier.VOLATILE);
            }

            @Override
            CharSequence initializeCacheField(CodeBuilder currentBuilder, AbstractBridgeParser.CacheData cacheData) {
                return null;    // No initialisation code is needed
            }

            @Override
            CharSequence readCache(CodeBuilder currentBuilder, CharSequence cacheField, CharSequence receiver) {
                return cacheField;
            }

            @Override
            CharSequence writeCache(CodeBuilder currentBuilder, CharSequence cacheField, CharSequence receiver, CharSequence value) {
                return new CodeBuilder(currentBuilder).write(cacheField).write(" = ").write(value).build();
            }
        }

        private static final class CustomDispatch extends CacheSnippet {

            CustomDispatch(Types type, AbstractTypeCache cache) {
                super(type, cache);
            }

            @Override
            CharSequence entryType(CodeBuilder currentBuilder, AbstractBridgeParser.CacheData cacheData) {
                return new CodeBuilder(currentBuilder).parameterizedType(cache.map, cache.object, cacheData.cacheEntryType).build();
            }

            @Override
            Set<Modifier> modifiers(AbstractBridgeParser.CacheData cacheData) {
                return EnumSet.of(Modifier.FINAL);
            }

            @Override
            CharSequence initializeCacheField(CodeBuilder currentBuilder, AbstractBridgeParser.CacheData cacheData) {
                CodeBuilder map = new CodeBuilder(currentBuilder).newInstance((DeclaredType) types.erasure(cache.weakHashMap),
                                Arrays.asList(cache.object, cacheData.cacheEntryType));
                return new CodeBuilder(currentBuilder).invokeStatic(cache.collections, "synchronizedMap", map.build()).build();
            }

            @Override
            CharSequence readCache(CodeBuilder currentBuilder, CharSequence cacheField, CharSequence receiver) {
                return new CodeBuilder(currentBuilder).invoke(cacheField, "get", receiver).build();
            }

            @Override
            CharSequence writeCache(CodeBuilder currentBuilder, CharSequence cacheField, CharSequence receiver, CharSequence value) {
                return new CodeBuilder(currentBuilder).invoke(cacheField, "put", receiver, value).build();
            }
        }
    }

    static final class FactoryMethodInfo {

        final CharSequence name;
        final CharSequence startPointSimpleName;
        final CharSequence endPointSimpleName;
        final List<CodeBuilder.Parameter> parameters;
        final List<CodeBuilder.Parameter> superCallParameters;

        FactoryMethodInfo(CharSequence name, CharSequence startPointSimpleName, CharSequence endPointSimpleName, List<CodeBuilder.Parameter> parameters,
                        List<CodeBuilder.Parameter> superCallParameters) {
            this.name = name;
            this.startPointSimpleName = startPointSimpleName;
            this.endPointSimpleName = endPointSimpleName;
            this.parameters = parameters;
            this.superCallParameters = superCallParameters;
        }
    }

    static final class MarshalledParameter {
        final boolean reserveSpace;
        final CharSequence parameterName;
        final TypeMirror parameterType;
        final boolean isResult;
        final MarshallerData marshallerData;

        MarshalledParameter(CharSequence parameterName, TypeMirror parameterType, boolean isResult, MarshallerData marshallerData) {
            this.reserveSpace = false;
            this.parameterName = parameterName;
            this.parameterType = parameterType;
            this.isResult = isResult;
            this.marshallerData = marshallerData;
        }

        MarshalledParameter(TypeMirror resultType, MarshallerData marshallerData) {
            this.reserveSpace = true;
            this.parameterName = null;
            this.parameterType = resultType;
            this.isResult = true;
            this.marshallerData = marshallerData;
        }
    }

    static final class PreUnmarshallResult {
        final CharSequence result;
        final CharSequence binaryInputResourcesToFree;
        final CharSequence binaryInput;
        final boolean unmarshalled;

        PreUnmarshallResult(CharSequence result, CharSequence binaryInputResourcesToFree, CharSequence binaryInput, boolean unmarshalled) {
            this.result = result;
            this.binaryInputResourcesToFree = binaryInputResourcesToFree;
            this.binaryInput = binaryInput;
            this.unmarshalled = unmarshalled;
        }
    }

    static final class BinaryNameCache {

        private final Types types;
        private final Elements elements;
        private final AbstractTypeCache typeCache;
        private final Map<TypeElement, String> cachedNameByType;

        private BinaryNameCache(Types types, Elements elements, AbstractTypeCache typeCache, Map<TypeElement, String> cachedNameByType) {
            this.types = types;
            this.elements = elements;
            this.typeCache = typeCache;
            this.cachedNameByType = cachedNameByType;
        }

        CharSequence getCacheEntry(TypeMirror type) {
            TypeMirror rawType = types.erasure(type);
            if (rawType.getKind() != TypeKind.DECLARED) {
                throw new IllegalArgumentException(rawType + " must be declared type");
            }
            TypeElement element = (TypeElement) ((DeclaredType) rawType).asElement();
            CharSequence result = cachedNameByType.get(element);
            if (result == null) {
                throw new IllegalArgumentException("No foreign reference array marshaller for " + element.getQualifiedName());
            }
            return result;
        }

        void generateCache(CodeBuilder builder) {
            Map<String, TypeElement> ordered = new TreeMap<>();
            for (Map.Entry<TypeElement, String> e : cachedNameByType.entrySet()) {
                ordered.put(e.getValue(), e.getKey());
            }
            for (Map.Entry<String, TypeElement> e : ordered.entrySet()) {
                String cacheEntryName = e.getKey();
                TypeElement typeElement = e.getValue();
                CharSequence cacheEntryValue = '"' + elements.getBinaryName(typeElement).toString().replace('.', '/') + '"';
                builder.lineStart().writeModifiers(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)).space().write(typeCache.string).space().write(cacheEntryName).write(" = ").write(
                                cacheEntryValue).lineEnd(";");
            }
        }

        static BinaryNameCache create(DefinitionData definitionData, boolean hostToIsolate, Types types, Elements elements, AbstractTypeCache typeCache) {
            Map<TypeElement, String> typeToCacheEntry = new HashMap<>();
            Map<String, TypeElement> simpleNameCacheEntryToType = new HashMap<>();
            for (DeclaredType type : findJObjectArrayComponentTypes(definitionData, hostToIsolate, types)) {
                TypeElement componentElement = (TypeElement) type.asElement();
                String cacheEntry = cacheEntryName(componentElement.getSimpleName());
                TypeElement existingElement = simpleNameCacheEntryToType.get(cacheEntry);
                if (existingElement != null && !existingElement.equals(componentElement)) {
                    // Simple name already used, switch to fqn.
                    typeToCacheEntry.put(existingElement, cacheEntryName(existingElement.getQualifiedName()));
                    cacheEntry = cacheEntryName(componentElement.getQualifiedName());
                    typeToCacheEntry.put(componentElement, cacheEntry);
                } else {
                    // Use simple name
                    typeToCacheEntry.put(componentElement, cacheEntry);
                    simpleNameCacheEntryToType.put(cacheEntry, componentElement);
                }
            }
            return new BinaryNameCache(types, elements, typeCache, typeToCacheEntry);
        }

        private static Collection<? extends DeclaredType> findJObjectArrayComponentTypes(DefinitionData definitionData, boolean hostToIsolate, Types types) {
            Collection<DeclaredType> result = new ArrayList<>();
            for (MethodData method : definitionData.toGenerate) {
                if (isReferenceArray(method.getReturnTypeMarshaller(), method.type.getReturnType(), hostToIsolate)) {
                    result.add(getComponentType(method.type.getReturnType(), types));
                }
                List<? extends TypeMirror> parameterTypes = method.type.getParameterTypes();
                for (int i = 0; i < parameterTypes.size(); i++) {
                    TypeMirror parameterType = parameterTypes.get(i);
                    if (isReferenceArray(method.getParameterMarshaller(i), parameterType, hostToIsolate)) {
                        result.add(getComponentType(parameterType, types));
                    }
                }
            }
            return result;
        }

        private static boolean isReferenceArray(MarshallerData marshallerData, TypeMirror type, boolean hostToIsolate) {
            return marshallerData.isReference() && hostToIsolate != marshallerData.sameDirection && type.getKind() == TypeKind.ARRAY;
        }

        private static DeclaredType getComponentType(TypeMirror arrayType, Types types) {
            return (DeclaredType) types.erasure(((ArrayType) arrayType).getComponentType());
        }

        private static String cacheEntryName(CharSequence name) {
            return name.toString().replace('.', '_').toUpperCase(Locale.ROOT) + "_BINARY_NAME";
        }
    }
}
