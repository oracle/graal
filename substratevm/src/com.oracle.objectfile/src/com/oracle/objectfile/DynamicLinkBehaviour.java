/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.objectfile;

public interface DynamicLinkBehaviour {

    /**
     * Return an Iterable of Strings giving the identifiers <b>as recorded in the library's
     * format-specific form</b> of libraries on which this dynamic object is declared to depend.
     */
    Iterable<String> getNeededLibraries();

    /**
     * Return a list of paths in the filesystem which will be searched, in order, when searching for
     * a depended-on library.
     */
    Iterable<String> getRuntimeLinkPaths();

    /**
     * Add a library name to the list of libraries on which this dynamic object is declared to
     * depend. It will be searched using the runtime linker path. (Other behaviours which might be
     * offered by the underlying format, such as absolute pathnames or executable-relative search,
     * are not currently supported.)
     * 
     * @param soname the filename of the library -- this should <b>not</b> include a directory
     *            prefix
     */
    void addNeededLibrary(String soname);

    /**
     * Add a linker path to the list of libraries on which this dynamic object is declared to
     * depend.
     * 
     * @param linkPath the directory which is to be added to the run-time libary search path.
     */
    void addRuntimeLinkPath(String linkPath);
}
