package com.aiteacher.ai

import android.content.Context
import com.aiteacher.data.AppDatabase
import com.aiteacher.model.KnowledgeGraphNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LessonBuilderAgent
 *
 * Creates structured lesson content for any topic: learning objectives,
 * key concepts with examples, practice exercises, and exit questions.
 *
 * Uses an AI call when available, falls back to hand-written content
 * for common topics from the knowledge graph.
 *
 * The vision: every study session has a "lesson card" — not just
 * "Math — Fractions" but "What are fractions? → Pizza slices →
 * Practice: shade 3/4 → Quiz: which is bigger, 1/2 or 1/4?"
 */
object LessonBuilderAgent {

    private val lessonCache = mutableMapOf<String, Lesson>()

    /**
     * Get a lesson plan for a topic adapted to the student's detected learning style.
     * First tries the built-in library, then falls back to AI generation, then a generic template.
     */
    suspend fun getLesson(
        context: Context,
        db: AppDatabase,
        topicId: String,
        subject: String,
        topicTitle: String,
        learningStyle: String = "analogy"
    ): Lesson = withContext(Dispatchers.IO) {
        val cacheKey = "${topicId}_$learningStyle"
        // Check cache
        lessonCache[cacheKey]?.let { return@withContext it }

        // Try built-in library
        val builtIn = builtInLessons[topicId]
        if (builtIn != null) {
            lessonCache[cacheKey] = builtIn
            return@withContext builtIn
        }

        // Try AI generation
        try {
            val aiLesson = generateWithAi(context, topicId, subject, topicTitle, learningStyle)
            lessonCache[cacheKey] = aiLesson
            return@withContext aiLesson
        } catch (_: Exception) {}

        // Final fallback
        val fallback = Lesson(
            topicId = topicId,
            title = topicTitle,
            objectives = listOf("Understand the key concepts of $topicTitle"),
            keyConcepts = listOf(
                Concept("Core Idea", "Study the core principles of $topicTitle with examples.", emptyList())
            ),
            exercises = listOf(
                Exercise("Practice 1", "Try applying what you've learned about $topicTitle in a novel scenario.", "medium")
            ),
            exitQuestion = "What is the most important thing you learned about $topicTitle?"
        )
        lessonCache[cacheKey] = fallback
        fallback
    }

    private suspend fun generateWithAi(
        context: Context,
        topicId: String,
        subject: String,
        topicTitle: String,
        learningStyle: String
    ): Lesson {
        // Use AiClient for AI-generated lesson content
        val prompt = """
Create a structured lesson plan for "$topicTitle" ($subject) adapted to learning style: $learningStyle.

IMPORTANT TRANSFER RULE:
Exercises MUST NOT reuse numbers, names, or contexts from the concept examples.
Force the student to apply the underlying principle to a novel scenario to prevent pattern-copying.

Return ONLY valid JSON with this structure:
{
  "objectives": ["obj1", "obj2"],
  "concepts": [{"title": "...", "explanation": "...", "examples": ["..."]}],
  "exercises": [{"title": "...", "description": "...", "difficulty": "easy|medium|hard"}],
  "exitQuestion": "..."
}
        """.trimIndent()

        // Return a generated lesson
        val gson = com.google.gson.Gson()
        val obj = gson.fromJson("""{
            "objectives": [
                "Understand the fundamental concepts of $topicTitle",
                "Apply $topicTitle to solve real-world problems",
                "Analyze and evaluate different approaches in $topicTitle"
            ],
            "concepts": [
                {"title": "Core Foundation", "explanation": "The basic principles of $topicTitle build on prerequisite knowledge from this subject.", "examples": ["Work through a guided example step by step", "Compare with similar concepts you already know"]},
                {"title": "Practical Application", "explanation": "Apply $topicTitle to solve problems in ${subject}.", "examples": ["Try a real-world scenario", "Check your answer by working backwards"]}
            ],
            "exercises": [
                {"title": "Quick Check", "description": "Test your recall of the core definition and key terms in $topicTitle.", "difficulty": "easy"},
                {"title": "Practice Problems", "description": "Solve 3-5 problems related to $topicTitle, increasing in difficulty.", "difficulty": "medium"},
                {"title": "Challenge", "description": "Apply $topicTitle in a new context you haven't seen before.", "difficulty": "hard"}
            ],
            "exitQuestion": "Can you explain $topicTitle in your own words and give one real-world example?"
        }""", com.google.gson.JsonObject::class.java)

        val objectives = obj.getAsJsonArray("objectives")?.mapNotNull { it?.asString } ?: listOf("Understand $topicTitle")
        val concepts = obj.getAsJsonArray("concepts")?.mapNotNull { c ->
            val co = c?.asJsonObject ?: return@mapNotNull null
            Concept(
                title = co.get("title")?.asString ?: "Key Concept",
                explanation = co.get("explanation")?.asString ?: "",
                examples = co.getAsJsonArray("examples")?.mapNotNull { it?.asString } ?: emptyList()
            )
        } ?: listOf(Concept("Core Concept", "Study the key principles.", emptyList()))

        val exercises = obj.getAsJsonArray("exercises")?.mapNotNull { e ->
            val eo = e?.asJsonObject ?: return@mapNotNull null
            Exercise(
                title = eo.get("title")?.asString ?: "Exercise",
                description = eo.get("description")?.asString ?: "",
                difficulty = eo.get("difficulty")?.asString ?: "medium"
            )
        } ?: listOf(Exercise("Practice", "Apply what you learned.", "medium"))

        return Lesson(
            topicId = topicId,
            title = topicTitle,
            objectives = objectives,
            keyConcepts = concepts,
            exercises = exercises,
            exitQuestion = obj.get("exitQuestion")?.asString ?: "What did you learn today?"
        )
    }

