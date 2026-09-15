package com.enterpriseai.hub.rag;

import com.enterpriseai.hub.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders retrieved passages into the numbered context block that the prompt refers to.
 *
 * <p>The numbering is the contract between three parts of the system: the model is told to
 * cite {@code [n]}, the persisted citations are stored with the same rank, and the UI links
 * {@code [n]} in the answer to the corresponding source card. Passages are added in score
 * order until the character budget is spent, so the most relevant material is never the
 * part that gets truncated.</p>
 */
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private final AppProperties properties;

    public BuiltContext build(List<RetrievedChunk> chunks) {
        int budget = properties.getRag().getMaxContextCharacters();
        StringBuilder context = new StringBuilder();
        List<RetrievedChunk> included = new ArrayList<>();

        int used = 0;
        for (RetrievedChunk chunk : chunks) {
            String header = "[%d] %s".formatted(included.size() + 1, chunk.citationLabel());
            String body = chunk.content().strip();
            int cost = header.length() + body.length() + 3;

            if (used + cost > budget) {
                if (included.isEmpty()) {
                    // Always include at least one passage, truncated to fit.
                    int room = Math.max(200, budget - header.length() - 3);
                    body = body.length() > room ? body.substring(0, room) + "..." : body;
                    context.append(header).append('\n').append(body).append("\n\n");
                    included.add(chunk);
                }
                break;
            }

            context.append(header).append('\n').append(body).append("\n\n");
            included.add(chunk);
            used += cost;
        }

        return new BuiltContext(context.toString().strip(), included);
    }

    public record BuiltContext(String text, List<RetrievedChunk> includedChunks) {
    }
}
