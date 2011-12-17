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
package test.com.sun.max.collect;

import java.util.*;

import com.sun.max.collect.*;
import com.sun.max.ide.*;

/**
 * Tests for {@link PoolBitSet}.
 */
public class PoolSetTest extends MaxTestCase {

    public PoolSetTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(PoolSetTest.class);
    }

    private static class TestElement implements PoolObject {

        private int serial;

        public TestElement(int n) {
            serial = n;
        }

        public int serial() {
            return serial;
        }

        @Override
        public String toString() {
            return String.valueOf(serial);
        }
    }

    public void test_emptyPoolSet() {
        final Pool<TestElement> emptyPool = new ArrayPool<TestElement>();
        final PoolSet<TestElement> poolSet = PoolSet.noneOf(emptyPool);
        assertSame(poolSet.pool(), emptyPool);
        assertEquals(poolSet.size(), 0);
        assertTrue(poolSet.isEmpty());
        poolSet.clear();
        poolSet.addAll();
        assertTrue(poolSet.isEmpty());
        final PoolSet<TestElement> clone = poolSet.clone();
        assertSame(clone.pool(), emptyPool);
        assertEquals(clone.size(), 0);
        assertTrue(clone.isEmpty());
    }

    private int nElems;
    private TestElement[] elems;
    private Pool<TestElement> pool;

    private void foreachPool(Runnable runnable) {
        for (int numElems : new int[] {0, 1, 63, 64, 65, 127, 128, 129, 1000}) {
            this.nElems = numElems;
            elems = new TestElement[numElems];
            for (int i = 0; i < numElems; i++) {
                elems[i] = new TestElement(i);
            }
            pool = new ArrayPool<TestElement>(elems);
            runnable.run();
        }
    }

    private void check_poolSet(PoolSet<TestElement> poolSet, int n) {
        assertEquals(poolSet.size(), n);
        for (int i = 0; i < n; i++) {
            assertTrue(poolSet.contains(elems[i]));
        }
        for (int i = n; i < nElems; i++) {
            assertFalse(poolSet.contains(elems[i]));
        }
    }

    public void test_poolBitSet() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                for (int i = 0; i < nElems; i++) {
                    check_poolSet(poolSet, i);
                    poolSet.add(elems[i]);
                }
                check_poolSet(poolSet, nElems);
            }
        });
    }

    public void test_remove() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                poolSet.addAll();
                assertEquals(poolSet.size(), nElems);
                for (int i = 0; i < nElems; i++) {
                    assertTrue(poolSet.contains(elems[i]));
                    poolSet.remove(elems[i]);
                    assertFalse(poolSet.contains(elems[i]));
                    assertEquals(poolSet.size(), nElems - i - 1);
                }
            }
        });
    }

    public void test_removeOne() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                poolSet.addAll();
                assertEquals(poolSet.size(), nElems);
                if (nElems > 0) {
                    final TestElement elem = poolSet.removeOne();
                    assertFalse(poolSet.contains(elem));
                    assertEquals(poolSet.size(), nElems - 1);
                } else {
                    try {
                        poolSet.removeOne();
                        fail();
                    } catch (NoSuchElementException noSuchElementException) {
                    }
                }
            }
        });
    }

    public void test_pool() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                assertSame(poolSet.pool(), pool);
            }
        });
    }

    public void test_clear() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                poolSet.addAll();
                poolSet.clear();
                check_poolSet(poolSet, 0);
                assertEquals(poolSet.size(), 0);
                for (int i = 0; i < nElems; i++) {
                    assertFalse(poolSet.contains(elems[i]));
                }
            }
        });
    }

    public void test_addAll() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet1 = PoolSet.noneOf(pool);
                poolSet1.addAll();
                check_poolSet(poolSet1, nElems);

                final PoolSet<TestElement> evenSet = PoolSet.noneOf(pool);
                for (int i = 0; i < nElems; i += 2) {
                    evenSet.add(elems[i]);
                }
                final PoolSet<TestElement> oddSet = PoolSet.noneOf(pool);
                for (int i = 1; i < nElems; i += 2) {
                    oddSet.add(elems[i]);
                }
                final PoolSet<TestElement> poolSet2 = PoolSet.noneOf(pool);
                poolSet2.or(oddSet);
                assertEquals(poolSet2.size(), nElems / 2);
                poolSet2.or(evenSet);
                assertEquals(poolSet2.size(), nElems);
                check_poolSet(poolSet2, nElems);
            }
        });
    }

    public void test_and() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                poolSet.addAll();
                final PoolSet<TestElement> oddSet = PoolSet.noneOf(pool);
                for (int i = 1; i < nElems; i += 2) {
                    oddSet.add(elems[i]);
                }
                poolSet.and(oddSet);
                assertEquals(poolSet.size(), oddSet.size());
                for (TestElement elem : poolSet) {
                    assertTrue(oddSet.contains(elem));
                }
            }
        });
    }

    public void test_containsAll() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> emptyPoolSet = PoolSet.noneOf(pool);
                final PoolSet<TestElement> fullPoolSet = PoolSet.allOf(pool);
                if (nElems == 0) {
                    assertTrue(emptyPoolSet.containsAll(fullPoolSet));
                    assertTrue(fullPoolSet.containsAll(emptyPoolSet));
                } else {
                    assertFalse(emptyPoolSet.containsAll(fullPoolSet));
                    assertTrue(fullPoolSet.containsAll(emptyPoolSet));
                }

                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                for (int i = 0; i < nElems; i++) {
                    poolSet.add(elems[i]);
                    assertTrue(poolSet.containsAll(emptyPoolSet));
                    assertTrue(fullPoolSet.containsAll(poolSet));
                    if (i == nElems - 1) {
                        assertTrue(poolSet.containsAll(fullPoolSet));
                    } else {
                        assertFalse(poolSet.containsAll(fullPoolSet));
                    }

                }
                poolSet.clear();
                for (int i = nElems - 1; i >= 0; i--) {
                    poolSet.add(elems[i]);
                    assertTrue(poolSet.containsAll(emptyPoolSet));
                    assertTrue(fullPoolSet.containsAll(poolSet));
                    if (i == 0) {
                        assertTrue(poolSet.containsAll(fullPoolSet));
                    } else {
                        assertFalse(poolSet.containsAll(fullPoolSet));
                    }

                }
            }
        });
    }

    public void test_clone() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> evenSet = PoolSet.noneOf(pool);
                for (int i = 0; i < nElems; i += 2) {
                    evenSet.add(elems[i]);
                }
                final PoolSet<TestElement> clone = evenSet.clone();
                assertSame(clone.pool(), pool);
                assertEquals(evenSet.size(), clone.size());
                for (TestElement elem : evenSet) {
                    assertTrue(clone.contains(elem));
                }
            }
        });
    }

    public void test_isEmpty() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                assertTrue(poolSet.isEmpty());
                if (nElems > 0) {
                    poolSet.add(elems[0]);
                    assertFalse(poolSet.isEmpty());
                    poolSet.addAll();
                    assertFalse(poolSet.isEmpty());
                    poolSet.clear();
                    assertTrue(poolSet.isEmpty());
                }
            }
        });
    }

    public void test_iterator() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                for (TestElement elem : poolSet) {
                    assertTrue(poolSet.contains(elem));
                }
                for (int i = 0; i < nElems; i++) {
                    poolSet.add(elems[i]);
                    for (TestElement elem : poolSet) {
                        assertTrue(poolSet.contains(elem));
                    }
                }
            }
        });
    }

    public void test_staticAddAll() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                PoolSet.addAll(poolSet, Arrays.asList(elems));
                check_poolSet(poolSet, nElems);
            }
        });
    }

    public void test_toArray() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolSet = PoolSet.noneOf(pool);
                poolSet.addAll();
                final TestElement[] array = poolSet.toArray(new TestElement[poolSet.size()]);
                assertTrue(Arrays.equals(array, elems));
            }
        });
    }

    public void test_allOf() {
        foreachPool(new Runnable() {
            public void run() {
                final PoolSet<TestElement> poolBitSet = PoolSet.allOf(pool);
                check_poolSet(poolBitSet, nElems);
            }
        });
    }

}
