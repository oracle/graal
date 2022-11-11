/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.preinit;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.impl.ClassRegistry;

public abstract class AbstractCachedKlassProvider {
    private final TruffleLogger logger;

    protected AbstractCachedKlassProvider(TruffleLogger logger) {
        this.logger = logger;
    }

    public TruffleLogger getLogger() {
        return logger;
    }

    protected static boolean shouldCacheClass(ClassRegistry.ClassDefinitionInfo info) {
        /*
         * Cached class representations must not contain context-dependent objects that cannot be
         * shared on a language level. Anonymous classes, by definition, contain a Klass
         * self-reference in the constant pool.
         */
        return !info.isAnonymousClass() && !info.isHidden();
    }
}
