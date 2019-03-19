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
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

@SuppressWarnings("deprecation")
final class ExecuteGenerator extends MessageGenerator {

    private final int numberOfArguments;
    // Execute: TruffleObject receiver, Object[] args
    // Invoke: TruffleObject receiver, String identifier, Object[] args
    // New: TruffleObject receiver, Object[] args
    private final String targetableExecuteNode;

    ExecuteGenerator(ProcessingEnvironment processingEnv, com.oracle.truffle.api.interop.Resolve resolveAnnotation, com.oracle.truffle.api.interop.MessageResolution messageResolutionAnnotation,
                    TypeElement element,
                    ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        super(processingEnv, resolveAnnotation, messageResolutionAnnotation, element, containingForeignAccessFactory);
        this.targetableExecuteNode = (new StringBuilder(messageName)).replace(0, 1, messageName.substring(0, 1).toUpperCase()).append("Node").insert(0, "Targetable").toString();
        if (com.oracle.truffle.api.interop.Message.EXECUTE.toString().equalsIgnoreCase(messageName)) {
            numberOfArguments = 2;
        } else if (com.oracle.truffle.api.interop.Message.INVOKE.toString().equalsIgnoreCase(messageName)) {
            numberOfArguments = 3;
        } else if (com.oracle.truffle.api.interop.Message.NEW.toString().equalsIgnoreCase(messageName)) {
            numberOfArguments = 2;
        } else {
            throw new AssertionError();
        }
    }

    @Override
    int getParameterCount() {
        return numberOfArguments;
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
        w.append(indent).append("            Object receiver = com.oracle.truffle.api.interop.ForeignAccess.getReceiver(frame);\n");
        w.append(indent).append("            Object[] arguments = frame.getArguments();\n");
        boolean isInvoke = com.oracle.truffle.api.interop.Message.INVOKE.toString().equalsIgnoreCase(messageName);
        if (isInvoke) {
            w.append(indent).append("            Object identifier = arguments[1];\n");
            w.append(indent).append("            Object[] args = new Object[arguments.length - 2];\n");
            w.append(indent).append("            for (int i = 0; i < arguments.length - 2; i++) {\n");
            w.append(indent).append("                args[i] = arguments[i + 2];\n");
            w.append(indent).append("            }\n");
        } else {
            w.append(indent).append("            Object[] args = new Object[arguments.length - 1];\n");
            w.append(indent).append("            for (int i = 0; i < arguments.length - 1; i++) {\n");
            w.append(indent).append("                args[i] = arguments[i + 1];\n");
            w.append(indent).append("            }\n");
        }
        w.append(indent).append("            try {\n");
        if (isInvoke) {
            w.append(indent).append("                return node.executeWithTarget(frame, receiver, identifier, args);\n");
        } else {
            w.append(indent).append("                return node.executeWithTarget(frame, receiver, args);\n");
        }
        w.append(indent).append("            } catch (UnsupportedSpecializationException e) {\n");
        appendHandleUnsupportedTypeException(w);
        w.append(indent).append("            }\n");
        w.append(indent).append("        }\n");
        w.append(indent).append("    }\n");
        w.append("\n");
    }

    @Override
    public String checkSignature(ExecutableElement method) {
        final List<? extends VariableElement> params = method.getParameters();
        boolean hasFrameArgument = false;
        if (params.size() >= 1) {
            hasFrameArgument = ElementUtils.typeEquals(params.get(0).asType(), Utils.getTypeMirror(processingEnv, VirtualFrame.class));
        }
        int expectedNumberOfArguments = hasFrameArgument ? getParameterCount() + 1 : getParameterCount();

        if (params.size() != expectedNumberOfArguments) {
            if (com.oracle.truffle.api.interop.Message.INVOKE.toString().equalsIgnoreCase(messageName)) {
                return "Wrong number of arguments. Expected signature: ([frame: VirtualFrame], receiverObject: TruffleObject, identifier: String, arguments: Object[])";
            } else if (com.oracle.truffle.api.interop.Message.EXECUTE.toString().equalsIgnoreCase(messageName)) {
                return "Wrong number of arguments. Expected signature: ([frame: VirtualFrame], receiverObject: TruffleObject, arguments: Object[])";
            } else {
                throw new IllegalStateException();
            }
        }

        if (com.oracle.truffle.api.interop.Message.INVOKE.toString().equalsIgnoreCase(messageName)) {
            if (!ElementUtils.typeEquals(params.get(hasFrameArgument ? 2 : 1).asType(), Utils.getTypeMirror(processingEnv, String.class))) {
                int i = hasFrameArgument ? 3 : 2;
                return "The " + i + " argument must be a " + String.class.getName() + "- but is " + ElementUtils.getQualifiedName(params.get(hasFrameArgument ? 2 : 1).asType());
            }
        }
        VariableElement variableElement = params.get(params.size() - 1);
        if (!Utils.isObjectArray(variableElement.asType())) {
            return "The last argument must be the arguments array. Required type: java.lang.Object[]";
        }
        return super.checkSignature(method);
    }

}
