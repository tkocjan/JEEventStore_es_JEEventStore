/*
 * Copyright (c) 2013 Red Rainbow IT Solutions GmbH, Germany
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.jeeventstore.notifier;

import org.jeeventstore.EventStoreCommitListener;
import org.jeeventstore.EventStoreCommitNotifier;
import org.jeeventstore.EventStoreCommitNotification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ejb.Lock;
import javax.ejb.LockType;
import org.jeeventstore.ChangeSet;

/**
 * Base implementation implementing common functionality of commit notifiers.
 */
public abstract class AbstractEventStoreCommitNotifier 
        implements EventStoreCommitNotifier {
    
    private final Map<String, List<EventStoreCommitListener>> listeners = new HashMap<>();

    @Override
    @Lock(LockType.WRITE)
    public void addListener(String bucketId, EventStoreCommitListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("listener must not be null");
        List<EventStoreCommitListener> list = listFor(bucketId, true);
        if (list.contains(listener))
            throw new IllegalStateException("Listener already listening.");
        list.add(listener);
    }

    @Override
    @Lock(LockType.WRITE)
    public void removeListener(String bucketId, EventStoreCommitListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("listener must not be null");
        List<EventStoreCommitListener> list = listFor(bucketId, false);
        if (list == null || !list.contains(listener))
            throw new IllegalStateException("Listener not found.");
        list.remove(listener);
    }

    /**
     * Perform the actual notification for a given notification.
     * Iterates over all registered listeners and lets them receive the given
     * notification.
     * @param notification The notification that the listeners shall receive.
     */
    protected void performNotification(EventStoreCommitNotification notification) {
        List<EventStoreCommitListener> list = listFor(notification.changes().bucketId(), false);
        if (list == null)
            return;
        for (EventStoreCommitListener l : list)
            l.receive(notification);
    }

    /**
     * Perform the actual notification for a given change set.
     * Iterates over all registered listeners and lets them receive a
     * notification for the given change set.
     * @param changeSet The change set that listeners shall be notified of.
     */
    protected void performNotification(ChangeSet changeSet) {
        EventStoreCommitNotification notification = new DefaultCommitNotification(changeSet);
        this.performNotification(notification);
    }

    protected List<EventStoreCommitListener> listFor(String bucketId, boolean create) {
        List<EventStoreCommitListener> list = listeners.get(bucketId);
        if (list == null && create) {
            list = new ArrayList<>();
            listeners.put(bucketId, list);
        }
        return list;
    }

}
