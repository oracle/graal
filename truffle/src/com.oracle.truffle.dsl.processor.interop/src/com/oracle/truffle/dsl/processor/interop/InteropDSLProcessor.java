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
package com.oracle.truffle.dsl.processor.interop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.dsl.processor.ExpectError;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

/**
 * THIS IS NOT PUBLIC API.
 */
public final class InteropDSLProcessor extends AbstractProcessor {

    static final List<Message> KNOWN_MESSAGES = Arrays.asList(new Message[]{Message.READ, Message.WRITE, Message.REMOVE, Message.IS_NULL, Message.IS_EXECUTABLE,
                    Message.IS_INSTANTIABLE, Message.IS_BOXED, Message.UNBOX, Message.HAS_SIZE, Message.GET_SIZE, Message.KEY_INFO, Message.HAS_KEYS, Message.KEYS,
                    Message.IS_POINTER, Message.AS_POINTER, Message.TO_NATIVE, Message.EXECUTE, Message.INVOKE, Message.NEW});

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add("com.oracle.truffle.api.interop.MessageResolution");
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        process0(roundEnv);
        return true;
    }

    private void process0(RoundEnvironment roundEnv) {
        try {
            ProcessorContext.setThreadLocalInstance(new ProcessorContext(processingEnv, null));
            for (Element e : roundEnv.getElementsAnnotatedWith(MessageResolution.class)) {
                try {
                    processElement(e);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    String message = "Uncaught error in " + this.getClass();
                    processingEnv.getMessager().printMessage(Kind.ERROR, message + ": " + ElementUtils.printException(ex), e);
                }
            }
        } finally {
            ProcessorContext.setThreadLocalInstance(null);
        }
    }

    private void processElement(Element e) throws IOException {
        if (e.getKind() != ElementKind.CLASS) {
            return;
        }
        MessageResolution messageImplementations = e.getAnnotation(MessageResolution.class);
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
            if (innerClass.getAnnotation(CanResolve.class) != null) {
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
            if (innerClass.getAnnotation(Resolve.class) != null) {
                elements.add((TypeElement) innerClass);
            }

        }

        ForeignAccessFactoryGenerator factoryGenerator = new ForeignAccessFactoryGenerator(processingEnv, messageImplementations, (TypeElement) e);

        // Process inner classes with an @Resolve annotation
        boolean generationSuccessfull = true;
        for (TypeElement elem : elements) {
            generationSuccessfull &= processResolveClass(elem.getAnnotation(Resolve.class), messageImplementations, elem, factoryGenerator);
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

    private boolean processLanguageCheck(MessageResolution messageResolutionAnnotation, TypeElement element, ForeignAccessFactoryGenerator factoryGenerator) {
        LanguageCheckGenerator generator = new LanguageCheckGenerator(processingEnv, messageResolutionAnnotation, element, factoryGenerator);

        if (!ElementUtils.typeEquals(element.getSuperclass(), Utils.getTypeMirror(processingEnv, com.oracle.truffle.api.nodes.Node.class))) {
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

    private boolean processResolveClass(Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element, ForeignAccessFactoryGenerator factoryGenerator) {
        MessageGenerator currentGenerator = MessageGenerator.getGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, factoryGenerator);

        if (currentGenerator == null) {
            emitError("Unknown message type: " + resolveAnnotation.message(), element);
            return false;
        }

        if (!ElementUtils.typeEquals(element.getSuperclass(), Utils.getTypeMirror(processingEnv, com.oracle.truffle.api.nodes.Node.class))) {
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

        if (params.size() > 0 && ElementUtils.typeEquals(params.get(0).asType(), Utils.getTypeMirror(processingEnv, VirtualFrame.class))) {
            argumentSize -= 1;
        }
        for (ExecutableElement m : methods) {
            params = m.getParameters();

            int paramsSize = params.size();
            if (params.size() > 0 && ElementUtils.typeEquals(params.get(0).asType(), Utils.getTypeMirror(processingEnv, VirtualFrame.class))) {
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

        Object currentMessage = Utils.getMessage(processingEnv, resolveAnnotation.message());
        if (currentMessage == null) {
            currentMessage = currentGenerator.getMessageName();
        }
        factoryGenerator.addMessageHandler(currentMessage, currentGenerator);
        return true;
    }

    private static boolean isReceiverNonStaticInner(MessageResolution message) {
        try {
            message.receiverType();
            throw new AssertionError();
        } catch (MirroredTypeException mte) {
            // This exception is always thrown: use the mirrors to inspect the class
            DeclaredType type = (DeclaredType) mte.getTypeMirror();
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
        for (Element elem : ElementUtils.getTypeElement(this.processingEnv, receiverTypeFullClassName).getEnclosedElements()) {
            if (elem.getKind().equals(ElementKind.METHOD)) {
                ExecutableElement method = (ExecutableElement) elem;
                if (method.getSimpleName().toString().equals("isInstance") && method.getParameters().size() == 1 &&
                                ElementUtils.typeEquals(method.getParameters().get(0).asType(), Utils.getTypeMirror(processingEnv, TruffleObject.class))) {
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
