/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.js;

import java.util.Arrays;

import org.graalvm.webimage.api.JS;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;

import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.FixedNodeInterface;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Interface for nodes implementing functionality for executing user-provided JavaScript code
 * ({@link JSCode}).
 */
public sealed interface JSBody extends SingleMemoryKill, StateSplit, FixedNodeInterface permits JSBodyNode, JSBodyWithExceptionNode {
    class JSCode {
        private final String[] args;
        private final String body;

        public JSCode(String[] args, String body) {
            this.args = args;
            this.body = body;
        }

        public JSCode(JS js, ResolvedJavaMethod m) {
            this(getParameterNames(js.args(), m), js.value());
        }

        private static String[] getParameterNames(String[] explicitNames, ResolvedJavaMethod m) {
            if (explicitNames.length == 1 && explicitNames[0].equals(JS.DEFAULT_ARGUMENTS)) {
                // The list of arguments was unspecified, so it should default to the arguments in
                // the signature of the method.
                ResolvedJavaMethod.Parameter[] parameters = m.getParameters();
                String[] parameterNames = new String[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    ResolvedJavaMethod.Parameter param = parameters[i];

                    UserError.guarantee(param.isNamePresent(),
                                    "Cannot infer argument names for @%s annotation. Either explicitly specify argument names or compile the target method with the '-parameters' flag: %s",
                                    JS.class.getSimpleName(), m);

                    parameterNames[i] = param.getName();
                }
                return parameterNames;
            } else {
                return explicitNames;
            }
        }

        public String[] getArgs() {
            return args;
        }

        public String getBody() {
            return body;
        }

        @Override
        public String toString() {
            return "JSCode{" +
                            "args=" + Arrays.toString(args) + ", " +
                            "body='" + body + '\'' +
                            '}';
        }
    }

    @Override
    default LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    default boolean hasSideEffect() {
        return true;
    }

    NodeInputList<ValueNode> getArguments();

    JSCode getJsCode();

    String getJSCodeAsString(CodeGenTool codeGenTool);

    ResolvedJavaMethod getMethod();

    boolean declaresJSResources();

}
