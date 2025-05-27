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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class AbstractServiceParser extends AbstractBridgeParser {

    final AbstractEndPointMethodProvider endPointMethodProvider;
    private final Configuration myConfiguration;
    private final Configuration otherConfiguration;
    private final Set<DeclaredType> ignoreAnnotations;
    private Set<DeclaredType> marshallerAnnotations;

    AbstractServiceParser(NativeBridgeProcessor processor, AbstractTypeCache typeCache, AbstractEndPointMethodProvider endPointMethodProvider,
                    Configuration myConfiguration, Configuration otherConfiguration) {
        super(processor, typeCache, myConfiguration.getHandledAnnotationType());
        this.endPointMethodProvider = endPointMethodProvider;
        this.myConfiguration = myConfiguration;
        this.otherConfiguration = otherConfiguration;
        this.ignoreAnnotations = new HashSet<>();
        Collections.addAll(this.ignoreAnnotations, typeCache.override, typeCache.suppressWarnings,
                        typeCache.byLocalReference, typeCache.byRemoteReference,
                        typeCache.customDispatchFactory, typeCache.customDispatchAccessor,
                        typeCache.customReceiverAccessor, typeCache.idempotent, typeCache.in, typeCache.out,
                        typeCache.receiverMethod, typeCache.isolateDeathHandler);
        if (typeCache.expectError != null) {
            this.ignoreAnnotations.add(typeCache.expectError);
        }
    }

    ServiceDefinitionData createDefinitionData(DeclaredType annotatedType, @SuppressWarnings("unused") AnnotationMirror annotation,
                    DeclaredType serviceType, DeclaredType peerType, DeclaredType factoryClass, boolean mutable,
                    Collection<MethodData> toGenerate, List<? extends VariableElement> annotatedTypeConstructorParams,
                    List<? extends CodeBuilder.Parameter> peerConstructorParams, ExecutableElement customDispatchAccessor,
                    ExecutableElement customReceiverAccessor, DeclaredType marshallerConfig, MarshallerData throwableMarshaller,
                    Set<DeclaredType> annotationsToIgnore, Set<DeclaredType> annotationsForMarshallerLookup) {
        return new ServiceDefinitionData(annotatedType, serviceType, peerType, factoryClass, mutable, toGenerate, annotatedTypeConstructorParams,
                        peerConstructorParams, customDispatchAccessor, customReceiverAccessor, marshallerConfig,
                        throwableMarshaller, annotationsToIgnore, annotationsForMarshallerLookup);
    }

    @Override
    final ServiceDefinitionData parseElement(Element element) {
        marshallerAnnotations = new HashSet<>();
        AnnotationMirror handledAnnotation = processor.getAnnotation(element, myConfiguration.getHandledAnnotationType());
        TypeElement annotatedElement = (TypeElement) element;
        DeclaredType annotatedType = (DeclaredType) annotatedElement.asType();
        DeclaredType serviceType = findServiceType(annotatedElement, handledAnnotation);
        if (serviceType == null) {
            // Fatal error, parsing cannot continue.
            return null;
        }
        DeclaredType factoryClass = findFactoryClass(annotatedElement, handledAnnotation);
        DeclaredType marshallerConfigClass = factoryClass != null ? findMarshallerConfigClass(factoryClass) : null;
        // Use only declared methods for custom dispatch directives.
        List<ExecutableElement> methods = ElementFilter.methodsIn(annotatedElement.getEnclosedElements());
        ExecutableElement customDispatchAccessor = customDispatchAccessorIn(methods, serviceType);
        ExecutableElement customReceiverAccessor = customReceiverAccessorIn(methods, customDispatchAccessor, serviceType);
        customDispatchFactoryIn(methods, true, customDispatchAccessor);
        // Process all methods, including inherited.
        methods = ElementFilter.methodsIn(elements.getAllMembers(annotatedElement));
        boolean customDispatch = customDispatchAccessor != null || customReceiverAccessor != null;
        if (customDispatch) {
            if (implementsForeignObject(annotatedElement)) {
                CharSequence foreignObject = Utilities.getTypeName(getTypeCache().foreignObject);
                emitError(element, handledAnnotation, "A class with a custom dispatch must not implement `%s`.%n" +
                                "The `%s` is passed as the first parameter to the dispatch method. To resolve this, remove the `implements %s`.",
                                foreignObject, foreignObject, foreignObject);
            }
        } else {
            if (!implementsForeignObject(annotatedElement)) {
                Set<Modifier> staticMods = Collections.singleton(Modifier.STATIC);
                List<Map.Entry<? extends TypeMirror, ? extends CharSequence>> objectParam = Collections.singletonList(new SimpleImmutableEntry<>(getTypeCache().object, "receiver"));
                CharSequence foreignObject = Utilities.getTypeName(getTypeCache().foreignObject);
                emitError(element, handledAnnotation, "The annotated type must implement `%s`, or have a custom dispatch.%n" +
                                "To bridge a class, extend the class, add implement %s interface.%n" +
                                "To bridge an interface, implement the interface and %s.%n" +
                                "To bridge a class with a custom dispatch add `@%s %s` and `@%s %s` methods.",
                                foreignObject, foreignObject, foreignObject,
                                Utilities.getTypeName(getTypeCache().customDispatchAccessor), Utilities.printMethod(staticMods, "resolveDispatch", serviceType, objectParam),
                                Utilities.getTypeName(getTypeCache().customReceiverAccessor), Utilities.printMethod(staticMods, "resolveReceiver", getTypeCache().object, objectParam));
            }
        }
        ConstructorSelector constructorSelector = ConstructorSelector.singleConstructor(this, annotatedElement, handledAnnotation);
        Map<String, DeclaredType> alwaysByLocalReference = findAlwaysByReference(element, getTypeCache().alwaysByLocalReference, getTypeCache().alwaysByLocalReferenceRepeated);
        Map<String, DeclaredType> alwaysByRemoteReference = findAlwaysByReference(element, getTypeCache().alwaysByRemoteReference, getTypeCache().alwaysByRemoteReferenceRepeated);
        Set<String> duplicatedByReference = new HashSet<>(alwaysByLocalReference.keySet());
        duplicatedByReference.retainAll(alwaysByRemoteReference.keySet());
        if (!duplicatedByReference.isEmpty()) {
            CharSequence local = Utilities.getTypeName(getTypeCache().alwaysByLocalReference);
            CharSequence remote = Utilities.getTypeName(getTypeCache().alwaysByRemoteReference);
            for (String duplicated : duplicatedByReference) {
                emitError(element, handledAnnotation, "Invalid combination of @`%s` and @`%s` annotations for type `%s`. The type can be configured either as `%s` or `%s`.%n" +
                                "To resolve this, remove either `@%s(%s)` or `@%s(%s)`.", local, remote, duplicated, local, remote, local, duplicated, remote, duplicated);
            }
        }
        AnnotationMirror mutablePeer = processor.getAnnotation(annotatedElement, getTypeCache().mutablePeer);
        boolean mutable = mutablePeer != null;
        if (customDispatch && mutable) {
            emitError(element, mutablePeer, "Classes with custom dispatch cannot have mutable peers. To fix this, remove the `@%s` annotation from the class.",
                            Utilities.getTypeName(getTypeCache().mutablePeer));
        }
        List<? extends VariableElement> constructorParams = findConstructorParams(annotatedType, constructorSelector);
        IsolateDeathHandlerDefinition isolateDeathHandler = findIsolateDeathHandler(annotatedType, annotatedElement);
        Collection<MethodData> toGenerate = createMethodData(annotatedType, serviceType, methods, customDispatch,
                        processor.getAnnotation(annotatedElement, getTypeCache().idempotent) != null, alwaysByLocalReference, alwaysByRemoteReference, isolateDeathHandler);
        MarshallerData throwableMarshaller = MarshallerData.marshalled(getTypeCache().throwable, null, null, Collections.emptyList());
        if (hasErrors()) {
            return null;
        }
        DeclaredType implementation = (DeclaredType) getOptionalAnnotationValue(handledAnnotation, "implementation");
        if (implementation != null && !types.isAssignable(implementation, serviceType)) {
            emitError(element, handledAnnotation, "The class specified in `@%s.implementation` for `%s` registration must be assignable to `%s`.%n" +
                            "To resolve this, fix the implementation class.",
                            Utilities.getTypeName(myConfiguration.getHandledAnnotationType()), Utilities.getTypeName(annotatedType), Utilities.getTypeName(serviceType));
        }
        return createDefinitionData(annotatedType, handledAnnotation, serviceType, myConfiguration.getPeerType(), factoryClass, mutable,
                        toGenerate, constructorParams, myConfiguration.getPeerConstructorParameters(), customDispatchAccessor, customReceiverAccessor,
                        marshallerConfigClass, throwableMarshaller, ignoreAnnotations, marshallerAnnotations);
    }

    final AnnotationMirror getFactoryRegistration(DeclaredType factory) {
        Element factoryElement = factory.asElement();
        for (DeclaredType annotationType : myConfiguration.getFactoryAnnotationTypes()) {
            AnnotationMirror res = processor.getAnnotation(factoryElement, annotationType);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    private DeclaredType findMarshallerConfigClass(DeclaredType factory) {
        AnnotationMirror annotation = getFactoryRegistration(factory);
        return (DeclaredType) getAnnotationValue(annotation, "marshallers");
    }

    private DeclaredType findFactoryClass(TypeElement annotatedElement, AnnotationMirror handledAnnotation) {
        DeclaredType factory = (DeclaredType) getAnnotationValue(handledAnnotation, "factory");
        PackageElement factoryPackage = Utilities.getEnclosingPackageElement((TypeElement) factory.asElement());
        PackageElement servicePackage = Utilities.getEnclosingPackageElement(annotatedElement);
        if (!factoryPackage.equals(servicePackage)) {
            CharSequence serviceName = Utilities.getTypeName(annotatedElement.asType());
            CharSequence factoryName = Utilities.getTypeName(factory);
            emitError(annotatedElement, handledAnnotation, "Mismatched package definitions: service `%s` and factory `%s` must reside in the same package.%n" +
                            "To resolve this issue, move `%s` to the package `%s`.", serviceName, factoryName, serviceName, factoryPackage.getQualifiedName());
            return null;
        }
        AnnotationMirror registration = getFactoryRegistration(factory);
        if (registration == null) {
            CharSequence factoryName = Utilities.getTypeName(factory);
            CharSequence generateName = Utilities.getTypeName(myConfiguration.getFactoryAnnotationTypes().iterator().next());
            emitError(annotatedElement, handledAnnotation, "Missing required annotation: Factory `%s` must be annotated with `@%s`.%n" +
                            "To resolve this, add `@%s` to `%s`.", factoryName, generateName, generateName, factoryName);
            return null;
        }
        return factory;
    }

    private boolean implementsForeignObject(TypeElement annotatedElement) {
        for (TypeMirror type : annotatedElement.getInterfaces()) {
            if (types.isSameType(getTypeCache().foreignObject, type)) {
                return true;
            }
        }
        return false;
    }

    private ExecutableElement customDispatchAccessorIn(Iterable<? extends ExecutableElement> methods, DeclaredType serviceType) {
        ExecutableElement res = null;
        AnnotationMirror annotation = null;
        for (ExecutableElement method : methods) {
            AnnotationMirror customDispatchAccessor = processor.getAnnotation(method, getTypeCache().customDispatchAccessor);
            if (customDispatchAccessor != null) {
                if (res != null) {
                    emitError(method, customDispatchAccessor, "Only a single method can be annotated by the `%s`.%n" +
                                    "Fix the ambiguity by removing the `%s` method or the `%s` method.",
                                    Utilities.getTypeName(getTypeCache().customDispatchAccessor), Utilities.printMethod(res), Utilities.printMethod(method));
                    break;
                } else {
                    res = method;
                    annotation = customDispatchAccessor;
                }
            }
        }
        if (res != null) {
            if (!res.getModifiers().contains(Modifier.STATIC) || res.getModifiers().contains(Modifier.PRIVATE) || res.getParameters().size() != 1 ||
                            !types.isSameType(serviceType, res.getReturnType())) {
                Set<Modifier> expectedModifiers = staticNonPrivate(res.getModifiers());
                List<Map.Entry<TypeMirror, CharSequence>> expectedParameters = switch (res.getParameters().size()) {
                    case 0 -> Collections.singletonList(new SimpleImmutableEntry<>(getTypeCache().object, "receiver"));
                    case 1 -> {
                        VariableElement parameter = res.getParameters().get(0);
                        yield Collections.singletonList(new SimpleImmutableEntry<>(parameter.asType(), parameter.getSimpleName()));
                    }
                    default -> Collections.singletonList(new SimpleImmutableEntry<>(res.getParameters().get(0).asType(), "receiver"));
                };
                emitError(res, annotation, "A method annotated by `%s` must be a non-private static method with a single parameter and `%s` return type.%n" +
                                "To fix this change the signature to `%s`.", Utilities.getTypeName(getTypeCache().customDispatchAccessor), Utilities.getTypeName(serviceType),
                                Utilities.printMethod(expectedModifiers, res.getSimpleName(), serviceType, expectedParameters));
            }
        }
        return res;
    }

    private ExecutableElement customReceiverAccessorIn(Iterable<? extends ExecutableElement> methods, ExecutableElement customDispatchAccessor, DeclaredType serviceType) {
        ExecutableElement res = null;
        AnnotationMirror annotation = null;
        for (ExecutableElement method : methods) {
            AnnotationMirror customReceiverAccessor = processor.getAnnotation(method, getTypeCache().customReceiverAccessor);
            if (customReceiverAccessor != null) {
                if (res != null) {
                    emitError(method, customReceiverAccessor, "Only a single method can be annotated by the `%s`.%n" +
                                    "Fix the ambiguity by removing the `%s` method or the `%s` method.",
                                    Utilities.getTypeName(getTypeCache().customReceiverAccessor), Utilities.printMethod(res), Utilities.printMethod(method));
                    break;
                } else {
                    res = method;
                    annotation = customReceiverAccessor;
                }
            }
        }
        if (res != null) {
            if (!res.getModifiers().contains(Modifier.STATIC) || res.getModifiers().contains(Modifier.PRIVATE) || res.getParameters().size() != 1 ||
                            !types.isSubtype(getTypeCache().object, types.erasure(res.getReturnType()))) {
                Set<Modifier> expectedModifiers = staticNonPrivate(res.getModifiers());
                List<Map.Entry<TypeMirror, CharSequence>> expectedParameters;

                switch (res.getParameters().size()) {
                    case 0: {
                        TypeMirror parameterType;
                        if (customDispatchAccessor != null && !customDispatchAccessor.getParameters().isEmpty()) {
                            parameterType = customDispatchAccessor.getParameters().get(0).asType();
                        } else {
                            parameterType = getTypeCache().object;
                        }
                        expectedParameters = Collections.singletonList(new SimpleImmutableEntry<>(parameterType, "receiver"));
                        break;
                    }
                    case 1:
                        VariableElement parameter = res.getParameters().get(0);
                        expectedParameters = Collections.singletonList(new SimpleImmutableEntry<>(parameter.asType(), parameter.getSimpleName()));
                        break;
                    default: {
                        TypeMirror parameterType;
                        if (customDispatchAccessor != null && !customDispatchAccessor.getParameters().isEmpty()) {
                            TypeMirror dispatchAccessorArg = customDispatchAccessor.getParameters().get(0).asType();
                            TypeMirror receiverAccessorArg = res.getParameters().get(0).asType();
                            parameterType = types.isSubtype(dispatchAccessorArg, receiverAccessorArg) ? dispatchAccessorArg : receiverAccessorArg;
                        } else {
                            parameterType = res.getParameters().get(0).asType();
                        }
                        expectedParameters = Collections.singletonList(new SimpleImmutableEntry<>(parameterType, "receiver"));
                    }
                }
                emitError(res, annotation, "A method annotated by `%s` must be a non-private non-void static method with a single parameter.%n" +
                                "To fix this change the signature to `%s`.", Utilities.getTypeName(getTypeCache().customReceiverAccessor),
                                Utilities.printMethod(expectedModifiers, res.getSimpleName(), getTypeCache().object, expectedParameters));
            }

            if (customDispatchAccessor == null) {
                emitError(res, annotation, "Class with a custom receiver accessor must also provide a custom dispatch accessor.%n" +
                                "To fix this add the `@%s %s` method.", Utilities.getTypeName(getTypeCache().customDispatchAccessor),
                                Utilities.printMethod(staticNonPrivate(Collections.emptySet()), "resolveDispatch", serviceType, res.getParameters().toArray(new VariableElement[0])));
            } else if (customDispatchAccessor.getParameters().size() == 1 && res.getParameters().size() == 1) {
                if (!types.isSameType(customDispatchAccessor.getParameters().get(0).asType(), res.getParameters().get(0).asType())) {
                    emitError(res, annotation, "The custom receiver accessor must have the same parameter type as the custom dispatch accessor.");
                }
            }
        } else if (customDispatchAccessor != null) {
            AnnotationMirror customDispatchAccessorAnnotation = processor.getAnnotation(customDispatchAccessor, getTypeCache().customDispatchAccessor);
            emitError(customDispatchAccessor, customDispatchAccessorAnnotation, "Classes with a custom dispatch accessor must also provide a custom receiver accessor.%n" +
                            "To fix this add the `@%s %s` method.", Utilities.getTypeName(getTypeCache().customReceiverAccessor),
                            Utilities.printMethod(staticNonPrivate(Collections.emptySet()), "resolveReceiver", getTypeCache().object,
                                            customDispatchAccessor.getParameters().toArray(new VariableElement[0])));
        }
        return res;
    }

    private ExecutableElement customDispatchFactoryIn(Iterable<? extends ExecutableElement> methods, boolean verify, ExecutableElement customDispatchAccessor) {
        ExecutableElement res = null;
        AnnotationMirror annotation = null;
        for (ExecutableElement method : methods) {
            AnnotationMirror customDispatchFactory = processor.getAnnotation(method, getTypeCache().customDispatchFactory);
            if (customDispatchFactory != null) {
                if (verify && res != null) {
                    emitError(method, customDispatchFactory, "Only a single method can be annotated by the `%s`.%n" +
                                    "Fix the ambiguity by removing the `%s` method or the `%s` method.",
                                    Utilities.getTypeName(getTypeCache().customDispatchFactory), Utilities.printMethod(res), Utilities.printMethod(method));
                    break;
                } else {
                    res = method;
                    annotation = customDispatchFactory;
                }
            }
        }
        if (verify && res != null) {
            if (customDispatchAccessor == null) {
                emitError(res, annotation, "A method annotated by `%s` is allowed only for classes with a custom dispatch.%n" +
                                "To fix this add a custom dispatch accessor method annotated by `%s` and a custom receiver accessor method annotated by `%s`.",
                                Utilities.getTypeName(getTypeCache().customDispatchFactory), Utilities.getTypeName(getTypeCache().customDispatchAccessor),
                                Utilities.getTypeName(getTypeCache().customReceiverAccessor));
            } else {
                List<? extends VariableElement> customDispatchAccessorParams = customDispatchAccessor.getParameters();
                TypeMirror expectedReturnType = customDispatchAccessorParams.size() == 1 ? customDispatchAccessorParams.get(0).asType() : getTypeCache().object;
                if (!res.getModifiers().contains(Modifier.STATIC) || res.getModifiers().contains(Modifier.PRIVATE) ||
                                res.getParameters().size() != 1 ||
                                !types.isSameType(res.getParameters().getFirst().asType(), getTypeCache().foreignObject) ||
                                !types.isSubtype(res.getReturnType(), expectedReturnType)) {
                    Set<Modifier> expectedModifiers = staticNonPrivate(res.getModifiers());
                    List<Map.Entry<TypeMirror, CharSequence>> expectedParameters = Collections.singletonList(new SimpleImmutableEntry<>(getTypeCache().foreignObject, "receiver"));
                    emitError(res, annotation, "A method annotated by `%s` must be a non-private static method with a single `%s` parameter and `%s` return type.%n" +
                                    "To fix this change the signature to `%s`.", Utilities.getTypeName(getTypeCache().customDispatchFactory),
                                    Utilities.getTypeName(getTypeCache().foreignObject),
                                    Utilities.getTypeName(expectedReturnType),
                                    Utilities.printMethod(expectedModifiers, res.getSimpleName(), expectedReturnType, expectedParameters));
                }
            }
        }
        return res;
    }

    private DeclaredType findServiceType(TypeElement typeElement, AnnotationMirror annotation) {
        TypeMirror superClass = typeElement.getSuperclass();
        if (superClass.getKind() != TypeKind.DECLARED) {
            return null;
        }
        List<? extends TypeMirror> interfaces = typeElement.getInterfaces().stream().filter((m) -> !types.isSameType(getTypeCache().foreignObject, m)).toList();
        String fix;
        if (!types.isSameType(superClass, getTypeCache().object)) {
            if (interfaces.isEmpty()) {
                return (DeclaredType) superClass;
            } else {
                Stream<CharSequence> toImplement = interfaces.stream().filter((tm) -> tm.getKind() == TypeKind.DECLARED).map((tm) -> ((DeclaredType) tm).asElement().getSimpleName());
                fix = String.format("To fix this introduce a new bridged base class extending `%s` and implementing %s and extend it.",
                                ((DeclaredType) superClass).asElement().getSimpleName(), toImplement.map((s) -> "`" + s + "`").collect(Collectors.joining(", ")));
            }
        } else {
            switch (interfaces.size()) {
                case 0:
                    fix = "To fix this implement the bridged interface or extend the bridged class.";
                    break;
                case 1:
                    return (DeclaredType) interfaces.get(0);
                default:
                    Stream<CharSequence> toImplement = interfaces.stream().filter((tm) -> tm.getKind() == TypeKind.DECLARED).map((tm) -> ((DeclaredType) tm).asElement().getSimpleName());
                    fix = String.format("To fix this introduce a new bridged interface extending %s and implement it.",
                                    toImplement.map((s) -> "`" + s + "`").collect(Collectors.joining(", ")));
            }
        }
        emitError(typeElement, annotation, "The annotated type must have a non `Object` superclass or implement a single interface.%n" + fix);
        return null;
    }

    private Collection<MethodData> createMethodData(DeclaredType annotatedType, DeclaredType serviceType, List<ExecutableElement> methods,
                    boolean customDispatch, boolean enforceIdempotent, Map<String, DeclaredType> alwaysByLocalReference,
                    Map<String, DeclaredType> alwaysByRemoteReference, IsolateDeathHandlerDefinition classIsolateDeathHandler) {
        List<ExecutableElement> methodsToGenerate = methodsToGenerate(annotatedType, methods);
        Set<? extends CharSequence> overloadedMethods = methodsToGenerate.stream().//
                        collect(Collectors.groupingBy(ExecutableElement::getSimpleName, Collectors.counting())).//
                        entrySet().stream().//
                        filter((e) -> e.getValue() > 1).//
                        map(Map.Entry::getKey).//
                        collect(Collectors.toSet());
        Map<CharSequence, Integer> simpleNameCounter = new HashMap<>();
        Map<ExecutableElement, Integer> overloadIds = new HashMap<>();
        for (ExecutableElement methodToGenerate : methodsToGenerate) {
            if (methodToGenerate.getEnclosingElement().equals(annotatedType.asElement()) && !methodToGenerate.getModifiers().contains(Modifier.ABSTRACT)) {
                emitError(methodToGenerate, null, "Should be `final` to prevent override in the generated class or `abstract` to be generated.%n" +
                                "To fix this add a `final` modifier or remove implementation in the `%s`.", Utilities.getTypeName(annotatedType));
            }
            CharSequence simpleName = methodToGenerate.getSimpleName();
            int overloadId = overloadedMethods.contains(simpleName) ? simpleNameCounter.compute(simpleName, (id, prev) -> prev == null ? 1 : prev + 1) : 0;
            overloadIds.put(methodToGenerate, overloadId);
        }
        Collection<MethodData> toGenerate = new ArrayList<>(methodsToGenerate.size());
        Set<String> usedCacheFields = new HashSet<>();
        Map<String, List<Entry<Signature, ExecutableElement>>> startPointMethodNames = computeBaseClassOverrides(annotatedType);
        Map<String, List<Entry<Signature, ExecutableElement>>> endPointMethodNames = computeBaseClassOverrides(getTypeCache().object);
        for (ExecutableElement methodToGenerate : methodsToGenerate) {
            ExecutableType methodToGenerateType = (ExecutableType) types.asMemberOf(annotatedType, methodToGenerate);
            MarshallerData retMarshaller = lookupMarshaller(methodToGenerate, methodToGenerateType.getReturnType(), methodToGenerate.getAnnotationMirrors(), customDispatch,
                            alwaysByLocalReference, alwaysByRemoteReference);
            List<? extends VariableElement> parameters = methodToGenerate.getParameters();
            List<? extends TypeMirror> parameterTypes = methodToGenerateType.getParameterTypes();
            List<MarshallerData> paramsMarshallers = new ArrayList<>();
            if (customDispatch) {
                if (parameters.isEmpty() || types.erasure(parameterTypes.get(0)).getKind() != TypeKind.DECLARED) {
                    emitError(methodToGenerate, null, "In a class with a custom dispatch, the first method parameter must be the receiver.%n" +
                                    "For a class with a custom dispatch, make the method `final` to prevent its generation.%n" +
                                    "For a class that has no custom dispatch, remove methods annotated by `%s` and `%s`.",
                                    Utilities.getTypeName(getTypeCache().customDispatchAccessor), Utilities.getTypeName(getTypeCache().customReceiverAccessor));
                } else {
                    paramsMarshallers.add(MarshallerData.NO_MARSHALLER);
                    parameters = parameters.subList(1, parameters.size());
                    parameterTypes = parameterTypes.subList(1, parameterTypes.size());
                }
            }
            paramsMarshallers.addAll(lookupMarshallers(methodToGenerate, parameters, parameterTypes, customDispatch, alwaysByLocalReference, alwaysByRemoteReference));
            CacheData cacheData;
            AnnotationMirror idempotentMirror = processor.getAnnotation(methodToGenerate, getTypeCache().idempotent);
            boolean noReturnType = methodToGenerateType.getReturnType().getKind() == TypeKind.VOID;
            boolean referenceReturnType = retMarshaller.kind == MarshallerData.Kind.PEER_REFERENCE || retMarshaller.kind == MarshallerData.Kind.REFERENCE;
            boolean hasOutParameter = paramsMarshallers.stream().anyMatch((md) -> md.out != null);
            boolean hasCustomOutParameter = paramsMarshallers.stream().anyMatch((md) -> md.kind == MarshallerData.Kind.CUSTOM && md.out != null);
            if (idempotentMirror != null && noReturnType) {
                emitError(methodToGenerate, idempotentMirror, "A method with a cached return value must have a non-void return type.%n" +
                                "To fix this remove the `%s` annotation or change the return type.", Utilities.getTypeName(getTypeCache().idempotent));
                cacheData = null;
            } else if (idempotentMirror != null && hasOutParameter) {
                emitError(methodToGenerate, idempotentMirror, "A method with a cached return value cannot have an `Out` parameter.%n" +
                                "To fix this, remove the `%s` annotation.", Utilities.getTypeName(getTypeCache().idempotent));
                cacheData = null;
            } else if (idempotentMirror != null || (enforceIdempotent && !noReturnType && !hasOutParameter)) {
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
            if (referenceReturnType && hasCustomOutParameter) {
                emitError(methodToGenerate, idempotentMirror, "A method with a reference return type cannot have a marshalled Out parameter.%n" +
                                "To fix this, split the method into two methods, one having the reference return type, the other with marshalled Out parameter(s).",
                                Utilities.getTypeName(getTypeCache().idempotent));
            } else {
                int overloadId = overloadIds.get(methodToGenerate);
                IsolateDeathHandlerDefinition useIsolateDeathHandler = findIsolateDeathHandler(annotatedType, methodToGenerate);
                if (useIsolateDeathHandler == null) {
                    useIsolateDeathHandler = classIsolateDeathHandler;
                }
                if (useIsolateDeathHandler != null) {
                    List<DeclaredType> methodThrows = methodToGenerateType.getThrownTypes().stream().map((t) -> (DeclaredType) types.erasure(t)).toList();
                    for (DeclaredType checkedException : useIsolateDeathHandler.checkedExceptions()) {
                        if (!Utilities.contains(methodThrows, checkedException, types)) {
                            CharSequence checkedExceptionName = Utilities.getTypeName(checkedException);
                            String inheritedHandlerFix;
                            if (useIsolateDeathHandler == classIsolateDeathHandler) {
                                inheritedHandlerFix = String.format(" If only some `%s` operations throw `%s`, consider moving the `@IsolateDeathHandler` annotation to those specific methods.",
                                                Utilities.getTypeName(annotatedType),
                                                checkedExceptionName);
                            } else {
                                inheritedHandlerFix = "";
                            }
                            CharSequence methodDisplayName = Utilities.printMethod(methodToGenerate);
                            emitError(methodToGenerate, null, "The handler specified by the `@IsolateDeathHandler` annotation throws a Java checked exception `%s`, " +
                                            "but the method `%s` does not declare `%s` in its throws clause.%n" +
                                            "To fix this, change `%s` to throw a Java unchecked exception or checked exception thrown by `%s` method.%s",
                                            checkedExceptionName,
                                            methodDisplayName,
                                            checkedExceptionName,
                                            Utilities.getTypeName(useIsolateDeathHandler.handlerType()),
                                            methodDisplayName,
                                            inheritedHandlerFix);
                        }
                    }
                }
                MethodData methodData = new MethodData(methodToGenerate, methodToGenerateType, overloadId, receiverMethod, retMarshaller, paramsMarshallers, cacheData, useIsolateDeathHandler);
                String entryPointMethodName = endPointMethodProvider.getEntryPointMethodName(methodData);
                List<? extends TypeMirror> entryPointSignature = entryPointMethodName != null ? Utilities.erasure(endPointMethodProvider.getEntryPointSignature(methodData, customDispatch), types)
                                : null;
                String endPointMethodName = endPointMethodProvider.getEndPointMethodName(methodData);
                List<? extends TypeMirror> endPointSignature = Utilities.erasure(endPointMethodProvider.getEndPointSignature(methodData, serviceType, customDispatch), types);
                // Check a collision in the end-point class.
                ExecutableElement collidesWith = hasCollision(endPointMethodNames, endPointMethodName, endPointSignature);
                if (collidesWith == null) {
                    // Check a collision in the start-point class if an entry method is generated in
                    // it.
                    collidesWith = entryPointMethodName != null ? hasCollision(startPointMethodNames, entryPointMethodName, entryPointSignature) : null;
                }
                if (collidesWith != null) {
                    String errorMessageFormat = "The method generated for `%s` conflicts with a generated method for an overloaded method `%s`. " +
                                    "To resolve this, make `%s` final within the `%s` and delegate its functionality to a new abstract method. " +
                                    "This new method should have a unique name, the same signature, and be annotated with `@%s`. " +
                                    "For more details, please refer to the `%s` JavaDoc.";
                    CharSequence newMethod = Utilities.printMethod(methodToGenerate);
                    CharSequence prevMethod = Utilities.printMethod(collidesWith);
                    CharSequence receiverMethodName = Utilities.getTypeName(getTypeCache().receiverMethod);
                    String errorMessage = String.format(errorMessageFormat, newMethod, prevMethod, newMethod,
                                    Utilities.getTypeName(annotatedType), receiverMethodName, receiverMethodName);
                    emitError(methodToGenerate, null, errorMessage, Utilities.getTypeName(annotatedType));
                } else {
                    toGenerate.add(methodData);
                }
                endPointMethodNames.computeIfAbsent(endPointMethodName, (n) -> new ArrayList<>()).add(new SimpleImmutableEntry<>(new Signature(endPointSignature), methodToGenerate));
                if (entryPointMethodName != null) {
                    startPointMethodNames.computeIfAbsent(entryPointMethodName, (n) -> new ArrayList<>()).add(new SimpleImmutableEntry<>(new Signature(entryPointSignature), methodToGenerate));
                }
            }
        }
        return toGenerate;
    }

    private ExecutableElement hasCollision(Map<String, List<Entry<Signature, ExecutableElement>>> overloads, String name, List<? extends TypeMirror> signature) {
        List<Entry<Signature, ExecutableElement>> usedSignatures = overloads.get(name);
        if (usedSignatures != null) {
            for (Entry<Signature, ExecutableElement> usedSignature : usedSignatures) {
                if (Utilities.equals(signature, usedSignature.getKey().parameterTypes(), types)) {
                    return usedSignature.getValue();
                }
            }
        }
        return null;
    }

    private Map<String, List<Entry<Signature, ExecutableElement>>> computeBaseClassOverrides(DeclaredType forType) {
        Map<String, List<Entry<Signature, ExecutableElement>>> map = new HashMap<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements.getAllMembers((TypeElement) forType.asElement()))) {
            if (!method.getModifiers().contains(Modifier.PRIVATE)) {
                String name = method.getSimpleName().toString();
                ExecutableType methodToGenerateType = (ExecutableType) types.asMemberOf(forType, method);
                Signature signature = new Signature(Utilities.erasure(methodToGenerateType.getParameterTypes(), types));
                map.computeIfAbsent(name, (n) -> new ArrayList<>()).add(new SimpleImmutableEntry<>(signature, method));
            }
        }
        return map;
    }

    private List<ExecutableElement> methodsToGenerate(DeclaredType annotatedType, Iterable<? extends ExecutableElement> methods) {
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
            if (types.isSameType(getTypeCache().object, owner.asType()) || types.isSameType(getTypeCache().foreignObject, owner.asType())) {
                continue;
            }
            res.add(method);
        }
        res.sort(Utilities.executableElementComparator(elements, types));
        return res;
    }

    private static PackageElement getPackage(Element element) {
        Element enclosing = element.getEnclosingElement();
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            enclosing = enclosing.getEnclosingElement();
        }
        return (PackageElement) enclosing;
    }

    private List<MarshallerData> lookupMarshallers(ExecutableElement method, List<? extends VariableElement> parameters, List<? extends TypeMirror> parameterTypes,
                    boolean customDispatch, Map<String, DeclaredType> alwaysByLocalReference, Map<String, DeclaredType> alwaysByRemoteReference) {
        List<MarshallerData> res = new ArrayList<>(parameterTypes.size());
        for (int i = 0; i < parameters.size(); i++) {
            res.add(lookupMarshaller(method, parameterTypes.get(i), parameters.get(i).getAnnotationMirrors(), customDispatch, alwaysByLocalReference, alwaysByRemoteReference));
        }
        return res;
    }

    private MarshallerData lookupMarshaller(ExecutableElement method, TypeMirror type, List<? extends AnnotationMirror> annotationMirrors, boolean customDispatch,
                    Map<String, DeclaredType> alwaysByLocalReference, Map<String, DeclaredType> alwaysByRemoteReference) {
        MarshallerData res;
        if (type.getKind().isPrimitive() || type.getKind() == TypeKind.VOID) {
            res = MarshallerData.NO_MARSHALLER;
        } else if (types.isSameType(getTypeCache().string, type)) {
            res = MarshallerData.NO_MARSHALLER;
        } else if (isPrimitiveArray(type)) {
            res = MarshallerData.annotatedPrimitiveArray(findDirectionModifier(annotationMirrors, getTypeCache().in), findDirectionModifier(annotationMirrors, getTypeCache().out));
        } else {
            AnnotationMirror byReferenceAnnotationMirror;
            DeclaredType alwaysByReferenceEndPointClass = null;
            if ((byReferenceAnnotationMirror = findByReference(annotationMirrors, getTypeCache().byLocalReference)) != null ||
                            (alwaysByReferenceEndPointClass = resolveAlwaysByReference(type, alwaysByLocalReference)) != null) {
                if (byReferenceAnnotationMirror != null && isPeerReference(byReferenceAnnotationMirror)) {
                    validatePeerReference(method, type, getTypeCache().byLocalReference, byReferenceAnnotationMirror);
                    res = MarshallerData.peerReference(false);
                } else {
                    res = createReferenceMarshallerData(method, type, annotationMirrors, byReferenceAnnotationMirror, alwaysByReferenceEndPointClass,
                                    otherConfiguration, customDispatch, false);
                }
            } else if ((byReferenceAnnotationMirror = findByReference(annotationMirrors, getTypeCache().byRemoteReference)) != null ||
                            (alwaysByReferenceEndPointClass = resolveAlwaysByReference(type, alwaysByRemoteReference)) != null) {
                if (byReferenceAnnotationMirror != null && isPeerReference(byReferenceAnnotationMirror)) {
                    validatePeerReference(method, type, getTypeCache().byRemoteReference, byReferenceAnnotationMirror);
                    res = MarshallerData.peerReference(true);
                } else {
                    res = createReferenceMarshallerData(method, type, annotationMirrors, byReferenceAnnotationMirror, alwaysByReferenceEndPointClass,
                                    myConfiguration, customDispatch, true);
                }
            } else {
                List<? extends AnnotationMirror> annotations = filterMarshallerAnnotations(annotationMirrors, ignoreAnnotations);
                annotations.stream().map(AnnotationMirror::getAnnotationType).forEach(marshallerAnnotations::add);
                res = MarshallerData.marshalled(type, findDirectionModifier(annotationMirrors, getTypeCache().in), findDirectionModifier(annotationMirrors, getTypeCache().out), annotations);
            }
        }
        return res;
    }

    private boolean isPeerReference(AnnotationMirror byReferenceMirror) {
        DeclaredType target = (DeclaredType) getAnnotationValue(byReferenceMirror, "value");
        return types.isSameType(getTypeCache().peer, target);
    }

    private void validatePeerReference(ExecutableElement method, TypeMirror type, DeclaredType annotationType, AnnotationMirror byReferenceAnnotationMirror) {
        if (!types.isSameType(type, getTypeCache().object)) {
            emitError(method, byReferenceAnnotationMirror, "A parameter annotated by `%s(%s.class)` must have `Object` type.",
                            Utilities.getTypeName(annotationType), Utilities.getTypeName(getTypeCache().peer));
        }
    }

    private MarshallerData createReferenceMarshallerData(ExecutableElement method, TypeMirror parameterType, List<? extends AnnotationMirror> parameterAnnotationMirrors,
                    AnnotationMirror byReferenceAnnotationMirror, DeclaredType alwaysByReferenceEndPointClass,
                    Configuration primary, boolean customDispatch, boolean sameDirection) {
        boolean useCustomReceiverAccessor = byReferenceAnnotationMirror != null && (boolean) getAnnotationValueWithDefaults(byReferenceAnnotationMirror, "useCustomReceiverAccessor");
        if (useCustomReceiverAccessor && !customDispatch) {
            emitError(method, byReferenceAnnotationMirror, "`UseCustomReceiverAccessor` can be used only for types with a custom dispatch.");
        }
        DeclaredType referenceType = byReferenceAnnotationMirror != null ? (DeclaredType) getAnnotationValue(byReferenceAnnotationMirror, "value") : alwaysByReferenceEndPointClass;
        TypeElement referenceElement = (TypeElement) referenceType.asElement();
        VariableElement nonDefaultReceiver = null;
        ExecutableElement customDispatchFactory;
        ConstructorSelector proxyConstructorValidator = null;
        if ((customDispatchFactory = customDispatchFactoryIn(ElementFilter.methodsIn(referenceElement.getEnclosedElements()), false, null)) != null) {
            if (processor.getAnnotation(referenceElement, primary.getHandledAnnotationType()) != null) {
                referenceType = primary.getPeerType();
            } else {
                CharSequence referenceTypeName = Utilities.getTypeName(referenceType);
                CharSequence myAnnotationName = Utilities.getTypeName(primary.getHandledAnnotationType());
                emitError(method, byReferenceAnnotationMirror, "The `%s` must be a custom dispatch class annotated by `%s`.%n" +
                                "To fix this annotate the `%s` with `%s`.", referenceTypeName, myAnnotationName, referenceElement, myAnnotationName);
            }
            referenceElement = (TypeElement) referenceType.asElement();
        } else {
            if (!types.isSameType(referenceType, getTypeCache().foreignObject)) {
                if (!types.isSubtype(referenceType, getTypeCache().foreignObject)) {
                    CharSequence foreignObject = Utilities.getTypeName(getTypeCache().foreignObject);
                    emitError(method, byReferenceAnnotationMirror, "Type referred by @ByReference.value must implement %s or have a custom dispatch.%n" +
                                    "To fix this %s should implement %s.", foreignObject, Utilities.getTypeName(referenceType), foreignObject);
                }
                if (processor.getAnnotation(referenceElement, primary.getHandledAnnotationType()) == null) {
                    CharSequence referenceTypeName = Utilities.getTypeName(referenceType);
                    CharSequence myAnnotationName = Utilities.getTypeName(primary.getHandledAnnotationType());
                    emitError(method, byReferenceAnnotationMirror, "The `%s` must be annotated by `%s`.%n" +
                                    "To fix this annotate the `%s` with `%s`.", referenceTypeName, myAnnotationName, referenceElement, myAnnotationName);
                }
                proxyConstructorValidator = ConstructorSelector.withParameters(AbstractServiceParser.this, method, byReferenceAnnotationMirror, referenceElement, types, List.of(), true);
            }
        }
        if (proxyConstructorValidator != null) {
            ElementFilter.constructorsIn(referenceElement.getEnclosedElements()).forEach(proxyConstructorValidator);
            proxyConstructorValidator.get();
        }
        if (parameterType.getKind() == TypeKind.ARRAY) {
            return MarshallerData.referenceArray(referenceType,
                            findDirectionModifier(parameterAnnotationMirrors, getTypeCache().in),
                            findDirectionModifier(parameterAnnotationMirrors, getTypeCache().out),
                            useCustomReceiverAccessor, sameDirection, nonDefaultReceiver, customDispatchFactory);
        } else {
            return MarshallerData.reference(referenceType, useCustomReceiverAccessor, sameDirection, nonDefaultReceiver, customDispatchFactory);
        }
    }

    private static DeclaredType resolveAlwaysByReference(TypeMirror type, Map<String, DeclaredType> alwaysByReference) {
        TypeMirror useType = type;
        if (useType.getKind() == TypeKind.ARRAY) {
            useType = ((ArrayType) useType).getComponentType();
        }
        if (useType.getKind() == TypeKind.DECLARED) {
            return alwaysByReference.get(Utilities.getQualifiedName(useType));
        }
        return null;
    }

    private List<? extends AnnotationMirror> filterMarshallerAnnotations(List<? extends AnnotationMirror> annotationMirrors,
                    Collection<? extends DeclaredType> ignoredAnnotations) {
        List<AnnotationMirror> result = new ArrayList<>();
        for (AnnotationMirror mirror : annotationMirrors) {
            DeclaredType type = mirror.getAnnotationType();
            if (!isIgnoredAnnotation(type, ignoredAnnotations) && processor.getAnnotation(type.asElement(), getTypeCache().marshallerAnnotation) != null) {
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

    private static Set<Modifier> staticNonPrivate(Set<Modifier> modifiers) {
        Set<Modifier> newModifiers = EnumSet.noneOf(Modifier.class);
        // Use dispatch resolver modifiers
        newModifiers.addAll(modifiers);
        // Remove private if present
        newModifiers.remove(Modifier.PRIVATE);
        // Add static if missing
        newModifiers.add(Modifier.STATIC);
        // Remove final if present, it's illegal for static methods
        newModifiers.remove(Modifier.FINAL);
        // Remove abstract if present, it's illegal for static methods
        newModifiers.remove(Modifier.ABSTRACT);
        return newModifiers;
    }

    private static boolean isPrimitiveArray(TypeMirror type) {
        return type.getKind() == TypeKind.ARRAY && ((ArrayType) type).getComponentType().getKind().isPrimitive();
    }

    private AnnotationMirror findByReference(List<? extends AnnotationMirror> annotationMirrors, DeclaredType annotation) {
        return findAnnotationMirror(annotationMirrors, annotation);
    }

    private AnnotationMirror findAnnotationMirror(List<? extends AnnotationMirror> annotationMirrors, DeclaredType annotationType) {
        for (AnnotationMirror mirror : annotationMirrors) {
            DeclaredType type = mirror.getAnnotationType();
            if (types.isSameType(annotationType, type)) {
                return mirror;
            }
        }
        return null;
    }

    private DirectionData findDirectionModifier(List<? extends AnnotationMirror> annotationMirrors, TypeMirror directionAnnotationType) {
        for (AnnotationMirror mirror : annotationMirrors) {
            DeclaredType type = mirror.getAnnotationType();
            if (types.isSameType(directionAnnotationType, type)) {
                String offset = (String) getAnnotationValue(mirror, "arrayOffsetParameter");
                String length = (String) getAnnotationValue(mirror, "arrayLengthParameter");
                boolean trim = false;
                if (resolveElement(mirror, "trimToResult") != null) {
                    Boolean value = (Boolean) getAnnotationValue(mirror, "trimToResult");
                    trim = value != null && value;
                }
                return new DirectionData(offset, length, trim);
            }
        }
        return null;
    }

    private CharSequence findReceiverMethod(ExecutableElement executable, ExecutableType executableType, DeclaredType serviceType) {
        AnnotationMirror receiverMethodMirror = processor.getAnnotation(executable, getTypeCache().receiverMethod);
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
            emitError(executable, receiverMethodMirror, "A method `%s%s` is not found in the `%s`. " +
                            "The receiver method must have the same arguments as the annotated method and must exist in the bridged type.",
                            receiverMethodName, getSignature(executableType.getParameterTypes()), Utilities.getTypeName(serviceType));
        } else if (found.getModifiers().contains(Modifier.STATIC) || found.getModifiers().contains(Modifier.PRIVATE)) {
            emitError(executable, receiverMethodMirror, "The receiver method `%s` must be a non-private instance method.", receiverMethodName);
        }
        return receiverMethodName;
    }

    private Map<String, DeclaredType> findAlwaysByReference(Element annotatedElement, DeclaredType annotation, DeclaredType repeatedAnnotation) {
        AnnotationMirror alwaysByReference = processor.getAnnotation(annotatedElement, annotation);
        AnnotationMirror alwaysByReferenceRepeated = processor.getAnnotation(annotatedElement, repeatedAnnotation);
        if (alwaysByReference != null) {
            return loadAlwaysByReference(alwaysByReference);
        } else if (alwaysByReferenceRepeated != null) {
            List<? extends AnnotationValue> alwaysByReferenceMirrors = asAnnotationValuesList(getAnnotationValue(alwaysByReferenceRepeated, "value"));
            Map<String, DeclaredType> result = new HashMap<>(alwaysByReferenceMirrors.size());
            for (AnnotationValue value : alwaysByReferenceMirrors) {
                result.putAll(loadAlwaysByReference((AnnotationMirror) value.getValue()));
            }
            return result;
        } else {
            return Collections.emptyMap();
        }
    }

    private IsolateDeathHandlerDefinition findIsolateDeathHandler(DeclaredType definitionType, Element annotatedElement) {
        AnnotationMirror handledAnnotation = processor.getAnnotation(annotatedElement, getTypeCache().isolateDeathHandler);
        if (handledAnnotation == null) {
            return null;
        }
        DeclaredType handler = (DeclaredType) getAnnotationValue(handledAnnotation, "value");
        TypeElement handlerElement = (TypeElement) handler.asElement();
        ExecutableElement found = null;
        for (ExecutableElement method : ElementFilter.methodsIn(handlerElement.getEnclosedElements())) {
            Set<Modifier> mods = method.getModifiers();
            if ("handleIsolateDeath".contentEquals(method.getSimpleName()) && mods.contains(Modifier.STATIC) && !mods.contains(Modifier.PRIVATE) &&
                            method.getParameters().size() == 2) {
                ExecutableType methodType = (ExecutableType) method.asType();
                TypeMirror returnType = methodType.getReturnType();
                if (!types.isSameType(types.getNoType(TypeKind.VOID), returnType)) {
                    continue;
                }
                List<? extends TypeMirror> translateParameterTypes = methodType.getParameterTypes();
                if (!types.isAssignable(definitionType, translateParameterTypes.get(0))) {
                    continue;
                }
                if (!types.isSameType(getTypeCache().isolateDeathException, translateParameterTypes.get(1))) {
                    continue;
                }
                found = method;
                break;
            }
        }
        List<DeclaredType> checkedExceptions;
        if (found != null) {
            checkedExceptions = found.getThrownTypes().stream().map((t) -> (DeclaredType) types.erasure(t)).filter(this::isChecked).toList();
        } else {
            emitError(annotatedElement, handledAnnotation,
                            "The class specified in the `@IsolateDeathHandler` annotation must declare an accessible static void method named `handleIsolateDeath`. " +
                                            "This method must accept two parameters: the receiver object and an `IsolateDeathException`. " +
                                            "It must throw either a Java unchecked exception or an exception declared by the annotated method.%n" +
                                            "To resolve this, add the following method to %s:%n" +
                                            "  static void handleIsolateDeath(Object receiver, IsolateDeathException exception)",
                            Utilities.getTypeName(handler));
            return null;
        }

        return new IsolateDeathHandlerDefinition(handler, checkedExceptions);
    }

    private boolean isChecked(DeclaredType exception) {
        if (!types.isSubtype(exception, getTypeCache().throwable)) {
            throw new IllegalArgumentException("Expected throwable parameter, actual parameter " + Utilities.getTypeName(exception));
        }
        return !types.isSubtype(exception, getTypeCache().runtimeException) && !types.isSubtype(exception, getTypeCache().error);
    }

    @SuppressWarnings("unchecked")
    private static List<? extends AnnotationValue> asAnnotationValuesList(Object value) {
        return (List<? extends AnnotationValue>) value;
    }

    private static Map<String, DeclaredType> loadAlwaysByReference(AnnotationMirror loadByReferenceMirror) {
        DeclaredType type = (DeclaredType) getAnnotationValue(loadByReferenceMirror, "type");
        DeclaredType startPointClass = (DeclaredType) getAnnotationValue(loadByReferenceMirror, "startPointClass");
        return Collections.singletonMap(Utilities.getQualifiedName(type), startPointClass);
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

    private static CharSequence getSignature(List<? extends TypeMirror> types) {
        return getSignature(types, false, false);
    }

    private static CharSequence getSignature(List<? extends TypeMirror> types, boolean anyBefore, boolean anyAfter) {
        String prefix = anyBefore ? "(...," : "(";
        String suffix = anyAfter ? ",...)" : ")";
        return types.stream().map(Utilities::getTypeName).collect(Collectors.joining(", ", prefix, suffix));
    }

    private static List<? extends VariableElement> findConstructorParams(DeclaredType type, ConstructorSelector constructorSelector) {
        TypeElement te = (TypeElement) type.asElement();
        ElementFilter.constructorsIn(te.getEnclosedElements()).forEach(constructorSelector::accept);
        ExecutableElement selectedConstructor = constructorSelector.get();
        return selectedConstructor == null ? Collections.emptyList() : selectedConstructor.getParameters();
    }

    private abstract static class ConstructorSelector implements Consumer<ExecutableElement>, Supplier<ExecutableElement> {

        protected final AbstractServiceParser parser;
        ExecutableElement accepted;

        ConstructorSelector(AbstractServiceParser parser) {
            this.parser = parser;
        }

        static ConstructorSelector singleConstructor(AbstractServiceParser parser, TypeElement annotatedElement, AnnotationMirror mirror) {
            return new ConstructorSelector(parser) {

                @Override
                public void accept(ExecutableElement executableElement) {
                    if (accepted == null) {
                        accepted = executableElement;
                    } else {
                        parser.emitError(annotatedElement, mirror, "The annotated type must have a single constructor.%n" +
                                        "Fix the ambiguity by removing constructor overloads.");
                    }
                }

                @Override
                public ExecutableElement get() {
                    return accepted;
                }
            };
        }

        static ConstructorSelector withParameters(AbstractServiceParser parser, Element annotatedElement, AnnotationMirror mirror,
                        TypeElement enclosingElement, Types types, List<? extends TypeMirror> signature, boolean sameArity) {
            return new ConstructorSelector(parser) {

                @Override
                public void accept(ExecutableElement executableElement) {
                    List<? extends VariableElement> params = executableElement.getParameters();
                    boolean validParams;
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
                            }
                        }
                    }
                    if (validParams) {
                        if (accepted == null) {
                            accepted = executableElement;
                        } else {
                            invalidConstructor();
                        }
                    }
                }

                @Override
                public ExecutableElement get() {
                    if (accepted == null) {
                        invalidConstructor();
                    }
                    return accepted;
                }

                void invalidConstructor() {
                    CharSequence expectedSignature = getSignature(signature, false, !sameArity);
                    CharSequence name = enclosingElement.getSimpleName();
                    CharSequence constructorToAdd = Utilities.printMethod(Collections.emptySet(), name, types.getNoType(TypeKind.NONE), signature.toArray(new TypeMirror[0]));
                    parser.emitError(annotatedElement, mirror, "The annotated type must have a single constructor with a `%s` signature.%n" +
                                    "To fix this add the `%s` constructor into `%s`.", expectedSignature, constructorToAdd, name);
                }
            };
        }
    }

    static final class DirectionData {
        final String offsetParameter;
        final String lengthParameter;
        final boolean trimToResult;

        DirectionData(String offsetParameter, String lengthParameter, boolean trimToResult) {
            this.offsetParameter = offsetParameter;
            this.lengthParameter = lengthParameter;
            this.trimToResult = trimToResult;
        }

        @Override
        public int hashCode() {
            return Objects.hash(offsetParameter, lengthParameter, trimToResult);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != DirectionData.class) {
                return false;
            }
            DirectionData other = (DirectionData) obj;
            return Objects.equals(offsetParameter, other.offsetParameter) &&
                            Objects.equals(lengthParameter, other.lengthParameter) &&
                            trimToResult == other.trimToResult;
        }

        @Override
        public String toString() {
            return String.format("DirectionData[offsetParameter=%s, lengthParameter=%s, trimToResult=%b", offsetParameter, lengthParameter, trimToResult);
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

    record IsolateDeathHandlerDefinition(DeclaredType handlerType, List<? extends DeclaredType> checkedExceptions) {
    }

    static final class MethodData {

        final ExecutableElement element;
        final ExecutableType type;
        final int overloadId;
        final CharSequence receiverMethod;
        final CacheData cachedData;
        private final MarshallerData returnTypeMarshaller;
        private final List<MarshallerData> parameterMarshallers;
        final IsolateDeathHandlerDefinition isolateDeathHandler;

        MethodData(ExecutableElement element, ExecutableType type, int overloadId, CharSequence receiverMethod,
                        MarshallerData returnTypeMarshaller,
                        List<MarshallerData> parameterMarshallers,
                        CacheData cacheData,
                        IsolateDeathHandlerDefinition isolateDeathHandler) {
            this.element = element;
            this.type = type;
            this.overloadId = overloadId;
            this.receiverMethod = receiverMethod;
            this.cachedData = cacheData;
            this.returnTypeMarshaller = returnTypeMarshaller;
            this.parameterMarshallers = parameterMarshallers;
            this.isolateDeathHandler = isolateDeathHandler;
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

    static class ServiceDefinitionData extends DefinitionData {

        final DeclaredType serviceType;
        final DeclaredType peerType;
        final DeclaredType factory;
        final boolean mutable;
        final List<? extends VariableElement> annotatedTypeConstructorParams;
        final List<? extends CodeBuilder.Parameter> peerConstructorParams;

        final Collection<? extends MethodData> toGenerate;
        final ExecutableElement customDispatchAccessor;
        final ExecutableElement customReceiverAccessor;
        final DeclaredType marshallerConfig;
        final Set<DeclaredType> ignoreAnnotations;
        final Set<DeclaredType> marshallerAnnotations;

        ServiceDefinitionData(DeclaredType annotatedType, DeclaredType serviceType, DeclaredType peerType, DeclaredType factory,
                        boolean mutable, Collection<MethodData> toGenerate, List<? extends VariableElement> annotatedTypeConstructorParams,
                        List<? extends CodeBuilder.Parameter> peerConstructorParams, ExecutableElement customDispatchAccessor,
                        ExecutableElement customReceiverAccessor, DeclaredType marshallerConfig, MarshallerData throwableMarshaller,
                        Set<DeclaredType> ignoreAnnotations, Set<DeclaredType> marshallerAnnotations) {
            super(annotatedType, throwableMarshaller);
            this.serviceType = serviceType;
            this.peerType = peerType;
            this.factory = factory;
            this.mutable = mutable;
            this.annotatedTypeConstructorParams = annotatedTypeConstructorParams;
            this.peerConstructorParams = peerConstructorParams;
            this.toGenerate = toGenerate;
            this.customDispatchAccessor = customDispatchAccessor;
            this.customReceiverAccessor = customReceiverAccessor;
            this.marshallerConfig = marshallerConfig;
            this.ignoreAnnotations = ignoreAnnotations;
            this.marshallerAnnotations = marshallerAnnotations;
        }

        Collection<MarshallerData> getAllCustomMarshallers() {
            SortedSet<MarshallerData> res = new TreeSet<>(Comparator.comparing(a -> a.name));
            collectAllMarshallers(res, MarshallerData.Kind.CUSTOM);
            if (throwableMarshaller != null) {
                res.add(throwableMarshaller);
            }
            return res;
        }

        Collection<MarshallerData> getUserCustomMarshallers() {
            SortedSet<MarshallerData> res = new TreeSet<>(Comparator.comparing(a -> a.name));
            collectAllMarshallers(res, MarshallerData.Kind.CUSTOM);
            return res;
        }

        MarshallerData getCustomMarshaller(DeclaredType forType, DeclaredType annotationType, Types types) {
            return getAllCustomMarshallers().stream().filter((m) -> types.isSameType(forType, m.forType)).filter((m) -> annotationType == null ? m.annotations.isEmpty()
                            : Utilities.contains(m.annotations.stream().map(AnnotationMirror::getAnnotationType).collect(Collectors.toList()), annotationType, types)).findFirst().orElseThrow(
                                            () -> new IllegalStateException(String.format("No custom marshaller for type %s.", Utilities.getTypeName(forType))));
        }

        private void collectAllMarshallers(Set<? super MarshallerData> into, MarshallerData.Kind kind) {
            for (MethodData methodData : toGenerate) {
                Optional.of(methodData.returnTypeMarshaller).filter((m) -> m.kind == kind).ifPresent(into::add);
                methodData.parameterMarshallers.stream().filter((m) -> m.kind == kind).forEach(into::add);
            }
        }

        boolean hasCustomDispatch() {
            return customDispatchAccessor != null;
        }

        @Override
        Iterable<? extends Element> getVerifiedElements() {
            List<Element> result = new ArrayList<>();
            result.add(annotatedType.asElement());
            if (customDispatchAccessor != null) {
                result.add(customDispatchAccessor);
            }
            if (customReceiverAccessor != null) {
                result.add(customReceiverAccessor);
            }
            for (MethodData methodData : toGenerate) {
                result.add(methodData.element);
            }
            return result;
        }
    }

    static final class Configuration {

        private final DeclaredType handledAnnotationType;
        private final Collection<DeclaredType> factoryAnnotationTypes;
        private final DeclaredType peerType;
        private final List<CodeBuilder.Parameter> peerConstructorParameters;

        Configuration(DeclaredType handledAnnotationType, Collection<DeclaredType> factoryAnnotationTypes,
                        DeclaredType peerType,
                        List<CodeBuilder.Parameter> peerConstructorParameters) {
            this.handledAnnotationType = Objects.requireNonNull(handledAnnotationType, "HandledAnnotationType must be non-null.");
            this.factoryAnnotationTypes = Objects.requireNonNull(factoryAnnotationTypes, "FactoryAnnotationType must be non-null.");
            this.peerType = Objects.requireNonNull(peerType, "PeerType must be non null.");
            this.peerConstructorParameters = Objects.requireNonNull(peerConstructorParameters, "PeerConstructorParameters must be non-null.");
        }

        DeclaredType getHandledAnnotationType() {
            return handledAnnotationType;
        }

        Collection<DeclaredType> getFactoryAnnotationTypes() {
            return factoryAnnotationTypes;
        }

        DeclaredType getPeerType() {
            return peerType;
        }

        List<CodeBuilder.Parameter> getPeerConstructorParameters() {
            return peerConstructorParameters;
        }
    }

    abstract static class AbstractEndPointMethodProvider {

        abstract TypeMirror getEntryPointMethodParameterType(MarshallerData marshaller, TypeMirror type);

        abstract TypeMirror getEndPointMethodParameterType(MarshallerData marshaller, TypeMirror type);

        abstract String getEntryPointMethodName(MethodData methodData);

        abstract String getEndPointMethodName(MethodData methodData);

        abstract List<TypeMirror> getEntryPointSignature(MethodData methodData, boolean hasCustomDispatch);

        abstract List<TypeMirror> getEndPointSignature(MethodData methodData, TypeMirror serviceType, boolean hasCustomDispatch);
    }

    private record Signature(List<? extends TypeMirror> parameterTypes) {
    }
}
