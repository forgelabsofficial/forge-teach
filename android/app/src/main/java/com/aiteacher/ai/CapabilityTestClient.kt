package com.aiteacher.ai

import android.content.Context
import android.util.Log
import com.aiteacher.onboarding.CapabilityQuestion
import com.aiteacher.onboarding.CapabilityTest
import com.aiteacher.onboarding.StudentProfile
import com.aiteacher.security.SecureStorage
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Generates an adaptive capability test calibrated to the student's country curriculum and grade.
 */
object CapabilityTestClient {

    private val gson = Gson()

    enum class TestSource { AI_GENERATED, BUILT_IN_FALLBACK }

    data class CapabilityTestWithSource(
        val test: CapabilityTest,
        val source: TestSource,
        val failureReason: String? = null,
        val rawResponse: String? = null
    )

    suspend fun generateTest(
        context: Context,
        profile: StudentProfile,
        subjects: List<String>
    ): CapabilityTest = generateTestWithSource(context, profile, subjects).test

    suspend fun generateTestWithSource(
        context: Context,
        profile: StudentProfile,
        subjects: List<String>,
        onStep: (String) -> Unit = {}
    ): CapabilityTestWithSource = withContext(Dispatchers.IO) {
        val apiKey = SecureStorage.getApiKey(context)
        val provider = SecureStorage.getApiProvider(context)?.lowercase()
        val model = SecureStorage.getApiModel(context)

        var failureReason: String? = null
        var rawResponse: String? = null

        val subjectCount = subjects.size.coerceAtLeast(1)
        // Adaptive question count based on grade level
        val gradeNum = extractGradeNumber(profile.gradeLevelLabel)
        val questionsPerSubject = when {
            gradeNum <= 3 -> 3   // primary: 3 per subject
            gradeNum <= 6 -> 4   // upper primary: 4 per subject
            gradeNum <= 9 -> 5   // junior secondary: 5 per subject
            else -> 6            // senior secondary: 6 per subject
        }
        val questionCount = (subjectCount * questionsPerSubject).coerceIn(8, 36)

        if (!apiKey.isNullOrBlank() && !provider.isNullOrBlank()) {
            try {
                val curriculumContext = coroutineScope {
                    val searchDeferred = async(Dispatchers.IO) {
                        searchCurriculumContext(profile, subjects)
                    }
                    searchDeferred.await()
                }

                val prompt = buildPrompt(profile, subjects, questionCount, curriculumContext)
                onStep("🤖 Sending prompt to ${provider.replaceFirstChar { it.uppercase() }}…")
                val raw = callAi(context, provider, apiKey, model, prompt)
                rawResponse = raw
                val parsed = parseQuestionsFromJson(raw)
                if (parsed.size >= 5) {
                    return@withContext CapabilityTestWithSource(
                        test = CapabilityTest(parsed),
                        source = TestSource.AI_GENERATED,
                        rawResponse = raw
                    )
                }

                Log.w("CapabilityTestClient", "First attempt got ${parsed.size} questions, retrying…")
                onStep("🔄 Retrying with simplified prompt…")
                val retryRaw = callAi(
                    context, provider, apiKey, model,
                    buildSimplePrompt(profile, subjects, questionCount)
                )
                rawResponse = retryRaw
                val retryParsed = parseQuestionsFromJson(retryRaw)
                if (retryParsed.isNotEmpty()) {
                    return@withContext CapabilityTestWithSource(
                        test = CapabilityTest(retryParsed),
                        source = TestSource.AI_GENERATED,
                        rawResponse = retryRaw
                    )
                }

                failureReason =
                    "AI returned questions that couldn’t be parsed (got ${parsed.size} then 0). Provider=$provider Model=${model ?: "auto"}."
            } catch (e: Exception) {
                failureReason = "AI test generation failed: ${e.message ?: e.javaClass.simpleName}"
                Log.w("CapabilityTestClient", "AI test generation failed: ${e.message}")
            }
        } else {
            failureReason = "Missing API key or provider. Provider=$provider"
        }

        // Fallback: generate questions for the student's actual subjects and level
        return@withContext CapabilityTestWithSource(
            test = CapabilityTest(builtInAdaptiveFallback(profile, subjects)),
            source = TestSource.BUILT_IN_FALLBACK,
            failureReason = failureReason,
            rawResponse = rawResponse
        )
    }

    private suspend fun searchCurriculumContext(profile: StudentProfile, subjects: List<String>): String {
        val subjectStr = subjects.take(3).joinToString(", ")
        val query = "${profile.countryName} ${profile.gradeLevelLabel} ${profile.curriculumBody} curriculum syllabus $subjectStr topics"
        return try {
            val result = WebSearchTool.search(query)
            if (result.isNotBlank()) "Curriculum context from web search:\n$result\n\n" else ""
        } catch (e: Exception) { "" }
    }

