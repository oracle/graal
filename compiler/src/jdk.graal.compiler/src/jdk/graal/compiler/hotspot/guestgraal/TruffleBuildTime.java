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
package jdk.graal.compiler.hotspot.guestgraal;

import com.oracle.truffle.compiler.hotspot.libgraal.TruffleToLibGraal.Id;
import jdk.graal.compiler.debug.GraalError;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;

import static java.lang.invoke.MethodType.methodType;


public class TruffleBuildTime {

    private static final MethodHandles.Lookup MHL = MethodHandles.lookup();


    /**
     * Gets method handles to call Graal and JVMCI methods.
     *
     * @return a named set of handles
     */
    public static Map<String, MethodHandle> getRuntimeHandles() {
        try {
            return Map.ofEntries(
                    createMethodHandle(Id.InitializeIsolate, void.class),
                    createMethodHandle(Id.RegisterRuntime, void.class),
                    createMethodHandle(Id.InitializeRuntime, void.class),
                    createMethodHandle(Id.NewCompiler, void.class),
                    createMethodHandle(Id.InitializeCompiler, void.class),
                    createMethodHandle(Id.GetCompilerConfigurationFactoryName, void.class),
                    createMethodHandle(Id.DoCompile, void.class),
                    createMethodHandle(Id.Shutdown, void.class),
                    createMethodHandle(Id.InstallTruffleCallBoundaryMethod, void.class),
                    createMethodHandle(Id.InstallTruffleReservedOopMethod, void.class),
                    createMethodHandle(Id.PendingTransferToInterpreterOffset, void.class),
                    createMethodHandle(Id.GetSuppliedString, void.class),
                    createMethodHandle(Id.GetNodeCount, void.class),
                    createMethodHandle(Id.GetNodeTypes, void.class),
                    createMethodHandle(Id.GetTargetCodeSize, void.class),
                    createMethodHandle(Id.GetTotalFrameSize, void.class),
                    createMethodHandle(Id.GetExceptionHandlersCount, void.class),
                    createMethodHandle(Id.GetInfopointsCount, void.class),
                    createMethodHandle(Id.GetInfopoints, void.class),
                    createMethodHandle(Id.ListCompilerOptions, void.class),
                    createMethodHandle(Id.CompilerOptionExists, void.class),
                    createMethodHandle(Id.ValidateCompilerOption, void.class),
                    createMethodHandle(Id.GetMarksCount, void.class),
                    createMethodHandle(Id.GetDataPatchesCount, void.class),
                    createMethodHandle(Id.PurgePartialEvaluationCaches, void.class),
                    createMethodHandle(Id.GetCompilerVersion, void.class)
            );
        } catch (Throwable e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private static Map.Entry<String, MethodHandle> createMethodHandle(Id id, Class<?> returnType, Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle handle = MHL.findStatic(TruffleRunTime.class, toMethodName(id), methodType(returnType, parameterTypes));
        return Map.entry(id.name(), handle);
    }

    private static String toMethodName(Id id) {
        String name = id.name();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
