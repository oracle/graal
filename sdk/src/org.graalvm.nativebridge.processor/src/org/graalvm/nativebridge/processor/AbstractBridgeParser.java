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

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

abstract class AbstractBridgeParser {

    final NativeBridgeProcessor processor;
    final Elements elements;
    final Types types;
    private final AbstractTypeCache typeCache;
    final DeclaredType handledAnnotationType;

    private boolean hasLocalErrors;

    AbstractBridgeParser(NativeBridgeProcessor processor, AbstractTypeCache typeCache, DeclaredType handledAnnotationType) {
        this.processor = processor;
        this.types = processor.typeUtils();
        this.elements = processor.env().getElementUtils();
        this.typeCache = Objects.requireNonNull(typeCache, "TypeCache must be non-null");
        this.handledAnnotationType = Objects.requireNonNull(handledAnnotationType, "HandledAnnotation must be non-null");
    }

    final DefinitionData parse(Element element) {
        hasLocalErrors = false;
        checkAnnotatedType(element);
        if (hasLocalErrors) {
            return null;
        }
        DefinitionData definitionData = parseElement(element);
        return hasLocalErrors ? null : definitionData;
    }

    abstract DefinitionData parseElement(Element element);

    abstract AbstractBridgeGenerator createGenerator(DefinitionData definitionData);

    final void emitError(Element element, AnnotationMirror mirror, String format, Object... params) {
        hasLocalErrors = true;
        processor.emitError(element, mirror, format, params);
    }

    final boolean hasErrors() {
        return hasLocalErrors;
    }

    AbstractTypeCache getTypeCache() {
        return typeCache;
    }

    static Object getAnnotationValue(AnnotationMirror mirror, String elementName) {
        ExecutableElement element = resolveElement(mirror, elementName);
        if (element == null) {
            throw new IllegalArgumentException("The " + elementName + " is not a valid annotation attribute.");
        }
        AnnotationValue annotationValue = mirror.getElementValues().get(element);
        return annotationValue == null ? null : annotationValue.getValue();
    }

    static Object getOptionalAnnotationValue(AnnotationMirror mirror, String elementName) {
        ExecutableElement element = resolveElement(mirror, elementName);
        if (element != null) {
            AnnotationValue annotationValue = mirror.getElementValues().get(element);
            return annotationValue == null ? null : annotationValue.getValue();
        } else {
            return null;
        }
    }

    Object getAnnotationValueWithDefaults(AnnotationMirror mirror, String elementName) {
        ExecutableElement element = resolveElement(mirror, elementName);
        if (element == null) {
            throw new IllegalArgumentException("The " + elementName + " is not a valid annotation attribute.");
        }
        AnnotationValue annotationValue = elements.getElementValuesWithDefaults(mirror).get(element);
        return annotationValue == null ? null : annotationValue.getValue();
    }

