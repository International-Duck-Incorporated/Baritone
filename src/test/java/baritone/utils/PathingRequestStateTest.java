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

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class PathingRequestStateTest {
    private final PathingRequestState<Object> state = new PathingRequestState<>();
    private final Object world = new Object();
    private final Object original = new Object();
    private final Object response = new Object();

    @Test
    public void onlyTheRequesterCanReadTheFailure() {
        var ticket = state.begin(response, world);
        assertTrue(state.reportPrimaryFailure(ticket));
        assertFalse(state.consumeFailure(original));
        assertTrue(state.hasPendingFailure(response));
        assertTrue(state.consumeFailure(response));
    }

    @Test
    public void failureIsConsumedOnceAndDuplicateCompletionCannotRestoreIt() {
        var ticket = state.begin(original, world);
        assertTrue(state.reportPrimaryFailure(ticket));
        assertTrue(state.consumeFailure(original));
        assertFalse(state.consumeFailure(original));
        assertFalse(state.reportPrimaryFailure(ticket));
        assertFalse(state.hasPendingFailure(original));
    }

    @Test
    public void anEmptyEventDrainHasNoPowerToEraseAPendingFailure() {
        var ticket = state.begin(original, world);
        assertTrue(state.reportPrimaryFailure(ticket));
        for (int tick = 0; tick < 20; tick++) {
            // There is no ticket invalidation or consumption in an empty notification drain.
            state.observeWorld(world);
            assertTrue(state.hasPendingFailure(original));
        }
        assertTrue(state.consumeFailure(original));
    }

    @Test
    public void bracePauseKeepsOriginalFailureAndDoesNotGiveItToThePauser() {
        var ticket = state.begin(original, world);
        assertTrue(state.reportPrimaryFailure(ticket));
        for (int tick = 0; tick < 20; tick++) {
            assertFalse(state.consumeFailure(response));
        }
        assertTrue(state.consumeFailure(original));
    }

    @Test
    public void newCalculationForTheSameTaskDoesNotEraseReportedFailure() {
        var before = state.begin(original, world);
        assertTrue(state.reportPrimaryFailure(before));
        var after = state.begin(original, world);
        assertTrue(after.requestId() > before.requestId());
        assertTrue(state.isCurrent(after));
        assertTrue(state.consumeFailure(original));
    }

    @Test
    public void aSupersededWorkerCannotReportOrAdopt() {
        var old = state.begin(response, world);
        var current = state.begin(original, world);
        assertFalse(state.isCurrent(old));
        assertFalse(state.reportPrimaryFailure(old));
        assertTrue(state.isCurrent(current));
        assertFalse(state.consumeFailure(original));
    }

    @Test
    public void canonicalNewTaskExpiresOldWorkEvenForSameRequesterAndGoal() {
        var old = state.begin(original, world);
        assertTrue(state.reportPrimaryFailure(old));
        state.invalidateRequester(original);
        var fresh = state.begin(original, world);
        assertFalse(state.isCurrent(old));
        assertFalse(state.hasPendingFailure(original));
        assertTrue(state.isCurrent(fresh));
        assertTrue(state.reportPrimaryFailure(fresh));
        assertTrue(state.consumeFailure(original));
    }

    @Test
    public void aDifferentRequesterInvalidationDoesNotEraseOriginalWork() {
        var ticket = state.begin(original, world);
        assertTrue(state.reportPrimaryFailure(ticket));
        state.invalidateRequester(response);
        assertTrue(state.isCurrent(ticket));
        assertTrue(state.consumeFailure(original));
    }

    @Test
    public void geometryCancellationExpiresPendingCurrentRequesterFailure() {
        var ticket = state.begin(original, world);
        assertTrue(state.reportPrimaryFailure(ticket));
        state.cancelCurrent();
        assertFalse(state.isCurrent(ticket));
        assertFalse(state.consumeFailure(original));
        assertFalse(state.reportPrimaryFailure(ticket));
    }

    @Test
    public void worldReplacementDropsOldCompletionAndFailure() {
        var ticket = state.begin(original, world);
        assertTrue(state.reportPrimaryFailure(ticket));
        Object differentWorld = new Object();
        state.observeWorld(differentWorld);
        assertFalse(state.isCurrent(ticket));
        assertFalse(state.consumeFailure(original));
        var fresh = state.begin(original, differentWorld);
        assertTrue(state.isCurrent(fresh));
    }

    @Test
    public void outAndReturningToSameWorldObjectStillHasANewSession() {
        var ticket = state.begin(original, world);
        state.observeWorld(null);
        state.observeWorld(world);
        assertFalse(state.isCurrent(ticket));
        assertFalse(state.reportPrimaryFailure(ticket));
    }

    @Test
    public void wholeCancelExpiresSameWorldAndRequester() {
        var old = state.begin(original, world);
        assertTrue(state.reportPrimaryFailure(old));
        state.reset();
        var fresh = state.begin(original, world);
        assertFalse(state.isCurrent(old));
        assertFalse(state.consumeFailure(original));
        assertTrue(state.isCurrent(fresh));
    }

    @Test
    public void anUnattributedCalculationCannotAuthorizeProcessFailure() {
        var ticket = state.begin(null, world);
        assertTrue(state.isCurrent(ticket));
        assertFalse(state.reportPrimaryFailure(ticket));
        assertFalse(state.consumeFailure(original));
        assertFalse(state.consumeFailure(null));
    }

    @Test(expected = NullPointerException.class)
    public void noCalculationCanStartWithoutAWorldIdentity() {
        state.begin(original, null);
    }

    @Test
    public void userDefinedEqualsCannotConflateDifferentRequesters() {
        Object a = new Object() { public boolean equals(Object other) { return true; } };
        Object b = new Object() { public boolean equals(Object other) { return true; } };
        var ticket = state.begin(a, world);
        assertTrue(state.reportPrimaryFailure(ticket));
        assertFalse(state.consumeFailure(b));
        assertTrue(state.consumeFailure(a));
    }

    @Test
    public void failuresForSeparateLiveRequestersRemainSeparate() {
        var a = state.begin(original, world);
        assertTrue(state.reportPrimaryFailure(a));
        var b = state.begin(response, world);
        assertTrue(state.reportPrimaryFailure(b));
        assertTrue(state.consumeFailure(original));
        assertTrue(state.consumeFailure(response));
        assertFalse(state.consumeFailure(original));
        assertFalse(state.consumeFailure(response));
    }

    @Test
    public void aDelayedWorkerAfterCancellationCannotPublish() throws Exception {
        var old = state.begin(original, world);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> reported = executor.submit(() -> {
                ready.countDown();
                assertTrue(finish.await(5, TimeUnit.SECONDS));
                return state.reportPrimaryFailure(old);
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            state.invalidateRequester(original);
            var fresh = state.begin(original, world);
            finish.countDown();
            assertFalse(reported.get(5, TimeUnit.SECONDS));
            assertTrue(state.isCurrent(fresh));
            assertFalse(state.consumeFailure(original));
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
    }
}
