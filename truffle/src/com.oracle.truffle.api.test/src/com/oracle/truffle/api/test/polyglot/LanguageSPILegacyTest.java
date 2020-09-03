/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static com.oracle.truffle.tck.tests.ValueAssert.assertValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.tck.tests.ValueAssert;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

public class LanguageSPILegacyTest {

    static LanguageSPITestLanguage.LanguageContext langContext;

    @After
    public void cleanup() {
        langContext = null;
        ProxyLanguage.setDelegate(new ProxyLanguage());
        LanguageSPITest.InitializeTestLanguage.initialized = false;
        LanguageSPITest.InitializeTestInternalLanguage.initialized = false;
    }

    private int findScopeInvokes = 0;

    private void setupTopLegacyScopes(Object... scopesArray) {
        findScopeInvokes = 0;
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            @SuppressWarnings("deprecation")
            protected Iterable<com.oracle.truffle.api.Scope> findTopScopes(ProxyLanguage.LanguageContext context) {
                findScopeInvokes++;
                List<com.oracle.truffle.api.Scope> scopes = new ArrayList<>();
                for (int i = 0; i < scopesArray.length; i++) {
                    Object scope = scopesArray[i];
                    scopes.add(com.oracle.truffle.api.Scope.newBuilder(String.valueOf(i), scope).build());
                }
                return scopes;
            }
        });
    }

    private static void testFails(Runnable consumer) {
        try {
            consumer.run();
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isInternalError());
            assertFalse(e.isHostException()); // should not expose internal errors
        }
    }

    @Test
    public void testBindingsWithInvalidLegacyScopes() {
        setupTopLegacyScopes(new TruffleObject() {
        });
        Context c = Context.create();
        assertEquals(0, findScopeInvokes);
        testFails(() -> c.getBindings(ProxyLanguage.ID));
        assertEquals(1, findScopeInvokes);
        c.close();
    }

    @Test
    public void testBindingsWithSimpleLegacyScope() {
        LanguageSPITest.TestScope scope = new LanguageSPITest.TestScope();
        setupTopLegacyScopes(scope);
        testBindingsWithSimpleScope(scope);
    }

    private void testBindingsWithSimpleScope(LanguageSPITest.TestScope scope) {
        Context c = Context.create();
        assertEquals(0, findScopeInvokes);
        Value bindings = c.getBindings(ProxyLanguage.ID);
        c.getBindings(ProxyLanguage.ID);
        assertEquals(1, findScopeInvokes);

        scope.values.put("foobar", "baz");

        assertTrue(bindings.hasMembers());
        assertFalse(bindings.hasMember(""));
        assertTrue(bindings.hasMember("foobar"));
        assertEquals(new HashSet<>(Arrays.asList("foobar")), bindings.getMemberKeys());
        assertNull(bindings.getMember(""));
        assertEquals("baz", bindings.getMember("foobar").asString());
        AbstractPolyglotTest.assertFails(() -> bindings.putMember("", ""), UnsupportedOperationException.class);
        assertFalse(bindings.removeMember(""));
        AbstractPolyglotTest.assertFails(() -> bindings.removeMember("foobar"), UnsupportedOperationException.class);
        ValueAssert.assertValue(bindings, ValueAssert.Trait.MEMBERS);

        scope.insertable = true;
        bindings.putMember("baz", "val");
        assertEquals("val", scope.values.get("baz"));
        assertEquals("val", bindings.getMember("baz").asString());
        AbstractPolyglotTest.assertFails(() -> bindings.putMember("foobar", "42"), UnsupportedOperationException.class);
        ValueAssert.assertValue(bindings, ValueAssert.Trait.MEMBERS);

        scope.modifiable = true;
        bindings.putMember("foobar", "val");
        assertEquals("val", scope.values.get("foobar"));
        assertEquals("val", bindings.getMember("foobar").asString());
        ValueAssert.assertValue(bindings, ValueAssert.Trait.MEMBERS);

        scope.removable = true;
        assertFalse(bindings.removeMember(""));
        assertTrue(bindings.removeMember("foobar"));
        ValueAssert.assertValue(bindings, ValueAssert.Trait.MEMBERS);

        assertEquals(1, findScopeInvokes);

        c.close();
    }

    @Test
    public void testBindingsWithMultipleLegacyScopes() {
        // innermost to outermost
        LanguageSPITest.TestScope[] scopes = new LanguageSPITest.TestScope[5];
        for (int i = 0; i < 5; i++) {
            scopes[i] = new LanguageSPITest.TestScope();
        }
        setupTopLegacyScopes((Object[]) scopes);
        testBindingsWithMultipleScopes(scopes);
    }

    private void testBindingsWithMultipleScopes(LanguageSPITest.TestScope[] scopes) {

        Context c = Context.create();

        assertEquals(0, findScopeInvokes);
        Value bindings = c.getBindings(ProxyLanguage.ID);
        assertEquals(1, findScopeInvokes);

        assertTrue(bindings.hasMembers());
        assertFalse(bindings.hasMember(""));
        assertNull(bindings.getMember(""));

        AbstractPolyglotTest.assertFails(() -> bindings.putMember("foo", "bar"), UnsupportedOperationException.class);

        // test insertion into first insertable scope
        scopes[1].insertable = true;
        scopes[2].insertable = true;
        bindings.putMember("foo", "bar"); // should end up in scope 1
        assertEquals("bar", bindings.getMember("foo").asString());
        assertEquals("bar", scopes[1].values.get("foo"));
        assertNull(scopes[0].values.get("foo"));
        assertNull(scopes[2].values.get("foo"));

        // test it does not insert early before already existing member
        scopes[0].insertable = true;
        AbstractPolyglotTest.assertFails(() -> bindings.putMember("foo", "baz"), UnsupportedOperationException.class);
        scopes[1].modifiable = true;
        // does not insert in 1 but modifies foo in 2
        bindings.putMember("foo", "baz");
        assertNull(scopes[0].values.get("foo"));
        assertEquals("baz", scopes[1].values.get("foo"));
        assertEquals("baz", bindings.getMember("foo").asString());

        // test check for existing keys for remove
        scopes[1].values.clear();
        scopes[1].values.put("foo", "bar");
        scopes[2].removable = true;
        scopes[2].values.put("foo", "baz");
        scopes[2].values.put("bar", "baz");
        scopes[3].values.put("bar", "val");
        assertEquals("bar", bindings.getMember("foo").asString());
        assertEquals("baz", bindings.getMember("bar").asString());
        assertFails(() -> bindings.removeMember("foo"), UnsupportedOperationException.class);
        assertTrue(bindings.removeMember("bar"));
        assertNotNull(scopes[2].values.get("foo"));
        assertNull(scopes[2].values.get("bar"));
        assertEquals("val", bindings.getMember("bar").asString());
        assertValue(bindings, ValueAssert.Trait.MEMBERS);

        c.close();
    }

}
