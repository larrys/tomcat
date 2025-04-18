/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.session;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.Store;
import org.apache.catalina.StoreManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Extends the {@link ManagerBase} class to implement most of the functionality required by a Manager which supports any
 * kind of persistence, even if only for restarts.
 * <p>
 * <b>IMPLEMENTATION NOTE</b>: Correct behavior of session storing and reloading depends upon external calls to the
 * {@link Lifecycle#start()} and {@link Lifecycle#stop()} methods of this class at the correct times.
 *
 * @author Craig R. McClanahan
 */
public abstract class PersistentManagerBase extends ManagerBase implements StoreManager {

    private final Log log = LogFactory.getLog(PersistentManagerBase.class); // must not be static


    // ----------------------------------------------------- Instance Variables

    /**
     * The descriptive name of this Manager implementation (for logging).
     */
    private static final String name = "PersistentManagerBase";

    /**
     * Key of the note of a session in which the timestamp of last backup is stored.
     */
    private static final String PERSISTED_LAST_ACCESSED_TIME =
            "org.apache.catalina.session.PersistentManagerBase.persistedLastAccessedTime";


    /**
     * Store object which will manage the Session store.
     */
    protected Store store = null;


    /**
     * Whether to save and reload sessions when the Manager <code>unload</code> and <code>load</code> methods are
     * called.
     */
    protected boolean saveOnRestart = true;


    /**
     * How long a session must be idle before it should be backed up. {@code -1} means sessions won't be backed up.
     */
    protected int maxIdleBackup = -1;


    /**
     * The minimum time in seconds a session must be idle before it is eligible to be swapped to disk to keep the active
     * session count below maxActiveSessions. Setting to {@code -1} means sessions will not be swapped out to keep the
     * active session count down.
     */
    protected int minIdleSwap = -1;


    /**
     * The maximum time in seconds a session may be idle before it is eligible to be swapped to disk due to inactivity.
     * Setting this to {@code -1} means sessions should not be swapped out just because of inactivity.
     */
    protected int maxIdleSwap = -1;


    /**
     * Sessions currently being swapped in and the associated locks
     */
    private final Map<String,Object> sessionSwapInLocks = new HashMap<>();

    /*
     * Session that is currently getting swapped in to prevent loading it more than once concurrently
     */
    private final ThreadLocal<Session> sessionToSwapIn = new ThreadLocal<>();


    // ------------------------------------------------------------- Properties


    /**
     * Indicates how many seconds old a session can get, after its last use in a request, before it should be backed up
     * to the store. {@code -1} means sessions are not backed up.
     *
     * @return the timeout after which sessions are ripe for back up
     */
    public int getMaxIdleBackup() {

        return maxIdleBackup;

    }


    /**
     * Sets the option to back sessions up to the Store after they are used in a request. Sessions remain available in
     * memory after being backed up, so they are not passivated as they are when swapped out. The value set indicates
     * how old a session may get (since its last use) before it must be backed up: {@code -1} means sessions are not
     * backed up.
     * <p>
     * Note that this is not a hard limit: sessions are checked against this age limit periodically according to
     * {@code processExpiresFrequency}. This value should be considered to indicate when a session is ripe for backing
     * up.
     * <p>
     * So it is possible that a session may be idle for {@code maxIdleBackup +
     * processExpiresFrequency * engine.backgroundProcessorDelay} seconds, plus the time it takes to handle other
     * session expiration, swapping, etc. tasks.
     *
     * @param backup The number of seconds after their last accessed time when they should be written to the Store.
     */
    public void setMaxIdleBackup(int backup) {

        if (backup == this.maxIdleBackup) {
            return;
        }
        int oldBackup = this.maxIdleBackup;
        this.maxIdleBackup = backup;
        support.firePropertyChange("maxIdleBackup", Integer.valueOf(oldBackup), Integer.valueOf(this.maxIdleBackup));

    }


    /**
     * @return The maximum time in seconds a session may be idle before it is eligible to be swapped to disk due to
     *             inactivity. A value of {@code -1} means sessions should not be swapped out just because of
     *             inactivity.
     */
    public int getMaxIdleSwap() {
        return maxIdleSwap;
    }


    /**
     * Sets the maximum time in seconds a session may be idle before it is eligible to be swapped to disk due to
     * inactivity. Setting this to {@code -1} means sessions should not be swapped out just because of inactivity.
     *
     * @param max time in seconds to wait for possible swap out
     */
    public void setMaxIdleSwap(int max) {

        if (max == this.maxIdleSwap) {
            return;
        }
        int oldMaxIdleSwap = this.maxIdleSwap;
        this.maxIdleSwap = max;
        support.firePropertyChange("maxIdleSwap", Integer.valueOf(oldMaxIdleSwap), Integer.valueOf(this.maxIdleSwap));
    }


    /**
     * @return The minimum time in seconds a session must be idle before it is eligible to be swapped to disk to keep
     *             the active session count below maxActiveSessions. A value of {@code -1} means sessions will not be
     *             swapped out to keep the active session count down.
     */
    public int getMinIdleSwap() {
        return minIdleSwap;
    }


    /**
     * Sets the minimum time in seconds a session must be idle before it is eligible to be swapped to disk to keep the
     * active session count below maxActiveSessions. Setting to {@code -1} means sessions will not be swapped out to
     * keep the active session count down.
     *
     * @param min time in seconds before a possible swap out
     */
    public void setMinIdleSwap(int min) {

        if (this.minIdleSwap == min) {
            return;
        }
        int oldMinIdleSwap = this.minIdleSwap;
        this.minIdleSwap = min;
        support.firePropertyChange("minIdleSwap", Integer.valueOf(oldMinIdleSwap), Integer.valueOf(this.minIdleSwap));

    }


    /**
     * Check, whether a session is loaded in memory
     *
     * @param id The session id for the session to be searched for
     *
     * @return {@code true}, if the session id is loaded in memory otherwise {@code false} is returned
     */
    public boolean isLoaded(String id) {
        try {
            if (super.findSession(id) != null) {
                return true;
            }
        } catch (IOException e) {
            log.error(sm.getString("persistentManager.isLoadedError", id), e);
        }
        return false;
    }


    @Override
    public String getName() {
        return name;
    }


    /**
     * Set the Store object which will manage persistent Session storage for this Manager.
     *
     * @param store the associated Store
     */
    public void setStore(Store store) {
        this.store = store;
        store.setManager(this);
    }


    @Override
    public Store getStore() {
        return this.store;
    }


    /**
     * Indicates whether sessions are saved when the Manager is shut down properly. This requires the {@link #unload()}
     * method to be called.
     *
     * @return {@code true}, when sessions should be saved on restart, {code false} otherwise
     */
    public boolean getSaveOnRestart() {

        return saveOnRestart;

    }


    /**
     * Set the option to save sessions to the Store when the Manager is shut down, then loaded when the Manager starts
     * again. If set to false, any sessions found in the Store may still be picked up when the Manager is started again.
     *
     * @param saveOnRestart {@code true} if sessions should be saved on restart, {@code false} if they should be
     *                          ignored.
     */
    public void setSaveOnRestart(boolean saveOnRestart) {

        if (saveOnRestart == this.saveOnRestart) {
            return;
        }

        boolean oldSaveOnRestart = this.saveOnRestart;
        this.saveOnRestart = saveOnRestart;
        support.firePropertyChange("saveOnRestart", Boolean.valueOf(oldSaveOnRestart),
                Boolean.valueOf(this.saveOnRestart));

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Clear all sessions from the Store.
     */
    public void clearStore() {

        if (store == null) {
            return;
        }

        try {
            store.clear();
        } catch (IOException e) {
            log.error(sm.getString("persistentManager.storeClearError"), e);
        }

    }


    /**
     * {@inheritDoc}
     * <p>
     * Direct call to processExpires and processPersistenceChecks
     */
    @Override
    public void processExpires() {

        long timeNow = System.currentTimeMillis();
        Session[] sessions = findSessions();
        int expireHere = 0;
        if (log.isTraceEnabled()) {
            log.trace("Start expire sessions " + getName() + " at " + timeNow + " sessioncount " + sessions.length);
        }
        for (Session session : sessions) {
            if (!session.isValid()) {
                expiredSessions.incrementAndGet();
                expireHere++;
            }
        }
        processPersistenceChecks();
        if (getStore() instanceof StoreBase) {
            ((StoreBase) getStore()).processExpires();
        }

        long timeEnd = System.currentTimeMillis();
        if (log.isTraceEnabled()) {
            log.trace("End expire sessions " + getName() + " processingTime " + (timeEnd - timeNow) +
                    " expired sessions: " + expireHere);
        }
        processingTime += (timeEnd - timeNow);

    }


    /**
     * Called by the background thread after active sessions have been checked for expiration, to allow sessions to be
     * swapped out, backed up, etc.
     */
    public void processPersistenceChecks() {

        processMaxIdleSwaps();
        processMaxActiveSwaps();
        processMaxIdleBackups();

    }


    /**
     * {@inheritDoc}
     * <p>
     * This method checks the persistence store if persistence is enabled, otherwise just uses the functionality from
     * ManagerBase.
     */
    @Override
    public Session findSession(String id) throws IOException {

        Session session = super.findSession(id);
        // OK, at this point, we're not sure if another thread is trying to
        // remove the session or not so the only way around this is to lock it
        // (or attempt to) and then try to get it by this session id again. If
        // the other code ran swapOut, then we should get a null back during
        // this run, and if not, we lock it out so we can access the session
        // safely.
        if (session != null) {
            synchronized (session) {
                session = super.findSession(session.getIdInternal());
                if (session != null) {
                    // To keep any external calling code from messing up the
                    // concurrency.
                    session.access();
                    session.endAccess();
                }
            }
        }
        if (session != null) {
            return session;
        }

        // See if the Session is in the Store
        session = swapIn(id);
        return session;
    }

    @Override
    public void removeSuper(Session session) {
        super.remove(session, false);
    }

    /**
     * Load all sessions found in the persistence mechanism, assuming they are marked as valid and have not passed their
     * expiration limit. If persistence is not supported, this method returns without doing anything.
     * <p>
     * Note that by default, this method is not called by the MiddleManager class. In order to use it, a subclass must
     * specifically call it, for example in the start() and/or processPersistenceChecks() methods.
     */
    @Override
    public void load() {

        // Initialize our internal data structures
        sessions.clear();

        if (store == null) {
            return;
        }

        String[] ids;
        try {
            ids = store.keys();
        } catch (IOException e) {
            log.error(sm.getString("persistentManager.storeLoadKeysError"), e);
            return;
        }

        int n = ids.length;
        if (n == 0) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("persistentManager.loading", String.valueOf(n)));
        }

        for (String id : ids) {
            try {
                swapIn(id);
            } catch (IOException e) {
                log.error(sm.getString("persistentManager.storeLoadError"), e);
            }
        }

    }


    /**
     * {@inheritDoc}
     * <p>
     * Remove this Session from the Store.
     */
    @Override
    public void remove(Session session, boolean update) {

        super.remove(session, update);

        if (store != null) {
            removeSession(session.getIdInternal());
        }
    }


    /**
     * Remove this Session from the active Sessions for this Manager, and from the Store.
     *
     * @param id Session's id to be removed
     */
    protected void removeSession(String id) {
        try {
            store.remove(id);
        } catch (IOException e) {
            log.error(sm.getString("persistentManager.removeError"), e);
        }
    }

    /**
     * Save all currently active sessions in the appropriate persistence mechanism, if any. If persistence is not
     * supported, this method returns without doing anything.
     * <p>
     * Note that by default, this method is not called by the MiddleManager class. In order to use it, a subclass must
     * specifically call it, for example in the stop() and/or processPersistenceChecks() methods.
     */
    @Override
    public void unload() {

        if (store == null) {
            return;
        }

        Session[] sessions = findSessions();
        int n = sessions.length;
        if (n == 0) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("persistentManager.unloading", String.valueOf(n)));
        }

        for (Session session : sessions) {
            try {
                swapOut(session);
            } catch (IOException e) {
                // This is logged in writeSession()
            }
        }

    }


    @Override
    public int getActiveSessionsFull() {
        // In memory session count
        int result = getActiveSessions();
        try {
            // Store session count
            result += getStore().getSize();
        } catch (IOException ioe) {
            log.warn(sm.getString("persistentManager.storeSizeException"));
        }
        return result;
    }


    @Override
    public Set<String> getSessionIdsFull() {
        // In memory session ID list
        Set<String> sessionIds = new HashSet<>(sessions.keySet());
        try {
            // Store session ID list
            sessionIds.addAll(Arrays.asList(getStore().keys()));
        } catch (IOException e) {
            log.warn(sm.getString("persistentManager.storeKeysException"));
        }
        return sessionIds;
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Look for a session in the Store and, if found, restore it in the Manager's list of active sessions if
     * appropriate. The session will be removed from the Store after swapping in, but will not be added to the active
     * session list if it is invalid or past its expiration.
     *
     * @param id The id of the session that should be swapped in
     *
     * @return restored session, or {@code null}, if none is found
     *
     * @throws IOException an IO error occurred
     */
    protected Session swapIn(String id) throws IOException {

        if (store == null) {
            return null;
        }

        Object swapInLock;

        /*
         * The purpose of this sync and these locks is to make sure that a session is only loaded once. It doesn't
         * matter if the lock is removed and then another thread enters this method and tries to load the same session.
         * That thread will re-create a swapIn lock for that session, quickly find that the session is already in
         * sessions, use it and carry on.
         */
        synchronized (this) {
            swapInLock = sessionSwapInLocks.computeIfAbsent(id, k -> new Object());
        }

        Session session;

        synchronized (swapInLock) {
            // First check to see if another thread has loaded the session into
            // the manager
            session = sessions.get(id);

            if (session == null) {
                Session currentSwapInSession = sessionToSwapIn.get();
                try {
                    if (currentSwapInSession == null || !id.equals(currentSwapInSession.getId())) {
                        session = loadSessionFromStore(id);
                        sessionToSwapIn.set(session);

                        if (session != null && !session.isValid()) {
                            log.error(sm.getString("persistentManager.swapInInvalid", id));
                            session.expire();
                            removeSession(id);
                            session = null;
                        }

                        if (session != null) {
                            reactivateLoadedSession(id, session);
                        }
                    }
                } finally {
                    sessionToSwapIn.remove();
                }
            }
        }

        // Make sure the lock is removed
        synchronized (this) {
            sessionSwapInLocks.remove(id);
        }

        return session;

    }

    private void reactivateLoadedSession(String id, Session session) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("persistentManager.swapIn", id));
        }

        session.setManager(this);
        // make sure the listeners know about it.
        ((StandardSession) session).tellNew();
        add(session);
        ((StandardSession) session).activate();
        // endAccess() to ensure timeouts happen correctly.
        // access() to keep access count correct or it will end up
        // negative
        session.access();
        session.endAccess();
    }

    private Session loadSessionFromStore(String id) throws IOException {
        try {
            return store.load(id);
        } catch (ClassNotFoundException e) {
            String msg = sm.getString("persistentManager.deserializeError", id);
            log.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }


    /**
     * Remove the session from the Manager's list of active sessions and write it out to the Store. If the session is
     * past its expiration or invalid, this method does nothing.
     *
     * @param session The Session to write out
     *
     * @throws IOException an IO error occurred
     */
    protected void swapOut(Session session) throws IOException {

        if (store == null || !session.isValid()) {
            return;
        }

        ((StandardSession) session).passivate();
        writeSession(session);
        super.remove(session, true);
        session.recycle();

    }


    /**
     * Write the provided session to the Store without modifying the copy in memory or triggering passivation events.
     * Does nothing if the session is invalid or past its expiration.
     *
     * @param session The session that should be written
     *
     * @throws IOException an IO error occurred
     */
    protected void writeSession(Session session) throws IOException {

        if (store == null || !session.isValid()) {
            return;
        }

        try {
            store.save(session);
        } catch (IOException e) {
            log.error(sm.getString("persistentManager.serializeError", session.getIdInternal(), e));
            throw e;
        }

    }


    /**
     * Start this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        super.startInternal();

        if (store == null) {
            log.error(sm.getString("persistentManager.noStore"));
        } else if (store instanceof Lifecycle) {
            ((Lifecycle) store).start();
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * Stop this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        if (log.isTraceEnabled()) {
            log.trace("Stopping");
        }

        setState(LifecycleState.STOPPING);

        if (getStore() != null && saveOnRestart) {
            unload();
        } else {
            // Expire all active sessions
            Session[] sessions = findSessions();
            for (Session value : sessions) {
                StandardSession session = (StandardSession) value;
                if (!session.isValid()) {
                    continue;
                }
                session.expire();
            }
        }

        if (getStore() instanceof Lifecycle) {
            ((Lifecycle) getStore()).stop();
        }

        // Require a new random number generator if we are restarted
        super.stopInternal();
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Swap idle sessions out to Store if they are idle too long.
     */
    protected void processMaxIdleSwaps() {

        if (!getState().isAvailable() || maxIdleSwap < 0) {
            return;
        }

        Session[] sessions = findSessions();

        // Swap out all sessions idle longer than maxIdleSwap
        if (maxIdleSwap >= 0) {
            for (Session value : sessions) {
                StandardSession session = (StandardSession) value;
                synchronized (session) {
                    if (!session.isValid()) {
                        continue;
                    }
                    int timeIdle = (int) (session.getIdleTimeInternal() / 1000L);
                    if (timeIdle >= maxIdleSwap && timeIdle >= minIdleSwap) {
                        if (session.accessCount != null && session.accessCount.get() > 0) {
                            // Session is currently being accessed - skip it
                            continue;
                        }
                        if (log.isTraceEnabled()) {
                            log.trace(sm.getString("persistentManager.swapMaxIdle", session.getIdInternal(),
                                    Integer.valueOf(timeIdle)));
                        }
                        try {
                            swapOut(session);
                        } catch (IOException e) {
                            // This is logged in writeSession()
                        }
                    }
                }
            }
        }

    }


    /**
     * Swap idle sessions out to Store if too many are active
     */
    protected void processMaxActiveSwaps() {

        if (!getState().isAvailable() || minIdleSwap < 0 || getMaxActiveSessions() < 0) {
            return;
        }

        Session[] sessions = findSessions();

        // FIXME: Smarter algorithm (LRU)
        int limit = (int) (getMaxActiveSessions() * 0.9);

        if (limit >= sessions.length) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("persistentManager.tooManyActive", Integer.valueOf(sessions.length)));
        }

        int toswap = sessions.length - limit;

        for (int i = 0; i < sessions.length && toswap > 0; i++) {
            StandardSession session = (StandardSession) sessions[i];
            synchronized (session) {
                int timeIdle = (int) (session.getIdleTimeInternal() / 1000L);
                if (timeIdle >= minIdleSwap) {
                    if (session.accessCount != null && session.accessCount.get() > 0) {
                        // Session is currently being accessed - skip it
                        continue;
                    }
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("persistentManager.swapTooManyActive", session.getIdInternal(),
                                Integer.valueOf(timeIdle)));
                    }
                    try {
                        swapOut(session);
                    } catch (IOException e) {
                        // This is logged in writeSession()
                    }
                    toswap--;
                }
            }
        }

    }


    /**
     * Back up idle sessions.
     */
    protected void processMaxIdleBackups() {

        if (!getState().isAvailable() || maxIdleBackup < 0) {
            return;
        }

        Session[] sessions = findSessions();

        // Back up all sessions idle longer than maxIdleBackup
        if (maxIdleBackup >= 0) {
            for (Session value : sessions) {
                StandardSession session = (StandardSession) value;
                synchronized (session) {
                    if (!session.isValid()) {
                        continue;
                    }
                    long lastAccessedTime = session.getLastAccessedTimeInternal();
                    Long persistedLastAccessedTime = (Long) session.getNote(PERSISTED_LAST_ACCESSED_TIME);
                    if (persistedLastAccessedTime != null &&
                            lastAccessedTime == persistedLastAccessedTime.longValue()) {
                        continue;
                    }
                    int timeIdle = (int) (session.getIdleTimeInternal() / 1000L);
                    if (timeIdle >= maxIdleBackup) {
                        if (log.isTraceEnabled()) {
                            log.trace(sm.getString("persistentManager.backupMaxIdle", session.getIdInternal(),
                                    Integer.valueOf(timeIdle)));
                        }

                        try {
                            writeSession(session);
                        } catch (IOException e) {
                            // This is logged in writeSession()
                        }
                        session.setNote(PERSISTED_LAST_ACCESSED_TIME, Long.valueOf(lastAccessedTime));
                    }
                }
            }
        }

    }

}
