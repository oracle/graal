/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub.registry;

import com.oracle.svm.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.svm.espresso.classfile.descriptors.StaticSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;

public final class SVMSymbols {
    // Pre-allocate enough slots to avoid resizing the underlying map.
    // But not too much, since these maps will be persisted in the image heap (Native Image).
    public static final StaticSymbols SYMBOLS = new StaticSymbols(ParserSymbols.SYMBOLS, 1 << 8);

    private SVMSymbols() {
    }

    static {
        SVMTypes.ensureInitialized();
    }

    public static void ensureInitialized() {
        /* nop */
    }

    public static final class SVMTypes {
        public static final Symbol<Type> com_oracle_svm_core_hub_Hybrid = SYMBOLS.putType("Lcom/oracle/svm/core/hub/Hybrid;");

        private SVMTypes() {
        }

        public static void ensureInitialized() {
            /* nop */
        }
    }
}
