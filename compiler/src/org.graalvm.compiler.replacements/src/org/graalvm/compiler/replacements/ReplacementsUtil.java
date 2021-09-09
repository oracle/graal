/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.replacements.nodes.AssertionNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

// JaCoCo Exclude
public final class ReplacementsUtil {
    private ReplacementsUtil() {
        // empty
    }

    public static final boolean REPLACEMENTS_ASSERTIONS_ENABLED = Assertions.assertionsEnabled();

    /**
     * Asserts that condition evaluates to true by the time compilation is finished. This is
     * intended to be used within snippets or stubs, and will lead to a compile error if the
     * assertion fails.
     */
    public static void staticAssert(boolean condition, String message) {
        if (REPLACEMENTS_ASSERTIONS_ENABLED) {
            AssertionNode.staticAssert(condition, message);
        }
    }

    public static void staticAssert(boolean condition, String message, Object arg1) {
        if (REPLACEMENTS_ASSERTIONS_ENABLED) {
            AssertionNode.staticAssert(condition, message, arg1, "");
        }
    }

    /**
     * Asserts that condition evaluates to true at runtime. This is intended to be used within
     * snippets or stubs, and will lead to a VM error if it fails.
     */
    public static void dynamicAssert(boolean condition, String message) {
        if (REPLACEMENTS_ASSERTIONS_ENABLED) {
            AssertionNode.dynamicAssert(condition, message);
        }
    }

    @Fold
    public static int arrayIndexScale(@InjectedParameter MetaAccessProvider metaAccessProvider, JavaKind elementKind) {
        return metaAccessProvider.getArrayIndexScale(elementKind);
    }

    @Fold
    public static int getArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccessProvider, JavaKind elementKind) {
        return metaAccessProvider.getArrayBaseOffset(elementKind);
    }

    @Fold
    public static int charArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayBaseOffset(JavaKind.Char);
    }

    @Fold
    public static int charArrayIndexScale(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayIndexScale(JavaKind.Char);
    }

    @Fold
    public static int byteArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayBaseOffset(JavaKind.Byte);
    }

    @Fold
    public static int byteArrayIndexScale(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayIndexScale(JavaKind.Byte);
    }

}
