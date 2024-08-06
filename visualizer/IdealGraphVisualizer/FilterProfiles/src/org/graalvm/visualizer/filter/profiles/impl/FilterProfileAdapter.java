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

import org.graalvm.visualizer.data.ChangedListener;
import org.graalvm.visualizer.filter.CustomFilter;
import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterChain;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataShadow;
import org.openide.util.RequestProcessor;
import java.beans.PropertyChangeListener;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.FilterSequence;
import org.graalvm.visualizer.filter.Filters;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileStorage;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileRenameEvent;
import org.openide.util.Exceptions;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import static org.graalvm.visualizer.filter.profiles.FilterProfile.PROP_NAME;
import org.openide.filesystems.FileEvent;

/**
 * Implements filter operations on a folder - profile. Loads all files in the
 * folder as Filters, but only includes the enabled ones in a
 * {@link #getSelectedFilters()}.
 * <p/>
 * The implementation fires property change events if the name, the order, set of filters
 * change, so code does not need to rely on nonstandard ChangedEvent.
 * 
 * @author sdedic
 */
public class FilterProfileAdapter implements FilterProfile {
    static RequestProcessor REFRESH_RP = new RequestProcessor(FilterProfileAdapter.class);

    public static final String ENABLED_ID = "enabled";
    public static final String DISPLAY_NAME = "name";

    /**
     * The storage folder for the filters.
     */
    private final FileObject profileFolder;

    /**
     * Represents filters to be executed on graphs.
     */
    private final ProfileFilterChain sequence = new ProfileFilterChain();

    /**
     * Represents contents of the profile.
     */
    private final PropertyChangeSupport propSupport = new PropertyChangeSupport(this);
    private final ProfileFilterChain profileFilters = new ProfileFilterChain();
    private final ProfileService root;
    private final ProfileStorage storage;
    private boolean initialized;
    private final L pcl = new L();
    private final FileChangeListener weakFL;
    private final DataFolder dFolder;
    private final ChangedL chainsListener = new ChangedL();
    private final Map<FileObject, FileChangeListener> weakRL = new WeakHashMap<>();

    public FilterProfileAdapter(FileObject filterFolder, ProfileService root, ProfileStorage storage) {
        this.root = root;
        this.storage = storage;
        this.profileFolder = filterFolder;

        // Will listen on DataFolder, as it creates listeners for us, otherwise
        // would need to do bookkeeping of file-listener, which is done in datasystems already
        dFolder = DataFolder.findFolder(filterFolder);
        assert dFolder != null;
        // force data folder to listen...
        weakFL = WeakListeners.create(FileChangeListener.class, pcl, profileFolder);
        profileFilters.getChangedEvent().addListener(chainsListener);
        sequence.getChangedEvent().addListener(chainsListener);
        
        profileFolder.addFileChangeListener(weakFL);
    }
    
