/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions
public final class Target_java_lang_Object {
    @Substitution(hasReceiver = true)
    public static int hashCode(@Host(Object.class) StaticObject self) {
        return System.identityHashCode(MetaUtil.maybeUnwrapNull(self));
    }

    @Substitution(hasReceiver = true)
    public static @Host(Class.class) StaticObject getClass(@Host(Object.class) StaticObject self) {
        return self.getKlass().mirror();
    }

    @Substitution(hasReceiver = true, methodName = "<init>")
    public static void init(@Host(Object.class) StaticObject self) {
        assert self.getKlass() instanceof ObjectKlass;
        if (((ObjectKlass) self.getKlass()).hasFinalizer()) {
            registerFinalizer(self);
        }
    }

    @TruffleBoundary
    public static void registerFinalizer(@Host(Object.class) StaticObject self) {
        // TODO(tg): inject meta
        self.getKlass().getMeta().java_lang_ref_Finalizer_register.invokeDirect(null, self);
    }

    // TODO(peterssen): Substitution required, instead of calling native JVM_Clone, to avoid leaking
    // cloned objects. Remove once GR-19247 is resolved.
    @Substitution(hasReceiver = true)
    @Throws(CloneNotSupportedException.class)
    public static @Host(Object.class) StaticObject clone(@Host(Object.class) StaticObject self) {
        return VM.JVM_Clone(self);
    }
}
