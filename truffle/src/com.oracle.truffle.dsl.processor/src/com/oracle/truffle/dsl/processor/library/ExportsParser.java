/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.elementEquals;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.findAnnotationMirror;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterLowerCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.fromTypeMirror;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getAnnotationValue;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getQualifiedName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getRepeatedAnnotation;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSuperType;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getTypeId;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isAssignable;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isSubtype;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEquals;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.DynamicDispatch;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser.ParseMode;

public class ExportsParser extends AbstractParser<ExportsData> {

    public static final List<Class<? extends Annotation>> ANNOTATIONS = Arrays.asList(ExportMessage.class, ExportLibrary.class);

    @Override
    public boolean isDelegateToRootDeclaredType() {
        return false;
    }

    @Override
    protected ExportsData parse(Element element, List<AnnotationMirror> elementMirrors) {
        TypeElement type = (TypeElement) element;
        ExportsData model = parseExports(type, elementMirrors);

        if (model.hasErrors()) {
            return model;
        }

        parsedNodeCache.clear();

        List<Element> members = loadMembers(type);
        Map<String, List<Element>> potentiallyMissedOverrides = new LinkedHashMap<>();

        /*
         * First pass: element creation
         */
        List<ExportMessageElement> exportedElements = new ArrayList<>();
        for (Element member : members) {
            List<AnnotationMirror> exportedMessageMirrors = getRepeatedAnnotation(member.getAnnotationMirrors(), ExportMessage.class);
            if (exportedMessageMirrors.isEmpty()) {
                boolean isMethod = isMethodElement(member);
                boolean isNode = isNodeElement(member);
                String name = null;
                if (isMethod) {
                    name = member.getSimpleName().toString();
                } else if (isNode) {
                    name = inferNodeMessageName((TypeElement) member);
                }
                if (isMethod || isNode) {
                    Element enclosingType = member.getEnclosingElement();
                    if (elementEquals(model.getTemplateType(), enclosingType)) {
                        potentiallyMissedOverrides.computeIfAbsent(name, (n) -> new ArrayList<>()).add(member);
                    }
                }
            } else {
                for (AnnotationMirror exportMessage : exportedMessageMirrors) {
                    exportedElements.addAll(parseExportedMessage(model, member, exportMessage));
                }
            }
        }

        /*
         * Second pass: duplication checks.
         */
        for (ExportMessageElement exportedElement : exportedElements) {
            Element member = exportedElement.getMessageElement();
            if (isMethodElement(member)) {
                if (exportedElement.getExport().getExportedMethod() != null) {
                    // duplicate
                    String error = String.format("Duplicate exported method for library message %s.", exportedElement.getExport().getResolvedMessage().getSimpleName());
                    model.addError(member, error);
                    model.addError(exportedElement.getExport().getExportedMethod().getMessageElement(), error);
                } else {
                    exportedElement.getExport().setExportedMethod(exportedElement);
                }
            } else if (isNodeElement(member)) {
                if (exportedElement.getExport().getExportedClass() != null) {
                    // duplicate
                    String error = String.format("Duplicate exported class for library message %s.", exportedElement.getExport().getResolvedMessage().getSimpleName());
                    model.addError(member, error);
                    model.addError(exportedElement.getExport().getExportedMethod().getMessageElement(), error);
                } else {
                    exportedElement.getExport().setExportedClass(exportedElement);
                }
            } else {
                throw new AssertionError("should not be reachable");
            }
        }

        /*
         * Third pass: initialize and further parsing that need both method and node to be
         * available.
         */
        for (ExportMessageElement exportedElement : exportedElements) {
            Element member = exportedElement.getMessageElement();
            if (isMethodElement(member)) {
                initializeExportedMethod(model, exportedElement);
            } else if (isNodeElement(member)) {
                initializeExportedNode(exportedElement);
            } else {
                throw new AssertionError("should not be reachable");
            }
        }

        TypeMirror receiverClass = null;
        for (Entry<String, ExportsLibrary> entry : model.getExportedLibraries().entrySet()) {
            ExportsLibrary exportLib = entry.getValue();
            if (exportLib.hasErrors()) {
                continue;
            }

            if (receiverClass == null) {
                receiverClass = exportLib.getReceiverClass();
            } else if (!typeEquals(exportLib.getReceiverClass(), receiverClass)) {
                exportLib.addError("All receiver classes must match for a declared java type. Found '%s' and '%s'.", getSimpleName(receiverClass), getSimpleName(exportLib.getReceiverClass()));
                continue;
            }

            Set<LibraryMessage> missingAbstractMessage = new LinkedHashSet<>();
            for (LibraryMessage message : exportLib.getLibrary().getMethods()) {
                List<Element> elementsWithSameName = potentiallyMissedOverrides.getOrDefault(message.getName(), Collections.emptyList());
                if (!elementsWithSameName.isEmpty()) {
                    for (Element overridingElement : elementsWithSameName) {
                        if (overridingElement.getAnnotation(ExportMessage.Ignore.class) == null) {
                            exportLib.addError(overridingElement, "The method has the same name '%s' as a message in the exported library %s. Did you forget to export it? " +
                                            "Use @%s to export the message, @%s to ignore this warning, rename the method or reduce the visibility of the method to private to resolve this warning.",
                                            overridingElement.getSimpleName().toString(),
                                            getSimpleName(exportLib.getLibrary().getTemplateType()),
                                            ExportMessage.class.getSimpleName(), ExportMessage.Ignore.class.getSimpleName());
                        }
                    }
                }
                if (message.isAbstract() && !message.getName().equals("accepts")) {
                    ExportMessageData exportMessage = exportLib.getExportedMesssages().get(message.getName());

                    if (exportMessage == null || exportMessage.getResolvedMessage() != message) {

                        boolean isAbstract;
                        if (!message.getAbstractIfExported().isEmpty()) {
                            isAbstract = false;
                            for (LibraryMessage abstractIfExported : message.getAbstractIfExported()) {
                                if (exportLib.getExportedMesssages().containsKey(abstractIfExported.getName())) {
                                    isAbstract = true;
                                    break;
                                }
                            }
                        } else {
                            isAbstract = true;
                        }

                        if (isAbstract) {
                            missingAbstractMessage.add(message);
                        }
                    }
                }
            }
            if (!missingAbstractMessage.isEmpty()) {
                StringBuilder msg = new StringBuilder(
                                String.format("The following message(s) of library %s are abstract and must be exported using:%n",
                                                getSimpleName(exportLib.getLibrary().getTemplateType())));
                for (LibraryMessage message : missingAbstractMessage) {
                    msg.append("  ").append(generateExpectedSignature(type, message, exportLib.getExplicitReceiver())).append(" {");
                    if (!ElementUtils.isVoid(message.getExecutable().getReturnType())) {
                        msg.append(" return ");
                        switch (message.getExecutable().getReturnType().getKind()) {
                            case ARRAY:
                            case DECLARED:
                            case PACKAGE:
                            case NULL:
                                msg.append("null");
                                break;
                            case BOOLEAN:
                                msg.append("false");
                                break;
                            case BYTE:
                                msg.append("(byte) 0");
                                break;
                            case CHAR:
                                msg.append("(char) 0");
                                break;
                            case DOUBLE:
                                msg.append("0.0D");
                                break;
                            case LONG:
                                msg.append("0L");
                                break;
                            case INT:
                                msg.append("0");
                                break;
                            case FLOAT:
                                msg.append("0.0F");
                                break;
                            case SHORT:
                                msg.append("(short) 0");
                                break;
                            default:
                                throw new AssertionError();
                        }
                        msg.append(";");
                    }
                    msg.append(" }%n");
                }
                exportLib.addError(msg.toString());
            }
        }

        if (model.hasErrors()) {
            return model;
        }

        return model;
    }

