/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = BoxedStringTest.class)
public class BoxedStringTest extends ProxyLanguageEnvTest implements TruffleObject, ForeignAccess.StandardFactory {
    public interface ExactMatchInterop {
        String stringValue();

        char charValue();
    }

    private String value;
    private ExactMatchInterop interop;

    static final List<String> KEYS = Arrays.asList(new String[]{"charValue", "stringValue"});

    @Before
    public void initObjects() {
        interop = asJavaObject(ExactMatchInterop.class, this);
    }

    @Test
    public void convertToString() {
        value = "Hello";
        assertEquals("Hello", interop.stringValue());
    }

    @Test
    public void convertToChar() {
        value = "W";
        assertEquals('W', interop.charValue());
    }

    public static boolean isInstance(TruffleObject object) {
        return object instanceof BoxedStringTest;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return BoxedStringTestForeign.ACCESS;
    }

    @Resolve(message = "UNBOX")
    public abstract static class UnboxNode extends Node {
        public Object access(BoxedStringTest obj) {
            return obj.value;
        }
    }

    @Resolve(message = "READ")
    public abstract static class ReadNode extends Node {
        public Object access(BoxedStringTest obj, String key) {
            assert KEYS.contains(key);
            return obj;
        }
    }

    @Resolve(message = "KEYS")
    public abstract static class KeysNode extends Node {
        public Object access(BoxedStringTest obj) {
            return obj.asTruffleObject(KEYS);
        }
    }

    @Resolve(message = "KEY_INFO")
    public abstract static class KeyInfoNode extends Node {
        @SuppressWarnings("unused")
        public Object access(BoxedStringTest obj, String key) {
            return KEYS.contains(key) ? KeyInfo.READABLE : KeyInfo.NONE;
        }
    }
}
