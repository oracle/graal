/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;

public class TagsTest {

    private static class MyTag extends Tag {

    }

    @Tag.Identifier("MyTagWithIdentifier")
    private static class MyTagWithIdentifier extends Tag {

    }

    @Test
    public void testGetIdentifier() {
        assertEquals("EXPRESSION", Tag.getIdentifier(StandardTags.ExpressionTag.class));
        assertEquals("STATEMENT", Tag.getIdentifier(StandardTags.StatementTag.class));
        assertEquals("CALL", Tag.getIdentifier(StandardTags.CallTag.class));
        assertEquals("ROOT_BODY", Tag.getIdentifier(StandardTags.RootBodyTag.class));
        assertEquals("ROOT", Tag.getIdentifier(StandardTags.RootTag.class));
        assertNull(Tag.getIdentifier(MyTag.class));
        assertEquals("MyTagWithIdentifier", Tag.getIdentifier(MyTagWithIdentifier.class));
    }

    @Test
    public void testFindProvidedTags() {
        assertFails(() -> Tag.findProvidedTag(null, null), NullPointerException.class);
        assertFails(() -> Tag.findProvidedTag(null, ""), NullPointerException.class);

        Context context = Context.create("tagLanguage");
        context.initialize("tagLanguage");
        context.close();
    }

    private static void assertFails(Runnable r, Class<?> hostExceptionType) {
        try {
            r.run();
            Assert.fail("No error but expected " + hostExceptionType);
        } catch (Exception e) {
            if (!hostExceptionType.isInstance(e)) {
                throw new AssertionError(e.getClass().getName() + ":" + e.getMessage(), e);
            }
        }
    }

    @TruffleLanguage.Registration(id = "tagLanguage", name = "")
    @ProvidedTags({ExpressionTag.class, StatementTag.class})
    public static class TagLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            assertSame(ExpressionTag.class, Tag.findProvidedTag(env.getInternalLanguages().get("tagLanguage"), "EXPRESSION"));
            assertSame(StatementTag.class, Tag.findProvidedTag(env.getInternalLanguages().get("tagLanguage"), "STATEMENT"));
            assertFails(() -> Tag.findProvidedTag(env.getInternalLanguages().get("tagLanguage"), null), NullPointerException.class);
            assertNull(Tag.findProvidedTag(env.getInternalLanguages().get("tagLanguage"), "UNKNOWN_TAG"));
            return env;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

    }

}
