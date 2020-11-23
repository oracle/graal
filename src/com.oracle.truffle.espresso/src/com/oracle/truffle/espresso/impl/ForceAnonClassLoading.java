/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class ForceAnonClassLoading {

    private static final ThreadLocal<AnonClassLoading> ANON_CLASS_LOADING = new ThreadLocal<>();

    public static void mark(String name, StaticObject definingLoader) {
        ANON_CLASS_LOADING.set(new AnonClassLoading(name, definingLoader));
    }

    @TruffleBoundary
    public static boolean isMarked(String name, StaticObject definingLoader) {
        AnonClassLoading anonClassLoading = ANON_CLASS_LOADING.get();
        if (anonClassLoading != null) {
            return name.equals(anonClassLoading.getName()) && definingLoader == anonClassLoading.getDefiningLoader();
        }
        return false;
    }

    public static void clear() {
        ANON_CLASS_LOADING.remove();
    }

    public static RuntimeException throwing(byte[] bytes) {
        return new BlockDefiningClassException(bytes);
    }

    private static class AnonClassLoading {
        private String name;
        private StaticObject definingLoader;

        AnonClassLoading(String name, StaticObject definingLoader) {
            this.name = name;
            this.definingLoader = definingLoader;
        }

        public String getName() {
            return name;
        }

        public StaticObject getDefiningLoader() {
            return definingLoader;
        }
    }

    static final class BlockDefiningClassException extends RuntimeException {
        private static final long serialVersionUID = -3567983395371909541L;
        private byte[] bytes;

        private BlockDefiningClassException(byte[] bytes) {
            this.bytes = bytes;
        }

        public byte[] getBytes() {
            return this.bytes;
        }
    }
}
