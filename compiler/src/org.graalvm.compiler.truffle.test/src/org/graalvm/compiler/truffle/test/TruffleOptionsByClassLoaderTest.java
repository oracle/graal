/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.oracle.truffle.api.TruffleOptions;

public class TruffleOptionsByClassLoaderTest {

    @Test
    public void loadTruffleOptionsByOwnClassLoader() throws Exception {
        URL url = TruffleOptions.class.getResource("TruffleOptions.class");
        if (url != null) {
            String urlString = url.toString();
            String protocol = url.getProtocol();
            url = null;
            if ("jar".equals(protocol)) {
                // Example:
                // jar:file:/usr/lib/jvm/graalvm-ce-19.2.0/jre/lib/truffle/truffle-api.jar!/com/oracle/truffle/api/TruffleOptions.class
                Matcher matcher = Pattern.compile("jar:(.*)!/com/oracle/truffle/api/TruffleOptions\\.class").matcher(urlString);
                if (matcher.matches()) {
                    url = new URL(matcher.group(1));
                }
            } else if ("jrt".equals(protocol)) {
                // Example: jrt:/org.graalvm.truffle/com/oracle/truffle/api/TruffleOptions.class
                url = new URL(urlString.substring(0, urlString.length() - "com/oracle/truffle/api/TruffleOptions.class".length()));
            }
        }
        assertNotNull("Did not find a URL for accessing the Truffle API", url);

        final String truffleOptionsName = "com.oracle.truffle.api.TruffleOptions";
        ClassLoader loader = new URLClassLoader(new URL[]{url}) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith(truffleOptionsName)) {
                    return super.findClass(name);
                }
                return super.loadClass(name);
            }
        };

        Class<?> truffleOptions = loader.loadClass(truffleOptionsName);
        assertEquals("Right classloader", loader, truffleOptions.getClassLoader());

        final Field aotField = truffleOptions.getField("AOT");
        Object aot = aotField.get(null);
        assertEquals("Not running in AOT", Boolean.FALSE, aot);
    }
}
