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

package org.graalvm.visualizer.source;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.source.spi.LocationResolver;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.openide.filesystems.FileObject;
import org.openide.util.BaseUtilities;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakSet;
import org.openide.util.lookup.ServiceProvider;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventListener;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Collects references to files from graphs. Collects unresolved references and
 * fires events when references become resolved. An instance is registered in global Lookup.
 */
@ServiceProvider(service = FileRegistry.class)
public final class FileRegistry {
    private final Map<String, Holder> langTypeResolvers = new HashMap<>();

    // @GuardedBy(self)
    private final Map<FileKey, Reference<FileKey>> keyMap = new WeakHashMap<>();

    // @GuardedBy(keyMap)
    private final Map<FileObject, Reference<FileKey>> file2KeyMap = new WeakHashMap<>();

    // @GuardedBy(keyMap)
    private final Map<R, Collection<InputGraph>> unresolvedKeys = new HashMap<>();

    private Collection<FileKey> resolved = new ArrayList<>();

    private final Collection<FileRegistryListener> listeners = new ArrayList<>();

    public FileRegistry() {
    }

    public static FileRegistry getInstance() {
        return Lookup.getDefault().lookup(FileRegistry.class);
    }

    private void addUnresolvedGraph(FileKey key, Reference<FileKey> r, InputGraph usedIn) {
        // Spotbugs does not understand that all paths calling this method pass non-null `r'.
        if (r == null) {
            return;
        }
        R ref;
        if (!(r instanceof R)) {
            ref = new R(key);
        } else {
            ref = (R) r;
        }
        Collection<InputGraph> users = unresolvedKeys.get(ref);
        if (users == null) {
            users = new WeakSet<>();
            unresolvedKeys.put(ref, users);
        }
        users.add(usedIn);
    }

    /**
     * Registers a new file key. Returns an existing matching instance if already
     * known. Use the method to cannonicalize references before the key is used
     * in other structures.
     * <p/>
     * If the already known key is unresolved, and the newly registered key is resolved,
     * the existing key is updated and returned. Change event fires in that case.
     *
     * @param k the key to enter.
     * @return cannonical key
     */
    public FileKey enter(FileKey k, InputGraph usedIn) {
//        initResolvers();
        synchronized (keyMap) {
            Reference<FileKey> rK = keyMap.get(k);
            if (rK != null) {
                FileKey existing = rK.get();
                if (existing != null) {
                    if (k.isResolved() && !existing.isResolved()) {
                        markResolved(existing, k.getResolvedFile());
                        unresolvedKeys.remove(new R(existing));
                    } else if (!k.isResolved()) {
                        addUnresolvedGraph(existing, rK, usedIn);
                    }
                    return existing;
                }
            }
            // attempt to find an already registered key for the file:
            Reference<FileKey> r;
            FileObject resolved = k.getResolvedFile();
            if (resolved == null) {
                r = new R(k);
                addUnresolvedGraph(k, r, usedIn);
            } else {
                r = file2KeyMap.get(resolved);
                FileKey ref = r == null ? null : r.get();
                if (ref == null) {
                    r = new WeakReference<>(k);
                    file2KeyMap.put(resolved, r);
                } else {
                    throw new IllegalStateException("Key is missing, but file2Key is present: " + r);
                }
            }
            keyMap.put(k, r);
        }
        return k;
    }

    private final class R extends WeakReference<FileKey> implements Runnable {
        private final int h;

        public R(FileKey referent) {
            super(referent, BaseUtilities.activeReferenceQueue());
            this.h = referent.hashCode();
        }

        @Override
        public int hashCode() {
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            FileKey k = get();
            if (k == null || !(o instanceof R)) {
                return false;
            }
            return k == ((R) o).get();
        }

        @Override
        public void run() {
            synchronized (keyMap) {
                unresolvedKeys.remove(this);
            }
        }

        @Override
        public String toString() {
            FileKey k = get();
            if (k == null) {
                return "<obsolete>";
            } else {
                return k.getFileSpec();
            }
        }
    }

