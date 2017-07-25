/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.truffle.hotspot;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.truffle.substitutions.KnownTruffleFields;

public class HotSpotKnownTruffleFields extends KnownTruffleFields {

    public final ResolvedJavaType classWeakReference;
    public final ResolvedJavaType classSoftReference;
    public final ResolvedJavaField referenceReferent;

    public HotSpotKnownTruffleFields(MetaAccessProvider metaAccess) {
        super(metaAccess);

        try {
            classWeakReference = metaAccess.lookupJavaType(WeakReference.class);
            classSoftReference = metaAccess.lookupJavaType(SoftReference.class);
            referenceReferent = metaAccess.lookupJavaField(Reference.class.getDeclaredField("referent"));
        } catch (NoSuchFieldException ex) {
            throw GraalError.shouldNotReachHere(ex);
        }
    }

}
