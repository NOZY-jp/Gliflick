package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

import kotlin.random.Random

enum class DamageType { KINETIC, HEAT, ELECTRIC, EXPLOSIVE, CHEMICAL, COLD }

enum class WeaponId(val title: String, val tags: String, val description: String) {
    REPEATER("リピーター", "キネティック / メイン", "標的へ短いバーストで弾丸を放つ。"),
    SPECTRE("スペクター", "キネティック / ドローン", "語を打ち終えると、${WeaponTriggerGlyphs.ROMAN_REFERENCE}に含まれる ${WeaponTriggerGlyphs.label(WeaponTriggerGlyphs.SPECTRE)} の数だけ近接ドローンを召喚。"),
    SENTRY("セントリー", "熱 / タレット", "射程内の敵を自動で狙う常設タレット。"),
    TESLA_MINE("テスラマイン", "電気 / タレット / D.O.T.", "${WeaponTriggerGlyphs.startingCondition(WeaponTriggerGlyphs.TESLA)}を完成すると電撃タレットを設置。"),
    OSCILLATOR("オシレーター", "熱 / リモート", "最後の標的の方向へ、揺れる粒子の流れを放つ。"),
    METEOR("メテオ", "爆発 / リモート", "定期的に照準マーカーを展開。マーカーの語を打つと爆撃。"),
    HUNTER("ハンター", "電気 / ドローン / D.O.T.", "語を打ち終えると、${WeaponTriggerGlyphs.ROMAN_REFERENCE}に含まれる ${WeaponTriggerGlyphs.label(WeaponTriggerGlyphs.HUNTER)} の数だけ電撃ドローンを召喚。"),
    CHOPPER("チョッパー", "キネティック / リモート", "メイン武器の発射時、刃の空き枠があれば回転刃を放つ。文字条件なし。通常1枠で、画面外へ抜けると再使用可能。"),
    MINEFIELD("マインフィールド", "爆発 / ドローン", "${WeaponTriggerGlyphs.startingCondition(WeaponTriggerGlyphs.MINEFIELD)}を完成すると接触爆発する地雷を展開。"),
    BLADE_TRAP("ブレードトラップ", "キネティック / タレット", "${WeaponTriggerGlyphs.startingCondition(WeaponTriggerGlyphs.BLADE)}を完成すると四枚刃の回転トラップを設置。"),
    ORBITER("オービター", "ケミカル / リモート", "${WeaponTriggerGlyphs.startingCondition(WeaponTriggerGlyphs.ORBITER)}を完成すると、自機を周回するオーブを展開。"),
    GUARDIAN("ガーディアン", "熱 / ドローン", "火球を回す護衛ドローンを常設。燃料の語を打って活動時間を補給。")
}

enum class WeaponStat(val title: String) {
    DAMAGE("ダメージ"), RANGE("範囲"), DURATION("持続時間"), RATE("連射速度"),
    SPEED("移動速度"), SPREAD("拡散"), CRIT("Crit率"), CRIT_MULTIPLIER("Crit倍率"),
    EVOLUTION("進化"), MINE_COUNT("地雷数")
}