    static ExecutableElement resolveElement(AnnotationMirror mirror, String elementName) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(mirror.getAnnotationType().asElement().getEnclosedElements());
        for (ExecutableElement method : methods) {
            if (method.getSimpleName().contentEquals(elementName)) {
                return method;
            }
        }
        return null;
    }

    static class DefinitionData {

        final DeclaredType annotatedType;
        final MarshallerData throwableMarshaller;

        DefinitionData(DeclaredType annotatedType, MarshallerData throwableMarshaller) {
            this.annotatedType = Objects.requireNonNull(annotatedType, "AnnotatedType must be non-null");
            this.throwableMarshaller = Objects.requireNonNull(throwableMarshaller, "ThrowableMarshaller must be non-null");
        }

        Iterable<? extends Element> getVerifiedElements() {
            return List.of(annotatedType.asElement());
        }
    }

    static final class MarshallerData {

        enum Kind {
            VALUE,
            REFERENCE,
            PEER_REFERENCE,
            CUSTOM,
        }

        static final MarshallerData NO_MARSHALLER = new MarshallerData(Kind.VALUE, null, null, false, true, null, null, null, null, null);

        final Kind kind;
        final TypeMirror forType;
        final AbstractServiceParser.DirectionData in;
        final AbstractServiceParser.DirectionData out;
        final List<? extends AnnotationMirror> annotations; // only for CUSTOM
        final String name;                              // only for CUSTOM
        final boolean useCustomReceiverAccessor;        // only for REFERENCE
        final boolean sameDirection;                    // only for REFERENCE
        final VariableElement nonDefaultReceiver;       // only for REFERENCE
        final ExecutableElement customDispatchFactory;  // only for REFERENCE

        private MarshallerData(Kind kind, TypeMirror forType, String name,
                        boolean useCustomReceiverAccessor, boolean sameDirection, AbstractServiceParser.DirectionData in, AbstractServiceParser.DirectionData out,
                        List<? extends AnnotationMirror> annotations, VariableElement nonDefaultReceiver, ExecutableElement customDispatchFactory) {
            this.kind = kind;
            this.forType = forType;
            this.name = name;
            this.useCustomReceiverAccessor = useCustomReceiverAccessor;
            this.sameDirection = sameDirection;
            this.in = in;
            this.out = out;
            this.annotations = annotations;
            this.nonDefaultReceiver = nonDefaultReceiver;
            this.customDispatchFactory = customDispatchFactory;
        }

        boolean isCustom() {
            return kind == Kind.CUSTOM;
        }

        boolean isReference() {
            return kind == Kind.REFERENCE;
        }

        boolean isValue() {
            return kind == Kind.VALUE;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MarshallerData that = (MarshallerData) o;
            return kind == that.kind && Objects.equals(forType, that.forType) && Objects.equals(name, that.name) && Objects.equals(annotations, that.annotations);
        }

        @Override
        public int hashCode() {
            return Objects.hash(forType, name);
        }

        static MarshallerData annotatedPrimitiveArray(AbstractServiceParser.DirectionData in, AbstractServiceParser.DirectionData out) {
            return in == null && out == null ? NO_MARSHALLER : new MarshallerData(Kind.VALUE, null, null, false, true, in, out, Collections.emptyList(), null, null);
        }

        static MarshallerData marshalled(TypeMirror forType, AbstractServiceParser.DirectionData in, AbstractServiceParser.DirectionData out, List<? extends AnnotationMirror> annotations) {
            TypeMirror useType = forType;
            while (useType.getKind() == TypeKind.ARRAY) {
                useType = ((ArrayType) useType).getComponentType();
            }
            String name = marshallerName(useType, annotations);
            return new MarshallerData(Kind.CUSTOM, useType, name, false, true, in, out, annotations, null, null);
        }

        static MarshallerData reference(DeclaredType startPointType, boolean useCustomReceiverAccessor,
                        boolean sameDirection, VariableElement nonDefaultReceiver, ExecutableElement customDispatchFactory) {
            return new MarshallerData(Kind.REFERENCE, startPointType, null, useCustomReceiverAccessor,
                            sameDirection, null, null, Collections.emptyList(), nonDefaultReceiver, customDispatchFactory);
        }

        static MarshallerData referenceArray(DeclaredType startPointType, AbstractServiceParser.DirectionData in, AbstractServiceParser.DirectionData out,
                        boolean useCustomReceiverAccessor, boolean sameDirection, VariableElement nonDefaultReceiver, ExecutableElement customDispatchFactory) {
            return new MarshallerData(Kind.REFERENCE, startPointType, null, useCustomReceiverAccessor,
                            sameDirection, in, out, Collections.emptyList(), nonDefaultReceiver, customDispatchFactory);
        }

        static MarshallerData peerReference(boolean sameDirection) {
            return new MarshallerData(Kind.PEER_REFERENCE, null, null, false, sameDirection, null, null, null, null, null);
        }
    }

    abstract static class AbstractTypeCache {
        final DeclaredType alwaysByLocalReference;
        final DeclaredType alwaysByLocalReferenceRepeated;
        final DeclaredType alwaysByRemoteReference;
        final DeclaredType alwaysByRemoteReferenceRepeated;
        final DeclaredType assertionError;
        final DeclaredType binaryMarshaller;
        final DeclaredType binaryInput;
        final DeclaredType binaryOutput;
        final DeclaredType boxedLong;
        final DeclaredType byLocalReference;
        final DeclaredType byRemoteReference;
        final DeclaredType byteArrayBinaryOutput;
        final DeclaredType clazz;
        final DeclaredType collections;
        final DeclaredType customDispatchAccessor;
        final DeclaredType customDispatchFactory;
        final DeclaredType customReceiverAccessor;
        final DeclaredType error;
        final DeclaredType expectError;
        final DeclaredType foreignException;
        final DeclaredType foreignObject;
        final DeclaredType idempotent;
        final DeclaredType illegalArgumentException;
        final DeclaredType illegalStateException;
        final DeclaredType in;
        final DeclaredType isolate;
        final DeclaredType isolateCreateException;
        final DeclaredType isolateDeathException;
        final DeclaredType isolateDeathHandler;
        final DeclaredType list;
        final DeclaredType map;
        final DeclaredType math;
        final DeclaredType marshallerAnnotation;
        final DeclaredType marshallerConfig;
        final DeclaredType mutablePeer;
        final DeclaredType noImplementation;
        final DeclaredType object;
        final DeclaredType objects;
        final DeclaredType out;
        final DeclaredType override;
        final DeclaredType peer;
        final DeclaredType receiverMethod;
        final DeclaredType referenceHandles;
        final DeclaredType runtimeException;
        final DeclaredType string;
        final DeclaredType suppressWarnings;
        final DeclaredType throwable;
        final DeclaredType typeLiteral;
        final DeclaredType unsupportedOperationException;
        final DeclaredType weakHashMap;

        AbstractTypeCache(AbstractProcessor processor) {
            this.alwaysByLocalReference = processor.getDeclaredType("org.graalvm.nativebridge.AlwaysByLocalReference");
            this.alwaysByLocalReferenceRepeated = processor.getDeclaredType("org.graalvm.nativebridge.AlwaysByLocalReferenceRepeated");
            this.alwaysByRemoteReference = processor.getDeclaredType("org.graalvm.nativebridge.AlwaysByRemoteReference");
            this.alwaysByRemoteReferenceRepeated = processor.getDeclaredType("org.graalvm.nativebridge.AlwaysByRemoteReferenceRepeated");
            this.assertionError = processor.getDeclaredType("java.lang.AssertionError");
            this.binaryMarshaller = processor.getDeclaredType("org.graalvm.nativebridge.BinaryMarshaller");
            this.binaryInput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryInput");
            this.binaryOutput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryOutput");
            this.boxedLong = processor.getDeclaredType("java.lang.Long");
            this.byLocalReference = processor.getDeclaredType("org.graalvm.nativebridge.ByLocalReference");
            this.byRemoteReference = processor.getDeclaredType("org.graalvm.nativebridge.ByRemoteReference");
            this.byteArrayBinaryOutput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryOutput.ByteArrayBinaryOutput");
            TypeElement classTypeElement = processor.getTypeElement("java.lang.Class");
            WildcardType wildcardType = processor.typeUtils().getWildcardType(null, null);
            this.clazz = processor.typeUtils().getDeclaredType(classTypeElement, wildcardType);
            this.collections = processor.getDeclaredType("java.util.Collections");
            this.customDispatchAccessor = processor.getDeclaredType("org.graalvm.nativebridge.CustomDispatchAccessor");
            this.customReceiverAccessor = processor.getDeclaredType("org.graalvm.nativebridge.CustomReceiverAccessor");
            this.customDispatchFactory = processor.getDeclaredType("org.graalvm.nativebridge.CustomDispatchFactory");
            this.error = processor.getDeclaredType("java.lang.Error");
            this.expectError = processor.getDeclaredTypeOrNull("org.graalvm.nativebridge.processor.test.ExpectError");
            this.foreignException = processor.getDeclaredType("org.graalvm.nativebridge.ForeignException");
            this.foreignObject = processor.getDeclaredType("org.graalvm.nativebridge.ForeignObject");
            this.idempotent = processor.getDeclaredType("org.graalvm.nativebridge.Idempotent");
            this.illegalArgumentException = processor.getDeclaredType("java.lang.IllegalArgumentException");
            this.illegalStateException = processor.getDeclaredType("java.lang.IllegalStateException");
            this.in = processor.getDeclaredType("org.graalvm.nativebridge.In");
            this.isolate = processor.getDeclaredType("org.graalvm.nativebridge.Isolate");
            this.isolateCreateException = processor.getDeclaredType("org.graalvm.nativebridge.IsolateCreateException");
            this.isolateDeathException = processor.getDeclaredType("org.graalvm.nativebridge.IsolateDeathException");
            this.isolateDeathHandler = processor.getDeclaredType("org.graalvm.nativebridge.IsolateDeathHandler");
            this.list = processor.getDeclaredType("java.util.List");
            this.map = processor.getDeclaredType("java.util.Map");
            this.marshallerAnnotation = processor.getDeclaredType("org.graalvm.nativebridge.MarshallerAnnotation");
            this.marshallerConfig = processor.getDeclaredType("org.graalvm.nativebridge.MarshallerConfig");
            this.math = processor.getDeclaredType("java.lang.Math");
            this.mutablePeer = processor.getDeclaredType("org.graalvm.nativebridge.MutablePeer");
            this.noImplementation = processor.getDeclaredType("org.graalvm.nativebridge.NoImplementation");
            this.object = processor.getDeclaredType("java.lang.Object");
            this.objects = processor.getDeclaredType("java.util.Objects");
            this.out = processor.getDeclaredType("org.graalvm.nativebridge.Out");
            this.override = processor.getDeclaredType("java.lang.Override");
            this.peer = processor.getDeclaredType("org.graalvm.nativebridge.Peer");
            this.receiverMethod = processor.getDeclaredType("org.graalvm.nativebridge.ReceiverMethod");
            this.referenceHandles = processor.getDeclaredType("org.graalvm.nativebridge.ReferenceHandles");
            this.runtimeException = processor.getDeclaredType("java.lang.RuntimeException");
            this.string = processor.getDeclaredType("java.lang.String");
            this.suppressWarnings = processor.getDeclaredType("java.lang.SuppressWarnings");
            this.throwable = processor.getDeclaredType("java.lang.Throwable");
            this.typeLiteral = processor.getDeclaredType("org.graalvm.nativebridge.TypeLiteral");
            this.unsupportedOperationException = processor.getDeclaredType("java.lang.UnsupportedOperationException");
            this.weakHashMap = processor.getDeclaredType("java.util.WeakHashMap");
        }
    }

    private static String marshallerName(TypeMirror type, List<? extends AnnotationMirror> annotations) {
        StringBuilder className = new StringBuilder();
        buildMarshallerNameFromType(className, type);
        if (!annotations.isEmpty()) {
            className.append("With");
            for (AnnotationMirror annotation : annotations) {
                className.append(annotation.getAnnotationType().asElement().getSimpleName());
            }
        }
        className.append("Marshaller");
        className.setCharAt(0, Character.toLowerCase(className.charAt(0)));
        return className.toString();
    }

    private static void buildMarshallerNameFromType(StringBuilder builder, TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN:
                builder.append("boolean");
                break;
            case BYTE:
                builder.append("byte");
                break;
            case SHORT:
                builder.append("short");
                break;
            case CHAR:
                builder.append("char");
                break;
            case INT:
                builder.append("int");
                break;
            case LONG:
                builder.append("long");
                break;
            case FLOAT:
                builder.append("float");
                break;
            case DOUBLE:
                builder.append("double");
                break;
            case DECLARED:
                DeclaredType declaredType = (DeclaredType) type;
                builder.append(declaredType.asElement().getSimpleName());
                List<? extends TypeMirror> typeParameters = declaredType.getTypeArguments();
                if (!typeParameters.isEmpty()) {
                    builder.append("Of");
                    for (TypeMirror typeParameter : typeParameters) {
                        buildMarshallerNameFromType(builder, typeParameter);
                    }
                }
                break;
            case ARRAY:
                buildMarshallerNameFromType(builder, ((ArrayType) type).getComponentType());
                builder.append("Array");
                break;
            case WILDCARD:
                WildcardType wildcardType = (WildcardType) type;
                TypeMirror upperBound = wildcardType.getExtendsBound();
                TypeMirror lowerBound = wildcardType.getSuperBound();
                if (upperBound != null) {
                    builder.append("Extends");
                    buildMarshallerNameFromType(builder, upperBound);
                } else if (lowerBound != null) {
                    builder.append("Super");
                    buildMarshallerNameFromType(builder, lowerBound);
                } else {
                    builder.append("Object");
                }
                break;
            case TYPEVAR:
                buildMarshallerNameFromType(builder, ((TypeVariable) type).getUpperBound());
                break;
            case INTERSECTION:
                for (Iterator<? extends TypeMirror> it = ((IntersectionType) type).getBounds().iterator(); it.hasNext();) {
                    buildMarshallerNameFromType(builder, it.next());
                    if (it.hasNext()) {
                        builder.append("And");
                    }
                }
                break;
            default:
                throw new UnsupportedOperationException("Unsupported: " + type.getKind());
        }
    }

    private void checkAnnotatedType(Element annotatedElement) {
        if (!annotatedElement.getKind().isClass()) {
            AnnotationMirror annotation = processor.getAnnotation(annotatedElement, handledAnnotationType);
            emitError(annotatedElement, annotation, "The annotation is supported only on type declarations.");
        }
        if (annotatedElement.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
            AnnotationMirror annotation = processor.getAnnotation(annotatedElement, handledAnnotationType);
            emitError(annotatedElement, annotation, "Annotation is supported only on top-level types.%n" +
                            "To fix this make the `%s` a top-level class.", annotatedElement.getSimpleName());
        }
    }
}
