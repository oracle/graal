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
package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import java.io.Closeable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

final class PolyglotLogger extends Logger {

    private static final String ROOT_NAME = "";
    private static final int MAX_CLEANED_REFS = 100;
    private static final int OFF_VALUE = Level.OFF.intValue();
    private static final int DEFAULT_VALUE = Level.INFO.intValue();
    private static final ReferenceQueue<Logger> loggersRefQueue = new ReferenceQueue<>();
    private static final Object childrenLock = new Object();

    @CompilerDirectives.CompilationFinal private volatile int levelNum;
    @CompilerDirectives.CompilationFinal private volatile Assumption levelNumStable;
    private volatile Level levelObj;
    private volatile Logger parent;
    private Collection<ChildLoggerRef> children;

    private PolyglotLogger(final String loggerName, final String resourceBundleName) {
        super(loggerName, resourceBundleName);
        levelNum = DEFAULT_VALUE;
        levelNumStable = Truffle.getRuntime().createAssumption("Log Level Value stable for: " + loggerName);
    }

    private PolyglotLogger() {
        this(ROOT_NAME, null);
        addHandlerInternal(ForwardingHandler.INSTANCE);
        setParentInternal(Logger.getLogger(ROOT_NAME));
    }

    @Override
    public void addHandler(Handler handler) {
        if (handler != ForwardingHandler.INSTANCE) {
            super.addHandler(handler);
        }
    }

    @Override
    public void removeHandler(Handler handler) {
        if (handler != ForwardingHandler.INSTANCE) {
            super.removeHandler(handler);
        }
    }

    @Override
    public boolean isLoggable(Level level) {
        int value = getLevelNum();
        if (level.intValue() < value || value == OFF_VALUE) {
            return false;
        }
        final PolyglotContextImpl currentContext = PolyglotContextImpl.current();
        if (currentContext == null) {
            return false;
        }
        return isLoggableSlowPath(currentContext, level);
    }

    @Override
    public Level getLevel() {
        return levelObj;
    }

    @Override
    public void setLevel(final Level level) {
        throw new SecurityException("Reconfiguration of a PolyglotLogger is not allowed.");
    }

    @Override
    public void setUseParentHandlers(final boolean useParentHandlers) {
        throw new SecurityException("Reconfiguration of a PolyglotLogger is not allowed.");
    }

    @Override
    public void setFilter(Filter newFilter) throws SecurityException {
        throw new SecurityException("Reconfiguration of a PolyglotLogger is not allowed.");
    }

    @Override
    public Logger getParent() {
        return parent;
    }

    @Override
    public void setParent(final Logger newParent) {
        throw new SecurityException("Reconfiguration of a PolyglotLogger is not allowed.");
    }

    @Override
    public void log(final LogRecord record) {
        if (!isLoggable(record.getLevel())) {
            return;
        }
        logSlowPath(record);
    }

    @CompilerDirectives.TruffleBoundary
    private boolean isLoggableSlowPath(final PolyglotContextImpl context, Level level) {
        return LoggerCache.getInstance().isLoggable(getName(), context, level);
    }

    @CompilerDirectives.TruffleBoundary
    private void logSlowPath(final LogRecord record) {
        final Filter filter = getFilter();
        if (filter != null && !filter.isLoggable(record)) {
            return;
        }

        for (Logger current = this; current != null; current = current.getParent()) {
            for (Handler handler : current.getHandlers()) {
                handler.publish(record);
            }
            if (!current.getUseParentHandlers()) {
                break;
            }
        }
    }

    private void removeChild(final ChildLoggerRef child) {
        synchronized (childrenLock) {
            if (children != null) {
                for (Iterator<ChildLoggerRef> it = children.iterator(); it.hasNext();) {
                    if (it.next() == child) {
                        it.remove();
                        return;
                    }
                }
            }
        }
    }

