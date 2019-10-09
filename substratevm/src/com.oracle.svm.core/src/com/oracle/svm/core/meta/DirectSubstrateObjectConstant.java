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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/** An object constant that holds a direct reference to the object. */
public final class DirectSubstrateObjectConstant extends SubstrateObjectConstant {

    @Platforms(Platform.HOSTED_ONLY.class) //
    private static final AtomicReferenceFieldUpdater<DirectSubstrateObjectConstant, Object> ROOT_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(DirectSubstrateObjectConstant.class, Object.class, "root");

    /** The raw object wrapped by this constant. */
    private final Object object;

    /**
     * An object specifying the origin of this constant. This value is used to distinguish between
     * various constants of the same type. Only objects coming from static final fields and
     * from @Fold annotations processing have a root. The static final field originated objects use
     * the field itself as a root while the @Fold originated objects use the folded method as a
     * root. The subtree of a root object shares the same root information as the root object, i.e.,
     * the root information is transiently passed to the statically reachable objects. Other
     * constants, embedded in the code, might not have a root. The root is only used at image build
     * time.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private volatile Object root;

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
    public boolean setRoot(Object newRoot) {
        if (root == null && newRoot != null) {
            /*
             * It is possible that the same constant is reached on paths from different roots. We
             * can only register one, we choose the first one.
             */
            return ROOT_UPDATER.compareAndSet(this, null, newRoot);
        }
        return false;
    }

    @Override
    public Object getRoot() {
        return root;
    }

    @Override
    protected int getIdentityHashCode() {
        return System.identityHashCode(object);
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
