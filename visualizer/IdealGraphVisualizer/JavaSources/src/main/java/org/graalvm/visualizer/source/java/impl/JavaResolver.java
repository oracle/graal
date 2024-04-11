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

package org.graalvm.visualizer.source.java.impl;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.source.FileKey;
import org.graalvm.visualizer.source.spi.LocationResolver;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.classpath.GlobalPathRegistryEvent;
import org.netbeans.api.java.classpath.GlobalPathRegistryListener;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.java.queries.SourceForBinaryQuery;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 *
 */
@NbBundle.Messages({
        "Filter_JavaProjects=Projects with java sources"
})
@MimeRegistration(mimeType = "text/x-java", service = LocationResolver.Factory.class)
public class JavaResolver implements LocationResolver.Factory, GlobalPathRegistryListener, PropertyChangeListener {
    private final Collection<ChangeListener> listeners = new ArrayList<>();
    private Map<ClassPath, Reference<PropertyChangeListener>> pathListeners = new WeakHashMap<>();
    private final GlobalPathRegistry registry;

    public JavaResolver() {
        registry = GlobalPathRegistry.getDefault();
        registry.addGlobalPathRegistryListener(WeakListeners.create(GlobalPathRegistryListener.class, this, GlobalPathRegistry.getDefault()));
        initGlobalRegistry();
        pathsAdded(null);
    }

    @Override
    public LocationResolver create(InputGraph src) {
//        return updater.createInstance();
        return createInstance();
    }

    private LocationResolver sharedInstance;

    @Override
    public void addChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    private void unregisterFrom(ClassPath cp) {
        Reference<PropertyChangeListener> rpl = pathListeners.remove(cp);
        if (rpl != null) {
            PropertyChangeListener pl = rpl.get();
            if (pl != null) {
                cp.removePropertyChangeListener(pl);
            }
        }
    }

    @Override
    public void pathsRemoved(GlobalPathRegistryEvent event) {
        for (ClassPath cp : event.getChangedPaths()) {
            unregisterFrom(cp);
        }
    }

    private boolean possibleJavaRoot(FileObject f, Set<Project> seen) {
        Project p = FileOwnerQuery.getOwner(f);
        if (p != null) {
            if (seen.contains(p)) {
                Sources s = p.getLookup().lookup(Sources.class);
                if (s != null) {
                    for (SourceGroup sg : s.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)) {
                        FileObject root = sg.getRootFolder();
                        ClassPath srcPath = ClassPath.getClassPath(root, ClassPath.SOURCE);
                        if (srcPath != null) {
                            return true;
                        }
                    }
                }
            }
            seen.add(p);
            return false;
        } else {
            // may contain some java resources
            return true;
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (ClassPath.PROP_ROOTS.equals(evt.getPropertyName())) {
            fireChange();
        }
    }


    @Override
    public void pathsAdded(GlobalPathRegistryEvent event) {
        Set<Project> seen = new HashSet<>();
        synchronized (this) {
            Set<ClassPath> spaths = registry.getPaths(ClassPath.SOURCE);
            List<ClassPath> ncp = new ArrayList<>();
            Set<ClassPath> oldCp = new HashSet<>(pathListeners.keySet());

            for (ClassPath cp : spaths) {
                FileObject[] roots = cp.getRoots();
                if (roots.length == 0) {
                    ncp.add(cp);
                } else {
                    for (FileObject f : cp.getRoots()) {
                        if (possibleJavaRoot(f, seen)) {
                            ncp.add(cp);
                        }
                    }
                }
            }

            if (oldCp.containsAll(ncp) && oldCp.size() == ncp.size()) {
                return;
            }

            oldCp.removeAll(ncp);
            for (ClassPath oc : oldCp) {
                unregisterFrom(oc);
            }

            ncp.removeAll(pathListeners.keySet());
            for (ClassPath nc : ncp) {
                PropertyChangeListener l = WeakListeners.propertyChange(this, nc);
                pathListeners.put(nc, new WeakReference<>(l));
                nc.addPropertyChangeListener(l);
            }
        }
        // do not fire events on init
        if (event != null) {
            fireChange();
        }
    }

    private void fireChange() {
        synchronized (this) {
            sharedInstance = null;
        }
        ChangeListener[] ll;

        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            ll = listeners.toArray(new ChangeListener[listeners.size()]);
        }
        ChangeEvent ev = new ChangeEvent(this);
        for (ChangeListener l : ll) {
            l.stateChanged(ev);
        }
    }

    synchronized LocationResolver createInstance() {
        if (sharedInstance == null) {
            sharedInstance = new GraphLocationResolver();
        }
        return sharedInstance;
    }

    public static class GraphLocationResolver implements LocationResolver {
        private ClassPath proxySource;
        private ClassPath proxyCompile;

        public GraphLocationResolver() {
            Set<FileObject> roots = GlobalPathRegistry.getDefault().getSourceRoots();
            proxySource = ClassPathSupport.createClassPath(roots.toArray(new FileObject[roots.size()]));

            Set<ClassPath> compilePaths = GlobalPathRegistry.getDefault().getPaths(ClassPath.COMPILE);
            proxyCompile = ClassPathSupport.createProxyClassPath(compilePaths.toArray(new ClassPath[compilePaths.size()]));
        }

        @Override
        public FileObject resolve(FileKey l) {
            String javaSource = l.getFileSpec();
            if (!javaSource.endsWith(".java")) {
                return null;
            }
            String clazzSource = javaSource.substring(0, javaSource.length() - 4) + "class";
            FileObject src = proxySource.findResource(javaSource);
            if (src != null) {
                return src;
            }
            FileObject clazz = proxyCompile.findResource(clazzSource);
            if (clazz != null) {
                FileObject ownerRoot = proxyCompile.findOwnerRoot(clazz);
                URL ownerURL = URLMapper.findURL(ownerRoot, URLMapper.INTERNAL);
                if (ownerURL != null) {
                    SourceForBinaryQuery.Result2 res = SourceForBinaryQuery.findSourceRoots2(ownerURL);
                    proxySource = ClassPathSupport.createClassPath(res.getRoots());
                    src = proxySource.findResource(javaSource);
                    return src;
                }
            }
            return null;
        }
    }

    private static boolean globalInitialized;

    /**
     * Initialized global CP registry once, in a hope that some opened project did
     * it already. In that case, j.l.Object resource will be reachable through some
     * of the classpaths.
     */
    private static void initGlobalRegistry() {
        if (globalInitialized) {
            return;
        }
        try {
            Set<ClassPath> cpset = new HashSet<>(GlobalPathRegistry.getDefault().getPaths(ClassPath.BOOT));
            cpset.addAll(GlobalPathRegistry.getDefault().getPaths(ClassPath.COMPILE));
            for (ClassPath cp : cpset) {
                FileObject jlof = cp.findResource("java/lang/Object.class"); // NOI18N
                if (jlof != null) {
                    return;
                }
            }
            JavaPlatform def = JavaPlatform.getDefault();
            if (def == null) {
                return;
            }
            ClassPath src = def.getBootstrapLibraries();
            GlobalPathRegistry.getDefault().register(ClassPath.COMPILE, new ClassPath[]{src});
        } finally {
            globalInitialized = true;
        }
    }
}
