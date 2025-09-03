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

package com.oracle.svm.hosted.webimage.closurecompiler;

import static com.oracle.svm.hosted.webimage.codegen.WebImageJSCodeGen.ClosureWhitespaceTimer;
import static org.graalvm.webimage.api.JS.Import;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.graalvm.collections.MapCursor;

import com.google.javascript.jscomp.AbstractCommandLineRunner;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.StrictWarningsGuard;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.rhino.StaticSourceFile;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.DeadlockWatchdog;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.webimage.codegen.ClosureCompilerSupport;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.WebImageEntryFunctionLowerer;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;

/**
 * Encapsulation for the optional Google Closure Compiler.
 * <p>
 * No references to this class must exist. Instead {@link ClosureCompilerSupport} must be used to
 * get an instance of this class.
 *
 * @see ClosureCompilerSupport
 */
public class ClosureCompilerSupportImpl implements ClosureCompilerSupport {

    private final DeadlockWatchdog watchdog;
    private final JSCodeGenTool codeGenTool;
    private final String filename;
    private final String imageName;

    /**
     * Constructor is invoked through reflection.
     */
    public ClosureCompilerSupportImpl(DeadlockWatchdog watchdog, JSCodeGenTool codeGenTool, String imageName) {
        this.watchdog = watchdog;
        this.codeGenTool = codeGenTool;
        this.filename = imageName + ".js";
        this.imageName = imageName;
    }

    @Override
    public String applyClosureCompiler(String inputSourceCode) {
        Compiler.setLoggingLevel(Level.SEVERE);

        dumpPreClosure(imageName + ".preclosure.js", () -> inputSourceCode);

        String sourceCode = maybeReduceLines(inputSourceCode);

        final CompilerOptions closureCompOpts = createClosureCompilerOptions();

        /*
         * Collect externs we generate in separate list so that only those are dumped when
         * pre-closure dumping is turned on.
         */
        List<SourceFile> ourExterns = new ArrayList<>();

        // Add externs file that exposes the user-facing Web Image VM API.
        ourExterns.add(vmApiExterns());
        // Add externs file for externally defined JavaScript classes.
        ourExterns.add(jsExterns());

        /*
         * Node.js externs are always needed due to the feature detection referencing node-specific
         * variables
         */
        ourExterns.add(nodejsExterns());

        // Dump all externs files generated by Web Image
        for (SourceFile f : ourExterns) {
            dumpPreClosure(f.getName(), f::getCode);
        }

        List<SourceFile> externs = new ArrayList<>();
        /*
         * Add the closure compiler externs first. Otherwise, those externs may redeclare symbols in
         * our externs and fail the compilation. Our externs suppress those warnings to not fail the
         * compilation.
         */
        try {
            // TODO also add externs for stuff we use in our code (e.g process.argv for node)
            // With that we could enable property renaming
            externs.addAll(AbstractCommandLineRunner.getBuiltinExterns(closureCompOpts.getEnvironment()));
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }

        externs.addAll(ourExterns);

        final Compiler closureCompiler = new Compiler();
        Result r = compile(closureCompiler, externs, sourceCode, closureCompOpts);
        if (!r.warnings.isEmpty()) {
            codeGenTool.getProviders().stdout().println("Warnings:");
            for (JSError e : r.warnings) {
                codeGenTool.getProviders().stdout().println(e.toString());
            }
        }

        if (!r.errors.isEmpty()) {
            System.err.println("Errors:");
            for (JSError e : r.errors) {
                System.err.println(e.toString());
            }
        }

        VMError.guarantee(r.success, "Closure compilation is successful");

        return closureCompiler.toSource();
    }

    /**
     * May reduce the number of lines in the source code.
     * <p>
     * The closure compiler may crash when processing files with 2^20 lines or more. This is because
     * it uses a 32bit integer to store the source code position for its IR nodes. 12 bits are used
     * for the column number and 20 for the line number. Nodes after line 2^20 will be attributed
     * the wrong line and if they produce a warning or error, the closure compiler will try to index
     * into the source code string for the wrong line and potentially crash.
     * <p>
     * For source files longer than 2^20 lines, we first run a WHITESPACE_ONLY optimization to
     * reduce the number of lines.
     *
     * @return The new source code
     */
    @SuppressWarnings("try")
    protected String maybeReduceLines(String inputSourceCode) {
        int numLines;
        try {
            LineNumberReader lineNumberReader = new LineNumberReader(new StringReader(inputSourceCode));
            // Skip to the end
            lineNumberReader.skip(Long.MAX_VALUE);
            numLines = lineNumberReader.getLineNumber();
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }

        if (numLines < 1 << 20) {
            return inputSourceCode;
        }

        try (Timer.StopTimer t = TimerCollection.createTimerAndStart(ClosureWhitespaceTimer)) {
            codeGenTool.getProviders().stdout().println("Applying WHITESPACE_ONLY closure compiler pass.");

            final CompilerOptions closureCompOpts = new CompilerOptions();
            CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(closureCompOpts);
            closureCompOpts.setPreserveTypeAnnotations(true);

            final Compiler closureCompiler = new Compiler();
            Result r = compile(closureCompiler, Collections.emptyList(), inputSourceCode, closureCompOpts);

            VMError.guarantee(r.success, "WHITESPACE_ONLY Closure compilation is successful");

            String resultSourceCode = closureCompiler.toSource();
            dumpPreClosure(imageName + ".whitespace.js", () -> resultSourceCode);
            return resultSourceCode;
        }
    }

