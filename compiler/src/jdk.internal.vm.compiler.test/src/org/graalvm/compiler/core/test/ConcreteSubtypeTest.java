/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.junit.Test;

import jdk.vm.ci.meta.Assumptions.ConcreteSubtype;

/**
 * Ensure that abstract classes with a single implementor are properly optimized and that loading a
 * subclass below the leaf type triggers invalidation.
 */
public class ConcreteSubtypeTest extends GraalCompilerAssumptionsTest {
    abstract static class AbstractBase {
        abstract void check();
    }

    static class Subclass extends AbstractBase {
        @Override
        public void check() {
            throw new InternalError();
        }
    }

    static class SubSubclass extends Subclass {
        @Override
        public void check() {
        }
    }

    public void callAbstractType(AbstractBase object) {
        object.check();
    }

    /**
     * Test that {@link #callAbstractType} gets compiled into an empty method with a
     * {@link ConcreteSubtype} assumption on {@link AbstractBase} and {@link Subclass}. Then ensures
     * that loading and initialization of {@link SubSubclass} causes the compiled method to be
     * invalidated.
     */
    @Test
    public void testLeafAbstractType() {
        testAssumptionInvalidate("callAbstractType", new ConcreteSubtype(resolveAndInitialize(AbstractBase.class), resolveAndInitialize(Subclass.class)), "SubSubclass");
    }
}
