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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.log.FunctionPointerLogHandler;
import com.oracle.svm.core.logging.LogConfiguration;
import com.oracle.svm.core.logging.LogDecorators;
import com.oracle.svm.core.logging.LogLevel;
import com.oracle.svm.core.logging.LogMessage;
import com.oracle.svm.core.logging.LogOutput;
import com.oracle.svm.core.logging.LogOutputList;
import com.oracle.svm.core.logging.LogSelection;
import com.oracle.svm.core.logging.LogSelectionList;
import com.oracle.svm.core.logging.LogTag;
import com.oracle.svm.core.logging.LogTagSet;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.test.NativeImageBuildArgs;

/// Exercises the SVM unified logging implementation through the native JUnit runner.
@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:+StrictRuntimeJavaOptions",
                "-H:-UnlockExperimentalVMOptions",
                "--add-exports=jdk.jfr/jdk.jfr.internal=ALL-UNNAMED",
                "--add-exports=org.graalvm.nativeimage.guest.staging/com.oracle.svm.guest.staging.option=ALL-UNNAMED",
                "--add-exports=org.graalvm.nativeimage.guest.staging/com.oracle.svm.guest.staging.jdk=ALL-UNNAMED"
})
@SuppressWarnings("static-method")
public final class UnifiedLoggingTest {
    /// Preallocated multiline event used by the allocation-restriction test.
    private static final String[] JFR_EVENT_LINES = {"JFR event line 1", "JFR event line 2"};

    /// Payload large enough to fill the byte queue with a modest number of records.
    private static final String ASYNC_QUEUE_FILLER = "x".repeat(8 * 1024);

    /// Verifies level names, ordering, and threshold enablement.
    @Test
    public void testLevels() {
        LogLevel[] levels = {LogLevel.OFF, LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARNING, LogLevel.ERROR};
        for (LogLevel level : levels) {
            checkSame(LogLevel.fromString(level.label()), level, "level parser should round-trip the level label");
            checkSame(LogLevel.fromString(level.label().toUpperCase()), level, "level parser should ignore case");
        }
        checkFalse(LogLevel.OFF.enables(LogLevel.ERROR), "OFF should not enable ERROR messages");
        checkFalse(LogLevel.INFO.enables(LogLevel.DEBUG), "INFO should not enable DEBUG messages");
        checkTrue(LogLevel.INFO.enables(LogLevel.INFO), "INFO should enable INFO messages");
        checkTrue(LogLevel.INFO.enables(LogLevel.ERROR), "INFO should enable ERROR messages");
        expectFailure(() -> LogLevel.fromString("verbose"), "invalid log level was accepted");
        checkContains(failureMessage(() -> LogLevel.fromString("waring")), "warning", "level error should suggest the closest valid level");
    }

    /// Verifies tag names, keyword escaping, and invalid-tag diagnostics.
    @Test
    public void testTags() {
        checkEquals(LogTag.class_.label(), "class", "class tag should use its external name");
        checkSame(LogTag.fromString("CLASS"), LogTag.class_, "tag parser should ignore case");
        checkEquals(Arrays.stream(LogTag.values()).map(LogTag::label).distinct().count(), (long) LogTag.values().length, "tag labels should be unique");
        checkContains(failureMessage(() -> LogTag.fromString("lod")), "load", "tag error should suggest the closest valid tag");
        expectFailure(() -> LogTag.fromString("bad tag"), "invalid tag was accepted");
    }

    /// Verifies decorator defaults, abbreviations, combinations, and duplicate rejection.
    @Test
    public void testDecorators() {
        checkEquals(LogDecorators.DEFAULT.size(), 3, "default decorator count should be three");
        checkSame(LogDecorators.parse(null), LogDecorators.DEFAULT, "missing decorators should use the defaults");
        checkTrue(LogDecorators.parse("none").isEmpty(), "none should disable decorators");
        checkTrue(LogDecorators.parse("NONE").isEmpty(), "none should be case-insensitive");
        for (LogDecorators.Decorator decorator : LogDecorators.Decorator.values()) {
            checkTrue(LogDecorators.parse(decorator.label()).contains(decorator), "long decorator name should parse as " + decorator.label());
            checkTrue(LogDecorators.parse(decorator.abbreviation()).contains(decorator), "decorator abbreviation should parse as " + decorator.abbreviation());
            checkTrue(LogDecorators.parse(decorator.label().toUpperCase()).contains(decorator), "decorator names should be case-insensitive");
        }
        LogDecorators first = LogDecorators.parse("uptime,level");
        LogDecorators combined = ((Target_com_oracle_svm_core_logging_LogDecorators) (Object) first).union(LogDecorators.parse("tags"));
        checkEquals(combined.size(), 3, "decorator union should contain three decorators");
        checkTrue(combined.contains(LogDecorators.Decorator.LEVEL), "decorator union should retain the level decorator");
        expectFailure(() -> LogDecorators.parse("uptime,uptime"), "duplicate decorator was accepted");
        expectFailure(() -> LogDecorators.parse("unknown"), "invalid decorator was accepted");
    }

    /// Verifies exact and wildcard selections and last-selection-wins precedence.
    @Test
    public void testSelections() {
        LogSelection exact = LogSelection.parse("class+load=debug");
        checkSame(exact.level(), LogLevel.DEBUG, "exact selection should have DEBUG level");
        checkFalse(exact.wildcard(), "exact selection should not be a wildcard");
        checkTrue(exact.selects(LogTagSet.class_load), "exact selection should select class+load");
        checkFalse(exact.selects(LogTagSet.class_load_cause), "exact selection should not select class+load+cause");
        LogSelection wildcard = LogSelection.parse("class+load*=trace");
        checkTrue(wildcard.wildcard(), "wildcard selection should set the wildcard flag");
        checkTrue(wildcard.selects(LogTagSet.class_load_cause), "wildcard selection should select class+load+cause");
        checkFalse(wildcard.selects(LogTagSet.logging), "wildcard selection should not select logging");
        Target_com_oracle_svm_core_logging_LogSelection exactTarget = (Target_com_oracle_svm_core_logging_LogSelection) (Object) exact;
        checkEquals(exactTarget.tagCount(), 2, "exact selection should contain two tags");
        StringBuilder description = new StringBuilder();
        exactTarget.describeOn(description);
        checkEquals(description.toString(), "class+load=debug", "selection description should match the parsed selection");

        LogSelectionList precedence = LogSelectionList.parse("class+load*=debug,class+load+cause=off");
        checkSame(precedence.levelFor(LogTagSet.class_load), LogLevel.DEBUG, "wildcard selection should set class+load to DEBUG");
        checkSame(precedence.levelFor(LogTagSet.class_load_cause), LogLevel.OFF, "specific selection should override the wildcard selection");
        checkEquals(precedence.levelFor(LogTagSet.logging), null, "unmatched selection should leave logging without a level");

        checkTrue(exactTarget.consistsOf(EnumSet.of(LogTag.class_, LogTag.load)), "selection should retain class and load tags");
        String invalidLevel = failureMessage(() -> LogSelection.parse("class+load=waring"));
        checkContains(invalidLevel, "Invalid level 'waring' in log selection", "selection should report invalid levels");
        checkContains(invalidLevel, "Did you mean 'warning'?", "selection level errors should retain suggestions");
        String invalidTag = failureMessage(() -> LogSelection.parse("class+lod"));
        checkContains(invalidTag, "Invalid tag 'lod' in log selection", "selection should report invalid tags");
        checkContains(invalidTag, "Did you mean 'load'?", "selection tag errors should retain suggestions");
        checkContains(failureMessage(() -> LogSelection.parse("class+class")), "duplicates of tag class", "selection should report duplicate tags");
        checkTrue(LogSelection.parse("GC=INFO").selects(LogTagSet.gc), "ordinary tags and levels should be case-insensitive");
        expectFailure(() -> LogSelection.parse("ALL"), "the special all selector should be case-sensitive");
        expectFailure(() -> LogSelection.parse("all*"), "invalid all wildcard was accepted");
    }

