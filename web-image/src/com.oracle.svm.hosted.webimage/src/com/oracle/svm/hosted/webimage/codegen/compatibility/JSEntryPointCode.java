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
package com.oracle.svm.hosted.webimage.codegen.compatibility;

import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.codegen.Array;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.Runtime;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionDefinition;
import com.oracle.svm.webimage.functionintrinsics.JSGenericFunctionDefinition;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;
import com.oracle.svm.webimage.hightiercodegen.Emitter;

/**
 * Generates the function that calls the entry point.
 *
 * The generated function takes a JS array of JS strings, converts it to a Java array of Java
 * strings and passes it to the entry point method.
 */
public class JSEntryPointCode {
    public static void lower(JSCodeGenTool jsLTools, HostedMethod mainEntryPoint) {
        CodeBuffer masm = jsLTools.getCodeBuffer();

        String argName = "args";
        String convertedArgName = "javaArgs";

        masm.emitNewLine();
        masm.emitNewLine();
        masm.emitKeyword(JSKeyword.FUNCTION);
        masm.emitWhiteSpace();
        ENTRY_POINT_FUN.emitReference(jsLTools);
        masm.emitText("(" + argName + ") ");
        masm.emitScopeBegin();

        /*
         * The entry point gets passed an array of JS strings. Here we convert that into an array
         * (with hub) of Java strings.
         */
        HostedType stringtype = (HostedType) jsLTools.getProviders().getMetaAccess().lookupJavaType(String.class);
        jsLTools.genResolvedVarDeclPrefix(convertedArgName);
        Array.lowerNewArray(stringtype, Emitter.of(argName + ".length"), jsLTools);
        jsLTools.genResolvedVarDeclPostfix(null);

        masm.emitText("for (let i = 0; i < " + argName + ".length; i++) ");
        masm.emitScopeBegin();
        masm.emitText(convertedArgName + "[i] = ");
        Runtime.TO_JAVA_STRING.emitCall(jsLTools, Emitter.of(argName + "[i]"));
        masm.emitText(";");
        jsLTools.genScopeEnd();

        String nameRes = "exitCode";
        jsLTools.genResolvedVarDeclPrefix(nameRes);
        jsLTools.genStaticCall(mainEntryPoint, Emitter.of(convertedArgName));
        jsLTools.genResolvedVarDeclPostfix(null);

        jsLTools.genReturn(nameRes);

        jsLTools.genScopeEnd();
    }

    public static final JSFunctionDefinition ENTRY_POINT_FUN = new JSGenericFunctionDefinition("entryPointCall", 1, false, null, false);
}
