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

package com.oracle.svm.webimage.functionintrinsics;

import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;

/**
 * Representation of a javascript function with signature.
 *
 * used for code generation for non - compiled functions
 *
 * (e.g. Long64)
 *
 */
public class JSGenericFunctionDefinition implements JSFunctionDefinition {
    protected final String functionName;
    protected final int nrOfArgs;
    /**
     * To lower functions that are not bound to any prototype at all (not even the static 'class'
     * object), provide <code>static==null</code> and <code>isNewInstance==null</code>.
     */
    protected final boolean staticFunction;
    protected final String protoTypeName;
    protected final boolean isNewInstance;

    public JSGenericFunctionDefinition(String functionName, int nrOfArgs, boolean staticFunction, String protoTypeName, boolean isNewInstance) {
        this.functionName = functionName;
        this.nrOfArgs = nrOfArgs;
        this.staticFunction = staticFunction;
        this.protoTypeName = protoTypeName;
        this.isNewInstance = isNewInstance;
    }

    public String getFunctionName() {
        return functionName;
    }

    @Override
    public int getNrOfArgs() {
        return nrOfArgs;
    }

    @Override
    public boolean isStatic() {
        return staticFunction;
    }

    @Override
    public void emitReference(CodeGenTool loweringTool) {
        CodeBuffer masm = loweringTool.getCodeBuffer();

        if (!isNewInstance && staticFunction) {
            masm.emitText(protoTypeName);
            masm.emitText(".");
        } else {
            // no prototype
        }

        masm.emitText(functionName);
    }

    protected void emitCallPrefix(CodeGenTool jsLTools) {
        CodeBuffer masm = jsLTools.getCodeBuffer();

        if (isNewInstance) {
            masm.emitText("new ");
        }

        emitReference(jsLTools);
    }

    @Override
    public void emitCall(CodeGenTool jsLTools, IEmitter... params) {
        CodeBuffer masm = jsLTools.getCodeBuffer();
        emitCallPrefix(jsLTools);
        masm.emitText("(");
        if (params != null) {
            jsLTools.genCommaList(params);
        }
        masm.emitText(")");
    }

    @Override
    public String toString() {
        return "JSGenericFunction - name:" + functionName + " nrOfParams:" + nrOfArgs + " ProtoName:" + protoTypeName;
    }
}
