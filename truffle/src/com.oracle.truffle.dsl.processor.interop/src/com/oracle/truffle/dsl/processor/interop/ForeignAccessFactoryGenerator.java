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
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

final class ForeignAccessFactoryGenerator {

    private final String receiverTypeClass;
    private final String packageName;
    private final String simpleClassName;
    private final ProcessingEnvironment processingEnv;
    protected final TypeElement element;

    private final SortedSet<String> imports;
    private final Map<Object, MessageGenerator> messageGenerators;
    private LanguageCheckGenerator languageCheckGenerator;

    ForeignAccessFactoryGenerator(ProcessingEnvironment processingEnv, MessageResolution messageResolutionAnnotation, TypeElement element) {
        this.processingEnv = processingEnv;
        this.element = element;
        this.packageName = ElementUtils.getPackageName(element);
        this.simpleClassName = ElementUtils.getSimpleName(element) + "Foreign";
        this.receiverTypeClass = Utils.getReceiverTypeFullClassName(messageResolutionAnnotation);
        this.imports = new TreeSet<>();
        this.messageGenerators = new HashMap<>();
        this.languageCheckGenerator = null;
    }

    public void addMessageHandler(Object message, MessageGenerator messageGenerator) {
        messageGenerators.put(message, messageGenerator);
    }

    public void addLanguageCheckHandler(LanguageCheckGenerator generator) {
        this.languageCheckGenerator = generator;
    }

    public String getSimpleClassName() {
        return simpleClassName;
    }

    public String getFullClassName() {
        return packageName + "." + simpleClassName;
    }

    public void generate() throws IOException {
        JavaFileObject factoryFile = processingEnv.getFiler().createSourceFile(packageName + "." + simpleClassName, element);
        Writer w = factoryFile.openWriter();
        w.append("package ").append(packageName).append(";\n\n");
        appendImports(w);
        Utils.appendFactoryGeneratedFor(w, "", receiverTypeClass, ElementUtils.getQualifiedName(element));
        if (ElementUtils.isDeprecated(element)) {
            Utils.suppressDeprecationWarnings(w, "");
        }
        Utils.appendVisibilityModifier(w, element);
        w.append("final class ").append(simpleClassName);
        w.append(" implements StandardFactory, Factory {\n");

        appendSingletonAndGetter(w);
        appendPrivateConstructor(w);
        appendFactoryCanHandle(w);

        appendFactoryAccessIsNull(w);
        appendFactoryAccessIsExecutable(w);
        appendFactoryAccessIsInstantiable(w);
        appendFactoryAccessIsBoxed(w);
        appendFactoryAccessHasKeys(w);
        appendFactoryAccessHasSize(w);
        appendFactoryAccessGetSize(w);
        appendFactoryAccessUnbox(w);
        appendFactoryAccessRead(w);
        appendFactoryAccessWrite(w);
        appendFactoryAccessRemove(w);
        appendFactoryAccessExecute(w);
        appendFactoryAccessInvoke(w);
        appendFactoryAccessNew(w);
        appendFactoryAccessKeyInfo(w);
        appendFactoryAccessKeys(w);
        appendFactoryAccessIsPointer(w);
        appendFactoryAccessAsPointer(w);
        appendFactoryAccessToNative(w);
        appendFactoryAccessMessage(w);

        for (MessageGenerator generator : messageGenerators.values()) {
            generator.appendNode(w);
        }
        if (languageCheckGenerator != null) {
            languageCheckGenerator.appendNode(w);
        }

        w.append("}\n");
        w.close();
    }

    private boolean hasLanguageCheckNode() {
        return languageCheckGenerator != null;
    }

