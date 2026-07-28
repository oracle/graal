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
package com.oracle.svm.interpreter;

import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.espresso.shared.verifier.VerificationException;
import com.oracle.svm.espresso.shared.verifier.Verifier;
import com.oracle.svm.interpreter.metadata.CremaResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;

/**
 * Helper class calling the shared implementation of the
 * {@link com.oracle.svm.espresso.shared.verifier.Verifier method verifier}.
 * <p>
 * Also checks that a class' super type is not declared as
 * {@link com.oracle.svm.espresso.classfile.Constants#ACC_FINAL final}
 */
public final class CremaVerifier {
    private CremaVerifier() {
    }

    public static void verifyClass(CremaResolvedObjectType type) {
        if (!RuntimeClassLoading.Options.ClassVerification.getValue().needsVerification(type.getClassLoader())) {
            return;
        }
        InterpreterUtil.assertion(type.getSuperClass() == null || !type.getSuperClass().isFinalFlagSet(), "super type final flag check should have been checked at class creation time.");
        for (InterpreterResolvedJavaMethod m : type.getDeclaredMethods(false)) {
            if (!m.isInternal()) {
                verify(m);
            }
        }
    }

    private static void verify(InterpreterResolvedJavaMethod method) {
        try {
            Verifier.verify(CremaRuntimeAccess.getInstance(), method);
        } catch (VerificationException e) {
            String message = String.format("Verification for class `%s` failed for method `%s` with message `%s`",
                            method.getDeclaringClass().toClassName(),
                            method.getName(),
                            e.getMessage());
            switch (e.kind()) {
                case Verify:
                    throw new VerifyError(message);
                case ClassFormat:
                    throw new ClassFormatError(message);
            }
        }
    }
}
