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
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/** An object constant that holds a direct reference to the object. */
public final class DirectSubstrateObjectConstant extends SubstrateObjectConstant {

    /** The raw object wrapped by this constant. */
    private final Object object;

    DirectSubstrateObjectConstant(Object object, boolean compressed) {
        super(compressed);
        this.object = object;
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
        return new DirectSubstrateObjectConstant(object, true);
    }

    @Override
    public SubstrateObjectConstant uncompress() {
        assert compressed;
        return new DirectSubstrateObjectConstant(object, false);
    }

    @Override
    public int getIdentityHashCode() {
        return computeIdentityHashCode(object);
    }

    @Override
    public String toValueString() {
        Object obj = KnownIntrinsics.convertUnknownValue(object, Object.class);
        if (obj instanceof String) {
            return (String) obj;
        }
        return obj.getClass().getName();
    }
}
