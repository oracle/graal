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

import org.graalvm.visualizer.source.spi.LocationResolver;
import org.netbeans.api.java.platform.JavaPlatform;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author sdedic
 */
@ServiceProvider(service = LocationResolver.class)
public class PlatformLocationResolver implements LocationResolver {
    public static volatile boolean enabled;
    public static Collection<String> enabledPackages = new HashSet<>();

    JavaPlatform platform = JavaPlatform.getDefault();

    @Override
    public FileObject resolve(FileKey l) {
        if (!enabled) {
            return null;
        }
        synchronized (enabledPackages) {
            if (!enabledPackages.isEmpty()) {
                boolean ok = false;
                int x = l.getFileSpec().lastIndexOf('.');
                for (String p : enabledPackages) {
                    if (p.endsWith("/")) {
                        if (p.length() == x && l.getFileSpec().startsWith(p)) {
                            ok = true;
                            break;
                        }
                    } else {
                        if (l.getFileSpec().startsWith(p + "/")) {
                            ok = true;
                            break;
                        }
                    }
                }
                if (!ok) {
                    return null;
                }
            }
        }
        return platform.getSourceFolders().findResource(l.getFileSpec());
    }

    static void enablePackage(String s, boolean enable) {
        synchronized (enabledPackages) {
            if (enable) {
                enabledPackages.add(s);
            } else {
                enabledPackages.remove(s);
            }
        }
    }

    static void reset() {
        synchronized (enabledPackages) {
            enabledPackages.clear();
            enabled = false;
        }
    }
}
