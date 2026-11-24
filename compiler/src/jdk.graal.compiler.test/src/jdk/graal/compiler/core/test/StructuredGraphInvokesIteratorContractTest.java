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
package jdk.graal.compiler.core.test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;

/// Checks that StructuredGraph.getInvokes returns a conforming iterator.
public class StructuredGraphInvokesIteratorContractTest extends GraalCompilerTest {
    static native int callee(int value);

    static int caller(int value) {
        return callee(value);
    }

    private StructuredGraph graphWithInvoke() {
        StructuredGraph graph = parseEager(getResolvedJavaMethod("caller"), AllowAssumptions.NO);
        Assert.assertTrue(graph.getInvokes().iterator().hasNext());
        return graph;
    }

    @Test
    public void nextReturnsFirstInvokeWithoutPriorHasNext() {
        Iterator<Invoke> iterator = graphWithInvoke().getInvokes().iterator();
        Assert.assertNotNull("next() is valid without a preceding hasNext()", iterator.next());
    }

    @Test
    public void nextAfterExhaustionThrowsNoSuchElementException() {
        Iterator<Invoke> iterator = graphWithInvoke().getInvokes().iterator();
        while (iterator.hasNext()) {
            Assert.assertNotNull(iterator.next());
        }
        Assert.assertFalse(iterator.hasNext());
        Assert.assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    public void emptyIteratorThrowsNoSuchElementExceptionWithoutPriorHasNext() {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        Assert.assertThrows(NoSuchElementException.class, graph.getInvokes().iterator()::next);
    }

}
