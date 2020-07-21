/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.internal.TextListener;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.builtins.SLBuiltinNode;
import com.oracle.truffle.sl.test.SLTestRunner.TestCase;

public class SLTestRunner extends ParentRunner<TestCase> {

    private static final String SOURCE_SUFFIX = ".sl";
    private static final String INPUT_SUFFIX = ".input";
    private static final String OUTPUT_SUFFIX = ".output";

    private static final String LF = System.getProperty("line.separator");

    static class TestCase {
        protected final Description name;
        protected final Path path;
        protected final String sourceName;
        protected final String testInput;
        protected final String expectedOutput;
        protected final Map<String, String> options;
        protected String actualOutput;

        protected TestCase(Class<?> testClass, String baseName, String sourceName, Path path, String testInput, String expectedOutput, Map<String, String> options) {
            this.name = Description.createTestDescription(testClass, baseName);
            this.sourceName = sourceName;
            this.path = path;
            this.testInput = testInput;
            this.expectedOutput = expectedOutput;
            this.options = options;
        }
    }

    private final List<TestCase> testCases;

    public SLTestRunner(Class<?> runningClass) throws InitializationError {
        super(runningClass);
        try {
            testCases = createTests(runningClass);
        } catch (IOException e) {
            throw new InitializationError(e);
        }
    }

    @Override
    protected Description describeChild(TestCase child) {
        return child.name;
    }

    @Override
    protected List<TestCase> getChildren() {
        return testCases;
    }

