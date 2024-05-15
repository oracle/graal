/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graphio.parsing;

/**
 * Definitions to be shared between binary reader and writer.
 */
public class BinaryStreamDefs {
    static final int BEGIN_GROUP = 0;
    static final int BEGIN_GRAPH = 1;
    static final int CLOSE_GROUP = 2;
    static final int STREAM_PROPERTIES = 3;

    static final int POOL_NEW = 0;
    static final int POOL_STRING = 1;
    static final int POOL_ENUM = 2;
    static final int POOL_CLASS = 3;
    static final int POOL_METHOD = 4;
    static final int POOL_NULL = 5;
    static final int POOL_NODE_CLASS = 6;
    static final int POOL_FIELD = 7;
    static final int POOL_SIGNATURE = 8;
    static final int POOL_NODE_SOURCE_POSITION = 9;
    static final int POOL_NODE = 10;

    static final int KLASS = 0;
    static final int ENUM_KLASS = 1;
    static final int PROPERTY_POOL = 0;
    static final int PROPERTY_INT = 1;
    static final int PROPERTY_LONG = 2;
    static final int PROPERTY_DOUBLE = 3;
    static final int PROPERTY_FLOAT = 4;
    static final int PROPERTY_TRUE = 5;
    static final int PROPERTY_FALSE = 6;
    static final int PROPERTY_ARRAY = 7;
    static final int PROPERTY_SUBGRAPH = 8;
}
