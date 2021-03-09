/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.llvm.tests.services.TestEngineConfig;
import org.graalvm.polyglot.Context;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.tck.TruffleRunner;

public abstract class CommonTestUtils {

    public static final String TEST_DIR_EXT = ".dir";

    public static final Set<String> supportedFiles = new HashSet<>(Arrays.asList("f90", "f", "f03", "c", "cpp", "cc", "C", "m"));

    public static final Predicate<? super Path> isExecutable = f -> f.getFileName().toString().endsWith(".out");
    public static final Predicate<? super Path> isIncludeFile = f -> f.getFileName().toString().endsWith(".include");
    public static final Predicate<? super Path> isExcludeFile = f -> f.getFileName().toString().endsWith(".exclude");
    public static final Predicate<? super Path> isSulong = f -> f.getFileName().toString().endsWith(".bc");
    public static final Predicate<? super Path> isFile = f -> f.toFile().isFile();

    public static Set<Path> getFiles(Path source) {
        try (Stream<Path> files = Files.walk(source)) {
            return files.filter(f -> supportedFiles.contains(getFileEnding(f.getFileName().toString()))).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Error getting files.", e);
        }
    }

    public static String getFileEnding(String s) {
        return s.substring(s.lastIndexOf('.') + 1);
    }

    public static class RunWithTestEngineConfigRule implements TestRule {

        private final TruffleRunner.RunWithPolyglotRule rule;

        public RunWithTestEngineConfigRule() {
            this(c -> {
            });
        }

        public RunWithTestEngineConfigRule(Consumer<Context.Builder> contextBuilderUpdater) {
            rule = new TruffleRunner.RunWithPolyglotRule(updateContext(getContextBuilder(), contextBuilderUpdater));
        }

        private static Context.Builder updateContext(Context.Builder contextBuilder, Consumer<Context.Builder> contextBuilderUpdater) {
            contextBuilderUpdater.accept(contextBuilder);
            return contextBuilder;
        }

        private static Context.Builder getContextBuilder() {
            Map<String, String> options = TestEngineConfig.getInstance().getContextOptions();
            return Context.newBuilder().allowAllAccess(true).options(options);
        }

        @Override
        public Statement apply(Statement stmt, Description description) {
            return rule.apply(stmt, description);
        }

        public Context getPolyglotContext() {
            return rule.getPolyglotContext();
        }

        public TruffleLanguage.Env getTruffleTestEnv() {
            return rule.getTruffleTestEnv();
        }

        public TruffleLanguage<?> getTestLanguage() {
            return rule.getTestLanguage();
        }
    }
}
