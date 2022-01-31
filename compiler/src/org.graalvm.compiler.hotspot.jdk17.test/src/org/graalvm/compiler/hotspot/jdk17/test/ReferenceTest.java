/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.jdk17.test;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.junit.Assert;
import org.junit.Test;

public class ReferenceTest extends GraalCompilerTest {

    static class Entry extends WeakReference<Object> {
        Entry(Object referent) {
            super(referent);
        }
    }

    static boolean snippet1(Entry ref, Object o) {
        return ref.refersTo(o);
    }

    @Test
    public void testReference() {
        Object referent = new Object();
        test("snippet1", new Entry(referent), referent);
        test("snippet1", new Entry(referent), null);
        test("snippet1", new Entry(null), referent);
        test("snippet1", new Entry(null), null);
    }

    static boolean snippet2(PhantomReference<Object> ref, Object o) {
        return ref.refersTo(o);
    }

    @Test
    public void testPhantomReference() {
        Object referent = new Object();
        test("snippet2", new PhantomReference<>(referent, new ReferenceQueue<>()), referent);
        test("snippet2", new PhantomReference<>(referent, new ReferenceQueue<>()), null);
        test("snippet2", new PhantomReference<>(null, new ReferenceQueue<>()), referent);
        test("snippet2", new PhantomReference<>(null, new ReferenceQueue<>()), null);
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        Assert.assertEquals(true, graph.getNodes().filter(InvokeNode.class).isEmpty());
        super.checkHighTierGraph(graph);
    }
}
