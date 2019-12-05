/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c.function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Isolates.CreateIsolateParameters;
import org.graalvm.nativeimage.Isolates.IsolateException;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.IsolateSupport;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.function.CEntryPointNativeFunctions.IsolateThreadPointer;
import com.oracle.svm.core.option.SubstrateOptionsParser;

public final class IsolateSupportImpl implements IsolateSupport {
    private static final String ISOLATES_DISABLED_MESSAGE = "Spawning of multiple isolates is disabled, use " +
                    SubstrateOptionsParser.commandArgument(SubstrateOptions.SpawnIsolates, "+") + " option.";

    static void initialize() {
        ImageSingletons.add(IsolateSupport.class, new IsolateSupportImpl());
    }

    private IsolateSupportImpl() {
    }

    @Override
    public IsolateThread createIsolate(CreateIsolateParameters parameters) throws IsolateException {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            throw new IsolateException(ISOLATES_DISABLED_MESSAGE);
        }

        try (CTypeConversion.CCharPointerHolder auxImagePath = CTypeConversion.toCString(parameters.getAuxiliaryImagePath())) {
            CEntryPointCreateIsolateParameters params = StackValue.get(CEntryPointCreateIsolateParameters.class);
            params.setReservedSpaceSize(parameters.getReservedAddressSpaceSize());
            params.setAuxiliaryImagePath(auxImagePath.get());
            params.setAuxiliaryImageReservedSpaceSize(parameters.getAuxiliaryImageReservedSpaceSize());
            params.setVersion(2);

            IsolateThreadPointer isolateThreadPtr = StackValue.get(IsolateThreadPointer.class);
            throwOnError(CEntryPointNativeFunctions.createIsolate(params, WordFactory.nullPointer(), isolateThreadPtr));
            return isolateThreadPtr.read();
        }
    }

    @Override
    public IsolateThread attachCurrentThread(Isolate isolate) throws IsolateException {
        IsolateThreadPointer isolateThread = StackValue.get(IsolateThreadPointer.class);
        throwOnError(CEntryPointNativeFunctions.attachThread(isolate, isolateThread));
        return isolateThread.read();
    }

    @Override
    public IsolateThread getCurrentThread(Isolate isolate) throws IsolateException {
        return CEntryPointNativeFunctions.getCurrentThread(isolate);
    }

    @Override
    public Isolate getIsolate(IsolateThread thread) throws IsolateException {
        return CEntryPointNativeFunctions.getIsolate(thread);
    }

    @Override
    public void detachThread(IsolateThread thread) throws IsolateException {
        throwOnError(CEntryPointNativeFunctions.detachThread(thread));
    }

    @Override
    public void tearDownIsolate(IsolateThread thread) throws IsolateException {
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            throwOnError(CEntryPointNativeFunctions.tearDownIsolate(thread));
        } else {
            throw new IsolateException(ISOLATES_DISABLED_MESSAGE);
        }
    }

    private static void throwOnError(int code) {
        if (code != CEntryPointErrors.NO_ERROR) {
            String message = CEntryPointErrors.getDescription(code);
            throw new IsolateException(message);
        }
    }
}

@AutomaticFeature
class IsolateSupportFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        IsolateSupportImpl.initialize();
    }
}
