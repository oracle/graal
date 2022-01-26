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
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

abstract class AbstractBridgeParser {

    final NativeBridgeProcessor processor;
    final Types types;
    final Elements elements;
    final AbstractTypeCache typeCache;
    private final DeclaredType handledAnnotationType;
    private final DeclaredType proxyBaseType;
    private final Collection<DeclaredType> ignoreAnnotations;
    final Collection<DeclaredType> copyAnnotations;

    AbstractBridgeParser(NativeBridgeProcessor processor, AbstractTypeCache typeCache,
                    DeclaredType handledAnnotationType, DeclaredType proxyBaseType) {
        this.processor = processor;
        this.types = processor.env().getTypeUtils();
        this.elements = processor.env().getElementUtils();
        this.typeCache = typeCache;
        this.handledAnnotationType = handledAnnotationType;
        this.proxyBaseType = proxyBaseType;
        this.ignoreAnnotations = new HashSet<>();
        Collections.addAll(ignoreAnnotations, typeCache.override, typeCache.suppressWarnings, typeCache.idempotent);
        this.copyAnnotations = new HashSet<>();
    }

    abstract Iterable<List<? extends TypeMirror>> getSubClassReferenceConstructorTypes();

    abstract Iterable<List<? extends TypeMirror>> getHandleReferenceConstructorTypes();

    abstract List<TypeMirror> getExceptionHandlerTypes();

    abstract AbstractBridgeGenerator getGenerator();

    DefinitionData createDefinitionData(DeclaredType annotatedType, AnnotationMirror annotation, DeclaredType serviceType, Collection<MethodData> toGenerate,
                    List<? extends VariableElement> annotatedTypeConstructorParams,
                    ExecutableElement delegateAccessor, ExecutableElement receiverAccessor,
                    ExecutableElement exceptionHandler, VariableElement endPointHandle) {
        DeclaredType jniConfig = (DeclaredType) getAnnotationValue(annotation, "jniConfig");
        return new DefinitionData(annotatedType, serviceType, toGenerate, annotatedTypeConstructorParams,
                        delegateAccessor, receiverAccessor, exceptionHandler, endPointHandle, jniConfig);
    }

    final DefinitionData parse(Element element) {
        AnnotationMirror handledAnnotation = processor.getAnnotation(element, handledAnnotationType);
        checkAnnotatedType(element, handledAnnotation);
        TypeElement annotatedElement = (TypeElement) element;
        DeclaredType annotatedType = (DeclaredType) annotatedElement.asType();

        DeclaredType serviceType = findServiceType(annotatedElement, proxyBaseType);
        if (serviceType == null) {
            throw new ParseException(element, handledAnnotation, "The annotated type must have a non Object super class or implement a single interface.");
        }

        readAnnotationActions(element);

        List<ExecutableElement> methods = ElementFilter.methodsIn(elements.getAllMembers(annotatedElement));
        ExecutableElement delegateAccessor = delegateAccessorIn(methods, serviceType);
        ExecutableElement receiverAccessor = receiverAccessorIn(methods, delegateAccessor);
        ExecutableElement exceptionHandler = exceptionHandlerIn(methods);
        VariableElement endPointHandle = endPointHandleIn(ElementFilter.fieldsIn(elements.getAllMembers(annotatedElement)), proxyBaseType);
        if (delegateAccessor == null && endPointHandle == null && !types.isSubtype(annotatedElement.getSuperclass(), proxyBaseType)) {
            throw new ParseException(element, handledAnnotation, "The annotated type must extend %s, have a field annotated by EndPointHandle or have an explicit receiver.",
                            getTypeName(proxyBaseType));
        }
        ConstructorSelector constructorSelector;
        if (delegateAccessor != null) {
            constructorSelector = ConstructorSelector.singleConstructor(annotatedElement, handledAnnotation);
        } else if (endPointHandle != null) {
            constructorSelector = ConstructorSelector.withParameters(annotatedElement, handledAnnotation, types, getHandleReferenceConstructorTypes(), false);
        } else {
            constructorSelector = ConstructorSelector.withParameters(annotatedElement, handledAnnotation, types, getSubClassReferenceConstructorTypes(), false);
        }
        List<? extends VariableElement> constructorParams = findConstructorParams(annotatedType, constructorSelector);
        Collection<MethodData> toGenerate = createMethodData(annotatedType, serviceType, methods, delegateAccessor != null,
                        processor.getAnnotation(annotatedElement, typeCache.idempotent) != null);
        return createDefinitionData(annotatedType, handledAnnotation, serviceType, toGenerate, constructorParams, delegateAccessor, receiverAccessor, exceptionHandler, endPointHandle);
    }

    static Object getAnnotationValue(AnnotationMirror mirror, String elementName) {
        ExecutableElement element = resolveElement(mirror, elementName);
        if (element == null) {
            throw new IllegalArgumentException("The " + elementName + " is not a valid annotation attribute.");
        }
        AnnotationValue annotationValue = mirror.getElementValues().get(element);
        return annotationValue == null ? null : annotationValue.getValue();
    }

    Object getAnnotationValueWithDefaults(AnnotationMirror mirror, String elementName) {
        ExecutableElement element = resolveElement(mirror, elementName);
        if (element == null) {
            throw new IllegalArgumentException("The " + elementName + " is not a valid annotation attribute.");
        }
        AnnotationValue annotationValue = elements.getElementValuesWithDefaults(mirror).get(element);
        return annotationValue == null ? null : annotationValue.getValue();
    }

