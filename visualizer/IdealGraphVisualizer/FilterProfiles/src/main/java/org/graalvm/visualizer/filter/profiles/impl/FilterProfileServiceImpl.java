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

package org.graalvm.visualizer.filter.profiles.impl;

import org.graalvm.visualizer.filter.CustomFilter;
import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterProvider;
import org.graalvm.visualizer.filter.Filters;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileStorage;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataShadow;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.ServiceProvider;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements profile manager over config filesystem.
 *
 * @author sdedic
 */
@ServiceProvider(service = ProfileService.class)
@NbBundle.Messages({
        "PROFILE_DefaultFilters=All Filters"
})
public class FilterProfileServiceImpl implements ProfileService, ProfileStorage {

    private final Map<FileObject, E> profiles = new HashMap<>();
    private final PropertyChangeSupport prop = new PropertyChangeSupport(this);
    private final FileObject profilesRoot;
    private final FileObject defaultProfileFolder;
    private final FilterProfile defaultProfile;
    private List<FilterProfile> profileList = null;
    private final FileChangeListener fileL;
    private final Lookup myLookup;
    private final BiFunction<ProfileService, FileObject, FilterProfile> profileFactory;

    // @GuardedBy(this)
    FilterProfile selectedProfile;

    final class E extends FileChangeAdapter {
        final FileObject file;
        final FilterProfile profile;
        final FileChangeListener wL;

        public E(FileObject file, FilterProfile profile) {
            this.file = file;
            this.profile = profile;
            wL = WeakListeners.create(FileChangeListener.class, this, file);
            file.addFileChangeListener(wL);
        }

        void clear() {
            file.removeFileChangeListener(wL);
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            scheduleRefresh();
        }
    }

    /**
     * Maps individual FileObjects to filter instances. One map per application is
     * present to maintain identity of the filters.
     */
    // @GuardedBy(this)
    Map<FileObject, Filter> file2Filter = new WeakHashMap<>();

    public FilterProfileServiceImpl() {
        this(FileUtil.getConfigFile(PROFILES_FOLDER),
                FileUtil.getConfigFile(DEFAULT_PROFILE_FOLDER), null);
    }

    FilterProfileServiceImpl(FileObject root, FileObject defaultFolder, BiFunction<ProfileService, FileObject, FilterProfile> profileFactory) {
        this.profilesRoot = root;
        this.defaultProfileFolder = defaultFolder;
        if (profileFactory == null) {
            profileFactory = this::defaultCreateProfile;
        }
        this.profileFactory = profileFactory;

        defaultProfile = createProfile(defaultProfileFolder);

        selectedProfile = defaultProfile;
        profilesRoot.addFileChangeListener(WeakListeners.create(FileChangeListener.class, fileL = new FolderListener(), profilesRoot));

        myLookup = Lookups.fixed(this);
    }

    protected FilterProfile createProfile(FileObject f) {
        return profileFactory.apply(this, f);
    }

    private FilterProfile defaultCreateProfile(ProfileService srv, FileObject f) {
        return new FilterProfileAdapter(f, srv, this);
    }

    @Override
    public Lookup getLookup() {
        return myLookup;
    }

    @Override
    public FilterProfile getSelectedProfile() {
        synchronized (this) {
            return selectedProfile;
        }
    }

    @Override
    public void setSelectedProfile(FilterProfile selectedProfile) {
        FileObject storage = getProfileFolder(selectedProfile);
        if (storage == null || (storage != defaultProfileFolder && storage.getParent() != profilesRoot)) {
            throw new IllegalArgumentException("Invalid profile");
        }
        FilterProfile old;

        synchronized (this) {
            old = this.selectedProfile;
            this.selectedProfile = selectedProfile;
        }
        prop.firePropertyChange(PROP_SELECTED_PROFILE, old, selectedProfile);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        prop.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        prop.removePropertyChangeListener(listener);
    }

    @Override
    public FilterProfile getDefaultProfile() {
        return defaultProfile;
    }

    @Override
    public FileObject getProfileFolder(FilterProfile p) {
        synchronized (this) {
            if (p == getDefaultProfile()) {
                return defaultProfileFolder;
            }
            for (E e : profiles.values()) {
                if (p == e.profile) {
                    return e.file;
                }
            }
        }
        return null;
    }

    @Override
    public List<FilterProfile> getProfiles() {
        synchronized (this) {
            if (profileList == null) {
                // refresh, if some changes may be happening.
                if (delayedRefresh != null) {
                    delayedRefresh.cancel();
                }
            } else {
                return new ArrayList<>(profileList);
            }
        }
        return refreshProfiles();
    }

    private boolean wasChanged;

    private E createEntry(FileObject nf) {
        wasChanged = true;
        return new E(nf, profileFactory.apply(this, nf));
    }

