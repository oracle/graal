/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class JfrArtifactSet {

    private final Set<Class<?>> klassList = new HashSet<>();
    // Go by package name as java.lang.Package has default equals
    // and package instances are not equal in graal
    // https://github.com/oracle/graal/issues/2989
    private final Set<Package> packageList = new HashSet<>();
    private final Set<Module> moduleList = new HashSet<>();
    private final Set<ClassLoader> classLoaderList = new HashSet<>();

    private final Map<String, Long> symbolMap = new HashMap<>();
    private int totalCount;

    private static final AtomicLong symbolCounter = new AtomicLong(1);

    public JfrArtifactSet() {
        initialize(false);
    }

    public void initialize(boolean clearArtifacts) {
        this.klassList.clear();
        if (clearArtifacts) {
            this.symbolMap.clear();
            symbolCounter.set(1);
        }
        this.totalCount = 0;
    }

    public boolean hasKlassEntries() {
        return !this.klassList.isEmpty();
    }

    public void registerKlass(Class<?> c) {
        assert (c != null);
        this.klassList.add(c);
        // Don't include the package that represents no package
        if (c.getPackage() != null && c.getPackage().hashCode() != 0) {
            this.packageList.add(c.getPackage());
        }
        if (c.getModule() != null) {
            this.moduleList.add(c.getModule());
        }
        if (c.getClassLoader() != null) {
            this.classLoaderList.add(c.getClassLoader());
        }
    }

    public long mark(Class<?> c, boolean leak) {
        assert (c != null);
        // JFR.TODO
        // if (is_hidden_or_anon_klass(k)) {
        // assert(k->is_instance_klass(), "invariant");
        // symbol_id = mark_hidden_or_anon_klass_name((const InstanceKlass*)k, leakp);
        // }
        long id = mark(c.getName(), leak);
        assert (id > 0);
        return id;
    }

    public void iterateKlasses(Consumer<Class<?>> consumer) {
        iterateSet(consumer, klassList);
    }

    public void iteratePackages(Consumer<Package> consumer) {
        iterateSet(consumer, packageList);
    }

    public void iterateModules(Consumer<Module> consumer) {
        iterateSet(consumer, moduleList);
    }

    public void iterateClassLoaders(Consumer<ClassLoader> consumer) {
        iterateSet(consumer, classLoaderList);
    }

    private <T> void iterateSet(Consumer<T> c, Set<T> set) {
        for (T item : set) {
            c.accept(item);
        }
    }

    public long mark(String name, boolean leak) {
        if (this.symbolMap.containsKey(name)) {
            return this.symbolMap.get(name);
        } else if (name.trim().length() > 0) {
            long nextId = symbolCounter.getAndIncrement();
            this.symbolMap.put(name, nextId);
            return nextId;
        } else {
            return 0;
        }
    }

    public Map<String, Long> getSymbolMap() {
        return this.symbolMap;
    }

    public void tally(int count) {
        this.totalCount += count;
    }

    public int getTotalCount() {
        return this.totalCount;
    }

}