class WeaponRunState(val id: WeaponId) {
    private val bonuses = mutableMapOf<WeaponStat, Float>()
    val evolutions = mutableMapOf<WeaponEvolution, Int>()
    var extraTriggerLetters = ""; internal set
    var level = 1; private set
    var evolutionCredit = 0; private set
    var evolutionsChosen = 0; private set
    // Public demo: 50 kills, then 100 more, then 150 more; three choices per run.
    private val milestones = listOf(50, 150, 300)
    val nextEvolutionCredit get() = milestones.getOrNull(evolutionsChosen) ?: 300
    val isEvolutionComplete get() = evolutionsChosen >= milestones.size
    val evolutionProgress get() = if (isEvolutionComplete) 1f else {
        val previous = if (evolutionsChosen == 0) 0 else milestones[evolutionsChosen - 1]
        ((evolutionCredit - previous).toFloat() / (nextEvolutionCredit - previous)).coerceIn(0f, 1f)
    }
    val pendingEvolutionChoices get() = minOf(
        (milestones.count { evolutionCredit >= it } - evolutionsChosen).coerceAtLeast(0),
        EvolutionCatalog.available(this).sumOf { it.maxRank - rank(it) })
    val canEvolve get() = pendingEvolutionChoices > 0
    internal var globalBonus: (WeaponStat) -> Float = { 0f }
    internal var temporaryBonus: (WeaponStat) -> Float = { 0f }
    fun bonus(stat: WeaponStat) = (bonuses[stat] ?: 0f) + EvolutionEffects.localBonus(this, stat) +
        globalBonus(stat) + temporaryBonus(stat)
    fun scale(stat: WeaponStat) = (1f + bonus(stat)).coerceAtLeast(0.05f)
    fun rank(evolution: WeaponEvolution) = evolutions[evolution] ?: 0
    fun has(evolution: WeaponEvolution) = rank(evolution) > 0
    fun creditKills(count: Int = 1) { require(count >= 0); evolutionCredit += count }
    fun improve(effects: List<StatDelta>) {
        effects.forEach {
            if (it.stat == WeaponStat.EVOLUTION) creditKills(it.amount.toInt())
            else bonuses[it.stat] = (bonuses[it.stat] ?: 0f) + it.amount
        }
        level++
    }
    fun evolve(evolution: WeaponEvolution) {
        require(evolution.weapon == id && !isEvolutionComplete && evolution in EvolutionCatalog.available(this))
        evolutions[evolution] = rank(evolution) + 1
        evolutionsChosen++
        level++
    }
}

enum class RepeaterEvolution(override val title: String, override val first: String, override val second: String) : WeaponEvolution {
    SHOTGUN("ショットガン", "発射体 +5 / 拡散 +400% / 基礎ダメージ −10", "発射体 +5 / 拡散 +100%"),
    RICOCHET("跳弾", "近くの敵へ3回跳弾。2体目以降は50%ダメージ。", "跳弾回数 +2"),
    FROST_FANG("フロストファング", "冷気ダメージ +10 / 凍結確率 3%", "冷気 +20 / キネティック −10"),
    EXPLODING_BULLETS("爆発弾", "爆発 +10 / キネティック −10 / 命中時に範囲攻撃", "爆発 +15 / キネティック −10 / 爆発範囲を拡大");
    override val weapon get() = WeaponId.REPEATER
    override val maxRank get() = 2
}

enum class UpgradeRarity(val title: String) { COMMON("コモン"), UNCOMMON("アンコモン"), RARE("レア") }
enum class OfferKind(val title: String) { ACQUIRE("新武器"), UPGRADE("アップグレード"), EVOLUTION("進化") }
data class StatDelta(val stat: WeaponStat, val amount: Float)
data class UpgradeOffer(
    val id: String, val weapon: WeaponId, val title: String, val description: String,
    val rarity: UpgradeRarity = UpgradeRarity.COMMON, val kind: OfferKind = OfferKind.UPGRADE,
    val effects: List<StatDelta> = emptyList(), val evolution: WeaponEvolution? = null
)

