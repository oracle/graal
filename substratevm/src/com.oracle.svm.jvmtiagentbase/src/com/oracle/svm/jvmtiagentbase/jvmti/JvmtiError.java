/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jvmtiagentbase.jvmti;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;

@CEnum("jvmtiError")
@CContext(JvmtiDirectives.class)
public enum JvmtiError {
    JVMTI_ERROR_NONE,
    JVMTI_ERROR_INVALID_PRIORITY,
    JVMTI_ERROR_THREAD_NOT_SUSPENDED,
    JVMTI_ERROR_THREAD_SUSPENDED,
    JVMTI_ERROR_THREAD_NOT_ALIVE,
    JVMTI_ERROR_CLASS_NOT_PREPARED,
    JVMTI_ERROR_NO_MORE_FRAMES,
    JVMTI_ERROR_OPAQUE_FRAME,
    JVMTI_ERROR_DUPLICATE,
    JVMTI_ERROR_NOT_FOUND,
    JVMTI_ERROR_NOT_MONITOR_OWNER,
    JVMTI_ERROR_INTERRUPT,
    JVMTI_ERROR_UNMODIFIABLE_CLASS,
    JVMTI_ERROR_NOT_AVAILABLE,
    JVMTI_ERROR_ABSENT_INFORMATION,
    JVMTI_ERROR_INVALID_EVENT_TYPE,
    JVMTI_ERROR_NATIVE_METHOD,
    JVMTI_ERROR_CLASS_LOADER_UNSUPPORTED,
    JVMTI_ERROR_NULL_POINTER,
    JVMTI_ERROR_OUT_OF_MEMORY,
    JVMTI_ERROR_ACCESS_DENIED,
    JVMTI_ERROR_UNATTACHED_THREAD,
    JVMTI_ERROR_INVALID_ENVIRONMENT,
    JVMTI_ERROR_WRONG_PHASE,
    JVMTI_ERROR_INTERNAL,

    JVMTI_ERROR_INVALID_THREAD,
    JVMTI_ERROR_INVALID_FIELDID,
    JVMTI_ERROR_INVALID_METHODID,
    JVMTI_ERROR_INVALID_LOCATION,
    JVMTI_ERROR_INVALID_OBJECT,
    JVMTI_ERROR_INVALID_CLASS,
    JVMTI_ERROR_TYPE_MISMATCH,
    JVMTI_ERROR_INVALID_SLOT,
    JVMTI_ERROR_MUST_POSSESS_CAPABILITY,
    JVMTI_ERROR_INVALID_THREAD_GROUP,
    JVMTI_ERROR_INVALID_MONITOR,
    JVMTI_ERROR_ILLEGAL_ARGUMENT,
    JVMTI_ERROR_INVALID_TYPESTATE,
    JVMTI_ERROR_UNSUPPORTED_VERSION,
    JVMTI_ERROR_INVALID_CLASS_FORMAT,
    JVMTI_ERROR_CIRCULAR_CLASS_DEFINITION,
    JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED,
    JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED,
    JVMTI_ERROR_FAILS_VERIFICATION,
    JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED,
    JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED,
    JVMTI_ERROR_NAMES_DONT_MATCH,
    JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED,
    JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED;

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native JvmtiError fromValue(int value);
}
