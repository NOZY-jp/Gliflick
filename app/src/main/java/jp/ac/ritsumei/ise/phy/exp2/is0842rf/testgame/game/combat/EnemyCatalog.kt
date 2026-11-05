package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

/** Wiki health/contact values. Dimensions use 4.5dp / 7 source units, speeds preserve Drone tuning. */
object EnemyCatalog {
    const val UNIT = 4.5f / 7f
    const val SPEED = DroneTierCatalog.BASE_SPEED / 0.65f
    // Measured full silhouette bounds; the physical Shooter collider remains 15 source units.
    private val shooterVisualRadii = listOf(22.501f, 36.202f, 47.951f, 51.151f, 52.274f)
    fun definition(kind: EnemyKind, tier: Int = 1): EnemyDefinition = when (kind) {
        EnemyKind.BOSS -> error("Boss bodies are defined by BossDirector.")
        EnemyKind.DRONE -> DroneTierCatalog.definition(tier)
        EnemyKind.SHIELD_DRONE -> {
            require(tier in 1..5)
            val hp = listOf(100, 200, 400, 800, 2400)[tier - 1]
            val shield = listOf(200, 400, 800, 1600, 4800)[tier - 1]
            EnemyDefinition(kind = kind, maxHp = hp, maxShield = shield, contactDamage = tier * 50,
                radius = listOf(10f, 15f, 20f, 20f, 25f)[tier - 1] * UNIT,
                speed = DroneTierCatalog.BASE_SPEED, tier = tier,
                behaviorFactory = { RotatingPursuit() }, durabilityFactory = { RegeneratingShield(it, shield) })
        }
        EnemyKind.SHOOTER -> {
            require(tier in 1..5)
            EnemyDefinition(kind = kind, maxHp = listOf(300, 600, 1200, 2400, 7200)[tier - 1],
                contactDamage = 25, radius = shooterVisualRadii[tier - 1] * UNIT,
                collisionRadius = 15f * UNIT, speed = 1.5f * SPEED, tier = tier, behaviorFactory = { ShooterBehavior() })
        }
        EnemyKind.SHOOTER_MISSILE -> {
            require(tier in 1..5)
            EnemyDefinition(kind = kind, maxHp = listOf(200, 500, 1000, 2000, 6000)[tier - 1],
                contactDamage = listOf(40, 80, 160, 320, 320)[tier - 1],
                radius = (if (tier == 1) 15f else 20f) * UNIT, speed = 0.5f * SPEED,
                tier = tier, behaviorFactory = { MissileBehavior() })
        }
        EnemyKind.WARDER -> {
            require(tier == 1)
            EnemyDefinition(kind = kind, maxHp = 300, contactDamage = 0,
                radius = 13f * UNIT, collisionRadius = 10f * UNIT, speed = SPEED, behaviorFactory = { WarderBehavior() })
        }
        EnemyKind.SPLITTER -> {
            require(tier in 1..3)
            val count = tier + 3
            val hp = listOf(350, 500, 650)[tier - 1]
            val part = SplitterDefinition(count, hp, 15f * UNIT, (if (tier == 1) 10f else 15f) * UNIT)
            EnemyDefinition(kind = kind, maxHp = hp * count, contactDamage = 40,
                radius = 30f * UNIT, speed = DroneTierCatalog.BASE_SPEED,
                tier = tier, splitter = part, behaviorFactory = { SplitterBehavior() },
                durabilityFactory = { SplitterHealth(count, hp) })
        }
        EnemyKind.SPLITTER_SHARD -> {
            require(tier in 1..3)
            EnemyDefinition(kind = kind, maxHp = listOf(350, 500, 650)[tier - 1], contactDamage = 40,
                radius = 15f * UNIT, collisionRadius = (if (tier == 1) 10f else 15f) * UNIT,
                speed = 0.45f * SPEED, tier = tier, behaviorFactory = { ReleasedShardBehavior() })
        }
    }
}