    /// Verifies output insertion order, updates, removal, and enablement thresholds.
    @Test
    public void testOutputLists() {
        LogOutputList list = new LogOutputList();
        LogOutput first = new TestLogOutput("first");
        LogOutput second = new TestLogOutput("second");
        Target_com_oracle_svm_core_logging_LogOutputList target = (Target_com_oracle_svm_core_logging_LogOutputList) (Object) list;
        target.setOutputLevel(first, LogLevel.INFO);
        target.setOutputLevel(second, LogLevel.DEBUG);
        checkSame(target.levelFor(first), LogLevel.INFO, "first output should have INFO level");
        checkSame(target.levelFor(second), LogLevel.DEBUG, "second output should have DEBUG level");
        checkTrue(target.isLevel(LogLevel.DEBUG), "DEBUG should be enabled by the second output");
        checkFalse(target.isLevel(LogLevel.TRACE), "TRACE should not be enabled");
        checkEquals(Arrays.asList(target.outputsFor(LogLevel.ERROR)), Arrays.asList(first, second), "ERROR outputs should preserve insertion order");
        target.setOutputLevel(first, LogLevel.WARNING);
        checkSame(target.levelFor(first), LogLevel.WARNING, "first output should update to WARNING level");
        target.setOutputLevel(second, LogLevel.OFF);
        checkSame(target.levelFor(second), LogLevel.OFF, "second output should update to OFF level");
        checkEquals(target.outputsFor(LogLevel.ERROR).length, 1, "OFF output should be removed from ERROR outputs");
        target.clear();
        checkFalse(target.isLevel(LogLevel.ERROR), "cleared output list should not enable ERROR");
        checkEquals(target.outputsFor(LogLevel.ERROR).length, 0, "cleared output list should have no ERROR outputs");
    }

    /// Verifies the asynchronous byte budget, bounds, alignment, and startup immutability.
    @Test
    public void testAsyncLogBufferOptionAndPacking() {
        checkEquals(RuntimeOptions.get("AsyncLogBufferSize"), 2L * 1024 * 1024, "AsyncLogBufferSize should default to 2M");
        Target_com_oracle_svm_core_logging_LogAsyncWriter.validateBufferSize(100L * 1024);
        Target_com_oracle_svm_core_logging_LogAsyncWriter.validateBufferSize(50L * 1024 * 1024);
        expectFailure(() -> Target_com_oracle_svm_core_logging_LogAsyncWriter.validateBufferSize(100L * 1024 - 1), "AsyncLogBufferSize accepted a value below 100K");
        expectFailure(() -> Target_com_oracle_svm_core_logging_LogAsyncWriter.validateBufferSize(50L * 1024 * 1024 + 1), "AsyncLogBufferSize accepted a value above 50M");

        int emptyRecordSize = Target_com_oracle_svm_core_logging_LogAsyncWriter.recordSize(0, 0);
        int oneByteRecordSize = Target_com_oracle_svm_core_logging_LogAsyncWriter.recordSize(0, 1);
        checkEquals(emptyRecordSize % Long.BYTES, 0, "empty asynchronous records should be word aligned");
        checkEquals(oneByteRecordSize % Long.BYTES, 0, "nonempty asynchronous records should be word aligned");
        checkTrue(oneByteRecordSize > emptyRecordSize, "the first payload byte should require another aligned word");
        checkTrue(Target_com_oracle_svm_core_logging_LogAsyncWriter.bufferSizeIsImmutable(), "AsyncLogBufferSize should be immutable after startup");
    }

    /// Verifies that startup timestamp formatting uses its explicit native local offset.
    @Test
    public void testStartupTimestamp() {
        checkEquals(Target_com_oracle_svm_core_logging_LogConfiguration.formatStartupTimestamp(0, 0), "1970-01-01_00-00-00", "UTC startup timestamp");
        checkEquals(Target_com_oracle_svm_core_logging_LogConfiguration.formatStartupTimestamp(0, 19_800), "1970-01-01_05-30-00", "positive-offset startup timestamp");
        checkEquals(Target_com_oracle_svm_core_logging_LogConfiguration.formatStartupTimestamp(0, -28_800), "1969-12-31_16-00-00", "negative-offset startup timestamp");
    }

