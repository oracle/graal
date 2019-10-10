/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.interop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.ExpectError;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

/**
 * THIS IS NOT PUBLIC API.
 */
public final class InteropDSLProcessor extends AbstractProcessor {

    static final Set<String> KNOWN_MESSAGES = new HashSet<>();

    static {
        KNOWN_MESSAGES.add("READ");
        KNOWN_MESSAGES.add("WRITE");
        KNOWN_MESSAGES.add("REMOVE");
        KNOWN_MESSAGES.add("UNBOX");
        KNOWN_MESSAGES.add("GET_SIZE");
        KNOWN_MESSAGES.add("HAS_SIZE");
        KNOWN_MESSAGES.add("IS_NULL");
        KNOWN_MESSAGES.add("IS_BOXED");
        KNOWN_MESSAGES.add("IS_EXECUTABLE");
        KNOWN_MESSAGES.add("IS_INSTANTIABLE");
        KNOWN_MESSAGES.add("HAS_KEYS");
        KNOWN_MESSAGES.add("KEYS");
        KNOWN_MESSAGES.add("KEY_INFO");
        KNOWN_MESSAGES.add("IS_POINTER");
        KNOWN_MESSAGES.add("AS_POINTER");
        KNOWN_MESSAGES.add("TO_NATIVE");
        KNOWN_MESSAGES.add("EXECUTE");
        KNOWN_MESSAGES.add("NEW");
        KNOWN_MESSAGES.add("INVOKE");
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(TruffleTypes.MessageResolution_Name);
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            process0(roundEnv);
        }
        return false;
    }

    private void process0(RoundEnvironment roundEnv) {
        try {
            ProcessorContext context = ProcessorContext.enter(processingEnv);
            for (Element e : roundEnv.getElementsAnnotatedWith(ElementUtils.castTypeElement(context.getTypes().MessageResolution))) {
                try {
                    processElement(e);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    String message = "Uncaught error in " + this.getClass();
                    processingEnv.getMessager().printMessage(Kind.ERROR, message + ": " + ElementUtils.printException(ex), e);
                }
            }
        } finally {
            ProcessorContext.leave();
        }
    }

    private void processElement(Element e) throws IOException {
        if (e.getKind() != ElementKind.CLASS) {
            return;
        }
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        AnnotationMirror messageImplementations = ElementUtils.findAnnotationMirror(e, types.MessageResolution);
        if (messageImplementations == null) {
            return;
        }

        // Check the receiver
        final String receiverTypeFullClassName = Utils.getReceiverTypeFullClassName(messageImplementations);
        if (isReceiverNonStaticInner(messageImplementations)) {
            emitError(receiverTypeFullClassName + " cannot be used as a receiver as it is not a static inner class.", e);
            return;
        }

        if (e.getModifiers().contains(Modifier.PRIVATE) || e.getModifiers().contains(Modifier.PROTECTED)) {
            emitError("Class must be public or package protected", e);
            return;
        }

        // check if there is a @LanguageCheck class

        Element curr = e;
        List<TypeElement> receiverChecks = new ArrayList<>();
        for (Element innerClass : curr.getEnclosedElements()) {
            if (innerClass.getKind() != ElementKind.CLASS) {
                continue;
            }
            if (ElementUtils.findAnnotationMirror(innerClass, types.CanResolve) != null) {
                receiverChecks.add((TypeElement) innerClass);
            }
        }

        if (receiverChecks.size() == 0 && isInstanceMissing(receiverTypeFullClassName)) {
            emitError("Missing isInstance method in class " + receiverTypeFullClassName, e);
            return;
        }

        if (receiverChecks.size() == 0 && isInstanceHasWrongSignature(receiverTypeFullClassName)) {
            emitError("Method isInstance in class " + receiverTypeFullClassName + " has an invalid signature: expected signature (object: TruffleObject).", e);
            return;
        }

        if (receiverChecks.size() > 1) {
            emitError("Only one @LanguageCheck element allowed", e);
            return;
        }

        // Collect all inner classes with an @Resolve annotation

        curr = e;
        List<TypeElement> elements = new ArrayList<>();
        for (Element innerClass : curr.getEnclosedElements()) {
            if (innerClass.getKind() != ElementKind.CLASS) {
                continue;
            }
            if (ElementUtils.findAnnotationMirror(innerClass, types.Resolve) != null) {
                elements.add((TypeElement) innerClass);
            }

        }

        ForeignAccessFactoryGenerator factoryGenerator = new ForeignAccessFactoryGenerator(processingEnv, messageImplementations, (TypeElement) e);

        // Process inner classes with an @Resolve annotation
        boolean generationSuccessfull = true;
        for (TypeElement elem : elements) {
            generationSuccessfull &= processResolveClass(ElementUtils.findAnnotationMirror(elem, types.Resolve), messageImplementations, elem, factoryGenerator);
        }
        if (!generationSuccessfull) {
            return;
        }

        if (!receiverChecks.isEmpty()) {
            generationSuccessfull &= processLanguageCheck(messageImplementations, receiverChecks.get(0), factoryGenerator);
        }
        if (!generationSuccessfull) {
            return;
        }

        try {
            factoryGenerator.generate();
        } catch (FilerException ex) {
            emitError("Foreign factory class with same name already exists", e);
            return;
        }
    }

    private boolean processLanguageCheck(AnnotationMirror messageResolutionAnnotation, TypeElement element, ForeignAccessFactoryGenerator factoryGenerator) {
        LanguageCheckGenerator generator = new LanguageCheckGenerator(processingEnv, messageResolutionAnnotation, element, factoryGenerator);

        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        if (!ElementUtils.typeEquals(element.getSuperclass(), types.Node)) {
            emitError(ElementUtils.getQualifiedName(element) + " must extend com.oracle.truffle.api.nodes.Node.", element);
            return false;
        }

        if (!element.getModifiers().contains(Modifier.ABSTRACT)) {
            emitError("Class must be abstract", element);
            return false;
        }

        if (!element.getModifiers().contains(Modifier.STATIC)) {
            emitError("Class must be static", element);
            return false;
        }

        if (element.getModifiers().contains(Modifier.PRIVATE) || element.getModifiers().contains(Modifier.PROTECTED)) {
            emitError("Class must be public or package protected", element);
            return false;
        }

        List<ExecutableElement> methods = generator.getTestMethods();
        if (methods.isEmpty() || methods.size() > 1) {
            emitError("There needs to be exactly one test method.", element);
            return false;
        }

        ExecutableElement m = methods.get(0);
        String errorMessage = generator.checkSignature(m);
        if (errorMessage != null) {
            emitError(errorMessage, m);
            return false;
        }

        factoryGenerator.addLanguageCheckHandler(generator);
        return true;
    }

    private boolean processResolveClass(AnnotationMirror resolveAnnotation, AnnotationMirror messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator factoryGenerator) {
        MessageGenerator currentGenerator = MessageGenerator.getGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, factoryGenerator);

        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        if (currentGenerator == null) {
            emitError("Unknown message type: " + ElementUtils.getAnnotationValue(String.class, resolveAnnotation, "message"), element);
            return false;
        }

        if (!ElementUtils.typeEquals(element.getSuperclass(), types.Node)) {
            emitError(ElementUtils.getQualifiedName(element) + " must extend com.oracle.truffle.api.nodes.Node.", element);
            return false;
        }

        if (!element.getModifiers().contains(Modifier.ABSTRACT)) {
            emitError("Class must be abstract", element);
            return false;
        }

        if (!element.getModifiers().contains(Modifier.STATIC)) {
            emitError("Class must be static", element);
            return false;
        }

        if (element.getModifiers().contains(Modifier.PRIVATE) || element.getModifiers().contains(Modifier.PROTECTED)) {
            emitError("Class must be public or package protected", element);
            return false;
        }

        List<ExecutableElement> methods = currentGenerator.getAccessMethods();
        if (methods.isEmpty()) {
            emitError("There needs to be at least one access method.", element);
            return false;
        }

        List<? extends VariableElement> params = methods.get(0).getParameters();
        int argumentSize = params.size();

        if (params.size() > 0 && ElementUtils.typeEquals(params.get(0).asType(), types.VirtualFrame)) {
            argumentSize -= 1;
        }
        for (ExecutableElement m : methods) {
            params = m.getParameters();

            int paramsSize = params.size();
            if (params.size() > 0 && ElementUtils.typeEquals(params.get(0).asType(), types.VirtualFrame)) {
                paramsSize -= 1;
            }

            if (argumentSize != paramsSize) {
                emitError("Inconsistent argument length.", element);
                return false;
            }
        }

        for (ExecutableElement m : methods) {
            String errorMessage = currentGenerator.checkSignature(m);
            if (errorMessage != null) {
                emitError(errorMessage, m);
                return false;
            }
        }

        String message = ElementUtils.getAnnotationValue(String.class, resolveAnnotation, "message");
        if (!Utils.getMessage(processingEnv, message)) {
            message = currentGenerator.getMessageName();
        }
        factoryGenerator.addMessageHandler(message, currentGenerator);
        return true;
    }

    private static boolean isReceiverNonStaticInner(AnnotationMirror message) {
        DeclaredType type = (DeclaredType) ElementUtils.getAnnotationValue(TypeMirror.class, message, "receiverType");
        TypeElement element = (TypeElement) type.asElement();
        if (element.getNestingKind() == NestingKind.MEMBER || element.getNestingKind() == NestingKind.LOCAL) {
            for (Modifier modifier : element.getModifiers()) {
                if (modifier.compareTo(Modifier.STATIC) == 0) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean isInstanceMissing(String receiverTypeFullClassName) {
        for (Element elem : ElementUtils.getTypeElement(this.processingEnv, receiverTypeFullClassName).getEnclosedElements()) {
            if (elem.getKind().equals(ElementKind.METHOD)) {
                ExecutableElement method = (ExecutableElement) elem;
                if (method.getSimpleName().toString().equals("isInstance")) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isInstanceHasWrongSignature(String receiverTypeFullClassName) {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        for (Element elem : ElementUtils.getTypeElement(this.processingEnv, receiverTypeFullClassName).getEnclosedElements()) {
            if (elem.getKind().equals(ElementKind.METHOD)) {
                ExecutableElement method = (ExecutableElement) elem;
                if (method.getSimpleName().toString().equals("isInstance") && method.getParameters().size() == 1 &&
                                ElementUtils.typeEquals(method.getParameters().get(0).asType(), types.TruffleObject)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void emitError(String msg, Element e) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e);
    }

}
