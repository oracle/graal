/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import static com.oracle.svm.core.posix.headers.Signal.SignalEnum.SIGKILL;
import static com.oracle.svm.core.posix.headers.Signal.SignalEnum.SIGTERM;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.BaseProcessPropertiesSupport;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.core.posix.headers.Dlfcn;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Stdlib;
import com.oracle.svm.core.posix.headers.Unistd;

public abstract class PosixProcessPropertiesSupport extends BaseProcessPropertiesSupport {

    @Override
    public long getProcessID() {
        return PosixUtils.getpid();
    }

    @Override
    public long getProcessID(Process process) {
        return PosixUtils.getpid(process);
    }

    @Override
    public boolean destroy(long processID) {
        return Signal.kill(Math.toIntExact(processID), SIGTERM.getCValue()) == 0;
    }

    @Override
    public boolean destroyForcibly(long processID) {
        return Signal.kill(Math.toIntExact(processID), SIGKILL.getCValue()) == 0;
    }

    @Override
    public boolean isAlive(long processID) {
        return Signal.kill(Math.toIntExact(processID), 0) == 0;
    }

    @Override
    public int waitForProcessExit(long processID) {
        return PosixUtils.waitForProcessExit(Math.toIntExact(processID));
    }

    @Override
    public String getObjectFile(String symbol) {
        try (CTypeConversion.CCharPointerHolder symbolHolder = CTypeConversion.toCString(symbol)) {
            PointerBase symbolAddress = Dlfcn.dlsym(Dlfcn.RTLD_DEFAULT(), symbolHolder.get());
            if (symbolAddress.isNull()) {
                return null;
            }
            return getObjectFile(symbolAddress);
        }
    }

    @Override
    public String getObjectFile(PointerBase symbolAddress) {
        Dlfcn.Dl_info info = UnsafeStackValue.get(Dlfcn.Dl_info.class);
        if (Dlfcn.dladdr(symbolAddress, info) == 0) {
            return null;
        }
        CCharPointer realpath = Stdlib.realpath(info.dli_fname(), WordFactory.nullPointer());
        if (realpath.isNull()) {
            return null;
        }
        try {
            return CTypeConversion.toJavaString(realpath);
        } finally {
            UntrackedNullableNativeMemory.free(realpath);
        }
    }

    @Override
    public String setLocale(String category, String locale) {
        return PosixUtils.setLocale(category, locale);
    }

    @Override
    public void exec(Path executable, String[] args) {
        if (!Files.isExecutable(executable)) {
            throw new RuntimeException("Path " + executable + " does not point to executable file");
        }

        try (CTypeConversion.CCharPointerHolder pathHolder = CTypeConversion.toCString(executable.toString());
                        CTypeConversion.CCharPointerPointerHolder argvHolder = CTypeConversion.toCStrings(args)) {
            if (Unistd.execv(pathHolder.get(), argvHolder.get()) != 0) {
                String msg = PosixUtils.lastErrorString("Executing " + executable + " with arguments " + String.join(" ", args) + " failed");
                throw new RuntimeException(msg);
            }
        }
    }

    @Override
    public void exec(Path executable, String[] args, Map<String, String> env) {
        if (!Files.isExecutable(executable)) {
            throw new RuntimeException("Path " + executable + " does not point to executable file");
        }

        String[] envArray = new String[env.size()];
        int i = 0;
        for (Entry<String, String> e : env.entrySet()) {
            envArray[i++] = e.getKey() + "=" + e.getValue();
        }

        try (CTypeConversion.CCharPointerHolder pathHolder = CTypeConversion.toCString(executable.toString());
                        CTypeConversion.CCharPointerPointerHolder argvHolder = CTypeConversion.toCStrings(args);
                        CTypeConversion.CCharPointerPointerHolder envpHolder = CTypeConversion.toCStrings(envArray)) {
            if (Unistd.execve(pathHolder.get(), argvHolder.get(), envpHolder.get()) != 0) {
                String envString = env.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(" "));
                String msg = PosixUtils.lastErrorString("Executing " + executable + " with arguments " + String.join(" ", args) + " and environment " + envString + " failed");
                throw new RuntimeException(msg);
            }
        }
    }

    /** A platform-independent method to canonicalize a path. */
    protected static String realpath(String path) {
        /*
         * Find the real path to the executable. realpath(3) mallocs a result buffer and returns a
         * pointer to it, so I have to free it.
         */
        try (CCharPointerHolder pathHolder = CTypeConversion.toCString(path)) {
            CCharPointer realpath = Stdlib.realpath(pathHolder.get(), WordFactory.nullPointer());
            if (realpath.isNull()) {
                /* Failure to find a real path. */
                return null;
            } else {
                /* Success */
                String result = CTypeConversion.toJavaString(realpath);
                UntrackedNullableNativeMemory.free(realpath);
                return result;
            }
        }
    }
}
