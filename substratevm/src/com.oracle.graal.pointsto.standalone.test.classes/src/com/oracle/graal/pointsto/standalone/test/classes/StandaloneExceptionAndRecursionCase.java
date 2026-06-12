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

package com.oracle.graal.pointsto.standalone.test.classes;

/**
 * Fixture that combines exception edges with a small recursive helper.
 */
public class StandaloneExceptionAndRecursionCase {

    public static Result exceptionResult;
    public static Result recursionResult;

    /**
     * Drives both the exception and recursion scenarios.
     */
    public static void main(String[] args) {
        exceptionResult = handle(true);
        exceptionResult = handle(false);
        recursionResult = recurse(3);
    }

    /**
     * Returns either a normal value or the caught exception instance.
     */
    public static Result handle(boolean fail) {
        try {
            if (fail) {
                throw new Failure();
            }
            return new Success();
        } catch (Failure failure) {
            return failure;
        }
    }

    /**
     * Small recursive helper used to keep the recursion edge visible to analysis.
     */
    public static Result recurse(int depth) {
        if (depth == 0) {
            return new Success();
        }
        return recurse(depth - 1);
    }

    /**
     * Common result supertype.
     */
    public interface Result {
    }

    /**
     * Normal result type.
     */
    public static final class Success implements Result {
    }

    /**
     * Exception type that is also returned from the catch block.
     */
    public static final class Failure extends RuntimeException implements Result {
        private static final long serialVersionUID = 1L;
    }
}
