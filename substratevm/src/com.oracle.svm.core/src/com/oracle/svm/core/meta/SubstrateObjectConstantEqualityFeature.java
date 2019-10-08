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
package com.oracle.svm.core.meta;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.VMError;

final class SubstrateObjectConstantEquality implements ObjectConstantEquality {
    @Override
    public boolean test(SubstrateObjectConstant x, SubstrateObjectConstant y) {
        if (x == y) {
            return true;
        } else if (x instanceof DirectSubstrateObjectConstant && y instanceof DirectSubstrateObjectConstant) {
            return ((DirectSubstrateObjectConstant) x).getObject() == ((DirectSubstrateObjectConstant) y).getObject();
        }
        throw VMError.shouldNotReachHere("Unknown object constants: " + x + " and " + y);
    }
}

@AutomaticFeature
final class SubstrateObjectConstantEqualityFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!ImageSingletons.contains(ObjectConstantEquality.class)) {
            ImageSingletons.add(ObjectConstantEquality.class, new SubstrateObjectConstantEquality());
        }
    }
}
