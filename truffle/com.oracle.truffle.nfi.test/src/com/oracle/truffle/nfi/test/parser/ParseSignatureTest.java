/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
