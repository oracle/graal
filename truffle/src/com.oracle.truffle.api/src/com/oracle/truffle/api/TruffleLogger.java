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
package com.oracle.truffle.api;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TruffleLogger extends Logger {

    private TruffleLogger(final String loggerName, final String resourceBundleName) {
        super(loggerName, resourceBundleName);
    }

    public static Logger getLogger(final String languageId, final Class<?> forClass) {
        return getLogger(languageId, forClass.getName(), null);
    }

    public static Logger getLogger(final String languageId, final String loggerName) {
        return getLogger(languageId, loggerName, null);
    }

    public static Logger getLogger(final String languageId, final String loggerName, final String resourceBundleName) {
        final String globalLoggerId = languageId + '.' + loggerName;
        final LoggerCache logManager = LoggerCache.getInstance();
        return logManager.getOrCreateLogger(globalLoggerId, resourceBundleName);
    }

    @Override
    public void addHandler(Handler handler) {
        if (handler != TruffleLanguage.AccessAPI.engineAccess().getPolyglotLogHandler()) {
            super.addHandler(handler);
        }
    }

    @Override
    public void removeHandler(Handler handler) {
        if (handler != TruffleLanguage.AccessAPI.engineAccess().getPolyglotLogHandler()) {
            super.removeHandler(handler);
        }
    }

    private void addHandlerInternal(final Handler handler) {
        super.addHandler(handler);
    }

    static final class LoggerCache {
        private static final int MAX_CLEANED_REFS = 100;
        private static final LoggerCache INSTANCE = new LoggerCache();
        private final ReferenceQueue<Logger> loggersRefQueue = new ReferenceQueue<>();
        private final Map<String,LoggerRef> loggers = new HashMap<>();
        private final LoggerNode root;
        private final Map<Object,Map<String,Level>> levelsByContext;
        private Map<String,Level> effectiveLevels;

        private LoggerCache() {
            root = new LoggerNode(null, new LoggerRef(Logger.getLogger(""), ""));
            this.levelsByContext = new WeakHashMap<>();
            this.effectiveLevels = Collections.emptyMap();
        }

        void onContextCreated(final Object context, final Map<String,Level> addedLevels) {
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

        synchronized void onContextClosed(final Object context) {
            final Map<String,Level> levels = levelsByContext.remove(context);
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

        private Logger getOrCreateLogger(final String loggerName, final String resourceBundleName) {
            Logger found = getLogger(loggerName);
            if (found == null) {
                for (final TruffleLogger logger = new TruffleLogger(loggerName, resourceBundleName); found == null;) {
                    if (addLogger(logger)) {
                        logger.addHandlerInternal(TruffleLanguage.AccessAPI.engineAccess().getPolyglotLogHandler());
                        found = logger;
                        break;
                    }
                    found = getLogger(loggerName);
                }
            }
            return found;
        }

        private synchronized Logger getLogger(final String loggerName) {
            Logger res = null;
            final LoggerRef ref = loggers.get(loggerName);
            if (ref != null) {
                res = ref.get();
                if (res == null) {
                    ref.close();
                }
            }
            return res;
        }

        private boolean addLogger(final Logger logger) {
            final String loggerName = logger.getName();
            if (loggerName == null) {
                throw new NullPointerException("Logger must have non null name.");
            }
            synchronized (this) {
                cleanupFreedReferences();
                LoggerRef ref = loggers.get(loggerName);
                if (ref != null) {
                    final Logger loggerInstance = ref.get();
                    if (loggerInstance != null) {
                        return false;
                    } else {
                        ref.close();
                    }
                }
                ref = new LoggerRef(logger, loggerName);
                loggers.put(loggerName, ref);
                setLoggerLevel(logger, loggerName);
                createParents(loggerName);
                final LoggerNode node = findLoggerNode(loggerName);
                node.setLoggerRef(ref);
                final Logger parentLogger = node.findParentLogger();
                if (parentLogger != null) {
                    logger.setParent(parentLogger);
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
                final Logger logger = getLogger(loggerName);
                if (logger != null) {
                    logger.setLevel(null);
                }
            }
            for (String loggerName : changedLoogers) {
                final Logger logger = getLogger(loggerName);
                if (logger != null) {
                    setLoggerLevel(logger, loggerName);
                    createParents(loggerName);
                } else {
                    getOrCreateLogger(loggerName, null);
                }
            }
        }

        private void setLoggerLevel(final Logger logger, final String loggerName) {
            final Level l = getEffectiveLevel(loggerName);
            if (l != null) {
                logger.setLevel(l);
            }
        }

        private void createParents(final String loggerName) {
            int index = -1;
            for (int start = 1;;start = index+1) {
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

        private void cleanupFreedReferences() {
            for (int i=0; i< MAX_CLEANED_REFS; i++) {
                final LoggerRef ref = (LoggerRef) loggersRefQueue.poll();
                if (ref == null) {
                    break;
                }
                ref.close();
            }
        }

        private LoggerNode findLoggerNode(String loggerName) {
            LoggerNode node = root;
            while(!loggerName.isEmpty()) {
                int index = loggerName.indexOf('.');
                String currentNameCompoment;
                if (index > 0) {
                    currentNameCompoment = loggerName.substring(0, index);
                    loggerName = loggerName.substring(index+1);
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

        private static Map<String,Level> computeEffectiveLevels(
                final Map<String,Level> currentEffectiveLevels,
                final Set<String> removed,
                final Map<String,Level> added,
                final Map<Object,Map<String,Level>> levelsByContext,
                final Collection<? super String> removedLevels,
                final Collection<? super String> changedLevels) {
            final Map<String,Level> newEffectiveLevels = new HashMap<>(currentEffectiveLevels);
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
            for (Map.Entry<String,Level> addedLevel : added.entrySet()) {
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

        private static Level findMinLevel(final String loggerName, final Map<Object,Map<String,Level>> levelsByContext) {
            Level min = null;
            for (Map<String,Level> levels : levelsByContext.values()) {
                Level level = levels.get(loggerName);
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

        private final class LoggerRef extends WeakReference<Logger> {
            private final String loggerName;
            private LoggerNode node;
            private boolean closed;

            LoggerRef(final Logger logger, final String loggerName) {
                super(logger, loggersRefQueue);
                this.loggerName = loggerName;
            }

            void setNode(final LoggerNode node) {
                assert Thread.holdsLock(LoggerCache.this);
                this.node = node;
            }

            void close() {
                synchronized(LoggerCache.this) {
                    if (closed) {
                        return;
                    }
                    closed = true;
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
            Map<String,LoggerNode> children;
            private LoggerRef loggerRef;

            LoggerNode(final LoggerNode parent, final LoggerRef loggerRef) {
                this.parent = parent;
                this.loggerRef = loggerRef;
            }

            void setLoggerRef(final LoggerRef loggerRef) {
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
                    Logger childLogger = child.loggerRef != null ? child.loggerRef.get() : null;
                    if (childLogger != null) {
                        childLogger.setParent(parentLogger);
                    } else {
                        child.updateChildParentsImpl(parentLogger);
                    }
                }
            }
        }
    }
}
