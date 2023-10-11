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
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

abstract class AbstractBridgeParser {

    final NativeBridgeProcessor processor;
    final Types types;
    final Elements elements;
    final AbstractTypeCache typeCache;
    final AbstractEndPointMethodProvider endPointMethodProvider;
    private final Configuration myConfiguration;
    private final Configuration otherConfiguration;
    private final Set<DeclaredType> ignoreAnnotations;
    private Set<DeclaredType> marshallerAnnotations;
    private boolean hasErrors;

    AbstractBridgeParser(NativeBridgeProcessor processor, AbstractTypeCache typeCache, AbstractEndPointMethodProvider endPointMethodProvider,
                    Configuration myConfiguration, Configuration otherConfiguration) {
        this.processor = processor;
        this.types = processor.env().getTypeUtils();
        this.elements = processor.env().getElementUtils();
        this.typeCache = typeCache;
        this.endPointMethodProvider = endPointMethodProvider;
        this.myConfiguration = myConfiguration;
        this.otherConfiguration = otherConfiguration;
        this.ignoreAnnotations = new HashSet<>();
        Collections.addAll(this.ignoreAnnotations, typeCache.override, typeCache.suppressWarnings,
                        typeCache.byReference, typeCache.customDispatchFactory, typeCache.customDispatchAccessor,
                        typeCache.customReceiverAccessor, typeCache.idempotent, typeCache.in, typeCache.out,
                        typeCache.rawReference, typeCache.receiverMethod);
    }

    abstract AbstractBridgeGenerator createGenerator(DefinitionData definitionData);

    DefinitionData createDefinitionData(DeclaredType annotatedType, @SuppressWarnings("unused") AnnotationMirror annotation,
                    DeclaredType serviceType, Collection<MethodData> toGenerate, List<? extends VariableElement> annotatedTypeConstructorParams,
                    ExecutableElement customDispatchAccessor, ExecutableElement customReceiverAccessor, VariableElement endPointHandle,
                    DeclaredType jniConfig, MarshallerData throwableMarshaller, Set<DeclaredType> annotationsToIgnore,
                    Set<DeclaredType> annotationsForMarshallerLookup) {
        return new DefinitionData(annotatedType, serviceType, toGenerate, annotatedTypeConstructorParams,
                        customDispatchAccessor, customReceiverAccessor, endPointHandle, jniConfig,
                        throwableMarshaller, annotationsToIgnore, annotationsForMarshallerLookup);
    }

    final DefinitionData parse(Element element) {
        hasErrors = false;
        marshallerAnnotations = new HashSet<>();
        AnnotationMirror handledAnnotation = processor.getAnnotation(element, myConfiguration.getHandledAnnotationType());
        checkAnnotatedType(element, handledAnnotation);
        if (hasErrors) {
            // Fatal error, parsing cannot continue.
            return null;
        }
        TypeElement annotatedElement = (TypeElement) element;
        DeclaredType annotatedType = (DeclaredType) annotatedElement.asType();

        DeclaredType serviceType = findServiceType(annotatedElement, handledAnnotation, myConfiguration.getProxyBaseType());
        if (serviceType == null) {
            // Fatal error, parsing cannot continue.
            return null;
        }

        DeclaredType jniConfig = findJNIConfig(annotatedElement, handledAnnotation);
        // Use only declared methods for custom dispatch directives.
        List<ExecutableElement> methods = ElementFilter.methodsIn(annotatedElement.getEnclosedElements());
        ExecutableElement customDispatchAccessor = customDispatchAccessorIn(methods, serviceType);
        ExecutableElement customReceiverAccessor = customReceiverAccessorIn(methods, customDispatchAccessor, serviceType);
        customDispatchFactoryIn(methods, true, customDispatchAccessor);
        // Process all methods, including inherited.
        methods = ElementFilter.methodsIn(elements.getAllMembers(annotatedElement));
        VariableElement endPointHandle = endPointHandleIn(ElementFilter.fieldsIn(elements.getAllMembers(annotatedElement)), myConfiguration.getProxyBaseType());
        boolean customDispatch = customDispatchAccessor != null || customReceiverAccessor != null;
        if (!customDispatch && endPointHandle == null && !types.isSubtype(annotatedElement.getSuperclass(), myConfiguration.getProxyBaseType())) {
            Set<Modifier> staticMods = Collections.singleton(Modifier.STATIC);
            List<Map.Entry<? extends TypeMirror, ? extends CharSequence>> objectParam = Collections.singletonList(new SimpleImmutableEntry<>(typeCache.object, "receiver"));
            emitError(element, handledAnnotation, "The annotated type must extend `%s`, have a field annotated by `EndPointHandle` or a custom dispatch.%n" +
                            "To bridge an interface extend `%s` and implement the interface.%n" +
                            "To bridge a class extend the class, add the `@%s %s` field and initialize it in the constructor.%n" +
                            "To bridge a class with a custom dispatch add `@%s %s` and `@%s %s` methods.",
                            Utilities.getTypeName(myConfiguration.getProxyBaseType()), Utilities.getTypeName(myConfiguration.getProxyBaseType()),
                            Utilities.getTypeName(typeCache.endPointHandle), Utilities.printField(Collections.singleton(Modifier.FINAL), "delegate", myConfiguration.getProxyBaseType()),
                            Utilities.getTypeName(typeCache.customDispatchAccessor), Utilities.printMethod(staticMods, "resolveDispatch", serviceType, objectParam),
                            Utilities.getTypeName(typeCache.customReceiverAccessor), Utilities.printMethod(staticMods, "resolveReceiver", typeCache.object, objectParam));
        }
        ConstructorSelector constructorSelector;
        if (customDispatch) {
            constructorSelector = ConstructorSelector.singleConstructor(this, annotatedElement, handledAnnotation);
        } else if (endPointHandle != null) {
            constructorSelector = ConstructorSelector.withParameters(this, annotatedElement, handledAnnotation, annotatedElement, types, myConfiguration.getHandleReferenceConstructorTypes(), false);
        } else {
            constructorSelector = ConstructorSelector.withParameters(this, annotatedElement, handledAnnotation, annotatedElement, types, myConfiguration.getSubClassReferenceConstructorTypes(), false);
        }
        Map<String, DeclaredType> alwaysByReference = findAlwaysByReference(element);
        List<? extends VariableElement> constructorParams = findConstructorParams(annotatedType, constructorSelector);
        Collection<MethodData> toGenerate = createMethodData(annotatedType, serviceType, methods, customDispatch,
                        processor.getAnnotation(annotatedElement, typeCache.idempotent) != null, alwaysByReference);
        if (hasErrors) {
            return null;
        } else {
            MarshallerData throwableMarshaller = MarshallerData.marshalled(typeCache.throwable, null, null, Collections.emptyList());
            DefinitionData definitionData = createDefinitionData(annotatedType, handledAnnotation, serviceType, toGenerate, constructorParams, customDispatchAccessor,
                            customReceiverAccessor, endPointHandle, jniConfig, throwableMarshaller, ignoreAnnotations, marshallerAnnotations);
            assertNoExpectedErrors(definitionData);
            return definitionData;
        }
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

    private DeclaredType findJNIConfig(Element element, AnnotationMirror handledAnnotation) {
        DeclaredType jniConfigType = (DeclaredType) getAnnotationValue(handledAnnotation, "jniConfig");
        ExecutableElement getInstanceMethod = null;
        for (ExecutableElement executableElement : ElementFilter.methodsIn(jniConfigType.asElement().getEnclosedElements())) {
            Set<Modifier> modifiers = executableElement.getModifiers();
            if (!modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.PRIVATE)) {
                continue;
            }
            if (!"getInstance".contentEquals(executableElement.getSimpleName())) {
                continue;
            }
            if (!executableElement.getParameters().isEmpty()) {
                continue;
            }
            if (!types.isSameType(typeCache.jniConfig, executableElement.getReturnType())) {
                continue;
            }
            getInstanceMethod = executableElement;
        }
        if (getInstanceMethod == null) {
            emitError(element, handledAnnotation, "JNI config must have a non-private static `getInstance()` method returning `JNIConfig`.%n" +
                            "The `getInstance` method is used by the generated code to look up marshallers.%n" +
                            "To fix this add `static JNIConfig getInstance() { return INSTANCE;}` into `%s`.",
                            jniConfigType.asElement().getSimpleName());
        }
        return jniConfigType;
    }

