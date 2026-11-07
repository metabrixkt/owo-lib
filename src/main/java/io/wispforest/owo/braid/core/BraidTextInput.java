package io.wispforest.owo.braid.core;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Routes vanilla editor focus and IME positioning to the surface currently running a Braid app.
///
/// @since 0.13.1+metabrix+26.3
/// @author metabrix
@NullMarked
public final class BraidTextInput {
    private static final ThreadLocal<@Nullable AppState> CURRENT_APP = new ThreadLocal<>();

    @Contract("-> fail")
    private BraidTextInput() {
        throw new UnsupportedOperationException("This class is not meant to be instantiated");
    }

    public static @Nullable AppState currentApp() {
        return CURRENT_APP.get();
    }

    public static void withApp(final AppState app, final Runnable action) {
        var previous = CURRENT_APP.get();
        CURRENT_APP.set(app);
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT_APP.remove();
            else CURRENT_APP.set(previous);
        }
    }
}
