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

package com.oracle.svm.webimage;

import java.lang.reflect.Method;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.jdk.SystemInOutErrSupport;
import com.oracle.svm.core.option.RuntimeOptionParser;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.webimage.JSExceptionSupport.ExceptionToNonLocalizedString;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionIntrinsics;
import com.oracle.svm.webimage.substitute.system.Target_java_lang_Throwable_Web;

import jdk.graal.compiler.debug.GraalError;

/**
 * Clone of {@link JavaMainSupport} but without the assumption that entry point is called from C.
 * <p>
 * The code registers and uses WebImageJavaMainSupport where SVM would use {@link JavaMainSupport}.
 */
public abstract class WebImageJavaMainSupport extends JavaMainSupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    protected WebImageJavaMainSupport(Method javaMainMethod) throws IllegalAccessException {
        super(javaMainMethod);
    }

    /**
     * Calls the real entry method to the compiled program.
     * <p>
     * Also sets up the runtime.
     */
    public static int run(String[] args) {
        int exitCode = doRun(args, JavaMainWrapper::invokeMain);
        setExitCode(exitCode);
        return exitCode;
    }

    /**
     * The same as {@link #run(String[])} but does not call any real user code and only does
     * initialization.
     * <p>
     * This is the main entry point for non-executable images (e.g. libraries). After the main entry
     * point is executed, the runtime state has been set up and exported methods can be called.
     */
    public static int initializeLibrary(String[] args) {
        int exitCode = doRun(args, Runner.NOP);
        setExitCode(exitCode);
        return exitCode;
    }

    protected static int doRun(String[] args, Runner runner) {
        try {
            startMainThread();
            String[] parsedArgs = RuntimeOptionParser.parseAndConsumeAllOptions(args, false);

            if (ImageSingletons.contains(JavaMainSupport.class)) {
                ImageSingletons.lookup(JavaMainSupport.class).mainArgs = parsedArgs;
            }

            runner.run(parsedArgs);
            return 0;
        } catch (ExitError e) {
            // This is a special case used to mimic System.exit
            return e.exitCode;
        } catch (Throwable throwable) {
            handleThrowable(SubstrateUtil.cast(throwable, Target_java_lang_Throwable_Web.class));
            return 1;
        } finally {
            SystemInOutErrSupport inOut = ImageSingletons.lookup(SystemInOutErrSupport.class);
            inOut.err().flush();
            inOut.out().flush();
        }
    }

    @FunctionalInterface
    protected interface Runner {

        Runner NOP = ignored -> {
        };

        void run(String[] args) throws Throwable;
    }

    private static void startMainThread() {
        // Creates the main thread.
        PlatformThreads.singleton().assignMainThread();
        Isolates.assignStartTime();
    }

    protected static void handleThrowable(Target_java_lang_Throwable_Web t) {
        System.err.print("Exception in thread \"" + Thread.currentThread().getName() + "\" ");
        /*
         * Print exception without localization to avoid pulling in too many types unconditionally
         */
        JSExceptionSupport.printStackTrace(t, System.err::println, new ExceptionToNonLocalizedString());
    }

    /**
     * Sets the exit code for the VM.
     */
    public static void setExitCode(int status) {
        JSFunctionIntrinsics.setExitCode(status);
    }

    /**
     * Overriding this method and {@link #getInputArguments()} is for safety to avoid pulling
     * unnecessary code into the universe.
     */
    @Override
    public String getJavaCommand() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public List<String> getInputArguments() {
        throw GraalError.unimplementedOverride();
    }
}
