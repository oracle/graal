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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions(value = TimeZone.class, group = LibJava.class)
public final class Target_java_util_TimeZone {
    @Substitution
    @TruffleBoundary
    @SuppressWarnings("unused")
    public static @JavaType(String.class) StaticObject getSystemTimeZoneID(@JavaType(String.class) StaticObject javaHome, @Inject Meta meta, @Inject EspressoContext context) {
        return meta.toGuestString(context.getEnv().getTimeZone().getId());
    }

    @Substitution
    @TruffleBoundary
    public static @JavaType(String.class) StaticObject getSystemGMTOffsetID(@Inject Meta meta, @Inject EspressoContext context) {
        ZoneId zone = context.getEnv().getTimeZone();
        String offsetId;
        if (zone instanceof ZoneOffset) {
            offsetId = zone.getId();
        } else {
            ZoneOffset offset = zone.getRules().getOffset(Instant.now());
            offsetId = offset.getId();
        }
        return meta.toGuestString(offsetId);

    }
}
