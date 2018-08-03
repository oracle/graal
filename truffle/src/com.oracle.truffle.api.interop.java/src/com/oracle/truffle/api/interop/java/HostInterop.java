/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.TruffleObject;

final class HostInterop {

    private HostInterop() {
    }

    static TruffleObject asTruffleObject(Object obj, Object languageContext) {
        return asTruffleObject(obj, languageContext, false);
    }

    /**
     * Exports a Java object for use in any {@link TruffleLanguage}.
     *
     * @param obj a Java object to convert into one suitable for <em>Truffle</em> languages
     * @return converted object
     */
    static TruffleObject asTruffleObject(Object obj, Object languageContext, boolean asStaticClass) {
        if (obj instanceof TruffleObject) {
            return ((TruffleObject) obj);
        } else if (obj instanceof Class) {
            if (asStaticClass) {
                return HostObject.forStaticClass((Class<?>) obj, languageContext);
            } else {
                return HostObject.forClass((Class<?>) obj, languageContext);
            }
        } else if (obj == null) {
            return HostObject.NULL;
        } else if (obj.getClass().isArray()) {
            return HostObject.forObject(obj, languageContext);
        } else if (obj instanceof PolyglotList) {
            return ((PolyglotList<?>) obj).guestObject;
        } else if (obj instanceof PolyglotMap) {
            return ((PolyglotMap<?, ?>) obj).guestObject;
        } else if (obj instanceof PolyglotFunction) {
            return ((PolyglotFunction<?, ?>) obj).guestObject;
        } else if (TruffleOptions.AOT) {
            return HostObject.forObject(obj, languageContext);
        } else {
            return HostInteropReflect.asTruffleViaReflection(obj, languageContext);
        }
    }

    static boolean isPrimitive(Object obj) {
        if (obj instanceof TruffleObject) {
            // Someone tried to pass a TruffleObject in
            return false;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean ||
                        obj instanceof Byte ||
                        obj instanceof Short ||
                        obj instanceof Integer ||
                        obj instanceof Long ||
                        obj instanceof Float ||
                        obj instanceof Double ||
                        obj instanceof Character ||
                        obj instanceof String) {
            return true;
        }
        return false;
    }

    static Value toHostValue(Object obj, Object languageContext) {
        return HostInteropAccessor.ACCESSOR.engine().toHostValue(obj, languageContext);
    }

    static Object toGuestValue(Object obj, Object languageContext) {
        if (isPrimitive(obj)) {
            return obj;
        }
        return toGuestObject(obj, languageContext);
    }

    static Object toGuestObject(Object obj, Object languageContext) {
        assert !isPrimitive(obj);
        EngineSupport engine = HostInteropAccessor.ACCESSOR.engine();
        assert engine != null;
        assert languageContext != null;
        return engine.toGuestValue(obj, languageContext);
    }

    static Throwable wrapHostException(Object languageContext, Throwable exception) {
        if (exception instanceof TruffleException) {
            return exception;
        }
        EngineSupport engine = HostInteropAccessor.ACCESSOR.engine();
        return engine.wrapHostException(languageContext, exception);
    }

}
