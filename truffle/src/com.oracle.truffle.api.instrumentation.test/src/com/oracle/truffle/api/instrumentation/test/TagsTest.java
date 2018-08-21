/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
            assertSame(ExpressionTag.class, Tag.findProvidedTag(env.getLanguages().get("tagLanguage"), "EXPRESSION"));
            assertSame(StatementTag.class, Tag.findProvidedTag(env.getLanguages().get("tagLanguage"), "STATEMENT"));
            assertFails(() -> Tag.findProvidedTag(env.getLanguages().get("tagLanguage"), null), NullPointerException.class);
            assertNull(Tag.findProvidedTag(env.getLanguages().get("tagLanguage"), "UNKNOWN_TAG"));
            return env;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

    }

}
