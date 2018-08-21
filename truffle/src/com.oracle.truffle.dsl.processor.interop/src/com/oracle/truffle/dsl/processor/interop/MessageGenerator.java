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
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import java.util.Arrays;
import javax.tools.Diagnostic;

abstract class MessageGenerator extends InteropNodeGenerator {
    protected static final String ACCESS_METHOD_NAME = "access";

    protected final String messageName;
    protected final String receiverClassName;
    protected final String rootNodeName;

    MessageGenerator(ProcessingEnvironment processingEnv, Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        super(processingEnv, element, containingForeignAccessFactory);
        this.receiverClassName = Utils.getReceiverTypeFullClassName(messageResolutionAnnotation);
        this.messageName = resolveAnnotation.message();
        String mName = messageName.substring(messageName.lastIndexOf('.') + 1);
        this.rootNodeName = mName + "RootNode";
    }

    public void appendGetName(Writer w) throws IOException {
        w.append(indent).append("        @Override\n");
        w.append(indent).append("        public String getName() {\n");
        String rootName = "Interop::" + messageName + "::" + receiverClassName;
        w.append(indent).append("            return \"").append(rootName).append("\";\n");
        w.append(indent).append("        }\n\n");
    }

    @Override
    public void appendNode(Writer w) throws IOException {
        Utils.appendMessagesGeneratedByInformation(w, indent, ElementUtils.getQualifiedName(element), null);
        w.append(indent);
        Utils.appendVisibilityModifier(w, element);
        w.append("abstract static class ").append(clazzName).append(" extends ").append(userClassName).append(" {\n");

        appendExecuteWithTarget(w);
        appendSpecializations(w);

        appendRootNode(w);
        appendRootNodeFactory(w);

        w.append(indent).append("}\n");
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

    abstract int getParameterCount();

    public String checkSignature(ExecutableElement method) {
        if (method.getThrownTypes().size() > 0) {
            return "Method access must not throw a checked exception. Use an InteropException (e.g. UnknownIdentifierException.raise() ) to report an error to the host language.";
        }
        return null;
    }

    abstract String getTargetableNodeName();

    void appendExecuteWithTarget(Writer w) throws IOException {
        w.append(indent).append("    public abstract Object executeWithTarget(VirtualFrame frame");
        for (int i = 0; i < Math.max(1, getParameterCount()); i++) {
            w.append(", ").append("Object ").append("o").append(String.valueOf(i));
        }
        w.append(");\n");
    }

    void appendSpecializations(Writer w) throws IOException {
        String sep = "";
        for (ExecutableElement method : getAccessMethods()) {
            final List<? extends VariableElement> params = method.getParameters();

            w.append(indent).append("    @Specialization\n");
            w.append(indent).append("    protected Object ").append(ACCESS_METHOD_NAME).append("WithTarget");
            w.append("(");

            sep = "";
            for (VariableElement p : params) {
                w.append(sep).append(ElementUtils.getUniqueIdentifier(p.asType())).append(" ").append(p.getSimpleName());
                sep = ", ";
            }
            w.append(") {\n");
            w.append(indent).append("        return ").append(ACCESS_METHOD_NAME).append("(");
            sep = "";
            for (VariableElement p : params) {
                w.append(sep).append(p.getSimpleName());
                sep = ", ";
            }
            w.append(");\n");
            w.append(indent).append("    }\n");
        }
    }

    abstract void appendRootNode(Writer w) throws IOException;

    void appendRootNodeFactory(Writer w) throws IOException {
        w.append(indent).append("    public static RootNode createRoot() {\n");
        w.append(indent).append("        return new ").append(rootNodeName).append("();\n");
        w.append(indent).append("    }\n");
    }

    @Override
    public String toString() {
        return clazzName;
    }

    public static MessageGenerator getGenerator(ProcessingEnvironment processingEnv, Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        String messageName = resolveAnnotation.message();

        Object currentMessage = Utils.getMessage(processingEnv, messageName);
        if (currentMessage == null) {
            SuppressWarnings suppress = element.getAnnotation(SuppressWarnings.class);
            if (suppress == null || !Arrays.asList(suppress.value()).contains("unknown-message")) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "Unknown message " + messageName + " (add @SuppressWarnings(\"unknown-message\") to ignore this warning)", element);
            }
        }
        if (Message.READ.toString().equalsIgnoreCase(messageName) || Message.KEY_INFO.toString().equalsIgnoreCase(messageName) ||
                        Message.REMOVE.toString().equalsIgnoreCase(messageName)) {
            return new ReadGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        } else if (Message.WRITE.toString().equalsIgnoreCase(messageName)) {
            return new WriteGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        } else if (Message.IS_NULL.toString().equalsIgnoreCase(messageName) || Message.IS_EXECUTABLE.toString().equalsIgnoreCase(messageName) ||
                        Message.IS_BOXED.toString().equalsIgnoreCase(messageName) || Message.HAS_SIZE.toString().equalsIgnoreCase(messageName) ||
                        Message.GET_SIZE.toString().equalsIgnoreCase(messageName) || Message.UNBOX.toString().equalsIgnoreCase(messageName) ||
                        Message.IS_INSTANTIABLE.toString().equalsIgnoreCase(messageName) || Message.HAS_KEYS.toString().equalsIgnoreCase(messageName) ||
                        Message.IS_POINTER.toString().equalsIgnoreCase(messageName) ||
                        Message.AS_POINTER.toString().equalsIgnoreCase(messageName) || Message.TO_NATIVE.toString().equalsIgnoreCase(messageName)) {
            return new UnaryGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        } else if (Message.KEYS.toString().equalsIgnoreCase(messageName)) {
            return new KeysGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        } else if (Message.EXECUTE.toString().equalsIgnoreCase(messageName) || Message.INVOKE.toString().equalsIgnoreCase(messageName) ||
                        Message.NEW.toString().equalsIgnoreCase(messageName)) {
            return new ExecuteGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        } else {
            assert !InteropDSLProcessor.KNOWN_MESSAGES.contains(currentMessage);
            return new GenericGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        }
    }

    protected void appendHandleUnsupportedTypeException(Writer w) throws IOException {
        w.append(indent).append("                if (e.getNode() instanceof ").append(clazzName).append(") {\n");
        w.append(indent).append("                  throw UnsupportedTypeException.raise(e, e.getSuppliedValues());\n");
        w.append(indent).append("                } else {\n");
        w.append(indent).append("                  throw e;\n");
        w.append(indent).append("                }\n");
    }

    public String getMessageName() {
        return messageName;
    }

}
