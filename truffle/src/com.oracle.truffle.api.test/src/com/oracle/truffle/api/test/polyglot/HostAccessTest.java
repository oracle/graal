/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class HostAccessTest {
    public static class OK {
        public int value = 42;
    }

    public static class Ban {
        public int value = 24;
    }

    @Test
    public void usefulToStringExplicit() {
        Assert.assertEquals("HostAccessPolicy.EXPLICIT", HostAccess.EXPLICIT.toString());
    }

    @Test
    public void usefulToStringPublic() {
        Assert.assertEquals("HostAccessPolicy.ALL", HostAccess.ALL.toString());
    }

    @Test
    public void usefulToStringNone() {
        Assert.assertEquals("HostAccessPolicy.NONE", HostAccess.NONE.toString());
    }

    public static class MyEquals {

        @Override
        public boolean equals(Object arg0) {
            return arg0 != null && getClass() == arg0.getClass();
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }
    }

    @Test
    public void banAccessToReflection() throws Exception {
        HostAccess config = HostAccess.newBuilder().allowPublicAccess(true).preventAccess(Class.class).preventAccess(Method.class).preventAccess(Field.class).preventAccess(
                        Proxy.class).preventAccess(
                                        Object.class).build();

        Context c = Context.newBuilder().allowHostAccess(config).build();

        Value readValue = c.eval("sl", "" + "function readValue(x, y) {\n" + "  return x.equals(y);\n" + "}\n" + "function main() {\n" + "  return readValue;\n" + "}\n");

        MyEquals myEquals = new MyEquals();
        assertTrue("MyEquals.equals method is accessible", readValue.execute(myEquals, myEquals).asBoolean());

        Value res;
        try {
            res = readValue.execute(new Object());
        } catch (PolyglotException ex) {
            return;
        }
        fail("expecting no result: " + res);
    }

    @Test
    public void banAccessToEquals() throws Exception {
        HostAccess config = HostAccess.newBuilder().allowPublicAccess(true).preventAccess(Object.class.getMethod("equals", Object.class)).build();

        Context c = Context.newBuilder().allowHostAccess(config).build();

        Value readValue = c.eval("sl", "" + "function readValue(x, y) {\n" + "  return x.equals(y);\n" + "}\n" + "function main() {\n" + "  return readValue;\n" + "}\n");

        MyEquals myEquals = new MyEquals();
        assertTrue("MyEquals.equals method is accessible", readValue.execute(myEquals, myEquals).asBoolean());

        Value res;
        try {
            res = readValue.execute(new Object());
        } catch (PolyglotException ex) {
            return;
        }
        fail("expecting no result: " + res);
    }

    @Test
    public void publicCanAccessObjectEquals() throws Exception {
        HostAccess config = HostAccess.ALL;

        Context c = Context.newBuilder().allowHostAccess(config).build();

        Value readValue = c.eval("sl", "" + "function readValue(x, y) {\n" + "  return x.equals(y);\n" + "}\n" + "function main() {\n" + "  return readValue;\n" + "}\n");
        assertFalse("Cannot read equals 1", readValue.execute(new Object(), new Object()).asBoolean());
        Object same = new Object();
        assertTrue("Cannot read equals 2", readValue.execute(same, same).asBoolean());
    }

    @Test
    public void inheritFromPublic() throws Exception {
        HostAccess config = HostAccess.newBuilder().allowPublicAccess(true).build();

        Context c = Context.newBuilder().allowHostAccess(config).build();

        Value readValue = c.eval("sl", "" + "function readValue(x, y) {\n" + "  return x.equals(y);\n" + "}\n" + "function main() {\n" + "  return readValue;\n" + "}\n");
        assertFalse("Cannot read equals 1", readValue.execute(new Object(), new Object()).asBoolean());
        Object same = new Object();
        assertTrue("Cannot read equals 2", readValue.execute(same, same).asBoolean());
    }

    @Test
    public void useOneHostAccessByTwoContexts() throws Exception {
        HostAccess config = HostAccess.newBuilder().allowAccess(OK.class.getField("value")).build();

        Context c1 = Context.newBuilder().allowHostAccess(config).build();
        Context c2 = Context.newBuilder().allowHostAccess(config).build();

        assertAccess(c1);
        assertAccess(c2);
    }

    private static void assertAccess(Context context) {
        Value readValue = context.eval("sl", "" + "function readValue(x) {\n" + "  return x.value;\n" + "}\n" + "function main() {\n" + "  return readValue;\n" + "}\n");
        Assert.assertEquals(42, readValue.execute(new OK()).asInt());
        ExposeToGuestTest.assertPropertyUndefined("public isn't enough by default", "value", readValue, new Ban());
    }

    @Test
    public void onlyOneHostAccessPerEngine() throws Exception {
        Engine shared = Engine.create();

        HostAccess config = HostAccess.newBuilder().allowAccess(OK.class.getField("value")).build();

        Context c1 = Context.newBuilder().engine(shared).allowHostAccess(config).build();
        Context c2;
        try {
            c2 = Context.newBuilder().engine(shared).allowHostAccess(HostAccess.ALL).build();
        } catch (IllegalStateException ex) {
            Assert.assertNotEquals("Can't have one engine between two HostAccess configs: " + ex.getMessage(), -1, ex.getMessage().indexOf("Cannot share engine"));
            return;
        }
        Assert.assertNotEquals("cannot share engine for different HostAccess configs", c1.getEngine(), c2.getEngine());
    }
}
