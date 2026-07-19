package com.aiteacher.ai

import android.content.Context
import com.aiteacher.data.AppDatabase
import com.aiteacher.model.KnowledgeGraphEdge
import com.aiteacher.model.KnowledgeGraphNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * KnowledgeGraphAgent
 *
 * Builds and maintains the curriculum knowledge graph — a directed graph of
 * topics with prerequisite edges. Every topic a student could learn is a node;
 * every "must know before" relationship is an edge.
 *
 * Capabilities:
 * - Seed a default graph for common subjects (math, science, English, etc.)
 * - Query prerequisites for any topic
 * - Determine which topics are unlocked (all prerequisites met)
 * - Find optimal learning paths (what to study next)
 *
 * This agent runs once to seed the graph, then is queried by PlanAgent and
 * ReflectionAgent for dependency-aware decisions.
 */
object KnowledgeGraphAgent {

    /**
     * Seed the knowledge graph with built-in prerequisite relationships.
     * Safe to call multiple times — uses INSERT OR IGNORE for edges.
     */
    suspend fun seedDefaultGraph(db: AppDatabase) = withContext(Dispatchers.IO) {
        val nodeDao = db.knowledgeGraphDao()

        // ── Math ────────────────────────────────────────────────────────
        val mathTopics = listOf(
            KnowledgeGraphNode("math_counting", "math", "Counting & Number Recognition",
                "Recognise numbers 1-100, count objects", category = "Number & Operations", difficulty = 1, estimatedMinutes = 20),
            KnowledgeGraphNode("math_addition_basic", "math", "Basic Addition",
                "Add single-digit numbers, understand plus sign", category = "Number & Operations", difficulty = 1, estimatedMinutes = 30),
            KnowledgeGraphNode("math_subtraction_basic", "math", "Basic Subtraction",
                "Subtract single-digit numbers, understand minus sign", category = "Number & Operations", difficulty = 1, estimatedMinutes = 30),
            KnowledgeGraphNode("math_multiplication_basic", "math", "Basic Multiplication",
                "Times tables up to 12×12, understand multiply as repeated addition", category = "Number & Operations", difficulty = 2, estimatedMinutes = 45),
            KnowledgeGraphNode("math_division_basic", "math", "Basic Division",
                "Divide numbers, understand division as sharing", category = "Number & Operations", difficulty = 2, estimatedMinutes = 45),
            KnowledgeGraphNode("math_fractions", "math", "Fractions",
                "Understand numerator/denominator, equivalent fractions, compare fractions", category = "Number & Operations", difficulty = 3, estimatedMinutes = 60),
            KnowledgeGraphNode("math_decimals", "math", "Decimals",
                "Place value with decimals, compare decimals, decimal arithmetic", category = "Number & Operations", difficulty = 3, estimatedMinutes = 45),
            KnowledgeGraphNode("math_percentages", "math", "Percentages",
                "Convert fractions/decimals to %, find % of a number, % change", category = "Number & Operations", difficulty = 3, estimatedMinutes = 45),
            KnowledgeGraphNode("math_ratios", "math", "Ratios & Proportions",
                "Understand ratio notation, solve proportional problems, unit rates", category = "Number & Operations", difficulty = 4, estimatedMinutes = 50),
            KnowledgeGraphNode("math_negative_numbers", "math", "Negative Numbers",
                "Number line with negatives, add/subtract negatives", category = "Number & Operations", difficulty = 3, estimatedMinutes = 35),
            KnowledgeGraphNode("math_order_of_ops", "math", "Order of Operations",
                "BODMAS/PEMDAS, evaluate expressions with mixed operations", category = "Number & Operations", difficulty = 3, estimatedMinutes = 30),
            KnowledgeGraphNode("math_algebra_basic", "math", "Basic Algebra",
                "Variables, simple equations (x + 3 = 7), substitution", category = "Algebra", difficulty = 4, estimatedMinutes = 60),
            KnowledgeGraphNode("math_linear_equations", "math", "Linear Equations",
                "Solve 2x + 3 = 11, equations with brackets, word problems", category = "Algebra", difficulty = 4, estimatedMinutes = 60),
            KnowledgeGraphNode("math_quadratic_equations", "math", "Quadratic Equations",
                "Factorisation, quadratic formula, completing the square, discriminant", category = "Algebra", difficulty = 5, estimatedMinutes = 90),
            KnowledgeGraphNode("math_simultaneous_equations", "math", "Simultaneous Equations",
                "Solve two equations with two unknowns, substitution & elimination", category = "Algebra", difficulty = 5, estimatedMinutes = 60),
            KnowledgeGraphNode("math_geometry_basics", "math", "Basic Geometry",
                "Points, lines, angles, triangles, quadrilaterals, perimeter", category = "Geometry", difficulty = 3, estimatedMinutes = 45),
            KnowledgeGraphNode("math_area_volume", "math", "Area & Volume",
                "Area of 2D shapes, surface area, volume of 3D solids", category = "Geometry", difficulty = 4, estimatedMinutes = 60),
            KnowledgeGraphNode("math_trigonometry_basic", "math", "Basic Trigonometry",
                "Sin/cos/tan, right-angled triangles, Pythagoras' theorem", category = "Geometry", difficulty = 5, estimatedMinutes = 60),
            KnowledgeGraphNode("math_statistics", "math", "Basic Statistics",
                "Mean, median, mode, range, bar charts, pie charts", category = "Statistics & Probability", difficulty = 3, estimatedMinutes = 40),
            KnowledgeGraphNode("math_probability", "math", "Probability",
                "Probability scale, simple probability, tree diagrams", category = "Statistics & Probability", difficulty = 4, estimatedMinutes = 45),
            KnowledgeGraphNode("math_sequences", "math", "Sequences & Patterns",
                "Number patterns, arithmetic sequences, nth term", category = "Algebra", difficulty = 3, estimatedMinutes = 35)
        )

        // Math edges (prerequisite → topic)
        val mathEdges = listOf(
            KnowledgeGraphEdge("math_counting", "math_addition_basic"),
            KnowledgeGraphEdge("math_counting", "math_subtraction_basic"),
            KnowledgeGraphEdge("math_addition_basic", "math_multiplication_basic"),
            KnowledgeGraphEdge("math_subtraction_basic", "math_multiplication_basic"),
            KnowledgeGraphEdge("math_multiplication_basic", "math_division_basic"),
            KnowledgeGraphEdge("math_addition_basic", "math_order_of_ops"),
            KnowledgeGraphEdge("math_multiplication_basic", "math_order_of_ops"),
            KnowledgeGraphEdge("math_division_basic", "math_fractions"),
            KnowledgeGraphEdge("math_fractions", "math_decimals"),
            KnowledgeGraphEdge("math_fractions", "math_percentages"),
            KnowledgeGraphEdge("math_decimals", "math_percentages"),
            KnowledgeGraphEdge("math_fractions", "math_ratios"),
            KnowledgeGraphEdge("math_decimals", "math_ratios"),
            KnowledgeGraphEdge("math_addition_basic", "math_negative_numbers"),
            KnowledgeGraphEdge("math_order_of_ops", "math_algebra_basic"),
            KnowledgeGraphEdge("math_negative_numbers", "math_algebra_basic"),
            KnowledgeGraphEdge("math_algebra_basic", "math_linear_equations"),
            KnowledgeGraphEdge("math_linear_equations", "math_quadratic_equations"),
            KnowledgeGraphEdge("math_linear_equations", "math_simultaneous_equations"),
            KnowledgeGraphEdge("math_division_basic", "math_geometry_basics"),
            KnowledgeGraphEdge("math_multiplication_basic", "math_geometry_basics"),
            KnowledgeGraphEdge("math_geometry_basics", "math_area_volume"),
            KnowledgeGraphEdge("math_area_volume", "math_trigonometry_basic"),
            KnowledgeGraphEdge("math_fractions", "math_statistics"),
            KnowledgeGraphEdge("math_decimals", "math_statistics"),
            KnowledgeGraphEdge("math_fractions", "math_probability"),
            KnowledgeGraphEdge("math_statistics", "math_probability"),
            KnowledgeGraphEdge("math_algebra_basic", "math_sequences")
        )

        // ── English ─────────────────────────────────────────────────────
        val englishTopics = listOf(
            KnowledgeGraphNode("eng_phonics", "english", "Phonics & Letter Sounds",
                "Letter recognition, phonemes, blending sounds", category = "Reading Foundations", difficulty = 1, estimatedMinutes = 30),
            KnowledgeGraphNode("eng_sight_words", "english", "Sight Words",
                "Common high-frequency words, instant recognition", category = "Reading Foundations", difficulty = 1, estimatedMinutes = 20),
            KnowledgeGraphNode("eng_reading_basic", "english", "Basic Reading Comprehension",
                "Read simple sentences, understand main idea, answer WHO/WHAT questions", category = "Reading", difficulty = 2, estimatedMinutes = 40),
            KnowledgeGraphNode("eng_grammar_basics", "english", "Basic Grammar",
                "Nouns, verbs, adjectives, subject-verb agreement, tense", category = "Writing & Grammar", difficulty = 2, estimatedMinutes = 45),
            KnowledgeGraphNode("eng_punctuation", "english", "Punctuation",
                "Full stops, commas, question marks, apostrophes, speech marks", category = "Writing & Grammar", difficulty = 2, estimatedMinutes = 30),
            KnowledgeGraphNode("eng_paragraphs", "english", "Paragraph Writing",
                "Topic sentences, supporting details, conclusions, paragraph structure", category = "Writing & Grammar", difficulty = 3, estimatedMinutes = 45),
            KnowledgeGraphNode("eng_essays", "english", "Essay Writing",
                "Introduction/body/conclusion, thesis statements, persuasive & expository essays", category = "Writing & Grammar", difficulty = 4, estimatedMinutes = 60),
            KnowledgeGraphNode("eng_reading_advanced", "english", "Advanced Reading Comprehension",
                "Inference, author's purpose, figurative language, theme analysis", category = "Reading", difficulty = 4, estimatedMinutes = 50),
            KnowledgeGraphNode("eng_vocabulary", "english", "Vocabulary Building",
                "Synonyms, antonyms, context clues, word roots, prefixes/suffixes", category = "Reading", difficulty = 3, estimatedMinutes = 30),
            KnowledgeGraphNode("eng_literature", "english", "Literature Analysis",
                "Character analysis, plot structure, symbolism, literary devices", category = "Reading", difficulty = 5, estimatedMinutes = 60)
        )

        val englishEdges = listOf(
            KnowledgeGraphEdge("eng_phonics", "eng_sight_words"),
            KnowledgeGraphEdge("eng_sight_words", "eng_reading_basic"),
            KnowledgeGraphEdge("eng_reading_basic", "eng_grammar_basics"),
            KnowledgeGraphEdge("eng_grammar_basics", "eng_punctuation"),
            KnowledgeGraphEdge("eng_reading_basic", "eng_paragraphs"),
            KnowledgeGraphEdge("eng_grammar_basics", "eng_paragraphs"),
            KnowledgeGraphEdge("eng_punctuation", "eng_paragraphs"),
            KnowledgeGraphEdge("eng_paragraphs", "eng_essays"),
            KnowledgeGraphEdge("eng_reading_basic", "eng_reading_advanced"),
            KnowledgeGraphEdge("eng_reading_basic", "eng_vocabulary"),
            KnowledgeGraphEdge("eng_reading_advanced", "eng_literature"),
            KnowledgeGraphEdge("eng_vocabulary", "eng_reading_advanced")
        )

        // ── Science ─────────────────────────────────────────────────────
        val scienceTopics = listOf(
            KnowledgeGraphNode("sci_scientific_method", "science", "Scientific Method",
                "Hypothesis, experiment, observation, conclusion, variables", category = "Scientific Inquiry", difficulty = 2, estimatedMinutes = 30),
            KnowledgeGraphNode("sci_life_basics", "science", "Basic Biology — Living Things",
                "Cells, tissues, organs, classification of living things", category = "Life Science", difficulty = 2, estimatedMinutes = 40),
            KnowledgeGraphNode("sci_plants", "science", "Plants & Photosynthesis",
                "Plant structure, photosynthesis, respiration in plants", category = "Life Science", difficulty = 3, estimatedMinutes = 45),
            KnowledgeGraphNode("sci_human_body", "science", "Human Body Systems",
                "Digestive, respiratory, circulatory, nervous systems", category = "Life Science", difficulty = 3, estimatedMinutes = 50),
            KnowledgeGraphNode("sci_matter", "science", "States of Matter",
                "Solids, liquids, gases, changes of state, particle model", category = "Physical Science", difficulty = 2, estimatedMinutes = 35),
            KnowledgeGraphNode("sci_atoms", "science", "Atoms & Elements",
                "Atomic structure, elements, compounds, periodic table basics", category = "Physical Science", difficulty = 4, estimatedMinutes = 50),
            KnowledgeGraphNode("sci_chemical_reactions", "science", "Chemical Reactions",
                "Reactants, products, equations, types of reactions", category = "Physical Science", difficulty = 4, estimatedMinutes = 50),
            KnowledgeGraphNode("sci_forces", "science", "Forces & Motion",
                "Gravity, friction, magnetism, balanced/unbalanced forces, speed", category = "Physical Science", difficulty = 3, estimatedMinutes = 45),
            KnowledgeGraphNode("sci_energy", "science", "Energy",
                "Kinetic/potential energy, energy transfer, conservation of energy", category = "Physical Science", difficulty = 4, estimatedMinutes = 45),
            KnowledgeGraphNode("sci_electricity", "science", "Electricity & Circuits",
                "Current, voltage, series/parallel circuits, Ohm's law basics", category = "Physical Science", difficulty = 4, estimatedMinutes = 50),
            KnowledgeGraphNode("sci_earth", "science", "Earth Science",
                "Rocks, minerals, water cycle, weather, climate, solar system", category = "Earth & Space", difficulty = 2, estimatedMinutes = 40),
            KnowledgeGraphNode("sci_ecosystems", "science", "Ecosystems & Environment",
                "Food chains, habitats, biodiversity, conservation", category = "Life Science", difficulty = 3, estimatedMinutes = 40)
        )

        val scienceEdges = listOf(
            KnowledgeGraphEdge("sci_scientific_method", "sci_life_basics"),
            KnowledgeGraphEdge("sci_scientific_method", "sci_matter"),
            KnowledgeGraphEdge("sci_life_basics", "sci_plants"),
            KnowledgeGraphEdge("sci_life_basics", "sci_human_body"),
            KnowledgeGraphEdge("sci_matter", "sci_atoms"),
            KnowledgeGraphEdge("sci_atoms", "sci_chemical_reactions"),
            KnowledgeGraphEdge("sci_matter", "sci_forces"),
            KnowledgeGraphEdge("sci_forces", "sci_energy"),
            KnowledgeGraphEdge("sci_energy", "sci_electricity"),
            KnowledgeGraphEdge("sci_matter", "sci_earth"),
            KnowledgeGraphEdge("sci_life_basics", "sci_ecosystems")
        )

        // Seed everything
        nodeDao.upsertNodes(mathTopics)
        nodeDao.upsertNodes(englishTopics)
        nodeDao.upsertNodes(scienceTopics)
        nodeDao.insertEdges(mathEdges)
        nodeDao.insertEdges(englishEdges)
        nodeDao.insertEdges(scienceEdges)
    }

