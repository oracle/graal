/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.interop;

import org.graalvm.polyglot.Value;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PyObjectTest extends InteropTestBase {

    private static Value getPyObjectImplicitType;
    private static Value getPyObjectExplicitType;
    private static Value freePyObject;

    @BeforeClass
    public static void loadTestBitcode() {
        Value testLibrary = loadTestBitcodeValue("pyobject.c");
        getPyObjectImplicitType = testLibrary.getMember("getPyObjectImplicitType");
        getPyObjectExplicitType = testLibrary.getMember("getPyObjectExplicitType");
        freePyObject = testLibrary.getMember("freePyObject");
    }

    final class NotExistingMatcher extends TypeSafeMatcher<Value> {

        private final String name;

        private NotExistingMatcher(String name) {
            super(Value.class);
            this.name = name;
        }

        @Override
        protected boolean matchesSafely(Value item) {
            return !item.hasMember(name);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("member ").appendText(name).appendText(" does not exist");
        }

        @Override
        protected void describeMismatchSafely(Value item, Description description) {
            description.appendText("has value: ").appendValue(item.getMember(name));
        }
    }

    final class MemberMatcher extends TypeSafeMatcher<Value> {

        private final String name;
        private final TypeSafeMatcher<Value> matcher;

        MemberMatcher(String name, TypeSafeMatcher<Value> matcher) {
            super(Value.class);
            this.name = name;
            this.matcher = matcher;
        }

        @Override
        protected boolean matchesSafely(Value item) {
            if (item.hasMember(name)) {
                return matcher.matches(item.getMember(name));
            } else {
                return false;
            }
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("member ").appendText(name).appendText(" is ").appendDescriptionOf(matcher);
        }

        @Override
        protected void describeMismatchSafely(Value item, Description description) {
            if (item.hasMember(name)) {
                description.appendText(name).appendText(" ");
                matcher.describeMismatch(item.getMember(name), description);
            } else {
                description.appendText("has no member ").appendText(name);
            }
        }
    }

    final class IntMatcher extends TypeSafeMatcher<Value> {

        private final int expected;

        private IntMatcher(int expected) {
            super(Value.class);
            this.expected = expected;
        }

        @Override
        public boolean matchesSafely(Value item) {
            return item.isNumber() && item.fitsInInt() && item.asInt() == expected;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(expected);
        }

        @Override
        protected void describeMismatchSafely(Value item, Description description) {
            super.describeMismatchSafely(item, description);
            if (!item.isNumber()) {
                description.appendText(" (not a number)");
            } else if (!item.fitsInInt()) {
                description.appendText(" (doesn't fit in int)");
            }
        }
    }

    @Test
    public void testImplicitType() {
        Value obj = getPyObjectImplicitType.execute(); // should be of type PyObject
        Assert.assertThat(obj, new MemberMatcher("ob_refcnt", new IntMatcher(1)));
        Assert.assertThat(obj, new NotExistingMatcher("base"));
        Assert.assertThat(obj, new NotExistingMatcher("flags"));
        freePyObject.execute(obj);
    }

    @Test
    public void testExplicitType() {
        Value obj = getPyObjectExplicitType.execute(); // should be of type PyMemoryViewObject
        Assert.assertThat(obj, new NotExistingMatcher("ob_refcnt"));
        Assert.assertThat(obj, new MemberMatcher("base", new MemberMatcher("ob_refcnt", new IntMatcher(1))));
        Assert.assertThat(obj, new MemberMatcher("flags", new IntMatcher(42)));
        freePyObject.execute(obj);
    }
}
