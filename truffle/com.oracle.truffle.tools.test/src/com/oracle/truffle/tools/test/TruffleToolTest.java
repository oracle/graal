/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.test;

import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.instrument.*;

/**
 * Test the basic life cycle properties shared by all instances of {@link InstrumentationTool}.
 */
public class TruffleToolTest {

    @Test
    public void testEmptyLifeCycle() {
        final DummyTruffleTool tool = new DummyTruffleTool();
        assertFalse(tool.isEnabled());
        tool.install();
        assertTrue(tool.isEnabled());
        tool.reset();
        assertTrue(tool.isEnabled());
        tool.setEnabled(false);
        assertFalse(tool.isEnabled());
        tool.reset();
        assertFalse(tool.isEnabled());
        tool.setEnabled(true);
        assertTrue(tool.isEnabled());
        tool.reset();
        assertTrue(tool.isEnabled());
        tool.dispose();
        assertFalse(tool.isEnabled());
    }

    @Test(expected = IllegalStateException.class)
    public void testNotYetInstalled1() {
        final DummyTruffleTool tool = new DummyTruffleTool();
        tool.reset();
    }

    @Test(expected = IllegalStateException.class)
    public void testNotYetInstalled2() {
        final DummyTruffleTool tool = new DummyTruffleTool();
        tool.setEnabled(true);
    }

    @Test(expected = IllegalStateException.class)
    public void testNotYetInstalled3() {
        final DummyTruffleTool tool = new DummyTruffleTool();
        tool.dispose();
    }

    @Test(expected = IllegalStateException.class)
    public void testAlreadyInstalled() {
        final DummyTruffleTool tool = new DummyTruffleTool();
        tool.install();
        tool.install();
    }

    @Test(expected = IllegalStateException.class)
    public void testAlreadyDisposed1() {
        final DummyTruffleTool tool = new DummyTruffleTool();
        tool.install();
        tool.dispose();
        tool.install();
    }

    @Test(expected = IllegalStateException.class)
    public void testAlreadyDisposed2() {
        final DummyTruffleTool tool = new DummyTruffleTool();
        tool.install();
        tool.dispose();
        tool.reset();
    }

    @Test(expected = IllegalStateException.class)
    public void testAlreadyDisposed3() {
        final DummyTruffleTool tool = new DummyTruffleTool();
        tool.install();
        tool.dispose();
        tool.setEnabled(true);
    }

    @Test(expected = IllegalStateException.class)
    public void testAlreadyDisposed4() {
        final DummyTruffleTool tool = new DummyTruffleTool();
        tool.install();
        tool.dispose();
        tool.dispose();
    }

    private static final class DummyTruffleTool extends InstrumentationTool {

        @Override
        protected boolean internalInstall() {
            return true;
        }

        @Override
        protected void internalReset() {
        }

        @Override
        protected void internalDispose() {
        }

    }
}
