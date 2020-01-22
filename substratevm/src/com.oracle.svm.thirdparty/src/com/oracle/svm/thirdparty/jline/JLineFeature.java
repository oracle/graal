/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.thirdparty.jline;

// Checkstyle: allow reflection

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticFeature
final class JLineFeature implements Feature {

    /** List of the classes that access the JNI library. */
    private static final List<String> JNI_CLASS_NAMES = Arrays.asList(
                    "org.fusesource.jansi.internal.CLibrary",
                    "org.fusesource.jansi.internal.CLibrary$WinSize",
                    "org.fusesource.jansi.internal.CLibrary$Termios",
                    "org.fusesource.jansi.internal.Kernel32",
                    "org.fusesource.jansi.internal.Kernel32$SMALL_RECT",
                    "org.fusesource.jansi.internal.Kernel32$COORD",
                    "org.fusesource.jansi.internal.Kernel32$CONSOLE_SCREEN_BUFFER_INFO",
                    "org.fusesource.jansi.internal.Kernel32$CHAR_INFO",
                    "org.fusesource.jansi.internal.Kernel32$KEY_EVENT_RECORD",
                    "org.fusesource.jansi.internal.Kernel32$MOUSE_EVENT_RECORD",
                    "org.fusesource.jansi.internal.Kernel32$WINDOW_BUFFER_SIZE_RECORD",
                    "org.fusesource.jansi.internal.Kernel32$FOCUS_EVENT_RECORD",
                    "org.fusesource.jansi.internal.Kernel32$MENU_EVENT_RECORD",
                    "org.fusesource.jansi.internal.Kernel32$INPUT_RECORD");

    /**
     * Other classes that need to be initialized at run time because they reference the JNI classes
     * and/or have static state that depends on run time state.
     */
    private static final List<String> RUNTIME_INIT_CLASS_NAMES = Arrays.asList(
                    "org.fusesource.jansi.AnsiConsole",
                    "org.fusesource.jansi.WindowsAnsiOutputStream",
                    "org.fusesource.jansi.WindowsAnsiProcessor");

    Class<?> terminalFactoryClass;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        terminalFactoryClass = access.findClassByName("jline.TerminalFactory");
        /* Feature is only active when JLine is on the classpath. */
        return terminalFactoryClass != null;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            Stream.concat(JNI_CLASS_NAMES.stream(), RUNTIME_INIT_CLASS_NAMES.stream())
                            .map(access::findClassByName)
                            .filter(Objects::nonNull)
                            .forEach(RuntimeClassInitialization::initializeAtRunTime);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Object[] createMethods = Arrays.stream(terminalFactoryClass.getDeclaredMethods())
                        .filter(m -> Modifier.isStatic(m.getModifiers()) && m.getName().equals("create"))
                        .toArray();
        access.registerReachabilityHandler(JLineFeature::registerTerminalConstructor, createMethods);

        if (Platform.includedIn(Platform.WINDOWS.class)) {
            /*
             * Each listed class has a native method named "init" that initializes all declared
             * fields of the class using JNI. So when the "init" method gets reachable (which means
             * the class initializer got reachable), we need to register all fields for JNI access.
             */
            JNI_CLASS_NAMES.stream()
                            .map(access::findClassByName)
                            .filter(Objects::nonNull)
                            .map(jniClass -> ReflectionUtil.lookupMethod(jniClass, "init"))
                            .forEach(initMethod -> access.registerReachabilityHandler(a -> registerJNIFields(initMethod), initMethod));
        }
    }

    private static void registerTerminalConstructor(DuringAnalysisAccess access) {
        /*
         * TerminalFactory.create methods instantiate the actual Terminal implementation class via
         * reflection. We cannot automatically constant fold which class is going to be used, so we
         * register the default for each platform. If the user manually overrides the default
         * implementation class, they also need to provide a reflection configuration for that
         * class.
         */
        Class<?> terminalClass = access.findClassByName(Platform.includedIn(Platform.WINDOWS.class) ? "jline.AnsiWindowsTerminal" : "jline.UnixTerminal");
        if (terminalClass != null) {
            RuntimeReflection.register(terminalClass);
            RuntimeReflection.register(terminalClass.getDeclaredConstructors());
        }
    }

    private AtomicBoolean resourceRegistered = new AtomicBoolean();

    private void registerJNIFields(Method initMethod) {
        Class<?> jniClass = initMethod.getDeclaringClass();
        JNIRuntimeAccess.register(jniClass.getDeclaredFields());

        if (!resourceRegistered.getAndSet(true)) {
            /* The native library that is included as a resource in the .jar file. */
            String resource = "META-INF/native/windows64/jansi.dll";
            Resources.registerResource(resource, jniClass.getClassLoader().getResourceAsStream(resource));
        }
    }
}
