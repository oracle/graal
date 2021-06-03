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
package com.oracle.truffle.api.test.wrapper;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineDispatch;

public class HostToGuest {

    private final AbstractPolyglotImpl guest;

    private final AtomicLong nextId = new AtomicLong();
    private final Map<Long, Object> idToGuestObject = new IdentityHashMap<>();
    private final Map<Object, Long> guestObjectToId = new IdentityHashMap<>();

    HostToGuest(AbstractPolyglotImpl guest) {
        this.guest = guest;
    }

    public long remoteCreateEngine() {
        // host access needs to be replaced
        return guestToHost(guest.buildEngine(null, null, null, new HashMap<>(), false, false, false, null, null, guest.createHostAccess()));
    }

    private long guestToHost(Object value) {
        Long id = guestObjectToId.get(value);
        if (id == null) {
            id = nextId.getAndIncrement();
            guestObjectToId.put(value, id);
            idToGuestObject.put(id, value);
        }
        return id;
    }

    private <T> T get(Class<T> type, long id) {
        return type.cast(idToGuestObject.get(id));
    }

    public long remoteCreateContext(long engineId) {
        Engine engine = get(Engine.class, engineId);
        Object receiver = guest.getAPIAccess().getReceiver(engine);
        AbstractEngineDispatch dispatch = guest.getAPIAccess().getDispatch(engine);
        Context remoteContext = dispatch.createContext(receiver, null, null, null, false, null, PolyglotAccess.NONE,
                        false, false, false, false, false, null, new HashMap<>(), new HashMap<>(),
                        new String[0],
                        null, null,
                        false, null, EnvironmentAccess.NONE,
                        null, null, null, null, null);
        return guestToHost(remoteContext);
    }

}
