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

package org.graalvm.visualizer.source.impl;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Implementation of SourceRepository as sets of [java] source roots. Uses
 * Preferences as the storage. The strucutre of the preferences node is as
 * follows:
 * <ul>
 * <li>roots<ul>
 * <li><b>url.&lt;integer></b> - URL of the root location; assuming file: URL
 * <li><b>url.&lt;integer>.name</b> - configurable display name
 * </ul>
 * <li>groups</ul>
 * <li><b>group.&lt;integer></b><ul>
 * <li><b>name</b> - configurable display name
 * <li><b>members<b> - keys from the "roots" node which are member of this group
 */
public class SourceRepositoryImpl {
    private Preferences prefs;
    private int highestIndex;
    private final Map<URI, String> groupKeys = new HashMap<>();
    private final Map<URL, String> fileKeys = new HashMap<>();
    private final Map<URL, FileRoot> fileRoots = new HashMap<>();
    private final Map<String, FileGroup> groups = new HashMap<>();
    private final FileGroup defaultGroup;
    private final List<ChangeListener> listeners = new ArrayList<>();

    @NbBundle.Messages({
            "NAME_DefaultGroup=Default"
    })
    SourceRepositoryImpl(Preferences prefs) {
        try {
            this.prefs = prefs;
            prefs.flush();
            defaultGroup = new FileGroup(new URI("urn:defaultGroup"), Bundle.NAME_DefaultGroup(), this);
            loadContents(prefs);
        } catch (BackingStoreException | URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static volatile SourceRepositoryImpl INSTANCE;

    public static final SourceRepositoryImpl getInstance() {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (SourceRepositoryImpl.class) {
            INSTANCE = new SourceRepositoryImpl(NbPreferences.forModule(SourceRepositoryImpl.class));
        }
        return INSTANCE;
    }

    public FileGroup getDefaultGroup() {
        return defaultGroup;
    }

    public Collection<FileGroup> getGroups() {
        synchronized (fileRoots) {
            return new ArrayList<>(groups.values());
        }
    }

    public FileGroup createGroup(String displayName) throws IOException {
        String prefKey = "group." + Integer.toString(highestIndex++);
        URI uri;
        FileGroup g;
        try {
            uri = new URI("urn:" + prefKey);
            g = new FileGroup(uri, displayName, this);

        } catch (URISyntaxException ex) {
            // should never happen
            throw new IOException(ex);
        }
        Preferences groupNode = prefs.node("groups");
        groupNode.put(prefKey, displayName);
        try {
            groupNode.sync();
        } catch (BackingStoreException ex) {
            throw new IOException(ex);
        }
        synchronized (fileRoots) {
            this.groupKeys.put(uri, prefKey);
            this.groups.put(prefKey, g);
        }
        g.addRegisteredPath();
        fireChange();
        return g;
    }

    String groupPrefKey(FileGroup g) {
        return g.getURI().toString().substring(4);
    }

    void renameGroup(FileGroup g, String newName) throws IOException {
        String prefKey = groupPrefKey(g);
        Preferences groupNode = prefs.node("groups");
        groupNode.put(prefKey, newName);
    }

    public void deleteGroup(FileGroup g) throws IOException {
        String prefKey = g.getURI().toString().substring(4);
        Preferences groupParentNode = prefs.node("groups");
        if (groupParentNode.get(prefKey, null) == null) {
            throw new IOException("Unknown group: " + g.getDisplayName());
        }
        List<FileRoot> orphans = g.getFileRoots();
        for (FileRoot r : orphans) {
            removeRoot(r);
        }
        try {
            synchronized (fileRoots) {
                groupKeys.remove(g.getURI());
                groups.remove(prefKey);
            }
            groupParentNode.remove(prefKey);
            groupParentNode.sync();
            fireChange();
        } catch (BackingStoreException ex) {
            throw new IOException(ex);
        }
    }

    void setRootName(URL root, String dispName) {
        String key;
        synchronized (fileRoots) {
            assert fileRoots.containsKey(root);
            key = fileKeys.get(root);
        }
        prefs.node("roots" + "/" + key).put("name", dispName);
    }

    void removeRoot(FileRoot r) throws IOException {
        URL u = r.getLocation();
        String prefKey;
        FileGroup parent;

        synchronized (this.fileRoots) {
            prefKey = this.fileKeys.get(u);
            try {
                if (!prefs.nodeExists("roots" + "/" + prefKey)) {
                    throw new IOException("Unknown root:" + u);
                }
                Preferences n = prefs.node("roots").node(prefKey);
                n.removeNode();
                n.flush();
            } catch (BackingStoreException ex) {
                throw new IOException(ex);
            }
            parent = r.getParent();
            fileKeys.remove(u);
            fileRoots.remove(u);
        }
        parent.removeRoot(r);
        fireChange();
    }

    public FileRoot addLocation(FileObject f, String dispName, FileGroup parent) throws IOException {
        if (parent == null) {
            parent = defaultGroup;
        }
        if (dispName == null) {
            dispName = f.getName();
        }
        URL u = URLMapper.findURL(f, URLMapper.INTERNAL);
        synchronized (fileRoots) {
            FileRoot existing = fileRoots.get(u);
            // do not add one root twice
            if (existing != null) {
                return existing;
            }
        }
        FileRoot r = new FileRoot(this, u);
        r.setDisplayName(dispName);
        // create preference node:
        return addFileRoot(r, parent);
    }

    FileRoot addFileRoot(FileRoot r, FileGroup parent) throws IOException {
        String dispName = r.getDisplayName();
        URL u = r.getLocation();
        String prefKey = "root" + Integer.toString(highestIndex++);
        Preferences node = prefs.node("roots").node(prefKey);
        node.put("url", u.toString());
        node.put("name", dispName);
        if (parent != getDefaultGroup()) {
            String groupKey = groupKeys.get(parent.getURI());
            node.put("group", groupKey);
        }
        try {
            node.sync();
        } catch (BackingStoreException ex) {
            throw new IOException(ex);
        }
        synchronized (fileRoots) {
            this.fileKeys.put(u, prefKey);
            this.fileRoots.put(u, r);
            r.setDisplayName(dispName);
            r.setParent(parent);
        }
        // fires change event from its classpath
        parent.addRoot(r);
        fireChange();
        return r;
    }

    final void loadContents(Preferences collection) throws BackingStoreException {
        Map<String, FileGroup> groups = new HashMap<>();
        Map<URI, String> groupKeys = new HashMap<>();
        if (prefs.nodeExists("groups")) {
            Preferences groupNode = prefs.node("groups");
            for (String s : groupNode.keys()) {
                if (!s.startsWith("group")) {
                    continue;
                }
                int max = Integer.parseInt(s.substring(6));
                if (max > highestIndex) {
                    highestIndex = max;
                }
                String v = groupNode.get(s, null);
                if (v == null) {
                    continue;
                }
                try {
                    URI groupURI = new URI("urn:" + s);
                    FileGroup g = new FileGroup(groupURI, v, this);
                    groups.put(s, g);
                    groupKeys.put(groupURI, s);
                } catch (URISyntaxException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        Map<URL, FileRoot> roots = new HashMap<>();
        Map<URL, String> keys = new HashMap<>();
        if (prefs.nodeExists("roots")) {
            Preferences rootsParent = prefs.node("roots");
            for (String s : rootsParent.childrenNames()) {
                if (!s.startsWith("root")) {
                    continue;
                }
                Preferences rootNode = rootsParent.node(s);
                int max = Integer.parseInt(s.substring(4));
                if (max > highestIndex) {
                    highestIndex = max;
                }
                String v = rootNode.get("url", null);
                if (v == null) {
                    continue;
                }
                FileRoot r;
                try {
                    r = new FileRoot(this, new URL(v));
                    FileObject f = URLMapper.findFileObject(r.getLocation());
                    String name = rootNode.get("name", f != null ? f.getName() : "<unknown>");
                    r.setDisplayName(name);
                    roots.put(r.getLocation(), r);
                    keys.put(r.getLocation(), s);
                } catch (MalformedURLException ex) {
                    Exceptions.printStackTrace(ex);
                    continue;
                }
                String parentId = rootNode.get("group", null);
                FileGroup g = null;
                if (parentId != null) {
                    g = groups.get(parentId);
                }
                if (g == null) {
                    g = defaultGroup;
                }
                g.addRoot(r);
                r.setParent(g);
            }
        }
        getDefaultGroup().addRegisteredPath();
        for (FileGroup g : groups.values()) {
            g.addRegisteredPath();
        }
        synchronized (this.fileRoots) {
            this.groups.putAll(groups);
            this.fileRoots.putAll(roots);
            this.fileKeys.putAll(keys);
            this.groupKeys.putAll(groupKeys);
        }
    }

    public void addChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void removeChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    void fireChange() {
        ChangeListener[] ll;
        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            ll = listeners.toArray(new ChangeListener[listeners.size()]);
        }
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : ll) {
            l.stateChanged(e);
        }
    }

    static synchronized void _testReset() {
        if (INSTANCE != null) {
            INSTANCE.getDefaultGroup().removeRegisteredPath();
            INSTANCE.getGroups().forEach((g) -> g.removeRegisteredPath());
        }
        INSTANCE = null;
    }
}