    private List<Element> loadMembers(TypeElement templateType) {
        List<Element> elements = new ArrayList<>(CompilerFactory.getCompiler(templateType).getAllMembersInDeclarationOrder(context.getEnvironment(), templateType));
        Iterator<Element> elementIterator = elements.iterator();
        while (elementIterator.hasNext()) {
            Element element = elementIterator.next();
            // not interested in methods of Node
            if (typeEquals(element.getEnclosingElement().asType(), context.getTruffleTypes().getNode())) {
                elementIterator.remove();
            }
            // not interested in methods of Object
            if (typeEquals(element.getEnclosingElement().asType(), context.getType(Object.class))) {
                elementIterator.remove();
            }
        }
        return elements;
    }

    private ExportsData parseExports(TypeElement type, List<AnnotationMirror> elementMirrors) {
        ExportsData model = new ExportsData(context, type, null);

        List<AnnotationMirror> mirrors = new ArrayList<>(elementMirrors);
        TypeElement superType = type;
        while ((superType = getSuperType(superType)) != null) {
            mirrors.addAll(getRepeatedAnnotation(superType.getAnnotationMirrors(), ExportLibrary.class));
        }

        Map<String, AnnotationMirror> mappedMirrors = new LinkedHashMap<>();
        for (AnnotationMirror mirror : mirrors) {
            TypeMirror library = getAnnotationValue(TypeMirror.class, mirror, "value");
            mappedMirrors.putIfAbsent(getTypeId(library), mirror);
        }

        for (Entry<String, AnnotationMirror> entry : mappedMirrors.entrySet()) {
            AnnotationMirror mirror = entry.getValue();
            String libraryId = entry.getKey();
            TypeMirror libraryMirror = getAnnotationValue(TypeMirror.class, mirror, "value");
            TypeMirror receiverClass = getAnnotationValue(TypeMirror.class, mirror, "receiverClass", false);
            AnnotationValue receiverClassValue = getAnnotationValue(mirror, "receiverClass");
            boolean staticReceiver;
            if (receiverClass == null) {
                staticReceiver = false;
                receiverClass = type.asType();
            } else {
                staticReceiver = true;
            }

            LibraryParser parser = new LibraryParser();
            LibraryData libraryData = parser.parse(fromTypeMirror(libraryMirror));
            ExportsLibrary lib = new ExportsLibrary(context, type, mirror, libraryData, receiverClass, staticReceiver);

            ExportsLibrary otherLib = model.getExportedLibraries().get(libraryId);
            model.getExportedLibraries().put(libraryId, lib);

            if (ElementUtils.isPrimitive(receiverClass)) {
                lib.addError(mirror, receiverClassValue, "Primitive receiver classes are not supported yet.");
                continue;
            }

            if (libraryData == null) {
                lib.addError("Class '%s' is not a library annotated with @%s.", getSimpleName(libraryMirror), GenerateLibrary.class.getSimpleName());
                continue;
            } else if (libraryData.hasErrors()) {
                lib.addError("Library specification %s has errors. Please resolve them first.", getSimpleName(libraryMirror));
                continue;
            }

            boolean dispatched = lib.isDispatched();
            if (!staticReceiver && dispatched) {
                lib.addError("@%s cannot be used on types that implement %s. They are mutually exclusive.",
                                ExportLibrary.class.getSimpleName(),
                                DynamicDispatch.class.getSimpleName());
            } else if (staticReceiver && !dispatched) {
                boolean foundDefault = false;
                for (LibraryDefaultExportData defaultExport : libraryData.getDefaultExports()) {
                    if (typeEquals(defaultExport.getImplType(), type.asType())) {
                        foundDefault = true;
                        break;
                    }
                }
                if (!foundDefault) {
                    lib.addError(mirror, getAnnotationValue(mirror, "receiverClass"),
                                    "The annotated type '%s' is not specified using @DefaultExport in the library '%s'. " +
                                                    "Using explicit receiver classes is only supported for default exports or receiver types that implement %s.",
                                    getSimpleName(model.getTemplateType()), getSimpleName(libraryMirror), DynamicDispatch.class.getSimpleName());
                    continue;
                }
            }

            if (otherLib != null) {
                String message = String.format("Duplicate library specified %s.", getSimpleName(libraryMirror));
                otherLib.addError(message);
                lib.addError(message);
                continue;
            }

            if (staticReceiver) {
                if (!isSubtype(receiverClass, libraryData.getSignatureReceiverType())) {
                    lib.addError(mirror, receiverClassValue, "The export receiver class %s is not compatible with the library receiver type '%s' of library '%s'. ",
                                    getSimpleName(receiverClass),
                                    getSimpleName(libraryData.getSignatureReceiverType()),
                                    getSimpleName(libraryData.getTemplateType().asType()));
                }
            } else {
                if (!isSubtype(type.asType(), libraryData.getExportsReceiverType())) {
                    lib.addError("Type %s is not compatible with the receiver type '%s' of exported library '%s'. " +
                                    "Inhert from type '%s' to resolve this.",
                                    getSimpleName(type.asType()),
                                    getSimpleName(libraryData.getExportsReceiverType()),
                                    getSimpleName(libraryData.getTemplateType().asType()),
                                    getSimpleName(libraryData.getExportsReceiverType()));
                }
            }

            for (LibraryMessage message : libraryData.getMethods()) {
                model.getLibraryMessages().computeIfAbsent(message.getName(), (n) -> new ArrayList<>()).add(message);
            }
        }
        return model;
    }

