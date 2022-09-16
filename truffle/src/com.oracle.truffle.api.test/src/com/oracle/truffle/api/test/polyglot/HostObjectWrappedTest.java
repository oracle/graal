/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import java.lang.reflect.Field;
import java.math.BigInteger;

public class HostObjectWrappedTest {
    /**
     * Enso language supports <em>values with warnings</em>. They are
     * implemented as a {@link TruffleObject} that wraps original object,
     * holds the warnings and delegates all messages via {@link ReflectionLibrary} to the original object. 
     * Find more details in the
     * <a href="https://github.com/enso-org/enso/commit/f6340361a0d82047c56b59f8f2d20a209ba6c0b7#diff-d784205295fc228b108d6ca365bf8371e6fa2337bd8f2c38e7a3bdac721f3a30R14">implementation</a>.
     * <p>
     * This {@code WrapInt} mimics that behavior and tries to perform
     * <em>host interop</em>. That fails in 21.3 version and needs additional
     * fixes to work.
     */
    @ExportLibrary(ReflectionLibrary.class)
    public static final class WrapObject implements TruffleObject {
        private final TruffleObject delegate;

        private WrapObject(TruffleObject delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        Object send(Message message, Object[] args, @CachedLibrary(limit="3") ReflectionLibrary reflect) throws Exception {
            return reflect.send(delegate, message, args);
        }

        public static Value wrap(Value i) throws Exception {
            Field f = i.getClass().getSuperclass().getDeclaredField("receiver");
            f.setAccessible(true);
            TruffleObject o = (TruffleObject) f.get(i);
            WrapObject wrap = new WrapObject(o);
            return i.getContext().asValue(wrap);
        }
    }

    @Test
    public void testHostInteropWithWrapper() throws Exception {
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).allowPublicAccess(true).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).build()) {
            Value bigIntClass = context.asValue(BigInteger.class).getMember("static");
            Value four = bigIntClass.invokeMember("valueOf", 444L);
            Value five = bigIntClass.invokeMember("valueOf", 555L);

            Value simplePlus = four.invokeMember("add", five);
            assertEquals(444L + 555L, simplePlus.invokeMember("longValue").asLong());

            Value wrapFive = WrapObject.wrap(five);

            Value wrapPlus = four.invokeMember("add", wrapFive);
            assertEquals(444L + 555L, wrapPlus.invokeMember("longValue").asLong());

            Value wrapPlusCommutative = wrapFive.invokeMember("add", four);
            assertEquals(444L + 555L, wrapPlusCommutative.invokeMember("longValue").asLong());
        }
    }
}
