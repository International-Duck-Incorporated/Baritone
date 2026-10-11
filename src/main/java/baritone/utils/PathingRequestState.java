/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import java.util.IdentityHashMap;
import java.util.Objects;

/**
 * Requester lifetime and primary-failure mailbox for one pathing behaviour. This is metadata, not
 * another process scheduler: no Minecraft calls, goal comparison, callbacks or user settings live here.
 * Callers decide which process is eligible and retain the existing last-winner guard.
 * <p>
 * A worker reports only while its ticket is current. A reported failure then belongs to its requester
 * until consumed, cancelled, or superseded by a new logical task/world; an empty event drain or a new
 * calculation for the same task cannot erase it. Identity, not a process's equals(), names ownership.
 * All methods are short synchronized state operations and never call external code while locked.
 * Storage is one generation and at most one failure per requesting identity, not a history of events.
 * <p>
 * This helper is not wired into the behaviour until its contract and integration have been reviewed.
 */
public final class PathingRequestState<R> {

    public static final class Ticket<R> {
        private final R requester;
        private final Object world;
        private final long worldGeneration;
        private final long requesterGeneration;
        private final long requestId;
        private boolean cancelled;
        private boolean failureReported;

        private Ticket(R requester, Object world, long worldGeneration,
                       long requesterGeneration, long requestId) {
            this.requester = requester;
            this.world = world;
            this.worldGeneration = worldGeneration;
            this.requesterGeneration = requesterGeneration;
            this.requestId = requestId;
        }

        public R requester() {
            return requester;
        }

        public long requestId() {
            return requestId;
        }
    }

    private final IdentityHashMap<R, Long> requesterGenerations = new IdentityHashMap<>();
    private final IdentityHashMap<R, Ticket<R>> pendingFailures = new IdentityHashMap<>();
    private Object world;
    private long worldGeneration;
    private long nextRequestId;
    private Ticket<R> latest;

    /** Called on the client thread for a world change, including OUT/null. */
    public synchronized void observeWorld(Object world) {
        if (this.world == world) {
            return;
        }
        reset();
        this.world = world;
    }

    /** Whole cancellation invalidates tickets even if the world object has not changed. */
    public synchronized void reset() {
        if (latest != null) {
            latest.cancelled = true;
        }
        latest = null;
        worldGeneration++;
        requesterGenerations.clear();
        pendingFailures.clear();
    }

    /**
     * A canonical new task/lost-control notification, not an ordinary goal rescan or BRACE pause.
     * Opaque clients that mutate a private task without notification cannot have that mutation inferred.
     */
    public synchronized void invalidateRequester(R requester) {
        if (requester == null) {
            return;
        }
        requesterGenerations.put(requester, generation(requester) + 1);
        pendingFailures.remove(requester);
        if (latest != null && latest.requester == requester) {
            latest.cancelled = true;
        }
    }

    /** Cancelling geometry expires the current requester's outstanding failure too. */
    public synchronized void cancelCurrent() {
        if (latest != null) {
            latest.cancelled = true;
            invalidateRequester(latest.requester);
        }
    }

    /**
     * Capture a request under a real world token. A null requester can calculate, but cannot authorize a
     * process failure. The caller captures only identities on the client thread, never live worker reads.
     */
    public synchronized Ticket<R> begin(R requester, Object world) {
        Objects.requireNonNull(world, "A calculation needs a world token");
        observeWorld(world);
        latest = new Ticket<>(requester, world, worldGeneration, generation(requester), ++nextRequestId);
        return latest;
    }

    /** May this worker still adopt/emit? A newer job or a cancelled task/world disqualifies it. */
    public synchronized boolean isCurrent(Ticket<R> ticket) {
        return ticket != null && latest == ticket && valid(ticket);
    }

    /**
     * Report at worker completion, before the event is queued. Late superseded workers are refused.
     * Do not call this for a cancellation, exception or NEXT_CALC_FAILED outcome.
     */
    public synchronized boolean reportPrimaryFailure(Ticket<R> ticket) {
        if (!isCurrent(ticket) || ticket.requester == null || ticket.failureReported) {
            return false;
        }
        ticket.failureReported = true;
        // One pending failure is enough for the process's boolean contract; keep the first valid one.
        pendingFailures.putIfAbsent(ticket.requester, ticket);
        return true;
    }

    /** At most one consumption by the actual requester; eligibility is the controller's decision. */
    public synchronized boolean consumeFailure(R requester) {
        Ticket<R> ticket = pendingFailures.remove(requester);
        return ticket != null && ticket.requester == requester && valid(ticket);
    }

    public synchronized boolean hasPendingFailure(R requester) {
        Ticket<R> ticket = pendingFailures.get(requester);
        return ticket != null && valid(ticket);
    }

    private long generation(R requester) {
        return requester == null ? 0 : requesterGenerations.getOrDefault(requester, 0L);
    }

    private boolean valid(Ticket<R> ticket) {
        return !ticket.cancelled && ticket.world == world && ticket.worldGeneration == worldGeneration
                && ticket.requesterGeneration == generation(ticket.requester);
    }
}
