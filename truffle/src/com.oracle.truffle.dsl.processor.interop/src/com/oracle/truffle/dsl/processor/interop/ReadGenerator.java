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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public final class ReadGenerator extends MessageGenerator {

    private static final int NUMBER_OF_READ = 2; // TruffleObject receiver,
                                                 // Object identifier
    private final String targetablePropReadNode;
    private final String propReadRootNode;

    public ReadGenerator(ProcessingEnvironment processingEnv, Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        super(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        this.targetablePropReadNode = (new StringBuilder(messageName)).replace(0, 1, messageName.substring(0, 1).toUpperCase()).append("Node").insert(0, "Targetable").toString();
        this.propReadRootNode = (new StringBuilder(messageName)).replace(0, 1, messageName.substring(0, 1).toUpperCase()).append("RootNode").toString();
    }

    @Override
    void appendRootNode(Writer w) throws IOException {
        w.append("    private static final class ").append(propReadRootNode).append(" extends RootNode {\n");
        w.append("        protected ").append(propReadRootNode).append("() {\n");
        w.append("            super(null);\n");
        w.append("        }\n");
        w.append("\n");
        w.append("        @Child private ").append(clazzName).append(" node = ").append(packageName).append(".").append(clazzName).append("NodeGen.create();");
        w.append("\n");
        w.append("        @Override\n");
        w.append("        public Object execute(VirtualFrame frame) {\n");
        w.append("            Object receiver = ForeignAccess.getReceiver(frame);\n");
        w.append("            Object identifier = ForeignAccess.getArguments(frame).get(0);\n");
        w.append("            try {\n");
        w.append("                return node.executeWithTarget(frame, receiver, identifier);\n");
        w.append("            } catch (UnsupportedSpecializationException e) {\n");
        appendHandleUnsupportedTypeException(w);
        w.append("            }\n");
        w.append("        }\n");
        w.append("\n");
        w.append("    }\n");
    }

    @Override
    int getParameterCount() {
        return NUMBER_OF_READ;
    }

    @Override
    String getTargetableNodeName() {
        return targetablePropReadNode;
    }

    @Override
    String getRootNodeName() {
        return propReadRootNode;
    }

    @Override
    public String checkSignature(ExecutableElement method) {
        final List<? extends VariableElement> params = method.getParameters();
        boolean hasFrameArgument = false;
        if (params.size() >= 1) {
            hasFrameArgument = ElementUtils.areTypesCompatible(params.get(0).asType(), Utils.getTypeMirror(processingEnv, VirtualFrame.class));
        }
        int expectedNumberOfArguments = hasFrameArgument ? getParameterCount() + 1 : getParameterCount();

        if (params.size() != expectedNumberOfArguments) {
            return "Wrong number of arguments. Expected signature: ([frame: VirtualFrame], receiverObject: TruffleObject, identifier: String)";
        }
        return super.checkSignature(method);
    }

}
