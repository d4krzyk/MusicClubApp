package com.musicclubapp.repository;

import com.musicclubapp.entity.BlockedIp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Zablokowane adresy sieciowe. */
@Repository
public interface BlockedIpRepository extends JpaRepository<BlockedIp, Long> {

    boolean existsByAddress(String address);

    Optional<BlockedIp> findByAddress(String address);

    /** Lista dla panelu - od najnowszej blokady. */
    Page<BlockedIp> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
