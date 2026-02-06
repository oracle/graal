/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.cosmo.headers;

import com.oracle.svm.core.c.libc.CosmoLibC;
import com.oracle.svm.core.c.libc.LibCBase;
import org.graalvm.nativeimage.c.CContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CosmoDirectives implements CContext.Directives {
    private static final String[] commonLibs = new String[]{
                    "<dlfcn.h>",
                    "<dirent.h>",
                    "<fcntl.h>",
                    "<limits.h>",
                    "<locale.h>",
                    "<pthread.h>",
                    "<pwd.h>",
                    "<semaphore.h>",
                    "<signal.h>",
                    "<errno.h>",
                    "<sys/file.h>",
                    "<sys/mman.h>",
                    "<sys/resource.h>",
                    "<sys/stat.h>",
                    "<sys/time.h>",
                    "<sys/times.h>",
                    "<sys/types.h>",
                    "<sys/utsname.h>",
                    "<time.h>",
                    "<unistd.h>",
    };

    @Override
    public boolean isInConfiguration() {
        return LibCBase.targetLibCIs(CosmoLibC.class);
    }

    @Override
    public List<String> getHeaderFiles() {
        List<String> result = new ArrayList<>(Arrays.asList(commonLibs));
        return result;
    }

    @Override
    public List<String> getOptions() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getMacroDefinitions() {
        return Arrays.asList("_LARGEFILE64_SOURCE", "_COSMO_SOURCE");
    }
}
