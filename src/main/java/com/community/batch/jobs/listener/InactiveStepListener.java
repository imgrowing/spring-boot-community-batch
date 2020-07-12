package com.community.batch.jobs.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import static org.springframework.batch.core.ExitStatus.COMPLETED;

@Slf4j
@Component
public class InactiveStepListener implements StepExecutionListener {
	@Override
	public void beforeStep(StepExecution stepExecution) {
		log.warn("Before Step");
	}

	@Override
	public ExitStatus afterStep(StepExecution stepExecution) {
		log.warn("After Step");
		return ExitStatus.COMPLETED;
	}
}
