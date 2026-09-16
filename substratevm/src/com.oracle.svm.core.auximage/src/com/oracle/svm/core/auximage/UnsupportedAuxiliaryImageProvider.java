/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import static org.graalvm.word.impl.Word.unsigned;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.IsolateArgumentAccess;
import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.IsolateArguments;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.os.AuxiliaryImageProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
class UnsupportedAuxiliaryImageProvider implements AuxiliaryImageProvider {
    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initializeHeapAddressRange(IsolateArguments arguments, Pointer reservedBegin, UnsignedWord reservedSize, Pointer imageHeapEnd, WordPointer collectedHeapBeginOut) {
        CCharPointer auxImagePath = IsolateArgumentAccess.readCCharPointer(arguments, IsolateArgumentParser.getOptionIndex(SubstrateOptions.AuxiliaryImagePathIsolateArgument));
        UnsignedWord auxImageReserved = unsigned(IsolateArgumentAccess.readLong(arguments, IsolateArgumentParser.getOptionIndex(SubstrateOptions.AuxiliaryImageBytesIsolateArgument)));
        if (auxImagePath.isNonNull() || auxImageReserved.notEqual(0)) {
            return CEntryPointErrors.AUX_IMAGE_UNSUPPORTED;
        }

        assert PointerUtils.isAMultiple(imageHeapEnd, VirtualMemoryProvider.get().getGranularity());
        collectedHeapBeginOut.write(imageHeapEnd);
        return CEntryPointErrors.NO_ERROR;
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int loadAuxiliaryImage(Pointer reservedAddressSpace, UnsignedWord reservedSize, CCharPointer filePath, WordPointer basePointer, WordPointer endPointer) {
        return CEntryPointErrors.AUX_IMAGE_UNSUPPORTED;
    }

    @Override
    public int unloadAuxiliaryImage(Pointer base, Pointer end) {
        return CEntryPointErrors.AUX_IMAGE_UNSUPPORTED;
    }
}
