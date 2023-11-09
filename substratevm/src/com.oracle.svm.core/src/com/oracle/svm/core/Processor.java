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
package com.oracle.svm.core;

import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;

@AutomaticallyRegisteredImageSingleton
public class Processor {
    private int lastQueriedActiveProcessorCount = -1;

    @Fold
    public static Processor singleton() {
        return ImageSingletons.lookup(Processor.class);
    }

    public int getActiveProcessorCount() {
        VMError.guarantee(!SubstrateUtil.HOSTED, "must not be executed during the image build");

        int result = getActiveProcessorCount0();
        lastQueriedActiveProcessorCount = result;
        return result;
    }

    private static int getActiveProcessorCount0() {
        int optionValue = SubstrateOptions.ActiveProcessorCount.getValue();
        if (optionValue > 0) {
            return optionValue;
        }

        if (SubstrateOptions.MultiThreaded.getValue()) {
            return Containers.activeProcessorCount();
        } else {
            return 1;
        }
    }

    public int getLastQueriedActiveProcessorCount() {
        return lastQueriedActiveProcessorCount;
    }
}
