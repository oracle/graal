/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

import com.oracle.svm.shared.util.BasedOnJDKFile;

/**
 * Describes the legacy JDK 1.1 initialization arguments that HotSpot still accepts for
 * {@code JNI_GetDefaultJavaVMInitArgs}.
 */
@CContext(JNIHeaderDirectives.class)
@CStruct(value = "JDK1_1InitArgs")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+22/src/hotspot/share/include/jvm.h#L1141-L1169")
public interface JNIJavaVMInitArgsJDK1_1 extends PointerBase {
    /** Gets the JNI version field. */
    @CField("version")
    int getVersion();

    /** Sets the JNI version field to {@code version}. */
    @CField("version")
    void setVersion(int version);

    /** Gets the Java stack size reported to the launcher. */
    @CField("javaStackSize")
    int getJavaStackSize();

    /** Sets the Java stack size reported to the launcher to {@code stackSize}. */
    @CField("javaStackSize")
    void setJavaStackSize(int stackSize);
}
