/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativebridge.processor.test.references;

import org.graalvm.nativebridge.ByLocalReference;
import org.graalvm.nativebridge.ByRemoteReference;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.GenerateNativeToHotSpotBridge;
import org.graalvm.nativebridge.In;
import org.graalvm.nativebridge.Out;

import java.util.List;

@GenerateNativeToHotSpotBridge(factory = ForeignServiceFactory.class)
abstract class ForeignReferenceArrayOperationsOppositeDirection implements ReferenceArrayOperations, ForeignObject {

    @Override
    public abstract void acceptHost(@ByRemoteReference(ForeignHandler.class) Handler[] handlers);

    @Override
    public abstract void acceptHostSubArray(@In(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @ByRemoteReference(ForeignHandler.class) Handler[] handlers, int offset, int length);

    @Override
    public abstract void fillHost(@Out @ByRemoteReference(ForeignHandler.class) Handler[] handlers);

    @Override
    public abstract int fillHostSubArray(@Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length", trimToResult = true) @ByRemoteReference(ForeignHandler.class) Handler[] handlers,
                    int offset,
                    int length);

    @Override
    public abstract void exchangeHost(@In @Out @ByRemoteReference(ForeignHandler.class) Handler[] handlers);

    @Override
    public abstract int exchangeHostSubArray(
                    @In(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length", trimToResult = true) @ByRemoteReference(ForeignHandler.class) Handler[] handlers,
                    int offset, int length);

    @Override
    public abstract @ByRemoteReference(ForeignHandler.class) Handler[] getHostObjects();

    @Override
    public abstract void acceptGuest(@ByLocalReference(ForeignRecord.class) Record[] records);

    @Override
    public abstract void acceptGuestWithMarshalledParameter(@ByLocalReference(ForeignRecord.class) Record[] records, List<String> list);

    @Override
    public abstract void acceptGuestSubArray(@In(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @ByLocalReference(ForeignRecord.class) Record[] records, int offset, int length);

    @Override
    public abstract void fillGuest(@Out @ByLocalReference(ForeignRecord.class) Record[] records);

    @Override
    public abstract void fillGuestWithMarshalledParameter(@Out @ByLocalReference(ForeignRecord.class) Record[] records, List<String> list);

    @Override
    public abstract List<String> fillGuestWithMarshalledResult(@Out @ByLocalReference(ForeignRecord.class) Record[] records);

    @Override
    public abstract List<String> fillGuestWithMarshalledResultAndParameter(@Out @ByLocalReference(ForeignRecord.class) Record[] records, List<String> list);

    @Override
    public abstract int fillGuestSubArray(@Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length", trimToResult = true) @ByLocalReference(ForeignRecord.class) Record[] records,
                    int offset,
                    int length);

    @Override
    public abstract List<String> fillGuestSubArrayWithMarshalledResult(@Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @ByLocalReference(ForeignRecord.class) Record[] records,
                    int offset, int length);

    @Override
    public abstract void exchangeGuest(@In @Out @ByLocalReference(ForeignRecord.class) Record[] records);

    @Override
    public abstract void exchangeGuestWithMarshalledParameter(@In @Out @ByLocalReference(ForeignRecord.class) Record[] records, List<String> list);

    @Override
    public abstract List<String> exchangeGuestWithMarshalledResult(@In @Out @ByLocalReference(ForeignRecord.class) Record[] records);

    @Override
    public abstract List<String> exchangeGuestWithMarshalledResultAndParameter(@In @Out @ByLocalReference(ForeignRecord.class) Record[] records, List<String> list);

    @Override
    public abstract int exchangeGuestSubArray(
                    @In(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length", trimToResult = true) @ByLocalReference(ForeignRecord.class) Record[] records,
                    int offset, int length);

    @Override
    public abstract List<String> exchangeGuestSubArrayWithMarshalledResult(
                    @In(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @Out(arrayOffsetParameter = "offset", arrayLengthParameter = "length") @ByLocalReference(ForeignRecord.class) Record[] records,
                    int offset, int length);

    @Override
    @ByLocalReference(ForeignRecord.class)
    public abstract Record[] getGuestObjects();

    @Override
    public abstract void fillHostWithMarshalledInOutParameter(@Out @ByRemoteReference(ForeignHandler.class) Handler[] handlers, @In @Out List<String> list);
}