    private List<ExportMessageElement> parseExportedMessage(ExportsData model, Element member, AnnotationMirror exportAnnotation) throws AssertionError {
        AnnotationValue nameValue = getAnnotationValue(exportAnnotation, "name", false);
        String name = getAnnotationValue(String.class, exportAnnotation, "name");
        String error = null;
        AnnotationValue errorValue = null;
        if (nameValue == null) {
            if (isMethodElement(member)) {
                name = member.getSimpleName().toString();
            } else if (isNodeElement(member)) {
                name = inferNodeMessageName((TypeElement) member);
            } else {
                error = "Unsupported exported element.";
            }
        }

        AnnotationValue libraryValue = getAnnotationValue(exportAnnotation, "library", false);
        TypeMirror library = getAnnotationValue(TypeMirror.class, exportAnnotation, "library");
        List<ExportMessageElement> exportMessages;
        if (libraryValue == null) {
            List<LibraryMessage> messages = model.getLibraryMessages().get(name);
            if (messages == null || messages.size() == 0) {
                if (model.getExportedLibraries().isEmpty()) {
                    error = String.format("No libraries exported. Use @%s(MyLibrary.class) on the enclosing type to export libraries.", ExportLibrary.class.getSimpleName());
                } else {
                    StringBuilder libBuilder = new StringBuilder();
                    String sep = "";
                    for (ExportsLibrary lib : model.getExportedLibraries().values()) {
                        libBuilder.append(sep);
                        libBuilder.append(getSimpleName(lib.getLibrary().getTemplateType().asType()));
                        sep = ", ";
                    }
                    if (model.getExportedLibraries().size() <= 1) {
                        error = String.format("No message '%s' found for library %s.", name, libBuilder);
                    } else {
                        error = String.format("No message '%s' found for libraries %s.", name, libBuilder);
                    }

                    List<String> fuzzyMatches = fuzzyMatch(model.getLibraryMessages().keySet(), name, 0.7f);
                    if (fuzzyMatches.isEmpty()) {
                        fuzzyMatches = fuzzyMatch(model.getLibraryMessages().keySet(), name, 0.5f);
                    }
                    if (!fuzzyMatches.isEmpty()) {
                        StringBuilder appendix = new StringBuilder(" Did you mean ");
                        sep = "";
                        for (String string : fuzzyMatches) {
                            appendix.append(sep);
                            appendix.append('\'').append(string).append('\'');
                            sep = ", ";
                        }
                        error += appendix.toString() + "?";
                    }
                    errorValue = nameValue;
                }
                model.addError(member, error);
                return Collections.emptyList();
            } else if (messages.size() > 1) {
                LibraryMessage prevMessage = null;
                boolean signatureMatches = true;
                for (LibraryMessage message : messages) {
                    if (prevMessage != null && !ElementUtils.signatureEquals(prevMessage.getExecutable(), message.getExecutable())) {
                        signatureMatches = false;
                        break;
                    }
                    prevMessage = message;
                }

                if (!signatureMatches) {
                    StringBuilder libBuilder = new StringBuilder();
                    String sep = "";
                    for (LibraryMessage ambiguousMessage : messages) {
                        libBuilder.append(sep);
                        libBuilder.append(getSimpleName(ambiguousMessage.getLibrary().getTemplateType().asType()));
                        sep = " and ";
                    }
                    error = String.format("The message name '%s' is ambiguous for libraries %s. " +
                                    "Disambiguate the library by specifying the library explicitely using @%s(library=Library.class).",
                                    name, libBuilder.toString(), ExportMessage.class.getSimpleName());
                    model.addError(member, error);
                    return Collections.emptyList();
                }
            }
            exportMessages = new ArrayList<>(messages.size());
            for (LibraryMessage message : messages) {
                ExportsLibrary exportsLibrary = model.getExportedLibraries().get(getTypeId(((TypeElement) message.getLibrary().getMessageElement()).asType()));
                exportMessages.add(new ExportMessageElement(findOrCreateExport(exportsLibrary, message), member, exportAnnotation));
            }
        } else {
            ExportsLibrary exportsLibrary = model.getExportedLibraries().get(getTypeId(library));
            if (exportsLibrary == null) {
                // not exported
                AnnotationMirror mirror = findAnnotationMirror(context.getEnvironment(), library.getAnnotationMirrors(), GenerateLibrary.class);
                String qualifiedName = getQualifiedName(library);
                if (mirror == null) {
                    error = String.format("Class '%s' is not a library annotated with @%s.", qualifiedName, GenerateLibrary.class.getSimpleName());
                } else {
                    error = String.format("Explicitely specified library '%s' also needs to be exported on the class using @%s(%s.class).", qualifiedName, ExportLibrary.class.getSimpleName(),
                                    getSimpleName(library));
                }
                model.addError(member, error);
                return Collections.emptyList();
            } else {
                List<LibraryMessage> searchMessages = model.getLibraryMessages().get(name);
                LibraryMessage message = null;
                if (searchMessages != null) {
                    for (LibraryMessage searchMessage : searchMessages) {
                        if (searchMessage.getLibrary() == exportsLibrary.getLibrary()) {
                            message = searchMessage;
                            break;
                        }
                    }
                }
                if (message == null) {
                    StringBuilder libBuilder = new StringBuilder();
                    String sep = "";
                    for (ExportsLibrary lib : model.getExportedLibraries().values()) {
                        libBuilder.append(sep);
                        libBuilder.append(getSimpleName(lib.getLibrary().getTemplateType().asType()));
                        sep = ", ";
                    }
                    error = String.format("No message '%s' found for library %s.", name, getSimpleName(exportsLibrary.getLibrary().getTemplateType().asType()));
                    errorValue = nameValue;
                }
                exportMessages = new ArrayList<>(1);
                exportMessages.add(new ExportMessageElement(findOrCreateExport(exportsLibrary, message), member, exportAnnotation));
            }
        }

        if (error != null) {
            for (ExportMessageElement export : exportMessages) {
                export.addError(errorValue, error);
                // one time is enough. its the only element
                break;
            }
        }
        return exportMessages;
    }

