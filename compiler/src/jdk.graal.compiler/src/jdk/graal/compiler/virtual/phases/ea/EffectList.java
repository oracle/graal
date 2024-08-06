/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.virtual.phases.ea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.OptimizationLog;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * An {@link EffectList} can be used to maintain a list of {@link Effect}s and backtrack to a
 * previous state by truncating the list.
 */
public class EffectList implements Iterable<EffectList.Effect> {

    public abstract static class Effect {
        private final String name;

        public Effect(String name) {
            this.name = name;
        }

        boolean isVisible() {
            return true;
        }

        boolean isCfgKill() {
            return false;
        }

        abstract void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes);

        @Override
        public final String toString() {
            StringBuilder str = new StringBuilder();
            str.append(name).append(" [");
            format(str);
            str.append(']');
            return str.toString();
        }

        abstract void format(StringBuilder str);

        void format(StringBuilder str, String[] valueNames, Object[] values) {
            boolean first = true;
            for (int i = 0; i < valueNames.length; i++) {
                str.append(first ? "" : ", ").append(valueNames[i]).append("=").append(formatObject(values[i]));
                first = false;
            }
        }

        private static String formatObject(Object object) {
            if (object != null && Object[].class.isAssignableFrom(object.getClass())) {
                return Arrays.toString((Object[]) object);
            }
            return String.valueOf(object);
        }
    }

    public abstract static class SimpleEffect extends Effect {
        public SimpleEffect(String name) {
            super(name);
        }

        @Override
        void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
            apply(graph);
        }

        abstract void apply(StructuredGraph graph);
    }

    private static final Effect[] EMPTY_ARRAY = new Effect[0];

    private final DebugContext debug;
    private Effect[] effects = EMPTY_ARRAY;
    private int size;

    public EffectList(DebugContext debug) {
        this.debug = debug;
    }

    private void enlarge(int elements) {
        int length = effects.length;
        if (size + elements > length) {
            while (size + elements > length) {
                length = Math.max(length * 2, 4);
            }
            effects = Arrays.copyOf(effects, length);
        }
    }

    public void add(SimpleEffect effect) {
        add((Effect) effect);
    }

    public void add(Effect effect) {
        assert effect != null;
        enlarge(1);
        effects[size++] = effect;
    }

    /**
     * Adds an effect that reports an optimization happened if the optimization log is enabled.
     *
     * @param optimizationLog the optimization log
     * @param logConsumer the function that reports a transformation
     */
    public void addLog(OptimizationLog optimizationLog, Consumer<OptimizationLog> logConsumer) {
        if (!optimizationLog.isAnyLoggingEnabled()) {
            return;
        }
        add(new SimpleEffect("optimization log") {
            @Override
            public boolean isVisible() {
                return false;
            }

            @Override
            public void apply(StructuredGraph graph) {
                logConsumer.accept(optimizationLog);
            }

            @Override
            public void format(StringBuilder str) {

            }
        });
    }

    public void addAll(EffectList list) {
        enlarge(list.size);
        System.arraycopy(list.effects, 0, effects, size, list.size);
        size += list.size;
    }

    public void insertAll(EffectList list, int position) {
        assert position >= 0 && position <= size : Assertions.errorMessageContext("position", position, "size", size);
        enlarge(list.size);
        System.arraycopy(effects, position, effects, position + list.size, size - position);
        System.arraycopy(list.effects, 0, effects, position, list.size);
        size += list.size;
    }

    public int size() {
        return size;
    }

    @Override
    public Iterator<Effect> iterator() {
        return new Iterator<>() {

            int index;
            final int listSize = EffectList.this.size;

            @Override
            public boolean hasNext() {
                return index < listSize;
            }

            @Override
            public Effect next() {
                return effects[index++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public Effect get(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return effects[index];
    }

    public void clear() {
        size = 0;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void apply(StructuredGraph graph, ArrayList<Node> obsoleteNodes, boolean cfgKills) {
        boolean message = false;
        for (int i = 0; i < size(); i++) {
            Effect effect = effects[i];
            if (effect.isCfgKill() == cfgKills) {
                if (!message) {
                    message = true;
                    debug.log(cfgKills ? " ==== cfg kill effects" : " ==== effects");
                }
                try {
                    effect.apply(graph, obsoleteNodes);
                } catch (Throwable t) {
                    throw new GraalError(t).addContext("effect", effect);
                }
                if (effect.isVisible() && debug.isLogEnabled()) {
                    debug.log("    %s", effect);
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            Effect effect = get(i);
            if (effect.isVisible()) {
                str.append(effects[i]);
                str.append('\n');
            }
        }
        return str.toString();
    }
}