    protected static List<TestCase> createTests(final Class<?> c) throws IOException, InitializationError {
        SLTestSuite suite = c.getAnnotation(SLTestSuite.class);
        if (suite == null) {
            throw new InitializationError(String.format("@%s annotation required on class '%s' to run with '%s'.", SLTestSuite.class.getSimpleName(), c.getName(), SLTestRunner.class.getSimpleName()));
        }

        String[] paths = suite.value();
        Map<String, String> options = new HashMap<>();
        String[] optionsList = suite.options();
        for (int i = 0; i < optionsList.length; i += 2) {
            options.put(optionsList[i], optionsList[i + 1]);
        }

        Class<?> testCaseDirectory = c;
        if (suite.testCaseDirectory() != SLTestSuite.class) {
            testCaseDirectory = suite.testCaseDirectory();
        }
        Path root = getRootViaResourceURL(testCaseDirectory, paths);

        if (root == null) {
            for (String path : paths) {
                Path candidate = FileSystems.getDefault().getPath(path);
                if (Files.exists(candidate)) {
                    root = candidate;
                    break;
                }
            }
        }
        if (root == null && paths.length > 0) {
            throw new FileNotFoundException(paths[0]);
        }

        final Path rootPath = root;

        final List<TestCase> foundCases = new ArrayList<>();
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
                String sourceName = sourceFile.getFileName().toString();
                if (sourceName.endsWith(SOURCE_SUFFIX)) {
                    String baseName = sourceName.substring(0, sourceName.length() - SOURCE_SUFFIX.length());

                    Path inputFile = sourceFile.resolveSibling(baseName + INPUT_SUFFIX);
                    String testInput = "";
                    if (Files.exists(inputFile)) {
                        testInput = readAllLines(inputFile);
                    }

                    Path outputFile = sourceFile.resolveSibling(baseName + OUTPUT_SUFFIX);
                    String expectedOutput = "";
                    if (Files.exists(outputFile)) {
                        expectedOutput = readAllLines(outputFile);
                    }

                    foundCases.add(new TestCase(c, baseName, sourceName, sourceFile, testInput, expectedOutput, options));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return foundCases;
    }

    /**
     * Recursively deletes a file that may represent a directory.
     */
    private static void delete(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                delete(c);
            }
        }
        if (!f.delete()) {
            PrintStream err = System.err;
            err.println("Failed to delete file: " + f);
        }
    }

    /**
     * Unpacks a jar file to a temporary directory that will be removed when the VM exits.
     *
     * @param jarfilePath the path of the jar to unpack
     * @return the path of the temporary directory
     */
    private static String explodeJarToTempDir(File jarfilePath) {
        try {
            final Path jarfileDir = Files.createTempDirectory(jarfilePath.getName());
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    delete(jarfileDir.toFile());
                }
            });
            jarfileDir.toFile().deleteOnExit();
            JarFile jarfile = new JarFile(jarfilePath);
            Enumeration<JarEntry> entries = jarfile.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (!e.isDirectory()) {
                    File path = new File(jarfileDir.toFile(), e.getName().replace('/', File.separatorChar));
                    File dir = path.getParentFile();
                    dir.mkdirs();
                    assert dir.exists();
                    Files.copy(jarfile.getInputStream(e), path.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return jarfileDir.toFile().getAbsolutePath();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static Path getRootViaResourceURL(final Class<?> c, String[] paths) {
        URL url = c.getResource(c.getSimpleName() + ".class");
        if (url != null) {
            char sep = File.separatorChar;
            String externalForm = url.toExternalForm();
            String classPart = sep + c.getName().replace('.', sep) + ".class";
            String prefix = null;
            String base;
            if (externalForm.startsWith("jar:file:")) {
                prefix = "jar:file:";
                int bang = externalForm.indexOf('!', prefix.length());
                Assume.assumeTrue(bang != -1);
                File jarfilePath = new File(externalForm.substring(prefix.length(), bang));
                Assume.assumeTrue(jarfilePath.exists());
                base = explodeJarToTempDir(jarfilePath);
            } else if (externalForm.startsWith("file:")) {
                prefix = "file:";
                base = externalForm.substring(prefix.length(), externalForm.length() - classPart.length());
            } else {
                return null;
            }
            for (String path : paths) {
                String candidate = base + sep + path;
                if (new File(candidate).exists()) {
                    return FileSystems.getDefault().getPath(candidate);
                }
            }
        }
        return null;
    }

    private static String readAllLines(Path file) throws IOException {
        // fix line feeds for non unix os
        StringBuilder outFile = new StringBuilder();
        for (String line : Files.readAllLines(file, Charset.defaultCharset())) {
            outFile.append(line).append(LF);
        }
        return outFile.toString();
    }

    private static final List<NodeFactory<? extends SLBuiltinNode>> builtins = new ArrayList<>();

    public static void installBuiltin(NodeFactory<? extends SLBuiltinNode> builtin) {
        builtins.add(builtin);
    }

    @Override
    protected void runChild(TestCase testCase, RunNotifier notifier) {
        notifier.fireTestStarted(testCase.name);

        Context context = null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (NodeFactory<? extends SLBuiltinNode> builtin : builtins) {
                SLLanguage.installBuiltin(builtin);
            }

            Context.Builder builder = Context.newBuilder().allowExperimentalOptions(true).in(new ByteArrayInputStream(testCase.testInput.getBytes("UTF-8"))).out(out);
            for (Map.Entry<String, String> e : testCase.options.entrySet()) {
                builder.option(e.getKey(), e.getValue());
            }
            context = builder.build();
            PrintWriter printer = new PrintWriter(out);
            run(context, testCase.path, printer);
            printer.flush();

            String actualOutput = new String(out.toByteArray());
            Assert.assertEquals(testCase.name.toString(), testCase.expectedOutput, actualOutput);
        } catch (Throwable ex) {
            notifier.fireTestFailure(new Failure(testCase.name, ex));
        } finally {
            if (context != null) {
                context.close();
            }
            notifier.fireTestFinished(testCase.name);
        }
    }

    private static void run(Context context, Path path, PrintWriter out) throws IOException {
        try {
            /* Parse the SL source file. */
            Source source = Source.newBuilder(SLLanguage.ID, path.toFile()).interactive(true).build();

            /* Call the main entry point, without any arguments. */
            context.eval(source);
        } catch (PolyglotException ex) {
            if (!ex.isInternalError()) {
                out.println(ex.getMessage());
            } else {
                throw ex;
            }
        }
    }

    public static void runInMain(Class<?> testClass, String[] args) throws InitializationError, NoTestsRemainException {
        JUnitCore core = new JUnitCore();
        core.addListener(new TextListener(System.out));
        SLTestRunner suite = new SLTestRunner(testClass);
        if (args.length > 0) {
            suite.filter(new NameFilter(args[0]));
        }
        Result r = core.run(suite);
        if (!r.wasSuccessful()) {
            System.exit(1);
        }
    }

    private static final class NameFilter extends Filter {
        private final String pattern;

        private NameFilter(String pattern) {
            this.pattern = pattern.toLowerCase();
        }

        @Override
        public boolean shouldRun(Description description) {
            return description.getMethodName().toLowerCase().contains(pattern);
        }

        @Override
        public String describe() {
            return "Filter contains " + pattern;
        }
    }

}
