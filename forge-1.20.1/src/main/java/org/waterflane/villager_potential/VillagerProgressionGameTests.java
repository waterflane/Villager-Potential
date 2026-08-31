package org.waterflane.villager_potential;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SuspiciousStewItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.waterflane.villager_potential.core.ProfessionId;

@GameTestHolder(Villager_potential.MODID)
@PrefixGameTestTemplate(false)
public final class VillagerProgressionGameTests {
    private static final ProfessionId LIBRARIAN = ProfessionId.parse("minecraft:librarian");

    private VillagerProgressionGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void earnedSkillQueuesTheNextLevel(GameTestHelper helper) {
        Villager villager = librarian(helper);
        helper.runAfterDelay(5L, () -> {
            villager.setVillagerData(
                    villager.getVillagerData()
                            .setProfession(VillagerProfession.LIBRARIAN)
                            .setLevel(1)
            );
            rememberJobSite(helper, villager);
            VillagerPotentialAttachments.trackProfession(
                    villager,
                    helper.getLevel().getGameTime()
            );
            double apprenticeSkill = ServerConfig.gameplayConfig().skill()
                    .professionLevelThresholds().apprenticeSkill();
            VillagerPotentialAttachments.adminSetSkill(
                    villager,
                    LIBRARIAN,
                    apprenticeSkill
            );
            helper.assertTrue(
                    !((VillagerLevelUpAccess) villager).villagerPotential$queueLevelUp(),
                    "earned skill did not queue the vanilla level-up"
            );
            helper.assertTrue(villager.getVillagerData().getLevel() == 1,
                    "queued profession level was applied without vanilla's delay");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void tradesAloneCannotScheduleAProfessionLevel(GameTestHelper helper) {
        Villager villager = librarian(helper);
        MerchantOffer offer = villager.getOffers().get(0);
        villager.setTradingPlayer(helper.makeMockPlayer());
        for (int trade = 0; trade < 100; trade++) {
            villager.notifyTrade(offer);
        }
        villager.setTradingPlayer(null);

        helper.assertTrue(villager.getVillagerData().getLevel() == 1,
                "profession level was not 1");
        helper.runAfterDelay(45L, () -> {
            helper.assertTrue(villager.getVillagerData().getLevel() == 1,
                    "profession level changed after vanilla's former trade-XP delay");
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
        giveJobSite(helper, villager);

        helper.succeedWhen(() -> {
            var state = VillagerPotentialAttachments.get(villager);
            helper.assertTrue(
                    state.careerFor(LIBRARIAN).isPresent(),
                    "profession career has not initialized yet"
            );
            double expectedMinimum = ServerConfig.gameplayConfig().skill()
                    .professionLevelThresholds().thresholdForLevel(4);
            helper.assertTrue(
                    Double.compare(
                            state.careerFor(LIBRARIAN).orElseThrow().learnedSkill(),
                            expectedMinimum
                    ) == 0,
                    "bootstrapped profession skill was unexpected"
            );
            helper.assertTrue(villager.getVillagerData().getLevel() == 4,
                    "profession level was not 4");
            helper.assertTrue(
                    state.tradePalettes().isEmpty(),
                    "migration invented trade memory"
            );
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void capabilityMirrorsIntoPortableEntityNbt(GameTestHelper helper) {
        Villager villager = librarian(helper);
        VillagerPotentialAttachments.adminSetAptitude(villager, LIBRARIAN, 1.75);

        CompoundTag saved = villager.saveWithoutId(new CompoundTag());
        helper.assertTrue(
                saved.contains(VillagerPotentialPortableData.ROOT_KEY, CompoundTag.TAG_COMPOUND),
                "portable Potential container was not written"
        );

        Villager loaded = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 1));
        loaded.load(saved);
        helper.assertTrue(
                Double.compare(
                        VillagerPotentialAttachments.get(loaded).aptitudeFor(LIBRARIAN)
                                .orElse(Double.NaN),
                        1.75
                ) == 0,
                "portable Potential state did not round-trip"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void legacyItemMetadataProducesStableTradeKeys(GameTestHelper helper) {
        ItemStack firstBook = new ItemStack(Items.ENCHANTED_BOOK);
        firstBook.enchant(Enchantments.UNBREAKING, 3);
        ItemStack secondBook = new ItemStack(Items.ENCHANTED_BOOK);
        secondBook.enchant(Enchantments.UNBREAKING, 3);
        MerchantOffer first = new MerchantOffer(
                new ItemStack(Items.EMERALD, 12),
                new ItemStack(Items.BOOK),
                firstBook,
                12,
                5,
                0.05F
        );
        MerchantOffer second = new MerchantOffer(
                new ItemStack(Items.EMERALD, 12),
                new ItemStack(Items.BOOK),
                secondBook,
                12,
                5,
                0.05F
        );

        helper.assertTrue(MerchantOfferTradeKeys.identify(first).stable(),
                "vanilla enchantment metadata was marked unstable");
        helper.assertTrue(MerchantOfferTradeKeys.from(first).equals(
                        MerchantOfferTradeKeys.from(second)),
                "equivalent enchantment metadata produced different keys");

        ItemStack namedLeather = new ItemStack(Items.LEATHER_CHESTPLATE);
        namedLeather.setHoverName(Component.literal("Journeyman's coat"));
        namedLeather.getOrCreateTagElement("display").putInt("color", 0x336699);
        ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
        ItemStack stew = new ItemStack(Items.SUSPICIOUS_STEW);
        SuspiciousStewItem.saveMobEffect(stew, MobEffects.NIGHT_VISION, 100);
        var namedIdentity = MerchantOfferTradeKeys.identify(resultOffer(namedLeather));
        helper.assertTrue(namedIdentity.stable(),
                "custom name or dyed color metadata was unstable: " + namedIdentity.key());
        helper.assertTrue(MerchantOfferTradeKeys.identify(resultOffer(potion)).stable(),
                "potion metadata was unstable");
        helper.assertTrue(MerchantOfferTradeKeys.identify(resultOffer(stew)).stable(),
                "stew metadata was unstable");

        ItemStack firstMap = new ItemStack(Items.FILLED_MAP);
        firstMap.getOrCreateTag().putInt("map", 12);
        ItemStack secondMap = new ItemStack(Items.FILLED_MAP);
        secondMap.getOrCreateTag().putInt("map", 99);
        helper.assertTrue(
                MerchantOfferTradeKeys.from(resultOffer(firstMap)).equals(
                        MerchantOfferTradeKeys.from(resultOffer(secondMap))),
                "dynamic map id affected logical identity"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void demandStockMixinChangesEffectiveMaximum(GameTestHelper helper) {
        MerchantOffer offer = new MerchantOffer(
                new ItemStack(Items.EMERALD),
                new ItemStack(Items.BOOK),
                0,
                12,
                0.05F
        );
        DemandStockOffer stock = (DemandStockOffer) offer;
        stock.villagerPotential$setEffectiveMaximumUses(15);

        helper.assertTrue(offer.getMaxUses() == 15,
                "demand stock mixin did not expose the effective maximum");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cureAndRaidDiscountsDoNotStackOrDisappear(GameTestHelper helper) {
        Villager villager = librarian(helper);
        var player = helper.makeMockPlayer();
        MerchantOffer offer = new MerchantOffer(
                new ItemStack(Items.EMERALD, 20),
                ItemStack.EMPTY,
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
        helper.assertTrue(
                villager.getPlayerReputation(player) == reputationAfterFirstCure,
                "a repeated infection and cure stacked its discount"
        );

        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 200, 0));
        villager.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                offer.getCostA().getCount() == 8,
                "cure and raid price was changed by demand pricing"
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
        giveJobSite(helper, villager);
        return villager;
    }

    private static MerchantOffer resultOffer(ItemStack result) {
        return new MerchantOffer(
                new ItemStack(Items.EMERALD),
                result,
                12,
                5,
                0.05F
        );
    }

    private static void giveJobSite(GameTestHelper helper, Villager villager) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        BlockPos relativeJobSite = new BlockPos(1, 1, 2);
        helper.setBlock(relativeJobSite, Blocks.LECTERN);
        BlockPos absoluteJobSite = helper.absolutePos(relativeJobSite);
        helper.getLevel().getPoiManager().take(
                VillagerProfession.LIBRARIAN.heldJobSite(),
                (type, position) -> position.equals(absoluteJobSite),
                absoluteJobSite,
                1
        ).orElseThrow(() -> new IllegalStateException("lectern POI was not registered"));
        rememberJobSite(helper, villager);
    }

    private static void rememberJobSite(GameTestHelper helper, Villager villager) {
        villager.getBrain().setMemory(
                MemoryModuleType.JOB_SITE,
                GlobalPos.of(
                        helper.getLevel().dimension(),
                        helper.absolutePos(new BlockPos(1, 1, 2))
                )
        );
    }
}
