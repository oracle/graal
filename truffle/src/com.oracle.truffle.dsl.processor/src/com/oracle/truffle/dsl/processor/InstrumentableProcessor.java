/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.transform.FixWarningsVisitor;
import com.oracle.truffle.dsl.processor.java.transform.GenerateOverrideVisitor;

@SupportedAnnotationTypes("com.oracle.truffle.api.instrumentation.Instrumentable")
public final class InstrumentableProcessor extends AbstractProcessor {

    // configuration
    private static final String CLASS_SUFFIX = "Wrapper";
    private static final String EXECUTE_METHOD_PREFIX = "execute";

    // API name assumptions
    private static final String METHOD_GET_NODE_COST = "getCost";
    private static final String METHOD_ON_RETURN_EXCEPTIONAL = "onReturnExceptional";
    private static final String METHOD_ON_RETURN_VALUE = "onReturnValue";
    private static final String METHOD_ON_ENTER = "onEnter";
    private static final String FIELD_DELEGATE = "delegateNode";
    private static final String FIELD_PROBE = "probeNode";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        try {
            ProcessorContext context = new ProcessorContext(processingEnv, null);
            ProcessorContext.setThreadLocalInstance(context);

            for (Element element : roundEnv.getElementsAnnotatedWith(Instrumentable.class)) {
                if (!element.getKind().isClass() && !element.getKind().isInterface()) {
                    continue;
                }
                try {
                    if (element.getKind() != ElementKind.CLASS) {
                        emitError(element, String.format("Only classes can be annotated with %s.", Instrumentable.class.getSimpleName()));
                        continue;
                    }

                    TypeMirror instrumentableType = context.getType(Instrumentable.class);
                    AnnotationMirror instrumentable = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), instrumentableType);
                    if (instrumentable == null) {
                        continue;
                    }

                    TypeMirror factoryType = ElementUtils.getAnnotationValue(TypeMirror.class, instrumentable, "factory");

                    final boolean generateWrapper;
                    if (factoryType == null || factoryType.getKind() == TypeKind.ERROR) {
                        // factory type is erroneous or null (can mean error in javac)
                        // generate it
                        generateWrapper = true;
                    } else {
                        TypeElement type = context.getEnvironment().getElementUtils().getTypeElement("com.oracle.truffle.api.instrumentation.test.TestErrorFactory");

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
            return true;
        } finally {
            ProcessorContext.setThreadLocalInstance(null);
        }
    }

    private void handleThrowable(Throwable t, Element e) {
        String message = "Uncaught error in " + getClass().getSimpleName() + " while processing " + e + " ";
        ProcessorContext.getInstance().getEnvironment().getMessager().printMessage(Kind.ERROR, message + ": " + ElementUtils.printException(t), e);
    }

    private CodeTypeElement generateWrapperAndFactory(ProcessorContext context, Element e) {
        CodeTypeElement wrapper = generateWrapper(context, e);
        if (wrapper == null) {
            return null;
        }
        CodeTypeElement factory = generateFactory(context, e, wrapper);
        wrapper.getModifiers().add(Modifier.STATIC);
        factory.add(wrapper);
        assertNoErrorExpected(e);
        return factory;
    }

