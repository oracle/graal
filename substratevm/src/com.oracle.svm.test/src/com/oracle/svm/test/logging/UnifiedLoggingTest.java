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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.junit.Test;

import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.logging.LogConfiguration;
import com.oracle.svm.core.logging.LogDecorators;
import com.oracle.svm.core.logging.LogLevel;
import com.oracle.svm.core.logging.LogOutput;
import com.oracle.svm.core.logging.LogOutputList;
import com.oracle.svm.core.logging.LogSelection;
import com.oracle.svm.core.logging.LogSelectionList;
import com.oracle.svm.core.logging.LogTag;
import com.oracle.svm.core.logging.LogTagSet;
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

}
