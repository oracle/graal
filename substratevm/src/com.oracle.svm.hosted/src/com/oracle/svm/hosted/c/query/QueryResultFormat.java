/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.query;

/**
 * Description of the simple native library query result.
 *
 * Query result consists of a number of lines. Each line contains information regarding a certain
 * element in the library, i.e., constant or struct type.
 * <p>
 * A constant value query result:
 *
 * "CONSTANT : id : VALUE DELIMINATOR constantValue".
 *
 * A struct type size query result:
 *
 * "STRUCT : id : SIZE DELIMINATOR sizeValue".
 *
 * A struct field size query result:
 *
 * "STRUCT : id : FIELD : id : SIZE DELIMINATOR sizeValue ".
 *
 * A struct field offset query result:
 * 
 * "STRUCT : id : FIELD : id : OFFSET DELIMINATOR offsetValue ".
 *
 */
public class QueryResultFormat {

    public static final String DELIMINATOR = "=";

    public static final String STRING_MARKER = "$";
}
