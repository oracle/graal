/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.debug;

/**
 * The EventKind from the JDWP protocol. There is a bridge copy
 * <code>com.oracle.svm.jdwp.bridge.EventKind</code>, the enums are exchanged via ordinal number
 * ({@link EventKind#ordinal()} and {@link EventKind#fromOrdinal(int)}).
 */
public enum EventKind {

    SINGLE_STEP(1),
    BREAKPOINT(2),
    FRAME_POP(3),
    EXCEPTION(4),
    USER_DEFINED(5),
    THREAD_START(6),
    THREAD_DEATH(7),
    CLASS_PREPARE(8),
    CLASS_UNLOAD(9),
    CLASS_LOAD(10),
    FIELD_ACCESS(20),
    FIELD_MODIFICATION(21),
    EXCEPTION_CATCH(30),
    METHOD_ENTRY(40),
    METHOD_EXIT(41),
    METHOD_EXIT_WITH_RETURN_VALUE(42),
    MONITOR_CONTENDED_ENTER(43),
    MONITOR_CONTENDED_ENTERED(44),
    MONITOR_WAIT(45),
    MONITOR_WAITED(46),
    VM_START(90),
    VM_DEATH(99),
    VM_DISCONNECTED(100);

    EventKind(int id) {
        assert 0 < id && id < 127 : id;
    }

    private static final EventKind[] VALUES = EventKind.values();

    public static EventKind fromOrdinal(int ordinal) {
        return VALUES[ordinal];
    }

    /**
     * Flag of the event used by
     * {@link EventHandler#onEventAt(Thread, jdk.vm.ci.meta.ResolvedJavaMethod, int, Object, int)}.
     * The only events on the same thread and at the same location that can be combined to bit flags
     * are: {@link #BREAKPOINT}, {@link #SINGLE_STEP}, {@link #METHOD_ENTRY}, {@link #METHOD_EXIT},
     * {@link #METHOD_EXIT_WITH_RETURN_VALUE}
     */
    public int getFlag() {
        assert ordinal() < 32 : "Flag overflow, ordinal = " + ordinal();
        int flag = 1 << ordinal();
        return flag;
    }
}
