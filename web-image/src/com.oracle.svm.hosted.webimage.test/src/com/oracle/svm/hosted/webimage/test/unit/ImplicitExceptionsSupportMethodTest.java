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

package com.oracle.svm.hosted.webimage.test.unit;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.svm.hosted.webimage.codegen.WebImageImplicitExceptionsFeature;
import com.oracle.svm.webimage.functionintrinsics.ImplicitExceptions;

import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;

/**
 * Checks that every {@link BytecodeExceptionKind} has a usable support method in
 * {@link ImplicitExceptions}.
 * <p>
 * {@link WebImageImplicitExceptionsFeature#getSupportMethodName} throws for unmapped kinds, and
 * mismatched arities are only detected once a
 * {@link jdk.graal.compiler.nodes.extended.BytecodeExceptionNode} of that kind actually shows up in
 * the analyzed graphs. Both failure modes therefore only surface for applications that happen to
 * contain the offending node, which is how the unmapped
 * {@link BytecodeExceptionKind#UNSTRUCTURED_LOCKING} went unnoticed. Going over the enum here
 * catches a new or changed kind at test time instead.
 * <p>
 * The methods are only inspected reflectively, never invoked, so that
 * {@link ImplicitExceptions}'s class initializer (which registers foreign calls) does not have to
 * run outside of an image build.
 */
@RunWith(Parameterized.class)
public class ImplicitExceptionsSupportMethodTest {

    @Parameterized.Parameters(name = "{0}")
    public static BytecodeExceptionKind[] data() {
        return BytecodeExceptionKind.values();
    }

    @Parameterized.Parameter public BytecodeExceptionKind kind;

    @Test
    public void supportMethodIsUsable() {
        String methodName = WebImageImplicitExceptionsFeature.getSupportMethodName(kind);
        Assert.assertNotNull("No support method name for " + kind, methodName);

        List<Method> candidates = new ArrayList<>();
        for (Method method : ImplicitExceptions.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                candidates.add(method);
            }
        }

        Assert.assertEquals(ImplicitExceptions.class.getName() + "." + methodName + " for " + kind + " must exist exactly once", 1, candidates.size());

        Method supportMethod = candidates.get(0);
        Assert.assertTrue(supportMethod + " must be public", Modifier.isPublic(supportMethod.getModifiers()));
        Assert.assertTrue(supportMethod + " must be static", Modifier.isStatic(supportMethod.getModifiers()));
        Assert.assertTrue(supportMethod + " must return a Throwable for " + kind, Throwable.class.isAssignableFrom(supportMethod.getReturnType()));
        Assert.assertEquals(supportMethod + " must take the arguments of " + kind, kind.getNumArguments(), supportMethod.getParameterCount());
    }
}
