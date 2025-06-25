package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstrumentRepo extends JpaRepository<Instrument, String> {
}
