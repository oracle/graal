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

public final class WriteGenerator extends MessageGenerator {

    private static final int NUMBER_OF_WRITE = 3; // TruffleObject receiver,
                                                  // Object identifier, Object value
    private static final String TARGETABLE_WRITE_NODE = "TargetableWriteNode";
    private static final String WRITE_ROOT_NODE = "WriteRootNode";

    public WriteGenerator(ProcessingEnvironment processingEnv, Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        super(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
    }

    @Override
    void appendRootNode(Writer w) throws IOException {
        w.append("    private static final class ").append(WRITE_ROOT_NODE).append(" extends RootNode {\n");
        w.append("        protected ").append(WRITE_ROOT_NODE).append("() {\n");
        w.append("            super(null);\n");
        w.append("        }\n");
        w.append("\n");
        w.append("        @Child private ").append(clazzName).append(" node = ").append(packageName).append(".").append(clazzName).append("NodeGen.create();");
        w.append("\n");
        w.append("        @Override\n");
        w.append("        public Object execute(VirtualFrame frame) {\n");
        w.append("            Object receiver = ForeignAccess.getReceiver(frame);\n");
        w.append("            Object identifier = ForeignAccess.getArguments(frame).get(0);\n");
        w.append("            Object value = ForeignAccess.getArguments(frame).get(1);\n");
        w.append("            try {\n");
        w.append("                return node.executeWithTarget(frame, receiver, identifier, value);\n");
        w.append("            } catch (UnsupportedSpecializationException e) {\n");
        appendHandleUnsupportedTypeException(w);
        w.append("            }\n");
        w.append("        }\n");
        w.append("\n");
        w.append("    }\n");
    }

    @Override
    int getParameterCount() {
        return NUMBER_OF_WRITE;
    }

    @Override
    String getTargetableNodeName() {
        return TARGETABLE_WRITE_NODE;
    }

    @Override
    String getRootNodeName() {
        return WRITE_ROOT_NODE;
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
            return "Wrong number of arguments. Expected signature: ([frame: VirtualFrame], receiverObject: TruffleObject, identifier: String, value: Object)";
        }
        return super.checkSignature(method);
    }

}
