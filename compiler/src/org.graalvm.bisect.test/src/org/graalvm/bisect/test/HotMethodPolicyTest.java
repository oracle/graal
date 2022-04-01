/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.bisect.test;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.ExecutedMethodImpl;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.ExperimentImpl;
import org.graalvm.bisect.core.HotMethodPolicy;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class HotMethodPolicyTest {
    @Test
    public void testHotMethodPolicy() {
        ExecutedMethod foo1 = new ExecutedMethodImpl("foo1", "foo", List.of(),5);
        ExecutedMethod foo2 = new ExecutedMethodImpl("foo2", "foo", List.of(),35);
        ExecutedMethod foo3 = new ExecutedMethodImpl("foo3", "foo", List.of(),30);
        ExecutedMethod bar1 = new ExecutedMethodImpl("bar1", "bar", List.of(),20);
        ExecutedMethod baz1 = new ExecutedMethodImpl("baz1", "bar", List.of(),10);
        List<ExecutedMethod> methods = List.of(foo1, foo2, foo3, bar1, baz1);
        Experiment experiment = new ExperimentImpl(methods, "1", ExperimentId.ONE, 100, 100);
        HotMethodPolicy hotMethodPolicy = new HotMethodPolicy();
        hotMethodPolicy.markHotMethods(experiment);

        // the test must be adjusted when these parameters are changed
        // TODO create a setter
        assertEquals(1, HotMethodPolicy.HOT_METHOD_MIN_LIMIT);
        assertEquals(10, HotMethodPolicy.HOT_METHOD_MAX_LIMIT);
        assertEquals(0.9, HotMethodPolicy.HOT_METHOD_PERCENTILE, 0.01);

        Set<String> hotMethods = Set.of("foo2", "foo3", "bar1");
        for (ExecutedMethod method : methods) {
            assertEquals(hotMethods.contains(method.getCompilationId()), method.isHot());
        }
    }
}
