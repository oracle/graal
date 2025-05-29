/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.continuations;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions
public final class Target_org_graalvm_continuations_IdentityHashCodes {
    public static int getIHashCode(@JavaType(Object.class) StaticObject o, Meta meta, EspressoLanguage lang) {
        assert lang.isContinuumEnabled();
        if (StaticObject.isNull(o)) {
            return 0;
        }
        int hashcode = getHashCode(o, meta, lang);
        if (hashcode > 0) {
            return hashcode;
        }
        hashcode = System.identityHashCode(o);
        assert hashcode > 0;
        // Atomic update, will return the actual value if it is already set.
        return setHashCode(o, hashcode, meta, lang);
    }

    @Substitution
    public static boolean isInitialized0(@JavaType(Object.class) StaticObject o,
                    @Inject Meta meta,
                    @Inject EspressoLanguage lang) {
        if (!lang.isContinuumEnabled()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, "Continuations was not enabled.");
        }
        if (StaticObject.isNull(o)) {
            throw meta.throwNullPointerException();
        }
        assert meta.continuum != null;
        return getHashCode(o, meta, lang) > 0;
    }

    @Substitution
    public static boolean setIHashcode0(@JavaType(Object.class) StaticObject o, int hashcode,
                    @Inject Meta meta,
                    @Inject EspressoLanguage lang) {
        if (!meta.getLanguage().isContinuumEnabled()) {
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, "Continuations was not enabled.");
        }
        if (StaticObject.isNull(o)) {
            throw meta.throwNullPointerException();
        }
        if (hashcode <= 0) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, EspressoError.cat("Setting hashcode <= 0: ", hashcode));
        }
        assert meta.continuum != null;
        return setHashCode(o, hashcode, meta, lang) == hashcode;
    }

    private static int setHashCode(StaticObject o, int hashcode, Meta meta, EspressoLanguage language) {
        assert !StaticObject.isNull(o) && hashcode > 0;
        if (o.isArray()) {
            language.getArrayHashCodeProperty().compareAndExchangeInt(o, 0, hashcode);
        } else {
            meta.HIDDEN_SYSTEM_IHASHCODE.compareAndExchangeInt(o, 0, hashcode);
        }
        return getHashCode(o, meta, language);
    }

    private static int getHashCode(StaticObject o, Meta meta, EspressoLanguage language) {
        return o.isArray()
                        ? language.getArrayHashCodeProperty().getInt(o)
                        : meta.HIDDEN_SYSTEM_IHASHCODE.getInt(o);
    }
}
