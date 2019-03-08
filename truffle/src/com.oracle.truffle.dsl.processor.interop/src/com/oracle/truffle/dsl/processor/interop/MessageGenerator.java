/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import java.util.Arrays;
import javax.tools.Diagnostic;

@SuppressWarnings("deprecation")
abstract class MessageGenerator extends InteropNodeGenerator {
    protected static final String ACCESS_METHOD_NAME = "access";

    protected final String messageName;
    protected final String receiverClassName;
    protected final String rootNodeName;

    MessageGenerator(ProcessingEnvironment processingEnv, com.oracle.truffle.api.interop.Resolve resolveAnnotation, com.oracle.truffle.api.interop.MessageResolution messageResolutionAnnotation,
                    TypeElement element,
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
        String rootName = "Interop::" + messageName + "::";
        w.append(indent).append("            return \"").append(rootName).append("\" + " + receiverClassName + ".class.getName();\n");
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

    @SuppressWarnings("deprecation")
    public static MessageGenerator getGenerator(ProcessingEnvironment processingEnv, com.oracle.truffle.api.interop.Resolve resolveAnnotation,
                    com.oracle.truffle.api.interop.MessageResolution messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        String messageName = resolveAnnotation.message();

        Object currentMessage = Utils.getMessage(processingEnv, messageName);
        if (currentMessage == null) {
            SuppressWarnings suppress = element.getAnnotation(SuppressWarnings.class);
            if (suppress == null || !Arrays.asList(suppress.value()).contains("unknown-message")) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "Unknown message " + messageName + " (add @SuppressWarnings(\"unknown-message\") to ignore this warning)", element);
            }
        }
        if (com.oracle.truffle.api.interop.Message.READ.toString().equalsIgnoreCase(messageName) || com.oracle.truffle.api.interop.Message.KEY_INFO.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.REMOVE.toString().equalsIgnoreCase(messageName)) {
            return new ReadGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        } else if (com.oracle.truffle.api.interop.Message.WRITE.toString().equalsIgnoreCase(messageName)) {
            return new WriteGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        } else if (com.oracle.truffle.api.interop.Message.IS_NULL.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.IS_EXECUTABLE.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.IS_BOXED.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.HAS_SIZE.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.GET_SIZE.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.UNBOX.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.IS_INSTANTIABLE.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.HAS_KEYS.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.IS_POINTER.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.AS_POINTER.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.TO_NATIVE.toString().equalsIgnoreCase(messageName)) {
            return new UnaryGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        } else if (com.oracle.truffle.api.interop.Message.KEYS.toString().equalsIgnoreCase(messageName)) {
            return new KeysGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        } else if (com.oracle.truffle.api.interop.Message.EXECUTE.toString().equalsIgnoreCase(messageName) || com.oracle.truffle.api.interop.Message.INVOKE.toString().equalsIgnoreCase(messageName) ||
                        com.oracle.truffle.api.interop.Message.NEW.toString().equalsIgnoreCase(messageName)) {
            return new ExecuteGenerator(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        } else {
            assert !InteropDSLProcessor.getKnownMessages().contains(currentMessage);
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