    private fun buildPrompt(
        profile: StudentProfile,
        subjects: List<String>,
        count: Int,
        curriculumContext: String
    ): String {
        val subjectList = if (subjects.isEmpty()) "all core school subjects" else subjects.joinToString(", ")
        val perSubject = (count / subjects.size.coerceAtLeast(1)).coerceAtLeast(3)
        return """
${curriculumContext}You are an expert examiner for ${profile.countryName} schools.

Generate exactly $count multiple-choice questions to assess a ${profile.gradeLevelLabel} student's current knowledge.

Student details:
- Country: ${profile.countryName}
- Curriculum body: ${profile.curriculumBody}
- Grade / level: ${profile.gradeLevelLabel}
- Key exams: ${profile.keyExams.joinToString(", ").ifEmpty { "standard school exams" }}
- Subjects to test: $subjectList

Requirements:
1. Questions MUST align with the ${profile.curriculumBody} syllabus for ${profile.gradeLevelLabel}.
2. Distribute questions evenly across subjects (~$perSubject per subject).
3. Cover at least 70% of typical topic areas for each subject at this grade.
4. Include easy (30%), medium (50%) and hard (20%) questions.
5. Each question has EXACTLY 4 options. Only one is correct.
6. Return ONLY a valid JSON array.

JSON format:
[{"id":"q1","subject":"math","question":"...","options":["A","B","C","D"],"correctIndex":2,"difficulty":2}]

correctIndex is 0-based. difficulty 1=easy, 3=medium, 5=hard.
""".trimIndent()
    }

    private fun buildSimplePrompt(profile: StudentProfile, subjects: List<String>, count: Int): String {
        return "Generate $count school exam questions for a ${profile.gradeLevelLabel} student in ${profile.countryName} studying ${subjects.joinToString(", ")}. Return ONLY a JSON array: [{\"id\":\"q1\",\"subject\":\"math\",\"question\":\"...\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"correctIndex\":0,\"difficulty\":2}]"
    }

    private suspend fun callAi(
        context: Context, provider: String, apiKey: String, model: String?, prompt: String
    ): String = when (provider) {
        "anthropic" -> callAnthropic(apiKey, model, prompt)
        "google"    -> callGoogle(apiKey, prompt)
        else        -> callOpenAiCompat(provider, apiKey, model, prompt)
    }

