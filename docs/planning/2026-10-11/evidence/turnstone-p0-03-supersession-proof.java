import java.lang.reflect.*;

public class Proof {
    static PathingRequestState<Object> state;
    static Object world = new Object(), original = new Object(), response = new Object();

    public static void main(String[] a) throws Exception {
        System.out.println("--- A) the case their test covers: report, THEN re-plan ---");
        state = new PathingRequestState<>();
        var t1 = state.begin(original, world);
        System.out.println("   failure accepted:        " + state.reportPrimaryFailure(t1));
        state.begin(original, world);
        System.out.println("   still deliverable after: " + state.consumeFailure(original));

        System.out.println();
        System.out.println("--- B) the case no test covers: a re-plan races the result ---");
        state = new PathingRequestState<>();
        var first  = state.begin(original, world);   // a calculation starts
        var second = state.begin(original, world);   // SAME task re-planned. no cancel, no task change, no world change
        System.out.println("   same requester, same generation, same world, nothing cancelled");
        System.out.println("   ticket valid by attribution?  " + valid(first) + "   cancelled? " + cancelled(first));
        System.out.println("   reportPrimaryFailure(first):  " + state.reportPrimaryFailure(first));
        System.out.println("   requester can read failure:   " + state.consumeFailure(original));

        System.out.println();
        System.out.println("--- C) the proposed rule (valid() instead of isCurrent()) ---");
        state = new PathingRequestState<>();
        first  = state.begin(original, world);
        second = state.begin(original, world);
        boolean wouldAccept = valid(first) && !cancelled(first);
        System.out.println("   would be accepted:            " + wouldAccept);

        System.out.println();
        System.out.println("--- D) and the dangerous case stays refused under it ---");
        // a cancelled ticket must NOT be reportable even with the relaxed rule
        state = new PathingRequestState<>();
        var t = state.begin(original, world);
        state.cancelCurrent();
        System.out.println("   cancelled ticket valid()?     " + valid(t) + "  cancelled? " + cancelled(t) + "  -> refused: " + !(valid(t) && !cancelled(t)));
        // a new logical task for the same requester must NOT resurrect the old failure
        state = new PathingRequestState<>();
        var old = state.begin(original, world);
        state.invalidateRequester(original);
        System.out.println("   after a canonical new task:   valid(old)=" + valid(old) + " -> refused: " + !(valid(old) && !cancelled(old)));
        // a different requester can never read it
        state = new PathingRequestState<>();
        var mine = state.begin(original, world);
        System.out.println("   another requester reads it?   " + state.consumeFailure(response));
    }

    static boolean valid(Object ticket) throws Exception {
        Method m = PathingRequestState.class.getDeclaredMethod("valid", PathingRequestState.Ticket.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(state, ticket);
    }
    static boolean cancelled(Object ticket) throws Exception {
        Field f = PathingRequestState.Ticket.class.getDeclaredField("cancelled");
        f.setAccessible(true);
        return (Boolean) f.get(ticket);
    }
}
