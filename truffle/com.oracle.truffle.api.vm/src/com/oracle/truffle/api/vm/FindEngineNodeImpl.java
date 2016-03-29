package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.FindEngineNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;

final class FindEngineNodeImpl extends FindEngineNode {
    @Child private AbstractEngineNode engineNode;

    FindEngineNodeImpl() {
        engineNode = new OneEngineNode();
        adoptChildren();
    }

    synchronized void registerEngine(Thread usableIn, PolyglotEngine engine) {
        engineNode.registerEngine(usableIn, engine);
    }

    @SuppressWarnings("unused")
    void unregisterEngine(Thread wasUsedIn, PolyglotEngine engine) {
    }

    synchronized void disposeEngine(Thread wasUsedIn, PolyglotEngine engine) {
        engineNode.disposeEngine(wasUsedIn, engine);
    }

    @Override
    protected Object findEngine() {
        return findEngine(Thread.currentThread());
    }

    synchronized Object findEngine(Thread thread) {
        return engineNode.findEngine(thread);
    }

    static abstract class AbstractEngineNode extends Node {
        abstract AbstractEngineNode registerEngine(Thread usableIn, PolyglotEngine engine);

        abstract void disposeEngine(Thread wasUsedIn, PolyglotEngine engine);

        abstract PolyglotEngine findEngine(Thread usedBy);

        static IllegalStateException alreadyUsed(PolyglotEngine engine) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("There already is an engine " + engine);
        }
    }

    private static final class OneEngineNode extends AbstractEngineNode {
        @CompilationFinal private PolyglotEngine one;
        @CompilationFinal private Assumption oneEngine;
        @CompilationFinal private Thread oneThread;

        @Override
        AbstractEngineNode registerEngine(Thread usableIn, PolyglotEngine engine) {
            if (one == engine) {
                return this;
            }
            if (oneEngine == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                oneEngine = Truffle.getRuntime().createAssumption("The One Engine");
                one = engine;
                oneThread = usableIn;
            } else {
                if (usableIn == oneThread) {
                    throw alreadyUsed(engine);
                }
                oneEngine.invalidate();
                replace(new MultiThreadedEngineNode()).
                                registerEngine(oneThread, one).
                                registerEngine(usableIn, engine);
            }
            return this;
        }

        @Override
        void disposeEngine(Thread wasUsedIn, PolyglotEngine engine) {
            replace(new OneAtATimeEngineNode());
        }

        @Override
        PolyglotEngine findEngine(Thread usedBy) {
            return one;
        }
    }

    private static final class OneAtATimeEngineNode extends AbstractEngineNode {
        private PolyglotEngine current;
        private Thread currentThread;

        @Override
        AbstractEngineNode registerEngine(Thread usableIn, PolyglotEngine engine) {
            if (current == engine) {
                return this;
            }
            if (current != null) {
                if (usableIn == currentThread) {
                    throw alreadyUsed(current);
                }
                replace(new MultiThreadedEngineNode()).
                                registerEngine(currentThread, current).
                                registerEngine(usableIn, engine);
            } else {
                current = engine;
                currentThread = usableIn;
            }
            return this;
        }

        @Override
        void disposeEngine(Thread wasUsedIn, PolyglotEngine engine) {
            if (current == engine) {
                current = null;
                currentThread = null;
            }
        }

        @Override
        PolyglotEngine findEngine(Thread usedBy) {
            return currentThread == usedBy ? current : null;
        }
    }

    private static final class MultiThreadedEngineNode extends AbstractEngineNode {
        private final DynamicObject engines;

        public MultiThreadedEngineNode() {
            this.engines = Layout.createLayout().createShape(new ObjectType()).newInstance();
        }

        @Override
        AbstractEngineNode registerEngine(Thread usableIn, PolyglotEngine engine) {
            Object previous = engines.get(usableIn);
            if (previous == engine) {
                return this;
            }
            if (previous != null) {
                throw alreadyUsed((PolyglotEngine) previous);
            }
            engines.define(usableIn, engine);
            return this;
        }

        @Override
        void disposeEngine(Thread wasUsedIn, PolyglotEngine engine) {
            if (engines.get(wasUsedIn) == engine) {
                engines.set(wasUsedIn, null);
            }
        }

        @Override
        PolyglotEngine findEngine(Thread usedBy) {
            return (PolyglotEngine) engines.get(usedBy);
        }
    }
}
