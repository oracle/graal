/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Disallowed;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.hosted.webimage.wasm.gc.MemoryLayout;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = Disallowed.class)
@AutomaticallyRegisteredImageSingleton(StackOverflowCheck.PlatformSupport.class)
@Platforms(WebImageWasmLMPlatform.class)
final class WebImageWasmLMStackOverflowSupport implements StackOverflowCheck.PlatformSupport {
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean lookupStack(WordPointer stackBasePtr, WordPointer stackEndPtr) {
        stackBasePtr.write(MemoryLayout.getStackBase());
        stackEndPtr.write(MemoryLayout.getStackBottom());
        return true;
    }
}
