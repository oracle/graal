/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.polyglot.Context;
import org.junit.Test;

/**
 * Test lazy initialization of Graal in the context of Truffle. When simply executing Truffle code,
 * Graal should not be initialized unless there is an actual call targets created.
 */
/*
 * This test is used indirectly by org.graalvm.compiler.truffle.test.LazyInitializationTest.
 */
public class LazyClassLoadingTargetPositiveTest {

    @Test
    public void testInit() {
        Context c = Context.newBuilder().allowExperimentalOptions(true).option("engine.BackgroundCompilation", "false").build();
        c.initialize("sl"); // creates builtin call targets and triggers initialization
        c.close();
    }

}
