package com.musicclubapp.repository;

import com.musicclubapp.entity.PerformerTags;
import org.springframework.data.jpa.repository.JpaRepository;

/** Gatunki wykonawcow z Last.fm, zapamietane. */
public interface PerformerTagsRepository extends JpaRepository<PerformerTags, String> {
}
