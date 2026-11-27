/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.junit.nativeimage.foreign;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.ForeignSupport;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.NeverInline;

/**
 * Nests two scoped regions so that one block closes both of them while a sibling block in the
 * dominator tree still needs both open. The shared arena access phase must restore every outer
 * scope that such a block closed, otherwise the sibling block sees an inconsistent scope stack and
 * the image build fails. The two variants flip the branch so that each successor order is visited.
 */
public class SharedArenaNestedScopesTest {

    @Test
    public void testOuterScopesRestoredWhenThrowingOnTrue() {
        Assert.assertEquals(42, NestedScopes.outerThrowOnTrue(false));
        Assert.assertThrows(IllegalStateException.class, () -> NestedScopes.outerThrowOnTrue(true));
    }

    @Test
    public void testOuterScopesRestoredWhenThrowingOnFalse() {
        Assert.assertEquals(42, NestedScopes.outerThrowOnFalse(true));
        Assert.assertThrows(IllegalStateException.class, () -> NestedScopes.outerThrowOnFalse(false));
    }

    /**
     * Kept out of the test class so that JUnit's reflective lookup of the test methods does not
     * make the hosted-only inlining annotation reachable.
     */
    static final class NestedScopes {

        @NeverInline("Compile the nested scopes in a graph of their own.")
        @ForeignSupport.Scoped
        static int outerThrowOnTrue(boolean fail) {
            return innerThrowOnTrue(fail);
        }

        @AlwaysInline("Nest the inner scope in the outer one.")
        @ForeignSupport.Scoped
        static int innerThrowOnTrue(boolean fail) {
            if (fail) {
                throw new IllegalStateException();
            }
            return 42;
        }

        @NeverInline("Compile the nested scopes in a graph of their own.")
        @ForeignSupport.Scoped
        static int outerThrowOnFalse(boolean succeed) {
            return innerThrowOnFalse(succeed);
        }

        @AlwaysInline("Nest the inner scope in the outer one.")
        @ForeignSupport.Scoped
        static int innerThrowOnFalse(boolean succeed) {
            if (!succeed) {
                throw new IllegalStateException();
            }
            return 42;
        }
    }
}
