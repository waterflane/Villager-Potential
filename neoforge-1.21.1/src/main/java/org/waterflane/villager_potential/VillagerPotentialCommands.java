package org.waterflane.villager_potential;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.slf4j.Logger;
import org.waterflane.villager_potential.core.ProfessionId;
import org.waterflane.villager_potential.core.SkillProgressionConfig;
import org.waterflane.villager_potential.core.VillagerPotentialConfig;
import org.waterflane.villager_potential.core.api.InspectionFormat;
import org.waterflane.villager_potential.core.api.PotentialView;
import org.waterflane.villager_potential.core.api.VillagerPotentialService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/** Administrative inspection and supported state mutations. */
final class VillagerPotentialCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType NOT_A_VILLAGER =
            new SimpleCommandExceptionType(Component.literal("Target must be one villager"));

    private VillagerPotentialCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("villagerpotential")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("inspect")
                .then(Commands.argument("villager", EntityArgument.entity())
                        .executes(VillagerPotentialCommands::inspect)));
        root.then(Commands.literal("reload").executes(VillagerPotentialCommands::reload));

        var set = Commands.literal("set").requires(source -> source.hasPermission(4));
        set.then(Commands.literal("aptitude")
                .then(Commands.argument("villager", EntityArgument.entity())
                        .then(Commands.argument("profession", ResourceLocationArgument.id())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                                        .executes(context -> mutate(
                                                context,
                                                service -> service.setAptitude(
                                                        mutationVillager(context).getUUID(),
                                                        profession(context),
                                                        DoubleArgumentType.getDouble(context, "value")
                                                ),
                                                "aptitude set"
                                        ))))));
        set.then(Commands.literal("skill")
                .then(Commands.argument("villager", EntityArgument.entity())
                        .then(Commands.argument("profession", ResourceLocationArgument.id())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                                        .executes(context -> mutate(
                                                context,
                                                service -> service.setSkill(
                                                        mutationVillager(context).getUUID(),
                                                        profession(context),
                                                        DoubleArgumentType.getDouble(context, "value")
                                                ),
                                                "skill set"
                                        ))))));
        root.then(set);

        var reset = Commands.literal("reset").requires(source -> source.hasPermission(4));
        reset.then(Commands.literal("profession")
                .then(Commands.argument("villager", EntityArgument.entity())
                        .then(Commands.argument("profession", ResourceLocationArgument.id())
                                .executes(context -> mutate(
                                        context,
                                        service -> service.resetProfession(
                                                mutationVillager(context).getUUID(),
                                                profession(context)
                                        ),
                                        "profession derived state reset"
                                )))));
        root.then(reset);

        var regenerate = Commands.literal("regenerate")
                .requires(source -> source.hasPermission(4));
        regenerate.then(Commands.literal("profession")
                .then(Commands.argument("villager", EntityArgument.entity())
                        .then(Commands.argument("profession", ResourceLocationArgument.id())
                                .executes(context -> mutate(
                                        context,
                                        service -> service.regenerateProfession(
                                                mutationVillager(context).getUUID(),
                                                profession(context)
                                        ),
                                        "DESTRUCTIVE profession regeneration complete"
                                )))));
        regenerate.then(Commands.literal("all")
                .then(Commands.argument("villager", EntityArgument.entity())
                        .executes(context -> mutate(
                                context,
                                service -> service.regenerateAll(
                                        mutationVillager(context).getUUID()
                                ),
                                "DESTRUCTIVE full Potential regeneration complete"
                        ))));
        root.then(regenerate);
        dispatcher.register(root);
    }

    private static int inspect(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Villager villager = villager(context);
        Optional<PotentialView> view = service(context).find(villager.getUUID());
        if (view.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Villager is no longer loaded"));
            return 0;
        }
        String output = String.join("\n", formatInspection(
                villager.getUUID(),
                villager.getVillagerData().getLevel(),
                view.orElseThrow(),
                ServerConfig.gameplayConfig().skill()
        ));
        context.getSource().sendSuccess(() -> Component.literal(output), false);
        return 1;
    }

    private static int mutate(
            CommandContext<CommandSourceStack> context,
            Function<VillagerPotentialService, Optional<PotentialView>> operation,
            String success
    ) {
        try {
            Optional<PotentialView> result = operation.apply(service(context));
            if (result.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Villager is no longer loaded"));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.literal(success), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            context.getSource().sendFailure(Component.literal(
                    "Villager Potential mutation rejected: " + actionableMessage(exception)
            ));
            return 0;
        } catch (CommandTargetException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static Villager mutationVillager(CommandContext<CommandSourceStack> context) {
        try {
            return villager(context);
        } catch (CommandSyntaxException exception) {
            throw new CommandTargetException(exception);
        }
    }

    private static Villager villager(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(context, "villager");
        if (target instanceof Villager villager) {
            return villager;
        }
        throw NOT_A_VILLAGER.create();
    }

    private static ProfessionId profession(CommandContext<CommandSourceStack> context) {
        return ProfessionId.parse(ResourceLocationArgument.getId(context, "profession").toString());
    }

    private static VillagerPotentialService service(CommandContext<CommandSourceStack> context) {
        return VillagerPotentialServices.forServer(context.getSource().getServer());
    }

    /** Pure formatting seam used by command tests and future admin front ends. */
    static List<String> formatInspection(
            UUID villagerId,
            int vanillaProfessionLevel,
            PotentialView view
    ) {
        return formatInspection(
                villagerId,
                vanillaProfessionLevel,
                view,
                VillagerPotentialConfig.DEFAULT.skill()
        );
    }

    static List<String> formatInspection(
            UUID villagerId,
            int vanillaProfessionLevel,
            PotentialView view,
            SkillProgressionConfig progressionConfig
    ) {
        // The exact operator-facing output contract lives in core so every
        // loader renders inspections identically.
        return InspectionFormat.format(
                villagerId,
                vanillaProfessionLevel,
                view,
                progressionConfig
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

    private static final class CommandTargetException extends RuntimeException {
        private CommandTargetException(CommandSyntaxException cause) {
            super(cause.getRawMessage().getString(), cause);
        }
    }
}
