/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.libjava.impl;

import java.nio.charset.Charset;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.libs.JNU;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions(type = "Ljava/lang/ProcessEnvironment;", group = LibJava.class)
public final class Target_java_lang_ProcessEnvironment {
    @Substitution
    @TruffleBoundary
    public static @JavaType(byte[][].class) StaticObject environ(@Inject EspressoContext ctx, @Inject JNU jnu) {
        Charset charSet = jnu.getCharSet();
        Map<String, String> truffleEnvironment = ctx.getEnv().getEnvironment();
        int size = truffleEnvironment.size();
        StaticObject[] environ = new StaticObject[size * 2];

        int i = 0;
        for (Map.Entry<String, String> entry : truffleEnvironment.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            byte[] keyBytes = key.getBytes(charSet);
            byte[] valueBytes = value.getBytes(charSet);
            environ[i * 2] = ctx.getAllocator().wrapArrayAs(ctx.getMeta()._byte_array, keyBytes);
            environ[i * 2 + 1] = ctx.getAllocator().wrapArrayAs(ctx.getMeta()._byte_array, valueBytes);
            i++;
        }

        return ctx.getAllocator().wrapArrayAs(ctx.getMeta()._byte_array.array(), environ);
    }
}