/** Catalog data describes effects. Applying them belongs to the world/arsenal. */
object UpgradeCatalog {
    private data class Group(val stat: WeaponStat, val names: List<String>)
    private fun group(stat: WeaponStat, names: String) = Group(stat, names.split("|"))
    private val secondaryGroups = mapOf(
        WeaponId.SPECTRE to listOf(
            group(WeaponStat.DAMAGE, "Swarm Tactics|Coordinated Assault|Hive Mind Strategy"),
            group(WeaponStat.DURATION, "Ricodium Batteries|Energized Cells|Power Core Overcharge")),
        WeaponId.SENTRY to listOf(
            group(WeaponStat.DAMAGE, "Armor Piercing Rounds|Tactical Armor Shredders|Exo-Penetrating Munitions"),
            group(WeaponStat.RATE, "Hair Trigger|Rapid Fire Accelerator|Blitz Protocol"),
            group(WeaponStat.RANGE, "Magnified Optics|Enhanced Scopes|Precision Sight Systems")),
        WeaponId.TESLA_MINE to listOf(
            group(WeaponStat.DAMAGE, "Overcharge|Voltage Surge|Tempest Amplification"),
            group(WeaponStat.RANGE, "Amplified Field|Horizon Reach|Infinitum"),
            group(WeaponStat.DURATION, "Reinforced Coils|Resilient Conductors|Hypercharged Induction")),
        WeaponId.OSCILLATOR to listOf(
            group(WeaponStat.DAMAGE, "Plasma Ignition|Quantum Discharge|Nova Eruption"),
            group(WeaponStat.SPREAD, "Carpet Bombing|Orbic Reach|Quantum Expansion")),
        WeaponId.METEOR to listOf(
            group(WeaponStat.DAMAGE, "Precision Bombardment|Impact Enhancer|Apocalypse"),
            group(WeaponStat.RATE, "Relay Accelerator|Launch Sequencer|Rain of Death"),
            group(WeaponStat.RANGE, "Longshot Module|Amplification Array|Cataclysmic Reach")),
        WeaponId.HUNTER to listOf(
            group(WeaponStat.DAMAGE, "Electric Surge|Squall Intensifier|Quantum Overcharge"),
            group(WeaponStat.RANGE, "Focused Beam|Directed Discharge|Lightning Cascade"),
            group(WeaponStat.DURATION, "Energy-Efficiency|Optimized Destruction|Perpetual Matrix")),
        WeaponId.CHOPPER to listOf(
            group(WeaponStat.DAMAGE, "Sharpened Edges|Tempered Blade|Razor's Edge"),
            group(WeaponStat.RATE, "Rotor Enhancements|Turbo Rotation|Hyperdrive Rotors"),
            group(WeaponStat.RANGE, "Lengthened Blades|Extended Reach|Longreach Masters")),
        WeaponId.MINEFIELD to listOf(
            group(WeaponStat.DAMAGE, "Payload Booster|Heavy Ordnance|Warhead Amplification"),
            group(WeaponStat.RANGE, "Reach Expansion|Battlefield Coverage|Boundless Radius")),
        WeaponId.BLADE_TRAP to listOf(
            group(WeaponStat.DAMAGE, "Hardened Cutters|Titanium Cutters|Diamond Cutters"),
            group(WeaponStat.RATE, "Lubricated Gears|Precision Gears|Quantum Gearbox"),
            group(WeaponStat.DURATION, "Improved Clockwork|Faultless Clockwork|Ricodium Chargers")),
        WeaponId.ORBITER to listOf(
            group(WeaponStat.DAMAGE, "Torque Spiker|Momentum Core|Perihelion"),
            group(WeaponStat.RANGE, "Vector Ring|Orbit Extender|Radialix"),
            group(WeaponStat.DURATION, "Spin Stabilizer|Tempo Anchor|Continuance"),
            group(WeaponStat.SPEED, "Pulse Engine|Gyro Drive|Whirlwinder")),
        WeaponId.GUARDIAN to listOf(
            group(WeaponStat.DAMAGE, "Vanguard Impact|Defender's Wrath|Sentinel's Fury"),
            group(WeaponStat.RANGE, "Sharpshooter Lens|Longshot Enhancer|Watcher's Precision"),
            group(WeaponStat.DURATION, "Unyielding Presence|Timeless Ember|Eternal Flame"),
            group(WeaponStat.SPEED, "Lightning Reflexes|Swift Guardian|Rapid Vigilance"))
    )

