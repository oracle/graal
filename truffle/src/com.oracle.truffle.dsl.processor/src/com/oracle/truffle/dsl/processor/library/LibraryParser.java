/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.library;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;

public class LibraryParser extends AbstractParser<LibraryData> {

    public final List<DeclaredType> annotations = Arrays.asList(types.GenerateLibrary, types.GenerateLibrary_DefaultExport, types.GenerateLibrary_Abstract);

    @Override
    public boolean isDelegateToRootDeclaredType() {
        return true;
    }

    @Override
    protected LibraryData parse(Element element, List<AnnotationMirror> mirrors) {
        TypeElement type = (TypeElement) element;
        if (mirrors.isEmpty()) {
            return null;
        }
        AnnotationMirror mirror = mirrors.iterator().next();

        LibraryData model = new LibraryData((TypeElement) element, mirror);

        if (!ElementUtils.typeEquals(type.getSuperclass(), types.Library)) {
            model.addError("Declared library classes must exactly extend the type %s.", ElementUtils.getQualifiedName(types.Library));
            return model;
        }

        if (!element.getModifiers().contains(Modifier.ABSTRACT)) {
            model.addError("Declared library classes must be abstract.");
            return model;
        }
        if (element.getEnclosingElement().getKind() != ElementKind.PACKAGE && !element.getModifiers().contains(Modifier.STATIC)) {
            model.addError("Declared inner library classes must be static.");
            return model;
        }

        model.setDefaultExportLookupEnabled(ElementUtils.getAnnotationValue(Boolean.class, mirror, "defaultExportLookupEnabled"));
        model.setDynamicDispatchEnabled(ElementUtils.getAnnotationValue(Boolean.class, mirror, "dynamicDispatchEnabled"));

        boolean defaultExportReachable = true;
        List<AnnotationMirror> defaultExports = ElementUtils.getRepeatedAnnotation(element.getAnnotationMirrors(), types.GenerateLibrary_DefaultExport);
        for (AnnotationMirror defaultExport : defaultExports) {
            LibraryDefaultExportData export = loadDefaultExportImpl(model, defaultExport, "value");
            if (export == null) {
                continue;
            }

            for (LibraryDefaultExportData prev : model.getDefaultExports()) {
                if (ElementUtils.isAssignable(export.getReceiverType(), prev.getReceiverType())) {
                    model.addError(defaultExport, null, "The receiver type '%s' of the export '%s' is not reachable. " +
                                    "It is shadowed by receiver type '%s' of export '%s'.",
                                    ElementUtils.getSimpleName(export.getReceiverType()),
                                    ElementUtils.getSimpleName(export.getImplType()),
                                    ElementUtils.getSimpleName(prev.getReceiverType()),
                                    ElementUtils.getSimpleName(prev.getImplType()));
                    break;
                }
            }

            model.getDefaultExports().add(export);
            if (ElementUtils.isAssignable(context.getType(Object.class), export.getReceiverType())) {
                defaultExportReachable = false;
            }
        }

        parseAssertions(element, mirror, type, model);

        List<ExecutableElement> allMethods = ElementFilter.methodsIn(CompilerFactory.getCompiler(type).getEnclosedElementsInDeclarationOrder(type));
        allMethods.add(ElementUtils.findExecutableElement(types.Library, "accepts"));

        TypeMirror inferredReceiverType = null;
        Map<String, LibraryMessage> messages = new HashMap<>();
        for (ExecutableElement executable : allMethods) {
            Modifier visibility = ElementUtils.getVisibility(executable.getModifiers());
            if (visibility == Modifier.PRIVATE) {
                continue;
            } else if (executable.getModifiers().contains(Modifier.FINAL)) {
                continue;
            } else if (executable.getModifiers().contains(Modifier.STATIC)) {
                continue;
            } else if (model.isDynamicDispatch() && executable.getSimpleName().toString().equals("cast")) {
                // the cast method is abstract but ignore in the dynamic dispatch library.
                // it is automatically implemented.
                continue;
            }
            String messageName = executable.getSimpleName().toString();

            LibraryMessage message = messages.get(messageName);
            if (message == null) {
                message = new LibraryMessage(model, messageName, executable);
            } else {
                message.addError("Library message must have a unique name. Two methods with the same name found." +
                                "If this method is not intended to be a library message then add the private or final modifier to ignore it.");
                continue;
            }

            if (visibility == null) {
                message.addError("Library messages must be public or protected. Annotate with @GenerateLibrary.Ignore to ignore this method for generation. ");
            }

            if (executable.getParameters().isEmpty()) {
                message.addError("Not enough arguments specified for a library message. " +
                                "The first argument of a library method must be of type Object. " +
                                "Add a receiver argument with type Object resolve this." +
                                "If this method is not intended to be a library message then add the private or final modifier to ignore it.");
            } else {
                TypeMirror methodReceiverType = executable.getParameters().get(0).asType();
                if (inferredReceiverType == null) {
                    inferredReceiverType = methodReceiverType;
                } else if (!ElementUtils.isAssignable(methodReceiverType, inferredReceiverType)) {
                    if (!message.getName().equals("accepts")) {
                        message.addError(String.format("Invalid first argument type %s specified. " +
                                        "The first argument of a library method must be of the same type for all methods. " +
                                        "If this method is not intended to be a library message then add the private or final modifier to ignore it.",
                                        ElementUtils.getSimpleName(methodReceiverType)));
                    }
                }
            }

            LibraryMessage declaredIn = messages.get(messageName);
            if (declaredIn != null) {
                message.addError("Messages with the same name are not supported.");
                continue;
            }

            messages.put(messageName, message);
            model.getMethods().add(message);
        }

        if (!model.hasErrors() && model.getMethods().size() <= 1) {
            model.addError("The library does not export any messages. Use public instance methods to declare library messages.");
        }

        // parse abstract methods
        for (LibraryMessage message : model.getMethods()) {
            AnnotationMirror abstractMirror = ElementUtils.findAnnotationMirror(message.getExecutable(), types.GenerateLibrary_Abstract);
            if (abstractMirror != null) {
                message.setAbstract(true);
                AnnotationValue value = ElementUtils.getAnnotationValue(abstractMirror, "ifExported");
                for (String ifExported : ElementUtils.getAnnotationValueList(String.class, abstractMirror, "ifExported")) {
                    LibraryMessage ifExportedMessage = messages.get(ifExported);
                    if (ifExportedMessage == message) {
                        message.addError(abstractMirror, value, "The ifExported condition links to itself. Remove that condition to resolve this problem.");
                    } else if (ifExportedMessage == null) {
                        message.addError(abstractMirror, value, "The ifExported condition links to an unknown message '%s'. Only valid library messages may be linked.", ifExported);
                    } else {
                        message.getAbstractIfExported().add(ifExportedMessage);
                    }
                }
            }
        }

        if (inferredReceiverType == null) {
            inferredReceiverType = context.getType(Object.class);
        }

        if (!model.hasErrors() && inferredReceiverType.getKind().isPrimitive()) {
            model.addError("Primitive receiver type found. Only reference types are supported.");
            inferredReceiverType = context.getType(Object.class);
        }
        TypeMirror customReceiverType = ElementUtils.getAnnotationValue(TypeMirror.class, mirror, "receiverType", false);
        if (customReceiverType != null) {
            AnnotationValue customReceiverTypeValue = ElementUtils.getAnnotationValue(mirror, "receiverType");
            if (ElementUtils.typeEquals(customReceiverType, inferredReceiverType)) {
                model.addError(mirror, customReceiverTypeValue,
                                "Redundant receiver type. This receiver type could be inferred from the method signatures. Remove the explicit receiver type to resolve this redundancy.");
            }
            if (customReceiverType.getKind() != TypeKind.DECLARED) {
                model.addError(mirror, customReceiverTypeValue,
                                "Invalid type. Valid declared type expected.");
            }
            model.setExportsReceiverType(customReceiverType);
        } else {
            model.setExportsReceiverType(inferredReceiverType);
        }
        model.setSignatureReceiverType(inferredReceiverType);

        if (defaultExportReachable) {
            model.getDefaultExports().add(new LibraryDefaultExportData(null, context.getType(Object.class)));
            ExportsData exports = new ExportsData(context, type, mirror);
            ExportsLibrary objectExports = new ExportsLibrary(context, type, mirror, exports, model, model.getSignatureReceiverType(), true);

            for (LibraryMessage message : objectExports.getLibrary().getMethods()) {
                if (message.getName().equals("accepts")) {
                    continue;
                }
                ExportMessageData exportMessage = new ExportMessageData(objectExports, message, null, null);
                objectExports.getExportedMessages().put(message.getName(), exportMessage);
            }

            model.setObjectExports(objectExports);
        }

        return model;
    }

