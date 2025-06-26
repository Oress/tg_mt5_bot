package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.projections.EnabledInstrumentPerUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepo extends JpaRepository<AppUser, Long> {
    @Query(value = """
            SELECT u FROM AppUser u
                WHERE LOWER(u.telegramUsername) LIKE LOWER(:username) OR LOWER(u.telegramFirstName) LIKE LOWER(:firstName)
                ORDER BY u.telegramUsername, u.telegramFirstName, u.id
            """
            )
    Page<AppUser> searchByUsernameAndFirstname(String username, String firstName, PageRequest of);

    @Query("select u from AppUser u left join fetch u.availableInstruments where u.telegramId = :userId")
    AppUser findByIdWithInstruments(Long userId);

    List<AppUser> findByMt5AccNumber(Integer mt5AccNumber);

    @Query(nativeQuery = true,
            value = """
            select
                i.label,
                u.tg_id is not null as enabled
            from
                hed_instruments i
                    left join hed_user_instruments ui on i.label = ui.instrument_label
                    left join hed_users u on ui.instrument_label = i.label
            where u.tg_id = :userId or u.tg_id is null
            order by i.label
    """)
    List<EnabledInstrumentPerUser> getEnabledInstruments(Long userId);
}
