package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat.SecondaryEvolution.*

/** Stateless rules shared by live actors, newly emitted attacks and future equipment. */
object EvolutionEffects {
    val learningNames = mapOf(
        WeaponId.SPECTRE to "Ghostly Learnings|Spectral Expertise|Phantasmagoria",
        WeaponId.SENTRY to "Keen Observer|Vigilant Sentinel|Eternal Overwatch",
        WeaponId.TESLA_MINE to "Conductive Mastery|Voltage Expertise|Amplificatorium",
        WeaponId.OSCILLATOR to "Precision Harmonics|Adaptive Resonator|Waveforma",
        WeaponId.METEOR to "Eager Crew|Orbital Insight|Veteran Sightfinder",
        WeaponId.HUNTER to "Sharpened Focus|Shock Precision|Predator",
        WeaponId.CHOPPER to "Sharpener|Incision Expertise|Guillotine",
        WeaponId.MINEFIELD to "AI Detonator|Precision Ballistics|Devastator",
        WeaponId.BLADE_TRAP to "Refined Snare|Reactive Vortex|Lacerator",
        WeaponId.ORBITER to "Spiral Ascendancy|Cyclic Wisdom|Mindloop",
        WeaponId.GUARDIAN to "Eternal Sentinel|Supreme Protector|Ascendant Aegis"
    ).mapValues { it.value.split('|') }

    fun localBonus(s: WeaponRunState, stat: WeaponStat): Float = when (stat) {
        WeaponStat.SPEED -> s.rank(VOLATILE_FUEL) * 0.5f +
            (if (s.has(HUNTER_SEEKER)) 2f + if (s.rank(HUNTER_SEEKER) == 2) 1f else 0f else 0f) +
            (if (s.rank(BOOMERANG) == 2) 0.75f else 0f) + (if (s.rank(RELENTLESS) == 2) 0.5f else 0f)
        WeaponStat.DURATION -> if (s.rank(VOLATILE_FUEL) == 2) -0.25f else 0f
        WeaponStat.RANGE -> (if (s.has(HUNTER_SEEKER)) -0.5f else 0f) + (if (s.has(RELENTLESS)) -0.5f else 0f)
        WeaponStat.RATE -> (if (s.rank(BOOMERANG) == 2) 0.5f else 0f) +
            (if (s.has(RELENTLESS)) -0.5f else 0f) + (if (s.rank(RECURSIVE_MINES) == 2) 0.3f else 0f)
        WeaponStat.DAMAGE -> -s.rank(GUARDIAN_TEAM) * 0.25f
        WeaponStat.CRIT -> if (s.rank(RELENTLESS) == 2) 0.1f else 0f
        else -> 0f
    }

    fun globalBonus(states: List<WeaponRunState>, id: WeaponId, stat: WeaponStat): Float {
        fun has(e: SecondaryEvolution, rank: Int = 1) = states.any { it.rank(e) >= rank }
        return when (stat) {
            WeaponStat.SPEED -> if (id.family == WeaponFamily.DRONE && has(STALKING_ALGORITHMS)) 0.35f else 0f
            WeaponStat.DURATION -> if (id.family == WeaponFamily.DRONE && has(PREDATOR_STRAIN)) 0.35f else 0f
            WeaponStat.RANGE -> when (id.family) {
                WeaponFamily.TURRET -> if (has(WATCHER_SUPREME)) 0.5f else 0f
                WeaponFamily.REMOTE -> if (has(SKY_SPOTTER)) 0.5f else 0f
                else -> 0f
            }
            WeaponStat.RATE -> if (id.family == WeaponFamily.TURRET && has(STORM_SOVEREIGN)) 0.25f else 0f
            WeaponStat.CRIT -> if (id.family == WeaponFamily.TURRET && has(CUTTING_EDGE)) 0.1f else 0f
            WeaponStat.DAMAGE -> if (id.family == WeaponFamily.REMOTE && has(PRIME_RESONANCE)) 0.15f else 0f
            else -> 0f
        }
    }

    fun type(s: WeaponRunState, original: DamageType): DamageType = when {
        s.has(SOLID_CORE) || s.has(ELECTRIFIED_BOLTS) -> DamageType.KINETIC
        s.has(ELECTRICAL_BURN) || s.has(FLAMING_BLADES) -> DamageType.HEAT
        s.has(BALL_LIGHTNING) || s.has(STORM_RING) -> DamageType.ELECTRIC
        s.has(ICE_RAIN) -> DamageType.COLD
        s.has(INFUSION) || s.has(TOXIC_WARD) -> DamageType.CHEMICAL
        else -> original
    }

    fun extraDamage(s: WeaponRunState): Float = when {
        s.rank(SOLID_CORE) == 2 || s.rank(ICE_RAIN) == 2 || s.rank(STORM_RING) == 2 -> 20f
        s.rank(ELECTRICAL_BURN) == 2 || s.rank(BALL_LIGHTNING) == 2 -> 6f
        s.rank(ELECTRIFIED_BOLTS) == 2 -> 5f
        s.rank(FLAMING_BLADES) == 2 -> 10f // Wiki value; demo effect says 12 despite its description saying 10.
        s.rank(INFUSION) == 2 -> 50f
        s.rank(TOXIC_WARD) == 2 -> 15f
        else -> 0f
    }

    fun damageTypeBonus(states: List<WeaponRunState>, type: DamageType): Float {
        fun rank(e: SecondaryEvolution) = states.maxOfOrNull { it.rank(e) } ?: 0
        return when (type) {
            DamageType.KINETIC -> (if (rank(SOLID_CORE) == 2) 0.2f else 0f) + (if (rank(ELECTRIFIED_BOLTS) == 2) 0.2f else 0f)
            DamageType.HEAT -> (if (rank(ELECTRICAL_BURN) == 2) 0.2f else 0f) + (if (rank(FLAMING_BLADES) == 2) 0.2f else 0f)
            DamageType.ELECTRIC -> if (rank(BALL_LIGHTNING) == 2) 0.2f else 0f
            DamageType.COLD -> if (rank(ICE_RAIN) == 2) 0.2f else 0f
            DamageType.CHEMICAL -> if (rank(INFUSION) == 2) 0.2f else 0f
            else -> 0f
        }
    }

    fun triggerLetters(s: WeaponRunState, base: String): String = base + s.extraTriggerLetters
}
