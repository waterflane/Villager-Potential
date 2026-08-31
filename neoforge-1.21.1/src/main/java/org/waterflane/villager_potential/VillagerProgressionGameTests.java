package org.waterflane.villager_potential;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.waterflane.villager_potential.core.ProfessionId;

import java.util.Optional;

@GameTestHolder(Villager_potential.MODID)
@PrefixGameTestTemplate(false)
public final class VillagerProgressionGameTests {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");

    private VillagerProgressionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void earnedSkillUnlocksTheNextLevelsOffers(GameTestHelper helper) {
        Villager villager = librarian(helper);
        int noviceOfferCount = villager.getOffers().size();
        helper.runAfterDelay(5L, () -> {
            double apprenticeSkill = ServerConfig.gameplayConfig().skill()
                    .professionLevelThresholds().apprenticeSkill();
            VillagerPotentialAttachments.adminSetSkill(
                    villager,
                    LIBRARIAN,
                    apprenticeSkill
            );
        });

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

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void existingExpertBootstrapsSkillWithoutDemotion(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        villager.setVillagerData(
                villager.getVillagerData()
                        .setProfession(VillagerProfession.LIBRARIAN)
                        .setLevel(4)
        );
        villager.setVillagerXp(150);
        villager.setPersistenceRequired();

        helper.succeedWhen(() -> {
            var state = VillagerPotentialAttachments.get(villager);
            helper.assertTrue(
                    state.careerFor(LIBRARIAN).isPresent(),
                    "profession career has not initialized yet"
            );
            double expectedMinimum = ServerConfig.gameplayConfig().skill()
                    .professionLevelThresholds().thresholdForLevel(4);
            helper.assertValueEqual(
                    state.careerFor(LIBRARIAN).orElseThrow().learnedSkill(),
                    expectedMinimum,
                    "bootstrapped profession skill"
            );
            helper.assertValueEqual(
                    villager.getVillagerData().getLevel(),
                    4,
                    "profession level"
            );
            helper.assertTrue(
                    state.tradePalettes().isEmpty(),
                    "migration invented trade memory"
            );
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cureAndRaidDiscountsDoNotStackOrDisappear(GameTestHelper helper) {
        Villager villager = librarian(helper);
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 20),
                Optional.empty(),
                new ItemStack(Items.LEATHER_LEGGINGS),
                0,
                12,
                5,
                0.05F,
                1
        );
        villager.getOffers().clear();
        villager.getOffers().add(offer);

        villager.onReputationEventFrom(ReputationEventType.ZOMBIE_VILLAGER_CURED, player);
        int reputationAfterFirstCure = villager.getPlayerReputation(player);
        villager.onReputationEventFrom(ReputationEventType.ZOMBIE_VILLAGER_CURED, player);
        helper.assertValueEqual(
                villager.getPlayerReputation(player),
                reputationAfterFirstCure,
                "reputation after repeated infection and cure"
        );

        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 200, 0));
        villager.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(
                offer.getCostA().getCount(),
                8,
                "cure and raid price after demand pricing"
        );
        helper.succeed();
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
