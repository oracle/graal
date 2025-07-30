/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.graalvm.nativebridge.processor.AbstractBridgeParser.AbstractTypeCache;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MarshallerData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.ServiceDefinitionData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.MethodData;
import org.graalvm.nativebridge.processor.AbstractNativeServiceParser.NativeTypeCache;

abstract class AbstractNativeServiceGenerator extends AbstractServiceGenerator {

    static final String MARSHALLED_DATA_PARAMETER = "marshalledData";
    private static final int BYTES_PER_PARAMETER = 256;

    final BinaryNameCache binaryNameCache;
    private final NativeTypeCache typeCache;

    AbstractNativeServiceGenerator(AbstractServiceParser parser, NativeTypeCache typeCache, ServiceDefinitionData definitionData, BinaryNameCache binaryNameCache) {
        super(parser, typeCache, definitionData);
        this.binaryNameCache = binaryNameCache;
        this.typeCache = typeCache;
    }

    abstract MarshallerSnippet marshallerSnippets(MarshallerData marshallerData);

    static int getStaticBufferSize(int marshalledParametersCount, boolean marshalledResult, boolean hasOutParameter) {
        int slots = marshalledParametersCount != 0 ? marshalledParametersCount : marshalledResult ? 1 : 0;
        if (hasOutParameter) {
            slots++;
        }
        return slots * BYTES_PER_PARAMETER;
    }

    static boolean isBinaryMarshallable(MarshallerData marshaller, TypeMirror type, boolean hostToIsolate) {
        if (marshaller.isCustom()) {
            // Custom type is always marshalled
            return true;
        } else if (marshaller.isReference() && type.getKind() == TypeKind.ARRAY && hostToIsolate == marshaller.sameDirection) {
            // Arrays of NativePeer references (long handles) are marshalled
            return true;
        } else {
            return false;
        }
    }

    boolean isOutParameter(MarshallerData marshaller, TypeMirror type, boolean hostToIsolate) {
        return isBinaryMarshallable(marshaller, type, hostToIsolate) && marshaller.out != null;
    }

    abstract static class MarshallerSnippet extends AbstractMarshallerSnippet {

        private final NativeTypeCache cache;
        private final BinaryNameCache binaryNameCache;

        MarshallerSnippet(AbstractNativeServiceGenerator generator, MarshallerData marshallerData) {
            super(marshallerData, generator.types, generator.typeCache);
            this.cache = generator.typeCache;
            this.binaryNameCache = generator.binaryNameCache;
        }

        @SuppressWarnings("unused")
        Set<CharSequence> getEndPointSuppressedWarnings(CodeBuilder currentBuilder, TypeMirror type) {
            return Collections.emptySet();
        }

        abstract CharSequence marshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence formalParameter, CharSequence marshalledParametersOutput,
                        CharSequence jniEnvFieldName);

