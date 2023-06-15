/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

import jdk.internal.foreign.abi.CapturableState;

/**
 * In order to honor the user requested captures, we need some library supports. These should
 * seemingly always be supported if HotSpot thinks so, but we still inject a check in case HotSpot
 * ever turns out to be incorrect, so that the issue is clear.
 */
@TargetClass(className = "jdk.internal.foreign.abi.CapturableState")
public final class Target_jdk_internal_foreign_abi_CapturableState {
    @Alias private boolean isSupported;

    @Alias
    public boolean isSupported() {
        CapturableState self = SubstrateUtil.cast(this, CapturableState.class);
        if (this.isSupported) {
            VMError.guarantee(
                            AbiUtils.singleton().captureIsSupported(self),
                            "%s should be a capturable state on %s, but is somehow not supported.",
                            self.stateName(),
                            AbiUtils.singleton().name());
            return true;
        } else {
            return false;
        }
    }
}
