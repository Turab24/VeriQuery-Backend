package com.enterpriseai.hub.ai.demo;

import com.enterpriseai.hub.ai.ChatCompletionClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline generation used when {@code app.ai.provider=demo}.
 *
 * <p>It does not generate language. It parses the numbered context block that the RAG
 * pipeline puts into the user prompt, scores each passage's sentences against the
 * question by term overlap, and returns the best sentences verbatim with their citation
 * markers. The output is therefore always grounded in the retrieved documents - which is
 * exactly what is needed to demonstrate and test the pipeline, but it is extractive, not
 * abstractive. Point {@code app.ai.provider} at {@code openai} for real synthesis.</p>
 */
public class ExtractiveChatCompletionClient implements ChatCompletionClient {

    // A passage runs from its [n] marker to the next marker, to the QUESTION line, or to the
    // end of the prompt - whichever comes first.
    private static final Pattern PASSAGE_PATTERN =
            Pattern.compile("\\[(\\d+)]\\s*(.*?)\\n(.*?)(?=\\n\\[\\d+]|\\nQUESTION:|\\z)", Pattern.DOTALL);
    private static final Pattern QUESTION_PATTERN =
            Pattern.compile("QUESTION:\\s*(.*?)\\s*\\z", Pattern.DOTALL);
    private static final int MAX_SENTENCES = 5;

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String question = extract(QUESTION_PATTERN, userPrompt);
        List<Passage> passages = parsePassages(userPrompt);

        if (passages.isEmpty()) {
            return "I could not find anything in the indexed documents that answers this question. "
                    + "Try uploading a document that covers the topic, or rephrasing the question.";
        }

        List<String> questionTerms = terms(question);
        List<ScoredSentence> scored = new ArrayList<>();
        for (Passage passage : passages) {
            for (String sentence : splitSentences(passage.body())) {
                double score = overlapScore(questionTerms, terms(sentence));
                if (score > 0) {
                    scored.add(new ScoredSentence(sentence.trim(), passage.index(), score));
                }
            }
        }

        if (scored.isEmpty()) {
            Passage best = passages.get(0);
            return "The retrieved passages do not directly answer the question. The most relevant "
                    + "material found was:\n\n" + truncate(best.body(), 600) + " [" + best.index() + "]";
        }

        scored.sort((left, right) -> Double.compare(right.score(), left.score()));

        StringBuilder answer = new StringBuilder("Based on the indexed documents:\n\n");
        List<String> used = new ArrayList<>();
        for (ScoredSentence candidate : scored) {
            if (used.size() >= MAX_SENTENCES) {
                break;
            }
            if (used.contains(candidate.sentence())) {
                continue;
            }
            used.add(candidate.sentence());
            answer.append("- ").append(candidate.sentence())
                    .append(" [").append(candidate.passageIndex()).append("]\n");
        }
        answer.append("\nThese points are quoted from the cited sources; review them for the full context.");
        return answer.toString();
    }

    @Override
    public String modelName() {
        return "demo-extractive-reader";
    }

    private List<Passage> parsePassages(String userPrompt) {
        List<Passage> passages = new ArrayList<>();
        Matcher matcher = PASSAGE_PATTERN.matcher(userPrompt);
        while (matcher.find()) {
            passages.add(new Passage(Integer.parseInt(matcher.group(1)), matcher.group(3).trim()));
        }
        return passages;
    }

    private String extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : text;
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String candidate : text.split("(?<=[.!?])\\s+|\\n+")) {
            String trimmed = candidate.trim();
            if (trimmed.length() >= 25) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private List<String> terms(String text) {
        List<String> terms = new ArrayList<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+")) {
            if (token.length() > 2) {
                terms.add(token);
            }
        }
        return terms;
    }

    private double overlapScore(List<String> questionTerms, List<String> sentenceTerms) {
        if (questionTerms.isEmpty() || sentenceTerms.isEmpty()) {
            return 0;
        }
        int matches = 0;
        for (String term : questionTerms) {
            if (sentenceTerms.contains(term)) {
                matches++;
            }
        }
        return matches / (double) questionTerms.size() * (1.0 / (1.0 + Math.log1p(sentenceTerms.size())));
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private record Passage(int index, String body) {
    }

    private record ScoredSentence(String sentence, int passageIndex, double score) {
    }
}
