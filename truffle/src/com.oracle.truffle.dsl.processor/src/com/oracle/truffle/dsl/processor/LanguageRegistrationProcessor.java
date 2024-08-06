/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

@SupportedAnnotationTypes(TruffleTypes.TruffleLanguage_Registration_Name)
public final class LanguageRegistrationProcessor extends AbstractRegistrationProcessor {

    // also update list in PolyglotEngineImpl#RESERVED_IDS
    private static final Set<String> RESERVED_IDS = new HashSet<>(
                    Arrays.asList("host", "graal", "truffle", "language", "instrument", "graalvm", "context", "polyglot", "compiler", "vm", "file",
                                    "engine", "log", "image-build-time"));

    private static final Set<String> IGNORED_ATTRIBUTES = Set.of("services", "fileTypeDetectors", "internalResources");

    static String resolveLanguageId(Element annotatedElement, AnnotationMirror registration) {
        String id = ElementUtils.getAnnotationValue(String.class, registration, "id");
        if (id.isEmpty()) {
            return getDefaultLanguageId(annotatedElement);
        } else {
            return id;
        }
    }

    private static String getDefaultLanguageId(Element annotatedElement) {
        String className = annotatedElement.toString();
        assert TruffleTypes.TEST_PACKAGES.stream().anyMatch(className::startsWith);
        return className.replaceAll("[.$]", "_").toLowerCase();
    }

