package io.wispforest.owo.braid.core;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.TextInputManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.device.SurfaceException;
import io.wispforest.owo.Owo;
import io.wispforest.owo.braid.core.cursor.CursorController;
import io.wispforest.owo.braid.core.cursor.CursorStyle;
import io.wispforest.owo.braid.core.events.*;
import io.wispforest.owo.braid.framework.widget.Widget;
import io.wispforest.owo.braid.util.BraidGuiRenderer;
import io.wispforest.owo.mixin.braid.MinecraftAccessor;
import io.wispforest.owo.mixin.braid.RenderSystemAccessor;
import io.wispforest.owo.util.EventSource;
import io.wispforest.owo.util.EventStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import org.joml.Vector4f;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

import static org.lwjgl.sdl.SDLEvents.*;

public class BraidWindow implements Surface {

    public final EventBinding eventBinding = new WindowEventBinding(this);
    public final Window backendWindow;
    public final BraidGuiRenderer guiRenderer;

    private final GpuSurface windowSurface;
    private final TextInputManager textInputManager;
    private final CursorController cursorController;
    private final EventStream<ResizeCallback> onResize = ResizeCallback.newStream();
    private final Set<Integer> pressedKeys = new HashSet<>();
    private final Set<Integer> pressedMouseButtons = new HashSet<>();
    private final ArrayList<Path> droppedFiles = new ArrayList<>();

    private TextureTarget remoteTarget;
    private int scaleFactor;
    private boolean surfaceValid;
    private boolean disposed;

    public BraidWindow(String title, int width, int height) {
        var client = Minecraft.getInstance();
        this.backendWindow = new Window(
            new WindowEventHandler() {
                @Override
                public void framebufferSizeChanged() {
                    // Window construction may notify us before the render target exists.
                    if (remoteTarget != null) resize();
                }

                @Override
                public void resizeGui() {}

                @Override
                public void cursorEntered() {}

                @Override
                public void fullscreenStateChanged(boolean fullscreen) {}
            },
            new DisplayData(width, height, OptionalInt.empty(), OptionalInt.empty(), false),
            null,
            false,
            title,
            ((MinecraftAccessor) client).owo$getMonitorManager(),
            RenderSystemAccessor.owo$getBackend()
        );

        GpuSurface surface = null;
        BraidGuiRenderer renderer = null;
        TextureTarget target = null;
        try {
            surface = RenderSystem.getDevice().createSurface(this.backendWindow.handle(), this.backendWindow::isIconified);
            renderer = new BraidGuiRenderer(client);
            target = new TextureTarget(
                "braid window", this.backendWindow.getWidth(), this.backendWindow.getHeight(), GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT
            );
        } catch (RuntimeException | Error exception) {
            if (target != null) target.destroyBuffers();
            if (renderer != null) renderer.close();
            if (surface != null) surface.close();
            this.backendWindow.close();
            throw exception;
        }
        this.textInputManager = new TextInputManager(this.backendWindow);
        this.windowSurface = surface;
        this.guiRenderer = renderer;
        this.remoteTarget = target;
        this.cursorController = new CursorController(this.backendWindow.handle());
        this.recalculateScale();
        this.backendWindow.setWindowCloseCallback(() -> this.eventBinding.add(CloseEvent.INSTANCE));
        BraidWindowScheduler.registerWindow(this);
        SDLVideo.SDL_ShowWindow(this.backendWindow.handle());
    }

    private void resize() {
        this.surfaceValid = false;
        this.remoteTarget.resize(this.backendWindow.getWidth(), this.backendWindow.getHeight());
        this.recalculateScale();
        this.onResize.sink().onResize(this.width(), this.height());
    }

    private void recalculateScale() {
        var client = Minecraft.getInstance();
        this.scaleFactor = this.backendWindow.calculateScale(client.options.guiScale().get(), client.options.forceUnicodeFont().get());
        this.backendWindow.setGuiScale(this.scaleFactor);
    }

