/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * Fixture for constant propagation and field-folding checks in standalone analysis.
 */
public class StandaloneConstantPropagationCase {

    public static final Singleton INSTANCE = new Singleton();
    public static final Holder HOLDER = new Holder();

    /**
     * Drives the three propagation paths covered by the standalone test.
     */
    public static void main(String[] args) {
        propagateDirect();
        propagateViaReturn();
        propagateViaFieldLoadAndNullCheck();
    }

    /**
     * Propagates the singleton constant directly to the sink.
     */
    public static int propagateDirect() {
        return sink(INSTANCE);
    }

    /**
     * Propagates the singleton constant through a helper return.
     */
    public static int propagateViaReturn() {
        return sink(getInstance());
    }

    /**
     * Propagates the singleton constant through a final instance-field load.
     */
    public static int propagateViaFieldLoadAndNullCheck() {
        Singleton value = HOLDER.value;
        if (value != null) {
            return sink(value);
        }
        return -1;
    }

    private static Singleton getInstance() {
        return INSTANCE;
    }

    private static int sink(Object value) {
        return value.hashCode();
    }

    /**
     * Holder used for propagation through an instance-field load.
     */
    public static final class Holder {
        public final Singleton value = INSTANCE;
    }

    /**
     * Singleton object type propagated through the test scenarios.
     */
    public static final class Singleton {
    }
}
