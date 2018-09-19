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
package com.oracle.truffle.nfi.test.parser;

import com.oracle.truffle.nfi.types.NativeArrayTypeMirror;
import com.oracle.truffle.nfi.types.NativeSimpleType;
import com.oracle.truffle.nfi.types.NativeSimpleTypeMirror;
import com.oracle.truffle.nfi.types.NativeTypeMirror;
import com.oracle.truffle.nfi.types.NativeTypeMirror.Kind;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class ParseSignatureTest {

    private abstract static class TypeMatcher<T extends NativeTypeMirror> extends TypeSafeMatcher<NativeTypeMirror> {

        private final Class<T> expectedClass;
        private final Kind expectedKind;

        protected TypeMatcher(Class<T> expectedClass, Kind expectedKind) {
            this.expectedClass = expectedClass;
            this.expectedKind = expectedKind;
        }

        protected abstract boolean matchesType(T type);

        protected abstract void describeTypeMismatch(T item, Description mismatchDescription);

        @Override
        protected boolean matchesSafely(NativeTypeMirror item) {
            if (item.getKind() == expectedKind) {
                return matchesType(expectedClass.cast(item));
            } else {
                return false;
            }
        }

        @Override
        protected void describeMismatchSafely(NativeTypeMirror item, Description mismatchDescription) {
            if (item.getKind() == expectedKind) {
                describeTypeMismatch(expectedClass.cast(item), mismatchDescription);
            } else {
                mismatchDescription.appendText("is type of kind ").appendValue(item.getKind());
            }
        }
    }

    protected static Matcher<NativeTypeMirror> isSimpleType(NativeSimpleType expected) {
        return new TypeMatcher<NativeSimpleTypeMirror>(NativeSimpleTypeMirror.class, Kind.SIMPLE) {

            @Override
            protected boolean matchesType(NativeSimpleTypeMirror item) {
                return expected == item.getSimpleType();
            }

            @Override
            public void describeTo(Description description) {
                description.appendValue(expected);
            }

            @Override
            protected void describeTypeMismatch(NativeSimpleTypeMirror item, Description mismatchDescription) {
                mismatchDescription.appendValue(item.getSimpleType());
            }
        };
    }

    protected static Matcher<NativeTypeMirror> isArrayType(Matcher<?> component) {
        return new TypeMatcher<NativeArrayTypeMirror>(NativeArrayTypeMirror.class, Kind.ARRAY) {

            @Override
            protected boolean matchesType(NativeArrayTypeMirror type) {
                return component.matches(type.getElementType());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("array of ").appendDescriptionOf(component);
            }

            @Override
            protected void describeTypeMismatch(NativeArrayTypeMirror item, Description mismatchDescription) {
                mismatchDescription.appendText("array of ");
                component.describeMismatch(item.getElementType(), mismatchDescription);
            }
        };
    }
}