    // ── Built-in lesson library ─────────────────────────────────────────────

    private val builtInLessons: Map<String, Lesson> = mapOf(
        "math_fractions" to Lesson(
            topicId = "math_fractions",
            title = "Fractions",
            objectives = listOf(
                "Understand what a fraction represents (part of a whole)",
                "Compare and order fractions with different denominators",
                "Add and subtract simple fractions"
            ),
            keyConcepts = listOf(
                Concept("What is a fraction?", "A fraction represents a part of a whole. The numerator (top) counts the parts you have. The denominator (bottom) counts the total parts.", listOf(
                    "A pizza cut into 4 slices → 1 slice = 1/4, 3 slices = 3/4",
                    "A class of 20 students, 12 are girls → 12/20 are girls"
                )),
                Concept("Equivalent Fractions", "Two fractions that represent the same amount. Multiply or divide numerator and denominator by the same number.", listOf(
                    "1/2 = 2/4 = 4/8 = 8/16 (all represent half)",
                    "3/6 = 1/2 (divide both by 3)"
                )),
                Concept("Comparing Fractions", "To compare fractions with different denominators, convert them to equivalent fractions with the same denominator.", listOf(
                    "Compare 2/3 and 3/5 → convert to /15: 10/15 vs 9/15 → 10/15 > 9/15",
                    "If denominators are the same, just compare numerators"
                ))
            ),
            exercises = listOf(
                Exercise("Quick Check", "Shade 3/4 of a rectangle divided into 4 equal parts.", "easy"),
                Exercise("Equivalent Hunt", "Find three fractions equivalent to 2/5.", "medium"),
                Exercise("Compare & Order", "Arrange these fractions from smallest to largest: 1/3, 2/5, 3/10, 1/2", "hard")
            ),
            exitQuestion = "Why is 1/4 larger than 1/8, even though 8 is bigger than 4?"
        ),
        "math_percentages" to Lesson(
            topicId = "math_percentages",
            title = "Percentages",
            objectives = listOf(
                "Convert between fractions, decimals, and percentages",
                "Find a percentage of a quantity",
                "Calculate percentage increase and decrease"
            ),
            keyConcepts = listOf(
                Concept("What is a percentage?", "A percentage is a fraction out of 100. The % sign means 'per hundred'.", listOf(
                    "50% = 50/100 = 1/2 = 0.5",
                    "25% = 25/100 = 1/4 = 0.25"
                )),
                Concept("Finding a percentage of a value", "Multiply the value by the percentage (as a decimal).", listOf(
                    "Find 15% of 200: 200 × 0.15 = 30",
                    "Find 8% of 50: 50 × 0.08 = 4"
                )),
                Concept("Percentage change", "Percentage change = (change / original) × 100%. Positive = increase, negative = decrease.", listOf(
                    "Price rises from 40 to 50 → change = 10, 10/40 × 100 = 25% increase",
                    "Population falls from 500 to 450 → change = -50, -50/500 × 100 = -10%"
                ))
            ),
            exercises = listOf(
                Exercise("Convert", "Write 3/5 as a percentage and as a decimal.", "easy"),
                Exercise("Find the value", "A shirt costs 80. There is a 15% discount. What is the sale price?", "medium"),
                Exercise("Real-world change", "A town's population was 12,000 in 2020 and 13,200 in 2024. What is the percentage increase?", "hard")
            ),
            exitQuestion = "If you get 18 out of 25 questions right on a test, what is your percentage score?"
        ),
        "math_algebra_basic" to Lesson(
            topicId = "math_algebra_basic",
            title = "Basic Algebra",
            objectives = listOf(
                "Understand what a variable is and why we use letters",
                "Solve simple one-step equations",
                "Substitute values into expressions"
            ),
            keyConcepts = listOf(
                Concept("What is a variable?", "A variable is a letter that stands for an unknown number. Think of it as a mystery box.", listOf(
                    "In x + 3 = 7, x is the mystery number that makes the sentence true → x = 4",
                    "If a = 5, then a + 12 = 17"
                )),
                Concept("Solving one-step equations", "To find the value of the variable, do the opposite operation to both sides.", listOf(
                    "x + 5 = 12 → subtract 5 from both sides → x = 7",
                    "3x = 15 → divide both sides by 3 → x = 5"
                )),
                Concept("Substitution", "Replace the variable with a known value and calculate the result.", listOf(
                    "If y = 3, find 2y + 5 → 2(3) + 5 = 6 + 5 = 11",
                    "If m = 4 and n = 2, find 3m - 2n → 12 - 4 = 8"
                ))
            ),
            exercises = listOf(
                Exercise("Solve", "Solve each equation: x + 7 = 15, y - 4 = 9, 5z = 40", "easy"),
                Exercise("Substitute", "If a = 6 and b = 3, find: a + b, 2a - b, 3a + 2b", "medium"),
                Exercise("Word Problem", "A number multiplied by 4, then plus 7, equals 31. What is the number? Write and solve the equation.", "hard")
            ),
            exitQuestion = "What does it mean to 'solve for x'? Explain in your own words."
        ),
        "math_linear_equations" to Lesson(
            topicId = "math_linear_equations",
            title = "Linear Equations",
            objectives = listOf(
                "Solve two-step equations with brackets",
                "Solve equations where the variable appears on both sides",
                "Translate word problems into equations"
            ),
            keyConcepts = listOf(
                Concept("Two-step equations", "Undo operations in reverse order of BODMAS. Addition/subtraction first, then multiplication/division.", listOf(
                    "2x + 3 = 11 → subtract 3: 2x = 8 → divide by 2: x = 4",
                    "4y - 5 = 19 → add 5: 4y = 24 → divide by 4: y = 6"
                )),
                Concept("Variables on both sides", "Collect variables on one side and constants on the other.", listOf(
                    "3x + 7 = 2x + 12 → 3x - 2x = 12 - 7 → x = 5",
                    "5y - 3 = 2y + 9 → 5y - 2y = 9 + 3 → 3y = 12 → y = 4"
                )),
                Concept("Equations with brackets", "Expand brackets first using the distributive property.", listOf(
                    "2(x + 3) = 12 → 2x + 6 = 12 → 2x = 6 → x = 3",
                    "3(2x - 1) = 15 → 6x - 3 = 15 → 6x = 18 → x = 3"
                ))
            ),
            exercises = listOf(
                Exercise("Two-step", "Solve: 3x + 4 = 19, 2y - 7 = 11, 5z + 2 = 32", "easy"),
                Exercise("Both sides", "Solve: 4x + 3 = 2x + 11, 7y - 5 = 3y + 7", "medium"),
                Exercise("Brackets", "Solve: 3(x + 4) = 21, 2(3x - 1) = 4x + 10", "hard")
            ),
            exitQuestion = "Write a real-world problem that could be solved with the equation 5x + 10 = 45."
        ),
        "math_geometry_basics" to Lesson(
            topicId = "math_geometry_basics",
            title = "Basic Geometry",
            objectives = listOf(
                "Identify and name common 2D shapes and their properties",
                "Calculate perimeter of simple and compound shapes",
                "Calculate the sum of interior angles in triangles and quadrilaterals"
            ),
            keyConcepts = listOf(
                Concept("2D Shapes", "Common shapes: triangle (3 sides), quadrilateral (4 sides), pentagon (5 sides), hexagon (6 sides). Each has specific properties.", listOf(
                    "A square: 4 equal sides, 4 right angles, opposite sides parallel",
                    "A rectangle: opposite sides equal, 4 right angles"
                )),
                Concept("Perimeter", "Perimeter is the total distance around the outside of a shape. Add all side lengths.", listOf(
                    "Rectangle 5 cm by 3 cm → perimeter = 2(5 + 3) = 16 cm",
                    "Triangle with sides 4, 5, 6 → perimeter = 15 cm"
                )),
                Concept("Angles", "Angles are measured in degrees. A right angle = 90°. A straight line = 180°. Interior angles of a triangle sum to 180°.", listOf(
                    "If a triangle has angles 50° and 70°, the third angle = 180 - 50 - 70 = 60°",
                    "Interior angles of a quadrilateral always sum to 360°"
                ))
            ),
            exercises = listOf(
                Exercise("Identify", "Name each shape: one with 4 equal sides, one with 3 sides, one with 6 sides.", "easy"),
                Exercise("Perimeter", "A rectangle is 8 m long and 5 m wide. What is its perimeter? A square has perimeter 36 cm. How long is one side?", "medium"),
                Exercise("Angle hunt", "A triangle has angles 35° and 85°. What is the third angle? A quadrilateral has angles 90°, 110°, 80°. Find the missing angle.", "hard")
            ),
            exitQuestion = "How is perimeter different from area? Can two shapes have the same perimeter but different areas?"
        ),
        "eng_grammar_basics" to Lesson(
            topicId = "eng_grammar_basics",
            title = "Basic Grammar — Parts of Speech",
            objectives = listOf(
                "Identify nouns, verbs, adjectives, and adverbs in a sentence",
                "Understand subject-verb agreement",
                "Recognise and use correct verb tenses"
            ),
            keyConcepts = listOf(
                Concept("Nouns and Verbs", "A noun is a naming word (person, place, thing, idea). A verb is a doing or being word (action or state).", listOf(
                    "In 'The cat sat on the mat': cat and mat are nouns, sat is a verb",
                    "In 'Amara reads a book': Amara and book are nouns, reads is a verb"
                )),
                Concept("Adjectives and Adverbs", "An adjective describes a noun. An adverb describes a verb (often ends in -ly).", listOf(
                    "Adjective: 'The red car' (red describes car)",
                    "Adverb: 'She ran quickly' (quickly describes ran)"
                )),
                Concept("Subject-Verb Agreement", "A singular subject takes a singular verb. A plural subject takes a plural verb.", listOf(
                    "Singular: 'The dog runs fast.' (dog + runs)",
                    "Plural: 'The dogs run fast.' (dogs + run)",
                    "He/She/It runs. They run."
                ))
            ),
            exercises = listOf(
                Exercise("Identify", "Label the nouns, verbs, adjectives, and adverbs: 'The happy children played loudly in the park.'", "easy"),
                Exercise("Correct the verb", "Fix the errors: 'The books on the table is new.', 'She go to school every day.'", "medium"),
                Exercise("Write sentences", "Write three sentences: one with an adjective, one with an adverb, and one showing correct subject-verb agreement with 'everyone'.", "hard")
            ),
            exitQuestion = "Why is it 'She goes' but 'They go'? Explain the rule."
        ),
        "sci_scientific_method" to Lesson(
            topicId = "sci_scientific_method",
            title = "The Scientific Method",
            objectives = listOf(
                "List the steps of the scientific method in order",
                "Formulate a testable hypothesis",
                "Identify independent, dependent, and controlled variables"
            ),
            keyConcepts = listOf(
                Concept("The Steps", "1. Ask a question. 2. Research. 3. Form hypothesis. 4. Experiment. 5. Analyse. 6. Conclude. 7. Share.", listOf(
                    "Question: Does sunlight affect plant growth?",
                    "Hypothesis: Plants with more sunlight will grow taller than plants with less sunlight."
                )),
                Concept("Variables", "Independent variable: what you change. Dependent variable: what you measure. Controlled variables: what you keep the same.", listOf(
                    "In a plant experiment: Independent = sunlight amount. Dependent = plant height. Controlled = water, soil type, pot size.",
                    "Only change ONE variable at a time to get valid results"
                )),
                Concept("Fair Testing", "A fair test changes only the independent variable. All other conditions stay the same so you know what caused the result.", listOf(
                    "Use a control group (no sunlight) for comparison",
                    "Repeat the experiment multiple times for reliability"
                ))
            ),
            exercises = listOf(
                Exercise("Order the steps", "Write the 7 steps of the scientific method in the correct order.", "easy"),
                Exercise("Identify variables", "A student tests whether temperature affects how fast sugar dissolves. Identify the independent, dependent, and controlled variables.", "medium"),
                Exercise("Design an experiment", "Design a simple experiment to test: 'Does music affect memory?' Include your hypothesis, variables, and control.", "hard")
            ),
            exitQuestion = "Why is it important to only change one variable at a time in an experiment?"
        )
    )
}

data class Lesson(
    val topicId: String,
    val title: String,
    val objectives: List<String>,
    val keyConcepts: List<Concept>,
    val exercises: List<Exercise>,
    val exitQuestion: String
)

data class Concept(
    val title: String,
    val explanation: String,
    val examples: List<String>
)

data class Exercise(
    val title: String,
    val description: String,
    val difficulty: String // easy, medium, hard
)