    private ExecutableElement customDispatchAccessorIn(Iterable<? extends ExecutableElement> methods, DeclaredType serviceType) {
        ExecutableElement res = null;
        AnnotationMirror annotation = null;
        for (ExecutableElement method : methods) {
            AnnotationMirror customDispatchAccessor = processor.getAnnotation(method, typeCache.customDispatchAccessor);
            if (customDispatchAccessor != null) {
                if (res != null) {
                    emitError(method, customDispatchAccessor, "Only a single method can be annotated by the `%s`.%n" +
                                    "Fix the ambiguity by removing the `%s` method or the `%s` method.",
                                    Utilities.getTypeName(typeCache.customDispatchAccessor), Utilities.printMethod(res), Utilities.printMethod(method));
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
                    case 0 -> Collections.singletonList(new SimpleImmutableEntry<>(typeCache.object, "receiver"));
                    case 1 -> {
                        VariableElement parameter = res.getParameters().get(0);
                        yield Collections.singletonList(new SimpleImmutableEntry<>(parameter.asType(), parameter.getSimpleName()));
                    }
                    default -> Collections.singletonList(new SimpleImmutableEntry<>(res.getParameters().get(0).asType(), "receiver"));
                };
                emitError(res, annotation, "A method annotated by `%s` must be a non-private static method with a single parameter and `%s` return type.%n" +
                                "To fix this change the signature to `%s`.", Utilities.getTypeName(typeCache.customDispatchAccessor), Utilities.getTypeName(serviceType),
                                Utilities.printMethod(expectedModifiers, res.getSimpleName(), serviceType, expectedParameters));
            }
        }
        return res;
    }

