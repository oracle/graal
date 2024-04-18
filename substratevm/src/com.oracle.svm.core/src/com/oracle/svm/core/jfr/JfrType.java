/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;

/**
 * Maps JFR types against their IDs in the JDK.
 */
public enum JfrType {
    Class("java.lang.Class"),
    String("java.lang.String"),
    Thread("java.lang.Thread"),
    ThreadState("jdk.types.ThreadState"),
    ThreadGroup("jdk.types.ThreadGroup"),
    StackTrace("jdk.types.StackTrace"),
    ClassLoader("jdk.types.ClassLoader"),
    Method("jdk.types.Method"),
    Symbol("jdk.types.Symbol"),
    Module("jdk.types.Module"),
    Package("jdk.types.Package"),
    FrameType("jdk.types.FrameType"),
    GCCause("jdk.types.GCCause"),
    GCName("jdk.types.GCName"),
    GCWhen("jdk.types.GCWhen"),
    VMOperation("jdk.types.VMOperationType"),
    MonitorInflationCause("jdk.types.InflateCause"),
    OldObject("jdk.types.OldObject"),
    NMTType("jdk.types.NMTType");

    private final long id;

    JfrType(String name) {
        this.id = JfrMetadataTypeLibrary.lookupType(name);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getId() {
        return id;
    }
}
