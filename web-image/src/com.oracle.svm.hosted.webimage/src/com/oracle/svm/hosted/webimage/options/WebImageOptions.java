/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.options;

import java.io.OutputStream;
import java.io.PrintStream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.webimage.codegen.ClosureCompilerSupport;
import com.oracle.svm.hosted.webimage.name.WebImageNamingConvention;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;
import com.oracle.svm.webimage.platform.WebImagePlatform;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.EnumOptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;

public class WebImageOptions {

    public enum VMType {
        Browser,
        Node,
        Generic;

        /**
         * Returns true if the given target runtime is included in 'this' runtime.
         *
         * For example, the Generic runtime includes all other runtimes.
         */
        boolean includes(VMType target) {
            if (this == Generic) {
                return true;
            }

            return this == target;
        }
    }

    public enum CompilerBackend {
        JS(WebImageJSPlatform.class),
        WASM(WebImageWasmLMPlatform.class),
        WASMGC(WebImageWasmGCPlatform.class);

        public final Class<? extends WebImagePlatform> platform;

        CompilerBackend(Class<? extends WebImagePlatform> platform) {
            this.platform = platform;
        }

        public static CompilerBackend fromPlatform(Platform platform) {
            if (platform instanceof WebImageJSPlatform) {
                return JS;
            } else if (platform instanceof WebImageWasmLMPlatform) {
                return WASM;
            } else if (platform instanceof WebImageWasmGCPlatform) {
                return WASMGC;
            } else {
                throw GraalError.shouldNotReachHere(String.valueOf(platform)); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    public static final class LoggerOptions {
        public enum LoggingFormat {
            ReadableText,
            BenchmarkText
        }

        @Option(help = "Type of formatting to use during Web Image metrics printing.")//
        public static final EnumOptionKey<LoggingFormat> LoggingStyle = new EnumOptionKey<>(LoggingFormat.ReadableText);

        @Option(help = "Pattern for specifying scopes in which Web Image metric logging is enabled. By default logging is disabled. " +
                        "See file:../logging/doc-files/LogFilter.txt for detailed description of filter option.")//
        public static final OptionKey<String> LogFilter = new OptionKey<>(null);

        @Option(help = "Specify file in which to store logged Web Image statistics. See file:../logging/doc-files/LoggingFile.txt " +
                        "for detailed description of format.")//
        public static final OptionKey<String> LoggingFile = new OptionKey<>(null);
    }

    public static final class DebugOptions {
        @Option(help = "Dumping during function compilation: every function will be dumped to a separate file," +
                        " every node from scheduling which is lowered to js will be dumped with the associated scope (with parent relation) it was associated with")//
        public static final OptionKey<Boolean> DumpCurrentCompiledFunction = new OptionKey<>(false);

        @Option(help = "Instruments the generated code with timers.")//
        public static final OptionKey<Boolean> GenTimingCode = new OptionKey<>(false);

        @Option(help = "Enables runtime debug checks/assertions")//
        public static final HostedOptionKey<Boolean> RuntimeDebugChecks = new HostedOptionKey<>(false);

        @Option(help = "Enable verification phases.")//
        public static final HostedOptionKey<Boolean> VerificationPhases = new HostedOptionKey<>(false);

        @Option(help = "Dump type control graph, a graph of dependencies between types, methods, and inspected objects.")//
        public static final OptionKey<Boolean> DumpTypeControlGraph = new OptionKey<>(false);

        @Option(help = "Dump the expected value of the ProvidedHostedOptions property.")//
        public static final HostedOptionKey<Boolean> DumpProvidedHostedOptionsAndExit = new HostedOptionKey<>(false);
    }

    /**
     * Web Image only.
     * <p>
     * Do not read this value directly. Instead, look it up based on the selected {@link Platform}
     * using {@link #getBackend}.
     */
    @Option(help = "The Web Image Backend to use.") //
    public static final EnumOptionKey<CompilerBackend> Backend = new EnumOptionKey<>(CompilerBackend.JS);

    @Option(help = "Report the code sizes of different parts of the generated JavaScript image. If the closure compiler is applied, this instruments the generated javascript code by injecting labels.")//
    public static final HostedOptionKey<Boolean> ReportImageSizeBreakdown = new HostedOptionKey<>(false);

    @Option(help = "The JavaScript runtime this build should target.")//
    public static final EnumOptionKey<VMType> JSRuntime = new EnumOptionKey<>(VMType.Generic);

    /**
     * Access value of {@link #JSRuntime} for printing.
     *
     * This value should not be used to make compilation decisions.
     */
    public static String getTargetVM() {
        return JSRuntime.getValue(HostedOptionValues.singleton()).name();
    }

    /**
     * Returns true if the given target runtime should be supported.
     */
    public static boolean supportRuntime(VMType target) {
        return JSRuntime.getValue(HostedOptionValues.singleton()).includes(target);
    }

    /**
     * Determines the compiler backend from the target {@link Platform}.
     */
    public static CompilerBackend getBackend() {
        return CompilerBackend.fromPlatform(ImageSingletons.lookup(Platform.class));
    }

    public static CompilerBackend getBackend(ImageClassLoader loader) {
        return CompilerBackend.fromPlatform(loader.platform);
    }

    /**
     * Whether we are running as a Native Image backend or a Web Image one (i.e. which launcher was
     * used).
     * <p>
     * Since Native Image is a released product, it has some stronger requirements as to what this
     * backend is allowed to do. This should be used make certain functionality is available under
     * only Web Image or only Native Image.
     * <p>
     * The Web Image launcher sets the {@code com.oracle.graalvm.iswebimage} system property to
     * {@code true}. So effectively, this tells us whether the code was launched from the
     * `native-image` launcher (returns {@code true}) or the `web-image` launcher (returns
     * {@code false}).
     */
    @Fold
    public static boolean isNativeImageBackend() {
        return !Boolean.getBoolean("com.oracle.graalvm.iswebimage");
    }

    @Option(help = "Naming Convention for object, method, and property names. (FULL = Always uses qualified names, REDUCED = Plain name is used if possible, MINIMAL = Names replaced with numbered identifiers)")//
    public static final EnumOptionKey<WebImageNamingConvention.NamingMode> NamingConvention = new EnumOptionKey<>(WebImageNamingConvention.NamingMode.MINIMAL);

    @Option(help = "Generate vtable code instead of always using JavaScript's dynamic dispatch." +
                    "Using vtables is slightly faster but also slightly increases code size")//
    public static final OptionKey<Boolean> UseVtable = new OptionKey<>(true);

    @Option(help = "Determine if a last pass using the closure compiler should be done over the generated js code")//
    public static final HostedOptionKey<Boolean> ClosureCompiler = new HostedOptionKey<>(true, WebImageOptions::validateClosureCompiler) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (!newValue && !values.containsKey(NamingConvention)) {
                // If the Closure compiler is disabled, and the naming convention is not specified,
                // it should be REDUCED.
                NamingConvention.update(values, WebImageNamingConvention.NamingMode.REDUCED);
            }
        }
    };

