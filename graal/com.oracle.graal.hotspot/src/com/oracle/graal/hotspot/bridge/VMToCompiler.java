/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.bridge;

import java.io.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.phases.*;

/**
 * Calls from HotSpot into Java.
 */
public interface VMToCompiler {

    boolean compileMethod(HotSpotResolvedJavaMethod method, int entryBCI, boolean blocking, int priority) throws Throwable;

    void shutdownCompiler() throws Throwable;

    void startCompiler() throws Throwable;

    void bootstrap() throws Throwable;

    PrintStream log();

    JavaMethod createJavaMethod(String name, String signature, JavaType holder);

    Signature createSignature(String signature);

    JavaField createJavaField(JavaType holder, String name, JavaType type, int offset, int flags);

    JavaType createPrimitiveJavaType(int basicType);

    JavaType createJavaType(String name);

    Constant createConstant(Kind kind, long value);

    Constant createConstantFloat(float value);

    Constant createConstantDouble(double value);

    Constant createConstantObject(Object object);

    PhasePlan createPhasePlan(OptimisticOptimizations optimisticOpts);
}
