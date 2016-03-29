/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import com.oracle.truffle.object.basic.BasicLayout;
import org.junit.After;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import org.junit.Before;

import org.junit.Test;

public class FindEngineNodeImplTest {
    @SuppressWarnings("unused") private static final Class<?> basicLayout = BasicLayout.class;

    private FindEngineNodeImpl node;
    private PolyglotEngine another;
    private PolyglotEngine engine;

    @Before
    public void initNode() {
        node = new FindEngineNodeImpl();
    }

    @After
    public void disposeEngines() {
        if (engine != null) {
            engine.dispose();
        }
        if (another != null) {
            another.dispose();
        }
    }

    @Test
    public void cantRegisterTwoEnginesFromASingleThread() {
        Thread myThread = Thread.currentThread();
        engine = PolyglotEngine.newBuilder().build();
        node.registerEngine(myThread, engine);
        node.registerEngine(myThread, engine);
        assertSame("1st engine registered", engine, node.findEngine());

        another = PolyglotEngine.newBuilder().build();
        try {
            node.registerEngine(myThread, another);
            fail("Second registration shall not succeed");
        } catch (IllegalStateException ex) {
            // OK
        }
        assertSame("1st engine still registered", engine, node.findEngine());

        node.disposeEngine(myThread, engine);

        node.registerEngine(myThread, another);
        assertSame("2nd engine still registered", another, node.findEngine());
    }

    @Test
    public void moreEnginesForDifferentThreads() {
        Thread myThread = Thread.currentThread();
        Thread anotherThread = new Thread();
        engine = PolyglotEngine.newBuilder().build();
        another = PolyglotEngine.newBuilder().build();

        node.registerEngine(myThread, engine);
        assertSame("1st engine registered", engine, node.findEngine());

        node.registerEngine(anotherThread, another);
        assertSame("1st engine still registered", engine, node.findEngine(myThread));
        assertSame("2nd engine registered in other thread", another, node.findEngine(anotherThread));

        node.registerEngine(anotherThread, another);
        assertSame("2nd engine still registered in other thread", another, node.findEngine(anotherThread));
    }
}
