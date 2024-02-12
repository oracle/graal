/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.processor;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;

import com.oracle.truffle.espresso.processor.builders.AnnotationBuilder;
import com.oracle.truffle.espresso.processor.builders.ClassBuilder;
import com.oracle.truffle.espresso.processor.builders.ClassFileBuilder;
import com.oracle.truffle.espresso.processor.builders.FieldBuilder;
import com.oracle.truffle.espresso.processor.builders.JavadocBuilder;
import com.oracle.truffle.espresso.processor.builders.MethodBuilder;
import com.oracle.truffle.espresso.processor.builders.ModifierBuilder;
import com.oracle.truffle.espresso.processor.builders.SignatureBuilder;
import com.oracle.truffle.espresso.processor.builders.StatementBuilder;
import com.oracle.truffle.espresso.processor.builders.VariableBuilder;

@SupportedAnnotationTypes("com.oracle.truffle.espresso.runtime.dispatch.messages.GenerateInteropNodes")
public class InteropNodesProcessor extends BaseProcessor {

    private static final String GENERATE_INTEROP_NODES = "com.oracle.truffle.espresso.runtime.dispatch.messages.GenerateInteropNodes";
    private static final String EXPORT_MESSAGE = "com.oracle.truffle.api.library.ExportMessage";

    private static final String EXPORT_REPEAT_MESSAGE = "com.oracle.truffle.api.library.ExportMessage.Repeat";

    private static final String COLLECT = "com.oracle.truffle.espresso.substitutions.Collect";
    private static final String SPECIALIZATION = "com.oracle.truffle.api.dsl.Specialization";
    private static final String CACHED = "com.oracle.truffle.api.dsl.Cached";
    private static final String CACHED_LIBRARY = "com.oracle.truffle.api.library.CachedLibrary";
    private static final String BIND = "com.oracle.truffle.api.dsl.Bind";
    private static final String SHAREABLE = "com.oracle.truffle.espresso.runtime.dispatch.messages.Shareable";

    private static final String INTEROP_NODES = "com.oracle.truffle.espresso.runtime.dispatch.messages.InteropNodes";
    private static final String INTEROP_MESSAGE_FACTORIES = "com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactories";
    private static final String INTEROP_MESSAGE_FACTORY = "com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactory";
    private static final String INTEROP_MESSAGE = "com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessage";
    private static final String INTEROP_MESSAGE_MESSAGE = "com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessage.Message";

    private static final String INSTANCE = "INSTANCE";
    private static final String INSTANCE_GETTER = "getInstance";
    private static final String ADDED_CLASS_SUFFIX = "InteropNodes";
    private static final String FACTORY_CLASS_NAME = "Factory";
    private static final String FACTORY_FIELD_NAME = "factory";

    // @GenerateInteropNodes
    private TypeElement generateInteropNodes;
    // @ExportMessage
    private TypeElement exportMessage;

    private TypeElement exportRepeatMessage;
    // @Shareable
    private TypeElement shareable;
    // @Cached
    private TypeElement cached;
    // @CachedLibrary
    private TypeElement cachedLibrary;
    // @Bind
    private TypeElement bind;

    // InteropNodes
    private TypeElement interopNodes;
    // j.l.Object
    private TypeMirror objectType;

    private final Set<TypeElement> processedClasses = new HashSet<>();

    private void collectAndCheckRequiredAnnotations() {
        generateInteropNodes = getTypeElement(GENERATE_INTEROP_NODES);
        exportMessage = getTypeElement(EXPORT_MESSAGE);
        exportRepeatMessage = getTypeElement(EXPORT_REPEAT_MESSAGE);
        shareable = getTypeElement(SHAREABLE);
        cached = getTypeElement(CACHED);
        cachedLibrary = getTypeElement(CACHED_LIBRARY);
        bind = getTypeElement(BIND);

        interopNodes = getTypeElement(INTEROP_NODES);
        objectType = getType("java.lang.Object");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        collectAndCheckRequiredAnnotations();

        Set<? extends Element> elementsToProcess = roundEnv.getElementsAnnotatedWith(generateInteropNodes);
        if (elementsToProcess.isEmpty()) {
            return true;
        }
        for (Element element : elementsToProcess) {
            assert element.getKind().isClass() || element.getKind().isInterface();
            processElement((TypeElement) element);
        }
        return false;
    }

