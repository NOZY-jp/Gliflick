package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.combat

/** A branch is independent of its run rank; rank II is offered only after rank I. */
interface WeaponEvolution {
    val name: String
    val weapon: WeaponId
    val title: String
    val first: String
    val second: String
    val maxRank: Int
}

enum class SecondaryEvolution(
    override val weapon: WeaponId, override val title: String,
    override val first: String, override val second: String = "",
    val glyph: Int
) : WeaponEvolution {
    SUPERCHARGED(WeaponId.SPECTRE, "Supercharged", "20機ごとに強化機：ダメージ+100%、持続・速度+50%", glyph = 0xec2c),
    SELF_DESTRUCT(WeaponId.SPECTRE, "Self Destruct", "寿命終了時に爆発ダメージ50", "爆発ダメージ+50 / 爆発範囲+30%", 0xec2b),
    VOLATILE_FUEL(WeaponId.SPECTRE, "Volatile Fuel", "速度+50%", "速度+50% / 持続時間−25%", 0xec2d),
    STALKING_ALGORITHMS(WeaponId.SPECTRE, "Stalking Algorithms", "全ドローン：速度+35%", glyph = 0xec59),
    DEFENSE_GRID(WeaponId.SENTRY, "Defense Grid", "タレット+1", "タレット+1", 0xec28),
    SOLID_CORE(WeaponId.SENTRY, "Solid Core", "熱30をキネティック30へ変換", "キネティック+20 / 全キネティック武器：ダメージ+20%", 0xec2a),
    WATCHER_SUPREME(WeaponId.SENTRY, "Watcher Supreme", "全タレット：範囲+50%", glyph = 0xec48),
    ABUNDANT_COILS(WeaponId.TESLA_MINE, "Abundant Coils", "語頭y・zでも設置", "語頭v・wでも設置", 0xec2e),
    ELECTRICAL_BURN(WeaponId.TESLA_MINE, "Electrical Burn", "電気12を熱12へ変換", "熱+6 / 全熱武器：ダメージ+20%", 0xec2f),
    RESTRICTED_AREA(WeaponId.TESLA_MINE, "Restricted Area", "範囲内の敵：電気攻撃のStatic率+20%", "全Static：基礎ダメージ+5 / 範囲+30%", 0xec30),
    STORM_SOVEREIGN(WeaponId.TESLA_MINE, "Storm Sovereign", "全タレット：連射速度+25%", glyph = 0xec5a),
    TWIN_HEADED(WeaponId.OSCILLATOR, "Twin-Headed", "反対方向にも粒子列を発射", glyph = 0xec27),
    BALL_LIGHTNING(WeaponId.OSCILLATOR, "Ball Lightning", "熱10を電気10へ変換", "電気+6 / 全電気武器：ダメージ+20%", 0xec25),
    STABILIZED_PLASMA(WeaponId.OSCILLATOR, "Stabilized Plasma", "粒子が貫通。2体目以降は50%ダメージ", "全Ignite：基礎ダメージ+5", 0xec26),
    PRIME_RESONANCE(WeaponId.OSCILLATOR, "Prime Resonance", "全リモート武器：ダメージ+15%", glyph = 0xec5c),
    SURVEILLANCE(WeaponId.METEOR, "Surveillance", "照準候補を2個に増加", "照準候補を3個に増加", 0xec21),
    ICE_RAIN(WeaponId.METEOR, "Ice Rain", "爆発80を冷気80へ変換", "冷気+20 / 全冷気武器：ダメージ+20%", 0xec1f),
    RELENTLESS(WeaponId.METEOR, "Relentless", "連続爆撃へ変更。最後の標的へ移動 / 範囲・連射速度−50%", "速度+50% / Crit率+10%", 0xec20),
    SKY_SPOTTER(WeaponId.METEOR, "Sky Spotter", "全リモート武器：範囲+50%", glyph = 0xec5b),
    HUNTER_ELITE(WeaponId.HUNTER, "Hunter Elite", "12機ごとに強化機：ダメージ+100%、持続・速度・範囲+50%", glyph = 0xec1e),
    ELECTRIFIED_BOLTS(WeaponId.HUNTER, "Electrified Bolts", "電気ビームをキネティック弾10へ変更", "キネティック+5 / 全キネティック武器：ダメージ+20%", 0xec1d),
    CHAIN_LIGHTNING(WeaponId.HUNTER, "Chain Lightning", "追加の敵1体へ連鎖", "追加の敵2体へ連鎖", 0xec1c),
    PREDATOR_STRAIN(WeaponId.HUNTER, "Predator Strain", "全ドローン：持続時間+35%", glyph = 0xec58),
    DUAL_WIELD(WeaponId.CHOPPER, "Dual Wield", "チョッパー+1", glyph = 0xec1a),
    FLAMING_BLADES(WeaponId.CHOPPER, "Flaming Blades", "キネティック24を熱24へ変換", "熱+10 / 全熱武器：ダメージ+20%", 0xec1b),
    BOOMERANG(WeaponId.CHOPPER, "Boomerang", "画面端で折り返して自機へ戻る", "速度+75% / 回転速度+50%", 0xec19),
    BLOODBATH(WeaponId.CHOPPER, "Bloodbath", "出血中の敵へのダメージ+10%。撃破時10%でダメージ2倍", "出血中の敵へのダメージ+25%。撃破時25%でダメージ2倍", 0xec47),
    SAW_LAUNCHER(WeaponId.CHOPPER, "Saw Launcher", "小型ノコギリ2枚へ変更 / 出血率+7% / 全出血ダメージ+25% / Repeater出血率+7%", "ノコギリ+2 / 出血率+12% / 全出血ダメージ+50% / Repeater出血率+12%", 0xec46),
    MINELAYER(WeaponId.MINEFIELD, "Minelayer", "語頭y・zでも設置", "語頭v・wでも設置", 0xec24),
    INFUSION(WeaponId.MINEFIELD, "Infusion", "爆発150をケミカル150へ変換", "ケミカル+50 / Blight率+10% / 全ケミカル武器：ダメージ+20%", 0xec23),
    HUNTER_SEEKER(WeaponId.MINEFIELD, "Hunter Seeker", "敵を追尾 / 速度+200% / 範囲−50%", "地雷+1 / 速度+100%", 0xec22),
    RECURSIVE_MINES(WeaponId.MINEFIELD, "Recursive Mines", "12秒ごとに地雷数の半分（切り捨て）を自動生成", "生成速度+30% / 自動生成数+1", 0xec3d),
    BLADE_REPOSITORY(WeaponId.BLADE_TRAP, "Blade Repository", "語頭y・zでも設置", "語頭v・wでも設置", 0xec17),
    POISON_COATING(WeaponId.BLADE_TRAP, "Poison Coating", "ケミカル+5 / Blight率10%", "ケミカル+5 / Blight率+5%", 0xec16),
    SAWTOOTH_BLADES(WeaponId.BLADE_TRAP, "Sawtooth Blades", "Crit率を出血率に加算", "全出血：基礎ダメージ+5", 0xec18),
    CUTTING_EDGE(WeaponId.BLADE_TRAP, "Cutting Edge", "全タレット：Crit率+10%", glyph = 0xec57),
    CONTINUUM(WeaponId.ORBITER, "Continuum", "語頭y・zでも生成", "語頭v・wでも生成", 0xec45),
    STORM_RING(WeaponId.ORBITER, "Storm Ring", "ケミカル70を電気70へ変換", "電気+20 / Static率+5%", 0xec44),
    DOPPELGANGER(WeaponId.ORBITER, "Doppelganger", "逆方向へ周回するオーブを追加", glyph = 0xec43),
    TETHER(WeaponId.ORBITER, "Tether", "自機との実線に接触した敵へ30%ダメージ", "接続線のダメージを50%へ増加", 0xec42),
    TOXIC_WARD(WeaponId.GUARDIAN, "Toxic Ward", "熱20をケミカル20へ変換", "ケミカル+15", 0xec3f),
    EXPERIMENTAL_FUEL(WeaponId.GUARDIAN, "Experimental Fuel", "特殊燃料でダメージ・速度・範囲のいずれかを8秒強化（同時1種）", "特殊燃料の効果・持続時間+50%", 0xec40),
    GUARDIAN_TEAM(WeaponId.GUARDIAN, "Guardian Team", "護衛+1 / ダメージ−25%", "護衛+1 / ダメージ−25%", 0xec41);

    override val maxRank get() = if (second.isEmpty()) 1 else 2
}

