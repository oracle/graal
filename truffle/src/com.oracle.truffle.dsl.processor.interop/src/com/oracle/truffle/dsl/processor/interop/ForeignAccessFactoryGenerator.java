/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.interop;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public final class ForeignAccessFactoryGenerator {

    private final String receiverTypeClass;
    private final String packageName;
    private final String simpleClassName;
    private final ProcessingEnvironment processingEnv;
    protected final TypeElement element;

    private final Map<Object, String> messageHandlers;

    private String languageCheckFactoryInvokation;

    public ForeignAccessFactoryGenerator(ProcessingEnvironment processingEnv, MessageResolution messageResolutionAnnotation, TypeElement element) {
        this.processingEnv = processingEnv;
        this.element = element;
        this.packageName = ElementUtils.getPackageName(element);
        this.simpleClassName = ElementUtils.getSimpleName(element) + "Foreign";
        this.receiverTypeClass = Utils.getReceiverTypeFullClassName(messageResolutionAnnotation);
        this.messageHandlers = new HashMap<>();
        this.languageCheckFactoryInvokation = null;
    }

    public void addMessageHandler(Object message, String factoryMethodInvocation) {
        messageHandlers.put(message, factoryMethodInvocation);
    }

    public void addLanguageCheckHandler(String invocation) {
        this.languageCheckFactoryInvokation = invocation;
    }

    public String getFullClassName() {
        return packageName + "." + simpleClassName;
    }

    public void generate() throws IOException {
        JavaFileObject factoryFile = processingEnv.getFiler().createSourceFile(packageName + "." + simpleClassName, element);
        Writer w = factoryFile.openWriter();
        w.append("package ").append(packageName).append(";\n");
        appendImports(w);
        Utils.appendFactoryGeneratedFor(w, "", receiverTypeClass, ElementUtils.getQualifiedName(element));
        Utils.appendVisibilityModifier(w, element);
        w.append("final class ").append(simpleClassName);
        w.append(" implements Factory26, Factory {\n");

        appendSingletonAndGetter(w);
        appendPrivateConstructor(w);
        appendFactoryCanHandle(w);

        appendFactoryAccessIsNull(w);
        appendFactoryAccessIsExecutable(w);
        appendFactoryAccessIsBoxed(w);
        appendFactoryAccessHasSize(w);
        appendFactoryAccessGetSize(w);
        appendFactoryAccessUnbox(w);
        appendFactoryAccessRead(w);
        appendFactoryAccessWrite(w);
        appendFactoryAccessExecute(w);
        appendFactoryAccessInvoke(w);
        appendFactoryAccessNew(w);
        appendFactoryAccessKeyInfo(w);
        appendFactoryAccessKeys(w);
        appendFactoryAccessIsPointer(w);
        appendFactoryAccessAsPointer(w);
        appendFactoryAccessToNative(w);
        appendFactoryAccessMessage(w);

        w.append("}\n");
        w.close();
    }

    private boolean hasLanguageCheckNode() {
        return languageCheckFactoryInvokation != null;
    }

    private void appendImports(Writer w) throws IOException {
        w.append("import com.oracle.truffle.api.interop.ForeignAccess.Factory26;").append("\n");
        w.append("import com.oracle.truffle.api.interop.ForeignAccess.Factory;").append("\n");
        w.append("import com.oracle.truffle.api.interop.Message;").append("\n");
        w.append("import com.oracle.truffle.api.interop.ForeignAccess;").append("\n");
        w.append("import com.oracle.truffle.api.interop.TruffleObject;").append("\n");
        w.append("import com.oracle.truffle.api.CallTarget;").append("\n");
        if (hasLanguageCheckNode()) {
            w.append("import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;").append("\n");
        }
        w.append("import com.oracle.truffle.api.Truffle;").append("\n");
        if (!(messageHandlers.containsKey(Message.IS_BOXED) &&
                        messageHandlers.containsKey(Message.IS_NULL) &&
                        messageHandlers.containsKey(Message.IS_EXECUTABLE) &&
                        messageHandlers.containsKey(Message.KEY_INFO) &&
                        messageHandlers.containsKey(Message.HAS_SIZE) &&
                        messageHandlers.containsKey(Message.IS_POINTER))) {
            w.append("import com.oracle.truffle.api.nodes.RootNode;").append("\n");
        }
    }

    private void appendSingletonAndGetter(Writer w) throws IOException {
        String allocation;
        if (hasLanguageCheckNode()) {
            allocation = "ForeignAccess.create(new " + simpleClassName + "(), " + languageCheckFactoryInvokation + ");";
        } else {
            allocation = "ForeignAccess.create(new " + simpleClassName + "(), null);";
        }
        w.append("  public static final ForeignAccess ACCESS = ").append(allocation).append("\n");
        w.append("  @Deprecated");
        w.append("  public static ForeignAccess createAccess() { return ").append(allocation).append(" }\n");
        w.append("\n");
    }

    private void appendPrivateConstructor(Writer w) throws IOException {
        w.append("  private ").append(simpleClassName).append("() { }").append("\n");
        w.append("\n");
    }

    private void appendFactoryCanHandle(Writer w) throws IOException {
        w.append("  @Override").append("\n");
        if (hasLanguageCheckNode()) {
            w.append("  @TruffleBoundary").append("\n");
        }
        w.append("  public boolean canHandle(TruffleObject obj) {").append("\n");
        if (hasLanguageCheckNode()) {
            w.append("    return (boolean) Truffle.getRuntime().createCallTarget(").append(languageCheckFactoryInvokation).append(").call(obj);\n");
        } else {
            w.append("    return ").append(receiverTypeClass).append(".isInstance(obj);").append("\n");
        }
        w.append("  }").append("\n");
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
        appendOptionalDefaultHandlerBody(w, Message.IS_EXECUTABLE);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessIsBoxed(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessIsBoxed() {").append("\n");
        appendOptionalDefaultHandlerBody(w, Message.IS_BOXED);
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessHasSize(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessHasSize() {").append("\n");
        appendOptionalDefaultHandlerBody(w, Message.HAS_SIZE);
        w.append("    }").append("\n");
    }

    private void appendOptionalDefaultHandlerBody(Writer w, Message message) throws IOException {
        appendOptionalDefaultHandlerBody(w, message, "false");
    }

    private void appendOptionalDefaultHandlerBody(Writer w, Message message, String defaultValue) throws IOException {
        if (!messageHandlers.containsKey(message)) {
            w.append("      return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(" + defaultValue + "));").append("\n");
        } else {
            w.append("      return Truffle.getRuntime().createCallTarget(").append(messageHandlers.get(message)).append(");").append("\n");
        }
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
        appendOptionalDefaultHandlerBody(w, Message.IS_POINTER);
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

    private void appendFactoryAccessExecute(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessExecute(int argumentsLength) {").append("\n");
        appendOptionalHandlerBody(w, Message.createExecute(0));
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessInvoke(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessInvoke(int argumentsLength) {").append("\n");
        appendOptionalHandlerBody(w, Message.createInvoke(0));
        w.append("    }").append("\n");
    }

    private void appendFactoryAccessNew(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessNew(int argumentsLength) {").append("\n");
        appendOptionalHandlerBody(w, Message.createNew(0));
        w.append("    }").append("\n");
    }

    private void appendOptionalHandlerBody(Writer w, Message message) throws IOException {
        if (!messageHandlers.containsKey(message)) {
            w.append("      return null;\n");
        } else {
            w.append("      return com.oracle.truffle.api.Truffle.getRuntime().createCallTarget(").append(messageHandlers.get(message)).append(");").append("\n");
        }
    }

    private void appendFactoryAccessMessage(Writer w) throws IOException {
        w.append("    @Override").append("\n");
        w.append("    public CallTarget accessMessage(Message unknown) {").append("\n");
        for (Object m : messageHandlers.keySet()) {
            if (!InteropDSLProcessor.KNOWN_MESSAGES.contains(m)) {
                String msg = m instanceof Message ? Message.toString((Message) m) : (String) m;
                w.append("      if (unknown != null && unknown.getClass().getName().equals(\"").append(msg).append("\")) {").append("\n");
                w.append("        return Truffle.getRuntime().createCallTarget(").append(messageHandlers.get(m)).append(");").append("\n");
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
