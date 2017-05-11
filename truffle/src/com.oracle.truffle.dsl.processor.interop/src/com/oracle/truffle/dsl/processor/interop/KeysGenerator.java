/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;

final class KeysGenerator extends MessageGenerator {

    private static final String TARGETABLE_KEYS_NODE = "TargetableKeysNode";
    private static final String KEYS_ROOT_NODE = "KeysRootNode";
    private int parameterCount = 1;

    KeysGenerator(ProcessingEnvironment processingEnv, Resolve resolveAnnotation, MessageResolution messageResolutionAnnotation, TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        super(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
    }

    @Override
    int getParameterCount() {
        return parameterCount;
    }

    @Override
    void appendImports(Writer w) throws IOException {
        super.appendImports(w);
        if (parameterCount == 2) {
            w.append("import java.util.List;").append("\n");
        }
    }

    @Override
    void appendRootNode(Writer w) throws IOException {
        w.append("    private static final class ").append(KEYS_ROOT_NODE).append(" extends RootNode {\n");
        w.append("        protected ").append(KEYS_ROOT_NODE).append("() {\n");
        w.append("            super(null);\n");
        w.append("        }\n");
        w.append("\n");
        w.append("        @Child private ").append(clazzName).append(" node = ").append(packageName).append(".").append(clazzName).append("NodeGen.create();");
        w.append("\n");
        w.append("        @Override\n");
        w.append("        public Object execute(VirtualFrame frame) {\n");
        w.append("            Object receiver = ForeignAccess.getReceiver(frame);\n");
        if (parameterCount == 2) {
            w.append("            List<Object> arguments = ForeignAccess.getArguments(frame);\n");
            w.append("            Object internal = (arguments.isEmpty()) ? false : arguments.get(0);\n");
        }
        w.append("            try {\n");
        if (parameterCount == 2) {
            w.append("                return node.executeWithTarget(frame, receiver, internal);\n");
        } else {
            w.append("                return node.executeWithTarget(frame, receiver);\n");
        }
        w.append("            } catch (UnsupportedSpecializationException e) {\n");
        appendHandleUnsupportedTypeException(w);
        w.append("            }\n");
        w.append("        }\n");
        w.append("\n");
        w.append("    }\n");
    }

    @Override
    String getTargetableNodeName() {
        return TARGETABLE_KEYS_NODE;
    }

    @Override
    String getRootNodeName() {
        return KEYS_ROOT_NODE;
    }

    @Override
    public String checkSignature(ExecutableElement method) {
        final List<? extends VariableElement> params = method.getParameters();
        boolean hasFrameArgument = false;
        boolean hasInternalArgument = false;
        if (params.size() >= 1) {
            hasFrameArgument = ElementUtils.areTypesCompatible(params.get(0).asType(), Utils.getTypeMirror(processingEnv, VirtualFrame.class));
            int lastIndex = params.size() - 1;
            hasInternalArgument = ElementUtils.areTypesCompatible(params.get(lastIndex).asType(), processingEnv.getTypeUtils().getPrimitiveType(TypeKind.BOOLEAN));
        }

        int expectedNumberOfArguments = 1;
        if (hasFrameArgument) {
            expectedNumberOfArguments++;
        }
        if (hasInternalArgument) {
            expectedNumberOfArguments++;
        }

        if (params.size() != expectedNumberOfArguments) {
            return "Wrong number of arguments. Expected signature: ([frame: VirtualFrame], receiverObject: TruffleObject [, internal: boolean])";
        }

        parameterCount = (hasInternalArgument) ? 2 : 1;

        return super.checkSignature(method);
    }

}