    private static void validateClosureCompiler(HostedOptionKey<Boolean> optionKey) {
        if (optionKey.getValue() && !ClosureCompilerSupport.isAvailable()) {
            throw UserError.abort("The Google Closure Compiler is not available in this distribution, please turn off the '%s' option", optionKey.getName());
        }
    }

    public enum ClosurePrettyPrintLevel {
        /**
         * No pretty-printing.
         * <p>
         * The closure compiler emits fully-minified code
         */
        NONE,
        /**
         * Pretty-print only.
         * <p>
         * The closure compiler applies a pretty-print pass at the end.
         */
        SIMPLE,
        /**
         * Pretty-print with readable names.
         * <p>
         * Everything from {@link #SIMPLE}, turns off variable renaming, and sets reduced
         * {@link #NamingConvention} unless set explicitly.
         */
        FULL;

        /**
         * All levels except {@link #FULL} still rename variables.
         */
        public boolean renameVariables() {
            return ordinal() < FULL.ordinal();
        }

        /**
         * All levels except {@link #NONE} turn on the pretty-print pass.
         */
        public boolean prettyPrint() {
            return ordinal() > NONE.ordinal();
        }
    }

    @Option(help = "Pretty-printing level if the closure compiler is enabled to make debugging optimized images easier.")//
    public static final EnumOptionKey<ClosurePrettyPrintLevel> ClosurePrettyPrint = new EnumOptionKey<>(ClosurePrettyPrintLevel.NONE) {
        /**
         * If the Closure Compiler pretty-print level does not rename variables, the naming
         * convention should be {@code REDUCED} to produce readable names.
         * <p>
         * This only applies, if the naming convention is not explicitly set.
         */
        @Override
        public void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, ClosurePrettyPrintLevel oldValue, ClosurePrettyPrintLevel newValue) {
            if (!newValue.renameVariables() && !values.containsKey(NamingConvention)) {
                NamingConvention.update(values, WebImageNamingConvention.NamingMode.REDUCED);
            }
        }
    };

    @Option(help = "If the closure compiler is used, also dump the generated code before the closure compiler runs.")//
    public static final HostedOptionKey<Boolean> DumpPreClosure = new HostedOptionKey<>(false);

