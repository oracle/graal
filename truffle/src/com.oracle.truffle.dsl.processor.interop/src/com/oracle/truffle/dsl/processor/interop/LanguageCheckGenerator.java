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
import javax.lang.model.type.TypeKind;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

final class LanguageCheckGenerator extends InteropNodeGenerator {
    protected static final String TEST_METHOD_NAME = "test";

    protected String receiverClassName;

    LanguageCheckGenerator(ProcessingEnvironment processingEnv, MessageResolution messageResolutionAnnotation, TypeElement element, ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        super(processingEnv, element, containingForeignAccessFactory);
        this.receiverClassName = Utils.getReceiverTypeFullClassName(messageResolutionAnnotation);
    }

    @Override
    public void appendNode(Writer w) throws IOException {
        Utils.appendMessagesGeneratedByInformation(w, indent, ElementUtils.getQualifiedName(element), receiverClassName);
        w.append(indent);
        Utils.appendVisibilityModifier(w, element);
        w.append("abstract static class ").append(clazzName).append(" extends ").append(userClassName).append(" {\n");
        appendExecuteWithTarget(w);
        appendSpecializations(w);

        appendRootNode(w);
        appendRootNodeFactory(w);

        w.append(indent).append("}\n");
    }

    public List<ExecutableElement> getTestMethods() {
        List<ExecutableElement> methods = new ArrayList<>();
        for (Element m : element.getEnclosedElements()) {
            if (m.getKind() != ElementKind.METHOD) {
                continue;
            }
            if (!m.getSimpleName().contentEquals(TEST_METHOD_NAME)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) m;
            methods.add(method);
        }
        return methods;
    }

    public String checkSignature(ExecutableElement method) {
        final List<? extends VariableElement> params = method.getParameters();
        boolean hasFrameArgument = false;
        if (params.size() >= 1) {
            hasFrameArgument = ElementUtils.typeEquals(params.get(0).asType(), Utils.getTypeMirror(processingEnv, VirtualFrame.class));
        }
        int expectedNumberOfArguments = hasFrameArgument ? 2 : 1;

        if (!ElementUtils.typeEquals(params.get(hasFrameArgument ? 1 : 0).asType(), Utils.getTypeMirror(processingEnv, TruffleObject.class))) {
            return "The receiver type must be TruffleObject";
        }

        if (!ElementUtils.isPrimitive(method.getReturnType()) || method.getReturnType().getKind() != TypeKind.BOOLEAN) {
            return "Method must return a boolean value";
        }

        if (params.size() != expectedNumberOfArguments) {
            return "Wrong number of arguments.";
        }

        if (method.getThrownTypes().size() > 0) {
            return "Method test must not throw a checked exception.";
        }
        return null;
    }

    void appendExecuteWithTarget(Writer w) throws IOException {
        w.append(indent).append("    public abstract Object executeWithTarget(VirtualFrame frame, ").append("Object ").append("o").append(");\n");
    }

    void appendSpecializations(Writer w) throws IOException {
        String sep = "";
        List<ExecutableElement> testMethods = getTestMethods();
        assert testMethods.size() == 1;

        final List<? extends VariableElement> params = testMethods.get(0).getParameters();

        w.append(indent).append("    @Specialization\n");
        w.append(indent).append("    protected Object ").append(TEST_METHOD_NAME).append("WithTarget");
        w.append("(");

        sep = "";
        for (VariableElement p : params) {
            w.append(sep).append(ElementUtils.getUniqueIdentifier(p.asType())).append(" ").append(p.getSimpleName());
            sep = ", ";
        }
        w.append(") {\n");
        w.append(indent).append("        return ").append(TEST_METHOD_NAME).append("(");
        sep = "";
        for (VariableElement p : params) {
            w.append(sep).append(p.getSimpleName());
            sep = ", ";
        }
        w.append(");\n");
        w.append(indent).append("    }\n");
    }

    void appendRootNode(Writer w) throws IOException {
        w.append(indent).append("    private static final class LanguageCheckRootNode extends RootNode {\n");
        w.append(indent).append("        protected LanguageCheckRootNode() {\n");
        w.append(indent).append("            super(null);\n");
        w.append(indent).append("        }\n");
        w.append("\n");
        w.append(indent).append("        @Child private ").append(clazzName).append(" node = ").append(getGeneratedDSLNodeQualifiedName()).append(".create();");
        w.append("\n");
        w.append(indent).append("        @Override\n");
        w.append(indent).append("        public Object execute(VirtualFrame frame) {\n");
        w.append(indent).append("            try {\n");
        w.append(indent).append("              Object receiver = ForeignAccess.getReceiver(frame);\n");
        w.append(indent).append("              return node.executeWithTarget(frame, receiver);\n");
        w.append(indent).append("            } catch (UnsupportedSpecializationException e) {\n");
        appendHandleUnsupportedTypeException(w);
        w.append(indent).append("            }\n");
        w.append(indent).append("        }\n");
        w.append("\n");
        w.append(indent).append("    }\n");
    }

    void appendRootNodeFactory(Writer w) throws IOException {
        w.append(indent).append("    public static RootNode createRoot() {\n");
        w.append(indent).append("        return new LanguageCheckRootNode();\n");
        w.append(indent).append("    }\n");
    }

    protected void appendHandleUnsupportedTypeException(Writer w) throws IOException {
        w.append(indent).append("                if (e.getNode() instanceof ").append(clazzName).append(") {\n");
        w.append(indent).append("                  throw UnsupportedTypeException.raise(e, e.getSuppliedValues());\n");
        w.append(indent).append("                } else {\n");
        w.append(indent).append("                  throw e;\n");
        w.append(indent).append("                }\n");
    }

    @Override
    public String toString() {
        return clazzName;
    }
}
