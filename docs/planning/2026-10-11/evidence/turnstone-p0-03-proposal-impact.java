public class Break {
    static Object world = new Object(), original = new Object(), response = new Object();

    static void tick(String label, boolean ok, boolean expected) {
        System.out.printf("   %-58s %s   (test expects %s)%n", label, ok == expected ? "unchanged" : "*** FLIPS ***", expected);
    }

    public static void main(String[] a) {
        System.out.println("Their test: aSupersededWorkerCannotReportOrAdopt");
        var s = new ModifiedRequestState<Object>();
        var old = s.begin(response, world);
        var current = s.begin(original, world);
        tick("assertFalse(isCurrent(old))", s.isCurrent(old), false);
        tick("assertFalse(reportPrimaryFailure(old))", s.reportPrimaryFailure(old), false);
        tick("assertTrue(isCurrent(current))", s.isCurrent(current), true);
        tick("assertFalse(consumeFailure(original))", s.consumeFailure(original), false);

        System.out.println();
        System.out.println("Their test: newCalculationForTheSameTaskDoesNotEraseReportedFailure");
        s = new ModifiedRequestState<Object>();
        var before = s.begin(original, world);
        tick("assertTrue(reportPrimaryFailure(before))", s.reportPrimaryFailure(before), true);
        s.begin(original, world);
        tick("assertTrue(consumeFailure(original))", s.consumeFailure(original), true);

        System.out.println();
        System.out.println("Their test: canonicalNewTaskExpiresOldWorkEvenForSameRequesterAndGoal");
        s = new ModifiedRequestState<Object>();
        var old2 = s.begin(original, world);
        s.reportPrimaryFailure(old2);
        s.invalidateRequester(original);
        tick("assertFalse(consumeFailure(original)) [new task]", s.consumeFailure(original), false);

        System.out.println();
        System.out.println("Their test: geometryCancellationExpiresPendingCurrentRequesterFailure");
        s = new ModifiedRequestState<Object>();
        var t = s.begin(original, world);
        s.reportPrimaryFailure(t);
        s.cancelCurrent();
        tick("assertFalse(consumeFailure(original)) [cancelled]", s.consumeFailure(original), false);

        System.out.println();
        System.out.println("Their test: worldReplacementDropsOldCompletionAndFailure");
        s = new ModifiedRequestState<Object>();
        var w1 = s.begin(original, world);
        s.reportPrimaryFailure(w1);
        s.observeWorld(new Object());
        tick("assertFalse(consumeFailure(original)) [new world]", s.consumeFailure(original), false);
    }
}
