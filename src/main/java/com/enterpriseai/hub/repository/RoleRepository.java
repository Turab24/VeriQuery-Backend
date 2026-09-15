package com.enterpriseai.hub.repository;

import com.enterpriseai.hub.domain.Role;
import com.enterpriseai.hub.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
