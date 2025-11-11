/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.util.BasedOnJDKFile;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * Holds all JFR-related options that can be set by the user. It is also used to validate and adjust
 * the option values as needed.
 *
 * Similar to OpenJDK, the options set via -XX:FlightRecorderOptions cannot be changed after JFR is
 * initialized. This means that after {@link SubstrateJVM#createJFR(boolean)} is called, this class
 * is no longer needed.
 *
 * This class is used to store options set at the OpenJDK Java-level which propagate down to the VM
 * level via {@link jdk.jfr.internal.JVM}. The option values are stored here until they are
 * eventually used when first recording is created and JFR is initialized.
 */
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/jdk.jfr/share/classes/jdk/jfr/internal/Options.java#L48-L55")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/jdk.jfr/share/classes/jdk/jfr/internal/Options.java#L65-L69")
public class JfrOptionSet {
    private static final int MEMORY_SIZE_BIT = 1;
    private static final int GLOBAL_BUFFER_SIZE_BIT = 2;
    private static final int GLOBAL_BUFFER_COUNT_BIT = 4;
    private static final int THREAD_BUFFER_SIZE_BIT = 8;

    /*
     * Use 2.5 MB by default (instead of 10 MB on HotSpot). We explicitly hard-code the default
     * values here to avoid that those values are copied from HotSpot at build-time because
     * completely different values might be set there if JFR is enabled at build-time as well.
     */
    private static final long DEFAULT_GLOBAL_BUFFER_COUNT = 10;
    private static final long DEFAULT_GLOBAL_BUFFER_SIZE = 256 * 1024;
    private static final long DEFAULT_MEMORY_SIZE = DEFAULT_GLOBAL_BUFFER_COUNT * DEFAULT_GLOBAL_BUFFER_SIZE;
    private static final long DEFAULT_THREAD_BUFFER_SIZE = 8 * 1024;
    private static final int DEFAULT_STACK_DEPTH = 64;
    private static final long DEFAULT_MAX_CHUNK_SIZE = 12 * 1024 * 1024;

    private static final long MAX_ADJUSTED_GLOBAL_BUFFER_SIZE = 1 * 1024 * 1024;
    private static final long MIN_ADJUSTED_GLOBAL_BUFFER_SIZE_CUTOFF = 512 * 1024;
    private static final long MIN_GLOBAL_BUFFER_SIZE = 64 * 1024;
    private static final long MIN_GLOBAL_BUFFER_COUNT = 2;
    private static final long MIN_THREAD_BUFFER_SIZE = 4 * 1024;
    private static final long MIN_MEMORY_SIZE = 1024 * 1024;

    public final JfrOptionLong threadBufferSize;
    public final JfrOptionLong globalBufferSize;
    public final JfrOptionLong globalBufferCount;
    public final JfrOptionLong memorySize;
    public final JfrOptionLong maxChunkSize;
    public final JfrOptionLong stackDepth;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOptionSet() {
        threadBufferSize = new JfrOptionLong(DEFAULT_THREAD_BUFFER_SIZE);
        globalBufferSize = new JfrOptionLong(DEFAULT_GLOBAL_BUFFER_SIZE);
        globalBufferCount = new JfrOptionLong(DEFAULT_GLOBAL_BUFFER_COUNT);
        memorySize = new JfrOptionLong(DEFAULT_MEMORY_SIZE);
        maxChunkSize = new JfrOptionLong(DEFAULT_MAX_CHUNK_SIZE);
        stackDepth = new JfrOptionLong(DEFAULT_STACK_DEPTH);
    }

    public void validateAndAdjustMemoryOptions() {
        ensureValidMinimumSizes();
        ensureValidMemoryRelations();
        adjustMemoryOptions();
        assert checkPostCondition();
    }

    private void ensureValidMinimumSizes() {
        if (memorySize.isUserValue() && memorySize.getValue() < MIN_MEMORY_SIZE) {
            throw new IllegalStateException("The value specified for the JFR option 'memorysize' is too low. Please use at least " + MIN_MEMORY_SIZE + " bytes.");
        }
        if (globalBufferSize.isUserValue() && globalBufferSize.getValue() < MIN_GLOBAL_BUFFER_SIZE) {
            throw new IllegalStateException("The value specified for the JFR option 'globalbuffersize' is too low. Please use at least " + MIN_GLOBAL_BUFFER_SIZE + " bytes.");
        }
        if (globalBufferCount.isUserValue() && globalBufferCount.getValue() < MIN_GLOBAL_BUFFER_COUNT) {
            throw new IllegalStateException("The value specified for the JFR option 'globalbuffercount' is too low. Please use at least a value of " + MIN_GLOBAL_BUFFER_COUNT + ".");
        }
        if (threadBufferSize.isUserValue() && threadBufferSize.getValue() < MIN_THREAD_BUFFER_SIZE) {
            throw new IllegalStateException("The value specified for the JFR option 'thread_buffer_size' is too low. Please use at least " + MIN_THREAD_BUFFER_SIZE + " bytes.");
        }
    }

    private void ensureValidMemoryRelations() {
        if (globalBufferSize.isUserValue()) {
            if (memorySize.isUserValue() && memorySize.getValue() < globalBufferSize.getValue()) {
                throw new IllegalStateException("The value of the JFR option 'memorySize' must not be smaller than value of the option 'globalbuffersize'.");
            }
            if (threadBufferSize.isUserValue() && globalBufferSize.getValue() < threadBufferSize.getValue()) {
                throw new IllegalStateException("The value of the JFR option 'globalbuffersize' must not be smaller than the value of the option 'thread_buffer_size'.");
            }
            if (globalBufferCount.isUserValue() && globalBufferCount.getValue() * globalBufferSize.getValue() < MIN_MEMORY_SIZE) {
                throw new IllegalStateException("The total size of all global JFR buffers must not be lower than " + MIN_MEMORY_SIZE + " bytes.");
            }
        }
    }

    @SuppressWarnings("fallthrough")
    private void adjustMemoryOptions() {
        checkForAmbiguity();

        pageSizeAlignUp(memorySize);
        pageSizeAlignUp(globalBufferSize);
        pageSizeAlignUp(threadBufferSize);

        int setOfOptions = 0;
        if (memorySize.isUserValue()) {
            setOfOptions |= MEMORY_SIZE_BIT;
        }
        if (globalBufferSize.isUserValue()) {
            setOfOptions |= GLOBAL_BUFFER_SIZE_BIT;
        }
        if (globalBufferCount.isUserValue()) {
            setOfOptions |= GLOBAL_BUFFER_COUNT_BIT;
        }
        if (threadBufferSize.isUserValue()) {
            setOfOptions |= THREAD_BUFFER_SIZE_BIT;
        }

        switch (setOfOptions) {
            case MEMORY_SIZE_BIT | THREAD_BUFFER_SIZE_BIT:
            case MEMORY_SIZE_BIT:
                memoryAndThreadBufferSize();
                break;
            case MEMORY_SIZE_BIT | GLOBAL_BUFFER_COUNT_BIT:
                memorySizeAndBufferCount();
                break;
            case MEMORY_SIZE_BIT | GLOBAL_BUFFER_SIZE_BIT | THREAD_BUFFER_SIZE_BIT:
                assert threadBufferSize.isUserValue();
                // fall through
            case MEMORY_SIZE_BIT | GLOBAL_BUFFER_SIZE_BIT:
                memorySizeAndGlobalBufferSize();
                break;
            case MEMORY_SIZE_BIT | GLOBAL_BUFFER_SIZE_BIT | GLOBAL_BUFFER_COUNT_BIT:
            case MEMORY_SIZE_BIT | GLOBAL_BUFFER_SIZE_BIT | GLOBAL_BUFFER_COUNT_BIT | THREAD_BUFFER_SIZE_BIT:
                allOptionsSet();
                break;
            case GLOBAL_BUFFER_SIZE_BIT | GLOBAL_BUFFER_COUNT_BIT | THREAD_BUFFER_SIZE_BIT:
                assert globalBufferCount.isUserValue();
                assert threadBufferSize.isUserValue();
                // fall through
            case GLOBAL_BUFFER_SIZE_BIT | GLOBAL_BUFFER_COUNT_BIT:
                assert globalBufferSize.isUserValue();
                // fall through
            case GLOBAL_BUFFER_SIZE_BIT | THREAD_BUFFER_SIZE_BIT:
            case GLOBAL_BUFFER_COUNT_BIT:
            case GLOBAL_BUFFER_SIZE_BIT:
                globalBufferSize();
                break;
            case MEMORY_SIZE_BIT | GLOBAL_BUFFER_COUNT_BIT | THREAD_BUFFER_SIZE_BIT:
                assert memorySize.isUserValue();
                // fall through
            case GLOBAL_BUFFER_COUNT_BIT | THREAD_BUFFER_SIZE_BIT:
                assert globalBufferCount.isUserValue();
                // fall through
            case THREAD_BUFFER_SIZE_BIT:
                threadBufferSize();
                break;
            default:
                defaultSize();
        }
    }

    private void memoryAndThreadBufferSize() {
        assert memorySize.isUserValue();
        assert !globalBufferCount.isUserValue();
        assert !globalBufferSize.isUserValue();

        long pageSize = getPageSize();
        long totalPages = memorySize.getValue() / pageSize;
        long perUnitPages = totalPages / globalBufferCount.getValue();

        long threadBufferPages = threadBufferSize.getValue() / pageSize;

        long maxBufferSizePages = MAX_ADJUSTED_GLOBAL_BUFFER_SIZE / pageSize;
        long minBufferSizePages = totalPages * pageSize < memorySize.getDefaultValue() ? MIN_GLOBAL_BUFFER_SIZE / pageSize : MIN_ADJUSTED_GLOBAL_BUFFER_SIZE_CUTOFF / pageSize;

        perUnitPages = alignBufferSize(perUnitPages, maxBufferSizePages, minBufferSizePages);
        assert perUnitPages % minBufferSizePages == 0;

        long remainder = totalPages % perUnitPages;
        while (remainder >= (perUnitPages >> 1)) {
            if (perUnitPages <= minBufferSizePages) {
                break;
            }
            perUnitPages >>= 1;
            remainder = totalPages % perUnitPages;
        }
        assert perUnitPages * pageSize >= MIN_GLOBAL_BUFFER_SIZE;
        assert (perUnitPages * pageSize) % MIN_GLOBAL_BUFFER_SIZE == 0;

        if (threadBufferSize.isUserValue() && threadBufferPages > perUnitPages) {
            perUnitPages = threadBufferPages;
        }

        long units = totalPages / perUnitPages;
        long rem = totalPages % perUnitPages;
        if (rem > 0) {
            totalPages -= rem % units;
            perUnitPages += rem / units;
        }

        globalBufferCount.setValue(units);
        globalBufferSize.setValue(perUnitPages * pageSize);
        memorySize.setValue(totalPages * pageSize);
        threadBufferSize.setValue(threadBufferPages * pageSize);
    }

    private static long alignBufferSize(long bufferSizeInPages, long maxSizePages, long minSizePages) {
        long result = bufferSizeInPages;
        result = Math.min(result, maxSizePages);
        result = Math.max(result, minSizePages);
        long multiples = 0;
        if (result < maxSizePages) {
            while (result >= (minSizePages << multiples)) {
                ++multiples;
            }
            result = minSizePages << multiples;
        }
        assert result >= minSizePages && result <= maxSizePages;
        return result;
    }

    private void memorySizeAndBufferCount() {
        assert memorySize.isUserValue();
        assert !globalBufferSize.isUserValue();
        assert !threadBufferSize.isUserValue();
        assert globalBufferCount.isUserValue();
        setValuesUsingDivTotalByUnits();
    }

    private void memorySizeAndGlobalBufferSize() {
        assert memorySize.isUserValue();
        assert globalBufferSize.isUserValue();
        assert !globalBufferCount.isUserValue();
        setValuesUsingDivTotalByPerUnit();
        if (threadBufferSize.getValue() > globalBufferSize.getValue()) {
            globalBufferSize.setValue(threadBufferSize.getValue());
            setValuesUsingDivTotalByPerUnit();
        }
    }

    private void allOptionsSet() {
        setValuesUsingDivTotalByPerUnit();
        if (threadBufferSize.getValue() > globalBufferSize.getValue()) {
            globalBufferSize.setValue(threadBufferSize.getValue());
            setValuesUsingDivTotalByPerUnit();
        }
    }

    private void globalBufferSize() {
        assert !memorySize.isUserValue();
        if (threadBufferSize.getValue() > globalBufferSize.getValue()) {
            globalBufferSize.setValue(threadBufferSize.getValue());
        }
        memorySize.setValue(globalBufferSize.getValue() * globalBufferCount.getValue());
    }

    private void threadBufferSize() {
        assert !globalBufferSize.isUserValue();
        assert threadBufferSize.isUserValue();
        setValuesUsingDivTotalByUnits();
        if (threadBufferSize.getValue() > globalBufferSize.getValue()) {
            globalBufferSize.setValue(threadBufferSize.getValue());
            setValuesUsingDivTotalByPerUnit();
        }
    }

    private void defaultSize() {
        assert !threadBufferSize.isUserValue();
        assert !memorySize.isUserValue();
        assert !globalBufferCount.isUserValue();
        assert !globalBufferSize.isUserValue();
        // nothing to do
    }

    private void setValuesUsingDivTotalByUnits() {
        long pageSize = getPageSize();
        long totalPages = memorySize.getValue() / pageSize;
        long perUnitPages = totalPages <= globalBufferCount.getValue() ? 1 : totalPages / globalBufferCount.getValue();
        long units = totalPages / perUnitPages;
        long rem = totalPages % perUnitPages;
        if (rem > 0) {
            totalPages -= rem % units;
            perUnitPages += rem / units;
        }

        memorySize.setValue(totalPages * pageSize);
        globalBufferCount.setValue(units);
        globalBufferSize.setValue(perUnitPages * pageSize);
    }

    private void setValuesUsingDivTotalByPerUnit() {
        long pageSize = getPageSize();
        long totalPages = memorySize.getValue() / pageSize;
        long perUnitPages = globalBufferSize.getValue() / pageSize;
        long units = totalPages / perUnitPages;
        long rem = totalPages % perUnitPages;
        if (rem > 0) {
            totalPages -= rem % units;
            perUnitPages += rem / units;
        }

        memorySize.setValue(totalPages * pageSize);
        globalBufferCount.setValue(units);
        globalBufferSize.setValue(perUnitPages * pageSize);
    }

    private void checkForAmbiguity() {
        if (memorySize.isUserValue() && globalBufferSize.isUserValue() && globalBufferCount.isUserValue() && globalBufferSize.getValue() * globalBufferCount.getValue() != memorySize.getValue()) {
            throw new IllegalStateException(
                            "The values specified for the JFR options 'memorySize', 'globalbuffersize', and 'globalbuffercount' are causing an ambiguity when trying to determine how much memory to use. " +
                                            "Try to remove one of the involved options or make sure they are unambiguous.");
        }
    }

    private boolean checkPostCondition() {
        long pageSize = getPageSize();
        assert memorySize.getValue() >= MIN_MEMORY_SIZE;
        assert memorySize.getValue() % pageSize == 0;
        assert memorySize.getValue() % globalBufferCount.getValue() == 0;
        assert memorySize.getValue() / globalBufferCount.getValue() == globalBufferSize.getValue();

        assert globalBufferSize.getValue() >= MIN_GLOBAL_BUFFER_SIZE;
        assert globalBufferSize.getValue() % pageSize == 0;
        assert globalBufferSize.getValue() >= threadBufferSize.getValue();

        assert globalBufferCount.getValue() >= MIN_GLOBAL_BUFFER_COUNT;
        assert globalBufferCount.getValue() * globalBufferSize.getValue() == memorySize.getValue();

        assert threadBufferSize.getValue() >= MIN_THREAD_BUFFER_SIZE;
        assert threadBufferSize.getValue() % pageSize == 0;

        return true;
    }

    private static long getPageSize() {
        return VirtualMemoryProvider.get().getGranularity().rawValue();
    }

    private static void pageSizeAlignUp(JfrOptionLong option) {
        option.setValue(NumUtil.roundUp(option.getValue(), getPageSize()));
    }

    public static class JfrOptionLong {
        private final long defaultValue;
        private boolean setByUser;
        private long value;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrOptionLong(long defaultValue) {
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.setByUser = false;
        }

        public long getDefaultValue() {
            return defaultValue;
        }

        public long getValue() {
            return value;
        }

        private void setValue(long v) {
            this.value = v;
        }

        public void setUserValue(long v) {
            this.value = v;
            this.setByUser = true;
        }

        public boolean isUserValue() {
            return setByUser;
        }
    }
}
