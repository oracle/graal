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
package com.oracle.truffle.espresso.io;

import java.nio.channels.Channel;

import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

public class Checks {
    public static @JavaType(Object.class) StaticObject nullCheck(@JavaType(Object.class) StaticObject ref, ContextAccess contextAccess) {
        return nullCheck(ref, contextAccess.getMeta());
    }

    public static @JavaType(Object.class) StaticObject nullCheck(@JavaType(Object.class) StaticObject ref, EspressoContext context) {
        return nullCheck(ref, context.getMeta());
    }

    public static @JavaType(Object.class) StaticObject nullCheck(@JavaType(Object.class) StaticObject ref, Meta meta) {
        if (StaticObject.isNull(ref)) {
            throw meta.throwNullPointerExceptionBoundary();
        }
        return ref;
    }

    public static @JavaType(Object.class) StaticObject requireForeign(@JavaType(Object.class) StaticObject ref, String message, EspressoContext context) {
        if (!ref.isForeignObject()) {
            Meta meta = context.getMeta();
            throw meta.throwIllegalArgumentExceptionBoundary(message);
        }
        return ref;
    }

    public static @JavaType(Object.class) StaticObject requireForeign(@JavaType(Object.class) StaticObject ref, EspressoContext context) {
        return requireForeign(ref, "foreign object expected", context);
    }

    public static @JavaType(Object.class) StaticObject requireNonForeign(@JavaType(Object.class) StaticObject ref, String message, EspressoContext context) {
        if (ref.isForeignObject()) {
            Meta meta = context.getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, message);
        }
        return ref;
    }

    public static @JavaType(Object.class) StaticObject requireNonForeign(@JavaType(Object.class) StaticObject ref, EspressoContext context) {
        return requireNonForeign(ref, "foreign object", context);
    }

    public static Channel ensureOpen(Channel channel, EspressoContext context) {
        if (channel == null || !channel.isOpen()) {
            throw context.getMeta().throwExceptionWithMessage(context.getTruffleIO().java_io_IOException, "Stream closed");
        }
        return channel;
    }
}
