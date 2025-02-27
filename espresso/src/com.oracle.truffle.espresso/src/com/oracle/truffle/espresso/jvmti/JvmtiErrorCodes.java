/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmti;

public final class JvmtiErrorCodes {
    public static final int JVMTI_ERROR_NONE = 0;
    public static final int JVMTI_ERROR_INVALID_THREAD = 10;
    public static final int JVMTI_ERROR_INVALID_THREAD_GROUP = 11;
    public static final int JVMTI_ERROR_INVALID_PRIORITY = 12;
    public static final int JVMTI_ERROR_THREAD_NOT_SUSPENDED = 13;
    public static final int JVMTI_ERROR_THREAD_SUSPENDED = 14;
    public static final int JVMTI_ERROR_THREAD_NOT_ALIVE = 15;
    public static final int JVMTI_ERROR_INVALID_OBJECT = 20;
    public static final int JVMTI_ERROR_INVALID_CLASS = 21;
    public static final int JVMTI_ERROR_CLASS_NOT_PREPARED = 22;
    public static final int JVMTI_ERROR_INVALID_METHODID = 23;
    public static final int JVMTI_ERROR_INVALID_LOCATION = 24;
    public static final int JVMTI_ERROR_INVALID_FIELDID = 25;
    public static final int JVMTI_ERROR_INVALID_MODULE = 26;
    public static final int JVMTI_ERROR_NO_MORE_FRAMES = 31;
    public static final int JVMTI_ERROR_OPAQUE_FRAME = 32;
    public static final int JVMTI_ERROR_TYPE_MISMATCH = 34;
    public static final int JVMTI_ERROR_INVALID_SLOT = 35;
    public static final int JVMTI_ERROR_DUPLICATE = 40;
    public static final int JVMTI_ERROR_NOT_FOUND = 41;
    public static final int JVMTI_ERROR_INVALID_MONITOR = 50;
    public static final int JVMTI_ERROR_NOT_MONITOR_OWNER = 51;
    public static final int JVMTI_ERROR_INTERRUPT = 52;
    public static final int JVMTI_ERROR_INVALID_CLASS_FORMAT = 60;
    public static final int JVMTI_ERROR_CIRCULAR_CLASS_DEFINITION = 61;
    public static final int JVMTI_ERROR_FAILS_VERIFICATION = 62;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED = 63;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED = 64;
    public static final int JVMTI_ERROR_INVALID_TYPESTATE = 65;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED = 66;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED = 67;
    public static final int JVMTI_ERROR_UNSUPPORTED_VERSION = 68;
    public static final int JVMTI_ERROR_NAMES_DONT_MATCH = 69;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED = 70;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED = 71;
    public static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_ATTRIBUTE_CHANGED = 72;
    public static final int JVMTI_ERROR_UNMODIFIABLE_CLASS = 79;
    public static final int JVMTI_ERROR_UNMODIFIABLE_MODULE = 80;
    public static final int JVMTI_ERROR_NOT_AVAILABLE = 98;
    public static final int JVMTI_ERROR_MUST_POSSESS_CAPABILITY = 99;
    public static final int JVMTI_ERROR_NULL_POINTER = 100;
    public static final int JVMTI_ERROR_ABSENT_INFORMATION = 101;
    public static final int JVMTI_ERROR_INVALID_EVENT_TYPE = 102;
    public static final int JVMTI_ERROR_ILLEGAL_ARGUMENT = 103;
    public static final int JVMTI_ERROR_NATIVE_METHOD = 104;
    public static final int JVMTI_ERROR_CLASS_LOADER_UNSUPPORTED = 106;
    public static final int JVMTI_ERROR_OUT_OF_MEMORY = 110;
    public static final int JVMTI_ERROR_ACCESS_DENIED = 111;
    public static final int JVMTI_ERROR_WRONG_PHASE = 112;
    public static final int JVMTI_ERROR_INTERNAL = 113;
    public static final int JVMTI_ERROR_UNATTACHED_THREAD = 115;
    public static final int JVMTI_ERROR_INVALID_ENVIRONMENT = 116;
    public static final int JVMTI_ERROR_MAX = 116;

    public static final int JVMTI_OK = JVMTI_ERROR_NONE;
}
