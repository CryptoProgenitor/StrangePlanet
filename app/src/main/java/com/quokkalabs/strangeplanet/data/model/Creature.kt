package com.quokkalabs.strangeplanet.data.model

import com.quokkalabs.strangeplanet.R

enum class CreatureType {
    ALIEN_MUM, ALIEN_DAD, ALIEN_KID, CAT, DOG, SOCKS, UNICORN, ROLLSUCK;

    val drawableRes: Int
        get() = when (this) {
            ALIEN_MUM -> R.drawable.sp_alien_mum
            ALIEN_DAD -> R.drawable.sp_alien_dad
            ALIEN_KID -> R.drawable.sp_alien_kid
            CAT -> R.drawable.sp_cat
            DOG -> R.drawable.sp_dog
            SOCKS -> R.drawable.sp_socks
            UNICORN -> R.drawable.sp_unicorn
            ROLLSUCK -> R.drawable.sp_rollsuck
        }

    val soundRes: Int?
        get() = when (this) {
            ALIEN_MUM, ALIEN_DAD, ALIEN_KID -> R.raw.sp_beep_boop
            CAT -> R.raw.sp_meow
            DOG -> R.raw.sp_woof
            UNICORN -> R.raw.sp_neigh
            SOCKS -> null
            ROLLSUCK -> null
        }

    val displayName: String
        get() = when (this) {
            ALIEN_MUM -> "Mum Being"
            ALIEN_DAD -> "Dad Being"
            ALIEN_KID -> "Small Being"
            CAT -> "Vibrating Creature"
            DOG -> "Loyal Creature"
            SOCKS -> "Fabric Foot Tubes"
            UNICORN -> "Horned Equine"
            ROLLSUCK -> "Rollsuck Supreme"
        }
}

data class CreatureState(
    val type: CreatureType,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val mass: Float,
    val size: Float,
    val rotation: Float,
    val angularVelocity: Float,
)

object CreatureDefaults {
    private data class Blueprint(
        val type: CreatureType,
        val xFraction: Float,
        val yFraction: Float,
        val radius: Float,
        val mass: Float,
        val size: Float,
        val spinMultiplier: Float = 1f,
    )

    private val blueprints = listOf(
        Blueprint(CreatureType.ALIEN_MUM, 0.15f, 0.35f, 45f, 1.2f, 100f),
        Blueprint(CreatureType.ALIEN_DAD, 0.25f, 0.40f, 52f, 1.5f, 115f),
        Blueprint(CreatureType.ALIEN_KID, 0.20f, 0.50f, 28f, 0.7f, 58f, 1.5f),
        Blueprint(CreatureType.CAT, 0.70f, 0.30f, 22f, 0.8f, 45f),
        Blueprint(CreatureType.DOG, 0.80f, 0.55f, 26f, 1.0f, 52f),
        Blueprint(CreatureType.SOCKS, 0.50f, 0.20f, 16f, 0.4f, 34f, 2f),
        Blueprint(CreatureType.UNICORN, 0.60f, 0.65f, 35f, 1.4f, 75f, 0.7f),
        Blueprint(CreatureType.ROLLSUCK, 0.40f, 0.70f, 30f, 1.3f, 70f, 0.4f),
    )

    private const val BASE_SPEED = 2.5f

    fun create(screenWidth: Float, screenHeight: Float): List<CreatureState> =
        blueprints.map { bp ->
            CreatureState(
                type = bp.type,
                x = screenWidth * bp.xFraction,
                y = screenHeight * bp.yFraction,
                vx = randomVelocity(),
                vy = randomVelocity(),
                radius = bp.radius,
                mass = bp.mass,
                size = bp.size,
                rotation = 0f,
                angularVelocity = randomSpin() * bp.spinMultiplier,
            )
        }

    private fun randomVelocity(): Float =
        (Math.random().toFloat() - 0.5f) * BASE_SPEED * 2f

    private fun randomSpin(): Float =
        (Math.random().toFloat() - 0.5f) * 3f
}
