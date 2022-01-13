/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.launcher;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;

public abstract class AbstractLanguageLauncher extends LanguageLauncherBase {

    private static final Constructor<AbstractLanguageLauncher> LAUNCHER_CTOR;
    /**
     * Set to true if the launcher has been started via the {@code runLauncher} JNI entry point.
     */
    private boolean jniLaunch;
    /**
     * Native argument count, set if the launcher has been started via {@code runLauncher}.
     */
    private int nativeArgc;
    /**
     * Pointer to the native argument value array, set if the launcher has been started via
     * {@code runLauncher}.
     */
    private long nativeArgv;
    /**
     * Indicates if this launcher instance is the result of a relaunch, where actual VM arguments
     * have been identified and set previously.
     */
    private boolean relaunch;

    static {
        LAUNCHER_CTOR = getLauncherCtor();
    }

    /**
     * Looks up the launcher constructor based on the launcher class passed in via the
     * org.graalvm.launcher.class system property.
     *
     * @return launcher constructor, if found.
     */
    @SuppressWarnings("unchecked")
    private static Constructor<AbstractLanguageLauncher> getLauncherCtor() {
        String launcherClassName = System.getProperty("org.graalvm.launcher.class");
        Constructor<AbstractLanguageLauncher> launcherCtor = null;
        if (launcherClassName != null) {
            try {
                Class<AbstractLanguageLauncher> launcherClass = (Class<AbstractLanguageLauncher>) Class.forName(launcherClassName);
                if (!AbstractLanguageLauncher.class.isAssignableFrom(launcherClass)) {
                    throw new Exception("Launcher does not implement " + AbstractLanguageLauncher.class.getName());
                }
                launcherCtor = launcherClass.getConstructor();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return launcherCtor;
    }

    /**
     * This starts the launcher. it should be called from the main method:
     *
     * <pre>
     * public static void main(String[] args) {
     *     new MyLauncher().launch(args);
     * }
     * </pre>
     *
     * @param args the command line arguments.
     */
    protected final void launch(String[] args) {
        try {
            try {
                launch(new ArrayList<>(Arrays.asList(args)), null, true);
            } catch (AbortException e) {
                throw e;
            } catch (PolyglotException e) {
                handlePolyglotException(e);
            } catch (RelaunchException e) {
                throw e;
            } catch (Throwable t) {
                throw abort(t);
            }
        } catch (AbortException e) {
            handleAbortException(e);
        }
    }

    /**
     * Entry point for invoking the launcher via JNI. Relies on a launcher constructor to be set via
     * the org.graalvm.launcher.class system property.
     *
     * @param args the command line arguments as an encoding-agnostic byte array
     * @param argc the number of native command line arguments
     * @param argv pointer to argv
     * @param relaunch indicates if this is a relaunch with previously identified vm arguments
     * @throws Exception if no launcher constructor has been set.
     */
    public static void runLauncher(byte[][] args, int argc, long argv, boolean relaunch) throws Exception {
        if (isAOT()) {
            // enable signal handling for the launcher
            RuntimeOptions.set("EnableSignalHandling", true);
        }

        if (LAUNCHER_CTOR == null) {
            throw new Exception("Launcher constructor has not been set.");
        }

        AbstractLanguageLauncher launcher = LAUNCHER_CTOR.newInstance();
        launcher.jniLaunch = true;
        launcher.nativeArgc = argc;
        launcher.nativeArgv = argv;
        launcher.relaunch = relaunch;

        String[] arguments = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            arguments[i] = new String(args[i]);
        }

        launcher.launch(arguments);

        // shut down the launcher - do this in favor of calling the JNI DestroyJavaVM API, which
        // might hang waiting for daemon threads on SVM (GR-35345)
        System.exit(0);
    }

    /**
     * Check if the arguments parsing heuristic of the native launcher correctly identified the set
     * of VM arguments. Throw a {@code RelaunchException} if it hasn't. The exception will be picked
     * up by the native launcher, which will read the {@code vmArgs}, put them in environment
     * variables and restart the VM with the correct set of VM arguments.
     *
     * @param originalArgs original set of arguments (except for argv[0], the program name)
     * @param unrecognizedArgs set of arguments returned by {@code preprocessArguments()}
     */
    protected final void validateVmArguments(List<String> originalArgs, List<String> unrecognizedArgs) {
        if (relaunch) {
            // vm arguments have been explicitly set, bypassing the heuristic
            return;
        }

        List<String> heuristicVmArgs = new ArrayList<>();
        List<String> actualVmArgs = new ArrayList<>();

        for (String arg : originalArgs) {
            if (arg.startsWith("--vm.")) {
                heuristicVmArgs.add(arg);
            }
        }
        for (String arg : unrecognizedArgs) {
            if (arg.startsWith("--vm.")) {
                actualVmArgs.add(arg);
            }
        }

        if (!heuristicVmArgs.equals(actualVmArgs)) {
            throw new RelaunchException(actualVmArgs);
        }

        // all argument match, we're good
        return;
    }

    /**
     * Used by the native launcher to detect that a relaunch of the VM is needed.
     */
    protected static final class RelaunchException extends RuntimeException {
        private static final long serialVersionUID = -4014071914987464223L;

        /**
         * Actual VM arguments, set if validateVmArguments fails s.t. the native launcher can obtain
         * the actual VM arguments for a relaunch.
         */
        @SuppressWarnings("unused") private String[] vmArgs;

        RelaunchException(List<String> actualVmArgs) {
            vmArgs = actualVmArgs.toArray(new String[actualVmArgs.size()]);
        }

        @Override
        public String getMessage() {
            return "Misidentified VM arguments, relaunch required";
        }
    }

    /**
     * The native argument count as passed to the main method of the native launcher.
     *
     * @return native argument count, including the program name
     */
    protected int getNativeArgc() {
        return nativeArgc;
    }

    /**
     * The native argument values as passed to the main method of the native launcher.
     *
     * @return pointer to the native argument values, including the program name
     */
    protected long getNativeArgv() {
        return nativeArgv;
    }

    protected static final boolean IS_LIBPOLYGLOT = Boolean.getBoolean("graalvm.libpolyglot");

    final void launch(List<String> args, Map<String, String> defaultOptions, boolean doNativeSetup) {
        List<String> originalArgs = Collections.unmodifiableList(new ArrayList<>(args));

        Map<String, String> polyglotOptions = defaultOptions;
        if (polyglotOptions == null) {
            polyglotOptions = new HashMap<>();
        }

        if (isAOT() && doNativeSetup) {
            System.setProperty("org.graalvm.launcher.languageId", getLanguageId());
        }

        List<String> unrecognizedArgs = preprocessArguments(args, polyglotOptions);

        if (jniLaunch) {
            validateVmArguments(originalArgs, unrecognizedArgs);
        }

        if (isAOT() && doNativeSetup && !IS_LIBPOLYGLOT) {
            assert nativeAccess != null;
            maybeExec(originalArgs, unrecognizedArgs, false, getDefaultVMType(), jniLaunch);
        }

        parseUnrecognizedOptions(getLanguageId(), polyglotOptions, unrecognizedArgs);

        if (runLauncherAction()) {
            return;
        }

        validateArguments(polyglotOptions);
        argumentsProcessingDone();

        Context.Builder builder;
        if (isPolyglot()) {
            builder = Context.newBuilder().options(polyglotOptions);
        } else {
            builder = Context.newBuilder(getDefaultLanguages()).options(polyglotOptions);
        }

        builder.allowAllAccess(true);
        setupContextBuilder(builder);

        launch(builder);
    }

    /**
     * Process command line arguments by either saving the necessary state or adding it to the
     * {@code polyglotOptions}. Any unrecognized arguments should be accumulated and returned as a
     * list. VM (--jvm/--native/--polyglot/--vm.*) and polyglot options (--language.option or
     * --option) should be returned as unrecognized arguments to be automatically parsed and
     * validated by {@link Launcher#parsePolyglotOption(String, Map, boolean, String)}.
     *
     * The {@code arguments} should not be modified, but doing so also has no effect.
     *
     * {@code polyglotOptions.put()} can be used to set launcher-specific default values when they
     * do not match the OptionKey's default.
     *
     * The {@code preprocessArguments} implementations can use {@link Engine} to inspect the the
     * installed {@link Engine#getLanguages() guest languages} and {@link Engine#getInstruments()
     * instruments}. But creating a {@link Context} or inspecting {@link Engine#getOptions() engine
     * options} is forbidden.
     *
     * @param arguments the command line arguments that were passed to the launcher.
     * @param polyglotOptions a map where polyglot options can be set. These will be used when
     *            creating the {@link org.graalvm.polyglot.Engine Engine}.
     * @return the list of arguments that were not recognized.
     */
    protected abstract List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions);

