/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getTypeSimpleId;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isAssignable;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isSubtype;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEquals;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleSuppressedWarnings;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpressionResolver;
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

public class ExportsParser extends AbstractParser<ExportsData> {

    public static final String EXECUTE_PREFIX = "execute";
    public static final String EXECUTE_SUFFIX = "_";

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

        // set of types that contribute members
        Set<TypeElement> declaringTypes = new HashSet<>();
        Set<TypeElement> declaringInTemplateTypes = new HashSet<>();
        declaringInTemplateTypes.add(type);
        for (ExportsLibrary library : model.getExportedLibraries().values()) {
            declaringTypes.addAll(library.getDeclaringTypes());
            if (library.isDeclaredInTemplate()) {
                declaringInTemplateTypes.addAll(library.getDeclaringTypes());
            }
        }
        Element packageElement = ElementUtils.findPackageElement(type);

        for (TypeElement currentType : declaringInTemplateTypes) {
            if (ElementUtils.elementEquals(currentType, element)) {
                // current type always visible
                continue;
            }
            List<Element> foundInvisibleMembers = new ArrayList<>();
            List<? extends Element> superTypeMembers = loadMembers(null, currentType);
            for (Element superTypeMember : superTypeMembers) {
                List<AnnotationMirror> exportedMessages = getRepeatedAnnotation(superTypeMember.getAnnotationMirrors(), types.ExportMessage);
                if (!exportedMessages.isEmpty()) {
                    if (!ElementUtils.isVisible(packageElement, superTypeMember)) {
                        foundInvisibleMembers.add(superTypeMember);
                    } else if (superTypeMember.getKind().isClass()) {
                        for (Element specializationMember : loadMembers(null, (TypeElement) superTypeMember)) {
                            if (ElementUtils.findAnnotationMirror(specializationMember, types.Specialization) != null && !ElementUtils.isVisible(packageElement, specializationMember)) {
                                foundInvisibleMembers.add(specializationMember);
                            }
                        }
                    }
                }
            }
            if (!foundInvisibleMembers.isEmpty()) {
                StringBuilder b = new StringBuilder();
                for (Element invisibleMember : foundInvisibleMembers) {
                    b.append(System.lineSeparator()).append("   - ");
                    b.append(ElementUtils.getReadableReference(element, invisibleMember));
                }
                model.addError("Found invisible exported elements in super type '%s': %s%nIncrease their visibility to resolve this problem.", ElementUtils.getSimpleName(currentType),
                                b.toString());
            }
        }

        if (model.getExportedLibraries().isEmpty()) {
            for (Element member : loadMembers(null, type)) {
                List<AnnotationMirror> exportedMessageMirrors = getRepeatedAnnotation(member.getAnnotationMirrors(), types.ExportMessage);
                if (!exportedMessageMirrors.isEmpty()) {
                    model.addError("Class declares @%s annotations but does not export any libraries. "//
                                    + "Exported messages cannot be resolved without exported library. "//
                                    + "Add @%s(MyLibrary.class) to the class to fix this.", getSimpleName(types.ExportMessage), getSimpleName(types.ExportLibrary));
                    return model;
                }
            }
        }

        List<? extends Element> members = loadMembers(declaringInTemplateTypes, type);

        /*
         * First pass: element creation
         */
        Map<String, List<Element>> potentiallyMissedOverrides = new LinkedHashMap<>();
        List<ExportMessageData> exportedElements = new ArrayList<>();

