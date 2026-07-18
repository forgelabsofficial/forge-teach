package com.aiteacher.ai

/**
 * A tool an agent can call during its run.
 * name/description are injected into the system prompt so the AI knows what's available.
 */
interface AgentTool {
    val name: String
    val description: String
    suspend fun execute(input: String): String
}

/**
 * Formats the student's availability map into a human-readable string
 * the AI can reason about when building a schedule.
 */
object AvailabilityTool : AgentTool {
    override val name = "availability"
    override val description = "Returns the student's weekly availability as a formatted schedule"

    override suspend fun execute(input: String): String = input // input IS the formatted availability

    fun format(availability: Map<String, List<String>>, sessionMinutes: Int): String {
        if (availability.isEmpty()) return "No availability set — default to weekday evenings 16:00-17:00"
        val lines = availability.entries.joinToString("\n") { (day, slots) ->
            "  $day: ${slots.joinToString(", ")}"
        }
        return "Weekly availability (session length: ${sessionMinutes}min):\n$lines"
    }
}

/**
 * Wraps WebSearchTool as an AgentTool
 */
object WebSearchAgentTool : AgentTool {
    override val name = "web_search"
    override val description = "Search the web for curriculum and syllabus information"

    override suspend fun execute(input: String): String =
        WebSearchTool.search(input)
}
