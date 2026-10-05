package com.musicclubapp.repository;

import com.musicclubapp.entity.PerformerLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PerformerLinkRepository extends JpaRepository<PerformerLink, Long> {

    List<PerformerLink> findByNameKeyIn(Collection<String> nameKeys);

    List<PerformerLink> findByNameKey(String nameKey);
}
