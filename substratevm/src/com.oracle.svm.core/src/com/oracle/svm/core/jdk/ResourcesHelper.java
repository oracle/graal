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
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.util.VMError;

@SuppressWarnings("unchecked")
public class ResourcesHelper {

    private static <T> T urlToResource(String resourceName, URL url) {
        try {
            if (url == null) {
                return null;
            }
            URLConnection urlConnection = url.openConnection();
            Object resource = ImageSingletons.lookup(JDKVersionSpecificResourceBuilder.class).buildResource(resourceName, url, urlConnection);
            VMError.guarantee(resource != null);
            return (T) resource;
        } catch (IOException e) {
            return null;
        } catch (ClassCastException classCastException) {
            throw VMError.shouldNotReachHere(classCastException);
        }
    }

    public static <T> T nameToResource(String resourceName) {
        return urlToResource(resourceName, nameToResourceURL(resourceName));
    }

    public static <T> Enumeration<T> nameToResources(String resourceName) {
        Enumeration<URL> urls = Resources.createURLs(resourceName);
        List<T> resourceURLs = new ArrayList<>();
        while (urls.hasMoreElements()) {
            resourceURLs.add(urlToResource(resourceName, urls.nextElement()));
        }
        return Collections.enumeration(resourceURLs);
    }

    public static URL nameToResourceURL(String resourceName) {
        return Resources.createURL(resourceName);
    }

    public static URL nameToResourceURL(String moduleName, String resourceName) {
        return Resources.createURL(moduleName, resourceName);
    }

    public static InputStream nameToResourceInputStream(String resourceName) throws IOException {
        URL url = nameToResourceURL(resourceName);
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