        for (Element member : members) {
            List<AnnotationMirror> exportedMessageMirrors = getRepeatedAnnotation(member.getAnnotationMirrors(), types.ExportMessage);
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

        for (ExportMessageData exportedMessage : exportedElements) {
            Element member = exportedMessage.getMessageElement();
            String messageName = exportedMessage.getResolvedMessage().getName();
            Map<String, ExportMessageData> exportedMessages = exportedMessage.getExportsLibrary().getExportedMessages();
            ExportMessageData existing = exportedMessages.get(messageName);
            if (existing != null) {
                Element existingEnclosingElement = existing.getMessageElement().getEnclosingElement();
                Element currentEnclosingElement = exportedMessage.getMessageElement().getEnclosingElement();
                if (ElementUtils.elementEquals(existingEnclosingElement, currentEnclosingElement)) {
                    String error = String.format("Duplicate exported library message %s.", messageName);
                    model.addError(member, error);
                    model.addError(existing.getMessageElement(), error);
                } else if (ElementUtils.isSubtype(currentEnclosingElement.asType(), existingEnclosingElement.asType())) {
                    // new message is more concrete
                    exportedMessages.put(messageName, exportedMessage);
                    existing.setOverriden(true);
                } else {
                    // keep existing exported message
                    exportedMessage.setOverriden(true);
                }
            } else {
                exportedMessages.put(messageName, exportedMessage);
            }
        }

        /*
         * Generate synthetic exports for export delegation.
         */
        for (ExportsLibrary exportsLibrary : model.getExportedLibraries().values()) {

            if (!exportsLibrary.hasExportDelegation()) {
                continue;
            }

            ExportMessageData accepts = exportsLibrary.getExportedMessages().get("accepts");
            if (accepts == null) {
                String delegateName = exportsLibrary.getDelegationVariable().getSimpleName().toString();
                CodeAnnotationMirror annotation = new CodeAnnotationMirror(types.CachedLibrary);
                annotation.setElementValue(ElementUtils.findExecutableElement(types.CachedLibrary, "value"),
                                new CodeAnnotationValue("receiver_." + delegateName));
                CodeExecutableElement executable = CodeExecutableElement.clone(ElementUtils.findMethod(types.Library, "accepts"));
                executable.changeTypes(exportsLibrary.getReceiverType());
                executable.renameArguments("receiver_");
                executable.getModifiers().add(Modifier.STATIC);
                CodeVariableElement var = new CodeVariableElement(exportsLibrary.getLibrary().getTemplateType().asType(), delegateName);
                var.addAnnotationMirror(annotation);
                executable.setEnclosingElement(exportsLibrary.getTemplateType());
                executable.getParameters().add(var);

                LibraryMessage message = null;
                for (LibraryMessage libMessage : exportsLibrary.getLibrary().getMethods()) {
                    if (libMessage.getName().equals("accepts")) {
                        message = libMessage;
                        break;
                    }
                }

                accepts = new ExportMessageData(exportsLibrary, message, executable, annotation);
                exportsLibrary.getExportedMessages().put("accepts", accepts);
                exportedElements.add(accepts);
            } else {
                accepts.addError("Exporting a custom accepts method is currently not supported when export delegation is used in @%s. " +
                                "Remove delegateTo from all exports or remove the accepts export to resolve this.",
                                getSimpleName(types.ExportLibrary));
            }
        }

        for (ExportsLibrary exportsLibrary : model.getExportedLibraries().values()) {
            if (!exportsLibrary.isDeclaredInTemplate()) {
                for (ExportMessageData message : exportsLibrary.getExportedMessages().values()) {
                    if (elementEquals(message.getMessageElement().getEnclosingElement(), type)) {
                        message.addError("The @%s declaration is missing for this exported message. "//
                                        + "Add @%s(%s.class) to the enclosing class %s to resolve this.",
                                        getSimpleName(types.ExportLibrary),
                                        getSimpleName(types.ExportLibrary),
                                        getSimpleName(exportsLibrary.getLibrary().getTemplateType()),
                                        getSimpleName(type));
                    }
                }
            }
        }
        // avoid removal of elements if errors occured.
        if (model.hasErrors()) {
            return model;
        }

        for (ExportsLibrary exportsLibrary : model.getExportedLibraries().values()) {
            if (exportsLibrary.isBuiltinDefaultExport()) {
                // we don't print unused warnings for builtin defaults.
                continue;
            }
            if (exportsLibrary.isDeclaredInTemplate()) {
                boolean foundDeclared = false;
                for (ExportMessageData message : exportsLibrary.getExportedMessages().values()) {
                    if (message.isDeclared()) {
                        foundDeclared = true;
                        break;
                    }
                }

                if (!foundDeclared) {
                    exportsLibrary.addWarning("Exported library %s does not export any messages and therefore has no effect. Remove the export declaration to resolve this.",
                                    getSimpleName(exportsLibrary.getLibrary().getTemplateType()));
                }
            }
        }

        // avoid removal of elements if errors occured.
        if (model.hasErrors()) {
            return model;
        }

        /*
         * filter elements that come from exports not relevant for this class. Remove all export
         * declarations not relevant for this type.
         */
        Predicate<? super ExportsLibrary> filterpredicate = (library -> {
            if (library.isDynamicDispatchTarget()) {
                // Implicitly export super's library if dynamic dispatched.
                return true;
            }
            return library.isDeclaredInTemplate();
        });
        Set<ExportsLibrary> declaredExports = model.getExportedLibraries().values().stream().filter(filterpredicate).collect(Collectors.toSet());
        model.getExportedLibraries().values().removeIf((e) -> !declaredExports.contains(e));
        exportedElements = exportedElements.stream().filter((e) -> declaredExports.contains(e.getExportsLibrary())).collect(Collectors.toList());

        /*
         * Third pass deprecation overload resolution. Might update LibraryMessage.
         */
        for (ExportsLibrary exportsLibrary : declaredExports) {

            for (ExportMessageData exportedMessage : exportsLibrary.getExportedMessages().values()) {
                Element member = exportedMessage.getMessageElement();

                LibraryMessage libraryMessage = exportedMessage.getResolvedMessage();
                List<LibraryMessage> overloads = libraryMessage.getDeprecatedOverloads();
                if (overloads.isEmpty()) {
                    continue;
                }

                List<TypeMirror> actualTypes = computeGenericSignature(member);
                if (!member.getModifiers().contains(STATIC)) {
                    // add receiver
                    actualTypes.add(0, exportsLibrary.getReceiverType());
                }

                LibraryMessage overload = resolveOverload(overloads, actualTypes);
                if (overload != null) {
                    exportedMessage.updateOverload(overload);
                }
            }
        }

        /*
         * Forth pass: initialize and further parsing that need both method and node to be
         * available.
         */
        for (ExportsLibrary exportsLibrary : declaredExports) {
            // recreate cache for every exports library to not confuse exports configuration
            Map<String, NodeData> parsedNodeCache = new HashMap<>();
            int specializedNodeCount = 0;
            for (ExportMessageData exportedElement : exportsLibrary.getExportedMessages().values()) {
                if (exportedElement.isOverriden()) {
                    // must not initialize overridden elements because otherwise the parsedNodeCache
                    // gets confused.
                    continue;
                }
                Element member = exportedElement.getMessageElement();
                if (isMethodElement(member)) {
                    initializeExportedMethod(parsedNodeCache, model, exportedElement);
                } else if (isNodeElement(member)) {
                    initializeExportedNode(parsedNodeCache, exportedElement);
                } else {
                    throw new AssertionError("should not be reachable");
                }
                if (exportedElement.getSpecializedNode() != null) {
                    specializedNodeCount++;
                }
            }
            for (ExportMessageData exportedElement : exportsLibrary.getExportedMessages().values()) {
                if (exportedElement.isOverriden()) {
                    // must not initialize overridden elements because otherwise the parsedNodeCache
                    // gets confused.
                    continue;
                }

                if (exportedElement.getSpecializedNode() != null) {
                    exportedElement.getSpecializedNode().setActivationProbability(1.0d / specializedNodeCount);
                }
            }
        }

        TypeMirror receiverClass = null;
        for (Entry<String, ExportsLibrary> entry : model.getExportedLibraries().entrySet()) {
            ExportsLibrary exportLib = entry.getValue();
            if (exportLib.hasErrors()) {
                continue;
            }

            if (receiverClass == null) {
                receiverClass = exportLib.getReceiverType();
            } else if (!typeEquals(exportLib.getReceiverType(), receiverClass)) {
                exportLib.addError("All receiver classes must match for a declared java type. Found '%s' and '%s'.", getSimpleName(receiverClass), getSimpleName(exportLib.getReceiverType()));
                continue;
            }

            Set<LibraryMessage> missingAbstractMessage = new LinkedHashSet<>();
            Set<LibraryMessage> missingAbstractMessageAsWarning = new LinkedHashSet<>();
            for (LibraryMessage message : exportLib.getLibrary().getMethods()) {
                List<Element> elementsWithSameName = potentiallyMissedOverrides.getOrDefault(message.getName(), Collections.emptyList());
                if (!elementsWithSameName.isEmpty()) {
                    for (Element overridingElement : elementsWithSameName) {
                        if (ElementUtils.findAnnotationMirror(overridingElement, types.ExportMessage_Ignore) == null) {
                            exportLib.addError(overridingElement, "The method has the same name '%s' as a message in the exported library %s. Did you forget to export it? " +
                                            "Use @%s to export the message, @%s to ignore this warning, rename the method or reduce the visibility of the method to private to resolve this warning.",
                                            overridingElement.getSimpleName().toString(),
                                            getSimpleName(exportLib.getLibrary().getTemplateType()),
                                            types.ExportMessage.asElement().getSimpleName().toString(),
                                            types.ExportMessage_Ignore.asElement().getSimpleName().toString());
                        }
                    }
                }
                if (message.isAbstract() && !message.getName().equals("accepts")) {
                    if (!exportLib.isExported(message)) {
                        boolean isAbstract;
                        if (!message.getAbstractIfExported().isEmpty()) {
                            isAbstract = false;
                            for (LibraryMessage abstractIfExported : message.getAbstractIfExported()) {
                                if (exportLib.getExportedMessages().containsKey(abstractIfExported.getName())) {
                                    isAbstract = true;
                                    break;
                                }
                            }
                        } else if (message.getAbstractIfExportedAsWarning().isEmpty()) {
                            isAbstract = !exportLib.hasExportDelegation();
                        } else {
                            isAbstract = false;
                        }

                        if (isAbstract) {
                            missingAbstractMessage.add(message);
                        }

                        if (!message.getAbstractIfExportedAsWarning().isEmpty()) {
                            for (LibraryMessage abstractIfExportedAsWarning : message.getAbstractIfExportedAsWarning()) {
                                if (exportLib.getExportedMessages().containsKey(abstractIfExportedAsWarning.getName())) {
                                    missingAbstractMessageAsWarning.add(message);
                                    break;
                                }
                            }
                        }

                    }
                }
            }
            if (!missingAbstractMessage.isEmpty() || !missingAbstractMessageAsWarning.isEmpty()) {
                Set<LibraryMessage> missingAbstractMessages = new LinkedHashSet<>(missingAbstractMessage);
                missingAbstractMessages.addAll(missingAbstractMessageAsWarning);

                StringBuilder msg = new StringBuilder(
                                String.format("The following message(s) of library %s are abstract and should be exported using:%n",
                                                getSimpleName(exportLib.getLibrary().getTemplateType())));
                for (LibraryMessage message : missingAbstractMessages) {
                    msg.append("  ").append(generateExpectedSignature(type, message, exportLib.getExplicitReceiver())).append(" {");
                    if (!ElementUtils.isVoid(message.getExecutable().getReturnType())) {
                        msg.append(" return ").append(ElementUtils.defaultValue(message.getExecutable().getReturnType()));
                        msg.append(";");
                    }
                    msg.append(" }%n");
                }
                if (!missingAbstractMessage.isEmpty()) {
                    exportLib.addError(msg.toString());
                } else {
                    exportLib.addSuppressableWarning(TruffleSuppressedWarnings.ABSTRACT_LIBRARY_EXPORT, msg.toString());
                }
            }
        }

        for (ExportsLibrary libraryExports : model.getExportedLibraries().values()) {
            for (ExportMessageData export : libraryExports.getExportedMessages().values()) {
                LibraryMessage message = export.getResolvedMessage();
                if (message.isDeprecated()) {
                    LibraryMessage replacement = message.getDeprecatedReplacement();

                    if (replacement != null) {
                        export.addSuppressableWarning(TruffleSuppressedWarnings.DEPRECATION,
                                        "The message with signature '%s' of library '%s' is deprecated and should be updated to be compatible with its new signature '%s'. Update the signature to resolve this problem.",
                                        ElementUtils.getReadableSignature(message.getExecutable()),
                                        getSimpleName(message.getLibrary().getTemplateType()),
                                        ElementUtils.getReadableSignature(replacement.getExecutable()));
                    } else {
                        export.addSuppressableWarning(TruffleSuppressedWarnings.DEPRECATION,
                                        "The message '%s' from library '%s' is deprecated. Please refer to the library documentation on how to resolve this problem.",
                                        message.getExecutable().getSimpleName().toString(),
                                        getSimpleName(message.getLibrary().getTemplateType()));
                    }

                }
            }
        }

        for (ExportsLibrary libraryExports : model.getExportedLibraries().values()) {
            List<NodeData> cachedSharedNodes = new ArrayList<>();
            List<ExportMessageData> exportedMessages = new ArrayList<>();

            for (ExportMessageData export : libraryExports.getExportedMessages().values()) {
                NodeData node = export.getSpecializedNode();
                if (node != null) {
                    cachedSharedNodes.add(node);
                    exportedMessages.add(export);
                }
            }
            libraryExports.setSharedExpressions(NodeParser.computeSharing(libraryExports.getTemplateType(), cachedSharedNodes, true));

            // redirect errors on generated elements to the outer element
            // JDT will otherwise just ignore those messages and not display anything.
            for (int i = 0; i < cachedSharedNodes.size(); i++) {
                NodeData nodeData = cachedSharedNodes.get(i);
                ExportMessageData exportedMessage = exportedMessages.get(i);
                if (nodeData.hasErrorsOrWarnings()) {
                    nodeData.redirectMessagesOnGeneratedElements(exportedMessage);
                }
                nodeData.setGenerateUncached(false);
            }
        }

        for (ExportMessageData message : exportedElements) {
            if (!elementEquals(message.getMessageElement().getEnclosingElement(),
                            model.getTemplateType())) {
                message.redirectMessages(message.getExportsLibrary());
            }
        }

        if (isGenerateSlowPathOnly(type)) {
            for (ExportsLibrary libraryExports : model.getExportedLibraries().values()) {
                for (ExportMessageData export : libraryExports.getExportedMessages().values()) {
                    if (export.isClass() && export.getSpecializedNode() != null) {
                        NodeParser.removeFastPathSpecializations(export.getSpecializedNode(), libraryExports.getSharedExpressions());
                    }
                }
            }
        }
        return model;
    }

