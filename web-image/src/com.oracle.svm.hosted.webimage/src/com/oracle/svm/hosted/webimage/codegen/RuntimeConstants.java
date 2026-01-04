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

import java.util.HashMap;
import java.util.Map;

import com.oracle.svm.hosted.webimage.codegen.type.ClassMetadataLowerer;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.webimage.JSNameGenerator;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;

/**
 * Defines constants that can be used by handwritten (and generated) JS code.
 */
public class RuntimeConstants {

    /**
     * JS constant name that is set to 'true' if debug runtime checks are enabled.
     *
     * When changing this value, it must also be changed in handwritten JS code that uses this
     * constant.
     */
    public static final String DEBUG_CHECKS = JSNameGenerator.registerReservedSymbol("DEBUG_CHECKS");

    /**
     * Short name for 'null' constant to save space.
     */
    public static final String NULL = JSNameGenerator.registerReservedSymbol("N");

    /**
     * Short name for 'undefined' constant to save space.
     */
    public static final String UNDEFINED = JSNameGenerator.registerReservedSymbol("U");

    /**
     * Short name for constant that corresponds to the {@code runtime.symbol.classMeta} symbol.
     */
    public static final String CLASS_META_SYMBOL = JSNameGenerator.registerReservedSymbol("CM");

    /**
     * Short name for constant that corresponds to the {@code runtime.symbol.jsClass} symbol.
     */
    public static final String JS_CLASS_SYMBOL = JSNameGenerator.registerReservedSymbol("JC");

    /**
     * Short name that corresponds to the {@code cprops} function.
     */
    public static final String GET_CLASS_PROTOTYPE = JSNameGenerator.registerReservedSymbol("CP");

    /**
     * Short name that corresponds to the {@code runtime.symbol} expression.
     */
    public static final String RUNTIME_SYMBOL = JSNameGenerator.registerReservedSymbol("SYM");

    /**
     * Short name that corresponds to the {@code runtime.hubs} expression.
     */
    public static final String RUNTIME_HUBS = JSNameGenerator.registerReservedSymbol("RH");

    /**
     * Stores the name of the hash code field in objects so that we don't have to repeat it in
     * hand-written JS code.
     */
    public static final String HASH_CODE_FIELD = JSNameGenerator.registerReservedSymbol("HASH_CODE_FIELD");

    /**
     * Runtime constants used in code generation.
     */
    private static Map<String, ConstantDeclaration> constants;

    private static void genDefinition(JSCodeGenTool jsLTools, String docComment, String name, String value, String comment) {
        CodeBuffer masm = jsLTools.getCodeBuffer();
        masm.emitNewLine();
        if (docComment != null) {
            masm.emitText("/** ");
            masm.emitText(docComment);
            masm.emitText(" */");
            masm.emitWhiteSpace();
        }
        masm.emitKeyword(JSKeyword.CONST);
        masm.emitWhiteSpace();
        jsLTools.genResolvedVarAssignmentPrefix(name);
        masm.emitText(value);
        jsLTools.genResolvedVarDeclPostfix(comment);
    }

    @SuppressWarnings("serial")
    static Map<String, ConstantDeclaration> getConstantMap() {
        if (constants == null) {
            constants = new HashMap<>() {
                {
                    put(DEBUG_CHECKS, new ConstantDeclaration(null, DEBUG_CHECKS, WebImageOptions.DebugOptions.RuntimeDebugChecks.getValue() ? "true" : "false",
                                    WebImageOptions.DebugOptions.RuntimeDebugChecks.getDescriptor().getHelp().getFirst()));
                    put(NULL, new ConstantDeclaration(null, NULL, "null", "Short name for the null constant."));
                    put(UNDEFINED, new ConstantDeclaration(null, UNDEFINED, "undefined", "Short name for the undefined constant."));
                    put(CLASS_META_SYMBOL, new ConstantDeclaration(null, CLASS_META_SYMBOL, "runtime.symbol.classMeta",
                                    "Symbol that is the key for the class property with extra class metadata."));
                    put(JS_CLASS_SYMBOL, new ConstantDeclaration(null, JS_CLASS_SYMBOL, "runtime.symbol.jsClass",
                                    "Symbol that is the key for the property inside the hub object that points to the JavaScript class."));
                    put(GET_CLASS_PROTOTYPE, new ConstantDeclaration(null, GET_CLASS_PROTOTYPE, "cprops",
                                    "Function that obtains an object that can access the class properties."));
                    put(RUNTIME_SYMBOL, new ConstantDeclaration(null, RUNTIME_SYMBOL, "runtime.symbol",
                                    "Expression for obtaining the table of VM-internal Symbols."));
                    put(RUNTIME_HUBS, new ConstantDeclaration(null, RUNTIME_HUBS, "runtime.hubs",
                                    "Expression for obtaining the table of class hubs."));
                    put(HASH_CODE_FIELD, new ConstantDeclaration(null, HASH_CODE_FIELD, WebImageJSNodeLowerer.getStringLiteral(ClassMetadataLowerer.INSTANCE_TYPE_HASHCODE_FIELD_NAME),
                                    "Name of the hash code field in objects."));
                }
            };
        }
        return constants;
    }

    public static void lowerInitialDefinition(JSCodeGenTool jsLTools, Map<String, ConstantDeclaration> consts) {
        for (Map.Entry<String, ConstantDeclaration> entry : consts.entrySet()) {
            ConstantDeclaration decl = entry.getValue();
            genDefinition(jsLTools, decl.docComment, decl.name, decl.value, decl.comment);
        }
    }

    public static class ConstantDeclaration {
        public final String docComment;
        public final String name;
        public final String value;
        public final String comment;

        public ConstantDeclaration(String docComment, String name, String value, String comment) {
            this.docComment = docComment;
            this.name = name;
            this.value = value;
            this.comment = comment;
        }
    }
}
