/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.PointerBase;

/**
 * Stores information about a JVMTI environment. JVMTI environments and their lifecycle are managed
 * by {@link JvmtiEnvs}.
 */
@RawStructure
public interface JvmtiEnv extends PointerBase {
    @RawField
    Isolate getIsolate();

    @RawField
    void setIsolate(Isolate value);

    @RawField
    int getMagic();

    @RawField
    void setMagic(int value);

    @RawField
    JvmtiEnv getNext();

    @RawField
    void setNext(JvmtiEnv env);

    @RawField
    long getEventUserEnabled();

    @RawField
    void setEventUserEnabled(long userEnabled);

    // The annotation-based fields above are incomplete because we directly embed other structures
    // into this raw struct, see below:
    //
    // Internal data
    // -------------
    // JvmtiCapabilities capabilities;
    // JvmtiEventCallbacks callbacks;
    //
    // Externally visible data (must be at the end of this struct)
    // -----------------------------------------------------------
    // JvmtiExternalEnv externalEnv;
}