    private static ExportMessageData findOrCreateExport(ExportsLibrary exportsLibrary, LibraryMessage message) {
        return exportsLibrary.getExportedMesssages().computeIfAbsent(message.getName(), (n) -> new ExportMessageData(exportsLibrary, message));
    }

    private void initializeExportedNode(ExportMessageElement exportElement) {
        TypeElement exportedTypeElement = (TypeElement) exportElement.getMessageElement();
        if (exportedTypeElement.getModifiers().contains(Modifier.PRIVATE)) {
            exportElement.addError("Exported message node class must not be private.");
            return;
        } else if (!exportedTypeElement.getModifiers().contains(Modifier.STATIC)) {
            exportElement.addError("Inner message node class must be static.");
            return;
        }
        List<Element> typeMembers = loadMembers(exportedTypeElement);

        boolean hasSpecialization = false;
        boolean hasExecute = false;
        for (ExecutableElement method : ElementFilter.methodsIn(typeMembers)) {
            if (!hasSpecialization && findAnnotationMirror(method, Specialization.class) != null) {
                hasSpecialization = true;
            }
            Set<Modifier> modifiers = method.getModifiers();
            if (!modifiers.contains(Modifier.PRIVATE) //
                            && !modifiers.contains(Modifier.STATIC) //
                            && method.getSimpleName().toString().startsWith("execute")) {
                exportElement.addError(method, "An @%s annotated class must not declare any visible methods starting with 'execute'. Use @%s annotated methods instead.",
                                ExportMessage.class.getSimpleName(), Specialization.class.getSimpleName());
                return;
            }
        }

        if (!isAssignable(exportedTypeElement.asType(), context.getTruffleTypes().getNode())) {
            exportElement.addError("An @%s annotated class must be a subclass of @%s.", ExportMessage.class.getSimpleName(), Node.class.getSimpleName());
            return;
        }

        if (!hasSpecialization) {
            exportElement.addError("An @%s annotated class must have at least one method with @%s annotation.",
                            ExportMessage.class.getSimpleName(), Specialization.class.getSimpleName());
            return;
        }

        if (hasExecute) {
            exportElement.addError("An @%s annotated class must not declary any visible methods starting with 'execute'.",
                            ExportMessage.class.getSimpleName());
            return;
        }

        if (exportElement.hasErrors()) {
            return;
        }

        NodeData parsedNodeData = parseNode(exportedTypeElement, exportElement, typeMembers);
        if (parsedNodeData == null) {
            exportElement.addError("Could not parse invalid node.");
            return;
        }
        parsedNodeData.getNodeId();
        parsedNodeData.setGenerateUncached(false);
        exportElement.setSpecializedNode(parsedNodeData);
    }

