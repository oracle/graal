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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
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
import com.oracle.svm.guest.staging.option.RuntimeOptionParser;
import com.oracle.svm.test.NativeImageBuildArgs;

/// Exercises the SVM unified logging implementation through the native JUnit runner.
@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:+StrictRuntimeJavaOptions",
                "-H:-UnlockExperimentalVMOptions"
})
@SuppressWarnings("static-method")
public final class UnifiedLoggingTest {
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

    /// Verifies that runtime option parsing preserves an existing logging configuration on both
    /// success and failure.
    @Test
    public void testRuntimeOptionParsingPreservesLoggingConfiguration() {
        LogConfiguration.disableLogging();
        TestLogOutput output = new TestLogOutput("runtime-option-parser");
        Target_com_oracle_svm_core_logging_LogConfiguration.configureOutput(LogSelectionList.parse("class+load=debug"), output, LogDecorators.NONE);
        Target_com_oracle_svm_core_logging_LogTagSet classLoadTagSet = (Target_com_oracle_svm_core_logging_LogTagSet) (Object) LogTagSet.class_load;
        Target_com_oracle_svm_core_logging_LogOutputList classLoadOutputs = (Target_com_oracle_svm_core_logging_LogOutputList) (Object) classLoadTagSet.outputList();
        try {
            RuntimeOptionParser.parseAndConsumeAllOptions(new String[0], false);
            checkSame(classLoadOutputs.levelFor(output), LogLevel.DEBUG, "successful runtime parsing should preserve logging");
            expectFailure(() -> RuntimeOptionParser.parseAndConsumeAllOptions(new String[]{"-XX:UnknownUnifiedLoggingTestOption=1"}, false), "invalid runtime option was accepted");
            checkSame(classLoadOutputs.levelFor(output), LogLevel.DEBUG, "failed runtime parsing should preserve logging");
        } finally {
            LogConfiguration.disableLogging();
        }
    }

    /// Verifies that startup timestamp formatting uses its explicit native local offset.
    @Test
    public void testStartupTimestamp() {
        checkEquals(Target_com_oracle_svm_core_logging_LogConfiguration.formatStartupTimestamp(0, 0), "1970-01-01_00-00-00", "UTC startup timestamp");
        checkEquals(Target_com_oracle_svm_core_logging_LogConfiguration.formatStartupTimestamp(0, 19_800), "1970-01-01_05-30-00", "positive-offset startup timestamp");
        checkEquals(Target_com_oracle_svm_core_logging_LogConfiguration.formatStartupTimestamp(0, -28_800), "1969-12-31_16-00-00", "negative-offset startup timestamp");
    }

    /// Verifies that repeated logging completion does not emit startup records or register startup
    /// state again.
    @Test
    public void testLoggingCompletionIsIdempotent() {
        LogConfiguration.disableLogging();
        CapturingLogOutput output = new CapturingLogOutput();
        Target_com_oracle_svm_core_logging_LogConfiguration.configureOutput(LogSelectionList.parse("logging=info"), output, LogDecorators.NONE);
        boolean previousInitializationComplete = Target_com_oracle_svm_core_logging_LogConfiguration.initializationComplete;
        Target_com_oracle_svm_core_logging_LogConfiguration.initializationComplete = false;
        try {
            LogConfiguration.logInitializationComplete();
            String firstCompletionOutput = output.contents();
            checkContains(firstCompletionOutput, "Log configuration fully initialized.", "first completion should emit startup diagnostics");
            LogConfiguration.logInitializationComplete();
            checkEquals(output.contents(), firstCompletionOutput, "second completion should not emit startup diagnostics");
        } finally {
            Target_com_oracle_svm_core_logging_LogConfiguration.initializationComplete = previousInitializationComplete;
            LogConfiguration.disableLogging();
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
        awaitLoggingMemory(baseline);
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
        checkFalse(Files.exists(Path.of(existingLogFile + ".0")), "a preexisting active file should not be archived at startup");
        checkNotContains(read(existingLogFile), "existing log contents", "startup should discard preexisting active file contents");
        checkContains(read(existingLogFile), "new active log contents", "startup should write to the replaced active file");
        delete(existingLogFile);

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

    /// Verifies that file output performs normal rotation while logging from a VM operation.
    @Test
    public void testFileOutputFromVMOperation() throws IOException {
        String logFile = testLogFile("file-output-vm-operation");
        LogConfiguration.disableLogging();
        delete(logFile);
        delete(logFile + ".0");
        try {
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=info:file=" + logFile + ":none:filecount=2,filesize=1"),
                            "VM operation file output configuration should be accepted");

            new LoggingVMOperation().enqueue();

            checkTrue(Files.exists(Path.of(logFile + ".0")), "VM operation file output should rotate");
            checkContains(read(logFile + ".0"), "message from VM operation", "rotated output should contain the VM operation message");
        } finally {
            LogConfiguration.disableLogging();
            delete(logFile);
            delete(logFile + ".0");
        }
    }

    /// Verifies that `%i` expands to the current isolate identifier in a file output path.
    @Test
    public void testIsolateIdFilenamePlaceholder() throws IOException {
        String logFilePattern = testLogFile("file-output-isolate-%i");
        String logFile = logFilePattern.replace("%i", Long.toString(Isolates.getIsolateId()));
        LogConfiguration.disableLogging();
        delete(logFile);
        try {
            checkTrue(LogConfiguration.parseCommandLineArgument("-Xlog:class+load=info:file=" + logFilePattern + ":none"), "isolate file output configuration should be accepted");
            LogTagSet.class_load.info("isolate-specific file output");
            LogConfiguration.disableLogging();
            checkContains(read(logFile), "isolate-specific file output", "the isolate placeholder should identify the current isolate");
        } finally {
            LogConfiguration.disableLogging();
            delete(logFile);
        }
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

    /// Captures output for assertions about emitted startup diagnostics.
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


    /// Waits for post-termination thread listeners to release native logging state.
    private static void awaitLoggingMemory(long expected) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (true) {
            long actual = NativeMemoryTracking.singleton().getMallocMemory(NmtCategory.Logging);
            if (actual == expected) {
                return;
            }
            if (System.nanoTime() >= deadline) {
                checkEquals(actual, expected, "thread-local logging buffers should be released after thread exit");
                return;
            }
            Thread.onSpinWait();
        }
    }

    /// Logs while executing at a safepoint and records the executing thread for comparison.
    private static final class LoggingVMOperation extends JavaVMOperation {
        LoggingVMOperation() {
            super(VMOperationInfos.get(LoggingVMOperation.class, "Unified logging at safepoint", VMOperation.SystemEffect.SAFEPOINT));
        }

        /// Emits a class-loading message from the VM operation thread.
        @Override
        protected void operate() {
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
}
