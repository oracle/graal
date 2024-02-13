/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.meta;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/** An object constant that holds a direct reference to the object. */
public final class DirectSubstrateObjectConstant extends SubstrateObjectConstant {

    /** The raw object wrapped by this constant. */
    private final Object object;
    /**
     * The identity hash code for this constant. It may or may not be the same as of the identity
     * hashcode of the object. When the constant is used during image build the value is provided
     * via {@link IdentityHashCodeProvider}. When used for run time JIT compilation the initial
     * value is 0, and it is computed lazily.
     */
    private int identityHashCode;

    DirectSubstrateObjectConstant(Object object, boolean compressed, int identityHashCode) {
        super(compressed);
        this.object = object;
        this.identityHashCode = identityHashCode;
        assert object != null;
        if (SubstrateUtil.isInLibgraal()) {
            throw new InternalError();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Object getObject() {
        return object;
    }

    @Override
    public ResolvedJavaType getType(MetaAccessProvider provider) {
        return provider.lookupJavaType(object.getClass());
    }

    @Override
    public SubstrateObjectConstant compress() {
        assert !compressed;
        return new DirectSubstrateObjectConstant(object, true, identityHashCode);
    }

    @Override
    public SubstrateObjectConstant uncompress() {
        assert compressed;
        return new DirectSubstrateObjectConstant(object, false, identityHashCode);
    }

    @Override
    public int getIdentityHashCode() {
        if (identityHashCode == 0) {
            VMError.guarantee(!SubstrateUtil.HOSTED);
            identityHashCode = computeIdentityHashCode(object);
        }
        return identityHashCode;
    }

    @Override
    public String toValueString() {
        Object obj = object;
        if (obj instanceof String) {
            return (String) obj;
        }
        return obj.getClass().getName();
    }
}
