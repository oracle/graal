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

import org.graalvm.wasm.parser.validation.collections.ExtraDataFormatHelper;

/**
 * Represents an entry in the extra data list.
 */
public abstract class ExtraDataEntry {
    private final ExtraDataFormatHelper formatHelper;
    private boolean compact;

    protected ExtraDataEntry(ExtraDataFormatHelper formatHelper) {
        this.compact = true;
        this.formatHelper = formatHelper;
    }

    /**
     * Extend the internal format of the entry.
     */
    protected void extendFormat() {
        this.compact = false;
        formatHelper.extendExtraDataFormat();
    }

    /**
     * Generates compact data for this entry in the extra data array.
     * 
     * @param extraData The extra data array
     * @param entryOffset The offset in the extra data
     * @return The offset after the generated entry
     */
    protected abstract int generateCompactData(int[] extraData, int entryOffset);

    /**
     * Generates extended data for this entry in the extra data array.
     * 
     * @param extraData The extra data array
     * @param entryOffset The offset in the extra data
     * @return The offset after the generated entry
     */
    protected abstract int generateExtendedData(int[] extraData, int entryOffset);

    /**
     * Generates data for this entry in the extra data array.
     * 
     * @param extraData The extra data array.
     * @param entryOffset The offset in the extra data
     * @return The offset after the generated entry in the extra data array
     */
    public int generateData(int[] extraData, int entryOffset) {
        if (this.compact) {
            return generateCompactData(extraData, entryOffset);
        }
        return generateExtendedData(extraData, entryOffset);
    }

    /**
     * @return The compact size of this entry in the extra data array
     */
    protected abstract int compactLength();

    /**
     * @return The extended size of this entry in the extra data array
     */
    protected abstract int extendedLength();

    /**
     * @return The size of this entry in the extra data array
     */
    public int length() {
        if (this.compact) {
            return compactLength();
        } else {
            return extendedLength();
        }
    }
}