    private static void parseAssertions(Element element, AnnotationMirror mirror, TypeElement type, LibraryData model) {
        TypeMirror assertions = ElementUtils.getAnnotationValue(TypeMirror.class, mirror, "assertions", false);
        if (assertions != null) {
            AnnotationValue value = ElementUtils.getAnnotationValue(mirror, "assertions");
            TypeElement assertionsType = ElementUtils.castTypeElement(assertions);
            if (assertionsType.getModifiers().contains(Modifier.ABSTRACT)) {
                model.addError(value, "Assertions type must not be abstract.");
                return;
            }
            if (!ElementUtils.isVisible(element, assertionsType)) {
                model.addError(value, "Assertions type must be visible.");
                return;
            }
            if (!ElementUtils.isAssignable(assertions, type.asType())) {
                model.addError(value, "Assertions type must be a subclass of the library type '%s'.", ElementUtils.getSimpleName(model.getTemplateType()));
                return;
            }
            ExecutableElement foundConstructor = null;
            for (ExecutableElement constructor : ElementFilter.constructorsIn(assertionsType.getEnclosedElements())) {
                if (constructor.getParameters().size() == 1) {
                    if (ElementUtils.typeEquals(constructor.getParameters().get(0).asType(), model.getTemplateType().asType())) {
                        foundConstructor = constructor;
                        break;
                    }
                }
            }
            if (foundConstructor == null) {
                model.addError(value, "No constructor with single delegate parameter of type %s found.", ElementUtils.getSimpleName(model.getTemplateType()));
                return;
            }

            if (!ElementUtils.isVisible(model.getTemplateType(), foundConstructor)) {
                model.addError(value, "Assertions constructor is not visible.");
                return;
            }
            model.setAssertions(assertions);
        }
    }

