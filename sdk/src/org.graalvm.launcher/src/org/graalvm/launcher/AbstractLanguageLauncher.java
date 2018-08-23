/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;

public abstract class AbstractLanguageLauncher extends Launcher {

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
            } catch (Throwable t) {
                throw abort(t);
            }
        } catch (AbortException e) {
            handleAbortException(e);
        }
    }

    protected static final boolean IS_LIBPOLYGLOT = Boolean.getBoolean("graalvm.libpolyglot");

    final void launch(List<String> args, Map<String, String> defaultOptions, boolean doNativeSetup) {
        Map<String, String> polyglotOptions = defaultOptions;
        if (polyglotOptions == null) {
            polyglotOptions = new HashMap<>();
        }

        if (isAOT() && doNativeSetup) {
            System.setProperty("org.graalvm.launcher.languageId", getLanguageId());
        }

        List<String> unrecognizedArgs = preprocessArguments(args, polyglotOptions);

        if (isAOT() && doNativeSetup && !IS_LIBPOLYGLOT) {
            assert nativeAccess != null;
            nativeAccess.maybeExec(args, false, polyglotOptions, getDefaultVMType());
        }

        for (String arg : unrecognizedArgs) {
            if (!parsePolyglotOption(getLanguageId(), polyglotOptions, arg)) {
                throw abortUnrecognizedArgument(arg);
            }
        }

        if (runPolyglotAction()) {
            return;
        }

        validateArguments(polyglotOptions);

        Context.Builder builder;
        if (isPolyglot()) {
            builder = Context.newBuilder().options(polyglotOptions);
        } else {
            builder = Context.newBuilder(getDefaultLanguages()).options(polyglotOptions);
        }
        builder.allowAllAccess(true);

        launch(builder);
    }

    /**
     * This is called to abort execution when an argument can neither be recognized by the launcher
     * or as an option for the polyglot engine.
     * 
     * @param argument the argument that was not recognized.
     */
    protected AbortException abortUnrecognizedArgument(String argument) {
        throw abortInvalidArgument(argument, "Unrecognized argument: '" + argument + "'. Use --help for usage instructions.");
    }

    /**
     * Process command line arguments by either saving the necessary state or adding it to the
     * {@code polyglotOptions}. Any unrecognized arguments should be accumulated and returned as a
     * list.
     *
     * Arguments that are translated to polyglot options should be removed from the list. Other
     * arguments should not be removed.
     * 
     * @param arguments the command line arguments that were passed to the launcher.
     * @param polyglotOptions a map where polyglot options can be set. These will be uses when
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
        printVersion(Engine.create());
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
            languageImplementationName = "Graal " + languageName;
        }
        String engineImplementationName = engine.getImplementationName();
        if (isAOT()) {
            engineImplementationName += " Native";
        }
        System.out.println(String.format("%s %s (%s %s)", languageImplementationName, language.getVersion(), engineImplementationName, engine.getVersion()));
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
     * polyglot. E.g. Ruby needs llvm as well.
     *
     * @return an array of required language ids
     */
    protected String[] getDefaultLanguages() {
        return new String[]{getLanguageId()};
    }
}
