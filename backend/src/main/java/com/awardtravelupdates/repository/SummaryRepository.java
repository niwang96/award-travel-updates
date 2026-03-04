package com.awardtravelupdates.repository;

import com.awardtravelupdates.model.SubredditSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SummaryRepository extends JpaRepository<SubredditSummary, String> {}