    private LibraryDefaultExportData loadDefaultExportImpl(LibraryData model, AnnotationMirror exportAnnotation, String annotationName) {
        TypeMirror type = ElementUtils.getAnnotationValue(TypeMirror.class, exportAnnotation, annotationName);
        AnnotationValue typeValue = ElementUtils.getAnnotationValue(exportAnnotation, annotationName, false);
        if (typeValue == null) {
            return null;
        }
        if (type.getKind() != TypeKind.DECLARED) {
            model.addError(exportAnnotation, typeValue, "The %s type '%s' is invalid.", annotationName, ElementUtils.getSimpleName(type));
            return null;
        }

        List<AnnotationMirror> exportedLibraries = ElementUtils.getRepeatedAnnotation(ElementUtils.castTypeElement(type).getAnnotationMirrors(), types.ExportLibrary);
        TypeMirror receiverClass = null;
        for (AnnotationMirror exportedLibrary : exportedLibraries) {
            TypeMirror exportedLib = ElementUtils.getAnnotationValue(TypeMirror.class, exportedLibrary, "value");
            if (ElementUtils.typeEquals(model.getTemplateType().asType(), exportedLib)) {
                receiverClass = ElementUtils.getAnnotationValue(TypeMirror.class, exportedLibrary, "receiverType", false);
                if (receiverClass == null) {
                    model.addError(exportAnnotation, typeValue, "Default export '%s' must specify a receiverType.", ElementUtils.getSimpleName(exportedLib));
                    return null;
                }
                break;
            }
        }
        if (receiverClass == null) {
            model.addError(exportAnnotation, typeValue, "Default export '%s' does not export a library '%s'.", ElementUtils.getSimpleName(type),
                            ElementUtils.getSimpleName(model.getMessageElement().asType()));
            return null;
        }

        return new LibraryDefaultExportData(type, receiverClass);
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.GenerateLibrary;
    }

}
