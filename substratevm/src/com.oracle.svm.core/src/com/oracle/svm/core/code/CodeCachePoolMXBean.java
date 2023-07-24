/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.List;

import javax.management.ObjectName;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.AbstractMXBean;

import sun.management.Util;

public abstract class CodeCachePoolMXBean extends AbstractMXBean implements MemoryPoolMXBean {

    @Platforms(Platform.HOSTED_ONLY.class)
    CodeCachePoolMXBean() {
    }

    protected abstract long getCurrentSize();

    protected abstract long getPeakSize();

    @Override
    public MemoryUsage getUsage() {
        long used = getCurrentSize();
        return new MemoryUsage(UNDEFINED_MEMORY_USAGE, used, used, UNDEFINED_MEMORY_USAGE);
    }

    @Override
    public MemoryUsage getPeakUsage() {
        long peak = getPeakSize();
        return new MemoryUsage(UNDEFINED_MEMORY_USAGE, peak, peak, UNDEFINED_MEMORY_USAGE);
    }

    @Override
    public MemoryType getType() {
        return MemoryType.NON_HEAP;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String[] getMemoryManagerNames() {
        return new String[]{CodeCacheManagerMXBean.CODE_CACHE_MANAGER};
    }

    @Override
    public long getUsageThreshold() {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public void setUsageThreshold(long threshold) {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public boolean isUsageThresholdExceeded() {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public long getUsageThresholdCount() {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public boolean isUsageThresholdSupported() {
        return false;
    }

    @Override
    public long getCollectionUsageThreshold() {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public void setCollectionUsageThreshold(long threshold) {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public boolean isCollectionUsageThresholdExceeded() {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public long getCollectionUsageThresholdCount() {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public MemoryUsage getCollectionUsage() {
        return new MemoryUsage(UNDEFINED_MEMORY_USAGE, 0, 0, UNDEFINED_MEMORY_USAGE);
    }

    @Override
    public boolean isCollectionUsageThresholdSupported() {
        return false;
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE, getName());
    }

    public static List<MemoryPoolMXBean> getMemoryPools() {
        return List.of(new CodeAndDataPool(), new NativeMetadataPool());
    }

    static final class CodeAndDataPool extends CodeCachePoolMXBean {
        @Override
        protected long getCurrentSize() {
            RuntimeCodeInfoMemory.SizeCounters counters = RuntimeCodeInfoMemory.singleton().getSizeCounters();
            return counters.codeAndDataMemorySize().rawValue();
        }

        @Override
        protected long getPeakSize() {
            RuntimeCodeInfoMemory.SizeCounters counters = RuntimeCodeInfoMemory.singleton().getPeakSizeCounters();
            return counters.codeAndDataMemorySize().rawValue();
        }

        @Override
        public void resetPeakUsage() {
            RuntimeCodeInfoMemory.singleton().clearPeakCodeAndDataCounters();
        }

        @Override
        public String getName() {
            return CodeCacheManagerMXBean.CODE_CACHE_CODE_AND_DATA_POOL;
        }
    }

    static final class NativeMetadataPool extends CodeCachePoolMXBean {
        @Override
        protected long getCurrentSize() {
            RuntimeCodeInfoMemory.SizeCounters counters = RuntimeCodeInfoMemory.singleton().getSizeCounters();
            return counters.nativeMetadataSize().rawValue();
        }

        @Override
        protected long getPeakSize() {
            RuntimeCodeInfoMemory.SizeCounters counters = RuntimeCodeInfoMemory.singleton().getPeakSizeCounters();
            return counters.nativeMetadataSize().rawValue();
        }

        @Override
        public void resetPeakUsage() {
            RuntimeCodeInfoMemory.singleton().clearPeakNativeMetadataCounters();
        }

        @Override
        public String getName() {
            return CodeCacheManagerMXBean.CODE_CACHE_NATIVE_METADATA_POOL;
        }
    }
}
