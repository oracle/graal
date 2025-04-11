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
package com.oracle.svm.webimage.posix;

import java.nio.file.Path;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.impl.ProcessPropertiesSupport;

import com.oracle.svm.core.BaseProcessPropertiesSupport;
import com.oracle.svm.core.c.locale.LocaleSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;

public class WebImageProcessPropertiesSupport extends BaseProcessPropertiesSupport {
    /**
     * Return some non-null value. The actual executable is not available in the virtual file system
     * anyway.
     */
    @Override
    public String getExecutableName() {
        return "/this-is-not-a-real-file.js";
    }

    @Override
    public long getProcessID() {
        return 0;
    }

    @Override
    public long getProcessID(Process process) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public String getObjectFile(String symbol) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public String getObjectFile(CEntryPointLiteral<?> symbol) {
        return null;
    }

    /** This method is unsafe and should not be used, see {@link LocaleSupport}. */
    @Override
    @SuppressWarnings("deprecation")
    public String setLocale(String category, String locale) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public boolean destroy(long processID) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public boolean destroyForcibly(long processID) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public boolean isAlive(long processID) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public int waitForProcessExit(long processID) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void exec(Path executable, String[] args) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void exec(Path executable, String[] args, Map<String, String> env) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

}

@AutomaticallyRegisteredFeature
class ImagePropertiesFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ProcessPropertiesSupport.class, new WebImageProcessPropertiesSupport());
    }
}