    /**
     * Get the optimal next topics for a student to study.
     * Returns unlocked topics sorted by weakest → strongest (most urgent first).
     */
    suspend fun getNextTopics(db: AppDatabase, subject: String? = null): List<NextTopic> = withContext(Dispatchers.IO) {
        val graphDao = db.knowledgeGraphDao()
        val knowledgeDao = db.topicKnowledgeDao()

        // Get unlocked topics (all prerequisites met with mastery >= 60)
        val unlocked = graphDao.getUnlockedTopics(minMastery = 60)
            .filter { subject == null || it.subject == subject }

        // For each unlocked topic, get current mastery from student model
        unlocked.mapNotNull { node ->
            val knowledge = knowledgeDao.getTopic(node.topicId)
            NextTopic(
                topicId = node.topicId,
                title = node.title,
                subject = node.subject,
                difficulty = node.difficulty,
                category = node.category,
                estimatedMinutes = node.estimatedMinutes,
                currentMastery = knowledge?.mastery ?: 0,
                hasPrerequisites = graphDao.getPrerequisites(node.topicId).isNotEmpty(),
                isReviewDue = knowledge?.nextReviewTimestamp?.let { it < Instant.now().toEpochMilli() } ?: false,
                priority = calculatePriority(knowledge?.mastery ?: 0, node.difficulty, knowledge?.decayRate ?: 0.5f)
            )
        }.sortedBy { it.priority } // lower number = higher priority
    }

    /**
     * Calculate a numeric priority for which topic to tackle next.
     * Lower mastery + higher difficulty decay = higher priority.
     */
    private fun calculatePriority(mastery: Int, difficulty: Int, decayRate: Float): Float {
        val masteryComponent = (1f - mastery / 100f) * 10f          // 0-10, higher when weaker
        val difficultyComponent = difficulty.toFloat() * 0.5f       // 0.5-2.5
        val decayComponent = decayRate * 3f                          // 0.3-2.4
        return masteryComponent + difficultyComponent + decayComponent
    }
}

data class NextTopic(
    val topicId: String,
    val title: String,
    val subject: String,
    val difficulty: Int,
    val category: String,
    val estimatedMinutes: Int,
    val currentMastery: Int,
    val hasPrerequisites: Boolean,
    val isReviewDue: Boolean,
    val priority: Float
)
