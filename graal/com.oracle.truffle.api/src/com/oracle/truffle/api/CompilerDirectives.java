/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.util.concurrent.*;

/**
 * Directives that influence the optimizations of the Truffle compiler. All of the operations have
 * no effect when executed in the Truffle interpreter.
 */
public class CompilerDirectives {

    private static final double SLOWPATH_PROBABILITY = 0.0001;

    /**
     * Directive for the compiler to discontinue compilation at this code position and instead
     * insert a transfer to the interpreter.
     */
    public static void transferToInterpreter() {
    }

    /**
     * Directive for the compiler that the given runnable should only be executed in the interpreter
     * and ignored in the compiled code.
     * 
     * @param runnable the closure that should only be executed in the interpreter
     */
    public static void interpreterOnly(Runnable runnable) {
        runnable.run();
    }

    /**
     * Directive for the compiler that the given callable should only be executed in the
     * interpreter.
     * 
     * @param callable the closure that should only be executed in the interpreter
     * @return the result of executing the closure in the interpreter and null in the compiled code
     * @throws Exception If the closure throws an exception when executed in the interpreter.
     */
    public static <T> T interpreterOnly(Callable<T> callable) throws Exception {
        return callable.call();
    }

    /**
     * Directive for the compiler that the current path has a very low probability to be executed.
     */
    public static void slowpath() {
        injectBranchProbability(SLOWPATH_PROBABILITY);
    }

    /**
     * Injects a probability for the current path into the probability information of the
     * immediately preceeding branch instruction.
     * 
     * @param probability the probability value between 0.0 and 1.0 that should be injected
     */
    public static void injectBranchProbability(double probability) {
        assert probability >= 0.0 && probability <= 1.0;
    }

    /**
     * Bails out of a compilation (e.g., for guest language features that should never be compiled).
     * 
     * @param reason the reason for the bailout
     */
    public static void bailout(String reason) {
    }
}
