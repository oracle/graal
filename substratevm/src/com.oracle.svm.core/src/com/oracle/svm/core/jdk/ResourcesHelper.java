/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.svm.core.util.VMError;

public class ResourcesHelper {

    public static URL nameToResourceURL(String resourceName) {
        return Resources.createURL(resourceName);
    }

    public static URL nameToResourceURL(Module module, String resourceName) {
        return Resources.createURL(module, resourceName);
    }

    public static InputStream nameToResourceInputStream(String mn, String resourceName) throws IOException {
        VMError.guarantee(ImageInfo.inImageRuntimeCode(), "ResourcesHelper code should only be used at runtime");
        Module module = mn == null ? null : ModuleLayer.boot().findModule(mn).orElse(null);
        URL url = nameToResourceURL(module, resourceName);
        return url != null ? url.openStream() : null;
    }

    public static List<URL> nameToResourceListURLs(String resourcesName) {
        Enumeration<URL> urls = Resources.createURLs(resourcesName);
        List<URL> resourceURLs = new ArrayList<>();
        while (urls.hasMoreElements()) {
            resourceURLs.add(urls.nextElement());
        }
        return resourceURLs;
    }

    public static Enumeration<URL> nameToResourceEnumerationURLs(String resourcesName) {
        return Collections.enumeration(nameToResourceListURLs(resourcesName));
    }
}
