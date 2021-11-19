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
package org.graalvm.nativebridge;

import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.word.WordFactory;

public final class NativeObjectHandles {

    private NativeObjectHandles() {
    }

    public static long create(Object object) {
        return ObjectHandles.getGlobal().create(object).rawValue();
    }

    public static <T> T resolve(long handle, Class<T> type) {
        try {
            return type.cast(ObjectHandles.getGlobal().get(WordFactory.pointer(handle)));
        } catch (IllegalArgumentException iae) {
            throw new InvalidHandleException(iae);
        }
    }

    public static void remove(long handle) {
        try {
            ObjectHandles.getGlobal().destroy(WordFactory.pointer(handle));
        } catch (IllegalArgumentException iae) {
            throw new InvalidHandleException(iae);
        }
    }

    public static final class InvalidHandleException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        InvalidHandleException(IllegalArgumentException cause) {
            super(cause.getMessage(), cause);
            setStackTrace(cause.getStackTrace());
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
