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

import org.graalvm.nativebridge.processor.AbstractBridgeParser.AbstractTypeCache;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.MarshallerData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.CacheData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.ServiceDefinitionData;
import org.graalvm.nativebridge.processor.AbstractServiceParser.MethodData;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

abstract class AbstractServiceGenerator extends AbstractBridgeGenerator {

    static final String PEER_FIELD = "peer";

    final Types types;

    AbstractServiceGenerator(AbstractServiceParser parser, AbstractTypeCache typeCache, ServiceDefinitionData definitionData) {
        super(parser, definitionData, typeCache);
        this.types = parser.types;
    }

    @Override
    AbstractServiceParser getParser() {
        return (AbstractServiceParser) super.getParser();
    }

    @Override
    ServiceDefinitionData getDefinition() {
        return (ServiceDefinitionData) super.getDefinition();
    }

    boolean supportsCommonFactory() {
        return true;
    }

    void generateCommonFactoryReturn(CodeBuilder builder, List<CharSequence> parameters) {
        builder.lineStart("return ").invoke(null, AbstractBridgeGenerator.FACTORY_METHOD_NAME, parameters.toArray(new CharSequence[0])).lineEnd(";");
    }

    abstract void generateCommonCustomDispatchFactoryReturn(CodeBuilder builder);

    final FactoryMethodInfo resolveFactoryMethod(CharSequence factoryMethodName, CharSequence startPointSimpleName, CharSequence endPointSimpleName,
                    CodeBuilder.Parameter... additionalRequiredParameters) {
        List<CodeBuilder.Parameter> superParameters = new ArrayList<>(getDefinition().annotatedTypeConstructorParams.size());
        List<CodeBuilder.Parameter> constructorParameters = new ArrayList<>(getDefinition().annotatedTypeConstructorParams.size() + 1 + additionalRequiredParameters.length);
        List<CodeBuilder.Parameter> factoryMethodParameters = new ArrayList<>(getDefinition().annotatedTypeConstructorParams.size() + 1 + additionalRequiredParameters.length);
        List<CodeBuilder.Parameter> requiredList = new ArrayList<>();
        Collections.addAll(requiredList, additionalRequiredParameters);
        for (VariableElement ve : getDefinition().annotatedTypeConstructorParams) {
            TypeMirror parameterType = ve.asType();
            CodeBuilder.Parameter parameter = CodeBuilder.newParameter(parameterType, ve.getSimpleName());
            requiredList.removeIf((required) -> types.isSameType(required.type, parameterType));
            factoryMethodParameters.add(parameter);
            constructorParameters.add(parameter);
            superParameters.add(parameter);
        }
        if (!getDefinition().hasCustomDispatch()) {
            CodeBuilder.Parameter peer = CodeBuilder.newParameter(getDefinition().peerType, PEER_FIELD);
            factoryMethodParameters.add(peer);
            constructorParameters.add(peer);
            requiredList.removeIf((required) -> types.isSameType(required.type, getDefinition().peerType));
        }
        factoryMethodParameters.addAll(requiredList);
        constructorParameters.addAll(requiredList);
        return new FactoryMethodInfo(factoryMethodName, startPointSimpleName, endPointSimpleName, factoryMethodParameters, constructorParameters, superParameters);
    }

