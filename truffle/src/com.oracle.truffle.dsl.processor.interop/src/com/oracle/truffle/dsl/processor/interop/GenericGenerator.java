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
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;

final class GenericGenerator extends MessageGenerator {

    private final String targetableExecuteNode;

    GenericGenerator(ProcessingEnvironment processingEnv, Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        super(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        String mName = messageName.substring(messageName.lastIndexOf('.') + 1);
        this.targetableExecuteNode = (new StringBuilder(mName)).replace(0, 1, mName.substring(0, 1).toUpperCase()).append("Node").insert(0, "Targetable").toString();
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
    void appendRootNode(Writer w) throws IOException {
        w.append(indent).append("    private static final class ").append(rootNodeName).append(" extends RootNode {\n");
        w.append(indent).append("        protected ").append(rootNodeName).append("() {\n");
        w.append(indent).append("            super(null);\n");
        w.append(indent).append("        }\n");
        w.append("\n");
        w.append(indent).append("        @Child private ").append(clazzName).append(" node = ").append(getGeneratedDSLNodeQualifiedName()).append(".create();");
        w.append("\n");
        appendGetName(w);
        w.append(indent).append("        @Override\n");
        w.append(indent).append("        public Object execute(VirtualFrame frame) {\n");
        w.append(indent).append("            try {\n");
        w.append(indent).append("              Object receiver = ForeignAccess.getReceiver(frame);\n");
        boolean listGenerated = false;
        for (int i = 0; i < getParameterCount() - 1; i++) {
            if (!listGenerated) {
                w.append("              java.util.List<Object> arguments = ForeignAccess.getArguments(frame);\n");
                listGenerated = true;
            }
            String index = String.valueOf(i);
            w.append(indent).append("              Object arg").append(index).append(" = arguments.get(").append(index).append(");\n");
        }
        w.append(indent).append("              return node.executeWithTarget(frame, receiver");
        for (int i = 0; i < getParameterCount() - 1; i++) {
            String index = String.valueOf(i);
            w.append(", ").append("arg").append(index);
        }
        w.append(");\n");

        w.append(indent).append("            } catch (UnsupportedSpecializationException e) {\n");
        appendHandleUnsupportedTypeException(w);
        w.append(indent).append("            }\n");
        w.append(indent).append("        }\n");
        w.append("\n");
        w.append(indent).append("    }\n");
    }

}
