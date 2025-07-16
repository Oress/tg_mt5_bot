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

    @Query("select u from AppUser u left join fetch u.availableInstruments ui left join fetch ui.instrument where u.telegramId = :userId")
    AppUser findByIdWithInstruments(Long userId);

    List<AppUser> findByMt5AccNumber(Integer mt5AccNumber);

    @Query(nativeQuery = true,
            value = """
            select
                :userId,
                i.label,
                exists(select * from hed_user_instruments ui where ui.user_id = :userId and ui.instrument_label = i.label and ui.active = true) as enabled
            from
                hed_instruments i
            order by i.label
    """)
    List<EnabledInstrumentPerUser> getEnabledInstruments(Long userId);

    @Query(nativeQuery = true,
            value = """
                    select nextval('seq_hed_users_sheet_id')
                    """)
    Integer getNextSheetId();

    @Query(value = """
                    from AppUser u
                    where lower(u.telegramUsername) like :query or lower(u.telegramFirstName) like :query
                    order by u.telegramId, u.telegramUsername, u.telegramFirstName
                    """)
    List<AppUser> searchForUsers(String query);
}
