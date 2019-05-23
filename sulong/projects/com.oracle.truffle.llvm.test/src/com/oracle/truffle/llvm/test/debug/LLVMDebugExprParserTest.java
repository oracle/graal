package com.oracle.truffle.llvm.test.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context.Builder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.test.options.TestOptions;

@RunWith(Parameterized.class)
public final class LLVMDebugExprParserTest extends LLVMDebugTestBase {

    private static final Path BC_DIR_PATH = Paths.get(TestOptions.TEST_SUITE_PATH, "debugexpr");
    private static final Path SRC_DIR_PATH = Paths.get(TestOptions.PROJECT_ROOT, "..", "tests", "com.oracle.truffle.llvm.tests.debugexpr", "debug");
    private static final Path TRACE_DIR_PATH = Paths.get(TestOptions.PROJECT_ROOT, "..", "tests", "com.oracle.truffle.llvm.tests.debugexpr", "trace");

    private static final String EXPERIMENTAL_OPTIONS = "experimental-options";
    private static final String OPTION_ENABLE_LVI = "llvm.enableLVI";

    private static final String CONFIGURATION = "O1.bc";

    public LLVMDebugExprParserTest(String testName, String configuration) {
        super(testName, configuration);
    }

    @Override
    void setContextOptions(Builder contextBuilder) {
// contextBuilder.option(EXPERIMENTAL_OPTIONS, String.valueOf(true));
        contextBuilder.option(OPTION_ENABLE_LVI, String.valueOf(true));
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> getConfigurations() {
        try (Stream<Path> dirs = Files.walk(BC_DIR_PATH)) {
            return dirs.filter(path -> path.endsWith(CONFIGURATION)).map(path -> new Object[]{path.getParent().getFileName().toString(), CONFIGURATION}).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Error while finding tests!", e);
        }
    }

    @Override
    Path getBitcodePath() {
        return BC_DIR_PATH;
    }

    @Override
    Path getSourcePath() {
        return SRC_DIR_PATH;
    }

    @Override
    Path getTracePath() {
        return TRACE_DIR_PATH;
    }

// @Test
// @Override
// public void test() throws Throwable {
// }

}
