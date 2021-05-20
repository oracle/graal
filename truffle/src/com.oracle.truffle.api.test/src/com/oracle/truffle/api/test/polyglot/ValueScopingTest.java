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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.tck.tests.ValueAssert;

public class ValueScopingTest {
    @Test
    public void testScoping() {
        AssociativeArray languageBindings = new AssociativeArray("proxy language bindings");
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Object getScope(LanguageContext context) {
                return languageBindings;
            }
        });

        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.SCOPED).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).build()) {
            context.initialize(ProxyLanguage.ID);

            Value binding = context.getBindings(ProxyLanguage.ID);

            // let the guest call TestObject.storeGuestObjectReference
            // since it is not annotated with @HostAccess.Escape, the callback parameters will only
            // be valid during the callback
            TestObject to = new TestObject();
            binding.executeVoid("invokeCallback", to, "store");

            // access the guest object reference - this should fail, as it was passed as a parameter
            // to the callback and destroyed after
            try {
                to.accessGuestObject();
                fail("accessing the reference should fail");
            } catch (Exception ex) {
                String msg = ex.getMessage();
                assertTrue("wrong exception " + msg, msg.contains("This scoped object has already been released."));
            }

            // let the guest call TestObject.storeGuestObjectReferenceEscape
            // since it is annotated with @HostAccess.Escape, the callback parameters will stay
            // valid until after the callback
            binding.executeVoid("invokeCallback", to, "storeScopeDisabled");

            // access the guest object reference - this should succeed as the parameters to the
            // callback were allowed to escape
            try {
                to.accessGuestObject();
            } catch (Exception ex) {
                fail("accessing the reference should succeed " + ex.getMessage());
            }
        }
    }

    @Test
    public void testPinning() {
        AssociativeArray languageBindings = new AssociativeArray("proxy language bindings");
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Object getScope(LanguageContext context) {
                return languageBindings;
            }
        });

        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.SCOPED).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).build()) {
            context.initialize(ProxyLanguage.ID);

            Value binding = context.getBindings(ProxyLanguage.ID);

            TestObject to = new TestObject();
            binding.executeVoid("invokeCallback", to, "storeAndPin");

            try {
                to.accessGuestObject();
            } catch (Exception ex) {
                fail("accessing the reference should succeed " + ex.getMessage());
            }

            to.releaseGuestObject();

            try {
                to.accessGuestObject();
                fail("accessing the reference should fail");
            } catch (Exception ex) {
                String msg = ex.getMessage();
                assertTrue("wrong exception " + msg, msg.contains("This scoped object has already been released."));
            }
        }
    }

    @Test
    public void testFailureAfterRelease() {
        AssociativeArray languageBindings = new AssociativeArray("proxy language bindings");
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Object getScope(LanguageContext context) {
                return languageBindings;
            }
        });

        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.SCOPED).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).build()) {
            context.initialize(ProxyLanguage.ID);

            Value binding = context.getBindings(ProxyLanguage.ID);

            TestObject to = new TestObject();
            binding.executeVoid("invokeCallback", to, "storeAndPin");

            to.releaseGuestObject();

            try {
                to.releaseGuestObject();
                fail("releasing the object twice should fail");
            } catch (Exception ex) {
                String msg = ex.getMessage();
                assertTrue("wrong exception " + msg, msg.contains("Scoped objects can only be released once"));
            }

            try {
                to.pinGuestObject();
                fail("pinning the released object should fail");
            } catch (Exception ex) {
                String msg = ex.getMessage();
                assertTrue("wrong exception " + msg, msg.contains("Released objects cannot be pinned"));
            }
        }
    }

    @Test
    public void testPolyglotMapGuestObjectPinning() {
        AssociativeArray languageBindings = new AssociativeArray("proxy language bindings");
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Object getScope(LanguageContext context) {
                return languageBindings;
            }
        });

        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.SCOPED).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).build()) {
            context.initialize(ProxyLanguage.ID);

            Value binding = context.getBindings(ProxyLanguage.ID);

            TestObject to = new TestObject();
            binding.executeVoid("invokeCallback", to, "storeAndPinPolyglotMap");

            try {
                to.accessPolyglotMap();
            } catch (Exception ex) {
                fail("accessing the reference should succeed " + ex.getMessage());
            }

            to.releaseGuestObject();

            try {
                String r = to.accessPolyglotMap().toString();
                fail("accessing the reference should fail " + r);
            } catch (Exception ex) {
                String msg = ex.getMessage();
                assertTrue("wrong exception " + msg, msg.contains("This scoped object has already been released."));
            }
        }
    }

    @Test
    public void testNestedScoping() {
        AssociativeArray languageBindings = new AssociativeArray("proxy language bindings");
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Object getScope(LanguageContext context) {
                return languageBindings;
            }
        });

        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.SCOPED).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).build()) {
            context.initialize(ProxyLanguage.ID);

            Value binding = context.getBindings(ProxyLanguage.ID);

            TestObject to = new TestObject();
            binding.executeVoid("invokeCallback", to, "storePolyglotMapElement");

            try {
                to.accessGuestObject();
                fail("accessing the reference should fail");
            } catch (Exception ex) {
                String msg = ex.getMessage();
                assertTrue("wrong exception " + msg, msg.contains("This scoped object has already been released."));
            }
        }
    }

    @Test
    public void testGrowScope() {
        AssociativeArray languageBindings = new AssociativeArray("proxy language bindings");
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Object getScope(LanguageContext context) {
                return languageBindings;
            }
        });

        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.SCOPED).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).build()) {
            context.initialize(ProxyLanguage.ID);

            Value binding = context.getBindings(ProxyLanguage.ID);

            TestObject to = new TestObject();
            try {
                binding.executeVoid("invokeCallback", to, "accessAllMembers");
            } catch (Exception ex) {
                ex.printStackTrace();
                fail(ex.getMessage());
            }
        }
    }

    @Test
    public void testMultiThread() {
        AssociativeArray languageBindings = new AssociativeArray("proxy language bindings");
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected Object getScope(LanguageContext context) {
                return languageBindings;
            }

            @Override
            protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
                return true;
            }
        });

        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.SCOPED).build();
        Context context = Context.newBuilder().allowHostAccess(accessPolicy).build();
        context.initialize(ProxyLanguage.ID);

        Value binding = context.getBindings(ProxyLanguage.ID);

        TestObject to = new TestObject();
        binding.executeVoid("invokeCallback", to, "accessMultiThreaded");

        to.getTrigger().countDown();
    }

    public static class TestObject {
        private Value guestObject;
        private Map<String, Object> guestMap;
        private final CountDownLatch trigger;

        TestObject() {
            this.trigger = new CountDownLatch(1);
        }

        CountDownLatch getTrigger() {
            return trigger;
        }

        @HostAccess.Export
        public void store(Value v) {
            guestObject = v;
            ValueAssert.assertValue(guestObject);
        }

        @HostAccess.Export
        @HostAccess.DisableMethodScoping
        public void storeScopeDisabled(Value v) {
            guestObject = v;
            ValueAssert.assertValue(guestObject);
        }

        @HostAccess.Export
        public void storeAndPin(Value v) {
            guestObject = v;
            ValueAssert.assertValue(guestObject);
            guestObject.pin();
        }

        void releaseGuestObject() {
            if (guestObject != null) {
                guestObject.release();
            }
            if (guestMap != null) {
                Value.asValue(guestMap).release();
            }
        }

        void pinGuestObject() {
            guestObject.pin();
        }

        @HostAccess.Export
        public void storeAndPinPolyglotMap(Map<String, Object> map) {
            this.guestMap = map;
            Value.asValue(guestMap).pin();
        }

        @HostAccess.Export
        public void storePolyglotMapElement(Map<String, Object> map) {
            this.guestObject = Value.asValue(map.get("cafe"));
        }

        @HostAccess.Export
        public void accessAllMembers(Value v) {
            Value elem = v.getMember("grow");
            for (String key : elem.getMemberKeys()) {
                ValueAssert.assertValue(elem.getMember(key));
            }
        }

        @HostAccess.Export
        public void accessMultiThreaded(Value v) throws InterruptedException {
            CountDownLatch callbackReturnSignal = new CountDownLatch(1);
            Thread accessor = new Thread() {
                @Override
                public void run() {
                    Value elem = v.getMember("cafe");
                    callbackReturnSignal.countDown();
                    try {
                        trigger.await();
                        ValueAssert.assertValue(elem);
                        fail("accessing the references should not succeed as the callback function executing on the main thread already returned");
                    } catch (Exception ex) {
                        String msg = ex.getMessage();
                        assertTrue("wrong exception " + msg, msg.contains("This scoped object has already been released."));
                    }
                }
            };
            accessor.start();
            callbackReturnSignal.await();
        }

        public Object accessPolyglotMap() {
            return guestMap.get("foobar");
        }

        public void accessGuestObject() {
            ValueAssert.assertValue(guestObject);
        }
    }
}

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
class GuestObject implements TruffleObject {

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean isMemberReadable(@SuppressWarnings("unused") String member) {
        return true;
    }

