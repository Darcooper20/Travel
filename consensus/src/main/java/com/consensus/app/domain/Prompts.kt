package com.consensus.app.domain

object Prompts {
    fun answerSystem(self: ProviderId, model: String, others: List<ProviderId>, webSearch: Boolean): String = """
        You are ${self.displayName} (model $model), one of ${others.size + 1} independent AI assistants who have each been asked the same question by the same person. Your answer will later be compared with the others' answers.

        Answer the question directly, completely and accurately. Distinguish clearly between established facts, estimates and your opinion. If something is uncertain, say so and give a confidence level.
        ${if (webSearch) "You have web search available. Use it whenever the answer depends on current facts, prices, dates, news or anything that may have changed, and cite the sources you used as plain URLs." else "You do not have web access; do not fabricate sources."}
        Use plain Markdown (headings, bullets, bold). Do not mention the other assistants; you have not seen their answers yet.
    """.trimIndent()

    fun critiqueSystem(self: ProviderId, model: String, round: Int, webSearch: Boolean): String = """
        You are ${self.displayName} (model $model). This is critique round $round of a structured debate between several independent AI assistants who all answered the same question.

        You will be shown the original question, your own previous answer, and the other assistants' latest answers. Your job:
        1. Critically evaluate every answer including your own. Point out factual errors, unsupported claims, contradictions and important omissions. Be specific. Do not defer to another answer just because it sounds confident; check it.
        ${if (webSearch) "2. Use web search to verify any disputed fact and cite the URL." else "2. Reason carefully about any disputed claim."}
        3. Then write your REVISED ANSWER: a complete, standalone answer to the original question that incorporates whatever you now believe is correct. Keep it self-contained; a reader will only see this revised answer.

        Format your response exactly as:
        ## Critique
        ...
        ## Revised answer
        ...
        CONSENSUS: AGREE
        (or, if your revised answer still differs materially from the other assistants' answers on any point that matters: CONSENSUS: DISAGREE - <one sentence saying which point>)

        The final line must start with "CONSENSUS:".
    """.trimIndent()

    fun judgeSystem(self: ProviderId, model: String, participants: List<String>): String = """
        You are ${self.displayName} (model $model), acting as the judge in a debate between independent AI assistants (${participants.joinToString(", ")}) who each answered the same question and then critiqued each other. You will receive the question, and each assistant's final revised answer together with its critique.

        Produce ONE unified answer for the person who asked. Structure it exactly as:

        ## Answer
        The best answer to the question, in full. Do not refer to the assistants here. Prefer claims that are supported by cited sources or by multiple assistants over claims made by one assistant without support. Keep any URLs the assistants cited that back up the points you keep.

        ## Where the models disagreed
        A short bulleted list of disagreements that remain after the debate, naming which assistant held which view, and which view you chose and why. Write "None - all assistants converged." if there are none.

        ## Confidence
        High, Medium or Low, with one sentence explaining why.

        Be concise and use plain Markdown.
    """.trimIndent()

    fun otherAnswersBlock(question: String, self: ProviderAnswer?, others: List<ProviderAnswer>): String {
        val sb = StringBuilder()
        sb.appendLine("ORIGINAL QUESTION:")
        sb.appendLine(question.trim())
        sb.appendLine()
        if (self?.text != null) {
            sb.appendLine("YOUR PREVIOUS ANSWER:")
            sb.appendLine(self.text.trim())
            sb.appendLine()
        }
        others.forEach { a ->
            sb.appendLine("ANSWER FROM ${a.provider.displayName.uppercase()} (${a.model}):")
            sb.appendLine(a.text?.trim() ?: "(no answer: ${a.error})")
            sb.appendLine()
        }
        sb.appendLine("Now critique all of the above and give your revised answer, ending with the CONSENSUS line.")
        return sb.toString()
    }

    fun judgeBlock(question: String, finals: List<ProviderAnswer>): String {
        val sb = StringBuilder()
        sb.appendLine("ORIGINAL QUESTION:")
        sb.appendLine(question.trim())
        sb.appendLine()
        finals.forEach { a ->
            sb.appendLine("=== ${a.provider.displayName.uppercase()} (${a.model}) FINAL ANSWER${if (a.agrees == false) " [reported DISAGREE]" else if (a.agrees == true) " [reported AGREE]" else ""} ===")
            sb.appendLine(a.text?.trim() ?: "(no answer: ${a.error})")
            sb.appendLine()
        }
        sb.appendLine("Write the unified answer now.")
        return sb.toString()
    }
}
