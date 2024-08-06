/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.core.configure;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.util.json.JsonParserException;

public class PredefinedClassesConfigurationParser extends ConfigurationParser {
    public static InputStream openClassdataStream(URI baseUri, String providedHash) throws IOException {
        return openStream(resolveClassdataFile(baseUri, providedHash));
    }

    private final PredefinedClassesRegistry registry;

    public PredefinedClassesConfigurationParser(PredefinedClassesRegistry registry, boolean strictConfiguration) {
        super(strictConfiguration);
        this.registry = registry;
    }

    private static URI resolveBaseUri(URI original) throws IOException {
        String directory = ConfigurationFile.PREDEFINED_CLASSES_AGENT_EXTRACTED_SUBDIR + "/";
        /*
         * URIs into JAR files, such as those from JARs on the classpath, unfortunately use an
         * atypical legacy syntax in the form jar:<jar-path>!/<path-in-jar> on which URI.resolve
         * does not work (for example, jar:file:/path/to/file.jar!/org/graalvm/NativeImage.class).
         * We use string manipulation to properly determine the base URI.
         *
         * java.nio.file.Path is not an ideal alternative either because we would need to create and
         * keep open a FileSystem for the JAR file in order to be able to resolve URIs within it.
         * Resolving paths via NIO filesystems does a very similar string manipulation to the code
         * below anyway, see ZipFileSystemProvider and ZipPath.
         */
        URI uri = original;
        try {
            if ("jar".equalsIgnoreCase(uri.getScheme())) {
                String ssp = uri.getSchemeSpecificPart();
                int sep = ssp.indexOf("!/");
                String path = ssp.substring(0, sep);
                String entry = ssp.substring(sep + 2);
                int last = entry.lastIndexOf('/');
                String subdir = entry.substring(0, last + 1) + directory;
                return new URI(uri.getScheme(), path + "!/" + subdir, uri.getFragment());
            }
            if (uri.isOpaque()) {
                throw new URISyntaxException(uri.toString(), "expecting URI with absolute path");
            }
            return uri.resolve(directory);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private static URI resolveClassdataFile(URI baseUri, String providedHash) throws IOException {
        String fileName = providedHash + ConfigurationFile.PREDEFINED_CLASSES_AGENT_EXTRACTED_NAME_SUFFIX;
        if ("jar".equalsIgnoreCase(baseUri.getScheme())) { // legacy syntax, see resolveBaseUri
            try {
                return new URI(baseUri.getScheme(), baseUri.getSchemeSpecificPart() + fileName, baseUri.getFragment());
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }
        return baseUri.resolve(fileName);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) throws IOException {
        URI baseUri = origin == null ? null : resolveBaseUri(origin);
        for (Object classDataOrigin : asList(json, "first level of document must be an array of predefined class origin objects")) {
            parseOrigin(baseUri, asMap(classDataOrigin, "second level of document must be a predefined class origin object"));
        }
    }

    private void parseOrigin(URI baseUri, EconomicMap<String, Object> data) {
        checkAttributes(data, "class origin descriptor object", Arrays.asList("type", "classes"));

        String type = asString(data.get("type"), "type");
        if (!type.equals("agent-extracted")) {
            throw new JsonParserException("Attribute 'type' must have value 'agent-extracted'");
        }

        for (Object clazz : asList(data.get("classes"), "Attribute 'classes' must be an array of predefined class descriptor objects")) {
            parseClass(baseUri, asMap(clazz, "second level of document must be a predefined class descriptor object"));
        }
    }

    private void parseClass(URI baseUri, EconomicMap<String, Object> data) {
        checkAttributes(data, "class descriptor object", Collections.singleton("hash"), Collections.singleton("nameInfo"));

        String hash = asString(data.get("hash"), "hash");
        String nameInfo = asNullableString(data.get("nameInfo"), "nameInfo");
        registry.add(nameInfo, hash, baseUri);
    }
}