    @Option(help = "Treat warnings as compiler errors (e.g. in the Closure compiler)")//
    public static final HostedOptionKey<Boolean> StrictWarnings = new HostedOptionKey<>(false);

    @Option(help = "Lower Image Heap Arrays as a Base64 String. Used to reduce code size.")//
    public static final OptionKey<Boolean> EncodeImageHeapArraysBase64 = new OptionKey<>(true);

    @Option(help = "Name to use for the JavaScript class that represents the VM.")//
    public static final OptionKey<String> VMClassName = new OptionKey<>("GraalVM");

    @Option(help = "Generates code that automatically runs the VM after its code is loaded (otherwise, the user must run the VM manually).")//
    public static final OptionKey<Boolean> AutoRunVM = new OptionKey<>(true);

    @Option(help = "The list of libraries to fetch before the VM starts (comma-separated <library-name>:<library-url> pairs, where URLs can be relative). Only used when AutoRunVM is enabled.")//
    public static final OptionKey<String> AutoRunLibraries = new OptionKey<>("");

    @Option(help = "Whether the compiler should outline runtime checks (e.g. null checks and array bound checks) to reduce code size.")//
    public static final OptionKey<Boolean> OutlineRuntimeChecks = new OptionKey<>(true);

    @Option(help = "Generate source maps to debug Java code")//
    public static final OptionKey<Boolean> GenerateSourceMap = new OptionKey<>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (newValue) {
                SubstrateOptions.IncludeNodeSourcePositions.update(values, oldValue);
            }
        }
    };

    @Option(help = "Directory containing source files for DevTools. May be relative to output file.")//
    public static final OptionKey<String> SourceMapSourceRoot = new OptionKey<>("");

    @Option(help = "Determines if compound conditions should be rewritten")//
    public static final OptionKey<Boolean> AnalyzeCompoundConditionals = new OptionKey<>(true);

    @Option(help = "Determines whether partial escape analysis should be used [default false, increases code size but also runtime performance]")//
    public static final OptionKey<Boolean> UsePEA = new OptionKey<>(false);

    public static final class SemanticOptions {
        @Option(help = "Emit a fround around every float arithmetic operation to ensure valid value range is kept")//
        public static final OptionKey<Boolean> ForceSinglePrecision = new OptionKey<>(true);
    }

    @Option(help = "Determine the level of verbosity for generated comments.")//
    protected static final EnumOptionKey<CommentVerbosity> JSComments = new EnumOptionKey<>(CommentVerbosity.NONE);

    @Option(help = "Use Random instead of SecureRandom for generating temporary file names. This reduces image size when using temporary files.")//
    public static final OptionKey<Boolean> UseRandomForTempFiles = new OptionKey<>(true);

    @Option(help = "Provide additional entry points via a configuration file. The format is the same as reflect configuration.", type = OptionType.User)//
    public static final OptionKey<String> EntryPointsConfig = new OptionKey<>(null);

    public enum CommentVerbosity {
        /**
         * No comments are emitted.
         */
        NONE,
        /**
         * Generates a minimal number of comments.
         * <p>
         * This includes any fully qualified names of classes, methods, and field names.
         */
        MINIMAL,
        /**
         * Most comments are emitted.
         * <p>
         * This includes comments about individual nodes.
         */
        NORMAL,
        /**
         * All comments are emitted.
         */
        VERBOSE;

        /**
         * Returns true if this verbosity is at least as verbose as the required verbosity.
         */
        public boolean isEnabled(CommentVerbosity required) {
            return required.ordinal() <= this.ordinal();
        }
    }

    public static boolean genJSComments() {
        return genJSComments(null);
    }

    /**
     * Returns true if js comments should be emitted for the given verbosity (or
     * {@link CommentVerbosity#NORMAL} is {@code null}).
     */
    public static boolean genJSComments(CommentVerbosity verbosity) {
        return JSComments.getValue(HostedOptionValues.singleton()).isEnabled(verbosity == null ? CommentVerbosity.NORMAL : verbosity);
    }

    @Option(help = "Determine if the Web Image compilation should be silent and not dump info")//
    public static final OptionKey<Boolean> SILENT_COMPILE = new OptionKey<>(false);

    @Option(help = "Set the name of the benchmark that is executed. Allows to output benchmarking data during compilation")//
    public static final OptionKey<String> BenchmarkName = new OptionKey<>(null);

    public static PrintStream compilerPrinter(OptionValues options) {
        if (SILENT_COMPILE.getValue(options)) {
            return new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                    // silent mode
                }
            });
        } else {
            return System.out;
        }
    }
}