    private static LibraryMessage resolveOverload(List<LibraryMessage> overloads, List<TypeMirror> genericSignature) {
        for (LibraryMessage overload : overloads) {
            if (overload.isCompatibleExact(genericSignature)) {
                return overload;
            }
        }
        for (LibraryMessage overload : overloads) {
            if (overload.isCompatibleAssignable(genericSignature)) {
                return overload;
            }
        }
        return null;
    }

    private List<TypeMirror> computeGenericSignature(Element member) {
        if (isMethodElement(member)) {
            ExecutableElement exportedMethod = (ExecutableElement) member;
            return computeSpecializationSignature(exportedMethod);
        } else if (isNodeElement(member)) {
            TypeElement type = (TypeElement) member;
            List<List<TypeMirror>> signatures = new ArrayList<>();
            int maxArguments = 0;

            for (Element nodeMember : loadMembers(Set.of(type), type)) {
                if (nodeMember.getKind() != ElementKind.METHOD) {
                    continue;
                }
                if (ElementUtils.findAnnotationMirror(nodeMember, types.Specialization) == null && ElementUtils.findAnnotationMirror(nodeMember, types.Fallback) == null) {
                    continue;
                }
                List<TypeMirror> signature = computeSpecializationSignature((ExecutableElement) nodeMember);

                maxArguments = Math.max(maxArguments, signature.size());
                signatures.add(signature);
            }

            if (signatures.size() == 1) {
                return signatures.get(0);
            }

            List<TypeMirror> commonTypes = new ArrayList<>(maxArguments);
            for (int i = 0; i < maxArguments; i++) {
                List<TypeMirror> possibleTypes = new ArrayList<>();
                for (List<TypeMirror> signature : signatures) {
                    possibleTypes.add(signature.get(i));
                }
                commonTypes.add(ElementUtils.getCommonSuperType(context, possibleTypes));
            }

            return commonTypes;
        } else {
            throw new AssertionError("should not be reachable");
        }
    }

    private static List<TypeMirror> computeSpecializationSignature(ExecutableElement exportedMethod) {
        List<TypeMirror> cachedAnnotations = NodeParser.getCachedAnnotations();
        List<TypeMirror> signature = new ArrayList<>();
        for (VariableElement exportParameter : exportedMethod.getParameters()) {
            for (TypeMirror cachedAnnotation : cachedAnnotations) {
                AnnotationMirror found = ElementUtils.findAnnotationMirror(exportParameter.getAnnotationMirrors(), cachedAnnotation);
                if (found != null) {
                    return signature;
                }
            }
            signature.add(exportParameter.asType());
        }
        return signature;
    }

    private List<? extends Element> loadMembers(Set<TypeElement> relevantTypes, TypeElement templateType) {
        List<? extends Element> elements;
        if (relevantTypes != null && relevantTypes.isEmpty()) {
            return Collections.emptyList();
        } else if (relevantTypes == null || (relevantTypes.size() == 1 && elementEquals(relevantTypes.iterator().next(), templateType))) {
            elements = CompilerFactory.getCompiler(templateType).getEnclosedElementsInDeclarationOrder(templateType);
        } else {
            elements = CompilerFactory.getCompiler(templateType).getAllMembersInDeclarationOrder(context.getEnvironment(), templateType);
        }
        elements = new ArrayList<>(elements);

        Set<String> relevantTypeIds = null;
        if (relevantTypes != null) {
            relevantTypeIds = new HashSet<>();
            for (TypeElement element : relevantTypes) {
                relevantTypeIds.add(ElementUtils.getTypeSimpleId(element.asType()));
            }
        }

        Iterator<? extends Element> elementIterator = elements.iterator();
        while (elementIterator.hasNext()) {
            Element element = elementIterator.next();
            TypeMirror enclosingType = element.getEnclosingElement().asType();
            if (relevantTypeIds != null && !relevantTypeIds.contains(ElementUtils.getTypeSimpleId(enclosingType))) {
                elementIterator.remove();
            } else if (!ElementUtils.typeEquals(templateType.asType(), enclosingType) && !ElementUtils.isVisible(templateType, element)) {
                elementIterator.remove();
            } else if (ElementUtils.isObject(enclosingType)) {
                // not interested in object methods
                elementIterator.remove();
            }
        }

        return elements;
    }

