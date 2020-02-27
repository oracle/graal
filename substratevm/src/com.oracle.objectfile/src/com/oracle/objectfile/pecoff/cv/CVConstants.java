/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.pecoff.cv;

public interface CVConstants {

    /* names of relevant codeview sections */
    String CV_SYMBOL_SECTION_NAME = ".debug$S";
    String CV_TYPE_SECTION_NAME = ".debug$T";
    //String CV_RDATA_SECTION_NAME = ".rdata";
    //String CV_PDATA_SECTION_NAME = ".pdata";
    //String CV_XDATA_SECTION_NAME = ".xdata";
    //String TEXT_SECTION_NAME = ".text";
    //String DATA_SECTION_NAME = ".data";

    /* Codeview section header signature */
    int CV_SIGNATURE_C13 = 4;

    /* 
     * Knobs 
     * 
     * (some may become Graal options in the future)
     */

    /*
     * path to JDK source code (for example unzipped src.zip)
     * If set, source paths for JDK classes in the object file will be
     *    $JDK_SOURCE_BASE/java/package/someclass.java
     * instead of 
     *    (cache directory)/sources/jdk/java/package/someclass.java
     * or (if source cache is disabled)
     *    java/package/someclass.java
     * 
     * example
     *    JDK_SOURCE_BASE = C:\\tmp\\graal-8\\jdk8_jvmci\\src\\";
     */
    String JDK_SOURCE_BASE = "";

    /*
     * path to Graal source code base (for examplke checked out Graal source repository)
     * if set source paths will be inferred from appropriate Graal package directories
     * (behaves similarly to JDK_SOURCE_BASE)
     * 
     * Example:
     *    GRAAL_SOURCE_BASE = "C:\\tmp\\graal-8\\graal8\\";
     */
    String GRAAL_SOURCE_BASE = "";

    boolean skipGraalInternals = false;         /* if true, don't emit debug code for Graal classes */
    boolean skipJDKInternals = false;           /* (unimplemented) if true, don't emit debug code for JDK classes */
    boolean skipGraalIntrinsics = true;         /* Graal inlined code treated as generated code */
    boolean mergeAdjacentLineRecords = false;   /* if a line record is the same line in the same file as the previous record, meerge them */
    boolean emitUnadornedMain = true;           /* if true, first main() does not have args in the debug name */
    String replaceMainFunctionName = null;      /* first main() becomes this name (with no class name or arg list at all) (set null to disable) */

    /* 
     * The standard link.exe can't handle odd characters (parentheses or commas, for example) in enternal names.
     * Setting functionNamesHashArgs true replaces those names, 
     * so that "Foo.function(String[] args)" becomes "Foo.function_617849326".
     * If functionNamesHashArgs is false, currently the linker will fail.
     */
    boolean functionNamesHashArgs = true;       /* if true, arg lists become obscure integers (and link.exe will not work properly) */
}
