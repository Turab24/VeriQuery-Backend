package com.enterpriseai.hub.repository;

import com.enterpriseai.hub.domain.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Page<Conversation> findByUserId(Long userId, Pageable pageable);

    Page<Conversation> findByUserIdAndTitleContainingIgnoreCase(Long userId, String title, Pageable pageable);

    /**
     * Ownership is part of the lookup, not a check performed afterwards: there is no code
     * path that can load another user's conversation in the first place.
     */
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
