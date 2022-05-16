/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_hotswap_HotSwapHandler {

    @Substitution
    static boolean registerHandler(@JavaType(Object.class) StaticObject handler, @Inject Meta meta) {
        assert handler != null;
        if (meta.getContext().getEspressoEnv().JDWPOptions == null) {
            // only allow HotSwap handler registration when running in debug mode
            return false;
        }

        try {
            meta.getContext().registerExternalHotSwapHandler(handler);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return true;
    }
}