    static final RequestProcessor RP = new RequestProcessor(FileRegistry.class);

    // test access
    RequestProcessor.Task EVENT_TASK = RP.create(new Runnable() {
        @Override
        public void run() {
            fireResolved();
        }
    }, true);

    public void attemptResolve(FileKey fk) {
        RP.post(new RevalidateTask(fk));
    }

    private void fireResolved() {
        Collection<FileKey> fireKeys;
        synchronized (resolved) {
            if (this.resolved.isEmpty()) {
                return;
            }
            fireKeys = new HashSet<>(this.resolved);
            this.resolved.clear();
        }
        FileRegistryListener[] ll;
        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            ll = listeners.toArray(new FileRegistryListener[listeners.size()]);
        }
        FileRegistryEvent ev = new FileRegistryEvent(this, fireKeys);
        for (FileRegistryListener l : ll) {
            l.filesResolved(ev);
        }
    }

    private void markResolved(FileKey k, FileObject f) {
        k.setResolvedFile(f);
        synchronized (resolved) {
            resolved.add(k);
        }
        EVENT_TASK.schedule(200);
    }

    private void notifyResolved(Collection<FileKey> keys) {
        synchronized (resolved) {
            if (keys.isEmpty() && resolved.isEmpty()) {
                return;
            }
            resolved.addAll(keys);
        }
        EVENT_TASK.schedule(200);
    }

    public void addFileRegistryListener(FileRegistryListener frl) {
        synchronized (listeners) {
            listeners.add(frl);
        }
    }

    public void removeFileRegistryListener(FileRegistryListener frl) {
        synchronized (listeners) {
            listeners.remove(frl);
        }
    }

    public interface FileRegistryListener extends EventListener {
        public void filesResolved(FileRegistryEvent ev);
    }

    public static final class FileRegistryEvent extends EventObject {
        private Collection<FileKey> resolvedKeys;

        FileRegistryEvent(FileRegistry source, Collection resolvedKeys) {
            super(source);
            this.resolvedKeys = resolvedKeys;
        }

        public FileRegistry getRegistry() {
            return (FileRegistry) getSource();
        }

        public Collection<FileKey> getResolvedKeys() {
            return resolvedKeys;
        }
    }

    private class Holder implements LookupListener, ChangeListener {
        final String langID;
        final Lookup.Result<LocationResolver.Factory> lkpResult;
        Collection<LocationResolver.Factory> resolvers = new ArrayList<>();

        public Holder(String langID, Lookup.Result<LocationResolver.Factory> lkpResult) {
            this.langID = langID;
            this.lkpResult = lkpResult;
        }

        volatile boolean valid;

        synchronized Collection<LocationResolver.Factory> instances() {
            if (valid) {
                return resolvers;
            }
            Collection<LocationResolver.Factory> oldResolvers = new ArrayList<>(resolvers);
            Collection<LocationResolver.Factory> newResolvers = new ArrayList<>();
            for (LocationResolver.Factory f : lkpResult.allInstances()) {
                newResolvers.add(f);
                if (!oldResolvers.remove(f)) {
                    f.addChangeListener(this);
                }
            }
            this.resolvers = newResolvers;
            this.valid = true;
            if (!oldResolvers.isEmpty()) {
                for (LocationResolver.Factory o : oldResolvers) {
                    o.removeChangeListener(this);
                }
            }
            return newResolvers;
        }

        @Override
        public void resultChanged(LookupEvent ev) {
            valid = false;
            stateChanged(null);
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            postRevalidate(langID);
        }
    }

    private Collection<LocationResolver.Factory> findResolvers(String lang) {
        Holder h;
        synchronized (langTypeResolvers) {
            h = langTypeResolvers.get(lang);
            if (h == null) {
                h = new Holder(lang, MimeLookup.getLookup(lang).lookupResult(LocationResolver.Factory.class));
                langTypeResolvers.put(lang, h);
            }
        }
        return h.instances();
    }

    public void resolve(Location l, FileObject f) {
        resolve(l.getFile(), f);
    }

    public void resolve(FileKey fk, FileObject f) {
        resolve(fk, f, true);
    }

    private void resolve(FileKey fk, FileObject f, boolean revalidate) {
        assert f != null;
        R refKey = new R(fk);
        synchronized (unresolvedKeys) {
            if (fk.isResolved()) {
                return;
            }
            if (unresolvedKeys.remove(refKey) == null) {
                return;
            }
            fk.setResolvedFile(f);
            this.keyMap.put(fk, refKey);
        }
        synchronized (resolved) {
            resolved.add(fk);
        }
        if (revalidate) {
            postRevalidate(fk.getMime());
        }
    }

    private final Map<String, RequestProcessor.Task> revalidateTasks = new HashMap<>();

    private void postRevalidate(String langID) {
        synchronized (revalidateTasks) {
            RequestProcessor.Task t = revalidateTasks.get(langID);
            if (t == null) {
                t = RP.post(new RevalidateTask(langID), 200);
                revalidateTasks.put(langID, t);
                return;
            }
            t.schedule(100);
        }
    }

    List<LocationResolver> createResolvers(String langID, InputGraph g) {
        List<LocationResolver> ll = new ArrayList<>();
        for (LocationResolver.Factory f : findResolvers(langID)) {
            LocationResolver r = f.create(g);
            if (r != null) {
                ll.add(r);
            }
        }
        return ll;
    }

    class RevalidateTask implements Runnable {
        private final String langID;
        private final Map<InputGraph, List<LocationResolver>> cachedResolvers = new HashMap<>();
        private final FileKey fileKey;

        public RevalidateTask(String langID) {
            this.langID = langID;
            this.fileKey = null;
        }

        public RevalidateTask(FileKey fk) {
            this.langID = fk.getMime();
            this.fileKey = fk;
        }

        @Override
        public void run() {
            synchronized (revalidateTasks) {
                if (fileKey == null) {
                    revalidateTasks.remove(langID);
                }
            }
            Collection<FileKey> resolved = new ArrayList<>();
            Map<FileKey, Collection<InputGraph>> toResolve = new HashMap<>();
            synchronized (keyMap) {
                if (fileKey != null) {
                    R r = new R(fileKey);
                    Collection<InputGraph> gr = (Collection) unresolvedKeys.get(r);
                    if (gr == null) {
                        return;
                    }
                    toResolve.put(fileKey, gr);
                } else {
                    for (Map.Entry en : unresolvedKeys.entrySet()) {
                        Reference<FileKey> rfk = (Reference<FileKey>) en.getKey();
                        FileKey fk = rfk.get();
                        if (fk == null || !langID.equals(fk.getMime())) {
                            continue;
                        }
                        Collection<InputGraph> gr = (Collection) en.getValue();
                        toResolve.put(fk, gr);
                    }
                }
            }

            for (Map.Entry en : toResolve.entrySet()) {
                FileKey fk = (FileKey) en.getKey();
                Collection<InputGraph> gr = (Collection) en.getValue();
                outer:
                for (InputGraph g : gr) {
                    List<LocationResolver> cached = cachedResolvers.get(g);
                    if (cached == null) {
                        cached = createResolvers(langID, g);
                        cachedResolvers.put(g, cached);
                    }
                    for (LocationResolver lr : cached) {
                        FileObject f = lr.resolve(fk);
                        if (f != null) {
                            // force revalidation of Lang ID type after successful attempt
                            resolve(fk, f, fileKey != null);
                            resolved.add(fk);
                            break outer;
                        }
                    }
                }
            }
            notifyResolved(resolved);
        }
    }

    // only for testing
    static void _testReset() {
        FileRegistry fr = getInstance();
        fr.file2KeyMap.clear();
        fr.keyMap.clear();
//        fr.listeners.clear();
        fr.langTypeResolvers.clear();
        fr.resolved.clear();
        fr.revalidateTasks.clear();
        fr.unresolvedKeys.clear();
    }
}
