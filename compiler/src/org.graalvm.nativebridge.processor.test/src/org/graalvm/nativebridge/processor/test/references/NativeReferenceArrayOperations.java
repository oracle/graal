/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor.test.references;

import org.graalvm.nativebridge.ByReference;
import org.graalvm.nativebridge.GenerateHotSpotToNativeBridge;
import org.graalvm.nativebridge.In;
import org.graalvm.nativebridge.NativeIsolate;
import org.graalvm.nativebridge.NativeObject;
import org.graalvm.nativebridge.Out;
import org.graalvm.nativebridge.processor.test.TestJNIConfig;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import java.util.List;

@GenerateHotSpotToNativeBridge(jniConfig = TestJNIConfig.class, include = CEntryPoint.NotIncludedAutomatically.class)
abstract class NativeReferenceArrayOperations extends NativeObject implements ReferenceArrayOperations {

    NativeReferenceArrayOperations(NativeIsolate isolate, long handle) {
        super(isolate, handle);
    }

    @Override
    public abstract void acceptHost(@ByReference(HSHandler.class) Handler[] handlers);

    @Override
    public abstract void acceptHostSubArray(@In(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @ByReference(HSHandler.class) Handler[] handlers, int offset, int length);

    @Override
    public abstract void fillHost(@Out @ByReference(HSHandler.class) Handler[] handlers);

    @Override
    public abstract int fillHostSubArray(@Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length", trimToResult = true) @ByReference(HSHandler.class) Handler[] handlers, int offset,
                    int length);

    @Override
    public abstract void exchangeHost(@In @Out @ByReference(HSHandler.class) Handler[] handlers);

    @Override
    public abstract int exchangeHostSubArray(
                    @In(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length", trimToResult = true) @ByReference(HSHandler.class) Handler[] handlers,
                    int offset, int length);

    @Override
    @ByReference(HSHandler.class)
    public abstract Handler[] getHostObjects();

    @Override
    public abstract void acceptGuest(@ByReference(NativeRecord.class) Record[] records);

    @Override
    public abstract void acceptGuestWithMarshalledParameter(@ByReference(NativeRecord.class) Record[] records, List<String> list);

    @Override
    public abstract void acceptGuestSubArray(@In(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @ByReference(NativeRecord.class) Record[] records, int offset, int length);

    @Override
    public abstract void fillGuest(@Out @ByReference(NativeRecord.class) Record[] records);

    @Override
    public abstract void fillGuestWithMarshalledParameter(@Out @ByReference(NativeRecord.class) Record[] records, List<String> list);

    @Override
    public abstract List<String> fillGuestWithMarshalledResult(@Out @ByReference(NativeRecord.class) Record[] records);

    @Override
    public abstract List<String> fillGuestWithMarshalledResultAndParameter(@Out @ByReference(NativeRecord.class) Record[] records, List<String> list);

    @Override
    public abstract int fillGuestSubArray(@Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length", trimToResult = true) @ByReference(NativeRecord.class) Record[] records, int offset,
                    int length);

    @Override
    public abstract List<String> fillGuestSubArrayWithMarshalledResult(@Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @ByReference(NativeRecord.class) Record[] records,
                    int offset, int length);

    @Override
    public abstract void exchangeGuest(@In @Out @ByReference(NativeRecord.class) Record[] records);

    @Override
    public abstract void exchangeGuestWithMarshalledParameter(@In @Out @ByReference(NativeRecord.class) Record[] records, List<String> list);

    @Override
    public abstract List<String> exchangeGuestWithMarshalledResult(@In @Out @ByReference(NativeRecord.class) Record[] records);

    @Override
    public abstract List<String> exchangeGuestWithMarshalledResultAndParameter(@In @Out @ByReference(NativeRecord.class) Record[] records, List<String> list);

    @Override
    public abstract int exchangeGuestSubArray(
                    @In(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length", trimToResult = true) @ByReference(NativeRecord.class) Record[] records,
                    int offset, int length);

    @Override
    public abstract List<String> exchangeGuestSubArrayWithMarshalledResult(
                    @In(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @ByReference(NativeRecord.class) Record[] records,
                    int offset, int length);

    @Override
    @ByReference(NativeRecord.class)
    public abstract Record[] getGuestObjects();
}
