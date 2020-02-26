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

    /* Knobs */
    String JDK_SOURCE_BASE = ""; //"C:\\tmp\\graal-8\\jdk8_jvmci\\src\\";
    String GRAAL_SOURCE_BASE = ""; //"C:\\tmp\\graal-8\\graal8\\";

    boolean skipGraalInternals = false;             /* if true, don't emit debug code for Graal classes */
    boolean skipGraalIntrinsics = true;             /* Graal inlined code treated as generated code */
    boolean mergeAdjacentLineRecords = false;       /* if a line record is the same line in the same file as the previous record, meerge them */
    String replaceMainFunctionName = null; //"javamain";    /* first main() becomes this name (with no arg list at all) */

    /* setting functionNamesHashArgs causes link errors as the illegal characters in arg lists confuse link.exe */
    boolean functionNamesHashArgs = true;           /* if true, arg lists become obscure integers */
}
