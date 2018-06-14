/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.source;

import com.oracle.truffle.api.impl.TruffleLocator;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.util.Collections;
import java.util.Enumeration;
import static org.junit.Assert.assertNotNull;

public final class CommonMIMETypeLocator extends TruffleLocator {
    public static final class Detector extends FileTypeDetector {
        @Override
        public String probeContentType(Path path) throws IOException {
            if (path.getFileName().toString().endsWith(".locme")) {
                return "application/x-locator";
            }
            return null;
        }
    }

    @Override
    public void locate(Response response) {
        response.registerClassLoader(new CommonLoader());
    }

    private static class CommonLoader extends ClassLoader {
        CommonLoader() {
            super(getSystemClassLoader());
        }

        @Override
        protected URL findResource(String name) {
            if (name.equals("META-INF/services/java.nio.file.spi.FileTypeDetector")) {
                return getResource("com/oracle/truffle/api/test/source/CommonMimeTypeLocator");
            }
            return super.findResource(name);
        }

        @Override
        protected Enumeration<URL> findResources(String name) throws IOException {
            if (name.equals("META-INF/services/java.nio.file.spi.FileTypeDetector")) {
                URL locator = ClassLoader.getSystemClassLoader().getResource(
                                "com/oracle/truffle/api/test/source/CommonMIMETypeLocator");
                assertNotNull("We have to find a locator registration", locator);
                return Collections.enumeration(Collections.singleton(locator));
            }
            Enumeration<URL> ret = getParent().getResources(name);
            return ret;
        }
    }

}
