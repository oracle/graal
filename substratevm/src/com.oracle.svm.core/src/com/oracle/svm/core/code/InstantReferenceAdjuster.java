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
package com.oracle.svm.core.code;

import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

/**
 * Immediately writes object references and fails if it cannot do so.
 */
public class InstantReferenceAdjuster implements ReferenceAdjuster {
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T> void setObjectInArray(NonmovableObjectArray<T> array, int index, T object) {
        NonmovableArrays.setObject(array, index, object);
    }

    @Override
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T> void setConstantTargetInArray(NonmovableObjectArray<T> array, int index, SubstrateObjectConstant constant) {
        NonmovableArrays.setObject(array, index, (T) ((DirectSubstrateObjectConstant) constant).getObject());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setConstantTargetAt(PointerBase address, int length, SubstrateObjectConstant constant) {
        ReferenceAdjuster.writeReference((Pointer) address, length, ((DirectSubstrateObjectConstant) constant).getObject());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T> NonmovableObjectArray<T> copyOfObjectArray(T[] source) {
        return NonmovableArrays.copyOfObjectArray(source);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isFinished() {
        return true;
    }
}
