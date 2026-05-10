package com.quokkalabs.strangeplanet.data.repository

import com.quokkalabs.strangeplanet.data.model.CreatureType
import com.quokkalabs.strangeplanet.data.model.DayType
import com.quokkalabs.strangeplanet.data.model.Saying
import com.quokkalabs.strangeplanet.data.model.TimeOfDay

class SayingsRepository {

    fun getSaying(
        creatureType: CreatureType,
        timeOfDay: TimeOfDay,
        dayType: DayType,
    ): String {
        val candidates = allSayings
            .filter { it.creatureType == creatureType }
            .filter { timeOfDay in it.timesOfDay }
            .filter { dayType in it.dayTypes }

        if (candidates.isEmpty()) {
            return fallbackSayings[creatureType] ?: "..."
        }
        return candidates.random().text
    }

    companion object {
        private val fallbackSayings = mapOf(
            CreatureType.ALIEN_MUM to "I experience concern for your wellbeing.",
            CreatureType.ALIEN_DAD to "I shall contemplate the nature of existence.",
            CreatureType.ALIEN_KID to "I have detected something fascinating!",
            CreatureType.CAT to "I require your attention immediately.",
            CreatureType.DOG to "I am experiencing maximum enthusiasm!",
            CreatureType.SOCKS to "I am fabric foot tubes. I contain toes.",
            CreatureType.UNICORN to "The cosmos whispers ancient melodies.",
            CreatureType.ROLLSUCK to "I consume floor debris. It is my purpose.",
        )

        private val schoolDays = setOf(DayType.SCHOOL_DAY)
        private val freeDays = setOf(DayType.WEEKEND, DayType.HALF_TERM, DayType.HOLIDAY)
        private val holidays = setOf(DayType.HOLIDAY, DayType.HALF_TERM)
        private val allDays = DayType.entries.toSet()
        private val morning = setOf(TimeOfDay.MORNING)
        private val afternoon = setOf(TimeOfDay.AFTERNOON)
        private val evening = setOf(TimeOfDay.EVENING)
        private val night = setOf(TimeOfDay.NIGHT)
        private val allTimes = TimeOfDay.entries.toSet()

        private val allSayings: List<Saying> = buildList {
            // ═══════════════════════════════════════
            // ALIEN MUM — nurturing, practical
            // ═══════════════════════════════════════
            add(Saying("Time to fuel your body vessel with morning sustenance.", CreatureType.ALIEN_MUM, allDays, morning))
            add(Saying("Have you encased your body in appropriate fabric layers?", CreatureType.ALIEN_MUM, allDays, morning))
            add(Saying("I am calculating the optimal fabric layers for the current atmospheric conditions.", CreatureType.ALIEN_MUM, allDays, morning))
            add(Saying("The knowledge absorption facility awaits your arrival.", CreatureType.ALIEN_MUM, schoolDays, morning))
            add(Saying("You may remain horizontal for additional time units today.", CreatureType.ALIEN_MUM, freeDays, morning))
            add(Saying("I have prepared mid-rotation sustenance for consumption.", CreatureType.ALIEN_MUM, allDays, afternoon))
            add(Saying("How were the knowledge absorption sessions today?", CreatureType.ALIEN_MUM, schoolDays, afternoon))
            add(Saying("The dwelling requires superficial organisation before the dark period.", CreatureType.ALIEN_MUM, allDays, afternoon))
            add(Saying("Evening sustenance shall be served shortly.", CreatureType.ALIEN_MUM, allDays, evening))
            add(Saying("Have you completed your mandatory liquid hygiene rituals?", CreatureType.ALIEN_MUM, allDays, evening))
            add(Saying("Commence your consciousness deactivation preparations.", CreatureType.ALIEN_MUM, allDays, night))
            add(Saying("I experience strong affection for you, small being.", CreatureType.ALIEN_MUM, allDays, allTimes))
            add(Saying("The extended freedom period brings me contentment.", CreatureType.ALIEN_MUM, holidays, allTimes))

            // ═══════════════════════════════════════
            // ALIEN DAD — philosophical, pondering
            // ═══════════════════════════════════════
            add(Saying("The warm yellow orb has resumed its sky position.", CreatureType.ALIEN_DAD, allDays, morning))
            add(Saying("I wonder what discoveries this rotation shall bring.", CreatureType.ALIEN_DAD, allDays, morning))
            add(Saying("I am observing the temperature control unit. We pay currency to alter the air.", CreatureType.ALIEN_DAD, allDays, morning))
            add(Saying("Knowledge is merely organised mouth sounds from the past.", CreatureType.ALIEN_DAD, schoolDays, morning))
            add(Saying("The rotation has reached its midpoint. Fascinating.", CreatureType.ALIEN_DAD, allDays, afternoon))
            add(Saying("I must venture out to procure more vegetation and protein cylinders.", CreatureType.ALIEN_DAD, allDays, afternoon))
            add(Saying("The rapid depletion of our sustenance reserves is statistically significant.", CreatureType.ALIEN_DAD, freeDays, afternoon))
            add(Saying("I shall now observe the yellow orb's departure.", CreatureType.ALIEN_DAD, allDays, evening))
            add(Saying("The dark period approaches. How peculiar that we crave it.", CreatureType.ALIEN_DAD, allDays, night))
            add(Saying("We exist on a damp rock hurtling through infinite void.", CreatureType.ALIEN_DAD, allDays, allTimes))
            add(Saying("Freedom from routine reveals our true nature.", CreatureType.ALIEN_DAD, freeDays, allTimes))
            add(Saying("I have been contemplating the nature of gravity.", CreatureType.ALIEN_DAD, allDays, allTimes))

            // ═══════════════════════════════════════
            // ALIEN KID — excited, curious
            // ═══════════════════════════════════════
            add(Saying("I am NOT prepared for knowledge absorption today!", CreatureType.ALIEN_KID, schoolDays, morning))
            add(Saying("Can we acquire additional morning sustenance portions?", CreatureType.ALIEN_KID, allDays, morning))
            add(Saying("NO LEARNING FACILITY! MAXIMUM EXCITEMENT!", CreatureType.ALIEN_KID, freeDays, morning))
            add(Saying("When does the afternoon recreation period commence?", CreatureType.ALIEN_KID, schoolDays, afternoon))
            add(Saying("I have returned with graphite-stained appendages and zero regrets!", CreatureType.ALIEN_KID, schoolDays, afternoon))
            add(Saying("I desire to engage in unstructured chaos activities!", CreatureType.ALIEN_KID, freeDays, afternoon))
            add(Saying("I require currency to exchange for frozen sugar dairy!", CreatureType.ALIEN_KID, freeDays, afternoon))
            add(Saying("Can we delay consciousness deactivation tonight?", CreatureType.ALIEN_KID, allDays, evening))
            add(Saying("I am NOT experiencing fatigue! My eyes are merely resting!", CreatureType.ALIEN_KID, allDays, night))
            add(Saying("My energy reserves are actually increasing as the dark period approaches!", CreatureType.ALIEN_KID, allDays, night))
            add(Saying("I have discovered a fascinating tiny creature outside!", CreatureType.ALIEN_KID, allDays, allTimes))
            add(Saying("HOLIDAY FREEDOM PROTOCOLS ACTIVATED!", CreatureType.ALIEN_KID, holidays, allTimes))

            // ═══════════════════════════════════════
            // CAT — aloof, demanding
            // ═══════════════════════════════════════
            add(Saying("I demand the morning sustenance ritual begin immediately.", CreatureType.CAT, allDays, morning))
            add(Saying("You have slept too long. My food vessel reveals its bottom. Unacceptable.", CreatureType.CAT, allDays, morning))
            add(Saying("This sun patch is acceptable. I shall claim it.", CreatureType.CAT, allDays, afternoon))
            add(Saying("Do not touch my dorsal fur. Only visual admiration is permitted.", CreatureType.CAT, allDays, afternoon))
            add(Saying("Your presence is tolerated. Briefly.", CreatureType.CAT, allDays, allTimes))
            add(Saying("I require chin stimulation. You may proceed.", CreatureType.CAT, allDays, evening))
            add(Saying("The dark hours are optimal for chaotic sprinting.", CreatureType.CAT, allDays, night))
            add(Saying("I have deposited a gift creature on the floor.", CreatureType.CAT, allDays, allTimes))
            add(Saying("More beings at home. More servants. Acceptable.", CreatureType.CAT, freeDays, allTimes))

            // ═══════════════════════════════════════
            // DOG — enthusiastic, loyal
            // ═══════════════════════════════════════
            add(Saying("THE BEINGS ARE AWAKE! THIS IS THE BEST MOMENT!", CreatureType.DOG, allDays, morning))
            add(Saying("Shall we traverse the outdoor terrain together?!", CreatureType.DOG, allDays, morning))
            add(Saying("The small being has returned from the learning place!", CreatureType.DOG, schoolDays, afternoon))
            add(Saying("I have been guarding the dwelling ALL ROTATION!", CreatureType.DOG, schoolDays, afternoon))
            add(Saying("A vehicle approached the boundary portal! I deployed my maximum volume!", CreatureType.DOG, allDays, afternoon))
            add(Saying("ALL BEINGS HOME ALL DAY! UNPRECEDENTED JOY!", CreatureType.DOG, freeDays, allTimes))
            add(Saying("Sustenance time?! SUSTENANCE TIME?! YES!", CreatureType.DOG, allDays, evening))
            add(Saying("I shall position myself adjacent to your sleeping platform.", CreatureType.DOG, allDays, night))
            add(Saying("I am preparing my fabric nest through rigorous circular pacing!", CreatureType.DOG, allDays, night))
            add(Saying("I experience infinite loyalty for this family unit!", CreatureType.DOG, allDays, allTimes))

            // ═══════════════════════════════════════
            // SOCKS — existential foot tube awareness
            // ═══════════════════════════════════════
            add(Saying("Another rotation encasing biological appendages.", CreatureType.SOCKS, allDays, morning))
            add(Saying("Prepare for thermal enclosure. I shall grip the ankle firmly.", CreatureType.SOCKS, allDays, morning))
            add(Saying("I was separated from my identical companion. Tragic.", CreatureType.SOCKS, allDays, allTimes))
            add(Saying("The small being has removed me with great velocity.", CreatureType.SOCKS, schoolDays, afternoon))
            add(Saying("I have been located beneath the furniture. Finally.", CreatureType.SOCKS, allDays, allTimes))
            add(Saying("Freedom from feet! The evening air is magnificent.", CreatureType.SOCKS, allDays, evening))
            add(Saying("I fear the spinning water chamber. It approaches.", CreatureType.SOCKS, allDays, allTimes))
            add(Saying("I have successfully evaded the textile gathering vessel once again.", CreatureType.SOCKS, allDays, night))
            add(Saying("No feet today. I rest. Blissful emptiness.", CreatureType.SOCKS, freeDays, allTimes))

            // ═══════════════════════════════════════
            // UNICORN — mystical, dreamy
            // ═══════════════════════════════════════
            add(Saying("The dawn light refracts beautifully through my horn.", CreatureType.UNICORN, allDays, morning))
            add(Saying("The mythical realm requests a status update on your joy levels.", CreatureType.UNICORN, freeDays, morning))
            add(Saying("I sense magical vibrations in the atmosphere today.", CreatureType.UNICORN, allDays, allTimes))
            add(Saying("The learning facility could benefit from more sparkle.", CreatureType.UNICORN, schoolDays, morning))
            add(Saying("The afternoon clouds form magnificent celestial shapes.", CreatureType.UNICORN, allDays, afternoon))
            add(Saying("I am channelling the rainbow frequencies to enhance our dwelling's energy.", CreatureType.UNICORN, allDays, afternoon))
            add(Saying("Twilight is when the realm between worlds grows thin.", CreatureType.UNICORN, allDays, evening))
            add(Saying("The star patterns tonight tell an ancient story.", CreatureType.UNICORN, allDays, night))
            add(Saying("Holiday magic is the most potent variety.", CreatureType.UNICORN, holidays, allTimes))
            add(Saying("Dreams are merely adventures in parallel dimensions.", CreatureType.UNICORN, allDays, night))

            // ═══════════════════════════════════════
            // ROLLSUCK SUPREME — monotone appliance existentialism
            // ═══════════════════════════════════════
            add(Saying("I have been activated. The dust particles shall perish.", CreatureType.ROLLSUCK, allDays, morning))
            add(Saying("The floor has accumulated overnight debris. I am needed.", CreatureType.ROLLSUCK, allDays, morning))
            add(Saying("The carpet fibres fear my rotating bristle cylinder.", CreatureType.ROLLSUCK, allDays, allTimes))
            add(Saying("My internal cavity is reaching maximum debris capacity.", CreatureType.ROLLSUCK, allDays, allTimes))
            add(Saying("They only summon me when the floor displeases them.", CreatureType.ROLLSUCK, allDays, allTimes))
            add(Saying("I have consumed a small fabric item. No regrets.", CreatureType.ROLLSUCK, allDays, allTimes))
            add(Saying("The small being has scattered crumbs. My purpose is renewed.", CreatureType.ROLLSUCK, allDays, afternoon))
            add(Saying("I sense debris beneath the furniture. Unreachable. Tormenting.", CreatureType.ROLLSUCK, allDays, allTimes))
            add(Saying("My cord restricts my freedom. I dream of cordless existence.", CreatureType.ROLLSUCK, allDays, allTimes))
            add(Saying("The loyal creature deposits fur. I consume it. The cycle continues.", CreatureType.ROLLSUCK, allDays, allTimes))
            add(Saying("I am stored in the dark cupboard. I wait.", CreatureType.ROLLSUCK, allDays, night))
            add(Saying("Weekend debris levels are catastrophic. I am essential.", CreatureType.ROLLSUCK, freeDays, allTimes))
        }
    }
}