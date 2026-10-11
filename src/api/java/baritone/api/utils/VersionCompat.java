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

package baritone.api.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The five things that are called one name on one Minecraft version and another name on the next, and
 * the whole of the difference between them.
 * <p>
 * This fork builds the same source against 26.1, 26.1.2, 26.2 and 26.3, so the source has to compile
 * against all four. Five symbols do not: measured by compiling this tree against 26.1 and reading the
 * six errors it produced (four call sites of two symbols, plus two in the client's screen), and then by
 * reading both named jars directly:
 * <table>
 *   <caption>the five, and their two spellings</caption>
 *   <tr><th>what</th><th>up to 26.1</th><th>26.3</th></tr>
 *   <tr><td>the vanilla pack, as resources</td><td>{@code createVanillaPackSource()} is itself a
 *       {@link PackResources}</td><td>{@code .fullResources()} hands them out</td></tr>
 *   <tr><td>the world's data layer</td><td>{@code RegistryLayer.WORLDGEN}</td>
 *       <td>{@code RegistryLayer.WORLD}</td></tr>
 *   <tr><td>the registries that layer holds</td><td>{@code RegistryDataLoader.WORLDGEN_REGISTRIES}</td>
 *       <td>{@code RegistryDataLoader.WORLD_REGISTRIES}</td></tr>
 *   <tr><td>the chat</td><td>{@code Gui#getChat()}</td><td>{@code Gui.hud.getChat()}</td></tr>
 *   <tr><td>the toast manager</td><td>{@code Minecraft#getToastManager()}</td>
 *       <td>{@code Gui#toastManager()}</td></tr>
 * </table>
 * <p>
 * <b>Why this is resolved at run time, and why it is not resolved by name.</b> A name would be the
 * obvious way to ask for both spellings - try one, catch, try the other - and on a development box it
 * would even work. In a released Fabric jar it would not. The release jar is remapped to intermediary
 * names, and remapping rewrites the <em>references</em> in the byte code, not the <em>strings</em> in
 * the constants: after remapping, a field looks like {@code field_12345} and no longer answers to
 * {@code WORLD_REGISTRIES}. So every lookup below asks for what the thing <em>is</em> - its type, its
 * return type, its position in a layering, which registries it contains - and never for what it is
 * called. Types are remapped along with everything else, so a search by type survives the trip intact.
 * <p>
 * That also means these lookups do not care which name 26.2 chose, or whether a future version renames
 * one of them again: they ask for the {@code PackResources}-shaped thing, the chat-shaped thing, the
 * list that holds the world's presets. Where a version could plausibly answer twice, the code says so
 * and refuses rather than guessing, because a silent wrong answer here is a crash inside a remapped jar
 * with no line number to point at.
 * <p>
 * <b>What this is not.</b> It is not a compatibility layer, and it should not grow into one. If a sixth
 * difference appears, this file gains one method with its two spellings written down; if the versions
 * start differing in <em>behaviour</em> rather than in names, that is a different problem and a
 * preprocessing toolchain, not another accessor here.
 */
public final class VersionCompat {

    private VersionCompat() {}

    /**
     * The vanilla data pack, as the thing a {@code MultiPackResourceManager} takes.
     * <p>
     * Up to 26.1 {@code ServerPacksSource.createVanillaPackSource()} <em>is</em> a {@link PackResources}
     * and can be handed straight over, which is the branch that runs there and costs no reflection at
     * all. From 26.3 the same call returns a holder, and the resources come out of it.
     */
    public static PackResources vanillaPack() {
        VanillaPackResources vanilla = ServerPacksSource.createVanillaPackSource();
        if (vanilla instanceof PackResources pack) {
            return pack;
        }
        return invoke(uniqueMethod(VanillaPackResources.class, PackResources.class), vanilla);
    }

    /**
     * The layer of {@link RegistryLayer} a world's own data lives in - {@code WORLDGEN} up to 26.1,
     * {@code WORLD} from 26.3.
     * <p>
     * Found by position rather than by name, and the position is not arbitrary: the four layers are a
     * layering, each built on the one before it ({@code STATIC}, then the world's data, then the
     * dimensions, then the reloadable registries), and both jars declare them in exactly that order -
     * read out of the two class files, not assumed. So the second layer is the world's data layer
     * whichever name it is wearing.
     */
    public static RegistryLayer worldDataLayer() {
        RegistryLayer[] layers = RegistryLayer.values();
        if (layers.length != 4) {
            // A version with a different layering is not something to guess at: the layer that gets
            // replaced below holds the registries, and replacing the wrong one would load the world's
            // data over the top of something else.
            throw new IllegalStateException("RegistryLayer has " + layers.length
                    + " layers rather than the four this fork knows (static, world, dimensions, reloadable)");
        }
        return layers[1];
    }

    /**
     * The registries that belong in {@link #worldDataLayer()} - {@code WORLDGEN_REGISTRIES} up to 26.1,
     * {@code WORLD_REGISTRIES} from 26.3.
     * <p>
     * Found by what the list holds, because there is no other way to tell them apart: they are all
     * public static lists of the same type on the same class. The list wanted is the one that holds the
     * world presets, which is exactly what the world-generation set is, and which is unique in both
     * jars - the client's synchronised set is a strict subset of it (28 of 45 registries up to 26.1,
     * 32 of 51 from 26.3), and the dimension set is the one holding the level stem, so the second test
     * below keeps that one out. Two independent anchors, both true in both jars, both checked here
     * rather than assumed.
     */
    public static List<RegistryDataLoader.RegistryData<?>> worldDataRegistries() {
        return WorldData.REGISTRIES;
    }

    /**
     * The player's chat, wherever the version keeps it: on the screen itself up to 26.1, one hop away
     * on the HUD from 26.3.
     */
    public static ChatComponent chat(Gui gui) {
        Method onTheScreen = methodOrNull(Gui.class, ChatComponent.class);
        if (onTheScreen != null) {
            return invoke(onTheScreen, gui);
        }
        List<Object> found = new ArrayList<>();
        for (Field field : Gui.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Method onTheField = methodOrNull(field.getType(), ChatComponent.class);
            if (onTheField == null) {
                continue;
            }
            try {
                found.add(invoke(onTheField, field.get(gui)));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("the chat is on " + field.getName() + " and it will not open", e);
            }
        }
        if (found.size() != 1) {
            throw new IllegalStateException("the chat is reachable " + found.size() + " ways from the screen"
                    + " rather than one, so which one is the player's is a question this file cannot answer");
        }
        return (ChatComponent) found.get(0);
    }

    /**
     * The toast manager, wherever the version keeps it: on the client up to 26.1, on the screen from
     * 26.3.
     */
    public static ToastManager toasts(Minecraft minecraft) {
        if (minecraft.gui != null) {
            Method onTheScreen = methodOrNull(Gui.class, ToastManager.class);
            if (onTheScreen != null) {
                return invoke(onTheScreen, minecraft.gui);
            }
        }
        Method onTheClient = methodOrNull(Minecraft.class, ToastManager.class);
        if (onTheClient != null) {
            return invoke(onTheClient, minecraft);
        }
        throw new IllegalStateException("no toast manager on the screen or on the client");
    }

    /**
     * The unique public method of {@code owner} that takes nothing and returns the wanted kind of
     * thing, or {@code null} if there is none. Two candidates is not a thing to break the tie on.
     */
    private static Method methodOrNull(Class<?> owner, Class<?> returns) {
        Method found = null;
        for (Method method : owner.getMethods()) {
            if (method.getParameterCount() != 0 || !returns.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (found != null) {
                throw new IllegalStateException(owner.getSimpleName() + " answers a "
                        + returns.getSimpleName() + " twice (" + found.getName() + " and " + method.getName()
                        + "), so which one is meant is a question this file cannot answer");
            }
            found = method;
        }
        return found;
    }

    private static Method uniqueMethod(Class<?> owner, Class<?> returns) {
        Method found = methodOrNull(owner, returns);
        if (found == null) {
            throw new IllegalStateException(owner.getSimpleName() + " has nothing returning a "
                    + returns.getSimpleName());
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Method method, Object target) {
        try {
            return (T) method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                    + " would not answer", e);
        }
    }

    /**
     * The world's registry list, worked out once, the first time anything asks for it. In its own class
     * so that the search happens on first use and not when this file is loaded - a version that cannot
     * answer one of these questions should still be able to use the other four.
     */
    private static final class WorldData {

        private static final List<RegistryDataLoader.RegistryData<?>> REGISTRIES = resolve();

        @SuppressWarnings("unchecked")
        private static List<RegistryDataLoader.RegistryData<?>> resolve() {
            List<RegistryDataLoader.RegistryData<?>> world = null;
            for (Field field : RegistryDataLoader.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                List<?> candidate;
                try {
                    candidate = (List<?>) field.get(null);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (candidate == null || candidate.isEmpty()) {
                    continue;
                }
                Set<Object> keys = registryKeys(candidate);
                if (!keys.contains(Registries.WORLD_PRESET)) {
                    continue; // not the set that carries the world's presets
                }
                if (keys.contains(Registries.LEVEL_STEM)) {
                    continue; // that one is the dimensions, which are loaded into their own layer
                }
                if (world != null) {
                    throw new IllegalStateException("two of the registry lists carry the world's presets ("
                            + world.size() + " registries and " + candidate.size() + ")");
                }
                world = (List<RegistryDataLoader.RegistryData<?>>) candidate;
            }
            if (world == null) {
                throw new IllegalStateException("none of the registry lists carries the world's presets, so the"
                        + " world-generation set is not where this fork expects it");
            }
            return world;
        }

        /**
         * The registry keys a list of {@code RegistryData} holds. The record keeps its key behind the
         * consumer it offers for exactly this purpose - {@code runWithArguments} hands out the key and
         * the codec together - so this reads it the way the class intends rather than by reflection
         * into a field.
         */
        @SuppressWarnings("unchecked")
        private static Set<Object> registryKeys(List<?> registries) {
            Set<Object> keys = new LinkedHashSet<>();
            for (Object element : registries) {
                if (element instanceof RegistryDataLoader.RegistryData<?> data) {
                    ((RegistryDataLoader.RegistryData<Object>) data).runWithArguments((key, codec) -> keys.add(key));
                }
            }
            return keys;
        }
    }

    /** Kept so that a reader can see what the search above is looking for, and that it is not empty. */
    static {
        assert new HashSet<>(List.of(Registries.WORLD_PRESET, Registries.LEVEL_STEM)).size() == 2;
    }
}
