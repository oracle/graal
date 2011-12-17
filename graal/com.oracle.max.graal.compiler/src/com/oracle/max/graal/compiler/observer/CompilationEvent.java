/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.observer;

/**
 * An event that occurred during compilation. Instances of this class provide information about the event and the state
 * of the compilation when the event was raised. Depending on the state of the compiler and the compilation phase,
 * different types of objects are provided in the {@link #debugObjects}. The observer should filter events that it is
 * interested in by checking if an object of a specific type is provided by the event.
 */
public class CompilationEvent {

    /**
     * Marker object for the {@link #debugObject} array: When this object is present, the event is the result of a compilation error.
     */
    public static final Object ERROR = new Object() {};

    public final String label;
    private Object[] debugObjects;

    protected CompilationEvent(String label, Object...debugObjects) {
        this.label = label;
        this.debugObjects = debugObjects;
    }

    @SuppressWarnings("unchecked")
    public <T> T debugObject(Class<T> type) {
        for (Object o : debugObjects) {
            if (type.isInstance(o)) {
                return (T) o;
            }
        }
        return null;
    }

    public boolean hasDebugObject(Object search) {
        for (Object o : debugObjects) {
            if (o == search) {
                return true;
            }
        }
        return false;
    }
}
