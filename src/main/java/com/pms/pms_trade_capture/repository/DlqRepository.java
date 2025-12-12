package com.pms.pms_trade_capture.repository;

import com.pms.pms_trade_capture.domain.DlqEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DlqRepository extends JpaRepository<DlqEntry, Long> {
}
