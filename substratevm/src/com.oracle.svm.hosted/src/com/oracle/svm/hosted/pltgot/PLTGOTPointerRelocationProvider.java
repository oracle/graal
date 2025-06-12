/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pltgot;

import java.util.function.Predicate;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.MethodPointerRelocationProvider;
import com.oracle.svm.hosted.meta.HostedMethod;

/**
 * Emits method pointer relocations in the image object file that take PLT/GOT into account.
 *
 * For methods invoked through PLT/GOT, unless overridden explicitly, emits relocations that point
 * to the generated PLT stub.
 */
public class PLTGOTPointerRelocationProvider extends MethodPointerRelocationProvider {

    private final Predicate<SharedMethod> shouldMarkRelocationToPLTStub;
    private final PLTSectionSupport pltSectionSupport;

    public PLTGOTPointerRelocationProvider(Predicate<SharedMethod> shouldMarkRelocationToPLTStub) {
        this.shouldMarkRelocationToPLTStub = shouldMarkRelocationToPLTStub;
        this.pltSectionSupport = HostedPLTGOTConfiguration.singleton().getPLTSectionSupport();
    }

    @Override
    public void markMethodPointerRelocation(ObjectFile.ProgbitsSectionImpl section, int offset, ObjectFile.RelocationKind relocationKind, HostedMethod target, long addend,
                    MethodPointer methodPointer, boolean isInjectedNotCompiled) {
        if (methodPointer.permitsRewriteToPLT() && shouldMarkRelocationToPLTStub.test(target)) {
            pltSectionSupport.markRelocationToPLTStub(section, offset, relocationKind, target, addend);
        } else {
            super.markMethodPointerRelocation(section, offset, relocationKind, target, addend, methodPointer, isInjectedNotCompiled);
        }
    }

    @Override
    public void markMethodOffsetRelocation(ObjectFile.ProgbitsSectionImpl section, int offset, ObjectFile.RelocationKind relocationKind, HostedMethod target, long addend, MethodOffset methodOffset,
                    boolean isInjectedNotCompiled) {
        throw VMError.shouldNotReachHere("not implemented");
    }
}