        abstract CharSequence unmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
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
        boolean preUnmarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                        CharSequence jniEnvFieldName,
                        Map<String, CharSequence> parameterValueOverride) {
            return false;
        }

        @SuppressWarnings("unused")
        void postMarshallParameter(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
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
        CharSequence preUnmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence isolateVar, CharSequence marshalledResultInput,
                        CharSequence jniEnvFieldName) {
            return null;
        }

        abstract CharSequence marshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName);

        abstract CharSequence unmarshallResult(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence isolateVar,
                        CharSequence marshalledResultInput, CharSequence jniEnvFieldName);

        abstract void write(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence invocationSnippet, CharSequence marshalledResultOutput, CharSequence jniEnvFieldName);

        abstract void read(CodeBuilder currentBuilder, TypeMirror resultType, CharSequence resultVariable, CharSequence isolateVar, CharSequence marshalledResultInput, CharSequence jniEnvFieldName);

        static CharSequence outArrayLocal(CharSequence parameterName) {
            return parameterName + "Out";
        }

        final boolean isArrayWithDirectionModifiers(TypeMirror parameterType) {
            return parameterType.getKind() == TypeKind.ARRAY && (marshallerData.in != null || marshallerData.out != null);
        }

        final CharSequence unmarshallHotSpotToNativeProxyInNative(CodeBuilder builder, TypeMirror parameterType, CharSequence parameterName, ServiceDefinitionData data) {
            TypeMirror receiverType = marshallerData.useCustomReceiverAccessor ? data.customReceiverAccessor.getParameters().get(0).asType() : parameterType;
            CharSequence classLiteral = new CodeBuilder(builder).classLiteral(receiverType).build();
            CodeBuilder result = new CodeBuilder(builder).invokeStatic(cache.referenceHandles, "resolve", parameterName, classLiteral);
            if (marshallerData.useCustomReceiverAccessor) {
                result = new CodeBuilder(result).invokeStatic(data.annotatedType, data.customReceiverAccessor.getSimpleName(), result.build());
            }
            return result.build();
        }

        final CharSequence unmarshallNativeToHotSpotProxyInNative(CodeBuilder builder, CharSequence parameterName, CharSequence jniEnvFieldName) {
            List<CharSequence> args = Arrays.asList(jniEnvFieldName, parameterName);
            CharSequence proxy;
            CharSequence peer = new CodeBuilder(builder).invokeStatic(cache.peer, "create", args.toArray(new CharSequence[0])).build();
            if (marshallerData.customDispatchFactory != null) {
                CharSequence foreignObject = new CodeBuilder(builder).invokeStatic(cache.foreignObject, "createUnbound", peer).build();
                proxy = new CodeBuilder(builder).invokeStatic((DeclaredType) marshallerData.customDispatchFactory.getEnclosingElement().asType(),
                                marshallerData.customDispatchFactory.getSimpleName(), foreignObject).build();
            } else {
                proxy = createProxy(builder, FACTORY_METHOD_NAME, List.of(peer, jniEnvFieldName));
            }
            CodeBuilder result = new CodeBuilder(builder);
            result.invoke(parameterName, "isNonNull").write(" ? ").write(proxy).write(" : ").write("null");
            return result.build();
        }

        final CharSequence unmarshallHotSpotToNativeProxyInHotSpot(CodeBuilder builder, CharSequence parameterName, CharSequence currentIsolateSnippet) {
            List<CharSequence> args = Arrays.asList(currentIsolateSnippet, parameterName);
            CharSequence proxy;
            CharSequence peer = new CodeBuilder(builder).invokeStatic(cache.peer, "create", args.toArray(new CharSequence[0])).build();
            if (marshallerData.customDispatchFactory != null) {
                CharSequence foreignObject = new CodeBuilder(builder).invokeStatic(cache.foreignObject, "createUnbound", peer).build();
                proxy = new CodeBuilder(builder).invokeStatic((DeclaredType) marshallerData.customDispatchFactory.getEnclosingElement().asType(),
                                marshallerData.customDispatchFactory.getSimpleName(), foreignObject).build();
            } else {
                proxy = createProxy(builder, FACTORY_METHOD_NAME, List.of(peer));
            }
            CodeBuilder result = new CodeBuilder(builder);
            result.write(parameterName).write(" != 0L ? ").write(proxy).write(" : ").write("null");
            return result.build();
        }

        final CharSequence unmarshallNativeToHotSpotProxyInHotSpot(CodeBuilder builder, TypeMirror parameterType, CharSequence parameterName, ServiceDefinitionData data) {
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

        private CharSequence createProxy(CodeBuilder builder, CharSequence factoryMethod, List<CharSequence> args) {
            if (types.isSameType(marshallerData.forType, cache.foreignObject)) {
                return new CodeBuilder(builder).invokeStatic(cache.foreignObject, "createUnbound", args.get(0)).build();
            } else {
                CharSequence type = new CodeBuilder(builder).write(types.erasure(marshallerData.forType)).write("Gen").build();
                CodeBuilder newInstanceBuilder = new CodeBuilder(builder);
                newInstanceBuilder.invoke(type, factoryMethod, args.toArray(new CharSequence[0]));
                return newInstanceBuilder.build();
            }
        }

        final CharSequence marshallHotSpotToNativeProxyInNative(CodeBuilder builder, CharSequence parameterName) {
            return new CodeBuilder(builder).invokeStatic(cache.referenceHandles, "create", parameterName).build();
        }

        final CharSequence marshallNativeToHotSpotProxyInNative(CodeBuilder builder, CharSequence parameterName) {
            CodeBuilder foreignObject = new CodeBuilder(builder).cast(cache.foreignObject, parameterName, true);
            CodeBuilder peer = new CodeBuilder(builder).invoke(foreignObject.build(), "getPeer");
            CodeBuilder hsPeer = new CodeBuilder(builder).cast(cache.hSPeer, peer.build(), true);
            return new CodeBuilder(builder).write(parameterName).write(" != null ? ").invoke(hsPeer.build(), "getJObject").write(" : ").invokeStatic(cache.wordFactory, "nullPointer").build();
        }

        final CharSequence marshallHotSpotToNativeProxyInHotSpot(CodeBuilder builder, CharSequence parameterName) {
            CodeBuilder foreignObject = new CodeBuilder(builder).cast(cache.foreignObject, parameterName, true);
            CodeBuilder peer = new CodeBuilder(builder).invoke(foreignObject.build(), "getPeer");
            return new CodeBuilder(builder).write(parameterName).write(" != null ? ").invoke(peer.build(), "getHandle").write(" : 0L").build();
        }

        final void generateCopyHSPeerArrayToHotSpot(CodeBuilder currentBuilder, CharSequence hsTargetArrayVariable, TypeMirror guestArrayComponentType,
                        CharSequence guestArray, CharSequence guestArrayOffsetSnippet, CharSequence guestArrayLengthSnippet,
                        CharSequence jniEnvFieldName) {
            CharSequence componentTypeBinaryName = binaryNameCache.getCacheEntry(guestArrayComponentType);
            currentBuilder.lineStart("if (").write(guestArray).write(" != null) ").lineEnd("{");
            currentBuilder.indent();
            CharSequence nullptr = new CodeBuilder(currentBuilder).invokeStatic(cache.wordFactory, "nullPointer").build();
            CharSequence jclazz = new CodeBuilder(currentBuilder).invokeStatic(cache.jniUtil, "findClass", jniEnvFieldName, nullptr, componentTypeBinaryName, "true").build();
            currentBuilder.lineStart(hsTargetArrayVariable).write(" = ").invokeStatic(cache.jniUtil, "NewObjectArray",
                            jniEnvFieldName, guestArrayLengthSnippet, jclazz, nullptr).lineEnd(";");

            CharSequence indexVariable = hsTargetArrayVariable + "Index";
            CharSequence elementVariable = guestArray + "Element";
            currentBuilder.lineStart().forLoop(List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(indexVariable).write(" = 0").build()),
                            new CodeBuilder(currentBuilder).write(indexVariable).write(" < ").write(guestArrayLengthSnippet).build(),
                            List.of(new CodeBuilder(currentBuilder).write(indexVariable).write("++").build())).lineEnd(" {");
            currentBuilder.indent();
            CodeBuilder index = new CodeBuilder(currentBuilder);
            if (guestArrayOffsetSnippet != null) {
                index.write(guestArrayOffsetSnippet).write(" + ");
            }
            index.write(indexVariable);
            CharSequence readArrayElement = new CodeBuilder(currentBuilder).arrayElement(guestArray, index.build()).build();
            currentBuilder.lineStart().write(cache.foreignObject).space().write(elementVariable).write(" = ").cast(cache.foreignObject, readArrayElement).lineEnd(";");
            CharSequence peer = new CodeBuilder(currentBuilder).invoke(elementVariable, "getPeer").build();
            CharSequence hsPeer = new CodeBuilder(currentBuilder).cast(cache.hSPeer, peer, true).build();
            CharSequence jobject = new CodeBuilder(currentBuilder).invoke(hsPeer, "getJObject").build();
            CharSequence element = new CodeBuilder(currentBuilder).write(elementVariable).write(" != null ? ").write(jobject).write(" : ").write(nullptr).build();
            currentBuilder.lineStart().invokeStatic(cache.jniUtil, "SetObjectArrayElement", jniEnvFieldName, hsTargetArrayVariable, indexVariable, element).lineEnd(";");
            currentBuilder.dedent();
            currentBuilder.line("}");
            currentBuilder.dedent();
            currentBuilder.line("} else {");
            currentBuilder.indent();
            currentBuilder.lineStart(hsTargetArrayVariable).write(" = ").write(nullptr).lineEnd(";");
            currentBuilder.dedent();
            currentBuilder.line("}");
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

        final void generateCopyHotSpotToHSPeerArray(CodeBuilder currentBuilder, CharSequence guestTargetArrayVariable, CharSequence guestArrayOffsetSnippet, CharSequence guestArrayLengthSnippet,
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

        static BinaryNameCache create(ServiceDefinitionData definitionData, boolean hostToIsolate, Types types, Elements elements, AbstractTypeCache typeCache) {
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

        private static Collection<? extends DeclaredType> findJObjectArrayComponentTypes(ServiceDefinitionData definitionData, boolean hostToIsolate, Types types) {
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