    void update() {
        int previousScale = this.scaleFactor;
        this.recalculateScale();
        if (previousScale != this.scaleFactor) this.onResize.sink().onResize(this.width(), this.height());
        this.cursorController.apply();
    }

    /** Called while the SDL event is alive, before vanilla can route it to the main window. */
    void handleEvent(SDL_Event event) {
        if (this.disposed) return;

        switch (event.type()) {
            case SDL_EVENT_KEY_DOWN -> {
                var key = event.key();
                this.pressedKeys.add(key.scancode());
                this.eventBinding.add(new KeyPressEvent(key.scancode(), key.key(), key.mod()));
            }
            case SDL_EVENT_KEY_UP -> {
                var key = event.key();
                this.pressedKeys.remove(key.scancode());
                this.eventBinding.add(new KeyReleaseEvent(key.scancode(), key.key(), key.mod()));
            }
            case SDL_EVENT_TEXT_INPUT -> {
                var text = event.text().textString();
                var modifiers = new KeyModifiers(SDLKeyboard.SDL_GetModState());
                if (text != null) text.codePoints().forEach(codepoint -> this.eventBinding.add(new CharInputEvent(codepoint, modifiers)));
            }
            case SDL_EVENT_MOUSE_MOTION -> {
                var motion = event.motion();
                this.eventBinding.add(new MouseMoveEvent(
                    motion.x() * this.backendWindow.getWidth() / this.backendWindow.getScreenWidth() / this.scaleFactor,
                    motion.y() * this.backendWindow.getHeight() / this.backendWindow.getScreenHeight() / this.scaleFactor
                ));
            }
            case SDL_EVENT_MOUSE_BUTTON_DOWN -> {
                int button = event.button().button();
                this.pressedMouseButtons.add(button);
                this.eventBinding.add(new MouseButtonPressEvent(button, SDLKeyboard.SDL_GetModState()));
            }
            case SDL_EVENT_MOUSE_BUTTON_UP -> {
                int button = event.button().button();
                this.pressedMouseButtons.remove(button);
                this.eventBinding.add(new MouseButtonReleaseEvent(button, SDLKeyboard.SDL_GetModState()));
            }
            case SDL_EVENT_MOUSE_WHEEL -> {
                var wheel = event.wheel();
                boolean shiftInverted = InputQuirks.SHIFT_INVERTS_SCROLL_AXIS && wheel.y() == 0 && wheel.x() != 0
                    && (SDLKeyboard.SDL_GetModState() & InputConstants.MOD_SHIFT) != 0;
                this.eventBinding.add(new MouseScrollEvent(shiftInverted ? 0 : wheel.x(), shiftInverted ? -wheel.x() : wheel.y()));
            }
            case SDL_EVENT_DROP_BEGIN -> this.droppedFiles.clear();
            case SDL_EVENT_DROP_FILE -> {
                var path = event.drop().dataString();
                if (path != null) {
                    try {
                        this.droppedFiles.add(Path.of(path));
                    } catch (InvalidPathException exception) {
                        Owo.LOGGER.error("Failed to parse dropped path '{}'", path, exception);
                    }
                }
            }
            case SDL_EVENT_DROP_COMPLETE -> {
                if (!this.droppedFiles.isEmpty()) {
                    this.eventBinding.add(new FilesDroppedEvent(new ArrayList<>(this.droppedFiles)));
                    this.droppedFiles.clear();
                }
            }
            case SDL_EVENT_WINDOW_FOCUS_LOST -> {
                // SDL may move focus before key/button releases reach this window.
                for (int key : this.pressedKeys) this.eventBinding.add(new KeyReleaseEvent(key, 0, KeyModifiers.NONE));
                for (int button : this.pressedMouseButtons) this.eventBinding.add(new MouseButtonReleaseEvent(button, KeyModifiers.NONE));
                this.pressedKeys.clear();
                this.pressedMouseButtons.clear();
                this.backendWindow.handleEvent(event);
            }
            default -> this.backendWindow.handleEvent(event);
        }
    }

