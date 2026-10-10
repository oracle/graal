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
package com.oracle.svm.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;

import org.junit.After;
import org.junit.Test;

import com.oracle.svm.core.logging.LogConfiguration;
import com.oracle.svm.core.logging.LogLevel;
import com.oracle.svm.core.logging.LogMessage;
import com.oracle.svm.core.logging.LogTagSet;
import com.oracle.svm.guest.staging.option.RuntimeOptionParser;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;

/// Exercises file preservation and logging option ordering in a dedicated native image.
@NativeImageBuildArgs({"--add-exports=jdk.jfr/jdk.jfr.internal=ALL-UNNAMED", "--add-exports=org.graalvm.nativeimage.guest.staging/com.oracle.svm.guest.staging.option=ALL-UNNAMED",
                "-H:+UnlockExperimentalVMOptions", "-H:+StrictRuntimeJavaOptions", "--enable-monitoring=jfr", "-R:FlightRecorderLogging=all=trace"})
public class LoggingReviewTest {
    /// Directory containing the current test's active file and archives.
    private Path directory;

    /// Releases configured files before deleting their directory.
    @After
    public void cleanup() throws IOException {
        LogConfiguration.disableLogging();
        if (directory != null) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    /// Preserves the active file in the first unused slot and uses circular runtime rotation.
    @Test
    public void preservesExistingFile() throws IOException {
        Path active = activeFile();
        Files.writeString(active, "previous process");
        Files.writeString(archive(active, 0), "older archive");
        configureFile(active, 3, 1);
        assertEquals("previous process", Files.readString(archive(active, 1)));
        assertEquals("older archive", Files.readString(archive(active, 0)));
        assertEquals(0, Files.size(active));
        logGC();
        assertTrue(Files.readString(archive(active, 2)).contains("review rotation"));
        assertEquals("previous process", Files.readString(archive(active, 1)));
    }

    /// Selects the oldest modification time when all archive slots are occupied.
    @Test
    public void replacesOldestArchive() throws IOException {
        Path active = activeFile();
        Files.writeString(active, "previous process");
        for (int index = 0; index < 3; index++) {
            Files.writeString(archive(active, index), "archive " + index);
            Files.setLastModifiedTime(archive(active, index), FileTime.fromMillis(index == 1 ? 1_000 : 10_000));
        }
        configureFile(active, 3, 0);
        assertEquals("previous process", Files.readString(archive(active, 1)));
        assertEquals("archive 0", Files.readString(archive(active, 0)));
        assertEquals("archive 2", Files.readString(archive(active, 2)));
    }

    /// Rejects a nonregular archive slot without discarding the active file.
    @Test
    public void preservesActiveFileOnArchiveFailure() throws IOException {
        Path active = activeFile();
        Files.writeString(active, "previous process");
        Files.createDirectory(archive(active, 0));
        assertThrows(IllegalArgumentException.class, () -> configureFile(active, 3, 0));
        assertEquals("previous process", Files.readString(active));
    }

    /// Retains the explicit no-rotation behavior of truncating the active file.
    @Test
    public void truncatesWithoutRotation() throws IOException {
        Path active = activeFile();
        Files.writeString(active, "previous process");
        configureFile(active, 0, 0);
        assertEquals(0, Files.size(active));
        assertFalse(Files.exists(archive(active, 0)));
    }

    /// Pads archive numbers to the width of the largest slot, as HotSpot does.
    @Test
    public void padsArchiveNumbers() throws IOException {
        Path active = activeFile();
        Files.writeString(active, "previous process");
        configureFile(active, 11, 0);
        assertEquals("previous process", Files.readString(Path.of(active + ".00")));
    }

    /// Applies compatibility options and unified selectors in their command-line order.
    @Test
    public void ordersJfrOptions() {
        LogConfiguration.parseCommandLineArgument("-Xlog:disable");
        RuntimeOptionParser.parseAndConsumeAllOptions(new String[]{"-XX:FlightRecorderLogging=all=trace", "-Xlog:jfr=error"}, false);
        assertEquals(LogLevel.ERROR, stdoutLevel(LogTagSet.jfr));
        RuntimeOptionParser.parseAndConsumeAllOptions(new String[]{"-Xlog:jfr=error", "-XX:FlightRecorderLogging=all=trace"}, false);
        assertEquals(LogLevel.TRACE, stdoutLevel(LogTagSet.jfr));
        assertTrue(jdk.jfr.internal.Logger.shouldLog(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.TRACE));
        assertEquals(LogLevel.OFF, stdoutLevel(LogTagSet.gc));
        LogConfiguration.parseCommandLineArgument("-Xlog:jfr=off");
        assertFalse(LogTagSet.jfr.isInfo());
        assertFalse(jdk.jfr.internal.Logger.shouldLog(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.INFO));
    }

    /// Rejects an immutable compatibility-option update without changing logging routes.
    @Test
    public void rejectsLateJfrOptionWithoutChangingRoutes() {
        LogConfiguration.parseCommandLineArgument("-Xlog:disable");
        LogConfiguration.parseCommandLineArgument("-Xlog:jfr=debug");
        assertThrows(IllegalStateException.class, () -> RuntimeOptionParser.parseAndConsumeAllOptions(new String[]{"-XX:FlightRecorderLogging=all=error"}, false));
        assertEquals(LogLevel.DEBUG, stdoutLevel(LogTagSet.jfr));
    }

    /// Verifies that an ordinary parser call preserves existing routes after startup.
    @Test
    public void preservesRoutesAfterStartup() {
        LogConfiguration.parseCommandLineArgument("-Xlog:disable");
        LogConfiguration.parseCommandLineArgument("-Xlog:gc=debug");
        RuntimeOptionParser.parseAndConsumeAllOptions(new String[0], false);
        assertEquals(LogLevel.DEBUG, stdoutLevel(LogTagSet.gc));
    }

    /// Creates an isolated directory for each file test.
    private Path activeFile() throws IOException {
        directory = Files.createTempDirectory("logging-review-");
        return directory.resolve("active.log");
    }

    /// Resolves one single-digit archive slot.
    private static Path archive(Path active, int index) {
        return Path.of(active + "." + index);
    }

    /// Configures a fresh file output with the requested rotation policy.
    private static void configureFile(Path active, int count, long size) {
        LogConfiguration.parseCommandLineArgument("-Xlog:gc=info:file=" + active + ":none:filecount=" + count + ",filesize=" + size);
    }

    /// Reads only the stdout route so other destinations cannot mask ordering bugs.
    private static LogLevel stdoutLevel(LogTagSet tag) {
        return LogConfiguration.TestingBackdoor.levelFor(LogConfiguration.TestingBackdoor.outputList(tag), LogConfiguration.TestingBackdoor.stdout());
    }

    /// Emits a complete record inside the enforced uninterruptible message scope.
    @NeverInline(LogTagSet.LOG_MESSAGE_OWNER)
    @Uninterruptible(reason = LogTagSet.LOG_MESSAGE_OWNER)
    private static void logGC() {
        LogMessage message = LogTagSet.gc.message();
        try {
            message.info().string("review rotation");
        } finally {
            message.close();
        }
    }
}