    private ExportsData parseExports(TypeElement type, List<AnnotationMirror> elementMirrors) {
        ExportsData model = new ExportsData(context, type, null);

        if (type.getKind().isInterface()) {
            model.addError("@%s is not supported for interfaces at the moment.", types.ExportLibrary.asElement().getSimpleName().toString());
            return model;
        }

        if (ElementUtils.getVisibility(type.getModifiers()) == Modifier.PRIVATE) {
            model.addError("The exported type must not be private. " +
                            "Increase visibility to resolve this.");
            return model;
        }

        Map<AnnotationMirror, TypeElement> mirrors = new LinkedHashMap<>();
        for (AnnotationMirror annotationMirror : elementMirrors) {
            mirrors.put(annotationMirror, type);
        }

        TypeElement superType = type;
        while ((superType = getSuperType(superType)) != null) {
            for (AnnotationMirror mirror : getRepeatedAnnotation(superType.getAnnotationMirrors(), types.ExportLibrary)) {
                mirrors.put(mirror, superType);
            }
        }

        Map<String, List<AnnotationMirror>> mappedMirrors = new LinkedHashMap<>();
        for (AnnotationMirror mirror : mirrors.keySet()) {
            TypeMirror library = getAnnotationValue(TypeMirror.class, mirror, "value");
            mappedMirrors.computeIfAbsent(getTypeSimpleId(library), (id) -> new ArrayList<>()).add(mirror);
        }

        for (Entry<String, List<AnnotationMirror>> entry : mappedMirrors.entrySet()) {
            String libraryId = entry.getKey();
            List<AnnotationMirror> annotationMirrors = entry.getValue();
            AnnotationMirror annotationMirror = annotationMirrors.get(0);

            TypeMirror libraryMirror = getAnnotationValue(TypeMirror.class, annotationMirror, "value");
            AnnotationValue receiverClassValue = getAnnotationValue(annotationMirror, "receiverType");
            boolean explicitReceiver;

            TypeMirror receiverClass = getAnnotationValue(TypeMirror.class, annotationMirror, "receiverType", false);
            if (receiverClass == null) {
                explicitReceiver = false;
                receiverClass = type.asType();
            } else {
                explicitReceiver = true;
            }

            LibraryData libraryData = context.parseIfAbsent(fromTypeMirror(libraryMirror), LibraryParser.class, (t) -> new LibraryParser().parse(t));

            ExportsLibrary lib = new ExportsLibrary(context, type, annotationMirror, model, libraryData, receiverClass, explicitReceiver);
            ExportsLibrary otherLib = model.getExportedLibraries().get(libraryId);

            for (AnnotationMirror mirror : annotationMirrors) {
                lib.getDeclaringTypes().add(mirrors.get(mirror));
            }
            model.getExportedLibraries().put(libraryId, lib);

            Integer priority = getAnnotationValue(Integer.class, annotationMirror, "priority", false);
            if (priority == null && lib.needsDefaultExportProvider()) {
                lib.addError("The priority property must be set for default exports based on service providers. "//
                                + "See @%s(priority=...) for details.",
                                getSimpleName(types.ExportLibrary));
                continue;
            } else if (priority != null) {
                int prio = priority;
                AnnotationValue annotationValue = getAnnotationValue(annotationMirror, "priority");
                if (prio < 0) {
                    LibraryDefaultExportData builtinDefaultExport = libraryData.getBuiltinDefaultExport(receiverClass);
                    if (builtinDefaultExport != null) {
                        lib.addError(annotationValue, "The provided export receiver type '%s' is not reachable with the given priority. "//
                                        + "The '%s' library specifies @%s(%s) which has receiver type '%s' and that shadows this export. "//
                                        + "Increase the priority to a positive integer to resolve this.",
                                        getSimpleName(receiverClass),
                                        getSimpleName(libraryData.getTemplateType()),
                                        getSimpleName(types.GenerateLibrary_DefaultExport),
                                        getSimpleName(builtinDefaultExport.getImplType()),
                                        getSimpleName(builtinDefaultExport.getReceiverType()));
                        continue;
                    }
                }
                if (prio == 0) {
                    lib.addError(annotationValue, "The set priority must be either positive or negative, but must not be 0.");
                    continue;
                }

                lib.setDefaultExportPriority(priority);
            }

            if (ElementUtils.isPrimitive(receiverClass)) {
                lib.addError(annotationMirror, receiverClassValue, "Primitive receiver types are not supported yet.");
                continue;
            }

            if (libraryData == null) {
                lib.addError("Class '%s' is not a library annotated with @%s.", getSimpleName(libraryMirror), types.GenerateLibrary.asElement().getSimpleName().toString());
                continue;
            } else if (libraryData.hasErrors()) {
                lib.addError("Library specification %s has errors. Please resolve them first.", getSimpleName(libraryMirror));
                continue;
            }

            if (otherLib != null) {
                String message = String.format("Duplicate library specified %s.", getSimpleName(libraryMirror));
                otherLib.addError(message);
                lib.addError(message);
                continue;
            }

            lib.setUseForAOT(ElementUtils.getAnnotationValue(Boolean.class, annotationMirror, "useForAOT"));

            if (libraryData.isGenerateAOT()) {
                AnnotationValue useForAOT = getAnnotationValue(annotationMirror, "useForAOT", false);

                if (useForAOT == null) {
                    lib.addError(annotationMirror, useForAOT,
                                    "The useForAOT property needs to be declared for exports of libraries annotated with @%s. " + //
                                                    "Declare the useForAOT property to resolve this problem.",
                                    getSimpleName(types.GenerateAOT));
                }

                Integer useForAOTPriority = getAnnotationValue(Integer.class, annotationMirror, "useForAOTPriority", false);

                if (useForAOTPriority == null && lib.isUseForAOT()) {
                    lib.addError("The useForAOTPriority property must also be set for libraries used for AOT. "//
                                    + "See @%s(useForAOTPriority=...) for details.",
                                    getSimpleName(types.ExportLibrary));
                    continue;
                } else if (useForAOTPriority != null) {
                    lib.setUseForAOTPriority(useForAOTPriority);
                }

            }

            if (lib.isUseForAOT() && !lib.isFinalReceiver()) {
                AnnotationValue useForAOT = getAnnotationValue(annotationMirror, "useForAOT", false);

                lib.addError(annotationMirror, useForAOT,
                                "If useForAOT is set to true the receiver type must be a final. " + //
                                                "The compiled code would otherwise cause performance warnings. " + //
                                                "Add the final modifier to the receiver class or set useForAOT to false to resolve this.");
            } else if (lib.isUseForAOT() && lib.isDynamicDispatchTarget()) {
                AnnotationMirror dynamicDispatchExportMirror = lib.getReceiverDynamicDispatchExport();
                if (dynamicDispatchExportMirror == null) {
                    throw new AssertionError("Should not reach here. isDynamicDispatchTarget should not return true");
                }

                if (!ElementUtils.getAnnotationValue(Boolean.class, dynamicDispatchExportMirror, "useForAOT")) {
                    AnnotationValue useForAOT = getAnnotationValue(annotationMirror, "useForAOT", false);
                    lib.addError(annotationMirror, useForAOT,
                                    "The dynamic dispatch target must set useForAOT to true also for the DynamicDispatch export of the receiver type %s. ",
                                    getSimpleName(lib.getReceiverType()));
                }
            }

            if (lib.isUseForAOT() && !libraryData.isGenerateAOT() && !libraryData.isDynamicDispatch()) {
                AnnotationValue useForAOT = getAnnotationValue(annotationMirror, "useForAOT", false);

                lib.addError(annotationMirror, useForAOT,
                                "The exported library does not support AOT. Add the @%s annotation to the library class %s to resolve this.",
                                getSimpleName(types.GenerateAOT),
                                getQualifiedName(libraryData.getTemplateType()));
            }

            if (explicitReceiver) {
                TypeMirror receiverTypeErasure = context.getEnvironment().getTypeUtils().erasure(libraryData.getSignatureReceiverType());
                if (!isSubtype(receiverClass, receiverTypeErasure)) {
                    lib.addError(annotationMirror, receiverClassValue, "The export receiver type %s is not compatible with the library receiver type '%s' of library '%s'. ",
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

            String transitionLimit = ElementUtils.getAnnotationValue(String.class, annotationMirror, "transitionLimit", false);
            if (transitionLimit != null) {
                DSLExpressionResolver resolver = new DSLExpressionResolver(context, model.getTemplateType(),
                                NodeParser.importVisibleStaticMembers(model.getTemplateType(), model.getTemplateType(), false));
                lib.setTransitionLimit(DSLExpression.parseAndResolve(resolver, lib, "transitionLimit", transitionLimit));
            }

            String delegateTo = ElementUtils.getAnnotationValue(String.class, annotationMirror, "delegateTo", false);
            if (delegateTo != null) {
                AnnotationValue delegateToValue = ElementUtils.getAnnotationValue(annotationMirror, "delegateTo");
                if (receiverClass.getKind() != TypeKind.DECLARED) {
                    lib.addError(delegateToValue, "The receiver type must be declared type for delegation.");
                    continue;
                }

                VariableElement delegateVar = ElementUtils.findVariableElement((DeclaredType) receiverClass, delegateTo);
                if (delegateVar == null) {
                    lib.addError(delegateToValue, "The delegation variable with name '%s' could not be found in type '%s'. " +
                                    "Declare a field 'final Object %s' in '%s' to resolve this problem.",
                                    delegateTo,
                                    ElementUtils.getSimpleName(receiverClass),
                                    delegateTo, ElementUtils.getSimpleName(receiverClass));
                    continue;
                }

                if (!delegateVar.getModifiers().contains(Modifier.FINAL)) {
                    lib.addError(delegateToValue, "The delegation variable with name '%s' in type '%s' must be have the modifier final. " +
                                    "Make the variable final to resolve the problem.",
                                    delegateTo,
                                    ElementUtils.getSimpleName(receiverClass));
                    continue;
                }

                Element packageElement = ElementUtils.findPackageElement(lib.getTemplateType());

                if (!ElementUtils.isVisible(packageElement, delegateVar)) {
                    lib.addError(delegateToValue, "The delegation variable with name '%s' in type '%s' is not visible in package '%s'. " +
                                    "Increase the visibility to resolve this problem.",
                                    delegateTo,
                                    ElementUtils.getSimpleName(receiverClass),
                                    ElementUtils.getPackageName(packageElement));
                    continue;
                }
                TypeMirror delegateType = delegateVar.asType();
                TypeMirror exportsReceiverType = lib.getLibrary().getSignatureReceiverType();
                if (!ElementUtils.isAssignable(delegateType, exportsReceiverType)) {
                    lib.addError(delegateToValue, "The type of export delegation field '%s' is not assignable to the expected type '%s'. " +
                                    "Change the field type to '%s' to resolve this.",
                                    ElementUtils.getSimpleName(receiverClass) + "." + delegateTo,
                                    ElementUtils.getSimpleName(exportsReceiverType),
                                    ElementUtils.getSimpleName(exportsReceiverType));
                    continue;
                }

                lib.setDelegationVariable(delegateVar);

            }

            for (LibraryMessage message : libraryData.getMethods()) {
                model.getLibraryMessages().computeIfAbsent(message.getName(), (n) -> new ArrayList<>()).add(message);
            }
        }

        // initialize dynamic dispatch
        for (ExportsLibrary exportedLibrary : model.getExportedLibraries().values()) {
            if (exportedLibrary.hasErrors()) {
                continue;
            }

            boolean explicitReceiver = exportedLibrary.isExplicitReceiver();
            int dynamicDispatchEnabledCount = 0;
            for (ExportsLibrary library : model.getExportedLibraries().values()) {
                if (library.getLibrary().isDynamicDispatchEnabled()) {
                    dynamicDispatchEnabledCount++;
                }
            }

            if (exportedLibrary.getLibrary().isDynamicDispatch() && dynamicDispatchEnabledCount > 0) {
                exportedLibrary.addError(
                                "@%s cannot be used for other libraries if the %s library is exported. " +
                                                "Using dynamic dispatch and other libraries is mutually exclusive. " +
                                                "To resolve this use the dynamic dispatch mechanism of the receiver type instead to export libraries.",
                                types.ExportLibrary.asElement().getSimpleName().toString(),
                                types.DynamicDispatchLibrary.asElement().getSimpleName().toString());
            } else if (explicitReceiver && !exportedLibrary.getLibrary().isDefaultExportLookupEnabled() && !exportedLibrary.isDynamicDispatchTarget() && !exportedLibrary.isBuiltinDefaultExport()) {

                String dynamicDispatchDisabled = "";
                if (!exportedLibrary.getLibrary().isDynamicDispatchEnabled()) {
                    dynamicDispatchDisabled = String.format("Note that dynamic dispatch is disabled for the exported library '%s'.%n",
                                    ElementUtils.getSimpleName(exportedLibrary.getLibrary().getTemplateType()));
                }

                exportedLibrary.addError(exportedLibrary.getTemplateTypeAnnotation(), //
                                getAnnotationValue(exportedLibrary.getTemplateTypeAnnotation(), "receiverType"),
                                "Using explicit receiver types is only supported for default exports or types that export %s.%n" +
                                                "%s" + // dynamic dispatch disabled
                                                "To resolve this use one of the following strategies:%n" +
                                                "  - Make the receiver type implicit by applying '@%s(%s.class)' to the receiver type '%s' instead.%n" +
                                                "  - Declare a default export on the '%s' library with '@%s(%s.class)'%n" +
                                                "  - Enable default exports with service providers using @%s(defaultExportLookupEnabled=true) on the library and specify an export priority%n" +
                                                "  - Enable dynamic dispatch by annotating the receiver type with '@%s(%s.class)'.",
                                types.DynamicDispatchLibrary.asElement().getSimpleName().toString(),
                                dynamicDispatchDisabled,
                                types.ExportLibrary.asElement().getSimpleName().toString(),
                                exportedLibrary.getLibrary().getTemplateType().getSimpleName().toString(),
                                ElementUtils.getSimpleName(exportedLibrary.getExplicitReceiver()),
                                exportedLibrary.getLibrary().getTemplateType().getSimpleName().toString(),
                                types.GenerateLibrary_DefaultExport.asElement().getSimpleName().toString(),
                                ElementUtils.getSimpleName(exportedLibrary.getTemplateType().asType()),
                                ElementUtils.getSimpleName(types.GenerateLibrary),
                                types.ExportLibrary.asElement().getSimpleName().toString(),
                                types.DynamicDispatchLibrary.asElement().getSimpleName().toString());
            } else {
                if (explicitReceiver && !exportedLibrary.isBuiltinDefaultExport()) {
                    boolean foundInvalidExportsOnReceiver = false;
                    superType = ElementUtils.castTypeElement(exportedLibrary.getExplicitReceiver());
                    while (superType != null) {
                        List<AnnotationMirror> exports = getRepeatedAnnotation(superType.getAnnotationMirrors(), types.ExportLibrary);
                        for (AnnotationMirror export : exports) {
                            TypeMirror exportedLibraryType = getAnnotationValue(TypeMirror.class, export, "value");
                            if (!ElementUtils.typeEquals(exportedLibraryType, types.DynamicDispatchLibrary)) {
                                foundInvalidExportsOnReceiver = true;
                                break;
                            }
                        }
                        superType = getSuperType(superType);
                    }
                    if (foundInvalidExportsOnReceiver) {
                        AnnotationValue receiverClassValue = getAnnotationValue(exportedLibrary.getMessageAnnotation(), "receiverType");
                        exportedLibrary.addError(exportedLibrary.getMessageAnnotation(), receiverClassValue,
                                        "An export that is used for dynamic dispatch must not export any libraries other than %s with the receiver type.",
                                        types.DynamicDispatchLibrary.asElement().getSimpleName().toString());
                        continue;
                    }
                }

            }

        }
        return model;
    }

    private List<ExportMessageData> parseExportedMessage(ExportsData model, Element member, AnnotationMirror exportAnnotation) throws AssertionError {
        AnnotationValue nameValue = getAnnotationValue(exportAnnotation, "name", false);
        String name = getAnnotationValue(String.class, exportAnnotation, "name");
        String error = null;
        AnnotationValue errorValue = null;
        if (nameValue == null) {
            if (isMethodElement(member)) {
                name = member.getSimpleName().toString();
            } else if (isNodeElement(member)) {
                TypeElement type = (TypeElement) member;
                name = inferNodeMessageName(type);
            } else {
                error = "Unsupported exported element.";
            }
        }

        AnnotationValue libraryValue = getAnnotationValue(exportAnnotation, "library", false);
        TypeMirror library = getAnnotationValue(TypeMirror.class, exportAnnotation, "library");
        List<ExportMessageData> exportMessages;
        if (libraryValue == null) {
            List<LibraryMessage> messages = model.getLibraryMessages().get(name);

            if (messages == null || messages.size() == 0) {
                if (model.getExportedLibraries().isEmpty()) {
                    error = String.format("No libraries exported. Use @%s(MyLibrary.class) on the enclosing type to export libraries.", types.ExportLibrary.asElement().getSimpleName().toString());
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
                                    name, libBuilder.toString(), types.ExportMessage.asElement().getSimpleName().toString());
                    model.addError(member, error);
                    return Collections.emptyList();
                }
            }
            exportMessages = new ArrayList<>(messages.size());
            for (LibraryMessage message : messages) {
                ExportsLibrary exportsLibrary = model.getExportedLibraries().get(getTypeSimpleId(((TypeElement) message.getLibrary().getMessageElement()).asType()));
                exportMessages.add(new ExportMessageData(exportsLibrary, message, member, exportAnnotation));
            }
        } else {
            ExportsLibrary exportsLibrary = model.getExportedLibraries().get(getTypeSimpleId(library));
            if (exportsLibrary == null) {
                // not exported
                AnnotationMirror mirror = findAnnotationMirror(library.getAnnotationMirrors(), types.GenerateLibrary);
                String qualifiedName = getQualifiedName(library);
                if (mirror == null) {
                    error = String.format("Class '%s' is not a library annotated with @%s.", qualifiedName, types.GenerateLibrary.asElement().getSimpleName().toString());
                } else {
                    error = String.format("Explicitely specified library '%s' also needs to be exported on the class using @%s(%s.class).", qualifiedName,
                                    types.ExportLibrary.asElement().getSimpleName().toString(),
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
                exportMessages.add(new ExportMessageData(exportsLibrary, message, member, exportAnnotation));
            }
        }

        if (error != null) {
            for (ExportMessageData export : exportMessages) {
                export.addError(errorValue, error);
                // one time is enough. its the only element
                break;
            }
        }
        return exportMessages;
    }

    private void initializeExportedNode(Map<String, NodeData> parsedNodeCache, ExportMessageData exportElement) {
        TypeElement exportedTypeElement = (TypeElement) exportElement.getMessageElement();
        if (exportedTypeElement.getModifiers().contains(Modifier.PRIVATE)) {
            exportElement.addError("Exported message node class must not be private.");
            return;
        } else if (!exportedTypeElement.getModifiers().contains(Modifier.STATIC)) {
            exportElement.addError("Inner message node class must be static.");
            return;
        }
        List<? extends Element> typeMembers = loadMembers(null, exportedTypeElement);

        boolean hasSpecialization = false;
        boolean hasExecute = false;
        for (ExecutableElement method : ElementFilter.methodsIn(typeMembers)) {
            if (!hasSpecialization && findAnnotationMirror(method, types.Specialization) != null) {
                hasSpecialization = true;
            }
            Set<Modifier> modifiers = method.getModifiers();
            if (!modifiers.contains(Modifier.PRIVATE) //
                            && !modifiers.contains(Modifier.STATIC) //
                            && method.getSimpleName().toString().startsWith("execute")) {
                exportElement.addError(method, "An @%s annotated class must not declare any visible methods starting with 'execute'. Use @%s annotated methods instead.",
                                types.ExportMessage.asElement().getSimpleName().toString(), types.Specialization.asElement().getSimpleName().toString());
                return;
            }
        }

        if (!typeEquals(exportedTypeElement.getSuperclass(), context.getType(Object.class))) {
            exportElement.addError("An @%s annotated class must extend Object. Other base classes are not supported.", types.ExportMessage.asElement().getSimpleName().toString(),
                            types.Node.asElement().getSimpleName().toString());
            return;
        }

        if (!hasSpecialization) {
            ExecutableElement signature = exportElement.getResolvedMessage().getExecutable();
            StringBuilder fix = new StringBuilder();
            fix.append("@").append(types.Specialization.asElement().getSimpleName().toString()).append(" ");
            fix.append("static ");
            fix.append(ElementUtils.getSimpleName(signature.getReturnType()));
            fix.append(" ").append("doDefault(");
            String sep = "";
            for (VariableElement var : signature.getParameters()) {
                fix.append(sep);
                TypeMirror type;
                if (sep.length() == 0) { // if receiver
                    type = exportElement.getExportsLibrary().getReceiverType();
                } else {
                    type = var.asType();
                }
                fix.append(ElementUtils.getSimpleName(type));
                fix.append(" ");
                fix.append(var.getSimpleName().toString());
                sep = ", ";
            }
            fix.append(") { ");
            if (!ElementUtils.isVoid(signature.getReturnType())) {
                fix.append("return ").append(ElementUtils.defaultValue(signature.getReturnType())).append("; ");
            }
            fix.append("}");
            exportElement.addError("An @%s annotated class must have at least one method with @%s annotation. " +
                            "Add the following method to resolve this:%n     %s",
                            types.ExportMessage.asElement().getSimpleName().toString(), types.Specialization.asElement().getSimpleName().toString(),
                            fix.toString());
            return;
        }

        if (hasExecute) {
            exportElement.addError("An @%s annotated class must not declary any visible methods starting with 'execute'.",
                            types.ExportMessage.asElement().getSimpleName().toString());
            return;
        }

        if (exportElement.hasErrors()) {
            return;
        }

        NodeData parsedNodeData = parseNode(parsedNodeCache, exportedTypeElement, exportElement, typeMembers);
        if (parsedNodeData == null) {
            exportElement.addError("Could not parse invalid node.");
            return;
        }
        parsedNodeData.getNodeId();
        parsedNodeData.setGenerateUncached(false);
        exportElement.setSpecializedNode(parsedNodeData);
    }

    private void initializeExportedMethod(Map<String, NodeData> parsedNodeCache, ExportsData model, ExportMessageData exportedElement) {
        ExecutableElement exportedMethod = (ExecutableElement) exportedElement.getMessageElement();
        LibraryMessage message = exportedElement.getResolvedMessage();
        ExportsLibrary exportsLibrary = exportedElement.getExportsLibrary();

        if (ElementUtils.getVisibility(exportedMethod.getModifiers()) == Modifier.PRIVATE) {
            exportedElement.addError("The exported method must not be private. " +
                            "Increase visibility to resolve this.");
            return;
        }

        List<TypeMirror> cachedAnnotations = NodeParser.getCachedAnnotations();
        List<VariableElement> cachedNodes = new ArrayList<>();
        List<VariableElement> cachedLibraries = new ArrayList<>();
        int realParameterCount = 0;
        parameters: for (VariableElement exportParameter : exportedMethod.getParameters()) {

            AnnotationMirror cachedMirror = null;
            for (TypeMirror cachedAnnotation : cachedAnnotations) {
                AnnotationMirror found = ElementUtils.findAnnotationMirror(exportParameter.getAnnotationMirrors(), cachedAnnotation);
                if (found == null) {
                    continue;
                }
                if (cachedMirror == null) {
                    cachedMirror = found;
                } else {
                    StringBuilder b = new StringBuilder();
                    String sep = "";
                    for (TypeMirror stringCachedAnnotation : cachedAnnotations) {
                        b.append(sep);
                        b.append("@");
                        b.append(ElementUtils.getSimpleName(stringCachedAnnotation));
                        sep = ", ";
                    }
                    exportedElement.addError(exportParameter, "The following annotations are mutually exclusive for a parameter: %s.", b.toString());
                    continue parameters;
                }
            }

            if (isCachedLibrary(exportParameter)) {
                cachedLibraries.add(exportParameter);
            } else if (cachedMirror != null) {
                cachedNodes.add(exportParameter);
            } else {
                realParameterCount++;
            }
        }

        verifyMethodSignature(model.getTemplateType(), message, exportedElement, exportedMethod, exportsLibrary.getReceiverType(), realParameterCount, true);

        boolean aotExcluded = ElementUtils.findAnnotationMirror(exportedMethod, types.GenerateAOT_Exclude) != null;
        if (aotExcluded && message.getName().equals("accepts")) {
            exportedElement.addError("Cannot use with @%s.%s with the accepts message. The accepts message must always be usable for AOT.",
                            getSimpleName(types.GenerateAOT),
                            getSimpleName(types.GenerateAOT_Exclude));
        }

        if (exportedElement.hasErrors()) {
            return;
        }

        if (!cachedNodes.isEmpty() || !cachedLibraries.isEmpty()) {
            String nodeName = firstLetterUpperCase(exportedMethod.getSimpleName().toString()) + "Node_";
            CodeTypeElement type = GeneratorUtils.createClass(model, null, modifiers(PUBLIC, STATIC), nodeName, types.Node);
            AnnotationMirror importStatic = findAnnotationMirror(model.getMessageElement(), types.ImportStatic);
            if (importStatic != null) {
                type.getAnnotationMirrors().add(importStatic);
            }
            type.getAnnotationMirrors().add(exportedElement.getMessageAnnotation());
            CodeExecutableElement element = CodeExecutableElement.clone(exportedMethod);
            element.getParameters().clear();
            element.getParameters().addAll(exportedMethod.getParameters());

            DeclaredType specializationType = types.Specialization;
            CodeAnnotationMirror specialization = new CodeAnnotationMirror(specializationType);
            specialization.setElementValue(ElementUtils.findExecutableElement(specializationType, "limit"), ElementUtils.getAnnotationValue(exportedElement.getMessageAnnotation(), "limit", false));
            element.getAnnotationMirrors().clear();
            element.addAnnotationMirror(specialization);

            if (aotExcluded) {
                element.getAnnotationMirrors().add(new CodeAnnotationMirror(types.GenerateAOT_Exclude));
            }

            boolean isStatic = element.getModifiers().contains(Modifier.STATIC);
            if (!isStatic) {
                element.getParameters().add(0, new CodeVariableElement(exportedElement.getReceiverType(), "this"));
                element.getModifiers().add(Modifier.STATIC);
            }

            if (message.getName().equals("accepts")) {
                /*
                 * We suppress never default messages in accepts because they will be eager
                 * initialized. So it does not matter whether they are never default. Eager
                 * initialization is computed later in the exports generated, so is not yet
                 * available here.
                 */
                GeneratorUtils.mergeSuppressWarnings(element, TruffleSuppressedWarnings.NEVERDEFAULT);
            }

            type.add(element);
            NodeData parsedNodeData = parseNode(parsedNodeCache, type, exportedElement, Collections.emptyList());
            if (parsedNodeData == null) {
                exportedElement.addError("Error could not parse synthetic node: %s", element);
            }
            element.setEnclosingElement(exportedMethod.getEnclosingElement());
            exportedElement.setSpecializedNode(parsedNodeData);
        }

        if (exportsLibrary.isExplicitReceiver() && !exportedMethod.getModifiers().contains(STATIC)) {
            exportedElement.addError("Exported method must be static. @%s annotated types with explcit receiverClass must only contain static methods.",
                            types.ExportLibrary.asElement().getSimpleName().toString());
        }
    }

    private boolean isCachedLibrary(VariableElement exportParameter) {
        return findAnnotationMirror(exportParameter.getAnnotationMirrors(), types.CachedLibrary) != null;
    }

    // this cache is also needed for correctness
    // we only want to generate code for this once.
    private NodeData parseNode(Map<String, NodeData> parsedNodeCache, TypeElement nodeType, ExportMessageData exportedMessage, List<? extends Element> members) {
        String nodeTypeId = ElementUtils.getTypeSimpleId(nodeType.asType());
        // we skip the node cache for generated accepts messages
        if (!exportedMessage.isGenerated()) {
            NodeData cachedData = parsedNodeCache.get(nodeTypeId);
            if (cachedData != null) {
                return cachedData;
            }
        }

        for (ExecutableElement method : ElementFilter.methodsIn(members)) {
            if (!method.getModifiers().contains(Modifier.PRIVATE) //
                            && !method.getModifiers().contains(Modifier.STATIC) //
                            && method.getSimpleName().toString().startsWith("execute")) {
                exportedMessage.addError(method, "A class annotated with with @%s must not specify methods starting with execute. " +
                                "Execute methods for such classes can be inferred automatically from the message signature.",
                                types.ExportMessage.asElement().getSimpleName().toString());
            }
        }

        if (exportedMessage.hasErrors()) {
            return null;
        }

        LibraryMessage message = exportedMessage.getResolvedMessage();
        CodeExecutableElement syntheticExecute = null;
        CodeTypeElement clonedType = CodeTypeElement.cloneShallow(nodeType);
        // make the node parser happy
        clonedType.setSuperClass(types.Node);
        clonedType.setEnclosingElement(exportedMessage.getMessageElement().getEnclosingElement());

        syntheticExecute = CodeExecutableElement.clone(message.getExecutable());
        // temporarily set to execute* to allow the parser to parse it
        syntheticExecute.setSimpleName(CodeNames.of(EXECUTE_PREFIX + ElementUtils.firstLetterUpperCase(message.getName()) + EXECUTE_SUFFIX));
        syntheticExecute.getParameters().set(0, new CodeVariableElement(exportedMessage.getReceiverType(), "receiver"));
        syntheticExecute.getModifiers().add(Modifier.ABSTRACT);
        syntheticExecute.setVarArgs(false);

        clonedType.add(syntheticExecute);

        // add enclosing type to static imports. merge with existing static imports
        AnnotationMirror generateUncached = findAnnotationMirror(nodeType, types.GenerateUncached);
        AnnotationMirror importStatic = findAnnotationMirror(nodeType, types.ImportStatic);
        List<AnnotationValue> staticImports = new ArrayList<>();
        if (importStatic != null) {
            for (TypeMirror existingImport : ElementUtils.getAnnotationValueList(TypeMirror.class, importStatic, "value")) {
                staticImports.add(new CodeAnnotationValue(existingImport));
            }
        }
        DeclaredType importStaticType = types.ImportStatic;
        staticImports.add(new CodeAnnotationValue(exportedMessage.getExportsLibrary().getTemplateType().asType()));
        CodeAnnotationMirror newImports = new CodeAnnotationMirror(importStaticType);
        newImports.setElementValue(ElementUtils.findExecutableElement(importStaticType, "value"), new CodeAnnotationValue(staticImports));

        clonedType.getAnnotationMirrors().clear();
        clonedType.getAnnotationMirrors().add(newImports);

        if (exportedMessage.getExportsLibrary().isUseForAOT()) {
            clonedType.getAnnotationMirrors().add(new CodeAnnotationMirror(types.GenerateAOT));
        }

        if (generateUncached != null) {
            clonedType.getAnnotationMirrors().add(generateUncached);
        } else {
            clonedType.getAnnotationMirrors().add(new CodeAnnotationMirror(types.GenerateUncached));
        }
        transferReportPolymorphismAnnotations(nodeType, clonedType);

        NodeData parsedNodeData = NodeParser.createExportParser(
                        exportedMessage.getExportsLibrary().getLibrary().getTemplateType().asType(),
                        exportedMessage.getExportsLibrary().getTemplateType(),
                        exportedMessage.getExportsLibrary().hasExportDelegation()).parse(clonedType, false);

        parsedNodeCache.put(nodeTypeId, parsedNodeData);

        return parsedNodeData;

    }

    private void transferReportPolymorphismAnnotations(TypeElement nodeType, CodeTypeElement clonedType) {
        AnnotationMirror reportPolymorphism = findAnnotationMirror(nodeType, types.ReportPolymorphism);
        if (reportPolymorphism != null) {
            clonedType.getAnnotationMirrors().add(reportPolymorphism);
        }
        AnnotationMirror reportPolymorphismExclude = findAnnotationMirror(nodeType, types.ReportPolymorphism_Exclude);
        if (reportPolymorphismExclude != null) {
            clonedType.getAnnotationMirrors().add(reportPolymorphismExclude);
        }
        AnnotationMirror reportPolymorphismMegamorphic = findAnnotationMirror(nodeType, types.ReportPolymorphism_Megamorphic);
        if (reportPolymorphismMegamorphic != null) {
            clonedType.getAnnotationMirrors().add(reportPolymorphismMegamorphic);
        }
    }

    private static boolean isNodeElement(Element member) {
        return member.getKind().isClass();
    }

    private static boolean isMethodElement(Element member) {
        return member.getKind() == ElementKind.METHOD;
    }

    private static String inferNodeMessageName(TypeElement type) {
        String name = type.getSimpleName().toString();
        return firstLetterLowerCase(name);
    }

    private static boolean verifyMethodSignature(TypeElement type, LibraryMessage message, ExportMessageData exportedMessage, ExecutableElement exportedMethod, TypeMirror receiverType,
                    int realParameterCount,
                    boolean emitErrors) {
        ExecutableElement libraryMethod = message.getExecutable();

        if (exportedMessage.getExportsLibrary().isExplicitReceiver() && !exportedMethod.getModifiers().contains(Modifier.STATIC)) {
            if (emitErrors) {
                exportedMessage.addError("Exported methods with explicit receiver must be static.");
            }
            return false;
        }

        boolean explicitReceiver = exportedMethod.getModifiers().contains(Modifier.STATIC);
        int paramOffset = !explicitReceiver ? 1 : 0;
        List<? extends VariableElement> expectedParameters = libraryMethod.getParameters().subList(paramOffset, libraryMethod.getParameters().size());
        List<? extends VariableElement> exportedParameters = exportedMethod.getParameters().subList(0, realParameterCount);

        TypeMirror expectedStaticReceiverType = explicitReceiver || exportedMessage.getExportsLibrary().isExplicitReceiver() ? receiverType : null;
        if (exportedParameters.size() != expectedParameters.size()) {
            if (emitErrors) {
                exportedMessage.addError(exportedMethod,
                                "Expected parameter count %s for exported message, but was %s. Expected signature:%n    %s",
                                expectedParameters.size(),
                                exportedParameters.size(),
                                generateExpectedSignature(type, message, expectedStaticReceiverType));
            }
            return false;
        }

        if (!isAssignable(exportedMethod.getReturnType(), libraryMethod.getReturnType())) {
            if (emitErrors) {
                exportedMessage.addError(exportedMethod, "Invalid exported return type. Expected '%s' but was '%s'. Expected signature:%n    %s",
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
            TypeMirror libraryArgType = (explicitReceiver && i == 0) ? receiverType : libraryArg.asType();
            if (!typeEquals(exportedArgType, libraryArgType)) {
                if (emitErrors) {
                    exportedMessage.addError(exportedArg, "Invalid parameter type. Expected '%s' but was '%s'. Expected signature:%n    %s",
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
        b.append("@").append(ProcessorContext.getInstance().getTypes().ExportMessage.asElement().getSimpleName().toString()).append(" ");
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
    public static List<String> fuzzyMatch(Collection<String> descriptors, String optionKey, float minScore) {
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
    public DeclaredType getAnnotationType() {
        return types.ExportLibrary;
    }

    @Override
    public DeclaredType getRepeatAnnotationType() {
        return types.ExportLibrary_Repeat;
    }

    @Override
    public List<DeclaredType> getTypeDelegatedAnnotationTypes() {
        return Arrays.asList(types.ExportMessage, types.ExportMessage_Repeat);
    }

}