    public static OpenResult open(String title, int width, int height, Widget widget) {
        var window = new BraidWindow(title, width, height);
        try {
            var app = new AppState(
                Owo.LOGGER, AppState.formatName("BraidWindow", widget, title), Minecraft.getInstance(), window, window.eventBinding, widget
            );
            BraidWindowScheduler.add(window, app);
            return new OpenResult(app, window);
        } catch (RuntimeException | Error exception) {
            window.dispose();
            throw exception;
        }
    }

    @Override
    public void dispose() {
        if (this.disposed) return;
        this.disposed = true;
        BraidWindowScheduler.unregisterWindow(this);

        // The surface must release native swapchain resources before its SDL window is destroyed.
        this.windowSurface.close();
        this.guiRenderer.close();
        this.remoteTarget.destroyBuffers();
        this.cursorController.dispose();
        this.textInputManager.stopTextInput();
        this.backendWindow.close();
    }

    @Override
    public int width() {
        return this.backendWindow.getGuiScaledWidth();
    }

    @Override
    public int height() {
        return this.backendWindow.getGuiScaledHeight();
    }

    @Override
    public double scaleFactor() {
        return this.scaleFactor;
    }

    @Override
    public EventSource<ResizeCallback> onResize() {
        return this.onResize.source();
    }

    @Override
    public CursorStyle currentCursorStyle() {
        return this.cursorController.currentStyle();
    }

    @Override
    public void setCursorStyle(CursorStyle style) {
        this.cursorController.setStyle(style);
    }

    @Override
    public void beginRendering() {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(
            this.remoteTarget.getColorTexture(), new Vector4f(0, 0, 0, 1), this.remoteTarget.getDepthTexture(), 0
        );
        encoder.submit();
    }

    @Override
    public void endRendering() {
        this.guiRenderer.render(new BraidGuiRenderer.Target(this.remoteTarget, this));
        if (this.backendWindow.isIconified()) return;

        if (!this.surfaceValid || this.windowSurface.isSuboptimal()) {
            try {
                this.windowSurface.configure(new GpuSurface.Configuration(
                    this.backendWindow.getWidth(), this.backendWindow.getHeight(),
                    GpuSurface.PresentMode.getSupportedVsyncMode(this.windowSurface.supportedPresentModes(), false)
                ));
                this.surfaceValid = true;
            } catch (SurfaceException exception) {
                this.surfaceValid = false;
                Owo.LOGGER.warn("Failed to configure braid window surface", exception);
                return;
            }
        }

        try {
            this.windowSurface.acquireNextTexture();
        } catch (SurfaceException exception) {
            this.surfaceValid = false;
            Owo.LOGGER.warn("Failed to acquire braid window surface texture", exception);
            return;
        }

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        this.windowSurface.blitFromTexture(encoder, this.remoteTarget.getColorTextureView());
        encoder.submit();
        this.windowSurface.present();
    }

    @Override
    public TextInputManager textInputManager(TextInputManager mainManager) {
        return this.textInputManager;
    }

    public static class WindowEventBinding extends EventBinding {
        public final BraidWindow window;

        public WindowEventBinding(BraidWindow window) {
            this.window = window;
        }

        @Override
        public boolean isKeyPressed(int keyCode) {
            return SDLKeyboard.SDL_GetKeyboardFocus() == this.window.backendWindow.handle() && InputConstants.isKeyDown(keyCode);
        }

        @Override
        public KeyModifiers activeModifiers() {
            return SDLKeyboard.SDL_GetKeyboardFocus() == this.window.backendWindow.handle()
                ? new KeyModifiers(SDLKeyboard.SDL_GetModState()) : KeyModifiers.NONE;
        }
    }

    public record OpenResult(AppState state, BraidWindow window) {}
}
