package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<AppUser, Long> {
    @Query(value = """
            SELECT u FROM AppUser u
                WHERE LOWER(u.telegramUsername) LIKE LOWER(:username) OR LOWER(u.telegramFirstName) LIKE LOWER(:firstName)
                ORDER BY u.telegramUsername, u.telegramFirstName, u.id
            """
            )
    Page<AppUser> searchByUsernameAndFirstname(String username, String firstName, PageRequest of);
}
