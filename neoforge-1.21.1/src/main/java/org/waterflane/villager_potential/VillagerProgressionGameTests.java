package org.waterflane.villager_potential;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Villager_potential.MODID)
@PrefixGameTestTemplate(false)
public final class VillagerProgressionGameTests {
    private VillagerProgressionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 1_200)
    public static void timeProgressionUnlocksTheNextLevelsOffers(GameTestHelper helper) {
        Villager villager = librarian(helper);
        int noviceOfferCount = villager.getOffers().size();

        helper.succeedWhen(() -> {
            helper.assertValueEqual(
                    villager.getVillagerData().getLevel(),
                    2,
                    "profession level"
            );
            helper.assertValueEqual(
                    villager.getOffers().size(),
                    noviceOfferCount + 2,
                    "offer count after apprentice unlock"
            );
            helper.assertTrue(
                    villager.hasEffect(MobEffects.REGENERATION),
                    "vanilla level-up regeneration was not applied"
            );
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void tradesAloneCannotScheduleAProfessionLevel(GameTestHelper helper) {
        Villager villager = librarian(helper);
        MerchantOffer offer = villager.getOffers().getFirst();
        villager.setTradingPlayer(helper.makeMockPlayer(GameType.CREATIVE));
        for (int trade = 0; trade < 100; trade++) {
            villager.notifyTrade(offer);
        }
        villager.setTradingPlayer(null);

        helper.assertValueEqual(villager.getVillagerData().getLevel(), 1, "profession level");
        helper.runAfterDelay(45L, () -> {
            helper.assertValueEqual(
                    villager.getVillagerData().getLevel(),
                    1,
                    "profession level after vanilla's former trade-XP delay"
            );
            helper.succeed();
        });
    }

    private static Villager librarian(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        villager.setVillagerData(
                villager.getVillagerData()
                        .setProfession(VillagerProfession.LIBRARIAN)
                        .setLevel(1)
        );
        // Retain vanilla's post-trade profession lock without using XP to level.
        villager.setVillagerXp(1);
        villager.setPersistenceRequired();
        villager.restrictTo(villager.blockPosition(), 1);
        return villager;
    }
}
