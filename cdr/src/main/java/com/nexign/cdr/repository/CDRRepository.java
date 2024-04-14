package com.nexign.cdr.repository;

import com.nexign.cdr.model.CDR;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CDRRepository extends JpaRepository<CDR, Long> {
}
