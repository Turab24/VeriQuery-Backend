package com.enterpriseai.hub.repository;

import com.enterpriseai.hub.domain.Document;
import com.enterpriseai.hub.domain.DocumentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Composable filters for the document listing.
 *
 * <p>Specifications are used rather than a single JPQL query with {@code :param IS NULL}
 * branches: those force the database to plan for every combination at once, and they are
 * brittle with typed parameters. Here an absent filter contributes no predicate at all, so
 * the generated SQL contains exactly the conditions the caller asked for.</p>
 */
public final class DocumentSpecifications {

    private DocumentSpecifications() {
    }

    public static Specification<Document> hasStatus(DocumentStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<Document> matchesText(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
        return (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("title")), pattern),
                builder.like(builder.lower(root.get("fileName")), pattern));
    }

    /**
     * @return the combined specification, or {@code null} when no filter was requested -
     * which Spring Data interprets as "no restriction".
     */
    public static Specification<Document> filter(DocumentStatus status, String search) {
        Specification<Document> statusSpecification = hasStatus(status);
        Specification<Document> textSpecification = matchesText(search);

        if (statusSpecification == null) {
            return textSpecification;
        }
        return textSpecification == null ? statusSpecification : statusSpecification.and(textSpecification);
    }
}
