/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.home;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;

import org.graalvm.home.impl.DefaultHomeFinder;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * A utility class to find various paths of the running GraalVM, as well as the version.
 *
 * Use {@link HomeFinder#getInstance()} to get the singleton instance.
 *
 * @since 19.3
 */
public abstract class HomeFinder {
    /**
     * @since 19.3
     */
    public HomeFinder() {
    }

    /**
     * @return the GraalVM home folder if inside GraalVM or {@code null} otherwise.
     * @since 19.3
     */
    public abstract Path getHomeFolder();

    /**
     * @return the GraalVM version or {@code "snapshot"} if unknown.
     * @since 19.3
     */
    public abstract String getVersion();

    /**
     * @return a Map of language ids to their home path.
     * @since 19.3
     */
    public abstract Map<String, Path> getLanguageHomes();

    /**
     * @return a Map of tool ids to their home path.
     * @since 19.3
     */
    public abstract Map<String, Path> getToolHomes();

    /**
     * @return the HomeFinder instance by using a ServiceLoader.
     * @throws IllegalStateException if no implementation could be found.
     * @since 19.3
     */
    public static HomeFinder getInstance() {
        if (ImageInfo.inImageCode()) {
            return ImageSingletons.lookup(HomeFinder.class);
        } else {
            final ServiceLoader<HomeFinder> serviceLoader = ServiceLoader.load(HomeFinder.class);
            final Iterator<HomeFinder> iterator = serviceLoader.iterator();
            try {
                return iterator.next();
            } catch (NoSuchElementException e) {
                throw new IllegalStateException("No implementation of " + HomeFinder.class.getName() + " could be found");
            }
        }
    }
}

class HomeFinderFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(HomeFinder.class, new DefaultHomeFinder());
    }
}
