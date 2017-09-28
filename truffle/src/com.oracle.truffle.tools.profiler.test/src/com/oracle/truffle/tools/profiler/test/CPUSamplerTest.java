/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler.test;

import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotRuntime;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CallTreeNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Iterator;

public class CPUSamplerTest extends AbstractProfilerTest {

    private CPUSampler sampler;

    @Before
    public void setupSampler() {
        for (PolyglotRuntime.Instrument instrument : engine.getRuntime().getInstruments().values()) {
            sampler = instrument.lookup(CPUSampler.class);
            if (sampler != null) {
                break;
            }
        }
        Assert.assertNotNull(sampler);
    }

    @Test
    public void testCollectingAndHasData() {

        sampler.setCollecting(true);

        Assert.assertEquals(0, sampler.getTotalSamples());
        Assert.assertTrue(sampler.isCollecting());
        Assert.assertFalse(sampler.hasData());

        for (int i = 0; i < 1000; i++) {
            execute(defaultSource);
        }

        Assert.assertNotEquals(0, sampler.getTotalSamples());
        Assert.assertTrue(sampler.isCollecting());
        Assert.assertTrue(sampler.hasData());

        sampler.setCollecting(false);

        Assert.assertFalse(sampler.isCollecting());
        Assert.assertTrue(sampler.hasData());

        sampler.clearData();
        Assert.assertFalse(sampler.isCollecting());
        Assert.assertEquals(0, sampler.getTotalSamples());

        Assert.assertFalse(sampler.hasData());
    }

    Source defaultSourceForSampling = makeSource("ROOT(" +
                    "DEFINE(foo,ROOT(WASTE_TIME))," +
                    "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                    "DEFINE(baz,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar)))))," +
                    "CALL(baz),CALL(bar)" +
                    ")");

    @Test
    public void testCorrectRootStructure() {

        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setCollecting(true);
        for (int i = 0; i < 10_000; i++) {
            execute(defaultSourceForSampling);
        }

        Collection<CallTreeNode<CPUSampler.HitCounts>> children = sampler.getRootNodes();
        Assert.assertEquals(1, children.size());
        CallTreeNode<CPUSampler.HitCounts> program = children.iterator().next();
        Assert.assertEquals("", program.getRootName());

        children = program.getChildren();
        Assert.assertEquals(2, children.size());
        Iterator<CallTreeNode<CPUSampler.HitCounts>> iterator = children.iterator();
        CallTreeNode<CPUSampler.HitCounts> baz = iterator.next();
        if (!"baz".equals(baz.getRootName())) {
            baz = iterator.next();
        }
        Assert.assertEquals("baz", baz.getRootName());

        children = baz.getChildren();
        Assert.assertEquals(1, children.size());
        CallTreeNode<CPUSampler.HitCounts> bar = children.iterator().next();
        Assert.assertEquals("bar", bar.getRootName());

        children = bar.getChildren();
        Assert.assertEquals(1, children.size());
        CallTreeNode<CPUSampler.HitCounts> foo = children.iterator().next();
        Assert.assertEquals("foo", foo.getRootName());

        children = foo.getChildren();
        Assert.assertTrue(children.size() == 0);
    }

    final Source defaultRecursiveSourceForSampling = makeSource("ROOT(" +
                    "DEFINE(foo,ROOT(BLOCK(WASTE_TIME,RECURSIVE_CALL(foo))))," +
                    "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                    "CALL(bar)" +
                    ")");

    @Test
    public void testCorrectRootStructureRecursive() {

        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setCollecting(true);
        for (int i = 0; i < 10_000; i++) {
            execute(defaultRecursiveSourceForSampling);
        }

        Collection<CallTreeNode<CPUSampler.HitCounts>> children = sampler.getRootNodes();
        Assert.assertEquals(1, children.size());
        CallTreeNode<CPUSampler.HitCounts> program = children.iterator().next();
        Assert.assertEquals("", program.getRootName());

        children = program.getChildren();
        Assert.assertEquals(1, children.size());
        CallTreeNode<CPUSampler.HitCounts> bar = children.iterator().next();
        Assert.assertEquals("bar", bar.getRootName());

        children = bar.getChildren();
        Assert.assertEquals(1, children.size());
        CallTreeNode<CPUSampler.HitCounts> foo = children.iterator().next();
        Assert.assertEquals("foo", foo.getRootName());

        // RECURSIVE_CALL does recutions to depth 10
        for (int i = 0; i < 10; i++) {
            children = foo.getChildren();
            Assert.assertEquals(1, children.size());
            foo = children.iterator().next();
            Assert.assertEquals("foo", foo.getRootName());
        }

        children = foo.getChildren();
        Assert.assertTrue(children.size() == 0);
    }

    @Test
    public void testCorrectCallStructure() {
        sampler.setFilter(NO_INTERNAL_CALL_TAG_FILTER);
        sampler.setCollecting(true);
        for (int i = 0; i < 10_000; i++) {
            execute(defaultSourceForSampling);
        }
        Collection<CallTreeNode<CPUSampler.HitCounts>> children = sampler.getRootNodes();
        Assert.assertEquals(2, children.size());

        Iterator<CallTreeNode<CPUSampler.HitCounts>> iterator = children.iterator();
        CallTreeNode<CPUSampler.HitCounts> call = iterator.next();
        if (!"CALL(baz)".equals(call.getSourceSection().getCharacters().toString())) {
            call = iterator.next();
        }
        Assert.assertTrue(call.getTags().contains(StandardTags.CallTag.class));
        children = call.getChildren();
        Assert.assertEquals(1, children.size());

        call = children.iterator().next();
        Assert.assertTrue(call.getTags().contains(StandardTags.CallTag.class));
        children = call.getChildren();
        Assert.assertEquals(1, children.size());

        call = children.iterator().next();
        Assert.assertTrue(call.getTags().contains(StandardTags.CallTag.class));
        children = call.getChildren();
        Assert.assertEquals(0, children.size());
    }

    @Test
    public void testCorrectCallStructureRecursive() {
        sampler.setFilter(NO_INTERNAL_CALL_TAG_FILTER);
        sampler.setCollecting(true);
        for (int i = 0; i < 10_000; i++) {
            execute(defaultRecursiveSourceForSampling);
        }
        Collection<CallTreeNode<CPUSampler.HitCounts>> children = sampler.getRootNodes();

        // 10 recursive calls, base foo and bar = 12
        for (int i = 0; i < 12; i++) {
            Assert.assertEquals(1, children.size());
            CallTreeNode<CPUSampler.HitCounts> call = children.iterator().next();
            Assert.assertTrue(call.getTags().contains(StandardTags.CallTag.class));
            children = call.getChildren();
        }
    }

    @Test
    public void testShadowStackOverflows() {
        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setStackLimit(2);
        sampler.setCollecting(true);
        for (int i = 0; i < 10_000; i++) {
            execute(defaultRecursiveSourceForSampling);
        }
        Assert.assertTrue(sampler.hasStackOverflowed());
    }
}
