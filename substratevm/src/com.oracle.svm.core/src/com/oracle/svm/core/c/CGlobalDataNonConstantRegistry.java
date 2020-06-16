/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c;

import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * The following class is a helper registry, that contains only CGlobalDataInfo for
 * non-constant symbols defining during image generation. This object will be burned
 * into a native image.
 */
public class CGlobalDataNonConstantRegistry {

    private final EconomicMap<CGlobalDataImpl<?>, CGlobalDataInfo> cGlobalDataInfos = EconomicMap.create(Equivalence.IDENTITY);

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Lock lock = new ReentrantLock();

    public CGlobalDataInfo getCGlobalDataInfo(CGlobalDataImpl<?> cGlobalData) {
        return cGlobalDataInfos.get(cGlobalData);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerNonConstantSymbol(CGlobalDataInfo cGlobalDataInfo) {
        lock.lock();
        try {
            cGlobalDataInfos.put(cGlobalDataInfo.getData(), cGlobalDataInfo);
        } finally {
            lock.unlock();
        }
    }
}
