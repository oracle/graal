/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.cds;

interface CDSArchiveFormat {

    int TAG_NULL = 1;
    int TAG_REFERENCE = 2;
    int TAG_STRING = 3;
    int TAG_SYMBOL = 4;
    int TAG_CLASS_REGISTRY_DATA = 5;
    int TAG_MODULE_ENTRY = 6;
    int TAG_PACKAGE_ENTRY = 7;
    int TAG_ARRAY_LIST = 8;

    int TAG_GUEST_NULL = 31;
    int TAG_GUEST_ARRAY = 32;
    int TAG_GUEST_CLASS = 33;
    int TAG_GUEST_STRING = 34;
    int TAG_GUEST_OBJECT = 35;
    int TAG_GUEST_CLASS_LOADER = 36;

    int MAGIC = 0x35973550;

    int MAJOR_VERSION = 1;
    int MINOR_VERSION = 1;
}
