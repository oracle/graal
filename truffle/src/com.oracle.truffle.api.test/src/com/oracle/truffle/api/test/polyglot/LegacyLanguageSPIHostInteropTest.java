/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.test.polyglot.ValueHostInteropTest.Data;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;
import org.hamcrest.CoreMatchers;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class LegacyLanguageSPIHostInteropTest extends AbstractPolyglotTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    public LegacyLanguageSPIHostInteropTest() {
        needsLanguageEnv = true;
    }

    @Before
    public void before() {
        setupEnv();
    }

    @Test
    public void testSystemMethod() throws InteropException {
        Object system = languageEnv.lookupHostSymbol(System.class.getName());
        Object value = INTEROP.invokeMember(system, "getProperty", "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));

        Object getProperty = INTEROP.readMember(system, "getProperty");
        assertThat(getProperty, CoreMatchers.instanceOf(TruffleObject.class));
        assertTrue("IS_EXECUTABLE", INTEROP.isExecutable(getProperty));
        value = INTEROP.execute(getProperty, "file.separator");
        assertThat(value, CoreMatchers.instanceOf(String.class));
        assertThat(value, CoreMatchers.anyOf(CoreMatchers.equalTo("/"), CoreMatchers.equalTo("\\")));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void assertKeysAndProperties() throws InteropException {
        Data dataObj = new Data();
        TruffleObject data = (TruffleObject) languageEnv.asGuestValue(dataObj);

        Object keys = INTEROP.getMembers(data);
        List<Object> list = context.asValue(keys).as(List.class);
        assertThat(list, CoreMatchers.hasItems("x", "y", "arr", "value", "map", "dataMap", "data", "plus"));

        Method[] objectMethods = Object.class.getMethods();
        for (Method objectMethod : objectMethods) {
            assertThat("No java.lang.Object methods", list, CoreMatchers.not(CoreMatchers.hasItem(objectMethod.getName())));
        }

        keys = INTEROP.getMembers(data, true);
        List<Object> legacyInternalList = context.asValue(keys).as(List.class);
        assertTrue(legacyInternalList.size() >= list.size());
        for (Object member : list) {
            assertTrue(member.toString(), legacyInternalList.contains(member));
        }
    }

    @Test
    public void invokeJavaLangObjectFields() throws InteropException {
        Data data = new Data();
        TruffleObject obj = (TruffleObject) languageEnv.asGuestValue(data);

        Object string = INTEROP.invokeMember(obj, "toString");
        assertTrue(string instanceof String && ((String) string).startsWith(Data.class.getName() + "@"));
        Object clazz = INTEROP.invokeMember(obj, "getClass");
        assertTrue(clazz instanceof TruffleObject && languageEnv.asHostObject(clazz) == Data.class);
        assertEquals(true, INTEROP.invokeMember(obj, "equals", obj));
        assertTrue(INTEROP.invokeMember(obj, "hashCode") instanceof Integer);

        for (String m : new String[]{"notify", "notifyAll", "wait"}) {
            assertThrowsExceptionWithCause(() -> INTEROP.invokeMember(obj, m), IllegalMonitorStateException.class);
        }
    }

    private void assertThrowsExceptionWithCause(Callable<?> callable, Class<? extends Exception> exception) {
        try {
            callable.call();
            fail("Expected " + exception.getSimpleName() + " but no exception was thrown");
        } catch (Exception e) {
            assertTrue(languageEnv.isHostException(e));
            assertSame(exception, languageEnv.asHostException(e).getClass());
        }
    }

    @Test
    public void keyInfoJavaObject() {
        Object d = languageEnv.asGuestValue(new LanguageSPIHostInteropTest.TestJavaObject());
        assertFalse(INTEROP.isMemberExisting(d, "nnoonnee"));

        assertTrue(INTEROP.isMemberExisting(d, "aField"));
        assertTrue(INTEROP.isMemberReadable(d, "aField"));
        assertTrue(INTEROP.isMemberWritable(d, "aField"));
        assertFalse(INTEROP.isMemberInvocable(d, "aField"));
        assertFalse(INTEROP.isMemberRemovable(d, "aField"));

        assertTrue(INTEROP.isMemberExisting(d, "toString"));
        assertTrue(INTEROP.isMemberReadable(d, "toString"));
        assertFalse(INTEROP.isMemberWritable(d, "toString"));
        assertTrue(INTEROP.isMemberInvocable(d, "toString"));
        assertFalse(INTEROP.isMemberRemovable(d, "toString"));
    }

    @Test
    public void testIsHostFunction() throws InteropException {
        TruffleObject system = (TruffleObject) languageEnv.lookupHostSymbol(System.class.getName());
        Object exit = INTEROP.readMember(system, "exit");
        assertTrue(exit instanceof TruffleObject);
        assertFalse(languageEnv.isHostObject(exit));
        assertTrue(languageEnv.isHostFunction(exit));

        Object out = INTEROP.readMember(system, "out");
        assertTrue(exit instanceof TruffleObject);
        assertTrue(languageEnv.isHostObject(out));
        assertFalse(languageEnv.isHostFunction(out));

        assertFalse(languageEnv.isHostFunction(system));
        assertFalse(languageEnv.isHostFunction(false));
    }
}
