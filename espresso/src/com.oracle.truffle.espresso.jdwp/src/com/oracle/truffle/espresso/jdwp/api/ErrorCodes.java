/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.api;

public final class ErrorCodes {

    public static final int INVALID_THREAD = 10;
    public static final int INVALID_THREAD_GROUP = 11;
    public static final int THREAD_NOT_SUSPENDED = 13;
    public static final int INVALID_OBJECT = 20;
    public static final int INVALID_CLASS = 21;
    public static final int CLASS_NOT_PREPARED = 22;
    public static final int INVALID_METHODID = 23;
    public static final int INVALID_FIELDID = 25;
    public static final int INVALID_FRAMEID = 30;
    public static final int OPAQUE_FRAME = 32;
    public static final int INVALID_SLOT = 35;
    public static final int INVALID_MODULE = 42;
    public static final int INVALID_CLASS_FORMAT = 60;
    public static final int FAILS_VERIFICATION = 62;
    public static final int ADD_METHOD_NOT_IMPLEMENTED = 63;
    public static final int SCHEMA_CHANGE_NOT_IMPLEMENTED = 64;
    public static final int HIERARCHY_CHANGE_NOT_IMPLEMENTED = 66;
    public static final int DELETE_METHOD_NOT_IMPLEMENTED = 67;
    public static final int UNSUPPORTED_VERSION = 68;
    public static final int NAMES_DONT_MATCH = 69;
    public static final int CLASS_MODIFIERS_CHANGE_NOT_IMPLEMENTED = 70;
    public static final int METHOD_MODIFIERS_CHANGE_NOT_IMPLEMENTED = 71;
    public static final int NOT_IMPLEMENTED = 99;
    public static final int ABSENT_INFORMATION = 101;
    public static final int INVALID_EVENT_TYPE = 102;
    public static final int INTERNAL = 113;
    public static final int INVALID_LENGTH = 504;
    public static final int INVALID_STRING = 506;
    public static final int INVALID_CLASS_LOADER = 507;
    public static final int INVALID_ARRAY = 508;

    private ErrorCodes() {
    }
}