    private void collectImports() {
        imports.add("com.oracle.truffle.api.CallTarget");
        if (hasLanguageCheckNode()) {
            imports.add("com.oracle.truffle.api.CompilerDirectives.TruffleBoundary");
        }
        imports.add("com.oracle.truffle.api.Truffle");
        imports.add("com.oracle.truffle.api.interop.ForeignAccess");
        imports.add("com.oracle.truffle.api.interop.ForeignAccess.Factory");
        imports.add("com.oracle.truffle.api.interop.ForeignAccess.StandardFactory");
        imports.add("com.oracle.truffle.api.interop.Message");
        imports.add("com.oracle.truffle.api.interop.TruffleObject");
        if (!(messageGenerators.containsKey(Message.IS_BOXED) &&
                        messageGenerators.containsKey(Message.IS_NULL) &&
                        messageGenerators.containsKey(Message.IS_EXECUTABLE) &&
                        messageGenerators.containsKey(Message.IS_INSTANTIABLE) &&
                        messageGenerators.containsKey(Message.KEY_INFO) &&
                        messageGenerators.containsKey(Message.HAS_KEYS) &&
                        messageGenerators.containsKey(Message.HAS_SIZE) &&
                        messageGenerators.containsKey(Message.IS_POINTER))) {
            imports.add("com.oracle.truffle.api.nodes.RootNode");
        }

        for (MessageGenerator generator : messageGenerators.values()) {
            generator.addImports(imports);
        }
        if (languageCheckGenerator != null) {
            languageCheckGenerator.addImports(imports);
        }
    }

    private void appendImports(Writer w) throws IOException {
        collectImports();

        for (String importedClassName : imports) {
            w.append("import ").append(importedClassName).append(";\n");
        }
    }

    private void appendSingletonAndGetter(Writer w) throws IOException {
        String allocation;
        if (hasLanguageCheckNode()) {
            allocation = "ForeignAccess.create(new " + simpleClassName + "(), " + languageCheckGenerator.getRootNodeFactoryInvocation() + ");";
        } else {
            allocation = "ForeignAccess.create(new " + simpleClassName + "(), null);";
        }
        w.append("    public static final ForeignAccess ACCESS = ").append(allocation).append("\n");
        w.append("    @Deprecated public static ForeignAccess createAccess() { return ").append(allocation).append(" }\n");
        w.append("\n");
    }

    private void appendPrivateConstructor(Writer w) throws IOException {
        w.append("    private ").append(simpleClassName).append("() { }").append("\n");
        w.append("\n");
    }

    private void appendFactoryCanHandle(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        if (hasLanguageCheckNode()) {
            w.append("    @TruffleBoundary").append("\n");
        }
        w.append("    public boolean canHandle(TruffleObject obj) {").append("\n");
        if (hasLanguageCheckNode()) {
            w.append("        return (boolean) Truffle.getRuntime().createCallTarget(").append(languageCheckGenerator.getRootNodeFactoryInvocation()).append(").call(obj);\n");
        } else {
            w.append("        return ").append(receiverTypeClass).append(".isInstance(obj);").append("\n");
        }
        w.append("    }").append("\n");
        w.append("\n");
    }

