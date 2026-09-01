/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug.test;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.debug.MethodFilter;

/**
 * Tests {@link MethodFilter} parsing and matching.
 */
public class MethodFilterTest {

    /**
     * Tests that omitting the parameter list matches any number of parameters.
     */
    @Test
    public void testNoParameterList() {
        MethodFilter anyParameters = MethodFilter.parse("method");
        Assert.assertTrue(anyParameters.matchesWithArgs("Class", "method", List.of()));
        Assert.assertTrue(anyParameters.matchesWithArgs("Class", "method", List.of(Object.class)));
    }

    /**
     * Tests that an empty parameter list matches no parameters.
     */
    @Test
    public void testEmptyParameterList() {
        MethodFilter noParameters = MethodFilter.parse("method()");
        Assert.assertTrue(noParameters.matchesWithArgs("Class", "method", List.of()));
        Assert.assertFalse(noParameters.matchesWithArgs("Class", "method", List.of(Object.class)));
    }

    /**
     * Tests that a wildcard parameter matches exactly one parameter of any type.
     */
    @Test
    public void testWildcardParameter() {
        MethodFilter oneWildcardParameter = MethodFilter.parse("method(*)");
        Assert.assertFalse(oneWildcardParameter.matchesWithArgs("Class", "method", List.of()));
        Assert.assertTrue(oneWildcardParameter.matchesWithArgs("Class", "method", List.of(Object.class)));
    }

    /**
     * Tests that empty parameter slots remain wildcards in a delimited parameter list.
     */
    @Test
    public void testEmptyParameterSlots() {
        MethodFilter twoWildcardParameters = MethodFilter.parse("method(;)");
        Assert.assertFalse(twoWildcardParameters.matchesWithArgs("Class", "method", List.of(Object.class)));
        Assert.assertTrue(twoWildcardParameters.matchesWithArgs("Class", "method", List.of(Object.class, String.class)));
    }
}
