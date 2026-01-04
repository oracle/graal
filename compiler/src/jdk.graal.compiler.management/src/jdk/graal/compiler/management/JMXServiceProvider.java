/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.management;

import static jdk.graal.compiler.serviceprovider.GraalServices.getCurrentThreadId;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.ThreadMXBean;

import jdk.graal.compiler.serviceprovider.JMXService;
import jdk.graal.compiler.serviceprovider.ServiceProvider;

/**
 * Implementation of {@link JMXService}.
 */
@ServiceProvider(JMXService.class)
public class JMXServiceProvider extends JMXService {
    private final ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    @Override
    protected long getThreadAllocatedBytes(long id) {
        return threadMXBean.getThreadAllocatedBytes(id);
    }

    @Override
    protected long getCurrentThreadCpuTime() {
        long[] times = threadMXBean.getThreadCpuTime(new long[]{getCurrentThreadId()});
        return times[0];
    }

    @Override
    protected boolean isThreadAllocatedMemorySupported() {
        return threadMXBean.isThreadAllocatedMemorySupported();
    }

    @Override
    protected boolean isCurrentThreadCpuTimeSupported() {
        return threadMXBean.isThreadCpuTimeSupported();
    }

    @Override
    protected List<String> getInputArguments() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }

    /**
     * Name of the HotSpot Diagnostic MBean.
     */
    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

    private volatile HotSpotDiagnosticMXBean hotspotMXBean;

    @Override
    protected void dumpHeap(String outputFile, boolean live) throws IOException {
        initHotSpotMXBean();
        try {
            Path path = Path.of(outputFile);
            if (Files.exists(path) && Files.size(path) == 0) {
                Files.delete(path);
            }
            hotspotMXBean.dumpHeap(outputFile, live);
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reports information about time in the garbage collector.
     */
    static class GCTimeStatisticsImpl implements GCTimeStatistics {

        private final List<GarbageCollectorMXBean> gcs;
        private final long startTimeNanos;
        private final long beforeCount;
        private final long beforeMillis;

        GCTimeStatisticsImpl(List<GarbageCollectorMXBean> gcs) {
            this.gcs = gcs;
            long totalCount = 0;
            long totalMillis = 0;
            for (GarbageCollectorMXBean gc : gcs) {
                totalCount += gc.getCollectionCount();
                totalMillis += gc.getCollectionTime();
            }
            beforeCount = totalCount;
            beforeMillis = totalMillis;
            startTimeNanos = System.nanoTime();
        }

        @Override
        public long getGCTimeMillis() {
            long afterMillis = 0;
            for (GarbageCollectorMXBean gc : gcs) {
                afterMillis += gc.getCollectionTime();
            }
            return afterMillis - beforeMillis;
        }

        @Override
        public long getGCCount() {
            long afterCount = 0;
            for (GarbageCollectorMXBean gc : gcs) {
                afterCount += gc.getCollectionCount();
            }
            return afterCount - beforeCount;
        }

        @Override
        public long getElapsedTimeMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
        }
    }

    @Override
    protected GCTimeStatistics getGCTimeStatistics() {
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        if (gcs != null) {
            return new GCTimeStatisticsImpl(gcs);
        }
        return null;
    }

    private void initHotSpotMXBean() {
        if (hotspotMXBean == null) {
            synchronized (this) {
                if (hotspotMXBean == null) {
                    hotspotMXBean = getHotSpotMXBean();
                }
            }
        }
    }

    private static HotSpotDiagnosticMXBean getHotSpotMXBean() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            return ManagementFactory.newPlatformMXBeanProxy(server,
                            HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }
}