/** Gameplay categories, not translated UI strings, define global evolution targets. */
enum class WeaponFamily { MAIN, DRONE, TURRET, REMOTE }
val WeaponId.family get() = when (this) {
    WeaponId.REPEATER -> WeaponFamily.MAIN
    WeaponId.SPECTRE, WeaponId.HUNTER, WeaponId.MINEFIELD, WeaponId.GUARDIAN -> WeaponFamily.DRONE
    WeaponId.SENTRY, WeaponId.TESLA_MINE, WeaponId.BLADE_TRAP -> WeaponFamily.TURRET
    WeaponId.OSCILLATOR, WeaponId.METEOR, WeaponId.CHOPPER, WeaponId.ORBITER -> WeaponFamily.REMOTE
}

object EvolutionCatalog {
    val triggerBranches = setOf(SecondaryEvolution.ABUNDANT_COILS, SecondaryEvolution.MINELAYER,
        SecondaryEvolution.BLADE_REPOSITORY, SecondaryEvolution.CONTINUUM)
    val extraLetterGroups = WeaponTriggerGlyphs.extraGroups
    fun branches(id: WeaponId): List<WeaponEvolution> = if (id == WeaponId.REPEATER)
        RepeaterEvolution.entries else SecondaryEvolution.entries.filter { it.weapon == id }

    fun available(state: WeaponRunState): List<WeaponEvolution> = branches(state.id).filter {
        state.rank(it) < it.maxRank && when (it) {
            SecondaryEvolution.SURVEILLANCE -> !state.has(SecondaryEvolution.RELENTLESS)
            SecondaryEvolution.RELENTLESS -> !state.has(SecondaryEvolution.SURVEILLANCE)
            else -> true
        }
    }

    fun offers(state: WeaponRunState, nextLetters: String? = null) = available(state).filter {
        it !in triggerBranches || nextLetters != ""
    }.map {
        val rank = state.rank(it)
        UpgradeOffer("evolve-${it.name}-${rank + 1}", state.id,
            it.title + if (rank > 0) " II" else "", if (it in triggerBranches && nextLetters != null) "${WeaponTriggerGlyphs.startingCondition(nextLetters)}でも発動"
            else if (rank == 0) it.first else it.second,
            UpgradeRarity.RARE, OfferKind.EVOLUTION, evolution = it)
    }
}
