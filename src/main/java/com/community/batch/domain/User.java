package com.community.batch.domain;

import com.community.batch.domain.enums.Grade;
import com.community.batch.domain.enums.SocialType;
import com.community.batch.domain.enums.UserStatus;
import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(of = { "idx", "email" })
@Entity
@Table
public class User implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idx;

	private String name;

	private String password;

	private String email;

	private String principal;

	@Enumerated(value = EnumType.STRING)
	private SocialType socialType;

	@Enumerated(value = EnumType.STRING)
	private UserStatus status;

	@Enumerated(value = EnumType.STRING)
	private Grade grade;

	private LocalDateTime createdDate;

	private LocalDateTime updatedDate;

	public User() {
	}

	@Builder
	public User(String name, String password, String email, String principal, SocialType socialType, UserStatus status,
		LocalDateTime createdDate, LocalDateTime updatedDate) {
		this.name = name;
		this.password = password;
		this.email = email;
		this.principal = principal;
		this.socialType = socialType;
		this.status = status;
		this.createdDate = createdDate;
		this.updatedDate = updatedDate;
	}

	public User setInactive() {
		status = UserStatus.INACTIVE;
		return this;
	}
}