    @ExportMessage
    Object readMember(String member) {
        if (member.contentEquals("cafe")) {
            return new AssociativeArray(member);
        }
        if (member.contentEquals("grow")) {
            AssociativeArray a = new AssociativeArray(member);
            for (int i = 0; i < 50; i++) {
                String key = String.valueOf(i);
                a.put(key, new GuestEcho(key + "_elem"));
            }
            return a;
        }
        return new GuestEcho(member);
    }

    @ExportMessage
    final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }
}

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
class GuestEcho implements TruffleObject {

    final String value;

    GuestEcho(String echo) {
        value = echo;
    }

    @ExportMessage
    boolean isString() {
        return true;
    }

    @ExportMessage
    String asString() {
        return value;
    }
}

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
class AssociativeArray implements TruffleObject {

    private Map<String, Object> map;
    private String displayString;

    AssociativeArray(String displayString) {
        map = new HashMap<>();
        this.displayString = displayString;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object readMember(String member) {
        return get(member);
    }

    @TruffleBoundary
    Object get(String member) {
        return map.get(member);
    }

    @ExportMessage
    void writeMember(String member, Object value) {
        put(member, value);
    }

    @TruffleBoundary
    void put(String key, Object value) {
        map.put(key, value);
    }

    @ExportMessage
    boolean isMemberRemovable(String member) {
        return contains(member);
    }

    @ExportMessage
    void removeMember(String member) {
        remove(member);
    }

    @TruffleBoundary
    void remove(String key) {
        map.remove(key);
    }

    @ExportMessage
    boolean isScope() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return members();
    }

