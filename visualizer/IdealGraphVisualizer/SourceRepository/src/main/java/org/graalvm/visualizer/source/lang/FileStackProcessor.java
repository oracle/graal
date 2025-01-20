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

package org.graalvm.visualizer.source.lang;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NODE_SOURCE_POSITION;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.visualizer.source.FileKey;
import org.graalvm.visualizer.source.Language;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.ProcessorContext;
import org.graalvm.visualizer.source.spi.StackProcessor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.lookup.ServiceProvider;

import jdk.graal.compiler.graphio.parsing.LocationStackFrame;
import jdk.graal.compiler.graphio.parsing.LocationStratum;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.Properties;

/**
 * Generic processor, which extracts just filename and line.
 */
public class FileStackProcessor implements StackProcessor {
    private final String langID;
    private final String langMime;
    //GC prevention
    private ProcessorContext context;

    public FileStackProcessor(String langID, String langMime) {
        this.langID = langID;
        this.langMime = langMime;
    }

    @Override
    public void attach(ProcessorContext ctx) {
        this.context = ctx;
    }

    @Override
    public List<Location> processStack(InputNode node) {
        Properties props = node.getProperties();
        Object o = props.get(PROPNAME_NODE_SOURCE_POSITION, Object.class);
        if (!(o instanceof LocationStackFrame)) {
            return null;
        }

        return new Proc((LocationStackFrame) o).process();
    }

    private class Proc {
        private final LocationStackFrame top;
        private LocationStackFrame frame;
        private final List<Location> locs = new ArrayList<>();
        private Location lastloc;
        private LocationStratum langStratum;
        private int depth;
        private int lastDepth = -1;

        public Proc(LocationStackFrame top) {
            this.top = top;
        }

        private void replaceTop(Location loc) {
            locs.set(locs.size() - 1, loc);
        }

        private List<Location> process() {
            frame = top;
            while (frame != null) {
                boolean found = false;
                for (LocationStratum stratum : frame.getStrata()) {
                    if (!langID.equals(stratum.language)) {
                        continue;
                    }
                    found = true;
                    langStratum = stratum;
                    try {
                        Location l = processFrame();
                        if (l != null) {
                            locs.add(l);
                            lastloc = l;
                            lastDepth = depth;
                        }
                        break;
                    } catch (IOException | URISyntaxException ex) {
                        // ignore for now
                    }
                }
                depth++;
                if (!found) {
                    lastDepth = -1;
                }
                frame = frame.getParent();
            }
            return locs.isEmpty() ? null : locs;
        }

        private Location processFrame() throws IOException, URISyntaxException {
            URI uri = new URI(langStratum.uri);
            FileObject fo = null;
            if (uri.isAbsolute()) {
                URL possibleURL = uri.toURL();
                fo = URLMapper.findFileObject(possibleURL);
            }
            FileKey fk;

            if (fo != null) {
                fk = new FileKey(langStratum.uri, fo);
            } else {
                fk = new FileKey(langMime, langStratum.uri);
            }
            Location newLoc = new Location(langStratum.uri,
                    fk, langStratum.line, langStratum.startOffset, langStratum.endOffset, lastloc, lastDepth, depth);
            if (newLoc.equals(lastloc)) {
                replaceTop(newLoc);
                return null;
            }
            return newLoc;

        }
    }

    @ServiceProvider(service = StackProcessor.Factory.class, position = Integer.MAX_VALUE)
    public final static class RegisteredLangFactory implements StackProcessor.Factory {
        @Override
        public String[] getLanguageIDs() {
            /*
            Collection<String> l = Language.getRegistry().getLanguageIDs();
            String[] langs = l.toArray(new String[l.size()]);
            return langs;
             */
            return null;
        }

        @Override
        public StackProcessor createProcessor(ProcessorContext ctx) {
            Language lng = Language.getRegistry().findLanguageByMime(ctx.getLangID());
            if (lng == null) {
                return null;
            }
            return new FileStackProcessor(lng.getGraalID(), lng.getMimeType());
        }
    }
}