    private List<FilterProfile> refreshProfiles() {
        // try to load them:
        List<FilterProfile> adapters = new ArrayList<>();
        adapters.add(defaultProfile);
        List<FileObject> childList = FileUtil.getOrder(Arrays.asList(profilesRoot.getChildren()), false);
        boolean changed;
        // add the default profile as well
        synchronized (this) {
            delayedRefresh = null;
            wasChanged = false;

            for (FileObject fo : childList) {
                if (!fo.isFolder()) {
                    continue;
                }
                E e = profiles.computeIfAbsent(fo, this::createEntry);
                adapters.add(e.profile);
            }
            Set<FileObject> fos = new HashSet<>(profiles.keySet());
            fos.removeAll(childList);

            for (FileObject of : fos) {
                E e = profiles.get(of);
                if (e != null) {
                    e.clear();
                }
            }
            profiles.keySet().removeAll(fos);
            profileList = adapters;
            changed = wasChanged || !fos.isEmpty();
        }
        if (changed) {
            prop.firePropertyChange(PROP_PROFILES, null, null);
        }
        return new ArrayList<>(adapters);
    }

    @Override
    public FilterProfile createProfile(String name, FilterProfile basedOn) throws IOException {
        AtomicReference<FileObject> aFolder = new AtomicReference<>();
        profilesRoot.getFileSystem().runAtomicAction(new FileSystem.AtomicAction() {
            @Override
            public void run() throws IOException {
                FileObject folder = profilesRoot.createFolder(name);
                aFolder.set(folder);
                folder.getFileSystem().runAtomicAction(() -> {
                    DataFolder target = DataFolder.findFolder(folder);
                    List<FileObject> order = new ArrayList<>();
                    List<Filter> l = basedOn == null ? Collections.emptyList() : basedOn.getProfileFilters();
                    for (Filter f : l) {
                        FileObject c = f.getLookup().lookup(FileObject.class);
                        FileObject orig = c;
                        try {
                            FileObject tf = DataShadow.findOriginal(c);
                            if (tf != null && tf != c) {
                                orig = tf;
                            }
                        } catch (IOException ex) {
                            // ignore
                        }
                        if (c == null) {
                            continue;
                        }
                        FileObject shf = DataShadow.create(target, c.getName(), DataObject.find(orig)).getPrimaryFile();
                        FileUtil.copyAttributes(c, shf);
                        order.add(shf);
                    }
                    FileUtil.setOrder(order);
                });
            }
        });
        refreshProfiles();
        synchronized (this) {
            return profiles.get(aFolder.get()).profile;
        }
    }

    RequestProcessor.Task delayedRefresh;

    private void scheduleRefresh() {
        if (delayedRefresh != null) {
            delayedRefresh.cancel();
        }
        delayedRefresh = FilterProfileAdapter.REFRESH_RP.post(this::refreshProfiles, 100);
    }

    @Override
    public FileObject getFilterStorage(Filter f) {
        return f.getLookup().lookup(FileObject.class);
    }

    @NbBundle.Messages({
            "# {0} - profile name",
            "PROFILE_FilterIsUsed=Filter is still used in {0}",
            "# {0} - profile name",
            "# {1} - number of users minus one",
            "PROFILE_FilterIsUsed2=Filter is still used in {0} (and {1} other(s)",
            "# {0} - filter name",
            "PROFILE_CannotDeleteFilter=Cannot find file for {0}"
    })
    @Override
    public void deleteFilter(Filter f) throws IOException {
        FileObject ff = f.getLookup().lookup(FileObject.class);
        if (ff == null) {
            throw new IOException(Bundle.PROFILE_CannotDeleteFilter(f.getName()));
        }
        FileObject fp = ff.getParent();
        Set<FilterProfile> owners = findLocations(f);
        for (Iterator<FilterProfile> ito = owners.iterator(); ito.hasNext(); ) {
            FilterProfile p = ito.next();
            if (fp == getProfileFolder(p)) {
                ito.remove();
            }
        }
        if (!owners.isEmpty()) {
            String s = owners.size() == 1 ?
                    Bundle.PROFILE_FilterIsUsed(owners.iterator().next().getName()) :
                    Bundle.PROFILE_FilterIsUsed2(owners.iterator().next().getName(), owners.size());
            throw new IOException(s);
        }
        ff.delete();
    }

    public FileObject getDefaultProfileFolder() {
        return defaultProfileFolder;
    }

    public FileObject getProfilesRoot() {
        return profilesRoot;
    }

    @Override
    public Filter findDefaultFilter(Filter fromProfile) {
        FileObject storage = getFilterStorage(fromProfile);
        try {
            if (storage.getParent() == defaultProfileFolder) {
                return fromProfile;
            }
            FileObject linkTarget = DataShadow.findOriginal(storage);
            if (linkTarget != null && linkTarget.getParent() == defaultProfileFolder) {
                synchronized (this) {
                    return file2Filter.get(linkTarget);
                }
            }
        } catch (IOException ex) {
            // FIXME: report
        }
        return null;
    }

    private class FolderListener extends FileChangeAdapter {
        @Override
        public void fileDeleted(FileEvent fe) {
            if (fe.getFile().isFolder()) {
                scheduleRefresh();
            }
        }

        @Override
        public void fileFolderCreated(FileEvent fe) {
            scheduleRefresh();
        }
    }