    fun pool(world: CombatWorld): List<UpgradeOffer> = buildList {
        if (world.secondaryWeapons.size < 4) WeaponId.entries.filter {
            it != WeaponId.REPEATER && world.secondaryWeapons.none { weapon -> weapon.id == it }
        }.forEach { add(UpgradeOffer("new-" + it.name, it, it.title, it.description, kind = OfferKind.ACQUIRE)) }
        if (world.mainWeapon is Repeater) {
            val damageNames = listOf("フルメタル・ジャケット", "徹甲弾アップグレード", "貫通力強化")
            val critNames = listOf("ターゲット力強化", "精密誘導システム", "イーグルアイ")
            val learningNames = listOf("知識の増幅", "戦闘データ", "熟練の指導")
            UpgradeRarity.entries.forEachIndexed { tier, rarity ->
                val percent = (tier + 1) * 10
                add(UpgradeOffer("repeater-damage-" + tier, WeaponId.REPEATER, damageNames[tier],
                    "ダメージ +" + percent + "%", rarity,
                    effects = listOf(StatDelta(WeaponStat.DAMAGE, percent / 100f))))
                add(UpgradeOffer("repeater-crit-" + tier, WeaponId.REPEATER, critNames[tier],
                    "Crit率 +" + (tier + 1) + "% / Crit倍率 +" + listOf(50, 100, 200)[tier] + "%", rarity,
                    effects = listOf(StatDelta(WeaponStat.CRIT, (tier + 1) / 100f),
                        StatDelta(WeaponStat.CRIT_MULTIPLIER, listOf(0.5f, 1f, 2f)[tier]))))
                if (!world.mainWeapon.upgrades.isEvolutionComplete)
                    add(UpgradeOffer("repeater-learning-" + tier, WeaponId.REPEATER, learningNames[tier],
                        "進化の撃破カウント +" + percent, rarity,
                        effects = listOf(StatDelta(WeaponStat.EVOLUTION, percent.toFloat()))))
            }
            if (world.mainWeapon.upgrades.rank(RepeaterEvolution.EXPLODING_BULLETS) > 0)
                add(UpgradeOffer("repeater-payload", WeaponId.REPEATER, "ペイロードブースター", "爆発範囲 +25%",
                    effects = listOf(StatDelta(WeaponStat.RANGE, 0.25f))))
        }
        world.secondaryWeapons.forEach { weapon ->
            if (!weapon.state.isEvolutionComplete) UpgradeRarity.entries.forEachIndexed { tier, rarity ->
                val count = (tier + 1) * 10
                add(UpgradeOffer("${weapon.id}-evolution-$tier", weapon.id,
                    EvolutionEffects.learningNames.getValue(weapon.id)[tier], "進化の撃破カウント +$count", rarity,
                    effects = listOf(StatDelta(WeaponStat.EVOLUTION, count.toFloat()))))
            }
            secondaryGroups[weapon.id].orEmpty().forEach { group ->
                UpgradeRarity.entries.forEachIndexed { tier, rarity ->
                    val amount = (tier + 1) * 10
                    add(UpgradeOffer(weapon.id.name + "-" + group.stat + "-" + tier, weapon.id,
                        group.names[tier], group.stat.title + " +" + amount + "%", rarity,
                        effects = listOf(StatDelta(group.stat, amount / 100f))))
                }
            }
            if (weapon.id == WeaponId.MINEFIELD) add(UpgradeOffer("mine-count", weapon.id,
                "Rapid Fabricator", "1回の発動で生成する地雷 +1",
                effects = listOf(StatDelta(WeaponStat.MINE_COUNT, 1f))))
        }
    }

    fun roll(world: CombatWorld, random: Random, evolution: Boolean = false,
             evolutionWeapon: WeaponId? = null): List<UpgradeOffer> {
        val state = if (evolution) evolutionWeapon?.let(world::weaponState)
            ?: world.weaponStates.firstOrNull { it.canEvolve } else null
        val pool = (if (evolution) state?.let { world.evolutionOffers(it.id) }.orEmpty()
            else pool(world)).toMutableList()
        val result = mutableListOf<UpgradeOffer>()
        while (result.size < 3 && pool.isNotEmpty()) {
            val weights = pool.map { when (it.rarity) { UpgradeRarity.COMMON -> 14; UpgradeRarity.UNCOMMON -> 5; UpgradeRarity.RARE -> 1 } }
            var draw = random.nextInt(weights.sum())
            val index = weights.indexOfFirst { draw -= it; draw < 0 }
            result += pool.removeAt(index)
        }
        return result
    }
}
