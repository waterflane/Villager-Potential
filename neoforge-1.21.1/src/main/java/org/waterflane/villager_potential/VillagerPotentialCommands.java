package org.waterflane.villager_potential;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.concurrent.CompletionException;

/** Administrative commands for safely reloading runtime configuration data. */
final class VillagerPotentialCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private VillagerPotentialCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("villagerpotential")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload").executes(VillagerPotentialCommands::reload))
        );
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        try {
            ServerConfig.reload(() -> server.reloadResources(
                    server.getPackRepository().getSelectedIds()
            )).whenComplete((ignored, failure) -> server.execute(() -> {
                if (failure == null) {
                    source.sendSuccess(
                            () -> Component.literal(
                                    "Villager Potential configuration and specialization data reloaded"
                            ),
                            true
                    );
                    return;
                }
                Throwable cause = rootCause(failure);
                LOGGER.warn("Villager Potential reload failed", cause);
                source.sendFailure(Component.literal(
                        "Villager Potential reload failed: " + actionableMessage(cause)
                ));
            }));
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(
                    "Villager Potential reload failed: " + actionableMessage(exception)
            ));
            return 0;
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause.getClass() == RuntimeException.class)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String actionableMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }
}
