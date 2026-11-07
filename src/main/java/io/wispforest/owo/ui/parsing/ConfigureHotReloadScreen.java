package io.wispforest.owo.ui.parsing;

import io.wispforest.owo.Owo;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import io.wispforest.owo.ui.util.UISounds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.system.MemoryUtil;

import java.nio.file.Path;

public class ConfigureHotReloadScreen extends BaseUIModelScreen<FlowLayout> implements CommandOpenedScreen {

    private final @Nullable Screen parent;

    private final Identifier modelId;
    private @Nullable Path reloadLocation;

    private LabelComponent fileNameLabel;
    private @Nullable SDL_DialogFileCallback fileDialogCallback;

    public ConfigureHotReloadScreen(Identifier modelId, @Nullable Screen parent) {
        super(FlowLayout.class, DataSource.asset(Owo.id("configure_hot_reload")));
        this.parent = parent;

        this.modelId = modelId;
        this.reloadLocation = UIModelLoader.getHotReloadPath(this.modelId);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.childById(LabelComponent.class, "ui-model-label").text(Component.translatable("text.owo.configure_hot_reload.model", this.modelId));
        this.fileNameLabel = rootComponent.childById(LabelComponent.class, "file-name-label");
        this.updateFileNameLabel();

        rootComponent.childById(ButtonComponent.class, "choose-button")
            .active(this.fileDialogCallback == null)
            .onPress(this::chooseReloadLocation);

        rootComponent.childById(ButtonComponent.class, "save-button").onPress(button -> {
            UIModelLoader.setHotReloadPath(this.modelId, this.reloadLocation);
            this.onClose();
        });

        rootComponent.childById(LabelComponent.class, "close-label").mouseDown().subscribe((click, doubled) -> {
            UISounds.playInteractionSound();
            this.onClose();
            return true;
        });
    }

    private void chooseReloadLocation(final @NonNull ButtonComponent button) {
        if (this.fileDialogCallback != null) return;

        button.active(false);
        final var client = this.minecraft;

        this.fileDialogCallback = SDL_DialogFileCallback.create((_, fileList, _) -> {
            // SDL owns the result strings and may invoke this callback on another thread.
            // Copy the selection before returning and perform all UI work on the client thread.
            String selected = null;
            final String error = fileList == 0 ? SDLError.SDL_GetError() : null;
            if (fileList != 0) {
                final long firstPath = MemoryUtil.memGetAddress(fileList);
                if (firstPath != 0) selected = MemoryUtil.memUTF8(firstPath);
            }
            final String path = selected;
            client.schedule(() -> {
                this.fileDialogCallback.free();
                this.fileDialogCallback = null;
                button.active(true);
                if (error != null) Owo.LOGGER.error("Failed to choose UI model source: {}", error);
                if (path == null) return;

                try {
                    this.reloadLocation = Path.of(path);
                    if (client.gui.screen() == this) this.updateFileNameLabel();
                } catch (java.nio.file.InvalidPathException exception) {
                    Owo.LOGGER.error("Invalid UI model source path '{}'", path, exception);
                }
            });
        });

        final int properties = SDLProperties.SDL_CreateProperties();
        try {
            SDLProperties.SDL_SetStringProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, "Choose UI Model source");
            SDLProperties.SDL_SetPointerProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER, client.getWindow().handle());
            if (this.reloadLocation != null) {
                SDLProperties.SDL_SetStringProperty(properties, SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING, this.reloadLocation.toString());
            }
            SDLDialog.SDL_ShowFileDialogWithProperties(SDLDialog.SDL_FILEDIALOG_OPENFILE, this.fileDialogCallback, 0, properties);
        } catch (final RuntimeException exception) {
            this.fileDialogCallback.free();
            this.fileDialogCallback = null;
            button.active(true);
            Owo.LOGGER.error("Failed to open UI model source dialog", exception);
        } finally {
            SDLProperties.SDL_DestroyProperties(properties);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    private void updateFileNameLabel() {
        this.fileNameLabel.text(Component.translatable(
                "text.owo.configure_hot_reload.reload_from",
                this.reloadLocation == null ? Component.translatable("text.owo.configure_hot_reload.reload_from.unset") : this.reloadLocation
        ));
    }
}
