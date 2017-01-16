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
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;

public final class GenericGenerator extends MessageGenerator {

    private final String targetableExecuteNode;
    private final String executeRootNode;

    public GenericGenerator(ProcessingEnvironment processingEnv, Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        super(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        String mName = messageName.substring(messageName.lastIndexOf('.') + 1);
        this.targetableExecuteNode = (new StringBuilder(mName)).replace(0, 1, mName.substring(0, 1).toUpperCase()).append("Node").insert(0, "Targetable").toString();
        this.executeRootNode = (new StringBuilder(mName)).replace(0, 1, mName.substring(0, 1).toUpperCase()).append("RootNode").toString();
    }

    @Override
    int getParameterCount() {
        List<? extends VariableElement> parameters = getAccessMethods().get(0).getParameters();
        int parameterCount = parameters.size();
        if (parameters.size() >= 1) {
            parameterCount -= 1;
        }
        return parameterCount;
    }

    @Override
    String getTargetableNodeName() {
        return targetableExecuteNode;
    }

    @Override
    void appendImports(Writer w) throws IOException {
        super.appendImports(w);
        w.append("import java.util.List;").append("\n");
    }

    @Override
    void appendRootNode(Writer w) throws IOException {
        w.append("    private static final class ").append(executeRootNode).append(" extends RootNode {\n");
        w.append("        protected ").append(executeRootNode).append("(Class<? extends TruffleLanguage<?>> language) {\n");
        w.append("            super(language, null, null);\n");
        w.append("        }\n");
        w.append("\n");
        w.append("        @Child private ").append(clazzName).append(" node = ").append(packageName).append(".").append(clazzName).append("NodeGen.create();");
        w.append("\n");
        w.append("        @Override\n");
        w.append("        public Object execute(VirtualFrame frame) {\n");
        w.append("            try {\n");
        w.append("              Object receiver = ForeignAccess.getReceiver(frame);\n");
        w.append("              List<Object> arguments = ForeignAccess.getArguments(frame);\n");
        for (int i = 0; i < getParameterCount() - 1; i++) {
            String index = String.valueOf(i);
            w.append("              Object arg").append(index).append(" = arguments.get(").append(index).append(");\n");
        }
        w.append("              return node.executeWithTarget(frame, receiver");
        for (int i = 0; i < getParameterCount() - 1; i++) {
            String index = String.valueOf(i);
            w.append(", ").append("arg").append(index);
        }
        w.append(");\n");

        w.append("            } catch (UnsupportedSpecializationException e) {\n");
        appendHandleUnsupportedTypeException(w);
        w.append("            }\n");
        w.append("        }\n");
        w.append("\n");
        w.append("    }\n");
    }

    @Override
    String getRootNodeName() {
        return executeRootNode;
    }

}