    /**
     * Validates arguments after all arguments have been parsed.
     *
     * @param polyglotOptions the options that will be used to create engine.
     */
    protected void validateArguments(Map<String, String> polyglotOptions) {
        // nothing to validate by default
    }

    /**
     * Launch the scripts as required by the arguments received during the previous call to
     * {@link #preprocessArguments(List, Map)}.
     *
     * @param contextBuilder a {@linkplain Context.Builder context builder} configured with the
     *            proper language and polyglot options.
     */
    protected abstract void launch(Context.Builder contextBuilder);

    /**
     * Returns the {@linkplain Language#getId() language id} of the language launched by this
     * launcher.
     */
    protected abstract String getLanguageId();

    @Override
    protected void printVersion() {
        printVersion(getTempEngine());
    }

    protected void printVersion(Engine engine) {
        String languageId = getLanguageId();
        Language language = engine.getLanguages().get(languageId);
        if (language == null) {
            throw abort(String.format("Unknown language: '%s'!", languageId));
        }
        String languageImplementationName = language.getImplementationName();
        if (languageImplementationName == null || languageImplementationName.length() == 0) {
            String languageName = language.getName();
            if (languageName == null || languageName.length() == 0) {
                languageName = languageId;
            }
            languageImplementationName = languageName;
        }
        String engineImplementationName = engine.getImplementationName();
        if (isAOT()) {
            engineImplementationName += " Native";
        } else {
            engineImplementationName += " JVM";
        }
        String languageVersion = language.getVersion();
        if (languageVersion.equals(engine.getVersion())) {
            languageVersion = "";
        } else {
            languageVersion += " ";
        }
        System.out.println(String.format("%s %s(%s %s)", languageImplementationName, languageVersion, engineImplementationName, engine.getVersion()));
    }

    protected void runVersionAction(VersionAction action, Engine engine) {
        switch (action) {
            case PrintAndContinue:
                printVersion(engine);
                break;
            case PrintAndExit:
                printVersion(engine);
                throw exit();
        }
    }

    /**
     * The return value specifies what languages should be available by default when not using
     * --polyglot. Note that TruffleLanguage.Registration#dependentLanguages() should be preferred
     * in most cases.
     *
     * @return an array of required language ids
     */
    protected String[] getDefaultLanguages() {
        return new String[]{getLanguageId()};
    }
}
