/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.program;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import com.sun.max.lang.*;

/**
 * Provides a facility for finding classes reachable on a given {@linkplain Classpath classpath}.
 */
public class ClassSearch extends ClasspathTraversal {

    private final HashSet<String> classes;

    public ClassSearch() {
        this(false);
    }

    /**
     * Creates a class search object.
     *
     * @param omitDuplicates if true, then each argument passed to {@link #visitClass(String)} is guaranteed to be unique.
     */
    public ClassSearch(boolean omitDuplicates) {
        if (omitDuplicates) {
            classes = new HashSet<String>();
        } else {
            classes = null;
        }
    }

    /**
     * Handles a class file encountered during the traversal.
     *
     * @param className
     *                the name of the class denoted by the class file
     * @return true if the traversal should continue, false if it should terminate
     */
    protected boolean visitClass(String className) {
        return true;
    }

    /**
     * Handles a class file encountered during the traversal. Unless this object was initialized to omit duplicates,
     * this method may be called more than once for the same class as class files are not guaranteed to be unique in a
     * classpath.
     *
     * @param isArchiveEntry true if the class is in a .zip or .jar file, false if it is a file in a directory
     * @param className the name of the class denoted by the class file
     * @return true if the traversal should continue, false if it should terminate
     */
    protected boolean visitClass(boolean isArchiveEntry, String className) {
        if (classes != null) {
            if (classes.contains(className)) {
                return true;
            }
            classes.add(className);
        }
        return visitClass(className);
    }

    protected boolean visit(boolean isArchiveEntry, String dottifiedResource) {
        if (dottifiedResource.endsWith(".class")) {
            final String className = Strings.chopSuffix(dottifiedResource, ".class");
            return visitClass(isArchiveEntry, className);
        }
        return true;
    }

    @Override
    protected boolean visitArchiveEntry(ZipFile archive, ZipEntry resource) {
        return visit(true, resource.getName().replace('/', '.'));
    }

    @Override
    protected boolean visitFile(File parent, String resource) {
        return visit(false, resource.replace(File.separatorChar, '.'));
    }
}
