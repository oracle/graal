/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.debug;

/**
 * Performs some kind of verification on an object.
 */
public interface DebugVerifyHandler {

    /**
     * Verifies that a given object satisfies some invariants.
     *
     * @param object object to verify
     * @param context object(s) describing the context of verification
     */
    void verify(Object object, Object... context);

    /**
     * Extracts the first object of a given type from a verification input object.
     */
    default <T> T extract(Class<T> type, Object input) {
        if (type.isInstance(input)) {
            return type.cast(input);
        }
        if (input instanceof Object[]) {
            for (Object nestedContext : (Object[]) input) {
                T object = extract(type, nestedContext);
                if (object != null) {
                    return object;
                }
            }
        }
        return null;
    }
}
