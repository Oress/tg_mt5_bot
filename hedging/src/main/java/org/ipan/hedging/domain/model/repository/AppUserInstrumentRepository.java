package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.AppUserInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserInstrumentRepository extends JpaRepository<AppUserInstrument, Long> {
}