    @Override
    boolean validateRegistration(Element annotatedElement, AnnotationMirror registrationMirror) {
        if (annotatedElement.getModifiers().contains(Modifier.PRIVATE)) {
            emitError("Registered language class must be at least package protected", annotatedElement);
            return false;
        }
        if (annotatedElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !annotatedElement.getModifiers().contains(Modifier.STATIC)) {
            emitError("Registered language inner-class must be static", annotatedElement);
            return false;
        }
        ProcessorContext context = ProcessorContext.getInstance();
        TruffleTypes types = context.getTypes();
        TypeMirror truffleLang = processingEnv.getTypeUtils().erasure(types.TruffleLanguage);
        TypeMirror truffleLangProvider = types.TruffleLanguageProvider;
        boolean processingTruffleLanguage;
        if (processingEnv.getTypeUtils().isAssignable(annotatedElement.asType(), truffleLang)) {
            processingTruffleLanguage = true;
        } else if (processingEnv.getTypeUtils().isAssignable(annotatedElement.asType(), truffleLangProvider)) {
            processingTruffleLanguage = false;
        } else {
            emitError("Registered language class must subclass TruffleLanguage", annotatedElement);
            return false;
        }
        boolean foundConstructor = false;
        for (ExecutableElement constructor : ElementFilter.constructorsIn(annotatedElement.getEnclosedElements())) {
            if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            if (!constructor.getParameters().isEmpty()) {
                continue;
            }
            foundConstructor = true;
            break;
        }

        Element singletonElement = null;
        for (Element mem : annotatedElement.getEnclosedElements()) {
            if (!mem.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (mem.getKind() != ElementKind.FIELD) {
                continue;
            }
            if (!mem.getModifiers().contains(Modifier.FINAL)) {
                continue;
            }
            if (!"INSTANCE".equals(mem.getSimpleName().toString())) {
                continue;
            }
            if (processingEnv.getTypeUtils().isAssignable(mem.asType(), truffleLang)) {
                singletonElement = mem;
                break;
            }
        }
        boolean valid = true;

        if (processingTruffleLanguage && singletonElement != null) {
            emitWarning("Using a singleton field is deprecated. Please provide a public no-argument constructor instead.", singletonElement);
            valid = false;
        } else {
            if (!foundConstructor) {
                emitError("A TruffleLanguage subclass must have at least package protected no argument constructor.", annotatedElement);
                return false;
            }
        }

        Set<String> mimeTypes = new HashSet<>();
        List<String> characterMimeTypes = ElementUtils.getAnnotationValueList(String.class, registrationMirror, "characterMimeTypes");
        if (!validateMimeTypes(mimeTypes, annotatedElement, registrationMirror, ElementUtils.getAnnotationValue(registrationMirror, "characterMimeTypes"), characterMimeTypes)) {
            return false;
        }
        List<String> byteMimeTypes = ElementUtils.getAnnotationValueList(String.class, registrationMirror, "byteMimeTypes");
        if (!validateMimeTypes(mimeTypes, annotatedElement, registrationMirror, ElementUtils.getAnnotationValue(registrationMirror, "byteMimeTypes"), byteMimeTypes)) {
            return false;
        }

        String defaultMimeType = ElementUtils.getAnnotationValue(String.class, registrationMirror, "defaultMimeType");
        if (mimeTypes.size() > 1 && (defaultMimeType == null || defaultMimeType.equals(""))) {
            emitError("No defaultMimeType attribute specified. " +
                            "The defaultMimeType attribute needs to be specified if more than one MIME type was specified.", annotatedElement,
                            registrationMirror, ElementUtils.getAnnotationValue(registrationMirror, "defaultMimeType"));
            return false;
        }

        if (defaultMimeType != null && !defaultMimeType.equals("") && !mimeTypes.contains(defaultMimeType)) {
            emitError("The defaultMimeType is not contained in the list of supported characterMimeTypes or byteMimeTypes. Add the specified default MIME type to" +
                            " character or byte MIME types to resolve this.", annotatedElement,
                            registrationMirror, ElementUtils.getAnnotationValue(registrationMirror, "defaultMimeType"));
            return false;
        }

        String id = ElementUtils.getAnnotationValue(String.class, registrationMirror, "id");
        if (id.isEmpty()) {
            String className = annotatedElement.toString();
            if (TruffleTypes.TEST_PACKAGES.stream().noneMatch(className::startsWith)) {
                emitError("The attribute id is mandatory.", annotatedElement, registrationMirror, null);
                return false;
            }
        }
        if (RESERVED_IDS.contains(id)) {
            emitError(String.format("Id '%s' is reserved for other use and must not be used as id.", id), annotatedElement, registrationMirror,
                            ElementUtils.getAnnotationValue(registrationMirror, "id"));
            return false;
        }

        if (!validateFileTypeDetectors(annotatedElement, registrationMirror)) {
            return false;
        }

        if (!validateInternalResources(annotatedElement, registrationMirror, context)) {
            return false;
        }

        if (valid) {
            assertNoErrorExpected(annotatedElement);
        }
        return processingTruffleLanguage;
    }

    @Override
    DeclaredType getProviderClass() {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        return types.TruffleLanguageProvider;
    }

    @Override
    Iterable<AnnotationMirror> getProviderAnnotations(TypeElement annotatedElement) {
        List<AnnotationMirror> result = new ArrayList<>(2);
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        DeclaredType registrationType = types.TruffleLanguage_Registration;
        CodeAnnotationMirror registration = copyAnnotations(ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(), registrationType),
                        (t) -> !IGNORED_ATTRIBUTES.contains(t.getSimpleName().toString()));
        if (ElementUtils.getAnnotationValue(String.class, registration, "id").isEmpty()) {
            registration.setElementValue(registration.findExecutableElement("id"), new CodeAnnotationValue(getDefaultLanguageId(annotatedElement)));
        }
        result.add(registration);
        AnnotationMirror providedTags = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(), types.ProvidedTags);
        if (providedTags != null) {
            result.add(providedTags);
        }
        return result;
    }

    @Override
    void implementMethod(TypeElement annotatedElement, CodeExecutableElement methodToImplement) {
        ProcessorContext context = ProcessorContext.getInstance();
        TruffleTypes types = context.getTypes();
        CodeTreeBuilder builder = methodToImplement.createBuilder();
        switch (methodToImplement.getSimpleName().toString()) {
            case "create":
                DeclaredType languageType = (DeclaredType) annotatedElement.asType();
                List<? extends TypeParameterElement> typeParams = annotatedElement.getTypeParameters();
                if (!typeParams.isEmpty()) {
                    builder.startReturn().string("new " + annotatedElement.getQualifiedName() + "<>()").end();
                } else {
                    builder.startReturn().startNew(languageType).end(2);
                }
                break;
            case "getLanguageClassName": {
                Elements elements = context.getEnvironment().getElementUtils();
                builder.startReturn().doubleQuote(elements.getBinaryName(annotatedElement).toString()).end();
                break;
            }
            case "getServicesClassNames": {
                AnnotationMirror registration = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(),
                                types.TruffleLanguage_Registration);
                generateGetServicesClassNames(registration, builder, context);
                break;
            }
            case "getInternalResourceIds": {
                AnnotationMirror registration = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(),
                                types.TruffleLanguage_Registration);
                generateGetInternalResourceIds(registration, builder, context);
                break;
            }
            case "createInternalResource": {
                AnnotationMirror registration = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(),
                                types.TruffleLanguage_Registration);
                generateCreateInternalResource(registration, methodToImplement.getParameters().get(0), builder, context);
                break;
            }
            case "createFileTypeDetectors": {
                AnnotationMirror registration = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(),
                                types.TruffleLanguage_Registration);
                generateCreateFileTypeDetectors(registration, builder, context);
                break;
            }
            default:
                throw new IllegalStateException("Unsupported method: " + methodToImplement.getSimpleName());
        }
    }

    private static void generateCreateFileTypeDetectors(AnnotationMirror registration, CodeTreeBuilder builder, ProcessorContext context) {
        List<TypeMirror> detectors = ElementUtils.getAnnotationValueList(TypeMirror.class, registration, "fileTypeDetectors");
        if (detectors.isEmpty()) {
            builder.startReturn().startStaticCall(context.getType(List.class), "of").end().end();
        } else {
            builder.startReturn();
            builder.startStaticCall(context.getType(List.class), "of");
            for (TypeMirror detector : detectors) {
                builder.startGroup().startNew(detector).end(2);
            }
            builder.end(2);
        }
    }

    private boolean validateMimeTypes(Set<String> collectedMimeTypes, Element e, AnnotationMirror mirror, AnnotationValue value, List<String> loadedMimeTypes) {
        for (String mimeType : loadedMimeTypes) {
            if (!validateMimeType(e, mirror, value, mimeType)) {
                return false;
            }
            if (collectedMimeTypes.contains(mimeType)) {
                emitError(String.format("Duplicate MIME type specified '%s'. MIME types must be unique.", mimeType), e, mirror,
                                value);
                return false;
            }
            collectedMimeTypes.add(mimeType);
        }
        return true;
    }

    private boolean validateMimeType(Element type, AnnotationMirror mirror, AnnotationValue value, String mimeType) {
        int index = mimeType.indexOf('/');
        if (index == -1 || index == 0 || index == mimeType.length() - 1) {
            emitError(String.format("Invalid MIME type '%s' provided. MIME types consist of a type and a subtype separated by '/'.", mimeType), type, mirror, value);
            return false;
        }
        if (mimeType.indexOf('/', index + 1) != -1) {
            emitError(String.format("Invalid MIME type '%s' provided. MIME types consist of a type and a subtype separated by '/'.", mimeType), type, mirror, value);
            return false;
        }
        return true;
    }

    private boolean validateFileTypeDetectors(Element annotatedElement, AnnotationMirror mirror) {
        AnnotationValue value = ElementUtils.getAnnotationValue(mirror, "fileTypeDetectors", true);
        for (TypeMirror fileTypeDetectorImpl : ElementUtils.getAnnotationValueList(TypeMirror.class, mirror, "fileTypeDetectors")) {
            TypeElement fileTypeDetectorImplElement = ElementUtils.fromTypeMirror(fileTypeDetectorImpl);
            PackageElement targetPackage = ElementUtils.findPackageElement(annotatedElement);
            boolean samePackage = targetPackage.equals(ElementUtils.findPackageElement(fileTypeDetectorImplElement));
            Set<Modifier> modifiers = fileTypeDetectorImplElement.getModifiers();
            if (samePackage ? modifiers.contains(Modifier.PRIVATE) : !modifiers.contains(Modifier.PUBLIC)) {
                emitError(String.format("The class %s must be public or package protected in the %s package. To resolve this, make the %s public or move it to the %s package.",
                                getScopedName(fileTypeDetectorImplElement), targetPackage.getQualifiedName(), getScopedName(fileTypeDetectorImplElement), targetPackage.getQualifiedName()),
                                annotatedElement, mirror, value);
                return false;
            }
            if (fileTypeDetectorImplElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !modifiers.contains(Modifier.STATIC)) {
                emitError(String.format("The class %s must be a static inner-class or a top-level class. To resolve this, make the %s static or top-level class.",
                                getScopedName(fileTypeDetectorImplElement), fileTypeDetectorImplElement.getSimpleName()), annotatedElement, mirror, value);
                return false;
            }
            boolean foundConstructor = false;
            for (ExecutableElement constructor : ElementFilter.constructorsIn(fileTypeDetectorImplElement.getEnclosedElements())) {
                modifiers = constructor.getModifiers();
                if (samePackage ? modifiers.contains(Modifier.PRIVATE) : !modifiers.contains(Modifier.PUBLIC)) {
                    continue;
                }
                if (!constructor.getParameters().isEmpty()) {
                    continue;
                }
                foundConstructor = true;
                break;
            }
            if (!foundConstructor) {
                emitError(String.format("The class %s must have a no argument public constructor. To resolve this, add public %s() constructor.",
                                getScopedName(fileTypeDetectorImplElement), ElementUtils.getSimpleName(fileTypeDetectorImplElement)), annotatedElement, mirror, value);
                return false;
            }
        }
        return true;
    }
}