    private void initializeExportedMethod(ExportsData model, ExportMessageElement exportedElement) {
        ExecutableElement exportedMethod = (ExecutableElement) exportedElement.getMessageElement();
        LibraryMessage message = exportedElement.getExport().getResolvedMessage();
        ExportsLibrary exportsLibrary = exportedElement.getExport().getExportsLibrary();

        TypeMirror cached = context.getType(Cached.class);
        TypeMirror cachedLibrary = context.getType(CachedLibrary.class);

        List<VariableElement> cachedNodes = new ArrayList<>();
        List<VariableElement> cachedLibraries = new ArrayList<>();
        int realParameterCount = 0;
        for (VariableElement exportParameter : exportedMethod.getParameters()) {
            AnnotationMirror cachedMirror = findAnnotationMirror(exportParameter.getAnnotationMirrors(), cached);
            AnnotationMirror cachedLibraryMirror = findAnnotationMirror(exportParameter.getAnnotationMirrors(), cachedLibrary);
            if (cachedMirror != null) {
                if (cachedLibraryMirror != null) {
                    exportedElement.addError(exportParameter, "@%s and @%s cannot be used at the same time.", Cached.class.getSimpleName(), CachedLibrary.class.getSimpleName());
                    break;
                }
                cachedNodes.add(exportParameter);
            } else if (cachedLibraryMirror != null) {
                cachedLibraries.add(exportParameter);
            } else {
                if (!cachedNodes.isEmpty() || !cachedLibraries.isEmpty()) {
                    exportedElement.addError(exportParameter, "@%s and @%s must be declared at the end of the method.", Cached.class.getSimpleName(),
                                    CachedLibrary.class.getSimpleName());
                    break;
                }
                realParameterCount++;
            }
        }
        verifyMethodSignature(model.getTemplateType(), message, exportedElement, exportedMethod, exportsLibrary.getReceiverClass(), realParameterCount, true);
        if (exportedElement.hasErrors()) {
            return;
        }

        if (!cachedNodes.isEmpty() || !cachedLibraries.isEmpty()) {
            String nodeName = firstLetterUpperCase(exportedMethod.getSimpleName().toString()) + "Node_";
            CodeTypeElement type = GeneratorUtils.createClass(model, null, modifiers(PUBLIC, STATIC), nodeName, context.getType(Node.class));

            AnnotationMirror importStatic = findAnnotationMirror(model.getMessageElement(), ImportStatic.class);
            if (importStatic != null) {
                type.getAnnotationMirrors().add(importStatic);
            }
            type.getAnnotationMirrors().add(exportedElement.getMessageAnnotation());
            CodeExecutableElement element = CodeExecutableElement.clone(exportedMethod);

            DeclaredType specializationType = context.getDeclaredType(Specialization.class);
            CodeAnnotationMirror specialization = new CodeAnnotationMirror(specializationType);
            specialization.setElementValue(ElementUtils.findExecutableElement(specializationType, "limit"), ElementUtils.getAnnotationValue(exportedElement.getMessageAnnotation(), "limit", false));
            element.getAnnotationMirrors().clear();
            element.addAnnotationMirror(specialization);

            boolean isStatic = element.getModifiers().contains(Modifier.STATIC);
            if (!isStatic) {
                element.getParameters().add(0, new CodeVariableElement(exportsLibrary.getReceiverClass(), "this"));
                element.getModifiers().add(Modifier.STATIC);
            }
            type.add(element);
            NodeData parsedNodeData = parseNode(type, exportedElement, Collections.emptyList());
            element.setEnclosingElement(exportedMethod.getEnclosingElement());

            if (parsedNodeData == null) {
                exportedElement.addError("Error could not parse synthetic node: %s", element);
            }

            exportedElement.setSpecializedNode(parsedNodeData);
        }

        if (exportsLibrary.isExplicitReceiver() && !exportedMethod.getModifiers().contains(STATIC)) {
            exportedElement.addError("Exported method must be static. @%s annotated types with explcit receiverClass must only contain static methods.", ExportLibrary.class.getSimpleName());
        }
    }

