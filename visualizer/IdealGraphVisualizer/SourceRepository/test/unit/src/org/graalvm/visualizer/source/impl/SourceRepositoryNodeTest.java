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

package org.graalvm.visualizer.source.impl;

import org.graalvm.visualizer.source.SourcesRoot;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import org.netbeans.junit.RandomlyFails;

/**
 *
 * @author sdedic
 */
public class SourceRepositoryNodeTest extends SourceRepositoryTestBase {
    public SourceRepositoryNodeTest(String name) {
        super(name);
    }
    
    Node repoNode;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        waitForEDT(true);
        edtSynced = false;
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    public void testDefaultGroupNotShown() throws Exception {
        repoNode = new SourceRepositoryNode(repo, false);
        // repository is empty, no nodes should be shown
        assertEquals(0, repoNode.getChildren().getNodes().length);
    }
    
    private boolean edtSynced = false;
    
    private void waitForEDT() {
        waitForEDT(false);
    }
    
    private void waitForEDT(boolean force) {
        if (force || !edtSynced) {
            Children.MUTEX.writeAccess(() -> { edtSynced = true; });
        }
    }
    
    private void assertFileRootNode(Node n, FileRoot r) {
        waitForEDT();
        assertSame(r, n.getLookup().lookup(FileRoot.class));
        assertEquals(r.getDisplayName(), n.getDisplayName());
        assertSame(URLMapper.findFileObject(r.getLocation()), n.getLookup().lookup(FileObject.class));
    }
    
    private void assertGroupNode(Node n, FileGroup g) {
        waitForEDT();
        assertSame(g, n.getLookup().lookup(FileGroup.class));
        assertEquals(g.getDisplayName(), n.getDisplayName());
        SourcesRoot sr = n.getLookup().lookup(SourcesRoot.class);
        assertNotNull(sr);
        assertEquals(g.getURI(), sr.getURI());
        assertEquals(g.getSourcePath(), sr.getSourcePath());
    }
    
    public void testDirectShowDefaultContents() throws Exception {
        FileRoot r = repo.addLocation(fsrc1, getName(), null);
        repoNode = new SourceRepositoryNode(repo, false);
        waitForEDT();
        assertEquals(1, repoNode.getChildren().getNodes().length);
        Node n = repoNode.getChildren().getNodes()[0];
        assertFileRootNode(n, r);
    }
    
    public void testAddRemoveRootDisplayedInRoot() throws Exception {
        repoNode = new SourceRepositoryNode(repo, false);
        // repository is empty, no nodes should be shown
        assertEquals(0, repoNode.getChildren().getNodes().length);
        FileRoot r = repo.addLocation(fsrc1, getName() + "1", null);
        
        Children.MUTEX.writeAccess(() -> {
            assertEquals(1, repoNode.getChildren().getNodes().length);
            Node n = repoNode.getChildren().getNodes()[0];
            assertFileRootNode(n, r);
        });
        FileRoot r2 = repo.addLocation(fsrc2, getName() + "2", null);
        Children.MUTEX.writeAccess(() -> {
            Node[] ch = repoNode.getChildren().getNodes();
            assertEquals(2, ch.length);
            assertFileRootNode(ch[0], r);
            assertFileRootNode(ch[1], r2);
        });
        r.discard();
        Children.MUTEX.writeAccess(() -> {
            Node[] ch = repoNode.getChildren().getNodes();
            assertEquals(1, ch.length);
            assertFileRootNode(ch[0], r2);
        });
    }
    
    public void testFileRootDisplayChange() throws Exception {
        FileRoot r = repo.addLocation(fsrc1, getName(), null);
        repoNode = new SourceRepositoryNode(repo, false);
        
        waitForEDT();
        assertEquals(1, repoNode.getChildren().getNodes().length);
        Node n = repoNode.getChildren().getNodes()[0];
        
        assertEquals(r.getDisplayName(), n.getDisplayName());
        r.setDisplayName(getName() + "Changed");
        assertEquals(r.getDisplayName(), n.getDisplayName());
    }
    
    public void testGroupsShown() throws Exception {
        FileGroup g = repo.createGroup(getName());
        repoNode = new SourceRepositoryNode(repo, false);
        waitForEDT();
        assertEquals(1, repoNode.getChildren().getNodes().length);
        assertGroupNode(repoNode.getChildren().getNodes()[0], g);
    }
    
    public void testAddRemoveGroupsVisible() throws Exception {
        repoNode = new SourceRepositoryNode(repo, false);

        FileGroup g = repo.createGroup(getName() + "-1");
        waitForEDT(true);
        assertEquals(1, repoNode.getChildren().getNodes().length);
        assertGroupNode(repoNode.getChildren().getNodes()[0], g);

        FileGroup g2 = repo.createGroup(getName() + "-2");
        waitForEDT(true);
        assertEquals(2, repoNode.getChildren().getNodes().length);
        assertGroupNode(repoNode.getChildren().getNodes()[0], g);
        assertGroupNode(repoNode.getChildren().getNodes()[1], g2);
        
        repo.deleteGroup(g);
        waitForEDT(true);

        assertEquals(1, repoNode.getChildren().getNodes().length);
        assertGroupNode(repoNode.getChildren().getNodes()[0], g2);
    }
    
