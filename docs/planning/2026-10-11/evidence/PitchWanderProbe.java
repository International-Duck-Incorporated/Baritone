import baritone.behavior.look.PitchWander;
import java.util.Random;

/** Investigation-only probe: compiles the actual existing pure helper, not a replacement. */
public final class PitchWanderProbe {
    public static void main(String[] args) {
        PitchWander helper = new PitchWander(new Random(1L));
        float next40 = helper.step(40f);
        float next90 = helper.step(90f);
        if (next40 != 39f || next90 != 89f) throw new AssertionError("helper failed to relax");
        float current = 90f;
        for (int i = 0; i < 100; i++) current = helper.step(current);
        if (current != 0f) throw new AssertionError("helper did not reach initial preferred level");
        float contextPitch = 40f;
        float rawEchoPitch = 40f;
        float processedPitch = 40.15f;
        if (rawEchoPitch != contextPitch) throw new AssertionError("raw echo differs");
        if (!(processedPitch != contextPitch)) throw new AssertionError("example gate did not differ");
        System.out.println("actual PitchWander.step(40)=39; step(90)=89; 100 steps from 90=0 (initial preference, no advance)");
        System.out.println("example raw echo 40 matches ctx 40; processed 40.15 differs, so the current settledGaze equality guard bypasses settling");
        System.out.println("scope: existing helper and guard-expression probe; does not measure runtime processedPitch, guard cadence, or idle ownership");
    }
}