    private void appendFactoryAccessIsNull(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessIsNull() {").append("\n");
        appendOptionalDefaultHandlerBody(w, Message.IS_NULL);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessIsExecutable(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessIsExecutable() {").append("\n");
        appendOptionalDefaultHandlerBody(w, Message.IS_EXECUTABLE, Message.EXECUTE);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessIsInstantiable(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessIsInstantiable() {").append("\n");
        appendOptionalDefaultHandlerBody(w, Message.IS_INSTANTIABLE, Message.NEW);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessIsBoxed(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessIsBoxed() {").append("\n");
        appendOptionalDefaultHandlerBody(w, Message.IS_BOXED, Message.UNBOX);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessHasKeys(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessHasKeys() {").append("\n");
        appendOptionalDefaultHandlerBody(w, Message.HAS_KEYS, Message.KEYS);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessHasSize(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessHasSize() {").append("\n");
        appendOptionalDefaultHandlerBody(w, Message.HAS_SIZE, Message.GET_SIZE);
        w.append("    }").append("\n");
    }

    private void appendOptionalDefaultHandlerBody(Writer w, Message message) throws IOException {
        appendOptionalDefaultHandlerBody(w, message, "false");
    }

    private void appendOptionalDefaultHandlerBody(Writer w, Message message, String defaultValue) throws IOException {
        if (!messageGenerators.containsKey(message)) {
            w.append("      return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(" + defaultValue + "));").append("\n");
        } else {
            w.append("      return Truffle.getRuntime().createCallTarget(").append(messageGenerators.get(message).getRootNodeFactoryInvocation()).append(");").append("\n");
        }
    }

    private void appendOptionalDefaultHandlerBody(Writer w, Message message, Message testPresentMessage) throws IOException {
        appendOptionalDefaultHandlerBody(w, message, Boolean.toString(messageGenerators.containsKey(testPresentMessage)));
    }

    private void appendFactoryAccessGetSize(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessGetSize() {").append("\n");
        appendOptionalHandlerBody(w, Message.GET_SIZE);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessKeyInfo(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessKeyInfo() {").append("\n");
        appendOptionalHandlerBody(w, Message.KEY_INFO);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessKeys(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessKeys() {").append("\n");
        appendOptionalHandlerBody(w, Message.KEYS);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessIsPointer(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessIsPointer() {").append("\n");
        appendOptionalDefaultHandlerBody(w, Message.IS_POINTER, Message.AS_POINTER);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessAsPointer(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessAsPointer() {").append("\n");
        appendOptionalHandlerBody(w, Message.AS_POINTER);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessToNative(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessToNative() {").append("\n");
        appendOptionalHandlerBody(w, Message.TO_NATIVE);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessUnbox(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessUnbox() {").append("\n");
        appendOptionalHandlerBody(w, Message.UNBOX);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessRead(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessRead() {").append("\n");
        appendOptionalHandlerBody(w, Message.READ);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessWrite(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessWrite() {").append("\n");
        appendOptionalHandlerBody(w, Message.WRITE);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessRemove(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessRemove() {").append("\n");
        appendOptionalHandlerBody(w, Message.REMOVE);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessExecute(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessExecute(int argumentsLength) {").append("\n");
        appendOptionalHandlerBody(w, Message.EXECUTE);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessInvoke(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessInvoke(int argumentsLength) {").append("\n");
        appendOptionalHandlerBody(w, Message.INVOKE);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessNew(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessNew(int argumentsLength) {").append("\n");
        appendOptionalHandlerBody(w, Message.NEW);
        w.append("    }").append("\n");
    }

    private void appendOptionalHandlerBody(Writer w, Message message) throws IOException {
        if (!messageGenerators.containsKey(message)) {
            w.append("      return null;\n");
        } else {
            w.append("      return com.oracle.truffle.api.Truffle.getRuntime().createCallTarget(").append(messageGenerators.get(message).getRootNodeFactoryInvocation()).append(");").append("\n");
        }
    }

    private void appendFactoryAccessMessage(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessMessage(Message unknown) {").append("\n");
        for (Object m : messageGenerators.keySet()) {
            if (!InteropDSLProcessor.KNOWN_MESSAGES.contains(m)) {
                String msg = m instanceof Message ? Message.toString((Message) m) : (String) m;
                w.append("      if (unknown != null && unknown.getClass().getCanonicalName().equals(\"").append(msg).append("\")) {").append("\n");
                w.append("        return Truffle.getRuntime().createCallTarget(").append(messageGenerators.get(m).getRootNodeFactoryInvocation()).append(");").append("\n");
                w.append("      }").append("\n");
            }
        }
        w.append("      return null;\n");
        w.append("    }").append("\n");
    }

    @Override
    public String toString() {
        return "FactoryGenerator: " + simpleClassName;
    }

}
