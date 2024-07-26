/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.guestgraal.truffle;

import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.truffle.hotspot.TruffleCallBoundaryInstrumentationFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.NoSuchElementException;

import static java.lang.invoke.MethodType.methodType;

public class BuildTime {

    public static void configureGraalForLibGraal() {
        GraalServices.load(TruffleCallBoundaryInstrumentationFactory.class);
        GraalServices.load(TruffleHostEnvironment.Lookup.class);
        TruffleHostEnvironment.overrideLookup(new LibGraalTruffleHostEnvironmentLookup());
    }

    private static final MethodHandles.Lookup MHL = MethodHandles.lookup();

    /**
     * Gets method handles to call Graal and JVMCI methods.
     *
     * @return a named set of handles
     */
    public static Map<String, MethodHandle> getRuntimeHandles(Map<String, MethodHandle> upCallHandles) {
        try {
            initializeUpCalls(upCallHandles);
            return Map.ofEntries(
                            createMethodHandle(Id.InitializeIsolate, void.class),
                            createMethodHandle(Id.RegisterRuntime, boolean.class, long.class),
                            createMethodHandle(Id.InitializeRuntime, Object.class, Object.class, long.class),
                            createMethodHandle(Id.NewCompiler, Object.class, Object.class),
                            createMethodHandle(Id.InitializeCompiler, void.class, Object.class, Object.class, boolean.class),
                            createMethodHandle(Id.GetCompilerConfigurationFactoryName, String.class),
                            createMethodHandle(Id.DoCompile, void.class, Object.class, Object.class, Object.class, Object.class),
                            createMethodHandle(Id.Shutdown, void.class, Object.class),
                            createMethodHandle(Id.InstallTruffleCallBoundaryMethod, void.class, Object.class, long.class),
                            createMethodHandle(Id.InstallTruffleReservedOopMethod, void.class, Object.class, long.class),
                            createMethodHandle(Id.PendingTransferToInterpreterOffset, int.class, Object.class, Object.class),
                            createMethodHandle(Id.GetSuppliedString, String.class, Object.class),
                            createMethodHandle(Id.GetNodeCount, int.class, Object.class),
                            createMethodHandle(Id.GetNodeTypes, String[].class, Object.class, boolean.class),
                            createMethodHandle(Id.GetTargetCodeSize, int.class, Object.class),
                            createMethodHandle(Id.GetTotalFrameSize, int.class, Object.class),
                            createMethodHandle(Id.GetExceptionHandlersCount, int.class, Object.class),
                            createMethodHandle(Id.GetInfopointsCount, int.class, Object.class),
                            createMethodHandle(Id.GetInfopoints, String[].class, Object.class),
                            createMethodHandle(Id.ListCompilerOptions, Object[].class),
                            createMethodHandle(Id.CompilerOptionExists, boolean.class, String.class),
                            createMethodHandle(Id.ValidateCompilerOption, String.class, String.class, String.class),
                            createMethodHandle(Id.GetMarksCount, int.class, Object.class),
                            createMethodHandle(Id.GetDataPatchesCount, int.class, Object.class),
                            createMethodHandle(Id.PurgePartialEvaluationCaches, void.class, Object.class),
                            createMethodHandle(Id.GetCompilerVersion, String.class));
        } catch (Throwable e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    static MethodHandle getOrFail(Map<String, MethodHandle> handles, TruffleFromLibGraal.Id id) {
        MethodHandle handle = handles.get(id.name());
        if (handle != null) {
            return handle;
        } else {
            throw new NoSuchElementException(id.name());
        }
    }

    private static void initializeUpCalls(Map<String, MethodHandle> upCallHandles) {
        RunTime.initialize(upCallHandles);
        TruffleLibGraalShutdownHook.ShutdownHook.initialize(upCallHandles);
        HSTruffleCompilerRuntime.initialize(upCallHandles);
        HSTruffleCompilable.initialize(upCallHandles);
        HSConsumer.initialize(upCallHandles);
        HSTruffleCompilationTask.initialize(upCallHandles);
        HSTruffleCompilerListener.initialize(upCallHandles);
        HSTruffleSourceLanguagePosition.initialize(upCallHandles);
    }

    private static Map.Entry<String, MethodHandle> createMethodHandle(Id id, Class<?> returnType, Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle handle = MHL.findStatic(RunTime.class, toMethodName(id), methodType(returnType, parameterTypes));
        return Map.entry(id.name(), handle);
    }

    private static String toMethodName(Id id) {
        String name = id.name();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