    private boolean isShareable(Element e, boolean topLevelShareable) {
        AnnotationMirror shareableAnnotation = getAnnotation(e, shareable.asType());
        if (shareableAnnotation != null) {
            return getAnnotationValue(shareableAnnotation, "value", Boolean.class);
        }
        return topLevelShareable;
    }

    private void processElement(TypeElement cls) {
        if (processedClasses.contains(cls)) {
            return;
        }

        processedClasses.add(cls);
        Imports imports = new Imports();
        boolean shareableCls = isShareable(cls, false);
        List<Message> nodes = new ArrayList<>();
        for (Element methodElement : cls.getEnclosedElements()) {
            List<AnnotationMirror> exportedMethods = getAnnotations(methodElement, exportMessage.asType(), exportRepeatMessage.asType());
            // Look for exported messages. Create one node per export.
            for (AnnotationMirror exportAnnotation : exportedMethods) {
                String exportedMessageName = getAnnotationValue(exportAnnotation, "name", String.class);
                if (exportedMessageName == null || exportedMessageName.isEmpty()) {
                    exportedMessageName = methodElement.getSimpleName().toString();
                }
                String capitalizedMessageName = ProcessorUtils.capitalize(exportedMessageName);
                String clsName = capitalizedMessageName + "Node";
                boolean isShareable = isShareable(methodElement, shareableCls);
                nodes.add(new Message(processInteropNode(cls, (ExecutableElement) methodElement, exportedMessageName, clsName, imports),
                                capitalizedMessageName, clsName, isShareable));
            }
        }

        String clsName = cls.getSimpleName().toString() + ADDED_CLASS_SUFFIX;
        String superNodes = (env().getTypeUtils().isSameType(cls.getSuperclass(), objectType) ? "null"
                        : env().getTypeUtils().asElement(cls.getSuperclass()).getSimpleName().toString() + ADDED_CLASS_SUFFIX + "." + INSTANCE_GETTER + "()");

        ClassBuilder nodesClass = new ClassBuilder(clsName) //
                        // @Collect(value = "InteropNodes.class", getter = "getInstance")
                        // public final class [InteropName]InteropNodes extends InteropNodes
                        .withQualifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withJavaDoc(new JavadocBuilder().addGeneratedByLine(cls)) //
                        .withAnnotation(new AnnotationBuilder(COLLECT).withValue("value", INTEROP_NODES + ".class", false).withValue("getter",
                                        INSTANCE_GETTER).withLineBreak()) //
                        .withSuperClass(interopNodes.getQualifiedName().toString()) //
                        // Factory declaration
                        .withField(new FieldBuilder(FACTORY_CLASS_NAME, FACTORY_FIELD_NAME).withQualifiers(new ModifierBuilder().asStatic().asFinal().asPrivate()).withDeclaration(
                                        new StatementBuilder().addContent("new ", FACTORY_CLASS_NAME, "(", INTEROP_MESSAGE_FACTORIES, ".dispatchToId(", cls.getSimpleName(), ".class)", ")")))
                        // Singleton implementation
                        .withField(new FieldBuilder(interopNodes, INSTANCE).withQualifiers(new ModifierBuilder().asStatic().asFinal().asPrivate()).withDeclaration(
                                        new StatementBuilder().addContent("new " + clsName + "()"))) //
                        .withMethod(new MethodBuilder(clsName).asConstructor().withModifiers(new ModifierBuilder().asPrivate()).addBodyLine("super(", cls.getSimpleName(), ".class, ", superNodes,
                                        ");")) //
                        .withMethod(new MethodBuilder(INSTANCE_GETTER).withReturnType(interopNodes.toString()).addBodyLine("return " + INSTANCE + ";").withModifiers(
                                        new ModifierBuilder().asStatic().asPublic()))
                        // Factory implementation
                        .withInnerClass(generateFactory(nodes, cls));

        // Implementation of InteropNodes.registerMessages
        MethodBuilder registerMessages = new MethodBuilder("registerMessages").withOverrideAnnotation().withParams("Class<?> cls").withModifiers(new ModifierBuilder().asProtected());

        // For all messages, add a line in registerMessages, and create the corresponding class
        for (Message m : nodes) {
            registerMessages.addBodyLine(INTEROP_MESSAGE_FACTORIES, ".register(cls, ", INTEROP_MESSAGE_MESSAGE, ".", m.targetMessage, ", ", FACTORY_FIELD_NAME, ",",
                            m.isShareable, ");");
            nodesClass.withInnerClass(m.cls);
        }
        nodesClass.withMethod(registerMessages);

        String pkg = env().getElementUtils().getPackageOf(cls).getQualifiedName().toString();
        ClassFileBuilder clsFile = new ClassFileBuilder() //
                        .withCopyright() //
                        .withClass(nodesClass) //
                        .inPackage(pkg); //
        try {
            FileObject file = processingEnv.getFiler().createSourceFile(pkg + "." + clsName);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"));
            writer.print(clsFile.build());
            writer.close();
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), cls);
        }

    }

    private static ClassBuilder generateFactory(List<Message> nodes, TypeElement sourceDispatch) {
        /*-
         static final class Factory implements InteropMessageFactory {
            final int sourceDispatch;
            
            Factory(int sourceDispatch) {
                this.sourceDispatch = sourceDispatch;
            }
            
            final int sourceDispatch() {
                return sourceDispatch;
            }
            
            final InteropMessage create(InteropMessage.Message message) {
                switch (message) {
                    ...
                    default: return null;
                }
            }
         }
         */
        FieldBuilder sourceDispatchField = new FieldBuilder("int", "sourceDispatch").withQualifiers(new ModifierBuilder().asPrivate().asFinal());
        MethodBuilder sourceDispatchMethod = new MethodBuilder("sourceDispatch") //
                        .withModifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withOverrideAnnotation() //
                        .withReturnType("int") //
                        .addBodyLine("return sourceDispatch;");

        MethodBuilder createMethod = new MethodBuilder("create") //
                        .withModifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withOverrideAnnotation() //
                        .withReturnType(INTEROP_MESSAGE) //
                        .withSignature(new SignatureBuilder().addParam(new VariableBuilder().withDeclaration(INTEROP_MESSAGE_MESSAGE).withName("message")));

        createMethod.addBodyLine("switch (message) {");
        String clsName = sourceDispatch.getSimpleName().toString() + ADDED_CLASS_SUFFIX;
        for (Message m : nodes) {
            String targetMessageEnum = m.targetMessage;
            String targetMessageImpl = clsName + "Factory." + m.clsName + "Gen";
            createMethod.addIndentedBodyLine(1, "case ", targetMessageEnum, ": return ", targetMessageImpl, ".create();");
        }
        createMethod.addIndentedBodyLine(1, "default: return null;");
        createMethod.addBodyLine("}");

        MethodBuilder init = new MethodBuilder(FACTORY_CLASS_NAME) //
                        .asConstructor() //
                        .withParams("int sourceDispatch") //
                        .addBodyLine("this.sourceDispatch = sourceDispatch;");

        ClassBuilder classBuilder = new ClassBuilder(FACTORY_CLASS_NAME) //
                        .withQualifiers(new ModifierBuilder().asFinal().asStatic()) //
                        .withSuperInterfaces(INTEROP_MESSAGE_FACTORY) //
                        .withField(sourceDispatchField) //
                        .withMethod(init) //
                        .withMethod(sourceDispatchMethod) //
                        .withMethod(createMethod);
        return classBuilder;
    }

    private ClassBuilder processInteropNode(TypeElement processingClass, ExecutableElement element, String exportedMessageName, String clsName, Imports imports) {
        String targetMethodName = element.getSimpleName().toString();
        /*- abstract static class [exportedMessageName]Node extends InteropMessage.[exportedMessageName] */
        ClassBuilder result = new ClassBuilder(clsName) //
                        .withQualifiers(new ModifierBuilder().asStatic().asAbstract()) //
                        .withSuperClass(INTEROP_MESSAGE + "." + ProcessorUtils.capitalize(exportedMessageName));

        /*- 
            @Specialization
            static [returnType] [exportedMessageName]([signature]) throws [thrownExceptions] {
                [return] [processingClass].[targetMethodName]([args]);
            }
         */
        MethodBuilder m = new MethodBuilder(exportedMessageName) //
                        .withAnnotation(new AnnotationBuilder(SPECIALIZATION).withLineBreak()) //
                        .withModifiers(new ModifierBuilder().asStatic()) //
                        .withReturnType(element.getReturnType().toString());

        // Signature used in method declaration, needs type information and to reuse some DSL
        // annotations.
        SignatureBuilder declaredSig = new SignatureBuilder();
        // Signature used in message invocation, only needs parameter names.
        SignatureBuilder invokeSig = new SignatureBuilder();

        // Copy parameters from the message itself, inheriting relevant dsl annotations.
        for (VariableElement ve : element.getParameters()) {
            VariableBuilder declParam = new VariableBuilder();
            VariableBuilder invokeParam = new VariableBuilder();

            AnnotationMirror cachedAnnot = getAnnotation(ve, cached.asType());
            if (cachedAnnot != null) {
                imports.usesCached();
                String value = getAnnotationValue(cachedAnnot, "value", String.class);
                // Reuse @Cached annotation
                declParam.withAnnotation(new AnnotationBuilder(CACHED).withValue("value", value));
            }
            AnnotationMirror cachedLibraryAnnot = getAnnotation(ve, cachedLibrary.asType());
            if (cachedLibraryAnnot != null) {
                imports.usesCachedLibrary();
                // Force dynamic libraries.
                declParam.withAnnotation(new AnnotationBuilder(CACHED_LIBRARY).withValue("limit", "1"));
            }
            AnnotationMirror bindAnnot = getAnnotation(ve, bind.asType());
            if (bindAnnot != null) {
                imports.usesBind();
                String value = getAnnotationValue(bindAnnot, "value", String.class);
                // Reuse @Bind annotation
                declParam.withAnnotation(new AnnotationBuilder(BIND).withValue("value", value));
            }
            // Do not reuse @Cached.Shared or @Cached.Exclusive

            declParam.withDeclaration(ve.asType().toString());

            declParam.withName(ve.getSimpleName().toString());
            invokeParam.withName(ve.getSimpleName().toString());

            declaredSig.addParam(declParam);
            invokeSig.addParam(invokeParam);
        }

        for (TypeMirror thrown : element.getThrownTypes()) {
            m.withThrown(thrown.toString());
        }
        m.withSignature(declaredSig);
        String returnOrEmpty = element.getReturnType().toString().equals("void") ? "" : "return ";
        m.addBodyLine(returnOrEmpty, processingClass.getQualifiedName(), ".", targetMethodName, invokeSig.toString(), ";");

        result.withMethod(m);

        return result;
    }

    static class Message {
        final ClassBuilder cls;
        final String targetMessage;
        final String clsName;
        final boolean isShareable;

        Message(ClassBuilder cls, String targetMessage, String clsName, boolean isShareable) {
            this.cls = cls;
            this.targetMessage = targetMessage;
            this.clsName = clsName;
            this.isShareable = isShareable;
        }
    }

    static class Imports {
        boolean cached;
        boolean cachedLibrary;
        boolean bind;

        void usesCached() {
            cached = true;
        }

        void usesCachedLibrary() {
            cachedLibrary = true;
        }

        void usesBind() {
            bind = true;
        }
    }
}
