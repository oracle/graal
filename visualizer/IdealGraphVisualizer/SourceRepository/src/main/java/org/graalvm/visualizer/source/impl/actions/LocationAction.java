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

package org.graalvm.visualizer.source.impl.actions;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.source.FileRegistry;
import org.graalvm.visualizer.source.FileRegistry.FileRegistryListener;
import org.openide.nodes.Node;
import org.openide.util.ContextAwareAction;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.Pair;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Base class for actions, which need graph+node for operation. Each action can override
 * {@link #computeEnabled} to determine precise enablement status. The default implementation
 * returns true if at least one InputNode within a graph is selected.
 */
public abstract class LocationAction extends AbstractAction implements ContextAwareAction {
    private Lookup context;
    private final L list = new L();
    private final FileRegistry registry;
    private Lookup.Result nbNodeResult;
    private Lookup.Result inputNodeResult;

    protected LocationAction() {
        this(Utilities.actionsGlobalContext());
    }

    protected LocationAction(String name) {
        this(name, Utilities.actionsGlobalContext());
    }

    protected LocationAction(String name, Icon icon) {
        this(name, icon, Utilities.actionsGlobalContext());
    }

    protected LocationAction(Lookup context) {
        super();
        this.context = context;
        this.registry = FileRegistry.getInstance();
        init();
    }

    protected LocationAction(String name, Lookup context) {
        super(name);
        this.context = context;
        this.registry = FileRegistry.getInstance();
        init();
    }

    protected LocationAction(String name, Icon icon, Lookup context) {
        super(name, icon);
        this.context = context;
        this.registry = FileRegistry.getInstance();
    }

    private void init() {
        if (context != null) {
            nbNodeResult = context.lookupResult(Node.class);
            inputNodeResult = context.lookupResult(InputNode.class);
            nbNodeResult.addLookupListener(WeakListeners.create(LookupListener.class, list, nbNodeResult));
            inputNodeResult.addLookupListener(WeakListeners.create(LookupListener.class, list, inputNodeResult));
        }
        registry.addFileRegistryListener(WeakListeners.create(FileRegistryListener.class, list, registry));
    }

    protected final Lookup context() {
        return context == null ? Lookup.EMPTY : context;
    }

    protected final Node[] activeNodes() {
        Collection<? extends Node> nbNodes = nbNodeResult.allInstances();
        return nbNodes.toArray(new Node[nbNodes.size()]);

    }

    private Pair<InputGraph, InputNode[]> data() {
        Collection<InputNode> gNodes = inputNodeResult.allInstances();
        InputGraph g = null;
        if (!gNodes.isEmpty()) {
            g = context.lookup(InputGraph.class);
        } else {
            Collection<? extends Node> nbNodes = nbNodeResult.allInstances();
            if (nbNodes.isEmpty()) {
                return null;
            }

            for (Node n : nbNodes) {
                InputNode in = n.getLookup().lookup(InputNode.class);
                if (in != null) {
                    if (gNodes.isEmpty()) {
                        gNodes = new LinkedHashSet<>();
                    }
                    InputGraph ig = n.getLookup().lookup(InputGraph.class);
                    if (g != null && ig != g) {
                        if (!acceptDifferentGraphs()) {
                            return null;
                        }
                    }
                    if (g == null) {
                        g = ig;
                    }
                    gNodes.add(in);
                }
            }
        }
        return Pair.of(g, gNodes.toArray(new InputNode[gNodes.size()]));
    }

    private void refreshEnabled() {
        Collection<InputNode> gNodes = inputNodeResult.allInstances();
        Pair<InputGraph, InputNode[]> p = data();
        setEnabled(p != null && computeEnabled(p.first(), p.second()));
    }

    protected boolean acceptDifferentGraphs() {
        return false;
    }

    protected boolean computeEnabled(InputGraph graph, InputNode[] nodes) {
        return nodes.length > 0;
    }

    @Override
    public final void actionPerformed(ActionEvent e) {
        if (context == null) {
            Object source = e.getSource();
            /*
	    Kind of a hack, but I can't think up a better way to get a current
	    terminal when Action is performed with the shortcut. Thus it's context
	    independent and we need to find an active Terminal. Getting it from TC
	    won't work because we can have multiple active Terminals on screen
	    (debugger console and terminalemulator, for example).
	    Luckily, we can get some useful information from the caller`s source
             */
            if (source instanceof Component) {
                Container container = SwingUtilities.getAncestorOfClass(Lookup.Provider.class, (Component) source);
                if (container != null && container instanceof Lookup.Provider) {
                    this.context = ((Lookup.Provider) container).getLookup();
                }
            }
        }
        Pair<InputGraph, InputNode[]> data = data();
        if (data == null || !computeEnabled(data.first(), data.second())) {
            setEnabled(false);
            Toolkit.getDefaultToolkit().beep();
        } else {
            actionPerformed(e, data.first(), data.second());
        }
    }

    protected abstract void actionPerformed(ActionEvent e, InputGraph g, InputNode[] nodes);

    class L implements LookupListener, FileRegistryListener {
        @Override
        public void resultChanged(LookupEvent ev) {
            refreshEnabled();
        }

        @Override
        public void filesResolved(FileRegistry.FileRegistryEvent ev) {
            refreshEnabled();
        }
    }

    private Icon smallIcon;

    private Icon getIcon() {
        if (smallIcon != null) {
            return smallIcon;
        }
        String r = iconResource();
        if (r == null) {
            return null;
        }
        smallIcon = ImageUtilities.loadImageIcon(r, true);
        return smallIcon;
    }

    protected String iconResource() {
        return null;
    }

    public String getKey() {
        return (String) super.getValue(NAME);
    }

    public String getName() {
        return (String) super.getValue(NAME);
    }

    @Override
    public Object getValue(String key) {
        switch (key) {
            case SMALL_ICON:
                return getIcon();
            case SHORT_DESCRIPTION:
                return getName();
            case NAME:
                return getName();
        }
        return super.getValue(key);
    }


}
