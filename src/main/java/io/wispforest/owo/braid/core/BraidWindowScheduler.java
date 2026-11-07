package io.wispforest.owo.braid.core;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.NonNull;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class BraidWindowScheduler {
    private static final List<App> APPS = new ArrayList<>();
    private static final Map<Long, BraidWindow> WINDOWS = new HashMap<>();

    static void registerWindow(BraidWindow window) {
        WINDOWS.put(window.backendWindow.handle(), window);
    }

    static void unregisterWindow(BraidWindow window) {
        WINDOWS.remove(window.backendWindow.handle(), window);
    }

    public static void add(BraidWindow window, AppState app) {
        APPS.add(new App(window, app));
    }

    /// Tries to dispatch the given event to a Braid window, if it is associated with one.
    ///
    /// @param event the event to dispatch
    /// @return `true` if the event was consumed by a secondary window, `false` otherwise
    /// @since 0.13.1+metabrix+26.3
    /// @author metabrix
    public static boolean dispatchEvent(final @NonNull SDL_Event event) {
        final long handle = SDLEvents.SDL_GetWindowFromEvent(event);
        if (handle == 0) return false;

        final var window = WINDOWS.get(handle);
        if (window == null) return false;

        window.handleEvent(event);
        return true;
    }

    public static void frame() {
        for (var app : new ArrayList<>(APPS)) {
            if (!app.state().running()) {
                app.state().dispose();

                APPS.remove(app);
                continue;
            }

            app.surface().update();
            app.state().processEvents(
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks()
            );

            if (!app.state().running() || app.surface().backendWindow.isIconified()) continue;

            app.state().draw(app.surface().guiRenderer.newGraphics(app.state().cursorPosition().x(), app.state().cursorPosition().y()));
        }
    }

    static {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            APPS.forEach(app -> app.state().dispose());
            APPS.clear();
            for (var window : new ArrayList<>(WINDOWS.values())) window.dispose();
            WINDOWS.clear();
        });
    }
}

record App(BraidWindow surface, AppState state) {}
