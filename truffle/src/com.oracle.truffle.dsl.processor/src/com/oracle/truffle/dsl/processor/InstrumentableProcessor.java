/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.GenerateWrapper.IncomingConverter;
import com.oracle.truffle.api.instrumentation.GenerateWrapper.OutgoingConverter;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.transform.FixWarningsVisitor;
import com.oracle.truffle.dsl.processor.java.transform.GenerateOverrideVisitor;

@SupportedAnnotationTypes({"com.oracle.truffle.api.instrumentation.Instrumentable", "com.oracle.truffle.api.instrumentation.GenerateWrapper"})
public final class InstrumentableProcessor extends AbstractProcessor {

    // configuration
    private static final String CLASS_SUFFIX = "Wrapper";
    private static final String EXECUTE_METHOD_PREFIX = "execute";

    // API name assumptions
    private static final String CONSTANT_REENTER = "ProbeNode.UNWIND_ACTION_REENTER";
    private static final String METHOD_GET_NODE_COST = "getCost";
    private static final String METHOD_ON_RETURN_EXCEPTIONAL_OR_UNWIND = "onReturnExceptionalOrUnwind";
    private static final String METHOD_ON_RETURN_VALUE = "onReturnValue";
    private static final String METHOD_ON_ENTER = "onEnter";
    private static final String FIELD_DELEGATE = "delegateNode";
    private static final String FIELD_PROBE = "probeNode";
    private static final String VAR_RETURN_CALLED = "wasOnReturnExecuted";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private static final String CREATE_WRAPPER_NAME = "createWrapper";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        try {
            ProcessorContext context = new ProcessorContext(processingEnv, null);
            ProcessorContext.setThreadLocalInstance(context);

            DeclaredType instrumentableNode = context.getDeclaredType(InstrumentableNode.class);
            ExecutableElement createWrapper = ElementUtils.findExecutableElement(instrumentableNode, CREATE_WRAPPER_NAME);

            for (Element element : roundEnv.getElementsAnnotatedWith(GenerateWrapper.class)) {
                if (!element.getKind().isClass() && !element.getKind().isInterface()) {
                    continue;
                }
                try {
                    if (element.getKind() != ElementKind.CLASS) {
                        emitError(element, String.format("Only classes can be annotated with %s.", GenerateWrapper.class.getSimpleName()));
                        continue;
                    }

                    if (createWrapper == null) {
                        emitError(element, String.format("Fatal %s.%s not found.", InstrumentableNode.class.getSimpleName(), CREATE_WRAPPER_NAME));
                        continue;
                    }

                    if (!ElementUtils.isAssignable(element.asType(), instrumentableNode)) {
                        emitError(element, String.format("Classes annotated with @%s must implement %s.", GenerateWrapper.class.getSimpleName(), InstrumentableNode.class.getSimpleName()));
                        continue;
                    } else {
                        boolean createWrapperFound = false;
                        for (ExecutableElement declaredMethod : ElementFilter.methodsIn(element.getEnclosedElements())) {
                            if (ElementUtils.signatureEquals(declaredMethod, createWrapper)) {
                                createWrapperFound = true;
                                break;
                            }
                        }
                        if (!createWrapperFound) {
                            emitError(element, String.format("Classes annotated with @%s must declare/override %s.%s and return a new instance of the generated wrapper class called %s." +
                                            " You may copy the following generated implementation: %n" +
                                            "  @Override public %s createWrapper(%s probeNode) {%n" +
                                            "    return new %s(this, probeNode);%n" +
                                            "  }",
                                            GenerateWrapper.class.getSimpleName(),
                                            InstrumentableNode.class.getSimpleName(),
                                            CREATE_WRAPPER_NAME,
                                            createWrapperClassName((TypeElement) element),
                                            com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode.class.getSimpleName(),
                                            ProbeNode.class.getSimpleName(),
                                            createWrapperClassName((TypeElement) element)));
                            continue;
                        }
                        if (!ElementUtils.isAssignable(element.asType(), context.getType(Node.class))) {
                            emitError(element, String.format("Classes annotated with @%s must extend %s.", GenerateWrapper.class.getSimpleName(), Node.class.getSimpleName()));
                            continue;
                        }
                    }

                    AnnotationMirror generateWrapperMirror = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), context.getType(GenerateWrapper.class));
                    if (generateWrapperMirror == null) {
                        continue;
                    }

                    CodeTypeElement unit = generateWrapperOnly(context, element);

                    if (unit == null) {
                        continue;
                    }
                    DeclaredType overrideType = (DeclaredType) context.getType(Override.class);
                    DeclaredType unusedType = (DeclaredType) context.getType(SuppressWarnings.class);
                    unit.accept(new GenerateOverrideVisitor(overrideType), null);
                    unit.accept(new FixWarningsVisitor(context.getEnvironment(), unusedType, overrideType), null);
                    unit.accept(new CodeWriter(context.getEnvironment(), element), null);
                } catch (Throwable e) {
                    // never throw annotation processor exceptions to the compiler
                    // it might screw up its state.
                    handleThrowable(e, element);
                }
            }

            // remove with deprecations
            processLegacyInstrumentable(roundEnv, context);

            return true;
        } finally {
            ProcessorContext.setThreadLocalInstance(null);
        }
    }

    /*
     * TO BE REMOVED WITH DEPRECATIONS
     */
    @SuppressWarnings("deprecation")
    private void processLegacyInstrumentable(RoundEnvironment roundEnv, ProcessorContext context) {
        for (Element element : roundEnv.getElementsAnnotatedWith(com.oracle.truffle.api.instrumentation.Instrumentable.class)) {
            if (!element.getKind().isClass() && !element.getKind().isInterface()) {
                continue;
            }
            try {
                if (element.getKind() != ElementKind.CLASS) {
                    emitError(element, String.format("Only classes can be annotated with %s.", com.oracle.truffle.api.instrumentation.Instrumentable.class.getSimpleName()));
                    continue;
                }

                AnnotationMirror generateWrapperMirror = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), context.getType(GenerateWrapper.class));
                if (generateWrapperMirror != null) {
                    continue;
                }

                TypeMirror instrumentableType = context.getType(com.oracle.truffle.api.instrumentation.Instrumentable.class);
                AnnotationMirror instrumentable = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), instrumentableType);
                if (instrumentable == null) {
                    continue;
                } else {
                    final boolean generateWrapper;
                    TypeMirror factoryType = ElementUtils.getAnnotationValue(TypeMirror.class, instrumentable, "factory");

                    if (factoryType == null || factoryType.getKind() == TypeKind.ERROR) {
                        // factory type is erroneous or null (can mean error in javac)
                        // generate it
                        generateWrapper = true;
                    } else {
                        TypeElement type = ElementUtils.getTypeElement(context.getEnvironment(), "com.oracle.truffle.api.instrumentation.test.TestErrorFactory");

                        if (type != null && ElementUtils.typeEquals(factoryType, type.asType())) {
                            generateWrapper = true;
                        } else {
                            // factory is user defined or already generated
                            generateWrapper = false;
                        }
                    }
                    if (!generateWrapper) {
                        continue;
                    }
                }

                CodeTypeElement unit = generateWrapperAndFactory(context, element);

                if (unit == null) {
                    continue;
                }
                DeclaredType overrideType = (DeclaredType) context.getType(Override.class);
                DeclaredType unusedType = (DeclaredType) context.getType(SuppressWarnings.class);
                unit.accept(new GenerateOverrideVisitor(overrideType), null);
                unit.accept(new FixWarningsVisitor(context.getEnvironment(), unusedType, overrideType), null);
                unit.accept(new CodeWriter(context.getEnvironment(), element), null);
            } catch (Throwable e) {
                // never throw annotation processor exceptions to the compiler
                // it might screw up its state.
                handleThrowable(e, element);
            }
        }
    }

    private void handleThrowable(Throwable t, Element e) {
        String message = "Uncaught error in " + getClass().getSimpleName() + " while processing " + e + " ";
        ProcessorContext.getInstance().getEnvironment().getMessager().printMessage(Kind.ERROR, message + ": " + ElementUtils.printException(t), e);
    }

    private CodeTypeElement generateWrapperOnly(ProcessorContext context, Element e) {
        CodeTypeElement wrapper = generateWrapper(context, e, true);
        if (wrapper == null) {
            return null;
        }
        assertNoErrorExpected(e);
        return wrapper;
    }

    private CodeTypeElement generateWrapperAndFactory(ProcessorContext context, Element e) {
        CodeTypeElement wrapper = generateWrapper(context, e, false);
        if (wrapper == null) {
            return null;
        }
        CodeTypeElement factory = generateFactory(context, e, wrapper);

        // add @SuppressWarnings("deprecation")
        DeclaredType suppressWarnings = context.getDeclaredType(SuppressWarnings.class);
        CodeAnnotationMirror suppressWarningsAnnotation = new CodeAnnotationMirror(suppressWarnings);
        suppressWarningsAnnotation.setElementValue(ElementUtils.findExecutableElement(suppressWarnings, "value"),
                        new CodeAnnotationValue(Arrays.asList(new CodeAnnotationValue("deprecation"))));
        factory.getAnnotationMirrors().add(suppressWarningsAnnotation);

        wrapper.getModifiers().add(Modifier.STATIC);
        factory.add(wrapper);
        assertNoErrorExpected(e);
        return factory;
    }

    @SuppressWarnings("deprecation")
    private static CodeTypeElement generateFactory(ProcessorContext context, Element e, CodeTypeElement wrapper) {
        TypeElement sourceType = (TypeElement) e;
        PackageElement pack = context.getEnvironment().getElementUtils().getPackageOf(sourceType);
        Set<Modifier> typeModifiers = ElementUtils.modifiers(Modifier.PUBLIC, Modifier.FINAL);
        CodeTypeElement factory = new CodeTypeElement(typeModifiers, ElementKind.CLASS, pack, createWrapperClassName(sourceType));

        TypeMirror factoryType = context.reloadType(context.getType(com.oracle.truffle.api.instrumentation.InstrumentableFactory.class));
        factory.getImplements().add(new CodeTypeMirror.DeclaredCodeTypeMirror(ElementUtils.fromTypeMirror(factoryType), Arrays.asList(sourceType.asType())));

        addGeneratedBy(context, factory, sourceType);

        TypeMirror returnType = context.getType(com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode.class);
        CodeExecutableElement createMethod = new CodeExecutableElement(ElementUtils.modifiers(Modifier.PUBLIC), returnType, "createWrapper");

        createMethod.addParameter(new CodeVariableElement(sourceType.asType(), FIELD_DELEGATE));
        createMethod.addParameter(new CodeVariableElement(context.getType(ProbeNode.class), FIELD_PROBE));

        CodeTreeBuilder builder = createMethod.createBuilder();
        ExecutableElement constructor = ElementFilter.constructorsIn(wrapper.getEnclosedElements()).iterator().next();

        String firstParameterReference = null;
        if (constructor.getParameters().size() > 2) {
            TypeMirror firstParameter = constructor.getParameters().get(0).asType();
            if (ElementUtils.typeEquals(firstParameter, sourceType.asType())) {
                firstParameterReference = FIELD_DELEGATE;
            } else if (ElementUtils.typeEquals(firstParameter, context.getType(SourceSection.class))) {
                firstParameterReference = FIELD_DELEGATE + ".getSourceSection()";
            }
        }

        builder.startReturn().startNew(wrapper.asType());
        if (firstParameterReference != null) {
            builder.string(firstParameterReference);
        }
        builder.string(FIELD_DELEGATE).string(FIELD_PROBE);
        builder.end().end();

        factory.add(createMethod);

        return factory;
    }

    private static String createWrapperClassName(TypeElement sourceType) {
        return sourceType.getSimpleName().toString() + CLASS_SUFFIX;
    }

    @SuppressWarnings("deprecation")
    private CodeTypeElement generateWrapper(ProcessorContext context, Element e, boolean topLevelClass) {
        if (!e.getKind().isClass()) {
            return null;
        }

        if (e.getModifiers().contains(Modifier.PRIVATE)) {
            emitError(e, "Class must not be private to generate a wrapper.");
            return null;
        }

        if (e.getModifiers().contains(Modifier.FINAL)) {
            emitError(e, "Class must not be final to generate a wrapper.");
            return null;
        }
        if (e.getEnclosingElement().getKind() != ElementKind.PACKAGE && !e.getModifiers().contains(Modifier.STATIC)) {
            emitError(e, "Inner class must be static to generate a wrapper.");
            return null;
        }

        TypeElement sourceType = (TypeElement) e;

        ExecutableElement constructor = null;
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(e.getEnclosedElements());
        if (constructors.isEmpty()) {
            // add default constructor
            constructors.add(new CodeExecutableElement(ElementUtils.modifiers(Modifier.PUBLIC), null, e.getSimpleName().toString()));
        }

        // try visible default constructor
        for (ListIterator<ExecutableElement> iterator = constructors.listIterator(); iterator.hasNext();) {
            ExecutableElement c = iterator.next();
            Modifier modifier = ElementUtils.getVisibility(c.getModifiers());
            if (modifier == Modifier.PRIVATE) {
                iterator.remove();
                continue;
            }
            if (c.getParameters().isEmpty()) {
                constructor = c;
                break;
            }
        }

        // try copy constructor
        if (constructor == null) {
            for (ExecutableElement c : constructors) {
                VariableElement firstParameter = c.getParameters().iterator().next();
                if (ElementUtils.typeEquals(firstParameter.asType(), sourceType.asType())) {
                    constructor = c;
                    break;
                }
            }
        }

        // try source section constructor
        if (constructor == null) {
            for (ExecutableElement c : constructors) {
                VariableElement firstParameter = c.getParameters().iterator().next();
                if (ElementUtils.typeEquals(firstParameter.asType(), context.getType(SourceSection.class))) {
                    constructor = c;
                    break;
                }
            }
        }

        if (constructor == null) {
            emitError(sourceType, "No suiteable constructor found for wrapper factory generation. At least one default or copy constructor must be visible.");
            return null;
        }

        PackageElement pack = context.getEnvironment().getElementUtils().getPackageOf(sourceType);
        Set<Modifier> typeModifiers;

        String wrapperClassName = createWrapperClassName(sourceType);
        if (topLevelClass) {
            typeModifiers = ElementUtils.modifiers(Modifier.FINAL);
        } else {
            typeModifiers = ElementUtils.modifiers(Modifier.PRIVATE, Modifier.FINAL);
            // add some suffix to avoid name clashes
            wrapperClassName += "0";
        }

        CodeTypeElement wrapperType = new CodeTypeElement(typeModifiers, ElementKind.CLASS, pack, wrapperClassName);
        TypeMirror resolvedSuperType = sourceType.asType();
        wrapperType.setSuperClass(resolvedSuperType);
        if (topLevelClass) {
            wrapperType.getImplements().add(context.getType(InstrumentableNode.WrapperNode.class));
        } else {
            wrapperType.getImplements().add(context.getType(com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode.class));
        }

        addGeneratedBy(context, wrapperType, sourceType);

        wrapperType.add(createNodeChild(context, sourceType.asType(), FIELD_DELEGATE));
        wrapperType.add(createNodeChild(context, context.getType(ProbeNode.class), FIELD_PROBE));

        Set<Modifier> constructorModifiers;
        if (topLevelClass) {
            // package protected
            constructorModifiers = ElementUtils.modifiers();
        } else {
            constructorModifiers = ElementUtils.modifiers(Modifier.PRIVATE);
        }

        CodeExecutableElement wrappedConstructor = GeneratorUtils.createConstructorUsingFields(constructorModifiers, wrapperType, constructor);
        wrapperType.add(wrappedConstructor);

        // generate getters
        for (VariableElement field : wrapperType.getFields()) {
            CodeExecutableElement getter = new CodeExecutableElement(ElementUtils.modifiers(Modifier.PUBLIC), field.asType(), "get" +
                            ElementUtils.firstLetterUpperCase(field.getSimpleName().toString()));
            getter.createBuilder().startReturn().string(field.getSimpleName().toString()).end();
            wrapperType.add(getter);
        }

        if (isOverrideableOrUndeclared(sourceType, METHOD_GET_NODE_COST)) {
            TypeMirror returnType = context.getType(NodeCost.class);
            CodeExecutableElement getInstrumentationTags = new CodeExecutableElement(ElementUtils.modifiers(Modifier.PUBLIC), returnType, METHOD_GET_NODE_COST);
            getInstrumentationTags.createBuilder().startReturn().staticReference(returnType, "NONE").end();
            wrapperType.add(getInstrumentationTags);
        }

        List<ExecutableElement> wrappedMethods = new ArrayList<>();
        List<ExecutableElement> wrappedExecuteMethods = new ArrayList<>();
        List<? extends Element> elementList = context.getEnvironment().getElementUtils().getAllMembers(sourceType);

        ExecutableElement genericExecuteDelegate = null;
        for (ExecutableElement method : ElementFilter.methodsIn(elementList)) {
            if (isExecuteMethod(method) && isOverridable(method)) {
                VariableElement firstParam = method.getParameters().isEmpty() ? null : method.getParameters().get(0);
                if (topLevelClass && (firstParam == null || !ElementUtils.isAssignable(firstParam.asType(), context.getType(VirtualFrame.class)))) {
                    emitError(e, String.format("Wrapped execute method %s must have VirtualFrame as first parameter.", method.getSimpleName().toString()));
                    return null;
                }
                if (ElementUtils.isObject(method.getReturnType()) && method.getParameters().size() == 1 && genericExecuteDelegate == null) {
                    genericExecuteDelegate = method;
                }
            }
        }

        for (ExecutableElement method : ElementFilter.methodsIn(elementList)) {
            if (!isOverridable(method)) {
                continue;
            }

            String methodName = method.getSimpleName().toString();
            if (methodName.startsWith(EXECUTE_METHOD_PREFIX)) {
                wrappedExecuteMethods.add(method);
            } else {
                if (method.getModifiers().contains(Modifier.ABSTRACT) && !methodName.equals("getSourceSection") //
                                && !methodName.equals(METHOD_GET_NODE_COST) && !method.getThrownTypes().contains(context.getType(UnexpectedResultException.class))) {
                    wrappedMethods.add(method);
                }
            }
        }

        ExecutableElement incomingConverterMethod = null;
        ExecutableElement outgoingConverterMethod = null;

        for (ExecutableElement method : ElementFilter.methodsIn(elementList)) {
            IncomingConverter incomingConverter = method.getAnnotation(IncomingConverter.class);
            OutgoingConverter outgoingConverter = method.getAnnotation(OutgoingConverter.class);

            if (incomingConverter != null) {
                if (incomingConverterMethod != null) {
                    emitError(sourceType, String.format("Only one @%s method allowed, found multiple.", IncomingConverter.class.getSimpleName()));
                    return null;
                }
                if (!verifyConverter(method, IncomingConverter.class)) {
                    continue;
                }
                incomingConverterMethod = method;
            }

            if (outgoingConverter != null) {
                if (outgoingConverterMethod != null) {
                    emitError(sourceType, String.format("Only one @%s method allowed, found multiple.", OutgoingConverter.class.getSimpleName()));
                    return null;
                }
                if (!verifyConverter(method, OutgoingConverter.class)) {
                    continue;
                }
                outgoingConverterMethod = method;
            }
        }

        if (wrappedExecuteMethods.isEmpty()) {
            emitError(sourceType, String.format("No methods starting with name execute found to wrap."));
            return null;
        }

        Collections.sort(wrappedExecuteMethods, new Comparator<ExecutableElement>() {
            public int compare(ExecutableElement o1, ExecutableElement o2) {
                return ElementUtils.compareMethod(o1, o2);
            }
        });

        for (ExecutableElement method : wrappedExecuteMethods) {
            ExecutableElement executeMethod = method;
            CodeExecutableElement wrappedExecute = CodeExecutableElement.clone(processingEnv, executeMethod);
            wrappedExecute.getModifiers().remove(Modifier.ABSTRACT);
            wrappedExecute.getAnnotationMirrors().clear();

            String frameParameterName = "null";
            for (VariableElement parameter : wrappedExecute.getParameters()) {
                if (ElementUtils.typeEquals(context.getType(VirtualFrame.class), parameter.asType())) {
                    frameParameterName = parameter.getSimpleName().toString();
                    break;
                }
            }

            CodeTreeBuilder builder = wrappedExecute.createBuilder();
            TypeMirror returnTypeMirror = executeMethod.getReturnType();
            boolean executeReturnsVoid = ElementUtils.isVoid(returnTypeMirror);
            if (executeReturnsVoid && genericExecuteDelegate != null && executeMethod.getParameters().size() == genericExecuteDelegate.getParameters().size()) {
                executeMethod = genericExecuteDelegate;
                returnTypeMirror = genericExecuteDelegate.getReturnType();
                executeReturnsVoid = false;
            }

            String returnName;
            if (!executeReturnsVoid) {
                returnName = "returnValue";
                builder.declaration(returnTypeMirror, returnName, (CodeTree) null);
            } else {
                returnName = "null";
            }
            builder.startFor().startGroup().string(";;").end().end().startBlock();
            builder.declaration("boolean", VAR_RETURN_CALLED, "false");
            builder.startTryBlock();
            if (wrappedExecute.getThrownTypes().contains(context.getType(UnexpectedResultException.class))) {
                builder.startTryBlock();
            }

            builder.startStatement().startCall(FIELD_PROBE, METHOD_ON_ENTER).string(frameParameterName).end().end();

            CodeTreeBuilder callDelegate = builder.create();
            callDelegate.startCall(FIELD_DELEGATE, executeMethod.getSimpleName().toString());
            for (VariableElement parameter : wrappedExecute.getParameters()) {
                callDelegate.string(parameter.getSimpleName().toString());
            }
            callDelegate.end();
            if (executeReturnsVoid) {
                builder.statement(callDelegate.build());
            } else {
                builder.startStatement().string(returnName).string(" = ").tree(callDelegate.build()).end();
            }

            builder.startStatement().string(VAR_RETURN_CALLED).string(" = true").end();

            builder.startStatement().startCall(FIELD_PROBE, METHOD_ON_RETURN_VALUE).string(frameParameterName);
            if (outgoingConverterMethod == null || executeReturnsVoid) {
                builder.string(returnName);
            } else {
                builder.tree(createCallConverter(outgoingConverterMethod, frameParameterName, CodeTreeBuilder.singleString(returnName)));
            }
            builder.end().end();

            builder.statement("break");
            if (wrappedExecute.getThrownTypes().contains(context.getType(UnexpectedResultException.class))) {
                builder.end().startCatchBlock(context.getType(UnexpectedResultException.class), "e");
                builder.startStatement().string(VAR_RETURN_CALLED).string(" = true").end();
                builder.startStatement().startCall(FIELD_PROBE, METHOD_ON_RETURN_VALUE).string(frameParameterName);
                if (outgoingConverterMethod == null || executeReturnsVoid) {
                    builder.string("e.getResult()");
                } else {
                    builder.tree(createCallConverter(outgoingConverterMethod, frameParameterName, CodeTreeBuilder.singleString("e.getResult()")));
                }
                builder.end().end();
                builder.startThrow().string("e").end().end();
            }
            builder.end().startCatchBlock(context.getType(Throwable.class), "t");
            CodeTreeBuilder callExOrUnwind = builder.create();
            callExOrUnwind.startCall(FIELD_PROBE, METHOD_ON_RETURN_EXCEPTIONAL_OR_UNWIND).string(frameParameterName).string("t").string(VAR_RETURN_CALLED).end();
            builder.declaration("Object", "result", callExOrUnwind.build());
            builder.startIf().string("result == ").string(CONSTANT_REENTER).end();
            builder.startBlock();
            builder.statement("continue");
            if (ElementUtils.isVoid(wrappedExecute.getReturnType())) {
                builder.end().startElseIf();
                builder.string("result != null").end();
                builder.startBlock();
                builder.statement("break");
            } else {
                boolean objectReturnType = "java.lang.Object".equals(ElementUtils.getQualifiedName(returnTypeMirror)) && returnTypeMirror.getKind() != TypeKind.ARRAY;
                boolean throwsUnexpectedResult = wrappedExecute.getThrownTypes().contains(context.getType(UnexpectedResultException.class));
                if (objectReturnType || !throwsUnexpectedResult) {
                    builder.end().startElseIf();
                    builder.string("result != null").end();
                    builder.startBlock();
                    builder.startStatement().string(returnName).string(" = ");
                    if (!objectReturnType) {
                        builder.string("(").string(ElementUtils.getSimpleName(returnTypeMirror)).string(") ");
                    }
                    if (incomingConverterMethod == null) {
                        builder.string("result");
                    } else {
                        builder.tree(createCallConverter(incomingConverterMethod, frameParameterName, CodeTreeBuilder.singleString("result")));
                    }
                    builder.end();
                    builder.statement("break");
                } else { // can throw UnexpectedResultException
                    builder.end();

                    if (incomingConverterMethod != null) {
                        builder.startIf().string("result != null").end().startBlock();
                        builder.startStatement();
                        builder.string("result = ");
                        builder.tree(createCallConverter(incomingConverterMethod, frameParameterName, CodeTreeBuilder.singleString("result")));
                        builder.end();
                        builder.end();
                    }

                    builder.startIf();
                    builder.string("result").instanceOf(boxed(returnTypeMirror, context.getEnvironment().getTypeUtils())).end();
                    builder.startBlock();

                    builder.startStatement().string(returnName).string(" = ");
                    builder.string("(").string(ElementUtils.getSimpleName(returnTypeMirror)).string(") ");
                    builder.string("result");
                    builder.end();
                    builder.statement("break");
                    builder.end();
                    builder.startElseIf().string("result != null").end();
                    builder.startBlock();
                    builder.startThrow().startNew(context.getType(UnexpectedResultException.class));
                    builder.string("result");
                    builder.end().end(); // new, throw
                }
            }
            builder.end();
            builder.startThrow().string("t").end();
            builder.end(2);
            if (!ElementUtils.isVoid(wrappedExecute.getReturnType())) {
                builder.startReturn().string(returnName).end();
            }

            wrapperType.add(wrappedExecute);
        }

        for (ExecutableElement delegateMethod : wrappedMethods) {
            CodeExecutableElement generatedMethod = CodeExecutableElement.clone(processingEnv, delegateMethod);

            generatedMethod.getModifiers().remove(Modifier.ABSTRACT);

            CodeTreeBuilder callDelegate = generatedMethod.createBuilder();
            if (ElementUtils.isVoid(delegateMethod.getReturnType())) {
                callDelegate.startStatement();
            } else {
                callDelegate.startReturn();
            }
            callDelegate.startCall("this." + FIELD_DELEGATE, generatedMethod.getSimpleName().toString());
            for (VariableElement parameter : generatedMethod.getParameters()) {
                callDelegate.string(parameter.getSimpleName().toString());
            }
            callDelegate.end().end();
            wrapperType.add(generatedMethod);
        }

        return wrapperType;
    }

    private static boolean isExecuteMethod(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        if (!methodName.startsWith(EXECUTE_METHOD_PREFIX)) {
            return false;
        }
        return true;
    }

    private static boolean isOverridable(ExecutableElement method) {
        Set<Modifier> modifiers = method.getModifiers();
        if (modifiers.contains(Modifier.FINAL)) {
            return false;
        }
        Modifier visibility = ElementUtils.getVisibility(modifiers);
        if (visibility == Modifier.PRIVATE) {
            return false;
        }
        return true;
    }

    private static CodeTree createCallConverter(ExecutableElement converterMethod, String frameParameterName, CodeTree returnName) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        if (converterMethod.getModifiers().contains(Modifier.STATIC)) {
            builder.startStaticCall(converterMethod);
        } else {
            builder.startCall("this." + FIELD_DELEGATE, converterMethod.getSimpleName().toString());
        }

        if (converterMethod.getParameters().size() == 1) {
            builder.tree(returnName);
        } else {
            // should be verified
            if (converterMethod.getParameters().size() != 2) {
                throw new AssertionError();
            }
            builder.string(frameParameterName);
            builder.tree(returnName);
        }
        builder.end();
        return builder.build();
    }

    private boolean verifyConverter(ExecutableElement method, Class<?> annotationClass) {
        if (method.getModifiers().contains(Modifier.PRIVATE)) {
            emitError(method, String.format("Method annotated with @%s must not be private.", annotationClass.getSimpleName()));
            return false;
        }

        if (method.getModifiers().contains(Modifier.ABSTRACT)) {
            emitError(method, String.format("Method annotated with @%s must not be abstract.", annotationClass.getSimpleName()));
            return false;
        }

        TypeMirror frameClass = ProcessorContext.getInstance().getDeclaredType(VirtualFrame.class);
        TypeMirror objectClass = ProcessorContext.getInstance().getDeclaredType(Object.class);

        boolean valid = true;
        if (method.getParameters().size() == 1) {
            TypeMirror firstType = method.getParameters().get(0).asType();
            if (!ElementUtils.typeEquals(firstType, objectClass)) {
                valid = false;
            }
        } else if (method.getParameters().size() == 2) {
            TypeMirror firstType = method.getParameters().get(0).asType();
            if (!ElementUtils.typeEquals(firstType, frameClass)) {
                valid = false;
            }
            TypeMirror secondType = method.getParameters().get(1).asType();
            if (!ElementUtils.typeEquals(secondType, objectClass)) {
                valid = false;
            }
        } else {
            valid = false;
        }

        if (!ElementUtils.typeEquals(method.getReturnType(), objectClass)) {
            valid = false;
        }

        if (!valid) {
            emitError(method, String.format("Invalid @%s method signature. Must be either " +
                            "Object converter(Object) or Object converter(%s, Object)", annotationClass.getSimpleName(), VirtualFrame.class.getSimpleName()));
            return false;
        }

        return true;
    }

    private static TypeMirror boxed(TypeMirror type, Types types) {
        if (type.getKind().isPrimitive()) {
            return types.boxedClass((PrimitiveType) type).asType();
        } else {
            return type;
        }
    }

    private static void addGeneratedBy(ProcessorContext context, CodeTypeElement generatedType, TypeElement generatedByType) {
        DeclaredType generatedBy = (DeclaredType) context.getType(GeneratedBy.class);
        // only do this if generatedBy is on the classpath.
        if (generatedBy != null) {
            CodeAnnotationMirror generatedByAnnotation = new CodeAnnotationMirror(generatedBy);
            generatedByAnnotation.setElementValue(generatedByAnnotation.findExecutableElement("value"), new CodeAnnotationValue(generatedByType.asType()));
            generatedType.addAnnotationMirror(generatedByAnnotation);
        }
    }

    private static boolean isOverrideableOrUndeclared(TypeElement sourceType, String methodName) {
        List<ExecutableElement> elements = ElementUtils.getDeclaredMethodsInSuperTypes(sourceType, methodName);
        return elements.isEmpty() || !elements.iterator().next().getModifiers().contains(Modifier.FINAL);
    }

    private static CodeVariableElement createNodeChild(ProcessorContext context, TypeMirror type, String name) {
        CodeVariableElement var = new CodeVariableElement(ElementUtils.modifiers(Modifier.PRIVATE), type, name);
        var.addAnnotationMirror(new CodeAnnotationMirror((DeclaredType) context.getType(Child.class)));
        return var;
    }

    void assertNoErrorExpected(Element e) {
        ExpectError.assertNoErrorExpected(processingEnv, e);
    }

    void emitError(Element e, String msg) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e);
    }

    void emitError(Element e, AnnotationMirror annotation, String msg) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e, annotation);
    }

}
