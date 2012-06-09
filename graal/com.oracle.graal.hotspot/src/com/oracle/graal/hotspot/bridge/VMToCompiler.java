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
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.hotspot.ri.*;

/**
 * Calls from HotSpot into Java.
 */
public interface VMToCompiler {

    boolean compileMethod(HotSpotMethodResolved method, int entryBCI, boolean blocking, int priority) throws Throwable;

    void shutdownCompiler() throws Throwable;

    void startCompiler() throws Throwable;

    void bootstrap() throws Throwable;

    PrintStream log();

    JavaMethod createRiMethodUnresolved(String name, String signature, JavaType holder);

    Signature createRiSignature(String signature);

    JavaField createRiField(JavaType holder, String name, JavaType type, int offset, int flags);

    JavaType createRiType(HotSpotConstantPool pool, String name);

    JavaType createRiTypePrimitive(int basicType);

    JavaType createRiTypeUnresolved(String name);

    Constant createCiConstant(Kind kind, long value);

    Constant createCiConstantFloat(float value);

    Constant createCiConstantDouble(double value);

    Constant createCiConstantObject(Object object);

    PhasePlan createPhasePlan(OptimisticOptimizations optimisticOpts);
}
