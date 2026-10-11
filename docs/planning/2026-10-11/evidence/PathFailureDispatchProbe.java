import baritone.Baritone;
import baritone.api.event.events.PathEvent;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.IElytraProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.IPlayerContext;
import baritone.behavior.PathingBehavior;
import baritone.event.GameEventHandler;
import baritone.utils.PathingControlManager;
import java.lang.reflect.*;
import sun.misc.Unsafe;

/** Investigation-only controlled completion against the real queue and manager; no client/GLFW. */
public class PathFailureDispatchProbe {
    static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
    static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }
    static final class Job implements IBaritoneProcess {
        boolean active;
        boolean receivedFailure;
        public boolean isActive() { return active; }
        public boolean isTemporary() { return false; }
        public void onLostControl() { active = false; }
        public String displayName0() { return "probe original"; }
        public PathingCommand onTick(boolean failed, boolean safe) {
            receivedFailure = failed;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
    }
    public static void main(String[] args) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe u = (Unsafe) f.get(null);
        Baritone b = (Baritone) u.allocateInstance(Baritone.class);
        IPlayerContext ctx = (IPlayerContext) Proxy.newProxyInstance(IPlayerContext.class.getClassLoader(),
                new Class<?>[]{IPlayerContext.class}, (p,m,a) -> {
                    if (m.getName().equals("world") || m.getName().equals("player") || m.getName().equals("minecraft")) return null;
                    throw new UnsupportedOperationException(m.getName());
                });
        set(b, "playerContext", ctx);
        PathingBehavior path = new PathingBehavior(b);
        set(b, "pathingBehavior", path);
        set(b, "gameEventHandler", new GameEventHandler(b));
        IElytraProcess elytra = (IElytraProcess) Proxy.newProxyInstance(IElytraProcess.class.getClassLoader(),
                new Class<?>[]{IElytraProcess.class}, (p,m,a) -> {
                    if (m.getName().equals("isActive")) return false;
                    if (m.getName().equals("isSafeToCancel")) return true;
                    throw new UnsupportedOperationException(m.getName());
                });
        set(b, "elytraProcess", elytra);
        PathingControlManager manager = new PathingControlManager(b);
        set(b, "pathingControlManager", manager);
        Job original = new Job();
        manager.registerProcess(original);
        original.active = true;
        set(manager, "inControlLastTick", original);
        // Simulate an executor's late completion after its transient requester has yielded.
        call(path, "queuePathEvent", new Class<?>[]{PathEvent.class}, PathEvent.CALC_FAILED);
        call(path, "dispatchEvents", new Class<?>[]{});
        manager.executeProcesses();
        System.out.println("actual queue/manager, late untagged transient failure: resumedOriginalReceived=" + original.receivedFailure);
        call(path, "queuePathEvent", new Class<?>[]{PathEvent.class}, PathEvent.CALC_FAILED);
        call(path, "dispatchEvents", new Class<?>[]{});
        boolean afterFailureDrain = path.calcFailedLastTick();
        call(path, "dispatchEvents", new Class<?>[]{});
        boolean afterEmptyDrain = path.calcFailedLastTick();
        System.out.println("actual dispatcher failure flag: failureDrain=" + afterFailureDrain + " nextEmptyDrain=" + afterEmptyDrain);
        System.out.println("Scope: real methods + controlled completion; not a live A* callback, mine blacklist or client physics scenario.");
        if (original.receivedFailure && afterFailureDrain && !afterEmptyDrain) {
            throw new AssertionError("old behavior reproduces cross-requester delivery and empty-drain loss");
        }
    }
}
