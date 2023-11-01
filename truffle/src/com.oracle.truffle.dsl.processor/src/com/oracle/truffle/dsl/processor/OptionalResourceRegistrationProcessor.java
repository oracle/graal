/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes(TruffleTypes.InternalResource_Id_Name)
public class OptionalResourceRegistrationProcessor extends AbstractRegistrationProcessor {

    @Override
    DeclaredType getProviderClass() {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        return types.InternalResourceProvider;
    }

    @Override
    Iterable<AnnotationMirror> getProviderAnnotations(TypeElement annotatedElement) {
        return List.of();
    }

    @Override
    boolean accepts(Element annotatedElement, AnnotationMirror registrationMirror) {
        return ElementUtils.getAnnotationValue(Boolean.class, registrationMirror, "optional");
    }

    @Override
    boolean validateRegistration(Element annotatedElement, AnnotationMirror registrationMirror) {
        ProcessorContext context = ProcessorContext.getInstance();
        TruffleTypes types = context.getTypes();
        TypeElement internalResourceElement = (TypeElement) annotatedElement;
        if (!processingEnv.getTypeUtils().isAssignable(annotatedElement.asType(), types.InternalResource)) {
            String idSimpleName = ElementUtils.getSimpleName(types.InternalResource_Id);
            String internalResourceClzName = getScopedName((TypeElement) types.InternalResource.asElement());
            emitError(String.format("The annotation @%s can be applied only to %s instances. To resolve this, remove the @%s annotation or implement %s.",
                            idSimpleName, internalResourceClzName, idSimpleName, internalResourceClzName), annotatedElement, registrationMirror, null);
            return false;
        }
        if (ElementUtils.getAnnotationValue(String.class, registrationMirror, "componentId").isEmpty()) {
            String idSimpleName = ElementUtils.getSimpleName(types.InternalResource_Id);
            emitError(String.format("The '@%s.componentId' for an optional internal resource must be set to language " +
                            "or instrument identifier for which the resource is registered. " +
                            "To resolve this, add 'componentId = \"<component-id>\"' or make the internal resource required.",
                            idSimpleName), annotatedElement, registrationMirror, null);
            return false;
        }
        Set<Modifier> modifiers = internalResourceElement.getModifiers();
        if (internalResourceElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !modifiers.contains(Modifier.STATIC)) {
            emitError(String.format("The class %s must be a static inner-class or a top-level class. To resolve this, make the %s static or top-level class.",
                            getScopedName(internalResourceElement), internalResourceElement.getSimpleName()), annotatedElement, registrationMirror, null);
            return false;
        }
        if (internalResourceElement.getModifiers().contains(Modifier.PRIVATE)) {
            PackageElement targetPackage = ElementUtils.findPackageElement(annotatedElement);
            emitError(String.format("The class %s must be public or package protected in the %s package. To resolve this, make the %s package protected or move it to the %s package.",
                            getScopedName(internalResourceElement), targetPackage.getQualifiedName(), getScopedName(internalResourceElement), targetPackage.getQualifiedName()),
                            annotatedElement, registrationMirror, null);
            return false;
        }
        boolean foundConstructor = false;
        for (ExecutableElement constructor : ElementFilter.constructorsIn(internalResourceElement.getEnclosedElements())) {
            if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            if (!constructor.getParameters().isEmpty()) {
                continue;
            }
            foundConstructor = true;
            break;
        }
        if (!foundConstructor) {
            emitError(String.format("The class %s must have a no argument public or package protected constructor. To resolve this, add %s() constructor.",
                            getScopedName(internalResourceElement), ElementUtils.getSimpleName(internalResourceElement)), annotatedElement, registrationMirror, null);
            return false;
        }
        return true;
    }

    @Override
    void implementMethod(TypeElement annotatedElement, CodeExecutableElement methodToImplement) {
        ProcessorContext context = ProcessorContext.getInstance();
        TruffleTypes types = context.getTypes();
        AnnotationMirror registration = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(), types.InternalResource_Id);
        CodeTreeBuilder builder = methodToImplement.createBuilder();
        switch (methodToImplement.getSimpleName().toString()) {
            case "getComponentId" -> builder.startReturn().doubleQuote(ElementUtils.getAnnotationValue(String.class, registration, "componentId")).end();
            case "getResourceId" -> builder.startReturn().doubleQuote(ElementUtils.getAnnotationValue(String.class, registration, "value")).end();
            case "createInternalResource" -> {
                DeclaredType internalResource = (DeclaredType) annotatedElement.asType();
                builder.startReturn().startNew(internalResource).end(2);
            }
            default -> throw new IllegalStateException("Unsupported method: " + methodToImplement.getSimpleName());
        }
    }
}
