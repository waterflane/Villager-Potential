package org.waterflane.villager_potential;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VillagerPotentialCommandsTest {
    @Test
    void reloadCommandRequiresVanillaAdministratorPermissionLevel() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        VillagerPotentialCommands.register(dispatcher);
        var root = dispatcher.getRoot().getChild("villagerpotential");
        CommandSourceStack user = mock(CommandSourceStack.class);
        CommandSourceStack administrator = mock(CommandSourceStack.class);
        when(user.hasPermission(2)).thenReturn(false);
        when(administrator.hasPermission(2)).thenReturn(true);

        assertNotNull(root);
        assertFalse(root.canUse(user));
        assertTrue(root.canUse(administrator));
        assertNotNull(root.getChild("reload"));
    }
}