    final void generateStartPointFactory(CodeBuilder builder, FactoryMethodInfo factoryMethod) {
        assert !getDefinition().hasCustomDispatch() : "Should never be reached with custom dispatch.";
        builder.methodStart(EnumSet.of(Modifier.STATIC), factoryMethod.name, getDefinition().annotatedType,
                        factoryMethod.factoryMethodParameters, Collections.emptyList());
        builder.indent();
        builder.lineStart("return ").newInstance(factoryMethod.startPointSimpleName, parameterNames(factoryMethod.constructorParameters)).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    final CacheSnippet cacheSnippets() {
        if (getDefinition().hasCustomDispatch()) {
            return CacheSnippet.customDispatch(types, getTypeCache());
        } else {
            return CacheSnippet.standardDispatch(types, getTypeCache());
        }
    }

    boolean generateMarshallerFields(CodeBuilder builder, boolean allMarshallers, Modifier... modifiers) {
        boolean modified = false;
        Collection<MarshallerData> customMarshallers = allMarshallers ? getDefinition().getAllCustomMarshallers() : getDefinition().getUserCustomMarshallers();
        for (MarshallerData marshaller : customMarshallers) {
            Set<Modifier> modSet = EnumSet.noneOf(Modifier.class);
            Collections.addAll(modSet, modifiers);
            builder.lineStart().writeModifiers(modSet).space().parameterizedType(getTypeCache().binaryMarshaller, marshaller.forType).space().write(marshaller.name).lineEnd(";");
            modified = true;
        }
        return modified;
    }

    void generatePeerField(CodeBuilder builder, DeclaredType type, CharSequence name) {
        Set<Modifier> modifiers = EnumSet.of(Modifier.PRIVATE);
        if (getDefinition().mutable) {
            modifiers.add(Modifier.VOLATILE);
        } else {
            modifiers.add(Modifier.FINAL);
        }
        builder.lineStart().writeModifiers(modifiers).space().write(type).space().write(name).lineEnd(";");
    }

    void generateGetPeerMethod(CodeBuilder builder, DeclaredType type, CharSequence fieldName) {
        Set<Modifier> modifiers = EnumSet.of(Modifier.PUBLIC);
        builder.lineStart().annotation(getTypeCache().override, null).lineEnd("");
        builder.methodStart(modifiers, "getPeer", type, List.of(), List.of());
        builder.indent();
        builder.lineStart("return").space().write(fieldName).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    void generateSetPeerMethod(CodeBuilder builder, DeclaredType type, CharSequence fieldName) {
        List<ExecutableElement> foreignObjectMethods = ElementFilter.methodsIn(getTypeCache().foreignObject.asElement().getEnclosedElements());
        Optional<ExecutableElement> hasSetPeer = foreignObjectMethods.stream().filter((e) -> "setPeer".contentEquals(e.getSimpleName())).findAny();
        if (hasSetPeer.isEmpty()) {
            throw new IllegalStateException(String.format("Incompatible `%s` change. Missing `setPeer(Peer)` method.", Utilities.getTypeName(getTypeCache().foreignObject)));
        }
        ExecutableElement setPeer = hasSetPeer.get();
        overrideMethod(builder, setPeer, (ExecutableType) setPeer.asType());
        builder.indent();
        if (getDefinition().mutable) {
            CharSequence parameter = setPeer.getParameters().get(0).getSimpleName();
            builder.lineStart("if (").write(parameter).write(" == null || ").write(parameter).write(" instanceof ").write(type).lineEnd(") {");
            builder.indent();
            builder.lineStart(fieldName).write(" = ").cast(type, parameter).lineEnd(";");
            builder.dedent();
            builder.line("} else {");
            builder.indent();
            CharSequence message = String.format("\"Invalid new peer type. Expected `%s`, actual new peer `%%s`.\"",
                            Utilities.getTypeName(type));
            CharSequence messageBuilder = new CodeBuilder(builder).invokeStatic(getTypeCache().string, "format", message, parameter).build();
            builder.lineStart("throw ").newInstance(getTypeCache().illegalArgumentException, messageBuilder).lineEnd(";");
            builder.dedent();
            builder.line("}");
        } else {
            CharSequence message = String.format("\"The definition class `%s` does not support mutable peer. To enable it, annotate it with `@%s`.\"",
                            Utilities.getTypeName(getDefinition().annotatedType),
                            Utilities.getTypeName(getTypeCache().mutablePeer));
            builder.lineStart("throw ").newInstance(getTypeCache().unsupportedOperationException, message).lineEnd(";");
        }
        builder.dedent();
        builder.line("}");
    }

    boolean generateCacheFields(CodeBuilder builder, CacheSnippet cacheSnippets) {
        boolean modified = false;
        for (MethodData methodData : getDefinition().toGenerate) {
            CacheData cacheData = methodData.cachedData;
            if (cacheData != null) {
                Set<Modifier> modifiers = EnumSet.of(Modifier.PRIVATE);
                modifiers.addAll(cacheSnippets.modifiers(methodData.cachedData));
                builder.lineStart().writeModifiers(modifiers).space().write(cacheSnippets.entryType(builder, cacheData)).space().write(cacheData.cacheFieldName).lineEnd(";");
                modified = true;
            }
        }
        return modified;
    }

    void generateCacheFieldsInit(CodeBuilder builder, CacheSnippet cacheSnippets) {
        for (MethodData methodData : getDefinition().toGenerate) {
            CacheData cacheData = methodData.cachedData;
            if (cacheData != null) {
                CharSequence cacheFieldInit = cacheSnippets.initializeCacheField(builder, cacheData);
                if (cacheFieldInit != null) {
                    builder.lineStart("this.").write(cacheData.cacheFieldName).write(" = ").write(cacheFieldInit).lineEnd(";");
                }
            }
        }
    }

    final void generateMarshallerLookups(CodeBuilder builder, boolean allMarshallers) {
        Collection<MarshallerData> customMarshallers = allMarshallers ? getDefinition().getAllCustomMarshallers() : getDefinition().getUserCustomMarshallers();
        for (MarshallerData marshaller : customMarshallers) {
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
            builder.invoke("marshallerConfig", "lookupMarshaller", params.toArray(new CharSequence[0])).lineEnd(";");
        }
    }

    final CodeBuilder overrideMethod(CodeBuilder builder, MethodData methodData) {
        for (AnnotationMirror mirror : methodData.element.getAnnotationMirrors()) {
            if (!Utilities.contains(getDefinition().ignoreAnnotations, mirror.getAnnotationType(), types) &&
                            !Utilities.contains(getDefinition().marshallerAnnotations, mirror.getAnnotationType(), types)) {
                builder.lineStart().annotation(mirror.getAnnotationType(), null).lineEnd("");
            }
        }
        return overrideMethod(builder, methodData.element, methodData.type);
    }

    final CodeBuilder overrideMethod(CodeBuilder builder, ExecutableElement methodElement, ExecutableType methodType) {
        builder.lineStart().annotation(getTypeCache().override, null).lineEnd("");
        Set<Modifier> newModifiers;
        if (methodElement.getModifiers().isEmpty()) {
            newModifiers = EnumSet.noneOf(Modifier.class);
        } else {
            newModifiers = EnumSet.copyOf(methodElement.getModifiers());
            newModifiers.remove(Modifier.ABSTRACT);
        }
        builder.methodStart(newModifiers, methodElement.getSimpleName(),
                        methodType.getReturnType(),
                        CodeBuilder.newParameters(methodElement.getParameters(), methodType.getParameterTypes(), methodElement.isVarArgs()),
                        methodType.getThrownTypes());
        return builder;
    }

    final void generateSizeEstimate(CodeBuilder builder, CharSequence targetVar, List<MarshalledParameter> marshalledParameters, boolean addReferenceArrayLength, boolean isOutParametersUpdate) {
        builder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(targetVar).write(" = ");
        boolean first = true;
        for (AbstractNativeServiceGenerator.MarshalledParameter e : marshalledParameters) {
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
                        generateSizeOf(builder, e.parameterName, types.getPrimitiveType(TypeKind.LONG), e.reserveSpace, e.marshallerData);
                    }
                    break;
                case VALUE:
                    generateSizeOf(builder, e.parameterName, e.parameterType, e.reserveSpace, e.marshallerData);
                    break;
                case PEER_REFERENCE:
                    generateSizeOf(builder, e.parameterName, types.getPrimitiveType(TypeKind.LONG), e.reserveSpace, e.marshallerData);
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

    static CharSequence resolveLength(AbstractServiceParser.DirectionData directionData) {
        return directionData.lengthParameter != null && !directionData.lengthParameter.isEmpty() ? directionData.lengthParameter : null;
    }

    static CharSequence resolveOffset(AbstractServiceParser.DirectionData directionData) {
        return directionData.offsetParameter != null && !directionData.offsetParameter.isEmpty() ? directionData.offsetParameter : null;
    }

    private void generateSizeOf(CodeBuilder builder, CharSequence variable, TypeMirror type, boolean reserveSpace, MarshallerData marshallerData) {
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
                    var explicitLength = Optional.ofNullable(marshallerData).//
                                    map((md) -> md.in != null ? md.in : md.out).//
                                    map((dd) -> dd.lengthParameter).//
                                    map((l) -> l.isEmpty() ? null : l);
                    if (explicitLength.isPresent()) {
                        // Array content, length is passes as other method formal parameter, we need
                        // a byte for null check
                        generateSizeOf(builder, null, types.getPrimitiveType(TypeKind.BOOLEAN), reserveSpace, null);
                        builder.write(" + ");
                        builder.write(explicitLength.get());
                    } else {
                        // Array length
                        generateSizeOf(builder, null, types.getPrimitiveType(TypeKind.INT), reserveSpace, null);
                        builder.write(" + ");
                        builder.write("(");
                        builder.write(variable).write(" != null ? ");
                        // Array content
                        builder.memberSelect(variable, "length", false);
                        // `0` for null array
                        builder.write(" : 0");
                        builder.write(")");
                    }
                    builder.write(" * ");
                    TypeMirror componentType = ((ArrayType) type).getComponentType();
                    if (componentType.getKind().isPrimitive()) {
                        generateSizeOf(builder, null, componentType, reserveSpace, null);
                    } else {
                        assert types.isSameType(getTypeCache().string, type);
                        builder.write("32");    // String array element size estimate
                    }
                }
                break;
            case DECLARED:
                if (reserveSpace) {
                    // Reservation for strings is hard to guess as we don't have an instance yet.
                    // It's highly probable that reallocation is needed anyway, we ignore it.
                } else {
                    assert types.isSameType(getTypeCache().string, type);
                    generateSizeOf(builder, null, types.getPrimitiveType(TypeKind.BYTE), reserveSpace, null);
                    builder.write(" + ");
                    generateSizeOf(builder, null, types.getPrimitiveType(TypeKind.INT), reserveSpace, null);
                    builder.write(" + ");
                    builder.write("(");
                    builder.write(variable).write(" != null ? ");
                    // string content
                    builder.invoke(variable, "length");
                    // `0` for null array
                    builder.write(" : 0");
                    builder.write(")");
                }
                break;
            default:
                throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    private CodeBuilder typeLiteral(CodeBuilder builder, TypeMirror type) {
        return builder.newInstance((DeclaredType) builder.types.erasure(getTypeCache().typeLiteral), Collections.singletonList(type)).write("{}");
    }

    static CharSequence[] parameterNames(List<? extends CodeBuilder.Parameter> parameters) {
        return parameters.stream().map((p) -> p.name).toArray(CharSequence[]::new);
    }

    static final class FactoryMethodInfo {

        final CharSequence name;
        final CharSequence startPointSimpleName;
        final CharSequence endPointSimpleName;
        final List<CodeBuilder.Parameter> factoryMethodParameters;
        final List<CodeBuilder.Parameter> constructorParameters;
        final List<CodeBuilder.Parameter> superCallParameters;

        FactoryMethodInfo(CharSequence name, CharSequence startPointSimpleName, CharSequence endPointSimpleName,
                        List<CodeBuilder.Parameter> factoryMethodParameters,
                        List<CodeBuilder.Parameter> constructorParameters,
                        List<CodeBuilder.Parameter> superCallParameters) {
            this.name = name;
            this.startPointSimpleName = startPointSimpleName;
            this.endPointSimpleName = endPointSimpleName;
            this.factoryMethodParameters = factoryMethodParameters;
            this.constructorParameters = constructorParameters;
            this.superCallParameters = superCallParameters;
        }
    }

    abstract static class CacheSnippet {

        final Types types;
        final AbstractTypeCache cache;

        CacheSnippet(Types type, AbstractTypeCache cache) {
            this.types = type;
            this.cache = cache;
        }

        abstract CharSequence entryType(CodeBuilder currentBuilder, CacheData cacheData);

        abstract Set<Modifier> modifiers(CacheData cacheData);

        abstract CharSequence initializeCacheField(CodeBuilder currentBuilder, CacheData cacheData);

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
            CharSequence entryType(CodeBuilder currentBuilder, CacheData cacheData) {
                return new CodeBuilder(currentBuilder).write(cacheData.cacheEntryType).build();
            }

            @Override
            Set<Modifier> modifiers(CacheData cacheData) {
                return EnumSet.of(Modifier.VOLATILE);
            }

            @Override
            CharSequence initializeCacheField(CodeBuilder currentBuilder, CacheData cacheData) {
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
            CharSequence entryType(CodeBuilder currentBuilder, CacheData cacheData) {
                return new CodeBuilder(currentBuilder).parameterizedType(cache.map, cache.object, cacheData.cacheEntryType).build();
            }

            @Override
            Set<Modifier> modifiers(CacheData cacheData) {
                return EnumSet.of(Modifier.FINAL);
            }

            @Override
            CharSequence initializeCacheField(CodeBuilder currentBuilder, CacheData cacheData) {
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

    enum WriteArrayPolicy {
        STORE,
        RESERVE,
        IGNORE
    }

    abstract static class AbstractMarshallerSnippet {

        final MarshallerData marshallerData;
        final Types types;
        private final AbstractTypeCache cache;

        AbstractMarshallerSnippet(MarshallerData marshallerData, Types types, AbstractTypeCache cache) {
            this.marshallerData = marshallerData;
            this.types = types;
            this.cache = cache;
        }

        final CharSequence lookupDirectSnippetWriteMethod(TypeMirror type) {
            if (types.isSameType(cache.string, type)) {
                return "writeString";
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
                    default -> throw unsupported(type);
                };
            }
        }

        final CharSequence lookupDirectSnippetReadMethod(TypeMirror type) {
            if (types.isSameType(cache.string, type)) {
                return "readString";
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
                    default -> throw unsupported(type);
                };
            }
        }

        CharSequence updateLengthSnippet(CodeBuilder builder, CharSequence formalParameter, CharSequence resultVar) {
            CharSequence length;
            if (marshallerData.out.trimToResult) {
                length = resultVar;
            } else {
                length = resolveLength(marshallerData.out);
                if (length == null) {
                    length = new CodeBuilder(builder).memberSelect(formalParameter, "length", false).build();
                }
            }
            return length;
        }

        final void writeCustomObject(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput) {
            if (parameterType.getKind() == TypeKind.ARRAY) {
                currentBuilder.lineStart().write("if (").write(parameterName).write(" != null)").lineEnd(" {");
                currentBuilder.indent();
                CharSequence len = new CodeBuilder(currentBuilder).memberSelect(parameterName, "length", false).build();
                currentBuilder.lineStart().invoke(marshalledParametersOutput, "writeInt", len).lineEnd(";");
                writeCustomObjectArray(currentBuilder, (ArrayType) parameterType, parameterName, marshalledParametersOutput);
                currentBuilder.dedent();
                currentBuilder.line("} else {");
                currentBuilder.indent();
                currentBuilder.lineStart().invoke(marshalledParametersOutput, "writeInt", "-1").lineEnd(";");
                currentBuilder.dedent();
                currentBuilder.line("}");
            } else {
                currentBuilder.lineStart().invoke(marshallerData.name, "write", marshalledParametersOutput, parameterName).lineEnd(";");
            }
        }

        final void readCustomObject(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                        boolean needsDeclaration) {
            if (parameterType.getKind() == TypeKind.ARRAY) {
                readCustomObjectArray(currentBuilder, (ArrayType) parameterType, parameterName, isolateVar, marshalledParametersInput, needsDeclaration);
            } else {
                currentBuilder.lineStart();
                if (needsDeclaration) {
                    currentBuilder.write(parameterType).space();
                }
                currentBuilder.write(parameterName).write(" = ").invoke(marshallerData.name, "read", isolateVar, marshalledParametersInput).lineEnd(";");
            }
        }

        final void writeCustomObjectUpdate(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput) {
            if (parameterType.getKind() == TypeKind.ARRAY) {
                // For an array element, we cannot use BinaryInput#writeUpdatepdate, but we need to
                // re-read the whole element. Java array is a covariant type and the array
                // element may change its type.
                currentBuilder.lineStart("if (").write(parameterName).write(" != null) ").lineEnd("{");
                currentBuilder.indent();
                writeCustomObjectArray(currentBuilder, (ArrayType) parameterType, parameterName, marshalledParametersOutput);
                currentBuilder.dedent();
                currentBuilder.line("}");

            } else {
                currentBuilder.lineStart().invoke(marshallerData.name, "writeUpdate", marshalledParametersOutput, parameterName).lineEnd(";");
            }
        }

        final void readCustomObjectUpdate(CodeBuilder currentBuilder, TypeMirror parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                        CharSequence offsetVar, CharSequence lengthVar, boolean inArray) {
            if (parameterType.getKind() == TypeKind.ARRAY) {
                currentBuilder.lineStart("if (").write(parameterName).write(" != null) ").lineEnd("{");
                currentBuilder.indent();
                updateCustomObjectArray(currentBuilder, (ArrayType) parameterType, parameterName, isolateVar, marshalledParametersInput, offsetVar, lengthVar);
                currentBuilder.dedent();
                currentBuilder.line("}");
            } else {
                if (inArray) {
                    // For an array element, we cannot use BinaryInput#writeUpdate, but we need to
                    // re-read the whole element. Java array is a covariant type and the array
                    // element may change its type.
                    currentBuilder.lineStart().write(parameterName).write(" = ").invoke(marshallerData.name, "read", isolateVar, marshalledParametersInput).lineEnd(";");
                } else {
                    currentBuilder.lineStart().invoke(marshallerData.name, "readUpdate", isolateVar, marshalledParametersInput, parameterName).lineEnd(";");
                }
            }
        }

        final void generateReadReferenceArray(CodeBuilder currentBuilder, CharSequence parameterName,
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

        final void generateWriteReferenceArray(CodeBuilder currentBuilder, CharSequence parameterName, WriteArrayPolicy writeArrayPolicy, boolean includeLength,
                        CharSequence arrayOffsetParameter, CharSequence arrayLengthSnippet, CharSequence outputVar,
                        Function<CharSequence, CharSequence> marshallFunction) {
            if (includeLength) {
                currentBuilder.lineStart().invoke(outputVar, "writeInt", arrayLengthSnippet).lineEnd(";");
            }
            if (writeArrayPolicy == WriteArrayPolicy.STORE) {
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
                if (writeArrayPolicy == WriteArrayPolicy.RESERVE) {
                    currentBuilder.lineStart().invoke(outputVar, "skip", size).lineEnd(";");
                }
            }
        }

        private void writeCustomObjectArray(CodeBuilder currentBuilder, ArrayType parameterType, CharSequence parameterName, CharSequence marshalledParametersOutput) {
            TypeMirror componentType = parameterType.getComponentType();
            CharSequence componentVariable = parameterName + "Element";
            currentBuilder.lineStart().forEachLoop(componentType, componentVariable, parameterName).lineEnd(" {");
            currentBuilder.indent();
            writeCustomObject(currentBuilder, componentType, componentVariable, marshalledParametersOutput);
            currentBuilder.dedent();
            currentBuilder.line("}");
        }

        private void readCustomObjectArray(CodeBuilder currentBuilder, ArrayType parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                        boolean needsDeclaration) {
            CharSequence len = parameterName + "Length";
            currentBuilder.lineStart().write(types.getPrimitiveType(TypeKind.INT)).space().write(len).write(" = ").invoke(marshalledParametersInput, "readInt").lineEnd(";");
            if (needsDeclaration) {
                currentBuilder.lineStart().write(parameterType).space().write(parameterName).lineEnd(";");
            }
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
            readCustomObject(currentBuilder, componentType, componentVariable, isolateVar, marshalledParametersInput, true);
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

        private void updateCustomObjectArray(CodeBuilder currentBuilder, ArrayType parameterType, CharSequence parameterName, CharSequence isolateVar, CharSequence marshalledParametersInput,
                        CharSequence offsetVar,
                        CharSequence lengthVar) {
            CharSequence index = parameterName + "Index";
            List<CharSequence> init = List.of(new CodeBuilder(currentBuilder).write(types.getPrimitiveType(TypeKind.INT)).space().write(index).write(" = 0").build());
            CharSequence test = new CodeBuilder(currentBuilder).write(index).write(" < ").write(lengthVar).build();
            List<CharSequence> increment = List.of(new CodeBuilder(currentBuilder).write(index).write("++").build());
            currentBuilder.lineStart().forLoop(init, test, increment).lineEnd(" {");
            currentBuilder.indent();
            CharSequence select = offsetVar != null ? new CodeBuilder(currentBuilder).write(offsetVar).write(" + ").write(index).build() : index;
            CharSequence arrayElement = new CodeBuilder(currentBuilder).arrayElement(parameterName, select).build();
            readCustomObjectUpdate(currentBuilder, parameterType.getComponentType(), arrayElement, isolateVar, marshalledParametersInput, null, null, true);
            currentBuilder.dedent();
            currentBuilder.line("}");
        }

        static RuntimeException unsupported(TypeMirror type) {
            throw new IllegalArgumentException("Unsupported value type " + Utilities.getTypeName(type));
        }

    }
}
