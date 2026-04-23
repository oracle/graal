/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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
 * Fixture for constant-field analysis in standalone mode.
 *
 * The test guards that the analysis models the class initializer and the referenced constant field
 * through normal reachability rather than folding the field away as an already computed constant.
 */
public class ConstantFieldCase {
    public static final ConstantType constantField = new ConstantType();
    /*
     * This sink keeps the hash computation side-effectful for the standalone analysis test without
     * adding extra observable behavior to the exercised code path.
     */
    @SuppressWarnings("unused") private static volatile int sink;

    /**
     * Entry point that triggers the call through {@link #constantField}.
     */
    public static void main(String[] args) {
        constantField.foo();
    }

    /**
     * Value type stored in the static final field that is expected to stay visible to analysis.
     */
    public static class ConstantType {
        /**
         * Performs calls whose reachability depends on the constant field being analyzed instead of
         * collapsed away.
         */
        public void foo() {
            consume("first");
            consume("second");
        }
    }

    /**
     * Forces the test constant to execute {@link String#hashCode()} without changing the expected
     * observable behavior of the test case.
     */
    private static void consume(String value) {
        sink ^= value.hashCode();
    }
}