    // this cache is also needed for correctness
    // we only want to generate code for this once.
    final Map<String, NodeData> parsedNodeCache = new HashMap<>();

    private NodeData parseNode(TypeElement nodeType, ExportMessageElement exportedMessage, List<Element> members) {
        String nodeTypeId = ElementUtils.getTypeId(nodeType.asType());
        NodeData cachedData = parsedNodeCache.get(nodeTypeId);
        if (cachedData != null) {
            return cachedData;
        }

        List<ExecutableElement> foundExecutes = new ArrayList<>();
        for (ExecutableElement method : ElementFilter.methodsIn(members)) {
            if (!method.getModifiers().contains(Modifier.PRIVATE) //
                            && !method.getModifiers().contains(Modifier.STATIC) //
                            && method.getSimpleName().toString().startsWith("execute") &&
                            !ElementUtils.canThrowTypeExact(method.getThrownTypes(), context.getType(UnexpectedResultException.class))) {
                foundExecutes.add(method);
            }
        }

        LibraryMessage message = exportedMessage.getExport().getResolvedMessage();
        TypeElement parseNodeType;
        CodeExecutableElement syntheticExecute = null;
        CodeTypeElement clonedType = CodeTypeElement.cloneShallow(nodeType);

        if (foundExecutes.isEmpty()) {
            syntheticExecute = CodeExecutableElement.clone(message.getExecutable());
            // temporarily set to execute* to allow the parser to parse it
            syntheticExecute.setSimpleName(CodeNames.of("execute" + ElementUtils.firstLetterUpperCase(message.getName())));
            syntheticExecute.getParameters().set(0, new CodeVariableElement(exportedMessage.getReceiverType(), "receiver"));
            syntheticExecute.getModifiers().add(Modifier.ABSTRACT);
            syntheticExecute.setVarArgs(false);
            clonedType.add(syntheticExecute);
        }

        boolean requireUncached = (isNodeElement(exportedMessage.getMessageElement()) && exportedMessage.getExport().getExportedMethod() == null) ||
                        isMethodElement(exportedMessage.getMessageElement());

        // add enclosing type to static imports. merge with existing static imports
        AnnotationMirror generateUncached = findAnnotationMirror(nodeType, GenerateUncached.class);
        AnnotationMirror importStatic = findAnnotationMirror(nodeType, ImportStatic.class);
        List<AnnotationValue> staticImports = new ArrayList<>();
        if (importStatic != null) {
            for (TypeMirror existingImport : ElementUtils.getAnnotationValueList(TypeMirror.class, importStatic, "value")) {
                staticImports.add(new CodeAnnotationValue(existingImport));
            }
        }
        DeclaredType importStaticType = context.getDeclaredType(ImportStatic.class);
        staticImports.add(new CodeAnnotationValue(exportedMessage.getExport().getExportsLibrary().getTemplateType().asType()));
        CodeAnnotationMirror newImports = new CodeAnnotationMirror(importStaticType);
        newImports.setElementValue(ElementUtils.findExecutableElement(importStaticType, "value"), new CodeAnnotationValue(staticImports));

        clonedType.getAnnotationMirrors().clear();
        clonedType.getAnnotationMirrors().add(newImports);
        if (generateUncached != null) {
            clonedType.getAnnotationMirrors().add(generateUncached);
        } else if (requireUncached) {
            clonedType.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(GenerateUncached.class)));
        }

        parseNodeType = clonedType;

        NodeData parsedNodeData = NodeParser.createExportParser(exportedMessage.getExport().getExportsLibrary().getLibrary().getTemplateType().asType()).parse(parseNodeType, false);
        if (syntheticExecute != null) {
            // reset name to library
            syntheticExecute.setEnclosingElement(message.getExecutable().getEnclosingElement());
        }

        // redirect errors on generated elements to the outer element
        // JDT will otherwise just ignore those messages and not display anything.
        if (parsedNodeData != null) {
            if (parsedNodeData.hasErrorsOrWarnings()) {
                parsedNodeData.redirectMessagesOnGeneratedElements(exportedMessage);
            }
            parsedNodeData.setGenerateUncached(false);
        }

        parsedNodeCache.put(nodeTypeId, parsedNodeData);

        return parsedNodeData;
    }

    private static boolean isNodeElement(Element member) {
        return member.getKind().isClass();
    }

    private static boolean isMethodElement(Element member) {
        return member.getKind() == ElementKind.METHOD;
    }

    private static String inferNodeMessageName(TypeElement type) {
        String name = type.getSimpleName().toString();
        if (name.endsWith("Node") && !name.equals("Node")) {
            name = firstLetterLowerCase(name.substring(0, name.length() - 4));
        }
        return name;
    }

    private static boolean verifyMethodSignature(TypeElement type, LibraryMessage message, ExportMessageElement exportedMessage, ExecutableElement exportedMethod, TypeMirror receiverType,
                    int realParameterCount,
                    boolean emitErrors) {
        ExecutableElement libraryMethod = message.getExecutable();

        if (exportedMessage.getExport().getExportsLibrary().isExplicitReceiver() && !exportedMethod.getModifiers().contains(Modifier.STATIC)) {
            if (emitErrors) {
                exportedMessage.addError("Exported methods with explicit receiver must be static.");
            }
            return false;
        }

        boolean explicitReceiver = exportedMethod.getModifiers().contains(Modifier.STATIC);
        int paramOffset = !explicitReceiver ? 1 : 0;
        List<? extends VariableElement> expectedParameters = libraryMethod.getParameters().subList(paramOffset, libraryMethod.getParameters().size());
        List<? extends VariableElement> exportedParameters = exportedMethod.getParameters().subList(0, realParameterCount);

        TypeMirror expectedStaticReceiverType = explicitReceiver || exportedMessage.getExport().getExportsLibrary().isExplicitReceiver() ? receiverType : null;
        if (exportedParameters.size() != expectedParameters.size()) {
            if (emitErrors) {
                exportedMessage.addError(exportedMethod,
                                "Expected parameter count %s for exported message, but was %s. Expected signature:\n    %s",
                                expectedParameters.size(),
                                exportedParameters.size(),
                                generateExpectedSignature(type, message, expectedStaticReceiverType));
            }
            return false;
        }

        if (!isAssignable(exportedMethod.getReturnType(), libraryMethod.getReturnType())) {
            if (emitErrors) {
                exportedMessage.addError(exportedMethod, "Invalid exported return type. Expected '%s' but was '%s'. Expected signature:\n    %s",
                                getSimpleName(libraryMethod.getReturnType()),
                                getSimpleName(exportedMethod.getReturnType()),
                                generateExpectedSignature(type, message, expectedStaticReceiverType));
            }
            return false;
        }

        for (int i = 0; i < exportedParameters.size(); i++) {
            VariableElement exportedArg = exportedParameters.get(i);
            VariableElement libraryArg = expectedParameters.get(i);
            TypeMirror exportedArgType = exportedArg.asType();
            TypeMirror libraryArgType = libraryArg.asType();

            boolean valid = true;
            if (!typeEquals(exportedArgType, libraryArgType)) {
                if (explicitReceiver && i == 0) {
                    // special case for receiver type
                    if (!typeEquals(exportedArgType, receiverType)) {
                        valid = false;
                    }
                } else {
                    valid = false;
                }
            }

            if (!valid) {
                if (emitErrors) {
                    exportedMessage.addError(exportedArg, "Invalid exported type. Expected '%s' but was '%s'. Expected signature:\n    %s",
                                    getSimpleName(libraryArgType),
                                    getSimpleName(exportedArgType),
                                    generateExpectedSignature(type, message, expectedStaticReceiverType));
                }
                return false;
            }
        }
        return true;
    }

    private static String generateExpectedSignature(TypeElement targetType, LibraryMessage message, TypeMirror staticReceiverType) {
        StringBuilder b = new StringBuilder();
        b.append("@").append(ExportMessage.class.getSimpleName()).append(" ");
        if (staticReceiverType != null) {
            b.append("static ");
        } else {
            if (!targetType.getModifiers().contains(Modifier.FINAL)) {
                b.append("final ");
            }
        }
        b.append(getSimpleName(message.getExecutable().getReturnType()));
        b.append(" ");
        b.append(message.getName());
        b.append("(");
        int startIndex = staticReceiverType == null ? 1 : 0;
        List<? extends VariableElement> parameters = message.getExecutable().getParameters();
        for (int i = startIndex; i < parameters.size(); i++) {
            VariableElement parameter = parameters.get(i);
            TypeMirror parameterType;
            if (i == startIndex && staticReceiverType != null) {
                parameterType = staticReceiverType;
            } else {
                parameterType = parameter.asType();
            }
            if (i > startIndex) {
                b.append(", ");
            }
            b.append(getSimpleName(parameterType));
            b.append(" ");
            b.append(parameter.getSimpleName().toString());
        }
        b.append(")");

        if (!message.getExecutable().getThrownTypes().isEmpty()) {
            b.append(" throws ");
            String sep = "";
            for (TypeMirror thrownType : message.getExecutable().getThrownTypes()) {
                b.append(sep);
                b.append(getSimpleName(thrownType));
                sep = ", ";
            }
        }

        return b.toString();

    }

    /**
     * Returns the set of options that fuzzy match a given option name.
     */
    static List<String> fuzzyMatch(Collection<String> descriptors, String optionKey, float minScore) {
        List<String> matches = new ArrayList<>();
        for (String string : descriptors) {
            float score = stringSimiliarity(string, optionKey);
            if (score >= minScore) {
                matches.add(string);
            }
        }
        return matches;
    }

    /**
     * Compute string similarity based on Dice's coefficient.
     */
    private static float stringSimiliarity(String str1, String str2) {
        int hit = 0;
        for (int i = 0; i < str1.length() - 1; ++i) {
            for (int j = 0; j < str2.length() - 1; ++j) {
                if ((str1.charAt(i) == str2.charAt(j)) && (str1.charAt(i + 1) == str2.charAt(j + 1))) {
                    ++hit;
                    break;
                }
            }
        }
        return 2.0f * hit / (str1.length() + str2.length());
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return ExportLibrary.class;
    }

    @Override
    public Class<? extends Annotation> getRepeatAnnotationType() {
        return ExportLibrary.Repeat.class;
    }

    @Override
    public List<Class<? extends Annotation>> getTypeDelegatedAnnotationTypes() {
        return Arrays.asList(ExportMessage.class, ExportMessage.Repeat.class);
    }

}
