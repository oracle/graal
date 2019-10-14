/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

final class ForeignAccessFactoryGenerator {

    private final String receiverTypeClass;
    private final String packageName;
    private final String simpleClassName;
    private final ProcessingEnvironment processingEnv;
    protected final TypeElement element;

    private final SortedSet<String> imports;
    private final Map<String, MessageGenerator> messageGenerators;
    private LanguageCheckGenerator languageCheckGenerator;

    ForeignAccessFactoryGenerator(ProcessingEnvironment processingEnv, AnnotationMirror messageResolutionAnnotation, TypeElement element) {
        this.processingEnv = processingEnv;
        this.element = element;
        this.packageName = ElementUtils.getPackageName(element);
        this.simpleClassName = ElementUtils.getSimpleName(element) + "Foreign";
        this.receiverTypeClass = Utils.getReceiverTypeFullClassName(messageResolutionAnnotation);
        this.imports = new TreeSet<>();
        this.messageGenerators = new HashMap<>();
        this.languageCheckGenerator = null;
    }

    public void addMessageHandler(String message, MessageGenerator messageGenerator) {
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
        Utils.suppressDeprecationWarnings(w, "");
        Utils.appendVisibilityModifier(w, element);
        w.append("final class ").append(simpleClassName);
        w.append(" implements com.oracle.truffle.api.interop.ForeignAccess.StandardFactory, com.oracle.truffle.api.interop.ForeignAccess.Factory {\n");

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
            imports.add("java.util.function.Supplier");
        }
        imports.add("com.oracle.truffle.api.Truffle");
        imports.add("com.oracle.truffle.api.interop.TruffleObject");
        if (!(messageGenerators.containsKey("IS_BOXED") &&
                        messageGenerators.containsKey("IS_NULL") &&
                        messageGenerators.containsKey("IS_EXECUTABLE") &&
                        messageGenerators.containsKey("IS_INSTANTIABLE") &&
                        messageGenerators.containsKey("KEY_INFO") &&
                        messageGenerators.containsKey("HAS_KEYS") &&
                        messageGenerators.containsKey("HAS_SIZE") &&
                        messageGenerators.containsKey("IS_POINTER"))) {
            imports.add("com.oracle.truffle.api.nodes.RootNode");
        }
        imports.add("com.oracle.truffle.api.dsl.GeneratedBy");

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
        String allocation = "com.oracle.truffle.api.interop.ForeignAccess.createAccess(new " + simpleClassName + "(), ";
        if (hasLanguageCheckNode()) {
            allocation += "new Supplier<RootNode>() { @Override public RootNode get() { return " + languageCheckGenerator.getRootNodeFactoryInvocation() + "; }});";
        } else {
            allocation += "null);";
        }
        w.append("    public static final com.oracle.truffle.api.interop.ForeignAccess ACCESS = ").append(allocation).append("\n");
        w.append("    @Deprecated public static com.oracle.truffle.api.interop.ForeignAccess createAccess() { return ").append(allocation).append(" }\n");
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
        appendOptionalDefaultHandlerBody(w, "IS_NULL");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessIsExecutable(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessIsExecutable() {").append("\n");
        appendOptionalDefaultHandlerBody(w, "IS_EXECUTABLE", "EXECUTE");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessIsInstantiable(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessIsInstantiable() {").append("\n");
        appendOptionalDefaultHandlerBody(w, "IS_INSTANTIABLE", "NEW");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessIsBoxed(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessIsBoxed() {").append("\n");
        appendOptionalDefaultHandlerBody(w, "IS_BOXED", "UNBOX");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessHasKeys(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessHasKeys() {").append("\n");
        appendOptionalDefaultHandlerBody(w, "HAS_KEYS", "KEYS");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessHasSize(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessHasSize() {").append("\n");
        appendOptionalDefaultHandlerBody(w, "HAS_SIZE", "GET_SIZE");
        w.append("    }").append("\n");
    }

    private void appendOptionalDefaultHandlerBody(Writer w, String message) throws IOException {
        appendOptionalDefaultHandlerBodyDefault(w, message, "false");
    }

    private void appendOptionalDefaultHandlerBodyDefault(Writer w, String message, String defaultValue) throws IOException {
        if (!messageGenerators.containsKey(message)) {
            w.append("      return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(" + defaultValue + "));").append("\n");
        } else {
            w.append("      return Truffle.getRuntime().createCallTarget(").append(messageGenerators.get(message).getRootNodeFactoryInvocation()).append(");").append("\n");
        }
    }

    private void appendOptionalDefaultHandlerBody(Writer w, String message, String testPresentMessage) throws IOException {
        appendOptionalDefaultHandlerBodyDefault(w, message, Boolean.toString(messageGenerators.containsKey(testPresentMessage)));
    }

    private void appendFactoryAccessGetSize(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessGetSize() {").append("\n");
        appendOptionalHandlerBody(w, "GET_SIZE");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessKeyInfo(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessKeyInfo() {").append("\n");
        appendOptionalHandlerBody(w, "KEY_INFO");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessKeys(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessKeys() {").append("\n");
        appendOptionalHandlerBody(w, "KEYS");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessIsPointer(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessIsPointer() {").append("\n");
        appendOptionalDefaultHandlerBody(w, "IS_POINTER", "AS_POINTER");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessAsPointer(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessAsPointer() {").append("\n");
        appendOptionalHandlerBody(w, "AS_POINTER");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessToNative(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessToNative() {").append("\n");
        appendOptionalHandlerBody(w, "TO_NATIVE");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessUnbox(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessUnbox() {").append("\n");
        appendOptionalHandlerBody(w, "UNBOX");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessRead(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessRead() {").append("\n");
        appendOptionalHandlerBody(w, "READ");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessWrite(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessWrite() {").append("\n");
        appendOptionalHandlerBody(w, "WRITE");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessRemove(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessRemove() {").append("\n");
        appendOptionalHandlerBody(w, "REMOVE");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessExecute(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessExecute(int argumentsLength) {").append("\n");
        appendOptionalHandlerBody(w, "EXECUTE");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessInvoke(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessInvoke(int argumentsLength) {").append("\n");
        appendOptionalHandlerBody(w, "INVOKE");
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessNew(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessNew(int argumentsLength) {").append("\n");
        appendOptionalHandlerBody(w, "NEW");
        w.append("    }").append("\n");
    }

    private void appendOptionalHandlerBody(Writer w, String message) throws IOException {
        if (!messageGenerators.containsKey(message)) {
            w.append("      return null;\n");
        } else {
            w.append("      return com.oracle.truffle.api.Truffle.getRuntime().createCallTarget(").append(messageGenerators.get(message).getRootNodeFactoryInvocation()).append(");").append("\n");
        }
    }

    private void appendFactoryAccessMessage(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessMessage(com.oracle.truffle.api.interop.Message unknown) {").append("\n");
        for (String msg : messageGenerators.keySet()) {
            if (!InteropDSLProcessor.KNOWN_MESSAGES.contains(msg)) {
                w.append("      if (unknown != null && unknown.getClass().getCanonicalName().equals(\"").append(msg).append("\")) {").append("\n");
                w.append("        return Truffle.getRuntime().createCallTarget(").append(messageGenerators.get(msg).getRootNodeFactoryInvocation()).append(");").append("\n");
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
