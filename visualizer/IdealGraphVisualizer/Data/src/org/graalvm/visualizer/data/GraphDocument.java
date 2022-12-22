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
package org.graalvm.visualizer.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;

public class GraphDocument extends Properties.Entity implements ChangedEventProvider<GraphDocument>, Folder, FolderElement, 
        DumpedElement, Properties.MutableOwner<GraphDocument> {
    private final Executor evRunner;
    private final List<FolderElement> elements;
    private final ChangedEvent<GraphDocument> changedEvent;
    private final ChangedEvent<GraphDocument> propertyEvent;
    private final List<DataCollectionListener> dataListeners = new ArrayList<>();
    
    private boolean modified;
    
    // @GuardedBy(this)
    private volatile boolean trackModified;
    
    // @GuardedBy(this)
    private DocumentlLock lock;
    
    private Object documentId;
    
    /**
     * Mutable properties from individual objects in the document. The objects may be
     * garbage - collected eventually, so their changed Properties would be lost.
     * This field preserves the changed values,
     */
    private Map<Object, Properties> mutableProperties = new HashMap<>();
    
    private static final Executor DEFAULT_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            SwingUtilities.invokeLater(command);
        }
    };
    
    public GraphDocument() {
        this(null);
    }
    
    public GraphDocument(Executor eventRunner) {
        elements = new ArrayList<>();
        evRunner = eventRunner == null ? DEFAULT_EXECUTOR : eventRunner;
        changedEvent = new ThreadedChange<>(this, evRunner);
        propertyEvent = new ThreadedChange<>(this, evRunner);
    }

    @Override
    public Object getID() {
        return documentId;
    }

    public void setDocumentId(Object documentId) {
        this.documentId = documentId;
    }
    
    public void clear() {
        synchronized (this) {
            elements.clear();
        }
        changedEvent.fire();
    }

    @Override
    public Properties writableProperties() {
        return getProperties();
    }

    @Override
    public void updateProperties(Properties props) {
        // document properties are always read-write, so no need to replace.
        setModified(true);
        propertyEvent.fire();
    }
    
    /**
     * Determines if the document has been changed.
     * @return true, if the document has been changed.
     */
    public boolean isModified() {
        return modified;
    }
    
    /**
     * Marks the document as (un)modified. If the status changes, the {@link #getPropertyChangedEvent()}
     * will be also fired.
     * @param mod true if the document should become modified.
     */
    protected void setModified(boolean mod) {
        synchronized (this) {
            if (mod == modified) {
                return;
            }
            modified = mod;
        }
        propertyEvent.fire();
    }
    
    public Properties getModifiedProperties(FolderElement item) {
        synchronized (this) {
            return mutableProperties.get(item);
        }
    }
    
    protected void notifyPropertiesChanged(FolderElement item, Properties writableProps) {
        synchronized (this) {
            Properties oldValue = mutableProperties.put(item, writableProps);
            if (oldValue != null && oldValue != writableProps) {
                throw new IllegalArgumentException();
            }
        }
        setModified(true);
    }

    @Override
    public final ChangedEvent<GraphDocument> getChangedEvent() {
        return changedEvent;
    }
    
    public void addGraphDocument(GraphDocument document) {
        if (document == this) {
            return;
        }
        changedEvent.beginAtomic();
        try {
            List<? extends FolderElement> otherElems = document.getElements();
            synchronized (this) {
                for (FolderElement e : otherElems) {
                    e.setParent(this);
                    this.addElement(e);
                }
            }
            document.clear();
        } finally {
            changedEvent.endAtomic();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("GraphDocument: ").append(getProperties().toString()).append(" \n\n");
        for (FolderElement g : getElements()) {
            sb.append(g.toString());
            sb.append("\n\n");
        }

        return sb.toString();
    }

    public int getSize() {
        synchronized (this) {
            return elements.size();
        }
    }
    
    @Override
    public List<? extends FolderElement> getElements() {
        synchronized (this) {
            return Collections.unmodifiableList(new ArrayList<>(elements));
        }
    }

    @Override
    public void removeElement(FolderElement element) {
        synchronized (this) {
            if (!elements.remove(element)) {
                return;
            }
        }
        changedEvent.fire();
        fireDataRemoved(Collections.singleton(element));
    }

    @Override
    public void addElement(FolderElement element) {
        boolean propertiesChanged = false;
        
        synchronized (this) {
            // note that element.setParent(this) is not called; this is because
            // the GraphDocument is used to group selected, possibly unrelated Groups/Graphs when saving or processing in bulk.
            // setParent must be called explicitly
            elements.add(element);
            String n  = getName();
            if ((n == null || n.isEmpty()) && (element instanceof Properties.Entity)) {
                final Iterator<Property> it = ((Properties.Entity)element).getProperties().iterator();
                while (it.hasNext()) {
                    Property p = it.next();
                    if (!KnownPropertyNames.PROPNAME_NAME.equals(p.getName())) {
                        getProperties().setProperty(p.getName(), p.getValue());
                    }
                }
                propertiesChanged = true;
            }
        }
        changedEvent.fire();
        if (propertiesChanged) {
            propertyEvent.fire();
        }
        fireDataAdded(Collections.singletonList(element));
    }
    
    void fireDataAdded(List<? extends FolderElement> elements) {
        DataCollectionEvent ev = new DataCollectionEvent(this, elements, true, this);
        fireDataCollectionEvent(ev, DataCollectionListener::dataLoaded);
    }
    
    public Executor getEventRunner() {
        return evRunner;
    }
    
    static Executor getEventRunner(FolderElement e) {
        GraphDocument d = e.getOwner();
        if (d == null) {
            return DEFAULT_EXECUTOR;
        } else {
            return d.evRunner;
        }
    }
    
    public static Executor defaultExecutor() {
        return DEFAULT_EXECUTOR;
    }
    
    public void addDataCollectionListener(DataCollectionListener l) {
        synchronized (this) {
            dataListeners.add(l);
        }
    }

    public void removeDataCollectionListener(DataCollectionListener l) {
        synchronized (this) {
            dataListeners.remove(l);
        }
    }
    
    private void fireDataCollectionEvent(DataCollectionEvent ev, BiConsumer<DataCollectionListener, DataCollectionEvent> fn) {
        DataCollectionListener[] ll = null;
        if (trackModified) {
            setModified(true);
        }
        synchronized (this) {
            if (dataListeners.isEmpty()) {
                return;
            }
            ll = dataListeners.toArray(new DataCollectionListener[dataListeners.size()]);
        }
        for (DataCollectionListener l : ll) {
            fn.accept(l, ev);
        }
    }
    
    void fireDataRemoved(Collection<? extends FolderElement> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        fireDataCollectionEvent(new DataCollectionEvent(this, data, this), DataCollectionListener::dataRemoved);
        setModified(true);
    }

    @Override
    public boolean isParentOf(FolderElement child) {
        return child.getOwner() == this;
    }

    @Override
    public Folder getParent() {
        return null;
    }

    @Override
    public String getName() {
        return getProperties().getString(KnownPropertyNames.PROPNAME_NAME, null);
    }

    @Override
    public void setParent(Folder parent) {
        if (parent != null) {
            throw new IllegalStateException("Unsupported");
        }
    }

    @Override
    public ChangedEvent<GraphDocument> getPropertyChangedEvent() {
        return propertyEvent;
    }
    
    /**
     * Informs that the document is locked for modifications. The exception carries
     * a description of the lock owner operation, and can be used to break the lock.
     */
    public static final class LockedException extends RuntimeException {
        private DocumentlLock theLock;

        public LockedException(String message, DocumentlLock theLock) {
            super(message);
            this.theLock = theLock;
        }
        
        public String getLockingOperation() {
            return theLock.getOperationLabel();
        }
        
        public boolean tryBreakLock() {
            return theLock.cancel();
        }
    }

    /**
     * Locks document for writing for one operation. The lock is NOT reentrant.
     * @param operationLabel user-readable label of the lock owner.
     * @param cancel optional; callback to cancel the operation
     * @return lock instance
     */
    public synchronized DocumentlLock writeLock(String operationLabel, Callable<Boolean> cancel) {
        if (lock != null) {
            throw new LockedException("Document is locked by " + lock.getOperationLabel(), lock);
        }
        return lock = new DocumentlLock(operationLabel, cancel);
    }
    
    /**
     * Represents a lock on the document. During the lock, no other modifications are
     * permitted (throw a {@link LockedException). Lock owner may disable modification tracking,
     * i.e. during load from file, the GraphDocument does not become modified.
     * <p>
     * Modification tracking is restored when the lock is released.
     */
    public final class DocumentlLock implements AutoCloseable {
        private final String operationLabel;
        private final Callable<Boolean> cancelFunc;
        private boolean unlocked;
        
        public DocumentlLock(String operationLabel, Callable<Boolean> cancelFunc) {
            this.operationLabel = operationLabel;
            this.cancelFunc = cancelFunc;
        }
        
        public String getOperationLabel() {
            return operationLabel;
        }

        @Override
        public void close() {
            unlock();
        }
        
        /**
         * Unlocks the document. Unlock may be called multiple times after a
         * successful unlock.
         */
        public void unlock() {
            synchronized (GraphDocument.this) {
                if (unlocked) {
                    return;
                }
                if (lock != this) {
                    throw new IllegalArgumentException("Not locked."); 
                }
                unlocked = true;
                trackModified = false;
                lock = null;
            }
        }
        
        /**
         * Disables modified status tracking for add/remove operations.
         * @param track enable/disable tracking
         */
        public void trackModifications(boolean track) {
            if (unlocked) {
                throw new IllegalStateException("No longer locked.");
            }
            trackModified = track;
        }
        
        boolean cancel() {
            synchronized (GraphDocument.this) {
                if (lock != this) {
                    return true;
                }
            }
            try {
                boolean status = cancelFunc.call();
                if (!status) {
                    return false;
                }
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            synchronized (GraphDocument.this) {
                return lock != this;
            }
        }
    }
}
