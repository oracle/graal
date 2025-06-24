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

import com.oracle.svm.webimage.functionintrinsics.JSGenericFunctionDefinition;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;

/**
 * Wraps the Web Image namespace into its own scope so that definitions don't leak out.
 *
 * All java class definitions and our own handwritten JS code will be inside the createVM function:
 *
 * <pre>
 * function createVM(vmArgs, data) {
 *   // Initial definitions
 *   class _Object {
 *       ...
 *   }
 *
 *   // Other classes
 *
 *   // Entry point
 * }
 * </pre>
 *
 * Calling this function runs the compiled java runtime.
 */
public class NameSpaceHideLowerer {

    public static final JSGenericFunctionDefinition FUNCTION = new JSGenericFunctionDefinition("createVM", 2, false, null, false);
    public static final String VM_ARGS = "vmArgs";
    /**
     * Name of the data argument to the 'createVM` function.
     *
     * This is an object created by us containing data that may be useful to the runtime (e.g.
     * loaded resources).
     */
    public static final String DATA_ARG = "data";

    public static void lowerNameSpaceHidingFunction(JSCodeGenTool jsLTools) {
        CodeBuffer masm = jsLTools.getCodeBuffer();

        masm.emitText("const ");
        FUNCTION.emitReference(jsLTools);
        masm.emitText(" = function(" + VM_ARGS + ", " + DATA_ARG + ") {");
        jsLTools.getCodeBuffer().emitNewLine();
        jsLTools.getCodeBuffer().emitText("runtime.data = " + DATA_ARG + ";");
        jsLTools.getCodeBuffer().emitNewLine();
    }

    public static void lowerNameSpaceHidingEnd(JSCodeGenTool jsLTools) {
        jsLTools.getCodeBuffer().emitNewLine();
        jsLTools.getCodeBuffer().emitText("};");
    }
}
