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

package com.oracle.svm.hosted.webimage.codegen;

import com.oracle.svm.hosted.webimage.snippets.JSSnippet;
import com.oracle.svm.hosted.webimage.snippets.JSSnippets;
import com.oracle.svm.webimage.functionintrinsics.JSGenericFunctionDefinition;

/**
 * Generates the main entry function for users.
 * <p>
 * Users call this function with an array of JS strings that represent the arguments to the Java
 * entry point as well as an optional configuration object:
 *
 * <pre>
 *     const vm = await GraalVM.run(["Hello", "World"], {});
 *     // or
 *     GraalVM.run(["Hello", "World"], {}).then(ret => console.log("Exit code " + vm.exitCode));
 * </pre>
 *
 * The function is async and, once finished, returns the exit code.
 */
public class WebImageEntryFunctionLowerer {
    public static final JSGenericFunctionDefinition FUNCTION = new JSGenericFunctionDefinition("run", 2, false, null, false);

    public WebImageEntryFunctionLowerer() {
    }

    public void lowerEntryFunction(JSCodeGenTool codeGenTool) {
        JSSnippet entryFunction = JSSnippets.instantiateEntryFunction(
                        codeGenTool.vmClassName(),
                        FUNCTION.getFunctionName(),
                        Runtime.CONFIG_CLASS_NAME,
                        preCallSnippet(codeGenTool).asString(),
                        NameSpaceHideLowerer.FUNCTION.getFunctionName());
        entryFunction.lower(codeGenTool);
    }

    protected JSSnippet preCallSnippet(@SuppressWarnings("unused") JSCodeGenTool jsCodeGenTool) {
        return JSSnippets.emptySnippet();
    }
}
