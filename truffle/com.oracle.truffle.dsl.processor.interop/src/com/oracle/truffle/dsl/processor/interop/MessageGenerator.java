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
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;

import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

/**
 * THIS IS NOT PUBLIC API.
 */
public abstract class MessageGenerator {
    protected static final String ACCESS_METHOD_NAME = "access";

    protected final TypeElement element;
    protected final String packageName;
    protected final String clazzName;
    protected final String messageName;
    protected final String userClassName;
    protected final String receiverClassName;
    protected final ProcessingEnvironment processingEnv;
    protected final ForeignAccessFactoryGenerator containingForeignAccessFactory;

    MessageGenerator(ProcessingEnvironment processingEnv, Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        this.processingEnv = processingEnv;
        this.element = element;
        this.receiverClassName = Utils.getReceiverTypeFullClassName(messageResolutionAnnotation);
        this.packageName = ElementUtils.getPackageName(element);
        this.messageName = resolveAnnotation.message();
        this.userClassName = ElementUtils.getQualifiedName(element);
        this.clazzName = Utils.getSimpleResolveClassName(element);
        this.containingForeignAccessFactory = containingForeignAccessFactory;
    }

    public final void generate() throws IOException {
        JavaFileObject file = processingEnv.getFiler().createSourceFile(Utils.getFullResolveClassName(element), element);
        Writer w = file.openWriter();
        w.append("package ").append(packageName).append(";\n");
        appendImports(w);

        Utils.appendMessagesGeneratedByInformation(w, "", containingForeignAccessFactory.getFullClassName(), ElementUtils.getQualifiedName(element));
        Utils.appendVisibilityModifier(w, element);
        w.append("abstract class ").append(clazzName).append(" extends ").append(userClassName).append(" {\n");
        appendExecuteWithTarget(w);
        appendSpecializations(w);

        appendRootNode(w);
        appendRootNodeFactory(w);

        w.append("}\n");
        w.close();
    }

    public final List<ExecutableElement> getAccessMethods() {
        List<ExecutableElement> methods = new ArrayList<>();
        for (Element m : element.getEnclosedElements()) {
            if (m.getKind() != ElementKind.METHOD) {
                continue;
            }
            if (!m.getSimpleName().contentEquals(ACCESS_METHOD_NAME)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) m;
            methods.add(method);
        }
        return methods;
    }

    void appendImports(Writer w) throws IOException {
        w.append("import com.oracle.truffle.api.frame.VirtualFrame;").append("\n");
        w.append("import com.oracle.truffle.api.dsl.Specialization;").append("\n");
        w.append("import com.oracle.truffle.api.nodes.RootNode;").append("\n");
        w.append("import com.oracle.truffle.api.TruffleLanguage;").append("\n");
        w.append("import com.oracle.truffle.api.interop.ForeignAccess;").append("\n");
        w.append("import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;").append("\n");
        w.append("import com.oracle.truffle.api.interop.UnsupportedTypeException;").append("\n");
    }

    abstract int getParameterCount();

    public String checkSignature(ExecutableElement method) {
        if (method.getThrownTypes().size() > 0) {
            return "Method access must not throw a checked exception. Use an InteropException (e.g. UnknownIdentifierException.raise() ) to report an error to the host language.";
        }
        return null;
    }

    abstract String getTargetableNodeName();

    void appendExecuteWithTarget(Writer w) throws IOException {
        w.append("    public abstract Object executeWithTarget(VirtualFrame frame");
        for (int i = 0; i < getParameterCount(); i++) {
            w.append(", ").append("Object ").append("o").append(String.valueOf(i));
        }
        w.append(");\n");
    }

    void appendSpecializations(Writer w) throws IOException {
        String sep = "";
        for (ExecutableElement method : getAccessMethods()) {
            final List<? extends VariableElement> params = method.getParameters();

            w.append("    @Specialization\n");
            w.append("    protected Object ").append(ACCESS_METHOD_NAME).append("WithTarget");
            w.append("(");

            sep = "";
            for (VariableElement p : params) {
                w.append(sep).append(ElementUtils.getUniqueIdentifier(p.asType())).append(" ").append(p.getSimpleName());
                sep = ", ";
            }
            w.append(") {\n");
            w.append("        return ").append(ACCESS_METHOD_NAME).append("(");
            sep = "";
            for (VariableElement p : params) {
                w.append(sep).append(p.getSimpleName());
                sep = ", ";
            }
            w.append(");\n");
            w.append("    }\n");
        }
    }

    abstract void appendRootNode(Writer w) throws IOException;

    abstract String getRootNodeName();

    void appendRootNodeFactory(Writer w) throws IOException {
        w.append("    @Deprecated\n");
        w.append("    @SuppressWarnings(\"unused\")\n");
        w.append("    public static RootNode createRoot(Class<? extends TruffleLanguage<?>> language) {\n");
        w.append("        return createRoot();\n");
        w.append("    }\n");
        w.append("    public static RootNode createRoot() {\n");
        w.append("        return new ").append(getRootNodeName()).append("();\n");
        w.append("    }\n");

    }

    public String getRootNodeFactoryInvokation() {
        return packageName + "." + clazzName + ".createRoot()";
    }

    @Override
    public String toString() {
        return clazzName;
    }

    public static MessageGenerator getGenerator(ProcessingEnvironment processingEnv, Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        String messageName = resolveAnnotation.message();

        Object currentMessage = Utils.getMessage(processingEnv, messageName);
        if (currentMessage != null) {
            if (Message.READ.toString().equalsIgnoreCase(messageName)) {
                return new ReadGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
            } else if (Message.WRITE.toString().equalsIgnoreCase(messageName)) {
                return new WriteGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
            } else if (Message.IS_NULL.toString().equalsIgnoreCase(messageName) || Message.IS_EXECUTABLE.toString().equalsIgnoreCase(messageName) ||
                            Message.IS_BOXED.toString().equalsIgnoreCase(messageName) || Message.HAS_SIZE.toString().equalsIgnoreCase(messageName) ||
                            Message.GET_SIZE.toString().equalsIgnoreCase(messageName) || Message.UNBOX.toString().equalsIgnoreCase(messageName) ||
                            Message.KEYS.toString().equalsIgnoreCase(messageName)) {
                return new UnaryGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
            } else if (Message.createExecute(0).toString().equalsIgnoreCase(messageName) || Message.createInvoke(0).toString().equalsIgnoreCase(messageName) ||
                            Message.createNew(0).toString().equalsIgnoreCase(messageName)) {
                return new ExecuteGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
            } else {
                assert !InteropDSLProcessor.KNOWN_MESSAGES.contains(currentMessage);
                return new GenericGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
            }
        }
        return null;
    }

    protected void appendHandleUnsupportedTypeException(Writer w) throws IOException {
        w.append("                if (e.getNode() instanceof ").append(clazzName).append(") {\n");
        w.append("                  throw UnsupportedTypeException.raise(e, e.getSuppliedValues());\n");
        w.append("                } else {\n");
        w.append("                  throw e;\n");
        w.append("                }\n");
    }

}
