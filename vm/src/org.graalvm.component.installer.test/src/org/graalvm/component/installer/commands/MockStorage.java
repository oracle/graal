/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.commands;

import static org.graalvm.component.installer.CommonConstants.CAP_GRAALVM_VERSION;
import static org.graalvm.component.installer.CommonConstants.CAP_OS_ARCH;
import static org.graalvm.component.installer.CommonConstants.CAP_OS_NAME;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentStorage;

/**
 * Mock implementation of component storage to avoid mess with disk files.
 */
public class MockStorage implements ComponentStorage {
    public static final Map<String, String> DEFAULT_GRAAL_INFO = new HashMap<>();

    static {
        DEFAULT_GRAAL_INFO.put(CAP_GRAALVM_VERSION, "0.32");
        DEFAULT_GRAAL_INFO.put(CAP_OS_ARCH, "amd64");
        DEFAULT_GRAAL_INFO.put(CAP_OS_NAME, "linux");
    }

    public List<ComponentInfo> installed = new ArrayList<>();
    public Map<String, String> graalInfo = new HashMap<>(DEFAULT_GRAAL_INFO);
    public Map<String, Collection<String>> replacedFiles = new HashMap<>();
    public Map<String, Collection<String>> updatedReplacedFiles = new HashMap<>();
    public List<ComponentInfo> savedInfos = new ArrayList<>();

    @Override
    public void deleteComponent(String id) throws IOException {
        // no op
        for (Iterator<ComponentInfo> it = installed.iterator(); it.hasNext();) {
            ComponentInfo info = it.next();
            if (id.equals(info.getId())) {
                it.remove();
            }
        }
    }

    @Override
    public Set<String> listComponentIDs() throws IOException {
        return installed.stream().map((ci) -> ci.getId()).collect(Collectors.toSet());
    }

    @Override
    public ComponentInfo loadComponentFiles(ComponentInfo ci) throws IOException {
        return ci;
    }

    @Override
    public ComponentInfo loadComponentMetadata(String tag) throws IOException {
        return installed.stream().filter((ci) -> ci.getId().equals(tag)).findFirst().orElse(null);
    }

    @Override
    public Map<String, String> loadGraalVersionInfo() {
        return graalInfo;
    }

    @Override
    public Map<String, Collection<String>> readReplacedFiles() throws IOException {
        return new HashMap<>(replacedFiles);
    }

    @Override
    public void saveComponent(ComponentInfo info) throws IOException {
        savedInfos.add(info);
    }

    @Override
    public void updateReplacedFiles(Map<String, Collection<String>> newReplacedFiles) throws IOException {
        updatedReplacedFiles = newReplacedFiles;
    }

}