    /**
     * Compiles a single source file with the closure compiler.
     * <p>
     * The closure compiler may take a considerable amount of time. Because of that, the compilation
     * is run in a background thread with the current thread calling the {@link #watchdog}
     * regularly.
     *
     * @param compiler Instantiated compiler
     * @param externs List of source files with extern declarations
     * @param sourceCode The main source code contents
     * @param options Addition closure compiler options.
     * @return A {@link Result compilation result}. The compilation may have failed.
     */
    protected Result compile(Compiler compiler, List<SourceFile> externs, String sourceCode, CompilerOptions options) {
        AtomicReference<Result> resultHolder = new AtomicReference<>();
        Thread workThread = new Thread(() -> {
            SourceFile s = SourceFile.fromCode(filename, sourceCode);
            resultHolder.set(compiler.compile(externs, Collections.singletonList(s), options));
        });
        workThread.start();

        while (workThread.isAlive()) {
            watchdog.recordActivity();
            try {
                // Record activity once per minute.
                workThread.join(60000);
            } catch (InterruptedException ex) {
                throw VMError.shouldNotReachHere("Interrupted while waiting for Closure to finish", ex.getCause());
            }
        }

        return Objects.requireNonNull(resultHolder.get());
    }

    /**
     * Dumps the given code to file if {@link WebImageOptions#DumpPreClosure} is turned on.
     *
     * @param name Filename for the dumped file
     * @param codeSupplier Is called to get the source code
     */
    private static void dumpPreClosure(String name, Callable<String> codeSupplier) {
        if (WebImageOptions.DumpPreClosure.getValue()) {
            try (PrintWriter out = new PrintWriter(NativeImageGenerator.getOutputDirectory().resolve(name).toFile())) {
                out.write(codeSupplier.call());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static SourceFile nodejsExterns() {
        return SourceFile.fromCode("externs-nodejs.js",
                        new BufferedReader(new InputStreamReader(ClosureCompilerSupportImpl.class.getResourceAsStream("externs/externs-node.js"))).lines().collect(Collectors.joining("\n")),
                        StaticSourceFile.SourceKind.EXTERN);
    }

    private static String getExternClass(JSCodeGenTool.ExternClassDescriptor descriptor) {
        StringBuilder sb = new StringBuilder();
        sb.append("class {\n");
        if (descriptor != null) {
            sb.append("  constructor() {\n");
            for (String p : descriptor.getProperties()) {
                sb.append("    this." + p + ";\n");
            }
            sb.append("  }\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private SourceFile vmApiExterns() {
        // This is a Closure-Compiler-documented way of making sure symbols are not erased.
        // The externs file declares symbols that are in the actual generated image.
        // To avoid redeclaration warnings, a suppress annotation is used when redeclaring such
        // symbols in the image.
        String code = "";
        code += "/**\n";
        code += " * @fileoverview Public API of the Web Image image for " + this.filename + ".\n";
        code += " * @externs\n";
        code += " */\n";
        code += "var " + codeGenTool.vmClassName() + " = {};\n";
        code += codeGenTool.vmClassName() + "." + WebImageEntryFunctionLowerer.FUNCTION.getFunctionName() + " = async function(vmArgs, config = {}) {};\n";
        return SourceFile.fromCode(this.filename + ".api.externs.js", code, StaticSourceFile.SourceKind.EXTERN);
    }

    /**
     * Creates a closure compiler externs file for externally defined classes that are imported by
     * {@link Import}.
     * <p>
     * For any classes that are components of other classes (e.g. <code>Foo.Bar</code>), it will
     * also generate definitions for all the preceding types (<code>Foo</code> in this case).
     */
    private SourceFile jsExterns() {
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n");
        sb.append(" * @fileoverview User-defined JS classes. \n");
        sb.append(" * @externs\n");
        sb.append(" */\n");
        sb.append("\n");

        /*
         * A set is needed because each element should only be defined once. LinkedHashSet is used
         * because the insertion order has to be preserved as components added later depend on
         * previous components.
         */
        Map<String, JSCodeGenTool.ExternClassDescriptor> variables = new LinkedHashMap<>();
        Map<String, JSCodeGenTool.ExternClassDescriptor> components = new LinkedHashMap<>();

        /*
         * Split the registered class names into base variables and components of those base
         * variables (and other components).
         */
        MapCursor<String, JSCodeGenTool.ExternClassDescriptor> classes = codeGenTool.getExternJSClasses();
        while (classes.advance()) {
            String name = classes.getKey();
            JSCodeGenTool.ExternClassDescriptor descriptor = classes.getValue();

            String[] split = name.split("\\.");

            String base = split[0];

            variables.put(base, split.length == 1 ? descriptor : null);

            String prev = base;
            for (int i = 1; i < split.length; i++) {
                String className = prev + "." + split[i];
                prev = className;
                components.put(className, i == split.length - 1 ? descriptor : null);
            }
        }

        for (Map.Entry<String, JSCodeGenTool.ExternClassDescriptor> entry : variables.entrySet()) {
            String var = entry.getKey();
            JSCodeGenTool.ExternClassDescriptor descriptor = entry.getValue();
            sb.append("/** @suppress {checkVars|duplicate} */\n");
            sb.append("var ").append(var).append(" = ").append(getExternClass(descriptor)).append(";\n");
            sb.append("\n");
        }

        for (Map.Entry<String, JSCodeGenTool.ExternClassDescriptor> entry : components.entrySet()) {
            String comp = entry.getKey();
            JSCodeGenTool.ExternClassDescriptor descriptor = entry.getValue();
            sb.append("/** @suppress {checkVars|duplicate} */\n");
            sb.append(comp).append(" = ").append(getExternClass(descriptor)).append(";\n");
            sb.append("\n");
        }

        return SourceFile.fromCode(this.filename + ".jssuperclass.externs.js", sb.toString(), StaticSourceFile.SourceKind.EXTERN);
    }

    /**
     * Set up closure compiler options for the main compilation pass.
     */
    protected CompilerOptions createClosureCompilerOptions() {
        final CompilerOptions closureCompOpts = new CompilerOptions();

        WebImageOptions.ClosurePrettyPrintLevel prettyPrintLevel = WebImageOptions.ClosurePrettyPrint.getValue(HostedOptionValues.singleton());

        CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(closureCompOpts);

        VariableRenamingPolicy variableRenamingPolicy = prettyPrintLevel.renameVariables() ? VariableRenamingPolicy.ALL : VariableRenamingPolicy.OFF;
        closureCompOpts.setRenamingPolicy(variableRenamingPolicy, PropertyRenamingPolicy.OFF);

        // Optionally enable pretty-printing
        closureCompOpts.setPrettyPrint(prettyPrintLevel.prettyPrint());

        // Targeting ECMASCRIPT_2020 because of our BigInteger and BigDecimal substitutions which
        // utilise JavaScript's BigInt (GR-48488). BigInt was introduced in ECMAScript 2020, and
        // the closure compiler can't transpile BigInt literals if the output language is lower.
        // Even if the literals were removed, the output would still contain the BigInt type and
        // would cause runtime errors on platforms that don't support it. Additionally, some part
        // of the codebase already emits JavaScript's globalThis variable, also introduced in
        // ECMAScript 2020.
        closureCompOpts.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2020);

        // Ensures that the compiler loads browser-specific externs
        // TODO set externs depending on compiler configuration
        closureCompOpts.setEnvironment(CompilerOptions.Environment.BROWSER);

        /*
         * Inlining functions has little impact on code size. Meanwhile, inlining of the function
         * `generateConstantProperties` results in significant (4x-8x) increase in stack usage and
         * even lead to the following error for some test cases:
         *
         * RangeError: Maximum call stack size exceeded
         *
         * In addition, the Web Image VM internals are protected from untrusted JavaScript code by
         * code patterns like the following:
         *
         * (function() { VM internals })()
         *
         * Inlining of such functions would defeat the purpose.
         *
         * See GR-44729
         */
        closureCompOpts.setInlineFunctions(CompilerOptions.Reach.NONE);

        /*
         * Maps available closure compiler error names to their DiagnosticType.
         */
        Map<String, DiagnosticType> allTypes = DiagnosticGroups.getRegisteredGroups().values().stream().flatMap(g -> g.getTypes().stream()).distinct().collect(Collectors.toMap(t -> t.key, t -> t));

        /*
         * Calling a static function on a class, will set 'this' to the class prototype:
         *
         * @formatter:off
         * <pre>
         * class foo {
         *     static f(){return this;}
         * }
         * </pre>
         * @formatter:on
         *
         * 'foo.f()' will return the 'foo' object, while 'let z = foo.f; z()' will return 'undefined'.
         *
         * The latter case appears in Web Image and causes the closure compiler to complain that 'this' will not be defined in the function.
         * This is fine for Web Image because it doesn't use `this` in static methods.
         */
        closureCompOpts.setWarningLevel(DiagnosticGroup.forType(allTypes.get("JSC_EXPECTED_THIS_TYPE")), CheckLevel.OFF);

        /*
         * We set the VM class on globalThis to prevent Closure from optimizing it away.
         */
        closureCompOpts.setWarningLevel(DiagnosticGroup.forType(allTypes.get("JSC_USED_GLOBAL_THIS")), CheckLevel.OFF);

        /*
         * The closure compiler has trouble properly deducing types in Web Image produced code.
         * Especially when it comes to the code for the class casts in java that is of the form:
         *
         * @formatter:off
         * <pre>
         * let l1 = (l0 === null || l0 instanceof _String);
         * if (l1) {
         *   var l2 = l0;
         *   if (l2 === null) {
         *     throw NPE
         *   } else {
         *     return l2.value___String;
         *   }
         * } else {
         *   throw ClassCastException
         * }
         * </pre>
         * @formatter:on
         *
         * Here, the closure compiler doesn't recognize 'l2' as being a string and reports 'JSC_INEXISTENT_PROPERTY'
         */
        closureCompOpts.setWarningLevel(DiagnosticGroup.forType(allTypes.get("JSC_INEXISTENT_PROPERTY")), CheckLevel.OFF);

        /*
         * The closure compiler seems to have a bug determining the type of an lvalue in one case.
         * When compiling jdk.nio.zipfs.ZipFileSystem.sync, we get the following snippet:
         *
         * @formatter:off
         * ...
         *   var l56 = l48 instanceof _ZipFileSystem$Entry;
         *   var l79 = l40===N;
         *   if(l56){
         *       lb8:{
         *           var l69 = l48;
         *           var l68 = (l69.type__ZipFileSystem$Entry);
         *           if(((l68 == 4))){
         *               if(l79){
         *                   var l82 = _ai8(l8,cg);
         *                   ;
         *                   l81=l82;
         *                   ;
         *               }else{
         *                   var l80 = l40;
         *                   l81=l80;
         *                   ;
         *               }
         *               var l93 = l81;
         *               try{
         *                   var l92 = hmir(p0,l69,l50,l5,l148,l93);
         *               }catch( exception_object_478 ) {
         *                   var l94 = exception_object_478;
         *                   if(l94 instanceof _IOException){
         *                       l72=l94;
         *                       l73=l39;
         *                       l74=l81;
         *                       ;
         *                       break lb8;
         *                   }else{
         *                       break lb7;
         *                   }
         *               }
         *               var l144 = l92;
         *               var l151 = _Long64.add___Long64_Long64_Long64(l39,l144);
         *               l77=l151;
         *               l78=l81;
         *               ;
         *           }else{
         *               l69.locoff__ZipFileSystem$Entry=l148; // <-- ERROR
         * ...
         * @formatter:on
         *
         * The closure compiler asserts that l69 has the type jdk.nio.zipfs.ZipFileSystem.IndexNode which is a superclass of Entry and
         * therefore does not have the field locoff. This leads to the report of JSC_ILLEGAL_PROPERTY_CREATION.
         */
        closureCompOpts.setWarningLevel(DiagnosticGroup.forType(allTypes.get("JSC_ILLEGAL_PROPERTY_CREATION")), CheckLevel.OFF);
        if (WebImageOptions.StrictWarnings.getValue()) {
            closureCompOpts.addWarningsGuard(new StrictWarningsGuard());
        }

        if (WebImageProviders.isLabelInjectionEnabled()) {
            /*
             * We use property access to inject labels around method definitions.
             *
             * See {@link Labeler#injectMethodLabel}.
             */
            closureCompOpts.setWarningLevel(DiagnosticGroup.forType(allTypes.get("JSC_ILLEGAL_PROPERTY_ACCESS")), CheckLevel.OFF);
        }

        return closureCompOpts;
    }
}
