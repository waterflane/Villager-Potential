package org.waterflane.villager_potential.core;

import java.util.Objects;

/** Loader-neutral configuration for identity, inheritance, careers and progression. */
public record VillagerPotentialConfig(
        AptitudeGenerationConfig aptitude,
        AptitudeInheritanceConfig inheritance,
        CareerProgressionConfig career,
        SkillProgressionConfig skill,
        ProfessionActivityConfig activity
) {
    public static final VillagerPotentialConfig DEFAULT = new VillagerPotentialConfig(
            new AptitudeGenerationConfig(
                    true,
                    0.5,
                    2.0,
                    1.0,
                    0.09,
                    new RareTalentConfig(true, 0.02, 3.0)
            ),
            new AptitudeInheritanceConfig(true, 0.7, 0.2, 1.0, 0.01),
            new CareerProgressionConfig(true, true, false, false),
            new SkillProgressionConfig(
                    true,
                    1.0 / 24_000.0,
                    1.0,
                    0.0,
                    10.5,
                    new ProfessionLevelThresholds(0.0, 1.5, 3.5, 6.5, 10.5)
            ),
            new ProfessionActivityConfig(true, 0.5, 1.0, 2.0, 0.1, 0.0)
    );

    public VillagerPotentialConfig {
        Objects.requireNonNull(aptitude, "aptitude");
        Objects.requireNonNull(inheritance, "inheritance");
        Objects.requireNonNull(career, "career");
        Objects.requireNonNull(skill, "skill");
        Objects.requireNonNull(activity, "activity");
    }
}
