/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.util.test;

import org.graalvm.compiler.test.AddModules;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link AddModules} annotation.
 */
@AddModules("jdk.incubator.vector")
public class AddModulesTest {

    @Test
    public void testVector() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        /*
         * Load a value from a jdk.incubator.vector class. Do this via reflection to avoid a
         * build-time dependency on the jdk.incubator.vector module. When the Vector API comes out
         * of incubation, this will have to be replaced by some other incubator module.
         */
        Class<?> intVector = Class.forName("jdk.incubator.vector.IntVector");
        Object species128 = intVector.getDeclaredField("SPECIES_128").get(intVector);
        Assert.assertEquals("IntSpecies", species128.getClass().getSimpleName());
    }
}