    /// Verifies configuration defaults, stream aliases, parser diagnostics, and descriptions.
    @Test
    public void testConfiguration() {
        LogConfiguration.disableLogging();
        LogOutput stdout = (LogOutput) (Object) Target_com_oracle_svm_core_logging_LogConfiguration.stdout;
        LogOutput stderr = (LogOutput) (Object) Target_com_oracle_svm_core_logging_LogConfiguration.stderr;
        Target_com_oracle_svm_core_logging_LogOutput stdoutOutput = (Target_com_oracle_svm_core_logging_LogOutput) (Object) stdout;
        checkEquals(stdout.name(), "stdout", "stdout alias should resolve to stdout");
        checkEquals(stderr.name(), "stderr", "stderr alias should resolve to stderr");
        checkContains(stdoutOutput.describe(), "all=off", "disabled stdout description should include all=off");
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        memoryMXBean.setVerbose(true);
        checkTrue(memoryMXBean.isVerbose(), "the memory management bean should report verbose GC logging as enabled");
        checkTrue(LogTagSet.gc.isInfo(), "the management update should enable INFO GC logging");
        checkContains(stdoutOutput.describe(), "gc=info", "the stdout description should include the management update");
        memoryMXBean.setVerbose(false);
        checkFalse(memoryMXBean.isVerbose(), "the memory management bean should report verbose GC logging as disabled");
        checkFalse(LogTagSet.gc.isError(), "the management update should disable GC logging");
        RuntimeOptions.set("VerboseGC", true);
        checkTrue(Boolean.TRUE.equals(RuntimeOptions.get("PrintGC")), "DEBUG GC logging should also enable PrintGC");
        checkTrue(LogTagSet.gc.isDebug(), "VerboseGC should enable DEBUG GC logging");
        RuntimeOptions.set("VerboseGC", false);
        checkTrue(LogTagSet.gc.isInfo(), "disabling VerboseGC should retain PrintGC INFO logging");
        RuntimeOptions.set("PrintGC", false);
        checkFalse(LogTagSet.gc.isError(), "disabling PrintGC should disable GC logging");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:gc=debug"), "GC DEBUG configuration should be accepted");
        checkTrue(Boolean.TRUE.equals(RuntimeOptions.get("PrintGC")), "GC DEBUG configuration should enable PrintGC");
        checkTrue(Boolean.TRUE.equals(RuntimeOptions.get("VerboseGC")), "GC DEBUG configuration should enable VerboseGC");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:gc=info"), "GC INFO configuration should be accepted");
        checkTrue(Boolean.TRUE.equals(RuntimeOptions.get("PrintGC")), "GC INFO configuration should retain PrintGC");
        checkFalse(Boolean.TRUE.equals(RuntimeOptions.get("VerboseGC")), "GC INFO configuration should disable VerboseGC");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:gc=off"), "GC OFF configuration should be accepted");
        checkFalse(Boolean.TRUE.equals(RuntimeOptions.get("PrintGC")), "GC OFF configuration should disable PrintGC");
        checkFalse(Boolean.TRUE.equals(RuntimeOptions.get("VerboseGC")), "GC OFF configuration should disable VerboseGC");
        checkFalse(LogConfiguration.parseCommandLineArgument("-verbose"), "non-Xlog option should be rejected by the logger");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:stdout:none"), "stdout configuration should be accepted");
        checkContains(stdoutOutput.describe(), "class+load=debug", "stdout description should include the configured selection");
        checkTrue(LogTagSet.class_load.isDebug(), "configured class+load tag set should enable DEBUG");
        checkFalse(LogTagSet.logging.isDebug(), "unconfigured logging tag set should not enable DEBUG");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:class+load=verbose"), "invalid configuration level was accepted");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:stdout:unknown"), "invalid configuration decorator was accepted");
        TestLogOutput transactionalOutput = new TestLogOutput("transactional-options");
        Target_com_oracle_svm_core_logging_LogOutput transactionalTarget = (Target_com_oracle_svm_core_logging_LogOutput) (Object) transactionalOutput;
        expectFailure(() -> transactionalTarget.parseOptionsIfFirstConfiguration("foldmultilines=true,unknown=value"), "invalid output option was accepted");
        checkTrue(transactionalTarget.parseOptionsIfFirstConfiguration("foldmultilines=false"), "rejected output options should not consume the first configuration");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:async:stall"), "stall-mode async configuration should be accepted");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:HELP"), "the help directive should be case-sensitive");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:DISABLE"), "the disable directive should be case-sensitive");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:ASYNC"), "the async directive should be case-sensitive");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:async:STALL"), "the async mode should be case-sensitive");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:async:invalid"), "invalid async mode was accepted");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:disable"), "disable configuration should be accepted");
        checkTrue(Target_com_oracle_svm_core_logging_LogConfiguration.asyncRequested, "disable should preserve an earlier asynchronous logging request");
        LogConfiguration.disableLogging();
        LogOutput uppercaseStdout = Target_com_oracle_svm_core_logging_LogConfiguration.findOrCreateOutput("STDOUT");
        checkFalse(uppercaseStdout == stdout, "the stdout output alias should be case-sensitive");
        checkEquals(uppercaseStdout.name(), "file=STDOUT", "an uppercase output alias should denote a file name");
        LogConfiguration.disableLogging();
    }

    /// Verifies independent standalone and unified routing for JFR records.
    @Test
    public void testJfrRouting() throws IOException {
        String standaloneLogFile = testLogFile("jfr-standalone");
        String unifiedLogFile = testLogFile("jfr-unified");
        String unifiedOnlyLogFile = testLogFile("jfr-unified-only");
        String eventLogFile = testLogFile("jfr-event");
        delete(standaloneLogFile);
        delete(unifiedLogFile);
        delete(unifiedOnlyLogFile);
        delete(eventLogFile);

        com.oracle.svm.core.jfr.logging.JfrLogging jfrLogging = SubstrateJVM.getLogging();
        LogConfiguration.disableLogging();
        RuntimeSupport.Hook closeStandaloneLog = FunctionPointerLogHandler.configureLogFile("JFR logging test", standaloneLogFile);
        try {
            jfrLogging.parseConfiguration("jfr=warning");
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:jfr=debug:file=" + unifiedLogFile + ":level,tags"), "unified JFR output should be accepted");
            checkTrue(jdk.jfr.internal.Logger.shouldLog(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.DEBUG), "the combined threshold should admit unified-only DEBUG records");

            jdk.jfr.internal.Logger.log(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.DEBUG, "JFR unified-only debug");
            jdk.jfr.internal.Logger.log(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.WARN, "JFR standalone-and-unified warning");
            String standaloneOutput = read(standaloneLogFile);
            String unifiedOutput = read(unifiedLogFile);
            checkNotContains(standaloneOutput, "JFR unified-only debug", "the unified-only record should not leak into standalone output");
            checkContains(standaloneOutput, "[warn][jfr] JFR standalone-and-unified warning", "standalone output should retain its established format");
            checkContains(unifiedOutput, "[debug][jfr] JFR unified-only debug", "unified output should contain the DEBUG record");
            checkContains(unifiedOutput, "[warning][jfr] JFR standalone-and-unified warning", "unified output should contain the WARNING record");

            LogConfiguration.disableLogging();
            checkFalse(jdk.jfr.internal.Logger.shouldLog(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.DEBUG), "disabling unified logging should leave the standalone WARNING threshold");
            checkTrue(jdk.jfr.internal.Logger.shouldLog(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.WARN), "disabling unified logging should not disable standalone JFR logging");

            jfrLogging.parseConfiguration("disable");
            checkFalse(jdk.jfr.internal.Logger.shouldLog(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.ERROR), "disabling both sinks should disable the JDK JFR tag set");
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:jfr=info:file=" + unifiedOnlyLogFile + ":none"), "unified-only JFR output should be accepted");
            checkTrue(jdk.jfr.internal.Logger.shouldLog(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.INFO), "unified logging should enable JFR when the standalone sink is disabled");
            jdk.jfr.internal.Logger.log(jdk.jfr.internal.LogTag.JFR, jdk.jfr.internal.LogLevel.INFO, "JFR enabled only by Xlog");
            checkContains(read(unifiedOnlyLogFile), "JFR enabled only by Xlog", "unified logging should receive a record while standalone logging is disabled");
            checkNotContains(read(standaloneLogFile), "JFR enabled only by Xlog", "standalone logging should remain disabled");

            LogConfiguration.disableLogging();
            jfrLogging.parseConfiguration("jfr+event=info,jfr+system+event=warning");
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:jfr+system+event=info:file=" + eventLogFile + ":none"), "unified JFR event output should be accepted");
            NoAllocationVerifier verifier = NoAllocationVerifier.factory("JFR dual logging", false);
            verifier.open();
            try {
                jdk.jfr.internal.Logger.logEvent(jdk.jfr.internal.LogLevel.INFO, JFR_EVENT_LINES, true);
            } finally {
                verifier.close();
            }
            standaloneOutput = read(standaloneLogFile);
            checkNotContains(standaloneOutput, "JFR event line 1", "the standalone system event threshold should filter INFO records");
            jdk.jfr.internal.Logger.logEvent(jdk.jfr.internal.LogLevel.INFO, JFR_EVENT_LINES, false);
            standaloneOutput = read(standaloneLogFile);
            checkContains(standaloneOutput, "][jfr,event] JFR event line 1", "the standalone event threshold should admit INFO records");
            checkContains(standaloneOutput, "][jfr,event] JFR event line 2", "standalone event routing should write every event line");
            checkContains(read(eventLogFile), "JFR event line 1\nJFR event line 2\n", "unified event routing should preserve one contiguous multiline message");
        } finally {
            LogConfiguration.disableLogging();
            jfrLogging.parseConfiguration("all=warning");
            closeStandaloneLog.execute(false);
            delete(standaloneLogFile);
            delete(unifiedLogFile);
            delete(unifiedOnlyLogFile);
            delete(eventLogFile);
        }
    }

    /// Verifies level filtering, multiline filtering, and message-level decorations.
    @Test
    public void testMessages() throws IOException {
        String logFile = testLogFile("messages");
        delete(logFile);
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + logFile + ":level,tags"), "message configuration should be accepted");
        try (LogMessage message = LogTagSet.class_load.message()) {
            message.line(LogLevel.INFO).string("info line");
            message.line(LogLevel.DEBUG).string("debug line");
        }
        LogTagSet.class_load.trace("trace line");
        LogTagSet.class_load.debug("embedded line 1\nembedded line 2\n");
        String output = read(logFile);
        checkContains(output, "[info][class,load] info line", "INFO message should include its level and tags");
        checkContains(output, "[debug][class,load] debug line", "DEBUG message should include its level and tags");
        checkNotContains(output, "trace line", "disabled TRACE message should not be written");
        checkContains(output, "[debug][class,load] embedded line 1\n[debug][class,load] embedded line 2\n", "embedded records should each include metadata without adding a blank record");
        LogConfiguration.disableLogging();
        delete(logFile);
    }

    /// Verifies that the `tid` decorator uses the operating-system thread identifier.
    @Test
    public void testThreadIdDecorator() throws IOException {
        String logFile = testLogFile("thread-id");
        LogConfiguration.disableLogging();
        delete(logFile);
        try {
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=info:file=" + logFile + ":tid"), "thread-id configuration should be accepted");
            LogTagSet.class_load.info("thread-id message");
            String line = lineContaining(read(logFile), "thread-id message");
            int decoratorEnd = line.indexOf(']');
            long threadId = Long.parseLong(line.substring(1, decoratorEnd).strip());
            checkTrue(threadId > 0, "tid should contain a positive operating-system thread identifier");
            checkFalse(threadId == Thread.currentThread().threadId(), "tid should not contain the Java thread identifier");
        } finally {
            LogConfiguration.disableLogging();
            delete(logFile);
        }
    }

    /// Verifies that mixed-level messages are filtered per output while retaining event metadata.
    @Test
    public void testMixedLevelMessageRouting() throws IOException {
        String debugLogFile = testLogFile("mixed-level-debug");
        String infoLogFile = testLogFile("mixed-level-info");
        configureMixedLevelOutputs(debugLogFile, infoLogFile);
        try {
            writeMixedLevelMessage("synchronous");
            assertMixedLevelOutputs(debugLogFile, infoLogFile, "synchronous");
        } finally {
            LogConfiguration.disableLogging();
            delete(debugLogFile);
            delete(infoLogFile);
        }
    }

    /// Verifies that concurrent platform threads keep each synchronous event contiguous.
    @Test
    public void testSynchronousMessageAtomicity() throws IOException {
        String logFile = testLogFile("synchronous-message-atomicity");
        LogConfiguration.disableLogging();
        delete(logFile);
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + logFile + ":none"), "synchronous message configuration should be accepted");

        AtomicInteger ready = new AtomicInteger();
        AtomicBoolean start = new AtomicBoolean();
        Thread first = new Thread(new ConcurrentMessageWriter("first", ready, start));
        Thread second = new Thread(new ConcurrentMessageWriter("second", ready, start));
        first.start();
        second.start();
        while (ready.get() != 2) {
            Thread.onSpinWait();
        }
        start.set(true);
        try {
            first.join();
            second.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for concurrent log writers", ex);
        }

        String output = read(logFile);
        for (String prefix : new String[]{"first", "second"}) {
            String block = prefix + " line 1\n" + prefix + " line 2\n" + prefix + " line 3\n";
            checkContains(output, block, "concurrent event should remain contiguous for " + prefix);
        }
        LogConfiguration.disableLogging();
        delete(logFile);
    }

    /// Verifies that closing an empty message releases the thread-local event state.
    @Test
    public void testEmptyMessageScope() throws IOException {
        String logFile = testLogFile("empty-message-scope");
        LogConfiguration.disableLogging();
        delete(logFile);
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=info:file=" + logFile + ":none"), "empty message configuration should be accepted");
        // The empty scope must still be closed to release the carrier's event state.
        LogMessage emptyMessage = LogTagSet.class_load.message();
        emptyMessage.close();
        LogTagSet.class_load.info("message after empty scope");
        checkContains(read(logFile), "message after empty scope", "closing an empty scope should permit the next message");
        LogConfiguration.disableLogging();
        delete(logFile);
    }

    /// Verifies that logging buffers from short-lived threads are released when NMT is available.
    @Test
    public void testThreadLocalBufferLifecycle() throws Exception {
        Assume.assumeTrue("native memory tracking is unavailable", VMInspectionOptions.hasNativeMemoryTrackingSupport());
        LogConfiguration.disableLogging();
        long baseline = NativeMemoryTracking.singleton().getMallocMemory(NmtCategory.Logging);
        Thread writer = new Thread(() -> {
            try (LogMessage message = LogTagSet.class_load.message()) {
                message.debug().string("thread-local lifecycle message");
            }
        });
        writer.start();
        writer.join();
        checkEquals(NativeMemoryTracking.singleton().getMallocMemory(NmtCategory.Logging), baseline, "thread-local logging buffers should be released after thread exit");
    }

    /// Verifies that thread start eagerly allocates logging state only while async output is active.
    @Test
    public void testAsyncThreadLocalInitialization() throws Exception {
        LogConfiguration.disableLogging();
        AtomicBoolean initialized = new AtomicBoolean(true);
        Thread synchronousThread = new Thread(() -> initialized.set(Target_com_oracle_svm_core_logging_LogThreadLocal.isInitialized()));
        synchronousThread.start();
        synchronousThread.join();
        checkFalse(initialized.get(), "synchronous-only thread start should not allocate logging state");

        try {
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:async"), "async configuration should be accepted");
            LogConfiguration.logInitializationComplete();
            Thread asynchronousThread = new Thread(() -> initialized.set(Target_com_oracle_svm_core_logging_LogThreadLocal.isInitialized()));
            asynchronousThread.start();
            asynchronousThread.join();
            checkTrue(initialized.get(), "async thread start should allocate logging state before running Java code");
        } finally {
            LogConfiguration.disableLogging();
        }
    }

    /// Verifies that asynchronous messages are copied before the producer scope is cleared.
    @Test
    public void testAsyncMessages() throws IOException {
        String logFile = testLogFile("async-messages");
        String foldedLogFile = testLogFile("async-messages-folded");
        LogConfiguration.disableLogging();
        delete(logFile);
        delete(foldedLogFile);
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=info:file=" + logFile + ":level,tags"), "async message configuration should be accepted");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=info:file=" + foldedLogFile + ":none:foldmultilines=true"),
                        "folded async message configuration should be accepted");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:async"), "async configuration should be accepted");
        LogConfiguration.logInitializationComplete();
        LogTagSet.class_load.info("asynchronous line 1\nasynchronous line 2\n");
        LogTagSet.class_load.info("asynchronous CRLF line 1\r\nasynchronous CRLF line 2\r\n");
        LogConfiguration.disableLogging();
        String output = read(logFile);
        checkContains(output, "[info][class,load] asynchronous line 1\n[info][class,load] asynchronous line 2\n",
                        "asynchronous records should retain producer metadata and omit a trailing blank record");
        checkContains(output, "asynchronous CRLF line 1\n[info][class,load] asynchronous CRLF line 2\n", "asynchronous CRLF should produce one separator");
        checkNotContains(output, "\r", "asynchronous CRLF should not retain carriage returns");
        String foldedOutput = read(foldedLogFile);
        checkContains(foldedOutput, "asynchronous CRLF line 1\\nasynchronous CRLF line 2\\n", "folded asynchronous CRLF should produce one escaped separator");
        checkNotContains(foldedOutput, "\r", "folded asynchronous CRLF should not retain carriage returns");
        delete(logFile);
        delete(foldedLogFile);
    }

    /// Verifies that queued mixed-level messages retain explicit line levels and event metadata.
    @Test
    public void testAsyncMixedLevelMessageRouting() throws IOException {
        String debugLogFile = testLogFile("async-mixed-level-debug");
        String infoLogFile = testLogFile("async-mixed-level-info");
        configureMixedLevelOutputs(debugLogFile, infoLogFile);
        try {
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:async:stall"), "stall-mode async configuration should be accepted");
            LogConfiguration.logInitializationComplete();
            writeMixedLevelMessage("asynchronous");
            LogConfiguration.disableLogging();
            assertMixedLevelOutputs(debugLogFile, infoLogFile, "asynchronous");
        } finally {
            LogConfiguration.disableLogging();
            delete(debugLogFile);
            delete(infoLogFile);
        }
    }

    /// Verifies asynchronous level filtering and the raw message path.
    @Test
    public void testAsyncRawMessages() throws IOException {
        String logFile = testLogFile("async-raw-messages");
        startAsyncLogging(logFile, "class+load=debug", "drop");
        LogTagSet.class_load.debug("1Debug");
        LogTagSet.class_load.info("1Info");
        LogTagSet.class_load.warning("1Warning");
        LogTagSet.class_load.error("1Error");
        LogTagSet.class_load.trace("1Trace");
        LogConfiguration.disableLogging();

        String output = read(logFile);
        checkContains(output, "1Debug", "async DEBUG message should be written");
        checkContains(output, "1Info", "async INFO message should be written");
        checkContains(output, "1Warning", "async WARNING message should be written");
        checkContains(output, "1Error", "async ERROR message should be written");
        checkNotContains(output, "1Trace", "async TRACE message should be filtered");
        delete(logFile);
    }

    /// Verifies that all lines of one asynchronous message remain ordered around other messages.
    @Test
    public void testAsyncMessageOrdering() throws IOException {
        String logFile = testLogFile("async-message-ordering");
        startAsyncLogging(logFile, "class+load=debug,logging=debug", "drop");
        final int multiLineCount = 20;
        String[] expectedLines = new String[multiLineCount];
        try (LogMessage message = LogTagSet.class_load.message()) {
            for (int index = 0; index < multiLineCount; index++) {
                expectedLines[index] = "nonbreakable log message line-" + index;
                message.line(LogLevel.DEBUG).string(expectedLines[index]);
            }
        }
        LogTagSet.logging.debug("a noisy message from another logger");
        LogConfiguration.disableLogging();

        String output = read(logFile);
        checkSubstringsInOrder(output, expectedLines, "async message lines should remain in order");
        delete(logFile);
    }

    /// Verifies that stall mode drains a burst without dropping its first and last messages.
    @Test
    public void testAsyncStallMode() throws IOException {
        String logFile = testLogFile("async-stall-mode");
        startAsyncLogging(logFile, "class+load=info", "stall");
        final int messageCount = 4096;
        for (int index = 0; index < messageCount; index++) {
            LogTagSet.class_load.info("stall message " + index);
        }
        LogConfiguration.disableLogging();

        String output = read(logFile);
        checkContains(output, "stall message 0", "stall mode should write the first queued message");
        checkContains(output, "stall message " + (messageCount / 2), "stall mode should write a middle queued message");
        checkContains(output, "stall message " + (messageCount - 1), "stall mode should write the last queued message");
        delete(logFile);
    }

    /// Verifies that drop mode reports the number of discarded lines to the affected output.
    @Test
    public void testAsyncDropModeReportsDroppedMessages() throws InterruptedException {
        LogConfiguration.disableLogging();
        BlockingCapturingLogOutput firstOutput = new BlockingCapturingLogOutput();
        CapturingLogOutput secondOutput = new CapturingLogOutput();
        CountDownLatch configurationThreadReady = new CountDownLatch(1);
        CountDownLatch disableRequested = new CountDownLatch(1);
        AtomicBoolean initializedBeforeDisable = new AtomicBoolean();
        AtomicBoolean initializedAfterDisable = new AtomicBoolean();
        AtomicReference<Throwable> configurationFailure = new AtomicReference<>();
        Thread configurationThread = new Thread(() -> {
            initializedBeforeDisable.set(Target_com_oracle_svm_core_logging_LogThreadLocal.isInitialized());
            configurationThreadReady.countDown();
            try {
                disableRequested.await();
                LogConfiguration.disableLogging();
                initializedAfterDisable.set(Target_com_oracle_svm_core_logging_LogThreadLocal.isInitialized());
            } catch (Throwable throwable) {
                configurationFailure.set(throwable);
            }
        });
        configurationThread.start();
        configurationThreadReady.await();
        Target_com_oracle_svm_core_logging_LogConfiguration.configureOutput(LogSelectionList.parse("class+load=info"), firstOutput, LogDecorators.NONE);
        Target_com_oracle_svm_core_logging_LogConfiguration.configureOutput(LogSelectionList.parse("module+load=info"), secondOutput, LogDecorators.NONE);
        try {
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:async:drop"), "async drop-reporting mode should be accepted");
            LogConfiguration.logInitializationComplete();
            LogTagSet.class_load.info("blocked before filling the asynchronous queue");
            firstOutput.awaitFirstWrite();
            int bufferCapacity = Target_com_oracle_svm_core_logging_LogAsyncWriter.bufferCapacity();
            int lineCount = bufferCapacity / ASYNC_QUEUE_FILLER.length() * 2;
            for (int index = 0; index < lineCount; index++) {
                LogTagSet.class_load.info(ASYNC_QUEUE_FILLER + " drop-reporting line " + index);
            }
            LogTagSet.module_load.info(ASYNC_QUEUE_FILLER + " dropped without a preceding accepted record");

            firstOutput.releaseFirstWrite();
            disableRequested.countDown();
            configurationThread.join();
            if (configurationFailure.get() != null) {
                throw new AssertionError("logging configuration thread failed", configurationFailure.get());
            }
            checkFalse(initializedBeforeDisable.get(), "a thread started before async logging should not eagerly allocate logging state");
            checkTrue(initializedAfterDisable.get(), "reporting a pending drop should initialize logging state on the configuration thread");
            checkContains(firstOutput.contents(), "messages dropped due to async logging", "first output should report lines discarded after its byte budget was exhausted");
            checkContains(secondOutput.contents(), "messages dropped due to async logging", "second output should report lines discarded after the shared byte budget was exhausted");
            checkNotContains(secondOutput.contents(), "dropped without a preceding accepted record", "the second output should have no accepted record to trigger its drop report");
            checkFalse(firstOutput.wasInterrupted(), "asynchronous output thread interruption");
        } finally {
            firstOutput.releaseFirstWrite();
            disableRequested.countDown();
            configurationThread.join();
            LogConfiguration.disableLogging();
        }
    }

    /// Verifies that a record larger than the complete byte queue uses synchronous output in both
    /// queue-full modes.
    @Test
    public void testAsyncOversizedRecordFallsBackSynchronously() {
        for (String mode : new String[]{"drop", "stall"}) {
            LogConfiguration.disableLogging();
            ThreadRecordingLogOutput output = new ThreadRecordingLogOutput();
            LogSelectionList selections = LogSelectionList.parse("class+load=info");
            Target_com_oracle_svm_core_logging_LogConfiguration.configureOutput(selections, output, LogDecorators.NONE);
            try {
                checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:async:" + mode), "async oversized-record mode should be accepted");
                LogConfiguration.logInitializationComplete();
                String oversized = "x".repeat(Target_com_oracle_svm_core_logging_LogAsyncWriter.bufferCapacity());
                Thread producer = Thread.currentThread();
                LogTagSet.class_load.info(oversized);
                checkSame(output.writingThread, producer, "an oversized record should use synchronous output in " + mode + " mode");
                checkEquals(output.writeCount.get(), 1, "an oversized record should be written completely once in " + mode + " mode");
            } finally {
                LogConfiguration.disableLogging();
            }
        }
    }

    /// Verifies that a runtime disable does not discard the requested asynchronous mode.
    @Test
    public void testAsyncReactivationAfterRuntimeDisable() {
        LogConfiguration.disableLogging();
        ThreadRecordingLogOutput output = new ThreadRecordingLogOutput();
        LogSelectionList selections = LogSelectionList.parse("class+load=info");
        Target_com_oracle_svm_core_logging_LogConfiguration.configureOutput(selections, output, LogDecorators.NONE);
        try {
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:async:drop"), "async reactivation mode should be accepted");
            LogConfiguration.logInitializationComplete();
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:disable"), "runtime disable should be accepted");
            Target_com_oracle_svm_core_logging_LogConfiguration.configureOutput(selections, output, LogDecorators.NONE);
            LogTagSet.class_load.info("message after runtime reactivation");
            LogConfiguration.disableLogging();
            checkFalse(output.writingThread == Thread.currentThread(), "reactivated asynchronous output should use the consumer thread");
            checkEquals(output.writeCount.get(), 1, "reactivated asynchronous output should write the record once");
        } finally {
            LogConfiguration.disableLogging();
        }
    }

    /// Verifies that runtime reconfiguration drains records using the previous output state.
    @Test
    public void testAsyncReconfigurationFlushesQueuedRecords() throws Exception {
        LogConfiguration.disableLogging();
        BlockingThreadRecordingLogOutput output = new BlockingThreadRecordingLogOutput();
        LogSelectionList selections = LogSelectionList.parse("class+load=info");
        Target_com_oracle_svm_core_logging_LogConfiguration.configureOutput(selections, output, LogDecorators.NONE);
        Thread reconfiguration = null;
        try {
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:async:drop"), "async reconfiguration test mode should be accepted");
            LogConfiguration.logInitializationComplete();
            LogTagSet.class_load.info("blocked before reconfiguration");
            output.awaitFirstWrite();
            LogTagSet.class_load.info("queued before reconfiguration");

            AtomicBoolean reconfigurationFinished = new AtomicBoolean();
            reconfiguration = new Thread(() -> {
                Target_com_oracle_svm_core_logging_LogConfiguration.configureOutput(selections, output, LogDecorators.parse("uptimenanos"));
                reconfigurationFinished.set(true);
            });
            reconfiguration.start();

            Target_com_oracle_svm_core_logging_LogTagSet tagSet = (Target_com_oracle_svm_core_logging_LogTagSet) (Object) LogTagSet.class_load;
            awaitDecorator(tagSet, LogDecorators.Decorator.UPTIMENANOS);
            checkFalse(reconfigurationFinished.get(), "reconfiguration should wait for the blocked asynchronous record");

            output.releaseFirstWrite();
            reconfiguration.join();
            checkTrue(reconfigurationFinished.get(), "reconfiguration should finish after the asynchronous queue drains");
            checkEquals(output.writeCount.get(), 2, "reconfiguration should drain every record using the previous output state");
        } finally {
            output.releaseFirstWrite();
            if (reconfiguration != null) {
                reconfiguration.join();
            }
            LogConfiguration.disableLogging();
        }
    }

    /// Verifies that a VM operation uses synchronous output when its complete message cannot fit
    /// in the asynchronous queue.
    @Test
    public void testAsyncLoggingFromVMOperation() throws IOException, InterruptedException {
        String logFile = testLogFile("async-vm-operation");
        LogConfiguration.disableLogging();
        delete(logFile);
        BlockingThreadRecordingLogOutput output = new BlockingThreadRecordingLogOutput();
        TestLogOutput secondOutput = new TestLogOutput("second-vm-operation-output");
        Target_com_oracle_svm_core_logging_LogTagSet tagSet = (Target_com_oracle_svm_core_logging_LogTagSet) (Object) LogTagSet.class_load;
        Target_com_oracle_svm_core_logging_LogOutputList outputList = (Target_com_oracle_svm_core_logging_LogOutputList) (Object) tagSet.outputList();
        outputList.setOutputLevel(output, LogLevel.INFO);
        outputList.setOutputLevel(secondOutput, LogLevel.INFO);
        try {
            String loggingOption = "-Xlog:logging=debug:file=" + logFile + ":none";
            checkTrue(LogConfiguration.parseCommandLineArgument(loggingOption), "logging statistics output should be accepted: " + loggingOption);
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:async:drop"), "async VM operation configuration should be accepted");
            LogConfiguration.logInitializationComplete();

            LoggingVMOperation queuedOperation = new LoggingVMOperation();
            queuedOperation.enqueue();
            output.awaitFirstWrite();
            checkTrue(output.firstWritingThread != queuedOperation.executingThread, "a VM operation should use asynchronous output when its message fits in the queue");
            /* More bytes than the queue can hold leave it full while the consumer is blocked. */
            int lineCount = Target_com_oracle_svm_core_logging_LogAsyncWriter.bufferCapacity() / ASYNC_QUEUE_FILLER.length() * 2;
            for (int index = 0; index < lineCount; index++) {
                LogTagSet.class_load.info(ASYNC_QUEUE_FILLER + " queued line " + index);
            }
            /* Small records consume any gap that was too short for another filler record. */
            int smallLineCount = ASYNC_QUEUE_FILLER.length() / Target_com_oracle_svm_core_logging_LogAsyncWriter.recordSize(0, 0) * 2;
            for (int index = 0; index < smallLineCount; index++) {
                LogTagSet.class_load.info("x");
            }

            LoggingVMOperation operation = new LoggingVMOperation();
            operation.enqueue();
            checkSame(output.writingThread, operation.executingThread, "a VM operation must use synchronous output when the asynchronous queue is full");

            output.releaseFirstWrite();
            LogConfiguration.disableLogging();
            checkFalse(output.wasInterrupted(), "asynchronous output thread interruption");
            checkContains(read(logFile), "VM operation log messages that used synchronous mode because the asynchronous queue was unavailable: 1",
                            "shutdown should report synchronous VM operation enqueue calls");
        } finally {
            output.releaseFirstWrite();
            LogConfiguration.disableLogging();
            delete(logFile);
        }
    }

    /// Verifies quoted file names, file-size parsing, folding, rotation, and invalid options.
    @Test
    public void testFileOutput() throws IOException {
        String logFile = testLogFile("file-output");
        String rotatingLogFile = testLogFile("file-output-rotating");
        String invalidLogFile = testLogFile("file-output-invalid");
        String existingLogFile = testLogFile("file-output-existing");
        delete(logFile);
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=\"" + logFile + "\":none:filecount=2,filesize=1"), "file output configuration should be accepted");
        LogTagSet.class_load.debug("first");
        LogTagSet.class_load.debug("second");
        checkTrue(Files.exists(Path.of(logFile)), "configured log file should be created: " + logFile);
        checkTrue(Files.exists(Path.of(logFile + ".0")), "size-based log rotation should create an archive: " + logFile + ".0");
        LogConfiguration.disableLogging();
        delete(logFile);
        delete(logFile + ".0");

        delete(existingLogFile);
        delete(existingLogFile + ".0");
        Files.writeString(Path.of(existingLogFile), "existing log contents");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + existingLogFile + ":none:filecount=2"), "existing file output configuration should be accepted");
        LogTagSet.class_load.debug("new active log contents");
        LogConfiguration.disableLogging();
        checkContains(read(existingLogFile + ".0"), "existing log contents", "a preexisting active file should be archived at startup");
        checkContains(read(existingLogFile), "new active log contents", "startup should write to a fresh active file");
        delete(existingLogFile);
        delete(existingLogFile + ".0");

        delete(rotatingLogFile);
        delete(rotatingLogFile + ".0");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + rotatingLogFile + ":none:foldmultilines=true"), "folding configuration should be accepted");
        LogTagSet.class_load.debug("first\\part\nsecond");
        LogTagSet.class_load.debug("first\r\nsecond");
        String foldedMessage = "first\\\\part" + "\\n" + "second";
        checkContains(read(rotatingLogFile), foldedMessage, "multiline event should be folded");
        checkContains(read(rotatingLogFile), "first\\nsecond", "CRLF should be folded as one line separator");
        checkNotContains(read(rotatingLogFile), "first\r", "CRLF should not retain the carriage return");
        LogConfiguration.disableLogging();
        delete(rotatingLogFile);
        delete(invalidLogFile);
        Files.writeString(Path.of(invalidLogFile), "preserve existing contents");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + invalidLogFile + ":badoption=1"), "invalid file option was accepted");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + invalidLogFile + ":none:FoldMultiLines=true"),
                        "case-insensitive file option key was accepted");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + invalidLogFile + ":none:foldmultilines=TRUE"),
                        "case-insensitive foldmultilines value was accepted");
        checkEquals(read(invalidLogFile), "preserve existing contents", "invalid configuration should not truncate an existing file");
        LogConfiguration.disableLogging();
        delete(invalidLogFile);

        String indexedLogFile = testLogFile("file-output-indexed");
        delete(indexedLogFile);
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=info:file=" + indexedLogFile + ":none"), "indexed output configuration should be accepted");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:#2"), "a reported file output index should resolve to its existing output");
        expectFailure(() -> LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:#3"), "an unknown output index was accepted");
        LogTagSet.class_load.debug("indexed output message");
        checkContains(read(indexedLogFile), "indexed output message", "the reported output index should not become a filename");
        LogConfiguration.disableLogging();
        delete(indexedLogFile);
    }

    /// Verifies that first-use, contended writes, and rotation do not allocate on the Java heap.
    @Test
    public void testAllocationFreeOutput() throws Exception {
        String logFile = testLogFile("allocation-free-output");
        LogConfiguration.disableLogging();
        delete(logFile);
        delete(logFile + ".0");
        try {
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + logFile + ":none:filecount=2,filesize=1"), "allocation-free output configuration should be accepted");

            AtomicInteger ready = new AtomicInteger();
            AtomicBoolean start = new AtomicBoolean();
            Thread first = new Thread(new AllocationFreeWriter(ready, start));
            Thread second = new Thread(new AllocationFreeWriter(ready, start));
            first.start();
            second.start();
            while (ready.get() != 2) {
                Thread.onSpinWait();
            }
            start.set(true);
            first.join();
            second.join();

            checkContains(read(logFile) + read(logFile + ".0"), "allocation-free output", "allocation-free output should be written");
        } finally {
            LogConfiguration.disableLogging();
            delete(logFile);
            delete(logFile + ".0");
        }
    }

    /// Verifies that Windows drive-letter colons do not split file output components.
    @Test
    public void testWindowsFileOutputPath() throws IOException {
        Assume.assumeTrue("Windows drive-letter paths are only valid on Windows", Platform.includedIn(Platform.WINDOWS.class));
        Path path = Files.createTempFile("unified-logging-windows", ".log");
        String nativePath = path.toString();
        String slashPath = nativePath.replace('\\', '/');
        String[] outputs = {nativePath, slashPath, "file=" + nativePath, "file=" + slashPath};
        try {
            for (String output : outputs) {
                LogConfiguration.disableLogging();
                Files.deleteIfExists(path);
                String option = "-Xlog:class+load=debug:" + output + ":none";
                checkTrue(LogConfiguration.parseCommandLineArgument(option), "Windows file output path should be accepted: " + option);
                LogTagSet.class_load.debug("Windows path output");
                checkContains(read(path.toString()), "Windows path output", "Windows file output should receive log messages");
            }
        } finally {
            LogConfiguration.disableLogging();
            Files.deleteIfExists(path);
        }
    }

    /// Verifies that output options are ignored when a file output already exists.
    @Test
    public void testExistingOutputOptionsIgnored() throws IOException {
        String logFile = testLogFile("existing-output-options");
        String symlink = logFile + ".alias";
        LogConfiguration.disableLogging();
        delete(logFile);
        delete(symlink);
        try {
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + logFile + ":none"), "initial file output configuration should be accepted");
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=./" + logFile + ":none:invalid=1"), "output options for an existing aliased output should be ignored");
            if (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class)) {
                Files.createSymbolicLink(Path.of(symlink), Path.of(logFile).toAbsolutePath());
                checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + symlink + ":none:invalid=1"), "symbolic links should resolve to an existing aliased output");
            }
            LogTagSet.class_load.debug("existing output message");
            checkContains(read(logFile), "existing output message", "existing file output should remain usable");
        } finally {
            LogConfiguration.disableLogging();
            delete(symlink);
            delete(logFile);
        }
    }

    /// Verifies that an output file whose parent directory is missing does not abort logging.
    @Test
    public void testFileOutputMissingDirectory() throws IOException {
        LogConfiguration.disableLogging();
        Path missingDirectory = Files.createTempDirectory("logging-test-file-output-missing-directory");
        Files.delete(missingDirectory);
        Path logFile = missingDirectory.resolve("output.log");
        try {
            String option = "-Xlog:class+load=debug:file=" + logFile + ":none";
            checkTrue(LogConfiguration.parseCommandLineArgument(option), "file output with a missing parent directory should be accepted: " + option);
            LogTagSet.class_load.debug("message for unavailable log file");
            checkFalse(Files.exists(logFile), "a log file should not be created when its parent directory is missing: " + logFile);
        } finally {
            LogConfiguration.disableLogging();
            delete(logFile.toString());
        }
    }

    /// Verifies that logging remains safe after a POSIX file-backed output is unlinked.
    @Test
    public void testFileOutputDeletedWhileOpen() throws IOException {
        // POSIX permits unlinking a file while the logging descriptor remains open.
        Assume.assumeTrue("deleting an open log file requires POSIX semantics", Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class));
        String logFile = testLogFile("file-output-deleted-while-open");
        LogConfiguration.disableLogging();
        delete(logFile);
        try {
            String option = "-Xlog:class+load=debug:file=" + logFile + ":none";
            checkTrue(LogConfiguration.parseCommandLineArgument(option), "file output configuration should be accepted: " + option);
            LogTagSet.class_load.debug("message before deletion");
            checkTrue(Files.exists(Path.of(logFile)), "configured log file should exist before deletion: " + logFile);
            Files.delete(Path.of(logFile));
            checkFalse(Files.exists(Path.of(logFile)), "log file should be absent after deletion: " + logFile);

            // These calls will succeed as a Unix process can continue to read and write to an open file
            // descriptor even after the file's directory entry has been deleted using unlink() or rm.
            LogTagSet.class_load.debug("message after deletion 1");
            LogTagSet.class_load.debug("message after deletion 2");

            // Close the file descriptor for the log file
            LogOutput output = Target_com_oracle_svm_core_logging_LogConfiguration.findOrCreateOutput(logFile);
            RawFileOperationSupport.RawFileDescriptor descriptor = ((Target_com_oracle_svm_core_logging_LogFileOutput) (Object) output).descriptor();
            checkTrue(RawFileOperationSupport.nativeByteOrder().close(descriptor), "deleted log file descriptor should close successfully");

            // The first `debug` call below should produce a warning on the console:
            //
            // Could not write to log: file=logging-test-file-output-deleted-while-open.log
            //
            // The remaining calls silently do nothing but do not crash the VM.
            LogTagSet.class_load.debug("message after closing descriptor 1");
            LogTagSet.class_load.debug("message after closing descriptor 2");
            LogTagSet.class_load.debug("message after closing descriptor 3");
        } finally {
            LogConfiguration.disableLogging();
            delete(logFile);
        }
    }

    /// Reads a UTF-8 test log file.
    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file));
    }

    /// Removes a test log file when it exists.
    private static void delete(String file) throws IOException {
        Files.deleteIfExists(Path.of(file));
    }

    /// Returns the isolated log path used by one test method.
    private static String testLogFile(String testName) {
        return "logging-test-" + testName + ".log";
    }

    /// Minimal output used when only routing identity is under test.
    private static final class TestLogOutput extends LogOutput {
        TestLogOutput(String name) {
            super(name);
        }

        /// Accepts bytes without performing I/O.
        @Override
        protected int writeRaw(CCharPointer bytes, UnsignedWord length) {
            return 0;
        }
    }

    /// Records the thread and count for nonblocking synchronous-fallback assertions.
    private static final class ThreadRecordingLogOutput extends LogOutput {
        private volatile Thread writingThread;
        private final AtomicInteger writeCount = new AtomicInteger();

        ThreadRecordingLogOutput() {
            super("thread-recording-nonblocking");
        }

        /// Records a complete output write without performing I/O.
        @Override
        protected int writeRaw(CCharPointer bytes, UnsignedWord length) {
            writingThread = Thread.currentThread();
            writeCount.incrementAndGet();
            return 0;
        }
    }

    /// Blocks its first write so the asynchronous queue can be filled deterministically.
    private static class BlockingThreadRecordingLogOutput extends LogOutput {
        private final AtomicBoolean firstWrite = new AtomicBoolean(true);
        private final CountDownLatch firstWriteStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstWrite = new CountDownLatch(1);

        private volatile Thread firstWritingThread;
        private volatile Thread writingThread;
        private volatile boolean interrupted;

        /// Counts records that reached this output.
        private final AtomicInteger writeCount = new AtomicInteger();

        BlockingThreadRecordingLogOutput() {
            super("thread-recording");
        }

        /// Blocks the asynchronous consumer on its first call and records subsequent writers.
        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "The test output intentionally uses blocking JDK synchronization.")
        protected int writeRaw(CCharPointer bytes, UnsignedWord length) {
            writeCount.incrementAndGet();
            if (firstWrite.compareAndSet(true, false)) {
                firstWritingThread = Thread.currentThread();
                firstWriteStarted.countDown();
                try {
                    releaseFirstWrite.await();
                } catch (InterruptedException exception) {
                    interrupted = true;
                    Thread.currentThread().interrupt();
                }
            } else {
                writingThread = Thread.currentThread();
            }
            return 0;
        }

        /// Waits until the asynchronous consumer has entered its first output call.
        void awaitFirstWrite() throws InterruptedException {
            firstWriteStarted.await();
        }

        /// Returns whether the asynchronous consumer was interrupted while blocked.
        boolean wasInterrupted() {
            return interrupted;
        }

        /// Allows the asynchronous consumer to drain the queue.
        void releaseFirstWrite() {
            releaseFirstWrite.countDown();
        }
    }

    /// Extends the blocking output with byte capture for asynchronous output assertions.
    private static final class BlockingCapturingLogOutput extends BlockingThreadRecordingLogOutput {
        /// Captures output bytes after the asynchronous writer releases the first blocked write.
        private final StringBuilder contents = new StringBuilder();

        /// Records bytes after applying the blocking behavior inherited from the test output.
        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "The test output intentionally uses blocking JDK synchronization.")
        protected int writeRaw(CCharPointer bytes, UnsignedWord length) {
            int status = super.writeRaw(bytes, length);
            synchronized (contents) {
                for (int index = 0; index < length.rawValue(); index++) {
                    contents.append((char) Byte.toUnsignedInt(bytes.read(index)));
                }
            }
            return status;
        }

        /// Gets all bytes written to this output as ASCII test content.
        String contents() {
            synchronized (contents) {
                return contents.toString();
            }
        }
    }

    /// Captures output without blocking so unexpected accepted records produce an assertion rather
    /// than stalling the drop-reporting test.
    private static final class CapturingLogOutput extends LogOutput {
        private final StringBuilder contents = new StringBuilder();

        CapturingLogOutput() {
            super("capturing");
        }

        /// Copies output bytes into managed test storage.
        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "The test output intentionally captures bytes in managed storage.")
        protected int writeRaw(CCharPointer bytes, UnsignedWord length) {
            synchronized (contents) {
                for (int index = 0; index < length.rawValue(); index++) {
                    contents.append((char) Byte.toUnsignedInt(bytes.read(index)));
                }
            }
            return 0;
        }

        /// Gets all bytes written to this output as ASCII test content.
        String contents() {
            synchronized (contents) {
                return contents.toString();
            }
        }
    }

    /// Waits until a reconfiguration publishes a transition containing `decorator`.
    private static void awaitDecorator(Target_com_oracle_svm_core_logging_LogTagSet tagSet, LogDecorators.Decorator decorator) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!tagSet.decorators.contains(decorator)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for the " + decorator.label() + " decorator transition");
            }
            Thread.onSpinWait();
        }
    }

    /// Logs while executing at a safepoint and records the executing thread for comparison.
    private static final class LoggingVMOperation extends JavaVMOperation {
        private Thread executingThread;

        LoggingVMOperation() {
            super(VMOperationInfos.get(LoggingVMOperation.class, "Unified logging at safepoint", VMOperation.SystemEffect.SAFEPOINT));
        }

        /// Emits a message whose queue capacity is checked before publication.
        @Override
        protected void operate() {
            executingThread = Thread.currentThread();
            LogTagSet.class_load.info("message from VM operation");
        }
    }

    /// Writes messages while allocation is disabled, allowing concurrent output locking to be tested.
    private static final class AllocationFreeWriter implements Runnable {
        private final AtomicInteger ready;
        private final AtomicBoolean start;

        AllocationFreeWriter(AtomicInteger ready, AtomicBoolean start) {
            this.ready = ready;
            this.start = start;
        }

        @Override
        public void run() {
            NoAllocationVerifier verifier = NoAllocationVerifier.factory("Unified logging output");
            try (verifier) {
                ready.incrementAndGet();
                while (!start.get()) {
                    Thread.onSpinWait();
                }
                for (int index = 0; index < 10; index++) {
                    LogTagSet.class_load.debug("allocation-free output");
                }
            }
        }
    }

    /// Builds a three-line event after two platform threads are released together.
    private static final class ConcurrentMessageWriter implements Runnable {
        private final String prefix;
        private final AtomicInteger ready;
        private final AtomicBoolean start;

        ConcurrentMessageWriter(String prefix, AtomicInteger ready, AtomicBoolean start) {
            this.prefix = prefix;
            this.ready = ready;
            this.start = start;
        }

        @Override
        public void run() {
            ready.incrementAndGet();
            while (!start.get()) {
                Thread.onSpinWait();
            }
            try (LogMessage message = LogTagSet.class_load.message()) {
                message.debug().string(prefix + " line 1");
                message.debug().string(prefix + " line 2");
                message.debug().string(prefix + " line 3");
            }
        }
    }

    /// Configures a file-backed asynchronous writer for one test case.
    private static void startAsyncLogging(String logFile, String selection, String mode) throws IOException {
        LogConfiguration.disableLogging();
        delete(logFile);
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:" + selection + ":file=" + logFile + ":none"), "async test output configuration should be accepted");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:async:" + mode), "async test mode configuration should be accepted");
        LogConfiguration.logInitializationComplete();
    }

    /// Configures DEBUG and INFO file outputs with event identity and line decorators.
    private static void configureMixedLevelOutputs(String debugLogFile, String infoLogFile) throws IOException {
        LogConfiguration.disableLogging();
        delete(debugLogFile);
        delete(infoLogFile);
        String decorators = "timenanos,uptimenanos,tid,level,tags";
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=debug:file=" + debugLogFile + ":" + decorators), "DEBUG mixed-level output should be accepted");
        checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=info:file=" + infoLogFile + ":" + decorators), "INFO mixed-level output should be accepted");
    }

    /// Writes one message whose lines are visible to different output thresholds.
    private static void writeMixedLevelMessage(String messagePrefix) {
        try (LogMessage message = LogTagSet.class_load.message()) {
            message.line(LogLevel.DEBUG).string(messagePrefix + " debug line");
            message.line(LogLevel.INFO).string(messagePrefix + " info line");
        }
    }

    /// Verifies filtering and the normalized decorations of a mixed-level message.
    private static void assertMixedLevelOutputs(String debugLogFile, String infoLogFile, String messagePrefix) throws IOException {
        String debugOutput = read(debugLogFile);
        String infoOutput = read(infoLogFile);
        String debugMessage = messagePrefix + " debug line";
        String infoMessage = messagePrefix + " info line";
        checkContains(debugOutput, debugMessage, "DEBUG output should contain the DEBUG line");
        checkContains(debugOutput, infoMessage, "DEBUG output should contain the INFO line");
        checkNotContains(infoOutput, debugMessage, "INFO output should filter the DEBUG line");
        checkContains(infoOutput, infoMessage, "INFO output should contain the INFO line");

        String debugInfoPrefix = normalizedDecoratorPrefix(lineContaining(debugOutput, infoMessage), 5);
        String infoInfoPrefix = normalizedDecoratorPrefix(lineContaining(infoOutput, infoMessage), 5);
        checkEquals(debugInfoPrefix, infoInfoPrefix, "all outputs should retain identical INFO-line decorations");
        checkTrue(debugInfoPrefix.endsWith("[info][class,load]"), "normalized decorations should contain the INFO level and class-load tags");
    }

    /// Finds the physical output line containing `message`.
    private static String lineContaining(String output, String message) {
        return output.lines().filter(line -> line.contains(message)).findFirst().orElseThrow(() -> new AssertionError("No output line contains <" + message + "> in <" + output + ">"));
    }

    /// Removes alignment padding from the requested number of leading decorators.
    private static String normalizedDecoratorPrefix(String line, int decoratorCount) {
        StringBuilder result = new StringBuilder();
        int offset = 0;
        for (int index = 0; index < decoratorCount; index++) {
            int start = line.indexOf('[', offset);
            int end = start < 0 ? -1 : line.indexOf(']', start + 1);
            if (start < 0 || end < 0) {
                throw new AssertionError("Expected " + decoratorCount + " decorators in <" + line + ">");
            }
            result.append('[').append(line.substring(start + 1, end).strip()).append(']');
            offset = end + 1;
        }
        return result.toString();
    }

    /// Runs an operation and verifies that it reports an illegal argument.
    private static void expectFailure(Runnable operation, String failure) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(failure);
    }

    /// Gets the message from an expected illegal-argument failure.
    private static String failureMessage(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return expected.getMessage();
        }
        throw new AssertionError("operation unexpectedly succeeded");
    }

    /// Fails the test when a condition is not true.
    private static void checkTrue(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Expected " + description);
        }
    }

    /// Fails the test when a condition is true.
    private static void checkFalse(boolean condition, String description) {
        if (condition) {
            throw new AssertionError("Expected " + description + " to be false");
        }
    }

    /// Fails the test when two values are not equal.
    private static void checkEquals(Object actual, Object expected, String comparison) {
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError(comparison + ": expected <" + expected + ">, actual <" + actual + ">");
        }
    }

    /// Fails the test when two values are not the same object.
    private static void checkSame(Object actual, Object expected, String comparison) {
        if (actual != expected) {
            throw new AssertionError(comparison + ": expected the same object <" + expected + ">, actual <" + actual + ">");
        }
    }

    /// Fails the test when a target string does not contain a searched substring.
    private static void checkContains(String target, String searched, String comparison) {
        if (!normalizeLineEndings(target).contains(normalizeLineEndings(searched))) {
            throw new AssertionError(comparison + ": expected target string <" + target + "> to contain searched substring <" + searched + ">");
        }
    }

    /// Converts platform-specific line endings so that log content can be compared consistently.
    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    /// Fails the test when a target string contains a searched substring.
    private static void checkNotContains(String target, String searched, String comparison) {
        if (target.contains(searched)) {
            throw new AssertionError(comparison + ": expected target string <" + target + "> not to contain searched substring <" + searched + ">");
        }
    }

    /// Fails when searched substrings do not occur in target in the requested order.
    private static void checkSubstringsInOrder(String target, String[] searched, String comparison) {
        int offset = 0;
        for (String value : searched) {
            int found = target.indexOf(value, offset);
            if (found < 0) {
                throw new AssertionError(comparison + ": expected target string <" + target + "> to contain searched substring <" + value + "> after index <" + offset + ">");
            }
            offset = found + value.length();
        }
    }
}
