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

package com.oracle.truffle.espresso.runtime.dispatch.messages;

import java.util.Objects;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.espresso.EspressoLanguage;

/**
 * Provides {@link CallTarget} factories for interop messages.
 * <p>
 * Factories need to be registered through {@link #register(Class, String, Supplier)} before being
 * able to be {@link #getFactory(EspressoLanguage, Class, String)} fetched.
 */
public final class InteropMessageFactory {
    public static final class Key {
        private final Class<?> cls;
        private final String message;

        public Key(Class<?> cls, String message) {
            this.cls = cls;
            this.message = message;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Key) {
                Key other = (Key) obj;
                return other.cls.equals(this.cls) && other.message.equals(this.message);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(cls, message);
        }
    }

    private InteropMessageFactory() {
    }

    private static final EconomicMap<Key, Supplier<InteropMessage>> messageMap = EconomicMap.create();

    public static void register(Class<?> cls, String message, Supplier<InteropMessage> factory) {
        assert cls != null;
        assert message != null;
        assert factory != null;
        messageMap.putIfAbsent(new Key(cls, message), factory);
    }

    public static Supplier<CallTarget> getFactory(EspressoLanguage lang, Class<?> cls, String message) {
        return () -> {
            Supplier<InteropMessage> factory = messageMap.get(new Key(cls, message));
            if (factory == null) {
                return null;
            }
            InteropMessage interopMessage = factory.get();
            return new InteropMessageRootNode(lang, interopMessage).getCallTarget();
        };
    }
}
