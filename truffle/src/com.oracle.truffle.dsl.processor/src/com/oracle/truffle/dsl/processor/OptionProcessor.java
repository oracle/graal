/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import static javax.lang.model.element.Modifier.ABSTRACT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
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
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.transform.FixWarningsVisitor;
import com.oracle.truffle.dsl.processor.java.transform.GenerateOverrideVisitor;

/**
 * Processes static fields annotated with Option. An OptionDescriptors implementation is generated
 * for each top level class containing at least one such field. The name of the generated class for
 * top level class {@code com.foo.Bar} is {@code com.foo.Bar_OptionDescriptors}.
 */
@SupportedAnnotationTypes({TruffleTypes.Option_Name, TruffleTypes.Option_Group_Name})
public class OptionProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private final Set<Element> processed = new HashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }
        ProcessorContext context = ProcessorContext.enter(processingEnv);
        try {
            TruffleTypes types = context.getTypes();
            Map<Element, OptionsInfo> map = new HashMap<>();
            for (Element element : roundEnv.getElementsAnnotatedWith(ElementUtils.castTypeElement(types.Option))) {
                if (!processed.contains(element)) {
                    processed.add(element);
                    Element topElement = element.getEnclosingElement();

                    OptionsInfo options = map.get(topElement);
                    if (options == null) {
                        options = new OptionsInfo(topElement);
                        map.put(topElement, options);
                    }
                    AnnotationMirror mirror = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), types.Option);
                    try {
                        processElement(element, mirror, options);
                    } catch (Throwable t) {
                        handleThrowable(t, topElement);
                    }
                }
            }

            Map<String, OptionInfo> seenKeys = new HashMap<>();
            for (OptionsInfo infos : map.values()) {
                for (OptionInfo info : infos.options) {
                    if (seenKeys.containsKey(info.name)) {
                        OptionInfo otherInfo = seenKeys.get(info.name);
                        String message = "Two options with duplicated resolved descriptor name '%s' found.";
                        info.valid = false;
                        otherInfo.valid = false;
                        error(info.field, info.annotation, message, info.name);
                        error(otherInfo.field, otherInfo.annotation, message, otherInfo.name);
                    } else {
                        seenKeys.put(info.name, info);
                    }
                }
            }

            for (OptionsInfo infos : map.values()) {
                ListIterator<OptionInfo> listIterator = infos.options.listIterator();
                while (listIterator.hasNext()) {
                    OptionInfo info = listIterator.next();
                    if (info.valid) {
                        ExpectError.assertNoErrorExpected(processingEnv, info.field);
                    } else {
                        listIterator.remove();
                    }
                }
                Collections.sort(infos.options, new Comparator<OptionInfo>() {
                    public int compare(OptionInfo o1, OptionInfo o2) {
                        return o1.name.compareTo(o2.name);
                    }
                });
            }

            for (OptionsInfo info : map.values()) {
                try {
                    generateOptionDescriptor(info);
                } catch (Throwable t) {
                    handleThrowable(t, info.type);
                }
            }
        } finally {
            ProcessorContext.leave();
        }

        return true;
    }

    private boolean processElement(Element element, AnnotationMirror elementAnnotation, OptionsInfo info) {
        ProcessorContext context = ProcessorContext.getInstance();
        TruffleTypes types = context.getTypes();

        if (!element.getModifiers().contains(Modifier.STATIC)) {
            error(element, elementAnnotation, "Option field must be static");
            return false;
        }
        if (element.getModifiers().contains(Modifier.PRIVATE)) {
            error(element, elementAnnotation, "Option field cannot be private");
            return false;
        }

        List<String> groupPrefixStrings = null;

        AnnotationMirror prefix = ElementUtils.findAnnotationMirror(info.type, types.Option_Group);

        if (prefix != null) {
            groupPrefixStrings = ElementUtils.getAnnotationValueList(String.class, prefix, "value");
        } else {
            TypeMirror erasedTruffleType = context.getEnvironment().getTypeUtils().erasure(types.TruffleLanguage);
            if (context.getEnvironment().getTypeUtils().isAssignable(info.type.asType(), erasedTruffleType)) {
                AnnotationMirror registration = ElementUtils.findAnnotationMirror(info.type, types.TruffleLanguage_Registration);
                if (registration != null) {
                    groupPrefixStrings = Arrays.asList(ElementUtils.getAnnotationValue(String.class, registration, "id"));
                    if (groupPrefixStrings.get(0).isEmpty()) {
                        error(element, elementAnnotation, "%s must specify an id such that Truffle options can infer their prefix.",
                                        types.TruffleLanguage_Registration.asElement().getSimpleName().toString());
                        return false;
                    }
                }

            } else if (context.getEnvironment().getTypeUtils().isAssignable(info.type.asType(), types.TruffleInstrument)) {
                AnnotationMirror registration = ElementUtils.findAnnotationMirror(info.type, types.TruffleInstrument_Registration);
                if (registration != null) {
                    groupPrefixStrings = Arrays.asList(ElementUtils.getAnnotationValue(String.class, registration, "id"));
                    if (groupPrefixStrings.get(0).isEmpty()) {
                        error(element, elementAnnotation, "%s must specify an id such that Truffle options can infer their prefix.",
                                        types.TruffleInstrument_Registration.asElement().getSimpleName().toString());
                        return false;
                    }
                }
            }
        }

        if (groupPrefixStrings == null || groupPrefixStrings.isEmpty()) {
            groupPrefixStrings = Arrays.asList("");
        }

        AnnotationMirror annotation = ElementUtils.findAnnotationMirror(element, types.Option);
        assert annotation != null;
        assert element instanceof VariableElement;
        assert element.getKind() == ElementKind.FIELD;
        VariableElement field = (VariableElement) element;
        String fieldName = field.getSimpleName().toString();

        Types typeUtils = processingEnv.getTypeUtils();

        TypeMirror fieldType = field.asType();
        if (fieldType.getKind() != TypeKind.DECLARED) {
            error(element, elementAnnotation, "Option field must be of type " + ElementUtils.getQualifiedName(types.OptionKey));
            return false;
        }
        if (!typeUtils.isSubtype(fieldType, typeUtils.erasure(types.OptionKey))) {
            error(element, elementAnnotation, "Option field type %s is not a subclass of %s", fieldType, types.OptionKey);
            return false;
        }

        if (!field.getModifiers().contains(Modifier.STATIC)) {
            error(element, elementAnnotation, "Option field must be static");
            return false;
        }
        if (field.getModifiers().contains(Modifier.PRIVATE)) {
            error(element, elementAnnotation, "Option field cannot be private");
            return false;
        }

        boolean optionMap = false;
        TypeMirror optionMapType = types.OptionMap;
        List<? extends TypeMirror> typeArguments = ((DeclaredType) fieldType).getTypeArguments();
        if (typeArguments.size() == 1) {
            optionMap = typeUtils.isSubtype(typeArguments.get(0), typeUtils.erasure(optionMapType));
        }

        String help = ElementUtils.getAnnotationValue(String.class, annotation, "help");
        if (help.length() != 0) {
            char firstChar = help.charAt(0);
            if (!Character.isUpperCase(firstChar)) {
                error(element, elementAnnotation, "Option help text must start with upper case letter");
                return false;
            }
        }

        AnnotationValue value = ElementUtils.getAnnotationValue(elementAnnotation, "name", false);
        String optionName;
        if (value == null) {
            optionName = fieldName;
        } else {
            optionName = ElementUtils.getAnnotationValue(String.class, annotation, "name");
        }

        // Applying this restriction to all options requires changes in some language
        // implementations.
        if (optionMap && optionName.contains(".")) {
            error(element, elementAnnotation, "Option (maps) cannot contain a '.' in the name");
            return false;
        }

        boolean deprecated = ElementUtils.getAnnotationValue(Boolean.class, annotation, "deprecated");

        VariableElement categoryElement = ElementUtils.getAnnotationValue(VariableElement.class, annotation, "category");
        String category = categoryElement != null ? categoryElement.getSimpleName().toString() : null;
        if (category == null) {
            category = "INTERNAL";
        }

        VariableElement stabilityElement = ElementUtils.getAnnotationValue(VariableElement.class, annotation, "stability");
        String stability = stabilityElement != null ? stabilityElement.getSimpleName().toString() : null;

        String deprecationMessage = ElementUtils.getAnnotationValue(String.class, annotation, "deprecationMessage");
        if (deprecationMessage.length() != 0) {
            if (!deprecated) {
                error(element, elementAnnotation, "Deprecation message can be specified only for deprecated options.");
                return false;
            }
            char firstChar = deprecationMessage.charAt(0);
            if (!Character.isUpperCase(firstChar)) {
                error(element, elementAnnotation, "Option deprecation message must start with upper case letter.");
                return false;
            }
        }

        for (String group : groupPrefixStrings) {
            String name;
            if (group.isEmpty() && optionName.isEmpty()) {
                error(element, elementAnnotation, "Both group and option name cannot be empty");
                continue;
            } else if (optionName.isEmpty()) {
                name = group;
            } else {
                if (group.isEmpty()) {
                    name = optionName;
                } else {
                    name = group + "." + optionName;
                }
            }
            info.options.add(new OptionInfo(name, help, field, elementAnnotation, deprecated, category, stability, optionMap, deprecationMessage));
        }
        return true;
    }

    private static void error(Element element, AnnotationMirror annotation, String message, Object... args) {
        ProcessingEnvironment processingEnv = ProcessorContext.getInstance().getEnvironment();
        String formattedMessage = String.format(message, args);
        if (ExpectError.isExpectedError(processingEnv, element, formattedMessage)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, formattedMessage, element, annotation);
    }

    private static void generateOptionDescriptor(OptionsInfo info) {
        Element element = info.type;
        ProcessorContext context = ProcessorContext.getInstance();

        CodeTypeElement unit = generateDescriptors(context, element, info);
        DeclaredType overrideType = (DeclaredType) context.getType(Override.class);
        unit.accept(new GenerateOverrideVisitor(overrideType), null);
        unit.accept(new FixWarningsVisitor(element, overrideType), null);
        try {
            unit.accept(new CodeWriter(context.getEnvironment(), element), null);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof FilerException) {
                // ignore spurious errors of source file already created in Eclipse.
                if (e.getCause().getMessage().startsWith("Source file already created")) {
                    return;
                }
            }
        }

    }

    private void handleThrowable(Throwable t, Element e) {
        String message = "Uncaught error in " + getClass().getSimpleName() + " while processing " + e + " ";
        ProcessorContext.getInstance().getEnvironment().getMessager().printMessage(Kind.ERROR, message + ": " + ElementUtils.printException(t), e);
    }

    private static CodeTypeElement generateDescriptors(ProcessorContext context, Element element, OptionsInfo model) {
        TruffleTypes types = context.getTypes();
        String optionsClassName = ElementUtils.getSimpleName(element.asType()) + types.OptionDescriptors.asElement().getSimpleName().toString();
        TypeElement sourceType = (TypeElement) model.type;
        PackageElement pack = context.getEnvironment().getElementUtils().getPackageOf(sourceType);
        Set<Modifier> typeModifiers = ElementUtils.modifiers(Modifier.FINAL);
        CodeTypeElement descriptors = new CodeTypeElement(typeModifiers, ElementKind.CLASS, pack, optionsClassName);
        DeclaredType optionDescriptorsType = types.OptionDescriptors;
        descriptors.getImplements().add(optionDescriptorsType);
        GeneratorUtils.addGeneratedBy(context, descriptors, (TypeElement) element);

        ExecutableElement get = ElementUtils.findExecutableElement(optionDescriptorsType, "get");
        CodeExecutableElement getMethod = CodeExecutableElement.clone(get);
        getMethod.getModifiers().remove(ABSTRACT);
        CodeTreeBuilder builder = getMethod.createBuilder();

        String nameVariableName = getMethod.getParameters().get(0).getSimpleName().toString();

        boolean elseIf = false;
        for (OptionInfo info : model.options) {
            if (!info.optionMap) {
                continue;
            }
            elseIf = builder.startIf(elseIf);
            // Prefix options must be delimited by a '.' or match exactly.
            // e.g. for java.Props: java.Props.Threshold and java.Props match, but
            // java.PropsThreshold doesn't.
            builder.startCall(nameVariableName, "startsWith").doubleQuote(info.name + ".").end();
            builder.string(" || ");
            builder.startCall(nameVariableName, "equals").doubleQuote(info.name).end();

            builder.end().startBlock();
            builder.startReturn().tree(createBuildOptionDescriptor(context, info)).end();
            builder.end();
        }

        boolean startSwitch = false;
        for (OptionInfo info : model.options) {
            if (info.optionMap) {
                continue;
            }
            if (!startSwitch) {
                builder.startSwitch().string(nameVariableName).end().startBlock();
                startSwitch = true;
            }
            builder.startCase().doubleQuote(info.name).end().startCaseBlock();
            builder.startReturn().tree(createBuildOptionDescriptor(context, info)).end();
            builder.end(); // case
        }
        if (startSwitch) {
            builder.end(); // block
        }
        builder.returnNull();
        descriptors.add(getMethod);

        CodeExecutableElement iteratorMethod = CodeExecutableElement.clone(ElementUtils.findExecutableElement(optionDescriptorsType, "iterator"));
        iteratorMethod.getModifiers().remove(ABSTRACT);
        builder = iteratorMethod.createBuilder();

        builder.startReturn();
        if (model.options.isEmpty()) {
            builder.startStaticCall(context.getType(Collections.class), "<OptionDescriptor> emptyList().iterator").end();
        } else {
            builder.startStaticCall(context.getType(Arrays.class), "asList");
            for (OptionInfo info : model.options) {
                builder.startGroup();
                builder.startIndention();
                builder.newLine();
                builder.tree(createBuildOptionDescriptor(context, info));
                builder.end();
                builder.end();
            }
            builder.end(); /// asList call
            builder.newLine();
            builder.startCall("", "iterator").end();
        }
        builder.end(); // return
        descriptors.add(iteratorMethod);

        return descriptors;
    }

    private static CodeTree createBuildOptionDescriptor(ProcessorContext context, OptionInfo info) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        TruffleTypes types = context.getTypes();
        builder.startStaticCall(types.OptionDescriptor, "newBuilder");
        VariableElement var = info.field;
        builder.staticReference(var.getEnclosingElement().asType(), var.getSimpleName().toString());
        builder.doubleQuote(info.name);
        builder.end(); // newBuilder call
        if (info.deprecated) {
            builder.startCall("", "deprecated").string("true").end();
            builder.startCall("", "deprecationMessage").doubleQuote(info.deprecationMessage).end();
        } else {
            builder.startCall("", "deprecated").string("false").end();
        }
        builder.startCall("", "help").doubleQuote(info.help).end();
        builder.startCall("", "category").staticReference(types.OptionCategory, info.category).end();
        builder.startCall("", "stability").staticReference(types.OptionStability, info.stability).end();

        builder.startCall("", "build").end();
        return builder.build();
    }

    static class OptionInfo implements Comparable<OptionInfo> {

        boolean valid = true;
        final String name;
        final String help;
        final boolean deprecated;
        final boolean optionMap;
        final VariableElement field;
        final AnnotationMirror annotation;
        final String category;
        final String stability;
        final String deprecationMessage;

        OptionInfo(String name, String help, VariableElement field, AnnotationMirror annotation, boolean deprecated, String category, String stability, boolean optionMap, String deprecationMessage) {
            this.name = name;
            this.help = help;
            this.field = field;
            this.annotation = annotation;
            this.deprecated = deprecated;
            this.category = category;
            this.stability = stability;
            this.optionMap = optionMap;
            this.deprecationMessage = deprecationMessage;
        }

        @Override
        public int compareTo(OptionInfo other) {
            return name.compareTo(other.name);
        }

    }

    static class OptionsInfo {

        final Element type;
        final List<OptionInfo> options = new ArrayList<>();

        OptionsInfo(Element topDeclaringType) {
            this.type = topDeclaringType;
        }
    }
}
