/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.impl.InternalPlatform;

//Checkstyle: stop

/**
 * Definitions for Hotspot JVM internal functions
 *
 * We declare an initialize function in order to ensure that the jvm lib is on the link line. This
 * allows the core library dependencies on JVM_ functions to be satisfied by our jvm library
 * (jvm.lib or libjvm.a).
 *
 */
@Platforms(InternalPlatform.PLATFORM_JNI.class)
@CLibrary(value = "jvm", requireStatic = true)
public class Jvm {

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native void initialize();

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int JVM_ActiveProcessorCount();

    @Platforms(Platform.WINDOWS.class)
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native VoidPointer JVM_RegisterSignal(int sig, VoidPointer handler);
}
