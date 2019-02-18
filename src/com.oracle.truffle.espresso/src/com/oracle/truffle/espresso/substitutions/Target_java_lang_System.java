/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.object.DebugCounter;

@EspressoSubstitutions
public final class Target_java_lang_System {

    static final DebugCounter arraycopyCount = DebugCounter.create("arraycopyCount");
    static final DebugCounter identityHashCodeCount = DebugCounter.create("identityHashCodeCount");

    @Substitution
    public static int identityHashCode(@Host(Object.class) StaticObject self) {
        identityHashCodeCount.inc();
        return System.identityHashCode(MetaUtil.maybeUnwrapNull(self));
    }

    @Substitution
    public static void arraycopy(@Host(Object.class) StaticObject src, int srcPos, @Host(Object.class) StaticObject dest, int destPos, int length) {
        arraycopyCount.inc();
        try {
            if (src instanceof StaticObjectArray && dest instanceof StaticObjectArray) {
                System.arraycopy(((StaticObjectArray) src).unwrap(), srcPos, ((StaticObjectArray) dest).unwrap(), destPos, length);
            } else {
                assert src.getClass().isArray();
                assert dest.getClass().isArray();
                System.arraycopy(src, srcPos, dest, destPos, length);
            }
        } catch (Exception e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwExWithMessage(e.getClass(), e.getMessage());
        }
    }
}
