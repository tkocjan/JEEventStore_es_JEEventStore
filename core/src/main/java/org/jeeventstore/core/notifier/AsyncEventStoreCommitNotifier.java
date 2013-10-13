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

package org.jeeventstore.core.notifier;

import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import org.jeeventstore.core.ChangeSet;

/**
 * An asynchronous event store commit notifier.
 * Notifies registered listeners asynchronously in a different thread, i.e.,
 * the notifyListeners() method does not block.  The notifier begins to
 * notify listeners when the transaction surrounding the call to notifyListeners()
 * commits. If the surrounding transaction is rolled back, the listeners are 
 * not notified.  If the transaction from which notifyListeners() is called
 * is committed, the actual notification is scheduled and will run in a new
 * transaction.  The listeners are therefore notified in a new transaction
 * and cannot cause the commit to the event store to fail.
 * If one of the listeners causes the notification-transaction to fail,
 * the notification is re-tried automatically until it succeeds.
 * 
 * The notification order is currently in the order of registration, but this is
 * not a guarantee and might change in the future.
 * 
 * @author Alexander Langer
 */
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
public class AsyncEventStoreCommitNotifier
        extends AbstractEventStoreCommitNotifier 
        implements EventStoreCommitNotifier {

    private static final Logger log = Logger.getLogger(AsyncEventStoreCommitNotifier.class.getName());

    @Resource
    private TimerService timerService;

    private long retryInterval = 100;

    /**
     * Bean initialization, used to load configuration settings, if any.
     */
    @PostConstruct
    public void init() {
        // Test whether a custom retry interval has been configured for this
        try {
            InitialContext ic = new InitialContext();
            Long tmpRetry = (Long) ic.lookup("java:comp/env/retryInterval");
            if (tmpRetry != null)
                retryInterval = tmpRetry;
        } catch (NameNotFoundException e) {
            log.info("Error looking up retryInterval variable, falling back to default of " + retryInterval + "ms");
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Lock(LockType.READ)
    public void notifyListeners(ChangeSet changeSet) {
        /*
         * Schedule the actual notification into a new thread by creating
         * an create an EJB TimerService interval timer that tries to notify
         * all listeners until all listeners have been notified and then
         * cancels itself.  The timer is created transient (non-persistent),
         * since persistent timers do require a 2-phase-commit to the storage
         * engine(s) and are therefore very expensive.  We do not need persistent
         * timers by the semantics of notifyListeners()
         */
        TimerConfig config = new TimerConfig(changeSet, false);
        timerService.createIntervalTimer(0, retryInterval, config);
    }
    
    /**
     * Handle the EJB TimerService timeout and notify any registered append
     * listeners. The timer will only fire if the transaction in which the timer
     * was created was committed successfully.
     * @param timer 
     */
    @Timeout
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void handleTimeout(Timer timer) {
        ChangeSet changeSet = (ChangeSet) timer.getInfo();
        this.performNotification(changeSet);
        timer.cancel();
    }
    
}