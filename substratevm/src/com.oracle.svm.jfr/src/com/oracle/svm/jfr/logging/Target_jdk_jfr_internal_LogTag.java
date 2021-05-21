/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.jfr.logging;

import com.oracle.svm.jfr.JfrEnabled;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(value = jdk.jfr.internal.LogTag.class, onlyWith = JfrEnabled.class)
final class Target_jdk_jfr_internal_LogTag {
    //Checkstyle: stop field name check
    @Alias static Target_jdk_jfr_internal_LogTag JFR;
    @Alias static Target_jdk_jfr_internal_LogTag JFR_SYSTEM;
    @Alias static Target_jdk_jfr_internal_LogTag JFR_SYSTEM_EVENT;
    @Alias static Target_jdk_jfr_internal_LogTag JFR_SYSTEM_SETTING;
    @Alias static Target_jdk_jfr_internal_LogTag JFR_SYSTEM_BYTECODE;
    @Alias static Target_jdk_jfr_internal_LogTag JFR_SYSTEM_PARSER;
    @Alias static Target_jdk_jfr_internal_LogTag JFR_SYSTEM_METADATA;
    @Alias static Target_jdk_jfr_internal_LogTag JFR_METADATA;
    @Alias static Target_jdk_jfr_internal_LogTag JFR_EVENT;
    @Alias static Target_jdk_jfr_internal_LogTag JFR_SETTING;
    @Alias static Target_jdk_jfr_internal_LogTag JFR_DCMD;
    //Checkstyle: resume field name check

    @Alias volatile int tagSetLevel;
    @Alias int id;

    @Alias
    static native Target_jdk_jfr_internal_LogTag[] values();
}