    private static CodeTypeElement generateFactory(ProcessorContext context, Element e, CodeTypeElement wrapper) {
        TypeElement sourceType = (TypeElement) e;
        PackageElement pack = context.getEnvironment().getElementUtils().getPackageOf(sourceType);
        Set<Modifier> typeModifiers = ElementUtils.modifiers(Modifier.PUBLIC, Modifier.FINAL);
        CodeTypeElement factory = new CodeTypeElement(typeModifiers, ElementKind.CLASS, pack, createWrapperClassName(sourceType));

        TypeMirror factoryType = context.reloadType(context.getType(InstrumentableFactory.class));
        factory.getImplements().add(new CodeTypeMirror.DeclaredCodeTypeMirror(ElementUtils.fromTypeMirror(factoryType), Arrays.asList(sourceType.asType())));

        addGeneratedBy(context, factory, sourceType);

        TypeMirror returnType = context.getType(WrapperNode.class);
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

    private CodeTypeElement generateWrapper(ProcessorContext context, Element e) {
        if (!e.getKind().isClass()) {
            return null;
        }

        if (!e.getModifiers().contains(Modifier.PUBLIC)) {
            emitError(e, "Class must be public to generate a wrapper.");
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
            if (modifier == null || modifier == Modifier.PRIVATE) {
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
        Set<Modifier> typeModifiers = ElementUtils.modifiers(Modifier.PRIVATE, Modifier.FINAL);
        CodeTypeElement wrapperType = new CodeTypeElement(typeModifiers, ElementKind.CLASS, pack, sourceType.getSimpleName().toString() + CLASS_SUFFIX + "0");
        TypeMirror resolvedSuperType = sourceType.asType();
        wrapperType.setSuperClass(resolvedSuperType);
        wrapperType.getImplements().add(context.getType(WrapperNode.class));

        addGeneratedBy(context, wrapperType, sourceType);

        wrapperType.add(createNodeChild(context, sourceType.asType(), FIELD_DELEGATE));
        wrapperType.add(createNodeChild(context, context.getType(ProbeNode.class), FIELD_PROBE));

        CodeExecutableElement wrappedConstructor = GeneratorUtils.createConstructorUsingFields(ElementUtils.modifiers(Modifier.PRIVATE), wrapperType, constructor);
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

        List<ExecutableElement> wrappedExecuteMethods = new ArrayList<>();
        List<? extends Element> elementList = context.getEnvironment().getElementUtils().getAllMembers(sourceType);
        for (ExecutableElement method : ElementFilter.methodsIn(elementList)) {
            Set<Modifier> modifiers = method.getModifiers();
            if (modifiers.contains(Modifier.FINAL)) {
                continue;
            }
            Modifier visibility = ElementUtils.getVisibility(modifiers);
            if (visibility == Modifier.PRIVATE || visibility == null) {
                continue;
            }

            String methodName = method.getSimpleName().toString();

            if (methodName.startsWith(EXECUTE_METHOD_PREFIX)) {
                wrappedExecuteMethods.add(method);
            } else {
                if (modifiers.contains(Modifier.ABSTRACT) && !methodName.equals("getSourceSection") //
                                && !methodName.equals(METHOD_GET_NODE_COST)) {
                    emitError(sourceType, String.format("Unable to implement unknown abstract method %s in generated wrapper node.", ElementUtils.createReferenceName(method)));
                    return null;
                }
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

        for (ExecutableElement executeMethod : wrappedExecuteMethods) {
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
            builder.startTryBlock();
            builder.startStatement().startCall(FIELD_PROBE, METHOD_ON_ENTER).string(frameParameterName).end().end();

            CodeTreeBuilder callDelegate = builder.create();
            callDelegate.startCall(FIELD_DELEGATE, executeMethod.getSimpleName().toString());
            for (VariableElement parameter : wrappedExecute.getParameters()) {
                callDelegate.string(parameter.getSimpleName().toString());
            }
            callDelegate.end();
            String returnName;
            if (ElementUtils.isVoid(executeMethod.getReturnType())) {
                returnName = "null";
                builder.statement(callDelegate.build());
            } else {
                returnName = "returnValue";
                builder.declaration(executeMethod.getReturnType(), returnName, callDelegate.build());
            }

            builder.startStatement().startCall(FIELD_PROBE, METHOD_ON_RETURN_VALUE).string(frameParameterName).string(returnName).end().end();
            if (!ElementUtils.isVoid(executeMethod.getReturnType())) {
                builder.startReturn().string(returnName).end();
            }
            builder.end().startCatchBlock(context.getType(Throwable.class), "t");
            builder.startStatement().startCall(FIELD_PROBE, METHOD_ON_RETURN_EXCEPTIONAL).string(frameParameterName).string("t").end().end();
            builder.startThrow().string("t").end();
            builder.end();

            wrapperType.add(wrappedExecute);
        }

        return wrapperType;
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
