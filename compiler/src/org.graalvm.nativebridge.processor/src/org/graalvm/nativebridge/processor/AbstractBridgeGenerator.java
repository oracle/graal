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
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.UnionType;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    AbstractBridgeGenerator(AbstractBridgeParser parser, DefinitionData definitionData) {
        this.parser = parser;
        this.definitionData = definitionData;
        this.types = parser.types;
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

    abstract MarshallerSnippets marshallerSnippets(MarshallerData marshallerData);

    void configureMultipleDefinitions(@SuppressWarnings("unused") List<DefinitionData> otherDefinitions) {
    }

    static int getStaticBufferSize(int marshalledParametersCount, boolean marshalledResult) {
        int slots = marshalledParametersCount != 0 ? marshalledParametersCount : marshalledResult ? 1 : 0;
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
                params.add(new CodeBuilder(builder).typeLiteral(marshaller.forType).build());
            }
            for (AnnotationMirror annotationType : marshaller.annotations) {
                params.add(new CodeBuilder(builder).classLiteral(annotationType.getAnnotationType()).build());
            }
            builder.lineStart().write(marshaller.name).write(" = ");
            builder.invoke("config", "lookupMarshaller", params.toArray(new CharSequence[0])).lineEnd(";");
        }
    }

    final CacheSnippets cacheSnippets() {
        if (definitionData.hasCustomDispatch()) {
            return CacheSnippets.customDispatch(types, parser.typeCache);
        } else {
            return CacheSnippets.standardDispatch(types, parser.typeCache);
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
                        CodeBuilder.newParameters(methodElement.getParameters(), methodType.getParameterTypes()),
                        methodType.getThrownTypes());
        return builder;
    }

    static TypeMirror jniTypeForJavaType(TypeMirror javaType, Types types, AbstractTypeCache cache) {
        if (javaType.getKind().isPrimitive() || javaType.getKind() == TypeKind.VOID) {
            return javaType;
        }
        TypeMirror erasedType = types.erasure(javaType);
        switch (erasedType.getKind()) {
            case DECLARED:
                if (types.isSameType(cache.string, javaType)) {
                    return cache.jString;
                } else if (types.isSameType(cache.clazz, javaType)) {
                    return cache.jClass;
                } else if (types.isSubtype(javaType, cache.throwable)) {
                    return cache.jThrowable;
                } else {
                    return cache.jObject;
                }
            case ARRAY:
                TypeMirror componentType = ((ArrayType) erasedType).getComponentType();
                switch (componentType.getKind()) {
                    case BOOLEAN:
                        return cache.jBooleanArray;
                    case BYTE:
                        return cache.jByteArray;
                    case CHAR:
                        return cache.jCharArray;
                    case SHORT:
                        return cache.jShortArray;
                    case INT:
                        return cache.jIntArray;
                    case LONG:
                        return cache.jLongArray;
                    case FLOAT:
                        return cache.jFloatArray;
                    case DOUBLE:
                        return cache.jDoubleArray;
                    default:
                        throw new UnsupportedOperationException("Not supported for array of " + componentType.getKind());
                }
            default:
                throw new UnsupportedOperationException("Not supported for " + javaType.getKind());
        }
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

    void generateCacheFields(CodeBuilder builder, HotSpotToNativeBridgeGenerator.CacheSnippets cacheSnippets) {
        for (AbstractBridgeParser.MethodData methodData : definitionData.toGenerate) {
            AbstractBridgeParser.CacheData cacheData = methodData.cachedData;
            if (cacheData != null) {
                Set<Modifier> modifiers = EnumSet.of(Modifier.PRIVATE);
                modifiers.addAll(cacheSnippets.modifiers(methodData.cachedData));
                builder.lineStart().writeModifiers(modifiers).space().write(cacheSnippets.entryType(builder, cacheData)).space().write(cacheData.cacheFieldName).lineEnd(";");
            }
        }
    }

    void generateCacheFieldsInit(CodeBuilder builder, CacheSnippets cacheSnippets) {
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

    void generateSizeEstimate(CodeBuilder builder, CharSequence targetVar, List<Map.Entry<MarshallerData, CharSequence>> customMarshallers) {
        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(targetVar).write(" = ");
        boolean first = true;
        for (Map.Entry<MarshallerData, CharSequence> e : customMarshallers) {
            if (first) {
                first = false;
            } else {
                builder.spaceIfNeeded().write("+").space();
            }
            builder.invoke(e.getKey().name, "inferSize", e.getValue());
        }
        builder.lineEnd(";");
    }

    static CharSequence[] parameterNames(List<? extends CodeBuilder.Parameter> parameters) {
        return parameters.stream().map((p) -> p.name).toArray(CharSequence[]::new);
    }

    abstract static class MarshallerSnippets {

        private final NativeBridgeProcessor processor;
        private final AbstractTypeCache cache;
        final MarshallerData marshallerData;
        final Types types;

        MarshallerSnippets(NativeBridgeProcessor processor, MarshallerData marshallerData, Types types, AbstractTypeCache cache) {
            this.processor = processor;
            this.marshallerData = marshallerData;
            this.types = types;
            this.cache = cache;
        }

        @SuppressWarnings("unused")
        Set<CharSequence> getEndPointSuppressedWarnings(CodeBuilder currentBuilder, TypeMirror type) {
            return Collections.emptySet();
        }

        abstract TypeMirror getEndPointMethodParameterType(TypeMirror type);

        abstract CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence marshalledParametersOutput,
                        CharSequence jniEnvFieldName);

        abstract CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersInput,
                        CharSequence jniEnvFieldName);

        @SuppressWarnings("unused")
        boolean preMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName,
                        Map<String, CharSequence> parameterValueOverrides) {
            return false;
        }

        @SuppressWarnings("unused")
        boolean preUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName,
                        Map<String, CharSequence> parameterValueOverride) {
            return false;
        }

        @SuppressWarnings("unused")
        void postMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName, CharSequence resultVariableName) {
        }

        @SuppressWarnings("unused")
        void postUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence jniEnvFieldName, CharSequence resultVariableName) {
        }

        @SuppressWarnings("unused")
        CharSequence preMarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence jniEnvFieldName) {
            return null;
        }

        @SuppressWarnings("unused")
        CharSequence preUnmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence jniEnvFieldName) {
            return null;
        }

        abstract CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName);

        abstract CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence receiver, CharSequence marshalledResultInput,
                        CharSequence jniEnvFieldName);

        static CharSequence outArrayLocal(CharSequence parameterName) {
            return parameterName + "Out";
        }

        final boolean isArrayWithDirectionModifiers(TypeMirror parameterType) {
            return parameterType.getKind() == TypeKind.ARRAY && !marshallerData.annotations.isEmpty();
        }

        final AnnotationMirror findIn(List<? extends AnnotationMirror> annotations) {
            return find(annotations, cache.in);
        }

        final AnnotationMirror findOut(List<? extends AnnotationMirror> annotations) {
            return find(annotations, cache.out);
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
            boolean hasGeneratedFactory = !marshallerData.annotations.isEmpty();
            boolean isHSObject = types.isSubtype(marshallerData.forType, cache.hSObject);
            if (hasGeneratedFactory && !isHSObject) {
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
            boolean hasGeneratedFactory = !marshallerData.annotations.isEmpty();
            boolean isNativeObject = types.isSubtype(marshallerData.forType, cache.nativeObject);
            if (hasGeneratedFactory && !isNativeObject) {
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

        private CharSequence createProxy(CodeBuilder builder, CharSequence commonFactoryMethod, CharSequence jniFactoryMethod, CharSequence nativeFactoryMethod, List<CharSequence> args) {
            boolean hasGeneratedFactory = !marshallerData.annotations.isEmpty();
            if (hasGeneratedFactory) {
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

        static boolean trimToResult(AnnotationMirror annotation) {
            Boolean value = (Boolean) AbstractBridgeParser.getAnnotationValue(annotation, "trimToResult");
            return value != null && value;
        }

        static CharSequence getArrayLengthParameterName(AnnotationMirror annotation) {
            return (CharSequence) AbstractBridgeParser.getAnnotationValue(annotation, "arrayLengthParameter");
        }

        static CharSequence getArrayOffsetParameterName(AnnotationMirror annotation) {
            return (CharSequence) AbstractBridgeParser.getAnnotationValue(annotation, "arrayOffsetParameter");
        }

        private AnnotationMirror find(List<? extends AnnotationMirror> annotations, DeclaredType requiredAnnotation) {
            for (AnnotationMirror annotation : annotations) {
                if (types.isSameType(annotation.getAnnotationType(), requiredAnnotation)) {
                    return annotation;
                }
            }
            return null;
        }
    }

    abstract static class CacheSnippets {

        final Types types;
        final AbstractTypeCache cache;

        CacheSnippets(Types type, AbstractTypeCache cache) {
            this.types = type;
            this.cache = cache;
        }

        abstract CharSequence entryType(CodeBuilder currentBuilder, AbstractBridgeParser.CacheData cacheData);

        abstract Set<Modifier> modifiers(AbstractBridgeParser.CacheData cacheData);

        abstract CharSequence initializeCacheField(CodeBuilder currentBuilder, AbstractBridgeParser.CacheData cacheData);

        abstract CharSequence readCache(CodeBuilder currentBuilder, CharSequence cacheField, CharSequence receiver);

        abstract CharSequence writeCache(CodeBuilder currentBuilder, CharSequence cacheField, CharSequence receiver, CharSequence value);

        static CacheSnippets standardDispatch(Types types, AbstractTypeCache cache) {
            return new StandardDispatch(types, cache);
        }

        static CacheSnippets customDispatch(Types types, AbstractTypeCache cache) {
            return new CustomDispatch(types, cache);
        }

        private static final class StandardDispatch extends CacheSnippets {

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

        private static final class CustomDispatch extends CacheSnippets {

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
}