    @TruffleBoundary
    Object members() {
        return new Array(map.keySet().toArray());
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        return contains(member);
    }

    @TruffleBoundary
    boolean contains(String key) {
        return map.containsKey(key);
    }

    @ExportMessage
    boolean isMemberModifiable(String member) {
        return contains(member);
    }

    @ExportMessage
    boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return !contains(member);
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    public Class<? extends TruffleLanguage<?>> getLanguage() {
        return ProxyLanguage.class;
    }

    @ExportMessage
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return displayString;
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        if (arguments.length > 0 && arguments[0] instanceof String) {
            String cmd = (String) arguments[0];
            if (cmd.startsWith("invokeCallback")) {
                String method = InteropLibrary.getFactory().getUncached().asString(arguments[2]);
                try {
                    Object invokable = InteropLibrary.getFactory().getUncached().readMember(arguments[1], method);
                    GuestObject go = new GuestObject();
                    return InteropLibrary.getFactory().getUncached().execute(invokable, go);
                } catch (UnknownIdentifierException e) {
                }
            }
        }
        throw UnsupportedMessageException.create();
    }
}

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
class Array implements TruffleObject {

    private final Object[] array;

    Array(Object[] array) {
        this.array = array;
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    Object readArrayElement(long index) {
        return array[(int) index];
    }

    @ExportMessage
    long getArraySize() {
        return array.length;
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return index < array.length;
    }
}
