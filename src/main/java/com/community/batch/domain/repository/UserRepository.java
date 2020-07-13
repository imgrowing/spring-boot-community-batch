package com.community.batch.domain.repository;

import com.community.batch.domain.User;
import com.community.batch.domain.enums.Grade;
import com.community.batch.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
	List<User> findByUpdatedDateBeforeAndStatus(LocalDateTime localDateTime, UserStatus status);
	List<User> findByUpdatedDateBeforeAndStatusAndGrade(LocalDateTime localDateTime, UserStatus status, Grade grade);
}
