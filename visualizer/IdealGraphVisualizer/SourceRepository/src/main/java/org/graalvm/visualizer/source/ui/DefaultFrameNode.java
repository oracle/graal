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

package org.graalvm.visualizer.source.ui;

import org.graalvm.visualizer.source.FileRegistry;
import org.graalvm.visualizer.source.Language;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.NodeStack;
import org.graalvm.visualizer.source.impl.ui.LocationOpener;
import org.netbeans.api.actions.Openable;
import org.netbeans.api.actions.Viewable;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;

import java.awt.Image;
import java.util.function.Function;

/**
 * @author sdedic
 */
@NbBundle.Messages({
        "# Adds decoration to unresolved locations",
        "# {0} - the formatted name",
        "HtmlFormat_Unresolved=<font color=\"#aaaaaa\">{0}</font>",
        "# {0} - filename",
        "# {1} - path + filename",
        "# {2} - line number",
        "Format_NodeName={0}:{1}"
})
public final class DefaultFrameNode extends AbstractNode {
    private final NodeStack.Frame frame;
    private final Function<Location, String> htmlNameSupplier;
    private final Node langNode;

    private FileRegistry.FileRegistryListener regListener;
    private FileRegistry.FileRegistryListener wL;

    public DefaultFrameNode(NodeStack.Frame frame, Function<Location, String> htmlNameSupplier, Children children, Lookup lookup) {
        super(children, createLookup(lookup));
        this.frame = frame;
        this.htmlNameSupplier = htmlNameSupplier;
        PL lkp = (PL) getLookup();
        InstanceContent ic = lkp.content;
        ic.add(frame);
        ic.add(frame.getLocation());
        ic.add(frame.getNode());
        ic.add(this);
        ic.add(Openable.class, lkp);
        ic.add(Viewable.class, lkp);
        setName(Bundle.Format_NodeName(frame.getFileName(), null, frame.getLine()));

        Language lng = Language.getRegistry().findLanguageByMime(frame.getStack().getMime());
        langNode = lng.getLookup().lookup(Node.class);
    }

    @Override
    public Image getIcon(int type) {
        if (langNode != null) {
            return langNode.getIcon(type);
        } else {
            return super.getIcon(type);
        }
    }

    private static PL createLookup(Lookup additional) {
        InstanceContent content = new InstanceContent();
        AbstractLookup inner = new AbstractLookup(content);

        return new PL(content, new Lookup[]{additional, inner});
    }

    public NodeStack.Frame getFrame() {
        return frame;
    }

    public Location getLocation() {
        return frame.getLocation();
    }

    private static class PL extends ProxyLookup implements InstanceContent.Convertor {
        InstanceContent content;

        public PL(InstanceContent content, Lookup[] lkps) {
            super(lkps);
            this.content = content;
        }

        @Override
        public Object convert(Object t) {
            return new LocationOpener(lookup(Location.class));
        }

        @Override
        public Class type(Object t) {
            return (Class) t;
        }

        @Override
        public String id(Object t) {
            return ((Class) t).getName();
        }

        @Override
        public String displayName(Object t) {
            return id(t);
        }
    }

    class L implements FileRegistry.FileRegistryListener {
        @Override
        public void filesResolved(FileRegistry.FileRegistryEvent ev) {
            if (!ev.getResolvedKeys().contains(getLocation().getFile())) {
                return;
            }
            ev.getRegistry().removeFileRegistryListener(wL);
            fireDisplayNameChange(null, null);
        }
    }

    @Override
    public String getHtmlDisplayName() {
        String n = htmlNameSupplier.apply(getLocation());
        if (getLocation().isResolved()) {
            return n;
        } else {
            if (regListener == null) {
                regListener = new L();
                // attach a listener, revalidate on change:
                FileRegistry reg = FileRegistry.getInstance();
                reg.addFileRegistryListener(wL = WeakListeners.create(FileRegistry.FileRegistryListener.class, regListener, reg));
            }
            return Bundle.HtmlFormat_Unresolved(n);
        }
    }
}