    private void updateLevelNum() {
        int value;
        if (levelObj != null) {
            value = levelObj.intValue();
            if (parent != null) {
                value = Math.min(value, getParentLevelNum());
            }
        } else if (parent != null) {
            value = getParentLevelNum();
        } else {
            value = DEFAULT_VALUE;
        }
        setLevelNum(value);
        if (children != null) {
            for (ChildLoggerRef ref : children) {
                final PolyglotLogger logger = ref.get();
                if (logger != null) {
                    logger.updateLevelNum();
                }
            }
        }
    }

    private int getParentLevelNum() {
        if (parent.getClass() == PolyglotLogger.class) {
            return ((PolyglotLogger) parent).getLevelNum();
        } else {
            final Level level = parent.getLevel();
            return level != null ? level.intValue() : DEFAULT_VALUE;
        }
    }

    private int getLevelNum() {
        if (!levelNumStable.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return levelNum;
    }

    private boolean setLevelNum(final int value) {
        if (this.levelNum != value) {
            this.levelNum = value;
            final Assumption currentAssumtion = levelNumStable;
            levelNumStable = Truffle.getRuntime().createAssumption("Log Level Value stable for: " + getName());
            currentAssumtion.invalidate();
            return true;
        }
        return false;
    }

    private void setLevelInternal(final Level level) {
        synchronized (childrenLock) {
            this.levelObj = level;
            updateLevelNum();
        }
    }

    private void addHandlerInternal(final Handler handler) {
        super.addHandler(handler);
    }

    private void setParentInternal(final Logger newParent) {
        Objects.requireNonNull(newParent, "Parent must be non null.");
        synchronized (childrenLock) {
            ChildLoggerRef found = null;
            if (parent != null && parent.getClass() == PolyglotLogger.class) {
                final PolyglotLogger polyglotParent = (PolyglotLogger) parent;
                for (Iterator<ChildLoggerRef> it = polyglotParent.children.iterator(); it.hasNext();) {
                    final ChildLoggerRef childRef = it.next();
                    final PolyglotLogger childLogger = childRef.get();
                    if (childLogger == this) {
                        found = childRef;
                        it.remove();
                        break;
                    }
                }
            }
            this.parent = newParent;
            if (parent.getClass() == PolyglotLogger.class) {
                if (found == null) {
                    found = new ChildLoggerRef(this);
                }
                final PolyglotLogger polyglotParent = (PolyglotLogger) parent;
                found.setParent(polyglotParent);
                if (polyglotParent.children == null) {
                    polyglotParent.children = new ArrayList<>(2);
                }
                polyglotParent.children.add(found);
            }
            updateLevelNum();
        }
    }

    private static void cleanupFreedReferences() {
        for (int i = 0; i < MAX_CLEANED_REFS; i++) {
            final AbstractLoggerRef ref = (AbstractLoggerRef) loggersRefQueue.poll();
            if (ref == null) {
                break;
            }
            ref.close();
        }
    }

    private abstract static class AbstractLoggerRef extends WeakReference<PolyglotLogger> implements Closeable {
        private final AtomicBoolean closed;

        AbstractLoggerRef(final PolyglotLogger logger) {
            super(logger, loggersRefQueue);
            this.closed = new AtomicBoolean();
        }

        @Override
        public abstract void close();

        boolean shouldClose() {
            return !closed.getAndSet(true);
        }
    }

    private static final class ChildLoggerRef extends AbstractLoggerRef {

        private volatile Reference<PolyglotLogger> parent;

        ChildLoggerRef(final PolyglotLogger logger) {
            super(logger);
        }

        void setParent(PolyglotLogger parent) {
            this.parent = new WeakReference<>(parent);
        }

        @Override
        public void close() {
            if (shouldClose()) {
                final Reference<PolyglotLogger> p = parent;
                if (p != null) {
                    PolyglotLogger parentLogger = p.get();
                    if (parentLogger != null) {
                        parentLogger.removeChild(this);
                    }
                    parent = null;
                }
            }
        }
    }

    static final class LoggerCache {
        private static final LoggerCache INSTANCE = new LoggerCache();
        private final PolyglotLogger polyglotRootLogger;
        private final Map<String, NamedLoggerRef> loggers;
        private final LoggerNode root;
        private final Map<PolyglotContextImpl, Map<String, Level>> levelsByContext;
        private Map<String, Level> effectiveLevels;

        private LoggerCache() {
            this.polyglotRootLogger = new PolyglotLogger();
            this.loggers = new HashMap<>();
            this.loggers.put(ROOT_NAME, new NamedLoggerRef(this.polyglotRootLogger, ROOT_NAME));
            this.root = new LoggerNode(null, new NamedLoggerRef(this.polyglotRootLogger, ROOT_NAME));
            this.levelsByContext = new WeakHashMap<>();
            this.effectiveLevels = Collections.emptyMap();
        }

        void addLogLevelsForContext(final PolyglotContextImpl context, final Map<String, Level> addedLevels) {
            if (!addedLevels.isEmpty()) {
                synchronized (this) {
                    levelsByContext.put(context, addedLevels);
                    final Collection<String> removedLevels = new HashSet<>();
                    final Collection<String> changedLevels = new HashSet<>();
                    effectiveLevels = computeEffectiveLevels(
                                    effectiveLevels,
                                    Collections.emptySet(),
                                    addedLevels,
                                    levelsByContext,
                                    removedLevels,
                                    changedLevels);
                    reconfigure(removedLevels, changedLevels);
                }
            }
        }

        synchronized void removeLogLevelsForContext(final PolyglotContextImpl context) {
            final Map<String, Level> levels = levelsByContext.remove(context);
            if (context.logHandler != null) {
                context.logHandler.close();
            }
            if (levels != null && !levels.isEmpty()) {
                final Collection<String> removedLevels = new HashSet<>();
                final Collection<String> changedLevels = new HashSet<>();
                effectiveLevels = computeEffectiveLevels(
                                effectiveLevels,
                                levels.keySet(),
                                Collections.emptyMap(),
                                levelsByContext,
                                removedLevels,
                                changedLevels);
                reconfigure(removedLevels, changedLevels);
            }
        }

        synchronized boolean isLoggable(final String loggerName, final PolyglotContextImpl currentContext, final Level level) {
            final Map<String, Level> current = levelsByContext.get(currentContext);
            if (current == null) {
                final int currentLevel = Math.min(polyglotRootLogger.getParent().getLevel().intValue(), DEFAULT_VALUE);
                return level.intValue() >= currentLevel && currentLevel != OFF_VALUE;
            }
            if (levelsByContext.size() == 1) {
                return true;
            }
            final int currentLevel = Math.min(computeLevel(loggerName, current), DEFAULT_VALUE);
            return level.intValue() >= currentLevel && currentLevel != OFF_VALUE;
        }

        private int computeLevel(String loggeName, final Map<String, Level> levels) {
            for (String currentName = loggeName; currentName != null;) {
                final Level l = levels.get(currentName);
                if (l != null) {
                    return l.intValue();
                }
                if (currentName.isEmpty()) {
                    currentName = null;
                } else {
                    final int index = currentName.lastIndexOf('.');
                    currentName = index == -1 ? "" : currentName.substring(0, index);
                }
            }
            return polyglotRootLogger.getParent().getLevel().intValue();
        }

        Logger getOrCreateLogger(final String loggerName, final String resourceBundleName) {
            Logger found = getLogger(loggerName);
            if (found == null) {
                for (final PolyglotLogger logger = new PolyglotLogger(loggerName, resourceBundleName); found == null;) {
                    if (addLogger(logger)) {
                        found = logger;
                        break;
                    }
                    found = getLogger(loggerName);
                }
            }
            return found;
        }

        private synchronized PolyglotLogger getLogger(final String loggerName) {
            PolyglotLogger res = null;
            final NamedLoggerRef ref = loggers.get(loggerName);
            if (ref != null) {
                res = ref.get();
                if (res == null) {
                    ref.close();
                }
            }
            return res;
        }

        private boolean addLogger(final PolyglotLogger logger) {
            final String loggerName = logger.getName();
            if (loggerName == null) {
                throw new NullPointerException("Logger must have non null name.");
            }
            synchronized (this) {
                cleanupFreedReferences();
                NamedLoggerRef ref = loggers.get(loggerName);
                if (ref != null) {
                    final Logger loggerInstance = ref.get();
                    if (loggerInstance != null) {
                        return false;
                    } else {
                        ref.close();
                    }
                }
                ref = new NamedLoggerRef(logger, loggerName);
                loggers.put(loggerName, ref);
                setLoggerLevel(logger, loggerName);
                createParents(loggerName);
                final LoggerNode node = findLoggerNode(loggerName);
                node.setLoggerRef(ref);
                final Logger parentLogger = node.findParentLogger();
                if (parentLogger != null) {
                    logger.setParentInternal(parentLogger);
                }
                node.updateChildParents();
                ref.setNode(node);
                return true;
            }
        }

        private Level getEffectiveLevel(final String loggerName) {
            return effectiveLevels.get(loggerName);
        }

        private void reconfigure(final Collection<? extends String> removedLoggers, final Collection<? extends String> changedLoogers) {
            for (String loggerName : removedLoggers) {
                final PolyglotLogger logger = getLogger(loggerName);
                if (logger != null) {
                    logger.setLevelInternal(null);
                }
            }
            for (String loggerName : changedLoogers) {
                final PolyglotLogger logger = getLogger(loggerName);
                if (logger != null) {
                    setLoggerLevel(logger, loggerName);
                    createParents(loggerName);
                } else {
                    getOrCreateLogger(loggerName, null);
                }
            }
        }

        private void setLoggerLevel(final PolyglotLogger logger, final String loggerName) {
            final Level l = getEffectiveLevel(loggerName);
            if (l != null) {
                logger.setLevelInternal(l);
            }
        }

        private void createParents(final String loggerName) {
            int index = -1;
            for (int start = 1;; start = index + 1) {
                index = loggerName.indexOf('.', start);
                if (index < 0) {
                    break;
                }
                final String parentName = loggerName.substring(0, index);
                if (getEffectiveLevel(parentName) != null) {
                    getOrCreateLogger(parentName, null);
                }
            }
        }

        private LoggerNode findLoggerNode(String loggerName) {
            LoggerNode node = root;
            while (!loggerName.isEmpty()) {
                int index = loggerName.indexOf('.');
                String currentNameCompoment;
                if (index > 0) {
                    currentNameCompoment = loggerName.substring(0, index);
                    loggerName = loggerName.substring(index + 1);
                } else {
                    currentNameCompoment = loggerName;
                    loggerName = "";
                }
                if (node.children == null) {
                    node.children = new HashMap<>();
                }
                LoggerNode child = node.children.get(currentNameCompoment);
                if (child == null) {
                    child = new LoggerNode(node, null);
                    node.children.put(currentNameCompoment, child);
                }
                node = child;
            }
            return node;
        }

        static LoggerCache getInstance() {
            return INSTANCE;
        }

        private static Map<String, Level> computeEffectiveLevels(
                        final Map<String, Level> currentEffectiveLevels,
                        final Set<String> removed,
                        final Map<String, Level> added,
                        final Map<PolyglotContextImpl, Map<String, Level>> levelsByContext,
                        final Collection<? super String> removedLevels,
                        final Collection<? super String> changedLevels) {
            final Map<String, Level> newEffectiveLevels = new HashMap<>(currentEffectiveLevels);
            for (String loggerName : removed) {
                final Level level = findMinLevel(loggerName, levelsByContext);
                if (level == null) {
                    newEffectiveLevels.remove(loggerName);
                    removedLevels.add(loggerName);
                } else {
                    final Level currentLevel = newEffectiveLevels.get(loggerName);
                    if (min(level, currentLevel) != currentLevel) {
                        newEffectiveLevels.put(loggerName, level);
                        changedLevels.add(loggerName);
                    }
                }
            }
            for (Map.Entry<String, Level> addedLevel : added.entrySet()) {
                final String loggerName = addedLevel.getKey();
                final Level loggerLevel = addedLevel.getValue();
                final Level currentLevel = newEffectiveLevels.get(loggerName);
                if (currentLevel == null || min(loggerLevel, currentLevel) != currentLevel) {
                    newEffectiveLevels.put(loggerName, loggerLevel);
                    changedLevels.add(loggerName);
                }
            }
            return newEffectiveLevels;
        }

        private static Level findMinLevel(final String loggerName, final Map<PolyglotContextImpl, Map<String, Level>> levelsByContext) {
            Level min = null;
            for (Map<String, Level> levels : levelsByContext.values()) {
                Level level = levels.get(loggerName);
                if (level == null) {
                    continue;
                }
                if (min == null) {
                    min = level;
                } else {
                    min = min(min, level);
                }
            }
            return min;
        }

        private static Level min(final Level l1, final Level l2) {
            return l1.intValue() < l2.intValue() ? l1 : l2;
        }

        private final class NamedLoggerRef extends AbstractLoggerRef {
            private final String loggerName;
            private LoggerNode node;

            NamedLoggerRef(final PolyglotLogger logger, final String loggerName) {
                super(logger);
                this.loggerName = loggerName;
            }

            void setNode(final LoggerNode node) {
                assert Thread.holdsLock(LoggerCache.this);
                this.node = node;
            }

            @Override
            public void close() {
                assert Thread.holdsLock(LoggerCache.this);
                if (shouldClose()) {
                    if (node != null) {
                        if (node.loggerRef == this) {
                            LoggerCache.this.loggers.remove(loggerName);
                            node.loggerRef = null;
                        }
                        node = null;
                    }
                }
            }
        }

        private final class LoggerNode {
            final LoggerNode parent;
            Map<String, LoggerNode> children;
            private NamedLoggerRef loggerRef;

            LoggerNode(final LoggerNode parent, final NamedLoggerRef loggerRef) {
                this.parent = parent;
                this.loggerRef = loggerRef;
            }

            void setLoggerRef(final NamedLoggerRef loggerRef) {
                this.loggerRef = loggerRef;
            }

            void updateChildParents() {
                final Logger logger = loggerRef.get();
                updateChildParentsImpl(logger);
            }

            Logger findParentLogger() {
                if (parent == null) {
                    return null;
                }
                Logger logger;
                if (parent.loggerRef != null && (logger = parent.loggerRef.get()) != null) {
                    return logger;
                }
                return parent.findParentLogger();
            }

            private void updateChildParentsImpl(final Logger parentLogger) {
                if (children == null || children.isEmpty()) {
                    return;
                }
                for (LoggerNode child : children.values()) {
                    PolyglotLogger childLogger = child.loggerRef != null ? child.loggerRef.get() : null;
                    if (childLogger != null) {
                        childLogger.setParentInternal(parentLogger);
                    } else {
                        child.updateChildParentsImpl(parentLogger);
                    }
                }
            }
        }
    }

    private static final class ForwardingHandler extends Handler {

        static final Handler INSTANCE = new ForwardingHandler();

        private ForwardingHandler() {
        }

        @Override
        public void publish(final LogRecord record) {
            final Handler handler = findDelegate();
            if (handler != null) {
                handler.publish(record);
            }
        }

        @Override
        public void flush() {
            final Handler handler = findDelegate();
            if (handler != null) {
                handler.flush();
            }
        }

        @Override
        public void close() throws SecurityException {
            final Handler handler = findDelegate();
            if (handler != null) {
                handler.close();
            }
        }

        private Handler findDelegate() {
            Handler result = null;
            final PolyglotContextImpl currentContext = PolyglotContextImpl.current();
            if (currentContext != null) {
                result = currentContext.logHandler;
            }
            return result;
        }
    }
}