    private static ExecutableElement resolveElement(AnnotationMirror mirror, String elementName) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(mirror.getAnnotationType().asElement().getEnclosedElements());
        for (ExecutableElement method : methods) {
            if (method.getSimpleName().contentEquals(elementName)) {
                return method;
            }
        }
        return null;
    }

    private void readAnnotationActions(Element element) {
        Set<DeclaredType> handledAnnotations = new HashSet<>();
        handledAnnotations.addAll(ignoreAnnotations);
        for (AnnotationMirror mirror : processor.getAnnotations(element, typeCache.annotationAction)) {
            DeclaredType value = (DeclaredType) getAnnotationValue(mirror, "value");
            VariableElement action = (VariableElement) getAnnotationValue(mirror, "action");
            if (Utilities.contains(handledAnnotations, value, types)) {
                String actionDisplayName;
                if (Utilities.contains(copyAnnotations, value, types)) {
                    actionDisplayName = "copied";
                } else if (Utilities.contains(ignoreAnnotations, value, types)) {
                    actionDisplayName = "ignored";
                } else {
                    actionDisplayName = "used for marshaller lookup";
                }
                throw new ParseException(element, mirror, "The annotation %s is already configured to be %s",
                                value.asElement().getSimpleName(), actionDisplayName);
            }
            handledAnnotations.add(value);
            switch (action.getSimpleName().toString()) {
                case "IGNORE":
                    ignoreAnnotations.add(value);
                    break;
                case "COPY":
                    ignoreAnnotations.add(value);
                    copyAnnotations.add(value);
                    break;
            }
        }
    }

    private ExecutableElement delegateAccessorIn(Iterable<? extends ExecutableElement> methods, DeclaredType serviceType) {
        ExecutableElement res = null;
        AnnotationMirror annotation = null;
        for (ExecutableElement method : methods) {
            AnnotationMirror dispatchResolver = processor.getAnnotation(method, typeCache.dispatchResolver);
            if (dispatchResolver != null) {
                if (res != null) {
                    throw new ParseException(method, dispatchResolver, "Only single class method can be annotated.");
                } else {
                    res = method;
                    annotation = dispatchResolver;
                }
            }
        }
        if (res != null) {
            if (!res.getModifiers().contains(Modifier.STATIC)) {
                throw new ParseException(res, annotation, "Annotated method must be static.");
            }
            if (res.getModifiers().contains(Modifier.PRIVATE)) {
                throw new ParseException(res, annotation, "Annotated method must be at least package protected.");
            }
            if (res.getParameters().size() != 1) {
                throw new ParseException(res, annotation, "Annotated method must have a single parameter.");
            }
            if (!types.isSameType(serviceType, res.getReturnType())) {
                throw new ParseException(res, annotation, "Annotated method must have %s return type.", getTypeName(serviceType));
            }
        }
        return res;
    }

    private ExecutableElement receiverAccessorIn(Iterable<? extends ExecutableElement> methods, ExecutableElement dispatchReceiver) {
        ExecutableElement res = null;
        AnnotationMirror annotation = null;
        for (ExecutableElement method : methods) {
            AnnotationMirror receiverResolver = processor.getAnnotation(method, typeCache.receiverResolver);
            if (receiverResolver != null) {
                if (res != null) {
                    throw new ParseException(method, receiverResolver, "Only single class method can be annotated.");
                } else {
                    res = method;
                    annotation = receiverResolver;
                }
            }
        }
        if (res != null) {
            if (!res.getModifiers().contains(Modifier.STATIC)) {
                throw new ParseException(res, annotation, "Annotated method must be static.");
            }
            if (res.getModifiers().contains(Modifier.PRIVATE)) {
                throw new ParseException(res, annotation, "Annotated method must be at least package protected.");
            }
            if (res.getParameters().size() != 1) {
                throw new ParseException(res, annotation, "Annotated method must have a single parameter.");
            }
            if (dispatchReceiver == null) {
                throw new ParseException(res, annotation, "Must also have a dispatch resolver.");
            }
            if (!types.isSameType(dispatchReceiver.getParameters().get(0).asType(), res.getParameters().get(0).asType())) {
                throw new ParseException(res, annotation, "Annotated method must have the same parameters as dispatch resolver.");
            }
        } else if (dispatchReceiver != null) {
            AnnotationMirror dispatchResolver = processor.getAnnotation(dispatchReceiver, typeCache.dispatchResolver);
            throw new ParseException(dispatchReceiver, dispatchResolver, "Must also have a receiver resolver.");
        }
        return res;
    }

    private ExecutableElement exceptionHandlerIn(Iterable<? extends ExecutableElement> methods) {
        ExecutableElement res = null;
        AnnotationMirror annotation = null;
        for (ExecutableElement method : methods) {
            AnnotationMirror exceptionHandler = processor.getAnnotation(method, typeCache.exceptionHandler);
            if (exceptionHandler != null) {
                if (res != null) {
                    throw new ParseException(method, exceptionHandler, "Only single class method can be annotated.");
                } else {
                    res = method;
                    annotation = exceptionHandler;
                }
            }
        }
        if (res != null) {
            if (!res.getModifiers().contains(Modifier.STATIC)) {
                throw new ParseException(res, annotation, "Annotated method must be static.");
            }
            if (res.getModifiers().contains(Modifier.PRIVATE)) {
                throw new ParseException(res, annotation, "Annotated method must be at least package protected.");
            }
            if (!types.isSameType(types.getPrimitiveType(TypeKind.BOOLEAN), res.getReturnType())) {
                throw new ParseException(res, annotation, "Annotated method must have boolean return type.");
            }
            List<? extends VariableElement> parameters = res.getParameters();
            List<? extends TypeMirror> requiredTypes = getExceptionHandlerTypes();
            boolean validParameterTypes = false;
            if (parameters.size() == requiredTypes.size()) {
                validParameterTypes = true;
                for (int i = 0; i < parameters.size(); i++) {
                    if (!types.isSameType(parameters.get(i).asType(), requiredTypes.get(i))) {
                        validParameterTypes = false;
                        break;
                    }
                }
            }
            if (!validParameterTypes) {
                throw new ParseException(res, annotation, "Annotated method must have %s signature.", getSignature(requiredTypes));
            }
        }
        return res;
    }

    private VariableElement endPointHandleIn(Iterable<? extends VariableElement> fields, DeclaredType type) {
        VariableElement res = null;
        AnnotationMirror annotation = null;
        for (VariableElement field : fields) {
            AnnotationMirror endPointHandle = processor.getAnnotation(field, typeCache.endPointHandle);
            if (endPointHandle != null) {
                if (res != null) {
                    throw new ParseException(field, endPointHandle, "Only single class field can be annotated.");
                } else {
                    res = field;
                    annotation = endPointHandle;
                }
            }
        }
        if (res != null) {
            if (res.getModifiers().contains(Modifier.PRIVATE)) {
                throw new ParseException(res, annotation, "Annotated field must be at least package protected.");
            }
            if (!types.isSubtype(res.asType(), type)) {
                throw new ParseException(res, annotation, "Annotated field must have %s type.", getTypeName(type));
            }
        }
        return res;
    }

    private DeclaredType findServiceType(TypeElement typeElement, DeclaredType proxyType) {
        TypeMirror tm = typeElement.getSuperclass();
        if (tm.getKind() != TypeKind.DECLARED) {
            return null;
        }
        DeclaredType superType = (DeclaredType) tm;
        if (!types.isSubtype(tm, proxyType) && !types.isSameType(tm, typeCache.object)) {
            return superType;
        } else {
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            if (interfaces.size() == 1) {
                return (DeclaredType) interfaces.get(0);
            }
        }
        return null;
    }

    private Collection<MethodData> createMethodData(DeclaredType annotatedType, DeclaredType serviceType, List<ExecutableElement> methods,
                    boolean explicitReceiver, boolean enforceIdempotent) {
        Collection<ExecutableElement> methodsToGenerate = methodsToGenerate(annotatedType, methods);
        for (ExecutableElement methodToGenerate : methodsToGenerate) {
            if (methodToGenerate.getEnclosingElement().equals(annotatedType.asElement()) && !methodToGenerate.getModifiers().contains(Modifier.ABSTRACT)) {
                throw new ParseException(methodToGenerate, null, "Should be 'final' to prevent override in the generated class or 'abstract' to be generated.");
            }
        }
        Set<? extends CharSequence> overloadedMethods = methodsToGenerate.stream().collect(Collectors.toMap(ExecutableElement::getSimpleName, (e) -> 1, (l, r) -> l + r)).entrySet().stream().filter(
                        (e) -> e.getValue() > 1).map((e) -> e.getKey()).collect(Collectors.toSet());
        Map<CharSequence, Integer> simpleNameCounter = new HashMap<>();
        Map<ExecutableElement, Integer> overloadIds = new HashMap<>();
        for (ExecutableElement methodToGenerate : methodsToGenerate) {
            CharSequence simpleName = methodToGenerate.getSimpleName();
            int index = overloadedMethods.contains(simpleName) ? simpleNameCounter.compute(simpleName, (id, prev) -> prev == null ? 1 : prev + 1) : 0;
            overloadIds.put(methodToGenerate, index);
        }
        Collection<MethodData> toGenerate = new ArrayList<>(methodsToGenerate.size());
        Set<String> usedCacheFields = new HashSet<>();
        for (ExecutableElement methodToGenerate : methodsToGenerate) {
            ExecutableType methodToGenerateType = (ExecutableType) types.asMemberOf(annotatedType, methodToGenerate);
            MarshallerVerifier verifier = new MarshallerVerifier(methodToGenerate, proxyBaseType, getSubClassReferenceConstructorTypes(), getHandleReferenceConstructorTypes(), explicitReceiver);
            MarshallerData retMarshaller = lookupMarshaller(methodToGenerateType.getReturnType(), methodToGenerate.getAnnotationMirrors(),
                            handledAnnotationType, verifier);
            List<? extends VariableElement> parameters = methodToGenerate.getParameters();
            List<? extends TypeMirror> parameterTypes = methodToGenerateType.getParameterTypes();
            List<MarshallerData> paramsMarshallers = new ArrayList<>();
            if (explicitReceiver) {
                if (parameters.isEmpty() || types.erasure(parameterTypes.get(0)).getKind() != TypeKind.DECLARED) {
                    throw new ParseException(methodToGenerate, null, "The first parameter must be a receiver when DispatchResolver is used.");
                }
                paramsMarshallers.add(MarshallerData.NO_MARSHALLER);
                parameters = parameters.subList(1, parameters.size());
                parameterTypes = parameterTypes.subList(1, parameterTypes.size());
            }
            paramsMarshallers.addAll(lookupMarshallers(parameters, parameterTypes, handledAnnotationType, verifier));
            CacheData cacheData;
            AnnotationMirror idempotentMirror = processor.getAnnotation(methodToGenerate, typeCache.idempotent);
            boolean noReturnType = methodToGenerateType.getReturnType().getKind() == TypeKind.VOID;
            if (idempotentMirror != null && noReturnType) {
                throw new ParseException(methodToGenerate, idempotentMirror, "Method must have non void return type.");
            }
            if (idempotentMirror != null || (enforceIdempotent && !noReturnType)) {
                String cacheFieldName = methodToGenerate.getSimpleName() + "Cache";
                if (usedCacheFields.contains(cacheFieldName)) {
                    int index = 2;
                    while (usedCacheFields.contains(cacheFieldName + index)) {
                        index++;
                    }
                    cacheFieldName = cacheFieldName + index;
                }
                usedCacheFields.add(cacheFieldName);
                TypeMirror type = methodToGenerateType.getReturnType();
                if (type.getKind().isPrimitive()) {
                    type = types.boxedClass((PrimitiveType) type).asType();
                }
                cacheData = new CacheData(cacheFieldName, type);
            } else {
                cacheData = null;
            }
            CharSequence receiverMethod = findReceiverMethod(methodToGenerate, methodToGenerateType, serviceType);
            MethodData methodData = new MethodData(methodToGenerate, methodToGenerateType, overloadIds.get(methodToGenerate),
                            receiverMethod, retMarshaller, paramsMarshallers, cacheData);
            toGenerate.add(methodData);
        }
        return toGenerate;
    }

    private Collection<ExecutableElement> methodsToGenerate(DeclaredType annotatedType, Iterable<? extends ExecutableElement> methods) {
        List<ExecutableElement> res = new ArrayList<>();
        for (ExecutableElement method : methods) {
            Set<Modifier> modifiers = method.getModifiers();
            if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.NATIVE) || modifiers.contains(Modifier.FINAL) || modifiers.contains(Modifier.PRIVATE)) {
                continue;
            }
            boolean visible = modifiers.contains(Modifier.PUBLIC);
            TypeElement owner = (TypeElement) method.getEnclosingElement();
            if (!visible && !getPackage(annotatedType.asElement()).equals(getPackage(owner))) {
                continue;
            }
            if (types.isSameType(typeCache.object, owner.asType()) || types.isSameType(proxyBaseType, owner.asType())) {
                continue;
            }
            res.add(method);
        }
        return res;
    }

    private static PackageElement getPackage(Element element) {
        Element enclosing = element.getEnclosingElement();
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            enclosing = enclosing.getEnclosingElement();
        }
        return (PackageElement) enclosing;
    }

    private List<MarshallerData> lookupMarshallers(List<? extends VariableElement> parameters, List<? extends TypeMirror> parameterTypes,
                    DeclaredType generateAnnotation, BiFunction<MarshallerData, AnnotationMirror, VariableElement> verifier) {
        List<MarshallerData> res = new ArrayList<>(parameterTypes.size());
        for (int i = 0; i < parameters.size(); i++) {
            res.add(lookupMarshaller(parameterTypes.get(i), parameters.get(i).getAnnotationMirrors(),
                            generateAnnotation, verifier));
        }
        return res;
    }

    private MarshallerData lookupMarshaller(TypeMirror type, List<? extends AnnotationMirror> annotationMirrors,
                    DeclaredType generateAnnotation, BiFunction<MarshallerData, AnnotationMirror, VariableElement> verifier) {
        MarshallerData res;
        if (type.getKind().isPrimitive() || type.getKind() == TypeKind.VOID) {
            res = MarshallerData.NO_MARSHALLER;
            verifier.apply(res, null);
        } else if (types.isSameType(typeCache.string, type)) {
            res = MarshallerData.NO_MARSHALLER;
            verifier.apply(res, null);
        } else if (isPrimitiveArray(type)) {
            res = MarshallerData.annotatedArray(findDirectionModifiers(annotationMirrors));
            verifier.apply(res, null);
        } else {
            AnnotationMirror annotationMirror = findByReference(annotationMirrors);
            if (annotationMirror != null) {
                DeclaredType referenceType = (DeclaredType) getAnnotationValue(annotationMirror, "value");
                TypeElement referenceElement = (TypeElement) referenceType.asElement();
                AnnotationMirror annotation = processor.getAnnotation(referenceElement, generateAnnotation);
                boolean useReceiverResolver = (boolean) getAnnotationValueWithDefaults(annotationMirror, "useReceiverResolver");
                res = MarshallerData.reference(referenceType, annotation, useReceiverResolver);
                res.nonDefaultReceiver = verifier.apply(res, annotationMirror);
            } else {
                List<? extends AnnotationMirror> annotations = filterMarshallerAnnotations(annotationMirrors, ignoreAnnotations);
                String marshallerFieldName = marshallerName(type, annotations);
                res = MarshallerData.marshalled(type, marshallerFieldName, annotations);
                verifier.apply(res, null);
            }
        }
        return res;
    }

    private List<? extends AnnotationMirror> filterMarshallerAnnotations(List<? extends AnnotationMirror> annotationMirrors,
                    Collection<? extends DeclaredType> ignoredAnnotations) {
        List<AnnotationMirror> result = new ArrayList<>();
        for (AnnotationMirror mirror : annotationMirrors) {
            DeclaredType type = mirror.getAnnotationType();
            if (!isIgnoredAnnotation(type, ignoredAnnotations)) {
                result.add(mirror);
            }
        }
        return result;
    }

    private boolean isIgnoredAnnotation(DeclaredType type, Collection<? extends DeclaredType> ignoredAnnotations) {
        for (DeclaredType annotation : ignoredAnnotations) {
            if (types.isSameType(annotation, type)) {
                return true;
            }
        }
        return false;
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
                builder.append("Object");
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

    private static boolean isPrimitiveArray(TypeMirror type) {
        return type.getKind() == TypeKind.ARRAY && ((ArrayType) type).getComponentType().getKind().isPrimitive();
    }

    private AnnotationMirror findByReference(List<? extends AnnotationMirror> annotationMirrors) {
        for (AnnotationMirror mirror : annotationMirrors) {
            DeclaredType type = mirror.getAnnotationType();
            if (types.isSameType(typeCache.byReference, type)) {
                return mirror;
            }
        }
        return null;
    }

    private List<? extends AnnotationMirror> findDirectionModifiers(List<? extends AnnotationMirror> annotationMirrors) {
        List<AnnotationMirror> res = null;
        for (AnnotationMirror mirror : annotationMirrors) {
            DeclaredType type = mirror.getAnnotationType();
            if (types.isSameType(typeCache.in, type) || types.isSameType(typeCache.out, type)) {
                if (res == null) {
                    res = new ArrayList<>(2);
                }
                res.add(mirror);
            }
        }
        if (res != null && res.size() == 1 && isDefaultIn(res.get(0))) {
            res = null;
        }
        return res != null ? res : Collections.emptyList();
    }

    private boolean isDefaultIn(AnnotationMirror mirror) {
        return types.isSameType(typeCache.in, mirror.getAnnotationType()) &&
                        getAnnotationValue(mirror, "arrayOffsetParameter") == null &&
                        getAnnotationValue(mirror, "arrayLengthParameter") == null;
    }

    private CharSequence findReceiverMethod(ExecutableElement executable, ExecutableType executableType, DeclaredType serviceType) {
        AnnotationMirror receiverMethodMirror = processor.getAnnotation(executable, typeCache.receiverMethod);
        if (receiverMethodMirror == null) {
            return null;
        }
        CharSequence receiverMethodName = (CharSequence) getAnnotationValue(receiverMethodMirror, "value");
        ExecutableElement found = null;
        for (ExecutableElement receiverCandidate : ElementFilter.methodsIn(elements.getAllMembers((TypeElement) serviceType.asElement()))) {
            if (!receiverCandidate.getSimpleName().contentEquals(receiverMethodName)) {
                continue;
            }
            if (!compatibleSignature((ExecutableType) types.asMemberOf(serviceType, receiverCandidate), executableType)) {
                continue;
            }
            found = receiverCandidate;
            break;
        }
        if (found == null) {
            throw new ParseException(executable, receiverMethodMirror, "Method " + receiverMethodName + " " +
                            getSignature(executableType.getParameterTypes()) + " not found in " + serviceType.asElement().getSimpleName() + ".");
        }
        if (found.getModifiers().contains(Modifier.STATIC)) {
            throw new ParseException(executable, receiverMethodMirror, "Receiver method cannot be static.");
        }
        if (found.getModifiers().contains(Modifier.PRIVATE)) {
            throw new ParseException(executable, receiverMethodMirror, "Receiver method cannot be private.");
        }
        return receiverMethodName;
    }

    private boolean compatibleSignature(ExecutableType receiverMethod, ExecutableType implMethod) {
        if (!types.isSubtype(receiverMethod.getReturnType(), implMethod.getReturnType())) {
            return false;
        }
        List<? extends TypeMirror> parameterTypes1 = receiverMethod.getParameterTypes();
        List<? extends TypeMirror> parameterTypes2 = implMethod.getParameterTypes();
        if (parameterTypes1.size() != parameterTypes2.size()) {
            return false;
        }
        for (int i = 0; i < parameterTypes1.size(); i++) {
            if (!types.isSameType(parameterTypes1.get(i), parameterTypes2.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static void checkAnnotatedType(Element annotatedElement, AnnotationMirror annotation) {
        if (!annotatedElement.getKind().isClass()) {
            throw new ParseException(annotatedElement, annotation, "Annotation is supported only on type declarations.");
        }
        if (annotatedElement.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
            throw new ParseException(annotatedElement, annotation, "Annotation is supported only on top level types.");
        }
    }

    private static CharSequence getTypeName(TypeMirror type) {
        StringBuilder res = new StringBuilder();
        Utilities.printType(res, type);
        return res;
    }

    private static CharSequence getSignature(List<? extends TypeMirror> types) {
        return getSignature(types, false, false);
    }

    private static CharSequence getSignature(List<? extends TypeMirror> types, boolean anyBefore, boolean anyAfter) {
        String prefix = anyBefore ? "(...," : "(";
        String suffix = anyAfter ? ",...)" : ")";
        return types.stream().map((t) -> getTypeName(t)).collect(Collectors.joining(", ", prefix, suffix));
    }

    private static CharSequence getSignatures(Iterable<List<? extends TypeMirror>> signatures, boolean anyBefore, boolean anyAfter) {
        StringBuilder sb = new StringBuilder();
        for (List<? extends TypeMirror> signature : signatures) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(getSignature(signature, anyBefore, anyAfter));
        }
        return sb;
    }

    private static List<? extends VariableElement> findConstructorParams(DeclaredType type, ConstructorSelector constructorSelector) {
        TypeElement te = (TypeElement) type.asElement();
        ElementFilter.constructorsIn(te.getEnclosedElements()).stream().forEach(constructorSelector::accept);
        ExecutableElement selectedConstructor = constructorSelector.get();
        return selectedConstructor == null ? Collections.emptyList() : selectedConstructor.getParameters();
    }

    private abstract static class ConstructorSelector implements Consumer<ExecutableElement>, Supplier<ExecutableElement> {

        ExecutableElement accepted;

        ConstructorSelector() {
        }

        static ConstructorSelector singleConstructor(TypeElement annotatedElement, AnnotationMirror mirror) {
            return new ConstructorSelector() {

                @Override
                public void accept(ExecutableElement executableElement) {
                    if (accepted == null) {
                        accepted = executableElement;
                    } else {
                        throw new ParseException(annotatedElement, mirror, "Annotated type must have a single constructor.");
                    }
                }

                @Override
                public ExecutableElement get() {
                    return accepted;
                }
            };
        }

        static ConstructorSelector withParameters(Element annotatedElement, AnnotationMirror mirror,
                        Types types, Iterable<List<? extends TypeMirror>> parameterTypes, boolean sameArity) {
            return new ConstructorSelector() {

                @Override
                public void accept(ExecutableElement executableElement) {
                    List<? extends VariableElement> params = executableElement.getParameters();
                    boolean validParams = true;
                    for (List<? extends TypeMirror> signature : parameterTypes) {
                        if (params.size() < signature.size() || (sameArity && params.size() != signature.size())) {
                            validParams = false;
                        } else {
                            validParams = true;
                            for (int i = 0; i < signature.size(); i++) {
                                TypeMirror parameter = params.get(i).asType();
                                TypeMirror requiredParameter = signature.get(i);
                                boolean compatible = requiredParameter.getKind().isPrimitive() ? types.isSameType(parameter, requiredParameter) : types.isAssignable(parameter, requiredParameter);
                                if (!compatible) {
                                    validParams = false;
                                    break;
                                }
                            }
                            if (validParams) {
                                break;
                            }
                        }
                    }
                    if (validParams) {
                        if (accepted == null) {
                            accepted = executableElement;
                        } else {
                            throw new ParseException(annotatedElement, mirror, "Annotated type must have a single constructor with one of the following signatures %s",
                                            getSignatures(parameterTypes, false, !sameArity));
                        }
                    }
                }

                @Override
                public ExecutableElement get() {
                    if (accepted != null) {
                        return accepted;
                    } else {
                        throw new ParseException(annotatedElement, mirror,
                                        "The annotated type must have a constructor with one of the following signatures: %s", getSignatures(parameterTypes, false, !sameArity));
                    }
                }
            };
        }
    }

    private final class MarshallerVerifier implements BiFunction<MarshallerData, AnnotationMirror, VariableElement> {

        private final ExecutableElement method;
        private final DeclaredType baseType;
        private final Iterable<List<? extends TypeMirror>> subClassParameterTypes;
        private final Iterable<List<? extends TypeMirror>> handleParameterTypes;
        private final boolean explicitReceiver;

        MarshallerVerifier(ExecutableElement method, DeclaredType proxyType,
                        Iterable<List<? extends TypeMirror>> subClassRequiredParameterTypes,
                        Iterable<List<? extends TypeMirror>> handleRequiredParameterTypes,
                        boolean explicitReceiver) {
            this.method = method;
            this.baseType = proxyType;
            this.subClassParameterTypes = subClassRequiredParameterTypes;
            this.handleParameterTypes = handleRequiredParameterTypes;
            this.explicitReceiver = explicitReceiver;
        }

        @Override
        public VariableElement apply(MarshallerData marshallerData, AnnotationMirror mirror) {
            VariableElement res = null;
            if (marshallerData.kind == MarshallerData.Kind.REFERENCE) {
                if (marshallerData.useReceiverResolver && !explicitReceiver) {
                    throw new ParseException(method, mirror, "UseReceiverResolver can be used only for types with explicit receiver.");
                }
                ConstructorSelector selector;
                if (types.isSubtype(marshallerData.forType, baseType)) {
                    selector = ConstructorSelector.withParameters(method, mirror, types, subClassParameterTypes, true);
                } else {
                    selector = ConstructorSelector.withParameters(method, mirror, types, handleParameterTypes, true);
                    res = ElementFilter.fieldsIn(((DeclaredType) marshallerData.forType).asElement().getEnclosedElements()).stream().filter(
                                    (f) -> processor.getAnnotation(f, typeCache.endPointHandle) != null).findAny().orElse(null);
                    if (res == null) {
                        throw new ParseException(method, mirror, "Cannot lookup %s annotated field in %s", getTypeName(typeCache.endPointHandle), getTypeName(marshallerData.forType));
                    }
                }
                TypeElement referenceElement = (TypeElement) ((DeclaredType) marshallerData.forType).asElement();
                ElementFilter.constructorsIn(referenceElement.getEnclosedElements()).stream().forEach(selector);
                selector.get();
            }
            return res;
        }
    }

    static final class ParseException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final Element element;
        private final AnnotationMirror annotation;

        ParseException(Element element, AnnotationMirror annotation, String message) {
            super(message);
            this.element = element;
            this.annotation = annotation;
        }

        ParseException(Element element, AnnotationMirror annotation, String format, Object... args) {
            this(element, annotation, String.format(format, args));
        }

        Element getElement() {
            return element;
        }

        AnnotationMirror getAnnotation() {
            return annotation;
        }
    }

    static final class MarshallerData {

        enum Kind {
            VALUE,
            REFERENCE,
            CUSTOM
        }

        static final MarshallerData NO_MARSHALLER = new MarshallerData(Kind.VALUE, null, null, false, null);

        final Kind kind;
        final TypeMirror forType;
        final List<? extends AnnotationMirror> annotations;
        final String name;                  // only for CUSTOM
        final boolean useReceiverResolver;  // only for REFERENCE
        VariableElement nonDefaultReceiver; // only for REFERENCE

        private MarshallerData(Kind kind, TypeMirror forType, String name, boolean useReceiverResolver, List<? extends AnnotationMirror> annotations) {
            this.kind = kind;
            this.forType = forType;
            this.name = name;
            this.useReceiverResolver = useReceiverResolver;
            this.annotations = annotations;
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
            return Objects.equals(forType, that.forType) && Objects.equals(name, that.name) && useReceiverResolver == that.useReceiverResolver && Objects.equals(annotations, that.annotations);
        }

        @Override
        public int hashCode() {
            return Objects.hash(forType, name);
        }

        static MarshallerData annotatedArray(List<? extends AnnotationMirror> annotations) {
            return annotations == null ? NO_MARSHALLER : new MarshallerData(Kind.VALUE, null, null, false, annotations);
        }

        static MarshallerData marshalled(TypeMirror forType, String name, List<? extends AnnotationMirror> annotations) {
            return new MarshallerData(Kind.CUSTOM, forType, name, false, annotations);
        }

        static MarshallerData reference(DeclaredType startPointType, AnnotationMirror annotation, boolean useReceiverResolver) {
            List<AnnotationMirror> annotations = annotation == null ? Collections.emptyList() : Collections.singletonList(annotation);
            return new MarshallerData(Kind.REFERENCE, startPointType, null, useReceiverResolver, annotations);
        }
    }

    static final class CacheData {

        final String cacheFieldName;
        final TypeMirror cacheEntryType;

        CacheData(String cacheFieldName, TypeMirror cacheEntryType) {
            this.cacheFieldName = cacheFieldName;
            this.cacheEntryType = cacheEntryType;
        }
    }

    static final class MethodData {

        final ExecutableElement element;
        final ExecutableType type;
        final int overloadId;
        final CharSequence receiverMethod;
        final CacheData cachedData;
        private final MarshallerData returnTypeMarshaller;
        private final List<MarshallerData> parameterMarshallers;

        MethodData(ExecutableElement element, ExecutableType type, int overloadId, CharSequence receiverMethod,
                        MarshallerData returnTypeMarshaller,
                        List<MarshallerData> parameterMarshallers,
                        CacheData cacheData) {
            this.element = element;
            this.type = type;
            this.overloadId = overloadId;
            this.receiverMethod = receiverMethod;
            this.cachedData = cacheData;
            this.returnTypeMarshaller = returnTypeMarshaller;
            this.parameterMarshallers = parameterMarshallers;
        }

        MarshallerData getReturnTypeMarshaller() {
            return returnTypeMarshaller;
        }

        MarshallerData getParameterMarshaller(int arg) {
            return parameterMarshallers.get(arg);
        }

        boolean hasOverload() {
            return overloadId > 0;
        }
    }

    static class DefinitionData {

        final DeclaredType annotatedType;
        final List<? extends VariableElement> annotatedTypeConstructorParams;
        final DeclaredType serviceType;
        final Collection<? extends MethodData> toGenerate;
        final ExecutableElement delegateAccessor;
        final ExecutableElement receiverAccessor;
        final ExecutableElement exceptionHandler;
        final VariableElement endPointHandle;
        final DeclaredType jniConfig;

        DefinitionData(DeclaredType annotatedType, DeclaredType serviceType, Collection<MethodData> toGenerate,
                        List<? extends VariableElement> annotatedTypeConstructorParams,
                        ExecutableElement delegateAccessor, ExecutableElement receiverAccessor,
                        ExecutableElement exceptionHandler, VariableElement endPointHandle, DeclaredType jniConfig) {
            this.annotatedType = annotatedType;
            this.annotatedTypeConstructorParams = annotatedTypeConstructorParams;
            this.serviceType = serviceType;
            this.toGenerate = toGenerate;
            this.delegateAccessor = delegateAccessor;
            this.receiverAccessor = receiverAccessor;
            this.exceptionHandler = exceptionHandler;
            this.endPointHandle = endPointHandle;
            this.jniConfig = jniConfig;
        }

        Collection<MarshallerData> getAllCustomMarshallers() {
            SortedSet<MarshallerData> res = new TreeSet<>((a, b) -> a.name.compareTo(b.name));
            collectAllMarshallers(res, MarshallerData.Kind.CUSTOM);
            return res;
        }

        Collection<MarshallerData> getAllReferenceMarshallers() {
            Set<MarshallerData> res = new HashSet<>();
            collectAllMarshallers(res, MarshallerData.Kind.REFERENCE);
            return res;
        }

        private void collectAllMarshallers(Set<? super MarshallerData> into, MarshallerData.Kind kind) {
            for (MethodData methodData : toGenerate) {
                Optional.of(methodData.returnTypeMarshaller).filter((m) -> m.kind == kind).ifPresent(into::add);
                methodData.parameterMarshallers.stream().filter((m) -> m.kind == kind).forEach(into::add);
            }
        }

        boolean hasExplicitReceiver() {
            return delegateAccessor != null;
        }

        String getTargetClassSimpleName() {
            return annotatedType.asElement().getSimpleName() + "Gen";
        }

    }

    abstract static class AbstractTypeCache {

        final DeclaredType annotationAction;
        final DeclaredType byReference;
        final DeclaredType clazz;
        final DeclaredType collections;
        final DeclaredType dispatchResolver;
        final DeclaredType endPointHandle;
        final DeclaredType exceptionHandler;
        final DeclaredType idempotent;
        final DeclaredType in;
        final DeclaredType jBooleanArray;
        final DeclaredType jByteArray;
        final DeclaredType jCharArray;
        final DeclaredType jClass;
        final DeclaredType jDoubleArray;
        final DeclaredType jFloatArray;
        final DeclaredType jIntArray;
        final DeclaredType jLongArray;
        final DeclaredType jObject;
        final DeclaredType jShortArray;
        final DeclaredType jString;
        final DeclaredType jThrowable;
        final DeclaredType jniConfig;
        final DeclaredType jniEnv;
        final DeclaredType jniHotSpotMarshaller;
        final DeclaredType jniMethodScope;
        final DeclaredType jniNativeMarshaller;
        final DeclaredType jniUtil;
        final DeclaredType map;
        final DeclaredType object;
        final DeclaredType out;
        final DeclaredType override;
        final DeclaredType receiverMethod;
        final DeclaredType receiverResolver;
        final DeclaredType string;
        final DeclaredType suppressWarnings;
        final DeclaredType throwable;
        final DeclaredType typeLiteral;
        final DeclaredType weakHashMap;

        AbstractTypeCache(NativeBridgeProcessor processor) {
            this.annotationAction = (DeclaredType) processor.getType("org.graalvm.nativebridge.AnnotationAction");
            this.byReference = (DeclaredType) processor.getType("org.graalvm.nativebridge.ByReference");
            this.clazz = (DeclaredType) processor.getType("java.lang.Class");
            this.collections = (DeclaredType) processor.getType("java.util.Collections");
            this.dispatchResolver = (DeclaredType) processor.getType("org.graalvm.nativebridge.DispatchResolver");
            this.endPointHandle = (DeclaredType) processor.getType("org.graalvm.nativebridge.EndPointHandle");
            this.exceptionHandler = (DeclaredType) processor.getType("org.graalvm.nativebridge.ExceptionHandler");
            this.idempotent = (DeclaredType) processor.getType("org.graalvm.nativebridge.Idempotent");
            this.in = (DeclaredType) processor.getType("org.graalvm.nativebridge.In");
            this.jBooleanArray = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JBooleanArray");
            this.jByteArray = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JByteArray");
            this.jCharArray = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JCharArray");
            this.jClass = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JClass");
            this.jDoubleArray = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JDoubleArray");
            this.jFloatArray = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JFloatArray");
            this.jIntArray = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JIntArray");
            this.jLongArray = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JLongArray");
            this.jObject = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JObject");
            this.jShortArray = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JShortArray");
            this.jString = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JString");
            this.jThrowable = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JThrowable");
            this.jniConfig = (DeclaredType) processor.getType("org.graalvm.nativebridge.JNIConfig");
            this.jniEnv = (DeclaredType) processor.getType("org.graalvm.jniutils.JNI.JNIEnv");
            this.jniHotSpotMarshaller = (DeclaredType) processor.getType("org.graalvm.nativebridge.JNIHotSpotMarshaller");
            this.jniMethodScope = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIMethodScope");
            this.jniNativeMarshaller = (DeclaredType) processor.getType("org.graalvm.nativebridge.JNINativeMarshaller");
            this.jniUtil = (DeclaredType) processor.getType("org.graalvm.jniutils.JNIUtil");
            this.map = (DeclaredType) processor.getType("java.util.Map");
            this.object = (DeclaredType) processor.getType("java.lang.Object");
            this.out = (DeclaredType) processor.getType("org.graalvm.nativebridge.Out");
            this.override = (DeclaredType) processor.getType("java.lang.Override");
            this.receiverMethod = (DeclaredType) processor.getType("org.graalvm.nativebridge.ReceiverMethod");
            this.receiverResolver = (DeclaredType) processor.getType("org.graalvm.nativebridge.ReceiverResolver");
            this.string = (DeclaredType) processor.getType("java.lang.String");
            this.suppressWarnings = (DeclaredType) processor.getType("java.lang.SuppressWarnings");
            this.throwable = (DeclaredType) processor.getType("java.lang.Throwable");
            this.typeLiteral = (DeclaredType) processor.getType("org.graalvm.polyglot.TypeLiteral");
            this.weakHashMap = (DeclaredType) processor.getType("java.util.WeakHashMap");
        }
    }
}
