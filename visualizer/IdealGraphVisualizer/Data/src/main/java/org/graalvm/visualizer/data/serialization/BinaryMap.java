/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.visualizer.data.serialization;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.graalvm.visualizer.settings.graal.GraalSettings;
import org.openide.util.NbBundle;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import jdk.graal.compiler.graphio.parsing.NameTranslator;

public final class BinaryMap implements NameTranslator, PreferenceChangeListener {
    private static final Logger LOG = Logger.getLogger(BinaryMap.class.getName());
    private static final BinaryMap INSTANCE = new BinaryMap();

    private volatile Map<String, String> map;
    private volatile Cache cache;
    private final Map<String, String> versions = Collections.synchronizedMap(new HashMap<>());

    public static BinaryMap versions() {
        return new BinaryMap(INSTANCE.cache());
    }


    private BinaryMap() {
        GraalSettings.obtain().addPreferenceChangeListener(this);
        this.cache = null;
    }

    private BinaryMap(Cache cache) {
        this.cache = cache;
    }

    @Override
    public String translate(String fqn) {
        Map<String, String> m = map();
        return m.get(fqn);
    }

    private Map<String, String> map() {
        if (map == null) {
            Map<String, String> copy;
            synchronized (versions) {
                copy = new HashMap<>(versions);
            }
            Map<String, String> tmp = cache().prepareMap(copy);
            map = tmp;
            return map;
        }
        return map;
    }

    private Cache cache() {
        if (cache == null) {
            List<URI> urls = new ArrayList<>();
            List<File> files = new ArrayList<>();
            final GraalSettings settings = GraalSettings.obtain();
            settings.getFileMap().stream().
                    map((s) -> new File(s)).
                    filter((f) -> f.exists()).
                    forEach(files::add);
            try {
                final String repo = settings.get(String.class, GraalSettings.REPOSITORY);
                urls.add(new URI(repo));
            } catch (URISyntaxException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
            Cache newCache = new Cache(urls, files);
            cache = newCache;
            return newCache;
        }
        return cache;
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent evt) {
        if (evt.getKey().equals(GraalSettings.MAP)) {
            cache = null;
        }
    }

    public void request(String component, String version) {
        versions.put(component, version);
        map = null;
    }

    static final class Cache {
        private static final Map<String, String> NONE = new HashMap<>();
        private final Map<URI, Map<String, String>> cache = new HashMap<>();
        private final List<URI> urls;
        private final Map<File, Map<String, String>> files;

        Cache(List<URI> urls, List<File> files) {
            this.urls = urls;
            this.files = new HashMap<>();
            for (File f : files) {
                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    this.files.put(f, parseMappings(r));
                } catch (IOException ex) {
                    this.files.put(f, NONE);
                }
            }
        }

        private Map<String, String> parseMappings(BufferedReader r) throws IOException {
            Map<String, String> fillIn = new HashMap<>();
            for (; ; ) {
                String line = r.readLine();
                if (line == null) {
                    break;
                }
                if (line.startsWith(" ")) {
                    continue;
                }
                if (line.endsWith(":")) {
                    line = line.substring(0, line.length() - 1);
                }
                int arrow = line.indexOf("->");
                if (arrow == -1) {
                    continue;
                }
                String before = line.substring(0, arrow).trim();
                String after = line.substring(arrow + 2).trim();
                fillIn.put(after, before);
            }
            return fillIn;
        }

        Map<String, String> prepareMap(Map<String, String> versions) {
            Map<String, String> fillIn = new HashMap<>();
            for (URI root : urls) {
                if (!root.isAbsolute()) {
                    continue;
                }
                for (Map.Entry<String, String> entry : versions.entrySet()) {
                    String component = entry.getKey();
                    String version = entry.getValue();

                    URI uri = resolveURI(root, component, version, null);
                    Map<String, String> found = cache.get(uri);
                    if (found == null) {
                        found = readFromMaven(uri, root, component, version);
                        cache.put(uri, found);
                    }
                    fillIn.putAll(found);
                }
            }

            for (Map.Entry<File, Map<String, String>> entry : files.entrySet()) {
                fillIn.putAll(entry.getValue());
            }

            return fillIn;
        }

        private Map<String, String> readFromMaven(URI uri, URI root, String component, String version) {
            if (uri == null) {
                return NONE;
            }
            final URI metaData = uri.resolve("maven-metadata.xml");
            String buildNumber;
            try (InputStream is = metaData.toURL().openStream()) {
                DefaultHandler2 parseNumber = new DefaultHandler2() {
                    private Object timestamp;
                    private Object buildNumber;

                    /**
                     * Blocks reading of external entities.
                     */
                    @Override
                    public InputSource resolveEntity(String name, String publicId,
                                                     String baseURI, String systemId
                    ) throws SAXException, IOException {
                        try {
                            InputSource is = new InputSource(new URI(baseURI).resolve(systemId).toString());
                            is.setCharacterStream(new StringReader("")); // empty external entity
                            return is;
                        } catch (URISyntaxException ex) {
                            throw new SAXException(ex);
                        }
                    }

                    @Override
                    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                        if ("timestamp".equals(qName)) {
                            timestamp = new StringBuilder();
                        }
                        if ("buildNumber".equals(qName)) {
                            buildNumber = new StringBuilder();
                        }
                    }

                    @Override
                    public void characters(char[] ch, int start, int length) throws SAXException {
                        if (timestamp instanceof StringBuilder) {
                            append(timestamp, ch, start, length);
                        }
                        if (buildNumber instanceof StringBuilder) {
                            append(buildNumber, ch, start, length);
                        }
                    }

                    @Override
                    public void endElement(String uri, String localName, String qName) throws SAXException {
                        if (timestamp != null) {
                            timestamp = timestamp.toString();
                        }
                        if (buildNumber != null) {
                            buildNumber = buildNumber.toString();
                        }
                    }

                    private void append(Object obj, char[] ch, int start, int length) {
                        StringBuilder sb = (StringBuilder) obj;
                        sb.append(ch, start, length);
                    }

                    @Override
                    public String toString() {
                        return timestamp + "-" + buildNumber;
                    }
                };
                SAXParserFactory f = javax.xml.parsers.SAXParserFactory.newInstance();
                SAXParser p = f.newSAXParser();
                p.parse(is, parseNumber);
                buildNumber = parseNumber.toString();
            } catch (ParserConfigurationException | SAXException | IOException ex) {
                LOG.log(Level.INFO, "Problems loading " + metaData, ex);
                return NONE;
            }

            URI mapFile = resolveURI(root, component, version, buildNumber);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(mapFile.toURL().openStream()))) {
                return parseMappings(r);
            } catch (IOException ex) {
                LOG.log(Level.INFO, "Problems loading " + uri, ex);
                return NONE;
            }
        }

        private URI resolveURI(URI root, String component, String version, String buildNumber) {
            try {
                String relative = NbBundle.getMessage(BinaryMap.class, component, version, buildNumber);
                URI found = root.resolve(relative);
                return found;
            } catch (MissingResourceException ex) {
                LOG.log(Level.FINE, "No mapping for " + component + " component", ex);
                return null;
            }
        }
    }
}