    private FileObject findStorage(Filter f) {
        FileObject fstorage = f.getLookup().lookup(FileObject.class);
        if (fstorage == null) {
            return null;
        }
        FileObject p = fstorage.getParent();
        if (p == defaultProfileFolder || p == profilesRoot) {
            return fstorage;
        } else {
            return null;
        }
    }

    private Stream<FilterProfile> findProfiles(FileObject fstorage) {
        if (fstorage == null) {
            return Stream.empty();
        }
        Collection<FilterProfile> profs;
        profs = new ArrayList<>(getProfiles());
        return profs.stream().filter(p -> findLinkedFilter(fstorage, p) != null);
    }

    private FileObject findLinkedFilter(FileObject fstorage, FilterProfile p) {
        FileObject profStorage = getProfileFolder(p);
        for (FileObject c : profStorage.getChildren()) {
            try {
                if (fstorage == DataShadow.findOriginal(c)) {
                    return c;
                }
            } catch (IOException ex) {
                // PENDING: log only
            }
        }
        return null;
    }

    @Override
    public void deleteFromAllProfiles(Filter f) throws IOException {
        try {
            FileObject fstorage = findStorage(f);
            findProfiles(fstorage).forEach(fpa -> {
                FileObject toDelete = findLinkedFilter(fstorage, fpa);
                if (toDelete != null) {
                    try {
                        toDelete.delete();
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }
            });
            fstorage.delete();
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    @Override
    public Set<FilterProfile> findLocations(Filter f) {
        FileObject fstorage = findStorage(f);
        return findProfiles(fstorage).collect(Collectors.toSet());
    }

    @Override
    public void deleteProfile(FilterProfile p) throws IOException {
        FileObject pd = getProfileFolder(p);
        if (pd == null || pd == defaultProfileFolder) {
            return;
        }
        final boolean change;
        synchronized (this) {
            change = p == selectedProfile;
        }
        pd.getFileSystem().runAtomicAction(new FileSystem.AtomicAction() {
            @Override
            public void run() throws IOException {
                pd.delete();
                if (change) {
                    setSelectedProfile(getDefaultProfile());
                }
            }
        });
    }

    private Filter provideIfAbsent(Map<FileObject, Filter> map, FileObject storage, Filter instance) {
        Filter tmp = map.putIfAbsent(storage, instance);
        return tmp == null ? instance : tmp;
    }

    synchronized Filter forFileObject(FileObject f) {
        return file2Filter.get(f);
    }

    private void removeFromCache(FileObject f) {
        synchronized (this) {
            file2Filter.remove(f);
        }
    }

    @Override
    public Filter createFilter(FileObject storage, FilterProfile parent) {
        if (!storage.isData()) {
            return null;
        }
        if (storage.getExt().equals("selector")) {
            return null;
        }
        Filter filter;
        FileObject fo = storage;
        synchronized (this) {
            filter = file2Filter.get(storage);
            if (filter != null) {
                return filter;
            }
        }
        FilterProvider chSrc = Filters.locateChainSource(fo.getLookup());

        if (chSrc != null) {
            filter = new FilterBridge(fo, parent, chSrc, this);
        } else {
            String code = "";
            try {
                code = fo.asText();
            } catch (FileNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            CustomFilter cf = new CustomFilter(FilterBridge.file2Name(fo), code, fo.getMIMEType(),
                    new ProxyLookup(Lookups.fixed(this), fo.getLookup()));
            LegacyFilterSynchronizer listener = new LegacyFilterSynchronizer(fo, cf) {
                @Override
                public void fileDeleted(FileEvent fe) {
                    removeFromCache(fe.getFile());
                }
            };
            cf.getChangedEvent().addListener(listener);
            fo.addFileChangeListener(listener);
            filter = cf;
        }
        synchronized (this) {
            Filter existing = provideIfAbsent(file2Filter, fo, filter);
            return existing;
        }
    }

    @Override
    public void renameFilter(Filter f, String newName) throws IOException {
        FileObject storage = getFilterStorage(f);
        if (storage.getParent() == defaultProfileFolder) {
            storage.setAttribute("displayName", newName); // NOI18N
            return;
        }
        if (!FileUtil.isParentOf(this.profilesRoot, storage)) {
            // FIXME probably fire an exception
            return;
        }
        try (FileLock fl = storage.lock()) {
            storage.rename(fl, newName, storage.getExt());
        }
    }

    @Override
    public void renameProfile(FilterProfile profile, String newName) throws IOException {
        FileObject storage = getProfileFolder(profile);
        if (storage == null) {
            return;
        }
        try (FileLock lck = storage.lock()) {
            storage.rename(lck, newName, storage.getExt());
        }
    }

    @Override
    public FilterProfile getProfile(FileObject folder) {
        if (folder == this.defaultProfileFolder) {
            return defaultProfile;
        }
        synchronized (this) {
            E e = profiles.get(folder);
            if (e != null) {
                return e.profile;
            }
        }
        return null;
    }

    @Override
    public Filter getFilter(FileObject filter) {
        synchronized (this) {
            return file2Filter.get(filter);
        }
    }

    public ProfileStorage getStorage() {
        return this;
    }
} 
