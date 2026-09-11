/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.log.FunctionPointerLogHandler;
import com.oracle.svm.core.logging.HasXlogSupport;
import com.oracle.svm.core.logging.LogConfiguration;
import com.oracle.svm.core.logging.LogLevel;
import com.oracle.svm.core.logging.LogTagSet;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.test.NativeImageBuildArgs;

/// Verifies standalone JFR and fallback GC logging in an image without `-Xlog` support.
@NativeImageBuildArgs({
                "--add-exports=jdk.jfr/jdk.jfr.internal=ALL-UNNAMED",
                "--add-exports=org.graalvm.nativeimage.guest.staging/com.oracle.svm.guest.staging.jdk=ALL-UNNAMED"
})
@SuppressWarnings("static-method")
public final class JfrStandaloneLoggingTest {
    /// Preallocated multiline event used by the allocation-restriction test.
    private static final String[] EVENT_LINES = {"standalone event line 1", "standalone event line 2"};

    /// Verifies that fallback logging buffers from short-lived threads are released.
    @Test
    public void testFallbackThreadLocalBufferLifecycle() throws InterruptedException {
        Assume.assumeTrue("native memory tracking is unavailable", VMInspectionOptions.hasNativeMemoryTrackingSupport());
        LogConfiguration.updateGCLogging(LogLevel.INFO);
        try {
            long baseline = NativeMemoryTracking.singleton().getMallocMemory(NmtCategory.Logging);
            Thread writer = new Thread(() -> LogTagSet.gc.info("fallback thread-local lifecycle message"));
            writer.start();
            writer.join();
            assertEquals("fallback logging buffers must be released after thread exit", baseline,
                            NativeMemoryTracking.singleton().getMallocMemory(NmtCategory.Logging));
        } finally {
            LogConfiguration.updateGCLogging(LogLevel.OFF);
        }
    }

    /// Verifies routing, formatting, and allocation behavior without `-Xlog` support.
    @Test
    public void testStandaloneLoggingWithoutUnifiedLogging() throws IOException {
        assertFalse("unified logging must be absent from this test image", HasXlogSupport.get());

        String logFile = "logging-test-jfr-standalone-only.log";
        Files.deleteIfExists(Path.of(logFile));
        com.oracle.svm.core.jfr.logging.JfrLogging logging = SubstrateJVM.getLogging();
        RuntimeSupport.Hook closeLog = FunctionPointerLogHandler.configureLogFile("standalone JFR logging test", logFile);
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        try {
            assertFalse("the memory management bean must initially report verbose GC logging as disabled", memoryMXBean.isVerbose());
            memoryMXBean.setVerbose(true);
            assertTrue("the memory management bean must report verbose GC logging as enabled", memoryMXBean.isVerbose());
            assertTrue("INFO fallback logging must be enabled", LogTagSet.gc.isInfo());
            assertFalse("DEBUG fallback logging must remain disabled", LogTagSet.gc.isDebug());

            logging.parseConfiguration("jfr=info,jfr+event=info");
            assertTrue("the standalone threshold must control the JDK fast-path gate",
                            jdk.jfr.internal.Logger.shouldLog(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.INFO));

            NoAllocationVerifier verifier = NoAllocationVerifier.factory("standalone JFR logging", false);
            verifier.open();
            try {
                LogTagSet.gc.info("fallback GC info");
                LogTagSet.gc.debug("filtered fallback GC debug");
                jdk.jfr.internal.Logger.log(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.INFO, "standalone JFR info");
                jdk.jfr.internal.Logger.logEvent(jdk.jfr.internal.LogLevel.INFO, EVENT_LINES, false);
                logging.logJfrWarning("standalone direct warning", true);
            } finally {
                verifier.close();
            }

            LogConfiguration.updateGCLogging(LogLevel.DEBUG);
            assertTrue("DEBUG fallback logging must be enabled", LogTagSet.gc.isDebug());
            LogTagSet.gc.debug("fallback GC debug");
            memoryMXBean.setVerbose(false);
            assertFalse("the memory management bean must report verbose GC logging as disabled", memoryMXBean.isVerbose());
            assertFalse("fallback GC logging must be disabled", LogTagSet.gc.isError());
            LogTagSet.gc.info("disabled fallback GC info");

            String output = Files.readString(Path.of(logFile));
            assertTrue("fallback logging must use the legacy uptime and GC prefix", output.contains("s] GC(") && output.contains("fallback GC info"));
            assertFalse("INFO fallback logging must filter debug messages", output.contains("filtered fallback GC debug"));
            assertTrue("DEBUG fallback logging must emit debug messages", output.contains("fallback GC debug"));
            assertFalse("disabled fallback logging must filter messages", output.contains("disabled fallback GC info"));
            assertFalse("fallback logging must not add unified level or tag decorations", output.contains("[info][gc]"));
            assertTrue("standalone output must retain its established format", output.contains("[info][jfr] standalone JFR info"));
            assertTrue("standalone output must contain each event line", output.contains("][jfr,event] standalone event line 1"));
            assertTrue("direct diagnostic helpers must retain standalone output", output.contains("standalone direct warning"));

            logging.parseConfiguration("disable");
            assertFalse("disabling standalone logging must close the JDK fast-path gate",
                            jdk.jfr.internal.Logger.shouldLog(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.ERROR));
        } finally {
            memoryMXBean.setVerbose(false);
            logging.parseConfiguration("all=warning");
            closeLog.execute(false);
            Files.deleteIfExists(Path.of(logFile));
        }
    }
}