    public void testGroupDisplayNameChanges() throws Exception {
        FileGroup g = repo.createGroup(getName() + "-1");
        FileGroup g2 = repo.createGroup(getName() + "-2");
        repoNode = new SourceRepositoryNode(repo, false);

        waitForEDT(true);
        assertEquals(2, repoNode.getChildren().getNodes().length);
        assertGroupNode(repoNode.getChildren().getNodes()[0], g);
        assertGroupNode(repoNode.getChildren().getNodes()[1], g2);
        
        g2.setDisplayName(getName() + "_First");
        waitForEDT(true);
        assertEquals(2, repoNode.getChildren().getNodes().length);
        assertGroupNode(repoNode.getChildren().getNodes()[0], g);
        assertGroupNode(repoNode.getChildren().getNodes()[1], g2);
    }
    
    public void testNodeRenamesFileRoot() throws Exception {
        FileRoot r = repo.addLocation(fsrc1, getName(), null);
        repoNode = new SourceRepositoryNode(repo, false);
        waitForEDT();

        Node n = repoNode.getChildren().getNodes()[0];
        n.setName(getName() + "Bubak");
        assertEquals(n.getDisplayName(), r.getDisplayName());
    }
    
    public void testNodeRenamesGroup() throws Exception {
        repoNode = new SourceRepositoryNode(repo, false);
        FileGroup g = repo.createGroup(getName());
        waitForEDT();
        
        Node n = repoNode.getChildren().getNodes()[0];
        n.setName(getName() + "Bubak");
        
        assertEquals(n.getDisplayName(), g.getDisplayName());
    }
    
    public void testDestroyNodeRemovesFile() throws Exception {
        FileRoot r = repo.addLocation(fsrc1, getName() + "-1", null);
        FileRoot r2 = repo.addLocation(fsrc2, getName() + "-2", null);
        assertTrue(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc1));
        
        repoNode = new SourceRepositoryNode(repo, false);
        waitForEDT();
        Node[] nodes = repoNode.getChildren().getNodes();
        nodes[0].destroy();
        assertFalse(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc1));
        assertTrue(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc2));
    }
    
    public void testDestroyNodeRemovesGroup() throws Exception {
        FileGroup g = repo.createGroup(getName() + "-1");
        FileGroup g2 = repo.createGroup(getName() + "-2");
        FileRoot r = repo.addLocation(fsrc1, getName() + "-Loc", g);
        
        repoNode = new SourceRepositoryNode(repo, false);
        assertTrue(g.getSourcePath().contains(fsrc1));
        assertTrue(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc1));
        
        waitForEDT();
        Node[] nodes = repoNode.getChildren().getNodes();
        nodes[0].destroy();
        
        assertFalse(repo.getGroups().contains(g));
        assertFalse(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc1));
    }
    
    public void testAddRemoveVisibleInGroup() throws Exception {
        FileGroup g = repo.createGroup(getName());

        repoNode = new SourceRepositoryNode(repo, false);

        waitForEDT();
        Node gNode = repoNode.getChildren().getNodes()[0];
        assertGroupNode(gNode, g);
        // repository is empty, no nodes should be shown
        assertEquals(0, gNode.getChildren().getNodes().length);
        FileRoot r = repo.addLocation(fsrc1, getName() + "-Source1", g);
        
        Children.MUTEX.writeAccess(() -> {
            assertEquals(1, gNode.getChildren().getNodes().length);
            Node n = gNode.getChildren().getNodes()[0];
            assertFileRootNode(n, r);
        });
        FileRoot r2 = repo.addLocation(fsrc2, getName() + "-Source2", g);
        Children.MUTEX.writeAccess(() -> {
            Node[] ch = gNode.getChildren().getNodes();
            assertEquals(2, ch.length);
            assertFileRootNode(ch[0], r);
            assertFileRootNode(ch[1], r2);
        });
        r.discard();
        Children.MUTEX.writeAccess(() -> {
            Node[] ch = gNode.getChildren().getNodes();
            assertEquals(1, ch.length);
            assertFileRootNode(ch[0], r2);
        });
    }

    public void testGroupChildrenGCed() throws Exception {
        FileGroup g = repo.createGroup(getName());

        repoNode = new SourceRepositoryNode(repo, false);

        waitForEDT();
        Node gNode = repoNode.getChildren().getNodes()[0];
        assertGroupNode(gNode, g);
        FileRoot r = repo.addLocation(fsrc1, getName() + "-Source1", g);
        Children ch = gNode.getChildren();
        assertEquals(1, ch.getNodes().length);
        
        Reference<Children> wCh = new WeakReference<>(ch);
        Reference<Node> fn = new WeakReference<>(ch.getNodes()[0]);
        gNode = null;
        ch = null;
        assertGC("Children must be freed if unused", wCh, Collections.singleton(repoNode));
        assertGC("File node must be freed", fn, Collections.singleton(repoNode));
    }
}