    private ExecutableElement customReceiverAccessorIn(Iterable<? extends ExecutableElement> methods, ExecutableElement customDispatchAccessor, DeclaredType serviceType) {
        ExecutableElement res = null;
        AnnotationMirror annotation = null;
        for (ExecutableElement method : methods) {
            AnnotationMirror customReceiverAccessor = processor.getAnnotation(method, typeCache.customReceiverAccessor);
            if (customReceiverAccessor != null) {
                if (res != null) {
                    emitError(method, customReceiverAccessor, "Only a single method can be annotated by the `%s`.%n" +
                                    "Fix the ambiguity by removing the `%s` method or the `%s` method.",
                                    Utilities.getTypeName(typeCache.customReceiverAccessor), Utilities.printMethod(res), Utilities.printMethod(method));
                    break;
                } else {
                    res = method;
                    annotation = customReceiverAccessor;
                }
            }
        }
        if (res != null) {
            if (!res.getModifiers().contains(Modifier.STATIC) || res.getModifiers().contains(Modifier.PRIVATE) || res.getParameters().size() != 1 ||
                            !types.isSubtype(typeCache.object, types.erasure(res.getReturnType()))) {
                Set<Modifier> expectedModifiers = staticNonPrivate(res.getModifiers());
                List<Map.Entry<TypeMirror, CharSequence>> expectedParameters;

                switch (res.getParameters().size()) {
                    case 0: {
                        TypeMirror parameterType;
                        if (customDispatchAccessor != null && !customDispatchAccessor.getParameters().isEmpty()) {
                            parameterType = customDispatchAccessor.getParameters().get(0).asType();
                        } else {
                            parameterType = typeCache.object;
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
                                "To fix this change the signature to `%s`.", Utilities.getTypeName(typeCache.customReceiverAccessor),
                                Utilities.printMethod(expectedModifiers, res.getSimpleName(), typeCache.object, expectedParameters));
            }

            if (customDispatchAccessor == null) {
                emitError(res, annotation, "Class with a custom receiver accessor must also provide a custom dispatch accessor.%n" +
                                "To fix this add the `@%s %s` method.", Utilities.getTypeName(typeCache.customDispatchAccessor),
                                Utilities.printMethod(staticNonPrivate(Collections.emptySet()), "resolveDispatch", serviceType, res.getParameters().toArray(new VariableElement[0])));
            } else if (customDispatchAccessor.getParameters().size() == 1 && res.getParameters().size() == 1) {
                if (!types.isSameType(customDispatchAccessor.getParameters().get(0).asType(), res.getParameters().get(0).asType())) {
                    emitError(res, annotation, "The custom receiver accessor must have the same parameter type as the custom dispatch accessor.");
                }
            }
        } else if (customDispatchAccessor != null) {
            AnnotationMirror customDispatchAccessorAnnotation = processor.getAnnotation(customDispatchAccessor, typeCache.customDispatchAccessor);
            emitError(customDispatchAccessor, customDispatchAccessorAnnotation, "Classes with a custom dispatch accessor must also provide a custom receiver accessor.%n" +
                            "To fix this add the `@%s %s` method.", Utilities.getTypeName(typeCache.customReceiverAccessor),
                            Utilities.printMethod(staticNonPrivate(Collections.emptySet()), "resolveReceiver", typeCache.object,
                                            customDispatchAccessor.getParameters().toArray(new VariableElement[0])));
        }
        return res;
    }

    private VariableElement endPointHandleIn(Iterable<? extends VariableElement> fields, DeclaredType baseType) {
        VariableElement res = null;
        AnnotationMirror annotation = null;
        for (VariableElement field : fields) {
            AnnotationMirror endPointHandle = processor.getAnnotation(field, typeCache.endPointHandle);
            if (endPointHandle != null) {
                if (res != null) {
                    emitError(field, endPointHandle, "Only a single field can be annotated by the `%s`.%n" +
                                    "Fix the ambiguity by removing the `%s` field or the `%s` field.",
                                    Utilities.getTypeName(typeCache.endPointHandle), Utilities.printField(res), Utilities.printField(field));
                    break;
                } else {
                    res = field;
                    annotation = endPointHandle;
                }
            }
        }
        if (res != null) {
            if (res.getModifiers().contains(Modifier.PRIVATE) || !types.isSubtype(res.asType(), baseType)) {
                Set<Modifier> expectedModifiers = EnumSet.noneOf(Modifier.class);
                expectedModifiers.addAll(res.getModifiers());
                // Remove private if present
                expectedModifiers.remove(Modifier.PRIVATE);
                emitError(res, annotation, "A field annotated by `%s` must be a non-private field of `%s` type.%n" +
                                "To fix this change the signature to `%s`.", Utilities.getTypeName(typeCache.endPointHandle),
                                Utilities.getTypeName(baseType),
                                Utilities.printField(expectedModifiers, res.getSimpleName(), baseType));
            }
        }
        return res;
    }

    private ExecutableElement customDispatchFactoryIn(Iterable<? extends ExecutableElement> methods, boolean verify, ExecutableElement customDispatchAccessor) {
        ExecutableElement res = null;
        AnnotationMirror annotation = null;
        for (ExecutableElement method : methods) {
            AnnotationMirror customDispatchFactory = processor.getAnnotation(method, typeCache.customDispatchFactory);
            if (customDispatchFactory != null) {
                if (verify && res != null) {
                    emitError(method, customDispatchFactory, "Only a single method can be annotated by the `%s`.%n" +
                                    "Fix the ambiguity by removing the `%s` method or the `%s` method.",
                                    Utilities.getTypeName(typeCache.customDispatchFactory), Utilities.printMethod(res), Utilities.printMethod(method));
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
                                Utilities.getTypeName(typeCache.customDispatchFactory), Utilities.getTypeName(typeCache.customDispatchAccessor),
                                Utilities.getTypeName(typeCache.customReceiverAccessor));
            } else {
                List<? extends VariableElement> customDispatchAccessorParams = customDispatchAccessor.getParameters();
                TypeMirror expectedReturnType = customDispatchAccessorParams.size() == 1 ? customDispatchAccessorParams.get(0).asType() : typeCache.object;
                if (!res.getModifiers().contains(Modifier.STATIC) || res.getModifiers().contains(Modifier.PRIVATE) || res.getParameters().size() != 1 ||
                                !types.isSubtype(res.getReturnType(), expectedReturnType)) {
                    Set<Modifier> expectedModifiers = staticNonPrivate(res.getModifiers());
                    List<Map.Entry<TypeMirror, CharSequence>> expectedParameters = Collections.singletonList(new SimpleImmutableEntry<>(typeCache.object, "receiver"));
                    emitError(res, annotation, "A method annotated by `%s` must be a non-private static method with a single object parameter and `%s` return type.%n" +
                                    "To fix this change the signature to `%s`.", Utilities.getTypeName(typeCache.customDispatchFactory), Utilities.getTypeName(expectedReturnType),
                                    Utilities.printMethod(expectedModifiers, res.getSimpleName(), expectedReturnType, expectedParameters));
                }
            }
        }
        return res;
    }

    private DeclaredType findServiceType(TypeElement typeElement, AnnotationMirror annotation, DeclaredType proxyType) {
        TypeMirror superClass = typeElement.getSuperclass();
        if (superClass.getKind() != TypeKind.DECLARED) {
            return null;
        }
        String fix;
        List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
        if (!types.isSubtype(superClass, proxyType) && !types.isSameType(superClass, typeCache.object)) {
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
                    boolean customDispatch, boolean enforceIdempotent, Map<String, DeclaredType> alwaysByReference) {
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
        Map<String, List<Entry<Signature, ExecutableElement>>> endPointMethodNames = computeBaseClassOverrides(typeCache.object);
        for (ExecutableElement methodToGenerate : methodsToGenerate) {
            ExecutableType methodToGenerateType = (ExecutableType) types.asMemberOf(annotatedType, methodToGenerate);
            MarshallerData retMarshaller = lookupMarshaller(methodToGenerate, methodToGenerateType.getReturnType(), methodToGenerate.getAnnotationMirrors(), customDispatch, alwaysByReference);
            List<? extends VariableElement> parameters = methodToGenerate.getParameters();
            List<? extends TypeMirror> parameterTypes = methodToGenerateType.getParameterTypes();
            List<MarshallerData> paramsMarshallers = new ArrayList<>();
            if (customDispatch) {
                if (parameters.isEmpty() || types.erasure(parameterTypes.get(0)).getKind() != TypeKind.DECLARED) {
                    emitError(methodToGenerate, null, "In a class with a custom dispatch, the first method parameter must be the receiver.%n" +
                                    "For a class with a custom dispatch, make the method `final` to prevent its generation.%n" +
                                    "For a class that has no custom dispatch, remove methods annotated by `%s` and `%s`.",
                                    Utilities.getTypeName(typeCache.customDispatchAccessor), Utilities.getTypeName(typeCache.customReceiverAccessor));
                } else {
                    paramsMarshallers.add(MarshallerData.NO_MARSHALLER);
                    parameters = parameters.subList(1, parameters.size());
                    parameterTypes = parameterTypes.subList(1, parameterTypes.size());
                }
            }
            paramsMarshallers.addAll(lookupMarshallers(methodToGenerate, parameters, parameterTypes, customDispatch, alwaysByReference));
            CacheData cacheData;
            AnnotationMirror idempotentMirror = processor.getAnnotation(methodToGenerate, typeCache.idempotent);
            boolean noReturnType = methodToGenerateType.getReturnType().getKind() == TypeKind.VOID;
            boolean referenceReturnType = retMarshaller.kind == MarshallerData.Kind.RAW_REFERENCE || retMarshaller.kind == MarshallerData.Kind.REFERENCE;
            boolean hasOutParameter = paramsMarshallers.stream().anyMatch((md) -> md.out != null);
            boolean hasCustomOutParameter = paramsMarshallers.stream().anyMatch((md) -> md.kind == MarshallerData.Kind.CUSTOM && md.out != null);
            if (idempotentMirror != null && noReturnType) {
                emitError(methodToGenerate, idempotentMirror, "A method with a cached return value must have a non-void return type.%n" +
                                "To fix this remove the `%s` annotation or change the return type.", Utilities.getTypeName(typeCache.idempotent));
                cacheData = null;
            } else if (idempotentMirror != null && hasOutParameter) {
                emitError(methodToGenerate, idempotentMirror, "A method with a cached return value cannot have an `Out` parameter.%n" +
                                "To fix this, remove the `%s` annotation.", Utilities.getTypeName(typeCache.idempotent));
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
                                Utilities.getTypeName(typeCache.idempotent));
            } else {
                int overloadId = overloadIds.get(methodToGenerate);
                MethodData methodData = new MethodData(methodToGenerate, methodToGenerateType, overloadId, receiverMethod, retMarshaller, paramsMarshallers, cacheData);
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
                    CharSequence receiverMethodName = Utilities.getTypeName(typeCache.receiverMethod);
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
            if (types.isSameType(typeCache.object, owner.asType()) || types.isSameType(myConfiguration.getProxyBaseType(), owner.asType())) {
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
                    boolean customDispatch, Map<String, DeclaredType> alwaysByReference) {
        List<MarshallerData> res = new ArrayList<>(parameterTypes.size());
        for (int i = 0; i < parameters.size(); i++) {
            res.add(lookupMarshaller(method, parameterTypes.get(i), parameters.get(i).getAnnotationMirrors(), customDispatch, alwaysByReference));
        }
        return res;
    }

    private MarshallerData lookupMarshaller(ExecutableElement method, TypeMirror type, List<? extends AnnotationMirror> annotationMirrors, boolean customDispatch,
                    Map<String, DeclaredType> alwaysByReference) {
        MarshallerData res;
        if (type.getKind().isPrimitive() || type.getKind() == TypeKind.VOID) {
            res = MarshallerData.NO_MARSHALLER;
        } else if (types.isSameType(typeCache.string, type)) {
            res = MarshallerData.NO_MARSHALLER;
        } else if (isPrimitiveArray(type)) {
            res = MarshallerData.annotatedPrimitiveArray(findDirectionModifier(annotationMirrors, typeCache.in), findDirectionModifier(annotationMirrors, typeCache.out));
        } else {
            AnnotationMirror annotationMirror;
            DeclaredType endPointClass = null;
            if ((annotationMirror = findByReference(annotationMirrors)) != null || (endPointClass = resolveAlwaysByReference(type, alwaysByReference)) != null) {
                DeclaredType referenceType = annotationMirror != null ? (DeclaredType) getAnnotationValue(annotationMirror, "value") : endPointClass;
                TypeElement referenceElement = (TypeElement) referenceType.asElement();
                boolean useCustomReceiverAccessor = annotationMirror != null && (boolean) getAnnotationValueWithDefaults(annotationMirror, "useCustomReceiverAccessor");
                if (useCustomReceiverAccessor && !customDispatch) {
                    emitError(method, annotationMirror, "`UseCustomReceiverAccessor` can be used only for types with a custom dispatch.");
                }
                boolean sameDirection = true;
                VariableElement nonDefaultReceiver = null;
                ExecutableElement customDispatchFactory = null;
                ConstructorSelector selector;
                if (types.isSubtype(referenceType, myConfiguration.getProxyBaseType())) {
                    selector = ConstructorSelector.withParameters(AbstractBridgeParser.this, method, annotationMirror, referenceElement, types, myConfiguration.getSubClassReferenceConstructorTypes(),
                                    true);
                } else if (types.isSubtype(referenceType, otherConfiguration.getProxyBaseType())) {
                    selector = ConstructorSelector.withParameters(AbstractBridgeParser.this, method, annotationMirror, referenceElement, types,
                                    otherConfiguration.getSubClassReferenceConstructorTypes(), true);
                    sameDirection = false;
                } else if ((customDispatchFactory = customDispatchFactoryIn(ElementFilter.methodsIn(referenceElement.getEnclosedElements()), false, null)) != null) {
                    if (processor.getAnnotation(referenceElement, myConfiguration.getHandledAnnotationType()) != null) {
                        referenceType = myConfiguration.getProxyBaseType();
                    } else if (processor.getAnnotation(referenceElement, otherConfiguration.getHandledAnnotationType()) != null) {
                        sameDirection = false;
                        referenceType = otherConfiguration.getProxyBaseType();
                    } else {
                        CharSequence referenceTypeName = Utilities.getTypeName(referenceType);
                        CharSequence myAnnotationName = Utilities.getTypeName(myConfiguration.getHandledAnnotationType());
                        CharSequence otherAnnotationName = Utilities.getTypeName(otherConfiguration.getHandledAnnotationType());
                        emitError(method, annotationMirror, "The `%s` is must be a custom dispatch class annotated by `%s` or `%s`.%n" +
                                        "To fix this annotate the `%s` with `%s` or `%s`.", referenceTypeName, myAnnotationName, otherAnnotationName,
                                        referenceElement, myAnnotationName, otherAnnotationName);
                    }
                    referenceElement = (TypeElement) referenceType.asElement();
                    selector = null;
                } else {
                    selector = null;
                    nonDefaultReceiver = ElementFilter.fieldsIn(referenceElement.getEnclosedElements()).stream().filter(
                                    (f) -> processor.getAnnotation(f, typeCache.endPointHandle) != null).findAny().orElse(null);
                    if (nonDefaultReceiver != null) {
                        if (types.isSubtype(nonDefaultReceiver.asType(), myConfiguration.getProxyBaseType())) {
                            selector = ConstructorSelector.withParameters(AbstractBridgeParser.this, method, annotationMirror, referenceElement, types,
                                            myConfiguration.getHandleReferenceConstructorTypes(), true);
                        } else if (types.isSubtype(nonDefaultReceiver.asType(), otherConfiguration.getProxyBaseType())) {
                            selector = ConstructorSelector.withParameters(AbstractBridgeParser.this, method, annotationMirror, referenceElement, types,
                                            otherConfiguration.getHandleReferenceConstructorTypes(), true);
                            sameDirection = false;
                        }
                    }
                    if (selector == null) {
                        emitError(method, annotationMirror, "Cannot lookup a field annotated by `%s` in the `%s`.", Utilities.getTypeName(typeCache.endPointHandle),
                                        Utilities.getTypeName(referenceType));
                    }
                }
                if (selector != null) {
                    ElementFilter.constructorsIn(referenceElement.getEnclosedElements()).forEach(selector);
                    selector.get();
                }
                AnnotationMirror annotation = processor.getAnnotation(referenceElement,
                                sameDirection ? myConfiguration.getHandledAnnotationType() : otherConfiguration.getHandledAnnotationType());
                if (type.getKind() == TypeKind.ARRAY) {
                    res = MarshallerData.referenceArray(referenceType, annotation, findDirectionModifier(annotationMirrors, typeCache.in), findDirectionModifier(annotationMirrors, typeCache.out),
                                    useCustomReceiverAccessor, sameDirection, nonDefaultReceiver, customDispatchFactory);
                } else {
                    res = MarshallerData.reference(referenceType, annotation, useCustomReceiverAccessor, sameDirection, nonDefaultReceiver, customDispatchFactory);
                }
            } else if ((annotationMirror = findRawReference(annotationMirrors)) != null) {
                if (!types.isSameType(type, typeCache.object)) {
                    emitError(method, annotationMirror, "A parameter annotated by `%s` must have `Object` type.");
                }
                res = MarshallerData.RAW_REFERENCE;
            } else {
                List<? extends AnnotationMirror> annotations = filterMarshallerAnnotations(annotationMirrors, ignoreAnnotations);
                annotations.stream().map(AnnotationMirror::getAnnotationType).forEach(marshallerAnnotations::add);
                res = MarshallerData.marshalled(type, findDirectionModifier(annotationMirrors, typeCache.in), findDirectionModifier(annotationMirrors, typeCache.out), annotations);
            }
        }
        return res;
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
            if (!isIgnoredAnnotation(type, ignoredAnnotations) && processor.getAnnotation(type.asElement(), typeCache.marshallerAnnotation) != null) {
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

    private static boolean isPrimitiveArray(TypeMirror type) {
        return type.getKind() == TypeKind.ARRAY && ((ArrayType) type).getComponentType().getKind().isPrimitive();
    }

    private AnnotationMirror findByReference(List<? extends AnnotationMirror> annotationMirrors) {
        return findAnnotationMirror(annotationMirrors, typeCache.byReference);
    }

    private AnnotationMirror findRawReference(List<? extends AnnotationMirror> annotationMirrors) {
        return findAnnotationMirror(annotationMirrors, typeCache.rawReference);
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
            emitError(executable, receiverMethodMirror, "A method `%s%s` is not found in the `%s`. " +
                            "The receiver method must have the same arguments as the annotated method and must exist in the bridged type.",
                            receiverMethodName, getSignature(executableType.getParameterTypes()), Utilities.getTypeName(serviceType));
        } else if (found.getModifiers().contains(Modifier.STATIC) || found.getModifiers().contains(Modifier.PRIVATE)) {
            emitError(executable, receiverMethodMirror, "The receiver method `%s` must be a non-private instance method.", receiverMethodName);
        }
        return receiverMethodName;
    }

    private Map<String, DeclaredType> findAlwaysByReference(Element annotatedElement) {
        AnnotationMirror alwaysByReference = processor.getAnnotation(annotatedElement, typeCache.alwaysByReference);
        AnnotationMirror alwaysByReferenceRepeated = processor.getAnnotation(annotatedElement, typeCache.alwaysByReferenceRepeated);
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

    private void assertNoExpectedErrors(DefinitionData definitionData) {
        ExpectError.assertNoErrorExpected(this, definitionData.annotatedType.asElement());
        if (definitionData.customDispatchAccessor != null) {
            ExpectError.assertNoErrorExpected(this, definitionData.customDispatchAccessor);
        }
        if (definitionData.customReceiverAccessor != null) {
            ExpectError.assertNoErrorExpected(this, definitionData.customReceiverAccessor);
        }
        for (MethodData methodData : definitionData.toGenerate) {
            ExpectError.assertNoErrorExpected(this, methodData.element);
        }
        if (definitionData.endPointHandle != null) {
            ExpectError.assertNoErrorExpected(this, definitionData.endPointHandle);
        }
    }

    private void checkAnnotatedType(Element annotatedElement, AnnotationMirror annotation) {
        if (!annotatedElement.getKind().isClass()) {
            emitError(annotatedElement, annotation, "The annotation is supported only on type declarations.");
        }
        if (annotatedElement.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
            emitError(annotatedElement, annotation, "Annotation is supported only on top-level types.%n" +
                            "To fix this make the `%s` a top-level class.", annotatedElement.getSimpleName());
        }
    }

    final void emitError(Element element, AnnotationMirror mirror, String format, Object... params) {
        hasErrors = true;
        String msg = String.format(format, params);
        if (ExpectError.isExpectedError(this, element, msg)) {
            return;
        }
        this.processor.env().getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element, mirror);
    }

    private static CharSequence getSignature(List<? extends TypeMirror> types) {
        return getSignature(types, false, false);
    }

    private static CharSequence getSignature(List<? extends TypeMirror> types, boolean anyBefore, boolean anyAfter) {
        String prefix = anyBefore ? "(...," : "(";
        String suffix = anyAfter ? ",...)" : ")";
        return types.stream().map(Utilities::getTypeName).collect(Collectors.joining(", ", prefix, suffix));
    }

    private static Collection<CharSequence> getSignatures(Iterable<List<? extends TypeMirror>> signatures, boolean anyBefore, boolean anyAfter) {
        List<CharSequence> result = new ArrayList<>();
        for (List<? extends TypeMirror> signature : signatures) {
            result.add(getSignature(signature, anyBefore, anyAfter));
        }
        return result;
    }

    private static List<? extends VariableElement> findConstructorParams(DeclaredType type, ConstructorSelector constructorSelector) {
        TypeElement te = (TypeElement) type.asElement();
        ElementFilter.constructorsIn(te.getEnclosedElements()).forEach(constructorSelector::accept);
        ExecutableElement selectedConstructor = constructorSelector.get();
        return selectedConstructor == null ? Collections.emptyList() : selectedConstructor.getParameters();
    }

    private abstract static class ConstructorSelector implements Consumer<ExecutableElement>, Supplier<ExecutableElement> {

        protected final AbstractBridgeParser parser;
        ExecutableElement accepted;

        ConstructorSelector(AbstractBridgeParser parser) {
            this.parser = parser;
        }

        static ConstructorSelector singleConstructor(AbstractBridgeParser parser, TypeElement annotatedElement, AnnotationMirror mirror) {
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

        static ConstructorSelector withParameters(AbstractBridgeParser parser, Element annotatedElement, AnnotationMirror mirror,
                        TypeElement enclosingElement, Types types, Iterable<List<? extends TypeMirror>> parameterTypes, boolean sameArity) {
            return new ConstructorSelector(parser) {

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
                    Collection<CharSequence> expectedSignatures = getSignatures(parameterTypes, false, !sameArity);
                    CharSequence name = enclosingElement.getSimpleName();
                    CharSequence constructorToAdd = Utilities.printMethod(Collections.emptySet(), name, types.getNoType(TypeKind.NONE), parameterTypes.iterator().next().toArray(new TypeMirror[0]));
                    if (expectedSignatures.size() == 1) {
                        parser.emitError(annotatedElement, mirror, "The annotated type must have a single constructor with a `%s` signature.%n" +
                                        "To fix this add the `%s` constructor into `%s`.", expectedSignatures.iterator().next(), constructorToAdd, name);
                    } else {
                        parser.emitError(annotatedElement, mirror, "The annotated type must have a single constructor with one of the following signatures %s.%n" +
                                        "To fix this add the `%s` constructor into `%s`.", expectedSignatures.stream().map((s) -> "`" + s + "`").collect(Collectors.joining(", ")),
                                        constructorToAdd, name);
                    }
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

    static final class MarshallerData {

        enum Kind {
            VALUE,
            REFERENCE,
            RAW_REFERENCE,
            CUSTOM,
        }

        static final MarshallerData NO_MARSHALLER = new MarshallerData(Kind.VALUE, null, null, false, false, true, null, null, null, null, null);
        static final MarshallerData RAW_REFERENCE = new MarshallerData(Kind.RAW_REFERENCE, null, null, false, false, true, null, null, null, null, null);

        final Kind kind;
        final TypeMirror forType;
        final DirectionData in;
        final DirectionData out;
        final List<? extends AnnotationMirror> annotations; // only for CUSTOM
        final String name;                              // only for CUSTOM
        final boolean hasGeneratedFactory;              // only for REFERENCE
        final boolean useCustomReceiverAccessor;        // only for REFERENCE
        final boolean sameDirection;                    // only for REFERENCE
        final VariableElement nonDefaultReceiver;       // only for REFERENCE
        final ExecutableElement customDispatchFactory;  // only for REFERENCE

        private MarshallerData(Kind kind, TypeMirror forType, String name,
                        boolean hasGeneratedFactory, boolean useCustomReceiverAccessor, boolean sameDirection, DirectionData in, DirectionData out,
                        List<? extends AnnotationMirror> annotations, VariableElement nonDefaultReceiver, ExecutableElement customDispatchFactory) {
            this.kind = kind;
            this.forType = forType;
            this.name = name;
            this.hasGeneratedFactory = hasGeneratedFactory;
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

        static MarshallerData annotatedPrimitiveArray(DirectionData in, DirectionData out) {
            return in == null && out == null ? NO_MARSHALLER : new MarshallerData(Kind.VALUE, null, null, false, false, true, in, out, Collections.emptyList(), null, null);
        }

        static MarshallerData marshalled(TypeMirror forType, DirectionData in, DirectionData out, List<? extends AnnotationMirror> annotations) {
            TypeMirror useType = forType;
            while (useType.getKind() == TypeKind.ARRAY) {
                useType = ((ArrayType) useType).getComponentType();
            }
            String name = marshallerName(useType, annotations);
            return new MarshallerData(Kind.CUSTOM, useType, name, false, false, true, in, out, annotations, null, null);
        }

        static MarshallerData reference(DeclaredType startPointType, AnnotationMirror referenceRegistrationAnnotation, boolean useCustomReceiverAccessor,
                        boolean sameDirection, VariableElement nonDefaultReceiver, ExecutableElement customDispatchFactory) {
            return new MarshallerData(Kind.REFERENCE, startPointType, null, referenceRegistrationAnnotation != null, useCustomReceiverAccessor,
                            sameDirection, null, null, Collections.emptyList(), nonDefaultReceiver, customDispatchFactory);
        }

        static MarshallerData referenceArray(DeclaredType startPointType, AnnotationMirror referenceRegistrationAnnotation, DirectionData in, DirectionData out,
                        boolean useCustomReceiverAccessor, boolean sameDirection, VariableElement nonDefaultReceiver, ExecutableElement customDispatchFactory) {
            return new MarshallerData(Kind.REFERENCE, startPointType, null, referenceRegistrationAnnotation != null, useCustomReceiverAccessor,
                            sameDirection, in, out, Collections.emptyList(), nonDefaultReceiver, customDispatchFactory);
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
        final ExecutableElement customDispatchAccessor;
        final ExecutableElement customReceiverAccessor;
        final VariableElement endPointHandle;
        final DeclaredType jniConfig;
        final MarshallerData throwableMarshaller;
        final Set<DeclaredType> ignoreAnnotations;
        final Set<DeclaredType> marshallerAnnotations;

        DefinitionData(DeclaredType annotatedType, DeclaredType serviceType, Collection<MethodData> toGenerate,
                        List<? extends VariableElement> annotatedTypeConstructorParams, ExecutableElement customDispatchAccessor,
                        ExecutableElement customReceiverAccessor, VariableElement endPointHandle, DeclaredType jniConfig,
                        MarshallerData throwableMarshaller, Set<DeclaredType> ignoreAnnotations, Set<DeclaredType> marshallerAnnotations) {
            this.annotatedType = annotatedType;
            this.annotatedTypeConstructorParams = annotatedTypeConstructorParams;
            this.serviceType = serviceType;
            this.toGenerate = toGenerate;
            this.customDispatchAccessor = customDispatchAccessor;
            this.customReceiverAccessor = customReceiverAccessor;
            this.endPointHandle = endPointHandle;
            this.jniConfig = jniConfig;
            this.throwableMarshaller = throwableMarshaller;
            this.ignoreAnnotations = ignoreAnnotations;
            this.marshallerAnnotations = marshallerAnnotations;
        }

        Collection<MarshallerData> getAllCustomMarshallers() {
            SortedSet<MarshallerData> res = new TreeSet<>(Comparator.comparing(a -> a.name));
            collectAllMarshallers(res, MarshallerData.Kind.CUSTOM);
            res.add(throwableMarshaller);
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
    }

    abstract static class AbstractTypeCache extends BaseTypeCache {
        final DeclaredType alwaysByReference;
        final DeclaredType alwaysByReferenceRepeated;
        final DeclaredType binaryMarshaller;
        final DeclaredType binaryInput;
        final DeclaredType binaryOutput;
        final DeclaredType byReference;
        final DeclaredType byteArrayBinaryOutput;
        final DeclaredType cCharPointer;
        final DeclaredType cCharPointerBinaryOutput;
        final DeclaredType customDispatchAccessor;
        final DeclaredType customDispatchFactory;
        final DeclaredType customReceiverAccessor;
        final DeclaredType endPointHandle;
        final DeclaredType expectError;
        final DeclaredType foreignException;
        final DeclaredType generateHSToNativeBridge;
        final DeclaredType generateNativeToHSBridge;
        final DeclaredType generateNativeToNativeBridge;
        final DeclaredType hSObject;
        final DeclaredType idempotent;
        final DeclaredType imageInfo;
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
        final DeclaredType jObjectArray;
        final DeclaredType jShortArray;
        final DeclaredType jString;
        final DeclaredType jThrowable;
        final DeclaredType jniConfig;
        final DeclaredType jniEnv;
        final DeclaredType jniMethodScope;
        final DeclaredType jniUtil;
        final DeclaredType marshallerAnnotation;
        final DeclaredType nativeIsolate;
        final DeclaredType nativeObject;
        final DeclaredType nativeObjectHandles;
        final DeclaredType out;
        final DeclaredType rawReference;
        final DeclaredType receiverMethod;
        final DeclaredType stackValue;
        final DeclaredType typeLiteral;
        final DeclaredType unmanagedMemory;
        final DeclaredType weakHashMap;
        final DeclaredType wordFactory;

        AbstractTypeCache(AbstractProcessor processor) {
            super(processor);
            this.alwaysByReference = processor.getDeclaredType("org.graalvm.nativebridge.AlwaysByReference");
            this.alwaysByReferenceRepeated = processor.getDeclaredType("org.graalvm.nativebridge.AlwaysByReferenceRepeated");
            this.binaryMarshaller = processor.getDeclaredType("org.graalvm.nativebridge.BinaryMarshaller");
            this.binaryInput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryInput");
            this.binaryOutput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryOutput");
            this.byReference = processor.getDeclaredType("org.graalvm.nativebridge.ByReference");
            this.byteArrayBinaryOutput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryOutput.ByteArrayBinaryOutput");
            this.cCharPointer = processor.getDeclaredType("org.graalvm.nativeimage.c.type.CCharPointer");
            this.cCharPointerBinaryOutput = processor.getDeclaredType("org.graalvm.nativebridge.BinaryOutput.CCharPointerBinaryOutput");
            this.customDispatchAccessor = processor.getDeclaredType("org.graalvm.nativebridge.CustomDispatchAccessor");
            this.customReceiverAccessor = processor.getDeclaredType("org.graalvm.nativebridge.CustomReceiverAccessor");
            this.customDispatchFactory = processor.getDeclaredType("org.graalvm.nativebridge.CustomDispatchFactory");
            this.endPointHandle = processor.getDeclaredType("org.graalvm.nativebridge.EndPointHandle");
            this.expectError = processor.getDeclaredTypeOrNull("org.graalvm.nativebridge.processor.test.ExpectError");
            this.foreignException = processor.getDeclaredType("org.graalvm.nativebridge.ForeignException");
            this.generateHSToNativeBridge = processor.getDeclaredType(HotSpotToNativeBridgeParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION);
            this.generateNativeToHSBridge = processor.getDeclaredType(NativeToHotSpotBridgeParser.GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION);
            this.generateNativeToNativeBridge = processor.getDeclaredType(NativeToNativeBridgeParser.GENERATE_NATIVE_TO_NATIVE_ANNOTATION);
            this.hSObject = processor.getDeclaredType("org.graalvm.jniutils.HSObject");
            this.idempotent = processor.getDeclaredType("org.graalvm.nativebridge.Idempotent");
            this.imageInfo = processor.getDeclaredType("org.graalvm.nativeimage.ImageInfo");
            this.in = processor.getDeclaredType("org.graalvm.nativebridge.In");
            this.jBooleanArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JBooleanArray");
            this.jByteArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JByteArray");
            this.jCharArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JCharArray");
            this.jClass = processor.getDeclaredType("org.graalvm.jniutils.JNI.JClass");
            this.jDoubleArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JDoubleArray");
            this.jFloatArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JFloatArray");
            this.jIntArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JIntArray");
            this.jLongArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JLongArray");
            this.jObject = processor.getDeclaredType("org.graalvm.jniutils.JNI.JObject");
            this.jObjectArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JObjectArray");
            this.jShortArray = processor.getDeclaredType("org.graalvm.jniutils.JNI.JShortArray");
            this.jString = processor.getDeclaredType("org.graalvm.jniutils.JNI.JString");
            this.jThrowable = processor.getDeclaredType("org.graalvm.jniutils.JNI.JThrowable");
            this.jniConfig = processor.getDeclaredType("org.graalvm.nativebridge.JNIConfig");
            this.jniEnv = processor.getDeclaredType("org.graalvm.jniutils.JNI.JNIEnv");
            this.jniMethodScope = processor.getDeclaredType("org.graalvm.jniutils.JNIMethodScope");
            this.jniUtil = processor.getDeclaredType("org.graalvm.jniutils.JNIUtil");
            this.marshallerAnnotation = processor.getDeclaredType("org.graalvm.nativebridge.MarshallerAnnotation");
            this.nativeIsolate = processor.getDeclaredType("org.graalvm.nativebridge.NativeIsolate");
            this.nativeObject = processor.getDeclaredType("org.graalvm.nativebridge.NativeObject");
            this.nativeObjectHandles = processor.getDeclaredType("org.graalvm.nativebridge.NativeObjectHandles");
            this.out = processor.getDeclaredType("org.graalvm.nativebridge.Out");
            this.rawReference = processor.getDeclaredType("org.graalvm.nativebridge.RawReference");
            this.receiverMethod = processor.getDeclaredType("org.graalvm.nativebridge.ReceiverMethod");
            this.stackValue = processor.getDeclaredType("org.graalvm.nativeimage.StackValue");
            this.typeLiteral = processor.getDeclaredType("org.graalvm.nativebridge.TypeLiteral");
            this.unmanagedMemory = processor.getDeclaredType("org.graalvm.nativeimage.UnmanagedMemory");
            this.weakHashMap = processor.getDeclaredType("java.util.WeakHashMap");
            this.wordFactory = processor.getDeclaredType("org.graalvm.word.WordFactory");
        }
    }

    static final class Configuration {

        private final DeclaredType handledAnnotationType;
        private final DeclaredType proxyBaseType;
        private final Iterable<List<? extends TypeMirror>> subClassReferenceConstructorTypes;
        private final Iterable<List<? extends TypeMirror>> handleReferenceConstructorTypes;

        Configuration(DeclaredType handledAnnotationType, DeclaredType proxyBaseType,
                        Iterable<List<? extends TypeMirror>> subClassReferenceConstructorTypes,
                        Iterable<List<? extends TypeMirror>> handleReferenceConstructorTypes) {
            this.handledAnnotationType = Objects.requireNonNull(handledAnnotationType, "HandledAnnotationType must be non null.");
            this.proxyBaseType = Objects.requireNonNull(proxyBaseType, "ProxyBaseType must be non null.");
            this.subClassReferenceConstructorTypes = Objects.requireNonNull(subClassReferenceConstructorTypes, "SubClassReferenceConstructorTypes must be non null.");
            this.handleReferenceConstructorTypes = Objects.requireNonNull(handleReferenceConstructorTypes, "HandleReferenceConstructorTypes must be non null.");
        }

        DeclaredType getHandledAnnotationType() {
            return handledAnnotationType;
        }

        DeclaredType getProxyBaseType() {
            return proxyBaseType;
        }

        Iterable<List<? extends TypeMirror>> getSubClassReferenceConstructorTypes() {
            return subClassReferenceConstructorTypes;
        }

        Iterable<List<? extends TypeMirror>> getHandleReferenceConstructorTypes() {
            return handleReferenceConstructorTypes;
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
