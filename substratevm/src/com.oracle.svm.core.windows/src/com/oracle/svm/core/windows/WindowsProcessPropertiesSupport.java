/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.impl.ProcessPropertiesSupport;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.BaseProcessPropertiesSupport;
import com.oracle.svm.core.c.locale.LocaleSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.LibLoaderAPI;
import com.oracle.svm.core.windows.headers.Process;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;

import jdk.graal.compiler.word.Word;

@AutomaticallyRegisteredImageSingleton(ProcessPropertiesSupport.class)
public class WindowsProcessPropertiesSupport extends BaseProcessPropertiesSupport {

    @Override
    public String getExecutableName() {
        return getModulePath(LibLoaderAPI.GetModuleHandleA(Word.nullPointer()));
    }

    @Override
    public void exec(Path executable, String[] args) {
        exec(executable, args, null);
    }

    @Override
    public void exec(Path executable, String[] args, Map<String, String> env) {
        if (!Files.isExecutable(executable)) {
            throw new RuntimeException("Path " + executable + " does not point to executable file");
        }
        List<String> cmd = new ArrayList<>(args.length);
        cmd.add(executable.toString());
        cmd.addAll(Arrays.asList(args).subList(1, args.length));
        java.lang.Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectInput(ProcessBuilder.Redirect.INHERIT).redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT);
            if (env != null) {
                // set a new environment
                pb.environment().clear();
                pb.environment().putAll(env);
            }
            process = pb.start();
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
        while (process.isAlive()) {
            try {
                System.exit(process.waitFor());
            } catch (InterruptedException e) {
                // continue
            }
        }
        System.exit(process.exitValue());
    }

    @Override
    public long getProcessID() {
        return Process.NoTransitions.GetCurrentProcessId();
    }

    @Override
    public long getProcessID(java.lang.Process process) {
        return WindowsUtils.getpid(process);
    }

    @Override
    public String getObjectFile(String symbol) {
        try (CTypeConversion.CCharPointerHolder symbolHolder = CTypeConversion.toCString(symbol)) {
            WinBase.HMODULE builtinHandle = LibLoaderAPI.GetModuleHandleA(Word.nullPointer());
            PointerBase symbolAddress = LibLoaderAPI.GetProcAddress(builtinHandle, symbolHolder.get());
            if (symbolAddress.isNonNull()) {
                return getObjectFile(symbolAddress);
            }
        }
        return null;
    }

    @Override
    public String getObjectFile(PointerBase symbolAddress) {
        WinBase.HMODULEPointer module = UnsafeStackValue.get(WinBase.HMODULEPointer.class);
        if (LibLoaderAPI.GetModuleHandleExA(LibLoaderAPI.GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS() | LibLoaderAPI.GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT(),
                        (CCharPointer) symbolAddress, module) == 0) {
            return null;
        }
        return getModulePath(module.read());
    }

    private static String getModulePath(WinBase.HMODULE module) {
        WCharPointer path = UnsafeStackValue.get(WinBase.MAX_PATH, WCharPointer.class);
        int length = LibLoaderAPI.GetModuleFileNameW(module, path, WinBase.MAX_PATH);
        if (length == 0 || length == WinBase.MAX_PATH) {
            return null;
        }
        return WindowsSystemPropertiesSupport.toJavaString(path, length);
    }

    /** This method is unsafe and should not be used, see {@link LocaleSupport}. */
    @Override
    @SuppressWarnings("deprecation")
    public String setLocale(String category, String locale) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean destroy(long processID) {
        return destroyForcibly(processID);
    }

    @Override
    public boolean destroyForcibly(long processID) {
        HANDLE handle = Process.NoTransitions.OpenProcess(Process.PROCESS_TERMINATE(), 0, (int) processID);
        if (handle.isNull()) {
            return false;
        }
        boolean result = Process.NoTransitions.TerminateProcess(handle, 1) != 0;
        WinBase.CloseHandle(handle);
        return result;
    }

    @Override
    public boolean isAlive(long processID) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int waitForProcessExit(long processID) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }
}
