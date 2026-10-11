import baritone.api.event.events.TickEvent;
import baritone.api.event.events.type.EventState;
import baritone.utils.InputOverrideHandler;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

/** Investigation-only invocation of the actual current OUT path. No game input is sent. */
public class UseButtonOutProbe {
    public static void main(String[] args) throws Exception {
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        Unsafe unsafe = (Unsafe) access.get(null);
        InputOverrideHandler input = (InputOverrideHandler) unsafe.allocateInstance(InputOverrideHandler.class);
        Field asked = InputOverrideHandler.class.getDeclaredField("useButtonAskedFor");
        Field holding = InputOverrideHandler.class.getDeclaredField("holdingTheUseButton");
        asked.setAccessible(true);
        holding.setAccessible(true);
        asked.setBoolean(input, true);
        holding.setBoolean(input, true);
        input.onTick(new TickEvent(EventState.PRE, TickEvent.Type.OUT, 1));
        System.out.println("actual InputOverrideHandler.onTick(OUT): asked=" + asked.getBoolean(input)
                + " holding=" + holding.getBoolean(input));
        System.out.println("Scope: actual method/lease state only; no GLFW key or live item-use outcome measured.");
        if (asked.getBoolean(input) || holding.getBoolean(input)) {
            throw new AssertionError("OUT leaves the automation lease state alive");
        }
    }
}
