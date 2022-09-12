/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.parser.validation.collections.entries;

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.validation.collections.ExtraDataFormatHelper;
import org.graalvm.wasm.util.ExtraDataUtil;

public class LocalOpEntry extends ExtraDataEntry {
    private final int localIndex;
    private final int typeIndicator;
    private final int valueLength;

    public LocalOpEntry(int localIndex, byte valueType, int valueLength, ExtraDataFormatHelper formatHelper) {
        super(formatHelper);
        this.localIndex = localIndex;
        this.typeIndicator = ExtraDataUtil.getTypeIndicator(valueType);
        this.valueLength = valueLength;
        assert !ExtraDataUtil.exceeds2BitValue(this.typeIndicator) : "Invalid value type";
        if (ExtraDataUtil.exceedsUnsignedShortValueWithIndicator(localIndex)) {
            if (ExtraDataUtil.exceedsUnsignedIntValueWithIndicator(localIndex)) {
                throw WasmException.create(Failure.NON_REPRESENTABLE_EXTRA_DATA_VALUE);
            }
            extendFormat();
        }
    }

    @Override
    protected int generateCompactData(int[] extraData, int entryOffset) {
        int offset = entryOffset;
        offset += ExtraDataUtil.addCompactLocalOp(extraData, offset, typeIndicator, valueLength, localIndex);
        return offset;
    }

    @Override
    protected int generateExtendedData(int[] extraData, int entryOffset) {
        int offset = entryOffset;
        offset += ExtraDataUtil.addExtendedLocalOp(extraData, offset, typeIndicator, valueLength, localIndex);
        return offset;
    }

    @Override
    protected int compactLength() {
        return ExtraDataUtil.COMPACT_LOCAL_OP_SIZE;
    }

    @Override
    protected int extendedLength() {
        return ExtraDataUtil.EXTENDED_LOCAL_OP_SIZE;
    }
}
