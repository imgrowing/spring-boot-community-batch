package com.community.batch.jobs.listener;

import com.community.batch.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InactiveProcessListener implements ItemProcessListener<User, User> {

	@Override
	public void beforeProcess(User user) {
		log.warn("Before Process: user {}", user.getIdx());
	}

	@Override
	public void afterProcess(User input, User output) {
		log.warn("After Process: user {} -> {}", input.getIdx(), output.getIdx());
	}

	@Override
	public void onProcessError(User user, Exception e) {
		log.warn("onProcessError()");
	}
}
