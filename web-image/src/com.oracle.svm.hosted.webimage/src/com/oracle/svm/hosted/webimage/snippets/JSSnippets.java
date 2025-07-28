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

package com.oracle.svm.hosted.webimage.snippets;

import java.text.MessageFormat;
import java.util.List;

import com.oracle.svm.webimage.hightiercodegen.Emitter;

/**
 * Collection of small handwritten JavaScript snippets that may contain some placeholder values.
 */
public final class JSSnippets {

    private static final String entryFunction = "" +
                    "/** @suppress '{'checkVars,duplicate'}' */ {0}.{1} = async function (vmArgs, config = new {0}.{2}()) '{'\n" +
                    "   let data = new Data(config);\n" +
                    "   for (let libname in config.libraries) '{'\n" +
                    "       const content = await runtime.fetchText(config.libraries[libname]);\n" +
                    "       data.libraries[libname] = content;\n" +
                    "   '}'\n" +
                    "{3}\n" +
                    "   let vm = {4}(vmArgs, data);\n" +
                    "   return vm;\n" +
                    "'}'";

    public static JSSnippet instantiateEntryFunction(String vmClassName, String runFunctionName, String configClassName, String preCall, String vmFunctionName) {
        return new JSSnippet(MessageFormat.format(entryFunction, vmClassName, runFunctionName, configClassName, preCall, vmFunctionName));
    }

    private static final String exportMirrorClassDefinition = "" +
                    "Object.defineProperty(runtime.ensureExportPackage(''{0}''),\n" +
                    "''{1}'',\n" +
                    "'{' configurable: false, enumerable: true, get: () => '{' {2} return {3}; '}' '}')";

    public static JSSnippet instantiateExportMirrorClassDefinition(String packageName, String className, String hub, String internalMirrorClassName) {
        return new JSSnippet(MessageFormat.format(exportMirrorClassDefinition, packageName, className, hub == null ? "" : "ensureInitialized(" + hub + ");", internalMirrorClassName));
    }

    private static final String signExtend = MessageFormat.format("(({0} << (32 - {0})) >> (32 - {0}))", JSSnippetWithEmitterSupport.EmitterPlaceHolder);

    public static JSSnippetWithEmitterSupport instantiateSignExtendSnippet(List<Emitter> emitters) {
        return new JSSnippetWithEmitterSupport(signExtend, emitters);
    }

    /**
     * Used in {@code string-extension.js}.
     */
    private static final String stringCharConstructor = String.format("""
                    function stringCharConstructor(javaStr, charArray) {
                        javaStr.%s(javaStr, charArray);
                    }
                    """, JSSnippetWithEmitterSupport.EmitterPlaceHolder);

    /**
     * Calls the {@link String#String(char[])} constructor on an already created, uninitialized
     * instance of {@link String}.
     */
    public static JSSnippetWithEmitterSupport instantiateStringCharConstructor(Emitter emitter) {
        return new JSSnippetWithEmitterSupport(stringCharConstructor, List.of(emitter));
    }

    public static JSSnippet emptySnippet() {
        return new JSSnippet("");
    }
}
