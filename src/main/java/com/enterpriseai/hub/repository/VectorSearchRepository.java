package com.enterpriseai.hub.repository;

import com.enterpriseai.hub.document.TextChunk;
import com.enterpriseai.hub.exception.VectorSearchException;
import com.enterpriseai.hub.rag.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * pgvector access layer.
 *
 * <p>Deliberately plain JDBC rather than JPA. Three reasons:</p>
 * <ol>
 *   <li>Hibernate has no native mapping for the {@code vector} type, and a converter would
 *       serialise embeddings through Java objects on every read.</li>
 *   <li>The similarity query is an {@code ORDER BY} on a distance operator with a LIMIT -
 *       the exact shape the HNSW index can serve. Expressing it in JPQL is not possible,
 *       and a native query returning entities would fetch the embedding column too.</li>
 *   <li>Ingestion writes thousands of rows at a time and benefits from a real JDBC batch.</li>
 * </ol>
 *
 * <p>Vectors are bound as their PostgreSQL text form ({@code [0.1,0.2,...]}) and cast in
 * SQL with {@code CAST(? AS vector)}, which keeps the driver dependency to the standard
 * PostgreSQL JDBC driver.</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class VectorSearchRepository {

    private static final String INSERT_CHUNK = """
            INSERT INTO document_chunks
                (document_id, chunk_index, content, character_count, token_estimate,
                 page_number, section, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
            """;

    private static final String SEARCH_TEMPLATE = """
            SELECT c.id,
                   c.document_id,
                   d.title,
                   d.file_name,
                   c.chunk_index,
                   c.page_number,
                   c.section,
                   c.content,
                   1 - (c.embedding <=> CAST(? AS vector)) AS similarity
            FROM document_chunks c
                     JOIN documents d ON d.id = c.document_id
            WHERE c.embedding IS NOT NULL
              AND d.status = 'READY'
              %s
            ORDER BY c.embedding <=> CAST(? AS vector)
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<RetrievedChunk> CHUNK_MAPPER = (rs, rowNum) -> new RetrievedChunk(
            rs.getLong("id"),
            rs.getLong("document_id"),
            rs.getString("title"),
            rs.getString("file_name"),
            rs.getInt("chunk_index"),
            (Integer) rs.getObject("page_number"),
            rs.getString("section"),
            rs.getString("content"),
            rs.getDouble("similarity"));

    /**
     * Bulk-inserts the chunks of a single document with their embeddings.
     *
     * @throws IllegalArgumentException if the two lists are not aligned - an off-by-one
     *                                  here would attach the wrong vector to a passage and
     *                                  silently corrupt retrieval.
     */
    @Transactional
    public int insertChunks(Long documentId, List<TextChunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException(
                    "Chunk/embedding count mismatch: " + chunks.size() + " vs " + embeddings.size());
        }
        if (chunks.isEmpty()) {
            return 0;
        }

        try {
            int[] affected = jdbcTemplate.batchUpdate(INSERT_CHUNK, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int index) throws SQLException {
                    TextChunk chunk = chunks.get(index);
                    ps.setLong(1, documentId);
                    ps.setInt(2, chunk.index());
                    ps.setString(3, chunk.content());
                    ps.setInt(4, chunk.characterCount());
                    ps.setInt(5, chunk.tokenEstimate());
                    if (chunk.pageNumber() == null) {
                        ps.setNull(6, Types.INTEGER);
                    } else {
                        ps.setInt(6, chunk.pageNumber());
                    }
                    ps.setString(7, chunk.section());
                    ps.setString(8, toVectorLiteral(embeddings.get(index)));
                }

                @Override
                public int getBatchSize() {
                    return chunks.size();
                }
            });
            return affected.length;
        } catch (DataAccessException ex) {
            throw new VectorSearchException("Failed to persist embeddings for document " + documentId, ex);
        }
    }

    /**
     * Approximate nearest-neighbour search over cosine distance.
     *
     * @param documentIds optional restriction to a subset of documents (used by
     *                    "ask this document"); {@code null} or empty searches everything.
     */
    public List<RetrievedChunk> search(float[] queryEmbedding, int limit, List<Long> documentIds) {
        String vector = toVectorLiteral(queryEmbedding);
        List<Object> arguments = new ArrayList<>();
        arguments.add(vector);

        String filter = "";
        if (documentIds != null && !documentIds.isEmpty()) {
            filter = "AND c.document_id IN (" + placeholders(documentIds.size()) + ")";
            arguments.addAll(documentIds);
        }
        arguments.add(vector);
        arguments.add(limit);

        String sql = SEARCH_TEMPLATE.formatted(filter);
        try {
            return jdbcTemplate.query(sql, CHUNK_MAPPER, arguments.toArray());
        } catch (DataAccessException ex) {
            throw new VectorSearchException("Vector similarity search failed", ex);
        }
    }

    @Transactional
    public int deleteByDocumentId(Long documentId) {
        return jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
    }

    public long countEmbeddedChunks() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE embedding IS NOT NULL", Long.class);
        return count == null ? 0L : count;
    }

    /**
     * Verifies at startup that the column dimension matches the configured embedding model.
     * A mismatch would otherwise surface as a confusing runtime error on the first insert.
     */
    public int embeddingColumnDimensions() {
        try {
            Integer dimensions = jdbcTemplate.queryForObject("""
                    SELECT a.atttypmod
                    FROM pg_attribute a
                             JOIN pg_class c ON c.oid = a.attrelid
                    WHERE c.relname = 'document_chunks' AND a.attname = 'embedding'
                    """, Integer.class);
            return dimensions == null ? -1 : dimensions;
        } catch (DataAccessException ex) {
            log.warn("Could not read the embedding column dimension: {}", ex.getMessage());
            return -1;
        }
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    /** Renders a float array as the pgvector text literal {@code [v1,v2,...]}. */
    static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 8 + 2);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }
}
