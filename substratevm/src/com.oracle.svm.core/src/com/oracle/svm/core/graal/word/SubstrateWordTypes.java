/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.word;

import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.svm.core.util.UserError;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class SubstrateWordTypes extends WordTypes {
    public SubstrateWordTypes(MetaAccessProvider metaAccess, JavaKind wordKind) {
        super(metaAccess, wordKind);
    }

    @Override
    public boolean isWordOperation(ResolvedJavaMethod targetMethod) {
        if (GuardedAnnotationAccess.isAnnotationPresent(targetMethod, InvokeCFunctionPointer.class)) {
            return false; // handled by CInterfaceInvocationPlugin
        }
        return super.isWordOperation(targetMethod);
    }

    @Override
    public ResolvedJavaMethod getWordOperation(ResolvedJavaMethod targetMethod, ResolvedJavaType callingContextType) {
        ResolvedJavaMethod wordOperation = super.getWordOperation(targetMethod, callingContextType);
        if (wordOperation == null) {
            UserError.abort("Could not determine the implementation of word operation " + targetMethod.format("%H.%n(%p)" +
                            ". Check the use of annotations in your Java/C interface type declarations."));
        }
        return wordOperation;
    }
}
