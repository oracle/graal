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

package org.graalvm.visualizer.source.java.ui;

import org.graalvm.visualizer.source.FileRegistry;
import org.graalvm.visualizer.source.FileRegistry.FileRegistryListener;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.NodeStack;
import org.graalvm.visualizer.source.java.impl.JavaLocationInfo;
import org.graalvm.visualizer.source.spi.LocationServices;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 */
@NbBundle.Messages({
        "# adds HTML tags to method name",
        "# {0} - class name",
        "# {1} - metod name",
        "# {2} - line number",
        "HtmlFormat_MethodName={0}.<b>{1}()</b>:{2}",
        "# adds HTML tags to method name",
        "# {0} - class name",
        "# {1} - metod name",
        "HtmlFormat_MethodNameUnknownLine={0}.<b>{1}()</b> (line unknown)",
        "# Adds decoration to unresolved locations",
        "# {0} - the formatted name",
        "HtmlFormat_Unresolved=<font color=\"#aaaaaa\">{0}</font>"
})
@MimeRegistration(mimeType = "text/x-java", service = LocationServices.class)
public class JavaStackFramePresenter implements LocationServices {
    @Override
    public Lookup createLookup(NodeStack.Frame frame) {
        Location loc = frame.getLocation();
        JavaLocationInfo javaInfo = loc.getSpecificInfo(JavaLocationInfo.class);
        if (javaInfo == null) {
            return null;
        }
        String className = javaInfo.getClassName();
        int classDot = className.lastIndexOf('$');
        if (classDot == -1) {
            classDot = className.lastIndexOf('.');
        }
        String simpleName = className.substring(classDot + 1);
        InstanceContent content = new InstanceContent();
        Lookup nodeLookup = new AbstractLookup(content);
        AbstractNode presentNode = new AbstractNode(Children.LEAF, nodeLookup) {
            private FileRegistryListener regListener;
            private FileRegistryListener wL;

            class L implements FileRegistryListener {
                @Override
                public void filesResolved(FileRegistry.FileRegistryEvent ev) {
                    if (!ev.getResolvedKeys().contains(loc.getFile())) {
                        return;
                    }
                    ev.getRegistry().removeFileRegistryListener(wL);
                    fireDisplayNameChange(null, null);
                }
            }

            @Override
            public String getHtmlDisplayName() {
                String n;
                if (loc.getLine() > 0) {
                    n = Bundle.HtmlFormat_MethodName(simpleName, javaInfo.getMethodName(), loc.getLine());
                } else {
                    n = Bundle.HtmlFormat_MethodNameUnknownLine(simpleName, javaInfo.getMethodName());
                }
                if (javaInfo.isResolved()) {
                    return n;
                } else {
                    if (regListener == null) {
                        regListener = new L();
                        // attach a listener, revalidate on change:
                        FileRegistry reg = FileRegistry.getInstance();
                        reg.addFileRegistryListener(wL = WeakListeners.create(FileRegistryListener.class, regListener, reg));
                    }
                    return Bundle.HtmlFormat_Unresolved(n);
                }
            }

        };
        if (javaInfo.getInvokedMethod() != null) {
            presentNode.setIconBaseWithExtension("org/graalvm/visualizer/source/resources/methodCall.png"); // NOI18N
        } else if (javaInfo.getVariableName() != null) {
            presentNode.setIconBaseWithExtension("org/graalvm/visualizer/source/resources/fieldRef.png"); // NOI18N
        } else {
            presentNode.setIconBaseWithExtension("org/graalvm/visualizer/source/resources/executable.png"); // NOI18N
        }
        LocationOpener opener = new LocationOpener(loc);
        content.add(frame.getNode());
        content.add(opener);
        content.add(presentNode);
        content.add(loc);
        content.add(frame);
        content.add(javaInfo);
        return nodeLookup;
    }
}