    private class L extends FileChangeAdapter implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            postRefresh();
        }

        @Override
        public void fileAttributeChanged(FileAttributeEvent fe) {
            super.fileAttributeChanged(fe);
            String an = fe.getName();
            if (an == null || "displayName".equals(an)) { // NOI18N
                if (fe.getFile().isFolder()) {
                    // change of the profile's own name, refire as a display name change
                    propSupport.firePropertyChange(PROP_NAME, null, getName());
                    return;
                } else {
                    // change of a contained filter's def
                    Object nv = fe.getNewValue();
                    if (nv == null) {
                        nv = fe.getFile().getAttribute("displayName"); // NOI18N
                    }
                    if (nv != null) {
                        handleFileRenamed(fe.getFile(), nv.toString());
                    }
                }
            }
            if (an == null || "position".equals(an)) { // NOI18N
                if (fe.getFile().isData()) {
                    postRefresh();
                }
            }
        }
        
        @Override
        public void fileRenamed(FileRenameEvent fe) {
            if (fe.getFile().isData()) {
                super.fileRenamed(fe);
                handleFileRenamed(fe.getFile(), fe.getFile().getName());
            }
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            super.fileDeleted(fe);
            if (fe.getFile().isData()) {
                postRefresh();
            }
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            super.fileDataCreated(fe);
            postRefresh();
        }
        
    }
    
    private RequestProcessor.Task refreshTask;

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        propSupport.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        propSupport.removePropertyChangeListener(l);
    }

    /**
     * Returns display name of the folder to support localization.
     *
     * @return
     */
    @Override
    public String getName() {
        return dFolder.getNodeDelegate().getDisplayName();
    }

    @Override
    public void setName(String profileName) throws IOException {
        String oN = getName();
        dFolder.rename(profileName);
        propSupport.firePropertyChange(PROP_NAME, oN, profileName);
    }

    /**
     * Note that this only sets a temporary name. Will be overriden if the
     * underlying storage changes. Mainly used to provide standardized
     * name for the "All filters" profile.
     * 
     * @param profileName 
     */
    void setDisplayName(String profileName) {
        String oN = getName();
        dFolder.getNodeDelegate().setDisplayName(profileName);
        propSupport.firePropertyChange(PROP_NAME, oN, profileName);
    }

    @Override
    public FilterSequence getAllFilters() {
        init();
        return profileFilters;
    }

    private void init() {
        synchronized (this) {
            if (initialized) {
                return;
            }
        }
        refresh();
        synchronized (this) {
            initialized = true;
        }
    }

    @Override
    public FilterSequence getSelectedFilters() {
        init();
        return sequence;
    }

    private synchronized void postRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        refreshTask = REFRESH_RP.post(this::refresh, 100);
    }
    
    private FileObject getDefaultProfileFolder() {
        return storage.getProfileFolder(root.getDefaultProfile());
    }

    @Override
    public Filter addSharedFilter(Filter f) throws IOException {
        FileObject fo = f.getLookup().lookup(FileObject.class);
        if (fo == null || fo.getParent() != getDefaultProfileFolder()) {
            throw new IOException("Invalid shared filter");
        }
        if (fo.getParent() == getProfileFolder()) {
            // our own filter, will be refresh()ed
            return f;
        }
        FileObject ff = DataShadow.create(DataFolder.findFolder(getProfileFolder()), fo.getName(), DataObject.find(fo)).getPrimaryFile();
        Filter result = createFilter(ff);
        refresh();
        return result;
    }

    void refresh() {
        List<Filter> newFilters = new ArrayList<>();
        List<Filter> newAllFilters = new ArrayList<>();
        List<Filter> oldAllFilters;
        Set<Filter> oldFilterSet = new HashSet<>();
        Set<Filter> oldAllFilterSet = new HashSet<>();
        Set<FileObject> current = new HashSet<>();
        Map<FileObject, FileChangeListener> fLs;
        synchronized (this) {
            fLs = weakRL;
            chainsListener.changed = false;
            List<FileObject> children = FileUtil.getOrder(Arrays.asList(profileFolder.getChildren()), false);
            for (FileObject f : children) {
                Filter filter = createFilter(f);
                if (filter == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(f.getAttribute(ENABLED_ID))) {
                    newFilters.add(filter);
                }
                newAllFilters.add(filter);
                current.add(f);
            }
            oldAllFilters = profileFilters.getFilters();
            
            Collection<FileObject> toUnregister = new ArrayList<>(fLs.keySet());
            toUnregister.removeAll(current);
            for (FileObject f : toUnregister) {
                f.removeFileChangeListener(fLs.remove(f));
            }
            current.removeAll(fLs.keySet());
        }
        oldFilterSet.addAll(newFilters);
        oldAllFilterSet.addAll(oldAllFilters);
        profileFilters.replaceFilters(newAllFilters);
        sequence.replaceFilters(newFilters);

        if (chainsListener.changed) {
            boolean filtersChanged = false;
            if (!oldFilterSet.containsAll(newFilters)) {
                filtersChanged = true;
            } else {
                oldFilterSet.removeAll(newFilters);
                filtersChanged = oldFilterSet.isEmpty();
            }

            boolean allFiltersChanged = false;
            if (!oldAllFilterSet.containsAll(newAllFilters)) {
                allFiltersChanged = true;
            } else {
                oldAllFilterSet.removeAll(newAllFilters);
                allFiltersChanged = oldAllFilterSet.isEmpty();
            }
            if (allFiltersChanged) {
                propSupport.firePropertyChange(PROP_FILTERS, oldAllFilters, newAllFilters); 
            } else {
                propSupport.firePropertyChange(PROP_FILTER_ORDER, null, null);
            }
            if (filtersChanged) {
                propSupport.firePropertyChange(PROP_ENABLED_FILTERS, null, null);
            }
        }
    }

    protected Filter createFilter(FileObject fo) {
        return storage.createFilter(fo, this);
    }

    @NbBundle.Messages({
        "FILTER_ErrorNoStorage=Could not access the filter storage.",
        "# {0} - filter name",
        "FILTER_NotFound=Filder {0} is not part of the profile"
    })
    @Override
    public void moveDown(Filter f) throws IOException {
        changeOrder(f, true);
    }

    private void changeOrder(Filter f, boolean down) throws IOException {
        FileObject storage = f.getLookup().lookup(FileObject.class);
        if (storage == null) {
            throw new IOException(Bundle.FILTER_ErrorNoStorage());
        }
        FileObject profile = storage.getParent();
        if (profile != profileFolder) {
            throw new IOException(Bundle.FILTER_ErrorNoStorage());
        }
        List<Filter> filters = new ArrayList<>(profileFilters.getFilters());
        int index = filters.indexOf(f);
        if (index == -1) {
            throw new IOException(Bundle.FILTER_NotFound(f.getName()));
        }
        int nIndex = index + (down ? 1 : -1);
        if (nIndex < 0 || nIndex >= filters.size()) {
            return;
        }
        filters.remove(f);
        filters.add(nIndex, f);
        setFilterOrder(filters);
    }

    void setFilterOrder(List<Filter> filters) throws IOException {
        List<FileObject> filterOrder = new ArrayList<>(filters.size());
        for (Filter x : filters) {
            FileObject s = x.getLookup().lookup(FileObject.class);
            if (s != null) {
                filterOrder.add(s);
            }
        }
        List<FileObject> children = new ArrayList<>(
                        FileUtil.getOrder(Arrays.asList(profileFolder.getChildren()), false));
        children.removeAll(filterOrder);
        children.addAll(filterOrder);
        FileUtil.setOrder(children);
        propSupport.firePropertyChange(PROP_FILTER_ORDER, null, null);
    }

    @Override
    public void setEnabled(Filter f, boolean state) throws IOException {
        FileObject storage = f.getLookup().lookup(FileObject.class);
        if (storage == null || storage.getParent() != profileFolder) {
            throw new IOException(Bundle.FILTER_ErrorNoStorage());
        }
        boolean nowState = getSelectedFilters().getFilters().contains(f);
        if (nowState != state) {
            storage.setAttribute(ENABLED_ID, state);
            postRefresh();
        }
    }

    @Override
    public void moveUp(Filter f) throws IOException {
        changeOrder(f, false);
    }

    @Override
    public List<Filter> getProfileFilters() {
        init();
        return profileFilters.getFilters();
    }

    FileObject getProfileFolder() {
        return profileFolder;
    }

    @NbBundle.Messages({
        "ERROR_FilterAlreadyPresent=The filter is already present.",
        "# {0} - filter content type",
        "ERROR_UnknownExtensionForMime=A file cannot be created for filter content {0}.",
        "# {0} - filter class",
        "ERROR_UnknownFilterType=A file cannot be created for filter class {0}.",})
    void createRootFilter(Filter f) throws IOException {
        FileObject storage = f.getLookup().lookup(FileObject.class);
        if (storage != null) {
            FileObject source = DataShadow.findOriginal(storage);
            if (source == null) {
                source = storage;
            }
            if (source.getParent() == getDefaultProfileFolder() || source.getParent() == getProfileFolder()) {
                throw new IOException(Bundle.ERROR_FilterAlreadyPresent());
            }

            DataObject orig = DataObject.find(source);
            getDefaultProfileFolder().getFileSystem().runAtomicAction(() -> {
                DataObject o = orig.copy(DataFolder.findFolder(getDefaultProfileFolder()));
                o.rename(f.getName());
            });
            return;
        }
        CustomFilter cf = f.getLookup().lookup(CustomFilter.class);
        if (cf == null) {
            throw new IOException(Bundle.ERROR_UnknownExtensionForMime(f.getClass().getName()));
        }
        List<String> exts = FileUtil.getMIMETypeExtensions(cf.getMimeType());
        if (exts.isEmpty()) {
            throw new IOException(Bundle.ERROR_UnknownExtensionForMime(cf.getMimeType()));
        }
        FileObject folder = getDefaultProfileFolder();
        folder.getFileSystem().runAtomicAction(() -> {
            try (OutputStream out = folder.createAndOpen(cf.getName() + "." + exts.get(0));
                            InputStream in = new ByteArrayInputStream(cf.getCode().getBytes())) {
                FileUtil.copy(in, out);
            }
        });
    }

    @NbBundle.Messages({
        "# {0} - I/O message text",
        "PROFILE_CannotAddFilter=Could not add filter: {0}"
    })
    void addProfileFilter(Filter filter) {
        FileObject storage = filter.getLookup().lookup(FileObject.class);
        if (storage == null) {
            throw new IllegalArgumentException(Bundle.FILTER_ErrorNoStorage());
        }
        // avoid linking to links go for the original:
        FileObject original;
        try {
            original = DataShadow.findOriginal(storage);
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
        if (original == null) {
            original = storage;
        }
        if (original.getParent() != getDefaultProfileFolder()) {
            throw new IllegalArgumentException(Bundle.FILTER_ErrorNoStorage());
        }
        try {
            DataShadow.create(dFolder, filter.getName(), DataObject.find(original));
        } catch (IOException ex) {
            throw new IllegalArgumentException(Bundle.PROFILE_CannotAddFilter(ex.toString()), ex);
        }
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.profileFolder);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FilterProfileAdapter other = (FilterProfileAdapter) obj;
        return Objects.equals(this.profileFolder, other.profileFolder);
    }

    static class ChangedL implements ChangedListener<FilterChain> {
        volatile boolean changed;

        @Override
        public void changed(FilterChain source) {
            changed = true;
        }
    }

    @Override
    public String toString() {
        return "Profile[" + profileFolder.getName() + "]";
    }
    
    private void handleFileRenamed(FileObject f, String newName) {
        try {
            List<Filter> filters = getProfileFilters();
            setFilterOrder(filters);
            Filter filter = storage.getFilter(f);
            if (filter != null) {
                CustomFilter cf = Filters.lookupFilter(filter, CustomFilter.class);
                if (cf != null) {
                    cf.setName(newName);
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /**
     * Provides access to replaceFilters.
     */
    private static class ProfileFilterChain extends FilterChain {
        @Override
        public void replaceFilters(List<Filter> newFilters) {
            super.replaceFilters(newFilters);
        }
    }

}