    private suspend fun callOpenAiCompat(provider: String, apiKey: String, model: String?, prompt: String): String {
        val config = ModelRegistry.getProviderConfig(provider)
        val baseUrl = (config?.first ?: "https://api.openai.com").trimEnd('/') + "/"
        val authHeader = config?.second?.first ?: "Authorization"
        val authPrefix = (config?.second?.second ?: "Bearer").let { if (it.isNotEmpty() && !it.endsWith(" ")) "$it " else it }
        val api = OpenAiApi.create(apiKey, baseUrl, authHeader, authPrefix)
        val chosenModel = model ?: when (provider) {
            "deepseek" -> "deepseek-chat"; "mistral" -> "mistral-large-latest"
            "groq" -> "mixtral-8x7b-32768"; else -> "gpt-4o-mini"
        }
        val body: Map<String, Any?> = mapOf(
            "model" to chosenModel,
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You are an expert school examiner. Output ONLY valid JSON arrays."),
                mapOf("role" to "user", "content" to prompt)
            ),
            "temperature" to 0.4f,
            "max_tokens" to 4000
        )
        return api.chat(body).choices?.firstOrNull()?.message?.content ?: ""
    }

    private suspend fun callAnthropic(apiKey: String, model: String?, prompt: String): String {
        val api = AnthropicApi.create(apiKey, "https://api.anthropic.com/")
        val resp = api.generate(AnthropicRequest(
            model = model ?: "claude-3-5-sonnet-20241022", maxTokens = 4000,
            messages = listOf(AnthropicMessage("user", prompt))
        ))
        return resp.content?.firstOrNull()?.text ?: ""
    }

    private suspend fun callGoogle(apiKey: String, prompt: String): String {
        val api = GoogleApi.create(apiKey, "https://generativelanguage.googleapis.com/")
        val resp = api.generate(GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(prompt))))))
        return resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
    }

    private fun parseQuestionsFromJson(raw: String): List<CapabilityQuestion> {
        if (raw.isBlank()) return emptyList()
        return try {
            var text = raw.trim()
            if (text.contains("```json")) text = text.substringAfter("```json").substringBefore("```").trim()
            else if (text.contains("```")) text = text.substringAfter("```").substringBefore("```").trim()
            if (text.startsWith("{")) {
                val obj = gson.fromJson(text, com.google.gson.JsonObject::class.java)
                text = (obj.get("questions") ?: obj.get("data") ?: obj.get("items"))
                    ?.toString() ?: text
            }
            val el: JsonElement = gson.fromJson(text, JsonElement::class.java)
            val arr = if (el.isJsonArray) el.asJsonArray else return emptyList()
            arr.mapIndexedNotNull { i, qEl ->
                if (!qEl.isJsonObject) return@mapIndexedNotNull null
                val o = qEl.asJsonObject
                val options = o.getAsJsonArray("options")?.map { it.asString } ?: return@mapIndexedNotNull null
                if (options.size < 2) return@mapIndexedNotNull null
                CapabilityQuestion(
                    id = o.get("id")?.asString ?: "q${i + 1}",
                    subject = o.get("subject")?.asString ?: "general",
                    questionText = o.get("question")?.asString ?: return@mapIndexedNotNull null,
                    options = options,
                    correctIndex = (o.get("correctIndex")?.asInt ?: 0).coerceIn(0, options.size - 1),
                    difficulty = o.get("difficulty")?.asInt ?: 2
                )
            }
        } catch (e: Exception) {
            Log.w("CapabilityTestClient", "parse failed: ${e.message}")
            emptyList()
        }
    }

    // ─── Adaptive fallback — questions tailored to the student's subjects ─────

    fun builtInAdaptiveFallback(profile: StudentProfile, subjects: List<String>): List<CapabilityQuestion> {
        val perSubject = 3
        val allQuestions = mutableListOf<CapabilityQuestion>()

        // Determine difficulty tier by grade
        val gradeNum = extractGradeNumber(profile.gradeLevelLabel)
        val (tier1, tier2, tier3) = when {
            gradeNum <= 3 -> Triple(1, 1, 1)  // primary: mostly easy
            gradeNum <= 6 -> Triple(1, 2, 2)  // middle: mix easy/medium
            gradeNum <= 9 -> Triple(1, 2, 3)  // junior sec: easy/medium/medium
            else -> Triple(1, 3, 5)           // senior: easy/medium/hard
        }
        val difficulties = listOf(tier1, tier2, tier3)
        var globalIdx = 0

        // Map subject names to question generators
        val subjectGenerators = mapOf(
            "math" to { sid: Int ->
                listOf(
                    q(sid, "math", "What is 7 × 8?", listOf("48","54","56","64"), 2, difficulties[0]),
                    q(sid, "math", "If a rectangle is 9 cm × 4 cm, what is the area?", listOf("26 cm²","36 cm²","13 cm²","40 cm²"), 1, difficulties[1]),
                    q(sid, "math", "Solve 2x + 5 = 13", listOf("x=3","x=4","x=5","x=9"), 1, difficulties[2])
                )
            },
            "english" to { sid: Int ->
                listOf(
                    q(sid, "english", "Which word is a synonym for 'happy'?", listOf("Sad","Joyful","Tired","Angry"), 1, difficulties[0]),
                    q(sid, "english", "Identify the noun: 'The dog ran quickly.'", listOf("ran","quickly","dog","across"), 2, difficulties[1]),
                    q(sid, "english", "Which is correct punctuation?", listOf("Its a nice day.","It's a nice day.","Its' a nice day.","Its a nice day!"), 1, difficulties[2])
                )
            },
            "science" to { sid: Int ->
                listOf(
                    q(sid, "science", "What gas do plants absorb for photosynthesis?", listOf("Oxygen","Nitrogen","Carbon Dioxide","Hydrogen"), 2, difficulties[0]),
                    q(sid, "science", "Which planet is closest to the Sun?", listOf("Venus","Earth","Mars","Mercury"), 3, difficulties[1]),
                    q(sid, "science", "What is the unit of electric current?", listOf("Volt","Ohm","Ampere","Watt"), 2, difficulties[2])
                )
            },
            "history" to { sid: Int ->
                listOf(
                    q(sid, "history", "The Amazon rainforest is on which continent?", listOf("Africa","Asia","South America","Australia"), 2, difficulties[0]),
                    q(sid, "history", "What year did WWII end?", listOf("1943","1944","1945","1946"), 2, difficulties[1]),
                    q(sid, "history", "Which ancient civilisation built the pyramids?", listOf("Romans","Greeks","Egyptians","Persians"), 2, difficulties[2])
                )
            },
            "ict" to { sid: Int ->
                listOf(
                    q(sid, "ict", "What does CPU stand for?", listOf("Central Processing Unit","Computer Personal Unit","Central Program Utility","Core Processing Unit"), 0, difficulties[0]),
                    q(sid, "ict", "Which is an input device?", listOf("Monitor","Printer","Keyboard","Speaker"), 2, difficulties[1]),
                    q(sid, "ict", "What does HTML stand for?", listOf("Hyper Text Markup Language","High Tech Modern Language","Hyper Transfer Markup Language","Home Text Making Language"), 0, difficulties[2])
                )
            },
            "economics" to { sid: Int ->
                listOf(
                    q(sid, "economics", "What is inflation?", listOf("Rising unemployment","Falling GDP","General rise in prices","Lower interest rates"), 2, difficulties[0]),
                    q(sid, "economics", "What does supply and demand determine?", listOf("Tax rates","Prices of goods","Population size","Voter turnout"), 1, difficulties[1]),
                    q(sid, "economics", "What is a budget deficit?", listOf("More income than spending","Less spending than income","More spending than income","Balanced books"), 2, difficulties[2])
                )
            },
            "biology" to { sid: Int ->
                listOf(
                    q(sid, "biology", "What is the powerhouse of the cell?", listOf("Nucleus","Ribosome","Mitochondria","Chloroplast"), 2, difficulties[0]),
                    q(sid, "biology", "What system controls breathing?", listOf("Digestive","Respiratory","Circulatory","Nervous"), 1, difficulties[1]),
                    q(sid, "biology", "What is photosynthesis?", listOf("Breaking down food","Converting light to energy","Cell division","Transporting water"), 1, difficulties[2])
                )
            },
            "chemistry" to { sid: Int ->
                listOf(
                    q(sid, "chemistry", "What is H₂O?", listOf("Salt","Water","Oxygen","Hydrogen"), 1, difficulties[0]),
                    q(sid, "chemistry", "What is the pH of a neutral substance?", listOf("0","7","14","1"), 1, difficulties[1]),
                    q(sid, "chemistry", "What are protons, neutrons and electrons part of?", listOf("A molecule","An atom","A cell","A compound"), 1, difficulties[2])
                )
            },
            "physics" to { sid: Int ->
                listOf(
                    q(sid, "physics", "What keeps us on the ground?", listOf("Magnetism","Friction","Gravity","Pressure"), 2, difficulties[0]),
                    q(sid, "physics", "What is the unit of force?", listOf("Joule","Newton","Pascal","Watt"), 1, difficulties[1]),
                    q(sid, "physics", "What does a convex lens do to light?", listOf("Spreads it","Bends it inward","Blocks it","Reflects it"), 1, difficulties[2])
                )
            }
        )

        // Use subject IDs from the onboarding subject catalogue, or fall back to name matching
        val subjectIds = subjects.map { s ->
            when {
                s.contains("math", ignoreCase = true) || s.contains("mathematics", ignoreCase = true) -> "math"
                s.contains("english", ignoreCase = true) || s.contains("literacy", ignoreCase = true) -> "english"
                s.contains("science", ignoreCase = true) -> "science"
                s.contains("history", ignoreCase = true) -> "history"
                s.contains("ict", ignoreCase = true) || s.contains("computer", ignoreCase = true) || s.contains("computing", ignoreCase = true) -> "ict"
                s.contains("economic", ignoreCase = true) -> "economics"
                s.contains("chem", ignoreCase = true) -> "chemistry"
                s.contains("phys", ignoreCase = true) || s.contains("physics", ignoreCase = true) -> "physics"
                s.contains("bio", ignoreCase = true) -> "biology"
                else -> "math"
            }
        }

        for (sid in subjectIds) {
            val gen = subjectGenerators[sid]
            if (gen != null) {
                allQuestions.addAll(gen(globalIdx))
                globalIdx += perSubject
            }
        }

        // If somehow we got no questions, provide the default math questions
        if (allQuestions.isEmpty()) {
            allQuestions.addAll(listOf(
                CapabilityQuestion("f1","math","What is 7 × 8?", listOf("48","54","56","64"),2,1),
                CapabilityQuestion("f2","math","Area of 9×4 rectangle?", listOf("26 cm²","36 cm²","13 cm²","40 cm²"),1,2),
                CapabilityQuestion("f3","math","Solve 2x+5=13", listOf("x=3","x=4","x=5","x=9"),1,3)
            ))
        }

        return allQuestions
    }

    private fun q(id: Int, subject: String, question: String, options: List<String>, correctIndex: Int, difficulty: Int): CapabilityQuestion {
        return CapabilityQuestion("q$id", subject, question, options, correctIndex, difficulty)
    }

    /** Extract a numeric grade from a label like "JSS 2", "Year 6", "Grade 3" */
    private fun extractGradeNumber(label: String): Int {
        val num = label.filter { it.isDigit() }
        return if (num.isNotEmpty()) num.toInt() else 6
    }
}
