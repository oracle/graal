/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import com.oracle.graal.api.directives.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

@ClassSubstitution(GraalDirectives.class)
public class GraalDirectivesSubstitutions {

    @MethodSubstitution(forced = true)
    public static void deoptimize() {
        DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter);
    }

    @MethodSubstitution(forced = true)
    public static void deoptimizeAndInvalidate() {
        DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter);
    }

    @MethodSubstitution(forced = true)
    public static boolean inCompiledCode() {
        return true;
    }

    @MacroSubstitution(forced = true, macro = ControlFlowAnchorNode.class)
    public static native void controlFlowAnchor();

    @MethodSubstitution(forced = true)
    public static boolean injectBranchProbability(double probability, boolean condition) {
        return BranchProbabilityNode.probability(probability, condition);
    }

    @MethodSubstitution(forced = true)
    public static void blackhole(boolean value) {
        BlackholeNode.consume(value);
    }

    @MethodSubstitution(forced = true)
    public static void blackhole(byte value) {
        BlackholeNode.consume(value);
    }

    @MethodSubstitution(forced = true)
    public static void blackhole(short value) {
        BlackholeNode.consume(value);
    }

    @MethodSubstitution(forced = true)
    public static void blackhole(char value) {
        BlackholeNode.consume(value);
    }

    @MethodSubstitution(forced = true)
    public static void blackhole(int value) {
        BlackholeNode.consume(value);
    }

    @MethodSubstitution(forced = true)
    public static void blackhole(long value) {
        BlackholeNode.consume(value);
    }

    @MethodSubstitution(forced = true)
    public static void blackhole(float value) {
        BlackholeNode.consume(value);
    }

    @MethodSubstitution(forced = true)
    public static void blackhole(double value) {
        BlackholeNode.consume(value);
    }

    @MethodSubstitution(forced = true)
    public static void blackhole(Object value) {
        BlackholeNode.consume(value);
    }

    @MethodSubstitution(forced = true)
    public static boolean opaque(boolean value) {
        return OpaqueNode.opaque(value);
    }

    @MethodSubstitution(forced = true)
    public static byte opaque(byte value) {
        return OpaqueNode.opaque(value);
    }

    @MethodSubstitution(forced = true)
    public static short opaque(short value) {
        return OpaqueNode.opaque(value);
    }

    @MethodSubstitution(forced = true)
    public static char opaque(char value) {
        return OpaqueNode.opaque(value);
    }

    @MethodSubstitution(forced = true)
    public static int opaque(int value) {
        return OpaqueNode.opaque(value);
    }

    @MethodSubstitution(forced = true)
    public static long opaque(long value) {
        return OpaqueNode.opaque(value);
    }

    @MethodSubstitution(forced = true)
    public static float opaque(float value) {
        return OpaqueNode.opaque(value);
    }

    @MethodSubstitution(forced = true)
    public static double opaque(double value) {
        return OpaqueNode.opaque(value);
    }

    @MethodSubstitution(forced = true)
    public static <T> T opaque(T value) {
        return OpaqueNode.opaque(value);
    }
}
